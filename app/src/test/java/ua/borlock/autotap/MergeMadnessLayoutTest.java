package ua.borlock.autotap;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class MergeMadnessLayoutTest {
    @Test public void findsTheGridAtDifferentVerticalOffsetsAndReadsThreeChainRows() {
        int width = 576;
        int height = 1280;
        MergeMadnessLayout.Result first = MergeMadnessLayout.locate(
                syntheticFrame(width, height, 350, 3), width, height, null);
        assertNotNull(first);
        MergeMadnessLayout.Result shifted = MergeMadnessLayout.locate(
                syntheticFrame(width, height, 486, 3), width, height, first.geometry);
        assertNotNull(shifted);
        System.out.println("layout y=" + first.geometry.firstCenterY + "->"
                + shifted.geometry.firstCenterY + " step=" + shifted.geometry.cellStepPx
                + " rows=" + shifted.progressionRows
                + " ranks=" + shifted.progressionRanks + " slots="
                + java.util.Arrays.toString(shifted.progressionSlotsByRow));
        assertTrue("Поле не відстежило вертикальний зсув",
                shifted.geometry.firstCenterY - first.geometry.firstCenterY >= 120f);
        assertTrue(shifted.progressionRows >= 3);
        assertTrue(shifted.progressionRanks >= 15);
        assertTrue(shifted.progressionRanks >= 15 && shifted.progressionRanks <= 17);
    }

    @Test public void capsFourVisibleRowsAtTheSeventeenKnownRanks() {
        int width = 576;
        int height = 1280;
        MergeMadnessLayout.Result result = MergeMadnessLayout.locate(
                syntheticFrame(width, height, 350, 4), width, height, null);
        assertNotNull(result);
        assertEquals(4, result.progressionRows);
        assertEquals(17, result.progressionRanks);
    }

    @Test public void rejectsAFalseGridOutsideTheGameplayBand() {
        int width = 576;
        int height = 1280;
        MergeMadnessLayout.Result result = MergeMadnessLayout.locate(
                syntheticFrame(width, height, 280, 0), width, height, null);
        assertNull(result);
    }

    @Test public void rejectsGridHiddenByAnOpaqueOverlay() {
        int width = 576;
        int height = 1280;
        int[] pixels = syntheticFrame(width, height, 350, 3);
        int top = 350 + 120 + 20;
        int bottom = 350 + 3 * 120 - 20;
        for (int y = top; y < bottom; y++) {
            java.util.Arrays.fill(pixels, y * width, (y + 1) * width, 0xFF392D30);
        }

        assertNull(MergeMadnessLayout.locate(pixels, width, height, null));
    }

    @Test public void waitsForGeometryToSettleBetweenFrames() {
        BoardVision.Geometry first = new BoardVision.Geometry(100f, 500f, 120f, 120f);
        BoardVision.Geometry stable = new BoardVision.Geometry(101f, 502f, 121f, 120f);
        BoardVision.Geometry moving = new BoardVision.Geometry(101f, 510f, 121f, 120f);
        assertTrue(MergeMadnessLayout.isStable(first, stable));
        assertTrue(!MergeMadnessLayout.isStable(first, moving));
    }

    @Test public void staleGeometryStillFallsBackToAFullScreenSearch() {
        int width = 576;
        int height = 1280;
        int[] pixels = syntheticFrame(width, height, 486, 3);
        BoardVision.Geometry stale = new BoardVision.Geometry(108f, 280f, 120f, 120f);
        MergeMadnessLayout.Result result = MergeMadnessLayout.locate(
                pixels, width, height, stale);

        assertNotNull(result);
        assertEquals(540f, result.geometry.firstCenterY, 6f);
    }

    @Test public void nearbyButIncorrectAnchorFallsBackToTheActualGridCenter() {
        int width = 576;
        int height = 1280;
        int[] pixels = syntheticFrame(width, height, 442, 3);
        BoardVision.Geometry stale = new BoardVision.Geometry(108f, 480f, 120f, 120f);

        MergeMadnessLayout.Result result = MergeMadnessLayout.locate(
                pixels, width, height, stale);

        assertNotNull(result);
        assertEquals(496f, result.geometry.firstCenterY, 6f);
    }

    @Test public void staleManualCalibrationAlsoFallsBackToTheActualGridCenter() {
        int width = 576;
        int height = 1280;
        int[] pixels = syntheticFrame(width, height, 442, 3);
        BoardVision.Geometry stale = new BoardVision.Geometry(108f, 480f, 120f, 120f);

        MergeMadnessLayout.Result result = MergeMadnessLayout.locate(
                pixels, width, height, stale, stale);

        assertNotNull(result);
        assertEquals(496f, result.geometry.firstCenterY, 6f);
    }

    @Test public void misalignedManualXUsesTheFixedDeviceSearchInsteadOfStaleY() {
        int width = 576;
        int height = 1280;
        BoardVision.Geometry misaligned = new BoardVision.Geometry(132f, 480f, 120f, 120f);

        int[] pixels = syntheticFrame(width, height, 442, 3);
        MergeMadnessLayout.Result global = MergeMadnessLayout.locate(
                pixels, width, height, null);
        MergeMadnessLayout.Result result = MergeMadnessLayout.locate(
                pixels, width, height, null, misaligned);

        assertNotNull(result);
        assertNotNull(global);
        assertEquals(496f, result.geometry.firstCenterY, 6f);
        assertEquals(global.geometry.firstCenterX, result.geometry.firstCenterX, 0.001f);
        assertEquals(global.geometry.firstCenterY, result.geometry.firstCenterY, 0.1f);
        assertEquals(global.geometryConfidence, result.geometryConfidence, 0.001f);
    }

    @Test public void oldAnchorAndCalibrationCannotChangeTheFixedCellScale() {
        int width = 576;
        int height = 1280;
        int[] pixels = syntheticFrame(width, height, 442, 3);
        MergeMadnessLayout.Result expected = MergeMadnessLayout.locate(
                pixels, width, height, null);
        BoardVision.Geometry staleAnchor = new BoardVision.Geometry(132f, 481f, 140f, 140f);
        BoardVision.Geometry staleCalibration = new BoardVision.Geometry(132f, 481f, 140f, 140f);

        MergeMadnessLayout.Result result = MergeMadnessLayout.locate(
                pixels, width, height, staleAnchor, staleCalibration);

        assertNotNull(result);
        assertEquals(120f, result.geometry.cellStepPx, 1f);
        assertEquals(expected.geometry.firstCenterY, result.geometry.firstCenterY, 0.1f);
        assertEquals(expected.geometry.firstCenterX, result.geometry.firstCenterX, 0.1f);
    }

    @Test public void manualGeometryGuidesSearchToTheCalibratedColumns() {
        int width = 576;
        int height = 1280;
        BoardVision.Geometry calibrated = new BoardVision.Geometry(104f, 404f, 120f, 120f);
        MergeMadnessLayout.Result result = MergeMadnessLayout.locate(
                syntheticFrame(width, height, 350, 3), width, height, null, calibrated);

        assertNotNull(result);
        assertEquals(104f, result.geometry.firstCenterX, 5f);
        assertEquals(404f, result.geometry.firstCenterY, 6f);
    }

    @Test public void manualGeometryRemainsAvailableWhenGridLinesAreNotDetected() {
        int width = 576;
        int height = 1280;
        int[] pixels = syntheticFrame(width, height, 350, 3);
        for (int y = 350; y < 830; y++) {
            java.util.Arrays.fill(pixels, y * width + 38, y * width + 530, 0xFF303030);
        }
        BoardVision.Geometry calibrated = new BoardVision.Geometry(104f, 404f, 120f, 120f);

        MergeMadnessLayout.Result result = MergeMadnessLayout.locate(
                pixels, width, height, null, calibrated);

        assertNotNull(result);
        assertEquals(0.55f, result.geometryConfidence, 0.001f);
        assertEquals(404f, result.geometry.firstCenterY, 0.001f);
        assertTrue(result.progressionRanks > 0);
    }

    @Test public void rejectsFarOffManualGeometryWhenGridLinesAreHidden() {
        int width = 576;
        int height = 1280;
        int[] pixels = syntheticFrame(width, height, 350, 3);
        for (int y = 350; y < 830; y++) {
            java.util.Arrays.fill(pixels, y * width + 38, y * width + 530, 0xFF303030);
        }
        BoardVision.Geometry misaligned = new BoardVision.Geometry(132f, 404f, 120f, 120f);

        assertNull(MergeMadnessLayout.locate(pixels, width, height, null, misaligned));
    }

    private int[] syntheticFrame(int width, int height, int gridTop, int chainRows) {
        int[] pixels = new int[width * height];
        java.util.Arrays.fill(pixels, 0xFF303030);
        int cellSize = 120;
        int gridLeft = 44;
        for (int boundary = 0; boundary <= MergeMadnessBoard.SIDE; boundary++) {
            int y = gridTop + boundary * cellSize;
            for (int offset = -3; offset <= 3; offset++) {
                int row = y + offset;
                if (row < 0 || row >= height) continue;
                for (int x = gridLeft; x <= gridLeft + 4 * cellSize; x++) {
                    pixels[row * width + x] = 0xFFE0D0C0;
                }
            }
            int x = gridLeft + boundary * cellSize;
            for (int offset = -3; offset <= 3; offset++) {
                int column = x + offset;
                if (column < 0 || column >= width) continue;
                for (int yGrid = gridTop; yGrid <= gridTop + 4 * cellSize; yGrid++) {
                    pixels[yGrid * width + column] = 0xFFE0D0C0;
                }
            }
        }
        if (chainRows > 0) {
            float firstCenterX = gridLeft + 68f - 6f;
            float firstCenterY = gridTop + 0.45f * cellSize + 3 * cellSize + 1.70f * cellSize;
            for (int row = 0; row < chainRows; row++) {
                for (int slot = 0; slot < 5; slot++) {
                    int centerX = Math.round(firstCenterX + slot * 0.66f * cellSize);
                    int centerY = Math.round(firstCenterY + row * 0.54f * cellSize);
                    int color = row == 2 ? 0xFF050505 : 0xFFE2B34F;
                    fillRect(pixels, width, height, centerX - 20, centerY - 12,
                            centerX + 20, centerY + 12, color);
                }
            }
        }
        return pixels;
    }

    private void fillRect(int[] pixels, int width, int height,
                          int left, int top, int right, int bottom, int color) {
        for (int y = Math.max(0, top); y <= Math.min(height - 1, bottom); y++) {
            for (int x = Math.max(0, left); x <= Math.min(width - 1, right); x++) {
                pixels[y * width + x] = color;
            }
        }
    }
}
