package ua.borlock.autotap;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Random;

/** Prefers the largest immediate clear, then bonuses and cascades. */
final class GameSolver {
    private static final int MAX_CASCADE_STEPS = 5;
    private static final double CASCADE_WEIGHT = 0.62;
    // Video popups scale with cleared cells: 200 for 4, 250 for 5, 500 for 10.
    private static final int POINTS_PER_JEWEL = 50;
    private static final int REFILL_LOOKAHEAD_CANDIDATES = 3;
    private static final int REFILL_LOOKAHEAD_SAMPLES = 4;
    private static final int REFILL_TIEBREAK_SAMPLES = 8;
    private static final double REFILL_LOOKAHEAD_SCORE_WEIGHT = 0.45;
    private static final double SPECIAL_FUTURE_VALUE_WEIGHT = 0.35;
    private static final double NEAR_MATCH_TIEBREAK = 2.0;
    private static final double CLOSE_SCORE_RATIO = 0.08;
    private static final double CLOSE_SCORE_MINIMUM = 75.0;

    Choice chooseMove(GameBoard board) {
        if (board.hasAnyMatch()) {
            return Choice.waitForResolution(findMatches(board.copyTiles()).count);
        }

        int[] tiles = board.copyTiles();
        long boardSeed = board.hashCode();
        List<ImmediateSwap> largestSwaps = new ArrayList<>();
        int largestImmediateClear = -1;
        int candidateMoves = 0;
        for (int row = 0; row < GameBoard.SIDE; row++) {
            for (int column = 0; column < GameBoard.SIDE; column++) {
                int first = row * GameBoard.SIDE + column;
                for (int direction = 0; direction < 2; direction++) {
                    if (direction == 0 && column + 1 == GameBoard.SIDE) continue;
                    if (direction == 1 && row + 1 == GameBoard.SIDE) continue;
                    int second = first + (direction == 0 ? 1 : GameBoard.SIDE);
                    ImmediateSwap swap = inspectSwap(board, tiles, boardSeed, first, second);
                    if (swap == null) continue;
                    candidateMoves++;
                    if (swap.matchedCount > largestImmediateClear) {
                        largestImmediateClear = swap.matchedCount;
                        largestSwaps.clear();
                    }
                    if (swap.matchedCount == largestImmediateClear) {
                        largestSwaps.add(swap);
                    }
                }
            }
        }
        if (largestSwaps.isEmpty()) return Choice.noMove(candidateMoves);

        Candidate best = null;
        List<Candidate> refillCandidates = new ArrayList<>(REFILL_LOOKAHEAD_CANDIDATES);
        for (ImmediateSwap swap : largestSwaps) {
            Candidate candidate = swap.scored != null ? swap.scored
                    : scoreSwap(board, boardSeed, swap.firstCell, swap.secondCell);
            if (isBetterImmediate(candidate, best)) best = candidate;
            if (largestSwaps.size() > 1) {
                candidate.preliminaryScore = candidate.choice.score
                        + firstRefillScore(candidate) * REFILL_LOOKAHEAD_SCORE_WEIGHT;
            }
            keepRefillCandidate(refillCandidates, candidate);
        }
        // A unique largest clear is already decided; refill simulations cannot change it.
        if (largestSwaps.size() == 1) return best.choice.withCandidateCount(candidateMoves);

        List<Candidate> ranked = new ArrayList<>(refillCandidates.size());
        for (Candidate candidate : refillCandidates) {
            candidate.rankedChoice = candidate.choice.withScore(candidate.choice.score
                    + expectedRefillCascadeScore(candidate, REFILL_LOOKAHEAD_SAMPLES)
                    * REFILL_LOOKAHEAD_SCORE_WEIGHT);
            ranked.add(candidate);
        }
        sortByRankedScore(ranked);
        if (ranked.size() > 1) {
            double firstScore = ranked.get(0).rankedChoice.score;
            double secondScore = ranked.get(1).rankedChoice.score;
            if (ranked.get(0).choice.matchedCount == ranked.get(1).choice.matchedCount
                    && firstScore - secondScore <= Math.max(CLOSE_SCORE_MINIMUM,
                    Math.abs(firstScore) * CLOSE_SCORE_RATIO)) {
                for (int index = 0; index < Math.min(2, ranked.size()); index++) {
                    Candidate candidate = ranked.get(index);
                    candidate.rankedChoice = candidate.choice.withScore(candidate.choice.score
                            + expectedRefillCascadeScore(candidate, REFILL_TIEBREAK_SAMPLES)
                            * REFILL_LOOKAHEAD_SCORE_WEIGHT);
                }
            }
        }
        Choice selected = best.choice;
        double selectedScore = selected.score;
        for (Candidate candidate : refillCandidates) {
            Choice rankedChoice = candidate.rankedChoice;
            if (rankedChoice != null && (rankedChoice.matchedCount > selected.matchedCount
                    || (rankedChoice.matchedCount == selected.matchedCount
                    && rankedChoice.score > selectedScore))) {
                selected = rankedChoice;
                selectedScore = rankedChoice.score;
            }
        }
        return selected.withCandidateCount(candidateMoves);
    }

