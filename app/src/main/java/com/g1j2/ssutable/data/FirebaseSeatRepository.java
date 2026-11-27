package com.g1j2.ssutable.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;

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
import com.g1j2.ssutable.core.SeatClaim;
import com.g1j2.ssutable.core.SeatLayout;
import com.g1j2.ssutable.core.SeatRepository;
import com.g1j2.ssutable.core.SeatState;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * 여러 폰이 Firebase Realtime Database로 좌석 점유를 나눠 쓰는 저장소.
 * 렌더 루프는 리스너가 채운 메모리 캐시만 읽는다. 이력은 이 폰의 SharedPreferences에 둔다.
 * 익명 로그인 전에는 CONNECTING이고 가짜 학생 상태만 보이며, 예약은 ERROR.
 */
public final class FirebaseSeatRepository implements SeatRepository {
    private static final long RESERVE_TIMEOUT_MS = 10_000;
    private static final long CONNECT_GRACE_MS = 5_000;

    /** 진행 중인 예약 시도 하나. 콜백은 timeout과 결과 중 먼저 온 쪽이 한 번만 부른다. */
    private static final class Attempt {
        final Reservation reservation;
        final ReserveCallback cb;
        boolean finished;
        Runnable timeout;

        Attempt(Reservation reservation, ReserveCallback cb) {
            this.reservation = reservation;
            this.cb = cb;
        }
    }

    private final HistoryStore history;
    private final Env env;
    private final FirebaseDatabase db;
    private final Prefs profile;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SeatLayout layout = SeatLayout.get();
    private final FloorMerger merger = new FloorMerger(layout, new OccupancySimulator());
    private final ReservationEngine engine = new ReservationEngine();
    private final Random random = new Random();
    private final long createdAt = SystemClock.elapsedRealtime();

    private String uid;
    private boolean authFailed;
    private boolean loadFailed;
    private boolean everConnected;
    private boolean connected;
    private boolean seatsLoaded;
    private boolean currentLoaded;
    private Attempt pending;

    private Map<String, SeatClaim> claims = Collections.emptyMap();
    private Reservation current;
    private FloorSnapshot lastSnapshot;

