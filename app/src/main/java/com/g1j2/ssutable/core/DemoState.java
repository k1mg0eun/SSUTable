package com.g1j2.ssutable.core;

/** 모든 기기가 공유하는 시연 시계 상태. anchor가 -1이면 실제 시각. */
public final class DemoState {
    public long anchorServer = -1;
    public long anchorVirtual = -1;
    public int speed = 1;
    /** null이면 시간표에 따라 자동. */
    public Mode forcedMode;
    public boolean exam;
}
