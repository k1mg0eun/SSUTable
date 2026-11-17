package com.g1j2.ssutable.core;

import static com.g1j2.ssutable.core.ScheduleTest.at;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class SeatLayoutTest {

    @Test
    public void has340UniqueSeatsIn4Zones() {
        SeatLayout layout = SeatLayout.get();
        assertEquals(340, layout.size());
        Set<String> ids = new HashSet<>();
        int b = 0;
        for (Seat s : layout.seats) {
            ids.add(s.id);
            if (s.zone == Zone.B) b++;
        }
        assertEquals(340, ids.size());
        assertEquals(43, b);
    }

    @Test
    public void lunchClosesTwelveSeats() {
        SeatLayout layout = SeatLayout.get();
        OccupancySimulator sim = new OccupancySimulator();
        SeatState[] st = new SeatState[layout.size()];
        for (Seat s : layout.seats) st[s.index] = sim.stateOf(s, at(12, 30), Mode.LUNCH, false);
        FloorSnapshot snap = new FloorSnapshot(at(12, 30), Schedule.resolve(at(12, 30), null, false), layout.seats, st);
        assertEquals(328, snap.all.open);
        int pct = snap.all.congestionPercent();
        assertTrue("lunch peak should be busy-ish, was " + pct, pct > 40 && pct < 95);
    }

    @Test
    public void closedModeClosesEverything() {
        SeatLayout layout = SeatLayout.get();
        OccupancySimulator sim = new OccupancySimulator();
        for (Seat s : layout.seats) {
            assertEquals(SeatState.CLOSED, sim.stateOf(s, at(20, 0), Mode.CLOSED, false));
        }
    }
}
