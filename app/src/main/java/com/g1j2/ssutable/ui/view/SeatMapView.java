package com.g1j2.ssutable.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.g1j2.ssutable.R;
import com.g1j2.ssutable.core.Block;
import com.g1j2.ssutable.core.FloorSnapshot;
import com.g1j2.ssutable.core.Seat;
import com.g1j2.ssutable.core.SeatLayout;
import com.g1j2.ssutable.core.SeatState;
import com.g1j2.ssutable.core.Zone;

/** 학생식당 도면 위에 340석을 그린다. 드래그로 이동, 핀치·슬라이더로 확대. */
public class SeatMapView extends View {

    public interface Listener {
        void onSeatTap(Seat seat);

        void onZoomChanged(float zoom);
    }

    public static final float MIN_ZOOM = 1f;
    public static final float MAX_ZOOM = 4f;

    private final SeatLayout layout = SeatLayout.get();
    private FloorSnapshot snapshot;
    private boolean emptyOnly;
    private boolean outletOnly;
    private String selectedId;
    private Listener listener;

    private float zoom = 1f;
    private float tx;
    private float ty;
    private boolean placed;
    private Runnable pendingFocus;

    private final float dp;
    private final float pad;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final Matrix inverse = new Matrix();

    private final int cFloor, cInk, cInk2, cInk3, cLine, cLineStrong, cFurniture;
    private final int cFree, cFreeStroke, cReserved, cInUse, cMine, cClosed;

    private final GestureDetector gestures;
    private final ScaleGestureDetector scaler;

