package com.g1j2.ssutable.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import com.g1j2.ssutable.App;
import com.g1j2.ssutable.R;
import com.g1j2.ssutable.core.Mode;
import com.g1j2.ssutable.core.Reservation;
import com.g1j2.ssutable.core.ReservationEngine;
import com.g1j2.ssutable.core.Schedule;
import com.g1j2.ssutable.core.SeatLayout;
import com.g1j2.ssutable.core.SeatRepository;
import com.g1j2.ssutable.core.Ticket;
import com.g1j2.ssutable.ui.view.BarView;
import com.g1j2.ssutable.ui.view.RingView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** S-07 내 예약. 상태마다 지금 해야 할 일 하나를 크게 보여 준다. */
public class MyReservationFragment extends LiveFragment {
    private View emptyGroup, headerGroup, waitGroup, useGroup, awayGroup, endGroup, actions, demoBar;
    private TextView seatId, modeTag, seatSub, waitCountdown, waitDeadline, useCountdown, useEnd, extendNote;
    private TextView awayCountdown, endHeadline, endNote, locationLine;
    private MaterialButton ticketBtn, primary, secondary, tertiary;
    private BarView waitBar;
    private RingView useRing;
    private LinearLayout endRows;
    private MaterialButtonToggleGroup insideToggle;
    private String endRenderedFor;
    private final ReservationEngine engine = new ReservationEngine();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_my, container, false);
        emptyGroup = v.findViewById(R.id.emptyGroup);
        headerGroup = v.findViewById(R.id.headerGroup);
        waitGroup = v.findViewById(R.id.waitGroup);
        useGroup = v.findViewById(R.id.useGroup);
        awayGroup = v.findViewById(R.id.awayGroup);
        endGroup = v.findViewById(R.id.endGroup);
        actions = v.findViewById(R.id.actions);
        demoBar = v.findViewById(R.id.demoBar);
        seatId = v.findViewById(R.id.seatId);
        modeTag = v.findViewById(R.id.modeTag);
        seatSub = v.findViewById(R.id.seatSub);
        waitCountdown = v.findViewById(R.id.waitCountdown);
        waitDeadline = v.findViewById(R.id.waitDeadline);
        waitBar = v.findViewById(R.id.waitBar);
        useRing = v.findViewById(R.id.useRing);
        useCountdown = v.findViewById(R.id.useCountdown);
        useEnd = v.findViewById(R.id.useEnd);
        extendNote = v.findViewById(R.id.extendNote);
        awayCountdown = v.findViewById(R.id.awayCountdown);
        endHeadline = v.findViewById(R.id.endHeadline);
        endRows = v.findViewById(R.id.endRows);
        endNote = v.findViewById(R.id.endNote);
        locationLine = v.findViewById(R.id.locationLine);
        ticketBtn = v.findViewById(R.id.ticketBtn);
        primary = v.findViewById(R.id.primary);
        secondary = v.findViewById(R.id.secondary);
        tertiary = v.findViewById(R.id.tertiary);
        insideToggle = v.findViewById(R.id.insideToggle);

        useRing.setColor(ContextCompat.getColor(requireContext(), R.color.seat_mine));

        v.findViewById(R.id.emptyReserve).setOnClickListener(x -> main().openSeats(null, null));
        ticketBtn.setOnClickListener(x -> startActivity(new Intent(requireContext(), TicketActivity.class)));
        v.findViewById(R.id.locateBtn).setOnClickListener(x -> {
            Reservation r = App.get().repo().current();
            if (r != null) main().openSeats(null, r.seatId);
        });

        insideToggle.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return;
            boolean inside = id == R.id.demoInside;
            if (App.get().prefs().inside() != inside) {
                App.get().prefs().setInside(inside);
                App.get().tickNow();
            }
        });
        return v;
    }

    @Override
    protected void render() {
        App app = App.get();
        SeatRepository repo = app.repo();
        Reservation r = repo.current();
        long now = app.now();

        boolean showDemo = app.prefs().demoPanel();
        demoBar.setVisibility(showDemo ? View.VISIBLE : View.GONE);
        if (showDemo) {
            int want = app.prefs().inside() ? R.id.demoInside : R.id.demoOutside;
            if (insideToggle.getCheckedButtonId() != want) insideToggle.check(want);
        }

        Reservation.State state = r == null ? null : r.state;
        emptyGroup.setVisibility(r == null ? View.VISIBLE : View.GONE);
        headerGroup.setVisibility(r != null && r.active() ? View.VISIBLE : View.GONE);
        waitGroup.setVisibility(state == Reservation.State.WAITING ? View.VISIBLE : View.GONE);
        useGroup.setVisibility(state == Reservation.State.IN_USE ? View.VISIBLE : View.GONE);
        awayGroup.setVisibility(state == Reservation.State.AWAY ? View.VISIBLE : View.GONE);
        endGroup.setVisibility(state == Reservation.State.ENDED ? View.VISIBLE : View.GONE);
        actions.setVisibility(r == null ? View.GONE : View.VISIBLE);
        locationLine.setVisibility(r != null && r.active() ? View.VISIBLE : View.GONE);
        if (r == null) {
            endRenderedFor = null;
            return;
        }

        if (r.active()) renderHeader(r);
        if (r.active()) renderLocation(r);

        switch (r.state) {
            case WAITING:
                renderWaiting(r, now);
                break;
            case IN_USE:
                renderInUse(r, now);
                break;
            case AWAY:
                renderAway(r, now);
                break;
            case ENDED:
                renderEnded(r);
                break;
        }
    }

    private void renderHeader(Reservation r) {
        seatId.setText(r.seatId);
        ModeColors.tag(modeTag, r.mode);
        seatSub.setText(SeatLayout.get().seat(r.seatId).zone.label + ", 예약 " + Fmt.hm(r.createdAt)
                + (r.checkedInAt > 0 ? ", 도착 " + Fmt.hm(r.checkedInAt) : ""));
        ticketBtn.setVisibility(r.ticket != null ? View.VISIBLE : View.GONE);
    }

    private void renderLocation(Reservation r) {
        App app = App.get();
        String text;
        int color = R.color.ink2;
        if (!app.locationTracking()) {
            text = r.state == Reservation.State.WAITING
                    ? "위치 권한이 없어 어디서든 도착을 확인할 수 있어요"
                    : "위치 권한이 없어 시간이 끝날 때만 반납돼요";
        } else if (app.inside()) {
            text = "학생회관 안에 있어요";
            color = R.color.mode_lunch;
        } else {
            text = r.state == Reservation.State.WAITING
                    ? "학생회관 100m 밖이라 아직 도착할 수 없어요"
                    : "학생회관 밖에 있어요";
            color = R.color.warn_ink;
        }
        locationLine.setText(text);
        locationLine.setTextColor(ContextCompat.getColor(requireContext(), color));
        locationLine.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_pin, 0, 0, 0);
        TextViewCompat.setCompoundDrawableTintList(locationLine,
                ContextCompat.getColorStateList(requireContext(), color));
    }

    private void renderWaiting(Reservation r, long now) {
        long left = r.arriveBy - now;
        boolean urgent = left <= ReservationEngine.ARRIVAL_WARNING;
        int c = ContextCompat.getColor(requireContext(), urgent ? R.color.danger : R.color.ink);
        waitCountdown.setText(Fmt.countdown(left));
        waitCountdown.setTextColor(c);
        waitBar.setFraction(left / (float) ReservationEngine.ARRIVAL_WINDOW);
        waitBar.setBarColor(c);
        waitDeadline.setText(Fmt.hm(r.arriveBy) + "까지 도착하지 않으면 자동으로 취소돼요");

        App app = App.get();
        boolean canArrive = engine.canArrive(r, app.inside(), app.locationTracking());
        primary.setVisibility(View.VISIBLE);
        primary.setText("도착");
        primary.setEnabled(canArrive);
        primary.setOnClickListener(x -> {
            if (App.get().repo().arrive()) App.get().tickNow();
        });
        secondary.setVisibility(View.GONE);
        tertiary.setVisibility(View.VISIBLE);
        tertiary.setText("예약 취소");
        tertiary.setOnClickListener(x -> confirmCancel(r));
    }

    private void renderInUse(Reservation r, long now) {
        long total = Math.max(1, r.endsAt - r.checkedInAt);
        long left = r.endsAt - now;
        useRing.setProgress(left / (float) total);
        useCountdown.setText(Fmt.countdown(left));
        useEnd.setText("종료 예정 " + Fmt.hm(r.endsAt));

        secondary.setVisibility(View.VISIBLE);
        secondary.setText("체크아웃");
        secondary.setOnClickListener(x -> confirmCheckout(r));
        tertiary.setVisibility(View.GONE);

        if (r.mode != Mode.STUDY) {
            primary.setVisibility(View.GONE);
            extendNote.setText(r.mode.label + " 모드는 연장할 수 없어요");
            return;
        }
        primary.setVisibility(View.VISIBLE);
        long preview = App.get().repo().previewExtendedEnd();
        long opensAt = r.endsAt - ReservationEngine.EXTEND_OPENS;
        if (r.extended) {
            primary.setEnabled(false);
            primary.setText("연장은 1번만 할 수 있어요");
            extendNote.setText("이미 연장했어요");
        } else if (preview < 0) {
            primary.setEnabled(false);
            primary.setText("연장할 수 없어요");
            Mode after = Schedule.modeAt(r.endsAt, App.get().exam());
            extendNote.setText(after == Mode.CLOSED
                    ? Fmt.hm(r.endsAt) + "에 운영이 끝나서 연장할 수 없어요"
                    : Fmt.hm(r.endsAt) + "부터 " + after.label + " 모드라 연장할 수 없어요");
        } else if (engine.extendOpen(r, now)) {
            primary.setEnabled(true);
            primary.setText("연장하기, " + Fmt.hm(preview) + "까지");
            extendNote.setText("연장은 1번, 최대 2시간이에요");
        } else {
            primary.setEnabled(false);
            primary.setText("연장 (" + Fmt.hm(opensAt) + "부터)");
            extendNote.setText("끝나기 30분 전부터 1번 연장할 수 있어요");
        }
        primary.setOnClickListener(x -> {
            if (App.get().repo().extend()) App.get().tickNow();
        });
    }

    private void renderAway(Reservation r, long now) {
        awayCountdown.setText(Fmt.countdown(engine.awayDeadline(r) - now));
        primary.setVisibility(View.GONE);
        secondary.setVisibility(View.VISIBLE);
        secondary.setText("체크아웃");
        secondary.setOnClickListener(x -> confirmCheckout(r));
        tertiary.setVisibility(View.GONE);
    }

    private void renderEnded(Reservation r) {
        primary.setVisibility(View.VISIBLE);
        primary.setEnabled(true);
        primary.setText("다시 예약하기");
        primary.setOnClickListener(x -> {
            App.get().repo().acknowledgeEnded();
            App.get().tickNow();
            main().openSeats(null, null);
        });
        secondary.setVisibility(View.GONE);
        tertiary.setVisibility(View.VISIBLE);
        tertiary.setText("확인");
        tertiary.setOnClickListener(x -> {
            App.get().repo().acknowledgeEnded();
            App.get().tickNow();
        });

        if (r.id.equals(endRenderedFor)) return;
        endRenderedFor = r.id;
        endHeadline.setText(r.endReason.headline);
        endRows.removeAllViews();
        addRow("좌석", r.seatId + ", " + SeatLayout.get().seat(r.seatId).zone.label);
        addRow("종료 사유", r.endReason.label);
        addRow("이용 시간", r.checkedInAt > 0 ? Fmt.duration(r.usedMillis()) : "도착 전 종료");
        addRow("종료 시각", Fmt.hm(r.endedAt));
        if (r.ticket != null && r.ticket.status == Ticket.Status.CANCELLED) {
            endNote.setText(r.ticket.name + "도 함께 취소됐어요. 자리는 바로 다른 학생에게 빈자리로 보여요.");
        } else {
            endNote.setText("자리는 바로 다른 학생에게 빈자리로 보여요.");
        }
    }

    private void addRow(String key, String value) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_kv_row, endRows, false);
        ((TextView) row.findViewById(R.id.key)).setText(key);
        ((TextView) row.findViewById(R.id.value)).setText(value);
        endRows.addView(row);
    }

    private void confirmCheckout(Reservation r) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("체크아웃할까요?")
                .setMessage(r.seatId + "를 바로 빈자리로 돌려놓아요.")
                .setPositiveButton("체크아웃", (d, w) -> {
                    App.get().repo().checkout();
                    App.get().tickNow();
                })
                .setNegativeButton("계속 쓰기", null)
                .show();
    }

    private void confirmCancel(Reservation r) {
        String msg = r.ticket != null
                ? r.seatId + " 예약과 " + r.ticket.name + "이 함께 취소돼요."
                : r.seatId + " 예약이 취소돼요.";
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("예약을 취소할까요?")
                .setMessage(msg)
                .setPositiveButton("예약 취소", (d, w) -> {
                    App.get().repo().cancel();
                    App.get().tickNow();
                })
                .setNegativeButton("돌아가기", null)
                .show();
    }
}
