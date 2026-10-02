package ua.borlock.autotap;

import android.graphics.Bitmap;
import android.graphics.Color;

/** Locally reads seven jewel colors and the three bonus items from the 8x8 board. */
final class BoardVision {
    private static final float FIRST_CENTER_X_RATIO = 0.109f;
    private static final float FIRST_CENTER_Y_RATIO = 0.343f;
    private static final float CELL_STEP_X_RATIO = 0.1115f;
    private static final float CELL_STEP_Y_RATIO = 0.051f;
    private static final float CELL_SAMPLE_RADIUS = 0.24f;
    private static final int SAMPLE_SIDE = 11;
    private static final int MIN_COLOR_SAMPLES = 18;
    private static final double MIN_COLOR_SHARE = 0.40;
    private static final double MIN_COLOR_MARGIN = 0.12;
    private int[] screenshotPixels = new int[0];

    Result recognize(Bitmap screenshot) {
        if (screenshot.getWidth() >= screenshot.getHeight()) {
            return Result.error("Поверни телефон вертикально й відкрий поле Рюкзак-Бліц.");
        }

        int bitmapWidth = screenshot.getWidth();
        int bitmapHeight = screenshot.getHeight();
        int requiredPixels = bitmapWidth * bitmapHeight;
        if (screenshotPixels.length < requiredPixels) {
            screenshotPixels = new int[requiredPixels];
        }
        screenshot.getPixels(screenshotPixels, 0, bitmapWidth, 0, 0,
                bitmapWidth, bitmapHeight);

        Geometry geometry = geometryFor(bitmapWidth, bitmapHeight);
        int[] colors = new int[GameBoard.CELL_COUNT];
        double weakestConfidence = 1.0;
        for (int row = 0; row < GameBoard.SIDE; row++) {
            for (int column = 0; column < GameBoard.SIDE; column++) {
                int cell = row * GameBoard.SIDE + column;
                ColorMatch match = readGem(screenshotPixels, bitmapWidth, bitmapHeight,
                        geometry.centerX(column), geometry.centerY(row), geometry.cellStepPx);
                if (match.color == 0) {
                    return Result.error("Не розпізнав самоцвіт " + (row + 1) + ", "
                            + (column + 1) + ". Дочекайся завершення анімації.");
                }
                colors[cell] = match.color;
                weakestConfidence = Math.min(weakestConfidence, match.confidence);
            }
        }
        return Result.success(new GameBoard(colors), weakestConfidence, geometry);
    }

    private Geometry geometryFor(int width, int height) {
        float step = width * CELL_STEP_X_RATIO;
        return new Geometry(width * FIRST_CENTER_X_RATIO, height * FIRST_CENTER_Y_RATIO,
                step, height * CELL_STEP_Y_RATIO);
    }

