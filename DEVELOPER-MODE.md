# Developer Mode

Internal console for diagnostics + controlled local-only spoofs. Ships in release, gated behind a visible Settings toggle with a warning popup.

## Entry

- Settings → DEVELOPER section (`SettingsScreen.kt`, index 11): master toggle + `Developer console` row → `developer_mode` route (`Screen.DeveloperMode`, wired in `MainActivity.kt` `ToolzNavHost`).
- Enabling shows a warning dialog (local-only fakes, crash/wipe risks, auto-disable on update). Disabling is instant, no dialog.
- Optional shake-to-open: master ON + toggle ON → accelerometer (2.6g, 2s cooldown) navigates to the console. Zero sensor cost otherwise.

## Controlled-spoof contract (the important part)

Spoofs are a **local sandbox, not mocks**:

1. `DeveloperSpoofController` is the only class that reads spoof flags. Master OFF forces every tool to real data.
2. Each tool declares a `SpoofNetworkPolicy` (`DevToolId.kt`): `BLOCK_ALL`, `BLOCK_REMOTE_SPECS`, `SENSORS_OFF`, `JOBS_PAUSED`, `LOCAL_ONLY`.
3. When sandboxed, the real client is **never constructed/called** — fixtures come from RAM (`SpoofParams`, `FakeWhisperSandbox`). Nothing is written to real Room tables.
4. Every injection is marked `// DEV-SPOOF:` — audit with `grep -rn DEV-SPOOF app/src/main`.
5. Every sandboxed screen shows a `SpoofChip`/`SpoofBanner` watermark.

### Whisper (exemplar)

`spoof_whisper` + master ON → `WhisperRepository.sendMessage` / `sendEncryptedImage` throw `SpoofSandboxException` **before** touching Supabase; `sendTypingStatus` early-returns. The dev console shows `FakeWhisperSandbox` fixtures (contacts + thread) with an `OFFLINE • NOTHING LEAVES THIS DEVICE` banner. No auth token is read, no realtime channel opens. Turning master OFF discards the thread and re-observes real chats.

### Battery (exemplar)

Sandboxed → `BatteryInfoViewModel` skips receiver registration + remote spec fetch, emits slider-driven fixtures (`SpoofParams.batteryLevel/batteryTempC/batteryCharging`) with a `SpoofBanner` on `BatteryInfoScreen`.

## Auto-reset on version change

`SettingsRepository.ensureDeveloperModeVersion()` (called from `MainActivity.onCreate`) disables master + all 18 spoofs + crash/force-offline flags when `VERSION_CODE` changes. Slider values (`SPOOF_PARAMS_JSON`) and the shake toggle survive — they are harmless prefs, not fakes. `resetOnboarding()` also clears dev flags via `resetDeveloperMode()`.

## Console contents

Status/build (copy/share/bug-template) • 18 spoof toggles + value sliders • Whisper sandbox preview • Crash lab (NPE/ANR/OOM/non-fatal/crash-on-next-launch) • Notification lab (`[SPOOF TEST]`-tagged, 60001+ ID band) • Data lab (logcat viewer, force-offline, mock delay) • Storage/DB inspector (counts only; vault/whisper/key tables redacted) • DataStore inspector (sensitive values redacted) • Navigation lab • UI playground (RTL, font scale, theme force, system dev options link — true layout-bounds need `WRITE_SECURE_SETTINGS`, hence the link) • Language forcer • Endpoint reference (build-time, info only) • Danger zone.

## Testing checklist

- Fresh install: toggle works, warning shows once per enable, console opens.
- Master OFF mid-spoof → real data, chips gone, fake jobs cancelled.
- Battery spoof → 7%/overheat fixture + banner; logcat shows `DevSpoof` block lines; no spec fetch.
- Whisper spoof → send fails closed locally; zero Supabase traffic (verify via logcat + network).
- Flood → 6 tagged notifications; Cancel clears them.
- Bump `VERSION_CODE` → master + spoofs reset on next launch.
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
| Music/Downloader/Cleaner/Vault/Clipboard/Tasks/Timers/Update/Search/Files | global banner + console sliders | params ready (`musicTrack`, `downloadProgress`, `timerSecs`) | VM fixtures queued next |

## Console (redesigned)

3 tabs (Spoofs / Labs / System), M3 Expressive `ListItem` + `Switch` rows with live fixture summaries, per-tool sandbox chips in the top bar, debounced sliders (write per drag tick — acceptable; a 300ms debounce is queued polish), `SelectionContainer` on the prefs dump, no hand-rolled rows left.

## Custom photo icon (App Skin → Use photo)

`CustomIconManager` center-crops + EXIF-rotates + downscales to a 512px PNG in app files (location stripped by re-encode). `ToolzMark` renders it for `IconCatalog.CUSTOM_ID`; widgets and pinned shortcuts use the photo. The launcher tile keeps the nearest built-in alias (manifest aliases are fixed at build time — stated in the UI), plus a `ShortcutManager` pinned shortcut carries the exact bitmap to the Home screen.
