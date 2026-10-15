package ua.borlock.autotap;

import java.util.Arrays;

/** Learns the ordered, unlocked objects directly from the current merge chain. */
final class MergeMadnessObjects {
    private static final int WIDTH = 48;
    private static final int HEIGHT = 32;
    private static final int PIXELS = WIDTH * HEIGHT;
    private static final int NORMAL_WIDTH = 32;
    private static final int NORMAL_HEIGHT = 24;
    private static final int SLOTS_PER_ROW = 5;
    private static final float[] HINT_HEIGHTS = {0.38f, 0.44f, 0.34f, 0.30f};
    static final double MIN_SCORE = 0.82;
    static final double MIN_MARGIN = 0.045;

    private MergeMadnessObjects() { }

    /** Reuses descriptors only while every pixel that can enter a hint crop is unchanged. */
    static final class Cache {
        private Progression previous;
        private long previousFingerprint;

        Progression read(int[] pixels, int width, int height, MergeMadnessLayout.Result layout) {
            long fingerprint = fingerprint(pixels, width, height, layout);
            if (previous == null || fingerprint != previousFingerprint) {
                previous = readProgression(pixels, width, height, layout);
                previousFingerprint = fingerprint;
            }
            return previous;
        }

        private static long fingerprint(int[] pixels, int width, int height,
                                         MergeMadnessLayout.Result layout) {
            BoardVision.Geometry g = layout.geometry;
            long hash = mix(mix(0xcbf29ce484222325L, width), height);
            hash = mix(hash, Float.floatToIntBits(g.centerX(0)));
            hash = mix(hash, Float.floatToIntBits(g.cellStepPx));
            hash = mix(hash, Float.floatToIntBits(g.cellStepYpx));
            hash = mix(hash, layout.progressionRows);
            for (int row = 0; row < layout.progressionRows; row++) {
                int slots = layout.progressionSlotsByRow[row];
                float centerY = layout.progressionRowCentersY[row];
                hash = mix(mix(hash, slots), Float.floatToIntBits(centerY));
                if (slots == 0) continue;
                // The largest crop is 0.46 x 0.44 cells. Include interpolation
                // neighbors and every pixel, so a newly colored icon invalidates it.
                float firstX = g.centerX(0) - g.cellStepPx * 0.05f;
                int left = clamp((int) Math.floor(firstX - g.cellStepPx * 0.23f) - 2, width);
                int right = clamp((int) Math.ceil(firstX
                        + (slots - 1) * g.cellStepPx * 0.66f + g.cellStepPx * 0.23f) + 2, width);
                int top = clamp((int) Math.floor(centerY - g.cellStepYpx * 0.22f) - 2, height);
                int bottom = clamp((int) Math.ceil(centerY + g.cellStepYpx * 0.22f) + 2, height);
                for (int y = top; y <= bottom; y++) {
                    for (int x = left; x <= right; x++) hash = mix(hash, pixels[y * width + x]);
                }
            }
            return hash;
        }

        private static long mix(long hash, int value) {
            return (hash ^ value) * 0x100000001b3L;
        }
    }

