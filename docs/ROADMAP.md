# Future modules and interface ideas

These are proposals following 1.9.37. The working app currently has two game
modules; the capabilities below need separate implementation.

## Candidates for future modules

| Priority | Module / proposed ID | Behavior | Data needed |
| --- | --- | --- | --- |
| 1 | Simple tapper / `point_tapper` | Tap a selected point, set an interval and repeat limit, allow manual stop | Use case, screen point selection, desired interval bounds |
| 2 | Action sequence / `action_sequence` | Edit points and swipes with pauses, repeat a sequence a limited number of times | Example sequences, coordinate anchoring, completion rules |
| 3 | Image trigger / `image_trigger` | Find an icon in a region, act on a confident match, wait for a state change | Target sample, similar elements, examples of appearance/disappearance and occlusion |
| As games allow | New event or minigame | Separate rules and frame-based action evaluator | Board, hint, and result screenshots; game rules and animation examples |
| After gathering examples | Garden and rewards | Move from local reminders to assistance with visible in-game actions | Screens for available action, execution, and completion; goals and expected results |

Local timers already work for the garden. Automated collection or reward claiming
has not been implemented; timer duration alone cannot determine those rules.

## Architecture before adding a third execution type

1. Move the two existing lifecycles out of the service into session adapters.
   Keep board algorithms in their existing classes; move only the component
   wiring.
2. Define a session contract: start, stop, whether a frame is needed, frame
   processing, next-action description, and panel diagnostics. A simple tapper
   may not need board recognition, so frame capture should be optional.
3. Define a shared gesture object for taps/swipes, coordinates, duration, and
   expected result. Keep gesture execution in one service.
4. Add settings schemas for repeat count, interval, and region selection. Key
   settings by the module's stable ID.
5. Define behavior for manual stop, module switching, panel close, orientation
   changes, and returning from the background.

Preserve existing games' search depth, recognition thresholds, and timings during
this refactoring. Screenshots and tools for comparison are in `tools/`.

## Future interface

- **Favorites:** mark an expanded card and move favorites to the top of the
  catalog. Keep only the icon and name in the collapsed heading.
- **Search and categories:** add as the catalog grows; possible groups include
  “Minigames,” “Universal,” and “Scenarios.”
- **Setting profiles:** save multiple configurations for one module, such as
  different sequences or screen regions.
- **Target editor:** show points, regions, and swipe paths for universal modules;
  save screen-size and orientation anchoring.
- **Diagnostics:** show a shared short status and details only when expanded;
  let each module adapter define its diagnostic fields.
- **Automated builds:** configure GitHub Actions with access to the same key via
  repository secrets. Until then, builds use a local key; never add secrets or
  keystores to Git.

## Implemented in 1.9.37

One catalog, automatic cards, palettes, supported-setting schemas, independent
timeouts with migration, shared expandable sections, compact floating panel,
icon collapse, saved width and position, collapsed help, and compact permission
status. See [MODULES.md](MODULES.md) and [INTERFACE-1.9.37.md](INTERFACE-1.9.37.md).
