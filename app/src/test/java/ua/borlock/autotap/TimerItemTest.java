package ua.borlock.autotap;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class TimerItemTest {
    private TimerItem stopped() { return new TimerItem("x", "Тест", "💎", 3_600_000, 0, 0); }

    @Test public void stoppedTimerNeverBecomesReadyByWaiting() {
        TimerItem item = stopped();
        assertFalse(item.isActive());
        assertFalse(item.isReady(Long.MAX_VALUE));
        assertEquals(3_600_000, item.remainingMs(Long.MAX_VALUE));
    }

    @Test public void customRemainingIsOneCountdownAndKeepsTheFullInterval() {
        TimerItem item = stopped().start(1_000_000, 100, 1_872_000, "one", 1);
        assertEquals(1_872_000, item.remainingMs(100));
        assertEquals(3_600_000, item.durationMs);
        assertTrue(item.isReady(1_872_100));
        TimerItem restarted = item.start(5_000_000, 1000, item.durationMs, "two", 1);
        assertEquals(3_600_000, restarted.remainingMs(1000));
    }

    @Test public void editingDefinitionPreservesTheCurrentDeadline() {
        TimerItem item = stopped().start(5000, 1000, 3000, "same", 1);
        TimerItem edited = item.withDefinition("Нове ім'я", "🦴", 10_000);
        assertEquals(10_000, edited.durationMs);
        assertEquals(item.deadlineElapsedMs, edited.deadlineElapsedMs);
        assertEquals(item.deadlineWallMs, edited.deadlineWallMs);
        assertEquals(item.runToken, edited.runToken);
    }

    @Test public void stoppingDiscardsTheRunButKeepsItsDefinition() {
        TimerItem item = stopped().start(5000, 1000, 3000, "run", 1).stop();
        assertFalse(item.isActive());
        assertFalse(item.isReady(999999));
        assertEquals(0, item.deadlineWallMs);
        assertEquals("", item.runToken);
        assertEquals(3_600_000, item.durationMs);
    }

    @Test public void rebootUsesWallTimeAndAFreshElapsedClock() {
        TimerItem item = stopped().start(1_000_000, 700_000, 50_000, "run", 1);
        TimerItem rebooted = item.rebaseAfterBoot(1_020_000, 500, 2);
        assertEquals(30_000, rebooted.remainingMs(500));
        assertEquals("run", rebooted.runToken);
        assertEquals(2, rebooted.bootCount);
        assertTrue(item.rebaseAfterBoot(1_060_000, 500, 2).isReady(500));
        assertFalse(stopped().rebaseAfterBoot(1_060_000, 500, 2).isActive());
    }

    @Test public void changingClockPreservesMonotonicRemainingTime() {
        TimerItem item = stopped().start(1_000_000, 10_000, 5000, "run", 1);
        TimerItem adjusted = item.preserveRemainingAfterClockChange(6_000_000, 12_000);
        assertEquals(3000, adjusted.remainingMs(12_000));
        assertEquals(6_003_000, adjusted.deadlineWallMs);
        assertEquals(item.deadlineElapsedMs, adjusted.deadlineElapsedMs);
    }

    @Test public void presetsHaveFourIndependentStoppedCountdowns() {
        List<TimerItem> timers = TimerPresets.mergeMissing(new ArrayList<>());
        assertEquals(4, timers.size());
        long[] hours = {4, 1, 1, 3};
        for (int i = 0; i < timers.size(); i++) {
            assertEquals(hours[i] * 3_600_000, timers.get(i).durationMs);
            assertFalse(timers.get(i).isActive());
        }
        assertEquals(4, TimerPresets.mergeMissing(timers).size());
    }

    @Test public void addingPresetsKeepsExistingCustomNamesIntervalsAndRuns() {
        TimerItem existing = new TimerItem("reward.shop_gems", "Мої камінці", "💚", 99_000,
                100_000, 200_000, "keep", 3);
        ArrayList<TimerItem> timers = new ArrayList<>();
        timers.add(existing);
        List<TimerItem> merged = TimerPresets.mergeMissing(timers);
        assertEquals(4, merged.size());
        assertSame(existing, merged.get(0));
    }

    @Test public void knownLegacyNamesAvoidCreatingDuplicateRewards() {
        ArrayList<TimerItem> timers = new ArrayList<>();
        timers.add(new TimerItem("user-gems", " Безкоштовні самоцвіти ", "💎", 3_600_000, 0, 0));
        assertEquals(4, TimerPresets.mergeMissing(timers).size());
    }

    @Test public void countdownRoundsUpAndFormatsHoursAndDays() {
        assertEquals("00:01", TimerDisplay.duration(1));
        assertEquals("01:00", TimerDisplay.duration(59_001));
        assertEquals("04:00:00", TimerDisplay.duration(14_400_000));
        assertEquals("1д 01:00:00", TimerDisplay.duration(90_000_000));
        assertEquals("Зупинено", TimerDisplay.status(stopped(), 1000));
    }
}
