package vn.thanhtruong.maplinkreceiver;

import android.app.Notification;
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

public final class KeepAliveService extends Service {
    private static final String ACTION_STOP = "vn.thanhtruong.maplinkreceiver.STOP";
    private static final String PREFS = "receiver";
    private static final String ENABLED = "enabled";
    private static final int NOTIFICATION_ID = 6199;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock cpuLock;
    private WifiManager.WifiLock wifiLock;

    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            if (!enabled()) { stopSelf(); return; }
            if (!ReceiverActivity.isVisible() && wifiConnected()) {
                try {
                    startActivity(new Intent(KeepAliveService.this, ReceiverActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    | Intent.FLAG_ACTIVITY_CLEAR_TOP));
                } catch (Exception ignored) { }
            }
            handler.postDelayed(this, 4000L);
        }
    };

    static void startMode(Context context) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(ENABLED, true).apply();
        Intent intent = new Intent(context, KeepAliveService.class);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }

    static void stopMode(Context context) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(ENABLED, false).apply();
        context.stopService(new Intent(context, KeepAliveService.class));
    }

    @Override public void onCreate() {
        super.onCreate();
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        cpuLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MapLink:J2Cpu");
        cpuLock.setReferenceCounted(false);
        cpuLock.acquire();
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifi != null) {
            wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MapLink:J2Wifi");
            wifiLock.setReferenceCounted(false);
            wifiLock.acquire();
        }
        handler.post(watchdog);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(ENABLED, false).apply();
            ReceiverActivity.finishActive();
            stopSelf();
            return START_NOT_STICKY;
        }
        Intent open = new Intent(this, ReceiverActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle(getString(R.string.keep_alive_title))
                .setContentText(getString(R.string.keep_alive_text))
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATION_ID, notification);
        return START_STICKY;
    }

    private boolean enabled() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(ENABLED, false);
    }
    @SuppressWarnings("deprecation") private boolean wifiConnected() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo wifi = manager == null ? null
                : manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return wifi != null && wifi.isConnected();
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try { if (cpuLock != null && cpuLock.isHeld()) cpuLock.release(); } catch (Exception ignored) { }
        try { if (wifiLock != null && wifiLock.isHeld()) wifiLock.release(); } catch (Exception ignored) { }
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
