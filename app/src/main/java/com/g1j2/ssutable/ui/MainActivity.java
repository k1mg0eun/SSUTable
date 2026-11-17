package com.g1j2.ssutable.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.g1j2.ssutable.App;
import com.g1j2.ssutable.R;
import com.g1j2.ssutable.core.Reservation;
import com.g1j2.ssutable.core.Zone;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/** 하단 탭: 홈, 좌석, 내 예약. */
public class MainActivity extends AppCompatActivity {
    public static final String EXTRA_TAB = "tab";
    public static final String EXTRA_FOCUS_SEAT = "focusSeat";
    public static final String TAB_HOME = "home";
    public static final String TAB_SEATS = "seats";
    public static final String TAB_MY = "my";

    private BottomNavigationView nav;
    private HomeFragment home;
    private SeatMapFragment seats;
    private MyReservationFragment my;
    private final Runnable badgeUpdater = this::updateBadge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!App.get().prefs().registered()) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        nav = findViewById(R.id.nav);

        FragmentManager fm = getSupportFragmentManager();
        if (savedInstanceState == null) {
            home = new HomeFragment();
            seats = new SeatMapFragment();
            my = new MyReservationFragment();
            fm.beginTransaction()
                    .add(R.id.container, home, TAB_HOME)
                    .add(R.id.container, seats, TAB_SEATS)
                    .add(R.id.container, my, TAB_MY)
                    .hide(seats)
                    .hide(my)
                    .commitNow();
        } else {
            home = (HomeFragment) fm.findFragmentByTag(TAB_HOME);
            seats = (SeatMapFragment) fm.findFragmentByTag(TAB_SEATS);
            my = (MyReservationFragment) fm.findFragmentByTag(TAB_MY);
        }

        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) show(home);
            else if (id == R.id.nav_seats) show(seats);
            else show(my);
            return true;
        });

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        App.get().addListener(badgeUpdater);
        updateBadge();
    }

    @Override
    protected void onStop() {
        super.onStop();
        App.get().removeListener(badgeUpdater);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String tab = intent.getStringExtra(EXTRA_TAB);
        String seat = intent.getStringExtra(EXTRA_FOCUS_SEAT);
        if (seat != null) {
            openSeats(null, seat);
        } else if (TAB_MY.equals(tab)) {
            openMy();
        } else if (TAB_SEATS.equals(tab)) {
            openSeats(null, null);
        }
    }

    private void show(Fragment f) {
        getSupportFragmentManager().beginTransaction()
                .hide(home).hide(seats).hide(my)
                .show(f)
                .commitNow();
    }

    public void openSeats(Zone zone, String seatId) {
        nav.setSelectedItemId(R.id.nav_seats);
        if (zone != null) seats.focusZone(zone);
        if (seatId != null) seats.focusSeat(seatId);
    }

    public void openMy() {
        nav.setSelectedItemId(R.id.nav_my);
    }

    public void openHome() {
        nav.setSelectedItemId(R.id.nav_home);
    }

    private void updateBadge() {
        Reservation r = App.get().repo().current();
        boolean active = r != null && r.active();
        if (active) {
            BadgeDrawable badge = nav.getOrCreateBadge(R.id.nav_my);
            badge.setBackgroundColor(getColor(R.color.seat_mine));
            badge.clearNumber();
            badge.setVisible(true);
        } else {
            nav.removeBadge(R.id.nav_my);
        }
    }
}
