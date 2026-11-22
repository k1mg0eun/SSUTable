package com.g1j2.ssutable.core;

public enum Zone {
    A("흰 테이블"),
    B("1인석·소파"),
    C("분홍 테이블"),
    D("초록 테이블");

    public final String label;

    Zone(String label) {
        this.label = label;
    }
}
