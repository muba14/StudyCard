package com.studycard.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Build;

/** Alarm çaldığında tekrar bildirimini gösterir; "Yarın hatırlat" eylemini işler. */
public class ReminderReceiver extends BroadcastReceiver {

    public static final String ACTION_REMIND = "com.studycard.app.REMIND";
    public static final String ACTION_SNOOZE = "com.studycard.app.SNOOZE";
    public static final String EXTRA_NOTE_ID = "note_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        long id = intent.getLongExtra(EXTRA_NOTE_ID, 0);
        if (id == 0) return;
        NoteDb db = NoteDb.get(context);
        Note note = db.get(id);
        if (note == null) return;

        if (ACTION_SNOOZE.equals(intent.getAction())) {
            ReminderScheduler.dismissNotification(context, id);
            note.reviewAt = Ui.sameTimeDaysLater(note.reviewAt, System.currentTimeMillis(), 1);
            note.notified = false;
            db.save(note);
            ReminderScheduler.schedule(context, note);
            return;
        }

        if (!note.hasReminder() || note.notified) return;
        showNotification(context, note);
        db.setNotified(id, true);
    }

    static void showNotification(Context c, Note note) {
        App.createChannel(c);
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;

        // Bildirime dokununca: ana ekran + not detayı (geri tuşu ana ekrana döner)
        Intent main = new Intent(c, MainActivity.class);
        Intent detail = new Intent(c, NoteDetailActivity.class)
                .putExtra(NoteDetailActivity.EXTRA_ID, note.id);
        PendingIntent open = PendingIntent.getActivities(c, (int) note.id,
                new Intent[]{main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK), detail},
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent snooze = new Intent(c, ReminderReceiver.class)
                .setAction(ACTION_SNOOZE)
                .putExtra(EXTRA_NOTE_ID, note.id);
        PendingIntent snoozePi = PendingIntent.getBroadcast(c, (int) (note.id + 500000), snooze,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = "Tekrar zamanı: " + note.title;
        String subject = note.subject == null || note.subject.isEmpty() ? "Ders notu" : note.subject;
        String body = note.content == null || note.content.trim().isEmpty()
                ? "Notlarını açıp gözden geçirme vakti."
                : note.content.trim();
        String shortText = subject + " · " + (body.length() > 80 ? body.substring(0, 80) + "…" : body);

        Notification.Builder b = new Notification.Builder(c, App.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setColor(0xFF1E5EFF)
                .setContentTitle(title)
                .setContentText(shortText)
                .setSubText(subject)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(c, R.drawable.ic_alarm),
                        c.getString(R.string.snooze), snoozePi).build())
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(c, R.drawable.ic_notes),
                        c.getString(R.string.open), open).build());

        Bitmap pic = note.images.isEmpty() ? null
                : ImageStore.loadBitmap(c, note.images.get(0), 720);
        if (pic != null) {
            b.setLargeIcon(pic);
            b.setStyle(new Notification.BigPictureStyle()
                    .bigPicture(pic)
                    .bigLargeIcon((Bitmap) null)
                    .setSummaryText(shortText));
        } else {
            b.setStyle(new Notification.BigTextStyle()
                    .bigText(subject + "\n" + (body.length() > 400 ? body.substring(0, 400) + "…" : body)));
        }
        nm.notify((int) note.id, b.build());
    }
}
