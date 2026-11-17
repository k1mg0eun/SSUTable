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
import com.g1j2.ssutable.core.Mode;
import com.g1j2.ssutable.core.Schedule;
import com.g1j2.ssutable.ui.Fmt;
import com.g1j2.ssutable.ui.ModeColors;

import java.util.List;

/**
 * 오늘 운영 시간표 막대. 모드마다 색이 다르고, 지금 시각에 바늘이 선다.
 * 지난 구간은 회색, 지금 구간은 진하게, 남은 구간은 옅게.
 */
public class DayTimelineView extends View {
    private boolean exam;
    private int nowMinute = -1;
    private Mode highlight;

    private final float dp;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int cPast, cInk, cInk3;

    public DayTimelineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        cPast = ContextCompat.getColor(context, R.color.fill_soft);
        cInk = ContextCompat.getColor(context, R.color.ink);
        cInk3 = ContextCompat.getColor(context, R.color.ink3);
        text.setTextSize(11 * dp);
        text.setFontFeatureSettings("tnum");
    }

    /** highlight: 시연 모드로 강제 지정했으면 그 모드, 아니면 null. */
    public void setData(boolean exam, int nowMinute, Mode highlight) {
        this.exam = exam;
        this.nowMinute = nowMinute;
        this.highlight = highlight;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int h = (int) (52 * dp);
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), resolveSize(h, heightSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float left = 0;
        float right = getWidth();
        float barTop = 18 * dp;
        float barBottom = barTop + 12 * dp;
        int start = Schedule.OPEN;
        int end = Schedule.closeMinute(exam);
        float span = end - start;

        List<Schedule.Segment> segs = Schedule.segments(exam);
        for (Schedule.Segment s : segs) {
            float x0 = left + (s.start - start) / span * (right - left);
            float x1 = left + (s.end - start) / span * (right - left);
            int base = ModeColors.of(getContext(), s.mode);
            boolean current;
            boolean past;
            if (highlight != null) {
                current = s.mode == highlight;
                past = false;
            } else {
                current = nowMinute >= s.start && nowMinute < s.end;
                past = nowMinute >= s.end;
            }
            if (past) {
                fill.setColor(cPast);
            } else {
                fill.setColor(base);
                fill.setAlpha(current ? 255 : 70);
            }
            rect.set(x0 + 1 * dp, barTop, x1 - 1 * dp, barBottom);
            canvas.drawRoundRect(rect, 3 * dp, 3 * dp, fill);
            fill.setAlpha(255);
        }

        // 경계 시각
        text.setColor(cInk3);
        float labelY = barBottom + 16 * dp;
        for (int i = 0; i <= segs.size(); i++) {
            int m = i < segs.size() ? segs.get(i).start : end;
            float x = left + (m - start) / span * (right - left);
            String label = m % 60 == 0 ? String.valueOf(m / 60) : Fmt.hmMinute(m);
            if (i == 0) text.setTextAlign(Paint.Align.LEFT);
            else if (i == segs.size()) text.setTextAlign(Paint.Align.RIGHT);
            else text.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(label, x, labelY, text);
        }

        // 지금 바늘
        if (highlight == null && nowMinute >= start && nowMinute < end) {
            float x = left + (nowMinute - start) / span * (right - left);
            fill.setColor(cInk);
            canvas.drawRect(x - 1 * dp, barTop - 5 * dp, x + 1 * dp, barBottom + 3 * dp, fill);
            text.setColor(cInk);
            text.setFakeBoldText(true);
            String t = Fmt.hmMinute(nowMinute);
            float w = text.measureText(t);
            float tx = Math.max(w / 2, Math.min(getWidth() - w / 2, x));
            text.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(t, tx, barTop - 8 * dp, text);
            text.setFakeBoldText(false);
        }
    }
}
