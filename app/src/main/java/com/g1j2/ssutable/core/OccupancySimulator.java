package com.g1j2.ssutable.core;

/**
 * 폰 한 대 버전에서 "다른 학생들"의 예약·사용을 흉내 낸다.
 * 좌석마다 시작 시점이 엇갈린 세션을 돌려서, 몇 분마다 몇 자리씩 자연스럽게 바뀐다.
 * 같은 시각이면 항상 같은 결과가 나온다(저장할 필요 없음).
 */
public final class OccupancySimulator {

    public SeatState stateOf(Seat seat, long now, Mode mode, boolean exam) {
        return stateOf(seat, now, mode, exam, Schedule.minuteOfDay(now));
    }

    public SeatState stateOf(Seat seat, long now, Mode mode, boolean exam, int minuteOfDay) {
        if (mode == Mode.CLOSED) return SeatState.CLOSED;

        double p = targetRate(seat.zone, mode, exam, minuteOfDay);
        long session = (mode.meal ? 25L : 80L) * 60_000L;
        double pos = now / (double) session + hash(seat.index, 7);
        long slot = (long) Math.floor(pos);
        double within = pos - slot;

        if (hash(seat.index, slot) >= p) return SeatState.FREE;
        // 세션 앞부분은 아직 도착 전(예약)인 경우가 있다
        if (within < 0.18 && hash(seat.index * 31L + 5, slot) < 0.55) return SeatState.RESERVED;
        return SeatState.IN_USE;
    }

    static double targetRate(Zone zone, Mode mode, boolean exam, int minuteOfDay) {
        double p;
        switch (mode) {
            case BREAKFAST:
                p = zone == Zone.B ? 0.22 : 0.34;
                break;
            case LUNCH: {
                // 12:30 즈음이 가장 붐빈다
                double bump = Math.max(0, 1 - Math.abs(minuteOfDay - 750) / 95.0);
                p = 0.42 + 0.34 * bump;
                if (zone == Zone.A) p += 0.05;
                if (zone == Zone.B) p -= 0.18;
                if (zone == Zone.D) p -= 0.04;
                break;
            }
            case STUDY:
                p = exam ? 0.68 : 0.4;
                if (zone == Zone.B) p += 0.22;
                if (zone == Zone.D) p += 0.06;
                if (zone == Zone.C) p -= 0.06;
                break;
            default:
                p = 0;
        }
        return Math.max(0, Math.min(0.96, p));
    }

    static double hash(long a, long b) {
        long x = a * 0x9E3779B97F4A7C15L ^ (b + 0x632BE59BD9B4E019L);
        x ^= x >>> 33;
        x *= 0xff51afd7ed558ccdL;
        x ^= x >>> 33;
        x *= 0xc4ceb93e81a8b8ebL;
        x ^= x >>> 33;
        return (x >>> 11) * 0x1.0p-53;
    }
}
