package com.g1j2.ssutable.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.g1j2.ssutable.App;
import com.g1j2.ssutable.R;
import com.g1j2.ssutable.core.Reservation;
import com.g1j2.ssutable.core.Ticket;

/** S-08 식권 QR. 들어오면 화면 밝기를 최대로. */
public class TicketActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ticket);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL;
        getWindow().setAttributes(lp);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        findViewById(R.id.close).setOnClickListener(v -> finish());

        Reservation r = App.get().repo().current();
        if (r == null || r.ticket == null) {
            finish();
            return;
        }
        Ticket t = r.ticket;
        ((TextView) findViewById(R.id.title)).setText(t.name);
        ImageView qr = findViewById(R.id.qr);
        qr.setImageBitmap(Qr.make(t.qrPayload(), 720));

        boolean cancelled = t.status == Ticket.Status.CANCELLED;
        if (cancelled) {
            qr.setAlpha(0.15f);
            findViewById(R.id.cancelledStamp).setVisibility(View.VISIBLE);
        }

        LinearLayout rows = findViewById(R.id.rows);
        addRow(rows, "종류", t.name, false);
        addRow(rows, "발급 시각", Fmt.hm(t.issuedAt), false);
        addRow(rows, "연결된 좌석", t.seatId, false);
        addRow(rows, "상태", t.status.label, cancelled);
    }

    private void addRow(LinearLayout parent, String key, String value, boolean danger) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_kv_row, parent, false);
        ((TextView) row.findViewById(R.id.key)).setText(key);
        TextView v = row.findViewById(R.id.value);
        v.setText(value);
        if (danger) v.setTextColor(ContextCompat.getColor(this, R.color.danger));
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
}
