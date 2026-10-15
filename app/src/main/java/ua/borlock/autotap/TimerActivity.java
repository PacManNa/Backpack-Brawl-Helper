package ua.borlock.autotap;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Екран створення та керування таймерами гри. */
public final class TimerActivity extends Activity {
    private static final int BG = Color.rgb(25, 21, 25);
    private static final int CARD = Color.rgb(43, 36, 43);
    private static final int SOFT = Color.rgb(55, 45, 53);
    private static final int BORDER = Color.rgb(82, 64, 75);
    private static final int CREAM = Color.rgb(248, 239, 228);
    private static final int MUTED = Color.rgb(190, 177, 185);
    private static final int ROSE = Color.rgb(228, 143, 153);
    private static final int GOLD = Color.rgb(244, 194, 126);
    private static final int GREEN = Color.rgb(153, 204, 160);
    private static final int NOTIFICATION_REQUEST = 91;
    private static final String[] EMOJIS = {
            "🌱", "🌾", "🍄", "🪴", "🐝", "🎃", "🌻", "🌳", "🎁", "📦", "💎", "🪙", "🕰️"
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<TimerRow> timerRows = new ArrayList<>();
    private LinearLayout list;
    private TextView notificationHint;
    private TextView emptyHint;
    private long renderedRevision = -1L;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateTimerRows();
            handler.postDelayed(this, 1_000L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        setContentView(buildScreen());
        ensureNotificationChannel();
    }

    @Override protected void onResume() {
        super.onResume();
        TimerStore.rescheduleAll(this);
        refreshListIfNeeded();
        refreshNotificationHint();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout content = column();
        content.setPadding(dp(20), dp(16), dp(20), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        content.addView(text("⏱  Нагадування", 24, CREAM, true), margin(-1, -2, 0, 2, 0, 0));
        content.addView(text("Таймери саду, скринь і нагород", 13, MUTED, false), margin(-1, -2, 0, 14, 0, 0));

        LinearLayout intro = column();
        intro.setPadding(dp(15), dp(14), dp(15), dp(14));
        intro.setBackground(shape(CARD, BORDER, 18));
        intro.addView(text("Запусти відлік після дії в грі. Коли час вийде, отримаєш нагадування й зможеш почати цей самий таймер знову.",
                14, CREAM, false));
        notificationHint = text("Перевіряю сповіщення…", 12, MUTED, false);
        intro.addView(notificationHint, margin(-1, -2, 0, 9, 0, 0));
        TextView notificationButton = action("Налаштувати сповіщення", SOFT, CREAM, BORDER, 12);
        notificationButton.setOnClickListener(v -> requestOrOpenNotificationSettings());
        intro.addView(notificationButton, new LinearLayout.LayoutParams(-1, dp(40)));
        content.addView(intro, margin(-1, -2, 0, 0, 0, 12));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView add = action("＋  Додати таймер", ROSE, BG, ROSE, 14);
        add.setOnClickListener(v -> showAddTimerDialog());
        actions.addView(add, new LinearLayout.LayoutParams(0, dp(48), 1f));
        TextView floating = action("⏱  Плаваюче меню", SOFT, CREAM, BORDER, 12);
        floating.setOnClickListener(v -> openFloatingPanel());
        LinearLayout.LayoutParams floatingParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        floatingParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(floating, floatingParams);
        content.addView(actions, margin(-1, -2, 0, 0, 0, 16));

        TextView heading = text("МОЇ ТАЙМЕРИ", 11, GOLD, true);
        heading.setLetterSpacing(0.08f);
        content.addView(heading, margin(-1, -2, 0, 0, 0, 8));
        list = column();
        content.addView(list, new LinearLayout.LayoutParams(-1, -2));

        TextView back = action("←  Назад", SOFT, CREAM, BORDER, 13);
        back.setOnClickListener(v -> finish());
        content.addView(back, margin(-1, 46, 0, 18, 0, 0));
        return scroll;
    }

    private void showAddTimerDialog() {
        LinearLayout form = column();
        form.setPadding(dp(4), dp(4), dp(4), 0);

        EditText name = field("Назва: наприклад, Врожай яблук", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
        form.addView(name, margin(-1, 48, 0, 4, 0, 10));

        LinearLayout emojiRow = new LinearLayout(this);
        emojiRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText emoji = field("🌱", InputType.TYPE_CLASS_TEXT);
        emoji.setText("🌱");
        emoji.setGravity(Gravity.CENTER);
        emoji.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});
        emojiRow.addView(emoji, new LinearLayout.LayoutParams(dp(70), dp(48)));
        TextView chooseEmoji = action("Обрати емодзі", SOFT, CREAM, BORDER, 12);
        chooseEmoji.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Емодзі таймера")
                .setItems(EMOJIS, (dialog, which) -> emoji.setText(EMOJIS[which]))
                .show());
        LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        chooseParams.setMargins(dp(8), 0, 0, 0);
        emojiRow.addView(chooseEmoji, chooseParams);
        form.addView(emojiRow, margin(-1, -2, 0, 0, 0, 12));

        form.addView(text("ТРИВАЛІСТЬ", 10, MUTED, true), margin(-1, -2, 0, 0, 0, 5));
        LinearLayout durationRow = new LinearLayout(this);
        durationRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText hours = field("Год", InputType.TYPE_CLASS_NUMBER);
        hours.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});
        hours.setText("1");
        hours.setGravity(Gravity.CENTER);
        durationRow.addView(hours, new LinearLayout.LayoutParams(0, dp(48), 1f));
        TextView hourLabel = text("год", 13, MUTED, true);
        hourLabel.setGravity(Gravity.CENTER);
        durationRow.addView(hourLabel, new LinearLayout.LayoutParams(dp(38), -2));
        EditText minutes = field("Хв", InputType.TYPE_CLASS_NUMBER);
        minutes.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});
        minutes.setText("0");
        minutes.setGravity(Gravity.CENTER);
        durationRow.addView(minutes, new LinearLayout.LayoutParams(0, dp(48), 1f));
        TextView minuteLabel = text("хв", 13, MUTED, true);
        minuteLabel.setGravity(Gravity.CENTER);
        durationRow.addView(minuteLabel, new LinearLayout.LayoutParams(dp(38), -2));
        form.addView(durationRow);
        form.addView(text("Таймер запускається одразу після створення. Максимум — 365 днів.",
                11, MUTED, false), margin(-1, -2, 0, 9, 0, 0));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Новий таймер")
                .setView(form)
                .setNegativeButton("Скасувати", null)
                .setPositiveButton("Запустити", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String timerName = name.getText().toString().trim();
            long hourCount = parseNumber(hours.getText().toString());
            long minuteCount = parseNumber(minutes.getText().toString());
            if (minuteCount > 59L) {
                minutes.setError("Введи від 0 до 59 хвилин");
                return;
            }
            if (hourCount > 8_760L) {
                hours.setError("Максимум — 8 760 годин");
                return;
            }
            long duration = hourCount * 60L * 60L * 1000L + minuteCount * 60L * 1000L;
            if (timerName.isEmpty()) {
                name.setError("Введи назву таймера");
                return;
            }
            if (duration <= 0L || duration > 365L * 24L * 60L * 60L * 1000L) {
                minutes.setError("Задай тривалість від 1 хв до 365 днів");
                return;
            }
            TimerStore.addTimer(this, timerName, emoji.getText().toString(), duration);
            refreshListIfNeeded();
            maybeRequestNotificationPermission();
            Toast.makeText(this, "Таймер запущено", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void refreshListIfNeeded() {
        long revision = TimerStore.getRevision(this);
        if (revision == renderedRevision || list == null) return;
        renderedRevision = revision;
        list.removeAllViews();
        timerRows.clear();
        List<TimerItem> timers = TimerStore.getTimers(this);
        if (timers.isEmpty()) {
            emptyHint = text("Ще немає таймерів. Додай перший для врожаю, скрині чи безкоштовної нагороди.",
                    14, MUTED, false);
            emptyHint.setPadding(dp(15), dp(16), dp(15), dp(16));
            emptyHint.setBackground(shape(CARD, BORDER, 16));
            list.addView(emptyHint, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        emptyHint = null;
        for (TimerItem item : timers) {
            TimerRow row = createTimerRow(item);
            timerRows.add(row);
            list.addView(row.card, margin(-1, -2, 0, 0, 0, 8));
        }
        updateTimerRows();
    }

    private TimerRow createTimerRow(TimerItem item) {
        LinearLayout card = column();
        card.setPadding(dp(14), dp(12), dp(12), dp(12));
        card.setBackground(shape(CARD, BORDER, 17));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView emoji = text(item.emoji, 21, CREAM, true);
        emoji.setGravity(Gravity.CENTER);
        header.addView(emoji, new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout labels = column();
        labels.setPadding(dp(8), 0, 0, 0);
        TextView name = text(item.name, 15, CREAM, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(name);
        TextView duration = text("Інтервал · " + formatDuration(item.durationMs), 11, MUTED, false);
        labels.addView(duration, margin(-1, -2, 0, 3, 0, 0));
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView delete = action("×", SOFT, MUTED, BORDER, 17);
        delete.setContentDescription("Видалити таймер " + item.name);
        delete.setOnClickListener(v -> confirmDelete(item));
        header.addView(delete, new LinearLayout.LayoutParams(dp(36), dp(36)));
        card.addView(header);

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView remaining = text("", 14, GOLD, true);
        footer.addView(remaining, new LinearLayout.LayoutParams(0, dp(36), 1f));
        TextView restart = action("↻  Повторити", 0xFF41533F, CREAM, 0xFF526850, 12);
        restart.setVisibility(View.GONE);
        restart.setOnClickListener(v -> {
            TimerStore.restartTimer(this, item.id);
            refreshListIfNeeded();
        });
        footer.addView(restart, new LinearLayout.LayoutParams(-2, dp(36)));
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2);
        footerParams.topMargin = dp(6);
        card.addView(footer, footerParams);
        return new TimerRow(item, card, remaining, restart);
    }

    private void updateTimerRows() {
        if (list == null) return;
        refreshListIfNeeded();
        long now = SystemClock.elapsedRealtime();
        for (TimerRow row : timerRows) {
            boolean ready = row.item.isReady(now);
            row.remaining.setText(ready ? "ГОТОВО · можна виконати дію" : formatDuration(row.item.deadlineElapsedMs - now));
            row.remaining.setTextColor(ready ? GREEN : GOLD);
            row.restart.setVisibility(ready ? View.VISIBLE : View.GONE);
        }
    }

    private void confirmDelete(TimerItem item) {
        new AlertDialog.Builder(this)
                .setMessage("Видалити таймер «" + item.name + "»?")
                .setNegativeButton("Залишити", null)
                .setPositiveButton("Видалити", (dialog, which) -> {
                    TimerStore.deleteTimer(this, item.id);
                    refreshListIfNeeded();
                })
                .show();
    }

    private void refreshNotificationHint() {
        if (notificationHint == null) return;
        boolean enabled = areNotificationsAvailable();
        notificationHint.setText(enabled
                ? "Сповіщення про завершення ввімкнені."
                : "Дозволь сповіщення в Android, щоб отримувати нагадування поза плаваючим меню.");
        notificationHint.setTextColor(enabled ? GREEN : GOLD);
    }

    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_REQUEST) {
            refreshNotificationHint();
            if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Таймери працюють у плаваючому меню; сповіщення можна ввімкнути в налаштуваннях.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void requestOrOpenNotificationSettings() {
        if (areNotificationsAvailable()) {
            Toast.makeText(this, "Сповіщення вже ввімкнені", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
                && shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            maybeRequestNotificationPermission();
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
        } catch (Exception ignored) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private boolean areNotificationsAvailable() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null || !manager.areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = manager.getNotificationChannel(TimerStore.CHANNEL_ID);
            return channel != null && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
        }
        return true;
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(TimerStore.CHANNEL_ID,
                "Таймери саду та скринь", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Нагадування про завершення вручну доданих таймерів.");
        manager.createNotificationChannel(channel);
    }

    private void openFloatingPanel() {
        if (!TapAccessibilityService.showControlPanel()) {
            Toast.makeText(this, "Спершу ввімкни службу Пелюстки в налаштуваннях доступності.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        TapAccessibilityService.showTimerPanel();
        finish();
    }

    private EditText field(String hint, int inputType) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setTextColor(CREAM);
        edit.setHintTextColor(MUTED);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        edit.setSingleLine(true);
        edit.setInputType(inputType);
        edit.setPadding(dp(12), 0, dp(12), 0);
        edit.setBackground(shape(SOFT, BORDER, 12));
        return edit;
    }

    private TextView action(String label, int background, int foreground, int border, int size) {
        TextView view = text(label, size, foreground, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), 0, dp(10), 0);
        view.setBackground(shape(background, border, 12));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setIncludeFontPadding(false);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        return view;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private GradientDrawable shape(int color, int border, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (border != 0) drawable.setStroke(dp(1), border);
        return drawable;
    }

    private LinearLayout.LayoutParams margin(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width < 0 ? width : dp(width),
                height < 0 ? height : dp(height));
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private long parseNumber(String value) {
        try { return Math.max(0L, Long.parseLong(value.trim())); }
        catch (Exception ignored) { return 0L; }
    }

    private String formatDuration(long durationMs) {
        long seconds = Math.max(0L, (durationMs + 999L) / 1000L);
        long days = seconds / 86_400L;
        long hours = (seconds % 86_400L) / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        long remainingSeconds = seconds % 60L;
        if (days > 0L) return String.format(Locale.ROOT, "%dд %02d:%02d:%02d", days, hours, minutes, remainingSeconds);
        if (hours > 0L) return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, remainingSeconds);
        return String.format(Locale.ROOT, "%02d:%02d", minutes, remainingSeconds);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class TimerRow {
        final TimerItem item;
        final View card;
        final TextView remaining;
        final TextView restart;

        TimerRow(TimerItem item, View card, TextView remaining, TextView restart) {
            this.item = item;
            this.card = card;
            this.remaining = remaining;
            this.restart = restart;
        }
    }
}