    private ColorMatch readGem(int[] pixels, int bitmapWidth, int bitmapHeight,
                               float centerX, float centerY, float cellStep) {
        int[] votes = new int[GameBoard.MAX_TILE_TYPE + 1];
        float[] hsv = new float[3];
        int classified = 0;
        int darkNeutral = 0;
        float radius = cellStep * CELL_SAMPLE_RADIUS;
        int sampleCount = SAMPLE_SIDE * SAMPLE_SIDE;
        for (int y = 0; y < SAMPLE_SIDE; y++) {
            int pixelY = clamp(Math.round(centerY - radius
                    + (y + 0.5f) * radius * 2f / SAMPLE_SIDE), 0, bitmapHeight - 1);
            for (int x = 0; x < SAMPLE_SIDE; x++) {
                int pixelX = clamp(Math.round(centerX - radius
                        + (x + 0.5f) * radius * 2f / SAMPLE_SIDE), 0, bitmapWidth - 1);
                Color.colorToHSV(pixels[pixelY * bitmapWidth + pixelX], hsv);
                if (hsv[1] <= 0.30f && hsv[2] >= 0.08f && hsv[2] <= 0.42f) darkNeutral++;
                if (hsv[1] < 0.34f || hsv[2] < 0.18f) continue;
                int color = classifyHue(hsv[0]);
                if (color == 0) continue;
                votes[color]++;
                classified++;
            }
        }

        int bestColor = 0;
        int bestVotes = 0;
        int secondVotes = 0;
        for (int color = GameBoard.RED; color <= GameBoard.ORANGE; color++) {
            if (votes[color] > bestVotes) {
                secondVotes = bestVotes;
                bestVotes = votes[color];
                bestColor = color;
            } else if (votes[color] > secondVotes) {
                secondVotes = votes[color];
            }
        }
        double confidence = classified == 0 ? 0 : bestVotes / (double) classified;

        double redShare = votes[GameBoard.RED] / (double) Math.max(1, classified);
        double yellowShare = votes[GameBoard.YELLOW] / (double) Math.max(1, classified);
        double redYellowShare = redShare + yellowShare;
        if (Math.min(redShare, yellowShare) >= 0.30 && redYellowShare >= 0.78) {
            return new ColorMatch(GameBoard.ROCKET, redYellowShare);
        }

        if (darkNeutral / (double) sampleCount >= 0.26) {
            return new ColorMatch(GameBoard.BOMB, 0.70);
        }

        int distinctColors = 0;
        for (int color = GameBoard.RED; color <= GameBoard.ORANGE; color++) {
            if (votes[color] >= Math.max(3, classified * 0.10)) distinctColors++;
        }
        if (classified >= MIN_COLOR_SAMPLES && distinctColors >= 3 && confidence < 0.50) {
            return new ColorMatch(GameBoard.PORTAL, 0.70);
        }
        if (classified >= MIN_COLOR_SAMPLES && distinctColors >= 2 && confidence < 0.34) {
            return new ColorMatch(GameBoard.PORTAL, 0.65);
        }

        if (classified < MIN_COLOR_SAMPLES || confidence < MIN_COLOR_SHARE
                || confidence - secondVotes / (double) Math.max(1, classified) < MIN_COLOR_MARGIN) {
            return new ColorMatch(0, confidence);
        }
        return new ColorMatch(bestColor, confidence);
    }

    private int classifyHue(float hue) {
        if (hue <= 18f || hue >= 342f) return GameBoard.RED;
        if (hue >= 20f && hue <= 38f) return GameBoard.ORANGE;
        if (hue >= 39f && hue <= 66f) return GameBoard.YELLOW;
        if (hue >= 70f && hue <= 165f) return GameBoard.GREEN;
        if (hue >= 166f && hue <= 199f) return GameBoard.CYAN;
        if (hue >= 200f && hue <= 255f) return GameBoard.BLUE;
        if (hue >= 260f && hue <= 338f) return GameBoard.PURPLE;
        return 0;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static final class Geometry {
        final float firstCenterX;
        final float firstCenterY;
        final float cellStepPx;
        final float cellStepYpx;

        Geometry(float firstCenterX, float firstCenterY, float cellStepPx, float cellStepYpx) {
            this.firstCenterX = firstCenterX;
            this.firstCenterY = firstCenterY;
            this.cellStepPx = cellStepPx;
            this.cellStepYpx = cellStepYpx;
        }

        float centerX(int column) {
            return firstCenterX + column * cellStepPx;
        }

        float centerY(int row) {
            return firstCenterY + row * cellStepYpx;
        }
    }

    static final class Result {
        final GameBoard board;
        final Geometry geometry;
        final String error;
        final double similarity;

        private Result(GameBoard board, Geometry geometry, String error, double similarity) {
            this.board = board;
            this.geometry = geometry;
            this.error = error;
            this.similarity = similarity;
        }

        boolean isSuccess() {
            return board != null;
        }

        static Result success(GameBoard board, double similarity, Geometry geometry) {
            return new Result(board, geometry, null, similarity);
        }

        static Result error(String error) {
            return new Result(null, null, error, 0);
        }
    }

    private static final class ColorMatch {
        final int color;
        final double confidence;

        ColorMatch(int color, double confidence) {
            this.color = color;
            this.confidence = confidence;
        }
    }
}
