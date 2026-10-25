package com.studycard.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.function.IntConsumer;

/** Ortak arayüz ve tarih yardımcıları. */
public final class Ui {

    public static final Locale TR = new Locale("tr", "TR");

    /** Aralıklı tekrar için önerilen gün aralıkları (tekrar sayısına göre). */
    public static final int[] SPACED_DAYS = {1, 3, 7, 14, 30, 60};

    private Ui() {
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    // ---------------- Kenardan kenara görünüm ----------------

    /**
     * Uygulamayı durum çubuğunun altına kadar çizer; başlığa üst boşluk,
     * alttaki görünüme gezinme çubuğu (ve klavye) kadar alt boşluk ekler.
     */
    public static void edgeToEdge(Activity a, View header, View bottom, IntConsumer extraBottom) {
        Window w = a.getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            w.setNavigationBarContrastEnforced(false);
            w.setStatusBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController ic = w.getInsetsController();
            if (ic != null) {
                ic.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (Build.VERSION.SDK_INT >= 27) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            w.getDecorView().setSystemUiVisibility(flags);
        }

        final int hl = header.getPaddingLeft(), ht = header.getPaddingTop(),
                hr = header.getPaddingRight(), hb = header.getPaddingBottom();
        final int bl = bottom.getPaddingLeft(), bt = bottom.getPaddingTop(),
                br = bottom.getPaddingRight(), bb = bottom.getPaddingBottom();

        View root = a.findViewById(android.R.id.content);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bot, left, right;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                Insets ime = insets.getInsets(WindowInsets.Type.ime());
                top = bars.top;
                bot = Math.max(bars.bottom, ime.bottom);
                left = bars.left;
                right = bars.right;
            } else {
                top = insets.getSystemWindowInsetTop();
                bot = insets.getSystemWindowInsetBottom();
                left = insets.getSystemWindowInsetLeft();
                right = insets.getSystemWindowInsetRight();
            }
            v.setPadding(left, 0, right, 0);
            header.setPadding(hl, ht + top, hr, hb);
            bottom.setPadding(bl, bt, br, bb + bot);
            if (extraBottom != null) extraBottom.accept(bot);
            return insets;
        });
        root.requestApplyInsets();
    }

    // ---------------- Tarih yardımcıları ----------------

    public static long startOfDay(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public static long endOfToday() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(startOfDay(System.currentTimeMillis()));
        c.add(Calendar.DAY_OF_MONTH, 1);
        return c.getTimeInMillis() - 1;
    }

    private static int daysBetween(long fromDayStart, long toDayStart) {
        Calendar a = Calendar.getInstance();
        a.setTimeInMillis(fromDayStart);
        int n = 0;
        if (toDayStart >= fromDayStart) {
            while (a.getTimeInMillis() < toDayStart && n < 4000) {
                a.add(Calendar.DAY_OF_MONTH, 1);
                n++;
            }
            return n;
        }
        while (a.getTimeInMillis() > toDayStart && n < 4000) {
            a.add(Calendar.DAY_OF_MONTH, -1);
            n++;
        }
        return -n;
    }

    /**
     * Bugünden itibaren {@code days} gün sonrası; saat olarak önceki tekrar
     * saatini korur (yoksa 09:00).
     */
    public static long sameTimeDaysLater(long previousAt, long now, int days) {
        Calendar src = Calendar.getInstance();
        int hour = 9, minute = 0;
        if (previousAt > 0) {
            src.setTimeInMillis(previousAt);
            hour = src.get(Calendar.HOUR_OF_DAY);
            minute = src.get(Calendar.MINUTE);
        }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.add(Calendar.DAY_OF_MONTH, days);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public static int suggestedDays(int reviewCount) {
        return SPACED_DAYS[Math.min(Math.max(reviewCount, 0), SPACED_DAYS.length - 1)];
    }

    public static String daysLabel(int days) {
        if (days == 1) return "Yarın";
        if (days == 7) return "1 hafta sonra";
        if (days == 14) return "2 hafta sonra";
        if (days == 30) return "1 ay sonra";
        if (days == 60) return "2 ay sonra";
        return days + " gün sonra";
    }

    /** "27 Ekim Salı · 09:00" */
    public static String formatFull(long t) {
        return new SimpleDateFormat("d MMMM EEEE · HH:mm", TR).format(new Date(t));
    }

    public static String formatTime(long t) {
        return new SimpleDateFormat("HH:mm", TR).format(new Date(t));
    }

    /** "Bugün · 09:00", "Yarın · 18:30", "Cuma · 10:00", "12 Kasım · 09:00" */
    public static String formatFriendly(long t) {
        long today = startOfDay(System.currentTimeMillis());
        int d = daysBetween(today, startOfDay(t));
        String time = formatTime(t);
        if (d == 0) return "Bugün · " + time;
        if (d == 1) return "Yarın · " + time;
        if (d == -1) return "Dün · " + time;
        if (d > 1 && d < 7) return new SimpleDateFormat("EEEE", TR).format(new Date(t)) + " · " + time;
        return new SimpleDateFormat("d MMMM", TR).format(new Date(t)) + " · " + time;
    }

    /** Liste kartındaki küçük rozet metni. */
    public static String badge(long t) {
        long now = System.currentTimeMillis();
        long today = startOfDay(now);
        int d = daysBetween(today, startOfDay(t));
        if (d < 0) return "Gecikti · " + (-d) + " gün";
        if (d == 0) return t <= now ? "Şimdi tekrar et" : "Bugün " + formatTime(t);
        if (d == 1) return "Yarın " + formatTime(t);
        if (d < 7) return d + " gün sonra";
        return new SimpleDateFormat("d MMM", TR).format(new Date(t));
    }

    public static boolean isDueToday(long t) {
        return t > 0 && t <= endOfToday();
    }

    public static boolean isOverdue(long t) {
        return t > 0 && t < startOfDay(System.currentTimeMillis());
    }

    public static String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h < 6) return "İyi geceler";
        if (h < 12) return "Günaydın";
        if (h < 18) return "İyi günler";
        return "İyi akşamlar";
    }
}
