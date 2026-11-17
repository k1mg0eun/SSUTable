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

    private static Reservation fresh() {
        Reservation r = new Reservation();
        r.id = "R1";
        r.state = Reservation.State.WAITING;
        return r;
    }

    @Test
    public void freshReservationIsResetWhenItsClaimDisappears() {
        // 트랜잭션 직후 첫 /seats 이벤트를 current가 정해지기 전에 받았더라도, 다음 삭제 이벤트에서 끝나야 한다
        assertEquals(true, SeatClaims.shouldReset(true, null, fresh()));
        Map<String, Object> other = valid();
        other.put("resId", "R2");
        assertEquals(true, SeatClaims.shouldReset(true, SeatClaims.parse(other), fresh()));
    }

    @Test
    public void noResetWhileClaimIsMineOrNotLoaded() {
        assertEquals(false, SeatClaims.shouldReset(true, SeatClaims.parse(valid()), fresh()));
        assertEquals(false, SeatClaims.shouldReset(false, null, fresh()));
        Reservation ended = fresh();
        ended.state = Reservation.State.ENDED;
        assertEquals(false, SeatClaims.shouldReset(true, null, ended));
    }
}
