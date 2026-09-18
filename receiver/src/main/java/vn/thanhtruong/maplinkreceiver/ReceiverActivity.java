package vn.thanhtruong.maplinkreceiver;

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

import java.io.DataInputStream;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Collections;

public final class ReceiverActivity extends Activity implements SurfaceHolder.Callback {
    private static final int PORT = 6199;
    private static final int MAGIC = 0x4D4C4D31;
    private static final int VERSION = 1;
    private static final int MAX_PACKET = 2 * 1024 * 1024;
    private static volatile ReceiverActivity activeInstance;
    private static volatile boolean visible;

    private volatile boolean running;
    private volatile ServerSocket serverSocket;
    private volatile Socket clientSocket;
    private volatile MediaCodec decoder;
    private Surface surface;
    private TextView status;
    private View statusPanel;
    private View exit;

    static boolean isVisible() { return visible; }

    static void finishActive() {
        ReceiverActivity activity = activeInstance;
        if (activity != null) activity.runOnUiThread(activity::finish);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();
        setContentView(R.layout.activity_receiver);
        KeepAliveService.startMode(this);

        status = findViewById(R.id.status);
        statusPanel = findViewById(R.id.status_panel);
        exit = findViewById(R.id.exit);
        ((TextView) findViewById(R.id.ip_address)).setText(
                getString(R.string.ip_address, findLocalIpv4()));
        exit.setOnClickListener(v -> {
            KeepAliveService.stopMode(this);
            finish();
        });
        SurfaceView surfaceView = findViewById(R.id.video_surface);
        surfaceView.getHolder().addCallback(this);
        surfaceView.setOnClickListener(v -> {
            boolean show = statusPanel.getVisibility() != View.VISIBLE;
            statusPanel.setVisibility(show ? View.VISIBLE : View.GONE);
            exit.setVisibility(show ? View.VISIBLE : View.GONE);
            if (!show) hideSystemUi();
        });
    }

    @Override protected void onStart() {
        super.onStart();
        visible = true;
        activeInstance = this;
    }

    @Override protected void onStop() {
        visible = false;
        if (activeInstance == this) activeInstance = null;
        super.onStop();
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        surface = holder.getSurface();
        running = true;
        new Thread(this::serve, "MapLinkJ2Receiver").start();
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        surface = holder.getSurface();
    }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        stopReceiver();
        surface = null;
    }

    @Override protected void onDestroy() {
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
                setStatus(R.string.waiting, true);
                Socket socket = server.accept();
                if (!running) break;
                clientSocket = socket;
                socket.setTcpNoDelay(true);
                socket.setKeepAlive(true);
                try {
                    receiveStream(socket);
                } catch (Exception ignored) {
                    setStatus(R.string.disconnected, true);
                } finally {
                    closeClient();
                    releaseDecoder();
                }
            }
        } catch (Exception ignored) {
            if (running) setStatus(R.string.disconnected, true);
        } finally {
            closeServer();
        }
    }

    private void receiveStream(Socket socket) throws Exception {
        DataInputStream input = new DataInputStream(socket.getInputStream());
        if (input.readInt() != MAGIC || input.readInt() != VERSION) {
            throw new IllegalArgumentException("Unsupported stream");
        }
        int width = input.readInt();
        int height = input.readInt();
        int fps = input.readInt();
        if (width < 160 || height < 160 || width > 1920 || height > 1920 || fps > 60) {
            throw new IllegalArgumentException("Invalid dimensions");
        }

        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,
                width, height);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_PACKET);
        MediaCodec codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        decoder = codec;
        codec.configure(format, surface, null, 0);
        codec.start();
        codec.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
        setStatus(R.string.connected, false);

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (running && !socket.isClosed()) {
            int length = input.readInt();
            long ptsUs = input.readLong();
            int flags = input.readInt();
            if (length <= 0 || length > MAX_PACKET) throw new IllegalArgumentException();
            byte[] data = new byte[length];
            input.readFully(data);
            int index = -1;
            while (running && (index = codec.dequeueInputBuffer(10_000)) < 0) drain(codec, info);
            if (!running) break;
            ByteBuffer buffer = codec.getInputBuffer(index);
            if (buffer == null || buffer.capacity() < length) continue;
            buffer.clear();
            buffer.put(data);
            codec.queueInputBuffer(index, 0, length, ptsUs, flags);
            drain(codec, info);
        }
    }

    private void drain(MediaCodec codec, MediaCodec.BufferInfo info) {
        while (true) {
            int index = codec.dequeueOutputBuffer(info, 0);
            if (index >= 0) codec.releaseOutputBuffer(index, true);
            else if (index != MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED
                    && index != MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) return;
        }
    }

    private void setStatus(int text, boolean show) {
        runOnUiThread(() -> {
            status.setText(text);
            statusPanel.setVisibility(show ? View.VISIBLE : View.GONE);
            exit.setVisibility(show ? View.VISIBLE : View.GONE);
            if (!show) hideSystemUi();
        });
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LOW_PROFILE | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) hideSystemUi();
    }

    private String findLocalIpv4() {
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback()) continue;
                for (java.net.InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress())
                        return address.getHostAddress();
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
