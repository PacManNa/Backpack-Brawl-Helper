package ua.borlock.autotap;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Full-screen touch layer used only while the user marks a calibration rectangle. */
final class CalibrationDrawView extends View {
    interface Listener {
        void onSelectionChanged(RectF selection);
    }

    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Listener listener;
    private final int accentColor;
    private RectF selection;
    private float startX;
    private float startY;
    private boolean drawing;

    CalibrationDrawView(Context context, int accentColor, Listener listener) {
        super(context);
        this.accentColor = accentColor;
        this.listener = listener;
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setClickable(true);
        dimPaint.setColor(0x560A0608);
        fillPaint.setColor((accentColor & 0x00FFFFFF) | 0x30000000);
        borderPaint.setColor(accentColor);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(context.getResources().getDisplayMetrics().density * 3f);
    }

    RectF getSelection() {
        return selection == null ? null : new RectF(selection);
    }

    void clearSelection() {
        selection = null;
        invalidate();
        listener.onSelectionChanged(null);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);
        if (selection != null) {
            canvas.drawRect(selection, fillPaint);
            canvas.drawRect(selection, borderPaint);
            float radius = borderPaint.getStrokeWidth() * 1.5f;
            canvas.drawCircle(selection.left, selection.top, radius, borderPaint);
            canvas.drawCircle(selection.right, selection.bottom, radius, borderPaint);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX = clamp(event.getX(), 0, getWidth());
                startY = clamp(event.getY(), 0, getHeight());
                selection = new RectF(startX, startY, startX, startY);
                drawing = true;
                notifySelection();
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!drawing) return false;
                updateSelection(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!drawing) return false;
                updateSelection(event.getX(), event.getY());
                drawing = false;
                return true;
            default:
                return drawing;
        }
    }

    private void updateSelection(float x, float y) {
        float endX = clamp(x, 0, getWidth());
        float endY = clamp(y, 0, getHeight());
        selection = new RectF(Math.min(startX, endX), Math.min(startY, endY),
                Math.max(startX, endX), Math.max(startY, endY));
        notifySelection();
        invalidate();
    }

    private void notifySelection() {
        listener.onSelectionChanged(getSelection());
    }

    private float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
