package ua.borlock.autotap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Reward definitions; adding a definition never starts an unclaimed reward cycle. */
final class TimerPresets {
    private static final long HOUR = 3_600_000L;
    private TimerPresets() { }

    static List<TimerItem> mergeMissing(List<TimerItem> existing) {
        ArrayList<TimerItem> result = new ArrayList<>(existing);
        add(result, "event_tickets", "Квитки події", "🎟️", 4 * HOUR,
                "Квитки на події", "Квитки", "Квиток події");
        add(result, "shop_gems", "Самоцвіти крамниці", "💎", HOUR,
                "Безплатні самоцвіти", "Безкоштовні самоцвіти", "Самоцвіти");
        add(result, "shop_coins", "Монети крамниці", "🪙", HOUR,
                "Безплатні монети", "Безкоштовні монети", "Монети");
        add(result, "event_bones", "Мішок кісток", "🦴", 3 * HOUR,
                "Івентовий мішок кісток");
        return result;
    }

    private static void add(List<TimerItem> result, String key, String name, String emoji,
                            long duration, String... aliases) {
        String id = "reward." + key;
        ArrayList<String> names = new ArrayList<>(Arrays.asList(aliases));
        names.add(name);
        for (TimerItem timer : result) {
            if (timer.id.equals(id)) return;
            for (String alias : names) if (normalize(timer.name).equals(normalize(alias))) return;
        }
        result.add(new TimerItem(id, name, emoji, duration, 0, 0));
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
