package com.g1j2.ssutable;

import android.Manifest;
import android.app.Application;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import com.g1j2.ssutable.core.DemoClock;
import com.g1j2.ssutable.core.Env;
import com.g1j2.ssutable.core.Mode;
import com.g1j2.ssutable.core.ModeInfo;
import com.g1j2.ssutable.core.ReservationEngine;
import com.g1j2.ssutable.core.Schedule;
import com.g1j2.ssutable.core.SeatRepository;
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
        if (prefs.anchorReal() < 0) {
            clock = DemoClock.system();
        } else {
            clock = new DemoClock(System::currentTimeMillis, prefs.anchorReal(), prefs.anchorVirtual(), prefs.speed());
        }
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

    // ---- 시연용 시계 조작 ----

    public void setSpeed(int speed) {
        clock.setSpeed(speed);
        saveClock();
    }

    public void jumpToMinute(int minuteOfDay) {
        clock.jumpTo(Schedule.atMinute(clock.now(), minuteOfDay));
        saveClock();
    }

    public void resetClock() {
        clock.resetToReal();
        saveClock();
    }

    private void saveClock() {
        prefs.saveClock(clock.anchorReal(), clock.anchorVirtual(), clock.speed());
        tickNow();
    }

    public void resetAll() {
        repo.clearAll();
        prefs.clearAll();
        clock = DemoClock.system();
    }

    // ---- Env ----

    @Override
    public long now() {
        return clock.now();
    }

    @Override
    public ModeInfo mode() {
        Mode forced = prefs.forcedMode();
        return Schedule.resolve(now(), forced, prefs.exam());
    }

    @Override
    public boolean exam() {
        return prefs.exam();
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
