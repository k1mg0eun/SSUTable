package com.g1j2.ssutable.core;

/** 지금 적용되는 모드와 그 구간 정보. */
public final class ModeInfo {
    public final Mode mode;
    /** 시연 모드에서 강제로 지정된 모드인지. 강제 지정이면 구간 끝으로 자르지 않는다. */
    public final boolean forced;
    /** 이 모드 구간이 끝나는 시각. 강제 지정·운영 종료면 -1. */
    public final long windowEnd;
    /** 운영 종료일 때 다음 운영 시각. 아니면 -1. */
    public final long nextOpen;
    /** 이 구간 다음에 오는 모드. 알 수 없으면 null. */
    public final Mode nextMode;

    public ModeInfo(Mode mode, boolean forced, long windowEnd, long nextOpen, Mode nextMode) {
        this.mode = mode;
        this.forced = forced;
        this.windowEnd = windowEnd;
        this.nextOpen = nextOpen;
        this.nextMode = nextMode;
    }

    public boolean open() {
        return mode != Mode.CLOSED;
    }
}
