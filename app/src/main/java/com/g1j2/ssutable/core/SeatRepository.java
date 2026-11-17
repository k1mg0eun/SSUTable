package com.g1j2.ssutable.core;

import java.util.List;

/**
 * 좌석·예약 데이터를 다루는 유일한 창구.
 * 지금은 LocalSeatRepository(폰 한 대)가 구현하고, 나중에 Firebase 구현으로 갈아 끼운다.
 * 화면 코드는 이 인터페이스만 본다.
 */
public interface SeatRepository {

    enum ReserveResult { OK, TAKEN, ALREADY_HAVE, CLOSED, NOT_FOUND, ERROR }

    /** 예약 결과를 받는 콜백. 항상 메인 스레드에서 불린다. */
    interface ReserveCallback { void done(ReserveResult result); }

    /** 서버 연결 상태. 로컬 저장소는 항상 LOCAL. */
    enum Connection { LOCAL, CONNECTING, ONLINE, OFFLINE }

    FloorSnapshot snapshot();

    /** 진행 중이거나, 끝났지만 아직 확인하지 않은 예약. 없으면 null. */
    Reservation current();

    void reserve(String seatId, ReserveCallback cb);

    Connection connection();

    /** 시연용: 모든 좌석 점유를 지운다. 로컬은 아무것도 하지 않는다. */
    void resetAllSeats();

    boolean arrive();

    boolean extend();

    /** 지금 연장하면 끝날 시각. 연장할 수 없는 예약이면 -1. */
    long previewExtendedEnd();

    void checkout();

    void cancel();

    /** 종료 화면에서 확인을 누르면 예약 없음 상태로 돌아간다. */
    void acknowledgeEnded();

    List<Reservation> history();

    /** 시간 흐름에 따른 자동 처리. 앱이 주기적으로 부른다. */
    List<ReservationEngine.Event> tick();

    void clearAll();
}
