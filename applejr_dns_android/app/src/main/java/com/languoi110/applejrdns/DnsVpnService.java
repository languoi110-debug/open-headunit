package com.languoi110.applejrdns;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

public class DnsVpnService extends VpnService {
    static final String PREFS = "applejr_dns";
    static final String KEY_RUNNING = "running";
    static final String KEY_ENDPOINT = "endpoint";
    static final String KEY_MODE = "mode";
    static final String KEY_LAST_ERROR = "last_error";
    static final String MODE_GLOBAL = "global";
    static final String MODE_APPLE_ONLY = "apple_only";
    static final String DEFAULT_ENDPOINT = "https://novadns.novadev.vip/dns-query";

    static final String ACTION_START = "com.languoi110.applejrdns.START";
    static final String ACTION_STOP = "com.languoi110.applejrdns.STOP";

    private static final int NOTIFICATION_ID = 4012;
    private static final String CHANNEL_ID = "applejr_dns_vpn";
    private static final String VPN_CLIENT = "10.7.0.1";
    private static final String VPN_DNS = "10.7.0.2";

    private static final Set<String> APPLEJR_DOMAINS = Collections.unmodifiableSet(
            new java.util.HashSet<>(Arrays.asList(
                    "ocsp.apple.com",
                    "ocsp2.apple.com",
                    "mesu.apple.com",
                    "valid.apple.com",
                    "crl.apple.com",
                    "certs.apple.com",
                    "appattest.apple.com",
                    "vpp.itunes.apple.com",
                    "guzzoni-apple-com.v.aaplimg.com",
                    "gdmf.apple.com",
                    "axm-app.apple.com",
                    "comm-cohort.ess.apple.com",
                    "comm-main.ess.apple.com",
                    "applejr.net",
                    "ppq.apple.com",
                    "ocsp-a.g.aaplimg.com",
                    "ocsp2.g.aaplimg.com",
                    "ocsp2.apple.com.edgesuite.net",
                    "valid-apple.g.aaplimg.com",
                    "crl.g.aaplimg.com",
                    "certs.g.aaplimg.com",
                    "ppq-ext.v.aaplimg.com",
                    "usw2-ppq-ext-prod.apple.com",
                    "use1-ppq-ext-prod.apple.com",
                    "ppq-st-ext.itunes.apple.com",
                    "ocsp3.apple.com",
                    "ocsp4.apple.com"
            ))
    );

    private final Object writeLock = new Object();
    private final ExecutorService dnsWorkers = Executors.newFixedThreadPool(4);

