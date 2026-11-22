package com.g1j2.ssutable.core;

public enum Zone {
    A("긴 공용 테이블"),
    B("1인 칸막이석"),
    C("소파 부스"),
    D("작은 테이블");

    public final String label;

    Zone(String label) {
        this.label = label;
    }
}
