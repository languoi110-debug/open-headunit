package com.andrerinas.openheadunit.mirror;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import com.andrerinas.openheadunit.R;

/** Keeps the dedicated J2 receiver alive and restores its Activity after a system kill. */
public final class MirrorWatchdogService extends Service {
    private static final String ACTION_STOP =
            "com.andrerinas.openheadunit.mirror.STOP_WATCHDOG";
    private static final String PREFS = "maplink_receiver";
    private static final String KEY_ENABLED = "watchdog_enabled";
    private static final String CHANNEL = "maplink_receiver_watchdog";
    private static final int NOTIFICATION_ID = 6200;
    private static final long CHECK_INTERVAL_MS = 4000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock cpuLock;
    private WifiManager.WifiLock wifiLock;

    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            if (!isModeEnabled()) {
                stopSelf();
                return;
            }
            if (!MirrorReceiverActivity.isActivityVisible() && isWifiConnected()) {
                Intent open = new Intent(MirrorWatchdogService.this,
                        MirrorReceiverActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                try {
                    startActivity(open);
                } catch (Exception ignored) { }
            }
            handler.postDelayed(this, CHECK_INTERVAL_MS);
        }
    };

    public static void startMode(Context context) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, true).apply();
        Intent service = new Intent(context, MirrorWatchdogService.class);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
        else context.startService(service);
    }

    public static void stopMode(Context context) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, false).apply();
        context.stopService(new Intent(context, MirrorWatchdogService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        acquireLocks();
        handler.post(watchdog);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit().putBoolean(KEY_ENABLED, false).apply();
            MirrorReceiverActivity.finishActiveInstance();
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIFICATION_ID, buildNotification());
        return START_STICKY;
    }

    private boolean isModeEnabled() {
        return getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }

    @SuppressWarnings("deprecation")
    private boolean isWifiConnected() {
        ConnectivityManager manager =
                (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo wifi = manager == null
                ? null : manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return wifi != null && wifi.isConnected();
    }

    private void acquireLocks() {
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        cpuLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MapLink:KeepJ2ReceiverActive");
        cpuLock.setReferenceCounted(false);
        cpuLock.acquire();

        WifiManager wifi = (WifiManager) getApplicationContext()
                .getSystemService(WIFI_SERVICE);
        if (wifi != null) {
            wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "MapLink:KeepJ2WifiActive");
            wifiLock.setReferenceCounted(false);
            wifiLock.acquire();
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MirrorReceiverActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(this, 10, open,
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
        Intent stop = new Intent(this, MirrorWatchdogService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 11, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle(getString(R.string.mirror_watchdog_title))
                .setContentText(getString(R.string.mirror_watchdog_text))
                .setContentIntent(openPending)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null,
                        getString(R.string.mirror_watchdog_stop), stopPending).build())
                .build();
    }

    private int immutableFlag() {
        return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL,
                "MapLink J2", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try { if (cpuLock != null && cpuLock.isHeld()) cpuLock.release(); }
        catch (Exception ignored) { }
        try { if (wifiLock != null && wifiLock.isHeld()) wifiLock.release(); }
        catch (Exception ignored) { }
        cpuLock = null;
        wifiLock = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
