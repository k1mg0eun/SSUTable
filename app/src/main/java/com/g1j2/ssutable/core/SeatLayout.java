package com.g1j2.ssutable.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 학생회관 3층 학생식당 340석 배치.
 * 팀이 사진을 보고 편집기에서 테이블 하나하나 위치·방향·좌석 수를 맞춘 배치를 그대로 옮겼다.
 * 창가 1인석은 두 줄이 마주 보고, 원형 기둥 3개를 분홍·초록·흰 타원형 테이블이 두른다.
 *  A 흰 테이블    217석
 *  B 1인석·소파    83석 (1인석 43 + 소파석 40, 1인석은 전 좌석 콘센트)
 *  C 분홍 테이블   20석 (첫째 기둥을 두르는 타원형 테이블)
 *  D 초록 테이블   20석 (둘째 기둥을 두르는 타원형 테이블)
 */
public final class SeatLayout {
    public static final float WIDTH = 1000f;
    public static final float HEIGHT = 1800f;

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
    private final List<Seat> s = new ArrayList<>();
    private final List<Block> b = new ArrayList<>();

    public static synchronized SeatLayout get() {
        if (instance == null) instance = new SeatLayout();
        return instance;
    }

    private SeatLayout() {
        // 위쪽: 출입구, 작은 테이블 (오른쪽 흰 테이블 줄과 x를 맞춤)
        b.add(new Block(Block.Kind.DOOR, 60, 15, 160, 35, "출입구"));
        b.add(new Block(Block.Kind.DOOR, 180, 15, 280, 35, "출입구"));
        sideTable(440, 10, 90, 50);
        sideTable(560, 10, 90, 50);
        for (float y : new float[]{10, 100}) {
            table4(670, y, 155, 75);
            table4(845, y, 155, 75);
        }

        // 왼쪽 창가: 창, 같은 간격의 벽돌 기둥, 마주 보는 1인석 두 줄과 소파석 (세 묶음 위아래를 맞춤)
        b.add(new Block(Block.Kind.WINDOW, 0, 100, 10, 1710, null));
        for (float y : new float[]{120, 450, 780, 1110, 1440}) {
            b.add(new Block(Block.Kind.PILLAR, 4, y - 20, 44, y + 20, null));
        }
        int[][] singles = {{9, 9}, {8, 6}, {5, 6}};
        int[] sofas = {16, 12, 12};
        float[][] groups = {{100, 385}, {1110, 270}, {1440, 270}};
        for (int g = 0; g < 3; g++) {
            float y = groups[g][0], h = groups[g][1];
            singleCol(40, y, h, singles[g][0], 0);
            singleCol(150, y, h, singles[g][1], 2);
            sofaCol(200, y, h, sofas[g], 0);
        }
        // 왼쪽 가운데 흰 테이블 (10인 세로 테이블 2×2)
        for (float y : new float[]{565, 850}) {
            colTable(Zone.A, 60, y, 5, 5);
            colTable(Zone.A, 210, y, 5, 5);
        }

        // 가운데: 원형 기둥 3개와 기둥을 두르는 타원형 테이블, 사이 화분
        curveAround(510, 280, Zone.C, 0);
        curveAround(510, 770, Zone.D, 0);
        b.add(new Block(Block.Kind.PLANTER, 450, 505, 570, 545, null));
        b.add(new Block(Block.Kind.PLANTER, 450, 990, 570, 1030, null));

        // 오른쪽 위: 18인 가로 테이블 6개를 같은 간격으로 (마지막은 17석)
        for (int i = 0; i < 6; i++) rowTable(Zone.A, 670, 240 + i * 125, 9, i == 5 ? 8 : 9);

        // 아래쪽: 6인 테이블(왼쪽)과 4인 테이블(오른쪽)을 같은 줄에, 가운데 흰 타원형 테이블
        for (float y : new float[]{1150, 1360, 1570}) {
            rowTable(Zone.A, 400, y, 3, 3);
            rowTable(Zone.A, 910, y, 2, 2);
        }
        curveAround(710, 1360, Zone.A, 2);

        Map<Zone, Bounds> zb = new EnumMap<>(Zone.class);
        zb.put(Zone.A, new Bounds(660, 230, 1000, 960));
        zb.put(Zone.B, new Bounds(30, 90, 310, 1720));
        zb.put(Zone.C, new Bounds(345, 115, 675, 445));
        zb.put(Zone.D, new Bounds(345, 605, 675, 935));

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

    private void seat(Zone zone, float x, float y, float w, float h, boolean outlet) {
        int n = next.merge(zone, 1, Integer::sum);
        s.add(new Seat(s.size(), zone.name() + "-" + n, zone, x, y, w, h, outlet));
    }

    /**
     * 줄 모양 가구의 좌표 변환. u는 줄 길이 방향, v는 폭 방향(폭 width).
     * dir 0: 세로·v가 오른쪽으로, 1: 가로·v가 아래로, 2: 세로·v가 왼쪽으로, 3: 가로·v가 위로.
     */
    private static float[] place(int dir, float x, float y, float width, float u, float v, float lu, float lv) {
        switch (dir) {
            case 1: return new float[]{x + u, y + v, lu, lv};
            case 2: return new float[]{x + width - v - lv, y + u, lv, lu};
            case 3: return new float[]{x + u, y + width - v - lv, lu, lv};
            default: return new float[]{x + v, y + u, lv, lu};
        }
    }

    private void block(Block.Kind kind, float[] r) {
        b.add(new Block(kind, r[0], r[1], r[0] + r[2], r[1] + r[3], null));
    }

    private void seat(Zone zone, float[] r, boolean outlet) {
        seat(zone, r[0], r[1], r[2], r[3], outlet);
    }

    /** 칸막이 1인석 한 줄. dir 0이면 책상이 왼쪽(창 쪽), 2면 오른쪽. */
    private void singleCol(float x, float y, float h, int count, int dir) {
        block(Block.Kind.DESK, place(dir, x, y, 42, 0, 0, h, 12));
        float pitch = (h - 24) / (count - 1);
        for (int i = 0; i < count; i++) seat(Zone.B, place(dir, x, y, 42, i * pitch, 18, 24, 24), true);
    }

    /** 소파 벤치와 앞의 작은 2인 테이블. dir 0이면 소파가 왼쪽. */
    private void sofaCol(float x, float y, float h, int seats, int dir) {
        int pairs = seats / 2;
        float pitch = h / pairs;
        block(Block.Kind.SOFA_BACK, place(dir, x, y, 102, 0, 0, h, 8));
        for (int i = 0; i < pairs; i++) {
            float u = i * pitch + (pitch - 30) / 2;
            seat(Zone.B, place(dir, x, y, 102, u, 10, 30, 26), false);
            block(Block.Kind.TABLE, place(dir, x, y, 102, u + 2, 42, 26, 30));
            seat(Zone.B, place(dir, x, y, 102, u + 3, 78, 24, 24), false);
        }
    }

    /** 가로로 놓인 테이블. 위에 top석, 아래에 bottom석. */
    private void rowTable(Zone zone, float x, float y, int top, int bottom) {
        int n = Math.max(top, bottom);
        b.add(new Block(Block.Kind.TABLE, x, y + 28, x + n * 36, y + 58, null));
        for (int j = 0; j < top; j++) seat(zone, x + 4 + j * 36, y, 28, 24, false);
        for (int j = 0; j < bottom; j++) seat(zone, x + 4 + j * 36, y + 62, 28, 24, false);
    }

    /** 세로로 놓인 테이블. 왼쪽에 left석, 오른쪽에 right석. */
    private void colTable(Zone zone, float x, float y, int left, int right) {
        int n = Math.max(left, right);
        b.add(new Block(Block.Kind.TABLE, x + 28, y, x + 58, y + n * 36, null));
        for (int j = 0; j < left; j++) seat(zone, x, y + 4 + j * 36, 24, 28, false);
        for (int j = 0; j < right; j++) seat(zone, x + 62, y + 4 + j * 36, 24, 28, false);
    }

    /** 2인 테이블: 좌우로 한 명씩. */
    private void sideTable(float x, float y, float w, float h) {
        float cy = y + h / 2;
        float cx = x + w / 2;
        b.add(new Block(Block.Kind.TABLE, cx - 16, cy - 14, cx + 16, cy + 14, null));
        seat(Zone.A, cx - 44, cy - 12, 24, 24, false);
        seat(Zone.A, cx + 20, cy - 12, 24, 24, false);
    }

    /** 4인 테이블: 위아래로 두 명씩. */
    private void table4(float x, float y, float w, float h) {
        float cx = x + w / 2;
        b.add(new Block(Block.Kind.TABLE, cx - 36, y + 26, cx + 36, y + 48, null));
        for (int j = 0; j < 2; j++) {
            seat(Zone.A, cx - 32 + j * 36, y, 28, 24, false);
            seat(Zone.A, cx - 32 + j * 36, y + 51, 28, 24, false);
        }
    }


    /** 원형 기둥과 기둥을 250도 감싸는 타원형 테이블. 안쪽 9석, 바깥쪽 11석. dir 0이면 오른쪽이 트임, 90도씩 시계 방향. */
    private void curveAround(float cx, float cy, Zone zone, int dir) {
        b.add(new Block(Block.Kind.ROUND_PILLAR, cx - 35, cy - 35, cx + 35, cy + 35, null));
        double span = Math.toRadians(250);
        double a0 = Math.PI - span / 2 + dir * Math.PI / 2;
        float r = 110;
        int segments = 24;
        for (int i = 0; i <= segments; i++) {
            double a = a0 + span * i / segments;
            float px = cx + (float) (r * Math.cos(a));
            float py = cy + (float) (r * Math.sin(a));
            b.add(new Block(Block.Kind.TABLE, px - 12, py - 12, px + 12, py + 12, null));
        }
        ring(zone, cx, cy, r - 38, 9, a0, span);
        ring(zone, cx, cy, r + 38, 11, a0, span);
    }

    private void ring(Zone zone, float cx, float cy, float radius, int count, double a0, double span) {
        for (int i = 0; i < count; i++) {
            double a = a0 + span * (i + 0.5) / count;
            float px = cx + (float) (radius * Math.cos(a));
            float py = cy + (float) (radius * Math.sin(a));
            seat(zone, px - 11, py - 11, 22, 22, false);
        }
    }
}
