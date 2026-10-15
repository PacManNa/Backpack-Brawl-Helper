package ua.borlock.autotap;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Locates the 4×4 board and uses its merge chain plus local image templates to match tiles. */
final class MergeMadnessVision {
    private static final float FIRST_CENTER_X_RATIO = 0.1875f;
    private static final float FIRST_CENTER_Y_RATIO = 0.3875f;
    private static final float CELL_STEP_X_RATIO = 0.2083f;
    private static final float CELL_STEP_Y_RATIO = 0.09375f;
    private static final float CENTER_FROM_GRID_TOP_RATIO = 0.45f;
    private static final float GRID_SEARCH_HALF_RANGE_CELLS = 0.80f;
    private static final float MIN_GRID_EDGE_SCORE = 35f;
    private static final float MIN_VERTICAL_GRID_EDGE_SCORE = 20f;
    private static final int SAMPLE_WIDTH = 22;
    private static final int SAMPLE_HEIGHT = 14;
    private static final float[] MATCH_SCALES = {0.90f, 1.0f, 1.10f};
    private static final float EMPTY_CELL_MAX_LUMA = 69f;
    private static final float OCCUPIED_CELL_MIN_LUMA = 88f;
    private static final double MIN_TEMPLATE_SCORE = 0.40;
    private static final double MIN_TEMPLATE_MARGIN = 0.04;
    private static final double HIGH_RANK_MIN_TEMPLATE_SCORE = 0.60;
    private static final double HIGH_RANK_MIN_TEMPLATE_MARGIN = 0.08;
    private static final double CUSTOM_TEMPLATE_MIN_SCORE = 0.42;
    private static final double CUSTOM_TEMPLATE_MIN_MARGIN = 0.035;
    private static final String CUSTOM_TEMPLATE_PREFIX = "merge_mouse_sample_";
    // Gameplay samples recover tile art when the detected cell center is slightly off.
    private static final int FIRST_SAMPLED_RANK_INDEX = 0;
    private static final int LAST_SAMPLED_RANK_INDEX = 9;
    private static final int FIRST_HIGH_RANK_INDEX = 7;
    private static final int LAST_HIGH_RANK_INDEX = 9;
    private static final double SAMPLED_RANK_MIN_TEMPLATE_SCORE = 0.60;
    private static final double SAMPLE_ALIGNMENT_SEARCH_SCORE = 0.82;
    private static final int[] SAMPLED_RANK_OFFSETS = {-8, -4, 0, 4, 8};
    private static final int[][] TEMPLATE_REGIONS = {
            {68, 574, 132, 91}, {250, 574, 141, 91}, {431, 574, 157, 91}, {620, 574, 162, 91},
            {65, 714, 143, 96}, {249, 714, 148, 96}, {426, 714, 166, 96}, {620, 714, 172, 96},
            {65, 858, 150, 98}, {247, 858, 160, 98}, {422, 858, 177, 98}, {615, 858, 190, 98},
            {65, 1003, 153, 108}, {247, 1003, 162, 108}, {420, 1003, 190, 108},
            {615, 1003, 206, 108}, {65, 1145, 183, 132}
    };

    // Real board examples from the supplied gameplay screenshot are a better match
    // for the first three tiles than the flat illustrations in the guide.
    private static final int[][] GAMEPLAY_TEMPLATE_CELLS = {
            {0, 0}, {1, 3}, {3, 2}
    };
    // Additional in-game samples cover tile ranks that are only silhouettes in the guide.
    // The sample image uses the same 576×1280 board layout as the measured geometry.
    private static final int[][] SAMPLE_GAMEPLAY_TEMPLATE_CELLS = {
            {0, 2}, {3, 0}, {2, 0}, {1, 1}
    };
    // The chain layout is measured in cell steps so it follows the detected board.
    private static final int PROGRESSION_SLOTS_PER_ROW = 5;
    private static final float PROGRESSION_FIRST_SLOT_X_OFFSET = -0.05f;
    private static final float PROGRESSION_SLOT_STEP_X_RATIO = 0.66f;
    private static final float[] PROGRESSION_ROW_OFFSET_FROM_LAST_CELL = {1.70f, 2.24f};
    private static final float PROGRESSION_SLOT_WIDTH_RATIO = 0.46f;
    private static final float PROGRESSION_SLOT_HEIGHT_RATIO = 0.28f;
    private static final float VISIBLE_PROGRESSION_LUMA_P90 = 54f;
    private static final float PROGRESSION_ARROW_X_OFFSET = 0.32f;
    private static final float PROGRESSION_ARROW_MIN_LUMA = 90f;
    private static final int PROGRESSION_COLOR_PATCHES = 3;
    private static final int PROGRESSION_COLOR_HUE_BINS = 8;
    private static final int PROGRESSION_COLOR_VALUE_BINS = 4;
    private static final int PROGRESSION_COLOR_SAMPLES_X = 16;
    private static final int PROGRESSION_COLOR_SAMPLES_Y = 10;
    private static final double[] PROGRESSION_MIN_SCORE = {
             0.25, 0.30, 0.21, 0.20, 0.40, 0.55, 0.60,
             Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
             Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
             Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
             Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY
    };
    private static final double[] PROGRESSION_MIN_MARGIN = {
            0.035, 0.035, 0.025, 0.025, 0.08, 0.15, 0.10, 0.10, 0.10, 0.10,
            0.10, 0.10, 0.10, 0.10, 0.10, 0.10, 0.10
    };

    private final float[][] templates = new float[TEMPLATE_REGIONS.length][];
    private final float[][] gameplayTemplates = new float[GAMEPLAY_TEMPLATE_CELLS.length][];
    private final float[][] sampleGameplayTemplates = new float[TEMPLATE_REGIONS.length][];
    private final float[][] customTemplates = new float[TEMPLATE_REGIONS.length][];
    private final Context appContext;
    private int[] screenshotPixels = new int[0];

    private MergeMadnessVision() {
        appContext = null;
    }

    MergeMadnessVision(Context context) {
        appContext = context.getApplicationContext();
        Bitmap guide = BitmapFactory.decodeResource(context.getResources(), R.raw.merge_madness_guide);
        if (guide != null) {
            int width = guide.getWidth();
            int height = guide.getHeight();
            int[] pixels = new int[width * height];
            guide.getPixels(pixels, 0, width, 0, 0, width, height);
            for (int index = 0; index < TEMPLATE_REGIONS.length; index++) {
                int[] region = TEMPLATE_REGIONS[index];
                templates[index] = signature(pixels, width, height,
                        region[0], region[1], region[2], region[3]);
            }
            guide.recycle();
        }
        loadGameplayTemplates(context);
        loadSampleGameplayTemplates(context);
        loadHigherRankGameplayTemplates(context);
        loadCustomTemplates();
    }

