# Developer Mode

Internal console for diagnostics + controlled local-only spoofs. Ships in release, gated behind a visible Settings toggle with a warning popup. **No telemetry of any kind**: bug reports are built as local text and leave the device only via clipboard / the system share sheet, with the Toolz Discord (`https://discord.gg/aAswRUerwh` — same link as the Settings DISCORD button) as the handoff where the user pastes them.

**Naming**: user-facing strings say **Toolz Demo Mode** (mode name, dialogs, dashboard long-press). Internal identifiers are deliberately unchanged (`SpoofGate`, `spoofAll()`, `spoof_*` pref keys, the `DevSpoof` logcat tag) so greps and logcat contracts stay stable.

## Entry

- Settings → DEVELOPER section (`SettingsScreen.kt`, index 11): master toggle + `Developer console` row → `developer_mode` route (`Screen.DeveloperMode`, wired in `MainActivity.kt` `ToolzNavHost`).
- **Gating**: the `Developer console` row is only visible while the master switch is ON, and the console screen itself renders a locked panel ("Console locked") whenever the master is OFF — covering every entry path (Settings, shake, stale back stack). Re-enabling goes through the same warning dialog.
- Enabling shows a warning dialog (local-only fakes, crash/wipe risks, auto-disable on update) — in Settings AND in the console's own power card. Disabling is instant, no dialog.
- Optional shake-to-open: master ON + toggle ON → accelerometer (2.6g, 2s cooldown) navigates to the console. Zero sensor cost otherwise.
- The console itself also has a master switch (Home tab) — no Settings round-trip needed.

## Spoofed mode (Spoof all → relaunch)

- **Spoof all** (Home quick actions `SPOOF ALL` hero button, Spoofs tab header, command palette "Spoof all + relaunch"): `DeveloperModeViewModel.spoofAll()` turns master ON + all `DEV_TOOL` flags ON in one atomic write (`SettingsRepository.setAllSpoofs(true)`), then the Activity `recreate()`s so the whole app relaunches straight into full Spoofed mode.
- **Version status in Settings**: with master ON, the About section appends the console state to the version string — `V1.1.7 - Spoofed - Developer Mode` (the `- Spoofed` part appears only when at least one sandbox is effectively active; `- Developer Mode` when the master is on).

## Controlled-spoof contract (the important part)

Spoofs are a **local sandbox, not mocks**:

1. `DeveloperSpoofController` is the facade tool ViewModels ask; all state is delegated to `SpoofGate` (single hot cache of master + flags + params). Master OFF forces every tool to real data.
2. Each tool declares a `SpoofNetworkPolicy` (`DevToolId.kt`): `BLOCK_ALL`, `BLOCK_REMOTE_SPECS`, `SENSORS_OFF`, `JOBS_PAUSED`, `LOCAL_ONLY`.
3. When sandboxed, the real client is **never constructed/called** — fixtures come from RAM (`SpoofParams`, `FakeWhisperSandbox`, `FakeMusicSandbox`, `FakeCleanerSandbox`, …). Nothing is written to real Room tables, MediaStore, the real file system or the network.
4. Every injection is marked `// DEV-SPOOF:` — audit with `grep -rn DEV-SPOOF app/src/main`.
5. **Demo screens show no overlay of any kind** — no banner, no chip on the tool screen, no tint. The fake data IS the demo: everything on screen is shaped like the real thing (real phases, real delays, real widgets, honest refusals where a fake would lie). The demo flag surfaces only on the dashboard (red star) and the card long-press dialog.

### Whisper (exemplar)

`spoof_whisper` + master ON → `WhisperRepository.sendMessage` / `sendEncryptedImage` throw `SpoofSandboxException` **before** touching Supabase; `sendTypingStatus` early-returns. The console shows `FakeWhisperSandbox` fixtures (contacts + thread, bubbles preview in the configurator) with an `OFFLINE • NOTHING LEAVES THIS DEVICE` banner. Turning master OFF discards the thread and re-observes real chats.

### Battery (exemplar)

Sandboxed → `BatteryInfoViewModel` skips receiver registration + remote spec fetch and emits slider-driven fixtures (`SpoofParams.batteryLevel/batteryTempC/batteryCharging`). The screen stays watermark-free — only the fixture readings differ from reality.

## Auto-reset on version change

`SettingsRepository.ensureDeveloperModeVersion()` (called from `MainActivity.onCreate`) disables master + all 18 spoofs + crash/force-offline flags + crash history when `VERSION_CODE` changes. Slider values (`SPOOF_PARAMS_JSON`) and the shake toggle survive — they are harmless prefs, not fakes. `resetDeveloperMode()` behaves the same on demand.

