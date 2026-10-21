# Pelyustka — clicker catalog and reminders

An Android app with two independent modes: a match bot for Backpack Blitz and a 2048 bot for Merge Madness. Their cards, settings and floating controls are built from a shared module catalog. Both read the board through an AccessibilityService and play locally on the device.

[Українська версія README](README.md)

## Features

- Ukrainian and Czech interface languages. The **UA / CZ** button before the module count in the header switches languages and remembers the selection.
- Collapsed icon-and-name cards with descriptions, launch buttons and settings inside.
- Independent retry timeout and panel width per module, with migration of previous settings.
- Compact floating controls, optional diagnostics, an icon mode and saved panel positions.

- Recognizes seven gem colors, rockets, bombs, and portal/whirlwind specials.
- Evaluates up to 112 adjacent swaps on an 8×8 board.
- Prefers the largest immediate clear, then special items and cascades. The first random-refill match has an exact expectation under the uniform seven-color model.
- Identifies object ranks on Merge Madness's 4×4 board using templates and the hint chain below it, then selects a swipe direction using a search that models new tiles. The game has 15 ranks; recognizing all late-game artwork still needs real screenshots for verification.
- Provides a floating **Start / Stop** panel with status and diagnostics.
- Includes four stopped reward presets: event tickets (4 hours), shop gems (1 hour), shop coins (1 hour), and the event bone bag (3 hours). An independent floating window provides start/restart/stop controls; custom timers, editing, and notification actions are available.
- Processes screenshots in device memory; the app does not request network access.

## Requirements

- Android 11 or later for screenshots through the AccessibilityService.
- Portrait orientation and one of the supported board layouts: Backpack Blitz or Merge Madness.
- JDK 17 and Android SDK Platform 35 to build the project.

## Install and run

1. Build a debug APK using [docs/BUILDING.md](docs/BUILDING.md), or install the generated `app/build/outputs/apk/debug/app-debug.apk`.
2. Open **Pelyustka**, expand **Android permission**, tap **Enable access**, and enable **Pelyustka — game control** in Android Accessibility settings.
3. Return to the app, tap a module name, then tap **Open panel**.
4. Open the matching minigame, wait for animations to finish, and tap **Start**. Tap **Stop** to halt automated moves.

To use reminders, open **Set up timers**, claim a game reward, then start its timer. **Floating panel** opens a separate timer window that can coexist with the bot window. Restart a timer after claiming the next reward, or use the notification's **Claimed · restart** action. The **⋮** menu accepts the remaining time shown in the game for one countdown without changing the usual interval. Use **✎** to edit a timer and **Add timer** for custom reminders; starting a new custom timer immediately is optional.

Enable notifications and, when needed, Android's **Alarms & reminders** access in the timer settings section. Without exact-alarm access Android may delay background notifications. Timers persist through reboots and APK updates. See [docs/TIMERS-1.9.42.md](docs/TIMERS-1.9.42.md) for implementation and verification details.

Merge Madness requires the full 4×4 board to be visible. It detects the grid borders and anchors both board recognition and the merge chain below it to that grid. Tiles are matched to templates. If a frame is unclear or a rank cannot be identified confidently, the bot takes another screenshot and does not swipe.

The accessibility access is used to capture frames and dispatch swipes. The service does not inspect the game's accessibility view tree. See [docs/USAGE.md](docs/USAGE.md) for details.

## Move selection

The Backpack Blitz solver checks horizontal and vertical swaps, simulates matches and cascades, and estimates possible refills. The Merge Madness solver checks four directions, simulates merges of equal ranks, and models the next tile spawn. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for both modes.

The base score uses 50 points per cleared gem at a ×1 multiplier, based on observed in-game popups for 4, 5, and 10 gems. This is a move-selection estimate, not a guarantee of the actual game score.

## Limitations

- Board coordinates and color palette are calibrated for one portrait game layout. Changes to the board position, theme, or game version can reduce recognition accuracy.
- Merge Madness locates moderate board shifts from the grid borders, but current hint icons are learned automatically; obscured hints and major artwork changes may reduce recognition confidence.
- If a cell is uncertain or an animation is still running, the bot retries capture instead of swiping.
- Rocket stripe direction is not recognized from the screenshot; the solver models it randomly.
- Android limits screenshot frequency. The service enforces a minimum interval of about 334 ms, and game animations add more time.
- Cascade ranking uses random simulation and cannot predict future refills exactly.

## Build

From the project root in Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug --no-daemon --max-workers=1
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. See [docs/BUILDING.md](docs/BUILDING.md) for build and development notes.

## Privacy and reuse

Frames are processed in device memory and are not saved by the app. The manifest has no network permission, and app data backup is disabled. Reference screenshots live in `app/src/test/resources`; runtime screenshots are not recorded. Signing keys, local settings and build outputs are ignored by Git.

No license file is included, so reuse terms for the code are not specified.

## Modules and roadmap

Implementation and registration guide (Ukrainian): [docs/MODULES.md](docs/MODULES.md).
Proposed future modules: [docs/ROADMAP.md](docs/ROADMAP.md).
Current interface: [docs/USAGE.md](docs/USAGE.md).
Algorithm review: [docs/ALGORITHMS-1.9.39.md](docs/ALGORITHMS-1.9.39.md).
Rank 15 target and late-game verification: [docs/ALGORITHMS-LEVEL15.md](docs/ALGORITHMS-LEVEL15.md).
