import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Files;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Compares two compiled engines in isolated class loaders, without search deadlines. */
public final class MergeMadnessEquivalenceCheck {
    private static final String PACKAGE = "ua.borlock.autotap.";

    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("baseline-classes candidate-classes [screenshots]");
        try (Engine baseline = new Engine(Path.of(args[0]));
             Engine candidate = new Engine(Path.of(args[1]))) {
            exhaustiveLines(baseline, candidate);
            mixedBoards(baseline, candidate);
            exactSearches(baseline, candidate);
            simulatedGames(baseline, candidate);
            if (args.length > 2) layouts(baseline, candidate, Path.of(args[2]));
        }
    }

    private static void layouts(Engine baseline, Engine candidate, Path directory) throws Exception {
        int count = 0;
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".jpg")).sorted().toList()) {
                BufferedImage original = ImageIO.read(file.toFile());
                for (int[] size : new int[][]{{original.getWidth(), original.getHeight()},
                        {1080, 2340}, {1080, 2400}}) {
                    BufferedImage image = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = image.createGraphics();
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(original, 0, 0, size[0], size[1], null);
                    graphics.dispose();
                    int[] pixels = image.getRGB(0, 0, size[0], size[1], null, 0, size[0]);
                    Layout expected = baseline.layout(pixels, size[0], size[1]);
                    // Reuse one workspace through resolution changes, new frames and cache hits.
                    for (int repetition = 0; repetition < 2; repetition++) {
                        Layout actual = candidate.layout(pixels, size[0], size[1]);
                        if (!expected.equals(actual)) throw new AssertionError("Layout changed: "
                                + file + " " + size[0] + "x" + size[1]);
                        count++;
                    }
                }
            }
        }
        System.out.println("PASS: " + count
                + " identical layouts including geometry/confidence bits, hint rows, invalid frames and cache reuse.");
    }

    private static void exhaustiveLines(Engine baseline, Engine candidate) throws Exception {
        int possibilities = 18 * 18 * 18 * 18;
        for (int code = 0; code < possibilities; code++) {
            for (int orientation = 0; orientation < 2; orientation++) {
                int[] values = new int[16];
                int remaining = code;
                for (int position = 0; position < 4; position++) {
                    values[orientation == 0 ? 4 + position : 2 + position * 4] = remaining % 18;
                    remaining /= 18;
                }
                compareSlides(baseline, candidate, values);
            }
        }
        System.out.println("PASS: all 18^4 rank lines in both orientations, "
                + (possibilities * 8) + " identical slides, scores, merges and legal directions.");
    }

    private static void mixedBoards(Engine baseline, Engine candidate) throws Exception {
        Random random = new Random(0x2048B0A4DL);
        for (int sample = 0; sample < 2048; sample++) {
            int[] values = new int[16];
            for (int i = 0; i < values.length; i++) values[i] = random.nextInt(18);
            compareSlides(baseline, candidate, values);
        }
        System.out.println("PASS: 2048 mixed boards with ranks 0..17.");
    }

    private static void compareSlides(Engine baseline, Engine candidate, int[] values) throws Exception {
        Object oldBoard = baseline.board.newInstance((Object) values);
        Object newBoard = candidate.board.newInstance((Object) values);
        int expectedMask = 0;
        for (int direction = 0; direction < 4; direction++) {
            Slide expected = baseline.slide(oldBoard, direction);
            Slide actual = candidate.slide(newBoard, direction);
            if (expected.changed) expectedMask |= 1 << direction;
            if (expected.changed != actual.changed || expected.merges != actual.merges
                    || expected.points != actual.points || !Arrays.equals(expected.ranks, actual.ranks)) {
                throw new AssertionError("Slide " + direction + " changed: " + Arrays.toString(values));
            }
        }
        int actualMask = (int) candidate.legalMoves.invoke(newBoard);
        if (expectedMask != actualMask || oldBoard.hashCode() != newBoard.hashCode()) {
            throw new AssertionError("Mobility/hash changed: " + Arrays.toString(values));
        }
    }

    private static void exactSearches(Engine baseline, Engine candidate) throws Exception {
        Random random = new Random(0x19_34_35L);
        List<int[]> boards = new ArrayList<>();
        boards.add(new int[]{0,0,0,1, 0,1,2,3, 0,0,4,6, 1,5,7,9});
        while (boards.size() < 24) {
            int[] board = new int[16];
            for (int i = 0; i < board.length; i++) board[i] = 1 + random.nextInt(12);
            int emptyCount = random.nextInt(5);
            for (int empty = 0; empty < emptyCount; empty++) {
                int cell;
                do { cell = random.nextInt(16); } while (board[cell] == 0);
                board[cell] = 0;
            }
            boards.add(board);
        }
        double[] probabilities = {0.50, 0.90, 0.97};
        int comparisons = 0;
        for (int[] board : boards) {
            for (double probability : probabilities) {
                for (int depth = 1; depth <= 3; depth++) {
                    assertSame(baseline.search(board, depth, probability),
                            candidate.search(board, depth, probability), board);
                    comparisons++;
                }
            }
        }
        System.out.println("PASS: " + comparisons
                + " exact searches, depths 1..3 and spawn probabilities .50/.90/.97; identical value bits.");

        // Compare the same completed tree, not two searches interrupted at different times.
        for (int warm = 0; warm < 3; warm++) {
            baseline.search(boards.get(0), 3, .90);
            candidate.search(boards.get(0), 3, .90);
        }
        long[] before = new long[boards.size() * 3];
        long[] after = new long[before.length];
        int count = 0;
        for (int repetition = 0; repetition < 3; repetition++) {
            for (int[] board : boards) {
                Decision oldMove, newMove;
                if ((count & 1) == 0) {
                    oldMove = baseline.search(board, 3, .90);
                    newMove = candidate.search(board, 3, .90);
                } else {
                    newMove = candidate.search(board, 3, .90);
                    oldMove = baseline.search(board, 3, .90);
                }
                assertSame(oldMove, newMove, board);
                before[count] = oldMove.nanos;
                after[count++] = newMove.nanos;
            }
        }
        timing("baseline", before);
        timing("candidate", after);
    }

    private static void simulatedGames(Engine baseline, Engine candidate) throws Exception {
        int decisions = 0;
        for (int game = 0; game < 8; game++) {
            Random random = new Random(0xB4A0L + game);
            int[] board = new int[16];
            spawn(board, random);
            spawn(board, random);
            long points = 0;
            int moves = 0;
            for (; moves < 120; moves++) {
                Decision expected = baseline.search(board, 2, .90);
                Decision actual = candidate.search(board, 2, .90);
                assertSame(expected, actual, board);
                if (actual.direction < 0) break;
                Slide oldSlide = baseline.slide(baseline.board.newInstance((Object) board), expected.direction);
                Slide newSlide = candidate.slide(candidate.board.newInstance((Object) board), actual.direction);
                if (!oldSlide.changed || !Arrays.equals(oldSlide.ranks, newSlide.ranks)
                        || oldSlide.points != newSlide.points) throw new AssertionError("Game diverged");
                board = newSlide.ranks;
                points += newSlide.points;
                spawn(board, random);
                decisions++;
            }
            int max = Arrays.stream(board).max().orElse(0);
            System.out.println("game " + (game + 1) + ": identical moves=" + moves
                    + " mergePoints=" + points + " maxRank=" + max);
        }
        System.out.println("PASS: " + decisions + " identical decisions in 8 seeded games at depth 2.");
    }

    private static void spawn(int[] board, Random random) {
        int empty = 0;
        for (int rank : board) if (rank == 0) empty++;
        if (empty == 0) return;
        int position = random.nextInt(empty);
        for (int i = 0; i < board.length; i++) {
            if (board[i] == 0 && position-- == 0) {
                board[i] = random.nextDouble() < .90 ? 1 : 2;
                return;
            }
        }
    }

    private static void assertSame(Decision expected, Decision actual, int[] board) {
        if (expected.direction != actual.direction || expected.valueBits != actual.valueBits
                || expected.merges != actual.merges || expected.points != actual.points) {
            throw new AssertionError("Decision changed: " + Arrays.toString(board)
                    + " baseline=" + expected + " candidate=" + actual);
        }
    }

    private static void timing(String name, long[] nanos) {
        Arrays.sort(nanos);
        System.out.printf(Locale.ROOT, "%s exact depth3: median=%.3f ms p95=%.3f ms samples=%d%n",
                name, nanos[nanos.length / 2] / 1e6,
                nanos[(int) (nanos.length * .95)] / 1e6, nanos.length);
    }

    private record Slide(int[] ranks, boolean changed, int merges, long points) { }
    private record Decision(int direction, long valueBits, int merges, long points, long nanos) { }
    private record Layout(List<Integer> bits) { }

    private static final class Engine implements AutoCloseable {
        final URLClassLoader loader;
        final Constructor<?> board, context;
        final Method slide, copyRanks, search, legalMoves;
        final Field slideBoard, changed, slideMerges, slidePoints;
        final Field direction, value, moveMerges, movePoints;
        final Object solver;
        final Object[] directions;
        final Method locate;
        final Object layoutWorkspace;

        Engine(Path directory) throws Exception {
            loader = new URLClassLoader(new java.net.URL[]{directory.toUri().toURL()},
                    ClassLoader.getPlatformClassLoader());
            Class<?> boardType = loader.loadClass(PACKAGE + "MergeMadnessBoard");
            Class<?> solverType = loader.loadClass(PACKAGE + "MergeMadnessSolver");
            Class<?> contextType = loader.loadClass(PACKAGE + "MergeMadnessSolver$SearchContext");
            Class<?> directionType = loader.loadClass(PACKAGE + "MergeMadnessSolver$Direction");
            Class<?> slideType = loader.loadClass(PACKAGE + "MergeMadnessBoard$SlideResult");
            Class<?> moveType = loader.loadClass(PACKAGE + "MergeMadnessSolver$Move");
            board = constructor(boardType, int[].class);
            context = constructor(contextType, long.class, double.class);
            solver = constructor(solverType).newInstance();
            directions = directionType.getEnumConstants();
            slide = method(boardType, "slide", directionType);
            copyRanks = method(boardType, "copyRanks");
            search = method(solverType, "searchRoot", boardType, int.class, contextType);
            Method mask;
            try { mask = method(boardType, "legalMovesMask"); }
            catch (NoSuchMethodException ignored) { mask = null; }
            legalMoves = mask;
            slideBoard = field(slideType, "board"); changed = field(slideType, "changed");
            slideMerges = field(slideType, "mergedCount"); slidePoints = field(slideType, "mergePoints");
            direction = field(moveType, "direction"); value = field(moveType, "value");
            moveMerges = field(moveType, "mergedCount"); movePoints = field(moveType, "mergePoints");
            Class<?> layoutType = loader.loadClass(PACKAGE + "MergeMadnessLayout");
            Class<?> geometryType = loader.loadClass(PACKAGE + "BoardVision$Geometry");
            Object workspace;
            Method locateMethod;
            try {
                Class<?> workspaceType = loader.loadClass(PACKAGE + "MergeMadnessLayout$Workspace");
                workspace = constructor(workspaceType).newInstance();
                locateMethod = method(layoutType, "locate", int[].class, int.class, int.class,
                        geometryType, geometryType, workspaceType);
            } catch (ClassNotFoundException ignored) {
                workspace = null;
                locateMethod = method(layoutType, "locate", int[].class, int.class, int.class,
                        geometryType, geometryType);
            }
            layoutWorkspace = workspace;
            locate = locateMethod;
        }

        Layout layout(int[] pixels, int width, int height) throws Exception {
            Object result = layoutWorkspace == null
                    ? locate.invoke(null, pixels, width, height, null, null)
                    : locate.invoke(null, pixels, width, height, null, null, layoutWorkspace);
            List<Integer> bits = new ArrayList<>();
            if (result == null) return new Layout(bits);
            Class<?> resultType = result.getClass();
            Object geometry = field(resultType, "geometry").get(result);
            for (String name : new String[]{"firstCenterX", "firstCenterY", "cellStepPx", "cellStepYpx"}) {
                bits.add(Float.floatToIntBits(field(geometry.getClass(), name).getFloat(geometry)));
            }
            for (String name : new String[]{"fieldOffsetY", "frameShiftY", "geometryConfidence"}) {
                bits.add(Float.floatToIntBits(field(resultType, name).getFloat(result)));
            }
            bits.add(field(resultType, "progressionRows").getInt(result));
            bits.add(field(resultType, "progressionRanks").getInt(result));
            for (float y : (float[]) field(resultType, "progressionRowCentersY").get(result)) {
                bits.add(Float.floatToIntBits(y));
            }
            for (int slots : (int[]) field(resultType, "progressionSlotsByRow").get(result)) bits.add(slots);
            return new Layout(bits);
        }

        Slide slide(Object input, int ordinal) throws Exception {
            Object result = slide.invoke(input, directions[ordinal]);
            return new Slide((int[]) copyRanks.invoke(slideBoard.get(result)),
                    changed.getBoolean(result), slideMerges.getInt(result), slidePoints.getLong(result));
        }

        Decision search(int[] values, int depth, double probability) throws Exception {
            Object input = board.newInstance((Object) values);
            Object tree = context.newInstance(Long.MAX_VALUE, probability);
            long started = System.nanoTime();
            Object result = null;
            for (int current = 1; current <= depth; current++) result = search.invoke(solver, input, current, tree);
            long nanos = System.nanoTime() - started;
            if (result == null) return new Decision(-1, 0, 0, 0, nanos);
            return new Decision(((Enum<?>) direction.get(result)).ordinal(),
                    Double.doubleToLongBits(value.getDouble(result)), moveMerges.getInt(result),
                    movePoints.getLong(result), nanos);
        }

        @Override public void close() throws Exception { loader.close(); }
    }

    private static Constructor<?> constructor(Class<?> type, Class<?>... arguments) throws Exception {
        Constructor<?> result = type.getDeclaredConstructor(arguments);
        result.setAccessible(true);
        return result;
    }

    private static Method method(Class<?> type, String name, Class<?>... arguments) throws NoSuchMethodException {
        Method result = type.getDeclaredMethod(name, arguments);
        result.setAccessible(true);
        return result;
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }
}