    static Progression readProgression(int[] pixels, int width, int height,
                                      MergeMadnessLayout.Result layout) {
        Signature[] templates = new Signature[MergeMadnessBoard.MAX_RANK];
        BoardVision.Geometry g = layout.geometry;
        int unlocked = 0;
        int lockedRank = 0;
        // A wrapped row continues the sequence: 1..5, 6..10, 11..15, 16..17.
        // Keep absolute slot indices; unavailable/obscured icons never shift ranks.
        rows: for (int row = 0; row < layout.progressionRows; row++) {
            for (int slot = 0; slot < layout.progressionSlotsByRow[row]; slot++) {
                int index = row * SLOTS_PER_ROW + slot;
                if (index >= templates.length) break rows;
                float x = g.centerX(0) + (slot * 0.66f - 0.05f) * g.cellStepPx;
                Signature sample = null;
                // Follow small row-center errors and avoid the footer directly
                // below the final row. Use a complete crop, never a cut-off icon.
                for (float cropHeight : HINT_HEIGHTS) {
                    Signature candidate = describe(pixels, width, height, x,
                            layout.progressionRowCentersY[row],
                            g.cellStepPx * 0.46f, g.cellStepYpx * cropHeight);
                    if (candidate != null && candidate.complete) {
                        sample = candidate;
                        break;
                    }
                }
                if (sample == null) break rows;
                if (!sample.unlocked) {
                    lockedRank = index + 1;
                    break rows;
                }
                templates[index] = sample;
                unlocked++;
            }
            if (layout.progressionSlotsByRow[row] < SLOTS_PER_ROW) break;
        }
        return new Progression(templates, unlocked, lockedRank);
    }

    static final class Progression {
        private final Signature[] templates;
        final int unlockedRanks;
        final int lockedRank;

        private Progression(Signature[] templates, int unlockedRanks, int lockedRank) {
            this.templates = templates;
            this.unlockedRanks = unlockedRanks;
            this.lockedRank = lockedRank;
        }

        MergeMadnessVision.TileMatch matchCell(int[] pixels, int width, int height,
                                               BoardVision.Geometry g, int row, int column) {
            Signature object = describe(pixels, width, height, g.centerX(column),
                    g.centerY(row) + g.cellStepYpx * 0.015f,
                    g.cellStepPx * 0.82f, g.cellStepYpx * 0.60f);
            double[] scores = new double[templates.length];
            boolean[] visible = new boolean[templates.length];
            if (object != null) {
                for (int rank = 0; rank < templates.length; rank++) {
                    if (templates[rank] == null) continue;
                    visible[rank] = true;
                    Signature reference = templates[rank];
                    scores[rank] = 0.70 * dot(object.spatial, reference.spatial)
                            + 0.15 * dot(object.hue, reference.hue)
                            + 0.15 * dot(object.value, reference.value);
                }
            }
            return MergeMadnessVision.bestVisibleProgressionMatch(scores, visible);
        }
    }

