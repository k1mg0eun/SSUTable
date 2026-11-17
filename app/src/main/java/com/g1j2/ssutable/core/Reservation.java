package com.g1j2.ssutable.core;

/** 내 예약 하나. 상태는 ReservationEngine만 바꾼다. */
public final class Reservation {

    public enum State { WAITING, IN_USE, AWAY, ENDED }

    public enum EndReason {
        CHECKOUT("직접 체크아웃", "체크아웃했어요"),
        TIME_UP("시간 만료", "이용 시간이 끝나 체크아웃됐어요"),
        NO_SHOW("10분 미도착", "10분 안에 도착하지 않아 예약이 취소됐어요"),
        AWAY_TIMEOUT("30분 이탈", "30분 넘게 자리를 비워 체크아웃됐어요"),
        CANCELLED("예약 취소", "예약을 취소했어요"),
        RESET("시연 초기화", "시연 데이터가 초기화돼 예약이 끝났어요");

        public final String label;
        public final String headline;

        EndReason(String label, String headline) {
            this.label = label;
            this.headline = headline;
        }
    }

    public String id;
    public String seatId;
    public Mode mode;
    public long createdAt;
    public long arriveBy;
    /** 예약할 때 모드 구간이 끝나는 시각. 이용 시간은 여기서 자른다. -1이면 자르지 않음. */
    public long windowEnd = -1;

    public State state = State.WAITING;
    public long checkedInAt = -1;
    public long endsAt = -1;
    public boolean extended;
    public long awaySince = -1;

    public EndReason endReason;
    public long endedAt = -1;

    public Ticket ticket;

    public boolean warnedArrival;
    public boolean warnedEnd;

    public boolean active() {
        return state != State.ENDED;
    }

    /** 체크인부터 종료까지 쓴 시간. 체크인 안 했으면 0. */
    public long usedMillis() {
        if (checkedInAt < 0) return 0;
        long end = endedAt > 0 ? endedAt : checkedInAt;
        return Math.max(0, end - checkedInAt);
    }
}
