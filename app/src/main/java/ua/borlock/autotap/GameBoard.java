package ua.borlock.autotap;

import java.util.Arrays;

/** Immutable 8x8 board of seven jewel colors and three special items in Backpack Blitz. */
final class GameBoard {
    static final int SIDE = 8;
    static final int CELL_COUNT = SIDE * SIDE;
    static final int RED = 1;
    static final int GREEN = 2;
    static final int YELLOW = 3;
    static final int PURPLE = 4;
    static final int BLUE = 5;
    static final int CYAN = 6;
    static final int ORANGE = 7;
    static final int ROCKET = 8;
    static final int BOMB = 9;
    static final int PORTAL = 10;
    static final int MAX_TILE_TYPE = PORTAL;

    private final byte[] tiles;

    GameBoard(int[] values) {
        if (values.length != CELL_COUNT) {
            throw new IllegalArgumentException("Поле має бути 8×8");
        }
        tiles = new byte[CELL_COUNT];
        for (int index = 0; index < CELL_COUNT; index++) {
            int color = values[index];
            if (color < 0 || color > MAX_TILE_TYPE) {
                throw new IllegalArgumentException("Невідомий тип клітинки");
            }
            tiles[index] = (byte) color;
        }
    }

    private GameBoard(byte[] values) {
        tiles = values;
    }

    int get(int row, int column) {
        return tiles[row * SIDE + column];
    }

    int colorAt(int index) {
        return tiles[index];
    }

    int[] copyTiles() {
        int[] result = new int[CELL_COUNT];
        for (int index = 0; index < CELL_COUNT; index++) result[index] = tiles[index];
        return result;
    }

    GameBoard swapped(int firstCell, int secondCell) {
        byte[] result = tiles.clone();
        byte value = result[firstCell];
        result[firstCell] = result[secondCell];
        result[secondCell] = value;
        return new GameBoard(result);
    }

    boolean hasAnyMatch() {
        for (int row = 0; row < SIDE; row++) {
            for (int column = 0; column < SIDE; column++) {
                int index = row * SIDE + column;
                int color = tiles[index];
                if (color < RED || color > ORANGE) continue;
                if (column + 2 < SIDE && tiles[index + 1] == color
                        && tiles[index + 2] == color) return true;
                if (row + 2 < SIDE && tiles[index + SIDE] == color
                        && tiles[index + 2 * SIDE] == color) return true;
            }
        }
        return false;
    }

    @Override public boolean equals(Object other) {
        return this == other || other instanceof GameBoard
                && Arrays.equals(tiles, ((GameBoard) other).tiles);
    }

    @Override public int hashCode() {
        return Arrays.hashCode(tiles);
    }
}
