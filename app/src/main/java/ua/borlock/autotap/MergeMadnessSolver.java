package ua.borlock.autotap;

import java.util.HashMap;
import java.util.Map;

/** Deadline-bounded expectimax for 2048-style directional swipes. */
final class MergeMadnessSolver {
    static final int MAX_SEARCH_DEPTH = 3;
    static final long MAX_SEARCH_TIME_MS = 120L;
    private static final long MAX_SEARCH_TIME_NANOS = MAX_SEARCH_TIME_MS * 1_000_000L;
    private static final Direction[] DIRECTIONS = Direction.values();

    Move chooseMove(MergeMadnessBoard board) {
        return chooseMove(board, new MergeMadnessSpawnModel());
    }

    Move chooseMove(MergeMadnessBoard board, MergeMadnessSpawnModel spawnModel) {
        if (board == null) return null;
        long startedAt = System.nanoTime();
        long deadline = startedAt + MAX_SEARCH_TIME_NANOS;
        double rankOneProbability = spawnModel == null
                ? 0.90 : spawnModel.rankOneProbability();
        Move best = fallbackMove(board, rankOneProbability);
        if (best == null) return null;

        SearchContext context = new SearchContext(deadline, rankOneProbability);
        int completedDepth = 0;
        for (int depth = 1; depth <= MAX_SEARCH_DEPTH; depth++) {
            try {
                Move candidate = searchRoot(board, depth, context);
                if (candidate != null) {
                    best = candidate;
                    completedDepth = depth;
                }
            } catch (SearchTimeout ignored) {
                break;
            }
        }
        long elapsedMs = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
        return new Move(best.direction, best.value, best.mergedCount,
                best.mergePoints, completedDepth, elapsedMs);
    }

    private Move fallbackMove(MergeMadnessBoard board, double rankOneProbability) {
        Move best = null;
        for (Direction direction : DIRECTIONS) {
            MergeMadnessBoard.SlideResult slide = board.slide(direction);
            if (!slide.changed) continue;
            double value = moveReward(slide) + expectedLeafAfterSpawn(
                    slide.board, rankOneProbability);
            if (best == null || value > best.value) {
                best = new Move(direction, value, slide.mergedCount, slide.mergePoints, 0, 0L);
            }
        }
        return best;
    }

    private Move searchRoot(MergeMadnessBoard board, int depth, SearchContext context) {
        Move best = null;
        for (Direction direction : DIRECTIONS) {
            context.checkDeadline();
            MergeMadnessBoard.SlideResult slide = context.slide(board, direction);
            if (!slide.changed) continue;
            double value = moveReward(slide)
                    + expectedAfterSpawn(slide.board, depth - 1, context);
            if (best == null || value > best.value) {
                best = new Move(direction, value, slide.mergedCount,
                        slide.mergePoints, depth, 0L);
            }
        }
        return best;
    }

    private double bestFutureMove(MergeMadnessBoard board, int decisionsLeft,
                                  SearchContext context) {
        if (decisionsLeft <= 0) return evaluate(board, context);
        SearchKey key = new SearchKey(board, decisionsLeft);
        Double cached = context.futureValues.get(key);
        if (cached != null) return cached;

        context.checkDeadline();
        double best = Double.NEGATIVE_INFINITY;
        boolean found = false;
        for (Direction direction : DIRECTIONS) {
            context.checkDeadline();
            MergeMadnessBoard.SlideResult slide = context.slide(board, direction);
            if (!slide.changed) continue;
            found = true;
            double value = moveReward(slide)
                    + expectedAfterSpawn(slide.board, decisionsLeft - 1, context);
            best = Math.max(best, value);
        }
        if (!found) best = evaluate(board, context) - 5_000.0;
        context.futureValues.put(key, best);
        return best;
    }

