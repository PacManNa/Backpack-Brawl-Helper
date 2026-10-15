package ua.borlock.autotap;

import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;


/** Exercises the production reader without any guide, saved sample or bundled object art. */
public final class MergeMadnessObjectsCheck {
    public static void main(String[] args) throws Exception {
        MergeMadnessObjectsCheck check = new MergeMadnessObjectsCheck();
        check.readsEveryOccupiedCellFromTheCurrentHintsAlone();
        check.newlyColoredSilhouetteIsLearnedOnTheNextFrame();
        check.objectRankFollowsItsHintPositionAcrossAllFourRows();
        check.ignoresHintsCutOffByTheBottomPanel();
        check.hintCacheTracksPixelsWithoutDependingOnBoardTiles();
        System.out.println("PASS: hints alone at 3 resolutions, silhouette unlock, ranks 1..17 across 4 rows, obscured hints.");
    }

    private static void assertEquals(int expected, int actual) {
        assertEquals("", expected, actual);
    }

    private static void assertEquals(String context, int expected, int actual) {
        if (expected != actual) throw new AssertionError(context + " expected=" + expected + " actual=" + actual);
    }

    private static void assertNotNull(Object value) {
        assertNotNull("Missing fixture", value);
    }

    private static void assertNotNull(String context, Object value) {
        if (value == null) throw new AssertionError(context);
    }

    private static final int[] BOARD = {
            0, 0, 0, 1, 0, 1, 2, 3, 0, 0, 4, 6, 1, 5, 7, 9
    };

    private void readsEveryOccupiedCellFromTheCurrentHintsAlone() throws Exception {
        BufferedImage source = source();
        MergeMadnessObjects.Cache cache = new MergeMadnessObjects.Cache();
        MergeMadnessLayout.Workspace workspace = new MergeMadnessLayout.Workspace();
        for (int[] size : new int[][]{{576, 1280}, {1080, 2340}, {1080, 2400}}) {
            BufferedImage image = scaled(source, size[0], size[1]);
            Frame frame = new Frame(image, workspace);
            MergeMadnessObjects.Progression chain = frame.chain(cache);
            System.out.println("hints " + size[0] + "x" + size[1] + " unlocked="
                    + chain.unlockedRanks + " locked=" + chain.lockedRank
                    + " rows=" + java.util.Arrays.toString(frame.layout.progressionRowCentersY));
            assertEquals(10, chain.unlockedRanks);
            assertEquals(11, chain.lockedRank);
            for (int index = 0; index < BOARD.length; index++) {
                if (BOARD[index] == 0) continue;
                MergeMadnessVision.TileMatch match = frame.match(chain, index / 4, index % 4);
                assertEquals(size[0] + "x" + size[1] + " cell=" + index
                        + " score=" + match.score + " margin=" + match.margin,
                        BOARD[index], match.rank);
            }
        }
    }

    private void newlyColoredSilhouetteIsLearnedOnTheNextFrame() throws Exception {
        BufferedImage image = source();
        MergeMadnessLayout.Workspace workspace = new MergeMadnessLayout.Workspace();
        Frame before = new Frame(image, workspace);
        MergeMadnessObjects.Cache cache = new MergeMadnessObjects.Cache();
        MergeMadnessObjects.Progression original = before.chain(cache);
        assertEquals(11, original.lockedRank);
        if (before.chain(cache) != original) throw new AssertionError("Unchanged hints were rebuilt");
        BufferedImage newMouse = hint(image, before.layout, 9);
        recolor(newMouse, 0, 0, newMouse.getWidth(), newMouse.getHeight());
        BufferedImage lockedMouse = hint(image, before.layout, 11);
        Graphics2D graphics = image.createGraphics();
        drawHint(graphics, newMouse, before.layout, 11);
        drawHint(graphics, lockedMouse, before.layout, 12);
        graphics.dispose();
        BoardVision.Geometry g = before.layout.geometry;
        recolor(image, Math.round(g.centerX(3) - g.cellStepPx * 0.45f),
                Math.round(g.centerY(3) - g.cellStepYpx * 0.40f),
                Math.round(g.cellStepPx * 0.90f), Math.round(g.cellStepYpx * 0.80f));
        Frame after = new Frame(image, workspace);
        MergeMadnessObjects.Progression learned = after.chain(cache);
        if (learned == original) throw new AssertionError("Newly colored hints retained a stale cache");
        assertEquals(11, learned.unlockedRanks);
        assertEquals(12, learned.lockedRank);
        assertEquals(11, after.match(learned, 3, 3).rank);
    }

