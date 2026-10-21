package ua.borlock.autotap;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

/** Compares solver CPU time and the largest-clear invariant; excludes Android and animations. */
public final class BlitzSolverBenchmark {
    public static void main(String[] args) throws Exception {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        if (count < 1) throw new IllegalArgumentException("Need at least one board");
        boolean exact = args.length > 2 && args[2].equals("exact");
        GameBoard[] boards = new GameBoard[count];
        Random random = new Random(1941L);
        for (int i = 0; i < count; i++) boards[i] = stableBoard(random, i % 4 == 0);
        GameSolver solver = new GameSolver();
        GameSolver1940 baseline = new GameSolver1940();
        for (int i = 0; i < 1000; i++) {
            baseline.chooseMove(boards[i % count]);
            solver.chooseMove(boards[i % count]);
        }
        long[] previous = new long[count];
        long[] current = new long[count];
        int changedTiebreaks = 0;
        PrintWriter csv = null;
        if (args.length > 1 && !args[1].equals("-")) {
            File file = new File(args[1]);
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            csv = new PrintWriter(file, "UTF-8");
            csv.println("case,specials,baseline_ns,current_ns,immediate_clear,candidates,changed_tiebreak");
        }
        try {
            for (int i = 0; i < count; i++) {
                int[] original = boards[i].copyTiles();
                GameSolver1940.Choice old;
                GameSolver.Choice next;
                if (i % 2 == 0) {
                    long start = System.nanoTime();
                    old = baseline.chooseMove(boards[i]);
                    previous[i] = System.nanoTime() - start;
                    start = System.nanoTime();
                    next = solver.chooseMove(boards[i]);
                    current[i] = System.nanoTime() - start;
                } else {
                    long start = System.nanoTime();
                    next = solver.chooseMove(boards[i]);
                    current[i] = System.nanoTime() - start;
                    start = System.nanoTime();
                    old = baseline.chooseMove(boards[i]);
                    previous[i] = System.nanoTime() - start;
                }
                if (next.matchedCount != old.matchedCount || next.candidateCount != old.candidateCount
                        || next.waitingForResolution != old.waitingForResolution
                        || !Arrays.equals(original, boards[i].copyTiles())) {
                    throw new AssertionError("Largest clear, legal move count or input changed at " + i);
                }
                boolean changed = next.firstCell != old.firstCell || next.secondCell != old.secondCell;
                if (changed) {
                    changedTiebreaks++;
                    if (exact) throw new AssertionError("Choice changed during exact comparison at " + i);
                }
                if (csv != null) csv.printf(Locale.ROOT, "%d,%s,%d,%d,%d,%d,%s%n", i,
                        i % 4 == 0, previous[i], current[i], next.matchedCount,
                        next.candidateCount, changed);
            }
        } finally {
            if (csv != null) csv.close();
        }
        for (int i = 0; i < 10000; i++) {
            int[] values = new int[GameBoard.CELL_COUNT];
            for (int j = 0; j < values.length; j++) values[j] = random.nextInt(11);
            GameBoard board = new GameBoard(values);
            if (board.hasAnyMatch() != (GameSolver.findMatches(values).count > 0)) {
                throw new AssertionError("Fast match check differs at " + i);
            }
        }
        Arrays.sort(previous);
        Arrays.sort(current);
        System.out.printf(Locale.ROOT,
                "boards=%d; largest clear + candidate count preserved; changed tiebreaks=%d%n"
                + "baseline median=%.3f ms p95=%.3f ms; current median=%.3f ms p95=%.3f ms%n"
                + "fast match check agrees on 10000 additional boards%n",
                count, changedTiebreaks, previous[count / 2] / 1e6,
                previous[Math.min(count - 1, (int) (count * .95))] / 1e6,
                current[count / 2] / 1e6,
                current[Math.min(count - 1, (int) (count * .95))] / 1e6);
        if (args.length > 3) {
            int samples = args.length > 4 ? Integer.parseInt(args[4]) : 2048;
            compareModeledRefillQuality(Integer.parseInt(args[3]), samples,
                    args.length > 1 && !args[1].equals("-") ? args[1] + ".quality.csv" : null);
        }
    }

