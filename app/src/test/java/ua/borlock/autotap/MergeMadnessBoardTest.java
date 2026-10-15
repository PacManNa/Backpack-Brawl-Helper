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

    @Test public void aNewlyMergedTileCannotMergeAgainDuringTheSameSwipe() {
        assertSlide(MergeMadnessSolver.Direction.LEFT,
                new int[]{1, 1, 2, 0}, new int[]{2, 2, 0, 0}, 1);
        assertSlide(MergeMadnessSolver.Direction.RIGHT,
                new int[]{0, 2, 1, 1}, new int[]{0, 0, 2, 2}, 1);
        assertSlide(MergeMadnessSolver.Direction.UP,
                new int[]{11, 0, 11, 12}, new int[]{12, 12, 0, 0}, 1);
        assertSlide(MergeMadnessSolver.Direction.DOWN,
                new int[]{12, 11, 0, 11}, new int[]{0, 0, 12, 12}, 1);
    }

    @Test public void mergesEveryGameRankThroughFifteenInEveryDirection() {
        for (int rank = 1; rank < 15; rank++) {
            for (MergeMadnessSolver.Direction direction : MergeMadnessSolver.Direction.values()) {
                boolean reverse = direction == MergeMadnessSolver.Direction.RIGHT
                        || direction == MergeMadnessSolver.Direction.DOWN;
                MergeMadnessBoard.SlideResult result = assertSlide(direction,
                        new int[]{rank, rank, rank, rank},
                        reverse ? new int[]{0, 0, rank + 1, rank + 1}
                                : new int[]{rank + 1, rank + 1, 0, 0}, 2);
                assertEquals(2L * (1L << (rank + 1)), result.mergePoints);
            }
        }
    }

    @Test public void aHighRankMergeCannotChainAgainDuringOneSwipe() {
        for (int rank = 10; rank < 15; rank++) {
            for (MergeMadnessSolver.Direction direction : MergeMadnessSolver.Direction.values()) {
                boolean reverse = direction == MergeMadnessSolver.Direction.RIGHT
                        || direction == MergeMadnessSolver.Direction.DOWN;
                assertSlide(direction,
                        reverse ? new int[]{rank + 1, 0, rank, rank}
                                : new int[]{rank, rank, 0, rank + 1},
                        reverse ? new int[]{0, 0, rank + 1, rank + 1}
                                : new int[]{rank + 1, rank + 1, 0, 0}, 1);
            }
        }
    }

    @Test public void swipesConserveTileValueForEverySmallLineInEveryDirection() {
        for (int encoded = 0; encoded < 6 * 6 * 6 * 6; encoded++) {
            int[] ranks = new int[16];
            int remainder = encoded;
            for (int position = 0; position < 4; position++) {
                ranks[position] = remainder % 6;
                remainder /= 6;
            }
            MergeMadnessBoard board = new MergeMadnessBoard(ranks);
            for (MergeMadnessSolver.Direction direction : MergeMadnessSolver.Direction.values()) {
                assertEquals(tileValue(board), tileValue(board.slide(direction).board));
            }
        }
    }

    private long tileValue(MergeMadnessBoard board) {
        long result = 0;
        for (int rank : board.copyRanks()) if (rank > 0) result += 1L << rank;
        return result;
    }

    private MergeMadnessBoard.SlideResult assertSlide(MergeMadnessSolver.Direction direction,
                                                      int[] line, int[] expected,
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
        assertEquals(tileValue(new MergeMadnessBoard(values)), tileValue(result.board));
        return result;
    }
}
