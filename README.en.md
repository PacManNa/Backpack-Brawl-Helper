# Pelyustka — Backpack Brawl: Backpack Blitz bot

An Android app that uses an AccessibilityService to capture the 8×8 board, recognize gems, and choose an adjacent swap with the best estimated score. Recognition and move selection run locally on the device.

[Українська версія README](README.md)

## Features

- Recognizes seven gem colors, rockets, bombs, and portal/whirlwind specials.
- Evaluates up to 112 adjacent swaps on an 8×8 board.
- Estimates matches, special items, chained activations, and possible cascades after refills.
- Provides a floating **Start / Stop** panel with status and diagnostics.
- Processes screenshots in device memory; the app does not request network access.

## Requirements

- Android 11 or later for screenshots through the AccessibilityService.
- Portrait orientation and the Backpack Blitz board layout this recognizer is calibrated for.
- JDK 17 and Android SDK Platform 35 to build the project.

## Install and run

1. Build a debug APK using [docs/BUILDING.md](docs/BUILDING.md), or install the generated `app/build/outputs/apk/debug/app-debug.apk`.
2. Open **Pelyustka**, tap **Enable access**, and enable **Pelyustka — game control** in Android Accessibility settings.
3. Return to the app and open the floating bot panel.
4. Open the full Backpack Blitz board, wait for animations to finish, and tap **Start**. Tap **Stop** to halt automated moves.

The accessibility access is used to capture frames and dispatch swipes. The service does not inspect the game's accessibility view tree. See [docs/USAGE.md](docs/USAGE.md) for details.

## Move selection

The solver checks horizontal and vertical swaps, simulates resulting matches and cascades, then ranks legal moves. It estimates possible refills for the top three candidates using random samples. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the components and scoring details.

The base score uses 50 points per cleared gem at a ×1 multiplier, based on observed in-game popups for 4, 5, and 10 gems. This is a move-selection estimate, not a guarantee of the actual game score.

## Limitations

- Board coordinates and color palette are calibrated for one portrait game layout. Changes to the board position, theme, or game version can reduce recognition accuracy.
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