    private ImmediateSwap inspectSwap(GameBoard board, int[] tiles, long boardSeed,
                                      int first, int second) {
        int firstType = tiles[first];
        int secondType = tiles[second];
        if (firstType == secondType) return null;
        if (isSpecial(firstType) || isSpecial(secondType)) {
            Candidate candidate = scoreSwap(board, boardSeed, first, second);
            return new ImmediateSwap(first, second, candidate.choice.matchedCount, candidate);
        }
        tiles[first] = secondType;
        tiles[second] = firstType;
        long matched = matchMaskAt(tiles, first) | matchMaskAt(tiles, second);
        tiles[first] = firstType;
        tiles[second] = secondType;
        int count = Long.bitCount(matched);
        return count == 0 ? null : new ImmediateSwap(first, second, count, null);
    }

    /** On a stable board every new match must pass through a swapped cell. */
    private long matchMaskAt(int[] tiles, int cell) {
        int color = tiles[cell];
        if (!isGemColor(color)) return 0L;
        int row = cell / GameBoard.SIDE;
        int column = cell % GameBoard.SIDE;
        int left = column;
        int right = column;
        while (left > 0 && tiles[row * GameBoard.SIDE + left - 1] == color) left--;
        while (right + 1 < GameBoard.SIDE
                && tiles[row * GameBoard.SIDE + right + 1] == color) right++;
        long mask = 0L;
        if (right - left >= 2) {
            for (int c = left; c <= right; c++) mask |= 1L << (row * GameBoard.SIDE + c);
        }
        int top = row;
        int bottom = row;
        while (top > 0 && tiles[(top - 1) * GameBoard.SIDE + column] == color) top--;
        while (bottom + 1 < GameBoard.SIDE
                && tiles[(bottom + 1) * GameBoard.SIDE + column] == color) bottom++;
        if (bottom - top >= 2) {
            for (int r = top; r <= bottom; r++) mask |= 1L << (r * GameBoard.SIDE + column);
        }
        return mask;
    }

    private void sortByRankedScore(List<Candidate> candidates) {
        for (int i = 1; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            int index = i;
            while (index > 0 && isBetterRanked(candidate,
                    candidates.get(index - 1))) {
                candidates.set(index, candidates.get(index - 1));
                index--;
            }
            candidates.set(index, candidate);
        }
    }

    private void keepRefillCandidate(List<Candidate> candidates, Candidate candidate) {
        int insertion = 0;
        while (insertion < candidates.size()
                && candidates.get(insertion).preliminaryScore >= candidate.preliminaryScore) insertion++;
        if (insertion >= REFILL_LOOKAHEAD_CANDIDATES) return;
        candidates.add(insertion, candidate);
        if (candidates.size() > REFILL_LOOKAHEAD_CANDIDATES) {
            candidates.remove(candidates.size() - 1);
        }
    }

    private boolean isBetterImmediate(Candidate candidate, Candidate current) {
        if (current == null) return true;
        if (candidate.choice.matchedCount != current.choice.matchedCount) {
            return candidate.choice.matchedCount > current.choice.matchedCount;
        }
        return candidate.choice.score > current.choice.score;
    }

