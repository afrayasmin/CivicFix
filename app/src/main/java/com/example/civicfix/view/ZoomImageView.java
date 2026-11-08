package com.example.civicfix.view;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * An ImageView that supports pinch-to-zoom, panning and double-tap zoom.
 * It uses an image Matrix plus two gesture detectors.
 */
public class ZoomImageView extends AppCompatImageView {

    private static final float MAX_ZOOM = 4f;          // times the "fit to screen" size
    private static final float DOUBLE_TAP_ZOOM = 2.5f;

    private final Matrix matrix = new Matrix();
    private final float[] values = new float[9];
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;

    private float fitScale = 1f;
    private Runnable tapListener;

    public ZoomImageView(Context context) {
        this(context, null);
    }

    public ZoomImageView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZoomImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    /** Called when the user taps the image once (not a double tap). */
    public void setOnTapListener(Runnable listener) {
        this.tapListener = listener;
    }

    // ---------------- Fitting the image to the screen ----------------

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        fitToScreen();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fitToScreen();
    }

    private void fitToScreen() {
        Drawable d = getDrawable();
        if (d == null || getWidth() == 0 || getHeight() == 0) return;

        float dw = d.getIntrinsicWidth();
        float dh = d.getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) return;

        fitScale = Math.min(getWidth() / dw, getHeight() / dh);
        matrix.reset();
        matrix.postScale(fitScale, fitScale);
        matrix.postTranslate((getWidth() - dw * fitScale) / 2f,
                (getHeight() - dh * fitScale) / 2f);
        setImageMatrix(matrix);
    }

    // ---------------- Matrix helpers ----------------

    private float currentScale() {
        matrix.getValues(values);
        return values[Matrix.MSCALE_X];
    }

    /** Keeps the image inside the screen, and centred when it is smaller than the screen. */
    private void fixTranslation() {
        Drawable d = getDrawable();
        if (d == null) return;

        matrix.getValues(values);
        float scale = values[Matrix.MSCALE_X];
        float w = d.getIntrinsicWidth() * scale;
        float h = d.getIntrinsicHeight() * scale;
        float tx = values[Matrix.MTRANS_X];
        float ty = values[Matrix.MTRANS_Y];
        float vw = getWidth();
        float vh = getHeight();

        float dx = 0;
        float dy = 0;

        if (w <= vw) {
            dx = (vw - w) / 2f - tx;
        } else if (tx > 0) {
            dx = -tx;
        } else if (tx + w < vw) {
            dx = vw - (tx + w);
        }

        if (h <= vh) {
            dy = (vh - h) / 2f - ty;
        } else if (ty > 0) {
            dy = -ty;
        } else if (ty + h < vh) {
            dy = vh - (ty + h);
        }

        matrix.postTranslate(dx, dy);
    }

    private void zoomBy(float factor, float focusX, float focusY) {
        float current = currentScale();
        if (current <= 0) return;

        float target = Math.max(fitScale, Math.min(fitScale * MAX_ZOOM, current * factor));
        float adjusted = target / current;

        matrix.postScale(adjusted, adjusted, focusX, focusY);
        fixTranslation();
        setImageMatrix(matrix);
    }

    // ---------------- Touch handling ----------------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_UP) performClick();
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            zoomBy(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (scaleDetector.isInProgress()) return false;
            matrix.postTranslate(-distanceX, -distanceY);
            fixTranslation();
            setImageMatrix(matrix);
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            boolean zoomedIn = currentScale() > fitScale * 1.05f;
            float target = zoomedIn ? fitScale : fitScale * DOUBLE_TAP_ZOOM;
            zoomBy(target / currentScale(), e.getX(), e.getY());
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (tapListener != null) tapListener.run();
            return true;
        }
    }
}