package com.g1j2.ssutable.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DemoClockTest {
    private long fakeNow = 1_000_000L;

    @Test
    public void serverOffsetShiftsVirtualTime() {
        long offset = 5_000L;
        DemoClock clock = new DemoClock(() -> fakeNow + offset, 0, 0, 1);
        long anchor = fakeNow + offset;
        clock.apply(anchor, ScheduleTest.at(11, 15), 10);
        fakeNow += 30_000;
        assertEquals(ScheduleTest.at(11, 15) + 300_000, clock.now());
    }

    @Test
    public void twoClocksWithSameStateAgree() {
        long serverAnchor = 2_000_000L;
        long[] localNow = {1_000_000L};
        long[] localNow2 = {9_000_000L};
        // 각자 로컬 시각 + 서버 오프셋 = 서버 시각
        DemoClock a = new DemoClock(() -> localNow[0] + 1_000_000L, 0, 0, 1);
        DemoClock b = new DemoClock(() -> localNow2[0] - 7_000_000L, 0, 0, 1);
        a.apply(serverAnchor, ScheduleTest.at(12, 0), 5);
        b.apply(serverAnchor, ScheduleTest.at(12, 0), 5);
        localNow[0] += 60_000;
        localNow2[0] += 60_000;
        assertEquals(a.now(), b.now());
        assertEquals(ScheduleTest.at(12, 0) + 300_000, a.now());
    }

    @Test
    public void applyMinusOneMeansRealTime() {
        DemoClock clock = new DemoClock(() -> fakeNow, 0, 0, 1);
        clock.apply(-1, -1, 1);
        assertEquals(fakeNow, clock.now());
        fakeNow += 1234;
        assertEquals(fakeNow, clock.now());
    }
}
