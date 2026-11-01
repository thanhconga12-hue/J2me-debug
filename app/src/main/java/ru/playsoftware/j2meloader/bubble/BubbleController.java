package ru.playsoftware.j2meloader.bubble;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.preference.PreferenceManager;

/** Process-wide bridge to the original NST overlay bubble service. */
public final class BubbleController {
    public static final String ACTION_SHOW = "ru.playsoftware.j2meloader.bubble.SHOW";
    public static final String ACTION_HIDE = "ru.playsoftware.j2meloader.bubble.HIDE";
    public static final String ACTION_UPDATE_MODE = "ru.playsoftware.j2meloader.bubble.UPDATE_MODE";
    public static final String ACTION_REFRESH_STYLE = "ru.playsoftware.j2meloader.bubble.REFRESH_STYLE";
    public static final String EXTRA_MODE_LABEL = "mode_label";

    private static final String TAG = "NSTBUBBLE";
    private static volatile boolean running;

    private BubbleController() {
    }

    public static boolean isRunning() {
        return running;
    }

    static void setRunning(boolean value) {
        running = value;
    }

    public static void show(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(app);
        if (!preferences.getBoolean("pref_bubble_enable", false)) return;
        if (preferences.getBoolean("pref_bubble_only_when_treo", true)
                && !ru.playsoftware.j2meloader.keepalive.TreoController.isRunning()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(app)) {
            Log.i(TAG, "Overlay permission is not granted; bubble remains hidden");
            return;
        }
        start(app, ACTION_SHOW, null);
    }

    public static void hide(Context context) {
        start(context.getApplicationContext(), ACTION_HIDE, null);
    }

    public static void refreshStyle(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(
                context.getApplicationContext());
        if (!preferences.getBoolean("pref_bubble_enable", false)) return;
        start(context.getApplicationContext(), ACTION_REFRESH_STYLE, null);
    }

    public static void updateMode(Context context, String modeLabel) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(
                context.getApplicationContext());
        if (!preferences.getBoolean("pref_bubble_enable", false)) return;
        start(context.getApplicationContext(), ACTION_UPDATE_MODE, modeLabel);
    }

    private static void start(Context app, String action, String modeLabel) {
        Intent intent = new Intent(app, BubbleService.class).setAction(action);
        if (modeLabel != null) intent.putExtra(EXTRA_MODE_LABEL, modeLabel);
        try {
            app.startService(intent);
        } catch (Exception e) {
            Log.w(TAG, "Unable to send bubble service action: " + action, e);
        }
    }
}
