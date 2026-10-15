package ua.borlock.autotap;

/** Verifies that an observed board is one legal swipe followed by one rank-1/2 spawn. */
final class MergeMadnessTransition {
    final boolean expected;
    final int spawnedRank;
    final int mergedCount;

    private MergeMadnessTransition(boolean expected, int spawnedRank, int mergedCount) {
        this.expected = expected;
        this.spawnedRank = spawnedRank;
        this.mergedCount = mergedCount;
    }

    static MergeMadnessTransition analyze(MergeMadnessBoard before,
                                          MergeMadnessSolver.Direction direction,
                                          MergeMadnessBoard observedAfter) {
        if (before == null || direction == null || observedAfter == null) {
            return new MergeMadnessTransition(false, 0, 0);
        }
        MergeMadnessBoard.SlideResult slide = before.slide(direction);
        if (!slide.changed) return new MergeMadnessTransition(false, 0, slide.mergedCount);

        int spawnedRank = 0;
        for (int index = 0; index < MergeMadnessBoard.CELL_COUNT; index++) {
            int expectedRank = slide.board.get(index);
            int observedRank = observedAfter.get(index);
            if (expectedRank == observedRank) continue;
            if (expectedRank != 0 || (observedRank != 1 && observedRank != 2)
                    || spawnedRank != 0) {
                return new MergeMadnessTransition(false, 0, slide.mergedCount);
            }
            spawnedRank = observedRank;
        }
        return new MergeMadnessTransition(spawnedRank != 0, spawnedRank, slide.mergedCount);
    }

    boolean canTrainSpawnModel() {
        return expected && mergedCount == 1 && (spawnedRank == 1 || spawnedRank == 2);
    }
}
