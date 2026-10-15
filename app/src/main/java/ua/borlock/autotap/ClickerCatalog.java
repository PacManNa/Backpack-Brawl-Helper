package ua.borlock.autotap;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Single source for cards, names, icons and themes of installed modules. */
final class ClickerCatalog {
    private static final List<ClickerModule> MODULES = Collections.unmodifiableList(Arrays.asList(
            new ClickerModule(BotMode.BACKPACK_BLITZ, "Рюкзак-Бліц", "✦",
                    "Розпізнає самоцвіти, шукає збіги й активує спецфішки.", "8 × 8",
                    new ClickerModule.Theme(0xFFE48F99, 0xFF70505D,
                            0xFF48323F, 0xFF30252D, 0xFF372D35, 0xFF493339),
                    ClickerModule.Setting.NO_CHANGE_TIMEOUT, ClickerModule.Setting.PANEL_WIDTH),
            new ClickerModule(BotMode.MERGE_MADNESS, "Злиття Божевілля", "🐀",
                    "Зливай однакових щурів свайпом і піднімай їхній рівень.", "2048 · 4 × 4",
                    new ClickerModule.Theme(0xFFF4C27E, 0xFF695842,
                            0xFF3C3029, 0xFF2B2527, 0xFF4A3930, 0xFF5B4935),
                    ClickerModule.Setting.NO_CHANGE_TIMEOUT, ClickerModule.Setting.PANEL_WIDTH)));
    private static final EnumMap<BotMode, ClickerModule> BY_MODE = new EnumMap<>(BotMode.class);

    static {
        Set<String> ids = new HashSet<>();
        for (ClickerModule module : MODULES) {
            if (!ids.add(module.id) || BY_MODE.put(module.mode, module) != null) {
                throw new IllegalStateException("Duplicate clicker module: " + module.id);
            }
        }
        if (BY_MODE.size() != BotMode.values().length) {
            throw new IllegalStateException("Every supported engine needs a catalog entry");
        }
    }

    private ClickerCatalog() { }

    static List<ClickerModule> all() { return MODULES; }

    static ClickerModule get(BotMode mode) {
        ClickerModule module = BY_MODE.get(mode);
        if (module == null) throw new IllegalArgumentException("Unknown clicker mode: " + mode);
        return module;
    }

    static String countLabel() {
        int count = MODULES.size();
        int last = count % 10, lastTwo = count % 100;
        String noun = last == 1 && lastTwo != 11 ? "МОДУЛЬ"
                : last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14) ? "МОДУЛІ" : "МОДУЛІВ";
        return count + " " + noun;
    }
}
