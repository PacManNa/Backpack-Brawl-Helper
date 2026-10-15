package ua.borlock.autotap;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/** Показує нагадування, коли таймер завершується. */
public final class TimerNotificationReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String id = intent == null ? null : intent.getStringExtra(TimerStore.EXTRA_TIMER_ID);
        if (id == null) return;
        TimerItem item = TimerStore.findTimer(context, id);
        if (item == null || !TimerStore.markNotified(context, item)) return;

        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        ensureChannel(manager);
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;

        Intent open = new Intent(context, TimerActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, TimerStore.CHANNEL_ID)
                : new Notification.Builder(context);
        Notification notification = builder
                .setSmallIcon(R.drawable.ic_pelyustka)
                .setContentTitle(item.emoji + " " + item.name + " — час діяти")
                .setContentText("Виконай дію в грі, потім перезапусти таймер у меню.")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setPriority(Notification.PRIORITY_DEFAULT)
                .build();
        manager.notify(TimerStore.notificationId(item.id), notification);
    }

    private void ensureChannel(NotificationManager manager) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(TimerStore.CHANNEL_ID,
                "Таймери саду та скринь", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Нагадування про завершення вручну доданих таймерів.");
        manager.createNotificationChannel(channel);
    }
}
