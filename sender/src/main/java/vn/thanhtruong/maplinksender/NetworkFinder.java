package vn.thanhtruong.maplinksender;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

final class NetworkFinder {
    private NetworkFinder() { }

    static Socket findReceiver(Context context, int port) throws Exception {
        WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifi != null) {
            DhcpInfo dhcp = wifi.getDhcpInfo();
            if (dhcp != null && dhcp.gateway != 0) {
                String gateway = intToIp(dhcp.gateway);
                Socket direct = connect(gateway, port, 1000);
                if (direct != null) return direct;
            }
        }

        String localIp = findWifiIpv4();
        if (localIp == null || localIp.lastIndexOf('.') < 0) return null;
        String subnet = localIp.substring(0, localIp.lastIndexOf('.') + 1);
        AtomicReference<Socket> found = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(32);
        for (int i = 1; i <= 254; i++) {
            final String host = subnet + i;
            if (host.equals(localIp)) continue;
            pool.execute(() -> {
                if (found.get() != null) return;
                Socket candidate = connect(host, port, 300);
                if (candidate != null && !found.compareAndSet(null, candidate)) {
                    try { candidate.close(); } catch (Exception ignored) { }
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        pool.shutdownNow();
        return found.get();
    }

    private static Socket connect(String host, int port, int timeoutMs) {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            return socket;
        } catch (Exception ignored) {
            try { socket.close(); } catch (Exception ignoredAgain) { }
            return null;
        }
    }

    private static String findWifiIpv4() {
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback()) continue;
                String name = network.getName().toLowerCase();
                if (!name.contains("wlan") && !name.contains("wifi")) continue;
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static String intToIp(int value) {
        return (value & 0xff) + "." + ((value >> 8) & 0xff) + "."
                + ((value >> 16) & 0xff) + "." + ((value >> 24) & 0xff);
    }
}
