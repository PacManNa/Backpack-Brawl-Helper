package ua.borlock.autotap;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import javax.imageio.ImageIO;

/** Recognition-only host benchmark; capture and physical phone timings are excluded. */
public final class MergeMadnessPerformanceCheck {
    public static void main(String[] args) throws Exception {
        File raw = new File(args[1]);
        MergeMadnessVision.PixelImage[] high = new MergeMadnessVision.PixelImage[6];
        for (int i = 0; i < high.length; i++) high[i] = read(new File(raw,
                "merge_madness_rank" + (i + 5) + "_sample.jpg"));
        MergeMadnessVision vision = MergeMadnessVision.forScreenshotCheck(
                read(new File(raw, "merge_madness_guide.jpg")),
                read(new File(raw, "merge_madness_gameplay_reference.jpg")),
                read(new File(raw, "merge_madness_gameplay_sample.jpg")), high);
        BufferedImage source = ImageIO.read(new File(args[0], "1000011196.jpg"));
        int iterations = args.length > 2 ? Integer.parseInt(args[2]) : 40;
        int[] expected = {0,0,0,1, 0,1,2,3, 0,0,4,6, 1,5,7,9};
        for (int[] size : new int[][]{{576,1280},{1080,2400}}) {
            BufferedImage image = new BufferedImage(size[0],size[1],BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source,0,0,size[0],size[1],null);
            graphics.dispose();
            int[] pixels = image.getRGB(0,0,size[0],size[1],null,0,size[0]);
            for (int i = 0; i < 12; i++) vision.recognizePixels(pixels,size[0],size[1],null,null);
            long[] times = new long[iterations];
            double weakest = 1.0;
            for (int i = 0; i < iterations; i++) {
                long started = System.nanoTime();
                MergeMadnessVision.Result result = vision.recognizePixels(
                        pixels,size[0],size[1],null,null);
                times[i] = System.nanoTime() - started;
                if (!result.isSuccess() || !Arrays.equals(expected,result.board.copyRanks())) {
                    throw new AssertionError("Recognition changed during benchmark: " + result.error);
                }
                weakest = Math.min(weakest,result.similarity);
            }
            Arrays.sort(times);
            System.out.printf(java.util.Locale.ROOT,
                    "recognition %dx%d: median=%.3f ms p95=%.3f ms confidence=%.3f iterations=%d%n",
                    size[0],size[1],times[times.length / 2] / 1e6,
                    times[Math.min(times.length - 1,(int)(times.length * 0.95))] / 1e6,
                    weakest, iterations);
        }
    }

    private static MergeMadnessVision.PixelImage read(File file) throws Exception {
        BufferedImage image = ImageIO.read(file);
        int width = image.getWidth(), height = image.getHeight();
        return new MergeMadnessVision.PixelImage(image.getRGB(0,0,width,height,null,0,width),
                width,height);
    }
}
