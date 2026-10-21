# Reward reminders — 1.9.42

## Preset timers

Intervals follow the requested schedule and can be edited.

| Stable ID | Name | Interval |
| --- | --- | --- |
| `reward.event_tickets` | 🎟️ Event tickets | 4 hours |
| `reward.shop_gems` | 💎 Shop gems | 1 hour |
| `reward.shop_coins` | 🪙 Shop coins | 1 hour |
| `reward.event_bones` | 🦴 Bone bag | 3 hours |

Coins and gems are independent because the supplied screenshot showed different
remaining times. Presets are added stopped. Existing timers, names, intervals,
and running countdowns are retained; matching an ID or known previous name does
not create a duplicate. A deleted preset does not reappear automatically; use
**Add missing game timers** to restore it.

## Controls

- **Start** begins a full interval after the reward is claimed.
- **Restart / ↻ Restart** starts the interval from now, replacing the previous
  countdown, alarm, and notification.
- **Stop** clears the current countdown and notification while keeping the
  definition. Stop does not preserve a remaining time as a pause.
- **⋮ → Set remaining time from game** accepts hours, minutes, and seconds for
  one run. For example, gems can be synchronized to `00:31:12` while their next
  restart still uses the `01:00:00` interval.
- **✎** edits the name, emoji, and interval. The current deadline is retained;
  **Save and restart countdown** applies the interval immediately.
- **Add timer** saves a custom timer; starting it immediately is optional. The
  input range is 1 second to 365 days. Deletion requires confirmation.

After completion, **READY · claim reward** appears. The user starts the next
countdown after actually claiming the reward, so missed time does not cause false
repeat reminders.

## Separate floating window

`TimerOverlay` is an independent `TYPE_ACCESSIBILITY_OVERLAY`, separate from
the clicker panel. It can be open alongside a bot. Active countdowns are sorted
by completion time, with stopped timers below; long lists scroll. Each timer
shows its name, status, and two labeled buttons.

Drag the heading to move it. **To icon** leaves the nearest countdown or number
of ready rewards; tap the icon to expand the list. **×** closes only the window.
**Manage timers** opens the app and collapses the window so it does not cover the
editor.

Panel and icon positions are stored separately (`timers`, `timers_bubble`), as
are portrait and landscape positions. System bars, display cutouts, and font
scaling are accounted for. The accessibility service is needed for the floating
window; regular countdowns and notifications do not depend on it.

## Notifications and Android

Each run has a unique token. It gets one notification, with no duplicate when
the UI is refreshed. An old notification or alarm cannot change a newer
countdown. Tapping a notification opens the relevant timer; **Claimed · restart**
and **Stop** work without opening the timer screen.

The **Notifications and exact timing** section provides:

- notification permission on Android 13+ and a link to system settings;
- **Alarms & reminders** on Android 12+ for exact `AlarmManager` timing.

With access, the app uses `setExactAndAllowWhileIdle`; without it, it uses
`setAndAllowWhileIdle`. The countdown displayed in the menu remains accurate in
both cases, but Android may delay an inexact alarm. Doze limits, alarm frequency,
force-stopping the app, and phone-vendor restrictions can affect delivery even
with access. The app does not keep a permanent background service or wake the
phone every second while the menu is closed.

If notifications are denied, a ready run is not marked as delivered. After
permission is granted and the app returns to the foreground or the menu is
reopened, ready reminders are delivered. Timers do not restart automatically.

## Persistence and migration

`TimerCodec` uses the same JSON key, `reminder_timers_v1`, in
`pelyustka_settings`. The old fields `deadline_ms`, `deadline_wall_ms`, and
`deadline_elapsed_ms` remain supported. `run_token` and `boot_count` are added;
the previous delivery marker is migrated to the token without duplicating a
notification.

While the device is running, countdowns use monotonic `elapsedRealtime`. A
`TIME_SET` event updates the fallback wall-clock deadline while preserving the
current remaining time. After a real reboot, remaining time is restored from
wall-clock time; the phone cannot notify while off, and an expired countdown
becomes ready after boot. `MY_PACKAGE_REPLACED` reschedules alarms without
restarting countdowns. The old `PendingIntent` identity is preserved so an
update replaces the previous alarm.

The app does not read timer values from screenshots, watch ads, or claim rewards.
The user enters a remaining time and confirms a restart manually.

## Verification and APK

`testDebugUnitTest assembleDebug assembleRelease` completed successfully:
**91 tests, 0 failures, 0 skipped**, including 35 timer tests:

- 10 checks for states, intervals, one-time remaining time, and preset definitions;
- 20 checks for storage, alarms, permissions, notifications, stale actions,
  editing, reboot, clock changes, and old-data migration;
- 5 Robolectric Android UI checks for buttons, editor, in-game remaining time,
  add-without-start, and the independent window alongside the bot.

Android checks ran under Robolectric API 33. Delivery to a locked phone,
appearance on different screens, and vendor restrictions still need device
verification; no physical phone was available in this environment.

Release: `ua.borlock.autotap`, version `1.9.42`, `versionCode=73`.
Compared with APK 1.9.41, package ID, certificate, and accessibility-service
component match, while the version code increased from 72. It can be installed
over the previous release while keeping app data and previously granted service
access. Any new notification/exact-alarm permissions are granted separately as
needed.

Signing certificate SHA-256:

```text
78b0b3adc65fdd195744113386994b80319cfd119f1221f6960dbfc02c234985
```

Release APK SHA-256:

```text
4a7812e532d9cb1136610c32ec505f1ac0171ffddd1ba060180bf9b45006d1e1
```