    static MergeMadnessVision forScreenshotCheck(PixelImage guide, PixelImage reference,
                                                  PixelImage gameplaySample,
                                                  PixelImage[] higherRankSamples) {
        MergeMadnessVision vision = new MergeMadnessVision();
        for (int index = 0; index < TEMPLATE_REGIONS.length; index++) {
            int[] region = TEMPLATE_REGIONS[index];
            vision.templates[index] = signature(guide.pixels, guide.width, guide.height,
                    region[0], region[1], region[2], region[3]);
        }
        float stepX = reference.width * CELL_STEP_X_RATIO;
        float stepY = reference.height * CELL_STEP_Y_RATIO;
        for (int index = 0; index < GAMEPLAY_TEMPLATE_CELLS.length; index++) {
            int[] cell = GAMEPLAY_TEMPLATE_CELLS[index];
            vision.gameplayTemplates[index] = gameplayCellSignature(reference,
                    cell[0], cell[1], stepX, stepY);
        }
        stepX = gameplaySample.width * CELL_STEP_X_RATIO;
        stepY = gameplaySample.height * CELL_STEP_Y_RATIO;
        for (int index = 0; index < SAMPLE_GAMEPLAY_TEMPLATE_CELLS.length; index++) {
            int[] cell = SAMPLE_GAMEPLAY_TEMPLATE_CELLS[index];
            vision.sampleGameplayTemplates[index] = gameplayCellSignature(gameplaySample,
                    cell[0], cell[1], stepX, stepY);
        }
        int[][] extraRanks = {
                {4, 1, 1}, {5, 1, 0}, {6, 2, 0},
                {7, 3, 0}, {8, 0, 1}, {9, 3, 1}
        };
        if (higherRankSamples != null) {
            for (int index = 0; index < Math.min(extraRanks.length, higherRankSamples.length);
                    index++) {
                PixelImage sample = higherRankSamples[index];
                if (sample == null) continue;
                int[] rank = extraRanks[index];
                // Match loadGameplayTemplate(), including its measured 576x1280 sample scale.
                float cellStep = sample.width * CELL_STEP_X_RATIO;
                vision.sampleGameplayTemplates[rank[0]] = gameplayCellSignature(
                        sample, rank[1], rank[2], cellStep, cellStep);
            }
        }
        return vision;
    }

    private static float[] gameplayCellSignature(PixelImage image, int row, int column,
                                                 float cellStepX, float cellStepY) {
        float cropWidth = cellStepX * 0.74f;
        float cropHeight = cellStepY * 0.46f;
        float centerX = image.width * FIRST_CENTER_X_RATIO + column * cellStepX;
        float centerY = image.height * FIRST_CENTER_Y_RATIO + row * cellStepY
                + cellStepY * 0.015f;
        return signature(image.pixels, image.width, image.height,
                centerX - cropWidth * 0.5f, centerY - cropHeight * 0.5f,
                cropWidth, cropHeight);
    }

    private void loadGameplayTemplates(Context context) {
        Bitmap reference = BitmapFactory.decodeResource(context.getResources(),
                R.raw.merge_madness_gameplay_reference);
        if (reference == null) return;

        int width = reference.getWidth();
        int height = reference.getHeight();
        int[] pixels = new int[width * height];
        reference.getPixels(pixels, 0, width, 0, 0, width, height);
        float cellStepX = width * CELL_STEP_X_RATIO;
        float cellStepY = height * CELL_STEP_Y_RATIO;
        float cropWidth = cellStepX * 0.74f;
        float cropHeight = cellStepY * 0.46f;
        for (int index = 0; index < GAMEPLAY_TEMPLATE_CELLS.length; index++) {
            int[] cell = GAMEPLAY_TEMPLATE_CELLS[index];
            float centerX = width * FIRST_CENTER_X_RATIO + cell[1] * cellStepX;
            float centerY = height * FIRST_CENTER_Y_RATIO + cell[0] * cellStepY
                    + cellStepY * 0.015f;
            gameplayTemplates[index] = signature(pixels, width, height,
                    centerX - cropWidth * 0.5f, centerY - cropHeight * 0.5f,
                    cropWidth, cropHeight);
        }
        reference.recycle();
    }

    private void loadSampleGameplayTemplates(Context context) {
        Bitmap reference = BitmapFactory.decodeResource(context.getResources(),
                R.raw.merge_madness_gameplay_sample);
        if (reference == null) return;

        int width = reference.getWidth();
        int height = reference.getHeight();
        int[] pixels = new int[width * height];
        reference.getPixels(pixels, 0, width, 0, 0, width, height);
        float cellStepX = width * CELL_STEP_X_RATIO;
        float cellStepY = height * CELL_STEP_Y_RATIO;
        float cropWidth = cellStepX * 0.74f;
        float cropHeight = cellStepY * 0.46f;
        for (int rankIndex = 0; rankIndex < SAMPLE_GAMEPLAY_TEMPLATE_CELLS.length; rankIndex++) {
            int[] cell = SAMPLE_GAMEPLAY_TEMPLATE_CELLS[rankIndex];
            float centerX = width * FIRST_CENTER_X_RATIO + cell[1] * cellStepX;
            float centerY = height * FIRST_CENTER_Y_RATIO + cell[0] * cellStepY
                    + cellStepY * 0.015f;
            sampleGameplayTemplates[rankIndex] = signature(pixels, width, height,
                    centerX - cropWidth * 0.5f, centerY - cropHeight * 0.5f,
                    cropWidth, cropHeight);
        }
        reference.recycle();
    }

    private void loadHigherRankGameplayTemplates(Context context) {
        // These real board samples distinguish the two green mice from lower-rank art.
        loadGameplayTemplate(context, R.raw.merge_madness_rank5_sample, 4, 1, 1);
        loadGameplayTemplate(context, R.raw.merge_madness_rank6_sample, 5, 1, 0);
        loadGameplayTemplate(context, R.raw.merge_madness_rank7_sample, 6, 2, 0);
        loadGameplayTemplate(context, R.raw.merge_madness_rank8_sample, 7, 3, 0);
        loadGameplayTemplate(context, R.raw.merge_madness_rank9_sample, 8, 0, 1);
        loadGameplayTemplate(context, R.raw.merge_madness_rank10_sample, 9, 3, 1);
    }

