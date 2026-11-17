package com.g1j2.ssutable.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.g1j2.ssutable.core.Env;
import com.g1j2.ssutable.core.FloorMerger;
import com.g1j2.ssutable.core.FloorSnapshot;
import com.g1j2.ssutable.core.Mode;
import com.g1j2.ssutable.core.ModeInfo;
import com.g1j2.ssutable.core.OccupancySimulator;
import com.g1j2.ssutable.core.Reservation;
import com.g1j2.ssutable.core.ReservationEngine;
import com.g1j2.ssutable.core.Schedule;
import com.g1j2.ssutable.core.Seat;
import com.g1j2.ssutable.core.SeatLayout;
import com.g1j2.ssutable.core.SeatRepository;
import com.g1j2.ssutable.core.SeatState;
import com.g1j2.ssutable.core.Ticket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * 폰 한 대로 돌아가는 저장소.
 * 다른 학생들의 좌석은 OccupancySimulator가 만들고, 내 예약은 SharedPreferences에 JSON으로 저장한다.
 */
public final class LocalSeatRepository implements SeatRepository {
    private static final String PREFS = "repo";
    private static final String KEY_CURRENT = "current";
    private static final String KEY_HISTORY = "history";
    private static final int HISTORY_MAX = 30;

    private final SharedPreferences prefs;
    private final Env env;
    private final SeatLayout layout = SeatLayout.get();
    private final FloorMerger merger = new FloorMerger(layout, new OccupancySimulator());
    private final ReservationEngine engine = new ReservationEngine();
    private final Random random = new Random();

    private Reservation current;
    private final List<Reservation> history = new ArrayList<>();
    private FloorSnapshot lastSnapshot;

    public LocalSeatRepository(Context context, Env env) {
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.env = env;
        load();
    }

    // ---- 조회 ----

    @Override
    public synchronized FloorSnapshot snapshot() {
        if (lastSnapshot == null) lastSnapshot = compute();
        return lastSnapshot;
    }

    private FloorSnapshot compute() {
        return merger.merge(env.now(), env.mode(), env.exam(), Collections.emptyMap(), null, current);
    }

    @Override
    public synchronized Reservation current() {
        return current;
    }

    @Override
    public synchronized List<Reservation> history() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    // ---- 동작 ----

    @Override
    public synchronized void reserve(String seatId, ReserveCallback cb) {
        cb.done(reserveNow(seatId));
    }

    @Override
    public Connection connection() {
        return Connection.LOCAL;
    }

    @Override
    public void resetAllSeats() {
        // 로컬엔 서버 점유가 없어 할 일이 없다
    }

    private ReserveResult reserveNow(String seatId) {
        if (current != null && current.active()) return ReserveResult.ALREADY_HAVE;
        Seat seat = layout.seat(seatId);
        if (seat == null) return ReserveResult.NOT_FOUND;
        ModeInfo info = env.mode();
        if (!info.open()) return ReserveResult.CLOSED;

        // 예약 버튼을 누른 순간의 상태로 다시 확인한다 (Firebase에선 트랜잭션이 하는 일)
        lastSnapshot = compute();
        if (env.takeConflictOnce() || lastSnapshot.state(seat) != SeatState.FREE) {
            return ReserveResult.TAKEN;
        }

        long now = env.now();
        current = engine.create(newId("R", now), seat, info, now, newId("T", now));
        changed();
        return ReserveResult.OK;
    }

    @Override
    public synchronized boolean arrive() {
        if (current == null) return false;
        if (!engine.canArrive(current, env.inside(), env.locationTracking())) return false;
        engine.arrive(current, env.now());
        changed();
        return true;
    }

    @Override
    public synchronized boolean extend() {
        if (current == null) return false;
        boolean ok = engine.extend(current, env.now(), extensionClip(current));
        if (ok) changed();
        return ok;
    }

    @Override
    public synchronized long previewExtendedEnd() {
        if (current == null || current.mode != Mode.STUDY || current.extended || current.endsAt < 0) return -1;
        long end = engine.extendedEnd(current, extensionClip(current));
        return end > current.endsAt ? end : -1;
    }

    /** 연장은 스터디 구간이 끝나는 시각(식사 모드 시작·운영 종료)에서 자른다. */
    private long extensionClip(Reservation r) {
        if (env.mode().forced) return -1;
        long probe = r.endsAt - 1;
        if (Schedule.modeAt(probe, env.exam()) != Mode.STUDY) return r.endsAt;
        return Schedule.windowEnd(probe, env.exam());
    }

    @Override
    public synchronized void checkout() {
        if (current == null || !current.active()) return;
        engine.checkout(current, env.now());
        changed();
    }

    @Override
    public synchronized void cancel() {
        if (current == null || current.state != Reservation.State.WAITING) return;
        engine.cancel(current, env.now());
        changed();
    }

    @Override
    public synchronized void acknowledgeEnded() {
        if (current != null && !current.active()) {
            current = null;
            changed();
        }
    }

    @Override
    public synchronized List<ReservationEngine.Event> tick() {
        List<ReservationEngine.Event> events = Collections.emptyList();
        if (current != null && current.active()) {
            events = engine.tick(current, env.now(), env.inside(), env.locationTracking());
            if (!events.isEmpty()) changed();
        }
        lastSnapshot = compute();
        return events;
    }

    @Override
    public synchronized void clearAll() {
        current = null;
        history.clear();
        changed();
    }

    private void changed() {
        if (current != null && !current.active()) {
            boolean recorded = false;
            for (Reservation h : history) {
                if (h.id.equals(current.id)) {
                    recorded = true;
                    break;
                }
            }
            if (!recorded) {
                history.add(0, current);
                while (history.size() > HISTORY_MAX) history.remove(history.size() - 1);
            }
        }
        lastSnapshot = compute();
        save();
    }

    private String newId(String prefix, long now) {
        return String.format(Locale.US, "%s%d%04d", prefix, now / 1000, random.nextInt(10000));
    }

    // ---- 저장 ----

    private void save() {
        try {
            JSONArray h = new JSONArray();
            for (Reservation r : history) h.put(toJson(r));
            prefs.edit()
                    .putString(KEY_CURRENT, current == null ? null : toJson(current).toString())
                    .putString(KEY_HISTORY, h.toString())
                    .apply();
        } catch (JSONException ignored) {
            // 저장 실패해도 화면은 계속 동작한다
        }
    }

    private void load() {
        try {
            String c = prefs.getString(KEY_CURRENT, null);
            current = c == null ? null : fromJson(new JSONObject(c));
            String h = prefs.getString(KEY_HISTORY, null);
            if (h != null) {
                JSONArray arr = new JSONArray(h);
                for (int i = 0; i < arr.length(); i++) history.add(fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException | IllegalArgumentException e) {
            current = null;
            history.clear();
        }
    }

    private static JSONObject toJson(Reservation r) throws JSONException {
        JSONObject o = new JSONObject();
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
        o.put("endReason", r.endReason == null ? null : r.endReason.name());
        o.put("endedAt", r.endedAt);
        o.put("warnedArrival", r.warnedArrival);
        o.put("warnedEnd", r.warnedEnd);
        if (r.ticket != null) {
            JSONObject t = new JSONObject();
            t.put("id", r.ticket.id);
            t.put("name", r.ticket.name);
            t.put("issuedAt", r.ticket.issuedAt);
            t.put("seatId", r.ticket.seatId);
            t.put("status", r.ticket.status.name());
            o.put("ticket", t);
        }
        return o;
    }

    private static Reservation fromJson(JSONObject o) throws JSONException {
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
}
