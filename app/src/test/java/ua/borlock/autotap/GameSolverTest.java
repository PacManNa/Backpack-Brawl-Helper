package ua.borlock.autotap;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Random;
import org.junit.Test;

import static org.junit.Assert.*;

public final class GameSolverTest {
    @Test public void alwaysChoosesTheLargestImmediateMatchAndKeepsTheInput() {
        Random random = new Random(1941L);
        GameSolver solver = new GameSolver();
        for (int trial = 0; trial < 300; trial++) {
            int[] original = stableBoard(random);
            GameBoard board = new GameBoard(original);
            int maximum = 0;
            int legalMoves = 0;
            for (int cell = 0; cell < 64; cell++) {
                for (int direction = 0; direction < 2; direction++) {
                    if (direction == 0 && cell % 8 == 7) continue;
                    if (direction == 1 && cell / 8 == 7) continue;
                    int second = cell + (direction == 0 ? 1 : 8);
                    int matched = GameSolver.findMatches(board.swapped(cell, second).copyTiles()).count;
                    if (matched > 0) legalMoves++;
                    maximum = Math.max(maximum, matched);
                }
            }
            GameSolver.Choice choice = solver.chooseMove(board);
            assertEquals(maximum, choice.matchedCount);
            assertEquals(legalMoves, choice.candidateCount);
            assertArrayEquals(original, board.copyTiles());
            if (legalMoves > 0) {
                assertEquals(maximum, GameSolver.findMatches(
                        board.swapped(choice.firstCell, choice.secondCell).copyTiles()).count);
            } else assertEquals(-1, choice.firstCell);
        }
    }

    @Test public void touchingDifferentColorRunsProduceSeparateRockets() throws Exception {
        int[] tiles = new int[64];
        Arrays.fill(tiles, 0, 4, GameBoard.RED);
        Arrays.fill(tiles, 4, 8, GameBoard.GREEN);
        resolve(tiles);
        assertEquals(2, count(tiles, GameBoard.ROCKET));
    }

    @Test public void parallelRunsProduceSeparateRocketsEvenForTheSameColor() throws Exception {
        for (int secondColor : new int[]{GameBoard.RED, GameBoard.GREEN}) {
            int[] tiles = new int[64];
            Arrays.fill(tiles, 0, 4, GameBoard.RED);
            Arrays.fill(tiles, 8, 12, secondColor);
            resolve(tiles);
            assertEquals(2, count(tiles, GameBoard.ROCKET));
        }
    }

    @Test public void touchingPerpendicularRunsDoNotBecomeOneBonus() throws Exception {
        int[] tiles = new int[64];
        Arrays.fill(tiles, 8, 12, GameBoard.RED);
        for (int row = 2; row <= 5; row++) tiles[row * 8 + 3] = GameBoard.GREEN;
        resolve(tiles);
        assertEquals(2, count(tiles, GameBoard.ROCKET));
    }

    @Test public void connectedTAndLShapesCreateOneBombAndFiveInLineCreatesPortal() throws Exception {
        for (int column : new int[]{1, 2}) {
            int[] tiles = new int[64];
            for (int c = 1; c <= 3; c++) tiles[8 + c] = GameBoard.RED;
            for (int row = 1; row <= 3; row++) tiles[row * 8 + column] = GameBoard.RED;
            resolve(tiles);
            assertEquals(1, count(tiles, GameBoard.BOMB));
            assertEquals(0, count(tiles, GameBoard.ROCKET));
        }
        int[] line = new int[64];
        Arrays.fill(line, 0, 5, GameBoard.GREEN);
        resolve(line);
        assertEquals(1, count(line, GameBoard.PORTAL));
    }

    @Test public void exactRefillExpectationCountsAnOverlappingCrossOnlyOnce() {
        int[] tiles = patternedBoard();
        int center = 3 * 8 + 3;
        tiles[center] = 0;
        for (int distance : new int[]{-2, -1, 1, 2}) {
            tiles[center + distance] = GameBoard.RED;
            tiles[center + 8 * distance] = GameBoard.RED;
        }
        assertEquals(exhaustiveRefills(tiles, new int[]{center}, 0),
                GameSolver.expectedRefillMatchCount(tiles), 1e-10);
    }

    @Test public void exactRefillExpectationAgreesWithEveryFillOfFourUnknownCells() {
        Random random = new Random(20261021L);
        for (int trial = 0; trial < 6; trial++) {
            int[] tiles = stableBoard(random);
            int[] unknown = {0, 1, 8, 9};
            for (int cell : unknown) tiles[cell] = 0;
            assertEquals(exhaustiveRefills(tiles, unknown, 0),
                    GameSolver.expectedRefillMatchCount(tiles), 1e-10);
        }
    }

