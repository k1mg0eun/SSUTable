package com.g1j2.ssutable.core;

import static com.g1j2.ssutable.core.ScheduleTest.at;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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
        int[] perZone = new int[Zone.values().length];
        for (Seat s : layout.seats) {
            ids.add(s.id);
            perZone[s.zone.ordinal()]++;
        }
        assertEquals(340, ids.size());
        assertEquals(186, perZone[Zone.A.ordinal()]);
        assertEquals(43, perZone[Zone.B.ordinal()]);
        assertEquals(36, perZone[Zone.C.ordinal()]);
        assertEquals(75, perZone[Zone.D.ordinal()]);
    }

    @Test
    public void lunchPeakIsBusyWithEverySeatOpen() {
        SeatLayout layout = SeatLayout.get();
        OccupancySimulator sim = new OccupancySimulator();
        SeatState[] st = new SeatState[layout.size()];
        for (Seat s : layout.seats) st[s.index] = sim.stateOf(s, at(12, 30), Mode.LUNCH, false);
        FloorSnapshot snap = new FloorSnapshot(at(12, 30), Schedule.resolve(at(12, 30), null, false), layout.seats, st);
        assertEquals(340, snap.all.open);
        int pct = snap.all.congestionPercent();
        assertTrue("lunch peak should be busy-ish, was " + pct, pct > 40 && pct < 95);
    }

    @Test
    public void breakfastAndLunchOpenEverySeat() {
        SeatLayout layout = SeatLayout.get();
        OccupancySimulator sim = new OccupancySimulator();
        for (Seat s : layout.seats) {
            assertNotEquals(s.id, SeatState.CLOSED, sim.stateOf(s, at(8, 30), Mode.BREAKFAST, false));
            assertNotEquals(s.id, SeatState.CLOSED, sim.stateOf(s, at(12, 30), Mode.LUNCH, false));
        }
    }

    @Test
    public void noSeatOverlapsAnotherSeatOrFurniture() {
        SeatLayout layout = SeatLayout.get();
        for (Seat a : layout.seats) {
            assertTrue(a.id, a.left >= 0 && a.top >= 0 && a.right <= SeatLayout.WIDTH && a.bottom <= SeatLayout.HEIGHT);
            for (Seat o : layout.seats) {
                if (o.index <= a.index) continue;
                assertFalse(a.id + "/" + o.id, a.left < o.right && o.left < a.right && a.top < o.bottom && o.top < a.bottom);
            }
            for (Block bl : layout.blocks) {
                assertFalse(a.id + " on " + bl.kind, a.left < bl.right && bl.left < a.right && a.top < bl.bottom && bl.top < a.bottom);
            }
        }
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