    private boolean isBetterRanked(Candidate candidate, Candidate current) {
        if (candidate.rankedChoice.matchedCount != current.rankedChoice.matchedCount) {
            return candidate.rankedChoice.matchedCount > current.rankedChoice.matchedCount;
        }
        return candidate.rankedChoice.score > current.rankedChoice.score;
    }

    private double expectedRefillCascadeScore(Candidate candidate, int sampleCount) {
        int remainingSteps = MAX_CASCADE_STEPS - candidate.steps;
        if (remainingSteps <= 0) return 0;

        if (candidate.refillRandom == null) {
            candidate.refillRandom = new Random(candidate.seed ^ 0x9E3779B97F4A7C15L);
        }
        for (int sample = candidate.refillSamples; sample < sampleCount; sample++) {
            int[] tiles = candidate.settledTiles.clone();
            fillEmptyCells(tiles, candidate.refillRandom);
            double weight = Math.pow(CASCADE_WEIGHT, candidate.steps);
            for (int step = 0; step < remainingSteps; step++) {
                MatchAnalysis matches = findMatches(tiles);
                if (matches.count == 0) break;
                MatchResolution resolution = resolveMatches(tiles, matches, -1, -1);
                // The first clear has an exact expectation. Sample only bonuses and
                // later cascades, so four lucky refills cannot distort this part.
                double sampledScore = resolution.score;
                if (step == 0) sampledScore -= matches.count * POINTS_PER_JEWEL;
                candidate.refillResidualTotal += sampledScore * weight;
                tiles = dropMatches(tiles, resolution.clearedCells);
                fillEmptyCells(tiles, candidate.refillRandom);
                weight *= CASCADE_WEIGHT;
            }
            candidate.refillSamples++;
        }
        return firstRefillScore(candidate)
                + candidate.refillResidualTotal / candidate.refillSamples;
    }

    private double firstRefillScore(Candidate candidate) {
        if (candidate.steps >= MAX_CASCADE_STEPS) return 0;
        if (Double.isNaN(candidate.firstRefillScore)) {
            candidate.firstRefillScore = expectedRefillMatchCount(candidate.settledTiles)
                    * POINTS_PER_JEWEL * Math.pow(CASCADE_WEIGHT, candidate.steps);
        }
        return candidate.firstRefillScore;
    }

    /** Exact expected unique matched gems after independent uniform seven-color refills. */
    static double expectedRefillMatchCount(int[] tiles) {
        double expected = 0;
        for (int cell = 0; cell < GameBoard.CELL_COUNT; cell++) {
            int tile = tiles[cell];
            if (tile != 0 && !isGemColor(tile)) continue;
            int firstColor = tile == 0 ? GameBoard.RED : tile;
            int lastColor = tile == 0 ? GameBoard.ORANGE : tile;
            double colorProbability = tile == 0 ? 1.0 / 7 : 1.0;
            int row = cell / GameBoard.SIDE;
            int column = cell % GameBoard.SIDE;
            for (int color = firstColor; color <= lastColor; color++) {
                double horizontal = lineMatchProbability(
                        column >= 2 ? equalColorProbability(tiles[cell - 2], color) : 0,
                        column >= 1 ? equalColorProbability(tiles[cell - 1], color) : 0,
                        column + 1 < GameBoard.SIDE ? equalColorProbability(tiles[cell + 1], color) : 0,
                        column + 2 < GameBoard.SIDE ? equalColorProbability(tiles[cell + 2], color) : 0);
                double vertical = lineMatchProbability(
                        row >= 2 ? equalColorProbability(tiles[cell - 16], color) : 0,
                        row >= 1 ? equalColorProbability(tiles[cell - 8], color) : 0,
                        row + 1 < GameBoard.SIDE ? equalColorProbability(tiles[cell + 8], color) : 0,
                        row + 2 < GameBoard.SIDE ? equalColorProbability(tiles[cell + 16], color) : 0);
                expected += colorProbability * (horizontal + vertical - horizontal * vertical);
            }
        }
        return expected;
    }

