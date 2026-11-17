package com.g1j2.ssutable.ui;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.g1j2.ssutable.App;
import com.g1j2.ssutable.R;
import com.g1j2.ssutable.core.FloorSnapshot;
import com.g1j2.ssutable.core.Reservation;
import com.g1j2.ssutable.core.Seat;
import com.g1j2.ssutable.core.SeatState;
import com.g1j2.ssutable.core.Zone;
import com.g1j2.ssutable.ui.view.SeatMapView;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;

import java.util.Locale;

/** S-04 좌석 맵. 빈자리를 누르면 예약 시트가 올라온다. */
public class SeatMapFragment extends LiveFragment implements SeatMapView.Listener {
    private SeatMapView map;
    private SeekBar zoom;
    private TextView zoomLabel, modeTag, freeCount, closedBanner;
    private Zone pendingZone;
    private String pendingSeat;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_seat_map, container, false);
        map = v.findViewById(R.id.map);
        zoom = v.findViewById(R.id.zoom);
        zoomLabel = v.findViewById(R.id.zoomLabel);
        modeTag = v.findViewById(R.id.modeTag);
        freeCount = v.findViewById(R.id.freeCount);
        closedBanner = v.findViewById(R.id.closedBanner);

        map.setListener(this);
        zoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                float value = 1f + progress / 100f;
                if (fromUser) map.setZoom(value);
                zoomLabel.setText(String.format(Locale.KOREA, "%.1f×", value));
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        zoomLabel.setText("1.0×");

        Chip empty = v.findViewById(R.id.filterEmpty);
        Chip outlet = v.findViewById(R.id.filterOutlet);
        empty.setOnCheckedChangeListener((c, on) -> map.setEmptyOnly(on));
        outlet.setOnCheckedChangeListener((c, on) -> map.setOutletOnly(on));

        buildLegend(v.findViewById(R.id.legend));

        if (pendingZone != null) map.focusZone(pendingZone);
        if (pendingSeat != null) map.focusSeat(pendingSeat);
        pendingZone = null;
        pendingSeat = null;
        return v;
    }

    private void buildLegend(LinearLayout legend) {
        float dp = getResources().getDisplayMetrics().density;
        SeatState[] states = {SeatState.FREE, SeatState.RESERVED, SeatState.IN_USE, SeatState.MINE, SeatState.CLOSED};
        int[] fills = {R.color.seat_free, R.color.seat_reserved, R.color.seat_in_use, R.color.seat_mine, R.color.seat_closed};
        for (int i = 0; i < states.length; i++) {
            TextView t = new TextView(requireContext(), null, 0, R.style.Text_Caption);
            t.setText(states[i].label);
            t.setTextColor(ContextCompat.getColor(requireContext(), R.color.ink2));
            GradientDrawable sw = new GradientDrawable();
            sw.setColor(ContextCompat.getColor(requireContext(), fills[i]));
            sw.setCornerRadius(3 * dp);
            sw.setSize((int) (12 * dp), (int) (12 * dp));
            if (states[i] == SeatState.FREE) {
                sw.setStroke((int) Math.max(1, dp), ContextCompat.getColor(requireContext(), R.color.seat_free_stroke));
            }
            t.setCompoundDrawablesRelativeWithIntrinsicBounds(sw, null, null, null);
            t.setCompoundDrawablePadding((int) (5 * dp));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.setMarginStart((int) (12 * dp));
            legend.addView(t, lp);
        }
    }

    public void focusZone(Zone zone) {
        if (map == null) pendingZone = zone;
        else map.focusZone(zone);
    }

    public void focusSeat(String seatId) {
        if (map == null) pendingSeat = seatId;
        else map.focusSeat(seatId);
    }

    @Override
    protected void render() {
        FloorSnapshot snap = App.get().repo().snapshot();
        map.setSnapshot(snap);
        ModeColors.tag(modeTag, snap.mode.mode);
        boolean open = snap.mode.open();
        freeCount.setText(open ? "빈자리 " + snap.all.free + "석" : "");
        closedBanner.setVisibility(open ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onSeatTap(Seat seat) {
        FloorSnapshot snap = App.get().repo().snapshot();
        SeatState st = snap.state(seat);
        map.setSelected(seat.id);
        switch (st) {
            case FREE:
                if (snap.mode.open()) {
                    ReserveSheet.newInstance(seat.id).show(getChildFragmentManager(), "reserve");
                }
                break;
            case MINE: {
                Reservation r = App.get().repo().current();
                Snackbar.make(map, seat.id + "는 내 자리예요", Snackbar.LENGTH_SHORT)
                        .setAction("내 예약", x -> main().openMy())
                        .setActionTextColor(ContextCompat.getColor(requireContext(), R.color.seat_reserved))
                        .show();
                break;
            }
            case RESERVED:
                toast(seat.id + "는 다른 학생이 예약했어요. 빈자리를 골라 주세요.");
                break;
            case IN_USE:
                toast(seat.id + "는 사용 중이에요. 빈자리를 골라 주세요.");
                break;
            case CLOSED:
                toast(snap.mode.open()
                        ? seat.id + "는 이번 모드에 열지 않는 자리예요"
                        : "운영 시간이 아니라 예약할 수 없어요");
                break;
        }
    }

    private void toast(String msg) {
        Snackbar.make(map, msg, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onZoomChanged(float z) {
        zoom.setProgress(Math.round((Math.max(1f, Math.min(4f, z)) - 1f) * 100));
        zoomLabel.setText(String.format(Locale.KOREA, "%.1f×", z));
    }

    /** 예약 시트가 닫히면 선택 표시를 지운다. */
    void clearSelection() {
        if (map != null) map.setSelected(null);
    }
}
