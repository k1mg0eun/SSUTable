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

/** 얇은 가로 막대. 구역별 혼잡도와 도착 마감 진행에 쓴다. */
public class BarView extends View {
    private float fraction;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public BarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        track.setColor(ContextCompat.getColor(context, R.color.fill_soft));
        bar.setColor(ContextCompat.getColor(context, R.color.ink));
    }

    public void setFraction(float f) {
        fraction = Math.max(0, Math.min(1, f));
        invalidate();
    }

    public void setBarColor(int color) {
        bar.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float r = getHeight() / 2f;
        rect.set(0, 0, getWidth(), getHeight());
        canvas.drawRoundRect(rect, r, r, track);
        if (fraction > 0) {
            rect.set(0, 0, Math.max(getHeight(), getWidth() * fraction), getHeight());
            canvas.drawRoundRect(rect, r, r, bar);
        }
    }
}