    private static double equalColorProbability(int tile, int color) {
        return tile == 0 ? 1.0 / 7 : tile == color ? 1.0 : 0.0;
    }

    private static double lineMatchProbability(double left2, double left1,
                                                double right1, double right2) {
        return left1 * right1 + left1 * (1 - right1) * left2
                + (1 - left1) * right1 * right2;
    }

    private void fillEmptyCells(int[] tiles, Random random) {
        for (int index = 0; index < tiles.length; index++) {
            if (tiles[index] == 0) {
                tiles[index] = GameBoard.RED
                        + random.nextInt(GameBoard.ORANGE - GameBoard.RED + 1);
            }
        }
    }

    private Candidate scoreSwap(GameBoard board, long boardSeed, int firstCell, int secondCell) {
        int firstType = board.colorAt(firstCell);
        int secondType = board.colorAt(secondCell);
        if (firstType == secondType) return null;
        int[] swapped = board.copyTiles();
        swapped[firstCell] = secondType;
        swapped[secondCell] = firstType;
        long seed = boardSeed;
        seed = seed * 31 + firstCell;
        seed = seed * 31 + secondCell;
        Random random = new Random(seed);
        double score = 0;
        int matchedCount = 0;
        int cascadeCount = 0;
        int steps = 0;
        double weight = 1.0;
        int primarySpecial = 0;
        MatchAnalysis matches;

        if (isSpecial(firstType) || isSpecial(secondType)) {
            primarySpecial = isSpecial(firstType) ? firstType : secondType;
            List<SpecialActivation> roots = new ArrayList<>(2);
            if (isSpecial(firstType)) {
                roots.add(new SpecialActivation(secondCell, firstType,
                        isGemColor(secondType) ? secondType : 0));
            }
            if (isSpecial(secondType)) {
                roots.add(new SpecialActivation(firstCell, secondType,
                        isGemColor(firstType) ? firstType : 0));
            }
            SpecialResolution activation = activateSpecials(swapped, roots, random);
            matchedCount = activation.gemCount;
            score += activation.gemCount * POINTS_PER_JEWEL;
            swapped = dropMatches(swapped, activation.clearedCells);
            steps = 1;
            weight = CASCADE_WEIGHT;
            matches = findMatches(swapped);
        } else {
            matches = findMatches(swapped);
            if (matches.count == 0) return null;
            matchedCount = matches.count;
        }

        while (steps < MAX_CASCADE_STEPS && matches.count > 0) {
            if (steps > 0) cascadeCount++;
            MatchResolution resolution = resolveMatches(swapped, matches,
                    steps == 0 ? firstCell : -1, steps == 0 ? secondCell : -1);
            score += resolution.score * weight;
            swapped = dropMatches(swapped, resolution.clearedCells);
            matches = findMatches(swapped);
            steps++;
            weight *= CASCADE_WEIGHT;
        }
        score += futureOpportunities(swapped) * NEAR_MATCH_TIEBREAK;
        Choice choice = new Choice(firstCell, secondCell, matchedCount, cascadeCount, score,
                false, 0, 0, primarySpecial);
        return new Candidate(choice, swapped, steps, seed);
    }

