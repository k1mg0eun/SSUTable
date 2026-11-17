package com.g1j2.ssutable.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.g1j2.ssutable.R;

/** 원형 게이지. 혼잡도와 남은 이용 시간에 쓴다. 12시 방향에서 시계 방향으로 찬다. */
public class RingView extends View {
    private float progress;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public RingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float dp = getResources().getDisplayMetrics().density;
        float w = 10 * dp;
        android.content.res.TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.RingView);
        w = a.getDimension(R.styleable.RingView_ringWidth, w);
        a.recycle();
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(w);
        track.setColor(ContextCompat.getColor(context, R.color.fill_soft));
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(w);
        arc.setStrokeCap(Paint.Cap.BUTT);
        arc.setColor(ContextCompat.getColor(context, R.color.ink));
    }

    public void setProgress(float p) {
        progress = Math.max(0, Math.min(1, p));
        invalidate();
    }

    public void setColor(int color) {
        arc.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float half = track.getStrokeWidth() / 2;
        float size = Math.min(getWidth(), getHeight());
        float l = (getWidth() - size) / 2 + half;
        float t = (getHeight() - size) / 2 + half;
        rect.set(l, t, l + size - half * 2, t + size - half * 2);
        canvas.drawOval(rect, track);
        canvas.drawArc(rect, -90, 360 * progress, false, arc);
    }
}
