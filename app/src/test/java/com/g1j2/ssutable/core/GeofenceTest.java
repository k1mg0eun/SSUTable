package com.g1j2.ssutable.core;

import static com.g1j2.ssutable.core.Geofence.ENTER_RADIUS_M;
import static com.g1j2.ssutable.core.Geofence.EXIT_RADIUS_M;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GeofenceTest {
    private final Geofence fence = new Geofence(37.0, 127.0);

    @Test
    public void distanceOfSamePointIsZero() {
        assertEquals(0, fence.distanceTo(37.0, 127.0), 0.001);
    }

    @Test
    public void oneThousandthDegreeOfLatitudeIsAbout111m() {
        assertEquals(111.2, fence.distanceTo(37.001, 127.0), 0.5);
    }

    @Test
    public void longitudeShrinksWithLatitude() {
        // 위도 37도에서 경도 0.001도는 약 88.8m
        assertEquals(88.8, fence.distanceTo(37.0, 127.001), 0.5);
    }

    @Test
    public void radiiAre60And90m() {
        assertEquals(60, ENTER_RADIUS_M, 0);
        assertEquals(90, EXIT_RADIUS_M, 0);
    }

    @Test
    public void firstFixUsesEnterRadius() {
        assertTrue(Geofence.inside(null, ENTER_RADIUS_M));
        assertFalse(Geofence.inside(null, ENTER_RADIUS_M + 1));
    }

    @Test
    public void insideStaysInsideUntilExitRadius() {
        assertTrue(Geofence.inside(true, ENTER_RADIUS_M + 10));
        assertTrue(Geofence.inside(true, EXIT_RADIUS_M));
        assertFalse(Geofence.inside(true, EXIT_RADIUS_M + 1));
    }

    @Test
    public void outsideNeedsEnterRadiusToComeBack() {
        assertFalse(Geofence.inside(false, ENTER_RADIUS_M + 10));
        assertTrue(Geofence.inside(false, ENTER_RADIUS_M - 10));
    }

    @Test
    public void nanCoordinatesAreNotConfigured() {
        assertFalse(new Geofence(Double.NaN, Double.NaN).configured());
        assertTrue(fence.configured());
    }
}
