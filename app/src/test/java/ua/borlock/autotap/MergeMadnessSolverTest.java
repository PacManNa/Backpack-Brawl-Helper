package ua.borlock.autotap;

import org.junit.Test;

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
}
