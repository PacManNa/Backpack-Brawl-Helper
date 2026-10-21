# Rank-15 target: expanded algorithm evaluation

The user clarified that Merge Madness has **15 ranks**. The earlier
[1.9.39 comparison](ALGORITHMS-1.9.39.md) was limited to 600 moves and used
reaching rank 10 as its metric. It does not verify success through rank 15 or
show that the current algorithm is best for the entire game.

## Why 600 moves are insufficient

In the model, a tile of rank `r` has value `2^r`. Merges preserve the total,
while each new tile adds 2 or 4. A rank-15 tile is worth 32,768.

The supplied board has total value 770. After 600 moves, even if every new tile
were rank 2, the total could not exceed `770 + 600 × 4 = 3170`, which is not
enough even for one rank-12 tile (`4096`). Starting with two new tiles gives an
even lower upper bound of 2,408. Therefore ranks 12–15 were mathematically
unreachable in the previous run, regardless of algorithm quality.

Starting from two new tiles, reaching rank 15 requires at least 8,190 moves even
if every spawn has maximum value. Under the modeled 90% / 10% distribution, a
new tile adds 2.2 on average; about 14,900 spawns merely accumulate the required
total value. This estimates the amount of material, not a guarantee of reaching
the goal: the strategy must still combine it into one tile.

## Changes to the evaluation tool

- The default target is 15; `--target-rank` accepts 10–15. The default move
  limit is now 20,000. A game ends sooner if it reaches the goal or has no legal
  moves.
- CSV output records the first move on which each rank from 10 to 15 is reached:
  `-1` means “not reached,” and `0` means “already present on the starting board.”
- Results are explicitly labeled `reached`, `blocked`, or `move-limit`. A board
  that still has a legal move at the limit is not counted as a successful game.
- Initial and final board values are saved. Every transition checks value
  conservation for merges and the exact value added by the new tile.
- `--start mixed` alternates fresh games with continuations of the supplied
  board; `early` and `user` run only the corresponding group.
- `--start late` creates a **synthetic** late-game position: one tile for every
  rank `1..target-1`, plus a second rank-1 tile, arranged in a snake. Initial
  value is `2^target`, but the target tile is not yet present. Eight
  orientations cover four rotations and their reflections. This checks an
  endgame with higher-rank tiles already supplied; reaching the target here does
  not count as completing a game from the start.

Example base synthetic position for testing rank 15:

```text
14 13 12 11
 7  8  9 10
 6  5  4  3
 0  1  1  2
```

It has one empty cell and enough accumulated value for the goal, so it tests
combining higher-rank tiles under a nearly full board.

Candidates, board evaluation, 120 ms budget, and spawn-probability learning
remain the same as in the earlier comparison. Simulation assumes a uniformly
chosen empty cell and rank probabilities 1/2 = 0.90/0.10. App baseline:
`675f277` / 1.9.39. Environment: Linux, JDK 21, 2 CPU quota, 2 worker threads,
JVM `-Xms256m -Xmx768m`. Long runs and late positions were run sequentially.

The long series uses 16 shared seeds, 201–216, for each of six policies: eight
fresh games and eight continuations of the supplied board. The 20,000-move limit
allows enough value to accumulate for rank 15 even if only the smallest tiles
spawn; combining the target tile is evaluated separately. Each late-game target
uses eight seeds, 301–308, covering all eight orientations of the prepared board.

## Reproduction

Python 3 and JDK 17+ are required.

```bash
JAVA_HOME=/path/to/jdk python3 tools/check-merge-madness-algorithms.py \
  --games 16 --moves 20000 --workers 2 --seed-start 201 \
  --target-rank 15 --start mixed \
  --policies greedy,expectimax2,current,adaptive4,expectimax4,minimax \
  --output /tmp/merge-target15

for target in 11 12 13 14 15; do
  JAVA_HOME=/path/to/jdk python3 tools/check-merge-madness-algorithms.py \
    --games 8 --moves 500 --workers 2 --seed-start 301 \
    --target-rank "$target" --start late \
    --policies greedy,expectimax2,current,adaptive4,expectimax4,minimax \
    --output "/tmp/merge-late-$target"
done
```

## Long games: results

96 modeled games, **151,926 moves**. All ended because no legal moves remained,
before reaching the limit; none stopped at 20,000 moves. The longest game lasted
5,429 moves. The highest rank reached was **13**. Ranks 14 and 15 were not reached.

Each reach count below is out of 16 games for that policy. Policies share the
same rules and board evaluation; they do not cover all possible evaluators or
algorithms.

| Policy | ≥11 | ≥12 | ≥13 | ≥14 | 15 | Median search | p95 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| One move | 5/16 | 0/16 | 0/16 | 0/16 | 0/16 | 0.019 ms | 0.058 ms |
| Expectimax up to 2 | 10/16 | 2/16 | 0/16 | 0/16 | 0/16 | 0.247 ms | 0.540 ms |
| Current expectimax up to 3 | 12/16 | 3/16 | 0/16 | 0/16 | 0/16 | 3.472 ms | 8.448 ms |
| Adaptive up to 3–4 | 14/16 | 8/16 | 0/16 | 0/16 | 0/16 | 5.801 ms | 40.653 ms |
| Expectimax up to 4 | 14/16 | 8/16 | 1/16 | 0/16 | 0/16 | 41.821 ms | 119.457 ms |
| Minimax up to 3 | 9/16 | 1/16 | 0/16 | 0/16 | 0/16 | 3.232 ms | 8.316 ms |

