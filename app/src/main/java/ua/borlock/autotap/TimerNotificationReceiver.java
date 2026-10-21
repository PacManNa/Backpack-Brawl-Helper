package ua.borlock.autotap;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;

/** Delivers one reminder per run and handles explicit notification actions. */
public final class TimerNotificationReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String id = intent.getStringExtra(TimerStore.EXTRA_TIMER_ID);
        TimerItem item = id == null ? null : TimerStore.findTimer(context, id);
        if (item == null || !item.isActive()) return;
        String token = intent.getStringExtra(TimerStore.EXTRA_RUN_TOKEN);
        if (token == null ? !item.runToken.startsWith("legacy:") : !token.equals(item.runToken)) return;
        String action = intent.getAction();
        if (TimerStore.ACTION_RESTART.equals(action)) {
            if (item.isReady(SystemClock.elapsedRealtime())) TimerStore.restartTimer(context, id);
        } else if (TimerStore.ACTION_STOP.equals(action)) {
            TimerStore.stopTimer(context, id);
        } else if (TimerStore.ACTION_DUE.equals(action)) {
            if (!item.isReady(SystemClock.elapsedRealtime())) TimerStore.rescheduleAll(context);
            deliverDue(context);
        }
    }

    static void deliverDue(Context context) {
        java.util.List<TimerItem> timers = TimerStore.getTimers(context);
        boolean due = false;
        for (TimerItem item : timers) {
            if (item.isReady(SystemClock.elapsedRealtime()) && !TimerStore.wasNotified(context, item)) {
                due = true;
                break;
            }
        }
        if (!due) return;
        ensureChannel(context);
        if (!notificationsAvailable(context)) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        for (TimerItem item : timers) {
            if (!item.isReady(SystemClock.elapsedRealtime()) || TimerStore.wasNotified(context, item)) continue;
            Intent open = new Intent(context, TimerActivity.class)
                    .setData(TimerStore.timerUri(item.id, "open"))
                    .putExtra(TimerStore.EXTRA_TIMER_ID, item.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent content = PendingIntent.getActivity(context, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(context, TimerStore.CHANNEL_ID)
                    : new Notification.Builder(context);
            Notification notification = builder.setSmallIcon(R.drawable.ic_pelyustka)
                    .setContentTitle(item.emoji + " " + item.name + " — готово")
                    .setContentText("Отримай нагороду в грі, потім перезапусти відлік.")
                    .setContentIntent(content).setAutoCancel(true).setOnlyAlertOnce(true)
                    .setCategory(Notification.CATEGORY_REMINDER).setPriority(Notification.PRIORITY_DEFAULT)
                    .addAction(0, "Отримано · ще раз", action(context, item, TimerStore.ACTION_RESTART))
                    .addAction(0, "Зупинити", action(context, item, TimerStore.ACTION_STOP))
                    .build();
            try {
                manager.notify(TimerStore.notificationId(item.id), notification);
                // Permission/channel checks happen before marking delivery, so a denied
                // notification remains eligible when the user enables notifications.
                TimerStore.markNotified(context, item);
                TimerStore.cancelAlarm(context, item.id);
            } catch (SecurityException ignored) { return; }
        }
    }

    private static PendingIntent action(Context context, TimerItem item, String action) {
        Intent intent = new Intent(context, TimerNotificationReceiver.class).setAction(action)
                .setData(TimerStore.timerUri(item.id, action).buildUpon().appendPath(item.runToken).build())
                .putExtra(TimerStore.EXTRA_TIMER_ID, item.id).putExtra(TimerStore.EXTRA_RUN_TOKEN, item.runToken);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static boolean notificationsAvailable(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || !manager.areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = manager.getNotificationChannel(TimerStore.CHANNEL_ID);
            return channel != null && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
        }
        return true;
    }

    static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(TimerStore.CHANNEL_ID,
                "Нагадування про нагороди", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Завершення таймерів нагород, подій та інших дій.");
        manager.createNotificationChannel(channel);
    }
}
