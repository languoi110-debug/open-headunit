package vn.thanhtruong.maplinksender;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.view.Surface;
import android.view.WindowManager;
import android.util.DisplayMetrics;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MirrorService extends Service {
    static final String ACTION_STATUS = "vn.thanhtruong.maplinksender.STATUS";
    static final String EXTRA_STATUS_RES = "status_res";
    static final String EXTRA_RESULT_CODE = "result_code";
    static final String EXTRA_RESULT_DATA = "result_data";
    private static final String ACTION_STOP = "vn.thanhtruong.maplinksender.STOP";
    private static final String CHANNEL_ID = "maplink_mirror";
    private static final int NOTIFICATION_ID = 6199;
    private static final int PORT = 6199;
    private static final int MAGIC = 0x4D4C4D31;
    private static final int VERSION = 1;
    private static final int FPS = 24;
    private static final int BITRATE = 1_800_000;
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private volatile Thread streamThread;
    private volatile Socket socket;
    private volatile MediaCodec encoder;
    private volatile MediaProjection projection;
    private volatile VirtualDisplay virtualDisplay;
    private volatile Surface inputSurface;
    private PowerManager.WakeLock cpuWakeLock;
    private WifiManager.WifiLock wifiLock;

    static boolean isRunning() {
        return RUNNING.get();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (RUNNING.getAndSet(true)) return START_NOT_STICKY;

        Notification notification = buildNotification(R.string.sender_connecting);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        if (intent == null) {
            fail(R.string.sender_permission_denied);
            return START_NOT_STICKY;
        }
        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, ActivityResultCodes.CANCELED);
        Intent resultData;
        if (Build.VERSION.SDK_INT >= 33) {
            resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        } else {
            resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        }
        if (resultCode != ActivityResultCodes.OK || resultData == null) {
            fail(R.string.sender_permission_denied);
            return START_NOT_STICKY;
        }

        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        projection = manager.getMediaProjection(resultCode, resultData);
        if (projection == null) {
            fail(R.string.sender_permission_denied);
            return START_NOT_STICKY;
        }
        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() { stopSelf(); }
        }, new Handler(Looper.getMainLooper()));

        acquireConnectionLocks();

        streamThread = new Thread(this::stream, "MapLinkMirrorSender");
        streamThread.start();
        return START_NOT_STICKY;
    }

    private void stream() {
        try {
            sendStatus(R.string.sender_connecting);
            updateNotification(R.string.sender_connecting);
            int[] dimensions = captureDimensions();
            int width = dimensions[0];
            int height = dimensions[1];

            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, BITRATE);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            if (Build.VERSION.SDK_INT >= 23) {
                format.setInteger(MediaFormat.KEY_PROFILE,
                        MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
                format.setInteger(MediaFormat.KEY_LEVEL,
                        MediaCodecInfo.CodecProfileLevel.AVCLevel31);
            }

            MediaCodec codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            encoder = codec;
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = codec.createInputSurface();
            codec.start();

            int density = getResources().getDisplayMetrics().densityDpi;
            virtualDisplay = projection.createVirtualDisplay(
                    "MapLinkMirror", width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    inputSurface, null, null);

            drainEncoder(codec, width, height);
        } catch (Exception ignored) {
            if (RUNNING.get()) sendStatus(R.string.sender_failed);
        } finally {
            stopSelf();
        }
    }

    private void drainEncoder(MediaCodec codec, int width, int height) throws Exception {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        DataOutputStream output = null;
        byte[] csd0 = null;
        byte[] csd1 = null;
        long nextConnectAttemptMs = 0L;
        while (RUNNING.get()) {
            if (output == null && System.currentTimeMillis() >= nextConnectAttemptMs) {
                try {
                    output = connectReceiver(width, height, csd0, csd1);
                    if (output != null) {
                        requestKeyFrame(codec);
                        sendStatus(R.string.sender_running);
                        updateNotification(R.string.sender_notification_text);
                    } else {
                        nextConnectAttemptMs = System.currentTimeMillis() + 1000L;
                    }
                } catch (Exception connectionError) {
                    closeReceiverConnection();
                    output = null;
                    nextConnectAttemptMs = System.currentTimeMillis() + 1000L;
                }
            }

            int index = codec.dequeueOutputBuffer(info, 10_000);
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) continue;
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat changed = codec.getOutputFormat();
                csd0 = copyBuffer(changed.getByteBuffer("csd-0"));
                csd1 = copyBuffer(changed.getByteBuffer("csd-1"));
                if (output != null) {
                    try {
                        sendCsd(csd0, output);
                        sendCsd(csd1, output);
                    } catch (IOException sendError) {
                        closeReceiverConnection();
                        output = null;
                        sendStatus(R.string.sender_reconnecting);
                        updateNotification(R.string.sender_reconnecting);
                    }
                }
                continue;
            }
            if (index < 0) continue;
            ByteBuffer buffer = codec.getOutputBuffer(index);
            if (output != null && buffer != null && info.size > 0) {
                boolean duplicateConfig = csd0 != null
                        && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                if (!duplicateConfig) try {
                    byte[] data = new byte[info.size];
                    buffer.position(info.offset);
                    buffer.limit(info.offset + info.size);
                    buffer.get(data);
                    sendPacket(output, data, info.presentationTimeUs, info.flags);
                } catch (IOException sendError) {
                    closeReceiverConnection();
                    output = null;
                    nextConnectAttemptMs = System.currentTimeMillis() + 750L;
                    sendStatus(R.string.sender_reconnecting);
                    updateNotification(R.string.sender_reconnecting);
                }
            }
            codec.releaseOutputBuffer(index, false);
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
        }
    }

    private byte[] copyBuffer(ByteBuffer source) {
        if (source == null || !source.hasRemaining()) return null;
        ByteBuffer copy = source.duplicate();
        byte[] data = new byte[copy.remaining()];
        copy.get(data);
        return data;
    }

    private void sendCsd(byte[] data, DataOutputStream output) throws IOException {
        if (data != null) sendPacket(output, data, 0L, MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
    }

    private DataOutputStream connectReceiver(int width, int height, byte[] csd0, byte[] csd1)
            throws Exception {
        Socket receiver = NetworkFinder.findReceiver(this, PORT);
        if (receiver == null) return null;
        socket = receiver;
        DataOutputStream output = new DataOutputStream(receiver.getOutputStream());
        output.writeInt(MAGIC);
        output.writeInt(VERSION);
        output.writeInt(width);
        output.writeInt(height);
        output.writeInt(FPS);
        sendCsd(csd0, output);
        sendCsd(csd1, output);
        output.flush();
        return output;
    }

    private void requestKeyFrame(MediaCodec codec) {
        try {
            Bundle parameters = new Bundle();
            parameters.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
            codec.setParameters(parameters);
        } catch (Exception ignored) { }
    }

    private void sendPacket(DataOutputStream output, byte[] data, long ptsUs, int flags)
            throws IOException {
        output.writeInt(data.length);
        output.writeLong(ptsUs);
        output.writeInt(flags);
        output.write(data);
        output.flush();
    }

    private int[] captureDimensions() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        int sourceWidth = Math.min(metrics.widthPixels, metrics.heightPixels);
        int sourceHeight = Math.max(metrics.widthPixels, metrics.heightPixels);
        int width = 540;
        int height = Math.round(width * (sourceHeight / (float) sourceWidth));
        // H.264 hardware codecs require even dimensions; cap for the J2 Prime decoder.
        height = Math.min(1280, (height + 1) & ~1);
        return new int[] { width, height };
    }

    private void acquireConnectionLocks() {
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        // Keep the logical and physical display active. Locking the S24 display stops Android's
        // MediaProjection stream, so this no-Shizuku build deliberately never powers it off.
        cpuWakeLock = power.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                "MapLink:KeepSenderScreenActive");
        cpuWakeLock.setReferenceCounted(false);
        cpuWakeLock.acquire();

        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifi != null) {
            wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "MapLink:KeepSenderWifiActive");
            wifiLock.setReferenceCounted(false);
            wifiLock.acquire();
        }
    }

    private void releaseConnectionLocks() {
        try {
            if (cpuWakeLock != null && cpuWakeLock.isHeld()) cpuWakeLock.release();
        } catch (Exception ignored) { }
        try {
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        } catch (Exception ignored) { }
        cpuWakeLock = null;
        wifiLock = null;
    }

    private void closeReceiverConnection() {
        Socket current = socket;
        socket = null;
        try { if (current != null) current.close(); } catch (Exception ignored) { }
    }

    private void fail(int message) {
        sendStatus(message);
        updateNotification(message);
        new Handler(Looper.getMainLooper()).postDelayed(this::stopSelf, 1800);
    }

    private void sendStatus(int message) {
        Intent status = new Intent(ACTION_STATUS)
                .setPackage(getPackageName())
                .putExtra(EXTRA_STATUS_RES, message);
        sendBroadcast(status);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "MapLink Mirror", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Trạng thái truyền màn hình sang J2 Prime");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification(int textRes) {
        Intent open = new Intent(this, SenderActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, MirrorService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 2, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setContentTitle(getString(R.string.sender_notification_title))
                .setContentText(getString(textRes))
                .setContentIntent(openPending)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        null, getString(R.string.sender_notification_stop), stopPending).build())
                .build();
    }

    private void updateNotification(int textRes) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, buildNotification(textRes));
    }

    @Override
    public void onDestroy() {
        RUNNING.set(false);
        releaseConnectionLocks();
        closeReceiverConnection();
        try { if (virtualDisplay != null) virtualDisplay.release(); } catch (Exception ignored) { }
        try { if (inputSurface != null) inputSurface.release(); } catch (Exception ignored) { }
        if (encoder != null) {
            try { encoder.stop(); } catch (Exception ignored) { }
            try { encoder.release(); } catch (Exception ignored) { }
        }
        try { if (projection != null) projection.stop(); } catch (Exception ignored) { }
        socket = null;
        virtualDisplay = null;
        inputSurface = null;
        encoder = null;
        projection = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private static final class ActivityResultCodes {
        static final int OK = -1;
        static final int CANCELED = 0;
    }
}
