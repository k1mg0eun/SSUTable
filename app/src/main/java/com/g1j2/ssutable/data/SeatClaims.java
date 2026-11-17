package com.g1j2.ssutable.data;

import com.g1j2.ssutable.core.Reservation;
import com.g1j2.ssutable.core.SeatClaim;

import java.util.HashMap;
import java.util.Map;

/** /seats/{seatId} 항목 ↔ SeatClaim 변환. 숫자는 Long, state는 name() 문자열. */
final class SeatClaims {
    private SeatClaims() {
    }

    static Map<String, Object> toMap(SeatClaim c) {
        Map<String, Object> m = new HashMap<>();
        m.put("uid", c.uid);
        m.put("resId", c.resId);
        m.put("state", c.state.name());
        m.put("since", c.since);
        m.put("deadline", c.deadline);
        return m;
    }

    /** 모양이 깨진 항목(빈 uid·resId·state, 숫자가 아닌 시각)은 버린다. */
    static SeatClaim parse(Object value) {
        if (!(value instanceof Map)) return null;
        Map<?, ?> m = (Map<?, ?>) value;
        Object uid = m.get("uid");
        Object resId = m.get("resId");
        Object state = m.get("state");
        Object since = m.get("since");
        Object deadline = m.get("deadline");
        if (!(uid instanceof String) || !(resId instanceof String) || !(state instanceof String)) return null;
        if (!(since instanceof Number) || !(deadline instanceof Number)) return null;
        SeatClaim c = new SeatClaim();
        try {
            c.state = Reservation.State.valueOf((String) state);
        } catch (IllegalArgumentException e) {
            return null;
        }
        c.uid = (String) uid;
        c.resId = (String) resId;
        c.since = ((Number) since).longValue();
        c.deadline = ((Number) deadline).longValue();
        return c;
    }

    /**
     * 내 예약을 RESET으로 끝낼지. 둘 다 불러온 뒤(loaded) 서버 점유가 사라졌거나 남의 것이면 true.
     * 커밋된 트랜잭션은 서버가 확인한 것이고 이벤트는 순서대로 오므로 "내 점유를 봤는지"는 따지지 않는다.
     */
    static boolean shouldReset(boolean loaded, SeatClaim remote, Reservation mine) {
        return loaded && SeatClaim.lostSeat(remote, mine);
    }
}