    private void loadGameplayTemplate(Context context, int resourceId, int rankIndex,
                                      int row, int column) {
        Bitmap reference = BitmapFactory.decodeResource(context.getResources(), resourceId);
        if (reference == null || rankIndex < 0 || rankIndex >= sampleGameplayTemplates.length) return;

        int width = reference.getWidth();
        int height = reference.getHeight();
        int[] pixels = new int[width * height];
        reference.getPixels(pixels, 0, width, 0, 0, width, height);
        float cellStepX = width * CELL_STEP_X_RATIO;
        float cellStepY = width * CELL_STEP_X_RATIO;
        float cropWidth = cellStepX * 0.74f;
        float cropHeight = cellStepY * 0.46f;
        float centerX = width * FIRST_CENTER_X_RATIO + column * cellStepX;
        float centerY = height * FIRST_CENTER_Y_RATIO + row * cellStepY
                + cellStepY * 0.015f;
        sampleGameplayTemplates[rankIndex] = signature(pixels, width, height,
                centerX - cropWidth * 0.5f, centerY - cropHeight * 0.5f,
                cropWidth, cropHeight);
        reference.recycle();
    }

    Result recognize(Bitmap screenshot) {
        return recognize(screenshot, null);
    }

    Result recognize(Bitmap screenshot, BoardVision.Geometry previousGeometry) {
        return recognize(screenshot, previousGeometry, null);
    }

    Result recognize(Bitmap screenshot, BoardVision.Geometry previousGeometry,
                     BoardVision.Geometry calibratedGeometry) {
        int width = screenshot.getWidth();
        int height = screenshot.getHeight();
        if (width >= height) return Result.error("Поверни телефон вертикально й відкрий поле 4×4.");
        if (templates[0] == null) return Result.error("Не завантажилися зразки плиток 2048.");

        int requiredPixels = width * height;
        if (screenshotPixels.length < requiredPixels) screenshotPixels = new int[requiredPixels];
        screenshot.getPixels(screenshotPixels, 0, width, 0, 0, width, height);

        return recognizeCapturedPixels(width, height, previousGeometry, calibratedGeometry);
    }

    Result recognizePixels(int[] pixels, int width, int height,
                           BoardVision.Geometry previousGeometry,
                           BoardVision.Geometry calibratedGeometry) {
        if (pixels == null || width <= 0 || height <= 0 || pixels.length < width * height) {
            return Result.error("Кадр екрана пошкоджений.");
        }
        if (width >= height) return Result.error("Поверни телефон вертикально й відкрий поле 4×4.");
        if (templates[0] == null) return Result.error("Не завантажилися зразки плиток 2048.");
        int requiredPixels = width * height;
        if (screenshotPixels.length < requiredPixels) screenshotPixels = new int[requiredPixels];
        System.arraycopy(pixels, 0, screenshotPixels, 0, requiredPixels);
        return recognizeCapturedPixels(width, height, previousGeometry, calibratedGeometry);
    }

    private Result recognizeCapturedPixels(int width, int height,
                                           BoardVision.Geometry previousGeometry,
                                           BoardVision.Geometry calibratedGeometry) {
        MergeMadnessLayout.Result layout = MergeMadnessLayout.locate(
                screenshotPixels, width, height, previousGeometry, calibratedGeometry);
        if (layout == null) {
            return Result.error("Не знайшов межі поля 4×4. Відкрий «Злиття Божевілля» й дочекайся завершення анімації.");
        }
        BoardVision.Geometry geometry = layout.geometry;

        if (layout.progressionRanks == 0) {
            return Result.error("Не бачу списку злиття мишей унизу екрана.");
        }
        float[][] visibleProgressionTemplates = readVisibleProgressionTemplates(
                screenshotPixels, width, height, layout);
        // The chain may be scrolled or partially obscured. It confirms the game mode,
        // but must not cap ranks already present on the board.
        int maximumRank = templates.length;

        int[] ranks = new int[MergeMadnessBoard.CELL_COUNT];
        boolean[] recognizedCells = new boolean[MergeMadnessBoard.CELL_COUNT];
        int occupied = 0;
        double weakestConfidence = 1.0;
        String firstCellError = null;

        for (int row = 0; row < MergeMadnessBoard.SIDE; row++) {
            for (int column = 0; column < MergeMadnessBoard.SIDE; column++) {
                int index = row * MergeMadnessBoard.SIDE + column;
                float centerX = geometry.centerX(column);
                float centerY = geometry.centerY(row);
                float backgroundLuma = cellBackgroundLuma(screenshotPixels, width, height,
                        centerX, centerY, geometry.cellStepPx, geometry.cellStepYpx);
                if (backgroundLuma <= EMPTY_CELL_MAX_LUMA) {
                    recognizedCells[index] = true;
                    continue;
                }
                if (backgroundLuma < OCCUPIED_CELL_MIN_LUMA) {
                    if (firstCellError == null) {
                        firstCellError = "Не впевнено визначив клітинку " + (row + 1) + ", "
                                + (column + 1) + ". Дочекайся завершення анімації.";
                    }
                    continue;
                }

                occupied++;
                float cropWidth = geometry.cellStepPx * 0.74f;
                float cropHeight = geometry.cellStepYpx * 0.46f;
                float cropCenterY = centerY + geometry.cellStepYpx * 0.015f;
                TileMatch match = matchTile(screenshotPixels, width, height,
                        centerX - cropWidth * 0.5f, cropCenterY - cropHeight * 0.5f,
                        cropWidth, cropHeight, maximumRank);
                if (visibleProgressionTemplates != null) {
                    TileMatch progressionMatch = matchVisibleProgression(
                            screenshotPixels, width, height,
                            centerX - cropWidth * 0.5f, cropCenterY - cropHeight * 0.5f,
                            cropWidth, cropHeight, visibleProgressionTemplates);
                    match = chooseTileMatch(match, progressionMatch);
                }
                if (match.rank == 0) {
                    if (firstCellError == null) {
                        firstCellError = "Не розпізнав рівень щура в клітинці " + (row + 1) + ", "
                                + (column + 1) + ". Перевір, що відкрита саме «Злиття Божевілля».";
                    }
                    continue;
                }
                ranks[index] = match.rank;
                recognizedCells[index] = true;
                weakestConfidence = Math.min(weakestConfidence, match.score);
            }
        }
        if (firstCellError != null) {
            return Result.error(firstCellError, ranks, recognizedCells,
                    weakestConfidence, layout);
        }
        if (occupied == 0) {
            return Result.error("Не знайшов плиток на полі «Злиття Божевілля».",
                    ranks, recognizedCells, weakestConfidence, layout);
        }
        return Result.success(new MergeMadnessBoard(ranks), weakestConfidence, layout);
    }

