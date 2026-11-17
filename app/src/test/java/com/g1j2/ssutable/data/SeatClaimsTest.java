package com.g1j2.ssutable.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.g1j2.ssutable.core.Reservation;
import com.g1j2.ssutable.core.SeatClaim;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class SeatClaimsTest {

    private static Map<String, Object> valid() {
        Map<String, Object> m = new HashMap<>();
        m.put("uid", "u1");
        m.put("resId", "R1");
        m.put("state", "IN_USE");
        m.put("since", 100L);
        m.put("deadline", 200L);
        return m;
    }

    @Test
    public void roundTrip() {
        SeatClaim c = SeatClaims.parse(valid());
        assertEquals("u1", c.uid);
        assertEquals("R1", c.resId);
        assertEquals(Reservation.State.IN_USE, c.state);
        assertEquals(100L, c.since);
        assertEquals(200L, c.deadline);
        assertEquals(valid(), SeatClaims.toMap(c));
    }

    @Test
    public void dropsMalformed() {
        assertNull(SeatClaims.parse(null));
        assertNull(SeatClaims.parse("x"));
        for (String key : new String[]{"uid", "resId", "state", "since", "deadline"}) {
            Map<String, Object> m = valid();
            m.remove(key);
            assertNull(key, SeatClaims.parse(m));
        }
        Map<String, Object> badState = valid();
        badState.put("state", "ENDEDX");
        assertNull(SeatClaims.parse(badState));
        Map<String, Object> badTime = valid();
        badTime.put("deadline", "soon");
        assertNull(SeatClaims.parse(badTime));
    }

    @Test
    public void lostSeatSafeOnParsedClaims() {
        Reservation mine = new Reservation();
        mine.id = "R1";
        mine.state = Reservation.State.IN_USE;
        assertEquals(false, SeatClaim.lostSeat(SeatClaims.parse(valid()), mine));
        assertEquals(true, SeatClaim.lostSeat(SeatClaims.parse(new HashMap<>()), mine));
    }
}
