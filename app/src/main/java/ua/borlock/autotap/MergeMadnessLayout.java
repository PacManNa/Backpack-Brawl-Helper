package ua.borlock.autotap;

import java.util.ArrayList;
import java.util.Comparator;

/** Pixel-only geometry and progression-chain locator for Merge Madness screenshots. */
final class MergeMadnessLayout {
    private static final float FIRST_CENTER_X_RATIO = 0.1875f;
    private static final float FIRST_CENTER_Y_RATIO = 0.3875f;
    private static final float CELL_STEP_RATIO = 0.2083f;
    private static final float CENTER_FROM_GRID_TOP_RATIO = 0.45f;
    private static final float MIN_HORIZONTAL_SCORE = 35f;
    private static final float MIN_HORIZONTAL_EDGE_SCORE = 3f;
    private static final float MIN_VERTICAL_SCORE = 20f;
    private static final float MIN_COLUMN_SEPARATOR_SCORE = 12f;
    private static final int MIN_SEPARATORS_PER_ROW = 2;
    private static final float MIN_FIELD_CENTER_Y_RATIO = 0.30f;
    private static final float MAX_FIELD_CENTER_Y_RATIO = 0.45f;
    // This bot is used on one fixed device and the 4×4 cells remain square.
    // Searching nearby scales let a strong border elsewhere in the frame win and
    // caused row-to-row drift (for example 234 px instead of the real 225 px).
    private static final float[] CELL_SIZE_SCALES = {1.0f};
    private static final float[] VERTICAL_EDGE_OFFSETS = {-0.567f, 0.5f, 1.5f, 2.5f, 3.567f};
    private static final int MAX_TOP_CANDIDATES = 32;
    private static final int PROGRESSION_SLOTS_PER_ROW = 5;
    private static final int MAX_PROGRESSION_ROWS = 4;
    private static final float PROGRESSION_ROW_SPACING = 0.54f;
    private static final float PROGRESSION_SLOT_STEP_RATIO = 0.66f;
    private static final float PROGRESSION_SLOT_WIDTH_RATIO = 0.46f;
    private static final float PROGRESSION_SLOT_HEIGHT_RATIO = 0.28f;

    private MergeMadnessLayout() { }

    static Result locate(int[] pixels, int width, int height,
                         BoardVision.Geometry previousGeometry) {
        return locate(pixels, width, height, previousGeometry, null);
    }

    static Result locate(int[] pixels, int width, int height,
                         BoardVision.Geometry previousGeometry,
                         BoardVision.Geometry calibratedGeometry) {
        if (pixels == null || width <= 0 || height <= 0 || pixels.length < width * height) {
            return null;
        }
        // This bot runs on one fixed phone. Keep the same fixed X/scale model as the
        // original flower bot; only the board's vertical position can move in-game.
        float nominalStep = width * CELL_STEP_RATIO;
        if (nominalStep < 8f || height < nominalStep * MergeMadnessBoard.SIDE) return null;
        float[] horizontalEdges = horizontalEdgeProfile(pixels, width, height);
        // Re-scan the full allowed vertical band on every frame. Previously an old
        // anchor/calibration could restrict this pass to a stale Y or cell step.
        Candidate best = search(pixels, width, height, horizontalEdges, nominalStep,
                3, height - Math.round(nominalStep * 4.32f),
                Float.NaN);
        if (best == null && calibratedGeometry != null) {
            // A manually drawn rectangle remains a fallback for weak grid contrast,
            // but it cannot replace this phone's known horizontal position or scale.
            float defaultCenterX = width * FIRST_CENTER_X_RATIO;
            if (Math.abs(calibratedGeometry.firstCenterX - defaultCenterX)
                    > nominalStep * 0.12f) return null;
            if (Math.abs(calibratedGeometry.cellStepPx - nominalStep) > nominalStep * 0.12f
                    || Math.abs(calibratedGeometry.cellStepYpx - nominalStep)
                    > nominalStep * 0.12f) return null;
            BoardVision.Geometry fallback = new BoardVision.Geometry(defaultCenterX,
                    calibratedGeometry.firstCenterY, nominalStep, nominalStep);
            return resultForGeometry(pixels, width, height, fallback,
                    previousGeometry, 0.55f);
        }
        if (best == null || best.confidence < 1.0f) return null;

        BoardVision.Geometry geometry = new BoardVision.Geometry(best.firstCenterX,
                best.top + best.step * CENTER_FROM_GRID_TOP_RATIO, best.step, best.step);
        return resultForGeometry(pixels, width, height, geometry,
                previousGeometry, Math.min(1.0f, best.confidence));
    }

