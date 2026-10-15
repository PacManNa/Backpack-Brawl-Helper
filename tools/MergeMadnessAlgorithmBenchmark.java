package ua.borlock.autotap;

import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Paired seeds, bounded production search, and explicit outcomes through the game's rank 15. */
public final class MergeMadnessAlgorithmBenchmark {
    private static final int FIRST_MILESTONE = 10, FINAL_RANK = 15;
    private static final int[] SNAKE = {0,1,2,3, 7,6,5,4, 8,9,10,11, 15,14,13,12};
    private static final int[] USER_BOARD = {
            0,0,0,1, 0,1,2,3, 0,0,4,6, 1,5,7,9
    };

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        int games = Integer.parseInt(args[0]), limit = Integer.parseInt(args[1]);
        int workers = Integer.parseInt(args[2]), seedStart = Integer.parseInt(args[3]);
        Path output = Path.of(args[4]);
        double probability = Double.parseDouble(args[5]);
        String[] policies = args[6].split(",");
        int targetRank = Integer.parseInt(args[7]);
        String start = args[8];
        if (targetRank < FIRST_MILESTONE || targetRank > FINAL_RANK) {
            throw new IllegalArgumentException("Game target must be between 10 and 15");
        }
        checkLateFixtures();
        for (String policy : policies) {
            Agent warm = new Agent(policy);
            for (int repeat = 0; repeat < 3; repeat++) {
                warm.choose(new MergeMadnessBoard(USER_BOARD), new MergeMadnessSpawnModel());
            }
        }
        System.out.printf("games=%d moves=%d workers=%d seedStart=%d spawnP=%.3f targetRank=%d start=%s policies=%s%n",
                games, limit, workers, seedStart, probability, targetRank, start, Arrays.toString(policies));
        System.out.println("mixed: even seeds use the supplied board; odd seeds start with two spawned tiles.");
        System.out.println("late: synthetic snake with ranks 1..target-1 plus a second rank 1, in eight orientations.");
        System.out.println("A goal reached from a synthetic late position is not a full-game win.");
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        ExecutorCompletionService<Result> completed = new ExecutorCompletionService<>(executor);
        List<Result> results = new ArrayList<>();
        try {
            for (int index = 0; index < games; index++) {
                int seed = seedStart + index;
                for (int offset = 0; offset < policies.length; offset++) {
                    String policy = policies[(index + offset) % policies.length];
                    completed.submit(() -> play(policy, seed, limit, probability, targetRank, start));
                }
            }
            for (int count = 1; count <= games * policies.length; count++) {
                results.add(completed.take().get());
                if (count % 4 == 0 || count == games * policies.length) {
                    Result last = results.get(results.size() - 1);
                    System.out.printf("completed %d/%d last=%s seed=%d rank=%d moves=%d outcome=%s%n",
                            count, games * policies.length, last.policy, last.seed,
                            last.maxRank, last.moves, last.outcome);
                }
            }
        } finally {
            executor.shutdownNow();
        }
        results.sort(Comparator.comparing(Result::policy).thenComparingInt(Result::seed));
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(Path.of(output + ".games.csv")))) {
            writer.println("policy,seed,initial,targetRank,initialMass,finalMass,moves,outcome,alive,maxRank,mergePoints,"
                    + "rank10Move,rank11Move,rank12Move,rank13Move,rank14Move,rank15Move,"
                    + "meanDepth,medianMs,p95Ms,maxMs");
            for (Result result : results) {
                writer.printf("%s,%d,%s,%d,%d,%d,%d,%s,%s,%d,%d",
                        result.policy, result.seed, result.initial, result.targetRank,
                        result.initialMass, result.finalMass, result.moves, result.outcome,
                        result.alive, result.maxRank, result.points);
                for (int milestone : result.milestones) writer.print("," + milestone);
                writer.printf(",%.3f,%.3f,%.3f,%.3f%n", result.meanDepth,
                        percentile(result.nanos, .50), percentile(result.nanos, .95), percentile(result.nanos, 1));
            }
        }
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(Path.of(output + ".summary.csv")))) {
            writer.println("policy,initial,targetRank,games,meanMoves,reached,blocked,moveLimit,alive,meanRank,"
                    + "rank10,rank11,rank12,rank13,rank14,rank15,meanPoints,meanDepth,medianMs,p95Ms,maxMs");
            System.out.println("policy group targetRank games meanMoves reached blocked moveLimit alive meanRank "
                    + "rank10 rank11 rank12 rank13 rank14 rank15 meanPoints meanDepth medianMs p95Ms maxMs");
            for (String policy : policies) {
                for (String group : new String[]{"all", "early", "user", "synthetic-late"}) {
                    List<Result> selected = results.stream().filter(result -> result.policy.equals(policy)
                            && (group.equals("all") || result.initial.equals(group))).toList();
                    if (selected.isEmpty()) continue;
                    String line = summary(policy, group, selected);
                    writer.println(line);
                    System.out.println(line.replace(',', ' '));
                }
            }
        }
        for (String policy : policies) {
            if (policy.equals("current")) continue;
            List<Double> deltas = new ArrayList<>();
            int wins = 0, losses = 0, ties = 0;
            for (Result result : results) {
                if (!result.policy.equals(policy)) continue;
                Result reference = results.stream().filter(other -> other.policy.equals("current")
                        && other.seed == result.seed).findFirst().orElse(null);
                if (reference == null) continue;
                double delta = result.points - reference.points;
                deltas.add(delta);
                if (delta > 0) wins++; else if (delta < 0) losses++; else ties++;
            }
            if (!deltas.isEmpty()) {
                double[] intervals = bootstrap(deltas);
                System.out.printf("paired %s vs current: points wins/ties/losses=%d/%d/%d meanDelta=%.1f bootstrap95=[%.1f,%.1f]%n",
                        policy, wins, ties, losses, deltas.stream().mapToDouble(Double::doubleValue).average().orElse(0),
                        intervals[0], intervals[1]);
            }
        }
        System.out.println("CSV: " + output + ".games.csv and " + output + ".summary.csv");
        System.out.println("Simulation uses uniform empty-cell spawns; host timings exclude capture, gestures and animation.");
    }

    private static Result play(String policy, int seed, int limit, double probability,
                               int targetRank, String start) throws Exception {
        Random random = new Random(seed * 0x9E3779B97F4A7C15L);
        double[] cells = new double[limit + 2], ranks = new double[limit + 2];
        for (int i = 0; i < cells.length; i++) { cells[i] = random.nextDouble(); ranks[i] = random.nextDouble(); }
        String initial = start.equals("mixed") ? ((seed & 1) == 0 ? "user" : "early")
                : start.equals("late") ? "synthetic-late" : start;
        MergeMadnessBoard board = initial.equals("synthetic-late") ? lateBoard(targetRank, seed)
                : new MergeMadnessBoard(initial.equals("user") ? USER_BOARD : new int[16]);
        if (initial.equals("early")) {
            board = spawn(board, cells[0], ranks[0], probability);
            board = spawn(board, cells[1], ranks[1], probability);
        }
        MergeMadnessSpawnModel model = new MergeMadnessSpawnModel();
        Agent agent = new Agent(policy);
        long[] nanos = new long[limit];
        long points = 0;
        int moves = 0, depth = 0;
        long initialMass = mass(board);
        int[] milestones = new int[FINAL_RANK - FIRST_MILESTONE + 1];
        Arrays.fill(milestones, -1);
        markMilestones(milestones, maxRank(board), 0);
        for (; moves < limit && maxRank(board) < targetRank && board.legalMovesMask() != 0;) {
            long started = System.nanoTime();
            Choice move = agent.choose(board, model);
            long elapsed = System.nanoTime() - started;
            if (move == null) throw new AssertionError("Solver missed a legal move: " + policy);
            MergeMadnessBoard.SlideResult slide = board.slide(move.direction);
            if (!slide.changed) throw new AssertionError("Illegal unchanged swipe: " + policy);
            MergeMadnessBoard next = spawn(slide.board, cells[moves + 2], ranks[moves + 2], probability);
            long spawnedMass = ranks[moves + 2] < probability ? 2 : 4;
            if (mass(board) != mass(slide.board) || mass(next) != mass(board) + spawnedMass) {
                throw new AssertionError("Merge or spawn violated mass conservation");
            }
            MergeMadnessTransition transition = MergeMadnessTransition.analyze(board, move.direction, next);
            if (!transition.expected) throw new AssertionError("Invalid simulated transition");
            model.observeIfConfirmed(transition);
            board = next;
            points += slide.mergePoints;
            nanos[moves++] = elapsed;
            depth += move.depth;
            markMilestones(milestones, maxRank(board), moves);
        }
        String outcome = maxRank(board) >= targetRank ? "reached"
                : board.legalMovesMask() == 0 ? "blocked" : "move-limit";
        return new Result(policy, seed, initial, targetRank, initialMass, mass(board), moves,
                outcome, board.legalMovesMask() != 0, maxRank(board), points, milestones,
                moves == 0 ? 0 : depth / (double) moves, Arrays.copyOf(nanos, moves));
    }

    private static void markMilestones(int[] milestones, int maxRank, int moves) {
        for (int rank = FIRST_MILESTONE; rank <= Math.min(FINAL_RANK, maxRank); rank++) {
            if (milestones[rank - FIRST_MILESTONE] < 0) milestones[rank - FIRST_MILESTONE] = moves;
        }
    }

    private static long mass(MergeMadnessBoard board) {
        long total = 0;
        for (int index = 0; index < 16; index++) {
            if (board.get(index) > 0) total += 1L << board.get(index);
        }
        return total;
    }

    /** Controlled endgame fixture, not a checkpoint collected from an actual game. */
    private static MergeMadnessBoard lateBoard(int targetRank, int orientation) {
        int[] ranks = new int[16];
        int transform = Math.floorMod(orientation, 8);
        for (int offset = 0; offset < targetRank; offset++) {
            int index = SNAKE[offset], row = index / 4, column = index % 4;
            if (transform >= 4) column = 3 - column;
            for (int rotation = 0; rotation < transform % 4; rotation++) {
                int previousRow = row;
                row = column; column = 3 - previousRow;
            }
            ranks[row * 4 + column] = Math.max(1, targetRank - 1 - offset);
        }
        return new MergeMadnessBoard(ranks);
    }

    private static void checkLateFixtures() {
        for (int target = 11; target <= FINAL_RANK; target++) {
            for (int orientation = 0; orientation < 8; orientation++) {
                MergeMadnessBoard board = lateBoard(target, orientation);
                if (mass(board) != (1L << target) || maxRank(board) != target - 1
                        || board.emptyCount() != 16 - target || board.legalMovesMask() == 0) {
                    throw new AssertionError("Invalid late fixture");
                }
            }
        }
    }

    private static MergeMadnessBoard spawn(MergeMadnessBoard board, double cell, double rank, double probability) {
        int empty = board.emptyCount();
        if (empty == 0) throw new AssertionError("Changed slide must leave room for a spawn");
        int target = Math.min(empty - 1, (int) (cell * empty));
        for (int index = 0; index < 16; index++) {
            if (board.get(index) == 0 && target-- == 0) return board.withTile(index, rank < probability ? 1 : 2);
        }
        throw new AssertionError("Missing spawn cell");
    }

    private static int maxRank(MergeMadnessBoard board) {
        int max = 0;
        for (int i = 0; i < 16; i++) max = Math.max(max, board.get(i));
        return max;
    }

    private static String summary(String policy, String group, List<Result> results) {
        int count = results.size(), alive = 0, reached = 0, blocked = 0, limited = 0;
        int[] milestones = new int[FINAL_RANK - FIRST_MILESTONE + 1];
        double moves = 0, rank = 0, points = 0, depth = 0;
        List<Long> times = new ArrayList<>();
        for (Result result : results) {
            if (result.alive) alive++;
            if (result.outcome.equals("reached")) reached++;
            else if (result.outcome.equals("blocked")) blocked++;
            else limited++;
            for (int milestone = 0; milestone < milestones.length; milestone++) {
                if (result.milestones[milestone] >= 0) milestones[milestone]++;
            }
            moves += result.moves; rank += result.maxRank; points += result.points;
            depth += result.meanDepth * result.moves;
            for (long time : result.nanos) times.add(time);
        }
        long[] nanos = times.stream().mapToLong(Long::longValue).toArray();
        String prefix = String.format(Locale.ROOT, "%s,%s,%d,%d,%.2f,%d,%d,%d,%d,%.3f",
                policy, group, results.get(0).targetRank, count, moves / count,
                reached, blocked, limited, alive, rank / count);
        for (int milestone : milestones) prefix += "," + milestone;
        return prefix + String.format(Locale.ROOT, ",%.2f,%.3f,%.3f,%.3f,%.3f",
                points / count, moves == 0 ? 0 : depth / moves,
                percentile(nanos, .50), percentile(nanos, .95), percentile(nanos, 1));
    }

    private static double percentile(long[] values, double probability) {
        if (values.length == 0) return 0;
        long[] sorted = values.clone(); Arrays.sort(sorted);
        return sorted[Math.min(sorted.length - 1, (int) (sorted.length * probability))] / 1e6;
    }

    private static double[] bootstrap(List<Double> values) {
        Random random = new Random(0xB00757A4L);
        double[] means = new double[10_000];
        for (int iteration = 0; iteration < means.length; iteration++) {
            double total = 0;
            for (int i = 0; i < values.size(); i++) total += values.get(random.nextInt(values.size()));
            means[iteration] = total / values.size();
        }
        Arrays.sort(means);
        return new double[]{means[250], means[9750]};
    }

    private record Choice(MergeMadnessSolver.Direction direction, int depth) { }
    private record Result(String policy, int seed, String initial, int targetRank,
                          long initialMass, long finalMass, int moves, String outcome,
                          boolean alive, int maxRank, long points, int[] milestones,
                          double meanDepth, long[] nanos) { }

    private static final class Agent {
        final Object solver;
        final Method choose;
        final Field direction, depth;

        Agent(String policy) throws Exception {
            String className = switch (policy) {
                case "current" -> "MergeMadnessSolver";
                case "greedy" -> "BenchmarkGreedy";
                case "expectimax2" -> "BenchmarkDepth2";
                case "adaptive4" -> "BenchmarkAdaptive4";
                case "expectimax4" -> "BenchmarkDepth4";
                case "minimax" -> "BenchmarkMinimax";
                default -> throw new IllegalArgumentException("Unknown policy: " + policy);
            };
            Class<?> type = Class.forName("ua.borlock.autotap." + className);
            Constructor<?> constructor = type.getDeclaredConstructor(); constructor.setAccessible(true);
            solver = constructor.newInstance();
            choose = type.getDeclaredMethod("chooseMove", MergeMadnessBoard.class, MergeMadnessSpawnModel.class);
            choose.setAccessible(true);
            Class<?> move = Class.forName(type.getName() + "$Move");
            direction = move.getDeclaredField("direction"); direction.setAccessible(true);
            depth = move.getDeclaredField("searchDepth"); depth.setAccessible(true);
        }

        Choice choose(MergeMadnessBoard board, MergeMadnessSpawnModel model) throws Exception {
            Object move = choose.invoke(solver, board, model);
            return move == null ? null : new Choice((MergeMadnessSolver.Direction) direction.get(move), depth.getInt(move));
        }
    }
}
