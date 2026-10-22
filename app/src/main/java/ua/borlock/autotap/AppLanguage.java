package ua.borlock.autotap;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** App-only language selection. It never changes the phone locale or the game session. */
final class AppLanguage {
    private static final String PREFERENCES = "pelyustka_locale";
    private static final String KEY_LANGUAGE = "language";
    private static Configuration cachedConfiguration;
    private static Resources cachedResources;
    private static final Pattern GEM_ERROR = Pattern.compile(
            "Не розпізнав самоцвіт (\\d+), (\\d+)\\. Дочекайся завершення анімації\\.");
    private static final Pattern CELL_ERROR = Pattern.compile(
            "Не впевнено визначив клітинку (\\d+), (\\d+)\\. Дочекайся завершення анімації\\.");
    private static final Pattern RANK_ERROR = Pattern.compile(
            "Не розпізнав рівень щура в клітинці (\\d+), (\\d+)\\. Перевір, що відкрита саме «Злиття Божевілля»\\.");

    private AppLanguage() { }

    static String language(Context context) {
        String saved = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, "uk");
        if ("cs".equals(saved) || "en".equals(saved)) return saved;
        return "uk";
    }

    static Locale locale(Context context) { return Locale.forLanguageTag(language(context)); }

    static void toggle(Context context) {
        String current = language(context);
        String next = "uk".equals(current) ? "cs" : "cs".equals(current) ? "en" : "uk";
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString(KEY_LANGUAGE, next).apply();
    }

    static String buttonLabel(Context context) {
        switch (language(context)) {
            case "cs": return "🇨🇿 CZ";
            case "en": return "🇬🇧 EN";
            default: return "🇺🇦 UA";
        }
    }

    private static Configuration configuration(Context context) {
        Configuration desired = new Configuration(context.getResources().getConfiguration());
        Locale locale = locale(context);
        desired.setLocale(locale);
        desired.setLayoutDirection(locale);
        return desired;
    }

    static Context wrap(Context context) {
        return context.createConfigurationContext(configuration(context));
    }

    static Resources resources(Context context) {
        Configuration desired = configuration(context);
        if (desired.equals(context.getResources().getConfiguration())) return context.getResources();
        synchronized (AppLanguage.class) {
            if (!desired.equals(cachedConfiguration)) {
                cachedResources = context.createConfigurationContext(desired).getResources();
                cachedConfiguration = desired;
            }
            return cachedResources;
        }
    }

    static String text(Context context, int id, Object... args) {
        Resources resources = resources(context);
        return args.length == 0 ? resources.getString(id) : resources.getString(id, args);
    }

    static String moduleCount(Context context) {
        int count = ClickerCatalog.all().size();
        return resources(context).getQuantityString(R.plurals.module_count, count, count);
    }

    static String direction(Context context, MergeMadnessSolver.Direction direction) {
        switch (direction) {
            case LEFT: return text(context, R.string.direction_left);
            case RIGHT: return text(context, R.string.direction_right);
            case UP: return text(context, R.string.direction_up);
            case DOWN: return text(context, R.string.direction_down);
            default: throw new IllegalArgumentException("Unknown direction: " + direction);
        }
    }

    static String timerDuration(Context context, long durationMs) {
        long seconds = Math.max(0, durationMs / 1000 + (durationMs % 1000 > 0 ? 1 : 0));
        if (seconds < 86400) return TimerDisplay.duration(durationMs);
        return text(context, R.string.timer_days, seconds / 86400,
                seconds / 3600 % 24, seconds / 60 % 60, seconds % 60);
    }

    static String timerStatus(Context context, TimerItem item, long now) {
        if (!item.isActive()) return text(context, R.string.stopped);
        return item.isReady(now) ? text(context, R.string.timer_ready)
                : timerDuration(context, item.remainingMs(now));
    }

    /** Only untouched built-in names are translated. User labels remain verbatim. */
    static String timerName(Context context, TimerItem item) {
        int id;
        String canonical;
        switch (item.id) {
            case "reward.event_tickets": id = R.string.timer_tickets; canonical = TimerPresets.NAME_TICKETS; break;
            case "reward.shop_gems": id = R.string.timer_gems; canonical = TimerPresets.NAME_GEMS; break;
            case "reward.shop_coins": id = R.string.timer_coins; canonical = TimerPresets.NAME_COINS; break;
            case "reward.event_bones": id = R.string.timer_bones; canonical = TimerPresets.NAME_BONES; break;
            default: return item.name;
        }
        return canonical.equals(item.name) ? text(context, id) : item.name;
    }

    static String savedTimerName(Context context, TimerItem original, String entered) {
        return original != null && entered.equals(timerName(context, original)) ? original.name : entered;
    }

    /** Translate the recognizers' existing diagnostics at the presentation boundary. */
    static String recognitionError(Context context, String message) {
        if (message == null) return "";
        if ("uk".equals(language(context))) return message;
        int id;
        switch (message) {
            case "Поверни телефон вертикально й відкрий поле Рюкзак-Бліц.": id = R.string.vision_blitz_portrait; break;
            case "Поверни телефон вертикально й відкрий поле 4×4.": id = R.string.vision_merge_portrait; break;
            case "Не завантажилися зразки плиток 2048.": id = R.string.vision_templates_missing; break;
            case "Кадр екрана пошкоджений.": id = R.string.vision_corrupt_frame; break;
            case "Не знайшов межі поля 4×4. Відкрий «Злиття Божевілля» й дочекайся завершення анімації.":
                id = R.string.vision_merge_bounds; break;
            case "Не бачу списку злиття мишей унизу екрана.": id = R.string.vision_chain_missing; break;
            case "Не знайшов плиток на полі «Злиття Божевілля».": id = R.string.vision_tiles_missing; break;
            default:
                Pattern[] patterns = {GEM_ERROR, CELL_ERROR, RANK_ERROR};
                int[] ids = {R.string.vision_gem_unknown, R.string.vision_cell_uncertain, R.string.vision_rank_unknown};
                for (int i = 0; i < patterns.length; i++) {
                    Matcher match = patterns[i].matcher(message);
                    if (match.matches()) return text(context, ids[i],
                            Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)));
                }
                return message;
        }
        return text(context, id);
    }
}
