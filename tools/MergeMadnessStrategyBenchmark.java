package ua.borlock.autotap;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Historical early/midgame comparison: 500 moves and a rank-10 milestone.
 * For the actual rank-15 target use tools/check-merge-madness-algorithms.py.
 */
public final class MergeMadnessStrategyBenchmark {
    private static int scenarios = 500;
    private static final int MOVE_LIMIT = 500;
    private static final int LEVEL_GOAL = 10;

    public static void main(String[] args) {
        if (args.length > 0) scenarios = Integer.parseInt(args[0]);
        int requestedWorkers = args.length > 1 ? Integer.parseInt(args[1]) : 0;
        Metrics baseline = new Metrics(scenarios);
        Metrics expectimax = new Metrics(scenarios);
        int workerCount = requestedWorkers > 0 ? requestedWorkers
                : Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
        ExecutorService workers = Executors.newFixedThreadPool(workerCount);
        ExecutorCompletionService<ScenarioResult> completed = new ExecutorCompletionService<>(workers);
        for (int seed = 1; seed <= scenarios; seed++) {
            final int scenarioSeed = seed;
            completed.submit(() -> {
                Scenario scenario = new Scenario(scenarioSeed);
                return new ScenarioResult(play(scenario, false), play(scenario, true));
            });
        }
        try {
            for (int index = 1; index <= scenarios; index++) {
                ScenarioResult result = completed.take().get();
                baseline.add(result.baseline);
                expectimax.add(result.expectimax);
                if (index % 10 == 0 || index == scenarios) {
                    System.out.println("completed " + index + "/" + scenarios
                            + " on " + workerCount + " workers");
                }
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Seeded simulation failed.", failure);
        } finally {
            workers.shutdownNow();
        }
        baseline.finish();
        expectimax.finish();
        print("one-step baseline", baseline);
        print("three-ply expectimax", expectimax);
        double moveChange = percentChange(expectimax.medianMoves, baseline.medianMoves);
        double goalChange = percentChange(expectimax.goalRate, baseline.goalRate);
        System.out.printf(java.util.Locale.US,
                "paired change: median moves %+.2f%%, level-%d rate %+.2f%%; p95 choice %.3f ms, worst %.3f ms%n",
                moveChange, LEVEL_GOAL, goalChange, expectimax.p95ChoiceMs,
                expectimax.maxChoiceMs);
        if (expectimax.medianMoves < baseline.medianMoves
                || expectimax.goalRate < baseline.goalRate) {
            throw new AssertionError("The optimized policy regressed a required metric.");
        }
        if (Math.max(moveChange, goalChange) < 5.0) {
            throw new AssertionError("Neither required benchmark metric improved by 5%.");
        }
        if (expectimax.maxChoiceMs > 200.0) {
            throw new AssertionError("Move selection exceeded the 200 ms budget.");
        }
        System.out.println("PASS: paired " + scenarios + " seeded scenarios.");
    }

    private static GameResult play(Scenario scenario, boolean optimized) {
        MergeMadnessBoard board = scenario.initialBoard();
        MergeMadnessSpawnModel spawnModel = new MergeMadnessSpawnModel();
        MergeMadnessSolver solver = new MergeMadnessSolver();
        int moves = 0;
        int firstGoalMove = 0;
        long[] choiceNanos = new long[MOVE_LIMIT];
        while (moves < MOVE_LIMIT) {
            long started = System.nanoTime();
            DirectionChoice choice = optimized
                    ? DirectionChoice.from(solver.chooseMove(board, spawnModel))
                    : greedyMove(board, spawnModel);
            choiceNanos[moves] = System.nanoTime() - started;
            if (choice.direction == null) break;
            MergeMadnessBoard.SlideResult slide = board.slide(choice.direction);
            if (!slide.changed) throw new AssertionError("A policy selected an unchanged swipe.");
            board = slide.board;
            moves++;
            int rank = scenario.spawnRank(moves + 1);
            int cell = scenario.spawnCell(board, moves + 1);
            if (cell < 0) break;
            board = board.withTile(cell, rank);
            spawnModel.observeConfirmedRank(rank);
            if (firstGoalMove == 0 && maxRank(board) >= LEVEL_GOAL) firstGoalMove = moves;
        }
        return new GameResult(moves, firstGoalMove > 0, firstGoalMove,
                Arrays.copyOf(choiceNanos, moves));
    }

    private static DirectionChoice greedyMove(MergeMadnessBoard board,
                                               MergeMadnessSpawnModel model) {
        double rankOneProbability = model.rankOneProbability();
        MergeMadnessSolver.Move best = null;
        for (MergeMadnessSolver.Direction direction : MergeMadnessSolver.Direction.values()) {
            MergeMadnessBoard.SlideResult slide = board.slide(direction);
            if (!slide.changed) continue;
            double value = slide.mergedCount * 5.0 + Math.log1p(slide.mergePoints) * 1.5
                    + expectedLeafAfterSpawn(slide.board, rankOneProbability);
            if (best == null || value > best.value) {
                best = new MergeMadnessSolver.Move(direction, value, slide.mergedCount,
                        slide.mergePoints, 1, 0L);
            }
        }
        return best == null ? new DirectionChoice(null) : DirectionChoice.from(best);
    }

    private static double expectedLeafAfterSpawn(MergeMadnessBoard board,
                                                  double rankOneProbability) {
        int emptyCount = board.emptyCount();
        if (emptyCount == 0) return evaluate(board);
        double total = 0.0;
        for (int cell = 0; cell < MergeMadnessBoard.CELL_COUNT; cell++) {
            if (board.get(cell) != 0) continue;
            total += evaluate(board.withTile(cell, 1)) * rankOneProbability
                    + evaluate(board.withTile(cell, 2)) * (1.0 - rankOneProbability);
        }
        return total / emptyCount;
    }

    private static double evaluate(MergeMadnessBoard board) {
        int empty = 0, sum = 0, max = 0, mergePairs = 0, smoothness = 0;
        boolean maxInCorner = false;
        for (int row = 0; row < MergeMadnessBoard.SIDE; row++) {
            for (int column = 0; column < MergeMadnessBoard.SIDE; column++) {
                int rank = board.get(row, column);
                if (rank == 0) { empty++; continue; }
                sum += rank;
                if (rank > max) { max = rank; maxInCorner = isCorner(row, column); }
                else if (rank == max && isCorner(row, column)) maxInCorner = true;
                if (column + 1 < MergeMadnessBoard.SIDE) {
                    int neighbor = board.get(row, column + 1);
                    if (neighbor == rank && rank < MergeMadnessBoard.MAX_RANK) mergePairs++;
                    else if (neighbor > 0) smoothness += Math.abs(rank - neighbor);
                }
                if (row + 1 < MergeMadnessBoard.SIDE) {
                    int neighbor = board.get(row + 1, column);
                    if (neighbor == rank && rank < MergeMadnessBoard.MAX_RANK) mergePairs++;
                    else if (neighbor > 0) smoothness += Math.abs(rank - neighbor);
                }
            }
        }
        int mobility = 0;
        for (MergeMadnessSolver.Direction direction : MergeMadnessSolver.Direction.values()) {
            if (board.slide(direction).changed) mobility++;
        }
        int monotonicity = monotonicityPenalty(board);
        double survival = mobility == 0 ? -5_000.0 : mobility * 145.0;
        return empty * 310.0 + survival + mergePairs * 92.0 + max * 105.0 + sum * 4.0
                - smoothness * 18.0 - monotonicity * 42.0
                + (maxInCorner ? max * 185.0 : -max * 35.0);
    }

    private static int monotonicityPenalty(MergeMadnessBoard board) {
        int penalty = 0;
        for (int line = 0; line < MergeMadnessBoard.SIDE; line++) {
            int rowIncreasing = 0, rowDecreasing = 0;
            int columnIncreasing = 0, columnDecreasing = 0;
            for (int position = 1; position < MergeMadnessBoard.SIDE; position++) {
                int beforeRow = board.get(line, position - 1), afterRow = board.get(line, position);
                int beforeColumn = board.get(position - 1, line), afterColumn = board.get(position, line);
                if (afterRow > beforeRow) rowIncreasing += afterRow - beforeRow;
                else rowDecreasing += beforeRow - afterRow;
                if (afterColumn > beforeColumn) columnIncreasing += afterColumn - beforeColumn;
                else columnDecreasing += beforeColumn - afterColumn;
            }
            penalty += Math.min(rowIncreasing, rowDecreasing)
                    + Math.min(columnIncreasing, columnDecreasing);
        }
        return penalty;
    }

    private static boolean isCorner(int row, int column) {
        return (row == 0 || row == MergeMadnessBoard.SIDE - 1)
                && (column == 0 || column == MergeMadnessBoard.SIDE - 1);
    }

    private static int maxRank(MergeMadnessBoard board) {
        int max = 0;
        for (int rank : board.copyRanks()) max = Math.max(max, rank);
        return max;
    }

    private static void print(String label, Metrics metrics) {
        System.out.printf(java.util.Locale.US,
                "%s: medianMoves=%d level-%d=%d/%d (%.2f%%) medianGoalMove=%d medianChoice=%.3f ms%n",
                label, metrics.medianMoves, LEVEL_GOAL, metrics.goalCount, scenarios,
                metrics.goalRate * 100.0, metrics.medianGoalMove, metrics.medianChoiceMs);
    }

    private static double percentChange(double value, double baseline) {
        if (baseline == 0) return value == 0 ? 0 : 100;
        return (value - baseline) * 100.0 / baseline;
    }

    private static final class Scenario {
        private static final int RANDOMS_PER_GAME = MOVE_LIMIT + 2;
        final double[] cellFractions = new double[RANDOMS_PER_GAME];
        final double[] rankFractions = new double[RANDOMS_PER_GAME];

        Scenario(long seed) {
            Random random = new Random(seed * 0x9E3779B97F4A7C15L);
            for (int index = 0; index < RANDOMS_PER_GAME; index++) {
                cellFractions[index] = random.nextDouble();
                rankFractions[index] = random.nextDouble();
            }
        }

        MergeMadnessBoard initialBoard() {
            MergeMadnessBoard board = new MergeMadnessBoard(new int[MergeMadnessBoard.CELL_COUNT]);
            for (int index = 0; index < 2; index++) {
                board = board.withTile(spawnCell(board, index), spawnRank(index));
            }
            return board;
        }

        int spawnRank(int turn) { return rankFractions[turn] < 0.90 ? 1 : 2; }

        int spawnCell(MergeMadnessBoard board, int turn) {
            int emptyCount = board.emptyCount();
            if (emptyCount == 0) return -1;
            int wanted = Math.min(emptyCount - 1,
                    (int) (cellFractions[turn] * emptyCount));
            for (int cell = 0; cell < MergeMadnessBoard.CELL_COUNT; cell++) {
                if (board.get(cell) == 0 && wanted-- == 0) return cell;
            }
            return -1;
        }
    }

    private static final class DirectionChoice {
        final MergeMadnessSolver.Direction direction;
        DirectionChoice(MergeMadnessSolver.Direction direction) { this.direction = direction; }
        static DirectionChoice from(MergeMadnessSolver.Move move) {
            return new DirectionChoice(move == null ? null : move.direction);
        }
    }

    private static final class GameResult {
        final int moves;
        final boolean reachedGoal;
        final int goalMove;
        final long[] choiceNanos;
        GameResult(int moves, boolean reachedGoal, int goalMove, long[] choiceNanos) {
            this.moves = moves;
            this.reachedGoal = reachedGoal;
            this.goalMove = goalMove;
            this.choiceNanos = choiceNanos;
        }
    }

    private static final class ScenarioResult {
        final GameResult baseline;
        final GameResult expectimax;
        ScenarioResult(GameResult baseline, GameResult expectimax) {
            this.baseline = baseline;
            this.expectimax = expectimax;
        }
    }

    private static final class Metrics {
        int goalCount;
        int medianMoves;
        int medianGoalMove;
        double goalRate;
        double medianChoiceMs;
        double p95ChoiceMs;
        double maxChoiceMs;
        private final int[] moveCounts;
        private final int[] goalMoves;
        private final long[] times;
        private int games;
        private int timeCount;

        Metrics(int gameCount) {
            moveCounts = new int[gameCount];
            goalMoves = new int[gameCount];
            times = new long[gameCount * MOVE_LIMIT];
        }

        synchronized void add(GameResult result) {
            moveCounts[games] = result.moves;
            if (result.reachedGoal) { goalCount++; goalMoves[games] = result.goalMove; }
            for (long elapsed : result.choiceNanos) times[timeCount++] = elapsed;
            games++;
        }

        synchronized void finish() {
            Arrays.sort(moveCounts);
            medianMoves = moveCounts[games / 2];
            int[] found = Arrays.stream(goalMoves).filter(move -> move > 0).sorted().toArray();
            medianGoalMove = found.length == 0 ? 0 : found[found.length / 2];
            goalRate = goalCount / (double) games;
            Arrays.sort(times, 0, timeCount);
            medianChoiceMs = times[timeCount / 2] / 1_000_000.0;
            p95ChoiceMs = times[Math.min(timeCount - 1, (int) (timeCount * 0.95))]
                    / 1_000_000.0;
            maxChoiceMs = times[timeCount - 1] / 1_000_000.0;
        }
    }
}
