package com.g1j2.ssutable.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.g1j2.ssutable.core.Mode;

/** 사용자 정보, 알림 설정, 시연 모드 설정. */
public final class Prefs {

    /** 설정 화면의 알림 항목. */
    public enum Alert {
        ARRIVAL_SOON("도착 마감 임박"),
        NO_SHOW("미도착 자동 취소"),
        END_SOON("종료 10분 전"),
        AWAY("자리 비움 감지"),
        AWAY_TIMEOUT("이탈 자동 체크아웃"),
        TIME_UP("시간 만료 체크아웃");

        public final String label;

        Alert(String label) {
            this.label = label;
        }
    }

    private final SharedPreferences sp;

    public Prefs(Context context) {
        sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    // 사용자
    public boolean registered() {
        return sp.getString("studentId", null) != null;
    }

    public String name() {
        return sp.getString("name", "");
    }

    public String studentId() {
        return sp.getString("studentId", "");
    }

    public void saveProfile(String name, String studentId) {
        sp.edit().putString("name", name).putString("studentId", studentId).apply();
    }

    public boolean permissionsSeen() {
        return sp.getBoolean("permissionsSeen", false);
    }

    public void setPermissionsSeen() {
        sp.edit().putBoolean("permissionsSeen", true).apply();
    }

    // 알림
    public boolean alert(Alert a) {
        return sp.getBoolean("alert_" + a.name(), true);
    }

    public void setAlert(Alert a, boolean on) {
        sp.edit().putBoolean("alert_" + a.name(), on).apply();
    }

    // 시연 모드
    /** null이면 자동(시간표대로). */
    public Mode forcedMode() {
        String m = sp.getString("forcedMode", "");
        return m.isEmpty() ? null : Mode.valueOf(m);
    }

    public void setForcedMode(Mode mode) {
        sp.edit().putString("forcedMode", mode == null ? "" : mode.name()).apply();
    }

    public boolean exam() {
        return sp.getBoolean("exam", false);
    }

    public void setExam(boolean on) {
        sp.edit().putBoolean("exam", on).apply();
    }

    public boolean inside() {
        return sp.getBoolean("inside", true);
    }

    public void setInside(boolean on) {
        sp.edit().putBoolean("inside", on).apply();
    }

    public boolean demoPanel() {
        return sp.getBoolean("demoPanel", true);
    }

    public void setDemoPanel(boolean on) {
        sp.edit().putBoolean("demoPanel", on).apply();
    }

    public boolean conflictNext() {
        return sp.getBoolean("conflictNext", false);
    }

    public void setConflictNext(boolean on) {
        sp.edit().putBoolean("conflictNext", on).apply();
    }

    // 시계
    public long anchorReal() {
        return sp.getLong("anchorReal", -1);
    }

    public long anchorVirtual() {
        return sp.getLong("anchorVirtual", -1);
    }

    public int speed() {
        return sp.getInt("speed", 1);
    }

    public void saveClock(long anchorReal, long anchorVirtual, int speed) {
        sp.edit().putLong("anchorReal", anchorReal).putLong("anchorVirtual", anchorVirtual)
                .putInt("speed", speed).apply();
    }

    /** 내 정보·알림·위치 설정만 지운다. 시연 설정(시계·배속·모드·시험)은 남긴다. */
    public void clearPersonal() {
        sp.edit().remove("name").remove("studentId").remove("permissionsSeen")
                .remove("inside").remove("demoPanel").remove("conflictNext").apply();
        SharedPreferences.Editor e = sp.edit();
        for (Alert a : Alert.values()) e.remove("alert_" + a.name());
        e.apply();
    }
}
