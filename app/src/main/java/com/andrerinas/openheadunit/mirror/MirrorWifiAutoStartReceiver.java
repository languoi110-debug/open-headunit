package com.andrerinas.openheadunit.mirror;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.SystemClock;

/** Opens the dedicated mirror screen when the J2 Prime joins Wi-Fi. */
public final class MirrorWifiAutoStartReceiver extends BroadcastReceiver {
    private static long lastLaunchElapsed;

    @Override
    public void onReceive(Context context, Intent intent) {
        ConnectivityManager manager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifi = manager == null
                ? null : manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (wifi == null || !wifi.isConnected()) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastLaunchElapsed < 10_000L) return;
        lastLaunchElapsed = now;

        Intent open = new Intent(context, MirrorReceiverActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(open);
    }
}
