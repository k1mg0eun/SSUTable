package com.g1j2.ssutable.ui;

import com.g1j2.ssutable.core.Schedule;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Locale;

/** 화면에 쓰는 시각·시간 표기. 모두 서울 시간 기준. */
public final class Fmt {
    private Fmt() {}

    public static String hm(long millis) {
        ZonedDateTime t = Instant.ofEpochMilli(millis).atZone(Schedule.SEOUL);
        return String.format(Locale.KOREA, "%02d:%02d", t.getHour(), t.getMinute());
    }

    public static String hmMinute(int minuteOfDay) {
        return String.format(Locale.KOREA, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60);
    }

    /** 1시간 이상이면 1:12:05, 아니면 07:41. */
    public static String countdown(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) return String.format(Locale.KOREA, "%d:%02d:%02d", h, m, sec);
        return String.format(Locale.KOREA, "%02d:%02d", m, sec);
    }

    /** 45분, 2시간, 1시간 5분. */
    public static String duration(long ms) {
        long min = Math.max(0, Math.round(ms / 60_000.0));
        long h = min / 60;
        long m = min % 60;
        if (h == 0) return m + "분";
        if (m == 0) return h + "시간";
        return h + "시간 " + m + "분";
    }

    /** 오늘 08:00 / 내일 08:00. */
    public static String day(long now, long t) {
        LocalDate a = Instant.ofEpochMilli(now).atZone(Schedule.SEOUL).toLocalDate();
        LocalDate b = Instant.ofEpochMilli(t).atZone(Schedule.SEOUL).toLocalDate();
        String prefix = a.equals(b) ? "오늘" : a.plusDays(1).equals(b) ? "내일" : b.getMonthValue() + "/" + b.getDayOfMonth();
        return prefix + " " + hm(t);
    }

    /** 홈 상단 시각: 11월 10일 화 12:41 */
    public static String dateTime(long millis) {
        ZonedDateTime t = Instant.ofEpochMilli(millis).atZone(Schedule.SEOUL);
        String[] dow = {"월", "화", "수", "목", "금", "토", "일"};
        return String.format(Locale.KOREA, "%d월 %d일 %s %02d:%02d", t.getMonthValue(), t.getDayOfMonth(),
                dow[t.getDayOfWeek().getValue() - 1], t.getHour(), t.getMinute());
    }
}
