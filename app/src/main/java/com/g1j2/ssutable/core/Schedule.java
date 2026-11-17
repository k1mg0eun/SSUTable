package com.g1j2.ssutable.core;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 학생식당 시간표.
 * 평소 08:00~17:00, 시험기간 08:00~24:00.
 * 아침 08:00~09:00, 점심 11:20~14:00, 나머지 운영 시간은 스터디.
 */
public final class Schedule {
    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static final int OPEN = 8 * 60;
    public static final int BREAKFAST_END = 9 * 60;
    public static final int LUNCH_START = 11 * 60 + 20;
    public static final int LUNCH_END = 14 * 60;
    public static final int CLOSE_NORMAL = 17 * 60;
    public static final int CLOSE_EXAM = 24 * 60;

    public static final class Segment {
        public final int start;
        public final int end;
        public final Mode mode;

        Segment(int start, int end, Mode mode) {
            this.start = start;
            this.end = end;
            this.mode = mode;
        }
    }

    private Schedule() {}

    public static int closeMinute(boolean exam) {
        return exam ? CLOSE_EXAM : CLOSE_NORMAL;
    }

    public static List<Segment> segments(boolean exam) {
        List<Segment> list = new ArrayList<>();
        list.add(new Segment(OPEN, BREAKFAST_END, Mode.BREAKFAST));
        list.add(new Segment(BREAKFAST_END, LUNCH_START, Mode.STUDY));
        list.add(new Segment(LUNCH_START, LUNCH_END, Mode.LUNCH));
        list.add(new Segment(LUNCH_END, closeMinute(exam), Mode.STUDY));
        return Collections.unmodifiableList(list);
    }

    /** 하루 중 몇 분째인지에 해당하는 구간. 운영 시간이 아니면 null. */
    public static Segment segmentAt(int minuteOfDay, boolean exam) {
        for (Segment s : segments(exam)) {
            if (minuteOfDay >= s.start && minuteOfDay < s.end) return s;
        }
        return null;
    }

    public static Mode modeAtMinute(int minuteOfDay, boolean exam) {
        Segment s = segmentAt(minuteOfDay, exam);
        return s == null ? Mode.CLOSED : s.mode;
    }

    public static int minuteOfDay(long millis) {
        ZonedDateTime t = Instant.ofEpochMilli(millis).atZone(SEOUL);
        return t.getHour() * 60 + t.getMinute();
    }

    /** millis가 속한 날짜의 minute(0~1440)분 시각. */
    public static long atMinute(long millis, int minute) {
        LocalDate day = Instant.ofEpochMilli(millis).atZone(SEOUL).toLocalDate();
        return day.atStartOfDay(SEOUL).plusMinutes(minute).toInstant().toEpochMilli();
    }

    public static Mode modeAt(long millis, boolean exam) {
        return modeAtMinute(minuteOfDay(millis), exam);
    }

    /** millis가 속한 모드 구간이 끝나는 시각. 운영 시간이 아니면 millis 그대로. */
    public static long windowEnd(long millis, boolean exam) {
        Segment s = segmentAt(minuteOfDay(millis), exam);
        return s == null ? millis : atMinute(millis, s.end);
    }

    /** 다음 운영 시작 시각 (항상 아침 식사 모드로 시작). */
    public static long nextOpen(long millis) {
        int m = minuteOfDay(millis);
        long today = atMinute(millis, OPEN);
        if (m < OPEN) return today;
        return Instant.ofEpochMilli(today).atZone(SEOUL).plusDays(1).toInstant().toEpochMilli();
    }

    /** 현재 모드 정보. forced가 null이면 시간표대로. */
    public static ModeInfo resolve(long now, Mode forced, boolean exam) {
        if (forced != null) {
            long next = forced == Mode.CLOSED
                    ? Instant.ofEpochMilli(atMinute(now, OPEN)).atZone(SEOUL).plusDays(1).toInstant().toEpochMilli()
                    : -1;
            return new ModeInfo(forced, true, -1, next, null);
        }
        Segment s = segmentAt(minuteOfDay(now), exam);
        if (s == null) {
            return new ModeInfo(Mode.CLOSED, false, -1, nextOpen(now), null);
        }
        Segment following = segmentAt(s.end, exam);
        return new ModeInfo(s.mode, false, atMinute(now, s.end), -1,
                following == null ? Mode.CLOSED : following.mode);
    }
}
