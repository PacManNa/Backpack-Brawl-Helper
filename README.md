# 🌸 Pelyustka

**On-device Android game bots and reward timers for Backpack Brawl.**
Pelyustka recognizes the game board on the phone and automates moves in two modes.

## 🎮 Game modes

| Mode | What the bot does |
| --- | --- |
| **Backpack Blitz** | Reads an 8×8 board, evaluates adjacent swaps, special gems, and possible cascades. |
| **Merge Madness** | Reads a 4×4 board and its hint chain, then models merges and the next tile spawn. |

### Backpack Blitz

- Recognizes seven gem colors, rockets, bombs, and portal/whirlwind specials.
- Evaluates up to 112 adjacent swaps per move and prefers larger immediate clears.
- Breaks ties using special gems and estimated cascades after the board refills.

### Merge Madness

- Locates the grid and matches tile images to the rank sequence shown below it.
- Checks four swipe directions, simulates equal-rank merges, and models the next tile.
- If a cell is uncertain or an animation is in progress, it captures another frame instead of swiping.
- The game has 15 ranks; real screenshots are still needed to verify recognition of every late-game object.

## 🔔 Reward timers

Four presets are included: event tickets (4 hours), shop gems (1 hour), shop coins
(1 hour), and the event bone bag (3 hours). Start, restart, or stop them from a
separate floating menu. Custom timers, editing, and notifications are supported.

## ✨ Interface

- Ukrainian and Czech. The button before the module count shows the flag and code
  of the language it switches to: 🇨🇿 CZ or 🇺🇦 UA.
- Module cards start with an icon and name; tap to open descriptions and settings.
- Each module has independent timing and floating-panel settings.
- Panels can be collapsed and moved; the timer menu can run alongside a game bot.

## 📱 Requirements

- Android 11 / API 30 or later for screenshots through the AccessibilityService.
- Portrait orientation and one of the supported game modes.
- JDK 17 and Android SDK Platform 35 to build the project.

## 🚀 Get started

1. Build the APK using the [build guide](docs/BUILDING.md), or install a prepared APK.
2. In Pelyustka, open **Android permission** and enable **Pelyustka — game control** in Android Accessibility settings.
3. Return to the app, expand a module, and tap **Open panel**.
4. Open the matching minigame, wait for animations to finish, and tap **Start**.

For reward reminders, open **Set up timers** and start the relevant timer after claiming a reward. Its floating menu can run beside a bot panel. See the [timer guide](docs/TIMERS-1.9.42.md).

## 🧠 Move selection

Backpack Blitz models swaps, matches, and cascades. Its base estimate uses 50
points per cleared gem at a ×1 multiplier, based on observed in-game messages
for 4, 5, and 10 gems. This ranks moves; it does not guarantee the final score.
Merge Madness compares the results of four swipe simulations and the predicted
next-tile spawn.

## ⚠️ Limitations

- Changes to the game's layout, theme, or version can reduce recognition accuracy.
- An obscured hint chain or unrecognized late-game artwork may prevent rank detection.
- Rocket stripe direction is not read from the screenshot and is modeled randomly.
- Android limits screenshot frequency; the service interval is about 334 ms minimum, plus game animation time.
- Cascade estimates use random simulation and cannot predict the future board exactly.

## 🛠️ Build

See [docs/BUILDING.md](docs/BUILDING.md) for JDK, Android SDK, APK signing, and verification.
Local signing configuration and keystore files are not stored in this repository.
Configure your own key to sign builds; it cannot update an app installed from an
APK signed with a different key.

## 🔒 Privacy and reuse

Frames are processed in device memory; the app does not request network access.
Images in `app/src/test/resources` are game-board test fixtures, not screenshots
saved by the app during play. Git ignores local settings, signing keys, and APKs.

There is no license file, so code reuse terms are unspecified.

## 📚 Documentation

- [User guide](docs/USAGE.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Adding modules](docs/MODULES.md)
- [Future clicker ideas](docs/ROADMAP.md)
- [Merge Madness rank-15 strategy](docs/ALGORITHMS-LEVEL15.md)
- [Ukrainian and Czech localization](docs/LOCALIZATION-1.9.44.md)
