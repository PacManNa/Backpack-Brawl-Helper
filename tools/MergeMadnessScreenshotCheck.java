package ua.borlock.autotap;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.awt.Graphics2D;
import javax.imageio.ImageIO;

/** Host-JVM regression check for the production pixel-only board locator. */
public final class MergeMadnessScreenshotCheck {
    private static final int[] KNOWN_RAT_AND_FISH_BOARD = {
            0, 0, 0, 0,
            0, 1, 0, 0,
            1, 1, 0, 0,
            3, 3, 3, 1
    };
    private static final int[] SINGLE_RAT_AND_CHAIN_BOARD = {
            0, 0, 0, 0,
            1, 0, 0, 0,
            2, 0, 1, 0,
            4, 3, 1, 0
    };

    public static void main(String[] args) throws Exception {
        File fixtures = new File(args[0]);
        File[] files = fixtures.listFiles((directory, name) ->
                name.matches("10000111[0-9]+\\.jpg"));
        if (files == null || files.length < 10) {
            throw new AssertionError("The Merge Madness screenshot corpus is incomplete.");
        }
        Arrays.sort(files, (first, second) -> first.getName().compareTo(second.getName()));
        File raw = new File(args[1]);
        MergeMadnessVision.PixelImage[] highRankSamples = new MergeMadnessVision.PixelImage[6];
        for (int index = 0; index < highRankSamples.length; index++) {
            highRankSamples[index] = readPixelImage(new File(raw,
                    "merge_madness_rank" + (index + 5) + "_sample.jpg"));
        }
        MergeMadnessVision screenshotRecognizer = MergeMadnessVision.forScreenshotCheck(
                readPixelImage(new File(raw, "merge_madness_guide.jpg")),
                readPixelImage(new File(raw, "merge_madness_gameplay_reference.jpg")),
                readPixelImage(new File(raw, "merge_madness_gameplay_sample.jpg")),
                highRankSamples);

        float minimumY = Float.MAX_VALUE;
        float maximumY = -Float.MAX_VALUE;
        int maximumRows = 0;
        int fullScreenCount = 0;
        BoardVision.Geometry anchor = null;
        for (File file : files) {
            BufferedImage image = ImageIO.read(file);
            if (image == null) throw new AssertionError("Unreadable screenshot: " + file);
            int width = image.getWidth();
            int height = image.getHeight();
            int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
            MergeMadnessLayout.Result result = MergeMadnessLayout.locate(
                    pixels, width, height, anchor);
            BoardVision.Geometry staleOnDeviceState = file.getName().equals("1000011174.jpg")
                    ? new BoardVision.Geometry(132f, 481f, 140f, 140f) : null;
            MergeMadnessVision.Result recognizedScreenshot = screenshotRecognizer.recognizePixels(
                    pixels, width, height, staleOnDeviceState, staleOnDeviceState);
            System.out.println(file.getName() + " tileRead="
                    + (recognizedScreenshot.board == null
                    ? "REJECTED: " + recognizedScreenshot.error
                    : Arrays.toString(recognizedScreenshot.board.copyRanks())));

            if (file.getName().equals("1000011156.jpg")) {
                checkScaledFrameWithStaleAnchor(image);
            }
            if (file.getName().equals("1000011168.jpg")
                    || file.getName().equals("1000011170.jpg")
                    || file.getName().equals("1000011172.jpg")
                    || file.getName().equals("1000011174.jpg")) {
                checkScreenshotWithNearbyStaleAnchor(file.getName(), image);
            }

            boolean partialCrop = width >= height || height < width * 1.4f;
            boolean obstructedModal = file.getName().equals("1000011119.jpg");
            if (partialCrop || obstructedModal) {
                if (recognizedScreenshot.board != null) {
                    throw new AssertionError("Unsafe recognition accepted: " + file.getName());
                }
            } else if (recognizedScreenshot.board == null) {
                throw new AssertionError(file.getName() + " was not fully recognized: "
                        + recognizedScreenshot.error);
            }
            if (partialCrop || obstructedModal) {
                if (result != null) {
                    throw new AssertionError("Unsafe frame accepted: " + file.getName());
                }
                System.out.println(file.getName() + " rejected as incomplete or obstructed");
                image.flush();
                continue;
            }

            if (result == null) throw new AssertionError("Board not found: " + file.getName());
            if (result.geometry.cellStepPx < width * 0.19f
                    || result.geometry.cellStepPx > width * 0.226f) {
                throw new AssertionError("Incorrect cell scale: " + file.getName());
            }
            if (result.progressionRows < 1 || result.progressionRanks < 1
                    || result.progressionRanks > MergeMadnessBoard.MAX_RANK) {
                throw new AssertionError("Progression chain not found: " + file.getName());
            }
            if (file.getName().equals("1000011152.jpg") && result.progressionRows < 3) {
                throw new AssertionError("The newly wrapped chain row was missed.");
            }
            int[] expectedBoard = expectedBoardFor(file.getName());
            if (expectedBoard != null) {
                int[] actual = recognizedScreenshot.board == null
                        ? recognizedScreenshot.partialRanks : recognizedScreenshot.board.copyRanks();
                System.out.println(file.getName() + " exactBoard=" + Arrays.toString(actual)
                        + " error=" + recognizedScreenshot.error);
                if (recognizedScreenshot.board == null || actual == null
                        || !Arrays.equals(expectedBoard, actual)) {
                    throw new AssertionError(file.getName() + " tile overlay mismatch: expected="
                            + Arrays.toString(expectedBoard)
                            + " actual=" + Arrays.toString(actual));
                }
                if (file.getName().equals("1000011181.jpg")
                        && Math.abs(result.fieldOffsetY) > 2f) {
                    throw new AssertionError("Static 1000011181 frame should have zero offset; got "
                            + result.fieldOffsetY);
                }
            }
            if (file.getName().equals("1000011183.jpg")) {
                checkScaledRecognition(image, screenshotRecognizer, SINGLE_RAT_AND_CHAIN_BOARD,
                        1080, 2340);
                checkScaledRecognition(image, screenshotRecognizer, SINGLE_RAT_AND_CHAIN_BOARD,
                        1080, 2400);
            }

            minimumY = Math.min(minimumY, result.geometry.firstCenterY);
            maximumY = Math.max(maximumY, result.geometry.firstCenterY);
            maximumRows = Math.max(maximumRows, result.progressionRows);
            fullScreenCount++;
            System.out.println(file.getName() + " centerY="
                    + Math.round(result.geometry.firstCenterY) + " cell="
                    + Math.round(result.geometry.cellStepPx) + " chainRows="
                    + result.progressionRows + " ranks=" + result.progressionRanks
                    + " confidence=" + Math.round(result.geometryConfidence * 100) + "%");
            anchor = result.geometry;
            image.flush();
        }

        if (fullScreenCount < 10) throw new AssertionError("Too few full-screen fixtures.");
        if (maximumY - minimumY < 10f) {
            throw new AssertionError("The corpus did not exercise board movement.");
        }
        if (maximumRows < 3) throw new AssertionError("Later chain rows were not detected.");
        System.out.println("PASS: " + files.length + " screenshots; vertical span="
                + Math.round(maximumY - minimumY) + " px; max chain rows=" + maximumRows);
    }

