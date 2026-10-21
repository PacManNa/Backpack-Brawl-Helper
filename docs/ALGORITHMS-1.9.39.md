# Compact controls and algorithm evaluation — 1.9.39

**Correction following user feedback:** the game has 15 ranks. The 600-move
comparison below evaluates only early and mid-game. Its “rank ≥10” metric does
not confirm play quality through rank 15 or show that this is the best algorithm
for the whole game. Follow-up: [ALGORITHMS-LEVEL15.md](ALGORITHMS-LEVEL15.md).

## Interface

Minimum height for `Start / Stop`, `Show / Hide board` (also the timer list),
and `To icon` was reduced by 40%: `48 × 0.60 = 28.8 dp`. Rounding to physical
pixels gives about 29 dp; long labels with a large system font can still grow
to remain legible. The close button now shows only a large `×`, in 30 sp. Its
accessibility description still says “Stop bot and close panel.”

## Algorithms compared

The evaluation covers **Merge Madness**, the 4×4 board from earlier screenshots.

| Name | Algorithm |
| --- | --- |
| `greedy` | One-move expectimax using the current post-spawn board evaluation |
| `expectimax2` | Same evaluation and spawn model, depth up to 2 |
| `current` | Current expectimax, depth up to 3 |
| `expectimax4` | Same strategy, depth up to 4 |
| `adaptive4` | Depth up to 4 with no more than 4 empty cells, otherwise up to 3 |
| `minimax` | Depth up to 3; worst-case tile spawn instead of expected value |

Candidates are generated in a temporary directory from the current
`MergeMadnessSolver` source. Its evaluation, caches, direction order,
incremental completion of search levels, and 120 ms budget are retained. Board
rules are shared across all variants. Generated classes are not included in
the APK. The comparison varies search type and depth with the current board
evaluation; it does not cover other weight sets, learned evaluators, or every
possible algorithm.

## Simulation conditions

- All policies use the same starting seeds and random-value sequences. On every
  move, corresponding values determine the new tile rank and its ordinal among
  currently empty cells.
- Simulation model: rank 1 with probability 0.90, rank 2 with probability 0.10;
  position uniformly selected among empty cells. This is the current solver's
  assumption; new phone observations have not measured the actual game
  distribution.
- Odd seeds start with two new tiles; even seeds start from the user's board:

```text
0 0 0 1
0 1 2 3
0 0 4 6
1 5 7 9
```

- Spawn probabilities are learned through `observeIfConfirmed`, as in the app,
  only after a confirmed transition with one merge. Every simulated transition
  is checked by `MergeMadnessTransition`.
- Metrics in this historical run: reaching rank 10, completed moves, whether
  another move remains, maximum rank, and modeled merge score. Modeled score is
  not the score displayed by the game.
- Games are move-limited. A board that still has a legal move at the limit is
  not counted as a completed game, and its final outcome is unknown.
- Timing uses a JDK 21 JVM on a computer, with two worker threads. Screenshot
  capture, physical gestures, Android ART, and game animations are excluded.

## Reproduction

Python 3 and JDK 17 or later are required; Android SDK is not needed for this
simulation.

```bash
JAVA_HOME=/path/to/jdk python3 tools/check-merge-madness-algorithms.py \
  --games 4 --moves 300 --workers 2 --seed-start 1 \
  --policies greedy,expectimax2,current,expectimax4,minimax \
  --output /tmp/merge-pilot

JAVA_HOME=/path/to/jdk python3 tools/check-merge-madness-algorithms.py \
  --games 32 --moves 600 --workers 2 --seed-start 101 \
  --policies greedy,expectimax2,current,adaptive4,minimax \
  --output /tmp/merge-holdout
```

The first run estimates the cost of candidate policies. The main run uses
different seeds, 101–132, with 16 fresh games and 16 continuations of the
supplied board per policy. CSV files contain summaries and each individual
game. Scores are compared pairwise against the current policy; a 95% bootstrap
interval uses 10,000 resamples of paired differences.

