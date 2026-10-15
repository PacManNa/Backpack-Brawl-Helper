package ua.borlock.autotap;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.ArrayList;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(25, 21, 25);
    private static final int CARD = Color.rgb(43, 36, 43);
    private static final int CARD_SOFT = Color.rgb(55, 45, 53);
    private static final int BORDER = Color.rgb(82, 64, 75);
    private static final int CREAM = Color.rgb(248, 239, 228);
    private static final int MUTED = Color.rgb(190, 177, 185);
    private static final int ROSE = Color.rgb(228, 143, 153);
    private static final int GOLD = Color.rgb(244, 194, 126);
    private static final int GREEN = Color.rgb(153, 204, 160);

    private TextView serviceState;
    private TextView serviceDetail;
    private TextView permissionButton;
    private TextView serviceHelp;
    private TextView helpToggle;
    private TextView appSettingsButton;
    private final ArrayList<TextView> botLaunchButtons = new ArrayList<>();
    private TextView intervalValue;
    private View permissionCard;
    private boolean serviceHelpExpanded;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        setContentView(buildScreen());
    }

    @Override protected void onResume() {
        super.onResume();
        TimerStore.rescheduleAll(this);
        refreshStatus();
    }

    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(BG);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(12), dp(20), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                content.setPadding(dp(20) + bars.left, dp(12) + bars.top,
                        dp(20) + bars.right, dp(24) + bars.bottom);
                return insets;
            });
        }

        content.addView(buildHeader(), margin(-1, -2, 0, 4, 0, 14));
        content.addView(buildBotsSection(), margin(-1, -2, 0, 0, 0, 12));
        content.addView(buildTimerCard(), margin(-1, -2, 0, 0, 0, 12));
        content.addView(buildPermissionCard(), margin(-1, -2, 0, 0, 0, 12));
        content.addView(buildIntervalCard(), margin(-1, -2, 0, 0, 0, 12));
        content.addView(buildStepsCard(), margin(-1, -2, 0, 0, 0, 14));
        TextView footer = text("◈  Обробка на телефоні · кадри не зберігаються", 12, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        content.addView(footer, margin(-1, -2, 0, 10, 0, 0));
        refreshStatus();
        return scroll;
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView flower = text("✿", 27, GOLD, true);
        flower.setGravity(Gravity.CENTER);
        flower.setBackground(shape(CARD_SOFT, BORDER, 16));
        row.addView(flower, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout labels = column();
        labels.setPadding(dp(12), 0, 0, 0);
        labels.addView(text("Пелюстка", 22, CREAM, true));
        labels.addView(text("БОТИ ДЛЯ BACKPACK BRAWL", 10, MUTED, true), margin(-1, -2, 0, 3, 0, 0));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        TextView badge = text("2 РЕЖИМИ", 10, GOLD, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(11), dp(7), dp(11), dp(7));
        badge.setBackground(shape(CARD_SOFT, BORDER, 14));
        row.addView(badge);
        return row;
    }

    private View buildBotsSection() {
        LinearLayout section = column();
        TextView heading = text("ОБЕРИ БОТА", 11, GOLD, true);
        heading.setLetterSpacing(0.08f);
        section.addView(heading, margin(-1, -2, 0, 0, 0, 4));
        section.addView(text("Для кожної мінігри — своя кнопка запуску.", 13, MUTED, false),
                margin(-1, -2, 0, 0, 0, 10));
        section.addView(buildBotCard(BotMode.BACKPACK_BLITZ), margin(-1, -2, 0, 0, 0, 9));
        section.addView(buildBotCard(BotMode.MERGE_MADNESS), new LinearLayout.LayoutParams(-1, -2));
        return section;
    }

    private View buildBotCard(BotMode mode) {
        boolean merge = mode == BotMode.MERGE_MADNESS;
        int accent = merge ? GOLD : ROSE;
        int border = merge ? 0xFF695842 : 0xFF70505D;
        LinearLayout card = column();
        card.setPadding(dp(15), dp(14), dp(15), dp(13));
        card.setBackground(gradient(merge
                        ? new int[]{0xFF3C3029, 0xFF2B2527}
                        : new int[]{0xFF48323F, 0xFF30252D},
                border, 20));

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = text(mode.icon, merge ? 25 : 27, accent, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(shape(merge ? 0xFF4A3930 : CARD_SOFT, border, 15));
        heading.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout labels = column();
        labels.setPadding(dp(11), 0, 0, 0);
        labels.addView(text(mode.displayName, 17, CREAM, true));
        labels.addView(text(mode.boardLabel.toUpperCase(Locale.ROOT), 10, accent, true),
                margin(-1, -2, 0, 4, 0, 0));
        heading.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(heading);

        String description = merge
                ? "Зливай однакових щурів свайпом і піднімай їхній рівень."
                : "Розпізнає самоцвіти, шукає збіги й активує спецфішки.";
        TextView summary = text(description, 13, MUTED, false);
        summary.setLineSpacing(dp(2), 1f);
        card.addView(summary, margin(-1, -2, 0, 10, 0, 12));

        TextView launch = actionText("Відкрити панель  →", merge ? 0xFF5B4935 : 0xFF493339,
                CREAM, accent, 13);
        launch.setOnClickListener(view -> openFloatingPanel(mode));
        botLaunchButtons.add(launch);
        card.addView(launch, new LinearLayout.LayoutParams(-1, dp(46)));
        return card;
    }

    private View buildTimerCard() {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(shape(CARD, BORDER, 20));
        card.addView(text("Нагадування про дію", 17, CREAM, true));
        TextView details = text("Сад, скрині та безкоштовні нагороди", 13, MUTED, false);
        card.addView(details, margin(-1, -2, 0, 5, 0, 12));
        TextView open = actionText("Налаштувати таймери  →", GOLD, BG, GOLD, 13);
        open.setOnClickListener(v -> startActivity(new Intent(this, TimerActivity.class)));
        card.addView(open, new LinearLayout.LayoutParams(-1, dp(44)));
        return card;
    }

    private View buildPermissionCard() {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(shape(CARD, BORDER, 20));

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView step = text("01", 12, GOLD, true);
        step.setGravity(Gravity.CENTER);
        step.setBackground(shape(CARD_SOFT, BORDER, 12));
        heading.addView(step, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout titles = column();
        titles.setPadding(dp(11), 0, 0, 0);
        titles.addView(text("Дозвіл Android", 16, CREAM, true));
        serviceState = text("Перевіряємо…", 12, GOLD, false);
        titles.addView(serviceState, margin(-1, -2, 0, 3, 0, 0));
        heading.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(heading);

        serviceDetail = text("Потрібен для читання поля й виконання свайпів.", 13, MUTED, false);
        card.addView(serviceDetail, margin(-1, -2, 0, 12, 0, 0));

        permissionButton = actionText("Увімкнути доступ", CARD_SOFT, CREAM, BORDER, 12);
        permissionButton.setOnClickListener(v -> openAccessibilitySettings());
        card.addView(permissionButton, new LinearLayout.LayoutParams(-1, dp(46)));
        if (Build.VERSION.SDK_INT >= 33) {
            appSettingsButton = actionText("Налаштування застосунку", CARD_SOFT, MUTED, 0, 12);
            appSettingsButton.setOnClickListener(v -> openAppSettings());
            card.addView(appSettingsButton, margin(-1, 42, 0, 7, 0, 0));
        }

        helpToggle = text("Не бачиш сервіс у списку?  Показати інструкцію  ⌄", 12, GOLD, true);
        helpToggle.setGravity(Gravity.CENTER_VERTICAL);
        helpToggle.setPadding(dp(2), dp(11), 0, dp(3));
        helpToggle.setOnClickListener(v -> {
            serviceHelpExpanded = !serviceHelpExpanded;
            updateHelpVisibility();
        });
        card.addView(helpToggle, margin(-1, -2, 0, 5, 0, 0));

        serviceHelp = text("На Android 13+ відкрий налаштування застосунку → ⋮ → «Дозволити обмежені налаштування». Потім повернись у Доступність → встановлені програми та ввімкни «Пелюстка — керування грою».", 12, MUTED, false);
        serviceHelp.setLineSpacing(dp(3), 1f);
        serviceHelp.setVisibility(View.GONE);
        card.addView(serviceHelp, margin(-1, -2, 0, 2, 0, 0));
        permissionCard = card;
        return card;
    }

    private View buildIntervalCard() {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(15), dp(16), dp(13));
        card.setBackground(shape(CARD, BORDER, 20));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout labels = column();
        labels.addView(text("Пауза між ходами", 16, CREAM, true));
        labels.addView(text("Очікування після свайпу без змін поля", 12, MUTED, false), margin(-1, -2, 0, 3, 0, 0));
        titleRow.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        intervalValue = text("0,9 с", 14, GOLD, true);
        intervalValue.setGravity(Gravity.CENTER);
        intervalValue.setPadding(dp(9), dp(6), dp(9), dp(6));
        intervalValue.setBackground(shape(0x334A3540, 0, 12));
        titleRow.addView(intervalValue);
        card.addView(titleRow);

        SeekBar intervalSlider = new SeekBar(this);
        intervalSlider.setMax(33);
        intervalSlider.setProgress(5);
        if (Build.VERSION.SDK_INT >= 21) {
            intervalSlider.setProgressTintList(ColorStateList.valueOf(ROSE));
            intervalSlider.setThumbTintList(ColorStateList.valueOf(GOLD));
            intervalSlider.setProgressBackgroundTintList(ColorStateList.valueOf(BORDER));
        }
        long initial = TapAccessibilityService.getNoChangeTimeoutMs(this);
        int progress = (int) Math.max(0, Math.min(33, Math.round((initial - 700L) / 100f)));
        intervalSlider.setProgress(progress);
        updateIntervalLabel(progress);
        intervalSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                updateIntervalLabel(value);
                if (fromUser) getSharedPreferences(TapAccessibilityService.PREFS, MODE_PRIVATE)
                        .edit().putLong(TapAccessibilityService.KEY_NO_CHANGE_TIMEOUT, 700L + value * 100L).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        card.addView(intervalSlider, margin(-1, -2, -8, 4, -8, 0));

        LinearLayout range = new LinearLayout(this);
        TextView fast = text("ШВИДШЕ · 0,7 с", 10, MUTED, true);
        TextView slow = text("4 с · НАДІЙНІШЕ", 10, MUTED, true);
        range.addView(fast, new LinearLayout.LayoutParams(0, -2, 1));
        slow.setGravity(Gravity.RIGHT);
        range.addView(slow, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(range);
        card.addView(text("Якщо поле не змінилося, бот зачекає й повторить свайп один раз.", 11, MUTED, false),
                margin(-1, -2, 0, 7, 0, 0));
        return card;
    }

    private View buildStepsCard() {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(shape(CARD, BORDER, 20));
        card.addView(text("Перший запуск", 16, CREAM, true));
        card.addView(stepRow("01", "Увімкни дозвіл", "Дозволь сервіс у налаштуваннях доступності."),
                margin(-1, -2, 0, 12, 0, 0));
        card.addView(stepRow("02", "Обери потрібного бота", "На головному екрані в кожного режиму своя кнопка."),
                margin(-1, -2, 0, 10, 0, 0));
        card.addView(stepRow("03", "Натисни ▶ Старт", "Відкрий відповідну мінігру й дочекайся стабільного поля."),
                margin(-1, -2, 0, 10, 0, 0));
        return card;
    }

    private void refreshStatus() {
        if (serviceState == null) return;
        boolean enabled = isAccessibilityServiceEnabled();
        serviceState.setText(enabled ? "Доступ активний" : "Потрібен дозвіл доступності");
        serviceState.setTextColor(enabled ? GREEN : GOLD);
        serviceDetail.setText(enabled
                ? "Можна відкрити панель і запустити бота поверх гри."
                : "Дозвіл потрібен для читання поля й виконання свайпів.");
        permissionButton.setText(enabled ? "Керувати дозволом" : "Увімкнути доступ");
        if (appSettingsButton != null) appSettingsButton.setVisibility(enabled ? View.GONE : View.VISIBLE);
        helpToggle.setVisibility(enabled ? View.GONE : View.VISIBLE);
        for (TextView button : botLaunchButtons) {
            button.setText(enabled ? "Відкрити панель  →" : "Налаштувати доступ  →");
        }
        permissionCard.setBackground(shape(enabled ? 0xFF252D28 : CARD,
                enabled ? 0xFF526850 : BORDER, 20));
        updateHelpVisibility();
    }

    private void updateHelpVisibility() {
        if (serviceHelp == null) return;
        boolean enabled = isAccessibilityServiceEnabled();
        boolean showHelp = !enabled && serviceHelpExpanded;
        serviceHelp.setVisibility(showHelp ? View.VISIBLE : View.GONE);
        if (helpToggle != null) {
            helpToggle.setText(serviceHelpExpanded
                    ? "Сховати інструкцію  ⌃"
                    : "Не бачиш сервіс у списку?  Показати інструкцію  ⌄");
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        String setting = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(setting)) return false;
        String expected = new ComponentName(this, TapAccessibilityService.class).flattenToString();
        for (String component : setting.split(":")) {
            if (component.equalsIgnoreCase(expected)) return true;
        }
        return false;
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "Відкрий налаштування доступності Android", Toast.LENGTH_LONG).show();
        }
    }

    private void openAppSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Відкрий налаштування Пелюстки зі списку програм", Toast.LENGTH_LONG).show();
        }
    }

    private void openFloatingPanel(BotMode mode) {
        if (!isAccessibilityServiceEnabled()) {
            openAccessibilitySettings();
            return;
        }
        if (!TapAccessibilityService.showControlPanel(mode)) {
            Toast.makeText(this, "Повернись сюди після ввімкнення сервісу й відкрий панель ще раз", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "Панель відкрито — можна переходити до гри", Toast.LENGTH_SHORT).show();
    }

    private void updateIntervalLabel(int progress) {
        long ms = 700L + progress * 100L;
        if (intervalValue != null) intervalValue.setText(String.format(Locale.getDefault(), "%.1f с", ms / 1000f));
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private View stepRow(String number, String title, String detail) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.TOP);
        TextView numberBadge = text(number, 10, ROSE, true);
        numberBadge.setGravity(Gravity.CENTER);
        numberBadge.setBackground(shape(0x334A3540, 0, 11));
        row.addView(numberBadge, new LinearLayout.LayoutParams(dp(32), dp(32)));

        LinearLayout copy = column();
        copy.setPadding(dp(11), dp(1), 0, 0);
        copy.addView(text(title, 13, CREAM, true));
        TextView description = text(detail, 12, MUTED, false);
        description.setLineSpacing(dp(2), 1f);
        copy.addView(description, margin(-1, -2, 0, 3, 0, 0));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setIncludeFontPadding(false);
        return view;
    }

    private TextView actionText(String value, int fill, int foreground, int stroke, int radius) {
        TextView view = text(value, 13, foreground, true);
        view.setGravity(Gravity.CENTER);
        view.setLetterSpacing(0.01f);
        view.setBackground(ripple(fill, stroke, radius));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private Drawable gradient(int[] colors, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        d.setCornerRadius(dp(radiusDp));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private Drawable shape(int fill, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private Drawable ripple(int fill, int stroke, int radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), shape(fill, stroke, radiusDp), null);
    }

    private LinearLayout.LayoutParams margin(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width < 0 ? width : dp(width), height < 0 ? height : dp(height));
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return p;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
