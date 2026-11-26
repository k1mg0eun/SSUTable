package com.g1j2.ssutable.core;

/** 저장소가 바깥에서 알아야 하는 것들: 시각, 모드, 위치. */
public interface Env {
    long now();

    ModeInfo mode();

    boolean exam();

    /** 학생회관 반경(Geofence.ENTER_RADIUS_M) 안에 있는지. */
    boolean inside();

    /** 위치 권한이 있어 위치를 보고 있는지. 없으면 시간 만료로만 반납한다. */
    boolean locationTracking();

    /** 시연용: 다음 예약 한 번을 '다른 학생이 먼저 예약함'으로 실패시킨다. */
    boolean takeConflictOnce();
}
