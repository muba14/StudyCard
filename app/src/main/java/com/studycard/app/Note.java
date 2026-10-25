package com.studycard.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Tek bir ders notu: yazı, görseller, isteğe bağlı grafik ve tekrar zamanı. */
public class Note {

    public static final String CHART_BAR = "bar";
    public static final String CHART_LINE = "line";
    public static final String CHART_PIE = "pie";

    public long id = 0;
    public String subject = "";
    public String title = "";
    public String content = "";
    public List<String> images = new ArrayList<>();

    public String chartType = CHART_BAR;
    public String chartTitle = "";
    public List<ChartEntry> chartEntries = new ArrayList<>();

    /** Tekrar zamanı (epoch ms). 0 ise hatırlatıcı yok. */
    public long reviewAt = 0;
    public int reviewCount = 0;
    /** Bu tekrar zamanı için bildirim gönderildi mi? */
    public boolean notified = false;

    public long createdAt = 0;
    public long updatedAt = 0;

    public boolean hasChart() {
        return !chartEntries.isEmpty();
    }

    public boolean hasReminder() {
        return reviewAt > 0;
    }

    public static class ChartEntry {
        public String label;
        public float value;

        public ChartEntry(String label, float value) {
            this.label = label;
            this.value = value;
        }
    }

    // ---- Basit metin serileştirme (SQLite'ta saklamak için) ----

    public static String joinImages(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (s == null || s.isEmpty()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(s);
        }
        return sb.toString();
    }

    public static List<String> splitImages(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String s : raw.split("\n")) {
            if (!s.trim().isEmpty()) out.add(s.trim());
        }
        return out;
    }

    public static String serializeChart(List<ChartEntry> entries) {
        StringBuilder sb = new StringBuilder();
        for (ChartEntry e : entries) {
            String label = e.label == null ? "" : e.label.replace('\t', ' ').replace('\n', ' ');
            if (sb.length() > 0) sb.append('\n');
            sb.append(label).append('\t').append(String.format(Locale.US, "%s", e.value));
        }
        return sb.toString();
    }

    public static List<ChartEntry> parseChart(String raw) {
        List<ChartEntry> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String line : raw.split("\n")) {
            int tab = line.lastIndexOf('\t');
            if (tab < 0) continue;
            try {
                out.add(new ChartEntry(line.substring(0, tab), Float.parseFloat(line.substring(tab + 1))));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }
}