    @Test public void threeUnknownCellsMatchWithProbabilityOneInFortyNine() {
        int[] tiles = new int[64];
        Arrays.fill(tiles, GameBoard.BOMB);
        Arrays.fill(tiles, 0, 3, 0);
        assertEquals(3.0 / 49, GameSolver.expectedRefillMatchCount(tiles), 1e-12);
    }

    @Test public void exactRefillExpectationHandlesEdgesAndSpecialItems() {
        Random random = new Random(41L);
        for (int trial = 0; trial < 100; trial++) {
            int[] tiles = new int[64];
            for (int cell = 0; cell < tiles.length; cell++) tiles[cell] = 1 + random.nextInt(10);
            assertEquals(GameSolver.findMatches(tiles).count,
                    GameSolver.expectedRefillMatchCount(tiles), 1e-10);
        }
    }

    @Test public void refiningRefillSamplesReusesThePrefixWithoutChangingTheResult() throws Exception {
        GameSolver solver = new GameSolver();
        GameBoard board = new GameBoard(new int[]{
                5,4,2,6,7,2,2,6, 4,5,6,7,1,5,5,4,
                1,7,6,3,5,7,3,1, 3,2,7,5,3,2,4,6,
                3,2,5,6,2,2,4,4, 4,3,2,4,3,1,1,4,
                5,4,4,3,6,2,6,5, 4,5,3,2,7,5,6,2});
        Method scoreSwap = GameSolver.class.getDeclaredMethod("scoreSwap", GameBoard.class,
                long.class, int.class, int.class);
        scoreSwap.setAccessible(true);
        Object refined = scoreSwap.invoke(solver, board, (long) board.hashCode(), 30, 31);
        Object fresh = scoreSwap.invoke(solver, board, (long) board.hashCode(), 30, 31);
        Method estimate = GameSolver.class.getDeclaredMethod("expectedRefillCascadeScore",
                refined.getClass(), int.class);
        estimate.setAccessible(true);
        estimate.invoke(solver, refined, 4);
        double twice = (Double) estimate.invoke(solver, refined, 8);
        double once = (Double) estimate.invoke(solver, fresh, 8);
        assertEquals(once, twice, 0.0);
    }

    @Test public void activatesAnInteriorBombForItsLargerImmediateClear() {
        int[] tiles = patternedBoard();
        tiles[3 * 8 + 3] = GameBoard.BOMB;
        GameSolver.Choice choice = new GameSolver().chooseMove(new GameBoard(tiles));
        assertEquals(GameBoard.BOMB, choice.specialType);
        assertEquals(8, choice.matchedCount);
    }

    @Test public void waitsForExistingMatchesToFinish() {
        int[] tiles = patternedBoard();
        Arrays.fill(tiles, 0, 3, GameBoard.GREEN);
        GameSolver.Choice choice = new GameSolver().chooseMove(new GameBoard(tiles));
        assertTrue(choice.waitingForResolution);
        assertTrue(choice.unresolvedMatches >= 3);
        assertEquals(-1, choice.firstCell);
    }

    private static void resolve(int[] tiles) throws Exception {
        Method method = GameSolver.class.getDeclaredMethod("resolveMatches", int[].class,
                GameSolver.MatchAnalysis.class, int.class, int.class);
        method.setAccessible(true);
        method.invoke(new GameSolver(), tiles, GameSolver.findMatches(tiles), -1, -1);
    }

    private static int count(int[] tiles, int type) {
        int result = 0;
        for (int tile : tiles) if (tile == type) result++;
        return result;
    }

    private static double exhaustiveRefills(int[] tiles, int[] unknown, int index) {
        if (index == unknown.length) return GameSolver.findMatches(tiles).count;
        double total = 0;
        for (int color = 1; color <= 7; color++) {
            tiles[unknown[index]] = color;
            total += exhaustiveRefills(tiles, unknown, index + 1);
        }
        tiles[unknown[index]] = 0;
        return total / 7;
    }

    private static int[] patternedBoard() {
        int[] tiles = new int[64];
        for (int row = 0; row < 8; row++) {
            for (int column = 0; column < 8; column++) tiles[row * 8 + column] = (row * 2 + column) % 7 + 1;
        }
        return tiles;
    }

    private static int[] stableBoard(Random random) {
        int[] tiles = new int[64];
        do {
            for (int cell = 0; cell < tiles.length; cell++) tiles[cell] = 1 + random.nextInt(7);
        } while (GameSolver.findMatches(tiles).count > 0);
        return tiles;
    }
}
