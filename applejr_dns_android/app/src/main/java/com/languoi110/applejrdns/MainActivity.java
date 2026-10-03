package com.languoi110.applejrdns;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQUEST_VPN = 1001;

    private SharedPreferences prefs;
    private EditText endpointInput;
    private RadioButton globalMode;
    private RadioButton appleMode;
    private TextView statusText;
    private Button toggleButton;
    private Button testButton;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(DnsVpnService.PREFS, MODE_PRIVATE);
        setContentView(buildUi());
        loadPreferences();
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(32));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("AppleJr DNS Android");
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Bản Android không chính thức • DNS-over-HTTPS qua VPN cục bộ");
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(6), 0, dp(18));
        root.addView(subtitle);

        statusText = new TextView(this);
        statusText.setTextSize(16);
        statusText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusText.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(statusText, fullWidth());

        TextView resolverLabel = sectionLabel("DoH resolver");
        resolverLabel.setPadding(0, dp(22), 0, dp(8));
        root.addView(resolverLabel);

        endpointInput = new EditText(this);
        endpointInput.setSingleLine(true);
        endpointInput.setTextSize(15);
        endpointInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        endpointInput.setHint("https://.../dns-query");
        root.addView(endpointInput, fullWidth());

        TextView modeLabel = sectionLabel("Chế độ");
        modeLabel.setPadding(0, dp(22), 0, dp(8));
        root.addView(modeLabel);

        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);

        globalMode = new RadioButton(this);
        globalMode.setText("Toàn bộ DNS qua NovaDNS (khuyên dùng trên Android)");
        globalMode.setTextSize(15);
        group.addView(globalMode);

        appleMode = new RadioButton(this);
        appleMode.setText("Chỉ domain AppleJr (gần giống profile iOS gốc)");
        appleMode.setTextSize(15);
        group.addView(appleMode);

        root.addView(group);

        toggleButton = new Button(this);
        toggleButton.setAllCaps(false);
        toggleButton.setTextSize(17);
        toggleButton.setMinHeight(dp(52));
        toggleButton.setOnClickListener(v -> onToggleClicked());
        LinearLayout.LayoutParams buttonParams = fullWidth();
        buttonParams.setMargins(0, dp(24), 0, 0);
        root.addView(toggleButton, buttonParams);

        testButton = new Button(this);
        testButton.setAllCaps(false);
        testButton.setText("Kiểm tra NovaDNS");
        testButton.setOnClickListener(v -> testResolver());
        LinearLayout.LayoutParams testParams = fullWidth();
        testParams.setMargins(0, dp(10), 0, 0);
        root.addView(testButton, testParams);

        TextView note = new TextView(this);
        note.setText(
                "Lưu ý: Android không có ESign/IPA hay cơ chế thu hồi chứng chỉ enterprise của Apple. " +
                "Ứng dụng này chỉ tái tạo phần DNS-over-HTTPS của profile AppleJr. " +
                "NovaDNS là resolver bên thứ ba và có thể nhìn thấy các truy vấn DNS gửi qua nó. " +
                "Không bật đồng thời với một ứng dụng VPN khác."
        );
        note.setTextSize(13);
        note.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams noteParams = fullWidth();
        noteParams.setMargins(0, dp(22), 0, 0);
        root.addView(note, noteParams);

        TextView tech = new TextView(this);
        tech.setText(
                "Chế độ AppleJr chỉ chuyển các tên miền trong mobileconfig gốc (OCSP, PPQ, MESU, certs...) " +
                "qua NovaDNS; truy vấn khác dùng DNS của Wi-Fi/4G hiện tại. Nếu DNS mạng không đọc được, " +
                "ứng dụng dùng 1.1.1.1 làm phương án cho các truy vấn không thuộc AppleJr."
        );
        tech.setTextSize(12);
        tech.setPadding(0, dp(14), 0, 0);
        root.addView(tech);

        return scroll;
    }

    private TextView sectionLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(16);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return label;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private void loadPreferences() {
        endpointInput.setText(prefs.getString(
                DnsVpnService.KEY_ENDPOINT,
                DnsVpnService.DEFAULT_ENDPOINT
        ));
        String mode = prefs.getString(DnsVpnService.KEY_MODE, DnsVpnService.MODE_GLOBAL);
        if (DnsVpnService.MODE_APPLE_ONLY.equals(mode)) {
            appleMode.setChecked(true);
        } else {
            globalMode.setChecked(true);
        }
    }

    private boolean savePreferences() {
        String endpoint = endpointInput.getText().toString().trim();
        if (!endpoint.startsWith("https://")) {
            Toast.makeText(this, "DoH URL phải bắt đầu bằng https://", Toast.LENGTH_LONG).show();
            return false;
        }
        String mode = appleMode.isChecked()
                ? DnsVpnService.MODE_APPLE_ONLY
                : DnsVpnService.MODE_GLOBAL;

        prefs.edit()
                .putString(DnsVpnService.KEY_ENDPOINT, endpoint)
                .putString(DnsVpnService.KEY_MODE, mode)
                .remove(DnsVpnService.KEY_LAST_ERROR)
                .apply();
        return true;
    }

    private void onToggleClicked() {
        if (prefs.getBoolean(DnsVpnService.KEY_RUNNING, false)) {
            Intent stop = new Intent(this, DnsVpnService.class);
            stop.setAction(DnsVpnService.ACTION_STOP);
            startService(stop);
            mainHandler.postDelayed(this::refreshStatus, 500);
            return;
        }

        if (!savePreferences()) return;

        Intent permission = VpnService.prepare(this);
        if (permission != null) {
            startActivityForResult(permission, REQUEST_VPN);
        } else {
            startDnsService();
        }
    }

    private void startDnsService() {
        Intent start = new Intent(this, DnsVpnService.class);
        start.setAction(DnsVpnService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(start);
        } else {
            startService(start);
        }
        statusText.setText("Trạng thái: đang khởi động…");
        toggleButton.setEnabled(false);
        mainHandler.postDelayed(() -> {
            toggleButton.setEnabled(true);
            refreshStatus();
        }, 1200);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VPN) {
            if (resultCode == RESULT_OK) {
                startDnsService();
            } else {
                Toast.makeText(this, "Bạn chưa cấp quyền VPN DNS.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void refreshStatus() {
        boolean running = prefs.getBoolean(DnsVpnService.KEY_RUNNING, false);
        String error = prefs.getString(DnsVpnService.KEY_LAST_ERROR, "");

        if (running) {
            statusText.setText("Trạng thái: ĐANG BẬT");
            toggleButton.setText("Tắt DNS");
            endpointInput.setEnabled(false);
            globalMode.setEnabled(false);
            appleMode.setEnabled(false);
        } else {
            statusText.setText(
                    error == null || error.isEmpty()
                            ? "Trạng thái: ĐANG TẮT"
                            : "Trạng thái: LỖI — " + error
            );
            toggleButton.setText("Bật DNS");
            endpointInput.setEnabled(true);
            globalMode.setEnabled(true);
            appleMode.setEnabled(true);
        }
    }

    private void testResolver() {
        if (!savePreferences()) return;
        final String endpoint = endpointInput.getText().toString().trim();
        testButton.setEnabled(false);
        testButton.setText("Đang kiểm tra…");

        new Thread(() -> {
            String message;
            try {
                byte[] query = DnsWire.buildAQuery("example.com");
                byte[] response = DnsVpnService.queryDoh(endpoint, query);
                if (DnsWire.isDnsResponse(response)) {
                    message = "NovaDNS phản hồi hợp lệ (" + response.length + " byte).";
                } else {
                    message = "Có phản hồi nhưng không phải DNS response hợp lệ.";
                }
            } catch (Throwable t) {
                String detail = t.getMessage();
                message = "Kiểm tra thất bại: " +
                        (detail == null || detail.isEmpty() ? t.getClass().getSimpleName() : detail);
            }

            final String result = message;
            runOnUiThread(() -> {
                testButton.setEnabled(true);
                testButton.setText("Kiểm tra NovaDNS");
                Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
            });
        }, "AppleJrDnsTest").start();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