    private SpecialResolution activateSpecials(int[] tiles, List<SpecialActivation> roots,
                                               Random random) {
        boolean[] cleared = new boolean[GameBoard.CELL_COUNT];
        boolean[] activated = new boolean[GameBoard.CELL_COUNT];
        boolean[] queued = new boolean[GameBoard.CELL_COUNT];
        ArrayDeque<SpecialActivation> queue = new ArrayDeque<>();
        for (SpecialActivation root : roots) {
            if (root.cell >= 0 && root.cell < GameBoard.CELL_COUNT && !queued[root.cell]) {
                queue.addLast(root);
                queued[root.cell] = true;
            }
        }

        while (!queue.isEmpty()) {
            SpecialActivation activation = queue.removeFirst();
            int cell = activation.cell;
            if (activated[cell]) continue;
            activated[cell] = true;
            int type = activation.type;
            int row = cell / GameBoard.SIDE;
            int column = cell % GameBoard.SIDE;
            if (type == GameBoard.ROCKET) {
                // Vision recognizes the rocket, but not its stripe direction.
                boolean vertical = random.nextBoolean();
                for (int offset = 0; offset < GameBoard.SIDE; offset++) {
                    int target = vertical
                            ? offset * GameBoard.SIDE + column
                            : row * GameBoard.SIDE + offset;
                    addClearCell(target, tiles, cleared, queued, queue);
                }
            } else if (type == GameBoard.BOMB) {
                for (int r = Math.max(0, row - 1); r <= Math.min(GameBoard.SIDE - 1, row + 1); r++) {
                    for (int c = Math.max(0, column - 1); c <= Math.min(GameBoard.SIDE - 1, column + 1); c++) {
                        addClearCell(r * GameBoard.SIDE + c, tiles, cleared, queued, queue);
                    }
                }
            } else if (type == GameBoard.PORTAL) {
                int color = isGemColor(activation.targetColor)
                        ? activation.targetColor : mostCommonGemColor(tiles);
                addClearCell(cell, tiles, cleared, queued, queue);
                for (int index = 0; index < tiles.length; index++) {
                    if (tiles[index] == color) {
                        addClearCell(index, tiles, cleared, queued, queue);
                    }
                }
            }
        }

        int gemCount = 0;
        for (int index = 0; index < cleared.length; index++) {
            if (cleared[index] && isGemColor(tiles[index])) gemCount++;
        }
        return new SpecialResolution(cleared, gemCount);
    }

    private void addClearCell(int cell, int[] tiles, boolean[] cleared, boolean[] queued,
                              ArrayDeque<SpecialActivation> queue) {
        cleared[cell] = true;
        int type = tiles[cell];
        if (isSpecial(type) && !queued[cell]) {
            queue.addLast(new SpecialActivation(cell, type, 0));
            queued[cell] = true;
        }
    }

    private MatchResolution resolveMatches(int[] tiles, MatchAnalysis matches,
                                           int preferredFirst, int preferredSecond) {
        boolean[] cleared = matches.matchedCells.clone();
        boolean[] spawned = new boolean[GameBoard.CELL_COUNT];
        double score = matches.count * POINTS_PER_JEWEL;
        for (SpecialSpawn spawn : findSpecialSpawns(tiles, matches, preferredFirst, preferredSecond)) {
            if (spawned[spawn.cell]) continue;
            spawned[spawn.cell] = true;
            cleared[spawn.cell] = false;
            tiles[spawn.cell] = spawn.type;
            score += estimateSpecialClear(tiles, spawn.type, spawn.cell)
                    * POINTS_PER_JEWEL * SPECIAL_FUTURE_VALUE_WEIGHT;
        }
        return new MatchResolution(cleared, score);
    }

