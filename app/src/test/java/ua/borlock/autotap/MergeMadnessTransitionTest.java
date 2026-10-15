package ua.borlock.autotap;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MergeMadnessTransitionTest {
    @Test public void confirmsExpectedSpawnForAllFourDirections() {
        assertExpectedTransition(MergeMadnessSolver.Direction.LEFT,
                new int[]{1, 1, 2, 0}, 3, 2);
        assertExpectedTransition(MergeMadnessSolver.Direction.RIGHT,
                new int[]{0, 2, 1, 1}, 0, 1);
        assertExpectedTransition(MergeMadnessSolver.Direction.UP,
                new int[]{1, 1, 2, 0}, 12, 1);
        assertExpectedTransition(MergeMadnessSolver.Direction.DOWN,
                new int[]{0, 2, 1, 1}, 0, 2);
    }

    @Test public void rejectsMultipleUnexpectedTilesAndMovesWithoutMergeDoNotTrain() {
        MergeMadnessBoard before = rowBoard(new int[]{1, 1, 2, 0}, true);
        MergeMadnessBoard.SlideResult slide = before.slide(MergeMadnessSolver.Direction.LEFT);
        MergeMadnessBoard invalid = slide.board.withTile(2, 1).withTile(3, 2);
        MergeMadnessTransition transition = MergeMadnessTransition.analyze(
                before, MergeMadnessSolver.Direction.LEFT, invalid);
        assertFalse(transition.expected);

        MergeMadnessBoard noMergeBefore = rowBoard(new int[]{0, 1, 2, 3}, true);
        MergeMadnessBoard noMergeSlide = noMergeBefore.slide(MergeMadnessSolver.Direction.LEFT).board;
        MergeMadnessTransition noMerge = MergeMadnessTransition.analyze(
                noMergeBefore, MergeMadnessSolver.Direction.LEFT, noMergeSlide.withTile(3, 2));
        assertTrue(noMerge.expected);
        assertFalse(noMerge.canTrainSpawnModel());
    }

    private void assertExpectedTransition(MergeMadnessSolver.Direction direction,
                                          int[] line, int spawnIndex, int spawnRank) {
        MergeMadnessBoard before = rowBoard(line,
                direction == MergeMadnessSolver.Direction.LEFT
                        || direction == MergeMadnessSolver.Direction.RIGHT);
        MergeMadnessBoard expected = before.slide(direction).board.withTile(spawnIndex, spawnRank);
        MergeMadnessTransition transition = MergeMadnessTransition.analyze(before, direction, expected);
        assertTrue(direction.name(), transition.expected);
        assertEquals(spawnRank, transition.spawnedRank);
        assertEquals(1, transition.mergedCount);
        assertTrue(transition.canTrainSpawnModel());
    }

    private MergeMadnessBoard rowBoard(int[] line, boolean horizontal) {
        int[] values = new int[MergeMadnessBoard.CELL_COUNT];
        for (int position = 0; position < MergeMadnessBoard.SIDE; position++) {
            values[horizontal ? position : position * MergeMadnessBoard.SIDE] = line[position];
        }
        return new MergeMadnessBoard(values);
    }
}
