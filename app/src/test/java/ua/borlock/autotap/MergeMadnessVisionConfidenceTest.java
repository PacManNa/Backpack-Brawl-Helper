package ua.borlock.autotap;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MergeMadnessVisionConfidenceTest {
    @Test public void usesSeparateDirectThresholdsForLowAndHighRanks() {
        assertTrue(MergeMadnessVision.isConfidentTemplateMatch(
                new MergeMadnessVision.TileMatch(1, 0.41, 0.20)));
        assertFalse(MergeMadnessVision.isConfidentTemplateMatch(
                new MergeMadnessVision.TileMatch(1, 0.43, 0.03)));
        assertFalse(MergeMadnessVision.isConfidentTemplateMatch(
                new MergeMadnessVision.TileMatch(8, 0.59, 0.20)));
        assertTrue(MergeMadnessVision.isConfidentTemplateMatch(
                new MergeMadnessVision.TileMatch(1, 0.66, 0.12)));
        assertTrue(MergeMadnessVision.isConfidentTemplateMatch(
                new MergeMadnessVision.TileMatch(1, 0.46, 0.04, true)));
        assertFalse(MergeMadnessVision.isConfidentTemplateMatch(
                new MergeMadnessVision.TileMatch(1, 0.46, 0.02, true)));
    }

    @Test public void comparesProgressionMatchesAcrossRowsAndRejectsAmbiguity() {
        double[] scores = new double[17];
        boolean[] visible = new boolean[17];
        visible[0] = true;
        visible[6] = true;
        scores[0] = 0.43;
        scores[6] = 0.42;

        MergeMadnessVision.TileMatch ambiguous =
                MergeMadnessVision.bestVisibleProgressionMatch(scores, visible);

        assertEquals(0, ambiguous.rank);
    }

    @Test public void acceptsClearGlobalProgressionWinner() {
        double[] scores = new double[17];
        boolean[] visible = new boolean[17];
        visible[0] = true;
        visible[1] = true;
        visible[6] = true;
        scores[0] = 0.62;
        scores[1] = 0.31;
        scores[6] = 0.28;

        MergeMadnessVision.TileMatch match =
                MergeMadnessVision.bestVisibleProgressionMatch(scores, visible);

        assertEquals(1, match.rank);
        assertTrue(MergeMadnessVision.isConfidentTemplateMatch(
                new MergeMadnessVision.TileMatch(match.rank, match.score, match.margin)));
    }

    @Test public void rejectsProgressionMatchWhenOnlyOneRankIsVisible() {
        double[] scores = new double[17];
        boolean[] visible = new boolean[17];
        visible[0] = true;
        scores[0] = 0.90;

        assertEquals(0, MergeMadnessVision.bestVisibleProgressionMatch(
                scores, visible).rank);
    }

    @Test public void visibleChainOverridesConfidentWrongLowRankTemplate() {
        MergeMadnessVision.TileMatch direct =
                new MergeMadnessVision.TileMatch(7, 0.80, 0.16);
        MergeMadnessVision.TileMatch chain =
                new MergeMadnessVision.TileMatch(1, 0.42, 0.09);

        assertEquals(1, MergeMadnessVision.chooseTileMatch(direct, chain).rank);
    }

    @Test public void unrelatedLearnedMouseDoesNotEraseConfidentBuiltinMatch() {
        double[] builtin = new double[17];
        double[] learned = new double[17];
        builtin[0] = 0.48;
        builtin[1] = 0.435;
        learned[1] = 0.47;

        MergeMadnessVision.TileMatch match =
                MergeMadnessVision.bestTemplateMatchForTest(builtin, learned);

        assertEquals(1, match.rank);
    }

    @Test public void learnedMouseStillRecognizesWhenBuiltinTemplatesAreAmbiguous() {
        double[] builtin = new double[17];
        double[] learned = new double[17];
        builtin[0] = 0.50;
        builtin[1] = 0.49;
        learned[5] = 0.82;

        MergeMadnessVision.TileMatch match =
                MergeMadnessVision.bestTemplateMatchForTest(builtin, learned);

        assertEquals(6, match.rank);
        assertTrue(match.customSample);
    }

    @Test public void visibleChainOverridesConflictingLearnedSample() {
        MergeMadnessVision.TileMatch direct =
                new MergeMadnessVision.TileMatch(6, 0.82, 0.30, true);
        MergeMadnessVision.TileMatch chain =
                new MergeMadnessVision.TileMatch(1, 0.62, 0.25);

        assertEquals(1, MergeMadnessVision.chooseTileMatch(direct, chain).rank);
    }
}
