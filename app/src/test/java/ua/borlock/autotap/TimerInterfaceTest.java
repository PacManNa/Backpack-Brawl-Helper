package ua.borlock.autotap;

import android.Manifest;
import android.content.Context;
import android.os.SystemClock;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.app.AlertDialog;
import java.lang.reflect.Field;
import java.util.ArrayList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
@LooperMode(LooperMode.Mode.PAUSED)
public final class TimerInterfaceTest {
    private ActivityController<TimerActivity> controller;
    private TimerActivity activity;
    private static final String TICKETS = "reward.event_tickets";

    @Before public void prepare() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(TapAccessibilityService.PREFS, 0).edit().clear().commit();
        context.getSharedPreferences("pelyustka_modules", 0).edit().clear().commit();
        Settings.Global.putInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, 1);
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
    }

    @After public void closeActivity() {
        if (controller != null) controller.pause().stop().destroy();
    }

    private View screen() {
        if (controller == null) {
            controller = Robolectric.buildActivity(TimerActivity.class).setup();
            activity = controller.get();
        }
        return activity.getWindow().getDecorView();
    }

    @Test public void appButtonsStartAndStopTheSelectedTimer() {
        View root = screen();
        assertNotNull(findText(root, "Сповіщення та точний час"));
        assertFalse(TimerStore.findTimer(activity, TICKETS).isActive());
        findDescription(root, "Запустити Квитки події").performClick();
        assertTrue(TimerStore.findTimer(activity, TICKETS).isActive());
        findDescription(root, "Зупинити Квитки події").performClick();
        assertFalse(TimerStore.findTimer(activity, TICKETS).isActive());
    }

    @Test public void editorChangesTheIntervalWithoutResettingAnActiveCountdown() {
        View root = screen();
        findDescription(root, "Запустити Квитки події").performClick();
        long deadline = TimerStore.findTimer(activity, TICKETS).deadlineElapsedMs;
        findDescription(root, "Редагувати таймер Квитки події").performClick();
        AlertDialog dialog = latestDialog();
        ArrayList<EditText> fields = new ArrayList<>();
        collectInputs(dialog.getWindow().getDecorView(), fields);
        fields.get(0).setText("Квитки — змінено");
        ((EditText) findDescription(dialog.getWindow().getDecorView(), "Години")).setText("5");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        TimerItem item = TimerStore.findTimer(activity, TICKETS);
        assertEquals("Квитки — змінено", item.name);
        assertEquals(18_000_000, item.durationMs);
        assertEquals(deadline, item.deadlineElapsedMs);
    }

    @Test public void remainingDialogUsesTheValueFromTheGameForOneRun() {
        View root = screen();
        findDescription(root, "Інші дії з таймером Квитки події").performClick();
        AlertDialog menu = latestDialog();
        menu.getListView().performItemClick(null, 0, 0);
        AlertDialog dialog = latestDialog();
        View form = dialog.getWindow().getDecorView();
        ((EditText) findDescription(form, "Години")).setText("0");
        ((EditText) findDescription(form, "Хвилини")).setText("31");
        ((EditText) findDescription(form, "Секунди")).setText("12");
        long before = SystemClock.elapsedRealtime();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        long after = SystemClock.elapsedRealtime();
        TimerItem item = TimerStore.findTimer(activity, TICKETS);
        assertTrue(item.deadlineElapsedMs >= before + 1_872_000);
        assertTrue(item.deadlineElapsedMs <= after + 1_872_000);
        assertEquals(14_400_000, item.durationMs);
    }

    @Test public void addingATimerCanSaveItWithoutStartingTheCountdown() {
        findText(screen(), "Додати таймер").performClick();
        AlertDialog dialog = latestDialog();
        ArrayList<EditText> fields = new ArrayList<>();
        collectInputs(dialog.getWindow().getDecorView(), fields);
        fields.get(0).setText("Моя скриня");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        TimerItem added = null;
        for (TimerItem item : TimerStore.getTimers(activity)) if (item.name.equals("Моя скриня")) added = item;
        assertNotNull(added);
        assertFalse(added.isActive());
    }

    @Test public void timerWindowCoexistsWithTheBotAndClosingItKeepsCountdowns() throws Exception {
        ServiceController<TapAccessibilityService> serviceController = Robolectric.buildService(TapAccessibilityService.class).create();
        TapAccessibilityService service = serviceController.get();
        TimerOverlay overlay = new TimerOverlay(service);
        LinearLayout botWindow = new LinearLayout(service);
        set(TapAccessibilityService.class, service, "panel", botWindow);
        set(TapAccessibilityService.class, service, "autoPlaying", true);
        set(TapAccessibilityService.class, service, "timerOverlay", overlay);
        set(TapAccessibilityService.class, null, "instance", service);
        try {
            assertTrue(TapAccessibilityService.showTimerPanel());
            assertSame(botWindow, get(TapAccessibilityService.class, service, "panel"));
            assertEquals(true, get(TapAccessibilityService.class, service, "autoPlaying"));
            View root = (View) get(TimerOverlay.class, overlay, "root");
            findDescription(root, "Запустити Квитки події").performClick();
            findText(root, "В іконку").performClick();
            assertEquals(true, get(TimerOverlay.class, overlay, "collapsed"));
            ((View) get(TimerOverlay.class, overlay, "bubble")).performClick();
            assertEquals(false, get(TimerOverlay.class, overlay, "collapsed"));
            overlay.close();
            assertTrue(TimerStore.findTimer(service, TICKETS).isActive());
            assertEquals(true, get(TapAccessibilityService.class, service, "autoPlaying"));
        } finally {
            overlay.close();
            set(TapAccessibilityService.class, null, "instance", null);
            serviceController.destroy();
        }
    }

    private static View findText(View view, String text) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(text)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
    private static AlertDialog latestDialog() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return ShadowAlertDialog.getLatestAlertDialog();
    }
    private static View findDescription(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findDescription(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }
    private static void collectInputs(View view, ArrayList<EditText> inputs) {
        if (view instanceof EditText) inputs.add((EditText) view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectInputs(group.getChildAt(i), inputs);
        }
    }
    private static Object get(Class<?> type, Object owner, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static void set(Class<?> type, Object owner, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
    }
}
