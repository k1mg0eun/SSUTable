package com.g1j2.ssutable.core;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 어느 한 시점의 340석 상태와 집계. */
public final class FloorSnapshot {

    public static final class Counts {
        public int open;
        public int inUse;
        public int reserved;
        public int free;
        public int total;

        /** 혼잡도: 개방 좌석 중 체크인(사용 중) 비율, 0~100. */
        public int congestionPercent() {
            if (open == 0) return 0;
            return Math.round(inUse * 100f / open);
        }
    }

    public enum Level {
        EASY("여유"), NORMAL("보통"), BUSY("혼잡"), FULL("매우 혼잡");

        public final String label;

        Level(String label) {
            this.label = label;
        }

        public static Level of(int percent) {
            if (percent < 40) return EASY;
            if (percent < 70) return NORMAL;
            if (percent < 90) return BUSY;
            return FULL;
        }
    }

    public final long at;
    public final ModeInfo mode;
    private final SeatState[] states;
    public final Counts all = new Counts();
    public final Map<Zone, Counts> zones = new EnumMap<>(Zone.class);

    public FloorSnapshot(long at, ModeInfo mode, List<Seat> seats, SeatState[] states) {
        this.at = at;
        this.mode = mode;
        this.states = states;
        for (Zone z : Zone.values()) zones.put(z, new Counts());
        for (Seat seat : seats) {
            SeatState st = states[seat.index];
            add(all, st);
            add(zones.get(seat.zone), st);
        }
    }

    private static void add(Counts c, SeatState st) {
        c.total++;
        if (st == SeatState.CLOSED) return;
        c.open++;
        switch (st) {
            case IN_USE:
            case MINE:
                c.inUse++;
                break;
            case RESERVED:
                c.reserved++;
                break;
            case FREE:
                c.free++;
                break;
            default:
                break;
        }
    }

    public SeatState state(Seat seat) {
        return states[seat.index];
    }

    /** 좌석 상태가 모두 같은지. 같으면 맵을 다시 그릴 필요가 없다. */
    public boolean sameSeats(FloorSnapshot other) {
        return other != null && java.util.Arrays.equals(states, other.states);
    }

    public Level level() {
        return Level.of(all.congestionPercent());
    }
}
