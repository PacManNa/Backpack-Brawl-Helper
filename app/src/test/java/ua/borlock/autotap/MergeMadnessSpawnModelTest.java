package ua.borlock.autotap;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MergeMadnessSpawnModelTest {
    @Test public void startsAtNinetyTenAndLearnsOnlyConfirmedSingleMergeTransitions() {
        MergeMadnessSpawnModel model = new MergeMadnessSpawnModel();
        assertEquals(0.90, model.rankOneProbability(), 0.000001);
        assertEquals(0.10, model.rankTwoProbability(), 0.000001);

        MergeMadnessBoard before = rowBoard(new int[]{1, 1, 2, 0});
        MergeMadnessBoard after = before.slide(MergeMadnessSolver.Direction.LEFT).board
                .withTile(3, 2);
        MergeMadnessTransition confirmed = MergeMadnessTransition.analyze(
                before, MergeMadnessSolver.Direction.LEFT, after);
        assertTrue(model.observeIfConfirmed(confirmed));
        assertEquals(1, model.confirmedSamples());
        assertEquals(9.0 / 11.0, model.rankOneProbability(), 0.000001);
        assertEquals(2.0 / 11.0, model.rankTwoProbability(), 0.000001);

        MergeMadnessTransition invalid = MergeMadnessTransition.analyze(
                before, MergeMadnessSolver.Direction.LEFT,
                before.slide(MergeMadnessSolver.Direction.LEFT).board);
        assertFalse(model.observeIfConfirmed(invalid));
        assertEquals(1, model.confirmedSamples());

        model.reset();
        assertEquals(0, model.confirmedSamples());
        assertEquals(0.90, model.rankOneProbability(), 0.000001);
    }

    private MergeMadnessBoard rowBoard(int[] line) {
        int[] values = new int[MergeMadnessBoard.CELL_COUNT];
        System.arraycopy(line, 0, values, 0, line.length);
        return new MergeMadnessBoard(values);
    }
}
