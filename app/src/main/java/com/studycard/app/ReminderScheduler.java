package com.studycard.app;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Tekrar zamanları için AlarmManager alarmlarını kurar / iptal eder. */
public final class ReminderScheduler {

    private ReminderScheduler() {
    }

    private static PendingIntent pending(Context c, long noteId, int flags) {
        Intent i = new Intent(c, ReminderReceiver.class);
        i.setAction(ReminderReceiver.ACTION_REMIND);
        i.putExtra(ReminderReceiver.EXTRA_NOTE_ID, noteId);
        return PendingIntent.getBroadcast(c, (int) noteId, i, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Android 12+ cihazlarda "tam zamanlı alarm" izni var mı? */
    public static boolean canScheduleExact(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager am = c.getSystemService(AlarmManager.class);
        return am != null && am.canScheduleExactAlarms();
    }

    public static void schedule(Context c, Note n) {
        if (n == null || n.id == 0) return;
        if (n.reviewAt <= 0 || n.notified) {
            cancel(c, n.id);
            return;
        }
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        long trigger = Math.max(n.reviewAt, System.currentTimeMillis() + 2000);
        PendingIntent pi = pending(c, n.id, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            if (canScheduleExact(c)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
            } else {
                // İzin yoksa birkaç dakika sapabilen alarm kullanılır.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        }
    }

    public static void cancel(Context c, long noteId) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = pending(c, noteId, PendingIntent.FLAG_NO_CREATE);
        if (am != null && pi != null) {
            am.cancel(pi);
            pi.cancel();
        }
    }

    public static void dismissNotification(Context c, long noteId) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel((int) noteId);
    }

    /** Bekleyen tüm hatırlatıcıları yeniden kurar (açılışta, yeniden başlatmada). */
    public static void rescheduleAll(Context c) {
        for (Note n : NoteDb.get(c).getPendingReminders()) {
            schedule(c, n);
        }
    }
}
