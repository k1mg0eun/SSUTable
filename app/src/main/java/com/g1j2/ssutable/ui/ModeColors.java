package com.g1j2.ssutable.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.g1j2.ssutable.R;
import com.g1j2.ssutable.core.Mode;

/** 모드별 색. 시간표·모드 이름·모드 태그에만 쓴다. */
public final class ModeColors {
    private ModeColors() {}

    public static int of(Context c, Mode mode) {
        switch (mode) {
            case BREAKFAST: return ContextCompat.getColor(c, R.color.mode_breakfast);
            case LUNCH: return ContextCompat.getColor(c, R.color.mode_lunch);
            case STUDY: return ContextCompat.getColor(c, R.color.mode_study);
            default: return ContextCompat.getColor(c, R.color.mode_closed);
        }
    }

    /** 모드 이름을 담은 작은 태그로 꾸민다. */
    public static void tag(TextView v, Mode mode) {
        v.setText(mode.label);
        v.setBackgroundTintList(ColorStateList.valueOf(of(v.getContext(), mode)));
    }
}
