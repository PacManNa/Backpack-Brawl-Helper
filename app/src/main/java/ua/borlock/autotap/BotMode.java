package ua.borlock.autotap;

/** Stable keys for the currently supported engines. Never derive saved keys from ordinal(). */
enum BotMode {
    BACKPACK_BLITZ("backpack_blitz"),
    MERGE_MADNESS("merge_madness");

    final String id;

    BotMode(String id) {
        this.id = id;
    }
}