    private static MergeMadnessVision.PixelImage readPixelImage(File file) throws Exception {
        BufferedImage image = ImageIO.read(file);
        if (image == null) throw new AssertionError("Unreadable template image: " + file);
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        image.flush();
        return new MergeMadnessVision.PixelImage(pixels, width, height);
    }

    private static int[] expectedBoardFor(String name) {
        if (name.equals("1000011181.jpg") || name.equals("1000011183.jpg")) {
            return SINGLE_RAT_AND_CHAIN_BOARD;
        }
        if (name.equals("1000011164.jpg") || name.equals("1000011166.jpg")
                || name.equals("1000011168.jpg") || name.equals("1000011170.jpg")
                || name.equals("1000011172.jpg") || name.equals("1000011174.jpg")) {
            return KNOWN_RAT_AND_FISH_BOARD;
        }
        return null;
    }

    private static void checkScaledRecognition(BufferedImage source,
                                               MergeMadnessVision recognizer,
                                               int[] expected, int width, int height) {
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        try {
            int[] pixels = scaled.getRGB(0, 0, width, height, null, 0, width);
            MergeMadnessVision.Result result = recognizer.recognizePixels(
                    pixels, width, height, null, null);
            MergeMadnessLayout.Result geometry = MergeMadnessLayout.locate(
                    pixels, width, height, null);
            if (result.board == null
                    || !Arrays.equals(expected, result.board.copyRanks())) {
                throw new AssertionError("Scaled screenshot " + width + "x" + height
                        + " was not read exactly: " + result.error + " ranks="
                        + Arrays.toString(result.partialRanks) + " centerY="
                        + (geometry == null ? "none" : Math.round(geometry.geometry.firstCenterY))
                        + " step=" + (geometry == null ? "none"
                        : Math.round(geometry.geometry.cellStepPx)));
            }
            if (width == 1080 && height == 2400
                    && (Math.abs(geometry.geometry.cellStepPx - 225f) > 1f
                    || Math.abs(geometry.geometry.firstCenterY - 930f) > 3f)) {
                throw new AssertionError("1080x2400 geometry drifted: centerY="
                        + Math.round(geometry.geometry.firstCenterY) + " step="
                        + Math.round(geometry.geometry.cellStepPx));
            }
            System.out.println("1000011183.jpg scaled=" + width + "x" + height
                    + " centerY=" + Math.round(geometry.geometry.firstCenterY)
                    + " step=" + Math.round(geometry.geometry.cellStepPx)
                    + " exactBoard=PASS");
        } finally {
            scaled.flush();
        }
    }

