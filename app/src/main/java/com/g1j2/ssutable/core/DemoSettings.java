package com.g1j2.ssutable.core;

/** 시연 시계·모드·시험 설정의 저장소. 로컬은 Prefs, 이후 서버로 바꿔 끼운다. */
public interface DemoSettings {
    /** 현재 설정. */
    DemoState current();

    /** 새 설정을 저장하고 변경 리스너를 부른다. */
    void write(DemoState next);

    /** 설정이 바뀔 때 불릴 리스너. */
    void setListener(Runnable onChange);
}
