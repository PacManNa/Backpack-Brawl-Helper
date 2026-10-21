package ua.borlock.autotap;

/** Immutable timer definition and one explicitly started countdown. */
final class TimerItem {
    final String id;
    final String name;
    final String emoji;
    final long durationMs;
    final long deadlineWallMs;
    final long deadlineElapsedMs;
    final String runToken;
    final int bootCount;

    TimerItem(String id, String name, String emoji, long durationMs,
              long deadlineWallMs, long deadlineElapsedMs) {
        this(id, name, emoji, durationMs, deadlineWallMs, deadlineElapsedMs,
                deadlineWallMs > 0 ? "legacy:" + deadlineWallMs : "", -1);
    }

    TimerItem(String id, String name, String emoji, long durationMs,
              long deadlineWallMs, long deadlineElapsedMs, String runToken, int bootCount) {
        this.id = id;
        this.name = name;
        this.emoji = emoji;
        this.durationMs = durationMs;
        this.deadlineWallMs = deadlineWallMs;
        this.deadlineElapsedMs = deadlineElapsedMs;
        this.runToken = runToken;
        this.bootCount = bootCount;
    }

    boolean isActive() { return deadlineWallMs > 0 && !runToken.isEmpty(); }

    boolean isReady(long elapsedNowMs) {
        return isActive() && elapsedNowMs >= deadlineElapsedMs;
    }

    long remainingMs(long elapsedNowMs) {
        return isActive() ? Math.max(0L, deadlineElapsedMs - elapsedNowMs) : durationMs;
    }

    TimerItem withDefinition(String name, String emoji, long durationMs) {
        return new TimerItem(id, name, emoji, durationMs, deadlineWallMs,
                deadlineElapsedMs, runToken, bootCount);
    }

    TimerItem start(long wallNow, long elapsedNow, long delay, String token, int boot) {
        return new TimerItem(id, name, emoji, durationMs, wallNow + delay,
                elapsedNow + delay, token, boot);
    }

    TimerItem stop() {
        return new TimerItem(id, name, emoji, durationMs, 0, 0, "", -1);
    }

    TimerItem rebaseAfterBoot(long wallNow, long elapsedNow, int boot) {
        if (!isActive()) return this;
        return new TimerItem(id, name, emoji, durationMs, deadlineWallMs,
                elapsedNow + Math.max(0, deadlineWallMs - wallNow), runToken, boot);
    }

    TimerItem preserveRemainingAfterClockChange(long wallNow, long elapsedNow) {
        if (!isActive()) return this;
        return new TimerItem(id, name, emoji, durationMs, wallNow + remainingMs(elapsedNow),
                deadlineElapsedMs, runToken, bootCount);
    }
}
