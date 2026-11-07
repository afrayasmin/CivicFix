package com.example.civicfix.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

/**
 * A custom View that draws a three-stage progress tracker:
 * Submitted, Under Inspection, Resolved.
 * It measures itself, draws on a Canvas and animates between steps.
 */
public class StatusTrackerView extends View {

    private static final String[] LABELS = {"Submitted", "Under Inspection", "Resolved"};
    private static final int[] COLORS = {0xFFF9A825, 0xFF1565C0, 0xFF2E7D32};
    private static final int GRAY = 0xFFBDBDBD;
    private static final int TEXT_DARK = 0xFF212121;
    private static final int TEXT_GRAY = 0xFF9E9E9E;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path checkPath = new Path();

    private final float radius;

    private int step = 0;           // target step: 0, 1 or 2
    private float shown = 0f;       // animated position between 0 and 2
    private float fittedTextSize;
    private ValueAnimator animator;

    public StatusTrackerView(Context context) {
        this(context, null);
    }

    public StatusTrackerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StatusTrackerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        radius = dp(13);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(2));

        linePaint.setStrokeWidth(dp(4));
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint.setTextAlign(Paint.Align.CENTER);
        fittedTextSize = sp(12);

        updateContentDescription();
    }

    /** Moves the tracker to a step (0 = Submitted, 1 = Under Inspection, 2 = Resolved). */
    public void setStep(int newStep) {
        newStep = Math.max(0, Math.min(2, newStep));
        if (newStep == step) return;

        step = newStep;
        updateContentDescription();

        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(shown, step);
        animator.setDuration(500);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            shown = (float) a.getAnimatedValue();
            invalidate(); // ask Android to call onDraw() again
        });
        animator.start();
    }

    public int getStep() {
        return step;
    }

    private void updateContentDescription() {
        setContentDescription("Report status: " + LABELS[step] + ", step " + (step + 1) + " of 3");
    }

    // ---------------- Measuring ----------------

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = (int) dp(280);
        int desiredHeight = (int) dp(72);
        setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // Shrink the labels if the longest one does not fit in its third of the width
        float columnWidth = w / 3f - dp(8);
        float size = sp(12);
        textPaint.setTextSize(size);
        float widest = 0;
        for (String label : LABELS) widest = Math.max(widest, textPaint.measureText(label));
        if (widest > columnWidth && widest > 0) size = size * columnWidth / widest;
        fittedTextSize = Math.max(size, sp(8));
    }

    // ---------------- Drawing ----------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float columnWidth = getWidth() / 3f;
        float cy = dp(8) + radius + dp(4);   // leaves room for the ring around the current step
        float gap = radius + dp(6);

        // Connectors between the circles, filled in as the animation reaches them
        for (int i = 0; i < 2; i++) {
            float x1 = columnWidth * (i + 0.5f) + gap;
            float x2 = columnWidth * (i + 1.5f) - gap;

            linePaint.setColor(GRAY);
            canvas.drawLine(x1, cy, x2, cy, linePaint);

            float fraction = Math.max(0f, Math.min(1f, shown - i));
            if (fraction > 0f) {
                linePaint.setColor(COLORS[i + 1]);
                canvas.drawLine(x1, cy, x1 + (x2 - x1) * fraction, cy, linePaint);
            }
        }

        textPaint.setTextSize(fittedTextSize);

        for (int i = 0; i < 3; i++) {
            float cx = columnWidth * (i + 0.5f);
            boolean reached = shown >= i - 0.001f;

            if (reached) {
                fillPaint.setColor(COLORS[i]);
                canvas.drawCircle(cx, cy, radius, fillPaint);

                if (i == step && i < 2) {
                    // The stage in progress: a white dot
                    fillPaint.setColor(0xFFFFFFFF);
                    canvas.drawCircle(cx, cy, radius * 0.38f, fillPaint);
                } else {
                    drawCheck(canvas, cx, cy);
                }

                if (i == step && Math.abs(shown - step) < 0.001f) {
                    // Soft ring around the current stage
                    strokePaint.setColor(COLORS[i]);
                    strokePaint.setAlpha(110);
                    strokePaint.setStrokeWidth(dp(2));
                    canvas.drawCircle(cx, cy, radius + dp(4), strokePaint);
                }
            } else {
                fillPaint.setColor(0xFFFFFFFF);
                canvas.drawCircle(cx, cy, radius, fillPaint);
                strokePaint.setColor(GRAY);
                strokePaint.setStrokeWidth(dp(2));
                canvas.drawCircle(cx, cy, radius - dp(1), strokePaint);
            }

            textPaint.setColor(reached ? TEXT_DARK : TEXT_GRAY);
            textPaint.setFakeBoldText(i == step);
            float baseline = cy + radius + dp(10) - textPaint.ascent();
            canvas.drawText(LABELS[i], cx, baseline, textPaint);
        }
    }

    private void drawCheck(Canvas canvas, float cx, float cy) {
        checkPath.reset();
        checkPath.moveTo(cx - radius * 0.42f, cy + radius * 0.02f);
        checkPath.lineTo(cx - radius * 0.10f, cy + radius * 0.34f);
        checkPath.lineTo(cx + radius * 0.46f, cy - radius * 0.32f);

        strokePaint.setColor(0xFFFFFFFF);
        strokePaint.setStrokeWidth(dp(2.5f));
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawPath(checkPath, strokePaint);
        strokePaint.setStrokeWidth(dp(2));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) animator.cancel();
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                getResources().getDisplayMetrics());
    }
}