    boolean saveMouseSample(int rank, int[] pixels, int width, int height, RectF sampleBounds) {
        if (rank < 1 || rank > customTemplates.length || pixels == null
                || width <= 0 || height <= 0 || pixels.length < width * height
                || sampleBounds == null || sampleBounds.left < 0 || sampleBounds.top < 0
                || sampleBounds.right > width || sampleBounds.bottom > height
                || sampleBounds.width() < 8f || sampleBounds.height() < 6f
                || sampleBounds.width() / sampleBounds.height() < 0.35f
                || sampleBounds.width() / sampleBounds.height() > 4.0f) return false;

        float[] learned = signature(pixels, width, height, sampleBounds.left,
                sampleBounds.top, sampleBounds.width(), sampleBounds.height());
        if (learned.length != SAMPLE_WIDTH * SAMPLE_HEIGHT * 3) return false;
        String encoded = encodeTemplate(learned);
        if (encoded == null) return false;
        boolean saved = appContext.getSharedPreferences(TapAccessibilityService.PREFS,
                        Context.MODE_PRIVATE)
                .edit().putString(CUSTOM_TEMPLATE_PREFIX + rank, encoded).commit();
        if (saved) customTemplates[rank - 1] = learned;
        return saved;
    }

    private void loadCustomTemplates() {
        SharedPreferences preferences = appContext.getSharedPreferences(
                TapAccessibilityService.PREFS, Context.MODE_PRIVATE);
        for (int rank = 1; rank <= customTemplates.length; rank++) {
            String encoded = preferences.getString(CUSTOM_TEMPLATE_PREFIX + rank, null);
            if (encoded == null) continue;
            float[] decoded = decodeTemplate(encoded);
            if (decoded != null) customTemplates[rank - 1] = decoded;
        }
    }

