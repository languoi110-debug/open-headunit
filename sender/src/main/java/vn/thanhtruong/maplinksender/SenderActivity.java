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
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public final class SenderActivity extends Activity {
    private static final int REQUEST_CAPTURE = 91;
    private TextView status;
    private Button action;

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
                requestScreenCapture();
            }
        });
        updateButton();
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

    private void requestScreenCapture() {
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
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
