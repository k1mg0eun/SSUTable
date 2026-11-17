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

import com.g1j2.ssutable.App;
import com.g1j2.ssutable.R;
import com.g1j2.ssutable.core.FloorSnapshot;
import com.g1j2.ssutable.core.Mode;
import com.g1j2.ssutable.core.ModeInfo;
import com.g1j2.ssutable.core.Reservation;
import com.g1j2.ssutable.core.Schedule;
import com.g1j2.ssutable.core.SeatRepository;
import com.g1j2.ssutable.core.Zone;
import com.g1j2.ssutable.ui.view.BarView;
import com.g1j2.ssutable.ui.view.DayTimelineView;
import com.g1j2.ssutable.ui.view.RingView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** S-03 홈: 지금 모드, 혼잡도, 구역별 혼잡도, 모드 규칙. */
public class HomeFragment extends LiveFragment {
    private TextView offlineTag;
    private TextView clock, modeName, modeSuffix, modeHours, modeNext;
    private DayTimelineView timeline;
    private View openGroup, closedGroup;
    private RingView ring;
    private TextView percent, level, available, usage, nextOpen, hoursNote, myStatus;
    private ChipGroup rules;
    private MaterialButton cta;
    private final Map<Zone, View> zoneRows = new EnumMap<>(Zone.class);
    private Mode rulesFor;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_home, container, false);
        clock = v.findViewById(R.id.clock);
        offlineTag = v.findViewById(R.id.offlineTag);
        modeName = v.findViewById(R.id.modeName);
        modeSuffix = v.findViewById(R.id.modeSuffix);
        modeHours = v.findViewById(R.id.modeHours);
        modeNext = v.findViewById(R.id.modeNext);
        timeline = v.findViewById(R.id.timeline);
        openGroup = v.findViewById(R.id.openGroup);
        closedGroup = v.findViewById(R.id.closedGroup);
        ring = v.findViewById(R.id.ring);
        percent = v.findViewById(R.id.percent);
        level = v.findViewById(R.id.level);
        available = v.findViewById(R.id.available);
        usage = v.findViewById(R.id.usage);
        nextOpen = v.findViewById(R.id.nextOpen);
        hoursNote = v.findViewById(R.id.hoursNote);
        myStatus = v.findViewById(R.id.myStatus);
        rules = v.findViewById(R.id.rules);
        cta = v.findViewById(R.id.cta);

        LinearLayout zones = v.findViewById(R.id.zones);
        for (Zone z : Zone.values()) {
            View row = inflater.inflate(R.layout.item_zone_row, zones, false);
            ((TextView) row.findViewById(R.id.zoneName)).setText(z.label);
            row.setOnClickListener(x -> main().openSeats(z, null));
            row.setContentDescription(z.label + " 좌석 보기");
            zones.addView(row);
            zoneRows.put(z, row);
        }

        v.findViewById(R.id.settings).setOnClickListener(x ->
                startActivity(new Intent(requireContext(), SettingsActivity.class)));
        cta.setOnClickListener(x -> {
            Reservation r = App.get().repo().current();
            if (r != null && r.active()) main().openMy();
            else main().openSeats(null, null);
        });
        return v;
    }

    @Override
    protected void render() {
        App app = App.get();
        FloorSnapshot snap = app.repo().snapshot();
        ModeInfo info = snap.mode;
        long now = snap.at;
        boolean exam = app.exam();

        clock.setText(Fmt.dateTime(now) + (app.clock().speed() > 1 ? "  ×" + app.clock().speed() : ""));

        offlineTag.setVisibility(app.repo().connection() == SeatRepository.Connection.OFFLINE
                ? View.VISIBLE : View.GONE);

        // 모드
        modeName.setText(info.mode == Mode.CLOSED ? "운영 종료" : info.mode.label);
        modeName.setTextColor(ModeColors.of(requireContext(), info.mode));
        modeSuffix.setVisibility(info.mode == Mode.CLOSED ? View.GONE : View.VISIBLE);
        modeHours.setText(hoursLine(info, exam));
        timeline.setData(exam, Schedule.minuteOfDay(now), info.forced ? info.mode : null);
        modeNext.setText(nextLine(info));

        boolean open = info.open();
        openGroup.setVisibility(open ? View.VISIBLE : View.GONE);
        closedGroup.setVisibility(open ? View.GONE : View.VISIBLE);

        if (open) {
            int pct = snap.all.congestionPercent();
            ring.setProgress(pct / 100f);
            percent.setText(pct + "%");
            level.setText(snap.level().label);
            available.setText(snap.all.free + "석");
            usage.setText("사용 중 " + snap.all.inUse + " / 개방 " + snap.all.open + "석");

            for (Zone z : Zone.values()) {
                FloorSnapshot.Counts c = snap.zones.get(z);
                View row = zoneRows.get(z);
                BarView bar = row.findViewById(R.id.zoneBar);
                TextView p = row.findViewById(R.id.zonePercent);
                if (c.open == 0) {
                    bar.setFraction(0);
                    p.setText("닫힘");
                } else {
                    bar.setFraction(c.congestionPercent() / 100f);
                    p.setText(c.congestionPercent() + "%");
                }
            }
            if (rulesFor != info.mode) {
                rulesFor = info.mode;
                rules.removeAllViews();
                for (String rule : rulesOf(info.mode)) {
                    TextView t = new TextView(requireContext(), null, 0, R.style.Text_Rule);
                    t.setText(rule);
                    rules.addView(t);
                }
            }
        } else {
            nextOpen.setText("다음 운영: " + Fmt.day(now, info.nextOpen) + " 아침 식사 모드");
            hoursNote.setText(exam
                    ? "시험기간 운영 08:00~24:00\n아침 08:00~09:00, 점심 11:20~14:00, 그 외 스터디"
                    : "평소 운영 08:00~17:00\n아침 08:00~09:00, 점심 11:20~14:00, 그 외 스터디");
        }

        // 하단 버튼
        Reservation r = app.repo().current();
        if (r != null && r.active()) {
            myStatus.setVisibility(View.VISIBLE);
            myStatus.setText(statusLine(r, now));
            cta.setEnabled(true);
            cta.setText("내 예약 보기");
        } else {
            myStatus.setVisibility(View.GONE);
            cta.setEnabled(open);
            cta.setText(open ? "자리 예약하기" : "지금은 예약할 수 없어요");
        }
    }

    private static String hoursLine(ModeInfo info, boolean exam) {
        switch (info.mode) {
            case BREAKFAST:
                return "08:00 – 09:00, 최대 45분, 천원의 아침밥 식권 함께";
            case LUNCH:
                return "11:20 – 14:00, 최대 45분, 점심 식권 함께";
            case STUDY:
                return "식사 시간 외, 2시간씩, 연장 1번";
            default:
                return exam ? "시험기간엔 08:00부터 24:00까지 열어요" : "평소엔 08:00부터 17:00까지 열어요";
        }
    }

    private static String nextLine(ModeInfo info) {
        if (info.forced) return "시연 모드에서 지정한 모드예요. 설정에서 자동으로 되돌릴 수 있어요.";
        if (!info.open()) return "";
        if (info.nextMode == Mode.CLOSED) return Fmt.hm(info.windowEnd) + "에 운영이 끝나요";
        return Fmt.hm(info.windowEnd) + "부터 " + info.nextMode.label + " 모드로 바뀌어요";
    }

    static List<String> rulesOf(Mode mode) {
        List<String> list = new ArrayList<>();
        switch (mode) {
            case BREAKFAST:
            case LUNCH:
                list.add("최대 45분");
                list.add("예약 후 10분 안에 도착");
                list.add("식권 함께 예약");
                list.add("모드가 끝나면 함께 종료");
                break;
            case STUDY:
                list.add("2시간");
                list.add("종료 30분 전부터 1번 연장");
                list.add("예약 후 10분 안에 도착");
                list.add("30분 넘게 비우면 반납");
                break;
            default:
                break;
        }
        return list;
    }

    private static String statusLine(Reservation r, long now) {
        switch (r.state) {
            case WAITING:
                return r.seatId + " 도착 대기, " + Fmt.countdown(r.arriveBy - now) + " 남음";
            case IN_USE:
                return r.seatId + " 사용 중, " + Fmt.countdown(r.endsAt - now) + " 남음";
            case AWAY:
                return r.seatId + " 자리 비움, " + Fmt.countdown(r.awaySince + 30 * 60_000L - now) + " 안에 복귀";
            default:
                return r.seatId;
        }
    }
}
