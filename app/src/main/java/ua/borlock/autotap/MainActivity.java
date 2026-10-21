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
import java.util.EnumSet;

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
    private final EnumSet<BotMode> expandedBots = EnumSet.noneOf(BotMode.class);
    private View permissionCard;
    private boolean serviceHelpExpanded;
    private boolean permissionExpanded;
    private boolean stepsExpanded;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            for (ClickerModule module : ClickerCatalog.all()) {
                if (savedInstanceState.getBoolean("bot_expanded_" + module.id, false)) {
                    expandedBots.add(module.mode);
                }
            }
            permissionExpanded = savedInstanceState.getBoolean("permission_expanded", false);
            stepsExpanded = savedInstanceState.getBoolean("steps_expanded", false);
        }
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        setContentView(buildScreen());
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        for (ClickerModule module : ClickerCatalog.all()) {
            state.putBoolean("bot_expanded_" + module.id, expandedBots.contains(module.mode));
        }
        state.putBoolean("permission_expanded", permissionExpanded);
        state.putBoolean("steps_expanded", stepsExpanded);
        super.onSaveInstanceState(state);
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
        content.addView(buildStepsCard(), margin(-1, -2, 0, 0, 0, 14));
        TextView footer = text("v" + versionName() + " · Обробка на телефоні", 12, MUTED, false);
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
        labels.addView(text("КЛІКЕРИ ТА НАГАДУВАННЯ", 10, MUTED, true), margin(-1, -2, 0, 3, 0, 0));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        TextView badge = text(ClickerCatalog.countLabel(), 10, GOLD, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(11), dp(7), dp(11), dp(7));
        badge.setBackground(shape(CARD_SOFT, BORDER, 14));
        row.addView(badge);
        return row;
    }

    private View buildBotsSection() {
        LinearLayout section = column();
        TextView heading = text("КЛІКЕРИ", 11, GOLD, true);
        heading.setLetterSpacing(0.08f);
        section.addView(heading, margin(-1, -2, 0, 0, 0, 4));
        section.addView(text("Натисни назву, щоб відкрити керування.", 13, MUTED, false),
                margin(-1, -2, 0, 0, 0, 10));
        for (ClickerModule module : ClickerCatalog.all()) {
            section.addView(buildBotCard(module), margin(-1, -2, 0, 0, 0, 9));
        }
        return section;
    }

    private View buildBotCard(ClickerModule module) {
        BotMode mode = module.mode;
        ClickerModule.Theme theme = module.theme;
        int accent = theme.accent;
        int border = theme.border;
        LinearLayout card = column();
        card.setPadding(dp(15), dp(14), dp(15), dp(13));
        card.setBackground(gradient(new int[]{theme.gradientStart, theme.gradientEnd},
                border, 20));

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = text(module.icon, 26, accent, true);
        icon.setGravity(Gravity.CENTER);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        icon.setBackground(shape(theme.iconFill, border, 15));
        heading.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView name = text(module.name, 17, CREAM, true);
        name.setPadding(dp(11), 0, 0, 0);
        name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        heading.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        heading.setClickable(true);
        heading.setFocusable(true);
        heading.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        heading.setForeground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),
                null, shape(Color.WHITE, 0, 12)));
        card.addView(heading, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout details = column();
        details.setVisibility(View.GONE);
        details.setPadding(0, dp(12), 0, 0);
        View divider = new View(this);
        divider.setBackgroundColor(border);
        details.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        details.addView(text(module.boardLabel.toUpperCase(Locale.ROOT), 10, accent, true),
                margin(-1, -2, 0, 12, 0, 0));

        TextView summary = text(module.description, 13, MUTED, false);
        summary.setLineSpacing(dp(2), 1f);
        details.addView(summary, margin(-1, -2, 0, 8, 0, 12));

        TextView launch = actionText("Відкрити панель  →", theme.buttonFill,
                CREAM, accent, 13);
        launch.setOnClickListener(view -> openFloatingPanel(mode));
        botLaunchButtons.add(launch);
        details.addView(launch, new LinearLayout.LayoutParams(-1, dp(46)));
        for (ClickerModule.Setting setting : module.settings) {
            View control;
            switch (setting) {
                case NO_CHANGE_TIMEOUT: control = buildIntervalCard(module); break;
                case PANEL_WIDTH: control = buildPanelSettings(module); break;
                default: throw new IllegalStateException("Unsupported setting: " + setting);
            }
            details.addView(control, margin(-1, -2, 0, 12, 0, 0));
        }
        card.addView(details, new LinearLayout.LayoutParams(-1, -2));

        ExpandableSection.bind(heading, details, module.name, expandedBots.contains(mode), expanded -> {
            if (expanded) expandedBots.add(mode);
            else expandedBots.remove(mode);
        });
        return card;
    }

    private View buildTimerCard() {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(shape(CARD, BORDER, 20));
        card.addView(text("Нагадування про дію", 17, CREAM, true));
        TextView details = text("Квитки, самоцвіти, монети та інші нагороди", 13, MUTED, false);
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
        heading.setMinimumHeight(dp(48));
        card.addView(heading);
        LinearLayout body = column();
        body.setPadding(0, dp(6), 0, 0);

        serviceDetail = text("Потрібен для читання поля й виконання свайпів.", 13, MUTED, false);
        body.addView(serviceDetail, margin(-1, -2, 0, 12, 0, 0));

        permissionButton = actionText("Увімкнути доступ", CARD_SOFT, CREAM, BORDER, 12);
        permissionButton.setOnClickListener(v -> openAccessibilitySettings());
        body.addView(permissionButton, new LinearLayout.LayoutParams(-1, dp(46)));
        if (Build.VERSION.SDK_INT >= 33) {
            appSettingsButton = actionText("Налаштування застосунку", CARD_SOFT, MUTED, 0, 12);
            appSettingsButton.setOnClickListener(v -> openAppSettings());
            body.addView(appSettingsButton, margin(-1, 48, 0, 7, 0, 0));
        }

        helpToggle = text("Не бачиш сервіс у списку?  Показати інструкцію  ⌄", 12, GOLD, true);
        helpToggle.setGravity(Gravity.CENTER_VERTICAL);
        helpToggle.setPadding(dp(2), dp(11), 0, dp(3));
        helpToggle.setOnClickListener(v -> {
            serviceHelpExpanded = !serviceHelpExpanded;
            updateHelpVisibility();
        });
        body.addView(helpToggle, margin(-1, -2, 0, 5, 0, 0));

        serviceHelp = text("На Android 13+ відкрий налаштування застосунку → ⋮ → «Дозволити обмежені налаштування». Потім повернись у Доступність → встановлені програми та ввімкни «Пелюстка — керування грою».", 12, MUTED, false);
        serviceHelp.setLineSpacing(dp(3), 1f);
        serviceHelp.setVisibility(View.GONE);
        body.addView(serviceHelp, margin(-1, -2, 0, 2, 0, 0));
        card.addView(body, new LinearLayout.LayoutParams(-1, -2));
        ExpandableSection.bind(heading, body, "Дозвіл Android", permissionExpanded,
                expanded -> permissionExpanded = expanded);
        permissionCard = card;
        return card;
    }

    private View buildIntervalCard(ClickerModule module) {
        LinearLayout card = column();
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(shape(CARD_SOFT, 0, 12));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout labels = column();
        labels.addView(text("Очікування зміни поля", 14, CREAM, true));
        labels.addView(text("Час до повтору свайпу без змін поля", 12, MUTED, false), margin(-1, -2, 0, 3, 0, 0));
        titleRow.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        TextView intervalValue = text("0,9 с", 14, GOLD, true);
        intervalValue.setGravity(Gravity.CENTER);
        intervalValue.setPadding(dp(9), dp(6), dp(9), dp(6));
        intervalValue.setBackground(shape(0x334A3540, 0, 12));
        titleRow.addView(intervalValue);
        card.addView(titleRow);

        SeekBar intervalSlider = new SeekBar(this);
        int timeoutSteps = (int) ((TapAccessibilityService.MAX_NO_CHANGE_TIMEOUT_MS
                - TapAccessibilityService.MIN_NO_CHANGE_TIMEOUT_MS)
                / TapAccessibilityService.NO_CHANGE_TIMEOUT_STEP_MS);
        intervalSlider.setMax(timeoutSteps);
        if (Build.VERSION.SDK_INT >= 21) {
            intervalSlider.setProgressTintList(ColorStateList.valueOf(module.theme.accent));
            intervalSlider.setThumbTintList(ColorStateList.valueOf(module.theme.accent));
            intervalSlider.setProgressBackgroundTintList(ColorStateList.valueOf(BORDER));
        }
        long initial = ClickerPreferences.timeoutMs(this, module.mode);
        int progress = Math.max(0, Math.min(timeoutSteps, Math.round(
                (initial - TapAccessibilityService.MIN_NO_CHANGE_TIMEOUT_MS)
                / (float) TapAccessibilityService.NO_CHANGE_TIMEOUT_STEP_MS)));
        intervalSlider.setProgress(progress);
        intervalSlider.setContentDescription("Очікування зміни поля: " + module.name);
        updateIntervalLabel(intervalValue, progress);
        intervalSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                if (!fromUser) return;
                updateIntervalLabel(intervalValue, value);
                ClickerPreferences.setTimeoutMs(MainActivity.this, module.mode,
                        TapAccessibilityService.MIN_NO_CHANGE_TIMEOUT_MS
                                + value * TapAccessibilityService.NO_CHANGE_TIMEOUT_STEP_MS);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        card.addView(intervalSlider, margin(-1, -2, -8, 4, -8, 0));

        LinearLayout range = new LinearLayout(this);
        TextView fast = text("0,4 с", 10, MUTED, true);
        TextView slow = text("4,0 с", 10, MUTED, true);
        range.addView(fast, new LinearLayout.LayoutParams(0, -2, 1));
        slow.setGravity(Gravity.RIGHT);
        range.addView(slow, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(range);
        card.addView(text("Налаштування цього модуля. Якщо поле не змінилося, бот зачекає й повторить свайп один раз. Після успішного ходу це очікування не додається.", 11, MUTED, false),
                margin(-1, -2, 0, 7, 0, 0));
        return card;
    }

    private View buildPanelSettings(ClickerModule module) {
        LinearLayout card = column();
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(shape(CARD_SOFT, 0, 12));
        card.addView(text("Плаваюча панель", 14, CREAM, true));
        TextView widthLabel = text("", 12, MUTED, false);
        card.addView(widthLabel, margin(-1, -2, 0, 6, 0, 0));
        SeekBar widthSlider = new SeekBar(this);
        widthSlider.setMax((ClickerPreferences.MAX_PANEL_WIDTH_DP
                - ClickerPreferences.MIN_PANEL_WIDTH_DP) / ClickerPreferences.PANEL_WIDTH_STEP_DP);
        int width = ClickerPreferences.panelWidthDp(this, module.id);
        widthSlider.setProgress((width - ClickerPreferences.MIN_PANEL_WIDTH_DP)
                / ClickerPreferences.PANEL_WIDTH_STEP_DP);
        widthLabel.setText("Ширина: " + width);
        widthSlider.setContentDescription("Ширина плаваючої панелі: " + module.name);
        widthSlider.setProgressTintList(ColorStateList.valueOf(module.theme.accent));
        widthSlider.setThumbTintList(ColorStateList.valueOf(module.theme.accent));
        widthSlider.setProgressBackgroundTintList(ColorStateList.valueOf(BORDER));
        widthSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                int widthDp = ClickerPreferences.MIN_PANEL_WIDTH_DP
                        + progress * ClickerPreferences.PANEL_WIDTH_STEP_DP;
                widthLabel.setText("Ширина: " + widthDp);
                ClickerPreferences.setPanelWidthDp(MainActivity.this, module.mode, widthDp);
                TapAccessibilityService.refreshModulePanel(module.mode);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        card.addView(widthSlider, margin(-1, -2, -8, 4, -8, 0));
        card.addView(text("Перетягуй за назву з підписом «Перетягнути». Кнопка «Показати поле» відкриває діагностику, «В іконку» згортає меню.",
                11, MUTED, false), margin(-1, -2, 0, 7, 0, 0));
        return card;
    }

    private View buildStepsCard() {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(shape(CARD, BORDER, 20));
        TextView heading = text("Перший запуск", 16, CREAM, true);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setMinHeight(dp(48));
        card.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout body = column();
        body.addView(stepRow("01", "Увімкни дозвіл", "Натисни «Дозвіл Android» та ввімкни службу доступності."),
                margin(-1, -2, 0, 12, 0, 0));
        body.addView(stepRow("02", "Обери потрібного бота", "Натисни назву, потім «Відкрити панель»."),
                margin(-1, -2, 0, 10, 0, 0));
        body.addView(stepRow("03", "Натисни ▶ Старт", "Відкрий відповідну мінігру й дочекайся стабільного поля."),
                margin(-1, -2, 0, 10, 0, 0));
        card.addView(body, new LinearLayout.LayoutParams(-1, -2));
        ExpandableSection.bind(heading, body, "Перший запуск", stepsExpanded,
                expanded -> stepsExpanded = expanded);
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

    private void updateIntervalLabel(TextView value, int progress) {
        long ms = TapAccessibilityService.MIN_NO_CHANGE_TIMEOUT_MS
                + progress * TapAccessibilityService.NO_CHANGE_TIMEOUT_STEP_MS;
        String label = String.format(Locale.getDefault(), "%.1f с", ms / 1000f);
        value.setText(label);
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            return "?";
        }
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
