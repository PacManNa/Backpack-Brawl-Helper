# 🎒 PackMate

**PackMate is an offline, unofficial Android companion for Backpack Brawl.**
It recognizes the game board on your phone and automates moves in **Backpack Blitz** and
**Merge Madness**. It also reminds you when timed game rewards are ready.

<p align="center">
  <a href="https://github.com/PacManNa/Backpack-Brawl-Helper/raw/refs/heads/main/downloads/PackMate-1.9.48.apk"><strong>⬇ Download APK 1.9.48</strong></a>
  &nbsp;·&nbsp;
  <a href="docs/USAGE.md">User guide</a>
  &nbsp;·&nbsp;
  <a href="docs/BUILDING.md">Build guide</a>
</p>

Download the [APK 1.9.48](https://github.com/PacManNa/Backpack-Brawl-Helper/raw/refs/heads/main/downloads/PackMate-1.9.48.apk) once and install it from your phone. **The APK installs offline, and the app does not request internet access.**

> PackMate is an independent community project and is not affiliated with or endorsed by the game developers.

## 🎮 Game modes

| Mode | What it does |
| --- | --- |
| **Backpack Blitz** | Reads the 8×8 gem board, evaluates adjacent swaps, special gems, and possible cascades. |
| **Merge Madness** | Reads the 4×4 board and hint chain, then models merges and the next tile spawn. |

### Backpack Blitz

- Recognizes seven gem colors, rockets, bombs, and portal specials.
- Evaluates up to 112 adjacent swaps per move and favors larger clears.
- Uses special gems and estimated cascades to rank otherwise similar moves.

### Merge Madness

- Locates the grid and matches tile images to the rank sequence shown below it.
- Simulates four swipe directions, equal-rank merges, and the next tile spawn.
- Waits for a clear frame when a cell is uncertain or an animation is in progress.
- Supports the game's 15-rank progression; late-game artwork still needs more
  real screenshots for full recognition coverage.

## 🔔 Reward timers

Built-in timers cover event tickets (4 hours), shop gems (1 hour), shop coins
(1 hour), and the event bone bag (3 hours). Start, restart, or stop them from a
separate floating menu. You can add and edit custom timers, with notifications
when a countdown finishes.

## ✨ Interface and privacy

- Ukrainian 🇺🇦, Czech 🇨🇿, and English 🇬🇧. The language button shows the flag
  and code of the **next** language; tap it to cycle through all three.
- Expandable module cards keep the main screen compact.
- Floating controls can be moved and collapsed while the bot continues running.
- Board recognition runs on the device. Screenshots are processed in memory
  and are not saved by the app. The app does not request internet access.

## 📱 Install the APK offline

1. Download the APK from [GitHub Releases](https://github.com/PacManNa/Backpack-Brawl-Helper/releases/latest) to your Android phone.
2. Open the downloaded APK. If Android asks, allow your browser or file manager
   to install unknown apps, then return to the installer.
3. Install PackMate and open it. No network connection is needed for installation.
4. In PackMate, tap **Android permission** and enable **PackMate — game
   control** in Android Accessibility settings.
5. Return to the app, expand a module, tap **Open panel**, open the matching
   minigame, and tap **Start** after the board settles.

An update can be installed over an existing copy when it has the same package
ID and signing key. The APK in the official release is signed with this
project's persistent release key. Android keeps the app's data and granted
permissions during a compatible update.

For reward reminders, open **Set up timers** and start a timer after claiming a
reward. See the [timer guide](docs/TIMERS-1.9.42.md) for details.

## 🧠 Move selection

Backpack Blitz ranks possible swaps using matches, special gems, and estimated
cascades. Its base estimate uses 50 points per cleared gem at a ×1 multiplier,
based on observed in-game messages for 4, 5, and 10 gems; this does not
guarantee the final score. Merge Madness compares four swipe simulations and
the predicted next-tile spawn.

## ⚠️ Known limitations

- Game layout, theme, or version changes can reduce recognition accuracy.
- An obscured hint chain or unfamiliar late-game artwork can prevent rank detection.
- Rocket stripe direction is not read from the screenshot and is modeled randomly.
- Android limits screenshot frequency; the service interval is about 334 ms at
  minimum, plus game animation time.
- Cascade estimates use random simulation and cannot predict the future board exactly.

## 📚 Documentation

- [User guide](docs/USAGE.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Adding modules](docs/MODULES.md)
- [Future clicker ideas](docs/ROADMAP.md)
- [Merge Madness rank-15 strategy](docs/ALGORITHMS-LEVEL15.md)
- [Localization](docs/LOCALIZATION-1.9.47.md)
- [Compact timer screen](docs/INTERFACE-1.9.48.md)
- [Build and signing guide](docs/BUILDING.md)

The project has no license file, so code reuse terms are unspecified.
