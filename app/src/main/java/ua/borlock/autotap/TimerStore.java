package ua.borlock.autotap;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Локальне сховище таймерів і системних нагадувань. */
final class TimerStore {
    static final String EXTRA_TIMER_ID = "ua.borlock.autotap.extra.TIMER_ID";
    static final String CHANNEL_ID = "garden_timers";
    private static final String KEY_TIMERS = "reminder_timers_v1";
    private static final String KEY_REVISION = "reminder_timers_revision";
    private static final String KEY_NOTIFIED_PREFIX = "reminder_notified_";
    private static final long MAX_DURATION_MS = 365L * 24L * 60L * 60L * 1000L;

    private TimerStore() { }

    static List<TimerItem> getTimers(Context context) {
        String json = preferences(context).getString(KEY_TIMERS, "[]");
        ArrayList<TimerItem> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("id", "");
                String name = item.optString("name", "").trim();
                String emoji = item.optString("emoji", "🌱").trim();
                long duration = item.optLong("duration_ms", 0L);
                long wallDeadline = item.optLong("deadline_wall_ms", item.optLong("deadline_ms", 0L));
                long elapsedDeadline = item.optLong("deadline_elapsed_ms", 0L);
                if (id.isEmpty() || name.isEmpty() || duration <= 0L || duration > MAX_DURATION_MS) continue;
                if (elapsedDeadline <= 0L && wallDeadline > 0L) {
                    long remaining = Math.max(0L, wallDeadline - System.currentTimeMillis());
                    elapsedDeadline = SystemClock.elapsedRealtime() + remaining;
                }
                result.add(new TimerItem(id, name, emoji.isEmpty() ? "⏱️" : emoji, duration,
                        wallDeadline, elapsedDeadline));
            }
        } catch (JSONException ignored) { }
        return result;
    }

    static long getRevision(Context context) {
        return preferences(context).getLong(KEY_REVISION, 0L);
    }

    static void addTimer(Context context, String name, String emoji, long durationMs) {
        if (durationMs <= 0L || durationMs > MAX_DURATION_MS) return;
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty()) return;
        String cleanEmoji = emoji == null ? "" : emoji.trim();
        long nowWall = System.currentTimeMillis();
        long nowElapsed = SystemClock.elapsedRealtime();
        TimerItem item = new TimerItem(UUID.randomUUID().toString(), cleanName,
                cleanEmoji.isEmpty() ? "⏱️" : cleanEmoji, durationMs,
                nowWall + durationMs, nowElapsed + durationMs);
        List<TimerItem> timers = getTimers(context);
        timers.add(item);
        save(context, timers);
        schedule(context, item);
    }

    static boolean restartTimer(Context context, String id) {
        List<TimerItem> timers = getTimers(context);
        long nowWall = System.currentTimeMillis();
        long nowElapsed = SystemClock.elapsedRealtime();
        for (int i = 0; i < timers.size(); i++) {
            TimerItem old = timers.get(i);
            if (!old.id.equals(id)) continue;
            TimerItem restarted = new TimerItem(old.id, old.name, old.emoji, old.durationMs,
                    nowWall + old.durationMs, nowElapsed + old.durationMs);
            timers.set(i, restarted);
            preferences(context).edit().remove(KEY_NOTIFIED_PREFIX + id).apply();
            save(context, timers);
            cancelNotification(context, id);
            schedule(context, restarted);
            return true;
        }
        return false;
    }

    static boolean deleteTimer(Context context, String id) {
        List<TimerItem> timers = getTimers(context);
        boolean removed = false;
        for (int i = timers.size() - 1; i >= 0; i--) {
            if (timers.get(i).id.equals(id)) {
                timers.remove(i);
                removed = true;
            }
        }
        if (!removed) return false;
        cancelAlarm(context, id);
        cancelNotification(context, id);
        preferences(context).edit().remove(KEY_NOTIFIED_PREFIX + id).apply();
        save(context, timers);
        return true;
    }

    static TimerItem findTimer(Context context, String id) {
        for (TimerItem item : getTimers(context)) if (item.id.equals(id)) return item;
        return null;
    }

    static boolean markNotified(Context context, TimerItem item) {
        if (item == null || !item.isReady(SystemClock.elapsedRealtime())) return false;
        long previous = preferences(context).getLong(KEY_NOTIFIED_PREFIX + item.id, 0L);
        if (previous == item.deadlineWallMs) return false;
        preferences(context).edit().putLong(KEY_NOTIFIED_PREFIX + item.id, item.deadlineWallMs).apply();
        return true;
    }

    static void rescheduleAll(Context context) {
        long now = SystemClock.elapsedRealtime();
        for (TimerItem item : getTimers(context)) {
            if (item.deadlineElapsedMs <= 0L) continue;
            if (item.deadlineElapsedMs > now ||
                    preferences(context).getLong(KEY_NOTIFIED_PREFIX + item.id, 0L) != item.deadlineWallMs) {
                schedule(context, item);
            } else {
                cancelAlarm(context, item.id);
            }
        }
    }

    static void handleBootCompleted(Context context) {
        List<TimerItem> timers = getTimers(context);
        if (timers.isEmpty()) return;
        long nowWall = System.currentTimeMillis();
        long nowElapsed = SystemClock.elapsedRealtime();
        for (int i = 0; i < timers.size(); i++) {
            TimerItem item = timers.get(i);
            long remaining = Math.max(0L, item.deadlineWallMs - nowWall);
            timers.set(i, new TimerItem(item.id, item.name, item.emoji, item.durationMs,
                    item.deadlineWallMs, nowElapsed + remaining));
        }
        save(context, timers);
        rescheduleAll(context);
    }

    private static void save(Context context, List<TimerItem> timers) {
        JSONArray array = new JSONArray();
        for (TimerItem item : timers) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", item.id);
                object.put("name", item.name);
                object.put("emoji", item.emoji);
                object.put("duration_ms", item.durationMs);
                object.put("deadline_wall_ms", item.deadlineWallMs);
                object.put("deadline_elapsed_ms", item.deadlineElapsedMs);
                array.put(object);
            } catch (JSONException ignored) { }
        }
        android.content.SharedPreferences prefs = preferences(context);
        prefs.edit().putString(KEY_TIMERS, array.toString())
                .putLong(KEY_REVISION, prefs.getLong(KEY_REVISION, 0L) + 1L).apply();
    }

    private static void schedule(Context context, TimerItem item) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        PendingIntent pendingIntent = alarmIntent(context, item.id, PendingIntent.FLAG_UPDATE_CURRENT);
        long triggerAt = Math.max(item.deadlineElapsedMs, SystemClock.elapsedRealtime() + 1_000L);
        alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
    }

    private static void cancelAlarm(Context context, String id) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        PendingIntent pendingIntent = alarmIntent(context, id, PendingIntent.FLAG_NO_CREATE);
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }

    private static PendingIntent alarmIntent(Context context, String id, int mode) {
        int flags = mode;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        Intent intent = new Intent(context, TimerNotificationReceiver.class)
                .setAction("ua.borlock.autotap.action.TIMER_DUE")
                .setData(Uri.parse("pelyustka://timer/" + Uri.encode(id)))
                .putExtra(EXTRA_TIMER_ID, id);
        return PendingIntent.getBroadcast(context, 0, intent, flags);
    }

    private static void cancelNotification(Context context, String id) {
        android.app.NotificationManager manager = (android.app.NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(notificationId(id));
    }

    static int notificationId(String id) {
        return id.hashCode() & 0x7fffffff;
    }

    private static android.content.SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(TapAccessibilityService.PREFS, Context.MODE_PRIVATE);
    }
}
