package ua.borlock.autotap;

/** Per-run empirical model of the tile rank spawned after a confirmed move. */
final class MergeMadnessSpawnModel {
    private static final double PRIOR_RANK_ONE_COUNT = 9.0;
    private static final double PRIOR_RANK_TWO_COUNT = 1.0;

    private double rankOneCount = PRIOR_RANK_ONE_COUNT;
    private double rankTwoCount = PRIOR_RANK_TWO_COUNT;

    synchronized void reset() {
        rankOneCount = PRIOR_RANK_ONE_COUNT;
        rankTwoCount = PRIOR_RANK_TWO_COUNT;
    }

    synchronized double rankOneProbability() {
        return rankOneCount / (rankOneCount + rankTwoCount);
    }

    synchronized double rankTwoProbability() {
        return rankTwoCount / (rankOneCount + rankTwoCount);
    }

    synchronized int confirmedSamples() {
        return (int) Math.round(rankOneCount + rankTwoCount
                - PRIOR_RANK_ONE_COUNT - PRIOR_RANK_TWO_COUNT);
    }

    synchronized void observeConfirmedRank(int rank) {
        if (rank == 1) rankOneCount++;
        else if (rank == 2) rankTwoCount++;
    }

    synchronized boolean observeIfConfirmed(MergeMadnessTransition transition) {
        if (transition == null || !transition.canTrainSpawnModel()) return false;
        observeConfirmedRank(transition.spawnedRank);
        return true;
    }
}
