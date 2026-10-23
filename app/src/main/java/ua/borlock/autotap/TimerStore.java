package ua.borlock.autotap;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import java.util.List;
import java.util.UUID;

/** Persistent timer definitions, countdowns and Android alarms. */
final class TimerStore {
    static final String EXTRA_TIMER_ID = "ua.borlock.autotap.extra.TIMER_ID";
    static final String EXTRA_RUN_TOKEN = "ua.borlock.autotap.extra.TIMER_RUN";
    static final String CHANNEL_ID = "garden_timers";
    static final String ACTION_DUE = "ua.borlock.autotap.action.TIMER_DUE";
    static final String ACTION_RESTART = "ua.borlock.autotap.action.TIMER_RESTART";
    static final String ACTION_STOP = "ua.borlock.autotap.action.TIMER_STOP";
    private static final String KEY_TIMERS = "reminder_timers_v1";
    private static final String KEY_REVISION = "reminder_timers_revision";
    private static final String KEY_PRESETS = "reminder_reward_presets_v1";
    private static final String KEY_NOTIFIED_PREFIX = "reminder_notified_run_";
    private static final String LEGACY_NOTIFIED_PREFIX = "reminder_notified_";
    private TimerStore() { }

    static synchronized List<TimerItem> getTimers(Context context) {
        SharedPreferences prefs = preferences(context);
        List<TimerItem> timers = TimerCodec.decode(prefs.getString(KEY_TIMERS, "[]"));
        boolean changed = false;
        int boot = bootCount(context);
        for (int i = 0; i < timers.size(); i++) {
            TimerItem item = timers.get(i);
            if (item.isActive() && item.runToken.startsWith("legacy:")) {
                Object oldNotified = prefs.getAll().get(LEGACY_NOTIFIED_PREFIX + item.id);
                if (oldNotified instanceof Long && (Long) oldNotified == item.deadlineWallMs) {
                    prefs.edit().putString(KEY_NOTIFIED_PREFIX + item.id, item.runToken)
                            .remove(LEGACY_NOTIFIED_PREFIX + item.id).apply();
                }
            }
            if (item.isActive() && (item.deadlineElapsedMs <= 0
                    || (boot >= 0 && item.bootCount >= 0 && item.bootCount != boot))) {
                timers.set(i, item.rebaseAfterBoot(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot));
                changed = true;
            } else if (item.isActive() && item.bootCount < 0 && boot >= 0) {
                // An APK update must retain the legacy monotonic countdown. The boot
                // receiver explicitly rebases old records when a real reboot occurs.
                timers.set(i, new TimerItem(item.id, item.name, item.emoji, item.durationMs,
                        item.deadlineWallMs, item.deadlineElapsedMs, item.runToken, boot));
                changed = true;
            }
        }
        if (!prefs.getBoolean(KEY_PRESETS, false)) {
            timers = TimerPresets.mergeMissing(timers);
            changed = true;
        }
        if (changed) save(context, timers);
        return timers;
    }

    static long getRevision(Context context) {
        return preferences(context).getLong(KEY_REVISION, 0);
    }

    static synchronized boolean addTimer(Context context, String name, String emoji,
                                          long durationMs, boolean start) {
        if (!validDefinition(name, durationMs)) return false;
        TimerItem item = new TimerItem(UUID.randomUUID().toString(), name.trim(), cleanEmoji(emoji),
                durationMs, 0, 0);
        List<TimerItem> timers = getTimers(context);
        timers.add(item);
        save(context, timers);
        if (start) restartTimer(context, item.id);
        return true;
    }

    static synchronized boolean updateTimer(Context context, String id, String name,
                                             String emoji, long durationMs) {
        if (!validDefinition(name, durationMs)) return false;
        List<TimerItem> timers = getTimers(context);
        for (int i = 0; i < timers.size(); i++) {
            TimerItem old = timers.get(i);
            if (!old.id.equals(id)) continue;
            TimerItem updated = old.withDefinition(name.trim(), cleanEmoji(emoji), durationMs);
            timers.set(i, updated);
            save(context, timers);
            if (updated.isActive() && !wasNotified(context, updated)) schedule(context, updated);
            return true;
        }
        return false;
    }

    static synchronized boolean restartTimer(Context context, String id) {
        TimerItem item = findTimer(context, id);
        return item != null && startWithRemaining(context, id, item.durationMs);
    }

    static synchronized boolean startWithRemaining(Context context, String id, long delayMs) {
        if (!TimerCodec.validDuration(delayMs)) return false;
        List<TimerItem> timers = getTimers(context);
        for (int i = 0; i < timers.size(); i++) {
            TimerItem old = timers.get(i);
            if (!old.id.equals(id)) continue;
            TimerItem started = old.start(System.currentTimeMillis(), SystemClock.elapsedRealtime(),
                    delayMs, UUID.randomUUID().toString(), bootCount(context));
            timers.set(i, started);
            clearNotificationState(context, id);
            save(context, timers);
            cancelNotification(context, id);
            schedule(context, started);
            return true;
        }
        return false;
    }