    private double expectedAfterSpawn(MergeMadnessBoard board, int decisionsLeft,
                                      SearchContext context) {
        SearchKey key = new SearchKey(board, decisionsLeft);
        Double cached = context.spawnValues.get(key);
        if (cached != null) return cached;
        context.checkDeadline();

        int[] empty = new int[MergeMadnessBoard.CELL_COUNT];
        int emptyCount = 0;
        for (int index = 0; index < MergeMadnessBoard.CELL_COUNT; index++) {
            if (board.get(index) == 0) empty[emptyCount++] = index;
        }
        if (emptyCount == 0) {
            double value = bestFutureMove(board, decisionsLeft, context);
            context.spawnValues.put(key, value);
            return value;
        }

        double total = 0.0;
        double rankTwoProbability = 1.0 - context.rankOneProbability;
        for (int position = 0; position < emptyCount; position++) {
            context.checkDeadline();
            int cell = empty[position];
            MergeMadnessBoard rankOne = board.withTile(cell, 1);
            MergeMadnessBoard rankTwo = board.withTile(cell, 2);
            double oneValue = decisionsLeft == 0
                    ? evaluate(rankOne, context)
                    : bestFutureMove(rankOne, decisionsLeft, context);
            double twoValue = decisionsLeft == 0
                    ? evaluate(rankTwo, context)
                    : bestFutureMove(rankTwo, decisionsLeft, context);
            total += oneValue * context.rankOneProbability + twoValue * rankTwoProbability;
        }
        double expected = total / emptyCount;
        context.spawnValues.put(key, expected);
        return expected;
    }

    private double expectedLeafAfterSpawn(MergeMadnessBoard board, double rankOneProbability) {
        int emptyCount = board.emptyCount();
        if (emptyCount == 0) return evaluateLeaf(board);
        double total = 0.0;
        for (int index = 0; index < MergeMadnessBoard.CELL_COUNT; index++) {
            if (board.get(index) != 0) continue;
            total += evaluateLeaf(board.withTile(index, 1)) * rankOneProbability
                    + evaluateLeaf(board.withTile(index, 2)) * (1.0 - rankOneProbability);
        }
        return total / emptyCount;
    }

    private double moveReward(MergeMadnessBoard.SlideResult slide) {
        // Scores are a tie-breaker; board survival and future merge options dominate.
        return slide.mergedCount * 5.0 + Math.log1p(slide.mergePoints) * 1.5;
    }

    private double evaluate(MergeMadnessBoard board, SearchContext context) {
        context.checkDeadline();
        return evaluateLeaf(board);
    }

