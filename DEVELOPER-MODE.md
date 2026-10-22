# Developer Mode

Developer Mode is Toolz's on-device sandbox and diagnostics console. It ships in
release builds behind a visible Settings switch, but every fixture and diagnostic
is local. There is no account, no upload path and no telemetry: reports leave only
when the user copies them or chooses a destination in the system share sheet.

## Naming and contracts

- User-facing copy uses **Developer Mode**, **Developer console**, **sandbox** and
  **fixture**. Avoid “demo mode”, “mock” and “spoofed” in UI text.
- Internal identifiers are stable: `SpoofGate`, `spoofAll()`, `spoof_*` preference
  keys and the `DevSpoof` logcat tag.
- The master switch is the final gate. When it is off, every tool reads live data
  and the console shows its locked panel.
- Sandbox fixtures are in-memory values. They must not be written into real Room
  tables, MediaStore, user files or network requests.
- Destructive developer actions always require confirmation. Enabling or disabling
  sandboxes never restarts the app automatically.

## Entry

- **Settings → Developer Mode** controls the master switch. The developer console
  row is visible only while Developer Mode is on. Navigation waits for the
  DataStore write to finish, so the console cannot open before its flag is saved.
  If the write fails, the switch reflects the stored value and a snackbar reports
  that Developer Mode could not be enabled or turned off.
- `Screen.DeveloperMode` is also reachable from the optional shake gesture. A stale
  back-stack entry still renders the locked panel when the master is off.
- Enabling uses a warning dialog that explains fixtures, blocked live data and the
  version-change reset.
- Shake-to-open is opt-in in **System → Behavior**. The accelerometer listener is
  registered only while Developer Mode and shake-to-open are both enabled.
- Turning Developer Mode off dismisses any open command palette and returns every
  tool to live data immediately. The per-tool selection is kept for the next
  session; **Reset Developer Mode** clears it.

## Console layout

The top bar shows the active sandbox count and a command palette. The console has
four tabs:

### Overview

- Master power card with the current sandbox count.
- Active sandbox chips. Each chip removes one sandbox from the active set;
  **Manage all sandboxes** opens the full list and its configurators.
- **Presets**: Showcase, Edge cases and Travel. Applying a preset is one atomic
  write that sets the master switch, the exact sandbox set and the fixture values.
- Live runtime stats: uptime, heap, native heap, threads and a short heap history.
- Diagnostic report summary with a route to Diagnostics.

### Sandboxes

- Search, an **Active** filter and group filters across all 18 sandboxes.
- Grouped list rows keep the set scannable. Tapping a row opens the configurator;
  the switch turns that sandbox on or off without opening the sheet.
- Each row shows the tool, group, network policy and a fixture-accurate summary.

The configurator is a bottom sheet with the live fixture summary, active switch
and editable fixture fields. Slider writes happen on release. Text writes
are debounced by 350 ms and local edits survive DataStore echoes. Pending text is
flushed on dismissal or disposal. The sticky footer opens the live tool screen and
dismisses the sheet once.

### Diagnostics

- **Diagnostic report**: copy or share an on-device snapshot containing app/build,
  device, process, runtime, appearance, active sandbox names and crash-lab history.
  Log lines are never included automatically.
- **Logcat**: merges the local `DevLogHub` ring buffer with an app-PID logcat
  stream. Full-system scope requires authorized Shizuku. The viewer supports live
  tail, severity filters, text search, copy-all, copy warnings/errors and a true
  clear. Clearing empties the local hub and applies a cutoff so stale system lines
  do not immediately reappear. Tailing runs only while Diagnostics is visible and
  the screen is resumed; switching tabs or backgrounding the app stops it.
- **Crash lab**: crash-on-next-launch plus NPE, IllegalState, ANR and OOM tests.
  History is written before the failure and survives the crash. If the history
  write fails, the test aborts with an error snackbar and the app keeps running.
- **Crash history**: the newest 20 events, presented newest-first.
- **Notification lab**: six real test notifications on app channels with the stable
  ID band `60001–60006`. Sending respects `POST_NOTIFICATIONS`; cancel only removes
  that band. The matrix reflects notifications currently active in the system, not
  just IDs sent during this process.

### System

- Application and build context, refreshed from the running process.
- Appearance context: theme, language, font scale and layout direction are
  read-only summaries with a route to the real appearance settings.
- Behavior: shake-to-open.
- Session: explicit **Restart app** and **Reset onboarding**.
- Storage: cache size, app storage and a guarded clear-cache action that reports
  the bytes actually freed.
- Room inspector: read-only table names and row counts. SQLite/Room bookkeeping
  tables are skipped; application tables are listed by name without redaction.
- Preferences inspector: sorted, bounded and redacted for secrets, tokens, keys,
  passwords and cryptographic material.
- Screen launcher and system/community links, including the Toolz Discord.
- **Danger zone**: clear all Room tables, reset fixture values and reset Developer
  Mode. Each action asks for confirmation.

## Sandbox data model

