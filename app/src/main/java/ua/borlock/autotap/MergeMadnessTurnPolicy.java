package ua.borlock.autotap;

/** Accepts an already verified post-swipe board without requesting a duplicate frame. */
final class MergeMadnessTurnPolicy {
    static final long MIN_SETTLE_MS = 180L;
    private static final double MIN_CONFIDENCE = 0.82;

    private MergeMadnessTurnPolicy() { }

    static boolean canAdvance(MergeMadnessTransition transition, double confidence,
                               BoardVision.Geometry previousGeometry,
                               BoardVision.Geometry currentGeometry, long elapsedSinceSwipeMs) {
        return transition != null && transition.expected
                && Double.isFinite(confidence) && confidence >= MIN_CONFIDENCE
                && elapsedSinceSwipeMs >= MIN_SETTLE_MS
                && MergeMadnessLayout.isStable(previousGeometry, currentGeometry);
    }
}
