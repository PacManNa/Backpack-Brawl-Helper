package ua.borlock.autotap;

/** Дані одного повторюваного вручну таймера. */
final class TimerItem {
    final String id;
    final String name;
    final String emoji;
    final long durationMs;
    final long deadlineWallMs;
    final long deadlineElapsedMs;

    TimerItem(String id, String name, String emoji, long durationMs,
              long deadlineWallMs, long deadlineElapsedMs) {
        this.id = id;
        this.name = name;
        this.emoji = emoji;
        this.durationMs = durationMs;
        this.deadlineWallMs = deadlineWallMs;
        this.deadlineElapsedMs = deadlineElapsedMs;
    }

    boolean isReady(long elapsedNowMs) {
        return deadlineElapsedMs > 0L && elapsedNowMs >= deadlineElapsedMs;
    }
}
