package com.studycard.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Telefon yeniden başladığında / uygulama güncellendiğinde alarmları geri kurar. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        ReminderScheduler.rescheduleAll(context);
    }
}
