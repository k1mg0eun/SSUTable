package com.g1j2.ssutable.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.g1j2.ssutable.App;
import com.g1j2.ssutable.R;
import com.g1j2.ssutable.core.Mode;
import com.g1j2.ssutable.core.Reservation;
import com.g1j2.ssutable.core.SeatRepository;
import com.g1j2.ssutable.data.Prefs;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.List;

/** S-09 설정: 내 정보, 권한, 알림, 이용 기록, 시연 모드. */
public class SettingsActivity extends AppCompatActivity {
    private static final int[] JUMPS = {7 * 60 + 55, 11 * 60 + 15, 13 * 60 + 50, 16 * 60 + 50};

    private Prefs prefs;
    private TextView virtualTime;
    private LinearLayout permissionRows;
    private MaterialButtonToggleGroup modeGroup;
    private MaterialButtonToggleGroup speedGroup;
    private MaterialSwitch examSwitch;
    /** 다른 폰이 바꾼 /demo 값을 화면에 맞추는 중. 이때는 리스너가 다시 쓰지 않는다. */
    private boolean syncingDemo;
    private final Runnable tick = this::renderClock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = App.get().prefs();
        findViewById(R.id.back).setOnClickListener(v -> finish());
        virtualTime = findViewById(R.id.virtualTime);
        permissionRows = findViewById(R.id.permissionRows);

        renderProfile();
        findViewById(R.id.editProfile).setOnClickListener(v -> editProfile());

