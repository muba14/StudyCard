package com.studycard.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextUtils;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Kütüphanesiz, mavi tonlarda çubuk / çizgi / pasta grafik çizen görünüm. */
public class ChartView extends View {

    private static final int BLUE = 0xFF1E5EFF;
    private static final int BLUE_MID = 0xFF6C9BFF;
    private static final int GRID = 0xFFE6EDFB;
    private static final int TEXT_MID = 0xFF5B6B8C;
    private static final int TEXT_DARK = 0xFF0F1B3D;
    private static final int[] PIE_COLORS = {
            0xFF1E5EFF, 0xFF0B2E8A, 0xFF6C9BFF, 0xFF1446D6, 0xFFA9C4FF,
            0xFF3D7BFF, 0xFF0E3BB0, 0xFF8AB0FF, 0xFF5584FF, 0xFFD6E4FF};

    private String type = Note.CHART_BAR;
    private final List<Note.ChartEntry> entries = new ArrayList<>();

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint value = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final float d;

    public ChartView(Context c) {
        this(c, null);
    }

    public ChartView(Context c, AttributeSet a) {
        super(c, a);
        d = getResources().getDisplayMetrics().density;
        grid.setColor(GRID);
        grid.setStrokeWidth(1 * d);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        label.setColor(TEXT_MID);
        label.setTextSize(11 * d);
        value.setColor(TEXT_DARK);
        value.setTextSize(11 * d);
        value.setFakeBoldText(true);
    }

    public void setChart(String type, List<Note.ChartEntry> data) {
        this.type = type == null ? Note.CHART_BAR : type;
        entries.clear();
        if (data != null) entries.addAll(data);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (entries.isEmpty()) {
            label.setTextAlign(Paint.Align.CENTER);
            c.drawText("Grafik için etiket ve değer gir", getWidth() / 2f, getHeight() / 2f, label);
            label.setTextAlign(Paint.Align.LEFT);
            return;
        }
        if (Note.CHART_PIE.equals(type)) drawPie(c);
        else drawAxes(c, Note.CHART_LINE.equals(type));
    }

    private static String fmt(float v) {
        if (v == (long) v) return String.format(Locale.US, "%d", (long) v);
        return String.format(new Locale("tr", "TR"), "%.1f", v);
    }

    // ---------- Çubuk & çizgi ----------

    private void drawAxes(Canvas c, boolean line) {
        float left = 34 * d, right = getWidth() - 8 * d;
        float top = 18 * d, bottom = getHeight() - 26 * d;

        float max = 0, min = 0;
        for (Note.ChartEntry e : entries) {
            max = Math.max(max, e.value);
            min = Math.min(min, e.value);
        }
        if (max == min) max = min + 1;
        float range = max - min;

        // Yatay kılavuz çizgileri + eksen değerleri
        label.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i <= 4; i++) {
            float y = bottom - (bottom - top) * i / 4f;
            c.drawLine(left, y, right, y, grid);
            c.drawText(fmt(min + range * i / 4f), left - 6 * d, y + 4 * d, label);
        }
        float zeroY = bottom - (0 - min) / range * (bottom - top);

        int n = entries.size();
        float slot = (right - left) / n;
        label.setTextAlign(Paint.Align.CENTER);
        value.setTextAlign(Paint.Align.CENTER);

        float[] xs = new float[n], ys = new float[n];
        for (int i = 0; i < n; i++) {
            Note.ChartEntry e = entries.get(i);
            xs[i] = left + slot * i + slot / 2f;
            ys[i] = bottom - (e.value - min) / range * (bottom - top);
            String l = TextUtils.ellipsize(e.label == null ? "" : e.label, label, slot - 4 * d,
                    TextUtils.TruncateAt.END).toString();
            c.drawText(l, xs[i], bottom + 16 * d, label);
        }

