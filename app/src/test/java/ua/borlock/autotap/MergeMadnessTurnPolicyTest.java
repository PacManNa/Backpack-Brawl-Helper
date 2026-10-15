package ua.borlock.autotap;

import org.junit.Test;
import static org.junit.Assert.*;

public final class MergeMadnessTurnPolicyTest {
    private final BoardVision.Geometry geometry = new BoardVision.Geometry(108f, 497f, 120f, 120f);
    private final MergeMadnessBoard before = new MergeMadnessBoard(new int[]{
            1,1,2,0, 0,0,0,0, 0,0,0,0, 0,0,0,0
    });

    @Test public void acceptsExactlyOneLegalMoveAndSpawnAfterSettling() {
        MergeMadnessTransition transition = validTransition();
        assertTrue(MergeMadnessTurnPolicy.canAdvance(transition,0.824,geometry,geometry,180L));
    }

    @Test public void waitsForTwoFramesOnInitialOrUnchangedBoards() {
        assertFalse(MergeMadnessTurnPolicy.canAdvance(null,0.99,geometry,geometry,500L));
        MergeMadnessTransition unchanged = MergeMadnessTransition.analyze(before,
                MergeMadnessSolver.Direction.LEFT,before);
        assertFalse(MergeMadnessTurnPolicy.canAdvance(unchanged,0.99,geometry,geometry,500L));
    }

    @Test public void rejectsUnexpectedExtraTilesEvenWithHighConfidence() {
        MergeMadnessBoard wrong = before.slide(MergeMadnessSolver.Direction.LEFT).board
                .withTile(2,1).withTile(3,2);
        MergeMadnessTransition transition = MergeMadnessTransition.analyze(before,
                MergeMadnessSolver.Direction.LEFT,wrong);
        assertFalse(MergeMadnessTurnPolicy.canAdvance(transition,0.99,geometry,geometry,500L));
    }

    @Test public void requiresSettledGeometryAfterAChainRowMovesTheBoard() {
        BoardVision.Geometry shifted = new BoardVision.Geometry(108f,437f,120f,120f);
        assertFalse(MergeMadnessTurnPolicy.canAdvance(validTransition(),0.99,
                geometry,shifted,500L));
        assertFalse(MergeMadnessTurnPolicy.canAdvance(validTransition(),0.99,
                null,geometry,500L));
    }

    @Test public void waitsForAnimationAndRejectsWeakOrInvalidConfidence() {
        assertFalse(MergeMadnessTurnPolicy.canAdvance(validTransition(),0.99,
                geometry,geometry,179L));
        assertFalse(MergeMadnessTurnPolicy.canAdvance(validTransition(),0.81,
                geometry,geometry,500L));
        assertFalse(MergeMadnessTurnPolicy.canAdvance(validTransition(),Double.NaN,
                geometry,geometry,500L));
        assertFalse(MergeMadnessTurnPolicy.canAdvance(validTransition(),Double.POSITIVE_INFINITY,
                geometry,geometry,500L));
    }

    private MergeMadnessTransition validTransition() {
        return MergeMadnessTransition.analyze(before,MergeMadnessSolver.Direction.LEFT,
                before.slide(MergeMadnessSolver.Direction.LEFT).board.withTile(3,1));
    }
}
