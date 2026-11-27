package com.g1j2.ssutable.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 예약 규칙.
 *  - 예약 후 10분 안에 도착 확인을 안 하면 자동 취소
 *  - 이용 시간은 도착 확인한 시각부터, 모드 최대 시간만큼 (모드 구간 끝에서 자름)
 *  - 스터디는 종료 30분 전부터 1회, 2시간 연장
 *  - 위치 추적 중 학생회관을 벗어나면 자리 비움, 30분 안에 돌아오지 않으면 체크아웃
 */
public final class ReservationEngine {
    public static final long MIN = 60_000L;
    public static final long ARRIVAL_WINDOW = 10 * MIN;
    public static final long ARRIVAL_WARNING = 3 * MIN;
    public static final long AWAY_GRACE = 30 * MIN;
    public static final long EXTEND_OPENS = 30 * MIN;
    public static final long EXTENSION = 120 * MIN;
    public static final long END_WARNING = 10 * MIN;

    public enum Event {
        ARRIVAL_SOON, NO_SHOW, END_SOON, AWAY_STARTED, BACK, AWAY_TIMEOUT, TIME_UP
    }

    public Reservation create(String id, Seat seat, ModeInfo info, long now, String ticketId) {
        Reservation r = new Reservation();
        r.id = id;
        r.seatId = seat.id;
        r.mode = info.mode;
        r.createdAt = now;
        r.arriveBy = now + ARRIVAL_WINDOW;
        r.windowEnd = info.forced ? -1 : info.windowEnd;
        if (info.mode.meal) {
            r.ticket = new Ticket(ticketId, info.mode.ticketName(), now, seat.id, Ticket.Status.UNUSED);
        }
        return r;
    }

    /** 지금 도착 확인을 하면 끝날 시각 (예약 시트에 미리 보여주는 값). */
    public static long plannedEnd(Mode mode, long start, long windowEnd) {
        long end = start + mode.maxMillis();
        return windowEnd > 0 ? Math.min(end, windowEnd) : end;
    }

    /** 위치를 보는 중이면 학생회관 안으로 확인됐을 때만 도착할 수 있다. 위치를 아직 모르면 기다린다. */
    public boolean canArrive(Reservation r, Presence presence) {
        return r.state == Reservation.State.WAITING
                && (presence == Presence.INSIDE || presence == Presence.OFF);
    }

    public void arrive(Reservation r, long now) {
        if (r.state != Reservation.State.WAITING) return;
        r.state = Reservation.State.IN_USE;
        r.checkedInAt = now;
        r.endsAt = plannedEnd(r.mode, now, r.windowEnd);
    }

    /** 연장하면 끝날 시각. clip은 스터디 구간이 끝나는 시각(-1이면 자르지 않음). */
    public long extendedEnd(Reservation r, long clip) {
        long end = r.endsAt + EXTENSION;
        return clip > 0 ? Math.min(end, clip) : end;
    }

    public boolean extendOpen(Reservation r, long now) {
        return r.mode == Mode.STUDY && !r.extended
                && r.state == Reservation.State.IN_USE
                && now >= r.endsAt - EXTEND_OPENS && now < r.endsAt;
    }

    public boolean extend(Reservation r, long now, long clip) {
        if (!extendOpen(r, now)) return false;
        long end = extendedEnd(r, clip);
        if (end <= r.endsAt) return false;
        r.endsAt = end;
        r.extended = true;
        r.warnedEnd = false;
        return true;
    }

    public void checkout(Reservation r, long now) {
        end(r, now, Reservation.EndReason.CHECKOUT);
    }

    public void cancel(Reservation r, long now) {
        end(r, now, Reservation.EndReason.CANCELLED);
    }

    /** 외부 사유(시연 초기화 등)로 예약을 끝낸다. */
    public void endBy(Reservation r, long now, Reservation.EndReason reason) {
        end(r, now, reason);
    }

    /** 시간이 흐르며 일어나는 자동 처리. 일어난 일을 돌려준다. */
    public List<Event> tick(Reservation r, long now, Presence presence) {
        List<Event> events = new ArrayList<>();
        switch (r.state) {
            case WAITING:
                if (now >= r.arriveBy) {
                    end(r, r.arriveBy, Reservation.EndReason.NO_SHOW);
                    events.add(Event.NO_SHOW);
                } else if (!r.warnedArrival && now >= r.arriveBy - ARRIVAL_WARNING) {
                    r.warnedArrival = true;
                    events.add(Event.ARRIVAL_SOON);
                }
                break;
            case IN_USE:
                if (now >= r.endsAt) {
                    end(r, r.endsAt, Reservation.EndReason.TIME_UP);
                    events.add(Event.TIME_UP);
                    break;
                }
                if (presence == Presence.OUTSIDE) {
                    r.state = Reservation.State.AWAY;
                    r.awaySince = now;
                    events.add(Event.AWAY_STARTED);
                    break;
                }
                if (!r.warnedEnd && now >= r.endsAt - END_WARNING) {
                    r.warnedEnd = true;
                    events.add(Event.END_SOON);
                }
                break;
            case AWAY:
                if (now >= r.endsAt) {
                    end(r, r.endsAt, Reservation.EndReason.TIME_UP);
                    events.add(Event.TIME_UP);
                } else if (now >= r.awaySince + AWAY_GRACE) {
                    end(r, r.awaySince + AWAY_GRACE, Reservation.EndReason.AWAY_TIMEOUT);
                    events.add(Event.AWAY_TIMEOUT);
                } else if (presence == Presence.INSIDE || presence == Presence.OFF) {
                    // 위치를 모르는 동안(UNKNOWN)에는 자리 비움을 풀지 않고 30분을 계속 센다.
                    r.state = Reservation.State.IN_USE;
                    r.awaySince = -1;
                    events.add(Event.BACK);
                }
                break;
            default:
                break;
        }
        return events;
    }

    public long awayDeadline(Reservation r) {
        return Math.min(r.awaySince + AWAY_GRACE, r.endsAt);
    }

    private void end(Reservation r, long at, Reservation.EndReason reason) {
        if (r.state == Reservation.State.ENDED) return;
        boolean neverArrived = r.state == Reservation.State.WAITING;
        r.state = Reservation.State.ENDED;
        r.endReason = reason;
        r.endedAt = at;
        if (r.ticket != null && neverArrived) {
            r.ticket.status = Ticket.Status.CANCELLED;
        }
    }
}