    private static Result resultForGeometry(int[] pixels, int width, int height,
                                            BoardVision.Geometry geometry,
                                            BoardVision.Geometry previousGeometry,
                                            float confidence) {
        if (geometry.firstCenterY < height * MIN_FIELD_CENTER_Y_RATIO
                || geometry.firstCenterY > height * MAX_FIELD_CENTER_Y_RATIO) return null;
        Progression progression = findProgression(pixels, width, height, geometry);
        float baselineY = height * FIRST_CENTER_Y_RATIO;
        float frameShift = previousGeometry == null ? 0f
                : geometry.firstCenterY - previousGeometry.firstCenterY;
        return new Result(geometry, progression.rowCenters, progression.slotsByRow,
                progression.visibleRows, progression.visibleRanks,
                geometry.firstCenterY - baselineY, frameShift, confidence);
    }

    static boolean isStable(BoardVision.Geometry first, BoardVision.Geometry second) {
        if (first == null || second == null) return false;
        return Math.abs(first.firstCenterX - second.firstCenterX) <= 2.5f
                && Math.abs(first.firstCenterY - second.firstCenterY) <= 2.5f
                && Math.abs(first.cellStepPx - second.cellStepPx) <= 1.5f
                && Math.abs(first.cellStepYpx - second.cellStepYpx) <= 1.5f;
    }

    private static Candidate search(int[] pixels, int width, int height,
                                    float[] horizontalEdges, float nominalStep,
                                    int requestedMinimumTop, int requestedMaximumTop,
                                    float previousCenterY) {
        ArrayList<Candidate> candidates = new ArrayList<>();
        for (float scale : CELL_SIZE_SCALES) {
            float step = nominalStep * scale;
            int minimumGameplayTop = (int) Math.ceil(height * MIN_FIELD_CENTER_Y_RATIO
                    - step * CENTER_FROM_GRID_TOP_RATIO);
            int maximumGameplayTop = (int) Math.floor(height * MAX_FIELD_CENTER_Y_RATIO
                    - step * CENTER_FROM_GRID_TOP_RATIO);
            int minimumTop = Math.max(3, Math.max(requestedMinimumTop, minimumGameplayTop));
            int maximumTop = Math.min(height - Math.round(step * MergeMadnessBoard.SIDE) - 3,
                    Math.min(requestedMaximumTop, maximumGameplayTop));
            if (minimumTop > maximumTop) continue;
            for (int top = minimumTop; top <= maximumTop; top++) {
                float horizontalScore = horizontalGridScore(horizontalEdges, top, step);
                if (horizontalScore < MIN_HORIZONTAL_SCORE) continue;
                offerCandidate(candidates, new Candidate(top, step, horizontalScore));
            }
        }
        if (candidates.isEmpty()) return null;

        candidates.sort(Comparator.comparingDouble((Candidate candidate) -> candidate.horizontalScore)
                .reversed());
        Candidate best = null;
        int checked = 0;
        float fixedFirstCenterX = width * FIRST_CENTER_X_RATIO;
        for (Candidate candidate : candidates) {
            if (checked++ >= MAX_TOP_CANDIDATES) break;
            float[] verticalEdges = verticalEdgeProfile(pixels, width, height,
                    candidate.top, candidate.step);
            BoardVision.Geometry geometry = new BoardVision.Geometry(fixedFirstCenterX,
                    candidate.top + candidate.step * CENTER_FROM_GRID_TOP_RATIO,
                    candidate.step, candidate.step);
            float scoreCenterX = fixedFirstCenterX + candidate.step * 0.029f;
            float verticalScore = verticalGridScore(verticalEdges,
                    Math.round(scoreCenterX), candidate.step);
            if (verticalScore < MIN_VERTICAL_SCORE
                    || !hasColumnSeparators(pixels, width, height, geometry)) continue;

            candidate.firstCenterX = fixedFirstCenterX;
            candidate.verticalScore = verticalScore;
            candidate.confidence = Math.min(candidate.horizontalScore / MIN_HORIZONTAL_SCORE,
                    verticalScore / MIN_VERTICAL_SCORE);
            float distancePenalty = 1.0f;
            if (Float.isFinite(previousCenterY)) {
                float displacement = (candidate.top
                        + candidate.step * CENTER_FROM_GRID_TOP_RATIO - previousCenterY)
                        / nominalStep;
                distancePenalty += 3.0f * displacement * displacement;
            }
            candidate.quality = (candidate.horizontalScore / MIN_HORIZONTAL_SCORE)
                    * (verticalScore / MIN_VERTICAL_SCORE) / distancePenalty;
            if (best == null || candidate.quality > best.quality) best = candidate;
        }
        return best;
    }

