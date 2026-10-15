package ua.borlock.autotap;

import java.util.Arrays;

/** Immutable 4×4 rank board for Merge Madness. Rank zero represents an empty cell. */
final class MergeMadnessBoard {
    static final int SIDE = 4;
    static final int CELL_COUNT = SIDE * SIDE;
    static final int MAX_RANK = 17;

    private final byte[] ranks;

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
    }

    private MergeMadnessBoard(byte[] values) {
        ranks = values;
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
        int[] moved = new int[CELL_COUNT];
        int mergedCount = 0;
        long mergePoints = 0L;

        for (int line = 0; line < SIDE; line++) {
            int[] compact = new int[SIDE];
            int compactSize = 0;
            for (int position = 0; position < SIDE; position++) {
                int rank = ranks[cellIndex(direction, line, position)];
                if (rank > 0) compact[compactSize++] = rank;
            }

            int[] merged = new int[SIDE];
            int mergedSize = 0;
            for (int index = 0; index < compactSize; index++) {
                int rank = compact[index];
                if (index + 1 < compactSize && compact[index + 1] == rank
                        && rank < MAX_RANK) {
                    rank++;
                    index++;
                    mergedCount++;
                    mergePoints += 1L << rank;
                }
                merged[mergedSize++] = rank;
            }

            for (int position = 0; position < mergedSize; position++) {
                moved[cellIndex(direction, line, position)] = merged[position];
            }
        }

        boolean changed = false;
        for (int index = 0; index < CELL_COUNT; index++) {
            if (moved[index] != ranks[index]) {
                changed = true;
                break;
            }
        }
        return new SlideResult(new MergeMadnessBoard(toBytes(moved)), changed,
                mergedCount, mergePoints);
    }

    private int cellIndex(MergeMadnessSolver.Direction direction, int line, int position) {
        int orderedPosition = direction.isReverse() ? SIDE - 1 - position : position;
        if (direction.isHorizontal()) {
            return line * SIDE + orderedPosition;
        }
        return orderedPosition * SIDE + line;
    }

    private byte[] toBytes(int[] values) {
        byte[] result = new byte[CELL_COUNT];
        for (int index = 0; index < CELL_COUNT; index++) result[index] = (byte) values[index];
        return result;
    }

    @Override public boolean equals(Object other) {
        return this == other || other instanceof MergeMadnessBoard
                && Arrays.equals(ranks, ((MergeMadnessBoard) other).ranks);
    }

    @Override public int hashCode() {
        return Arrays.hashCode(ranks);
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
