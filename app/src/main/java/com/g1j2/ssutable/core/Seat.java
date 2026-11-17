package com.g1j2.ssutable.core;

/** 좌석 한 개. 좌표는 도면 단위(가로 1000) 기준. */
public final class Seat {
    public final int index;
    public final String id;
    public final Zone zone;
    public final float left, top, right, bottom;
    public final boolean outlet;

    Seat(int index, String id, Zone zone, float left, float top, float w, float h,
         boolean outlet) {
        this.index = index;
        this.id = id;
        this.zone = zone;
        this.left = left;
        this.top = top;
        this.right = left + w;
        this.bottom = top + h;
        this.outlet = outlet;
    }

    public float centerX() {
        return (left + right) / 2f;
    }

    public float centerY() {
        return (top + bottom) / 2f;
    }

    /** 맵에 쓰는 짧은 번호 (B-12 → 12). */
    public String number() {
        return id.substring(id.indexOf('-') + 1);
    }

    public String description() {
        return outlet ? zone.label + ", 콘센트 있음" : zone.label;
    }
}