    private static Signature describe(int[] source, int sourceWidth, int sourceHeight,
                                      float centerX, float centerY,
                                      float cropWidth, float cropHeight) {
        float[] colors = new float[PIXELS * 3];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                sample(source, sourceWidth, sourceHeight,
                        centerX - cropWidth * 0.5f + (x + 0.5f) * cropWidth / WIDTH - 0.5f,
                        centerY - cropHeight * 0.5f + (y + 0.5f) * cropHeight / HEIGHT - 0.5f,
                        colors, (y * WIDTH + x) * 3);
            }
        }
        float[][] corners = {corner(colors, 0, 0), corner(colors, WIDTH - 3, 0),
                corner(colors, 0, HEIGHT - 3), corner(colors, WIDTH - 3, HEIGHT - 3)};
        boolean[] mask = new boolean[PIXELS];
        for (int y = 0; y < HEIGHT; y++) {
            float fy = y / (float) (HEIGHT - 1);
            for (int x = 0; x < WIDTH; x++) {
                float fx = x / (float) (WIDTH - 1);
                float distance = 0f;
                for (int c = 0; c < 3; c++) {
                    float background = (1f - fy)
                            * ((1f - fx) * corners[0][c] + fx * corners[1][c])
                            + fy * ((1f - fx) * corners[2][c] + fx * corners[3][c]);
                    float delta = colors[(y * WIDTH + x) * 3 + c] - background;
                    distance += delta * delta;
                }
                mask[y * WIDTH + x] = distance > 3f * 18f * 18f;
            }
        }
        // Close tiny JPEG gaps, fill the body enclosed by its outline, and drop
        // isolated background texture. The hint and tile have different backgrounds.
        mask = morph(morph(mask, true), false);
        fillHoles(mask);
        removeSpeckles(mask);
        int left = WIDTH, top = HEIGHT, right = -1, bottom = -1;
        int count = 0, lit = 0;
        float lumaSum = 0f;
        for (int index = 0; index < PIXELS; index++) {
            if (!mask[index]) continue;
            int x = index % WIDTH, y = index / WIDTH;
            left = Math.min(left, x); right = Math.max(right, x);
            top = Math.min(top, y); bottom = Math.max(bottom, y);
            float luma = colors[index * 3] * 0.299f + colors[index * 3 + 1] * 0.587f
                    + colors[index * 3 + 2] * 0.114f;
            if (luma > 52f) lit++;
            lumaSum += luma;
            count++;
        }
        if (count < 30) return null;
        // A panel can leave only an ear or the top strip of an icon visible.
        // Such fragments must not become stretched, apparently valid templates.
        boolean complete = top >= 2 && bottom < HEIGHT - 2
                && bottom - top + 1 >= 10 && right - left + 1 >= 20;
        boolean unlocked = lit / (float) count > 0.08f;
        // A black animal's torso can blend into the dark hint background. Its
        // outline and ears still locate the body; include their convex envelope.
        if (lumaSum / count < 60f) mask = convexEnvelope(mask, count);
        left = Math.max(0, left - 1); top = Math.max(0, top - 1);
        right = Math.min(WIDTH, right + 2); bottom = Math.min(HEIGHT, bottom + 2);
        float[] spatial = new float[8 * 6 * 4];
        float[] hue = new float[8], value = new float[8];
        float[] rgb = new float[3];
        for (int y = 0; y < NORMAL_HEIGHT; y++) {
            float sy = top + (y + 0.5f) * (bottom - top) / NORMAL_HEIGHT - 0.5f;
            for (int x = 0; x < NORMAL_WIDTH; x++) {
                float sx = left + (x + 0.5f) * (right - left) / NORMAL_WIDTH - 0.5f;
                float alpha = interpolateMask(mask, sx, sy);
                interpolateColors(colors, sx, sy, rgb);
                int patch = ((y / 4) * 8 + x / 4) * 4;
                for (int c = 0; c < 3; c++) {
                    spatial[patch + c] += rgb[c] / 255f * alpha / 16f;
                }
                spatial[patch + 3] += alpha * 0.30f / 16f;
                if (alpha < 0.5f) continue;
                float max = Math.max(rgb[0], Math.max(rgb[1], rgb[2]));
                float min = Math.min(rgb[0], Math.min(rgb[1], rgb[2]));
                value[Math.min(7, (int) (max / 255f * 8f))] += alpha;
                float saturation = max == 0f ? 0f : (max - min) / max;
                if (saturation <= 0.18f || max <= 0.16f * 255f) continue;
                float h;
                if (max == rgb[0]) h = ((rgb[1] - rgb[2]) / (max - min)) % 6f;
                else if (max == rgb[1]) h = (rgb[2] - rgb[0]) / (max - min) + 2f;
                else h = (rgb[0] - rgb[1]) / (max - min) + 4f;
                if (h < 0f) h += 6f;
                hue[Math.min(7, (int) (h / 6f * 8f))] += saturation * alpha;
            }
        }
        float mean = 0f;
        for (float v : spatial) mean += v;
        mean /= spatial.length;
        double magnitude = 0;
        for (int i = 0; i < spatial.length; i++) {
            spatial[i] -= mean;
            magnitude += spatial[i] * spatial[i];
        }
        if (magnitude <= 0) return null;
        magnitude = Math.sqrt(magnitude);
        for (int i = 0; i < spatial.length; i++) spatial[i] /= magnitude;
        normalizeHistogram(hue); normalizeHistogram(value);
        return new Signature(spatial, hue, value, unlocked, complete);
    }

    private static float[] corner(float[] colors, int x, int y) {
        float[] result = new float[3];
        for (int c = 0; c < 3; c++) {
            float[] samples = new float[9];
            int n = 0;
            for (int dy = 0; dy < 3; dy++) for (int dx = 0; dx < 3; dx++) {
                samples[n++] = colors[((y + dy) * WIDTH + x + dx) * 3 + c];
            }
            Arrays.sort(samples);
            result[c] = samples[4];
        }
        return result;
    }

    private static boolean[] morph(boolean[] mask, boolean dilate) {
        boolean[] result = new boolean[PIXELS];
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            boolean v = !dilate;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                boolean neighbor = mask[clamp(y + dy, HEIGHT) * WIDTH + clamp(x + dx, WIDTH)];
                v = dilate ? v || neighbor : v && neighbor;
            }
            result[y * WIDTH + x] = v;
        }
        return result;
    }

    private static void fillHoles(boolean[] mask) {
        boolean[] outside = new boolean[PIXELS];
        int[] queue = new int[PIXELS];
        int end = 0;
        for (int i = 0; i < PIXELS; i++) {
            if (i < WIDTH || i >= PIXELS - WIDTH || i % WIDTH == 0 || i % WIDTH == WIDTH - 1) {
                if (!mask[i]) { outside[i] = true; queue[end++] = i; }
            }
        }
        for (int start = 0; start < end; start++) {
            int i = queue[start];
            for (int direction = 0; direction < 4; direction++) {
                int neighbor = neighbor(i, direction);
                if (!mask[neighbor] && !outside[neighbor]) {
                    outside[neighbor] = true; queue[end++] = neighbor;
                }
            }
        }
        for (int i = 0; i < PIXELS; i++) if (!outside[i]) mask[i] = true;
    }

    private static void removeSpeckles(boolean[] mask) {
        int[] labels = new int[PIXELS], sizes = new int[PIXELS + 1], queue = new int[PIXELS];
        int label = 0, largest = 0;
        for (int i = 0; i < PIXELS; i++) {
            if (!mask[i] || labels[i] != 0) continue;
            label++;
            int end = 1; queue[0] = i; labels[i] = label;
            for (int start = 0; start < end; start++) {
                for (int direction = 0; direction < 4; direction++) {
                    int neighbor = neighbor(queue[start], direction);
                    if (mask[neighbor] && labels[neighbor] == 0) {
                        labels[neighbor] = label; queue[end++] = neighbor;
                    }
                }
            }
            sizes[label] = end;
            largest = Math.max(largest, end);
        }
        float minimum = Math.max(5f, largest * 0.015f);
        for (int i = 0; i < PIXELS; i++) mask[i] = mask[i] && sizes[labels[i]] >= minimum;
    }

    private static int neighbor(int i, int direction) {
        // Clamped duplicates are harmless: pixels are marked before enqueueing.
        switch (direction) {
            case 0: return i % WIDTH > 0 ? i - 1 : i;
            case 1: return i % WIDTH < WIDTH - 1 ? i + 1 : i;
            case 2: return i >= WIDTH ? i - WIDTH : i;
            default: return i < PIXELS - WIDTH ? i + WIDTH : i;
        }
    }

    private static boolean[] convexEnvelope(boolean[] mask, int count) {
        int[] points = new int[count];
        int n = 0;
        for (int x = 0; x < WIDTH; x++) for (int y = 0; y < HEIGHT; y++) {
            if (mask[y * WIDTH + x]) points[n++] = y * WIDTH + x;
        }
        int[] hull = new int[2 * n + 1];
        int size = 0;
        for (int p : points) {
            while (size >= 2 && cross(hull[size - 2], hull[size - 1], p) <= 0) size--;
            hull[size++] = p;
        }
        int lower = size + 1;
        for (int i = n - 2; i >= 0; i--) {
            int p = points[i];
            while (size >= lower && cross(hull[size - 2], hull[size - 1], p) <= 0) size--;
            hull[size++] = p;
        }
        boolean[] result = new boolean[PIXELS];
        for (int i = 0; i < PIXELS; i++) {
            boolean inside = true;
            for (int edge = 0; edge < size - 1; edge++) {
                if (cross(hull[edge], hull[edge + 1], i) < 0) { inside = false; break; }
            }
            result[i] = inside;
        }
        return result;
    }

    private static int cross(int o, int a, int b) {
        return (a % WIDTH - o % WIDTH) * (b / WIDTH - o / WIDTH)
                - (a / WIDTH - o / WIDTH) * (b % WIDTH - o % WIDTH);
    }

    private static void sample(int[] source, int width, int height, float x, float y,
                               float[] output, int offset) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        float fx = x - ix, fy = y - iy;
        int a = source[clamp(iy, height) * width + clamp(ix, width)];
        int b = source[clamp(iy, height) * width + clamp(ix + 1, width)];
        int c = source[clamp(iy + 1, height) * width + clamp(ix, width)];
        int d = source[clamp(iy + 1, height) * width + clamp(ix + 1, width)];
        for (int channel = 0; channel < 3; channel++) {
            int shift = (2 - channel) * 8;
            output[offset + channel] = (1 - fy) * ((1 - fx) * ((a >> shift) & 255)
                    + fx * ((b >> shift) & 255)) + fy * ((1 - fx) * ((c >> shift) & 255)
                    + fx * ((d >> shift) & 255));
        }
    }

    private static void interpolateColors(float[] colors, float x, float y, float[] output) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        float fx = x - ix, fy = y - iy;
        int a = (clamp(iy, HEIGHT) * WIDTH + clamp(ix, WIDTH)) * 3;
        int b = (clamp(iy, HEIGHT) * WIDTH + clamp(ix + 1, WIDTH)) * 3;
        int c = (clamp(iy + 1, HEIGHT) * WIDTH + clamp(ix, WIDTH)) * 3;
        int d = (clamp(iy + 1, HEIGHT) * WIDTH + clamp(ix + 1, WIDTH)) * 3;
        for (int channel = 0; channel < 3; channel++) {
            output[channel] = (1 - fy) * ((1 - fx) * colors[a + channel] + fx * colors[b + channel])
                    + fy * ((1 - fx) * colors[c + channel] + fx * colors[d + channel]);
        }
    }

    private static float interpolateMask(boolean[] mask, float x, float y) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        float fx = x - ix, fy = y - iy;
        return (1 - fy) * ((1 - fx) * (mask[clamp(iy, HEIGHT) * WIDTH + clamp(ix, WIDTH)] ? 1 : 0)
                + fx * (mask[clamp(iy, HEIGHT) * WIDTH + clamp(ix + 1, WIDTH)] ? 1 : 0))
                + fy * ((1 - fx) * (mask[clamp(iy + 1, HEIGHT) * WIDTH + clamp(ix, WIDTH)] ? 1 : 0)
                + fx * (mask[clamp(iy + 1, HEIGHT) * WIDTH + clamp(ix + 1, WIDTH)] ? 1 : 0));
    }

    private static void normalizeHistogram(float[] histogram) {
        float sum = 0f;
        for (float v : histogram) sum += v;
        if (sum > 0f) for (int i = 0; i < histogram.length; i++) {
            histogram[i] = (float) Math.sqrt(histogram[i] / sum);
        }
    }

    private static double dot(float[] a, float[] b) {
        double result = 0;
        for (int i = 0; i < a.length; i++) result += a[i] * b[i];
        return Math.max(0, Math.min(1, result));
    }

    private static int clamp(int value, int length) {
        return Math.max(0, Math.min(length - 1, value));
    }

    private static final class Signature {
        final float[] spatial, hue, value;
        final boolean unlocked, complete;

        Signature(float[] spatial, float[] hue, float[] value, boolean unlocked, boolean complete) {
            this.spatial = spatial; this.hue = hue; this.value = value;
            this.unlocked = unlocked; this.complete = complete;
        }
    }
}