On this longer horizon, depth 4 scored 51,452.50 modeled points on average versus
32,195.25 for current depth 3. The mean paired difference was `+19,257.3`, with
a 95% bootstrap interval of `[+4,175.5; +35,245.3]` over 10,000 resamples. Thus
the shorter run missed a depth-4 advantage on this metric. Adaptive depth had a
difference of `+10,330.3`, interval `[-4,361.5; +24,457.3]`, with more
uncertainty. These intervals describe modeled score, not the probability of
reaching rank 15.

Greater depth costs time. The 120 ms budget is checked between operations; it
is not a hard real-time bound during JVM or scheduler pauses. The longest depth-4
decision in this series took 254.709 ms; current depth 3 took 66.239 ms. These
measurements are specific to this computer.

Data: [summaries by starting board](benchmarks/algorithms-target15-1.9.39.summary.csv),
[each game](benchmarks/algorithms-target15-1.9.39.games.csv).

## Prepared late-game positions

Another 240 modeled scenarios and 9,467 moves: eight orientations for each goal
from 11 to 15 and each of six policies. Higher-rank tiles are already in the
starting board. The table shows **reaching a goal from a prepared position**,
not completing a game that starts with two initial tiles.

| Policy | Goal 11 | Goal 12 | Goal 13 | Goal 14 | Goal 15 |
| --- | --- | --- | --- | --- | --- |
| One move | 7/8 | 7/8 | 5/8 | 5/8 | 5/8 |
| Expectimax up to 2 | 8/8 | 8/8 | 5/8 | 7/8 | 5/8 |
| Current expectimax up to 3 | 8/8 | 8/8 | 7/8 | 7/8 | 6/8 |
| Adaptive up to 3–4 | 8/8 | 8/8 | 7/8 | 7/8 | 7/8 |
| Expectimax up to 4 | 8/8 | 8/8 | 7/8 | 6/8* | 7/8 |
| Minimax up to 3 | 8/8 | 7/8 | 8/8 | 8/8 | 7/8 |

\* For goal 14 with constant depth 4: 6 wins, 1 loss, and 1 live board after
the 500-move limit. The last case counts as neither a win nor a loss.

For goal 15, median / p95 search times were: current `0.645 / 10.906 ms`,
adaptive `8.673 / 58.771 ms`, depth 4 `12.207 / 97.587 ms`, and minimax
`1.343 / 9.617 ms`.

This shows that the current code can represent rank 15, but does not always
assemble it even from a prepared board. Minimax warrants further study on full
late-game boards: weaker results from the start do not rule out usefulness at a
specific stage. Eight orientations of one construction are a small sample for
choosing a strategy across all late positions. Score alone is also insufficient:
a successful game stops as soon as the target is reached, while an unsuccessful
one may earn more points before losing or reaching the limit.

Each target has a summary and individual scenarios:

| Goal | Summary | Scenarios |
| --- | --- | --- |
| 11 | [CSV](benchmarks/algorithms-late11-1.9.39.summary.csv) | [CSV](benchmarks/algorithms-late11-1.9.39.games.csv) |
| 12 | [CSV](benchmarks/algorithms-late12-1.9.39.summary.csv) | [CSV](benchmarks/algorithms-late12-1.9.39.games.csv) |
| 13 | [CSV](benchmarks/algorithms-late13-1.9.39.summary.csv) | [CSV](benchmarks/algorithms-late13-1.9.39.games.csv) |
| 14 | [CSV](benchmarks/algorithms-late14-1.9.39.summary.csv) | [CSV](benchmarks/algorithms-late14-1.9.39.games.csv) |
| 15 | [CSV](benchmarks/algorithms-late15-1.9.39.summary.csv) | [CSV](benchmarks/algorithms-late15-1.9.39.games.csv) |

## Checks and conclusion

In total, **336 new modeled scenarios and 161,393 moves** were run. CSV
consistency was checked: board value, reached ranks, move numbers, and end
reasons agree.

**All 44 unit tests passed**, with no errors or skips. Added checks cover:

- merging every rank through 15 in all four directions, score, and value conservation;
- preventing a newly created higher-rank tile from merging again in one swipe;
- choosing a forced merge up to each rank 11–15 on a full board in four orientations.

**The earlier conclusion about the best algorithm for the entire game was
premature.** Deeper search improved some long-game metrics, but no tested
variant reached the final rank from a fresh starting board. Faster play without
loss of quality through rank 15 has not been proven for either a shallower or
deeper search.

Further candidates should change late-board evaluation or combine policies by
game stage, then be tested on independent complete games through rank 15.
Extending the test reveals the problem but does not improve the strategy. This
updates the tools, tests, and documentation for APK 1.9.39.

## Verification limits

This is a JVM logic test without screenshot capture, Android gestures, or
animations. Synthetic late boards test numeric-rank handling; recognizing the
real artwork for ranks 11–15 requires corresponding screenshots. The
implementation has technical capacity for 17 ranks; that does not mean the game
has 17 ranks.
