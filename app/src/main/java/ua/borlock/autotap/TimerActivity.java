package ua.borlock.autotap;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** Reward timer definitions, countdown controls and notification setup. */
public final class TimerActivity extends LocalizedActivity {
    private static final int BG = 0xFF12151E, CARD = 0xFF1D222D, SOFT = 0xFF272D3A;
    private static final int BORDER = 0xFF37404F, CREAM = 0xFFF6F4ED, MUTED = 0xFFAEB7C4;
    private static final int ROSE = 0xFFF07E8C, GOLD = 0xFFF6BE5B, GREEN = 0xFF6CCB97;
    private static final int NOTIFICATION_REQUEST = 91;
    private static final String[] EMOJIS = {
            "🎟️", "💎", "🪙", "🦴", "🎁", "📦", "⏱️", "🌱", "🌾", "🍄", "🐝", "🎃", "🌳"
    };
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<TimerRow> rows = new ArrayList<>();
    private LinearLayout list;
    private ScrollView screen;
    private TextView notificationHint, precisionHint;
    private long renderedRevision = -1;
    private String focusTimerId;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refreshListIfNeeded();
            long now = SystemClock.elapsedRealtime();
            for (TimerRow row : rows) {
                row.countdown.setText(AppLanguage.timerStatus(TimerActivity.this, row.item, now));
                row.countdown.setTextColor(row.item.isReady(now) ? GREEN : row.item.isActive() ? GOLD : MUTED);
            }
            TimerNotificationReceiver.deliverDue(TimerActivity.this);
            handler.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        TimerStore.getTimers(this);
        TimerNotificationReceiver.ensureChannel(this);
        focusTimerId = getIntent().getStringExtra(TimerStore.EXTRA_TIMER_ID);
        setContentView(buildScreen());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        focusTimerId = intent.getStringExtra(TimerStore.EXTRA_TIMER_ID);
        renderedRevision = -1;
    }

    @Override protected void onResume() {
        super.onResume();
        TapAccessibilityService.minimizeTimerPanel();
        TimerStore.rescheduleAll(this);
        refreshListIfNeeded();
        refreshPermissionHints();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    private View buildScreen() {
        screen = new ScrollView(this);
        screen.setFillViewport(true);
        screen.setClipToPadding(false);
        screen.setBackgroundColor(BG);
        LinearLayout content = column();
        content.setPadding(dp(16), dp(12), dp(16), dp(16));
        screen.addView(content, new ScrollView.LayoutParams(-1, -2));
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            screen.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                content.setPadding(dp(16) + bars.left, dp(12) + bars.top,
                        dp(16) + bars.right, dp(16) + bars.bottom);
                return insets;
            });
        }
        content.addView(text(t(R.string.timer_screen_title), 22, CREAM, true));
        content.addView(text(t(R.string.timer_screen_hint),
                12, MUTED, false), margin(-1, -2, 0, 5, 0, 12));

        LinearLayout settings = column();
        settings.setPadding(dp(10), dp(10), dp(10), dp(10));
        settings.setBackground(shape(CARD, BORDER, 20));
        TextView heading = text(t(R.string.timer_setup), 13, CREAM, true);
        settings.addView(heading);
        LinearLayout settingsBody = column();
        notificationHint = text("", 12, MUTED, false);
        precisionHint = text("", 12, MUTED, false);
        settingsBody.addView(notificationHint, margin(-1, -2, 0, 6, 0, 3));
        TextView notifications = action(t(R.string.timer_notifications_settings), SOFT, CREAM, 12);
        notifications.setOnClickListener(v -> openNotificationSettings());
        settingsBody.addView(notifications, new LinearLayout.LayoutParams(-1, dp(36)));
        settingsBody.addView(precisionHint, margin(-1, -2, 0, 6, 0, 3));
        TextView exact = action(t(R.string.timer_precision_settings), SOFT, CREAM, 12);
        exact.setOnClickListener(v -> openExactAlarmSettings());
        settingsBody.addView(exact, new LinearLayout.LayoutParams(-1, dp(36)));
        settings.addView(settingsBody);
        ExpandableSection.bind(heading, settingsBody, t(R.string.timer_setup),
                getSharedPreferences(TapAccessibilityService.PREFS, MODE_PRIVATE).getBoolean("timer_setup_expanded",
                        !TimerNotificationReceiver.notificationsAvailable(this) || !TimerStore.canScheduleExactly(this)),
                expanded -> {
                    heading.setText((expanded ? "▴ " : "▾ ") + t(R.string.timer_setup));
                    getSharedPreferences(TapAccessibilityService.PREFS, MODE_PRIVATE).edit()
                            .putBoolean("timer_setup_expanded", expanded).apply();
                });
        content.addView(settings, margin(-1, -2, 0, 0, 0, 12));

        LinearLayout actions = new LinearLayout(this);
        TextView add = action(t(R.string.add_timer), ROSE, BG, 13);
        add.setOnClickListener(v -> showEditor(null));
        actions.addView(add, new LinearLayout.LayoutParams(0, dp(40), 1));
        TextView floating = action(t(R.string.timer_floating_menu), SOFT, CREAM, 12);
        floating.setOnClickListener(v -> openFloatingPanel());
        LinearLayout.LayoutParams floatingParams = new LinearLayout.LayoutParams(0, dp(40), 1);
        floatingParams.leftMargin = dp(6);
        actions.addView(floating, floatingParams);
        content.addView(actions, margin(-1, -2, 0, 0, 0, 10));
        list = column();
        content.addView(list);
        TextView back = action(t(R.string.back), SOFT, CREAM, 13);
        back.setOnClickListener(v -> finish());
        content.addView(back, new LinearLayout.LayoutParams(-1, dp(40)));
        return screen;
    }

    private void refreshListIfNeeded() {
        List<TimerItem> timers = TimerStore.getTimers(this);
        long revision = TimerStore.getRevision(this);
        if (revision == renderedRevision || list == null) return;
        renderedRevision = revision;
        rows.clear();
        list.removeAllViews();
        if (timers.isEmpty()) {
            list.addView(text(t(R.string.timers_empty_hint), 14, MUTED, false));
        }
        for (TimerItem item : timers) {
            TimerRow row = createTimerRow(item);
            rows.add(row);
            list.addView(row.card, margin(-1, -2, 0, 0, 0, 7));
            if (item.id.equals(focusTimerId)) {
                row.card.post(() -> {
                    int[] card = new int[2], root = new int[2];
                    row.card.getLocationOnScreen(card);
                    screen.getLocationOnScreen(root);
                    screen.smoothScrollTo(0, screen.getScrollY() + card[1] - root[1]);
                });
                focusTimerId = null;
            }
        }
    }

    private TimerRow createTimerRow(TimerItem item) {
        LinearLayout card = column();
        card.setPadding(dp(10), dp(7), dp(10), dp(7));
        card.setBackground(shape(CARD, BORDER, 16));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout labels = column();
        TextView name = text(item.emoji + " " + AppLanguage.timerName(this, item), 15, CREAM, true);
        name.setOnClickListener(v -> showEditor(item));
        labels.addView(name);
        LinearLayout details = new LinearLayout(this);
        details.setGravity(Gravity.CENTER_VERTICAL);
        TextView interval = text(t(R.string.timer_interval_prefix)
                + AppLanguage.timerDuration(this, item.durationMs), 10, MUTED, false);
        interval.setSingleLine(true);
        interval.setEllipsize(android.text.TextUtils.TruncateAt.END);
        details.addView(interval, new LinearLayout.LayoutParams(0, -2, 1));
        TextView countdown = text(AppLanguage.timerStatus(TimerActivity.this, item,
                SystemClock.elapsedRealtime()), 14, GOLD, true);
        countdown.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        countdown.setSingleLine(true);
        countdown.setMaxWidth(dp(190));
        details.addView(countdown, new LinearLayout.LayoutParams(-2, -2));
        labels.addView(details, margin(-1, -2, 0, 2, 0, 0));
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView edit = action("✎", SOFT, CREAM, 21);
        edit.setContentDescription(t(R.string.edit_timer_accessibility) + AppLanguage.timerName(this, item));
        edit.setOnClickListener(v -> showEditor(item));
        header.addView(edit, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView more = action("⋮", CARD, MUTED, 23);
        more.setContentDescription(t(R.string.timer_more_actions) + AppLanguage.timerName(this, item));
        more.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(AppLanguage.timerName(this, item))
                .setItems(new String[]{t(R.string.set_remaining), t(R.string.delete_timer)},
                        (dialog, index) -> { if (index == 0) showRemaining(item); else confirmDelete(item); })
                .show());
        header.addView(more, new LinearLayout.LayoutParams(dp(32), dp(36)));
        card.addView(header);
        LinearLayout controls = new LinearLayout(this);
        TextView start = action(item.isActive() ? t(R.string.restart_timer) : t(R.string.start_timer), 0xFF41533F, CREAM, 12);
        start.setContentDescription((item.isActive() ? t(R.string.restart_prefix) : t(R.string.start_prefix)) + AppLanguage.timerName(this, item));
        start.setOnClickListener(v -> {
            TimerStore.restartTimer(this, item.id);
            refreshListIfNeeded();
            requestNotificationsOnce();
        });
        controls.addView(start, new LinearLayout.LayoutParams(0, dp(40), 1));
        TextView stop = action(t(R.string.stop_timer), SOFT, CREAM, 12);
        stop.setContentDescription(t(R.string.stop_prefix) + AppLanguage.timerName(this, item));
        stop.setEnabled(item.isActive());
        stop.setAlpha(item.isActive() ? 1f : .4f);
        stop.setOnClickListener(v -> { TimerStore.stopTimer(this, item.id); refreshListIfNeeded(); });
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(0, dp(40), 1);
        stopParams.leftMargin = dp(6);
        controls.addView(stop, stopParams);
        card.addView(controls, margin(-1, -2, 0, 5, 0, 0));
        return new TimerRow(item, card, countdown);
    }

    private void showEditor(TimerItem old) {
        LinearLayout form = column();
        form.setPadding(dp(16), dp(8), dp(16), dp(8));
        EditText name = field(t(R.string.timer_name_hint), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(48)});
        if (old != null) name.setText(AppLanguage.timerName(this, old));
        form.addView(name, margin(-1, 48, 0, 0, 0, 10));
        LinearLayout emojiRow = new LinearLayout(this);
        EditText emoji = field(t(R.string.emoji), InputType.TYPE_CLASS_TEXT);
        emoji.setText(old == null ? "⏱️" : old.emoji);
        emoji.setGravity(Gravity.CENTER);
        emoji.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});
        emojiRow.addView(emoji, new LinearLayout.LayoutParams(dp(66), dp(46)));
        TextView choose = action(t(R.string.choose_emoji), SOFT, CREAM, 12);
        choose.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(t(R.string.emoji))
                .setItems(EMOJIS, (dialog, index) -> emoji.setText(EMOJIS[index])).show());
        LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        chooseParams.leftMargin = dp(8);
        emojiRow.addView(choose, chooseParams);
        form.addView(emojiRow);
        form.addView(text(t(R.string.timer_interval_heading), 11, MUTED, true), margin(-1, -2, 0, 14, 0, 6));
        DurationFields duration = new DurationFields(old == null ? 3_600_000L : old.durationMs);
        form.addView(duration.view);
        CheckBox startNow = new CheckBox(this);
        startNow.setText(old == null ? t(R.string.timer_start_now) : t(R.string.timer_save_and_restart));
        startNow.setTextColor(CREAM);
        startNow.setTextSize(12);
        form.addView(startNow, margin(-1, -2, 0, 10, 0, 0));
        form.addView(text(old != null && old.isActive()
                ? t(R.string.timer_edit_hint)
                : t(R.string.timer_create_hint),
                11, MUTED, false));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(old == null ? t(R.string.new_timer) : t(R.string.edit_timer))
                .setView(scroll).setNegativeButton(t(R.string.cancel), null).setPositiveButton(t(R.string.save), null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = name.getText().toString().trim();
            title = AppLanguage.savedTimerName(this, old, title);
            if (title.isEmpty()) { name.setError(t(R.string.enter_name)); return; }
            long interval = duration.value();
            if (interval < 0) return;
            boolean saved = old == null
                    ? TimerStore.addTimer(this, title, emoji.getText().toString(), interval, startNow.isChecked())
                    : TimerStore.updateTimer(this, old.id, title, emoji.getText().toString(), interval);
            if (!saved) { Toast.makeText(this, t(R.string.timer_save_failed), Toast.LENGTH_SHORT).show(); return; }
            if (old != null && startNow.isChecked()) TimerStore.restartTimer(this, old.id);
            refreshListIfNeeded();
            if (startNow.isChecked()) requestNotificationsOnce();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void showRemaining(TimerItem timer) {
        TimerItem current = TimerStore.findTimer(this, timer.id);
        if (current == null) return;
        LinearLayout form = column();
        form.setPadding(dp(18), dp(8), dp(18), dp(8));
        form.addView(text(t(R.string.remaining_hint),
                13, CREAM, false), margin(-1, -2, 0, 0, 0, 10));
        long remaining = current.remainingMs(SystemClock.elapsedRealtime());
        DurationFields value = new DurationFields(remaining > 0 ? remaining : current.durationMs);
        form.addView(value.view);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(t(R.string.remaining_title_prefix) + AppLanguage.timerName(this, current)).setView(form)
                .setNegativeButton(t(R.string.cancel), null).setPositiveButton(t(R.string.start_countdown), null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            long delay = value.value();
            if (delay < 0) return;
            TimerStore.startWithRemaining(this, current.id, delay);
            refreshListIfNeeded();
            requestNotificationsOnce();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void confirmDelete(TimerItem item) {
        new AlertDialog.Builder(this).setMessage(t(R.string.timer_delete_question, AppLanguage.timerName(this, item)))
                .setNegativeButton(t(R.string.keep), null).setPositiveButton(t(R.string.delete), (dialog, which) -> {
                    TimerStore.deleteTimer(this, item.id);
                    refreshListIfNeeded();
                }).show();
    }

    private void refreshPermissionHints() {
        boolean enabled = TimerNotificationReceiver.notificationsAvailable(this);
        notificationHint.setText(enabled ? t(R.string.notifications_enabled_hint)
                : t(R.string.notifications_disabled_hint));
        notificationHint.setTextColor(enabled ? GREEN : GOLD);
        boolean exact = TimerStore.canScheduleExactly(this);
        precisionHint.setText(exact ? t(R.string.exact_alarms_enabled_hint)
                : t(R.string.exact_alarms_disabled_hint));
        precisionHint.setTextColor(exact ? GREEN : GOLD);
    }

    private void requestNotificationsOnce() {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;
        android.content.SharedPreferences prefs = getSharedPreferences(TapAccessibilityService.PREFS, MODE_PRIVATE);
        if (prefs.getBoolean("timer_notification_permission_asked", false)) return;
        prefs.edit().putBoolean("timer_notification_permission_asked", true).apply();
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST);
    }

    private void openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
                && (!getSharedPreferences(TapAccessibilityService.PREFS, MODE_PRIVATE)
                .getBoolean("timer_notification_permission_asked", false)
                || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
            getSharedPreferences(TapAccessibilityService.PREFS, MODE_PRIVATE).edit()
                    .putBoolean("timer_notification_permission_asked", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST);
        } else {
            try {
                if (Build.VERSION.SDK_INT >= 26) startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                else startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (android.content.ActivityNotFoundException ignored) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            }
        }
    }

    private void openExactAlarmSettings() {
        if (TimerStore.canScheduleExactly(this)) {
            Toast.makeText(this, t(R.string.exact_alarms_already_allowed), Toast.LENGTH_SHORT).show();
        } else if (Build.VERSION.SDK_INT >= 31) {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName())));
            } catch (android.content.ActivityNotFoundException ignored) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            }
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == NOTIFICATION_REQUEST) {
            refreshPermissionHints();
            TimerStore.rescheduleAll(this);
            TimerNotificationReceiver.deliverDue(this);
        }
    }

    private void openFloatingPanel() {
        if (TapAccessibilityService.showTimerPanel()) finish();
        else new AlertDialog.Builder(this).setTitle(t(R.string.floating_menu))
                .setMessage(t(R.string.timer_accessibility_help))
                .setNegativeButton(t(R.string.later), null)
                .setPositiveButton(t(R.string.open_settings), (dialog, which) ->
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).show();
    }

    private final class DurationFields {
        final LinearLayout view = new LinearLayout(TimerActivity.this);
        final EditText hours, minutes, seconds;
        DurationFields(long value) {
            long total = (value + 999) / 1000;
            hours = part(t(R.string.hours), total / 3600);
            minutes = part(t(R.string.minutes), total / 60 % 60);
            seconds = part(t(R.string.seconds), total % 60);
        }
        private EditText part(String label, long value) {
            LinearLayout group = column();
            TextView title = text(label, 10, MUTED, false);
            title.setGravity(Gravity.CENTER);
            group.addView(title, margin(-1, -2, 0, 0, 0, 4));
            EditText input = field("0", InputType.TYPE_CLASS_NUMBER);
            input.setContentDescription(label);
            input.setGravity(Gravity.CENTER);
            input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});
            input.setText(Long.toString(value));
            group.addView(input, new LinearLayout.LayoutParams(-1, dp(46)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            if (view.getChildCount() > 0) params.leftMargin = dp(7);
            view.addView(group, params);
            return input;
        }
        long value() {
            long h = number(hours), m = number(minutes), s = number(seconds);
            if (h < 0 || h > 8760) { hours.setError(t(R.string.hours_range)); return -1; }
            if (m < 0 || m > 59) { minutes.setError(t(R.string.minute_second_range)); return -1; }
            if (s < 0 || s > 59) { seconds.setError(t(R.string.minute_second_range)); return -1; }
            long result = (h * 3600 + m * 60 + s) * 1000;
            if (!TimerCodec.validDuration(result)) { seconds.setError(t(R.string.timer_duration_range)); return -1; }
            return result;
        }
        private long number(EditText field) {
            try { return field.getText().toString().trim().isEmpty() ? 0 : Long.parseLong(field.getText().toString().trim()); }
            catch (NumberFormatException ignored) { return -1; }
        }
    }

    private EditText field(String hint, int type) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextColor(CREAM);
        input.setHintTextColor(MUTED);
        input.setTextSize(14);
        input.setSingleLine(true);
        input.setInputType(type);
        input.setPadding(dp(10), 0, dp(10), 0);
        input.setBackground(shape(SOFT, BORDER, 11));
        return input;
    }
    private TextView action(String label, int background, int foreground, int size) {
        TextView view = text(label, size, foreground, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), dp(3), dp(8), dp(3));
        view.setBackground(shape(background, BORDER, 10));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setTextLocale(AppLanguage.locale(this));
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setIncludeFontPadding(false);
        return view;
    }
    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }
    private GradientDrawable shape(int color, int border, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (border != 0) drawable.setStroke(dp(1), border);
        return drawable;
    }
    private LinearLayout.LayoutParams margin(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width < 0 ? width : dp(width), height < 0 ? height : dp(height));
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static final class TimerRow {
        final TimerItem item;
        final LinearLayout card;
        final TextView countdown;
        TimerRow(TimerItem item, LinearLayout card, TextView countdown) {
            this.item = item; this.card = card; this.countdown = countdown;
        }
    }

    private String t(int id, Object... args) {
        return AppLanguage.text(this, id, args);
    }
}