    private volatile boolean active;
    private volatile boolean stopping;
    private ParcelFileDescriptor tun;
    private Thread readerThread;
    private List<InetAddress> underlyingDns = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            shutdown();
            return START_NOT_STICKY;
        }

        stopping = false;
        startForeground(NOTIFICATION_ID, buildNotification("Đang khởi động DNS…"));
        if (!active) {
            startVpn();
        }
        return START_STICKY;
    }

    private void startVpn() {
        captureUnderlyingDns();
        readerThread = new Thread(() -> {
            try {
                Builder builder = new Builder()
                        .setSession("AppleJr DNS Android")
                        .setMtu(1500)
                        .addAddress(VPN_CLIENT, 32)
                        .addDnsServer(VPN_DNS)
                        .addRoute(VPN_DNS, 32);

                try {
                    builder.addDisallowedApplication(getPackageName());
                } catch (Throwable ignored) {
                }

                tun = builder.establish();
                if (tun == null) {
                    throw new IllegalStateException("Không tạo được giao diện VPN DNS");
                }

                active = true;
                prefs().edit()
                        .putBoolean(KEY_RUNNING, true)
                        .remove(KEY_LAST_ERROR)
                        .apply();
                updateNotification("Đang hoạt động");

                try (FileInputStream in = new FileInputStream(tun.getFileDescriptor());
                     FileOutputStream out = new FileOutputStream(tun.getFileDescriptor())) {

                    byte[] buffer = new byte[65535];
                    while (active) {
                        int length = in.read(buffer);
                        if (length <= 0) continue;
                        byte[] packet = Arrays.copyOf(buffer, length);
                        dnsWorkers.execute(() -> handlePacket(packet, out));
                    }
                }
            } catch (Throwable t) {
                if (!stopping) {
                    recordError(t);
                }
            } finally {
                active = false;
                prefs().edit().putBoolean(KEY_RUNNING, false).apply();
                closeTun();
                stopForeground(true);
                stopSelf();
            }
        }, "AppleJrDnsReader");
        readerThread.start();
    }

    private void handlePacket(byte[] packet, FileOutputStream out) {
        try {
            if (packet == null || packet.length < 28) return;
            int version = (packet[0] >>> 4) & 0x0F;
            if (version != 4) return;

            int ihl = (packet[0] & 0x0F) * 4;
            if (ihl < 20 || packet.length < ihl + 8) return;
            if ((packet[9] & 0xFF) != 17) return;

            int udp = ihl;
            int sourcePort = DnsWire.readU16(packet, udp);
            int destinationPort = DnsWire.readU16(packet, udp + 2);
            if (destinationPort != 53) return;

            int udpLength = DnsWire.readU16(packet, udp + 4);
            int dnsOffset = udp + 8;
            int dnsLength = Math.min(Math.max(0, udpLength - 8), packet.length - dnsOffset);
            if (dnsLength < 12) return;

            byte[] query = Arrays.copyOfRange(packet, dnsOffset, dnsOffset + dnsLength);
            String qname = DnsWire.extractQuestionName(query);
            if (qname.isEmpty()) return;

            SharedPreferences sp = prefs();
            String endpoint = sp.getString(KEY_ENDPOINT, DEFAULT_ENDPOINT);
            String mode = sp.getString(KEY_MODE, MODE_GLOBAL);

            byte[] answer;
            if (MODE_GLOBAL.equals(mode) || isAppleJrDomain(qname)) {
                answer = queryDoh(endpoint, query);
            } else {
                answer = queryUnderlyingDns(query);
            }
            if (answer == null || answer.length < 12 || answer.length > 65000) return;

            byte[] responsePacket = buildUdpResponse(packet, sourcePort, answer);
            synchronized (writeLock) {
                out.write(responsePacket);
                out.flush();
            }
        } catch (Throwable ignored) {
        }
    }

    static boolean isAppleJrDomain(String qname) {
        if (qname == null) return false;
        String host = qname.toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        for (String domain : APPLEJR_DOMAINS) {
            if (host.equals(domain) || host.endsWith("." + domain)) return true;
        }
        return false;
    }

    private byte[] queryUnderlyingDns(byte[] query) {
        List<InetAddress> servers = underlyingDns;
        InetAddress selected = null;
        for (InetAddress address : servers) {
            if (address instanceof Inet4Address) {
                selected = address;
                break;
            }
        }
        if (selected == null && !servers.isEmpty()) selected = servers.get(0);

        try {
            if (selected == null) selected = InetAddress.getByName("1.1.1.1");
            DatagramSocket socket = new DatagramSocket();
            try {
                protect(socket);
                socket.setSoTimeout(5000);
                DatagramPacket request = new DatagramPacket(query, query.length, selected, 53);
                socket.send(request);

                byte[] buffer = new byte[65535];
                DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                socket.receive(response);
                return Arrays.copyOf(response.getData(), response.getLength());
            } finally {
                socket.close();
            }
        } catch (Throwable t) {
            return null;
        }
    }

    static byte[] queryDoh(String endpoint, byte[] query) throws Exception {
        if (endpoint == null || !endpoint.startsWith("https://")) {
            throw new IllegalArgumentException("DoH URL phải bắt đầu bằng https://");
        }

        URL url = new URL(endpoint);
        HttpURLConnection raw = (HttpURLConnection) url.openConnection();
        if (!(raw instanceof HttpsURLConnection)) {
            raw.disconnect();
            throw new IllegalArgumentException("DoH endpoint không dùng HTTPS");
        }

        HttpsURLConnection connection = (HttpsURLConnection) raw;
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/dns-message");
            connection.setRequestProperty("Content-Type", "application/dns-message");
            connection.setFixedLengthStreamingMode(query.length);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(query);
            }

            int code = connection.getResponseCode();
            if (code != 200) {
                throw new IllegalStateException("DoH HTTP " + code);
            }

            String type = connection.getContentType();
            if (type != null && !type.toLowerCase(Locale.ROOT).contains("application/dns-message")) {
                throw new IllegalStateException("Phản hồi không phải DNS message");
            }

            try (InputStream in = connection.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[4096];
                int total = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    total += n;
                    if (total > 65000) throw new IllegalStateException("DNS response quá lớn");
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }

    private byte[] buildUdpResponse(byte[] requestPacket, int requestSourcePort, byte[] dnsAnswer) {
        int totalLength = 20 + 8 + dnsAnswer.length;
        byte[] out = new byte[totalLength];

        out[0] = 0x45;
        out[1] = 0;
        DnsWire.putU16(out, 2, totalLength);
        out[4] = requestPacket[4];
        out[5] = requestPacket[5];
        out[6] = 0x40;
        out[7] = 0;
        out[8] = 64;
        out[9] = 17;
        out[10] = 0;
        out[11] = 0;

        System.arraycopy(requestPacket, 16, out, 12, 4);
        System.arraycopy(requestPacket, 12, out, 16, 4);

        int checksum = DnsWire.ipv4HeaderChecksum(out, 0, 20);
        DnsWire.putU16(out, 10, checksum);

        int udp = 20;
        DnsWire.putU16(out, udp, 53);
        DnsWire.putU16(out, udp + 2, requestSourcePort);
        DnsWire.putU16(out, udp + 4, 8 + dnsAnswer.length);
        DnsWire.putU16(out, udp + 6, 0);
        System.arraycopy(dnsAnswer, 0, out, udp + 8, dnsAnswer.length);
        return out;
    }

    private void captureUnderlyingDns() {
        ArrayList<InetAddress> result = new ArrayList<>();
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network network = cm == null ? null : cm.getActiveNetwork();
            LinkProperties lp = network == null || cm == null ? null : cm.getLinkProperties(network);
            if (lp != null) result.addAll(lp.getDnsServers());
        } catch (Throwable ignored) {
        }
        underlyingDns = result;
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void recordError(Throwable t) {
        String message = t.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = t.getClass().getSimpleName();
        }
        prefs().edit()
                .putBoolean(KEY_RUNNING, false)
                .putString(KEY_LAST_ERROR, message)
                .apply();
        updateNotification("Lỗi DNS: " + message);
    }

    private void shutdown() {
        stopping = true;
        active = false;
        prefs().edit().putBoolean(KEY_RUNNING, false).apply();
        closeTun();
        dnsWorkers.shutdownNow();
        stopForeground(true);
        stopSelf();
    }

    private void closeTun() {
        ParcelFileDescriptor local = tun;
        tun = null;
        if (local != null) {
            try {
                local.close();
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void onRevoke() {
        shutdown();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        stopping = true;
        active = false;
        prefs().edit().putBoolean(KEY_RUNNING, false).apply();
        closeTun();
        dnsWorkers.shutdownNow();
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "AppleJr DNS",
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("Trạng thái VPN DNS cục bộ");
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(
                this,
                0,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("AppleJr DNS Android")
                .setContentText(text)
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(text));
    }
}
