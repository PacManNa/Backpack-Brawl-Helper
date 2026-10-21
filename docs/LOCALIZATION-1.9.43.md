# Ukrainian and Czech languages — 1.9.43

## Header button

The main screen has a **UA / CZ** button before the module count. Its label shows
the current language; one tap switches languages. The accessibility description
names both the current and next language. Ukrainian remains the default after
upgrading.

The choice is stored locally in `pelyustka_locale`, key `language`, as `uk` or
`cs`. The phone language is not changed. Expanded module cards retain their
state when the main screen is rebuilt.

## Translation coverage

- Main screen, module names and descriptions, settings, and help.
- Floating bot panel: buttons, states, diagnostics, and swipe directions.
- Timer screen, editor, dialogs, separate reminder window, and its icon.
- Preset reward names, countdown statuses, and day units.
- Notifications, action buttons, and reminder-channel name/description.
- Accessibility labels.

Custom names and renamed preset timers are not translated. Unmodified built-in
reward names are translated when displayed; their stored definitions are not
rewritten. If only the interval or emoji is changed in the Czech editor, the
default name remains eligible for translation when the language changes.

## Implementation

`values/strings.xml` contains Ukrainian defaults and `values-cs/strings.xml`
contains Czech values. Android `plurals` handles module counts with the correct
forms for each supported language.

`AppLanguage` creates and caches resources for the selected language for service
windows and receivers. `LocalizedActivity` applies the configuration to
`MainActivity` and `TimerActivity`, including dialogs. Translated strings are
not used as module IDs, timer IDs, preference keys, or alarm identities.

`ClickerModule` stores `nameRes` and `descriptionRes`, not pretranslated labels.
Recognition diagnostics are translated at the UI boundary, preserving the
recognizers' existing output format.

After switching languages, floating windows rebuild their appearance while
preserving position and collapsed state. The running bot session, gesture queue,
and countdowns do not restart. Visible reminders get new text without replaying
their sound; dismissed notifications are not shown again.

For a new module, add its strings to both resource sets. For another language,
add `values-<language>`, plural forms, and a selection option in `AppLanguage`.

## Build

Release: `ua.borlock.autotap`, **1.9.43**, `versionCode=74`.
`bash gradlew assembleRelease --no-daemon --max-workers=1` succeeded, including
resource and Java compilation and `lintVitalRelease`. The package includes the
`cs` resource configuration. Unit tests were not run for this change, and the
language switch was not checked on a physical phone in this environment.

The APK uses the saved persistent key. `apksigner verify` confirmed the
signature; the package ID and certificate were matched to APK 1.9.42, and the
version code increased from 73 to 74. App data, timers, and previously granted
service access are retained when installing over the prior release. No Android
permissions were added.

Certificate SHA-256:

```text
78b0b3adc65fdd195744113386994b80319cfd119f1221f6960dbfc02c234985
```

APK 1.9.43 SHA-256:

```text
858d1935ce3f4b73891cbded5b445968320f7e5020a02e5ccc15ee1a88ae16d2
```
