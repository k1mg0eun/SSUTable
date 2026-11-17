package com.g1j2.ssutable.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.ZonedDateTime;

public class ScheduleTest {

    static long at(int h, int m) {
        return ZonedDateTime.of(2026, 11, 10, h, m, 0, 0, Schedule.SEOUL).toInstant().toEpochMilli();
    }

    @Test
    public void modesFollowTimetable() {
        assertEquals(Mode.CLOSED, Schedule.modeAt(at(7, 59), false));
        assertEquals(Mode.BREAKFAST, Schedule.modeAt(at(8, 0), false));
        assertEquals(Mode.STUDY, Schedule.modeAt(at(9, 0), false));
        assertEquals(Mode.LUNCH, Schedule.modeAt(at(11, 20), false));
        assertEquals(Mode.LUNCH, Schedule.modeAt(at(13, 59), false));
        assertEquals(Mode.STUDY, Schedule.modeAt(at(14, 0), false));
        assertEquals(Mode.CLOSED, Schedule.modeAt(at(17, 0), false));
    }

    @Test
    public void examPeriodRunsStudyUntilMidnight() {
        assertEquals(Mode.STUDY, Schedule.modeAt(at(17, 0), true));
        assertEquals(Mode.STUDY, Schedule.modeAt(at(23, 59), true));
    }

    @Test
    public void resolveGivesWindowEndAndNextMode() {
        ModeInfo info = Schedule.resolve(at(12, 30), null, false);
        assertEquals(Mode.LUNCH, info.mode);
        assertFalse(info.forced);
        assertEquals(at(14, 0), info.windowEnd);
        assertEquals(Mode.STUDY, info.nextMode);
    }

    @Test
    public void closedPointsToNextMorning() {
        ModeInfo before = Schedule.resolve(at(6, 0), null, false);
        assertEquals(Mode.CLOSED, before.mode);
        assertEquals(at(8, 0), before.nextOpen);

        ModeInfo after = Schedule.resolve(at(18, 0), null, false);
        assertEquals(at(8, 0) + 24 * 3600_000L, after.nextOpen);
    }

    @Test
    public void forcedModeIgnoresClock() {
        ModeInfo info = Schedule.resolve(at(3, 0), Mode.LUNCH, false);
        assertEquals(Mode.LUNCH, info.mode);
        assertTrue(info.forced);
        assertEquals(-1, info.windowEnd);
    }
}
