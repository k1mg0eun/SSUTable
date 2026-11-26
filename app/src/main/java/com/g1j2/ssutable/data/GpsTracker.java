package com.g1j2.ssutable.data;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import com.g1j2.ssutable.core.Geofence;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * 앱이 화면에 떠 있는 동안만 위치를 받아 학생회관 안/밖을 판정한다.
 * 멈추면 판정을 비워서(모름) 자리 비움 규칙이 적용되지 않게 한다.
 */
public final class GpsTracker {
    private static final long INTERVAL_MS = 10_000;
    /** 시작할 때 이보다 오래된 마지막 위치는 쓰지 않는다. */
    private static final long LAST_LOCATION_MAX_AGE_MS = 2 * 60_000;

    private final FusedLocationProviderClient client;
    private final Geofence fence;
    private final Runnable onChange;
    private boolean running;
    /** null이면 아직 위치를 모른다. */
    private Boolean inside;
    private double distance = Double.NaN;

    private final LocationCallback callback = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult result) {
            Location l = result.getLastLocation();
            if (l != null) accept(l);
        }
    };

    /** onChange는 메인 스레드에서 불린다. */
    public GpsTracker(Context context, Geofence fence, Runnable onChange) {
        this.client = LocationServices.getFusedLocationProviderClient(context);
        this.fence = fence;
        this.onChange = onChange;
    }

    /** 위치 권한은 부르는 쪽에서 확인한다. 좌표가 없으면 아무것도 하지 않는다. */
    @SuppressLint("MissingPermission")
    public void start() {
        if (running || !fence.configured()) return;
        running = true;
        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
                .setMinUpdateIntervalMillis(INTERVAL_MS / 2)
                .build();
        try {
            client.getLastLocation().addOnSuccessListener(l -> {
                if (l != null && running && ageMillis(l) <= LAST_LOCATION_MAX_AGE_MS) accept(l);
            });
            client.requestLocationUpdates(req, callback, Looper.getMainLooper())
                    .addOnFailureListener(e -> stop());
        } catch (SecurityException e) {
            running = false;
        }
    }

    public void stop() {
        if (!running) return;
        running = false;
        client.removeLocationUpdates(callback);
        boolean hadValue = inside != null;
        inside = null;
        distance = Double.NaN;
        if (hadValue) onChange.run();
    }

    public boolean configured() {
        return fence.configured();
    }

    /** 학생회관 안이면 TRUE, 밖이면 FALSE, 아직 모르면 null. */
    public Boolean inside() {
        return inside;
    }

    /** 학생회관까지의 마지막 거리(m). 모르면 NaN. */
    public double distance() {
        return distance;
    }

    private void accept(Location l) {
        if (!running) return;
        if (l.hasAccuracy() && l.getAccuracy() > Geofence.MAX_ACCURACY_M) return;
        distance = fence.distanceTo(l.getLatitude(), l.getLongitude());
        inside = Geofence.inside(inside, distance);
        onChange.run();
    }

    private static long ageMillis(Location l) {
        return (SystemClock.elapsedRealtimeNanos() - l.getElapsedRealtimeNanos()) / 1_000_000;
    }
}
