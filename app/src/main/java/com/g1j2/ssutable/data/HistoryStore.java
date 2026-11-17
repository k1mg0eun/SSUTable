package com.g1j2.ssutable.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.g1j2.ssutable.core.Reservation;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 이 폰의 지난 예약 이력. LocalSeatRepository와 같은 파일·키·모양으로 SharedPreferences에 둔다. */
final class HistoryStore {
    private static final String PREFS = "repo";
    private static final String KEY_HISTORY = "history";
    private static final int HISTORY_MAX = 30;

    private final SharedPreferences prefs;
    private final List<Reservation> list = new ArrayList<>();

    HistoryStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
    }

    List<Reservation> list() {
        return Collections.unmodifiableList(new ArrayList<>(list));
    }

    /** 끝난 예약을 맨 앞에 남긴다. 이미 있으면 그대로 둔다. */
    void record(Reservation ended) {
        for (Reservation h : list) {
            if (h.id.equals(ended.id)) return;
        }
        list.add(0, ended);
        while (list.size() > HISTORY_MAX) list.remove(list.size() - 1);
        save();
    }

    void clear() {
        list.clear();
        save();
    }

    private void save() {
        try {
            JSONArray h = new JSONArray();
            for (Reservation r : list) h.put(ReservationJson.toJson(r));
            prefs.edit().putString(KEY_HISTORY, h.toString()).apply();
        } catch (JSONException ignored) {
            // 저장 실패해도 화면은 계속 동작한다
        }
    }

    private void load() {
        try {
            String h = prefs.getString(KEY_HISTORY, null);
            if (h == null) return;
            JSONArray arr = new JSONArray(h);
            for (int i = 0; i < arr.length(); i++) list.add(ReservationJson.fromJson(arr.getJSONObject(i)));
        } catch (JSONException | IllegalArgumentException e) {
            list.clear();
        }
    }
}
