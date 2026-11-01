package ru.playsoftware.j2meloader.bubble;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.View;

/** Bottom-center drag target used by the original bubble trash gesture. */
public final class BubbleTrashView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private boolean highlighted;

    public BubbleTrashView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        fill.setColor(0xCC4A4A4A);
        stroke.setColor(0xFFFFFFFF);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(5f * density);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public int getSizePx() {
        return Math.round(64f * density);
    }

    public void setHighlighted(boolean value) {
        if (highlighted == value) return;
        highlighted = value;
        fill.setColor(value ? 0xE6F44336 : 0xCC4A4A4A);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) - 3f * density;
        canvas.drawCircle(cx, cy, radius, fill);
        float pad = radius * 0.42f;
        canvas.drawLine(cx - pad, cy - pad, cx + pad, cy + pad, stroke);
        canvas.drawLine(cx + pad, cy - pad, cx - pad, cy + pad, stroke);
    }
}