    private static boolean hasColumnSeparators(int[] pixels, int width, int height,
                                               BoardVision.Geometry geometry) {
        int sampleRadius = Math.max(4, Math.round(geometry.cellStepPx * 0.32f));
        int xRadius = Math.max(3, Math.round(geometry.cellStepPx * 0.06f));
        for (int row = 0; row < MergeMadnessBoard.SIDE; row++) {
            int centerY = Math.round(geometry.centerY(row));
            int visibleSeparators = 0;
            for (int separator = 1; separator < MergeMadnessBoard.SIDE; separator++) {
                int boundaryX = Math.round(geometry.centerX(0)
                        + (separator - 0.5f) * geometry.cellStepPx);
                float edgeTotal = 0f;
                int samples = 0;
                for (int y = centerY - sampleRadius; y <= centerY + sampleRadius; y += 3) {
                    if (y < 1 || y >= height - 1) continue;
                    float strongest = 0f;
                    for (int offset = -xRadius; offset <= xRadius; offset++) {
                        int x = boundaryX + offset;
                        if (x < 1 || x >= width - 1) continue;
                        float edge = Math.abs(pixelLuma(pixels[y * width + x - 1])
                                - pixelLuma(pixels[y * width + x + 1]));
                        strongest = Math.max(strongest, edge);
                    }
                    edgeTotal += strongest;
                    samples++;
                }
                if (samples > 0 && edgeTotal / samples >= MIN_COLUMN_SEPARATOR_SCORE) {
                    visibleSeparators++;
                }
            }
            if (visibleSeparators < MIN_SEPARATORS_PER_ROW) return false;
        }
        return true;
    }

    private static void offerCandidate(ArrayList<Candidate> candidates, Candidate offered) {
        for (int index = 0; index < candidates.size(); index++) {
            Candidate existing = candidates.get(index);
            if (Math.abs(existing.top - offered.top) <= 2
                    && Math.abs(existing.step - offered.step) <= 1.5f) {
                if (offered.horizontalScore > existing.horizontalScore) {
                    candidates.set(index, offered);
                }
                return;
            }
        }
        candidates.add(offered);
    }

    private static float horizontalGridScore(float[] profile, int top, float step) {
        float score = 0f;
        for (int row = 0; row <= MergeMadnessBoard.SIDE; row++) {
            float edge = strongestNearby(profile, Math.round(top + row * step), 2);
            if (edge < MIN_HORIZONTAL_EDGE_SCORE) return 0f;
            score += edge;
        }
        return score;
    }

    private static float[] horizontalEdgeProfile(int[] pixels, int width, int height) {
        int left = clamp(Math.round(width * 0.25f), 1, width - 2);
        int right = clamp(Math.round(width * 0.94f), left + 1, width - 1);
        float[] edges = new float[height];
        for (int y = 1; y < height - 1; y++) {
            float total = 0f;
            int count = 0;
            for (int x = left; x < right; x++) {
                total += Math.abs(pixelLuma(pixels[(y - 1) * width + x])
                        - pixelLuma(pixels[(y + 1) * width + x]));
                count++;
            }
            edges[y] = total / Math.max(1, count);
        }
        return smoothProfile(edges);
    }

    private static float[] verticalEdgeProfile(int[] pixels, int width, int height,
                                               int gridTop, float step) {
        int top = clamp(gridTop - 8, 1, height - 2);
        int bottom = clamp(Math.round(gridTop + step * 4f + 12f), top + 1, height - 1);
        float[] edges = new float[width];
        for (int x = 1; x < width - 1; x++) {
            float total = 0f;
            int count = 0;
            for (int y = top; y < bottom; y++) {
                total += Math.abs(pixelLuma(pixels[y * width + x - 1])
                        - pixelLuma(pixels[y * width + x + 1]));
                count++;
            }
            edges[x] = total / Math.max(1, count);
        }
        return smoothProfile(edges);
    }

