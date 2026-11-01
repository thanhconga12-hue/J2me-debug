package ru.playsoftware.j2meloader;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import javax.microedition.shell.MicroActivity;

/**
 * Transparent launcher router. When a MIDlet session is still alive, the
 * ordered PROBE_SESSION handshake reopens that activity; otherwise the normal
 * app list is launched.
 */
public final class LauncherRouterActivity extends Activity {
    private static final String TAG = "NSTROUTER";
    private static final String ACTION_PROBE = "ru.playsoftware.j2meloader.PROBE_SESSION";
    private static final int RESULT_ALIVE = 1;
    private static final long PROBE_TIMEOUT_MS = 2_000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean finished;

    private final Runnable fallback = () -> {
        Log.w(TAG, "MIDlet session probe timed out; opening the app list");
        openMain();
    };
    private final BroadcastReceiver probeResult = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handler.removeCallbacksAndMessages(null);
            if (isFinishing()) return;
            if (getResultCode() == RESULT_ALIVE) {
                try {
                    Intent reopen = new Intent(LauncherRouterActivity.this, MicroActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(reopen);
                    closeRouter();
                } catch (Exception e) {
                    Log.w(TAG, "Unable to bring the active MIDlet session to the foreground", e);
                    openMain();
                }
            } else {
                openMain();
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isMidletProcessRunning()) {
            openMain();
            return;
        }
        handler.postDelayed(fallback, PROBE_TIMEOUT_MS);
        Intent probe = new Intent(ACTION_PROBE).setPackage(getPackageName());
        try {
            sendOrderedBroadcast(probe, null, probeResult, handler, 0, null, null);
        } catch (Exception e) {
            Log.w(TAG, "Unable to probe the active MIDlet session", e);
            handler.removeCallbacks(fallback);
            openMain();
        }
    }

    private boolean isMidletProcessRunning() {
        try {
            ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (manager == null) return false;
            String expected = getPackageName() + ":midlet";
            for (ActivityManager.RunningAppProcessInfo process : manager.getRunningAppProcesses()) {
                if (expected.equals(process.processName)) return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to inspect MIDlet process", e);
        }
        return false;
    }

    private void openMain() {
        if (finished || isFinishing()) return;
        try {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        } finally {
            closeRouter();
        }
    }

    private void closeRouter() {
        if (finished) return;
        finished = true;
        handler.removeCallbacksAndMessages(null);
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
