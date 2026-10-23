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
            new ClickerModule(BotMode.BACKPACK_BLITZ, R.string.module_blitz_name, "💎",
                    R.string.module_blitz_description, "8 × 8",
                    new ClickerModule.Theme(0xFF53D2CC, 0xFF286D72,
                            0xFF263D4B, 0xFF242A38, 0xFF203B48, 0xFF294C55)),
            new ClickerModule(BotMode.MERGE_MADNESS, R.string.module_merge_name, "🐀",
                    R.string.module_merge_description, "2048 · 4 × 4",
                    new ClickerModule.Theme(0xFFF6BE5B, 0xFF786034,
                            0xFF3B3228, 0xFF252A38, 0xFF473923, 0xFF51402C))));
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

}
