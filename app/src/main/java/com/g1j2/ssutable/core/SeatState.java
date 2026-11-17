package com.g1j2.ssutable.core;

/** 좌석 맵에 표시되는 5가지 상태. */
public enum SeatState {
    FREE("빈자리"),
    RESERVED("예약"),
    IN_USE("사용 중"),
    MINE("내 자리"),
    CLOSED("미개방");

    public final String label;

    SeatState(String label) {
        this.label = label;
    }
}