    private List<SpecialSpawn> findSpecialSpawns(int[] tiles, MatchAnalysis matches,
                                                 int preferredFirst, int preferredSecond) {
        List<SpecialSpawn> spawns = new ArrayList<>();
        boolean[] visited = new boolean[GameBoard.CELL_COUNT];
        int[] queue = new int[GameBoard.CELL_COUNT];
        for (int start = 0; start < GameBoard.CELL_COUNT; start++) {
            if (!matches.matchedCells[start] || visited[start]) continue;
            boolean[] component = new boolean[GameBoard.CELL_COUNT];
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            visited[start] = true;
            int color = tiles[start];
            int componentSize = 0;
            while (head < tail) {
                int cell = queue[head++];
                component[cell] = true;
                componentSize++;
                int row = cell / GameBoard.SIDE;
                int column = cell % GameBoard.SIDE;
                // Runs form one shape only where they overlap. Merely touching
                // rows or different colors must keep their independent bonuses.
                if (column > 0 && matches.horizontal[cell] && matches.horizontal[cell - 1]) {
                    tail = enqueueMatchCell(cell - 1, color, tiles, matches, visited, queue, tail);
                }
                if (column + 1 < GameBoard.SIDE && matches.horizontal[cell] && matches.horizontal[cell + 1]) {
                    tail = enqueueMatchCell(cell + 1, color, tiles, matches, visited, queue, tail);
                }
                if (row > 0 && matches.vertical[cell] && matches.vertical[cell - GameBoard.SIDE]) {
                    tail = enqueueMatchCell(cell - GameBoard.SIDE, color, tiles, matches, visited, queue, tail);
                }
                if (row + 1 < GameBoard.SIDE && matches.vertical[cell] && matches.vertical[cell + GameBoard.SIDE]) {
                    tail = enqueueMatchCell(cell + GameBoard.SIDE, color, tiles, matches, visited, queue, tail);
                }
            }

            int longestRun = 0;
            MatchRun bestRun = null;
            for (MatchRun run : matches.runs) {
                if (!component[run.start]) continue;
                if (run.length > longestRun) {
                    longestRun = run.length;
                    bestRun = run;
                }
            }
            int intersectionCell = -1;
            for (int index = 0; index < component.length; index++) {
                if (component[index] && matches.horizontal[index] && matches.vertical[index]) {
                    intersectionCell = index;
                    break;
                }
            }

            int spawnType;
            if (longestRun >= 5) {
                spawnType = GameBoard.PORTAL;
            } else if (intersectionCell >= 0 && componentSize >= 5) {
                spawnType = GameBoard.BOMB;
            } else if (longestRun >= 4) {
                spawnType = GameBoard.ROCKET;
            } else {
                continue;
            }

            int preferred = componentCell(preferredFirst, component) ? preferredFirst
                    : componentCell(preferredSecond, component) ? preferredSecond : -1;
            int spawnCell = preferred >= 0 ? preferred
                    : spawnType == GameBoard.BOMB && intersectionCell >= 0
                    ? intersectionCell : bestRun.centerCell();
            spawns.add(new SpecialSpawn(spawnCell, spawnType));
        }
        return spawns;
    }

    private int enqueueMatchCell(int cell, int color, int[] tiles, MatchAnalysis matches, boolean[] visited,
                                 int[] queue, int tail) {
        if (matches.matchedCells[cell] && tiles[cell] == color && !visited[cell]) {
            visited[cell] = true;
            queue[tail++] = cell;
        }
        return tail;
    }

    private boolean componentCell(int cell, boolean[] component) {
        return cell >= 0 && cell < component.length && component[cell];
    }

    private double estimateSpecialClear(int[] tiles, int specialType, int targetCell) {
        int row = targetCell / GameBoard.SIDE;
        int column = targetCell % GameBoard.SIDE;
        if (specialType == GameBoard.ROCKET) {
            int rowCount = 0;
            int columnCount = 0;
            for (int index = 0; index < GameBoard.SIDE; index++) {
                if (isGemColor(tiles[row * GameBoard.SIDE + index])) rowCount++;
                if (isGemColor(tiles[index * GameBoard.SIDE + column])) columnCount++;
            }
            return (rowCount + columnCount) / 2.0;
        }
        if (specialType == GameBoard.BOMB) {
            int count = 0;
            for (int r = Math.max(0, row - 1); r <= Math.min(GameBoard.SIDE - 1, row + 1); r++) {
                for (int c = Math.max(0, column - 1); c <= Math.min(GameBoard.SIDE - 1, column + 1); c++) {
                    if (isGemColor(tiles[r * GameBoard.SIDE + c])) count++;
                }
            }
            return count;
        }
        return mostCommonGemCount(tiles);
    }

    private int mostCommonGemColor(int[] tiles) {
        int mostCommon = GameBoard.RED;
        int[] counts = gemColorCounts(tiles);
        for (int color = GameBoard.RED + 1; color <= GameBoard.ORANGE; color++) {
            if (counts[color] > counts[mostCommon]) mostCommon = color;
        }
        return mostCommon;
    }

    private int mostCommonGemCount(int[] tiles) {
        int max = 0;
        for (int count : gemColorCounts(tiles)) max = Math.max(max, count);
        return max;
    }

    private int[] gemColorCounts(int[] tiles) {
        int[] colorCounts = new int[GameBoard.ORANGE + 1];
        for (int color : tiles) {
            if (isGemColor(color)) colorCounts[color]++;
        }
        return colorCounts;
    }

