package ru.playsoftware.j2meloader.keepalive;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

/**
 * Lifecycle coordinator for the original NST Treo Online foreground service.
 * State values mirror the original controller: stopped, starting, running,
 * stopping and failed.
 */
public final class TreoController {
    private static final String TAG = "NSTTREO";
    private static final int STOPPED = 1;
    private static final int STARTING = 2;
    private static final int RUNNING = 3;
    private static final int STOPPING = 4;
    private static final int FAILED = 5;
    private static final long START_TIMEOUT_MS = 5_000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onResult(boolean success);
    }

    private static volatile int state = STOPPED;
    private static volatile int currentMode = TreoOnlineService.MODE_LIGHT;
    private static Callback callback;

    private static final Runnable START_TIMEOUT = () -> {
        Callback pending;
        synchronized (TreoController.class) {
            if (state != STARTING) return;
            state = FAILED;
            pending = callback;
            callback = null;
        }
        Log.w(TAG, "Treo service did not confirm startup within 5 seconds");
        if (pending != null) pending.onResult(false);
    };

    private TreoController() {
    }

    public static synchronized boolean isRunning() {
        return state == RUNNING;
    }

    public static synchronized boolean shouldKeepMidletAlive() {
        return state == STARTING || state == RUNNING;
    }

    public static synchronized int getState() {
        return state;
    }

    public static synchronized int getCurrentMode() {
        return currentMode;
    }

    public static boolean isStickyEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext())
                .getBoolean("pref_treo_sticky_on", true);
    }

    public static void setStickyEnabled(Context context, boolean enabled) {
        PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext())
                .edit().putBoolean("pref_treo_sticky_on", enabled).apply();
    }

    public static void start(Context context, Callback resultCallback) {
        Context app = context.getApplicationContext();
        synchronized (TreoController.class) {
            if (state == RUNNING) {
                if (resultCallback != null) resultCallback.onResult(true);
                return;
            }
            if (state == STARTING) {
                callback = resultCallback;
                return;
            }
            state = STARTING;
            callback = resultCallback;
        }

        Intent serviceIntent = new Intent(app, TreoOnlineService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(app, serviceIntent);
            } else {
                app.startService(serviceIntent);
            }
            MAIN.removeCallbacks(START_TIMEOUT);
            MAIN.postDelayed(START_TIMEOUT, START_TIMEOUT_MS);
        } catch (Exception e) {
            Log.w(TAG, "Unable to start Treo foreground service", e);
            onServiceFailed();
        }
    }

    static void onServiceStarted(int mode) {
        Callback pending;
        synchronized (TreoController.class) {
            currentMode = mode;
            state = RUNNING;
            MAIN.removeCallbacks(START_TIMEOUT);
            pending = callback;
            callback = null;
        }
        try {
            javax.microedition.util.ContextHolder.getAppContext();
            ru.playsoftware.j2meloader.bubble.BubbleController.show(
                    javax.microedition.util.ContextHolder.getAppContext());
            ru.playsoftware.j2meloader.bubble.BubbleController.updateMode(
                    javax.microedition.util.ContextHolder.getAppContext(), modeLabel(mode));
        } catch (Exception e) {
            Log.w(TAG, "Unable to start/update NST bubble", e);
        }
        if (pending != null) pending.onResult(true);
    }

    private static String modeLabel(int mode) {
        if (mode == TreoOnlineService.MODE_WIFI) return "W";
        if (mode == TreoOnlineService.MODE_STRONG) return "S";
        return "L";
    }

    static void onServiceFailed() {
        Callback pending;
        synchronized (TreoController.class) {
            MAIN.removeCallbacks(START_TIMEOUT);
            state = FAILED;
            pending = callback;
            callback = null;
        }
        if (pending != null) pending.onResult(false);
    }

    static synchronized void onServiceStopped() {
        MAIN.removeCallbacks(START_TIMEOUT);
        state = STOPPED;
        callback = null;
    }

    public static void stop(Context context) {
        Context app = context.getApplicationContext();
        HideStrategy.reset(app);
        ru.playsoftware.j2meloader.bubble.BubbleController.hide(app);
        synchronized (TreoController.class) {
            MAIN.removeCallbacks(START_TIMEOUT);
            callback = null;
            state = STOPPING;
        }
        Intent intent = new Intent(app, TreoOnlineService.class)
                .setAction(TreoOnlineService.ACTION_STOP);
        try {
            app.startService(intent);
        } catch (Exception e) {
            Log.w(TAG, "Unable to send Treo stop action", e);
        }
        synchronized (TreoController.class) {
            state = STOPPED;
        }
    }

    public static void cycleMode(Context context) {
        Intent intent = new Intent(context.getApplicationContext(), TreoOnlineService.class)
                .setAction(TreoOnlineService.ACTION_CYCLE_MODE);
        try {
            context.getApplicationContext().startService(intent);
        } catch (Exception e) {
            Log.w(TAG, "Unable to cycle Treo mode", e);
        }
    }
}
