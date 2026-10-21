# Interface — 1.9.36

- Bot cards are collapsed to their icon and name when the app opens.
- Tapping a heading reveals or hides the board format, description, panel button,
  and wait setting. Hidden content does not take up screen space.
- The wait slider is inside each card. It changes the same shared setting;
  both sliders and labels stay in sync. The current value is preserved on update.
- Expanded cards remain open after screen rotation. Headings support keyboard
  control and announce their collapsed state to accessibility services.
- The calibration button and its controls were removed from the floating menu.
  Saved recognition settings are retained.

## Change scope and build

Two UI files changed: `MainActivity.java` and the UI portion of
`TapAccessibilityService.java`, along with the version and documentation.
The other 20 Java files are byte-for-byte identical to the source before this
edit. The service path from starting autoplay through screen capture,
recognition, move selection, and swipe execution was compared with 1.9.35 and
is unchanged. Game parameters, thresholds, and timing values are retained.

`assembleDebug assembleRelease` succeeded. Version `1.9.36`, code `67`.
Both APKs passed signature verification with the previous key. Certificate
SHA-256:

```text
78b0b3adc65fdd195744113386994b80319cfd119f1221f6960dbfc02c234985
```

| APK | Package ID | File SHA-256 |
| --- | --- | --- |
| replacement | ua.borlock.autotap | 92a07a443f2b7d7047a1d24532cbe782029852969c41504ec92414251efdbefc |
| side-by-side | ua.borlock.autotap.newkey | 339fb63c3b48c19067e50b5438c6b63442aabe32ed4f4c79c42bb95e8bb55e41 |

The interface was not run on a physical phone in this environment.
