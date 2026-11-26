package com.g1j2.ssutable;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.g1j2.ssutable.core.DemoClock;
import com.g1j2.ssutable.core.DemoSettings;
import com.g1j2.ssutable.core.DemoState;
import com.g1j2.ssutable.core.Env;
import com.g1j2.ssutable.core.Geofence;
import com.g1j2.ssutable.core.Mode;
import com.g1j2.ssutable.core.ModeInfo;
import com.g1j2.ssutable.core.ReservationEngine;
import com.g1j2.ssutable.core.Schedule;
import com.g1j2.ssutable.core.SeatRepository;
import com.g1j2.ssutable.data.FirebaseDemoSettings;
import com.g1j2.ssutable.data.FirebaseSeatRepository;
import com.g1j2.ssutable.data.GpsTracker;
import com.g1j2.ssutable.data.LocalDemoSettings;
import com.g1j2.ssutable.data.LocalSeatRepository;
import com.g1j2.ssutable.data.Prefs;
import com.g1j2.ssutable.ui.Notifier;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 앱 전체에서 하나만 있는 것들: 시계, 설정, 저장소.
 * 0.25초마다 자동 반납 규칙을 돌리고 화면들에 다시 그리라고 알린다.
 */
public class App extends Application implements Env {
    private static final long TICK_MS = 250;
    private static final long SIGN_IN_RETRY_MS = 5_000;
    private static final long SIGN_IN_RETRY_MAX_MS = 60_000;
    private static App instance;