    private static void checkScaledFrameWithStaleAnchor(BufferedImage source) {
        int width = 1080;
        int height = 2400;
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        try {
            int[] pixels = scaled.getRGB(0, 0, width, height, null, 0, width);
            MergeMadnessLayout.Result global = MergeMadnessLayout.locate(
                    pixels, width, height, null);
            BoardVision.Geometry staleOverlay = new BoardVision.Geometry(
                    width * 0.1875f, height * 0.18f, width * 0.2083f, width * 0.2083f);
            MergeMadnessLayout.Result recovered = MergeMadnessLayout.locate(
                    pixels, width, height, staleOverlay);
            if (global == null) {
                throw new AssertionError("Scaled screenshot failed with a full-screen search.");
            }
            if (recovered == null || recovered.progressionRanks == 0) {
                throw new AssertionError("Scaled screenshot failed with a stale geometry anchor; global="
                        + Math.round(global.geometry.firstCenterY));
            }
            System.out.println("1000011156.jpg scaled=" + width + "x" + height
                    + " centerY=" + Math.round(recovered.geometry.firstCenterY)
                    + " staleAnchor=" + Math.round(height * 0.18f)
                    + " global=" + Math.round(global.geometry.firstCenterY));
        } finally {
            scaled.flush();
        }
    }

    private static void checkScreenshotWithNearbyStaleAnchor(String name, BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        MergeMadnessLayout.Result global = MergeMadnessLayout.locate(
                pixels, width, height, null);
        BoardVision.Geometry stale = new BoardVision.Geometry(108f, 480f, 120f, 120f);
        MergeMadnessLayout.Result recovered = MergeMadnessLayout.locate(
                pixels, width, height, stale);
        MergeMadnessLayout.Result recoveredCalibration = MergeMadnessLayout.locate(
                pixels, width, height, stale, stale);
        if (global == null || recovered == null) {
            throw new AssertionError(name + " could not be located with the stale y=480 anchor.");
        }
        if (recoveredCalibration == null) {
            throw new AssertionError(name + " could not be located with stale manual calibration.");
        }
        if (Math.abs(recovered.geometry.firstCenterY - global.geometry.firstCenterY) > 6f) {
            throw new AssertionError(name + " nearby stale anchor was retained: stale=480 recovered="
                    + Math.round(recovered.geometry.firstCenterY) + " global="
                    + Math.round(global.geometry.firstCenterY));
        }
        if (Math.abs(recoveredCalibration.geometry.firstCenterY
                - global.geometry.firstCenterY) > 6f) {
            throw new AssertionError(name + " retained stale manual calibration: recovered="
                    + Math.round(recoveredCalibration.geometry.firstCenterY) + " global="
                    + Math.round(global.geometry.firstCenterY));
        }
        if (name.equals("1000011174.jpg")) {
            BoardVision.Geometry oldStateGeometry = new BoardVision.Geometry(
                    132f, 481f, 140f, 140f);
            MergeMadnessLayout.Result recoveredFromOldState = MergeMadnessLayout.locate(
                    pixels, width, height, oldStateGeometry, oldStateGeometry);
            if (recoveredFromOldState == null
                    || Math.abs(recoveredFromOldState.geometry.firstCenterY
                    - global.geometry.firstCenterY) > 1f
                    || Math.abs(recoveredFromOldState.geometry.cellStepPx - 120f) > 1f) {
                throw new AssertionError(name + " retained stale geometry/scale from the old run.");
            }
            System.out.println(name + " staleState=(132,481,140) recovered="
                    + Math.round(recoveredFromOldState.geometry.firstCenterX) + ","
                    + Math.round(recoveredFromOldState.geometry.firstCenterY) + " step="
                    + Math.round(recoveredFromOldState.geometry.cellStepPx));
        }
        System.out.println(name + " staleAnchor=480 recovered="
                + Math.round(recovered.geometry.firstCenterY) + " global="
                + Math.round(global.geometry.firstCenterY) + " manual="
                + Math.round(recoveredCalibration.geometry.firstCenterY));
    }
}