## Console (Mission Control, 4 tabs)

Fixed top bar: back, live `SPOOF` count chip, **command palette** (`Tune` icon) and **reset**. Tabs: Home / Spoofs / Labs / System.

- **Home**: master power card (icon tile + "LOCAL ONLY • NO TELEMETRY" plate), live telemetry (heap/native/threads/uptime + rolling 48s heap sparkline, `TelemetryCard`), quick actions grid (`Spoof all` filled-primary relaunch, bug-report copy/share, Discord, all-spoofs-off, refresh storage), active-sandbox list (tap → configurator).
- **Spoofs**: 2-column tile grid grouped by `DevToolGroup` (Hardware/System-like/Network/App machinery), real icon per tool (`DevToolId.icon`), policy chip, glow border + `SPOOF • LOCAL` chip when armed, own search field. Tap = **bottom-sheet configurator** (`SpoofConfiguratorSheet`) rendered from `SpoofFieldSpec`s in `SpoofToolSpecs.kt` (number sliders / switches / text fields, bounds mirroring `SpoofParams.parse` — via the shared `DebouncedSlider`, all writes debounced 300ms); "Open tool screen" jumps to the real route. Whisper gets the bubble-thread preview.
- **Labs**: crash lab (+ **crash history**, last 8 of 20 stored events — DataStore `dev_crash_history_json`, auto-cleared on version bump) • notification lab (`[SPOOF TEST]`-tagged, 60001+ ID band, flood/cancel) • **logcat v2** (`LogcatViewer`: level chips, color-coded lines, live tail 1s — tail auto-stops when the tab or app is no longer visible —, filter, copy/clear; local `logcat -d` reads only) • data lab (verbose, force-offline, debounced mock-delay slider, range 0–10s matching the repository coercion) • deep-link launchpad (searchable, ~37 arg-free routes) • UI playground (segmented buttons for font scale / appearance / language **reflect the real current state**, incl. "current %.2f×" readout; RTL, perf HUD, endpoints).
- **System**: status & build (+ copy/share report + shake toggle), storage/DB inspector (counts only; vault/whisper/key tables redacted), DataStore inspector (own search field; sensitive values redacted by `snapshotPrefs()`), **Danger zone with ARM-then-fire** (arm switch first, then all-spoofs-off / reset).

## Bug report & Discord handoff (no telemetry)

- `DeveloperModeViewModel.buildBugReport(info)` → app/device/spoofs/heap/uptime + local crash-lab history. Copyable anywhere in the console.
- Share = `DevUiOverrider.shareText` (system chooser — the user picks the destination; the app uploads nothing).
- `DevUiOverrider.openToolzDiscord` opens the same invite the Settings DISCORD button uses (`TOOLZ_DISCORD_URL` = `https://discord.gg/aAswRUerwh`). The user pastes the report there.

## Testing checklist

- Fresh install: Settings toggle works, warning shows once per enable, console opens; console power card mirrors master.
- Master OFF → console row hidden in Settings + console route shows the locked panel; every sandbox inert.
- **Spoof all** → master + all 18 flags ON, app relaunches, screens render pure fixture data (no banners — Demo Mode contract), Settings About shows `V… - Spoofed - Developer Mode`.
- Battery spoof → 7%/overheat fixture; slider in configurator updates the tile summary (debounced, no per-tick DataStore storm).
- Whisper spoof → send fails closed locally; zero Supabase traffic (verify via logcat + network).
- Cleaner spoof → staged fake scan (≈1.2 s phases), trashing/restore/undo/permanent-delete all RAM-only; disarm restores the real storage view.
- Notification vault spoof → RAM backlog + Shizuku faked authorized; hide/delete leave real rows untouched.
- Update/news spoof → 9.9.9-demo release cycles Checking→Downloading→ReadyToInstall in RAM; install answers honest Error; news backlog with pinned Demo welcome item, sync never runs.
- Tasks spoof → board + calendar on RAM fixtures (ids ≥1000), sessions tick in-process, no alarm ever scheduled/cancelled/re-armed; calendar AI pass refuses honestly.
- Search spoof → `.demo` domains that never resolve; DNS benchmark answers fake latencies without probes.
- Media downloader spoof → extract fakes the option ladder from the URL; download tap says no network/no file; history RAM-only.
- Crash lab → event recorded BEFORE the crash, appears in history after relaunch; version bump clears it.
- Flood → 6 tagged notifications; Cancel clears them.
- Playground: font scale / appearance / language segmented buttons highlight the actual current values after relaunch/apply.
- Bump `VERSION_CODE` → master + spoofs + crash history reset on next launch.
- Export/copy paths contain no vault secrets (redaction lists in `snapshotPrefs()` + table inspector).

