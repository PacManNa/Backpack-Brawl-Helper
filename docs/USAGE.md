# Using PackMate

## First launch

1. Install the APK on a device running Android 11 or later.
2. Open the app, tap **Android permission**, then **Enable access**.
3. In Android settings, open the **PackMate — game control** service and enable
   it. Confirm Android's system warning about the service's capabilities.
4. Return to **PackMate** and open the floating panel.

The service uses Android APIs for screenshots and gestures. It does not read the
game's content from the accessibility view tree.

## Interface language

The main-screen header has a language button before the module count. It shows
the flag and code of the active language: **🇺🇦 UA** in Ukrainian,
**🇨🇿 CZ** in Czech, or **🇬🇧 EN** in English. Tapping it cycles to the next
language. The choice is retained when the app closes and when an APK is updated.

Settings, help, floating panels, timers, and notifications are translated too.
Custom and edited timer names remain as entered. Switching languages does not
restart countdowns or stop a bot.

## Wait for board changes

Tap a bot's name on the main screen. Its expanded card contains a description,
an **Open panel** button, and a **Wait for board change** slider. Tap the name
again to collapse the card to its icon and name. Cards are collapsed on a fresh
launch; rotation preserves expanded cards.

The slider sets the retry delay if the board did not change. Each module has its
own value. After upgrading from 1.9.36, both modules inherit the previous shared
value and can then be configured independently. The range is 0.4–4.0 seconds in
0.1-second steps. This timeout is not added after a successful move: the bot
continues as soon as it confirms the board state. Existing settings survive an
update; move the slider left to reduce the retry delay.

Version 1.9.35 reduced board-boundary search and move-computation time. Search
depth, move evaluation, and swipe-result checks are retained. Further pace
depends on game animations and Android screenshots: the current capture method
has a minimum interval of 334 ms. A 0.4-second slider setting only shortens the
retry delay when a previous swipe did not change the board.

## Floating panel

- The panel opens compactly with the module name, status, and one **Start / Stop**
  button. The button action follows the bot's current state.
- **Show board** opens diagnostics; **Hide board** hides them. The choice is
  stored per module. Timers have their own window, described below.
- Drag the panel by the heading labeled **⠿ Drag**. Position is remembered per
  module and separately for portrait/landscape. After a size change, the panel
  remains inside the usable screen area.
- **To icon** collapses the whole menu; the bot keeps running. The collapsed icon
  can be moved. Tap **Open** on it to restore the panel.
- The large **×** stops the bot and closes the panel.
- The name and status do not expand the menu when tapped. Use the labeled controls.
- In 1.9.39, **Start / Stop**, **Show / Hide board**, and **To icon** have a
  minimum height reduced by 40%: about 29 instead of 48 dp.
- Set width with the **Floating panel** slider in the expanded module card.
  Width is saved per clicker; height depends on diagnostics.

Only one clicker runs at a time. Opening another module stops the previous one.
The timer window can remain open alongside a clicker and stores its position
separately. Saving a position for landscape does not change the supported board
orientation.

**Android permission** shows compact status and expands controls when tapped.
The **First launch** help section is also collapsed by default.

## Reward reminders · 1.9.42 and later

Open **Set up timers** on the main screen. Preset timers start stopped; existing
countdowns from earlier versions continue.

| Reward | Interval |
| --- | --- |
| Event tickets | 4 hours |
| Shop gems | 1 hour |
| Shop coins | 1 hour |
| Bone bag | 3 hours |

Gems and coins have independent countdowns because they may be claimed at
different times.

1. Claim a reward in the game and tap **Start** beside its timer.
2. If the game already shows a countdown, open **⋮ → Set remaining time from
   game**. Enter hours, minutes, and seconds. This changes only the current
   countdown; the next restart uses the full interval.
3. Allow notifications in **Notifications and exact timing**. On Android 12+,
   you can separately allow **Alarms & reminders** for exact timing. Without
   this access, Android may delay notifications.
