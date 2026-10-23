package ua.borlock.autotap;

import android.content.Context;
import android.content.SharedPreferences;

/** Floating-panel presentation state, isolated from recognition data and timers. */
final class ClickerPreferences {
    private static final String FILE = "pelyustka_modules";
    static final int COMPACT_PANEL_WIDTH_DP = 224;
    static final int COMPACT_TIMER_PANEL_WIDTH_DP = 256;
    static final String TIMERS_PROFILE = "timers";

    private ClickerPreferences() { }

    private static SharedPreferences store(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean diagnosticsExpanded(Context context, String profile) {
        return store(context).getBoolean("panel." + profile + ".details", false);
    }

    static void setDiagnosticsExpanded(Context context, String profile, boolean expanded) {
        store(context).edit().putBoolean("panel." + profile + ".details", expanded).apply();
    }

    static Position position(Context context, String profile, String orientation) {
        return position(context, profile, orientation, 1f, .04f);
    }

    static Position position(Context context, String profile, String orientation, float defaultX, float defaultY) {
        SharedPreferences preferences = store(context);
        String prefix = "panel." + profile + "." + orientation + ".";
        return new Position(ratio(preferences.getFloat(prefix + "x", defaultX)),
                ratio(preferences.getFloat(prefix + "y", defaultY)));
    }

    static void savePosition(Context context, String profile, String orientation, float x, float y) {
        String prefix = "panel." + profile + "." + orientation + ".";
        store(context).edit().putFloat(prefix + "x", ratio(x)).putFloat(prefix + "y", ratio(y)).apply();
    }

    static int coordinate(float ratio, int start, int available, int size) {
        return start + Math.round(ratio(ratio) * Math.max(0, available - size));
    }

    static float fraction(int coordinate, int start, int available, int size) {
        int travel = available - size;
        return travel <= 0 ? 0f : ratio((coordinate - start) / (float) travel);
    }

    private static float ratio(float value) {
        return Float.isNaN(value) || Float.isInfinite(value) ? 0f : Math.max(0f, Math.min(1f, value));
    }

    static final class Position {
        final float x, y;
        Position(float x, float y) { this.x = x; this.y = y; }
    }
}