        // 알림
        LinearLayout alertRows = findViewById(R.id.alertRows);
        for (Prefs.Alert a : Prefs.Alert.values()) {
            MaterialSwitch s = new MaterialSwitch(this);
            s.setText(a.label);
            s.setTextSize(15);
            s.setTextColor(getColor(R.color.ink));
            s.setChecked(prefs.alert(a));
            s.setOnCheckedChangeListener((b, on) -> prefs.setAlert(a, on));
            int p = dp(20);
            s.setPadding(p, 0, p, 0);
            s.setMinHeight(dp(48));
            alertRows.addView(s, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        findViewById(R.id.historyRow).setOnClickListener(v -> showHistory());

        // 시연 모드
        modeGroup = findViewById(R.id.modeGroup);
        speedGroup = findViewById(R.id.speedGroup);
        examSwitch = findViewById(R.id.examSwitch);
        syncDemoControls();
        modeGroup.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked || syncingDemo) return;
            App.get().setForcedMode(modeFor(id));
        });

        LinearLayout jumpRow = findViewById(R.id.jumpRow);
        addJump(jumpRow, "지금", -1);
        for (int m : JUMPS) addJump(jumpRow, Fmt.hmMinute(m), m);

        speedGroup.addOnButtonCheckedListener((g, id, checked) -> {
            if (checked && !syncingDemo) App.get().setSpeed(id == R.id.speed10 ? 10 : 1);
        });

        MaterialButtonToggleGroup insideGroup = findViewById(R.id.insideGroup);
        Prefs.LocationSource source = prefs.locationSource();
        insideGroup.check(source == Prefs.LocationSource.INSIDE ? R.id.inside
                : source == Prefs.LocationSource.OUTSIDE ? R.id.outside
                : R.id.locationGps);
        insideGroup.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            prefs.setLocationSource(id == R.id.inside ? Prefs.LocationSource.INSIDE
                    : id == R.id.outside ? Prefs.LocationSource.OUTSIDE
                    : Prefs.LocationSource.GPS);
            App.get().refreshLocation();
            App.get().tickNow();
        });

        examSwitch.setOnCheckedChangeListener((b, on) -> {
            if (!syncingDemo) App.get().setExam(on);
        });

        MaterialSwitch demoPanel = findViewById(R.id.demoPanelSwitch);
        demoPanel.setChecked(prefs.demoPanel());
        demoPanel.setOnCheckedChangeListener((b, on) -> prefs.setDemoPanel(on));

        MaterialSwitch conflict = findViewById(R.id.conflictSwitch);
        conflict.setChecked(prefs.conflictNext());
        conflict.setOnCheckedChangeListener((b, on) -> prefs.setConflictNext(on));

        findViewById(R.id.resetSeats).setVisibility(
                App.get().repo().connection() == SeatRepository.Connection.LOCAL ? View.GONE : View.VISIBLE);
        findViewById(R.id.resetSeats).setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("모든 좌석 예약을 초기화할까요?")
                .setMessage("모든 폰의 좌석 예약이 지워지고, 예약 중이던 폰은 예약이 끝나요.")
                .setPositiveButton("초기화", (d, w) -> App.get().repo().resetAllSeats())
                .setNegativeButton("취소", null)
                .show());

        findViewById(R.id.resetAll).setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("모든 데이터를 지울까요?")
                .setMessage("내 정보, 예약, 이용 기록이 모두 지워지고 첫 화면으로 돌아가요. 다른 폰의 시연 설정은 그대로예요.")
                .setPositiveButton("지우기", (d, w) -> {
                    App.get().resetAll();
                    startActivity(new Intent(this, OnboardingActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                })
                .setNegativeButton("취소", null)
                .show());
    }

    @Override
    protected void onStart() {
        super.onStart();
        App.get().addListener(tick);
        renderPermissions();
        renderClock();
        ((TextView) findViewById(R.id.historyCount)).setText(App.get().repo().history().size() + "건");
        MaterialSwitch conflict = findViewById(R.id.conflictSwitch);
        conflict.setChecked(prefs.conflictNext());
    }

    @Override
    protected void onStop() {
        super.onStop();
        App.get().removeListener(tick);
    }

    private void renderConnection() {
        String text;
        switch (App.get().repo().connection()) {
            case ONLINE: text = "연결됨"; break;
            case OFFLINE: text = "오프라인"; break;
            case CONNECTING: text = "연결 중"; break;
            default: text = "로컬 모드(Firebase 설정 없음)";
        }
        ((TextView) findViewById(R.id.connectionStatus)).setText(text);
    }

    private void renderClock() {
        App app = App.get();
        String speed = app.clock().speed() > 1 ? ", " + app.clock().speed() + "배속" : "";
        renderConnection();
        syncDemoControls();
        virtualTime.setText("앱 시각 " + Fmt.dateTime(app.now()) + speed + "\n지금 모드: " + app.mode().mode.label
                + (app.mode().forced ? " (지정)" : ""));
        // 동시 예약 실패 스위치는 한 번 쓰면 꺼진다
        MaterialSwitch conflict = findViewById(R.id.conflictSwitch);
        if (conflict.isChecked() != prefs.conflictNext()) conflict.setChecked(prefs.conflictNext());
    }

    /** 모드·배속·시험 컨트롤을 공유 설정에 맞춘다. 다른 폰이 바꿔도 따라간다. */
    private void syncDemoControls() {
        App app = App.get();
        int modeId = idFor(app.demo().current().forcedMode);
        int speedId = app.clock().speed() > 1 ? R.id.speed10 : R.id.speed1;
        boolean exam = app.exam();
        syncingDemo = true;
        if (modeGroup.getCheckedButtonId() != modeId) modeGroup.check(modeId);
        if (speedGroup.getCheckedButtonId() != speedId) speedGroup.check(speedId);
        if (examSwitch.isChecked() != exam) examSwitch.setChecked(exam);
        syncingDemo = false;
    }

    private void renderProfile() {
        ((TextView) findViewById(R.id.profileName)).setText(prefs.name());
        ((TextView) findViewById(R.id.profileId)).setText(prefs.studentId());
    }

    private void renderPermissions() {
        permissionRows.removeAllViews();
        addPermissionRow("위치", App.get().hasLocationPermission());
        addPermissionRow("알림", App.get().hasNotificationPermission());
    }

    private void addPermissionRow(String name, boolean granted) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(4), dp(20), dp(4));
        row.setMinimumHeight(dp(52));

        TextView label = new TextView(this, null, 0, R.style.Text);
        label.setText(name);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (granted) {
            TextView v = new TextView(this, null, 0, R.style.Text_Sub);
            v.setText("허용됨");
            row.addView(v);
        } else {
            MaterialButton b = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            b.setText("설정에서 켜기");
            b.setTextSize(14);
            b.setMinHeight(dp(40));
            b.setMinimumHeight(dp(40));
            b.setOnClickListener(x -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null))));
            row.addView(b);
        }
        permissionRows.addView(row);
    }

    private void addJump(LinearLayout row, String label, int minute) {
        MaterialButton b = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(label);
        b.setTextSize(13);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(dp(40));
        b.setMinimumHeight(dp(40));
        b.setPadding(dp(14), 0, dp(14), 0);
        b.setStrokeWidth(dp(1));
        b.setStrokeColorResource(R.color.line_strong);
        b.setCornerRadius(dp(8));
        b.setOnClickListener(v -> {
            if (minute < 0) App.get().resetClock();
            else App.get().jumpToMinute(minute);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(6));
        row.addView(b, lp);
    }

    private void editProfile() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        EditText name = new EditText(this);
        name.setHint("이름");
        name.setText(prefs.name());
        EditText id = new EditText(this);
        id.setHint("학번 8자리");
        id.setInputType(InputType.TYPE_CLASS_NUMBER);
        id.setText(prefs.studentId());
        box.addView(name);
        box.addView(id);
        new MaterialAlertDialogBuilder(this)
                .setTitle("내 정보 수정")
                .setView(box)
                .setPositiveButton("저장", (d, w) -> {
                    String n = name.getText().toString().trim();
                    String s = id.getText().toString();
                    if (!n.isEmpty() && s.matches("\\d{8}")) {
                        prefs.saveProfile(n, s);
                        renderProfile();
                    } else {
                        new MaterialAlertDialogBuilder(this)
                                .setMessage("이름을 넣고, 학번은 숫자 8자리로 입력해 주세요.")
                                .setPositiveButton("확인", null)
                                .show();
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showHistory() {
        List<Reservation> list = App.get().repo().history();
        if (list.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("이용 기록")
                    .setMessage("아직 끝난 예약이 없어요.")
                    .setPositiveButton("확인", null)
                    .show();
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(4), dp(24), 0);
        for (Reservation r : list) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_kv_row, box, false);
            ((TextView) row.findViewById(R.id.key)).setText(Fmt.day(App.get().now(), r.createdAt) + "  " + r.seatId);
            String used = r.checkedInAt > 0 ? Fmt.duration(r.usedMillis()) : "미이용";
            ((TextView) row.findViewById(R.id.value)).setText(r.endReason.label + ", " + used);
            box.addView(row);
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(box);
        new MaterialAlertDialogBuilder(this)
                .setTitle("이용 기록")
                .setView(sv)
                .setPositiveButton("닫기", null)
                .show();
    }

    private static int idFor(Mode m) {
        if (m == null) return R.id.modeAuto;
        switch (m) {
            case BREAKFAST: return R.id.modeBreakfast;
            case LUNCH: return R.id.modeLunch;
            case STUDY: return R.id.modeStudy;
            default: return R.id.modeClosed;
        }
    }

    private static Mode modeFor(int id) {
        if (id == R.id.modeBreakfast) return Mode.BREAKFAST;
        if (id == R.id.modeLunch) return Mode.LUNCH;
        if (id == R.id.modeStudy) return Mode.STUDY;
        if (id == R.id.modeClosed) return Mode.CLOSED;
        return null;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
