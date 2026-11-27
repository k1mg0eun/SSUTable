package com.g1j2.ssutable.core;

import static com.g1j2.ssutable.core.Presence.INSIDE;
import static com.g1j2.ssutable.core.Presence.OFF;
import static com.g1j2.ssutable.core.Presence.OUTSIDE;
import static com.g1j2.ssutable.core.Presence.UNKNOWN;
import static com.g1j2.ssutable.core.ReservationEngine.MIN;
import static com.g1j2.ssutable.core.ScheduleTest.at;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class ReservationEngineTest {
    private final ReservationEngine engine = new ReservationEngine();
    private final Seat seat = SeatLayout.get().seat("B-12");

    private Reservation reserveAt(long now, Mode forced) {
        ModeInfo info = Schedule.resolve(now, forced, false);
        return engine.create("R1", seat, info, now, "T1");
    }

    @Test
    public void lunchReservationCarriesTicket() {
        Reservation r = reserveAt(at(12, 0), null);
        assertEquals(Mode.LUNCH, r.mode);
        assertNotNull(r.ticket);
        assertEquals("점심 식권", r.ticket.name);
        assertEquals(at(12, 10), r.arriveBy);
    }

    @Test
    public void endByResetCancelsUnusedTicket() {
        Reservation r = reserveAt(at(12, 0), null);
        engine.endBy(r, at(12, 3), Reservation.EndReason.RESET);
        assertEquals(Reservation.State.ENDED, r.state);
        assertEquals(Reservation.EndReason.RESET, r.endReason);
        assertEquals(Ticket.Status.CANCELLED, r.ticket.status);
    }

    @Test
    public void studyReservationHasNoTicket() {
        assertNull(reserveAt(at(15, 0), null).ticket);
    }

    @Test
    public void noShowAfterTenMinutesCancelsTicket() {
        Reservation r = reserveAt(at(12, 0), null);
        List<ReservationEngine.Event> warn = engine.tick(r, at(12, 7), INSIDE);
        assertTrue(warn.contains(ReservationEngine.Event.ARRIVAL_SOON));

        List<ReservationEngine.Event> ev = engine.tick(r, at(12, 10), INSIDE);
        assertTrue(ev.contains(ReservationEngine.Event.NO_SHOW));
        assertEquals(Reservation.State.ENDED, r.state);
        assertEquals(Reservation.EndReason.NO_SHOW, r.endReason);
        assertEquals(Ticket.Status.CANCELLED, r.ticket.status);
    }

    @Test
    public void arriveNeedsToBeInsideWhenTracking() {
        Reservation r = reserveAt(at(12, 0), null);
        assertFalse(engine.canArrive(r, OUTSIDE));
        assertTrue(engine.canArrive(r, INSIDE));
        assertTrue(engine.canArrive(r, OFF));
    }

    @Test
    public void cannotArriveBeforeLocationIsKnown() {
        Reservation r = reserveAt(at(12, 0), null);
        assertFalse(engine.canArrive(r, UNKNOWN));
    }

    @Test
    public void unknownLocationDoesNotStartAway() {
        Reservation r = reserveAt(at(14, 10), null);
        engine.arrive(r, at(14, 10));
        assertTrue(engine.tick(r, at(14, 30), UNKNOWN).isEmpty());
        assertEquals(Reservation.State.IN_USE, r.state);
    }

    @Test
    public void closingTheAppWhileAwayKeepsTheTimerRunning() {
        Reservation r = reserveAt(at(14, 10), null);
        engine.arrive(r, at(14, 10));
        engine.tick(r, at(14, 30), OUTSIDE);
        assertEquals(Reservation.State.AWAY, r.state);

        // 앱을 닫아 위치를 모르게 돼도 자리 비움은 풀리지 않는다
        assertTrue(engine.tick(r, at(14, 35), UNKNOWN).isEmpty());
        assertEquals(Reservation.State.AWAY, r.state);
        assertEquals(at(14, 30), r.awaySince);

        // 다시 열었을 때 밖이면 처음 나간 시각부터 30분으로 반납
        assertTrue(engine.tick(r, at(15, 0), OUTSIDE).contains(ReservationEngine.Event.AWAY_TIMEOUT));
        assertEquals(at(15, 0), r.endedAt);
    }

    @Test
    public void losingLocationPermissionWhileAwayFallsBackToTimeOnly() {
        Reservation r = reserveAt(at(14, 10), null);
        engine.arrive(r, at(14, 10));
        engine.tick(r, at(14, 30), OUTSIDE);
        assertTrue(engine.tick(r, at(14, 35), OFF).contains(ReservationEngine.Event.BACK));
        assertEquals(Reservation.State.IN_USE, r.state);
    }

    @Test
    public void usageIsClippedAtModeEnd() {
        Reservation r = reserveAt(at(13, 30), null);
        engine.arrive(r, at(13, 35));
        assertEquals(at(14, 0), r.endsAt);
    }

    @Test
    public void lunchUsageIs45Minutes() {
        Reservation r = reserveAt(at(12, 0), null);
        engine.arrive(r, at(12, 5));
        assertEquals(at(12, 50), r.endsAt);
        List<ReservationEngine.Event> ev = engine.tick(r, at(12, 50), INSIDE);
        assertTrue(ev.contains(ReservationEngine.Event.TIME_UP));
        assertEquals(Reservation.EndReason.TIME_UP, r.endReason);
        assertEquals(45 * MIN, r.usedMillis());
        assertEquals(Ticket.Status.UNUSED, r.ticket.status);
    }

    @Test
    public void studyExtendsOnceWithinLast30Minutes() {
        Reservation r = reserveAt(at(14, 10), null);
        engine.arrive(r, at(14, 10));
        assertEquals(at(16, 10), r.endsAt);

        long clip = at(17, 0);
        assertFalse(engine.extend(r, at(15, 30), clip));
        assertTrue(engine.extend(r, at(15, 45), clip));
        assertEquals(at(17, 0), r.endsAt);
        assertFalse(engine.extend(r, at(16, 40), clip));
    }

    @Test
    public void awayForThirtyMinutesChecksOut() {
        Reservation r = reserveAt(at(14, 10), null);
        engine.arrive(r, at(14, 10));

        assertTrue(engine.tick(r, at(14, 30), OUTSIDE).contains(ReservationEngine.Event.AWAY_STARTED));
        assertEquals(Reservation.State.AWAY, r.state);

        assertTrue(engine.tick(r, at(14, 50), INSIDE).contains(ReservationEngine.Event.BACK));
        assertEquals(Reservation.State.IN_USE, r.state);

        engine.tick(r, at(15, 0), OUTSIDE);
        assertTrue(engine.tick(r, at(15, 30), OUTSIDE).contains(ReservationEngine.Event.AWAY_TIMEOUT));
        assertEquals(Reservation.EndReason.AWAY_TIMEOUT, r.endReason);
    }

    @Test
    public void withoutLocationOnlyTimeEndsIt() {
        Reservation r = reserveAt(at(14, 10), null);
        engine.arrive(r, at(14, 10));
        assertTrue(engine.tick(r, at(15, 0), OFF).isEmpty());
        assertEquals(Reservation.State.IN_USE, r.state);
    }

    @Test
    public void forcedModeIsNotClipped() {
        Reservation r = reserveAt(at(13, 50), Mode.LUNCH);
        engine.arrive(r, at(13, 50));
        assertEquals(at(14, 35), r.endsAt);
    }
}
