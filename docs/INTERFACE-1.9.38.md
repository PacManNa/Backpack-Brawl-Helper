# Clear floating-panel controls — 1.9.38

## Controls

| Element | Action |
| --- | --- |
| `▼ Show board` | Shows the board and diagnostics; the label changes to `▲ Hide board` |
| `▲ Hide board` | Hides diagnostics while keeping the main controls visible |
| `Show timers / Hide timers` | Controls the list in timer mode |
| `To icon` | Collapses the whole panel to an icon; the active bot keeps running |
| Icon labeled `Open` | Restores the panel and its previous diagnostics state |
| `× Close` | Stops the bot and closes the panel |
| `Start / Stop` | Starts or stops the current module |
| Heading labeled `⠿ Drag` | Moves the panel; a regular tap does not change its appearance |

## Changes

- Tapping the name, status, or nearest timer text no longer expands the panel.
- Status is now a regular line prefixed with `Status:`.
- Added a separate diagnostics button with the current action and an arrow
  indicating its state.
- Collapse and close buttons have visible labels.
- The collapsed-panel icon is labeled `Open`. Its size accounts for system font
  scaling; a short tap opens it, while dragging moves it.
- Position, width, and diagnostics state continue to use existing module profiles.
- Updated the panel-settings hint and user guide.

## Scope and build

Compared with `1ae4941` (1.9.37), two UI files, the version, and documentation
changed. The other 24 Java files and the service path from autoplay start to
gesture execution are byte-for-byte identical to the previous version. Game
algorithms and parameters are unchanged.

`assembleDebug assembleRelease` succeeded. Version `1.9.38`, `versionCode=69`.
Both APKs passed signature verification with the saved key. Automated tests
were not run for this UI change; a physical phone was unavailable here.
