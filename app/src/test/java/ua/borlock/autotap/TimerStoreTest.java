package ua.borlock.autotap;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowSystemClock;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
@LooperMode(LooperMode.Mode.PAUSED)
public final class TimerStoreTest {
    private Application context;
    private SharedPreferences prefs;
    private AlarmManager alarms;
    private NotificationManager notifications;
    private static final String GEMS = "reward.shop_gems", COINS = "reward.shop_coins";

    @Before public void prepare() {
        context = RuntimeEnvironment.getApplication();
        prefs = context.getSharedPreferences(TapAccessibilityService.PREFS, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        Settings.Global.putInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, 1);
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        notifications = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Shadows.shadowOf(alarms).setCanScheduleExactAlarms(true);
        TimerNotificationReceiver.ensureChannel(context);
    }

    @Test public void installingPresetsDoesNotStartAlarmsAndIsIdempotent() {
        TimerStore.rescheduleAll(context);
        assertEquals(4, TimerStore.getTimers(context).size());
        assertEquals(0, Shadows.shadowOf(alarms).getScheduledAlarms().size());
        long revision = TimerStore.getRevision(context);
        TimerStore.getTimers(context);
        assertEquals(revision, TimerStore.getRevision(context));
        for (TimerItem item : TimerStore.getTimers(context)) assertFalse(item.isActive());
    }

    @Test public void deletedPresetsStayDeletedUntilExplicitlyRestored() {
        assertTrue(TimerStore.deleteTimer(context, GEMS));
        assertNull(TimerStore.findTimer(context, GEMS));
        TimerStore.rescheduleAll(context);
        assertEquals(3, TimerStore.getTimers(context).size());
        TimerStore.addMissingPresets(context);
        assertEquals(4, TimerStore.getTimers(context).size());
        assertFalse(TimerStore.findTimer(context, GEMS).isActive());
    }

    @Test public void restartReplacesTheAlarmAndStopCancelsIt() {
        assertTrue(TimerStore.restartTimer(context, GEMS));
        TimerItem first = TimerStore.findTimer(context, GEMS);
        assertEquals(3_600_000, first.remainingMs(SystemClock.elapsedRealtime()));
        assertEquals(1, Shadows.shadowOf(alarms).getScheduledAlarms().size());
        TimerStore.restartTimer(context, GEMS);
        assertNotEquals(first.runToken, TimerStore.findTimer(context, GEMS).runToken);
        assertEquals(1, Shadows.shadowOf(alarms).getScheduledAlarms().size());
        TimerStore.stopTimer(context, GEMS);
        assertEquals(0, Shadows.shadowOf(alarms).getScheduledAlarms().size());
        advance(4_000_000);
        assertFalse(TimerStore.findTimer(context, GEMS).isReady(SystemClock.elapsedRealtime()));
    }

    @Test public void editingARunningTimerPreservesItsCurrentDeadline() {
        TimerStore.restartTimer(context, GEMS);
        TimerItem original = TimerStore.findTimer(context, GEMS);
        assertTrue(TimerStore.updateTimer(context, GEMS, "Мої самоцвіти", "💚", 7_200_000));
        TimerItem edited = TimerStore.findTimer(context, GEMS);
        assertEquals(original.deadlineElapsedMs, edited.deadlineElapsedMs);
        assertEquals(original.runToken, edited.runToken);
        assertEquals("Мої самоцвіти", edited.name);
        TimerStore.restartTimer(context, GEMS);
        assertEquals(7_200_000, TimerStore.findTimer(context, GEMS).remainingMs(SystemClock.elapsedRealtime()));
    }

    @Test public void customRemainingKeepsTheRepeatInterval() {
        assertTrue(TimerStore.startWithRemaining(context, GEMS, 1_872_000));
        TimerItem item = TimerStore.findTimer(context, GEMS);
        assertEquals(1_872_000, item.remainingMs(SystemClock.elapsedRealtime()));
        assertEquals(3_600_000, item.durationMs);
        TimerStore.restartTimer(context, GEMS);
        assertEquals(3_600_000, TimerStore.findTimer(context, GEMS).remainingMs(SystemClock.elapsedRealtime()));
    }

