package com.g1j2.ssutable.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.g1j2.ssutable.App;
import com.g1j2.ssutable.R;
import com.g1j2.ssutable.core.Reservation;
import com.g1j2.ssutable.core.SeatLayout;

/** S-06 예약 완료. 10분 도착 카운트다운과 식권 미리보기. */
public class ReservedActivity extends AppCompatActivity {
    private TextView countdown;
    private final Runnable tick = this::render;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reserved);
        Reservation r = App.get().repo().current();
        if (r == null) {
            finish();
            return;
        }
        countdown = findViewById(R.id.countdown);
        ((TextView) findViewById(R.id.title)).setText(r.seatId + " 예약 완료");
        ((TextView) findViewById(R.id.subtitle)).setText(
                SeatLayout.get().seat(r.seatId).description() + ", " + r.mode.label + " 모드");

        View ticketRow = findViewById(R.id.ticketRow);
        if (r.ticket != null) {
            ((TextView) findViewById(R.id.ticketName)).setText(r.ticket.name);
            ((ImageView) findViewById(R.id.ticketQr)).setImageBitmap(Qr.make(r.ticket.qrPayload(), 160));
            ticketRow.setOnClickListener(v -> startActivity(new Intent(this, TicketActivity.class)));
        } else {
            ticketRow.setVisibility(View.GONE);
        }

        findViewById(R.id.toMy).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class)
                    .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_MY)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });
        findViewById(R.id.toSeat).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class)
                    .putExtra(MainActivity.EXTRA_FOCUS_SEAT, r.seatId)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });

        // 예약이 잡혔다는 걸 한 번만 보여 주는 움직임
        View check = findViewById(R.id.check);
        check.setScaleX(0.6f);
        check.setScaleY(0.6f);
        check.animate().scaleX(1f).scaleY(1f).setDuration(320)
                .setInterpolator(new OvershootInterpolator(2.2f)).start();
        render();
    }

    @Override
    protected void onStart() {
        super.onStart();
        App.get().addListener(tick);
    }

    @Override
    protected void onStop() {
        super.onStop();
        App.get().removeListener(tick);
    }

    private void render() {
        Reservation r = App.get().repo().current();
        if (r == null || r.state != Reservation.State.WAITING) {
            // 도착했거나 취소됐으면 내 예약 화면이 상태를 보여 준다
            startActivity(new Intent(this, MainActivity.class)
                    .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_MY)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
            return;
        }
        countdown.setText(Fmt.countdown(r.arriveBy - App.get().now()));
    }
}
