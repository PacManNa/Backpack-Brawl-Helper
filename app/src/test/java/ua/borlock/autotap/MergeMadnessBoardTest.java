package ua.borlock.autotap;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class MergeMadnessBoardTest {
    @Test public void mergesPairsOnceInEachDirection() {
        assertSlide(MergeMadnessSolver.Direction.LEFT,
                new int[]{1, 1, 1, 1}, new int[]{2, 2, 0, 0}, 2);
        assertSlide(MergeMadnessSolver.Direction.RIGHT,
                new int[]{1, 1, 1, 1}, new int[]{0, 0, 2, 2}, 2);
        assertSlide(MergeMadnessSolver.Direction.UP,
                new int[]{1, 1, 1, 1}, new int[]{2, 2, 0, 0}, 2);
        assertSlide(MergeMadnessSolver.Direction.DOWN,
                new int[]{1, 1, 1, 1}, new int[]{0, 0, 2, 2}, 2);
    }

    @Test public void doesNotMergePastMaximumRank() {
        int[] values = {
                17, 17, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0
        };
        MergeMadnessBoard.SlideResult result = new MergeMadnessBoard(values)
                .slide(MergeMadnessSolver.Direction.LEFT);
        assertFalse(result.changed);
        assertEquals(0, result.mergedCount);
        assertArrayEquals(values, result.board.copyRanks());
    }

    private void assertSlide(MergeMadnessSolver.Direction direction, int[] line, int[] expected,
                             int expectedMergeCount) {
        int[] values = new int[MergeMadnessBoard.CELL_COUNT];
        for (int position = 0; position < line.length; position++) {
            int index;
            if (direction == MergeMadnessSolver.Direction.LEFT
                    || direction == MergeMadnessSolver.Direction.RIGHT) {
                index = position;
            } else {
                index = position * MergeMadnessBoard.SIDE;
            }
            values[index] = line[position];
        }
        MergeMadnessBoard.SlideResult result = new MergeMadnessBoard(values).slide(direction);
        int[] actual = new int[MergeMadnessBoard.SIDE];
        for (int position = 0; position < MergeMadnessBoard.SIDE; position++) {
            int index = direction == MergeMadnessSolver.Direction.LEFT
                    || direction == MergeMadnessSolver.Direction.RIGHT
                    ? position : position * MergeMadnessBoard.SIDE;
            actual[position] = result.board.get(index);
        }
        assertArrayEquals(expected, actual);
        assertEquals(expectedMergeCount, result.mergedCount);
    }
}