## Pilot results

Four seeds, up to 300 moves, two fresh games and two continuations per policy.
Constant depth 4 averaged 5,458 modeled points versus 5,411 for current depth 3,
with the same rank-10 reach rate (2/4) and same number of boards with a legal
move at the end (4/4). The 95% score-difference interval was `[-40; +159]`, so
there is no convincing advantage. Median decision time rose from 5.883 to
90.202 ms, and p95 from 10.893 to 120.056 ms. A full fourth search level often
does not fit within the budget. This run does not justify constant depth 4 for
the goal of speeding up without reducing quality.

Measurements: [pilot summary](benchmarks/algorithms-pilot-1.9.39.summary.csv),
[each game](benchmarks/algorithms-pilot-1.9.39.games.csv).

## Main comparison

32 different seeds, up to 600 moves, five policies — 160 modeled games.

| Policy | Rank ≥10 | Board has a legal move at end | Mean modeled score | Median search | p95 |
| --- | --- | --- | --- | --- | --- |
| One move | 25/32 | 24/32 | 9343.88 | 0.021 ms | 0.060 ms |
| Expectimax, depth 2 | 31/32 | 28/32 | 10455.25 | 0.299 ms | 0.612 ms |
| **Current expectimax, depth 3** | **31/32** | **32/32** | **11057.63** | **4.949 ms** | **9.647 ms** |
| Adaptive, depth 3–4 | 29/32 | 32/32 | 10769.25 | 6.046 ms | 38.601 ms |
| Minimax, depth 3 | 26/32 | 25/32 | 9670.00 | 4.173 ms | 9.022 ms |

Results across all 32 paired scores, relative to the current algorithm:

| Variant | Better / tied / worse | Mean difference | 95% bootstrap interval |
| --- | --- | --- | --- |
| One move | 4 / 3 / 25 | −1713.8 | [−2771.6; −755.9] |
| Depth 2 | 11 / 2 / 19 | −602.4 | [−1367.0; +40.8] |
| Adaptive 3–4 | 11 / 3 / 18 | −288.4 | [−715.0; +51.9] |
| Minimax | 8 / 1 / 23 | −1387.6 | [−2502.8; −457.9] |

For continuations of the supplied board, the current policy reached rank 10 in
all 16 scenarios, and all 16 still had a legal move after 600 steps. These
numbers describe limited scenarios under the stated spawn model. They do not
prove global optimality or measure a phone result. Time-limited variants may
complete different depths on a different CPU.

Data: [summaries split by starting board](benchmarks/algorithms-holdout-1.9.39.summary.csv),
[each game](benchmarks/algorithms-holdout-1.9.39.games.csv). A total of 180
modeled games were run, including the pilot.

## APK decision

**Keep the current expectimax with depth up to 3 and a 120 ms budget.** This
early/mid-game evaluation found no replacement proven to improve results while
preserving speed. This is a temporary choice, not a conclusion about the best
algorithm for rank 15. Depth 2 is faster but ran out of moves in four scenarios;
the current policy retained a legal next move in those conditions. Adaptive
depth increased time without a confirmed result improvement. Minimax treats
tile spawning as an opponent's choice, while the current game model is random.

All 41 unit tests passed. Debug and release 1.9.39 (`versionCode=70`) were built
with the same saved key. Game algorithms and recognition were unchanged; new
variants exist only in the evaluation tool. The interface was not run on a
physical phone in this environment.

`apksigner verify` confirmed both APKs. Certificate SHA-256 matches 1.9.38:

```text
78b0b3adc65fdd195744113386994b80319cfd119f1221f6960dbfc02c234985
```

File SHA-256:

```text
replacement:  10f6227b537a75ebbcc263a1af5b035a0703ce61778955c6b10ea2eec3de0cda
side-by-side: 11b64f61fa364c28c717bea7668a408740da958fbe810f6b87f92a7ae812d6ac
```