    private boolean isSpecial(int type) {
        return type == GameBoard.ROCKET || type == GameBoard.BOMB || type == GameBoard.PORTAL;
    }

    private static boolean isGemColor(int color) {
        return color >= GameBoard.RED && color <= GameBoard.ORANGE;
    }

    private int futureOpportunities(int[] tiles) {
        int potential = 0;
        for (int row = 0; row < GameBoard.SIDE; row++) {
            for (int column = 0; column <= GameBoard.SIDE - 3; column++) {
                potential += nearMatch(tiles, row * GameBoard.SIDE + column, 1);
            }
        }
        for (int column = 0; column < GameBoard.SIDE; column++) {
            for (int row = 0; row <= GameBoard.SIDE - 3; row++) {
                potential += nearMatch(tiles, row * GameBoard.SIDE + column, GameBoard.SIDE);
            }
        }
        return potential;
    }

    private int nearMatch(int[] tiles, int start, int stride) {
        int emptyCount = 0;
        int commonColor = 0;
        for (int offset = 0; offset < 3; offset++) {
            int color = tiles[start + offset * stride];
            if (color == 0) {
                emptyCount++;
            } else if (!isGemColor(color)) {
                return 0;
            } else if (commonColor == 0) {
                commonColor = color;
            } else if (color != commonColor) {
                return 0;
            }
        }
        return commonColor != 0 && emptyCount == 1 ? 1 : 0;
    }

    private int[] dropMatches(int[] tiles, boolean[] matchedCells) {
        int[] result = new int[GameBoard.CELL_COUNT];
        for (int column = 0; column < GameBoard.SIDE; column++) {
            int targetRow = GameBoard.SIDE - 1;
            for (int row = GameBoard.SIDE - 1; row >= 0; row--) {
                int index = row * GameBoard.SIDE + column;
                if (!matchedCells[index]) result[targetRow-- * GameBoard.SIDE + column] = tiles[index];
            }
        }
        return result;
    }

    static MatchAnalysis findMatches(int[] tiles) {
        boolean[] matched = new boolean[GameBoard.CELL_COUNT];
        boolean[] horizontal = new boolean[GameBoard.CELL_COUNT];
        boolean[] vertical = new boolean[GameBoard.CELL_COUNT];
        List<MatchRun> runs = new ArrayList<>();

        for (int row = 0; row < GameBoard.SIDE; row++) {
            int start = 0;
            while (start < GameBoard.SIDE) {
                int color = tiles[row * GameBoard.SIDE + start];
                int end = start + 1;
                while (isGemColor(color) && end < GameBoard.SIDE
                        && tiles[row * GameBoard.SIDE + end] == color) end++;
                int length = end - start;
                if (isGemColor(color) && length >= 3) {
                    runs.add(new MatchRun(row * GameBoard.SIDE + start, length, 1));
                    for (int column = start; column < end; column++) {
                        int index = row * GameBoard.SIDE + column;
                        matched[index] = true;
                        horizontal[index] = true;
                    }
                }
                start = end;
            }
        }

        for (int column = 0; column < GameBoard.SIDE; column++) {
            int start = 0;
            while (start < GameBoard.SIDE) {
                int color = tiles[start * GameBoard.SIDE + column];
                int end = start + 1;
                while (isGemColor(color) && end < GameBoard.SIDE
                        && tiles[end * GameBoard.SIDE + column] == color) end++;
                int length = end - start;
                if (isGemColor(color) && length >= 3) {
                    runs.add(new MatchRun(start * GameBoard.SIDE + column, length,
                            GameBoard.SIDE));
                    for (int row = start; row < end; row++) {
                        int index = row * GameBoard.SIDE + column;
                        matched[index] = true;
                        vertical[index] = true;
                    }
                }
                start = end;
            }
        }

        int count = 0;
        int intersections = 0;
        for (int index = 0; index < matched.length; index++) {
            if (matched[index]) count++;
            if (horizontal[index] && vertical[index]) intersections++;
        }
        return new MatchAnalysis(matched, horizontal, vertical, count, intersections, runs);
    }

