# Verification — 1.9.32

Repository: https://github.com/PacManNa/Bot-klicker

## Reproduced issue

On `1000011196.jpg`, the previous version read the last cell as 7 instead of 9.
Rank scores 8–17 from hints were disabled with an infinite threshold, but the
result could still override a correct cell-template match. The protection added
in 1.9.31 compared a rank against the array length (17), so it did not apply to
rank 9. On the incorrect board, the bot expected two 7s to merge; on the actual
board, a right swipe changes nothing.

Correct matrix:

```text
0 0 0 1
0 1 2 3
0 0 4 6
1 5 7 9
```

## Changes

- Read current hints on every frame: five positions left-to-right in a row,
  then the next row, through rank 17. Rank is determined by sequence position.
- Separate the animal from the background and compare shape, spatial color,
  hue, and brightness. Recover a dark body from its contour.
- A closed black silhouette keeps the next-rank position but is not used as a
  sample for an open object. When a colored icon replaces it, the next frame
  picks it up. Static samples remain a fallback for unclear hints.
- Use all visible open ranks when checking the margin between the best matches.
  Cropped fragments are not stretched to fit a full template.
- The panel shows the number of open ranks and the next closed rank.

## Results

| Check | Result |
| --- | --- |
| Unit tests for shifts, merges, transitions, move selection, geometry, and confidence | 33 passed, 0 failures |
| Full screenshots with manually verified matrices | 27/27, all 432 cells matched |
| Occluded frame and horizontal crop | Both rejected |
| Scaling of latest and previous control frames | Exact matrices at 1080×2340 and 1080×2400 |
| Latest board read only from hints, without static samples | All occupied cells at 576×1280, 1080×2340, and 1080×2400 |
| New colored object replacing a silhouette | Rank 11 learned on next frame; rank 12 remains closed |
| Rank by position, four rows | All positions 1–17 passed |
| Incomplete icons under opaque panel | Not used as samples |
| Merging a newly created tile again in one move | Prevented, including ranks 11–12 |
| Conservation of total tile value | 1,296 row variants × 4 directions |
| Move selection on latest real board | Right not selected; chosen move changes the board |
| Debug/release APKs | Built; recognition classes identical |

Reproduction commands are in [BUILDING.md](BUILDING.md).

## APK and signature

Version `1.9.32`, `versionCode=63`. Both APKs are signed with the same saved key
as build 1.9.31; `apksigner` confirmed the match.

Certificate SHA-256:

```text
78b0b3adc65fdd195744113386994b80319cfd119f1221f6960dbfc02c234985
```

| File | Package ID | APK SHA-256 |
| --- | --- | --- |
| Bot-klicker-1.9.32-replacement.apk | ua.borlock.autotap | cc84460a0afdb6c56a592f9eb9dbab5b0b0e49a7eddf64c6a87ae8843125a625 |
| Bot-klicker-1.9.32-side-by-side.apk | ua.borlock.autotap.newkey | 35d5585d62168ff4d9634cf6c85d1293de6a3e756253faafc7460fae3d0739ff |

## Verification limits

The supplied materials do not include images of real, open objects at ranks
11–17. Automatic pickup was checked with controlled changes to icon colors and
positions. This checks the learning mechanism, not artwork the game has not yet
shown. Physical gestures and APK installation on a phone were not performed in
this environment.