    private double evaluateLeaf(MergeMadnessBoard board) {
        int empty = 0;
        int sum = 0;
        int maxRank = 0;
        int mergePairs = 0;
        int smoothness = 0;
        boolean maxInCorner = false;
        for (int row = 0; row < MergeMadnessBoard.SIDE; row++) {
            for (int column = 0; column < MergeMadnessBoard.SIDE; column++) {
                int rank = board.get(row, column);
                if (rank == 0) {
                    empty++;
                    continue;
                }
                sum += rank;
                if (rank > maxRank) {
                    maxRank = rank;
                    maxInCorner = isCorner(row, column);
                } else if (rank == maxRank && isCorner(row, column)) {
                    maxInCorner = true;
                }
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

        int mobility = countLegalDirections(board);
        int monotonicity = monotonicityPenalty(board);
        double survival = mobility == 0 ? -5_000.0 : mobility * 145.0;
        return empty * 310.0
                + survival
                + mergePairs * 92.0
                + maxRank * 105.0
                + sum * 4.0
                - smoothness * 18.0
                - monotonicity * 42.0
                + (maxInCorner ? maxRank * 185.0 : -maxRank * 35.0);
    }

    private int countLegalDirections(MergeMadnessBoard board) {
        return Integer.bitCount(board.legalMovesMask());
    }

    private boolean isCorner(int row, int column) {
        return (row == 0 || row == MergeMadnessBoard.SIDE - 1)
                && (column == 0 || column == MergeMadnessBoard.SIDE - 1);
    }

    private int monotonicityPenalty(MergeMadnessBoard board) {
        int penalty = 0;
        for (int line = 0; line < MergeMadnessBoard.SIDE; line++) {
            int rowIncreasing = 0;
            int rowDecreasing = 0;
            int columnIncreasing = 0;
            int columnDecreasing = 0;
            for (int position = 1; position < MergeMadnessBoard.SIDE; position++) {
                int previousRowValue = board.get(line, position - 1);
                int nextRowValue = board.get(line, position);
                if (nextRowValue > previousRowValue) {
                    rowIncreasing += nextRowValue - previousRowValue;
                } else {
                    rowDecreasing += previousRowValue - nextRowValue;
                }

                int previousColumnValue = board.get(position - 1, line);
                int nextColumnValue = board.get(position, line);
                if (nextColumnValue > previousColumnValue) {
                    columnIncreasing += nextColumnValue - previousColumnValue;
                } else {
                    columnDecreasing += previousColumnValue - nextColumnValue;
                }
            }
            penalty += Math.min(rowIncreasing, rowDecreasing)
                    + Math.min(columnIncreasing, columnDecreasing);
        }
        return penalty;
    }

    enum Direction {
        LEFT("ліворуч", true, false),
        RIGHT("праворуч", true, true),
        UP("вгору", false, false),
        DOWN("вниз", false, true);

        final String label;
        private final boolean horizontal;
        private final boolean reverse;

        Direction(String label, boolean horizontal, boolean reverse) {
            this.label = label;
            this.horizontal = horizontal;
            this.reverse = reverse;
        }

        boolean isHorizontal() {
            return horizontal;
        }

        boolean isReverse() {
            return reverse;
        }
    }

    static final class Move {
        final Direction direction;
        final double value;
        final int mergedCount;
        final long mergePoints;
        final int searchDepth;
        final long elapsedMs;

        Move(Direction direction, double value, int mergedCount, long mergePoints,
             int searchDepth, long elapsedMs) {
            this.direction = direction;
            this.value = value;
            this.mergedCount = mergedCount;
            this.mergePoints = mergePoints;
            this.searchDepth = searchDepth;
            this.elapsedMs = elapsedMs;
        }
    }

    private static final class SearchContext {
        final long deadline;
        final double rankOneProbability;
        final Map<SearchKey, Double> futureValues = new HashMap<>();
        final Map<SearchKey, Double> spawnValues = new HashMap<>();
        final Map<MergeMadnessBoard, MergeMadnessBoard.SlideResult[]> slideCache = new HashMap<>();

        SearchContext(long deadline, double rankOneProbability) {
            this.deadline = deadline;
            this.rankOneProbability = Math.max(0.0, Math.min(1.0, rankOneProbability));
        }

        void checkDeadline() {
            if (System.nanoTime() >= deadline) throw SearchTimeout.INSTANCE;
        }

        MergeMadnessBoard.SlideResult slide(MergeMadnessBoard board, Direction direction) {
            MergeMadnessBoard.SlideResult[] cached = slideCache.get(board);
            if (cached == null) {
                cached = new MergeMadnessBoard.SlideResult[DIRECTIONS.length];
                slideCache.put(board, cached);
            }
            if (cached[direction.ordinal()] == null) {
                cached[direction.ordinal()] = board.slide(direction);
            }
            return cached[direction.ordinal()];
        }
    }

    private static final class SearchKey {
        final MergeMadnessBoard board;
        final int decisionsLeft;

        SearchKey(MergeMadnessBoard board, int decisionsLeft) {
            this.board = board;
            this.decisionsLeft = decisionsLeft;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof SearchKey)) return false;
            SearchKey that = (SearchKey) other;
            return decisionsLeft == that.decisionsLeft && board.equals(that.board);
        }

        @Override public int hashCode() {
            return 31 * board.hashCode() + decisionsLeft;
        }
    }

    private static final class SearchTimeout extends RuntimeException {
        static final SearchTimeout INSTANCE = new SearchTimeout();

        private SearchTimeout() { super(null, null, false, false); }
    }
}
