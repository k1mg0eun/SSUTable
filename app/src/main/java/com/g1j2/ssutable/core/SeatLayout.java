package com.g1j2.ssutable.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 학생회관 3층 학생식당 340석 배치.
 * 실제 도면이 없어 구역 비율만 맞춘 배치다.
 *  A 긴 공용 테이블 168석 (7개 테이블 × 24)
 *  B 1인 칸막이석     43석 (왼쪽 벽, 전 좌석 콘센트)
 *  C 소파 부스        64석 (16개 부스 × 4)
 *  D 창가 라운지      65석 (창가 바 25 + 4인 테이블 10개)
 */
public final class SeatLayout {
    public static final float WIDTH = 1000f;
    public static final float HEIGHT = 1520f;
    public static final float ENTRANCE_LEFT = 430f;
    public static final float ENTRANCE_RIGHT = 570f;

    public static final class Bounds {
        public final float left, top, right, bottom;

        Bounds(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    private static SeatLayout instance;

    public final List<Seat> seats;
    public final List<Block> blocks;
    public final Map<Zone, Bounds> zoneBounds;
    private final Map<String, Seat> byId = new HashMap<>();

    public static synchronized SeatLayout get() {
        if (instance == null) instance = new SeatLayout();
        return instance;
    }

    private SeatLayout() {
        List<Seat> s = new ArrayList<>();
        List<Block> b = new ArrayList<>();

        b.add(new Block(Block.Kind.COUNTER, 140, 24, 860, 70, "배식대 · 식권 확인"));

        // B: 1인 칸막이석, 왼쪽 벽을 보고 앉는다
        b.add(new Block(Block.Kind.DESK, 8, 104, 22, 1400, null));
        for (int i = 0; i < 43; i++) {
            float y = 110 + i * 30;
            s.add(new Seat(s.size(), "B-" + (i + 1), Zone.B, 26, y, 24, 24, true, false));
        }

        // A: 긴 공용 테이블 7개, 한쪽에 12석씩
        int n = 1;
        for (int t = 0; t < 7; t++) {
            float y0 = 110 + t * 112;
            b.add(new Block(Block.Kind.TABLE, 100, y0 + 28, 532, y0 + 52, null));
            for (int side = 0; side < 2; side++) {
                float y = side == 0 ? y0 : y0 + 56;
                for (int j = 0; j < 12; j++) {
                    boolean outlet = j == 0 || j == 11;
                    s.add(new Seat(s.size(), "A-" + n++, Zone.A, 104 + j * 36, y, 28, 24, outlet, false));
                }
            }
        }

        // C: 소파 부스 4 × 4
        n = 1;
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                float bx = 100 + c * 110;
                float by = 904 + r * 122;
                b.add(new Block(Block.Kind.SOFA_BACK, bx, by - 8, bx + 92, by - 3, null));
                b.add(new Block(Block.Kind.TABLE, bx + 4, by + 27, bx + 88, by + 55, null));
                b.add(new Block(Block.Kind.SOFA_BACK, bx, by + 85, bx + 92, by + 90, null));
                s.add(new Seat(s.size(), "C-" + n++, Zone.C, bx + 6, by, 38, 23, false, false));
                s.add(new Seat(s.size(), "C-" + n++, Zone.C, bx + 48, by, 38, 23, false, false));
                s.add(new Seat(s.size(), "C-" + n++, Zone.C, bx + 6, by + 59, 38, 23, false, false));
                s.add(new Seat(s.size(), "C-" + n++, Zone.C, bx + 48, by + 59, 38, 23, false, false));
            }
        }

        // D: 창가 바 25석 (오른쪽 벽)
        b.add(new Block(Block.Kind.BAR, 966, 104, 986, 1390, null));
        n = 1;
        for (int i = 0; i < 25; i++) {
            float y = 110 + i * 52;
            s.add(new Seat(s.size(), "D-" + n, Zone.D, 936, y, 24, 24, true, n <= 4));
            n++;
        }
        // D: 4인 테이블 10개
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 2; c++) {
                float tx = 608 + c * 160;
                float ty = 130 + r * 250;
                b.add(new Block(Block.Kind.TABLE, tx + 28, ty + 28, tx + 84, ty + 84, null));
                boolean lunchClosed = r == 0;
                s.add(new Seat(s.size(), "D-" + n++, Zone.D, tx + 44, ty, 24, 24, false, lunchClosed));
                s.add(new Seat(s.size(), "D-" + n++, Zone.D, tx, ty + 44, 24, 24, false, lunchClosed));
                s.add(new Seat(s.size(), "D-" + n++, Zone.D, tx + 88, ty + 44, 24, 24, false, lunchClosed));
                s.add(new Seat(s.size(), "D-" + n++, Zone.D, tx + 44, ty + 88, 24, 24, false, lunchClosed));
            }
        }

        Map<Zone, Bounds> zb = new EnumMap<>(Zone.class);
        zb.put(Zone.A, new Bounds(92, 96, 545, 872));
        zb.put(Zone.B, new Bounds(0, 96, 70, 1410));
        zb.put(Zone.C, new Bounds(92, 882, 545, 1386));
        zb.put(Zone.D, new Bounds(592, 96, 1000, 1410));

        this.seats = Collections.unmodifiableList(s);
        this.blocks = Collections.unmodifiableList(b);
        this.zoneBounds = Collections.unmodifiableMap(zb);
        for (Seat seat : s) byId.put(seat.id, seat);
    }

    public Seat seat(String id) {
        return byId.get(id);
    }

    public int size() {
        return seats.size();
    }
}
