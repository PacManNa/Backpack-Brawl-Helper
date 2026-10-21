# Module catalog and floating panel — 1.9.37

## Implemented

- One catalog defines the two modules: stable IDs, names, icons, descriptions,
  palettes, and supported settings. Cards and the module count are built from it.
- A collapsed card shows its icon and name. Tapping it reveals the description,
  panel launch button, board-change wait setting, and panel width.
- Expandable behavior is shared in `ExpandableSection`: button, focus,
  state announcements, and expand/collapse accessibility actions.
- Board-change wait is stored separately per module. On first access, each
  module inherits the previous shared value.
- The panel shows the active module's name, icon, color, text status, and one
  `Start / Stop` button. Primary actions have 48 dp touch targets.
- Diagnostics expand when the name/status area is tapped. The selection persists.
- The `◉` button collapses the panel to an icon without stopping the session.
  The icon can be moved; tapping it restores the panel. `×` stops and closes it.
- Width from 224–360 dp is saved per module; the default is 272 dp. Height is
  automatic. Position is restored per module and orientation, within the usable
  area after accounting for system bars and display cutouts.
- Timers have their own position profile. Rotation restores the matching profile;
  supported game boards remain portrait-only.
- Android access and first-run instructions are collapsed. Access status remains
  visible in the heading.
- The app is named Pelyustka; the separate build is named “Pelyustka (new).”
  The version appears at the bottom of the main screen.

## Game code

Compared with commit `5b105dd` (1.9.36), 19 existing Java files for recognition,
board models, evaluators, timers, and helpers are byte-for-byte identical.

The service path from starting autoplay through capture, stabilization, action
selection, and gesture dispatch is retained. Two reads of the shared timeout
were replaced with a read of the active module setting. On update, the old value
is inherited; bounds, step size, search depth, thresholds, and intervals are
unchanged.

Routing for the two boards still lives in the shared service. The module catalog
does not make executable plugins dynamically loadable. See [MODULES.md](MODULES.md)
for current boundaries and how to add engines; future ideas are in [ROADMAP.md](ROADMAP.md).

## Build

`assembleDebug assembleRelease` succeeded: version `1.9.37`, `versionCode=68`.
Both builds completed and `apksigner verify` confirmed their signatures.
Automated game tests were not run for this UI change; earlier reports are kept
separately. The interface was not run on a physical phone in the development
environment.

Both builds use the previous key and package IDs `ua.borlock.autotap` and
`ua.borlock.autotap.newkey`. The signing key and local configuration are not
included in the repository.

Certificate SHA-256:

```text
78b0b3adc65fdd195744113386994b80319cfd119f1221f6960dbfc02c234985
```

APK file SHA-256:

```text
replacement:  1160e7eddb695fc4e09f776afc8e648f0a9a12c9233b46b44b4c479ba22761e0
side-by-side: 8021a6f59f05fa4669af7472d9fdb681d436ba03f25c01509e90ca893327d943
```
