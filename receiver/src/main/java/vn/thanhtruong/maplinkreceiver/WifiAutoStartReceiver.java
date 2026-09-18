package vn.thanhtruong.maplinkreceiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.SystemClock;

public final class WifiAutoStartReceiver extends BroadcastReceiver {
    private static long lastLaunch;

    @Override public void onReceive(Context context, Intent intent) {
        ConnectivityManager manager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifi = manager == null ? null
                : manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (wifi == null || !wifi.isConnected()) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastLaunch < 10_000L) return;
        lastLaunch = now;
        context.startActivity(new Intent(context, ReceiverActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }
}