    @Test public void invalidChangesDoNotTouchExistingTimers() {
        TimerStore.getTimers(context);
        long revision = TimerStore.getRevision(context);
        assertFalse(TimerStore.addTimer(context, "", "", 1000, false));
        assertFalse(TimerStore.updateTimer(context, GEMS, "Гем", "", 0));
        assertFalse(TimerStore.startWithRemaining(context, GEMS, -1));
        assertFalse(TimerStore.restartTimer(context, "missing"));
        assertEquals(revision, TimerStore.getRevision(context));
    }

    @Test public void dueNotificationsAreSentOnceWithRestartAndStopActions() {
        TimerStore.startWithRemaining(context, GEMS, 1000);
        advance(1001);
        TimerNotificationReceiver.deliverDue(context);
        TimerNotificationReceiver.deliverDue(context);
        assertEquals(1, Shadows.shadowOf(notifications).getAllNotifications().size());
        Notification notification = Shadows.shadowOf(notifications).getNotification(TimerStore.notificationId(GEMS));
        assertEquals(2, notification.actions.length);
        assertTrue(TimerStore.wasNotified(context, TimerStore.findTimer(context, GEMS)));
        assertEquals(0, Shadows.shadowOf(alarms).getScheduledAlarms().size());
    }

    @Test public void deniedPermissionDoesNotLoseTheReminder() {
        TimerStore.startWithRemaining(context, GEMS, 1000);
        advance(1001);
        Shadows.shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
        TimerNotificationReceiver.deliverDue(context);
        assertEquals(0, Shadows.shadowOf(notifications).getAllNotifications().size());
        assertFalse(TimerStore.wasNotified(context, TimerStore.findTimer(context, GEMS)));
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        TimerNotificationReceiver.deliverDue(context);
        assertEquals(1, Shadows.shadowOf(notifications).getAllNotifications().size());
    }

    @Test public void globallyBlockedNotificationsAreNotMarkedAsDelivered() {
        TimerStore.startWithRemaining(context, GEMS, 1000);
        advance(1001);
        Shadows.shadowOf(notifications).setNotificationsEnabled(false);
        TimerNotificationReceiver.deliverDue(context);
        assertFalse(TimerStore.wasNotified(context, TimerStore.findTimer(context, GEMS)));
        Shadows.shadowOf(notifications).setNotificationsEnabled(true);
        TimerNotificationReceiver.deliverDue(context);
        assertEquals(1, Shadows.shadowOf(notifications).getAllNotifications().size());
    }

    @Test public void staleAlarmsAndOldNotificationActionsCannotRestartANewerRun() {
        TimerStore.startWithRemaining(context, GEMS, 1000);
        TimerItem old = TimerStore.findTimer(context, GEMS);
        TimerStore.restartTimer(context, GEMS);
        TimerItem current = TimerStore.findTimer(context, GEMS);
        advance(1001);
        new TimerNotificationReceiver().onReceive(context, intent(old, TimerStore.ACTION_DUE));
        new TimerNotificationReceiver().onReceive(context, intent(old, TimerStore.ACTION_RESTART));
        new TimerNotificationReceiver().onReceive(context, intent(old, TimerStore.ACTION_STOP));
        assertEquals(current.runToken, TimerStore.findTimer(context, GEMS).runToken);
        assertEquals(0, Shadows.shadowOf(notifications).getAllNotifications().size());
    }

    @Test public void notificationRestartStartsAFullIntervalAndStopCancelsTheNextRun() {
        TimerStore.startWithRemaining(context, GEMS, 1000);
        advance(1001);
        TimerNotificationReceiver.deliverDue(context);
        Notification notification = Shadows.shadowOf(notifications).getNotification(TimerStore.notificationId(GEMS));
        Intent restart = Shadows.shadowOf(notification.actions[0].actionIntent).getSavedIntent();
        new TimerNotificationReceiver().onReceive(context, restart);
        TimerItem restarted = TimerStore.findTimer(context, GEMS);
        assertEquals(3_600_000, restarted.remainingMs(SystemClock.elapsedRealtime()));
        assertEquals(0, Shadows.shadowOf(notifications).getAllNotifications().size());
        TimerStore.startWithRemaining(context, GEMS, 1000);
        advance(1001);
        TimerNotificationReceiver.deliverDue(context);
        notification = Shadows.shadowOf(notifications).getNotification(TimerStore.notificationId(GEMS));
        new TimerNotificationReceiver().onReceive(context,
                Shadows.shadowOf(notification.actions[1].actionIntent).getSavedIntent());
        assertFalse(TimerStore.findTimer(context, GEMS).isActive());
        assertEquals(0, Shadows.shadowOf(alarms).getScheduledAlarms().size());
    }

