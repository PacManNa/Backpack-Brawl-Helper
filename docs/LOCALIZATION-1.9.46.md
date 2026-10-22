# Ukrainian, Czech, and English localization — 1.9.46

## Language switch

The header button appears immediately before the module count. It displays the
flag and code of the next language, then cycles through:

1. Ukrainian → 🇨🇿 CZ
2. Czech → 🇬🇧 EN
3. English → 🇺🇦 UA

Ukrainian remains the default for existing installs unless the user has
already selected another language. The choice is stored locally in
`pelyustka_locale`, key `language`, as `uk`, `cs`, or `en`. The phone language
is not changed.

## Coverage

All three resource sets include the main screen, module descriptions and
settings, help, floating bot panel, recognition diagnostics, timer screens and
dialogs, reminder overlay, notifications, accessibility labels, and plural
forms. Recognition messages are translated at the UI boundary, leaving the
underlying solver output unchanged.

Custom timer names remain as entered. Unmodified built-in reward timer names
are translated when displayed, without rewriting their stored definitions.

## Implementation

- `values/strings.xml` contains Ukrainian strings.
- `values-cs/strings.xml` contains Czech strings.
- `values-en/strings.xml` contains English strings.
- `AppLanguage` stores the selected locale and supplies the target flag/code.
- `LocalizedActivity` applies the selected resources to the main and timer
  screens. Floating windows and notifications use the same locale.

The selected locale is app-only; it does not change Android's system locale or
the game session. Switching languages rebuilds the interface without restarting
the bot or countdowns.

For another language, add a `values-<language>` resource directory, translate
the complete string and plural sets, and extend the language cycle and header
accessibility description in `AppLanguage` and the locale resources.