## Screen overlays — none (removed 2026-10)

Demo mode ships **watermark-free by design**: a banner overlay defeats the point of a realistic demo, so the fake data itself is the tell on every tool screen.

- `RouteSpoofBanner.kt` still mounts `SpoofWatermarkOverlay`/`SandboxWatermarkHost`, but both now render their content only — no visual, no tap target. The names are kept for call-site stability; the inline `SpoofBanner()` composable remains in `SpoofChip.kt` as uncalled dead code (compiles fine, kept for API stability; delete at the next API break).
- `routeToDevTool()` is still the single route→tool map and still must cover every tool route — it now feeds the dashboard star + long-press dialog.
- `SpoofStateViewModel.isSandbox(tool)` unchanged for screens/VMs that need the flag.
- Audit rules unchanged: `grep -rn DEV-SPOOF app/src/main` must hit every tool VM; a tool screen may only show fixture-shaped content while sandboxed.

## Permission skips (sandboxed screens never prompt)

- `ui/screens/developer/components/SpoofSandboxState.kt` exposes `rememberSandboxActive(tool)` (Hilt `SpoofGateEntryPoint`, no VM surgery needed) so composable screens can drop permission gates while their sandbox is on.
- Sandbox-first screens implemented so far: Music (audio placeholder skipped — the fake library needs no real files), QR scanner (camera prompt + analyzer binding skipped, fixture payload card shown), Whisper (auth/onboarding never shown).
- Contract: the skip is screen-local; flipping the sandbox off restores the real permission flow untouched.

## Dashboard affordances (star + quick-fix)

- Sandboxed tools get a small red star on their dashboard card — top-right in grid layout, right-center in list layout (`SpoofSandboxStar`, fed by `SpoofQuickFixViewModel.activeSandboxes`; cards resolve their tool via the same `routeToDevTool` map that used to drive the watermark).
- Long-pressing a spoofed card replaces the default long-press (pin/detail sheet) with `SpoofQuickFixDialog`: the tool's data is announced fake and spoofed, with **Disable spoof** (writes the same pref the console toggle writes) and **Developer options** (navigates to `developer_mode`). Disabling drops the star instantly — the live flags flow repaints the card without a relaunch.

## Sandbox coverage (all enforced, all local-only)

