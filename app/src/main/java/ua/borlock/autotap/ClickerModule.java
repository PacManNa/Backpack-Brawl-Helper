package ua.borlock.autotap;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Immutable catalog entry. Gameplay engines remain separate from presentation metadata. */
final class ClickerModule {
    final BotMode mode;
    final String id;
    final int nameRes;
    final String icon;
    final int descriptionRes;
    final String boardLabel;
    final Theme theme;
    final Set<Setting> settings;

    enum Setting { NO_CHANGE_TIMEOUT, PANEL_WIDTH }

    ClickerModule(BotMode mode, int nameRes, String icon, int descriptionRes,
                  String boardLabel, Theme theme, Setting... settings) {
        this.mode = mode;
        this.id = mode.id;
        this.nameRes = nameRes;
        this.icon = icon;
        this.descriptionRes = descriptionRes;
        this.boardLabel = boardLabel;
        this.theme = theme;
        EnumSet<Setting> available = EnumSet.noneOf(Setting.class);
        Collections.addAll(available, settings);
        this.settings = Collections.unmodifiableSet(available);
    }

    static final class Theme {
        final int accent;
        final int border;
        final int gradientStart;
        final int gradientEnd;
        final int iconFill;
        final int buttonFill;

        Theme(int accent, int border, int gradientStart, int gradientEnd,
              int iconFill, int buttonFill) {
            this.accent = accent;
            this.border = border;
            this.gradientStart = gradientStart;
            this.gradientEnd = gradientEnd;
            this.iconFill = iconFill;
            this.buttonFill = buttonFill;
        }
    }
}
