package ru.playsoftware.j2meloader.bubble;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import androidx.preference.PreferenceManager;

/** Tap, double-tap, long-press and drag handling for the NST bubble. */
public final class BubbleTouchHandler implements View.OnTouchListener {
    public interface Callback {
        void onAction(String action);
        void onDragChanged(boolean dragging, boolean overTrash);
        void onPositionSaved(int x, int y);
        void onInteraction();
    }

    private final Context context;
    private final BubbleView bubble;
    private final WindowManager.LayoutParams params;
    private final WindowManager windowManager;
    private final Callback callback;
    private final GestureDetector gestures;
    private final float touchSlop;
    private float downRawX;
    private float downRawY;
    private int startX;
    private int startY;
    private boolean dragging;
    private boolean overTrash;
    private boolean longPressFired;
    private long downTime;

    public BubbleTouchHandler(Context context, BubbleView bubble,
            WindowManager.LayoutParams params, WindowManager windowManager, Callback callback) {
        this.context = context.getApplicationContext();
        this.bubble = bubble;
        this.params = params;
        this.windowManager = windowManager;
        this.callback = callback;
        touchSlop = 12f * context.getResources().getDisplayMetrics().density;
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (!dragging) {
                    longPressFired = true;
                    fireAction("pref_bubble_long_press_action", "CYCLE_MODE");
                }
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (!dragging && !longPressFired) {
                    fireAction("pref_bubble_single_tap_action", "OPEN_GAME");
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (!dragging) {
                    longPressFired = true;
                    fireAction("pref_bubble_double_tap_action", "CYCLE_MODE");
                }
                return true;
            }
        });
        gestures.setIsLongpressEnabled(true);
    }

    private void fireAction(String key, String defaultValue) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        callback.onAction(preferences.getString(key, defaultValue));
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        gestures.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                startX = params.x;
                startY = params.y;
                downTime = SystemClock.uptimeMillis();
                dragging = false;
                overTrash = false;
                longPressFired = false;
                callback.onInteraction();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                if (!dragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                    dragging = true;
                    callback.onDragChanged(true, false);
                }
                if (dragging) {
                    params.x = Math.max(0, startX + Math.round(dx));
                    params.y = Math.max(0, startY + Math.round(dy));
                    try {
                        windowManager.updateViewLayout(bubble, params);
                    } catch (IllegalArgumentException ignored) {
                    }
                    overTrash = isOverTrash(event.getRawX(), event.getRawY());
                    callback.onDragChanged(true, overTrash);
                    callback.onInteraction();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    if (!overTrash) callback.onPositionSaved(params.x, params.y);
                    callback.onDragChanged(false, overTrash);
                    dragging = false;
                    return true;
                }
                return true;
            default:
                return true;
        }
    }

    private boolean isOverTrash(float rawX, float rawY) {
        Point size = new Point();
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                context.getDisplay().getRealSize(size);
            } else {
                WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                if (wm != null) wm.getDefaultDisplay().getRealSize(size);
            }
        } catch (Exception ignored) {
        }
        float density = context.getResources().getDisplayMetrics().density;
        float centerX = size.x / 2f;
        float top = size.y - 132f * density;
        float bottom = size.y - 32f * density;
        float halfWidth = 50f * density;
        return rawX >= centerX - halfWidth && rawX <= centerX + halfWidth
                && rawY >= top && rawY <= bottom;
    }
}
