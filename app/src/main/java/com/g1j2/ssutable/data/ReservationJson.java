package com.g1j2.ssutable.data;

import com.g1j2.ssutable.core.Mode;
import com.g1j2.ssutable.core.Reservation;
import com.g1j2.ssutable.core.Ticket;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * 예약 ↔ JSON 변환. 로컬 저장(SharedPreferences)과 Firebase(Map)가 같은 키를 쓴다.
 * enum은 name() 문자열로 저장한다.
 */
public final class ReservationJson {
    private ReservationJson() {
    }

    public static JSONObject toJson(Reservation r) throws JSONException {
        return new JSONObject(toMap(r));
    }

    public static Reservation fromJson(JSONObject o) throws JSONException {
        Reservation r = new Reservation();
        r.id = o.getString("id");
        r.seatId = o.getString("seatId");
        r.mode = Mode.valueOf(o.getString("mode"));
        r.createdAt = o.getLong("createdAt");
        r.arriveBy = o.getLong("arriveBy");
        r.windowEnd = o.getLong("windowEnd");
        r.state = Reservation.State.valueOf(o.getString("state"));
        r.checkedInAt = o.getLong("checkedInAt");
        r.endsAt = o.getLong("endsAt");
        r.extended = o.getBoolean("extended");
        r.awaySince = o.getLong("awaySince");
        String reason = o.optString("endReason", "");
        r.endReason = reason.isEmpty() || "null".equals(reason) ? null : Reservation.EndReason.valueOf(reason);
        r.endedAt = o.getLong("endedAt");
        r.warnedArrival = o.optBoolean("warnedArrival");
        r.warnedEnd = o.optBoolean("warnedEnd");
        JSONObject t = o.optJSONObject("ticket");
        if (t != null) {
            r.ticket = new Ticket(t.getString("id"), t.getString("name"), t.getLong("issuedAt"),
                    t.getString("seatId"), Ticket.Status.valueOf(t.getString("status")));
        }
        return r;
    }

    /** Firebase에 쓰는 모양. 값이 없는 endReason·ticket은 키를 넣지 않는다. */
    public static Map<String, Object> toMap(Reservation r) {
        Map<String, Object> o = new HashMap<>();
        o.put("id", r.id);
        o.put("seatId", r.seatId);
        o.put("mode", r.mode.name());
        o.put("createdAt", r.createdAt);
        o.put("arriveBy", r.arriveBy);
        o.put("windowEnd", r.windowEnd);
        o.put("state", r.state.name());
        o.put("checkedInAt", r.checkedInAt);
        o.put("endsAt", r.endsAt);
        o.put("extended", r.extended);
        o.put("awaySince", r.awaySince);
        if (r.endReason != null) o.put("endReason", r.endReason.name());
        o.put("endedAt", r.endedAt);
        o.put("warnedArrival", r.warnedArrival);
        o.put("warnedEnd", r.warnedEnd);
        if (r.ticket != null) {
            Map<String, Object> t = new HashMap<>();
            t.put("id", r.ticket.id);
            t.put("name", r.ticket.name);
            t.put("issuedAt", r.ticket.issuedAt);
            t.put("seatId", r.ticket.seatId);
            t.put("status", r.ticket.status.name());
            o.put("ticket", t);
        }
        return o;
    }

    /** Firebase에서 읽은 값. 모양이 맞지 않으면 null. */
    public static Reservation fromMap(Object value) {
        if (!(value instanceof Map)) return null;
        try {
            return fromJson(new JSONObject((Map<?, ?>) value));
        } catch (JSONException | RuntimeException e) {
            return null;
        }
    }
}
