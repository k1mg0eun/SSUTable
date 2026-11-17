package com.g1j2.ssutable.ui;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.g1j2.ssutable.App;
import com.g1j2.ssutable.R;
import com.google.android.material.button.MaterialButton;

/** S-01 권한 안내 → S-02 이름·학번 등록. */
public class OnboardingActivity extends AppCompatActivity {
    private ViewFlipper flipper;
    private MaterialButton allowNotify;
    private MaterialButton allowLocation;
    private EditText name;
    private EditText studentId;
    private TextView studentIdHelp;
    private MaterialButton register;

    private final ActivityResultLauncher<String> notifyPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), ok -> renderPermissions());
    private final ActivityResultLauncher<String[]> locationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), ok -> renderPermissions());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);
        flipper = findViewById(R.id.flipper);
        allowNotify = findViewById(R.id.allowNotify);
        allowLocation = findViewById(R.id.allowLocation);
        name = findViewById(R.id.name);
        studentId = findViewById(R.id.studentId);
        studentIdHelp = findViewById(R.id.studentIdHelp);
        register = findViewById(R.id.register);

        if (App.get().prefs().permissionsSeen()) flipper.setDisplayedChild(1);

        allowNotify.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 33) {
                notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        });
        allowLocation.setOnClickListener(v -> locationPermission.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}));

        findViewById(R.id.start).setOnClickListener(v -> {
            App.get().prefs().setPermissionsSeen();
            flipper.setInAnimation(this, android.R.anim.fade_in);
            flipper.setDisplayedChild(1);
            name.requestFocus();
        });

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                validate();
            }
        };
        name.addTextChangedListener(watcher);
        studentId.addTextChangedListener(watcher);

        register.setOnClickListener(v -> {
            App.get().prefs().saveProfile(name.getText().toString().trim(), studentId.getText().toString());
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        renderPermissions();
    }

    private void validate() {
        String id = studentId.getText().toString();
        boolean idOk = id.matches("\\d{8}");
        boolean nameOk = !name.getText().toString().trim().isEmpty();
        if (!id.isEmpty() && !idOk) {
            studentIdHelp.setText("학번은 숫자 8자리예요 (지금 " + id.length() + "자리)");
            studentIdHelp.setTextColor(ContextCompat.getColor(this, R.color.danger));
        } else {
            studentIdHelp.setText("숫자 8자리");
            studentIdHelp.setTextColor(ContextCompat.getColor(this, R.color.ink3));
        }
        register.setEnabled(idOk && nameOk);
    }

    private void renderPermissions() {
        boolean n = App.get().hasNotificationPermission();
        boolean l = App.get().hasLocationPermission();
        allowNotify.setText(n ? "허용됨" : "허용");
        allowNotify.setEnabled(!n);
        allowLocation.setText(l ? "허용됨" : "허용");
        allowLocation.setEnabled(!l);
    }
}