    static synchronized boolean stopTimer(Context context, String id) {
        List<TimerItem> timers = getTimers(context);
        for (int i = 0; i < timers.size(); i++) {
            if (!timers.get(i).id.equals(id)) continue;
            timers.set(i, timers.get(i).stop());
            cancelAlarm(context, id);
            cancelNotification(context, id);
            clearNotificationState(context, id);
            save(context, timers);
            return true;
        }
        return false;
    }

    static synchronized boolean deleteTimer(Context context, String id) {
        List<TimerItem> timers = getTimers(context);
        if (!timers.removeIf(item -> item.id.equals(id))) return false;
        cancelAlarm(context, id);
        cancelNotification(context, id);
        clearNotificationState(context, id);
        save(context, timers);
        return true;
    }

    static TimerItem findTimer(Context context, String id) {
        for (TimerItem item : getTimers(context)) if (item.id.equals(id)) return item;
        return null;
    }

    static boolean wasNotified(Context context, TimerItem item) {
        return item.isActive() && item.runToken.equals(
                preferences(context).getString(KEY_NOTIFIED_PREFIX + item.id, ""));
    }

    static synchronized boolean markNotified(Context context, TimerItem item) {
        TimerItem current = item == null ? null : findTimer(context, item.id);
        if (current == null || !current.runToken.equals(item.runToken)
                || !current.isReady(SystemClock.elapsedRealtime()) || wasNotified(context, current)) return false;
        preferences(context).edit().putString(KEY_NOTIFIED_PREFIX + item.id, item.runToken).apply();
        return true;
    }

    static void rescheduleAll(Context context) {
        for (TimerItem item : getTimers(context)) {
            if (!item.isActive() || (item.isReady(SystemClock.elapsedRealtime()) && wasNotified(context, item))) {
                cancelAlarm(context, item.id);
            } else schedule(context, item);
        }
    }

    static synchronized void handleBootCompleted(Context context) {
        List<TimerItem> timers = getTimers(context);
        for (int i = 0; i < timers.size(); i++) {
            timers.set(i, timers.get(i).rebaseAfterBoot(
                    System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount(context)));
        }
        save(context, timers);
        rescheduleAll(context);
    }

    static synchronized void handleClockChanged(Context context) {
        List<TimerItem> timers = getTimers(context);
        for (int i = 0; i < timers.size(); i++) {
            timers.set(i, timers.get(i).preserveRemainingAfterClockChange(
                    System.currentTimeMillis(), SystemClock.elapsedRealtime()));
        }
        save(context, timers);
        rescheduleAll(context);
    }

    static boolean canScheduleExactly(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return manager != null && (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms());
    }

    private static void save(Context context, List<TimerItem> timers) {
        SharedPreferences prefs = preferences(context);
        prefs.edit().putString(KEY_TIMERS, TimerCodec.encode(timers)).putBoolean(KEY_PRESETS, true)
                .putLong(KEY_REVISION, prefs.getLong(KEY_REVISION, 0) + 1).apply();
    }

    private static void schedule(Context context, TimerItem item) {
        if (!item.isActive()) return;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        PendingIntent pending = alarmIntent(context, item, PendingIntent.FLAG_UPDATE_CURRENT);
        long trigger = Math.max(item.deadlineElapsedMs, SystemClock.elapsedRealtime() + 1000);
        if (canScheduleExactly(context)) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending);
                return;
            } catch (SecurityException ignored) { }
        }
        manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending);
    }

    static void cancelAlarm(Context context, String id) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        Intent intent = new Intent(context, TimerNotificationReceiver.class).setAction(ACTION_DUE)
                .setData(Uri.parse("pelyustka://timer/" + Uri.encode(id)));
        PendingIntent pending = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pending != null) { manager.cancel(pending); pending.cancel(); }
    }

    private static PendingIntent alarmIntent(Context context, TimerItem item, int flags) {
        // Preserve the pre-1.9.42 identity so updating the APK replaces an existing alarm.
        Intent intent = new Intent(context, TimerNotificationReceiver.class).setAction(ACTION_DUE)
                .setData(Uri.parse("pelyustka://timer/" + Uri.encode(item.id)))
                .putExtra(EXTRA_TIMER_ID, item.id).putExtra(EXTRA_RUN_TOKEN, item.runToken);
        return PendingIntent.getBroadcast(context, 0, intent, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    static Uri timerUri(String id, String action) {
        return new Uri.Builder().scheme("pelyustka").authority("timer")
                .appendPath(id).appendPath(action).build();
    }

    private static void cancelNotification(Context context, String id) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(notificationId(id));
    }

    private static void clearNotificationState(Context context, String id) {
        preferences(context).edit().remove(KEY_NOTIFIED_PREFIX + id)
                .remove(LEGACY_NOTIFIED_PREFIX + id).apply();
    }

    static int notificationId(String id) { return id.hashCode() & 0x7fffffff; }

    private static boolean validDefinition(String name, long duration) {
        return name != null && !name.trim().isEmpty() && TimerCodec.validDuration(duration);
    }

    private static String cleanEmoji(String emoji) {
        return emoji == null || emoji.trim().isEmpty() ? "⏱️" : emoji.trim();
    }

    private static int bootCount(Context context) {
        try { return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1); }
        catch (SecurityException ignored) { return -1; }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(TapAccessibilityService.PREFS, Context.MODE_PRIVATE);
    }
}
