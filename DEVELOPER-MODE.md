# Developer Mode

Internal console for diagnostics + controlled local-only spoofs. Ships in release, gated behind a visible Settings toggle with a warning popup. **No telemetry of any kind**: bug reports are built as local text and leave the device only via clipboard / the system share sheet, with the Toolz Discord (`https://discord.gg/aAswRUerwh` — same link as the Settings DISCORD button) as the handoff where the user pastes them.

## Entry

- Settings → DEVELOPER section (`SettingsScreen.kt`, index 11): master toggle + `Developer console` row → `developer_mode` route (`Screen.DeveloperMode`, wired in `MainActivity.kt` `ToolzNavHost`).
- Enabling shows a warning dialog (local-only fakes, crash/wipe risks, auto-disable on update) — in Settings AND in the console's own power card. Disabling is instant, no dialog.
- Optional shake-to-open: master ON + toggle ON → accelerometer (2.6g, 2s cooldown) navigates to the console. Zero sensor cost otherwise.
- The console itself also has a master switch (Home tab) — no Settings round-trip needed.

## Controlled-spoof contract (the important part)

Spoofs are a **local sandbox, not mocks**:

1. `DeveloperSpoofController` is the facade tool ViewModels ask; all state is delegated to `SpoofGate` (single hot cache of master + flags + params). Master OFF forces every tool to real data.
2. Each tool declares a `SpoofNetworkPolicy` (`DevToolId.kt`): `BLOCK_ALL`, `BLOCK_REMOTE_SPECS`, `SENSORS_OFF`, `JOBS_PAUSED`, `LOCAL_ONLY`.
3. When sandboxed, the real client is **never constructed/called** — fixtures come from RAM (`SpoofParams`, `FakeWhisperSandbox`). Nothing is written to real Room tables.
4. Every injection is marked `// DEV-SPOOF:` — audit with `grep -rn DEV-SPOOF app/src/main`.
5. Every sandboxed screen shows a `SpoofChip`/`SpoofBanner` watermark.

### Whisper (exemplar)

`spoof_whisper` + master ON → `WhisperRepository.sendMessage` / `sendEncryptedImage` throw `SpoofSandboxException` **before** touching Supabase; `sendTypingStatus` early-returns. The console shows `FakeWhisperSandbox` fixtures (contacts + thread, bubbles preview in the configurator) with an `OFFLINE • NOTHING LEAVES THIS DEVICE` banner. Turning master OFF discards the thread and re-observes real chats.

### Battery (exemplar)

Sandboxed → `BatteryInfoViewModel` skips receiver registration + remote spec fetch, emits slider-driven fixtures (`SpoofParams.batteryLevel/batteryTempC/batteryCharging`) with a `SpoofBanner` on `BatteryInfoScreen`.

## Auto-reset on version change

`SettingsRepository.ensureDeveloperModeVersion()` (called from `MainActivity.onCreate`) disables master + all 18 spoofs + crash/force-offline flags + crash history when `VERSION_CODE` changes. Slider values (`SPOOF_PARAMS_JSON`) and the shake toggle survive — they are harmless prefs, not fakes. `resetDeveloperMode()` behaves the same on demand.

## Console (Mission Control, 4 tabs)

Fixed top bar: back, live `SPOOF` count chip, **command palette** (`Tune` icon) and **reset**. Tabs: Home / Spoofs / Labs / System.

- **Home**: master power card (+ "LOCAL ONLY • NO TELEMETRY" plate), live telemetry (heap/native/threads/uptime + rolling 48s heap sparkline, `TelemetryCard`), quick actions (copy/share bug report, Discord, all-spoofs-off, refresh storage), active-sandbox list (tap → configurator).
- **Spoofs**: 2-column tile grid grouped by `DevToolGroup` (Hardware/System-like/Network/App machinery), real icon per tool (`DevToolId.icon`), policy chip, glow border + `SPOOF • LOCAL` chip when armed, own search field. Tap = **bottom-sheet configurator** (`SpoofConfiguratorSheet`) rendered from `SpoofFieldSpec`s in `SpoofToolSpecs.kt` (number sliders / switches / text fields, bounds mirroring `SpoofParams.parse`); all writes are debounced 300ms; "Open tool screen" jumps to the real route. Whisper gets the bubble-thread preview.
- **Labs**: crash lab (+ **crash history**, last 8 of 20 stored events — DataStore `dev_crash_history_json`, auto-cleared on version bump) • notification lab (`[SPOOF TEST]`-tagged, 60001+ ID band, flood/cancel) • **logcat v2** (`LogcatViewer`: level chips, color-coded lines, live tail 1s — tail auto-stops when the tab or app is no longer visible —, filter, copy/clear; local `logcat -d` reads only) • data lab (verbose, force-offline, mock delay) • deep-link launchpad (searchable, ~37 arg-free routes) • UI playground (segmented buttons: font scale, appearance, language; RTL, perf HUD, endpoints).
- **System**: status & build (+ copy/share report + shake toggle), storage/DB inspector (counts only; vault/whisper/key tables redacted), DataStore inspector (own search field; sensitive values redacted by `snapshotPrefs()`), **Danger zone with ARM-then-fire** (arm switch first, then all-spoofs-off / reset).

