package com.g1j2.ssutable.core;

/** 시간대별 운영 모드. 모드마다 예약 규칙이 다르다. */
public enum Mode {
    BREAKFAST("아침 식사", true, 45),
    LUNCH("점심 식사", true, 45),
    STUDY("스터디", false, 120),
    CLOSED("운영 종료", false, 0);

    public final String label;
    /** 식사 모드면 식권을 함께 예약한다. */
    public final boolean meal;
    public final int maxMinutes;

    Mode(String label, boolean meal, int maxMinutes) {
        this.label = label;
        this.meal = meal;
        this.maxMinutes = maxMinutes;
    }

    public long maxMillis() {
        return maxMinutes * 60_000L;
    }

    /** 식사 모드에서 함께 발급되는 식권 이름. 식사 모드가 아니면 null. */
    public String ticketName() {
        switch (this) {
            case BREAKFAST: return "천원의 아침밥";
            case LUNCH: return "점심 식권";
            default: return null;
        }
    }
}
