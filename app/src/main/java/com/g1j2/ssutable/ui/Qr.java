package com.g1j2.ssutable.ui;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.EnumMap;
import java.util.Map;

/** 실제로 스캔되는 QR 코드 비트맵. */
public final class Qr {
    private Qr() {}

    public static Bitmap make(String content, int size) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            BitMatrix m = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            int w = m.getWidth();
            int h = m.getHeight();
            int[] px = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    px[y * w + x] = m.get(x, y) ? 0xFF1A1D21 : Color.WHITE;
                }
            }
            return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        } catch (WriterException e) {
            return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        }
    }
}
