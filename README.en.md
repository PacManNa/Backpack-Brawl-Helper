# Pelyustka — separate bots for Backpack Brawl

An Android app with two independent modes: a match bot for Backpack Blitz and a 2048 bot for Merge Madness. Each has its own launch button on the home screen. Both read the board through an AccessibilityService and play locally on the device.

[Українська версія README](README.md)

## Features

- Recognizes seven gem colors, rockets, bombs, and portal/whirlwind specials.
- Evaluates up to 112 adjacent swaps on an 8×8 board.
- Estimates matches, special items, chained activations, and possible cascades after refills.
- Recognizes 17 rat ranks on Merge Madness's 4×4 board and selects a swipe direction using a search that models new tiles.
- Provides a floating **Start / Stop** panel with status and diagnostics.
- Creates local garden, chest, and reward timers with completion notifications and one-tap restart.
- Processes screenshots in device memory; the app does not request network access.

## Requirements

- Android 11 or later for screenshots through the AccessibilityService.
- Portrait orientation and one of the supported board layouts: Backpack Blitz or Merge Madness.
- JDK 17 and Android SDK Platform 35 to build the project.

## Install and run

1. Build a debug APK using [docs/BUILDING.md](docs/BUILDING.md), or install the generated `app/build/outputs/apk/debug/app-debug.apk`.
2. Open **Pelyustka**, tap **Enable access**, and enable **Pelyustka — game control** in Android Accessibility settings.
3. Return to the app and tap the launch button for the bot you want.
4. Open the matching minigame, wait for animations to finish, and tap **Start**. Tap **Stop** to halt automated moves.

To use reminders, open **Set up timers** and create one with a name and duration. A timer starts as soon as it is created. To show the countdown in the floating panel, tap **Floating panel** on the timer screen. After completing the game action, tap **↻** to start the same duration again.

Merge Madness requires the full 4×4 board to be visible. It detects the grid borders and anchors both board recognition and the merge chain below it to that grid. Tiles are matched to templates. If a frame is unclear or a rank cannot be identified confidently, the bot takes another screenshot and does not swipe.

The accessibility access is used to capture frames and dispatch swipes. The service does not inspect the game's accessibility view tree. See [docs/USAGE.md](docs/USAGE.md) for details.

## Move selection

The Backpack Blitz solver checks horizontal and vertical swaps, simulates matches and cascades, and estimates possible refills. The Merge Madness solver checks four directions, simulates merges of equal ranks, and models the next tile spawn. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for both modes.

The base score uses 50 points per cleared gem at a ×1 multiplier, based on observed in-game popups for 4, 5, and 10 gems. This is a move-selection estimate, not a guarantee of the actual game score.

## Limitations

- Board coordinates and color palette are calibrated for one portrait game layout. Changes to the board position, theme, or game version can reduce recognition accuracy.
- Merge Madness locates moderate board shifts from the grid borders, but changes to the rat illustrations may require updated templates.
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

Frames are processed in device memory and are not saved by the app. The manifest has no network permission, and app data backup is disabled. Screenshots, recordings, local Android settings, and build outputs are excluded from the repository.

No license file is included, so reuse terms for the code are not specified.
