# Performance improvements — 1.9.35

## Changes

Java Flight Recorder profiling of 1.9.34 showed that the most time was spent
recalculating vertical contrast for candidate board boundaries. Searching for
hint rows was another significant cost.

- Pixel contrast is computed once per current frame for all candidates. All
  columns needed for boundary scoring and separator checks are read, including
  smoothing and local-maximum neighborhoods. Sums are added in the previous
  order to preserve float results.
- The horizontal profile is read across the full range where a board may be,
  with extra pixels for smoothing. Board position is searched on every frame;
  old coordinates do not replace the check.
- Positions of unchanged hints are cached by a fingerprint of all pixels in
  their search area, geometry, and screenshot size. Icon changes, a newly
  revealed rank, board shifts, and resolution changes trigger a new search.
- Swipe simulation no longer creates two temporary arrays per row. The hash of
  an immutable board is computed on creation.
- Available directions are determined from adjacent cells without creating
  four swipe results for each leaf evaluation. Swipe caches are built on demand.

Search depth 3, 120 ms budget, evaluation weights, direction order, and tile
spawn model are retained. All possible spawn locations for rank-1 and rank-2
tiles are computed completely. Screenshots are not downscaled, and recognition
thresholds are not loosened.

## Speed

Same screenshot, `1000011196.jpg`; JDK 21; one JVM per version;
`-Xms256m -Xmx512m`; 12 warm-up and 100 measured repetitions per size. Versions
were measured sequentially, without concurrent builds or tests. Hints remained
identical; board gradients were recalculated each time.

| Recognition | 1.9.34 median | 1.9.35 median | Time reduction | p95 before → after |
| --- | --- | --- | --- | --- |
| 576×1280 | 27.653 ms | 5.996 ms | 78.3% | 34.925 → 11.250 ms |
| 1080×2400 | 54.453 ms | 8.289 ms | 84.8% | 61.189 → 11.958 ms |

Matrix and minimum confidence did not change: 0.861 and 0.824.

Search was separately compared at depths 1–3 after full completion, using the
same trees and without timeout interruption. At depth 3: 24 boards, three
repetitions, alternating version order, first-rank spawn probability 0.90.

| Search, 72 measurements | Median | p95 |
| --- | --- | --- |
| 1.9.34 | 1.823 ms | 9.079 ms |
| 1.9.35 | 0.790 ms | 3.628 ms |

These are JVM times on a development computer. Screen capture, physical gestures,
phone frame rate, and game animations are excluded. These numbers do not measure
moves per minute on a phone. The current capture method retains a 334 ms
interval, with 180 ms after a gesture for fast transition confirmation.
Reducing them further requires phone measurements. The 0.4-second retry pause
is not added after a successful move.

## Quality checks

- 41 unit tests passed, including reuse of the working cache after board shifts,
  new hint rows, and board occlusion.
- 27 full screenshots: all 432 cells match manually verified matrices; two
  unusable frames were rejected.
- Hints without static samples: three resolutions, learning a new silhouette on
  the next frame, positions 1–17 in four rows, and occluded icons. One working
  cache is reused across resolution and hint changes.
- Saved 1.9.34 classes were compared with new classes in separate class loaders:
  all `18^4` row combinations in both orientations and all four directions —
  839,808 identical swipes, scores, merge counts, and legal-move masks. Also
  checked 2,048 mixed boards with ranks 0–17.
- 216 full searches at depths 1–3 and spawn probabilities 0.50, 0.90, and 0.97
  returned the same direction and bit-identical double scores.
- Eight reproducible 120-move games at depth 2 produced all 960 identical
  decisions, merge scores, and final boards.
- 174 geometry comparisons across 29 screenshots at three sizes with repeated
  reads matched 1.9.34 bit-for-bit for coordinates, float confidence, hint rows,
  and rejection of unusable frames.

To compare both versions after compiling and saving the old classes:

```bash
javac -d /tmp/merge-equivalence tools/MergeMadnessEquivalenceCheck.java
java -Xms256m -Xmx768m -Djava.awt.headless=true -cp /tmp/merge-equivalence \
  MergeMadnessEquivalenceCheck /path/to/1.9.34/classes \
  app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes \
  app/src/test/resources/merge_madness
```

General build and verification commands are in [BUILDING.md](BUILDING.md).

## Build and signature

Version `1.9.35`, `versionCode=66`. Release package is
`ua.borlock.autotap`; debug package is `ua.borlock.autotap.newkey`. Both use the
saved key from local configuration, with identical algorithm changes in both
packages. Main engine class bytes match between debug and release. Both APKs
passed `apksigner verify`; certificate SHA-256 matches APK 1.9.34:

```text
78b0b3adc65fdd195744113386994b80319cfd119f1221f6960dbfc02c234985
```

File SHA-256:

```text
replacement:  4956ad82613b20af09a56c3a98faa63c74016d7652e5b7e839f673a2fcd3dccd
side-by-side: edb468070883ea6fa620c756c68054ccd8da594c8f28a488bdf616fe4da561a0
```

Phone installation and physical gestures were not performed in the development environment.
