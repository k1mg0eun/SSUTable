package com.g1j2.ssutable.ui;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.g1j2.ssutable.App;
import com.g1j2.ssutable.R;
import com.g1j2.ssutable.core.Reservation;
import com.g1j2.ssutable.core.ReservationEngine;
import com.g1j2.ssutable.data.Prefs;

/** 예약 상태가 바뀔 때 알림을 띄운다. 설정에서 끈 항목은 띄우지 않는다. */
public final class Notifier {
    private static final String CHANNEL = "reservation";
    private static final int ID = 1001;

    private final Context context;

    public Notifier(Context context) {
        this.context = context.getApplicationContext();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "예약 알림",
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("도착 마감, 종료 전, 자리 비움, 자동 반납");
            context.getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    public void post(ReservationEngine.Event event, Reservation r) {
        if (r == null) return;
        Prefs.Alert alert = alertFor(event);
        if (alert == null || !App.get().prefs().alert(alert)) return;
        if (!App.get().hasNotificationPermission()) return;

        String seat = r.seatId;
        String title;
        String text;
        switch (event) {
            case ARRIVAL_SOON:
                title = "도착 마감 3분 전";
                text = seat + " 예약이 " + Fmt.hm(r.arriveBy) + "에 취소돼요. 도착하면 [도착]을 눌러 주세요.";
                break;
            case NO_SHOW:
                title = "예약이 취소됐어요";
                text = "10분 안에 도착 확인이 없어 " + seat + " 예약을 취소했어요.";
                break;
            case END_SOON:
                title = "이용 시간 10분 남음";
                text = seat + " 이용이 " + Fmt.hm(r.endsAt) + "에 끝나요.";
                break;
            case AWAY_STARTED:
                title = "자리 비움으로 바뀌었어요";
                text = Fmt.hm(r.awaySince + ReservationEngine.AWAY_GRACE) + "까지 돌아오지 않으면 "
                        + seat + "가 반납돼요. 돌아오면 앱을 열어 주세요.";
                break;
            case AWAY_TIMEOUT:
                title = "자리가 반납됐어요";
                text = "30분 넘게 자리를 비워 " + seat + "를 체크아웃했어요.";
                break;
            case TIME_UP:
                title = "이용 시간이 끝났어요";
                text = seat + "를 체크아웃했어요.";
                break;
            default:
                return;
        }

        Intent open = new Intent(context, MainActivity.class)
                .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_MY)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_seat_notify)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi);
        try {
            NotificationManagerCompat.from(context).notify(ID, b.build());
        } catch (SecurityException ignored) {
            // 권한이 막 회수된 경우
        }
    }

    private static Prefs.Alert alertFor(ReservationEngine.Event e) {
        switch (e) {
            case ARRIVAL_SOON: return Prefs.Alert.ARRIVAL_SOON;
            case NO_SHOW: return Prefs.Alert.NO_SHOW;
            case END_SOON: return Prefs.Alert.END_SOON;
            case AWAY_STARTED: return Prefs.Alert.AWAY;
            case AWAY_TIMEOUT: return Prefs.Alert.AWAY_TIMEOUT;
            case TIME_UP: return Prefs.Alert.TIME_UP;
            default: return null;
        }
    }
}
