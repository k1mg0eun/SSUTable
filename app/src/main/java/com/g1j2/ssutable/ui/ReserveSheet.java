package com.g1j2.ssutable.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.g1j2.ssutable.App;
import com.g1j2.ssutable.R;
import com.g1j2.ssutable.core.Mode;
import com.g1j2.ssutable.core.ModeInfo;
import com.g1j2.ssutable.core.Reservation;
import com.g1j2.ssutable.core.ReservationEngine;
import com.g1j2.ssutable.core.Seat;
import com.g1j2.ssutable.core.SeatLayout;
import com.g1j2.ssutable.core.SeatRepository;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

/** S-05 예약 시트. 지금 모드 규칙으로 이용 시간을 계산해 보여준다. */
public class ReserveSheet extends BottomSheetDialogFragment {
    private static final String ARG_SEAT = "seat";

    private Seat seat;
    private TextView useTime, message;
    private View details;
    private MaterialButton confirm;
    private final Runnable tick = this::renderTime;
    private boolean failed;

    public static ReserveSheet newInstance(String seatId) {
        ReserveSheet s = new ReserveSheet();
        Bundle b = new Bundle();
        b.putString(ARG_SEAT, seatId);
        s.setArguments(b);
        return s;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.sheet_reserve, container, false);
        seat = SeatLayout.get().seat(requireArguments().getString(ARG_SEAT));
        ModeInfo info = App.get().mode();

        ((TextView) v.findViewById(R.id.seatId)).setText(seat.id);
        ((TextView) v.findViewById(R.id.seatDesc)).setText(seat.description());
        ModeColors.tag(v.findViewById(R.id.modeTag), info.mode);
        useTime = v.findViewById(R.id.useTime);
        message = v.findViewById(R.id.message);
        details = v.findViewById(R.id.details);
        confirm = v.findViewById(R.id.confirm);

        TextView note = v.findViewById(R.id.ruleNote);
        if (info.mode.meal) {
            note.setText("10분 안에 [도착]을 누르지 않으면 예약과 식권이 함께 취소돼요.\n"
                    + "이용 시간은 도착을 확인한 때부터 세요.");
        } else {
            note.setText("10분 안에 [도착]을 누르지 않으면 자동 취소돼요.\n"
                    + "끝나기 30분 전부터 1번, 2시간 연장할 수 있어요.\n"
                    + "학생회관을 30분 넘게 벗어나면 자리가 반납돼요.");
        }

        View ticketGroup = v.findViewById(R.id.ticketGroup);
        ticketGroup.setVisibility(info.mode.meal ? View.VISIBLE : View.GONE);
        if (info.mode.meal) {
            ((TextView) v.findViewById(R.id.ticketName)).setText(info.mode.ticketName() + " 1장");
        }

        Reservation existing = App.get().repo().current();
        if (existing != null && existing.active()) {
            showMessage("이미 예약한 자리(" + existing.seatId + ")가 있어요. 한 번에 한 자리만 예약할 수 있어요.");
            confirm.setText("내 예약 보기");
            confirm.setOnClickListener(x -> {
                dismiss();
                ((MainActivity) requireActivity()).openMy();
            });
        } else {
            confirm.setOnClickListener(x -> reserve());
        }
        renderTime();
        return v;
    }

    @Override
    public void onStart() {
        super.onStart();
        App.get().addListener(tick);
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetDialog d = (BottomSheetDialog) getDialog();
            d.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
            d.getBehavior().setSkipCollapsed(true);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        App.get().removeListener(tick);
    }

    private void renderTime() {
        if (useTime == null) return;
        ModeInfo info = App.get().mode();
        if (!info.open()) {
            useTime.setText("—");
            return;
        }
        long now = App.get().now();
        long end = ReservationEngine.plannedEnd(info.mode, now, info.forced ? -1 : info.windowEnd);
        useTime.setText("지금 ~ " + Fmt.hm(end) + " (" + Fmt.duration(end - now) + ")");
    }

    private void reserve() {
        if (failed) {
            dismiss();
            return;
        }
        SeatRepository.ReserveResult result = App.get().repo().reserve(seat.id);
        switch (result) {
            case OK:
                App.get().tickNow();
                startActivity(new Intent(requireContext(), ReservedActivity.class));
                dismiss();
                break;
            case TAKEN:
                failed = true;
                details.setVisibility(View.GONE);
                showMessage("방금 다른 학생이 " + seat.id + "를 먼저 예약했어요. 다른 빈자리를 골라 주세요.");
                confirm.setText("다른 자리 고르기");
                App.get().tickNow();
                break;
            case CLOSED:
                showMessage("운영 시간이 끝나 예약할 수 없어요.");
                confirm.setEnabled(false);
                break;
            case ALREADY_HAVE:
                showMessage("이미 예약한 자리가 있어요.");
                break;
            default:
                showMessage("좌석 정보를 찾지 못했어요. 맵에서 다시 골라 주세요.");
                break;
        }
    }

    private void showMessage(String text) {
        message.setText(text);
        message.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        Fragment parent = getParentFragment();
        if (parent instanceof SeatMapFragment) ((SeatMapFragment) parent).clearSelection();
    }
}
