package ua.borlock.autotap;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores alarms after reboot, APK updates, clock changes and exact-alarm permission grants. */
public final class TimerBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            TimerStore.handleBootCompleted(context);
        } else if (Intent.ACTION_TIME_CHANGED.equals(intent.getAction())) {
            TimerStore.handleClockChanged(context);
        } else if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())
                || AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(intent.getAction())) {
            TimerStore.rescheduleAll(context);
        }
    }
}
