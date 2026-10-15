package ua.borlock.autotap;

/** A separately selectable game bot exposed by the floating panel. */
enum BotMode {
    BACKPACK_BLITZ("РЮКЗАК-БЛІЦ", "Рюкзак-Бліц", "8 × 8", "✦"),
    MERGE_MADNESS("ЗЛИТТЯ БОЖЕВІЛЛЯ", "Злиття Божевілля", "2048 · 4 × 4", "🐀");

    final String panelTitle;
    final String displayName;
    final String boardLabel;
    final String icon;

    BotMode(String panelTitle, String displayName, String boardLabel, String icon) {
        this.panelTitle = panelTitle;
        this.displayName = displayName;
        this.boardLabel = boardLabel;
        this.icon = icon;
    }
}
