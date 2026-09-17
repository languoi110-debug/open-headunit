package vn.thanhtruong.maplinksender;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.net.Uri;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

public final class SenderActivity extends Activity {
    private static final int REQUEST_CAPTURE = 91;
    private static final int REQUEST_WRITE_SETTINGS = 92;
    private TextView status;
    private Button action;
    private boolean continueAfterShizukuPermission;

    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionListener =
            (requestCode, grantResult) -> {
                if (requestCode != ShizukuShell.REQUEST_PERMISSION) return;
                if (grantResult == getPackageManager().PERMISSION_GRANTED) {
                    ShizukuShell.bind(getApplicationContext());
                    if (continueAfterShizukuPermission) {
                        continueAfterShizukuPermission = false;
                        requestBatteryExemptionThenCapture();
                    }
                } else {
                    continueAfterShizukuPermission = false;
                    status.setText(R.string.sender_shizuku_denied);
                }
            };

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!MirrorService.ACTION_STATUS.equals(intent.getAction())) return;
            int message = intent.getIntExtra(MirrorService.EXTRA_STATUS_RES, R.string.sender_ready);
            status.setText(message);
            updateButton();
            if (message == R.string.sender_running) openGoogleMaps();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sender);
        status = findViewById(R.id.sender_status);
        action = findViewById(R.id.sender_action);
        action.setOnClickListener(v -> {
            if (MirrorService.isRunning()) {
                stopService(new Intent(this, MirrorService.class));
                status.setText(R.string.sender_ready);
                updateButton();
            } else {
                requestDimPermissionThenCapture();
            }
        });
        updateButton();
        try { Shizuku.addRequestPermissionResultListener(shizukuPermissionListener); }
        catch (Throwable ignored) { }
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(MirrorService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(statusReceiver, filter);
        }
        updateButton();
    }

    @Override
    protected void onStop() {
        try { unregisterReceiver(statusReceiver); } catch (Exception ignored) { }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener); }
        catch (Throwable ignored) { }
        super.onDestroy();
    }

    private void requestScreenCapture() {
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE);
    }

    private void requestDimPermissionThenCapture() {
        if (!ShizukuShell.isRunning()) {
            status.setText(R.string.sender_shizuku_not_running);
            Toast.makeText(this, R.string.sender_shizuku_not_running, Toast.LENGTH_LONG).show();
            return;
        }
        if (!ShizukuShell.hasPermission()) {
            continueAfterShizukuPermission = true;
            status.setText(R.string.sender_shizuku_waiting);
            ShizukuShell.requestPermission();
            return;
        }
        ShizukuShell.bind(getApplicationContext());
        requestBatteryExemptionThenCapture();
    }

    private void requestBatteryExemptionThenCapture() {
        if (Build.VERSION.SDK_INT >= 23) {
            PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
            if (!power.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent permission = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + getPackageName()));
                    startActivityForResult(permission, REQUEST_WRITE_SETTINGS);
                    return;
                } catch (Exception ignored) { }
            }
        }
        requestScreenCapture();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_WRITE_SETTINGS) {
            requestScreenCapture();
            return;
        }
        if (requestCode != REQUEST_CAPTURE) return;
        if (resultCode != RESULT_OK || data == null) {
            status.setText(R.string.sender_permission_denied);
            return;
        }
        Intent service = new Intent(this, MirrorService.class)
                .putExtra(MirrorService.EXTRA_RESULT_CODE, resultCode)
                .putExtra(MirrorService.EXTRA_RESULT_DATA, data);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
        status.setText(R.string.sender_connecting);
        updateButton();
        // Opening Maps here avoids a race where the service connects while the capture consent
        // screen still has this activity stopped and its status receiver is not registered.
        new Handler(Looper.getMainLooper()).postDelayed(this::openGoogleMaps, 1200L);
    }

    private void updateButton() {
        action.setText(MirrorService.isRunning() ? R.string.sender_stop : R.string.sender_start);
    }

    private void openGoogleMaps() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("com.google.android.apps.maps");
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
        } else {
            Toast.makeText(this, "Không tìm thấy Google Maps", Toast.LENGTH_LONG).show();
        }
    }
}
