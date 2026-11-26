package com.g1j2.ssutable.core;

/**
 * 학생회관 반경 판정. 순수 Java라 단위 테스트로 확인한다.
 * GPS 값은 경계에서 흔들리므로 들어올 때(60m)와 나갈 때(90m) 기준을 다르게 둬서
 * 안/밖이 깜빡이지 않게 한다.
 */
public final class Geofence {
    // 숭실대학교 학생회관 학생식당 위치 (운동장 옆 학생회관, 상도로 369).
    //  NaN으로 두면 GPS 판정을 쓰지 않고, 위치와 상관없이 도착을 확인할 수 있다.
    public static final double BUILDING_LAT = 37.497033;
    public static final double BUILDING_LNG = 126.956785;

    /** 이 거리 안으로 들어오면 '안'. */
    public static final double ENTER_RADIUS_M = 60;
    /** '안'이던 사람은 이 거리를 넘어야 '밖'. */
    public static final double EXIT_RADIUS_M = 90;
    /** 오차가 이보다 큰 위치는 판정에 쓰지 않는다. */
    public static final float MAX_ACCURACY_M = 100;

    private static final double EARTH_RADIUS_M = 6_371_000;

    private final double lat;
    private final double lng;

    public Geofence(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
    }

    /** 학생회관 기준. */
    public static Geofence building() {
        return new Geofence(BUILDING_LAT, BUILDING_LNG);
    }

    /** 좌표가 들어 있는지. */
    public boolean configured() {
        return !Double.isNaN(lat) && !Double.isNaN(lng);
    }

    /** 중심까지의 거리(m). 하버사인 공식. */
    public double distanceTo(double toLat, double toLng) {
        double dLat = Math.toRadians(toLat - lat);
        double dLng = Math.toRadians(toLng - lng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(toLat))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /**
     * 새 거리로 안/밖을 정한다.
     * @param wasInside 직전 판정. null이면 처음이라 들어올 때 기준을 쓴다.
     */
    public static boolean inside(Boolean wasInside, double distanceM) {
        if (Boolean.TRUE.equals(wasInside)) return distanceM <= EXIT_RADIUS_M;
        return distanceM <= ENTER_RADIUS_M;
    }
}