    private static float verticalGridScore(float[] profile, int firstCenterX, float step) {
        float score = 0f;
        for (float offset : VERTICAL_EDGE_OFFSETS) {
            score += strongestNearby(profile, Math.round(firstCenterX + offset * step), 2);
        }
        return score;
    }

    private static float[] smoothProfile(float[] values) {
        float[] result = new float[values.length];
        for (int index = 2; index < values.length - 2; index++) {
            result[index] = (values[index - 2] + values[index - 1] + values[index]
                    + values[index + 1] + values[index + 2]) / 5f;
        }
        return result;
    }

    private static float strongestNearby(float[] profile, int center, int radius) {
        float best = 0f;
        for (int offset = -radius; offset <= radius; offset++) {
            int index = center + offset;
            if (index >= 0 && index < profile.length) best = Math.max(best, profile[index]);
        }
        return best;
    }

    private static Progression findProgression(int[] pixels, int width, int height,
                                               BoardVision.Geometry geometry) {
        float[] centers = new float[MAX_PROGRESSION_ROWS];
        int[] slotsByRow = new int[MAX_PROGRESSION_ROWS];
        float lastCellCenterY = geometry.centerY(MergeMadnessBoard.SIDE - 1);
        // Let the chain begin closer to the grid when the scrolling play area moves it up.
        int minimumY = Math.max(0, Math.round(lastCellCenterY + geometry.cellStepYpx * 0.75f));
        int maximumY = Math.min(height - 1,
                Math.round(height - geometry.cellStepYpx * PROGRESSION_SLOT_HEIGHT_RATIO * 0.52f));
        ArrayList<RowCandidate> peaks = progressionRowPeaks(
                pixels, width, height, geometry, minimumY, maximumY);
        peaks.sort(Comparator.comparingDouble(candidate -> candidate.centerY));

        RowCandidate first = null;
        float earliestFirstY = lastCellCenterY + geometry.cellStepYpx * 0.80f;
        float latestFirstY = lastCellCenterY + geometry.cellStepYpx * 2.25f;
        for (RowCandidate candidate : peaks) {
            if (candidate.centerY < earliestFirstY || candidate.centerY > latestFirstY) continue;
            if (candidate.score < 1.75f || candidate.visibleSlots < 2) continue;
            first = candidate;
            break;
        }
        if (first != null) {
            ArrayList<RowCandidate> selected = new ArrayList<>();
            selected.add(first);
            while (selected.size() < MAX_PROGRESSION_ROWS) {
                RowCandidate previous = selected.get(selected.size() - 1);
                float expectedY = previous.centerY
                        + geometry.cellStepYpx * PROGRESSION_ROW_SPACING;
                RowCandidate next = strongestRow(pixels, width, height, geometry,
                        expectedY);
                if (next == null) break;
                selected.add(next);
            }
            selected.sort(Comparator.comparingDouble(candidate -> candidate.centerY));
            for (int row = 0; row < selected.size(); row++) {
                centers[row] = selected.get(row).centerY;
                slotsByRow[row] = selected.get(row).visibleSlots;
            }
        }

        int rows = 0;
        int ranks = 0;
        for (int row = 0; row < MAX_PROGRESSION_ROWS; row++) {
            if (slotsByRow[row] == 0) break;
            rows++;
            ranks += slotsByRow[row];
        }
        return new Progression(centers, slotsByRow, rows, Math.min(17, ranks));
    }

    private static RowCandidate strongestRow(int[] pixels, int width, int height,
                                             BoardVision.Geometry geometry,
                                             float expectedY) {
        RowCandidate best = null;
        float tolerance = geometry.cellStepYpx * 0.12f;
        float minimumY = Math.max(0f, expectedY - tolerance);
        float maximumY = Math.min(height - 1f, expectedY + tolerance);
        for (float centerY = minimumY; centerY <= maximumY; centerY += 2f) {
            SlotRow sample = sampleProgressionRow(pixels, width, height, geometry, centerY);
            boolean convincing = sample.visibleCount >= 2 && sample.score >= 1.50f;
            boolean singleSilhouette = sample.visibleCount == 1 && sample.score >= 0.90f;
            if (!convincing && !singleSilhouette) continue;
            float distance = Math.abs(centerY - expectedY);
            float bestDistance = best == null ? Float.MAX_VALUE
                    : Math.abs(best.centerY - expectedY);
            if (best == null || distance < bestDistance
                    || (distance == bestDistance && sample.score > best.score)) {
                best = new RowCandidate(centerY, sample.score, sample.visibleCount);
            }
        }
        return best;
    }