    private void objectRankFollowsItsHintPositionAcrossAllFourRows() throws Exception {
        BufferedImage original = source();
        MergeMadnessLayout.Workspace workspace = new MergeMadnessLayout.Workspace();
        Frame reference = new Frame(original, workspace);
        BufferedImage[] sprites = new BufferedImage[9];
        for (int rank = 1; rank <= 9; rank++) sprites[rank - 1] = hint(original, reference.layout, rank);
        for (int rank = 1; rank <= MergeMadnessBoard.MAX_RANK; rank++) {
            BufferedImage image = scaled(original, original.getWidth(), original.getHeight());
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(new Color(42, 49, 51));
            graphics.fillRect(50, 935, 460, 285);
            BoardVision.Geometry g = reference.layout.geometry;
            MergeMadnessLayout.Result layout = new MergeMadnessLayout.Result(g,
                    new float[]{960f, 1025f, 1090f, 1155f}, new int[]{5, 5, 5, 2},
                    4, 17, 0f, 0f, 1f);
            for (int slot = 1; slot <= 17; slot++) {
                // The ninth sprite appears exactly once; other sprites are distractors.
                drawHint(graphics, sprites[slot == rank ? 8 : (slot - 1) % 8], layout, slot);
            }
            graphics.dispose();
            Frame frame = new Frame(image, workspace);
            assertEquals("Wrapped rows for rank " + rank, 4, frame.layout.progressionRows);
            MergeMadnessObjects.Progression chain = frame.chain();
            assertEquals("position=" + rank + " locked=" + chain.lockedRank
                    + " slots=" + java.util.Arrays.toString(frame.layout.progressionSlotsByRow)
                    + " centers=" + java.util.Arrays.toString(frame.layout.progressionRowCentersY),
                    17, chain.unlockedRanks);
            MergeMadnessVision.TileMatch match = frame.match(chain, 3, 3);
            assertEquals("Hint position " + rank + ": score=" + match.score
                    + " margin=" + match.margin, rank, match.rank);
        }
    }

    private void ignoresHintsCutOffByTheBottomPanel() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/merge_madness/1000011126.jpg")) {
            BufferedImage image = ImageIO.read(input);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(new Color(57, 45, 48));
            graphics.fillRect(0, 1118, image.getWidth(), image.getHeight() - 1118);
            graphics.dispose();
            Frame frame = new Frame(image);
            assertEquals(5, frame.chain().unlockedRanks);
        }
    }

    private void hintCacheTracksPixelsWithoutDependingOnBoardTiles() throws Exception {
        Frame frame = new Frame(source());
        MergeMadnessObjects.Cache cache = new MergeMadnessObjects.Cache();
        MergeMadnessObjects.Progression original = frame.chain(cache);
        int[] changed = frame.pixels.clone();
        changed[Math.round(frame.layout.geometry.centerY(0)) * frame.width
                + Math.round(frame.layout.geometry.centerX(0))] ^= 0x00FFFF;
        if (cache.read(changed,frame.width,frame.height,frame.layout) != original) {
            throw new AssertionError("Board-only changes rebuilt the hints");
        }
        int y = Math.round(frame.layout.progressionRowCentersY[0]);
        int x = Math.round(frame.layout.geometry.centerX(0));
        changed[y * frame.width + x] ^= 0x000001;
        if (cache.read(changed,frame.width,frame.height,frame.layout) == original) {
            throw new AssertionError("A changed hint pixel did not invalidate the cache");
        }
    }

    private BufferedImage source() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/merge_madness/1000011196.jpg")) {
            assertNotNull(input);
            return ImageIO.read(input);
        }
    }

    private static BufferedImage scaled(BufferedImage source, int width, int height) {
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return result;
    }

    private static BufferedImage hint(BufferedImage image, MergeMadnessLayout.Result layout, int rank) {
        BoardVision.Geometry g = layout.geometry;
        int row = (rank - 1) / 5, slot = (rank - 1) % 5;
        int width = Math.round(g.cellStepPx * 0.46f), height = Math.round(g.cellStepYpx * 0.38f);
        int left = Math.round(g.centerX(0) + (slot * 0.66f - 0.05f) * g.cellStepPx - width * 0.5f);
        int top = Math.round(layout.progressionRowCentersY[row] - height * 0.5f);
        return scaled(image.getSubimage(left, top, width, height), width, height);
    }

    private static void drawHint(Graphics2D graphics, BufferedImage hint,
                                  MergeMadnessLayout.Result layout, int rank) {
        BoardVision.Geometry g = layout.geometry;
        int row = (rank - 1) / 5, slot = (rank - 1) % 5;
        int left = Math.round(g.centerX(0) + (slot * 0.66f - 0.05f) * g.cellStepPx - hint.getWidth() * 0.5f);
        int top = Math.round(layout.progressionRowCentersY[row] - hint.getHeight() * 0.5f);
        graphics.drawImage(hint, left, top, null);
    }

    private static void recolor(BufferedImage image, int left, int top, int width, int height) {
        for (int y = top; y < top + height; y++) for (int x = left; x < left + width; x++) {
            int color = image.getRGB(x, y);
            int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
            if (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) >= 24 && r > g) {
                image.setRGB(x, y, 0xFF000000 | (b << 16) | (g << 8) | r);
            }
        }
    }

    private static final class Frame {
        final int[] pixels;
        final int width, height;
        final MergeMadnessLayout.Result layout;

        Frame(BufferedImage image) {
            this(image, new MergeMadnessLayout.Workspace());
        }

        Frame(BufferedImage image, MergeMadnessLayout.Workspace workspace) {
            width = image.getWidth(); height = image.getHeight();
            pixels = image.getRGB(0, 0, width, height, null, 0, width);
            layout = MergeMadnessLayout.locate(pixels, width, height, null, null, workspace);
            assertNotNull("Game board layout", layout);
        }

        MergeMadnessObjects.Progression chain() {
            return MergeMadnessObjects.readProgression(pixels, width, height, layout);
        }

        MergeMadnessObjects.Progression chain(MergeMadnessObjects.Cache cache) {
            return cache.read(pixels,width,height,layout);
        }

        MergeMadnessVision.TileMatch match(MergeMadnessObjects.Progression chain, int row, int column) {
            return chain.matchCell(pixels, width, height, layout.geometry, row, column);
        }
    }
}