    static final class MatchAnalysis {
        final boolean[] matchedCells;
        final boolean[] horizontal;
        final boolean[] vertical;
        final int count;
        final int intersectionCount;
        final List<MatchRun> runs;

        MatchAnalysis(boolean[] matchedCells, boolean[] horizontal, boolean[] vertical,
                      int count, int intersectionCount, List<MatchRun> runs) {
            this.matchedCells = matchedCells;
            this.horizontal = horizontal;
            this.vertical = vertical;
            this.count = count;
            this.intersectionCount = intersectionCount;
            this.runs = runs;
        }
    }

    private static final class MatchRun {
        final int start;
        final int length;
        final int stride;

        MatchRun(int start, int length, int stride) {
            this.start = start;
            this.length = length;
            this.stride = stride;
        }

        int centerCell() {
            return start + (length / 2) * stride;
        }
    }

    private static final class SpecialSpawn {
        final int cell;
        final int type;

        SpecialSpawn(int cell, int type) {
            this.cell = cell;
            this.type = type;
        }
    }

    private static final class SpecialActivation {
        final int cell;
        final int type;
        final int targetColor;

        SpecialActivation(int cell, int type, int targetColor) {
            this.cell = cell;
            this.type = type;
            this.targetColor = targetColor;
        }
    }

    private static final class SpecialResolution {
        final boolean[] clearedCells;
        final int gemCount;

        SpecialResolution(boolean[] clearedCells, int gemCount) {
            this.clearedCells = clearedCells;
            this.gemCount = gemCount;
        }
    }

    private static final class MatchResolution {
        final boolean[] clearedCells;
        final double score;

        MatchResolution(boolean[] clearedCells, double score) {
            this.clearedCells = clearedCells;
            this.score = score;
        }
    }

    private static final class Candidate {
        final Choice choice;
        final int[] settledTiles;
        final int steps;
        final long seed;
        double preliminaryScore;
        double firstRefillScore = Double.NaN;
        Random refillRandom;
        int refillSamples;
        double refillResidualTotal;
        Choice rankedChoice;

        Candidate(Choice choice, int[] settledTiles, int steps, long seed) {
            this.choice = choice;
            this.settledTiles = settledTiles;
            this.steps = steps;
            this.seed = seed;
            this.preliminaryScore = choice.score;
        }
    }

    private static final class ImmediateSwap {
        final int firstCell;
        final int secondCell;
        final int matchedCount;
        final Candidate scored;

        ImmediateSwap(int firstCell, int secondCell, int matchedCount, Candidate scored) {
            this.firstCell = firstCell;
            this.secondCell = secondCell;
            this.matchedCount = matchedCount;
            this.scored = scored;
        }
    }

    static final class Choice {
        final int firstCell;
        final int secondCell;
        final int matchedCount;
        final int cascadeCount;
        final double score;
        final boolean waitingForResolution;
        final int unresolvedMatches;
        final int candidateCount;
        final int specialType;

        private Choice(int firstCell, int secondCell, int matchedCount, int cascadeCount,
                       double score, boolean waitingForResolution, int unresolvedMatches,
                       int candidateCount, int specialType) {
            this.firstCell = firstCell;
            this.secondCell = secondCell;
            this.matchedCount = matchedCount;
            this.cascadeCount = cascadeCount;
            this.score = score;
            this.waitingForResolution = waitingForResolution;
            this.unresolvedMatches = unresolvedMatches;
            this.candidateCount = candidateCount;
            this.specialType = specialType;
        }

        static Choice waitForResolution(int matchCount) {
            return new Choice(-1, -1, 0, 0, 0, true, matchCount, 0, 0);
        }

        static Choice noMove(int candidateCount) {
            return new Choice(-1, -1, 0, 0, 0, false, 0, candidateCount, 0);
        }

        Choice withCandidateCount(int count) {
            return new Choice(firstCell, secondCell, matchedCount, cascadeCount, score,
                    false, 0, count, specialType);
        }

        Choice withScore(double updatedScore) {
            return new Choice(firstCell, secondCell, matchedCount, cascadeCount, updatedScore,
                    waitingForResolution, unresolvedMatches, candidateCount, specialType);
        }
    }
}