    private static void compareModeledRefillQuality(int count, int samples, String output) throws Exception {
        GameSolver solver = new GameSolver();
        GameSolver1940 baseline = new GameSolver1940();
        Method scoreSwap = GameSolver.class.getDeclaredMethod("scoreSwap", GameBoard.class,
                long.class, int.class, int.class);
        scoreSwap.setAccessible(true);
        Class<?> candidate = Class.forName("ua.borlock.autotap.GameSolver$Candidate");
        Method refill = GameSolver.class.getDeclaredMethod("expectedRefillCascadeScore", candidate, int.class);
        refill.setAccessible(true);
        Field choiceField = candidate.getDeclaredField("choice");
        choiceField.setAccessible(true);
        Random boards = new Random(1941041L);
        double[] differences = new double[count];
        int better = 0, worse = 0, same = 0;
        PrintWriter csv = output == null ? null : new PrintWriter(output, "UTF-8");
        if (csv != null) csv.println("case,baseline_model_score,current_model_score,difference");
        try {
            for (int i = 0; i < count; i++) {
                GameBoard board = stableBoard(boards, i % 4 == 0);
                GameSolver1940.Choice old = baseline.chooseMove(board);
                GameSolver.Choice next = solver.chooseMove(board);
                double oldScore = 0, newScore = 0;
                if (old.firstCell >= 0 && (old.firstCell != next.firstCell || old.secondCell != next.secondCell)) {
                    oldScore = modeledScore(solver, scoreSwap, refill, choiceField, board,
                            old.firstCell, old.secondCell, samples);
                    newScore = modeledScore(solver, scoreSwap, refill, choiceField, board,
                            next.firstCell, next.secondCell, samples);
                }
                differences[i] = newScore - oldScore;
                if (differences[i] > 1e-9) better++;
                else if (differences[i] < -1e-9) worse++;
                else same++;
                if (csv != null) {
                    if (old.firstCell == next.firstCell && old.secondCell == next.secondCell) {
                        csv.printf(Locale.ROOT, "%d,,,0.00000000%n", i);
                    } else csv.printf(Locale.ROOT, "%d,%.8f,%.8f,%.8f%n",
                            i, oldScore, newScore, differences[i]);
                }
            }
        } finally {
            if (csv != null) csv.close();
        }
        double mean = Arrays.stream(differences).average().orElse(0);
        Random bootstrap = new Random(1941042L);
        double[] means = new double[10000];
        for (int i = 0; i < means.length; i++) {
            for (int j = 0; j < count; j++) means[i] += differences[bootstrap.nextInt(count)];
            means[i] /= count;
        }
        Arrays.sort(means);
        System.out.printf(Locale.ROOT,
                "held-out model boards=%d; refill paths per differing move=%d; better/same/worse=%d/%d/%d%n"
                + "mean modeled difference=%+.4f; paired bootstrap 95%%=[%+.4f; %+.4f]%n"
                + "This uses the solver's uniform refill model, not measured phone scores.%n",
                count, samples, better, same, worse, mean, means[250], means[9750]);
    }

    private static double modeledScore(GameSolver solver, Method scoreSwap, Method refill,
                                        Field choiceField, GameBoard board,
                                        int first, int second, int samples) throws Exception {
        Object candidate = scoreSwap.invoke(solver, board, (long) board.hashCode(), first, second);
        GameSolver.Choice choice = (GameSolver.Choice) choiceField.get(candidate);
        return choice.score + (Double) refill.invoke(solver, candidate, samples) * .45;
    }

    private static GameBoard stableBoard(Random random, boolean specials) {
        int[] values = new int[GameBoard.CELL_COUNT];
        for (int row = 0; row < GameBoard.SIDE; row++) {
            for (int column = 0; column < GameBoard.SIDE; column++) {
                int cell = row * GameBoard.SIDE + column;
                int color;
                do {
                    color = 1 + random.nextInt(7);
                } while ((column >= 2 && values[cell - 1] == color && values[cell - 2] == color)
                        || (row >= 2 && values[cell - 8] == color && values[cell - 16] == color));
                values[cell] = color;
            }
        }
        if (specials) {
            int total = 1 + random.nextInt(5);
            for (int i = 0; i < total; i++) values[random.nextInt(64)] = 8 + random.nextInt(3);
        }
        return new GameBoard(values);
    }
}
