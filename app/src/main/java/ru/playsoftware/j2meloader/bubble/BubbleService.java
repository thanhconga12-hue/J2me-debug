package ru.playsoftware.j2meloader.bubble;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import javax.microedition.lcdui.HeadlessMode;
import ru.playsoftware.j2meloader.LauncherRouterActivity;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.keepalive.TreoController;
import ru.playsoftware.j2meloader.keepalive.TreoOnlineService;

public final class BubbleService extends Service implements BubbleTouchHandler.Callback {
    public static final String ACTION_SHOW = BubbleController.ACTION_SHOW;
    public static final String ACTION_HIDE = BubbleController.ACTION_HIDE;
    public static final String ACTION_UPDATE_MODE = BubbleController.ACTION_UPDATE_MODE;
    public static final String ACTION_REFRESH_STYLE = BubbleController.ACTION_REFRESH_STYLE;

    private static final String TAG = "NSTBUBBLE";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private WindowManager.LayoutParams bubbleParams;
    private WindowManager.LayoutParams trashParams;
    private BubbleView bubbleView;
    private BubbleTrashView trashView;
    private BubbleTouchHandler touchHandler;
    private boolean attached;
    private int autoHideMode;
    private long autoHideDelay;
    private float baseAlpha = 0.85f;

    private final Runnable autoHideRunnable = () -> {
        if (bubbleView == null) return;
        if (autoHideMode == 3) {
            bubbleView.animate().alpha(0f).setDuration(250L).start();
        } else if (autoHideMode == 2) {
            bubbleView.animate().alpha(baseAlpha * 0.3f).setDuration(250L).start();
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_SHOW : intent.getAction();
        if (ACTION_HIDE.equals(action)) {
            cleanup();
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.bubble_perm_overlay_toast, Toast.LENGTH_LONG).show();
            cleanup();
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (!preferences.getBoolean("pref_bubble_enable", false)) {
            cleanup();
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (preferences.getBoolean("pref_bubble_only_when_treo", true)
                && !TreoController.isRunning()) {
            cleanup();
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        if (!attached && !attachBubble()) {
            cleanup();
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        if (ACTION_UPDATE_MODE.equals(action) && bubbleView != null) {
            String modeLabel = intent == null ? null
                    : intent.getStringExtra(BubbleController.EXTRA_MODE_LABEL);
            if (modeLabel == null) modeLabel = modeLabel(TreoController.getCurrentMode());
            bubbleView.setMode(modeLabel);
        } else if (ACTION_REFRESH_STYLE.equals(action)) {
            applyStyle();
        } else if (bubbleView != null) {
            bubbleView.setMode(modeLabel(TreoController.getCurrentMode()));
        }
        scheduleAutoHide();
        return START_STICKY;
    }

    private boolean attachBubble() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            Log.w(TAG, "WindowManager unavailable");
            return false;
        }
        bubbleView = new BubbleView(this);
        applyStyle();

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        bubbleParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type, flags, PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int savedX = preferences.getInt("pref_bubble_pos_x", -1);
        int savedY = preferences.getInt("pref_bubble_pos_y", -1);
        bubbleParams.x = savedX >= 0 ? savedX : Math.max(0, screenWidth - bubbleView.getSizePx());
        bubbleParams.y = savedY >= 0 ? savedY : dp(80);

        touchHandler = new BubbleTouchHandler(this, bubbleView, bubbleParams,
                windowManager, this);
        bubbleView.setOnTouchListener(touchHandler);
        try {
            windowManager.addView(bubbleView, bubbleParams);
            attached = true;
            BubbleController.setRunning(true);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Unable to attach bubble overlay", e);
            Toast.makeText(this, getString(R.string.bubble_attach_failed, e.getMessage()),
                    Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void applyStyle() {
        if (bubbleView == null) return;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        int size = parseInt(preferences.getString("pref_bubble_size_dp", "52"), 52);
        int opacity = parseInt(preferences.getString("pref_bubble_opacity", "85"), 85);
        String style = preferences.getString("pref_bubble_style", "ROUND");
        String glowValue = preferences.getString("pref_bubble_glow_color", "#00E5FF");
        int glow;
        try {
            glow = Color.parseColor(glowValue);
        } catch (Exception ignored) {
            glow = Color.CYAN;
        }
        bubbleView.applyStyle(size, opacity, style, glow);
        baseAlpha = Math.max(0.1f, Math.min(1f, opacity / 100f));
        if (bubbleParams != null && attached) {
            try {
                windowManager.updateViewLayout(bubbleView, bubbleParams);
            } catch (Exception ignored) {
            }
        }
        if (bubbleView != null) bubbleView.setMode(modeLabel(TreoController.getCurrentMode()));
    }

    private void scheduleAutoHide() {
        handler.removeCallbacks(autoHideRunnable);
        if (bubbleView == null) return;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String mode = preferences.getString("pref_bubble_autohide_mode", "FADE");
        autoHideMode = "HIDE".equals(mode) ? 3 : ("FADE".equals(mode) ? 2 : 1);
        autoHideDelay = parseLong(preferences.getString("pref_bubble_autohide_ms", "3000"), 3000L);
        bubbleView.animate().alpha(baseAlpha).setDuration(150L).start();
        if (autoHideMode != 1 && autoHideDelay > 0L) {
            handler.postDelayed(autoHideRunnable, autoHideDelay);
        }
    }

    private String modeLabel(int mode) {
        if (mode == TreoOnlineService.MODE_WIFI) return getString(R.string.treo_mode_wifi);
        if (mode == TreoOnlineService.MODE_STRONG) return getString(R.string.treo_mode_strong);
        return getString(R.string.treo_mode_light);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (Exception ignored) { return fallback; }
    }

    private long parseLong(String value, long fallback) {
        try { return Long.parseLong(value); } catch (Exception ignored) { return fallback; }
    }

    @Override
    public void onAction(String action) {
        scheduleAutoHide();
        if ("OPEN_GAME".equals(action)) {
            Intent launch = new Intent(this, LauncherRouterActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try {
                startActivity(launch);
            } catch (Exception e) {
                Log.w(TAG, "Unable to reopen game from bubble", e);
                Toast.makeText(this, R.string.bubble_open_failed, Toast.LENGTH_SHORT).show();
            }
        } else if ("CYCLE_MODE".equals(action)) {
            TreoController.cycleMode(this);
        } else if ("STOP_TREO".equals(action)) {
            TreoController.setStickyEnabled(this, false);
            TreoController.stop(this);
        } else if ("HIDE".equals(action)) {
            BubbleController.hide(this);
        } else if ("TOGGLE_HEADLESS".equals(action)) {
            boolean enabled = HeadlessMode.toggle();
            Toast.makeText(this, enabled ? R.string.headless_enabled : R.string.headless_disabled,
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDragChanged(boolean dragging, boolean overTrash) {
        if (dragging) {
            if (trashView == null) showTrash();
            if (trashView != null) trashView.setHighlighted(overTrash);
        } else {
            hideTrash();
            if (overTrash) {
                TreoController.setStickyEnabled(this, false);
                TreoController.stop(this);
            } else {
                scheduleAutoHide();
            }
        }
    }

    @Override
    public void onPositionSaved(int x, int y) {
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putInt("pref_bubble_pos_x", x)
                .putInt("pref_bubble_pos_y", y)
                .apply();
    }

    @Override
    public void onInteraction() {
        scheduleAutoHide();
    }

    private void showTrash() {
        if (windowManager == null || trashView != null) return;
        trashView = new BubbleTrashView(this);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        trashParams = new WindowManager.LayoutParams(trashView.getSizePx(),
                trashView.getSizePx(), type, flags, PixelFormat.TRANSLUCENT);
        trashParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        trashParams.y = dp(42);
        try {
            windowManager.addView(trashView, trashParams);
        } catch (Exception e) {
            trashView = null;
            trashParams = null;
        }
    }

    private void hideTrash() {
        if (windowManager != null && trashView != null) {
            try { windowManager.removeView(trashView); } catch (Exception ignored) { }
        }
        trashView = null;
        trashParams = null;
    }

    private void cleanup() {
        handler.removeCallbacksAndMessages(null);
        hideTrash();
        if (windowManager != null && bubbleView != null && attached) {
            try { windowManager.removeView(bubbleView); } catch (Exception ignored) { }
        }
        attached = false;
        bubbleView = null;
        bubbleParams = null;
        touchHandler = null;
        BubbleController.setRunning(false);
    }

    @Override
    public void onDestroy() {
        cleanup();
        super.onDestroy();
    }
}
