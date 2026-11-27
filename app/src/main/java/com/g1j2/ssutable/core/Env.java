package com.g1j2.ssutable.core;

/** 저장소가 바깥에서 알아야 하는 것들: 시각, 모드, 위치. */
public interface Env {
    long now();

    ModeInfo mode();

    boolean exam();

    /** 학생회관(Geofence.ENTER_RADIUS_M) 안/밖, 아직 모름, 위치를 안 봄. */
    Presence presence();

    /** 시연용: 다음 예약 한 번을 '다른 학생이 먼저 예약함'으로 실패시킨다. */
    boolean takeConflictOnce();
}
