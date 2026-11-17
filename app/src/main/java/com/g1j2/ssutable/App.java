package com.g1j2.ssutable;

import android.Manifest;
import android.app.Application;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import com.g1j2.ssutable.core.DemoClock;
import com.g1j2.ssutable.core.DemoSettings;
import com.g1j2.ssutable.core.DemoState;
import com.g1j2.ssutable.core.Env;
import com.g1j2.ssutable.core.Mode;
import com.g1j2.ssutable.core.ModeInfo;
import com.g1j2.ssutable.core.ReservationEngine;
import com.g1j2.ssutable.core.Schedule;
import com.g1j2.ssutable.core.SeatRepository;
import com.g1j2.ssutable.data.LocalDemoSettings;
import com.g1j2.ssutable.data.LocalSeatRepository;
import com.g1j2.ssutable.data.Prefs;
import com.g1j2.ssutable.ui.Notifier;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 앱 전체에서 하나만 있는 것들: 시계, 설정, 저장소.
 * 0.25초마다 자동 반납 규칙을 돌리고 화면들에 다시 그리라고 알린다.
 */
public class App extends Application implements Env {
    private static final long TICK_MS = 250;
    private static App instance;

    private Prefs prefs;
    private DemoSettings demo;
    private DemoClock clock;
    private SeatRepository repo;
    private Notifier notifier;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

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
        clock = DemoClock.system();
        demo = new LocalDemoSettings(prefs);
        applyDemo();
        demo.setListener(() -> {
            applyDemo();
            tickNow();
        });
        repo = new LocalSeatRepository(this, this);
        notifier = new Notifier(this);
        handler.post(ticker);
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
        next.anchorServer = System.currentTimeMillis();
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
        return prefs.inside();
    }

    @Override
    public boolean locationTracking() {
        return hasLocationPermission();
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
