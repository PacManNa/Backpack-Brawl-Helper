# Performance improvements — 1.9.33

## Changes

After a swipe, the game previously required two reads, separated by at least
334 ms. One read is now enough when the full matrix exactly matches the previous
move plus one new tile, geometry is stable, confidence is at least 0.82, and at
least 180 ms have passed since the gesture ended. Game start, unclear frames,
geometry changes, and unconfirmed transitions still use the regular two reads.
Transition checks and learning the tile-spawn model are retained.

Android's screenshot interval remains 334 ms; move search uses the same strategy,
depth, and time budget.

Recognition was accelerated in three ways:

- A strong match with a current hint icon is used immediately. Static-sample
  search is skipped if it cannot change the result.
- Hint-icon descriptions are cached. The fingerprint covers all pixels in all
  possible read regions, sizes, and coordinates. Changes to an icon, revealing
  a silhouette, wrapping to another row, or resolution reset the cache.
- Contour traversal no longer allocates an array for every pixel. Temporary
  hardware bitmaps are released immediately after screenshot copying.

## Measurements

Compiled 1.9.32 and 1.9.33 classes were compared on `1000011196.jpg`: 12 warm-up
and 60 measured repetitions per resolution. This ran on a development JVM, not a
phone; screen capture, physical gestures, and game animations are not included.
Hints were identical between repetitions.

| Resolution | 1.9.32 median | 1.9.33 median | Time reduction | p95 before → after |
| --- | --- | --- | --- | --- |
| 576×1280 | 46.621 ms | 29.442 ms | 36.8% | 53.089 → 37.299 ms |
| 1080×2400 | 71.284 ms | 57.707 ms | 19.0% | 76.206 → 65.137 ms |

Matrix and minimum confidence did not change: 0.861 and 0.824. The transition
logic verifies the shorter wait for a second frame; actual moves per minute
still need to be measured on a phone.

Reproduce after compiling debug classes:

```bash
mkdir -p /tmp/merge-performance-check
javac -cp "app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes:$ANDROID_HOME/platforms/android-35/android.jar" \
  -d /tmp/merge-performance-check tools/MergeMadnessPerformanceCheck.java
java -Xms256m -Xmx512m -Djava.awt.headless=true \
  -cp "app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes:/tmp/merge-performance-check:$ANDROID_HOME/platforms/android-35/android.jar" \
  ua.borlock.autotap.MergeMadnessPerformanceCheck app/src/test/resources/merge_madness app/src/main/res/raw 60
```

## Checks and APK

- 39 unit tests passed, including confirmed transitions, animation wait, and
  rejecting fast transitions for unclear, unchanged, or invalid frames.
- 27 full screenshots: all 432 cells match manually verified matrices. Two
  unusable frames were rejected.
- Passed checks for three resolutions, learning a new object, positions 1–17,
  partially occluded hints, and cache reset on pixel changes.
- Old 1.9.32 classes were compared with new ones in separate class loaders:
  all `18^4` row combinations in both orientations, all four directions —
  839,808 identical swipes, scores, merge counts, and legal-move masks. Also
  checked 2,048 mixed boards with ranks 0–17.
- 216 complete searches at depths 1–3 and first-rank spawn probabilities 0.50,
  0.90, and 0.97 returned the same direction and bit-identical double scores.
- Eight reproducible 120-move games at depth 2 returned the same 960 decisions,
  merge scores, and final boards.
- 174 geometry comparisons across 29 screenshots at three sizes with repeated
  reads matched 1.9.32 bit-for-bit for coordinates, float confidence, hint rows,
  and unusable-frame rejection.

To compare both versions after compiling and saving the previous classes:

```bash
javac -d /tmp/merge-equivalence tools/MergeMadnessEquivalenceCheck.java
java -Xms256m -Xmx768m -Djava.awt.headless=true -cp /tmp/merge-equivalence \
  MergeMadnessEquivalenceCheck /path/to/1.9.32/classes \
  app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes \
  app/src/test/resources/merge_madness
```

General build and verification commands are in [BUILDING.md](BUILDING.md).

## Build and signature

Version `1.9.33`, `versionCode=64`. Release package is
`ua.borlock.autotap`; debug package is `ua.borlock.autotap.newkey`. Both use the
saved key from local configuration, and the algorithm changes are identical in
both packages. Main engine class bytes match between debug and release. Both
APKs passed `apksigner verify`; certificate SHA-256 matches APK 1.9.32:

```text
78b0b3adc65fdd195744113386994b80319cfd119f1221f6960dbfc02c234985
```

File SHA-256:

```text
replacement:  4956ad82613b20af09a56c3a98faa63c74016d7652e5b7e839f673a2fcd3dccd
side-by-side: edb468070883ea6fa620c756c68054ccd8da594c8f28a488bdf616fe4da561a0
```

Phone installation and physical gestures were not performed in the development environment.
