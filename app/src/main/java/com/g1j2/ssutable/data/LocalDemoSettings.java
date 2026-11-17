package com.g1j2.ssutable.data;

import com.g1j2.ssutable.core.DemoSettings;
import com.g1j2.ssutable.core.DemoState;

/** Prefs에 저장하는 시연 설정. anchorServer는 이 기기의 System.currentTimeMillis 기준점이다. */
public final class LocalDemoSettings implements DemoSettings {
    private final Prefs prefs;
    private DemoState state;
    private Runnable listener;

    public LocalDemoSettings(Prefs prefs) {
        this.prefs = prefs;
        this.state = copy(load());
    }

    private DemoState load() {
        DemoState s = new DemoState();
        s.anchorServer = prefs.anchorReal();
        s.anchorVirtual = prefs.anchorVirtual();
        s.speed = prefs.speed();
        s.forcedMode = prefs.forcedMode();
        s.exam = prefs.exam();
        return s;
    }

    private static DemoState copy(DemoState s) {
        DemoState c = new DemoState();
        c.anchorServer = s.anchorServer;
        c.anchorVirtual = s.anchorVirtual;
        c.speed = s.speed;
        c.forcedMode = s.forcedMode;
        c.exam = s.exam;
        return c;
    }

    @Override
    public synchronized DemoState current() {
        return copy(state);
    }

    @Override
    public void write(DemoState next) {
        synchronized (this) {
            state = copy(next);
            prefs.saveClock(next.anchorServer, next.anchorVirtual, next.speed);
            prefs.setForcedMode(next.forcedMode);
            prefs.setExam(next.exam);
        }
        if (listener != null) listener.run();
    }

    @Override
    public void setListener(Runnable onChange) {
        this.listener = onChange;
    }
}
