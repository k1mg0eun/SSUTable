package com.g1j2.ssutable.core;

/**
 * 시연용 시계. 실제 시각에서 출발하되, 배속을 올리거나 특정 시각으로 옮길 수 있다.
 * virtual = anchorVirtual + (real - anchorReal) * speed
 */
public final class DemoClock {
    public interface RealTime {
        long now();
    }

    private final RealTime real;
    private long anchorReal;
    private long anchorVirtual;
    private int speed;

    public DemoClock(RealTime real, long anchorReal, long anchorVirtual, int speed) {
        this.real = real;
        this.anchorReal = anchorReal;
        this.anchorVirtual = anchorVirtual;
        this.speed = Math.max(1, speed);
    }

    public static DemoClock system() {
        long t = System.currentTimeMillis();
        return new DemoClock(System::currentTimeMillis, t, t, 1);
    }

    public long now() {
        return anchorVirtual + (real.now() - anchorReal) * speed;
    }

    public int speed() {
        return speed;
    }

    public void setSpeed(int newSpeed) {
        long v = now();
        anchorReal = real.now();
        anchorVirtual = v;
        speed = Math.max(1, newSpeed);
    }

    /** 가상 시각을 원하는 시각으로 옮긴다. */
    public void jumpTo(long virtualMillis) {
        anchorReal = real.now();
        anchorVirtual = virtualMillis;
    }

    /** 실제 시각으로 되돌린다. 배속은 유지. */
    public void resetToReal() {
        jumpTo(real.now());
    }

    /** 원격 상태를 그대로 반영한다. anchor가 -1이면 실제 시각으로 맞춘다. */
    public void apply(long anchorReal, long anchorVirtual, int speed) {
        if (anchorReal < 0 || anchorVirtual < 0) {
            long t = real.now();
            this.anchorReal = t;
            this.anchorVirtual = t;
        } else {
            this.anchorReal = anchorReal;
            this.anchorVirtual = anchorVirtual;
        }
        this.speed = Math.max(1, speed);
    }

    public long anchorReal() {
        return anchorReal;
    }

    public long anchorVirtual() {
        return anchorVirtual;
    }
}