    private static ArrayList<RowCandidate> progressionRowPeaks(int[] pixels, int width,
                                                                int height,
                                                                BoardVision.Geometry geometry,
                                                                int minimumY, int maximumY) {
        ArrayList<RowCandidate> candidates = new ArrayList<>();
        if (minimumY >= maximumY) return candidates;
        int sampleStepPx = 3;
        int sampleCount = (maximumY - minimumY) / sampleStepPx + 1;
        SlotRow[] samples = new SlotRow[sampleCount];
        for (int index = 0; index < sampleCount; index++) {
            int y = minimumY + index * sampleStepPx;
            samples[index] = sampleProgressionRow(pixels, width, height, geometry, y);
        }

        ArrayList<RowCandidate> rawPeaks = new ArrayList<>();
        for (int index = 1; index < sampleCount - 1; index++) {
            SlotRow current = samples[index];
            if (current.score < 0.65f || current.score < samples[index - 1].score
                    || current.score < samples[index + 1].score) continue;
            rawPeaks.add(new RowCandidate(minimumY + index * sampleStepPx,
                    current.score, current.visibleCount));
        }
        rawPeaks.sort(Comparator.comparingDouble(candidate -> candidate.centerY));

        float groupingDistance = geometry.cellStepYpx * 0.28f;
        for (RowCandidate candidate : rawPeaks) {
            RowCandidate group = candidates.isEmpty() ? null : candidates.get(candidates.size() - 1);
            if (group == null || candidate.centerY - group.lastSampleY > groupingDistance) {
                candidates.add(candidate);
            } else {
                group.add(candidate);
            }
        }
        return candidates;
    }

    private static SlotRow sampleProgressionRow(int[] pixels, int width, int height,
                                                BoardVision.Geometry geometry, float centerY) {
        float firstCenterX = geometry.centerX(0) - geometry.cellStepPx * 0.05f;
        float sampleWidth = geometry.cellStepPx * PROGRESSION_SLOT_WIDTH_RATIO;
        float sampleHeight = geometry.cellStepYpx * PROGRESSION_SLOT_HEIGHT_RATIO;
        float score = 0f;
        int visibleCount = 0;
        boolean gap = false;
        for (int slot = 0; slot < PROGRESSION_SLOTS_PER_ROW; slot++) {
            float centerX = firstCenterX + slot * geometry.cellStepPx * PROGRESSION_SLOT_STEP_RATIO;
            float strength = progressionSlotStrength(pixels, width, height,
                    centerX, centerY, sampleWidth, sampleHeight);
            if (strength <= 0f) {
                gap = true;
                continue;
            }
            if (!gap) visibleCount++;
            score += strength;
        }
        return new SlotRow(score, visibleCount);
    }

