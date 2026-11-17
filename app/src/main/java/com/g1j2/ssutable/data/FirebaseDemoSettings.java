package com.g1j2.ssutable.data;

import androidx.annotation.NonNull;

import com.g1j2.ssutable.core.DemoSettings;
import com.g1j2.ssutable.core.DemoState;
import com.g1j2.ssutable.core.Mode;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * 모든 폰이 함께 쓰는 시연 설정(/demo). anchorServer는 서버 시각 기준점이다.
 * 서버 시각 오프셋(/.info/serverTimeOffset)도 여기서 듣는다.
 * /demo가 비어 있으면 실제 시각, 배속 1, 자동 모드, 시험기간 아님.
 */
public final class FirebaseDemoSettings implements DemoSettings {
    private final DatabaseReference demoRef;
    private DemoState state = new DemoState();
    private volatile long serverOffset;
    private Runnable listener;
    private boolean started;

    public FirebaseDemoSettings(FirebaseDatabase db) {
        demoRef = db.getReference("demo");
        // .info는 로그인 없이 읽을 수 있다
        db.getReference(".info/serverTimeOffset").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                Object v = snap.getValue();
                serverOffset = v instanceof Number ? ((Number) v).longValue() : 0;
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    /** 서버 시각 = System.currentTimeMillis() + 이 값. */
    public long serverOffset() {
        return serverOffset;
    }

    /** 로그인 뒤 /demo 듣기를 시작한다. */
    public void start() {
        if (started) return;
        started = true;
        demoRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                DemoState next = parse(snap.getValue());
                synchronized (FirebaseDemoSettings.this) {
                    state = next;
                }
                notifyChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // 읽기 실패: 마지막 값(없으면 실제 시각)을 그대로 쓴다
            }
        });
    }

    @Override
    public synchronized DemoState current() {
        return copy(state);
    }

    @Override
    public void write(DemoState next) {
        synchronized (this) {
            state = copy(next);
        }
        Map<String, Object> m = new HashMap<>();
        m.put("anchorServer", next.anchorServer);
        m.put("anchorVirtual", next.anchorVirtual);
        m.put("speed", (long) next.speed);
        if (next.forcedMode != null) m.put("forcedMode", next.forcedMode.name());
        m.put("exam", next.exam);
        m.put("updatedAt", ServerValue.TIMESTAMP);
        demoRef.setValue(m);
        notifyChanged();
    }

    @Override
    public void setListener(Runnable onChange) {
        this.listener = onChange;
    }

    private void notifyChanged() {
        Runnable l = listener;
        if (l != null) l.run();
    }

    private static DemoState parse(Object value) {
        DemoState s = new DemoState();
        if (!(value instanceof Map)) return s;
        Map<?, ?> m = (Map<?, ?>) value;
        s.anchorServer = num(m.get("anchorServer"), -1);
        s.anchorVirtual = num(m.get("anchorVirtual"), -1);
        s.speed = (int) Math.max(1, num(m.get("speed"), 1));
        Object mode = m.get("forcedMode");
        if (mode instanceof String) {
            try {
                s.forcedMode = Mode.valueOf((String) mode);
            } catch (IllegalArgumentException ignored) {
                s.forcedMode = null;
            }
        }
        s.exam = Boolean.TRUE.equals(m.get("exam"));
        return s;
    }

    private static long num(Object v, long fallback) {
        return v instanceof Number ? ((Number) v).longValue() : fallback;
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
}