- `DevToolId` is the single source of truth for the 18 tools, their stable
  `spoof_*` preference keys, display order, group, network policy and open route.
  `SettingsRepository.spoofKeyMap` is derived from that enum, so a new sandbox
  cannot silently lose its persisted flag.
- `SpoofGrid(master, flags)` computes effective state. Persisted per-tool flags do
  not bypass the master switch.
- `SpoofParams` is persisted as one JSON value. Parsing tolerates malformed,
  missing, non-finite and out-of-range values by falling back or clamping; text is
  capped at the documented limits.
- Master changes, enable-all, presets, fixture reset and the version reset are
  atomic DataStore edits. A preset cannot leave the master on with a half-applied
  tool set.
- `SpoofGate` is the hot read path used by tool ViewModels. `SpoofSandboxException`
  is the fail-closed guard for network-backed sends such as Whisper.
- Tool screens render fixture-shaped content without banners or watermarks. The
  dashboard star and long-press dialog identify an active sandbox and can turn it
  off. `routeToDevTool()` is the only route-to-sandbox map.

## Version changes and reset

`SettingsRepository.ensureDeveloperModeVersion()` runs from `MainActivity.onCreate`.
When the version code changes it atomically:

- turns the master switch off;
- turns all 18 sandbox flags off;
- clears crash history and the crash-on-next-launch marker;
- keeps shake-to-open and fixture values, which carry no live-data risk.

**Reset Developer Mode** applies the same reset on demand. **Reset fixture values**
restores `SpoofParams()` defaults without changing the current sandbox selection.
The Settings subtitle reflects the live count: `Off · local console locked` when
the master is off, `On · console unlocked` when no sandbox is active, or
`On · N sandbox(es) active · console unlocked`.

## Privacy

- Diagnostics are generated locally and never uploaded by Toolz.
- Reports include active sandbox names and crash-lab history, never raw log lines,
  clipboard contents, tokens or account data.
- Share opens the system chooser; the app does not choose a destination. Discord is
  a manual paste handoff: `https://discord.gg/aAswRUerwh`.
- Preference snapshots redact sensitive key/value pairs before display.

## Source map

| Area | Files |
|---|---|
| Console shell | `ui/screens/developer/DeveloperModeScreen.kt`, `DeveloperConsole.kt`, `DeveloperConsoleComponents.kt`, `DeveloperCommands.kt` |
| Tabs | `DeveloperOverviewTab.kt`, `DeveloperSandboxesTab.kt`, `DeveloperDiagnosticsTab.kt`, `DeveloperSystemTab.kt` |
| Pure catalogs | `DevModels.kt`, `DevFormatters.kt`, `DevLogcat.kt`, `DevPresets.kt`, `DevRoutes.kt`, `DevToolFilters.kt`, `SpoofToolSpecs.kt` |
| State/actions | `DeveloperModeViewModel.kt` |
| Components | `DevWidgets.kt`, `SpoofTileGrid.kt`, `SpoofConfiguratorSheet.kt`, `LogcatViewer.kt`, `RuntimeStatsCard.kt`, `CommandPalette.kt`, `CrashHistoryCard.kt`, `RouteLauncherSheet.kt`, `SpoofQuickFix.kt`, `DevRouteMap.kt`, `SpoofSandboxState.kt` |
| Data layer | `data/developer/DevToolId.kt`, `SpoofParams.kt`, `SpoofGrid.kt`, `SpoofGate.kt`, `SpoofSandboxException.kt`, `DevLogHub.kt`, `DevUiOverrider.kt`, `ShakeDetector.kt`, plus the `Fake*Sandbox` fixtures |
| Persistence | `data/settings/SettingsRepository.kt`, `SettingsViewModel.kt` |
| Entry points | `MainActivity.kt`, `SettingsScreen.kt` |

## Verification

Focused JVM tests cover fixture parsing, key coverage, effective sandbox state,
presets, field specs, route mapping, report formatting, logcat parsing/merging and
the notification ID band:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.frerox.toolz.developer.*'
./gradlew :app:compileDebugKotlin
```

Manual checks:

1. Master off: Settings console row hidden, stale route shows the locked panel,
   every sandbox inert.
2. Enable: warning appears, console opens after the write completes, no locked
   flash.
3. Enable all: all 18 flags and the master switch update atomically without a
   relaunch; use **Restart app** when a tool needs a cold process.
4. Apply a preset: exact tool set and fixture values persist across process death.
5. Configurator: sliders commit on release, text survives echoes, pending text is
   flushed on dismissal, and the tool route opens once.
6. Logcat: tail pause/resume, app/system scope, severity and text filters, clear,
   copy, Shizuku fallback.
7. Crash lab: the history event exists before the crash; crash-on-next-launch
   fires once.
8. Notifications: send all six, cancel only the band, restore permission state.
9. System: cache clear reports freed bytes, Room/prefs refresh, report redaction,
   destructive confirmations.
10. Dashboard star and long-press quick fix work for every mapped route.
