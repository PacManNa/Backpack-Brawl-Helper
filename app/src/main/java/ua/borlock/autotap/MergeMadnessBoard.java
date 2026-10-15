package ua.borlock.autotap;

import java.util.Arrays;

/** Immutable 4×4 rank board for Merge Madness. Rank zero represents an empty cell. */
final class MergeMadnessBoard {
    static final int SIDE = 4;
    static final int CELL_COUNT = SIDE * SIDE;
    static final int MAX_RANK = 17;

    private final byte[] ranks;
    private final int cachedHash;

    MergeMadnessBoard(int[] values) {
        if (values.length != CELL_COUNT) {
            throw new IllegalArgumentException("Поле має бути 4×4");
        }
        ranks = new byte[CELL_COUNT];
        for (int index = 0; index < CELL_COUNT; index++) {
            if (values[index] < 0 || values[index] > MAX_RANK) {
                throw new IllegalArgumentException("Невідомий рівень плитки");
            }
            ranks[index] = (byte) values[index];
        }
        cachedHash = Arrays.hashCode(ranks);
    }

    private MergeMadnessBoard(byte[] values) {
        ranks = values;
        cachedHash = Arrays.hashCode(ranks);
    }

    int get(int row, int column) {
        return ranks[row * SIDE + column];
    }

    int get(int index) {
        return ranks[index];
    }

    int[] copyRanks() {
        int[] result = new int[CELL_COUNT];
        for (int index = 0; index < CELL_COUNT; index++) result[index] = ranks[index];
        return result;
    }

    int emptyCount() {
        int count = 0;
        for (byte rank : ranks) if (rank == 0) count++;
        return count;
    }

    MergeMadnessBoard withTile(int index, int rank) {
        byte[] result = ranks.clone();
        result[index] = (byte) rank;
        return new MergeMadnessBoard(result);
    }

    SlideResult slide(MergeMadnessSolver.Direction direction) {
        byte[] moved = new byte[CELL_COUNT];
        int mergedCount = 0;
        long mergePoints = 0L;

        for (int line = 0; line < SIDE; line++) {
            int start = direction.isHorizontal()
                    ? line * SIDE + (direction.isReverse() ? SIDE - 1 : 0)
                    : line + (direction.isReverse() ? SIDE * (SIDE - 1) : 0);
            int step = (direction.isHorizontal() ? 1 : SIDE)
                    * (direction.isReverse() ? -1 : 1);
            int destination = start;
            int pending = 0;
            for (int position = 0; position < SIDE; position++) {
                int rank = ranks[start + position * step];
                if (rank == 0) continue;
                if (pending == 0) {
                    pending = rank;
                } else if (pending == rank && rank < MAX_RANK) {
                    moved[destination] = (byte) (rank + 1);
                    destination += step;
                    pending = 0;
                    mergedCount++;
                    mergePoints += 1L << (rank + 1);
                } else {
                    moved[destination] = (byte) pending;
                    destination += step;
                    pending = rank;
                }
            }
            if (pending > 0) moved[destination] = (byte) pending;
        }

        boolean changed = !Arrays.equals(moved, ranks);
        return new SlideResult(changed ? new MergeMadnessBoard(moved) : this, changed,
                mergedCount, mergePoints);
    }

    /** Direction bits follow LEFT, RIGHT, UP, DOWN; no slide objects are needed. */
    int legalMovesMask() {
        int mask = 0;
        for (int row = 0; row < SIDE; row++) {
            for (int column = 0; column < SIDE; column++) {
                int rank = ranks[row * SIDE + column];
                if (column + 1 < SIDE) {
                    int neighbor = ranks[row * SIDE + column + 1];
                    if (rank == 0 && neighbor > 0) mask |= 1;
                    if (rank > 0 && neighbor == 0) mask |= 2;
                    if (rank > 0 && rank == neighbor && rank < MAX_RANK) mask |= 3;
                }
                if (row + 1 < SIDE) {
                    int neighbor = ranks[(row + 1) * SIDE + column];
                    if (rank == 0 && neighbor > 0) mask |= 4;
                    if (rank > 0 && neighbor == 0) mask |= 8;
                    if (rank > 0 && rank == neighbor && rank < MAX_RANK) mask |= 12;
                }
            }
        }
        return mask;
    }

    @Override public boolean equals(Object other) {
        return this == other || other instanceof MergeMadnessBoard
                && Arrays.equals(ranks, ((MergeMadnessBoard) other).ranks);
    }

    @Override public int hashCode() {
        return cachedHash;
    }

    static final class SlideResult {
        final MergeMadnessBoard board;
        final boolean changed;
        final int mergedCount;
        final long mergePoints;

        SlideResult(MergeMadnessBoard board, boolean changed, int mergedCount, long mergePoints) {
            this.board = board;
            this.changed = changed;
            this.mergedCount = mergedCount;
            this.mergePoints = mergePoints;
        }
    }
}