    private String encodeTemplate(float[] template) {
        ByteBuffer bytes = ByteBuffer.allocate(template.length * Float.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (float value : template) bytes.putFloat(value);
        return Base64.encodeToString(bytes.array(), Base64.NO_WRAP);
    }

    private float[] decodeTemplate(String encoded) {
        try {
            byte[] bytes = Base64.decode(encoded, Base64.NO_WRAP);
            if (bytes.length != SAMPLE_WIDTH * SAMPLE_HEIGHT * 3 * Float.BYTES) return null;
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            float[] result = new float[bytes.length / Float.BYTES];
            for (int index = 0; index < result.length; index++) result[index] = buffer.getFloat();
            return result;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private BoardVision.Geometry locateGeometry(int[] pixels, int width, int height) {
        float cellStepX = width * CELL_STEP_X_RATIO;
        float cellStepY = width * CELL_STEP_X_RATIO;
        float expectedTop = height * FIRST_CENTER_Y_RATIO
                - cellStepY * CENTER_FROM_GRID_TOP_RATIO;
        float[] horizontalEdges = horizontalEdgeProfile(pixels, width, height);
        int minimumTop = Math.max(3, Math.round(expectedTop
                - cellStepY * GRID_SEARCH_HALF_RANGE_CELLS));
        int maximumTop = Math.min(height - Math.round(cellStepY * 4f) - 3,
                Math.round(expectedTop + cellStepY * GRID_SEARCH_HALF_RANGE_CELLS));
        float bestHorizontalScore = 0f;
        int bestTop = -1;
        for (int top = minimumTop; top <= maximumTop; top++) {
            float score = 0f;
            for (int row = 0; row <= MergeMadnessBoard.SIDE; row++) {
                score += strongestNearby(horizontalEdges,
                        Math.round(top + row * cellStepY), 2);
            }
            if (score > bestHorizontalScore) {
                bestHorizontalScore = score;
                bestTop = top;
            }
        }
        if (bestTop < 0 || bestHorizontalScore < MIN_GRID_EDGE_SCORE) return null;

        float firstCenterY = bestTop + cellStepY * CENTER_FROM_GRID_TOP_RATIO;
        float[] verticalEdges = verticalEdgeProfile(pixels, width, height,
                bestTop, cellStepY);
        float expectedCenterX = width * FIRST_CENTER_X_RATIO;
        int minimumCenterX = Math.max(Math.round(cellStepX * 0.60f),
                Math.round(expectedCenterX - cellStepX * 0.40f));
        int maximumCenterX = Math.min(width - Math.round(cellStepX * 3.60f),
                Math.round(expectedCenterX + cellStepX * 0.40f));
        float bestVerticalScore = 0f;
        int bestCenterX = -1;
        for (int centerX = minimumCenterX; centerX <= maximumCenterX; centerX++) {
            float score = verticalGridScore(verticalEdges, centerX, cellStepX);
            if (score > bestVerticalScore) {
                bestVerticalScore = score;
                bestCenterX = centerX;
            }
        }
        if (bestCenterX < 0 || bestVerticalScore < MIN_VERTICAL_GRID_EDGE_SCORE) return null;

        float correctedCenterX = bestCenterX - cellStepX * 0.029f;
        // Perspective, tile art and the floating helper panel can create a stronger
        // false edge alignment. Keep the measured center close to the known centered board.
        if (Math.abs(correctedCenterX - expectedCenterX) > cellStepX * 0.06f) {
            correctedCenterX = expectedCenterX;
        }
        return new BoardVision.Geometry(correctedCenterX, firstCenterY,
                cellStepX, cellStepY);
    }

    private float[] horizontalEdgeProfile(int[] pixels, int width, int height) {
        int left = clamp(Math.round(width * 0.52f), 1, width - 2);
        int right = clamp(Math.round(width * 0.92f), left + 1, width - 1);
        float[] edges = new float[height];
        for (int y = 1; y < height - 1; y++) {
            float total = 0f;
            int count = 0;
            for (int x = left; x < right; x++) {
                float above = pixelLuma(pixels[(y - 1) * width + x]);
                float below = pixelLuma(pixels[(y + 1) * width + x]);
                total += Math.abs(below - above);
                count++;
            }
            edges[y] = total / Math.max(1, count);
        }
        return smoothProfile(edges);
    }

    private float[] verticalEdgeProfile(int[] pixels, int width, int height,
                                        int gridTop, float cellStepY) {
        int top = clamp(gridTop - 8, 1, height - 2);
        int bottom = clamp(Math.round(gridTop + cellStepY * 4f + 12f), top + 1, height - 1);
        float[] edges = new float[width];
        for (int x = 1; x < width - 1; x++) {
            float total = 0f;
            int count = 0;
            for (int y = top; y < bottom; y++) {
                float left = pixelLuma(pixels[y * width + x - 1]);
                float right = pixelLuma(pixels[y * width + x + 1]);
                total += Math.abs(right - left);
                count++;
            }
            edges[x] = total / Math.max(1, count);
        }
        return smoothProfile(edges);
    }

    private float[] smoothProfile(float[] values) {
        float[] result = new float[values.length];
        for (int index = 2; index < values.length - 2; index++) {
            result[index] = (values[index - 2] + values[index - 1] + values[index]
                    + values[index + 1] + values[index + 2]) / 5f;
        }
        return result;
    }

    private float strongestNearby(float[] profile, int center, int radius) {
        float best = 0f;
        for (int offset = -radius; offset <= radius; offset++) {
            int index = center + offset;
            if (index >= 0 && index < profile.length) best = Math.max(best, profile[index]);
        }
        return best;
    }

    private float verticalGridScore(float[] profile, int firstCenterX, float cellStepX) {
        float[] edgeOffsets = {-0.567f, 0.5f, 1.5f, 2.5f, 3.567f};
        float score = 0f;
        for (float offset : edgeOffsets) {
            score += strongestNearby(profile, Math.round(firstCenterX + offset * cellStepX), 2);
        }
        return score;
    }

    private float pixelLuma(int color) {
        return red(color) * 0.299f + green(color) * 0.587f + blue(color) * 0.114f;
    }

    private int visibleProgressionRankCount(int[] pixels, int width, int height,
                                            BoardVision.Geometry geometry) {
        int firstRowCount = visibleProgressionRowCount(pixels, width, height, geometry, 0);
        if (firstRowCount < PROGRESSION_SLOTS_PER_ROW) return firstRowCount;

        float secondRowY = progressionRowCenterY(geometry, 1);
        float firstArrowX = progressionFirstSlotCenterX(geometry)
                + geometry.cellStepPx * PROGRESSION_ARROW_X_OFFSET;
        if (averageLuma(pixels, width, height, firstArrowX, secondRowY)
                < PROGRESSION_ARROW_MIN_LUMA) {
            return firstRowCount;
        }
        return firstRowCount + visibleProgressionRowCount(pixels, width, height, geometry, 1);
    }

    private float[][] readVisibleProgressionTemplates(int[] pixels, int width, int height,
                                                       MergeMadnessLayout.Result layout) {
        if (layout.progressionRanks == 0) return null;
        BoardVision.Geometry geometry = layout.geometry;
        float[][] result = new float[PROGRESSION_SLOTS_PER_ROW * 4][];
        float sampleWidth = geometry.cellStepPx * PROGRESSION_SLOT_WIDTH_RATIO;
        float sampleHeight = geometry.cellStepYpx * PROGRESSION_SLOT_HEIGHT_RATIO;
        float firstCenterX = geometry.centerX(0) + geometry.cellStepPx * PROGRESSION_FIRST_SLOT_X_OFFSET;
        for (int row = 0; row < layout.progressionRows; row++) {
            float centerY = layout.progressionRowCentersY[row];
            for (int slot = 0; slot < layout.progressionSlotsByRow[row]; slot++) {
                int rankIndex = row * PROGRESSION_SLOTS_PER_ROW + slot;
                if (rankIndex >= 17) break;
                float centerX = firstCenterX + slot * geometry.cellStepPx
                        * PROGRESSION_SLOT_STEP_X_RATIO;
                result[rankIndex] = progressionSignature(pixels, width, height,
                        centerX - sampleWidth * 0.5f, centerY - sampleHeight * 0.5f,
                        sampleWidth, sampleHeight);
            }
        }
        return result;
    }

    private int visibleProgressionRowCount(int[] pixels, int width, int height,
                                           BoardVision.Geometry geometry, int row) {
        float centerY = progressionRowCenterY(geometry, row);
        float sampleWidth = geometry.cellStepPx * PROGRESSION_SLOT_WIDTH_RATIO;
        float sampleHeight = geometry.cellStepYpx * PROGRESSION_SLOT_HEIGHT_RATIO;
        int count = 0;
        float firstCenterX = progressionFirstSlotCenterX(geometry);
        for (int slot = 0; slot < PROGRESSION_SLOTS_PER_ROW; slot++) {
            float centerX = firstCenterX
                    + slot * geometry.cellStepPx * PROGRESSION_SLOT_STEP_X_RATIO;
            if (lumaPercentile90(pixels, width, height, centerX, centerY,
                    sampleWidth, sampleHeight) < VISIBLE_PROGRESSION_LUMA_P90) {
                break;
            }
            count++;
        }
        return count;
    }

    private float progressionFirstSlotCenterX(BoardVision.Geometry geometry) {
        return geometry.centerX(0) + geometry.cellStepPx * PROGRESSION_FIRST_SLOT_X_OFFSET;
    }

    private float progressionRowCenterY(BoardVision.Geometry geometry, int row) {
        return geometry.centerY(MergeMadnessBoard.SIDE - 1)
                + geometry.cellStepYpx * PROGRESSION_ROW_OFFSET_FROM_LAST_CELL[row];
    }

    private float lumaPercentile90(int[] pixels, int width, int height,
                                   float centerX, float centerY,
                                   float sampleWidth, float sampleHeight) {
        int left = Math.round(centerX - sampleWidth * 0.5f);
        int top = Math.round(centerY - sampleHeight * 0.5f);
        int right = Math.round(centerX + sampleWidth * 0.5f);
        int bottom = Math.round(centerY + sampleHeight * 0.5f);
        int[] histogram = new int[256];
        int count = 0;
        for (int y = top; y <= bottom; y++) {
            int sampleY = clamp(y, 0, height - 1);
            for (int x = left; x <= right; x++) {
                int sampleX = clamp(x, 0, width - 1);
                int color = pixels[sampleY * width + sampleX];
                float luma = red(color) * 0.299f
                        + green(color) * 0.587f + blue(color) * 0.114f;
                histogram[clamp(Math.round(luma), 0, 255)]++;
                count++;
            }
        }
        int percentileIndex = Math.round(count * 0.90f);
        int accumulated = 0;
        for (int luma = 0; luma < histogram.length; luma++) {
            accumulated += histogram[luma];
            if (accumulated > percentileIndex) return luma;
        }
        return 0f;
    }

    private TileMatch matchTile(int[] pixels, int width, int height,
                                float left, float top, float cropWidth, float cropHeight,
                                int maximumRank) {
        int rankCount = Math.min(templates.length, maximumRank);
        if (rankCount <= 0) return new TileMatch(0, 0.0, 0.0);
        double[] rankScores = new double[rankCount];
        double[] customScores = new double[rankCount];
        for (int scaleIndex = 0; scaleIndex < MATCH_SCALES.length; scaleIndex++) {
            float scale = MATCH_SCALES[scaleIndex];
            float scaledWidth = cropWidth * scale;
            float scaledHeight = cropHeight * scale;
            float scaledLeft = left + (cropWidth - scaledWidth) * 0.5f;
            float scaledTop = top + (cropHeight - scaledHeight) * 0.5f;
            float[] candidate = signature(pixels, width, height,
                    scaledLeft, scaledTop, scaledWidth, scaledHeight);
            for (int index = 0; index < rankCount; index++) {
                rankScores[index] = Math.max(rankScores[index], dot(candidate, templates[index]));
                if (index < gameplayTemplates.length && gameplayTemplates[index] != null) {
                    rankScores[index] = Math.max(rankScores[index],
                            dot(candidate, gameplayTemplates[index]));
                }
                if (sampleGameplayTemplates[index] != null) {
                    rankScores[index] = Math.max(rankScores[index],
                            dot(candidate, sampleGameplayTemplates[index]));
                }
                if (customTemplates[index] != null) {
                    customScores[index] = Math.max(customScores[index],
                            dot(candidate, customTemplates[index]));
                }
            }
        }

        TileMatch initialMatch = bestTemplateMatch(rankScores, customScores, rankCount);
        // Positional recovery is for weak direct crops. Searching offsets after a
        // strong match can inflate similar-looking ranks and erase a valid margin.
        if (initialMatch.score < SAMPLE_ALIGNMENT_SEARCH_SCORE) {
            for (int rankIndex = FIRST_SAMPLED_RANK_INDEX;
                    rankIndex <= LAST_SAMPLED_RANK_INDEX && rankIndex < rankCount;
                    rankIndex++) {
                if (sampleGameplayTemplates[rankIndex] == null) continue;
                float[] rankTemplate = sampleGameplayTemplates[rankIndex];
                for (float scale : MATCH_SCALES) {
                    float scaledWidth = cropWidth * scale;
                    float scaledHeight = cropHeight * scale;
                    float scaledLeft = left + (cropWidth - scaledWidth) * 0.5f;
                    float scaledTop = top + (cropHeight - scaledHeight) * 0.5f;
                    for (int offsetY : SAMPLED_RANK_OFFSETS) {
                        for (int offsetX : SAMPLED_RANK_OFFSETS) {
                            float[] candidate = signature(pixels, width, height,
                                    scaledLeft + offsetX, scaledTop + offsetY,
                                    scaledWidth, scaledHeight);
                            rankScores[rankIndex] = Math.max(rankScores[rankIndex],
                                    dot(candidate, rankTemplate));
                        }
                    }
                }
            }
        }

        return bestTemplateMatch(rankScores, customScores, rankCount);
    }

    private TileMatch bestTemplateMatch(double[] rankScores, double[] customScores,
                                        int rankCount) {
        int builtinBestIndex = -1;
        double builtinBestScore = Double.NEGATIVE_INFINITY;
        double builtinSecondScore = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < rankCount; index++) {
            double score = rankScores[index];
            if (index >= FIRST_HIGH_RANK_INDEX && index <= LAST_HIGH_RANK_INDEX
                    && sampleGameplayTemplates[index] == null
                    && score < SAMPLED_RANK_MIN_TEMPLATE_SCORE) score = 0.0;
            if (score > builtinBestScore) {
                builtinSecondScore = builtinBestScore;
                builtinBestScore = score;
                builtinBestIndex = index;
            } else if (score > builtinSecondScore) {
                builtinSecondScore = score;
            }
        }
        TileMatch builtin = new TileMatch(builtinBestIndex + 1, builtinBestScore,
                Math.max(0.0, builtinBestScore - builtinSecondScore));

        // Learned samples supplement built-in recognition. They must not lower a
        // built-in match's margin and turn a clearly recognized tile into an '?'.
        if (isConfidentTemplateMatch(builtin)) return builtin;

        int customBestIndex = -1;
        double customBestScore = Double.NEGATIVE_INFINITY;
        double customSecondScore = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < rankCount; index++) {
            double score = customScores[index];
            if (score > customBestScore) {
                customSecondScore = customBestScore;
                customBestScore = score;
                customBestIndex = index;
            } else if (score > customSecondScore) {
                customSecondScore = score;
            }
        }
        if (customBestIndex >= 0) {
            double strongestOtherBuiltin = Double.NEGATIVE_INFINITY;
            for (int index = 0; index < rankCount; index++) {
                if (index != customBestIndex) {
                    strongestOtherBuiltin = Math.max(strongestOtherBuiltin, rankScores[index]);
                }
            }
            double customMargin = Math.max(0.0, customBestScore - Math.max(
                    customSecondScore, strongestOtherBuiltin));
            TileMatch custom = new TileMatch(customBestIndex + 1, customBestScore,
                    customMargin, true);
            if (isConfidentTemplateMatch(custom)) return custom;
            if (customBestScore > builtin.score) {
                return new TileMatch(0, customBestScore, customMargin);
            }
        }
        return new TileMatch(0, Math.max(0.0, builtin.score), builtin.margin);
    }

    static TileMatch bestTemplateMatchForTest(double[] rankScores, double[] customScores) {
        int count = Math.min(rankScores == null ? 0 : rankScores.length,
                customScores == null ? 0 : customScores.length);
        if (count == 0) return new TileMatch(0, 0.0, 0.0);
        return new MergeMadnessVision().bestTemplateMatch(rankScores, customScores, count);
    }

    static boolean isConfidentTemplateMatch(TileMatch match) {
        if (match == null || match.rank <= 0) return false;
        double minimumScore;
        double minimumMargin;
        if (match.customSample) {
            minimumScore = CUSTOM_TEMPLATE_MIN_SCORE;
            minimumMargin = CUSTOM_TEMPLATE_MIN_MARGIN;
        } else if (match.rank > FIRST_HIGH_RANK_INDEX) {
            minimumScore = HIGH_RANK_MIN_TEMPLATE_SCORE;
            minimumMargin = HIGH_RANK_MIN_TEMPLATE_MARGIN;
        } else {
            minimumScore = MIN_TEMPLATE_SCORE;
            minimumMargin = MIN_TEMPLATE_MARGIN;
        }
        return match.score >= minimumScore && match.margin >= minimumMargin;
    }

    static TileMatch chooseTileMatch(TileMatch direct, TileMatch progression) {
        if (isConfidentProgressionMatch(progression)) return progression;
        if (isConfidentTemplateMatch(direct)) return direct;
        double score = direct == null ? 0.0 : direct.score;
        double margin = direct == null ? 0.0 : direct.margin;
        return new TileMatch(0, score, margin);
    }

    private TileMatch matchVisibleProgression(int[] pixels, int width, int height,
                                              float left, float top,
                                              float cropWidth, float cropHeight,
                                              float[][] progressionTemplates) {
        double[] rankScores = new double[progressionTemplates.length];
        for (float scale : MATCH_SCALES) {
            float scaledWidth = cropWidth * scale;
            float scaledHeight = cropHeight * scale;
            float scaledLeft = left + (cropWidth - scaledWidth) * 0.5f;
            float scaledTop = top + (cropHeight - scaledHeight) * 0.5f;
            float[] candidate = progressionSignature(pixels, width, height,
                    scaledLeft, scaledTop, scaledWidth, scaledHeight);
            for (int rank = 0; rank < progressionTemplates.length; rank++) {
                if (progressionTemplates[rank] != null) {
                    rankScores[rank] = Math.max(rankScores[rank],
                            dot(candidate, progressionTemplates[rank]));
                }
            }
        }

        boolean[] visible = new boolean[rankScores.length];
        for (int rank = 0; rank < visible.length; rank++) {
            visible[rank] = progressionTemplates[rank] != null;
        }
        return bestVisibleProgressionMatch(rankScores, visible);
    }

    static TileMatch bestVisibleProgressionMatch(double[] rankScores, boolean[] visible) {
        if (rankScores == null || visible == null) return new TileMatch(0, 0.0, 0.0);
        int count = Math.min(rankScores.length, Math.min(visible.length,
                PROGRESSION_MIN_SCORE.length));
        int bestIndex = -1;
        int visibleTemplateCount = 0;
        double bestScore = Double.NEGATIVE_INFINITY;
        double secondScore = Double.NEGATIVE_INFINITY;
        for (int rank = 0; rank < count; rank++) {
            if (!visible[rank] || !Double.isFinite(PROGRESSION_MIN_SCORE[rank])) continue;
            double score = rankScores[rank];
            if (!Double.isFinite(score)) continue;
            visibleTemplateCount++;
            if (score > bestScore) {
                secondScore = bestScore;
                bestScore = score;
                bestIndex = rank;
            } else if (score > secondScore) {
                secondScore = score;
            }
        }
        if (bestIndex < 0) return new TileMatch(0, 0.0, 0.0);

        double margin = Math.max(0.0, bestScore - secondScore);
        if (visibleTemplateCount < 2) return new TileMatch(0, bestScore, margin);
        TileMatch candidate = new TileMatch(bestIndex + 1, bestScore, margin);
        return isConfidentProgressionMatch(candidate)
                ? candidate : new TileMatch(0, bestScore, margin);
    }

    private static boolean isConfidentProgressionMatch(TileMatch match) {
        if (match.rank <= 0 || match.rank > PROGRESSION_MIN_SCORE.length) return false;
        int rankIndex = match.rank - 1;
        return match.score >= PROGRESSION_MIN_SCORE[rankIndex]
                && match.margin >= PROGRESSION_MIN_MARGIN[rankIndex];
    }

    private float[] progressionSignature(int[] pixels, int imageWidth, int imageHeight,
                                         float left, float top, float width, float height) {
        int binsPerPatch = PROGRESSION_COLOR_HUE_BINS + PROGRESSION_COLOR_VALUE_BINS;
        float[] result = new float[PROGRESSION_COLOR_PATCHES
                * PROGRESSION_COLOR_PATCHES * binsPerPatch];
        float[] hsv = new float[3];
        int output = 0;
        for (int patchY = 0; patchY < PROGRESSION_COLOR_PATCHES; patchY++) {
            for (int patchX = 0; patchX < PROGRESSION_COLOR_PATCHES; patchX++) {
                float[] hueHistogram = new float[PROGRESSION_COLOR_HUE_BINS];
                int[] valueHistogram = new int[PROGRESSION_COLOR_VALUE_BINS];
                float hueWeight = 0f;
                for (int sampleY = 0; sampleY < PROGRESSION_COLOR_SAMPLES_Y; sampleY++) {
                    int sampleRow = patchY * PROGRESSION_COLOR_SAMPLES_Y + sampleY;
                    int sourceY = clamp(Math.round(top
                                    + (sampleRow + 0.5f) * height
                                    / (PROGRESSION_COLOR_PATCHES * PROGRESSION_COLOR_SAMPLES_Y)),
                            0, imageHeight - 1);
                    for (int sampleX = 0; sampleX < PROGRESSION_COLOR_SAMPLES_X; sampleX++) {
                        int sampleColumn = patchX * PROGRESSION_COLOR_SAMPLES_X + sampleX;
                        int sourceX = clamp(Math.round(left
                                        + (sampleColumn + 0.5f) * width
                                        / (PROGRESSION_COLOR_PATCHES * PROGRESSION_COLOR_SAMPLES_X)),
                                0, imageWidth - 1);
                        rgbToHsv(pixels[sourceY * imageWidth + sourceX], hsv);
                        int valueBin = clamp((int) (hsv[2] * PROGRESSION_COLOR_VALUE_BINS),
                                0, PROGRESSION_COLOR_VALUE_BINS - 1);
                        valueHistogram[valueBin]++;
                        if (hsv[1] > 0.18f && hsv[2] > 0.16f) {
                            int hueBin = clamp((int) (hsv[0] / 360f
                                            * PROGRESSION_COLOR_HUE_BINS),
                                    0, PROGRESSION_COLOR_HUE_BINS - 1);
                            hueHistogram[hueBin] += hsv[1];
                            hueWeight += hsv[1];
                        }
                    }
                }

                for (int bin = 0; bin < hueHistogram.length; bin++) {
                    result[output++] = hueWeight > 0f
                            ? hueHistogram[bin] / hueWeight : 0f;
                }
                float sampleCount = PROGRESSION_COLOR_SAMPLES_X
                        * PROGRESSION_COLOR_SAMPLES_Y;
                for (int bin = 0; bin < valueHistogram.length; bin++) {
                    result[output++] = valueHistogram[bin] / sampleCount;
                }
            }
        }

        float mean = 0f;
        for (float value : result) mean += value;
        mean /= result.length;
        double magnitudeSquared = 0.0;
        for (int index = 0; index < result.length; index++) {
            result[index] -= mean;
            magnitudeSquared += result[index] * result[index];
        }
        double magnitude = Math.sqrt(magnitudeSquared);
        if (magnitude > 0.0) {
            for (int index = 0; index < result.length; index++) result[index] /= magnitude;
        }
        return result;
    }

    private static float[] signature(int[] pixels, int imageWidth, int imageHeight,
                                     float left, float top, float width, float height) {
        float[] result = new float[SAMPLE_WIDTH * SAMPLE_HEIGHT * 3];
        float[] means = new float[3];
        int sample = 0;
        for (int y = 0; y < SAMPLE_HEIGHT; y++) {
            int sourceY = clamp(Math.round(top + (y + 0.5f) * height / SAMPLE_HEIGHT),
                    0, imageHeight - 1);
            for (int x = 0; x < SAMPLE_WIDTH; x++) {
                int sourceX = clamp(Math.round(left + (x + 0.5f) * width / SAMPLE_WIDTH),
                        0, imageWidth - 1);
                int color = pixels[sourceY * imageWidth + sourceX];
                int offset = sample++ * 3;
                result[offset] = red(color);
                result[offset + 1] = green(color);
                result[offset + 2] = blue(color);
                means[0] += result[offset];
                means[1] += result[offset + 1];
                means[2] += result[offset + 2];
            }
        }

        float sampleCount = SAMPLE_WIDTH * SAMPLE_HEIGHT;
        for (int channel = 0; channel < 3; channel++) means[channel] /= sampleCount;
        double magnitudeSquared = 0.0;
        for (int index = 0; index < result.length; index += 3) {
            for (int channel = 0; channel < 3; channel++) {
                float centered = result[index + channel] - means[channel];
                result[index + channel] = centered;
                magnitudeSquared += centered * centered;
            }
        }
        double magnitude = Math.sqrt(magnitudeSquared);
        if (magnitude > 0.0) {
            for (int index = 0; index < result.length; index++) result[index] /= magnitude;
        }
        return result;
    }

    private float cellBackgroundLuma(int[] pixels, int width, int height,
                                     float centerX, float centerY, float stepX, float stepY) {
        float offsetX = stepX * 0.33f;
        float offsetY = stepY * 0.29f;
        float total = 0f;
        total += averageLuma(pixels, width, height, centerX - offsetX, centerY - offsetY);
        total += averageLuma(pixels, width, height, centerX + offsetX, centerY - offsetY);
        total += averageLuma(pixels, width, height, centerX - offsetX, centerY + offsetY);
        total += averageLuma(pixels, width, height, centerX + offsetX, centerY + offsetY);
        return total * 0.25f;
    }

    private float averageLuma(int[] pixels, int width, int height, float centerX, float centerY) {
        int x = Math.round(centerX);
        int y = Math.round(centerY);
        float total = 0f;
        int count = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int color = pixels[clamp(y + dy, 0, height - 1) * width
                        + clamp(x + dx, 0, width - 1)];
                total += red(color) * 0.299f + green(color) * 0.587f
                        + blue(color) * 0.114f;
                count++;
            }
        }
        return total / count;
    }

    private double dot(float[] first, float[] second) {
        double result = 0.0;
        for (int index = 0; index < first.length; index++) result += first[index] * second[index];
        return result;
    }

    private static int red(int color) {
        return (color >>> 16) & 0xFF;
    }

    private static int green(int color) {
        return (color >>> 8) & 0xFF;
    }

    private static int blue(int color) {
        return color & 0xFF;
    }

    private static void rgbToHsv(int color, float[] hsv) {
        float red = red(color) / 255f;
        float green = green(color) / 255f;
        float blue = blue(color) / 255f;
        float maximum = Math.max(red, Math.max(green, blue));
        float minimum = Math.min(red, Math.min(green, blue));
        float delta = maximum - minimum;
        float hue;
        if (delta == 0f) hue = 0f;
        else if (maximum == red) hue = ((green - blue) / delta) % 6f;
        else if (maximum == green) hue = (blue - red) / delta + 2f;
        else hue = (red - green) / delta + 4f;
        hue *= 60f;
        if (hue < 0f) hue += 360f;
        hsv[0] = hue;
        hsv[1] = maximum == 0f ? 0f : delta / maximum;
        hsv[2] = maximum;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static final class PixelImage {
        final int[] pixels;
        final int width;
        final int height;

        PixelImage(int[] pixels, int width, int height) {
            if (pixels == null || width <= 0 || height <= 0
                    || pixels.length < width * height) {
                throw new IllegalArgumentException("Invalid pixel image");
            }
            this.pixels = pixels;
            this.width = width;
            this.height = height;
        }
    }

    static final class Result {
        final MergeMadnessBoard board;
        final BoardVision.Geometry geometry;
        final String error;
        final double similarity;

        boolean isSuccess() {
            return board != null;
        }

        final int progressionRows;
        final int progressionRanks;
        final float fieldOffsetY;
        final float frameShiftY;
        final float geometryConfidence;
        final int[] partialRanks;
        final boolean[] recognizedCells;

        private Result(MergeMadnessBoard board, BoardVision.Geometry geometry,
                       String error, double similarity, int progressionRows,
                       int progressionRanks, float fieldOffsetY, float frameShiftY,
                       float geometryConfidence, int[] partialRanks,
                       boolean[] recognizedCells) {
            this.board = board;
            this.geometry = geometry;
            this.error = error;
            this.similarity = similarity;
            this.progressionRows = progressionRows;
            this.progressionRanks = progressionRanks;
            this.fieldOffsetY = fieldOffsetY;
            this.frameShiftY = frameShiftY;
            this.geometryConfidence = geometryConfidence;
            this.partialRanks = partialRanks;
            this.recognizedCells = recognizedCells;
        }

        static Result success(MergeMadnessBoard board, double similarity,
                              MergeMadnessLayout.Result layout) {
            return new Result(board, layout.geometry, null, similarity,
                    layout.progressionRows, layout.progressionRanks,
                    layout.fieldOffsetY, layout.frameShiftY, layout.geometryConfidence,
                    null, null);
        }

        static Result error(String error) {
            return new Result(null, null, error, 0.0, 0, 0, 0f, 0f, 0f,
                    null, null);
        }

        static Result error(String error, int[] partialRanks, boolean[] recognizedCells,
                            double similarity, MergeMadnessLayout.Result layout) {
            return new Result(null, layout.geometry, error, similarity,
                    layout.progressionRows, layout.progressionRanks,
                    layout.fieldOffsetY, layout.frameShiftY, layout.geometryConfidence,
                    partialRanks, recognizedCells);
        }
    }

    static final class TileMatch {
        final int rank;
        final double score;
        final double margin;
        final boolean customSample;

        TileMatch(int rank, double score, double margin) {
            this(rank, score, margin, false);
        }

        TileMatch(int rank, double score, double margin, boolean customSample) {
            this.rank = rank;
            this.score = score;
            this.margin = margin;
            this.customSample = customSample;
        }
    }
}
