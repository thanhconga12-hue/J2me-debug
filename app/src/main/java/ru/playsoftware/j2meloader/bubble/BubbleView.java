package ru.playsoftware.j2meloader.bubble;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.io.InputStream;

import ru.playsoftware.j2meloader.R;

/** Circular NST bubble face, configured from the original bubble preference keys. */
public final class BubbleView extends FrameLayout {
    private final GradientDrawable background = new GradientDrawable();
    private final TextView label;
    private final ImageView image;
    private int sizePx;
    private int glowColor = Color.CYAN;
    private String modeLabel = "·";

    public BubbleView(@NonNull Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        background.setShape(GradientDrawable.OVAL);
        background.setColor(Color.argb(204, 16, 24, 32));
        setBackground(background);

        label = new TextView(context);
        label.setTextColor(Color.WHITE);
        label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
        label.setGravity(Gravity.CENTER);
        label.setIncludeFontPadding(false);
        addView(label, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));

        image = new ImageView(context);
        image.setVisibility(GONE);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(image, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));
        setBubbleSize(52);
        setMode("·");
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, getResources().getDisplayMetrics()));
    }

    public int getSizePx() {
        return sizePx;
    }

    public void setBubbleSize(int dpValue) {
        int bounded = Math.max(32, Math.min(96, dpValue));
        sizePx = dp(bounded);
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx * 0.38f);
        image.setPadding(dp(2), dp(2), dp(2), dp(2));
        setLayoutParams(new FrameLayout.LayoutParams(sizePx, sizePx));
        requestLayout();
    }

    public void applyStyle(int sizeDp, int opacity, String style, int color) {
        setBubbleSize(sizeDp);
        glowColor = color;
        int clampedOpacity = Math.max(10, Math.min(100, opacity));
        setAlpha(clampedOpacity / 100f);
        int strokeWidth = 0;
        int fill = Color.argb(204, 16, 24, 32);
        float elevation = dp(2);
        switch (style == null ? "ROUND" : style) {
            case "CLASSIC":
                fill = Color.argb(230, 16, 24, 32);
                elevation = dp(2);
                break;
            case "NEON":
                fill = Color.argb(179, 16, 24, 32);
                strokeWidth = dp(2);
                elevation = dp(10);
                break;
            case "ROUND":
            default:
                fill = Color.argb(204, 16, 24, 32);
                strokeWidth = dp(2);
                elevation = dp(4);
                glowColor = Color.WHITE;
                break;
        }
        background.setColor(fill);
        background.setStroke(strokeWidth, style != null && "NEON".equals(style)
                ? color : Color.WHITE);
        setElevation(elevation);
        if ("NEON".equals(style)) {
            label.setShadowLayer(dp(6), 0f, 0f, color);
            image.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
        } else {
            label.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
            image.clearColorFilter();
        }
        invalidate();
    }

    public void setMode(String mode) {
        modeLabel = mode == null || mode.isEmpty() ? "·" : mode;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        String source = preferences.getString("pref_bubble_icon_source", "BUBBLE_LOGO");
        image.clearColorFilter();
        image.setImageDrawable(null);
        label.setVisibility(GONE);
        image.setVisibility(VISIBLE);
        try {
            switch (source) {
                case "TEXT":
                    image.setVisibility(GONE);
                    label.setVisibility(VISIBLE);
                    label.setText(modeLabel.substring(0, 1).toUpperCase(java.util.Locale.ROOT));
                    break;
                case "APP":
                    image.setImageDrawable(getContext().getPackageManager()
                            .getApplicationIcon(getContext().getApplicationInfo()));
                    break;
                case "CUSTOM_PNG":
                    String uriValue = preferences.getString("pref_bubble_custom_icon_uri", null);
                    Bitmap bitmap = null;
                    if (uriValue != null) {
                        try (InputStream stream = getContext().getContentResolver()
                                .openInputStream(Uri.parse(uriValue))) {
                            if (stream != null) bitmap = BitmapFactory.decodeStream(stream);
                        }
                    }
                    if (bitmap != null) image.setImageBitmap(bitmap);
                    else image.setImageResource(R.drawable.ic_bubble);
                    break;
                case "BUBBLE_LOGO":
                default:
                    image.setImageResource(R.drawable.ic_bubble);
                    break;
            }
        } catch (Exception ignored) {
            image.setImageResource(R.drawable.ic_bubble);
        }
        if ("NEON".equals(preferences.getString("pref_bubble_style", "ROUND"))) {
            image.setColorFilter(glowColor, PorterDuff.Mode.SRC_ATOP);
        }
        invalidate();
    }
}
