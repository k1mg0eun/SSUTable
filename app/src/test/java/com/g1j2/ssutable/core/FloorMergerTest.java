package com.g1j2.ssutable.core;

import static com.g1j2.ssutable.core.ReservationEngine.MIN;
import static com.g1j2.ssutable.core.ScheduleTest.at;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class FloorMergerTest {
    private final SeatLayout layout = SeatLayout.get();
    private final OccupancySimulator sim = new OccupancySimulator();
    private final FloorMerger merger = new FloorMerger(layout, sim);
    private final Seat seat = layout.seat("B-12");
    private final ReservationEngine engine = new ReservationEngine();

    private static SeatClaim claim(String uid, String resId, Reservation.State st, long since, long deadline) {
        SeatClaim c = new SeatClaim();
        c.uid = uid;
        c.resId = resId;
        c.state = st;
        c.since = since;
        c.deadline = deadline;
        return c;
    }

    private FloorSnapshot merge(long now, Map<String, SeatClaim> claims, Reservation mine) {
        ModeInfo info = Schedule.resolve(now, null, false);
        return merger.merge(now, info, false, claims, mine);
    }

    private Map<String, SeatClaim> one(Seat s, SeatClaim c) {
        Map<String, SeatClaim> m = new HashMap<>();
        m.put(s.id, c);
        return m;
    }

    private Reservation reserveAt(Seat s, long now) {
        return engine.create("R1", s, Schedule.resolve(now, null, false), now, "T1");
    }

    @Test
    public void liveClaimOverridesSimulator() {
        long now = at(10, 0);
        FloorSnapshot a = merge(now, one(seat, claim("u2", "R9", Reservation.State.IN_USE, now - MIN, now + 30 * MIN)), null);
        assertEquals(SeatState.IN_USE, a.state(seat));
        FloorSnapshot b = merge(now, one(seat, claim("u2", "R9", Reservation.State.WAITING, now - MIN, now + 9 * MIN)), null);
        assertEquals(SeatState.RESERVED, b.state(seat));
    }

    @Test
    public void expiredClaimIsTakeable() {
        long now = at(10, 0);
        SeatClaim c = claim("u2", "R9", Reservation.State.IN_USE, now - 60 * MIN, now);
        FloorSnapshot s = merge(now, one(seat, c), null);
        assertEquals(sim.stateOf(seat, now, Mode.STUDY, false), s.state(seat));
        assertTrue(SeatClaim.takeable(c, now));
    }

    @Test
    public void ignoresClaimFromTheFuture() {
        long now = at(10, 0);
        SeatClaim c = claim("u2", "R9", Reservation.State.IN_USE, now + 60 * MIN, now + 90 * MIN);
        FloorSnapshot s = merge(now, one(seat, c), null);
        assertEquals(sim.stateOf(seat, now, Mode.STUDY, false), s.state(seat));
        assertTrue(SeatClaim.takeable(c, now));
    }

    @Test
    public void claimSlightlyInFutureIsLive() {
        long now = at(10, 0);
        SeatClaim c = claim("u2", "R9", Reservation.State.WAITING, now + MIN, now + 11 * MIN);
        assertTrue(c.liveAt(now));
        assertFalse(SeatClaim.takeable(c, now));
        assertEquals(SeatState.RESERVED, merge(now, one(seat, c), null).state(seat));
    }

    @Test
    public void liveClaimIsNotTakeable() {
        long now = at(10, 0);
        assertFalse(SeatClaim.takeable(claim("u2", "R9", Reservation.State.IN_USE, now - MIN, now + MIN), now));
        assertTrue(SeatClaim.takeable(null, now));
    }

    @Test
    public void claimOnClosedSeatStillShows() {
        // 운영 종료로 바뀐 직후에도 아직 쓰고 있는 자리는 숨기지 않는다
        Seat closed = layout.seat("D-1");
        long now = at(12, 30);
        ModeInfo off = Schedule.resolve(now, Mode.CLOSED, false);
        FloorSnapshot base = merger.merge(now, off, false, new HashMap<String, SeatClaim>(), null);
        assertEquals(SeatState.CLOSED, base.state(closed));
        FloorSnapshot s = merger.merge(now, off, false,
                one(closed, claim("u2", "R9", Reservation.State.IN_USE, now - MIN, now + 20 * MIN)), null);
        assertNotEquals(SeatState.CLOSED, s.state(closed));
    }

    @Test
    public void myClaimForMyActiveReservationShowsMine() {
        long now = at(10, 0);
        Reservation mine = reserveAt(seat, now);
        FloorSnapshot s = merge(now, one(seat, SeatClaim.of("u1", mine)), mine);
        assertEquals(SeatState.MINE, s.state(seat));
    }

    @Test
    public void leftoverClaimOfMineShowsAsSomeoneElse() {
        long now = at(10, 0);
        // 내 uid지만 지금 내 예약이 아닌 점유(커밋 뒤 꺼짐, 늦은 커밋)
        SeatClaim left = claim("u1", "R7", Reservation.State.WAITING, now - MIN, now + 9 * MIN);
        assertEquals(SeatState.RESERVED, merge(now, one(seat, left), null).state(seat));
        Reservation other = reserveAt(layout.seat("B-13"), now);
        SeatClaim inUse = claim("u1", "R7", Reservation.State.IN_USE, now - MIN, now + 30 * MIN);
        assertEquals(SeatState.IN_USE, merge(now, one(seat, inUse), other).state(seat));
    }

    @Test
    public void localReservationWithoutClaimsShowsMine() {
        long now = at(10, 0);
        Reservation mine = reserveAt(seat, now);
        FloorSnapshot s = merge(now, new HashMap<String, SeatClaim>(), mine);
        assertEquals(SeatState.MINE, s.state(seat));
    }

    @Test
    public void claimDeadlineFollowsState() {
        long now = at(10, 0);
        Reservation r = reserveAt(seat, now);
        SeatClaim w = SeatClaim.of("u1", r);
        assertEquals(Reservation.State.WAITING, w.state);
        assertEquals(r.createdAt, w.since);
        assertEquals(r.arriveBy, w.deadline);
        assertEquals("R1", w.resId);
        assertEquals("u1", w.uid);

        engine.arrive(r, now + MIN);
        assertEquals(r.endsAt, SeatClaim.of("u1", r).deadline);

        r.state = Reservation.State.AWAY;
        r.awaySince = r.endsAt - 10 * MIN;
        assertEquals(r.endsAt, SeatClaim.of("u1", r).deadline);
        r.awaySince = r.endsAt - 40 * MIN;
        assertEquals(r.awaySince + ReservationEngine.AWAY_GRACE, SeatClaim.of("u1", r).deadline);

        r.state = Reservation.State.ENDED;
        assertNull(SeatClaim.of("u1", r));
    }

    @Test
    public void lostSeatWhenClaimRemovedOrReplaced() {
        Reservation mine = reserveAt(seat, at(10, 0));
        assertTrue(SeatClaim.lostSeat(null, mine));
        assertTrue(SeatClaim.lostSeat(claim("u2", "R9", Reservation.State.IN_USE, 0, 1), mine));
    }

    @Test
    public void notLostWhenOwnClaim() {
        Reservation mine = reserveAt(seat, at(10, 0));
        assertFalse(SeatClaim.lostSeat(claim("u1", "R1", Reservation.State.WAITING, 0, 1), mine));
        mine.state = Reservation.State.ENDED;
        assertFalse(SeatClaim.lostSeat(null, mine));
    }
}
