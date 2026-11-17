package com.g1j2.ssutable.core;

import java.util.Map;

/** 시뮬레이터가 만든 좌석 위에 실제 점유 항목과 내 예약을 덮어 한 장의 스냅샷을 만든다. */
public final class FloorMerger {
    private final SeatLayout layout;
    private final OccupancySimulator simulator;

    public FloorMerger(SeatLayout layout, OccupancySimulator simulator) {
        this.layout = layout;
        this.simulator = simulator;
    }

    public FloorSnapshot merge(long now, ModeInfo info, boolean exam, Map<String, SeatClaim> claims,
                               String myUid, Reservation mine) {
        SeatState[] states = new SeatState[layout.size()];
        String mineSeat = mine != null && mine.active() ? mine.seatId : null;
        int minute = Schedule.minuteOfDay(now);
        for (Seat seat : layout.seats) {
            SeatClaim c = claims == null ? null : claims.get(seat.id);
            if (c != null && c.liveAt(now)) {
                if (c.uid != null && c.uid.equals(myUid)) {
                    states[seat.index] = SeatState.MINE;
                } else {
                    states[seat.index] = c.state == Reservation.State.WAITING
                            ? SeatState.RESERVED : SeatState.IN_USE;
                }
            } else if (seat.id.equals(mineSeat)) {
                states[seat.index] = SeatState.MINE;
            } else {
                states[seat.index] = simulator.stateOf(seat, now, info.mode, exam, minute);
            }
        }
        return new FloorSnapshot(now, info, layout.seats, states);
    }
}