        if (!line) {
            float bw = Math.min(slot * 0.58f, 44 * d);
            for (int i = 0; i < n; i++) {
                float t = Math.min(ys[i], zeroY), b = Math.max(ys[i], zeroY);
                if (b - t < 2 * d) t = b - 2 * d;
                rect.set(xs[i] - bw / 2, t, xs[i] + bw / 2, b);
                fill.setShader(new LinearGradient(0, t, 0, b, BLUE, BLUE_MID, Shader.TileMode.CLAMP));
                c.drawRoundRect(rect, 7 * d, 7 * d, fill);
                fill.setShader(null);
                float vy = entries.get(i).value >= 0 ? t - 5 * d : b + 13 * d;
                c.drawText(fmt(entries.get(i).value), xs[i], vy, value);
            }
        } else {
            // Alan dolgusu
            path.reset();
            path.moveTo(xs[0], zeroY);
            for (int i = 0; i < n; i++) path.lineTo(xs[i], ys[i]);
            path.lineTo(xs[n - 1], zeroY);
            path.close();
            fill.setShader(new LinearGradient(0, top, 0, bottom, 0x551E5EFF, 0x051E5EFF, Shader.TileMode.CLAMP));
            c.drawPath(path, fill);
            fill.setShader(null);
            // Çizgi
            path.reset();
            for (int i = 0; i < n; i++) {
                if (i == 0) path.moveTo(xs[i], ys[i]);
                else path.lineTo(xs[i], ys[i]);
            }
            stroke.setColor(BLUE);
            stroke.setStrokeWidth(3 * d);
            c.drawPath(path, stroke);
            // Noktalar
            for (int i = 0; i < n; i++) {
                fill.setColor(0xFFFFFFFF);
                c.drawCircle(xs[i], ys[i], 5 * d, fill);
                fill.setColor(BLUE);
                c.drawCircle(xs[i], ys[i], 3.2f * d, fill);
                c.drawText(fmt(entries.get(i).value), xs[i], ys[i] - 9 * d, value);
            }
        }
        label.setTextAlign(Paint.Align.LEFT);
    }

    // ---------- Pasta ----------

    private void drawPie(Canvas c) {
        float total = 0;
        for (Note.ChartEntry e : entries) if (e.value > 0) total += e.value;
        if (total <= 0) {
            label.setTextAlign(Paint.Align.CENTER);
            c.drawText("Pasta grafik için pozitif değer gir", getWidth() / 2f, getHeight() / 2f, label);
            label.setTextAlign(Paint.Align.LEFT);
            return;
        }
        float h = getHeight(), w = getWidth();
        float size = Math.min(h - 16 * d, w * 0.48f);
        float cx = 8 * d + size / 2, cy = h / 2;
        rect.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);

        float start = -90;
        int ci = 0;
        for (Note.ChartEntry e : entries) {
            if (e.value <= 0) {
                ci++;
                continue;
            }
            float sweep = e.value / total * 360f;
            fill.setColor(PIE_COLORS[ci % PIE_COLORS.length]);
            c.drawArc(rect, start, sweep, true, fill);
            start += sweep;
            ci++;
        }
        // Ortası boş halka görünümü
        fill.setColor(0xFFFFFFFF);
        c.drawCircle(cx, cy, size * 0.30f, fill);
        value.setTextAlign(Paint.Align.CENTER);
        value.setTextSize(14 * d);
        c.drawText(fmt(total), cx, cy + 5 * d, value);
        value.setTextSize(11 * d);

        // Açıklama listesi
        float lx = cx + size / 2 + 18 * d;
        float rowH = 20 * d;
        int count = entries.size();
        float ly = cy - (Math.min(count, 9) * rowH) / 2f + rowH / 2f;
        label.setTextAlign(Paint.Align.LEFT);
        ci = 0;
        for (int i = 0; i < count && i < 9; i++) {
            Note.ChartEntry e = entries.get(i);
            fill.setColor(PIE_COLORS[ci % PIE_COLORS.length]);
            rect.set(lx, ly - 5 * d, lx + 10 * d, ly + 5 * d);
            c.drawRoundRect(rect, 3 * d, 3 * d, fill);
            String pct = e.value > 0 ? String.format(new Locale("tr", "TR"), " %%%.0f", e.value / total * 100) : "";
            String txt = TextUtils.ellipsize((e.label == null ? "" : e.label) + pct, label,
                    w - lx - 18 * d, TextUtils.TruncateAt.MIDDLE).toString();
            c.drawText(txt, lx + 16 * d, ly + 4 * d, label);
            ly += rowH;
            ci++;
        }
    }
}
