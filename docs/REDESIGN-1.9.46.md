# PackMate interface refresh — 1.9.46

## Brand and visual system

- Renamed the user-facing app label to **PackMate** in Ukrainian, Czech, and English.
- Added an original vector backpack mark. It uses a general backpack silhouette and newly drawn colors and shapes; it does not reuse game artwork.
- Kept the existing Android application ID and persistent signing configuration so this release can update the installed app without resetting its data or accessibility permission.
- Replaced the plum palette with a dark slate base, clear cyan and gold module accents, and a brighter green for ready or enabled states.

## Main screen

- Put the Android accessibility status before the module cards so setup readiness is immediately visible.
- Reworked the status card into a clear enabled/needed state, with a matching icon and color.
- Increased spacing and corner radii across module and reminder cards, and added a timer icon and a stronger primary action.
- Kept the language control in the header and retained the three-language cycle.

## Floating panels and timers

- Applied the same slate palette and brighter state colors to the bot overlay, board cells, timer screen, and reminder overlay.
- Kept existing controls and game behavior unchanged. This release changes presentation and app branding only; recognition, move selection, swipe timing, and timer behavior are not redesigned here.

## Distribution and independence

- The README links directly to the release APK for offline installation and describes the download steps.
- PackMate is an independent community project and is not affiliated with or endorsed by the game developers.
- The compatibility names for supported game modes remain in the feature descriptions; the app itself is branded PackMate.
