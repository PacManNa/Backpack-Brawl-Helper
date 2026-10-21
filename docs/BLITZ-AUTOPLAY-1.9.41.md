# Backpack Blitz: faster search and better cascade estimates — 1.9.41

## Behavior

The largest immediate clear remains the first criterion. On a stable board, a
new match can only pass through one of the swapped cells: the first pass now
checks those two positions quickly. Full bonus and cascade evaluation is done
for moves with the largest clear. If there is only one such move, random-refill
simulation is unnecessary. The regular match-existence check also no longer
copies the board or creates full-analysis objects.

To choose between equally large matches, the solver exactly computes the
expected number of unique gems in the first cascade. The model assumes seven
colors appear independently and uniformly. For a fixed color at the center
cell, its probability of joining a horizontal match is:

```text
H = L1·R1 + L1·(1−R1)·L2 + (1−L1)·R1·R2
```

`L1`, `L2`, `R1`, and `R2` are probabilities of the same color in adjacent
positions: 0 or 1 for known cells and 1/7 for new cells. The vertical
probability `V` is calculated the same way. The probability of being in either
match is `H + V − H·V`, counting an intersection only once. For a new center
cell, the result is averaged over all seven colors. Summing over cells gives
the exact expected first clear under this model.

This expectation is used for all moves tied at the largest match size before
selecting three candidates. Random samples estimate the rest: bonuses and later
cascades. The first four samples are reused when extending the estimate to eight.

Bonus grouping was fixed: adjacent independent lines are no longer combined
into one shape. For example, two adjacent four-gem lines produce two rockets;
intersecting lines in one T/L shape produce one bomb. The model previously
sometimes undervalued such double matches.

The floating panel now restyles only cells that changed. Rendering the same
board again after the bot's decision no longer creates 64 new backgrounds.

## Verification

- An isolated optimization check before changing the prediction: 5,000 boards,
  same moves, largest clears, and candidate count as 1.9.40.
- Final comparison: 10,000 stable boards; 25% had 1–5 bonuses added. Largest
clear and available-move counts match 1.9.40 in every case. The choice among
equally large matches changed on 825 boards.
- The quick match-existence check agrees with full analysis on another 10,000
  boards containing colors, empty cells, and bonuses.
- Unit tests cover independent bonuses, T/L shapes and straight fives, largest
  match size, unchanged input board, and exact expectation by enumerating all
  7⁴ refills for control boards.

On Linux / JDK 21.0.12.1, using 10,000 boards and alternating run order after
warm-up: median move selection was **0.114 ms** in 1.9.40 and **0.040 ms** in
1.9.41; p95 was **0.174 / 0.128 ms**. This is engine time on a computer.
Screenshot capture, Android, gestures, and game animations are excluded; faster
computation does not imply the same increase in moves per minute.

On a separate 1,000 boards with different move choices, each move was tested
with 2,048 refills. Under the shared corrected model, the new choice was better
/ tied / worse in **74 / 905 / 21** cases. Mean paired modeled-score difference
was **+0.4154**, with a 95% bootstrap interval of **[+0.2740; +0.5692]**. This
checks consistency with the model; it is not a measured increase in the game's
actual score. Empty scores in the quality CSV mean the move was identical, so
the difference is zero.

Data: [timing and clears](benchmarks/blitz-1.9.41.csv),
[modeled-score comparison](benchmarks/blitz-1.9.41.csv.quality.csv).

To reproduce (Git and JDK 17+ required):

```bash
JAVA_HOME=/path/to/jdk bash tools/check-blitz-solver.sh \
  10000 /tmp/blitz.csv quality 1000 2048
```

Baseline code is taken from commit `e6e393c` (1.9.40); the comparison variant
is compiled only in a temporary directory and is not included in the APK.
Board-generation seed is 1941 for timing, 1941041 for the separate quality
comparison, and 1941042 for bootstrap sampling.

Package `ua.borlock.autotap`, service component, and persistent signing key are
retained; the APK has `versionCode=72`. It installs over the previous release
and retains data. A real 60-second phone round was not run in this environment.

## Build

`testDebugUnitTest assembleDebug assembleRelease` succeeded: **56 tests**, zero
failures and skips. `apksigner verify` confirmed the release APK. Comparison
with the actual 1.9.40 APK confirmed the same package, accessibility-service
component, and certificate SHA-256:

```text
78b0b3adc65fdd195744113386994b80319cfd119f1221f6960dbfc02c234985
```

Release APK 1.9.41 SHA-256:

```text
bc280474fa1582e484b0f558a70cc5077d6a30b0011db0ad0e8ce00b1c81ca7b
```
