package ua.borlock.autotap;

import java.util.Locale;

final class TimerDisplay {
    private TimerDisplay() { }
    static String duration(long durationMs) {
        long seconds = Math.max(0, durationMs / 1000 + (durationMs % 1000 > 0 ? 1 : 0));
        long days = seconds / 86400;
        long hours = seconds / 3600 % 24;
        long minutes = seconds / 60 % 60;
        long remainder = seconds % 60;
        if (days > 0) return String.format(Locale.ROOT, "%dд %02d:%02d:%02d", days, hours, minutes, remainder);
        if (hours > 0) return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, remainder);
        return String.format(Locale.ROOT, "%02d:%02d", minutes, remainder);
    }
    static String status(TimerItem item, long now) {
        if (!item.isActive()) return "Зупинено";
        return item.isReady(now) ? "ГОТОВО · отримай нагороду" : duration(item.remainingMs(now));
    }
}