    public SeatMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        pad = 12 * dp;
        cFloor = color(R.color.floor);
        cInk = color(R.color.ink);
        cInk2 = color(R.color.ink2);
        cInk3 = color(R.color.ink3);
        cLine = color(R.color.line);
        cLineStrong = color(R.color.line_strong);
        cFurniture = color(R.color.furniture);
        cFree = color(R.color.seat_free);
        cFreeStroke = color(R.color.seat_free_stroke);
        cReserved = color(R.color.seat_reserved);
        cInUse = color(R.color.seat_in_use);
        cMine = color(R.color.seat_mine);
        cClosed = color(R.color.seat_closed);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFontFeatureSettings("tnum");

        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2, float dx, float dy) {
                getParent().requestDisallowInterceptTouchEvent(true);
                tx -= dx;
                ty -= dy;
                clamp();
                invalidate();
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                Seat seat = hit(e.getX(), e.getY());
                if (seat != null && listener != null) listener.onSeatTap(seat);
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                setZoom(zoom < 2f ? 2.5f : 1f, e.getX(), e.getY());
                return true;
            }
        });
        scaler = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(@NonNull ScaleGestureDetector d) {
                setZoom(zoom * d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                return true;
            }
        });
        setContentDescription("학생식당 좌석 도면");
    }

    private int color(int res) {
        return ContextCompat.getColor(getContext(), res);
    }

    // ---- 바깥에서 쓰는 것들 ----

    public void setListener(Listener l) {
        listener = l;
    }

    public void setSnapshot(FloorSnapshot s) {
        boolean same = s.sameSeats(snapshot);
        snapshot = s;
        if (!same) invalidate();
    }

    public void setEmptyOnly(boolean on) {
        emptyOnly = on;
        invalidate();
    }

    public void setOutletOnly(boolean on) {
        outletOnly = on;
        invalidate();
    }

    public void setSelected(String seatId) {
        selectedId = seatId;
        invalidate();
    }

    public float zoom() {
        return zoom;
    }

    /** 슬라이더에서 바꿀 때: 화면 가운데를 기준으로 확대. */
    public void setZoom(float z) {
        setZoom(z, getWidth() / 2f, getHeight() / 2f);
    }

    public void focusZone(Zone zone) {
        whenPlaced(() -> {
            SeatLayout.Bounds b = layout.zoneBounds.get(zone);
            float base = baseScale();
            float zw = (getWidth() - pad * 2) / ((b.right - b.left) * base);
            float zh = (getHeight() - pad * 2) / ((b.bottom - b.top) * base);
            zoom = clampZoom(Math.min(zw, zh));
            centerOn((b.left + b.right) / 2f, (b.top + b.bottom) / 2f);
        });
    }

    public void focusSeat(String seatId) {
        Seat seat = layout.seat(seatId);
        if (seat == null) return;
        whenPlaced(() -> {
            zoom = Math.max(zoom, 2.6f);
            centerOn(seat.centerX(), seat.centerY());
        });
    }

    // ---- 좌표 ----

    private float baseScale() {
        return (getWidth() - pad * 2) / SeatLayout.WIDTH;
    }

    private float scale() {
        return baseScale() * zoom;
    }

    private float clampZoom(float z) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
    }

    private void setZoom(float z, float fx, float fy) {
        float old = scale();
        z = clampZoom(z);
        if (old <= 0) {
            zoom = z;
            return;
        }
        float px = (fx - tx) / old;
        float py = (fy - ty) / old;
        zoom = z;
        float s = scale();
        tx = fx - px * s;
        ty = fy - py * s;
        clamp();
        invalidate();
        if (listener != null) listener.onZoomChanged(zoom);
    }

    private void centerOn(float px, float py) {
        float s = scale();
        tx = getWidth() / 2f - px * s;
        ty = getHeight() / 2f - py * s;
        clamp();
        invalidate();
        if (listener != null) listener.onZoomChanged(zoom);
    }

    private void clamp() {
        float s = scale();
        float cw = SeatLayout.WIDTH * s;
        float ch = SeatLayout.HEIGHT * s;
        float w = getWidth();
        float h = getHeight();
        if (cw + pad * 2 <= w) tx = (w - cw) / 2f;
        else tx = Math.max(w - cw - pad, Math.min(pad, tx));
        if (ch + pad * 2 <= h) ty = pad;
        else ty = Math.max(h - ch - pad, Math.min(pad, ty));
    }

    private void whenPlaced(Runnable r) {
        if (placed) r.run();
        else pendingFocus = r;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w == 0 || h == 0) return;
        if (!placed) {
            tx = pad;
            ty = pad;
            placed = true;
        }
        clamp();
        if (pendingFocus != null) {
            Runnable r = pendingFocus;
            pendingFocus = null;
            r.run();
        }
    }

    private Seat hit(float x, float y) {
        float s = scale();
        float px = (x - tx) / s;
        float py = (y - ty) / s;
        float slop = Math.max(4f, 8 * dp / s);
        Seat best = null;
        float bestD = Float.MAX_VALUE;
        for (Seat seat : layout.seats) {
            if (px >= seat.left - slop && px <= seat.right + slop
                    && py >= seat.top - slop && py <= seat.bottom + slop) {
                float d = Math.abs(px - seat.centerX()) + Math.abs(py - seat.centerY());
                if (d < bestD) {
                    bestD = d;
                    best = seat;
                }
            }
        }
        return best;
    }

    // ---- 그리기 ----

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float s = scale();
        if (s <= 0) return;
        float px1 = 1f / s; // 화면 1px을 도면 단위로

        canvas.save();
        canvas.translate(tx, ty);
        canvas.scale(s, s);

        // 바닥과 벽 (출입구는 위쪽 배식대 옆)
        fill.setColor(cFloor);
        canvas.drawRect(0, 0, SeatLayout.WIDTH, SeatLayout.HEIGHT, fill);
        stroke.setColor(cInk);
        stroke.setStrokeWidth(2.5f * dp * px1);
        stroke.setPathEffect(null);
        canvas.drawRect(0, 0, SeatLayout.WIDTH, SeatLayout.HEIGHT, stroke);

        for (Block b : layout.blocks) drawBlock(canvas, b, px1);
        drawZoneLabels(canvas, px1);

        boolean showNumbers = 24 * s >= 19 * dp;
        for (Seat seat : layout.seats) drawSeat(canvas, seat, px1, showNumbers);

        canvas.restore();
    }

    private void drawBlock(Canvas canvas, Block b, float px1) {
        rect.set(b.left, b.top, b.right, b.bottom);
        switch (b.kind) {
            case COUNTER:
                fill.setColor(cInk);
                canvas.drawRoundRect(rect, 4, 4, fill);
                text.setColor(Color.WHITE);
                text.setTextSize(12 * dp * px1);
                text.setFakeBoldText(true);
                canvas.drawText(b.label, rect.centerX(), rect.centerY() + 4 * dp * px1, text);
                text.setFakeBoldText(false);
                break;
            case SOFA_BACK:
                fill.setColor(cLineStrong);
                canvas.drawRoundRect(rect, 2, 2, fill);
                break;
            case PILLAR:
                fill.setColor(Color.rgb(153, 60, 29));
                canvas.drawRect(rect, fill);
                break;
            case ROUND_PILLAR:
                fill.setColor(Color.rgb(186, 117, 23));
                canvas.drawOval(rect, fill);
                break;
            case WINDOW:
                fill.setColor(Color.rgb(133, 183, 235));
                canvas.drawRect(rect, fill);
                break;
            case PLANTER:
                fill.setColor(Color.rgb(151, 196, 89));
                canvas.drawRoundRect(rect, 6, 6, fill);
                break;
            case DOOR:
                fill.setColor(cFree);
                canvas.drawRect(rect, fill);
                stroke.setColor(cInk3);
                stroke.setStrokeWidth(1f * dp * px1);
                canvas.drawRect(rect, stroke);
                text.setColor(cInk3);
                text.setTextSize(10 * dp * px1);
                canvas.drawText(b.label, rect.centerX(), rect.bottom + 14 * dp * px1, text);
                break;
            default:
                fill.setColor(cFurniture);
                canvas.drawRoundRect(rect, 3, 3, fill);
                break;
        }
    }

    private void drawZoneLabels(Canvas canvas, float px1) {
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(cInk2);
        text.setTextSize(11 * dp * px1);
        text.setFakeBoldText(true);
        canvas.drawText("A  " + Zone.A.label, 670, 232, text);
        canvas.drawText("B  " + Zone.B.label, 290, 30, text);
        canvas.drawText("C  " + Zone.C.label, 350, 112, text);
        canvas.drawText("D  " + Zone.D.label, 345, 600, text);
        text.setFakeBoldText(false);
        text.setTextAlign(Paint.Align.CENTER);
    }

    private void drawSeat(Canvas canvas, Seat seat, float px1, boolean showNumbers) {
        SeatState st = snapshot == null ? SeatState.FREE : snapshot.state(seat);
        boolean dim = (emptyOnly && st != SeatState.FREE && st != SeatState.MINE)
                || (outletOnly && !seat.outlet && st != SeatState.MINE);
        int alpha = dim ? 50 : 255;
        rect.set(seat.left, seat.top, seat.right, seat.bottom);
        float r = 4;

        int numberColor;
        switch (st) {
            case RESERVED:
                fill.setColor(cReserved);
                numberColor = cInk;
                break;
            case IN_USE:
                fill.setColor(cInUse);
                numberColor = Color.WHITE;
                break;
            case MINE:
                fill.setColor(cMine);
                numberColor = Color.WHITE;
                break;
            case CLOSED:
                fill.setColor(cClosed);
                numberColor = cInk3;
                break;
            default:
                fill.setColor(cFree);
                numberColor = cInk2;
                break;
        }
        fill.setAlpha(alpha);
        canvas.drawRoundRect(rect, r, r, fill);
        fill.setAlpha(255);

        if (st == SeatState.FREE) {
            stroke.setColor(cFreeStroke);
            stroke.setAlpha(alpha);
            stroke.setStrokeWidth(1.2f * dp * px1);
            canvas.drawRoundRect(rect, r, r, stroke);
        } else if (st == SeatState.CLOSED) {
            stroke.setColor(cLineStrong);
            stroke.setAlpha(alpha);
            stroke.setStrokeWidth(1f * dp * px1);
            canvas.drawLine(rect.left + 3, rect.bottom - 3, rect.right - 3, rect.top + 3, stroke);
        }
        stroke.setAlpha(255);

        if (outletOnly && seat.outlet && !dim) {
            fill.setColor(cInk);
            canvas.drawCircle(rect.right - 4, rect.top + 4, 2.4f * dp * px1, fill);
        }

        if (showNumbers) {
            text.setColor(numberColor);
            text.setAlpha(alpha);
            text.setTextSize(9 * dp * px1);
            canvas.drawText(seat.number(), rect.centerX(), rect.centerY() + 3.2f * dp * px1, text);
            text.setAlpha(255);
        }

        if (seat.id.equals(selectedId) || st == SeatState.MINE) {
            stroke.setColor(st == SeatState.MINE ? cMine : cInk);
            stroke.setStrokeWidth(2.2f * dp * px1);
            float g = 3.5f * dp * px1;
            rect.inset(-g, -g);
            canvas.drawRoundRect(rect, r + g, r + g, stroke);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean a = scaler.onTouchEvent(event);
        boolean b = gestures.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
        return a || b || super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