    public FirebaseSeatRepository(Context context, Env env, FirebaseDatabase db, Prefs profile) {
        this.history = new HistoryStore(context);
        this.env = env;
        this.db = db;
        this.profile = profile;
        db.getReference(".info/connected").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                boolean on = Boolean.TRUE.equals(snap.getValue());
                synchronized (FirebaseSeatRepository.this) {
                    connected = on;
                    if (on) everConnected = true;
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    // ---- 로그인 ----

    /** 익명 로그인 성공. 리스너를 붙이고 내 예약을 복원한다. */
    public void attach(String signedInUid) {
        synchronized (this) {
            if (uid != null) return;
            uid = signedInUid;
            authFailed = false;
        }
        writeProfile();
        profile.setProfileListener(this::writeProfile);
        db.getReference("seats").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                Map<String, SeatClaim> next = new HashMap<>();
                for (DataSnapshot child : snap.getChildren()) {
                    SeatClaim c = SeatClaims.parse(child.getValue());
                    if (c != null) next.put(child.getKey(), c);
                }
                seatsChanged(Collections.unmodifiableMap(next));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                synchronized (FirebaseSeatRepository.this) {
                    loadFailed = true;
                }
            }
        });
        mine().child("current").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                restore(ReservationJson.fromMap(snap.getValue()));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                synchronized (FirebaseSeatRepository.this) {
                    loadFailed = true;
                }
            }
        });
    }

    /** 익명 로그인 실패: 다시 시도해 attach될 때까지 오프라인으로 보이고 예약은 ERROR. */
    public synchronized void authFailed() {
        authFailed = true;
    }

    private DatabaseReference mine() {
        return db.getReference("reservations").child(uid);
    }

    private void writeProfile() {
        String id;
        synchronized (this) {
            id = uid;
        }
        if (id == null) return;
        DatabaseReference ref = db.getReference("users").child(id);
        if (!profile.registered()) {
            ref.removeValue();
            return;
        }
        Map<String, Object> m = new HashMap<>();
        m.put("name", profile.name());
        m.put("studentId", profile.studentId());
        ref.setValue(m);
    }

    private synchronized void restore(Reservation saved) {
        if (current == null && saved != null) {
            current = saved;
            // 꺼져 있던 동안 지난 마감을 먼저 처리한다 (알림은 내지 않음)
            if (current.active() && !engine.tick(current, env.now(), env.presence()).isEmpty()) {
                changed();
                pushMine();
            } else {
                changed();
            }
        }
        currentLoaded = true;
        checkLost();
    }

    private synchronized void seatsChanged(Map<String, SeatClaim> next) {
        claims = next;
        seatsLoaded = true;
        checkLost();
        lastSnapshot = compute();
    }

    /** 내 좌석 점유가 사라졌거나 남의 것이 되면 RESET으로 끝낸다. /seats에는 다시 쓰지 않는다. */
    private void checkLost() {
        if (current == null) return;
        if (!SeatClaims.shouldReset(seatsLoaded && currentLoaded, claims.get(current.seatId), current)) return;
        engine.endBy(current, env.now(), Reservation.EndReason.RESET);
        changed();
        mine().child("current").removeValue();
    }

    // ---- 조회 ----

    @Override
    public synchronized FloorSnapshot snapshot() {
        if (lastSnapshot == null) lastSnapshot = compute();
        return lastSnapshot;
    }

    private FloorSnapshot compute() {
        return merger.merge(env.now(), env.mode(), env.exam(), claims, current);
    }

    @Override
    public synchronized Reservation current() {
        return current;
    }

    @Override
    public synchronized List<Reservation> history() {
        return history.list();
    }

    /** 연결·로그인·첫 불러오기가 CONNECT_GRACE_MS 안에 끝나지 않으면 연결 중 대신 오프라인으로 보인다. */
    @Override
    public synchronized Connection connection() {
        if (authFailed || loadFailed) return Connection.OFFLINE;
        if (uid == null || !everConnected || !seatsLoaded || !currentLoaded) {
            return SystemClock.elapsedRealtime() - createdAt < CONNECT_GRACE_MS
                    ? Connection.CONNECTING : Connection.OFFLINE;
        }
        return connected ? Connection.ONLINE : Connection.OFFLINE;
    }

    // ---- 예약 ----

    @Override
    public void reserve(String seatId, ReserveCallback cb) {
        Attempt attempt;
        ReserveResult early;
        // 트랜잭션은 Firebase 작업 스레드에서 돌므로 판정 시각은 여기(메인)서 잡는다
        long now = env.now();
        synchronized (this) {
            early = check(seatId);
            attempt = null;
            if (early == null) {
                Reservation r = engine.create(newId("R", now), layout.seat(seatId), env.mode(), now, newId("T", now));
                attempt = new Attempt(r, cb);
                pending = attempt;
            }
        }
        if (early != null) {
            deliver(cb, early);
            return;
        }
        Attempt a = attempt;
        a.timeout = () -> finish(a, ReserveResult.ERROR);
        main.postDelayed(a.timeout, RESERVE_TIMEOUT_MS);
        SeatClaim claim = SeatClaim.of(uid, a.reservation);
        db.getReference("seats").child(seatId).runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData data) {
                if (!SeatClaim.takeable(SeatClaims.parse(data.getValue()), now)) return Transaction.abort();
                data.setValue(SeatClaims.toMap(claim));
                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snap) {
                if (error != null) finish(a, ReserveResult.ERROR);
                else if (!committed) finish(a, ReserveResult.TAKEN);
                else committed(a);
            }
        }, false);
    }

    /** 서버에 가기 전 로컬 확인. 통과하면 null. */
    private ReserveResult check(String seatId) {
        if (uid == null || !currentLoaded || loadFailed || pending != null) return ReserveResult.ERROR;
        if (current != null && current.active()) return ReserveResult.ALREADY_HAVE;
        Seat seat = layout.seat(seatId);
        if (seat == null) return ReserveResult.NOT_FOUND;
        ModeInfo info = env.mode();
        if (!info.open()) return ReserveResult.CLOSED;
        lastSnapshot = compute();
        if (env.takeConflictOnce() || lastSnapshot.state(seat) != SeatState.FREE) return ReserveResult.TAKEN;
        return null;
    }

    private void committed(Attempt a) {
        boolean late;
        synchronized (this) {
            late = !close(a);
            if (!late) {
                // 커밋은 서버가 확인한 것. 이후 /seats 이벤트는 커밋 뒤 상태라 바로 사라짐 판정을 해도 된다
                current = a.reservation;
                changed();
                pushReservation();
            }
        }
        if (late) {
            // 이미 ERROR로 알렸으니 늦게 잡힌 좌석은 돌려놓는다
            writeClaim(a.reservation.seatId, a.reservation.id, null);
            return;
        }
        main.removeCallbacks(a.timeout);
        deliver(a.cb, ReserveResult.OK);
    }

    /** 시도를 실패로 끝내고 콜백을 한 번만 부른다. 이미 끝났으면 무시. 락 밖에서 부른다. */
    private void finish(Attempt a, ReserveResult result) {
        synchronized (this) {
            if (!close(a)) return;
        }
        main.removeCallbacks(a.timeout);
        deliver(a.cb, result);
    }

    /** 처음 닫는 쪽만 true. */
    private boolean close(Attempt a) {
        if (a.finished) return false;
        a.finished = true;
        if (pending == a) pending = null;
        return true;
    }

    private void deliver(ReserveCallback cb, ReserveResult result) {
        if (Looper.myLooper() == Looper.getMainLooper()) cb.done(result);
        else main.post(() -> cb.done(result));
    }

    // ---- 내 예약 동작 ----

    @Override
    public synchronized boolean arrive() {
        if (current == null) return false;
        if (!engine.canArrive(current, env.presence())) return false;
        engine.arrive(current, env.now());
        changed();
        pushMine();
        return true;
    }

    @Override
    public synchronized boolean extend() {
        if (current == null) return false;
        boolean ok = engine.extend(current, env.now(), extensionClip(current));
        if (ok) {
            changed();
            pushMine();
        }
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
        pushMine();
    }

    @Override
    public synchronized void cancel() {
        if (current == null || current.state != Reservation.State.WAITING) return;
        engine.cancel(current, env.now());
        changed();
        pushMine();
    }

    @Override
    public synchronized void acknowledgeEnded() {
        if (current != null && !current.active()) {
            current = null;
            changed();
            if (uid != null) mine().child("current").removeValue();
        }
    }

    @Override
    public synchronized List<ReservationEngine.Event> tick() {
        List<ReservationEngine.Event> events = Collections.emptyList();
        if (current != null && current.active()) {
            events = engine.tick(current, env.now(), env.presence());
            if (!events.isEmpty()) {
                changed();
                pushMine();
            }
        }
        lastSnapshot = compute();
        return events;
    }

    @Override
    public synchronized void clearAll() {
        if (uid != null) {
            if (current != null && current.active()) writeClaim(current.seatId, current.id, null);
            mine().removeValue();
        }
        current = null;
        history.clear();
        changed();
    }

    @Override
    public void resetAllSeats() {
        if (uid != null) db.getReference("seats").removeValue();
    }

    // ---- 쓰기 ----

    /** 내 예약과 그 좌석 점유를 서버에 반영한다. 끝난 예약이면 점유를 지운다. */
    private void pushMine() {
        if (uid == null || current == null) return;
        pushReservation();
        writeClaim(current.seatId, current.id, SeatClaim.of(uid, current));
    }

    private void pushReservation() {
        mine().child("current").setValue(ReservationJson.toMap(current));
    }

    /** 그 좌석이 아직 내 예약(resId)의 것일 때만 바꾸거나(next) 지운다(null). 남의 점유는 건드리지 않는다. */
    private void writeClaim(String seatId, String resId, SeatClaim next) {
        db.getReference("seats").child(seatId).runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData data) {
                SeatClaim existing = SeatClaims.parse(data.getValue());
                if (existing == null || !resId.equals(existing.resId)) return Transaction.abort();
                data.setValue(next == null ? null : SeatClaims.toMap(next));
                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snap) {
            }
        });
    }

    // ---- 로컬 반영 ----

    /** 끝난 예약은 로컬 이력에 남기고 스냅샷을 다시 만든다. */
    private void changed() {
        if (current != null && !current.active()) history.record(current);
        lastSnapshot = compute();
    }

    private String newId(String prefix, long now) {
        return String.format(Locale.US, "%s%d%04d", prefix, now / 1000, random.nextInt(10000));
    }
}