    private Prefs prefs;
    private DemoSettings demo;
    /** Firebase 모드일 때만 있다. 서버 시각 오프셋을 준다. */
    private FirebaseDemoSettings sharedDemo;
    private DemoClock clock;
    private SeatRepository repo;
    /** Firebase 모드일 때만 있다. */
    private FirebaseSeatRepository firebaseRepo;
    private boolean signedIn;
    private boolean signingIn;
    private long signInBackoff = SIGN_IN_RETRY_MS;
    private Notifier notifier;
    private GpsTracker gps;
    private int startedActivities;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private final Runnable retrySignIn = this::signIn;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tickNow();
            handler.postDelayed(this, TICK_MS);
        }
    };

    public static App get() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        prefs = new Prefs(this);
        long t = realNow();
        clock = new DemoClock(this::realNow, t, t, 1);
        if (FirebaseApp.initializeApp(this) == null) {
            // google-services.json 없음: 폰 한 대 로컬 모드
            demo = new LocalDemoSettings(prefs);
            repo = new LocalSeatRepository(this, this);
        } else {
            startFirebase();
        }
        applyDemo();
        demo.setListener(() -> {
            applyDemo();
            tickNow();
        });
        notifier = new Notifier(this);
        gps = new GpsTracker(this, Geofence.building(), this::tickNow);
        registerActivityLifecycleCallbacks(new ForegroundCounter());
        handler.post(ticker);
    }

    /** 화면에 떠 있는 동안만 위치를 받는다. 권한·시연 설정이 바뀌면 다시 부른다. */
    public void refreshLocation() {
        boolean want = startedActivities > 0
                && prefs.locationSource() == Prefs.LocationSource.GPS
                && hasLocationPermission();
        if (want) gps.start();
        else gps.stop();
    }

    public GpsTracker gps() {
        return gps;
    }

    /** 앱이 화면에 있는지 센다. */
    private final class ForegroundCounter implements ActivityLifecycleCallbacks {
        @Override
        public void onActivityStarted(@NonNull Activity activity) {
            startedActivities++;
            refreshLocation();
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
            startedActivities = Math.max(0, startedActivities - 1);
            refreshLocation();
        }

        @Override
        public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) {}

        @Override
        public void onActivityResumed(@NonNull Activity activity) {}

        @Override
        public void onActivityPaused(@NonNull Activity activity) {}

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {}
    }

    /** 디스크 캐시를 끄고, 익명 로그인 뒤 리스너를 붙인다. 그 전까지는 CONNECTING. */
    private void startFirebase() {
        FirebaseDatabase db = FirebaseDatabase.getInstance();
        db.setPersistenceEnabled(false);
        sharedDemo = new FirebaseDemoSettings(db);
        demo = sharedDemo;
        firebaseRepo = new FirebaseSeatRepository(this, this, db, prefs);
        repo = firebaseRepo;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            onSignedIn(user.getUid());
            return;
        }
        // 로그인이 실패해도 서버에 다시 붙으면(또는 잠시 뒤) 다시 시도한다
        db.getReference(".info/connected").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                if (!Boolean.TRUE.equals(snap.getValue())) return;
                handler.removeCallbacks(retrySignIn);
                signInBackoff = SIGN_IN_RETRY_MS;
                signIn();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
        signIn();
    }

    /** 익명 로그인 한 번. 이미 됐거나 진행 중이면 무시. 실패하면 점점 길게 기다렸다 다시 한다. */
    private void signIn() {
        if (signedIn || signingIn) return;
        signingIn = true;
        FirebaseAuth.getInstance().signInAnonymously().addOnCompleteListener(task -> {
            signingIn = false;
            FirebaseUser u = task.isSuccessful() && task.getResult() != null ? task.getResult().getUser() : null;
            if (u != null) {
                onSignedIn(u.getUid());
                return;
            }
            firebaseRepo.authFailed();
            handler.removeCallbacks(retrySignIn);
            handler.postDelayed(retrySignIn, signInBackoff);
            signInBackoff = Math.min(signInBackoff * 2, SIGN_IN_RETRY_MAX_MS);
        });
    }

    private void onSignedIn(String uid) {
        if (signedIn) return;
        signedIn = true;
        handler.removeCallbacks(retrySignIn);
        sharedDemo.start();
        firebaseRepo.attach(uid);
    }

    /** 실제 시각. Firebase 모드면 서버 시각에 맞춘 값(로컬은 오프셋 0). */
    public long realNow() {
        return System.currentTimeMillis() + (sharedDemo == null ? 0 : sharedDemo.serverOffset());
    }

    public Prefs prefs() {
        return prefs;
    }

    public SeatRepository repo() {
        return repo;
    }

    public DemoClock clock() {
        return clock;
    }

    public void addListener(Runnable r) {
        listeners.add(r);
    }

    public void removeListener(Runnable r) {
        listeners.remove(r);
    }

    /** 설정을 바꾼 직후 바로 반영되게 한 번 돈다. */
    public void tickNow() {
        List<ReservationEngine.Event> events = repo.tick();
        for (ReservationEngine.Event e : events) notifier.post(e, repo.current());
        for (Runnable r : listeners) r.run();
    }

    // ---- 시연 설정 (시계·모드·시험) ----

    public DemoSettings demo() {
        return demo;
    }

    private void applyDemo() {
        DemoState d = demo.current();
        clock.apply(d.anchorServer, d.anchorVirtual, d.speed);
    }

    /** 지금 시계 값을 기준점으로 한 새 설정을 만든다. */
    private DemoState nextState() {
        DemoState next = demo.current();
        next.anchorServer = realNow();
        next.anchorVirtual = clock.now();
        return next;
    }

    public void setSpeed(int speed) {
        DemoState next = nextState();
        next.speed = Math.max(1, speed);
        demo.write(next);
    }

    public void jumpToMinute(int minuteOfDay) {
        DemoState next = nextState();
        next.anchorVirtual = Schedule.atMinute(clock.now(), minuteOfDay);
        demo.write(next);
    }

    public void resetClock() {
        DemoState next = nextState();
        next.anchorVirtual = next.anchorServer;
        demo.write(next);
    }

    public void setForcedMode(Mode mode) {
        DemoState next = demo.current();
        next.forcedMode = mode;
        demo.write(next);
    }

    public void setExam(boolean on) {
        DemoState next = demo.current();
        next.exam = on;
        demo.write(next);
    }

    /** 내 폰 데이터만 지운다. 시연 설정(시계·배속·모드·시험)은 남긴다. */
    public void resetAll() {
        repo.clearAll();
        prefs.clearPersonal();
    }

    // ---- Env ----

    @Override
    public long now() {
        return clock.now();
    }

    @Override
    public ModeInfo mode() {
        DemoState d = demo.current();
        return Schedule.resolve(now(), d.forcedMode, d.exam);
    }

    @Override
    public boolean exam() {
        return demo.current().exam;
    }

    @Override
    public boolean inside() {
        switch (prefs.locationSource()) {
            case INSIDE:
                return true;
            case OUTSIDE:
                return false;
            default:
                return Boolean.TRUE.equals(gps.inside());
        }
    }

    /** GPS 모드에서는 위치를 실제로 알 때만 추적 중. 모르면 자리 비움 규칙을 쓰지 않는다. */
    @Override
    public boolean locationTracking() {
        if (!hasLocationPermission()) return false;
        return prefs.locationSource() != Prefs.LocationSource.GPS || gps.inside() != null;
    }

    @Override
    public boolean takeConflictOnce() {
        if (!prefs.conflictNext()) return false;
        prefs.setConflictNext(false);
        return true;
    }

    public boolean hasLocationPermission() {
        return granted(Manifest.permission.ACCESS_FINE_LOCATION)
                || granted(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    public boolean hasNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < 33) {
            return androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled();
        }
        return granted(Manifest.permission.POST_NOTIFICATIONS);
    }

    private boolean granted(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }
}
