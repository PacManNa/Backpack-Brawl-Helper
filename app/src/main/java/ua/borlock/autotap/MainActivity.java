package ua.borlock.autotap;

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

public final class MainActivity extends LocalizedActivity {
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
        TextView footer = text("v" + versionName() + t(R.string.footer_local), 12, MUTED, false);
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
        labels.addView(text(t(R.string.app_name), 22, CREAM, true));
        labels.addView(text(t(R.string.header_tagline), 10, MUTED, true), margin(-1, -2, 0, 3, 0, 0));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        TextView language = actionText(AppLanguage.buttonLabel(this), CARD_SOFT, CREAM, BORDER, 11);
        language.setContentDescription(t(R.string.language_switch));
        language.setOnClickListener(view -> {
            AppLanguage.toggle(this);
            TapAccessibilityService.refreshLanguage();
            TimerNotificationReceiver.refreshLanguage(this);
            recreate();
        });
        LinearLayout.LayoutParams languageParams = new LinearLayout.LayoutParams(dp(42), dp(36));
        languageParams.setMargins(dp(6), 0, dp(6), 0);
        row.addView(language, languageParams);

        TextView badge = text(AppLanguage.moduleCount(this), 10, GOLD, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(11), dp(7), dp(11), dp(7));
        badge.setBackground(shape(CARD_SOFT, BORDER, 14));
        row.addView(badge);
        return row;
    }

    private View buildBotsSection() {
        LinearLayout section = column();
        TextView heading = text(t(R.string.section_clickers), 11, GOLD, true);
        heading.setLetterSpacing(0.08f);
        section.addView(heading, margin(-1, -2, 0, 0, 0, 4));
        section.addView(text(t(R.string.clickers_hint), 13, MUTED, false),
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

        TextView name = text(t(module.nameRes), 17, CREAM, true);
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

        TextView summary = text(t(module.descriptionRes), 13, MUTED, false);
        summary.setLineSpacing(dp(2), 1f);
        details.addView(summary, margin(-1, -2, 0, 8, 0, 12));

        TextView launch = actionText(t(R.string.open_panel), theme.buttonFill,
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

        ExpandableSection.bind(heading, details, t(module.nameRes), expandedBots.contains(mode), expanded -> {
            if (expanded) expandedBots.add(mode);
            else expandedBots.remove(mode);
        });
        return card;
    }

    private View buildTimerCard() {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(shape(CARD, BORDER, 20));
        card.addView(text(t(R.string.reminder_title), 17, CREAM, true));
        TextView details = text(t(R.string.reminder_summary), 13, MUTED, false);
        card.addView(details, margin(-1, -2, 0, 5, 0, 12));
        TextView open = actionText(t(R.string.configure_timers), GOLD, BG, GOLD, 13);
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
        titles.addView(text(t(R.string.android_permission), 16, CREAM, true));
        serviceState = text(t(R.string.checking), 12, GOLD, false);
        titles.addView(serviceState, margin(-1, -2, 0, 3, 0, 0));
        heading.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        heading.setMinimumHeight(dp(48));
        card.addView(heading);
        LinearLayout body = column();
        body.setPadding(0, dp(6), 0, 0);

        serviceDetail = text(t(R.string.permission_brief), 13, MUTED, false);
        body.addView(serviceDetail, margin(-1, -2, 0, 12, 0, 0));

        permissionButton = actionText(t(R.string.enable_access), CARD_SOFT, CREAM, BORDER, 12);
        permissionButton.setOnClickListener(v -> openAccessibilitySettings());
        body.addView(permissionButton, new LinearLayout.LayoutParams(-1, dp(46)));
        if (Build.VERSION.SDK_INT >= 33) {
            appSettingsButton = actionText(t(R.string.app_settings), CARD_SOFT, MUTED, 0, 12);
            appSettingsButton.setOnClickListener(v -> openAppSettings());
            body.addView(appSettingsButton, margin(-1, 48, 0, 7, 0, 0));
        }

        helpToggle = text(t(R.string.show_service_help), 12, GOLD, true);
        helpToggle.setGravity(Gravity.CENTER_VERTICAL);
        helpToggle.setPadding(dp(2), dp(11), 0, dp(3));
        helpToggle.setOnClickListener(v -> {
            serviceHelpExpanded = !serviceHelpExpanded;
            updateHelpVisibility();
        });
        body.addView(helpToggle, margin(-1, -2, 0, 5, 0, 0));

        serviceHelp = text(t(R.string.service_help), 12, MUTED, false);
        serviceHelp.setLineSpacing(dp(3), 1f);
        serviceHelp.setVisibility(View.GONE);
        body.addView(serviceHelp, margin(-1, -2, 0, 2, 0, 0));
        card.addView(body, new LinearLayout.LayoutParams(-1, -2));
        ExpandableSection.bind(heading, body, t(R.string.android_permission), permissionExpanded,
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
        labels.addView(text(t(R.string.wait_for_board), 14, CREAM, true));
        labels.addView(text(t(R.string.wait_for_board_hint), 12, MUTED, false), margin(-1, -2, 0, 3, 0, 0));
        titleRow.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        TextView intervalValue = text(t(R.string.interval_default), 14, GOLD, true);
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
        intervalSlider.setContentDescription(t(R.string.wait_for_board_prefix) + t(module.nameRes));
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
        TextView fast = text(t(R.string.interval_fast), 10, MUTED, true);
        TextView slow = text(t(R.string.interval_slow), 10, MUTED, true);
        range.addView(fast, new LinearLayout.LayoutParams(0, -2, 1));
        slow.setGravity(Gravity.RIGHT);
        range.addView(slow, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(range);
        card.addView(text(t(R.string.wait_for_board_explanation), 11, MUTED, false),
                margin(-1, -2, 0, 7, 0, 0));
        return card;
    }

    private View buildPanelSettings(ClickerModule module) {
        LinearLayout card = column();
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(shape(CARD_SOFT, 0, 12));
        card.addView(text(t(R.string.floating_panel), 14, CREAM, true));
        TextView widthLabel = text("", 12, MUTED, false);
        card.addView(widthLabel, margin(-1, -2, 0, 6, 0, 0));
        SeekBar widthSlider = new SeekBar(this);
        widthSlider.setMax((ClickerPreferences.MAX_PANEL_WIDTH_DP
                - ClickerPreferences.MIN_PANEL_WIDTH_DP) / ClickerPreferences.PANEL_WIDTH_STEP_DP);
        int width = ClickerPreferences.panelWidthDp(this, module.id);
        widthSlider.setProgress((width - ClickerPreferences.MIN_PANEL_WIDTH_DP)
                / ClickerPreferences.PANEL_WIDTH_STEP_DP);
        widthLabel.setText(t(R.string.panel_width_prefix) + width);
        widthSlider.setContentDescription(t(R.string.panel_width_accessibility) + t(module.nameRes));
        widthSlider.setProgressTintList(ColorStateList.valueOf(module.theme.accent));
        widthSlider.setThumbTintList(ColorStateList.valueOf(module.theme.accent));
        widthSlider.setProgressBackgroundTintList(ColorStateList.valueOf(BORDER));
        widthSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                int widthDp = ClickerPreferences.MIN_PANEL_WIDTH_DP
                        + progress * ClickerPreferences.PANEL_WIDTH_STEP_DP;
                widthLabel.setText(t(R.string.panel_width_prefix) + widthDp);
                ClickerPreferences.setPanelWidthDp(MainActivity.this, module.mode, widthDp);
                TapAccessibilityService.refreshModulePanel(module.mode);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        card.addView(widthSlider, margin(-1, -2, -8, 4, -8, 0));
        card.addView(text(t(R.string.panel_help),
                11, MUTED, false), margin(-1, -2, 0, 7, 0, 0));
        return card;
    }

    private View buildStepsCard() {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(shape(CARD, BORDER, 20));
        TextView heading = text(t(R.string.first_launch), 16, CREAM, true);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setMinHeight(dp(48));
        card.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout body = column();
        body.addView(stepRow("01", t(R.string.step_permission_title), t(R.string.step_permission_body)),
                margin(-1, -2, 0, 12, 0, 0));
        body.addView(stepRow("02", t(R.string.step_bot_title), t(R.string.step_bot_body)),
                margin(-1, -2, 0, 10, 0, 0));
        body.addView(stepRow("03", t(R.string.step_start_title), t(R.string.step_start_body)),
                margin(-1, -2, 0, 10, 0, 0));
        card.addView(body, new LinearLayout.LayoutParams(-1, -2));
        ExpandableSection.bind(heading, body, t(R.string.first_launch), stepsExpanded,
                expanded -> stepsExpanded = expanded);
        return card;
    }

    private void refreshStatus() {
        if (serviceState == null) return;
        boolean enabled = isAccessibilityServiceEnabled();
        serviceState.setText(enabled ? t(R.string.access_enabled) : t(R.string.access_needed));
        serviceState.setTextColor(enabled ? GREEN : GOLD);
        serviceDetail.setText(enabled
                ? t(R.string.access_ready_help)
                : t(R.string.access_needed_help));
        permissionButton.setText(enabled ? t(R.string.manage_access) : t(R.string.enable_access));
        if (appSettingsButton != null) appSettingsButton.setVisibility(enabled ? View.GONE : View.VISIBLE);
        helpToggle.setVisibility(enabled ? View.GONE : View.VISIBLE);
        for (TextView button : botLaunchButtons) {
            button.setText(enabled ? t(R.string.open_panel) : t(R.string.configure_access));
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
                    ? t(R.string.hide_service_help)
                    : t(R.string.show_service_help));
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
            Toast.makeText(this, t(R.string.open_accessibility_toast), Toast.LENGTH_LONG).show();
        }
    }

    private void openAppSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, t(R.string.open_app_settings_toast), Toast.LENGTH_LONG).show();
        }
    }

    private void openFloatingPanel(BotMode mode) {
        if (!isAccessibilityServiceEnabled()) {
            openAccessibilitySettings();
            return;
        }
        if (!TapAccessibilityService.showControlPanel(mode)) {
            Toast.makeText(this, t(R.string.return_after_access), Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, t(R.string.panel_open_toast), Toast.LENGTH_SHORT).show();
    }

    private void updateIntervalLabel(TextView value, int progress) {
        long ms = TapAccessibilityService.MIN_NO_CHANGE_TIMEOUT_MS
                + progress * TapAccessibilityService.NO_CHANGE_TIMEOUT_STEP_MS;
        String label = String.format(AppLanguage.locale(this), t(R.string.interval_seconds), ms / 1000f);
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
        view.setTextLocale(AppLanguage.locale(this));
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

    private String t(int id, Object... args) {
        return AppLanguage.text(this, id, args);
    }
}
