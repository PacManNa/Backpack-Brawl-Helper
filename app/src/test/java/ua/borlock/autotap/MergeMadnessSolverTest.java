package ua.borlock.autotap;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class MergeMadnessSolverTest {
    @Test public void rankSevenAndRankNineCannotCreateAFakeRightwardMerge() {
        MergeMadnessBoard board = new MergeMadnessBoard(new int[]{
                0, 0, 0, 1,
                0, 1, 2, 3,
                0, 0, 4, 6,
                1, 5, 7, 9
        });
        MergeMadnessSolver.Move move = new MergeMadnessSolver().chooseMove(board);
        assertTrue(move != null);
        assertTrue(move.direction != MergeMadnessSolver.Direction.RIGHT);
        assertTrue(board.slide(move.direction).changed);
        assertTrue(move.mergedCount == 0);
    }

    @Test public void choosesALegalMoveWithinTheThreePlyBudget() {
        int[] ranks = {
                1, 2, 1, 0,
                2, 3, 4, 1,
                0, 1, 2, 3,
                1, 0, 3, 2
        };
        MergeMadnessBoard board = new MergeMadnessBoard(ranks);
        MergeMadnessSolver.Move move = new MergeMadnessSolver().chooseMove(
                board, new MergeMadnessSpawnModel());
        assertTrue(move != null);
        assertTrue(board.slide(move.direction).changed);
        assertTrue(move.searchDepth >= 1 && move.searchDepth <= MergeMadnessSolver.MAX_SEARCH_DEPTH);
        assertTrue("Хід перевищив часовий ліміт: " + move.elapsedMs,
                move.elapsedMs <= MergeMadnessSolver.MAX_SEARCH_TIME_MS + 25L);
    }

    @Test public void solvesAForcedMergeIntoEveryLateGameRankThroughFifteen() {
        for (int target = 11; target <= 15; target++) {
            for (int rotation = 0; rotation < 4; rotation++) {
                int[] ranks = new int[16];
                // The full checkerboard has no other equal neighbors or empty cells.
                // Every legal direction must merge this one high-rank pair.
                for (int index = 0; index < 16; index++) {
                    int row = index / 4, rotatedColumn = index % 4;
                    int rank = row == 0 && rotatedColumn < 2 ? target - 1
                            : (row + rotatedColumn) % 2 + 1;
                    for (int turn = 0; turn < rotation; turn++) {
                        int previousRow = row;
                        row = rotatedColumn; rotatedColumn = 3 - previousRow;
                    }
                    ranks[row * 4 + rotatedColumn] = rank;
                }
                MergeMadnessBoard board = new MergeMadnessBoard(ranks);
                MergeMadnessSolver.Move move = new MergeMadnessSolver().chooseMove(board);
                assertTrue("No move toward rank " + target, move != null);
                MergeMadnessBoard.SlideResult result = board.slide(move.direction);
                assertEquals("Missed an immediate merge into rank " + target, 1, result.mergedCount);
                assertEquals(1L << target, result.mergePoints);
                int highest = 0;
                for (int rank : result.board.copyRanks()) highest = Math.max(highest, rank);
                assertEquals(target, highest);
            }
        }
    }
}