    private static float progressionSlotStrength(int[] pixels, int imageWidth, int imageHeight,
                                                 float centerX, float centerY,
                                                 float sampleWidth, float sampleHeight) {
        float left = centerX - sampleWidth * 0.5f;
        float top = centerY - sampleHeight * 0.5f;
        float right = centerX + sampleWidth * 0.5f;
        float bottom = centerY + sampleHeight * 0.5f;
        float fullArea = (right - left) * (bottom - top);
        float visibleWidth = Math.max(0f, Math.min(imageWidth, right) - Math.max(0f, left));
        float visibleHeight = Math.max(0f, Math.min(imageHeight, bottom) - Math.max(0f, top));
        if (visibleWidth * visibleHeight < fullArea * 0.48f) return 0f;

        int xStart = Math.max(0, Math.round(left));
        int xEnd = Math.min(imageWidth - 1, Math.round(right));
        int yStart = Math.max(0, Math.round(top));
        int yEnd = Math.min(imageHeight - 1, Math.round(bottom));
        int sampleCount = 0;
        int brightCount = 0;
        int colorfulCount = 0;
        int darkCount = 0;
        int[] histogram = new int[256];
        for (int y = yStart; y <= yEnd; y += 2) {
            for (int x = xStart; x <= xEnd; x += 2) {
                int color = pixels[y * imageWidth + x];
                int red = (color >>> 16) & 0xFF;
                int green = (color >>> 8) & 0xFF;
                int blue = color & 0xFF;
                int luma = Math.round(red * 0.299f + green * 0.587f + blue * 0.114f);
                histogram[clamp(luma, 0, 255)]++;
                if (luma >= 54) brightCount++;
                if (Math.max(red, Math.max(green, blue))
                        - Math.min(red, Math.min(green, blue)) >= 28 && luma >= 20) colorfulCount++;
                if (luma <= 16) darkCount++;
                sampleCount++;
            }
        }
        if (sampleCount == 0) return 0f;
        int p90Index = Math.round(sampleCount * 0.90f);
        int accumulated = 0;
        int p90 = 0;
        for (; p90 < histogram.length; p90++) {
            accumulated += histogram[p90];
            if (accumulated > p90Index) break;
        }
        float brightRatio = brightCount / (float) sampleCount;
        float colorfulRatio = colorfulCount / (float) sampleCount;
        float darkRatio = darkCount / (float) sampleCount;
        if (p90 >= 54) return Math.min(1.25f, 0.65f + brightRatio);
        if (p90 >= 40 && colorfulRatio >= 0.045f) {
            return Math.min(1.10f, 0.60f + colorfulRatio);
        }
        if (darkRatio >= 0.05f) return Math.min(1.05f, 0.75f + darkRatio);
        return 0f;
    }

    private static float pixelLuma(int color) {
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        return red * 0.299f + green * 0.587f + blue * 0.114f;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static final class Result {
        final BoardVision.Geometry geometry;
        final float[] progressionRowCentersY;
        final int[] progressionSlotsByRow;
        final int progressionRows;
        final int progressionRanks;
        final float fieldOffsetY;
        final float frameShiftY;
        final float geometryConfidence;

        Result(BoardVision.Geometry geometry, float[] progressionRowCentersY,
               int[] progressionSlotsByRow, int progressionRows, int progressionRanks,
               float fieldOffsetY, float frameShiftY, float geometryConfidence) {
            this.geometry = geometry;
            this.progressionRowCentersY = progressionRowCentersY;
            this.progressionSlotsByRow = progressionSlotsByRow;
            this.progressionRows = progressionRows;
            this.progressionRanks = progressionRanks;
            this.fieldOffsetY = fieldOffsetY;
            this.frameShiftY = frameShiftY;
            this.geometryConfidence = geometryConfidence;
        }
    }

    private static final class Candidate {
        final int top;
        final float step;
        final float horizontalScore;
        float verticalScore;
        float firstCenterX;
        float confidence;
        float quality;

        Candidate(int top, float step, float horizontalScore) {
            this.top = top;
            this.step = step;
            this.horizontalScore = horizontalScore;
        }
    }

    private static final class SlotRow {
        final float score;
        final int visibleCount;

        SlotRow(float score, int visibleCount) {
            this.score = score;
            this.visibleCount = visibleCount;
        }
    }

    private static final class RowCandidate {
        float centerY;
        float score;
        float lastSampleY;
        int visibleSlots;
        private int sampleCount = 1;

        RowCandidate(float centerY, float score, int visibleSlots) {
            this.centerY = centerY;
            this.score = score;
            this.visibleSlots = visibleSlots;
            this.lastSampleY = centerY;
        }

        void add(RowCandidate sample) {
            centerY = (centerY * sampleCount + sample.centerY) / (sampleCount + 1);
            sampleCount++;
            score = Math.max(score, sample.score);
            visibleSlots = Math.max(visibleSlots, sample.visibleSlots);
            lastSampleY = sample.centerY;
        }
    }

    private static final class Progression {
        final float[] rowCenters;
        final int[] slotsByRow;
        final int visibleRows;
        final int visibleRanks;

        Progression(float[] rowCenters, int[] slotsByRow, int visibleRows, int visibleRanks) {
            this.rowCenters = rowCenters;
            this.slotsByRow = slotsByRow;
            this.visibleRows = visibleRows;
            this.visibleRanks = visibleRanks;
        }
    }
}