| Tool | Hook | Fixture | Blocked |
|---|---|---|---|
| Battery | VM gate + receiver skip | `SpoofParams` level/temp/charging | remote spec fetch |
| Whisper | auto-auth + VM/chat fixture swap (`FakeWhisperSandbox.hubState()/threadFor()`) | fixture account, contacts, threads, canned auto-replies | login/accounts, Supabase, realtime, push, tombstone/prekey/heartbeat hydration, image sends |
| Steps | `gate.active(STEPS)` walking-clock ticker | slider steps + `SpoofSim.addedSteps` accrued cadence | real step sensor (Room untouched) |
| Sensors (compass/bubble/altimeter/speed/sound/light) | listener unregister + event guards | `SpoofSim` drive cycle, dB, lux, tilt, altitude+pressure streams | accel, baro, GPS, mic, light sensor |
| Music | `FakeMusicSandbox` + full transport intercept | 7-track `spoof://` library, ticker/visualizer, queue/favs in RAM | MediaStore scan, Room, MediaController binding, audio permission |
| Clipboard | `FakeClipboardSandbox` + entries swap | 30 typed clips (OTP/links/code…), capture gate=fake Shizuku | device clipboard read, capture service, Room, AI summarize |
| Timers | fixture countdown/counter + service detach (timer, stopwatch, pomodoro) | 3s loop timer, RAM stopwatch + laps (200 cap), RAM pomodoro ticking real phase rules (`nextModeAfterWork`), auto-start + skip counts | ToolService bind/start, AlarmManager, notifications |
| QR | scanner `rememberSandboxActive` bypass | fake WIFI: payload card, camera idle | camera permission prompt, analyzer binding |
| Device info | `refresh`/`executeSearch` gates | flagship-almost-full fixture | spec API, disk rescan |
| Network | trigger + monitor guards | `SpoofSim` ping/RSSI drift ×2 sites, FakeFiber-5G | scans, DNS, ping, traceroute, Shizuku system writes |
| Flashlight | 5 choke-point guards (apply/setTorch/pulse/mode loops) + service-sync suppression | fake SOS toggle + beam-off state | torch hardware (can never fire), ToolService state |
| Cleaner | `FakeCleanerSandbox` + `CleanerViewModel` source-flow swap, `gate.active(CLEANER)` combine | 7 categories w/ RAM trash, staged ≈1.2 s scan phases, duplicate-group keepers | disk scan, real deletes (RAM trash + restore/undo/empty/permanent), Shizuku, weekly auto-clear reminders |
| Notification vault | `FakeNotificationSandbox` source-flow swap | 11 notifications across 6 apps, 3-day spread | Shizuku read (faked authorized), listener shell, Room hide/delete (RAM only), app-details intent (fixture lookup) |
| Update + News | `FakeNewsSandbox` history/pagination/search swap; `UpdateViewModel` RAM lifecycle | 7-item backlog + pinned Demo welcome item; 9.9.9-demo lifecycle Checking→Available→Downloading→ReadyToInstall | GitHub fetch, downloads, install sessions (install answers honest Error), notify/snooze/dismiss repo writes |
| Tasks (todo) | `FakeTasksSandbox` active/completedToday/history source swap (ids ≥1000) | 6-task board (5 priorities, subtasks), completed-today counter | Room writes, reminder alarm schedule/cancel/global re-arm, ToolService session (in-process 1 Hz tick), add-to-calendar (refused silently) |
| Tasks (calendar) | event + dated-task source swaps (dated tasks ARE the todo fixture) | 5 events around "today" (DEADLINE/EXAM/sync/gym), recurring-rule shape | Room events, event/task reminder alarms, AI prompt pass (honest refusal — no model call, no image upload) |
| Search | `FakeSearchSandbox` results/history/visits/engine-health swap | query-woven tiles on non-resolving `.demo` domains, seeded history + browser trail, fake engine health | engine HTTP + suggestion API, DNS UDP probes (fake latencies), Room history (RAM); bookmarks/quick-links (user's own rows) protected |
| Media downloader | extract/download/demo-history branches | option ladder (1080/720/480) fabricated from the URL, RAM history rows | extraction API + catalog prober, WorkManager enqueues, converters, persisted history |
| Files group (converter/PDF/encrypter/bg-remover) | VM gates in all four VMs (`FILES`) | converter: RAM progress walk + type-keyed demo output path; encrypter: genuine local transforms with no history write; bg-remover: faked model download (RAM "installed"), honest inference refusal | converter service + file writes, ONNX model download + inference, /Downloads/Toolz writes, history/pin/delete/rename/AI paths, user's real prefs & rows protected |

## File map (console)

- `ui/screens/developer/DeveloperModeScreen.kt` — 4-tab console (the only screen file), incl. the locked panel reachable when master is OFF and the `spoofAllAndRelaunch` wiring.
- `ui/screens/developer/DeveloperModeViewModel.kt` — flows/actions; lazy telemetry (cold flow, only runs while watched) + heap history, crash history, logcat tail, bug report builder, `spoofAll()`.
- `ui/screens/developer/SpoofToolSpecs.kt` — per-tool icon, fixture summary, group, open route, editable field specs.
- `ui/screens/developer/components/` — `DevWidgets` (DevCard/DevCardTitle/DevBtn/DevToggleLine/DevDivider/DevKv, `DevSpacing` scale, shared `DebouncedSlider`), `TelemetryCard`, `SpoofTileGrid`, `SpoofConfiguratorSheet`, `LogcatViewer`, `CrashHistoryCard`, `CommandPalette`, `SpoofChip`, `RouteSpoofBanner`, `SpoofSandboxState` (`rememberSandboxActive`).
- Design language: M3 Expressive — 28dp cards / 24dp tiles with 16–20dp content padding, 24dp screen gutters, title-style uppercase section headers; all spacing comes from the `DevSpacing` scale so the tabs stay visually consistent.
- `data/developer/` — `DevToolId`, `SpoofParams`, `SpoofSim` (deterministic live streams), `SpoofGate` (state), `DeveloperSpoofController` (facade), `DevUiOverrider`, `ShakeDetector`, `FakeWhisperSandbox`, `FakeMusicSandbox`, `FakeClipboardSandbox`, `FakeCleanerSandbox`, `FakeNotificationSandbox`, `FakeNewsSandbox`, `FakeTasksSandbox` (todo + calendar fixtures), `FakeSearchSandbox`.
