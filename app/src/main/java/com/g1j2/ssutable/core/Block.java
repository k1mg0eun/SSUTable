package com.g1j2.ssutable.core;

/** 도면에 그리는 가구·구조물. */
public final class Block {
    public enum Kind { TABLE, COUNTER, DESK, SOFA_BACK, BAR }

    public final Kind kind;
    public final float left, top, right, bottom;
    public final String label;

    Block(Kind kind, float left, float top, float right, float bottom, String label) {
        this.kind = kind;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.label = label;
    }
}
