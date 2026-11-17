package com.g1j2.ssutable.core;

/**
 * 서버에 올라가는 "이 좌석은 누가 쓰고 있다" 표시 하나.
 * since ~ deadline 사이에만 유효하고, 기한이 지나면 다른 사람이 가져갈 수 있다.
 */
public final class SeatClaim {
    public String uid;
    public String resId;
    /** WAITING, IN_USE, AWAY 중 하나. */
    public Reservation.State state;
    public long since;
    public long deadline;

    /** 예약에서 점유 항목을 만든다. 이미 끝난 예약이면 null. */
    public static SeatClaim of(String uid, Reservation r) {
        if (r == null || !r.active()) return null;
        SeatClaim c = new SeatClaim();
        c.uid = uid;
        c.resId = r.id;
        c.state = r.state;
        c.since = r.createdAt;
        switch (r.state) {
            case WAITING:
                c.deadline = r.arriveBy;
                break;
            case AWAY:
                c.deadline = Math.min(r.awaySince + ReservationEngine.AWAY_GRACE, r.endsAt);
                break;
            default:
                c.deadline = r.endsAt;
                break;
        }
        return c;
    }

    public boolean liveAt(long now) {
        return since <= now && now < deadline;
    }

    /** 이 좌석을 새로 잡아도 되는지. 유효한 점유가 없으면 true. */
    public static boolean takeable(SeatClaim existing, long now) {
        return existing == null || !existing.liveAt(now);
    }

    /** 내 예약은 진행 중인데 서버의 점유가 사라졌거나 다른 예약으로 바뀌었는지. */
    public static boolean lostSeat(SeatClaim remote, Reservation mine) {
        if (mine == null || !mine.active()) return false;
        return remote == null || !remote.resId.equals(mine.id);
    }
}
