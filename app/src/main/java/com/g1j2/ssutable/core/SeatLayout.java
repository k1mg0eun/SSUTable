package com.g1j2.ssutable.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 학생회관 3층 학생식당 340석 배치.
 * 팀이 그린 배치(1인석 6줄, 소파 부스 9개, 긴 테이블 4개, 6인 테이블 5개, 기둥, 출입구)를 옮겼다.
 * 전체 340석과 1인석 43석은 숭대시보 기사 숫자에 맞췄고,
 * 기억이 흐린 곳(가운데 세로 줄, 오른쪽 아래, 맨 아래 줄)은 340석이 되도록 채웠다.
 *  A 긴 공용 테이블 186석 (12인×2 테이블 7개, 9인×2 테이블 1개)
 *  B 1인 칸막이석     43석 (줄 6개, 전 좌석 콘센트)
 *  C 소파 부스        36석 (9개 부스 × 4)
 *  D 작은 테이블      75석 (6인 5개, 4인 9개, 바 테이블 9석)
 */
public final class SeatLayout {
    public static final float WIDTH = 1000f;
    public static final float HEIGHT = 1720f;

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
    private final Map<Zone, Integer> next = new EnumMap<>(Zone.class);

    public static synchronized SeatLayout get() {
        if (instance == null) instance = new SeatLayout();
        return instance;
    }

    private final List<Seat> s = new ArrayList<>();
    private final List<Block> b = new ArrayList<>();

    private SeatLayout() {
        b.add(new Block(Block.Kind.COUNTER, 20, 16, 509, 62, "배식대 · 식권 확인"));
        b.add(new Block(Block.Kind.DOOR, 96, 140, 229, 160, "출입구"));
        b.add(new Block(Block.Kind.DOOR, 240, 140, 380, 160, "출입구"));
        pillar(8, 132);
        pillar(50, 132);
        for (int i = 0; i < 12; i++) pillar(400 + i * 50, 132);
        pillar(660, 1090);

        // 왼쪽: 1인석 줄 두 열과 소파 부스 한 열을 세 묶음으로
        float[] groups = {255, 612, 970};
        int[] singles = {8, 7, 7, 7, 7, 7};
        for (int g = 0; g < groups.length; g++) {
            singleRow(50, 70, groups[g], singles[g * 2]);
            singleRow(210, 180, groups[g], singles[g * 2 + 1]);
        }
        for (float y0 : groups) {
            for (int k = 0; k < 3; k++) booth(255, y0 + k * 110);
        }

        // 가운데 세로 줄: 바 테이블과 4인 테이블 (채운 것)
        bar(400, 262, 9);
        table4(405, 660);
        table4(405, 790);

        // 오른쪽 위: 긴 테이블 (아래 두 개는 채운 것)
        for (float y : new float[]{270, 400, 535, 670, 800}) longTable(545, y, 12);
        // 가운데: 6인·4인 테이블
        for (float y : new float[]{940, 1060, 1190}) table6(440, y);
        for (float y : new float[]{1040, 1170}) table6(850, y);
        table4(575, 940);
        table4(715, 940);
        table4(575, 1150);
        table4(715, 1150);
        // 아래쪽 (채운 것)
        longTable(545, 1330, 12);
        longTable(60, 1330, 9);
        longTable(50, 1560, 12);
        table4(560, 1560);
        table4(700, 1560);
        table4(840, 1560);

        Map<Zone, Bounds> zb = new EnumMap<>(Zone.class);
        zb.put(Zone.A, new Bounds(535, 250, 990, 900));
        zb.put(Zone.B, new Bounds(40, 245, 235, 1290));
        zb.put(Zone.C, new Bounds(240, 245, 370, 1290));
        zb.put(Zone.D, new Bounds(390, 250, 990, 1300));

        this.seats = Collections.unmodifiableList(s);
        this.blocks = Collections.unmodifiableList(b);
        this.zoneBounds = Collections.unmodifiableMap(zb);
        for (Seat seat : s) byId.put(seat.id, seat);
    }

    private void seat(Zone zone, float x, float y, float w, float h, boolean outlet) {
        int n = next.merge(zone, 1, Integer::sum);
        s.add(new Seat(s.size(), zone.name() + "-" + n, zone, x, y, w, h, outlet));
    }

    private void pillar(float x, float y) {
        b.add(new Block(Block.Kind.PILLAR, x, y, x + 36, y + 36, null));
    }

    /** 벽을 보고 앉는 1인석 count개. desk는 칸막이 책상, seatX는 좌석 열. */
    private void singleRow(float deskX, float seatX, float y0, int count) {
        b.add(new Block(Block.Kind.DESK, deskX, y0, deskX + 12, y0 + 310, null));
        float gap = (310 - 24) / (float) (count - 1);
        for (int i = 0; i < count; i++) seat(Zone.B, seatX, y0 + i * gap, 24, 24, true);
    }

    /** 세로로 긴 바 테이블과 한쪽에 놓인 의자 count개. */
    private void bar(float x, float y, int count) {
        b.add(new Block(Block.Kind.BAR, x + 30, y, x + 46, y + count * 40, null));
        for (int i = 0; i < count; i++) seat(Zone.D, x, y + 8 + i * 40, 24, 24, false);
    }

    /** 세로로 놓인 4인 소파 부스. */
    private void booth(float x, float y) {
        b.add(new Block(Block.Kind.SOFA_BACK, x - 6, y, x - 1, y + 96, null));
        b.add(new Block(Block.Kind.TABLE, x + 26, y + 4, x + 74, y + 92, null));
        b.add(new Block(Block.Kind.SOFA_BACK, x + 101, y, x + 106, y + 96, null));
        seat(Zone.C, x, y + 6, 23, 38, false);
        seat(Zone.C, x, y + 52, 23, 38, false);
        seat(Zone.C, x + 77, y + 6, 23, 38, false);
        seat(Zone.C, x + 77, y + 52, 23, 38, false);
    }

    /** 한쪽에 perSide석씩 앉는 긴 테이블. 양 끝 좌석에 콘센트. */
    private void longTable(float x, float y, int perSide) {
        b.add(new Block(Block.Kind.TABLE, x, y + 30, x + perSide * 36, y + 58, null));
        for (int side = 0; side < 2; side++) {
            float sy = side == 0 ? y : y + 64;
            for (int j = 0; j < perSide; j++) {
                seat(Zone.A, x + 4 + j * 36, sy, 28, 24, j == 0 || j == perSide - 1);
            }
        }
    }

    private void table6(float x, float y) {
        b.add(new Block(Block.Kind.TABLE, x + 2, y + 30, x + 110, y + 64, null));
        for (int side = 0; side < 2; side++) {
            float sy = side == 0 ? y : y + 70;
            for (int j = 0; j < 3; j++) seat(Zone.D, x + 6 + j * 36, sy, 28, 24, false);
        }
    }

    private void table4(float x, float y) {
        b.add(new Block(Block.Kind.TABLE, x + 28, y + 28, x + 82, y + 82, null));
        seat(Zone.D, x + 43, y, 24, 24, false);
        seat(Zone.D, x, y + 43, 24, 24, false);
        seat(Zone.D, x + 86, y + 43, 24, 24, false);
        seat(Zone.D, x + 43, y + 86, 24, 24, false);
    }

    public Seat seat(String id) {
        return byId.get(id);
    }

    public int size() {
        return seats.size();
    }
}