## Bug report & Discord handoff (no telemetry)

- `DeveloperModeViewModel.buildBugReport(info)` → app/device/spoofs/heap/uptime + local crash-lab history. Copyable anywhere in the console.
- Share = `DevUiOverrider.shareText` (system chooser — the user picks the destination; the app uploads nothing).
- `DevUiOverrider.openToolzDiscord` opens the same invite the Settings DISCORD button uses (`TOOLZ_DISCORD_URL` = `https://discord.gg/aAswRUerwh`). The user pastes the report there.

## Testing checklist

- Fresh install: Settings toggle works, warning shows once per enable, console opens; console power card mirrors master.
- Master OFF mid-spoof → real data, chips gone, fake jobs cancelled.
- Battery spoof → 7%/overheat fixture + banner; slider in configurator updates the tile summary (debounced, no per-tick DataStore storm).
- Whisper spoof → send fails closed locally; zero Supabase traffic (verify via logcat + network).
- Crash lab → event recorded BEFORE the crash, appears in history after relaunch; version bump clears it.
- Flood → 6 tagged notifications; Cancel clears them.
- Bump `VERSION_CODE` → master + spoofs + crash history reset on next launch.
- Export/copy paths contain no vault secrets (redaction lists in `snapshotPrefs()` + table inspector).

## Watermarks — global overlay (cannot be forgotten)

- `SpoofWatermarkOverlay` (mounted once around the `NavHost` in `MainActivity.kt`) maps every route to its `DevToolId` via `routeToDevTool()` and floats a `SpoofBanner` whenever that tool is sandboxed. Tap opens the console.
- `SpoofStateViewModel` lets any screen/VM ask `isSandbox(tool)` without touching DataStore.
- `SandboxWatermarkHost(tool)` is available for inline placement; Battery additionally keeps its inline banner.
- Audit rule: `grep -rn DEV-SPOOF app/src/main` must hit every tool VM + `routeToDevTool` must cover every tool route.

## Sandbox coverage (all enforced, all local-only)

| Tool | Hook | Fixture | Blocked |
|---|---|---|---|
| Battery | VM gate + receiver skip | `SpoofParams` level/temp/charging | remote spec fetch |
| Whisper | repo send/image/typing gates | `FakeWhisperSandbox` thread | Supabase, realtime, push |
| Steps | `uiState` map | slider steps + derived kcal/km | UI overridden (service untouched) |
| Sensors (compass/bubble/altimeter/speed/sound/light) | listener unregister + event guards | `compassDeg/soundDb/lightLux/speedKmh/altitudeM` | accel, baro, GPS, mic, light sensor |
| Device info | `refresh`/`executeSearch` gates | flagship-almost-full fixture | spec API, disk rescan |
| Network | trigger + monitor guards | `FakeFiber-5G`, slider ping, health 4, VPN on | scans, DNS, ping, traceroute, Shizuku system writes |
| QR | input prefill | `qrPayload` | — (generator is offline anyway) |
| Flashlight | `toggleFlashlight` gate + service-state guard | fake SOS toggle | torch hardware, foreground service |
| Music/Downloader/Cleaner/Vault/Clipboard/Tasks/Timers/Update/Search/Files | global banner + configurator sliders | params ready (`musicTrack`, `downloadProgress`, `timerSecs`) | VM fixtures queued next |

## File map (console)

- `ui/screens/developer/DeveloperModeScreen.kt` — 4-tab console (the only screen file).
- `ui/screens/developer/DeveloperModeViewModel.kt` — flows/actions; telemetry tick + heap history, crash history, logcat tail, bug report builder.
- `ui/screens/developer/SpoofToolSpecs.kt` — per-tool icon, fixture summary, group, open route, editable field specs.
- `ui/screens/developer/components/` — `TelemetryCard`, `SpoofTileGrid`, `SpoofConfiguratorSheet`, `LogcatViewer`, `CrashHistoryCard`, `CommandPalette`, `DevWidgets`, `SpoofChip`, `RouteSpoofBanner`.
- `data/developer/` — `DevToolId`, `SpoofParams`, `SpoofGate` (state), `DeveloperSpoofController` (facade), `DevUiOverrider`, `ShakeDetector`, `FakeWhisperSandbox`.
