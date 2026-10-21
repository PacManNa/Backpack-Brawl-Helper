package ua.borlock.autotap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Reward definitions; adding a definition never starts an unclaimed reward cycle. */
final class TimerPresets {
    static final String NAME_TICKETS = "Квитки події";
    static final String NAME_GEMS = "Самоцвіти крамниці";
    static final String NAME_COINS = "Монети крамниці";
    static final String NAME_BONES = "Мішок кісток";
    private static final long HOUR = 3_600_000L;
    private TimerPresets() { }

    static List<TimerItem> mergeMissing(List<TimerItem> existing) {
        ArrayList<TimerItem> result = new ArrayList<>(existing);
        add(result, "event_tickets", NAME_TICKETS, "🎟️", 4 * HOUR,
                "Квитки на події", "Квитки", "Квиток події");
        add(result, "shop_gems", NAME_GEMS, "💎", HOUR,
                "Безплатні самоцвіти", "Безкоштовні самоцвіти", "Самоцвіти");
        add(result, "shop_coins", NAME_COINS, "🪙", HOUR,
                "Безплатні монети", "Безкоштовні монети", "Монети");
        add(result, "event_bones", NAME_BONES, "🦴", 3 * HOUR,
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