4. **Floating menu** opens a separate window over the game. Each row has
   **Start / ↻ Restart** and **Stop**. Drag the heading to move it. **To icon**
   leaves the nearest countdown or number of ready rewards; tap the icon to
   expand the list. **×** closes only the window.
5. When **READY** appears, claim the reward and tap **↻ Restart**. The
   notification offers **Claimed · restart** and **Stop**. Tapping the
   notification itself opens the timer in the app.

**Stop** clears the countdown, keeps its name and interval, and removes its
notification. It stops rather than pauses. Closing the window, collapsing to an
icon, or closing the bot does not stop timers. Timers do not repeat
automatically; restart them after actually claiming a reward.

**✎** edits the name, emoji, and interval. The current countdown continues;
choose **Save and restart countdown** to apply the new interval immediately.
**Add timer** creates a custom reminder from 1 second to 365 days; **Start now**
is optional. **⋮ → Delete timer** asks for confirmation. **Add missing game
timers** restores deleted presets without overwriting edited ones.

Countdowns and settings are stored locally and restored after a phone reboot or
APK update. Regular timers and notifications do not require the accessibility
service; it is needed only for the floating window. For plants or other rewards,
create custom timers using the game's displayed time. Technical details:
[TIMERS-1.9.42.md](TIMERS-1.9.42.md).

## Game context

The official description presents **Wild Garden** as a persistent idle mode:
plant seeds, collect resources, and gradually develop the garden. In version 7.0,
growth times were increased; the team said harvests and rewards had become worse
relative to the time spent. As of October 14, 2026, the latest notes are 7.1.1:
they fix game timers that could drift because of timezone or an incorrect device
clock. A future Season 8 rework is only planned in the team blog: plant timers
are expected to use several fixed durations, with the longest no more than a
day; the numbers are not final. The app therefore stores a duration chosen by
the player rather than a table that would quickly become outdated.

Version 7.1 also added more hints for the garden shop, free boosts, and ready
visitors. Screenshots show countdowns for free gifts and the bone bag; seasonal
chests beside their prices do not show their own countdown. Create a separate
reminder for each action.

Sources: [official Wild Garden overview](https://www.backpackbrawl.com/blog/season-4-back-to-nature/),
[7.1.0 notes](https://www.backpackbrawl.com/patch-notes/7-1-0/),
[7.1.1 fixes](https://www.backpackbrawl.com/patch-notes/7-1-1/), and
[Season 8 garden plan](https://www.backpackbrawl.com/blog/wild-garden-7-1-and-season-8/).

## Playing

Tap a mode name on the main screen to expand its card. The button inside opens a
floating panel configured for that game.

### Backpack Blitz · 8×8 board

1. Tap **Backpack Blitz**, then **Open panel** inside its card.
2. Switch to Backpack Brawl and wait for the full 8×8 board to appear without
   animation or a dialog covering it.
3. Tap **Start** on the floating panel. The bot reads the board, shows its status,
   and makes a swap.

### Merge Madness · 2048 · 4×4 board

1. Tap **Merge Madness**, then **Open panel** inside its card.
2. In Backpack Brawl, open the event, hold the device in portrait, and wait for
   the full 4×4 board to appear without animation or a dialog covering it.
3. Tap **Start**. The bot matches tile images to the rank list and chooses one
   of four swipe directions.
4. If a tile rank is uncertain or the board is elsewhere on the screen, the bot
   captures another frame without making a move.

For either mode, tap **Stop** to halt moves. Floating-panel controls are described
above; calibration controls are not part of the panel.

## If the bot does not move

- Make sure the accessibility service is enabled, the correct minigame is open,
  and the entire board is visible.
- Wait for explosions, falling pieces, and new pieces to settle. The bot reads
  the board again if a frame is unstable or unclear.
- If the board is shifted, has a different aspect ratio, or the tile artwork
  changed, this recognizer may not match the game.
- Try stopping and restarting the bot once the board is stable.

To turn off access completely, stop the bot and disable the service in Android
Accessibility settings.
