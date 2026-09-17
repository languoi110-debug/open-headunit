package vn.thanhtruong.maplinksender;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.util.Log;

import rikka.shizuku.Shizuku;

final class ShizukuShell {
    static final int REQUEST_PERMISSION = 93;
    private static final String TAG = "MapLinkShizuku";
    private static volatile IPrivilegedShell service;
    private static volatile boolean binding;
    private static Shizuku.UserServiceArgs userServiceArgs;

    private ShizukuShell() { }

    static boolean isRunning() {
        try {
            return Shizuku.pingBinder() && !Shizuku.isPreV11();
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean hasPermission() {
        try {
            return isRunning()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static void requestPermission() {
        Shizuku.requestPermission(REQUEST_PERMISSION);
    }

    static synchronized void bind(Context context) {
        if (!hasPermission() || binding || service != null) return;
        binding = true;
        userServiceArgs = new Shizuku.UserServiceArgs(
                new ComponentName(context.getPackageName(), PrivilegedService.class.getName()))
                .daemon(false)
                .processNameSuffix("display_control");
        try {
            Shizuku.bindUserService(userServiceArgs, CONNECTION);
        } catch (Throwable error) {
            binding = false;
            Log.e(TAG, "Cannot bind Shizuku user service", error);
        }
    }

    static void executeAsync(Context context, String command) {
        bind(context.getApplicationContext());
        new Thread(() -> {
            for (int attempt = 0; attempt < 50; attempt++) {
                IPrivilegedShell current = service;
                if (current != null) {
                    try {
                        int result = current.exec(command);
                        Log.i(TAG, command + " returned " + result);
                    } catch (Exception error) {
                        Log.e(TAG, "Command failed: " + command, error);
                    }
                    return;
                }
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            Log.e(TAG, "Shizuku user service did not connect");
        }, "MapLinkDisplayCommand").start();
    }

    public static final class PrivilegedService extends IPrivilegedShell.Stub {
        public PrivilegedService() { }

        @Override
        public int exec(String command) {
            try {
                Process process = Runtime.getRuntime().exec(new String[] { "sh", "-c", command });
                int result = process.waitFor();
                if (result != 0) {
                    Log.e(TAG, "Shell error: "
                            + new java.io.BufferedReader(new java.io.InputStreamReader(
                            process.getErrorStream())).readLine());
                }
                return result;
            } catch (Exception error) {
                Log.e(TAG, "Privileged shell failed", error);
                return -1;
            }
        }
    }

    private static final ServiceConnection CONNECTION = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = IPrivilegedShell.Stub.asInterface(binder);
            binding = false;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            binding = false;
        }
    };
}