    @Test public void oneAlarmDeliversBothSimultaneouslyReadyShopRewards() {
        TimerStore.startWithRemaining(context, GEMS, 1000);
        TimerStore.startWithRemaining(context, COINS, 1000);
        advance(1001);
        new TimerNotificationReceiver().onReceive(context,
                intent(TimerStore.findTimer(context, GEMS), TimerStore.ACTION_DUE));
        assertEquals(2, Shadows.shadowOf(notifications).getAllNotifications().size());
    }

    @Test public void stoppingOrDeletingRemovesAnAlreadyVisibleNotification() {
        TimerStore.startWithRemaining(context, GEMS, 1000);
        advance(1001);
        TimerNotificationReceiver.deliverDue(context);
        TimerStore.stopTimer(context, GEMS);
        assertEquals(0, Shadows.shadowOf(notifications).getAllNotifications().size());
        TimerStore.startWithRemaining(context, GEMS, 1000);
        advance(1001);
        TimerNotificationReceiver.deliverDue(context);
        TimerStore.deleteTimer(context, GEMS);
        assertNull(TimerStore.findTimer(context, GEMS));
        assertEquals(0, Shadows.shadowOf(notifications).getAllNotifications().size());
    }

    @Test public void apkUpdateRestoresAlarmsWithoutRestartingTheInterval() {
        TimerStore.restartTimer(context, GEMS);
        TimerItem original = TimerStore.findTimer(context, GEMS);
        TimerStore.cancelAlarm(context, GEMS);
        new TimerBootReceiver().onReceive(context, new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));
        TimerItem restored = TimerStore.findTimer(context, GEMS);
        assertEquals(original.runToken, restored.runToken);
        assertEquals(original.deadlineElapsedMs, restored.deadlineElapsedMs);
        assertEquals(1, Shadows.shadowOf(alarms).getScheduledAlarms().size());
    }

    @Test public void rebootRebasesActiveTimersAndKeepsStoppedOnesStopped() {
        TimerStore.restartTimer(context, GEMS);
        TimerItem original = TimerStore.findTimer(context, GEMS);
        Settings.Global.putInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, 2);
        long before = System.currentTimeMillis();
        new TimerBootReceiver().onReceive(context, new Intent(Intent.ACTION_BOOT_COMPLETED));
        long after = System.currentTimeMillis();
        TimerItem restored = TimerStore.findTimer(context, GEMS);
        long remaining = restored.remainingMs(SystemClock.elapsedRealtime());
        assertTrue(remaining <= Math.max(0, original.deadlineWallMs - before));
        assertTrue(remaining >= Math.max(0, original.deadlineWallMs - after));
        assertEquals(2, restored.bootCount);
        assertFalse(TimerStore.findTimer(context, COINS).isActive());
    }

    @Test public void exactAlarmAccessHasAWorkingFallbackAndReschedulesOnGrant() {
        Shadows.shadowOf(alarms).setCanScheduleExactAlarms(false);
        TimerStore.restartTimer(context, GEMS);
        assertFalse(TimerStore.canScheduleExactly(context));
        assertEquals(1, Shadows.shadowOf(alarms).getScheduledAlarms().size());
        Shadows.shadowOf(alarms).setCanScheduleExactAlarms(true);
        new TimerBootReceiver().onReceive(context,
                new Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED));
        assertTrue(TimerStore.canScheduleExactly(context));
        assertEquals(1, Shadows.shadowOf(alarms).getScheduledAlarms().size());
    }

    @Test public void legacyTimersAndPreviouslyDeliveredRemindersSurviveMigration() {
        long deadline = System.currentTimeMillis() - 10000;
        prefs.edit().putString("reminder_timers_v1", "[{\"id\":\"old\",\"name\":\"Моя скриня\","
                + "\"emoji\":\"📦\",\"duration_ms\":900000,\"deadline_ms\":" + deadline + "}]")
                .putLong("reminder_notified_old", deadline).commit();
        TimerItem old = TimerStore.findTimer(context, "old");
        assertEquals("Моя скриня", old.name);
        assertEquals(900000, old.durationMs);
        assertTrue(old.isReady(SystemClock.elapsedRealtime()));
        assertTrue(TimerStore.wasNotified(context, old));
        assertEquals(5, TimerStore.getTimers(context).size());
        TimerStore.rescheduleAll(context);
        TimerNotificationReceiver.deliverDue(context);
        assertEquals(0, Shadows.shadowOf(notifications).getAllNotifications().size());
    }

    @Test public void legacyMonotonicDeadlineSurvivesAnApkUpgrade() {
        long elapsedDeadline = SystemClock.elapsedRealtime() + 900000;
        long wallDeadline = System.currentTimeMillis() + 1800000;
        TimerItem legacy = new TimerItem("old", "Моя скриня", "📦", 900000,
                wallDeadline, elapsedDeadline);
        prefs.edit().putString("reminder_timers_v1", TimerCodec.encode(Arrays.asList(legacy))).commit();
        new TimerBootReceiver().onReceive(context, new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));
        TimerItem migrated = TimerStore.findTimer(context, "old");
        assertEquals(elapsedDeadline, migrated.deadlineElapsedMs);
        assertEquals(1, migrated.bootCount);
        assertEquals(legacy.runToken, migrated.runToken);
    }

    @Test public void changingTheWallClockPreservesTheMonotonicCountdown() {
        TimerStore.restartTimer(context, GEMS);
        TimerItem original = TimerStore.findTimer(context, GEMS);
        advance(12345);
        long before = System.currentTimeMillis();
        new TimerBootReceiver().onReceive(context, new Intent(Intent.ACTION_TIME_CHANGED));
        long after = System.currentTimeMillis();
        TimerItem updated = TimerStore.findTimer(context, GEMS);
        assertEquals(original.deadlineElapsedMs, updated.deadlineElapsedMs);
        assertEquals(original.runToken, updated.runToken);
        long remaining = updated.remainingMs(SystemClock.elapsedRealtime());
        assertTrue(updated.deadlineWallMs >= before + remaining);
        assertTrue(updated.deadlineWallMs <= after + remaining);
    }

    @Test public void codecRoundTripsNewFieldsAndSkipsInvalidOrDuplicateRecords() {
        TimerItem original = new TimerItem("id", "Кістки", "🦴", 1000, 5000, 6000, "token", 7);
        TimerItem decoded = TimerCodec.decode(TimerCodec.encode(Arrays.asList(original))).get(0);
        assertEquals(original.runToken, decoded.runToken);
        assertEquals(original.bootCount, decoded.bootCount);
        assertEquals(original.deadlineElapsedMs, decoded.deadlineElapsedMs);
        assertEquals(original.emoji, decoded.emoji);
        List<TimerItem> valid = TimerCodec.decode("[{},null,{\"id\":\"ok\",\"name\":\"a\",\"duration_ms\":1000},"
                + "{\"id\":\"ok\",\"name\":\"duplicate\",\"duration_ms\":1000}]");
        assertEquals(1, valid.size());
        assertFalse(valid.get(0).isActive());
        assertTrue(TimerCodec.decode("broken json").isEmpty());
    }

    private static void advance(long milliseconds) { ShadowSystemClock.advanceBy(Duration.ofMillis(milliseconds)); }
    private static Intent intent(TimerItem item, String action) {
        return new Intent(action).putExtra(TimerStore.EXTRA_TIMER_ID, item.id)
                .putExtra(TimerStore.EXTRA_RUN_TOKEN, item.runToken);
    }
}
