package com.andrerinas.openheadunit.mirror;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.andrerinas.openheadunit.R;

import java.io.DataInputStream;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Collections;

/** Receives the MapLink Sender H.264 stream and renders it directly to a Surface. */
public final class MirrorReceiverActivity extends Activity implements SurfaceHolder.Callback {
    private static final int PORT = 6199;
    private static final int MAGIC = 0x4D4C4D31; // MLM1
    private static final int VERSION = 1;
    private static final int MAX_PACKET = 2 * 1024 * 1024;

    private volatile boolean running;
    private volatile ServerSocket serverSocket;
    private volatile Socket clientSocket;
    private volatile MediaCodec decoder;
    private Surface surface;
    private TextView statusView;
    private View statusPanel;
    private View exitButton;
    private static volatile boolean activityVisible;
    private static volatile MirrorReceiverActivity activeInstance;

    static boolean isActivityVisible() {
        return activityVisible;
    }

    static void finishActiveInstance() {
        MirrorReceiverActivity activity = activeInstance;
        if (activity != null) activity.runOnUiThread(activity::finish);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();
        setContentView(R.layout.activity_mirror_receiver);
        MirrorWatchdogService.startMode(this);

        statusView = findViewById(R.id.mirror_status);
        statusPanel = findViewById(R.id.mirror_status_panel);
        TextView ipView = findViewById(R.id.mirror_ip);
        ipView.setText(getString(R.string.mirror_ip, findLocalIpv4()));
        exitButton = findViewById(R.id.mirror_exit);
        exitButton.setOnClickListener(v -> {
            MirrorWatchdogService.stopMode(this);
            finish();
        });
        SurfaceView surfaceView = findViewById(R.id.mirror_surface);
        surfaceView.getHolder().addCallback(this);
        surfaceView.setOnClickListener(v -> {
            boolean show = statusPanel.getVisibility() != View.VISIBLE;
            statusPanel.setVisibility(show ? View.VISIBLE : View.GONE);
            exitButton.setVisibility(show ? View.VISIBLE : View.GONE);
            if (!show) hideSystemUi();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        activityVisible = true;
        activeInstance = this;
    }

    @Override
    protected void onStop() {
        activityVisible = false;
        if (activeInstance == this) activeInstance = null;
        super.onStop();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surface = holder.getSurface();
        running = true;
        new Thread(this::serve, "MapLinkMirrorReceiver").start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        surface = holder.getSurface();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopReceiver();
        surface = null;
    }

    @Override
    protected void onDestroy() {
        stopReceiver();
        super.onDestroy();
    }

    private void serve() {
        try {
            ServerSocket server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(PORT));
            serverSocket = server;
            while (running) {
                setStatus(R.string.mirror_waiting, true);
                Socket socket = server.accept();
                if (!running) break;
                clientSocket = socket;
                socket.setTcpNoDelay(true);
                socket.setKeepAlive(true);
                try {
                    receiveStream(socket);
                } catch (Exception ignored) {
                    setStatus(R.string.mirror_disconnected, true);
                } finally {
                    closeClient();
                    releaseDecoder();
                }
            }
        } catch (Exception ignored) {
            if (running) setStatus(R.string.mirror_disconnected, true);
        } finally {
            closeServer();
        }
    }

    private void receiveStream(Socket socket) throws Exception {
        DataInputStream input = new DataInputStream(socket.getInputStream());
        if (input.readInt() != MAGIC || input.readInt() != VERSION) {
            throw new IllegalArgumentException("Unsupported MapLink stream");
        }
        int width = input.readInt();
        int height = input.readInt();
        int fps = input.readInt();
        if (width < 160 || height < 160 || width > 1920 || height > 1920 || fps > 60) {
            throw new IllegalArgumentException("Invalid stream dimensions");
        }

        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_PACKET);
        MediaCodec codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        decoder = codec;
        codec.configure(format, surface, null, 0);
        codec.start();
        codec.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
        setStatus(R.string.mirror_connected, false);

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (running && !socket.isClosed()) {
            int length = input.readInt();
            long ptsUs = input.readLong();
            int flags = input.readInt();
            if (length <= 0 || length > MAX_PACKET) throw new IllegalArgumentException("Invalid packet");
            byte[] data = new byte[length];
            input.readFully(data);

            int inputIndex = -1;
            while (running && (inputIndex = codec.dequeueInputBuffer(10_000)) < 0) {
                drain(codec, info);
            }
            if (!running) break;
            ByteBuffer buffer = codec.getInputBuffer(inputIndex);
            if (buffer == null || buffer.capacity() < length) continue;
            buffer.clear();
            buffer.put(data);
            codec.queueInputBuffer(inputIndex, 0, length, ptsUs, flags);
            drain(codec, info);
        }
    }

    private void drain(MediaCodec codec, MediaCodec.BufferInfo info) {
        while (true) {
            int outputIndex = codec.dequeueOutputBuffer(info, 0);
            if (outputIndex >= 0) {
                codec.releaseOutputBuffer(outputIndex, true);
            } else if (outputIndex != MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED
                    && outputIndex != MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                return;
            }
        }
    }

    private void setStatus(int textRes, boolean showPanel) {
        runOnUiThread(() -> {
            statusView.setText(textRes);
            statusPanel.setVisibility(showPanel ? View.VISIBLE : View.GONE);
            exitButton.setVisibility(showPanel ? View.VISIBLE : View.GONE);
            if (!showPanel) hideSystemUi();
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private String findLocalIpv4() {
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback()) continue;
                for (java.net.InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) { }
        return "tự động tìm";
    }

    private void stopReceiver() {
        running = false;
        closeClient();
        closeServer();
        releaseDecoder();
    }

    private void closeClient() {
        try { if (clientSocket != null) clientSocket.close(); } catch (Exception ignored) { }
        clientSocket = null;
    }

    private void closeServer() {
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) { }
        serverSocket = null;
    }

    private void releaseDecoder() {
        MediaCodec codec = decoder;
        decoder = null;
        if (codec != null) {
            try { codec.stop(); } catch (Exception ignored) { }
            try { codec.release(); } catch (Exception ignored) { }
        }
    }
}
