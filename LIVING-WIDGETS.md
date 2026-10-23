# Living Widgets

Living Widgets are looping GIF/video frames, single photos, and photo
slideshows on the Android home screen. Frames are prepared ahead of time and
shown with `RemoteViews`, so playback does not require a foreground service or
a continuously running app process.

The feature is local-only: sources are read from the system picker, converted
into private app storage, and never uploaded.

## What ships

- **Home-screen widget** — exported `LivingFramesWidgetReceiver`, configured
  through `LivingFramesConfigActivity`.
- **Tool screen** — the Living Widgets route in Toolz (`living_frames` remains
  a legacy route alias).
- **Background work** — WorkManager renders widgets after updates/resizes and
  an hourly watchdog repairs lost slideshow alarms and stale bindings.
- **Limits** — 900 frames per animation, 30 photos per slideshow, 640 MB per
  set, 1 GB total Living Widgets quota, and at least 256 MB free device
  storage before an import.

## Adding a widget

1. Open **Living Widgets** in Toolz and choose or import a set.
2. Use **Add to home screen**. The launcher creates the widget and sends the
   exact `EXTRA_APPWIDGET_ID` back to Toolz.
3. If the launcher offers configuration, choose the set for this copy.
   Each copy can show a different set; a binding is per widget ID, never
   "the newest set".
4. Long-press a placed widget and choose reconfigure to change its set.

Older launchers that omit the widget ID during the pin callback are handled
conservatively: Toolz binds only when exactly one unbound Living Widget
exists. Otherwise the widget shows a configuration placeholder.

## Importing

All imports are staged and published as follows:

1. Decode and encode one frame at a time into `<id>.tmp/`.
2. Enforce frame-count, per-set, quota, and free-space limits as bytes land.
3. Write a cover image and `fsync` every file.
4. Move the complete staging directory into place.
5. Persist metadata atomically; only then does the set appear in the library.

If an import is cancelled or fails, the staging directory is removed. A
metadata write failure rolls the set back, so the library never advertises
files that were not saved.

### GIF / animated images

- Animated GIF/WebP sources are sampled at a stable frame rate and stored as
  WebP frames.
- A single-frame GIF, or an animation whose frames do not actually move, is
  stored as a photo and labelled as such.
- Transparent animations keep their alpha; unfilled areas let the launcher
  wallpaper show through.

### Video

- Video imports decode a selected window of up to 30 seconds. The capture
  rate is chosen in the clip sheet (12/24/30/60 fps) and clamped to the
  import-quality preset and the 900-frame ceiling. A 30-second window
  therefore captures up to 30 fps; a 15-second window can capture 60 fps.
- Import quality presets store video frames at up to 1280 px (Compact),
  1920 px (High) or 2560 px (Ultra) on the longest edge, with matching WebP
  quality. That keeps a 1080p clip at 1920×1080 instead of shrinking it to a
  soft 1080×608.
- Codec-based extraction falls back to `MediaMetadataRetriever` when a decoder
  cannot handle the file.
- A window with no detectable motion is rejected with a suggestion to choose
  a different start point.

### Photos and slideshows

- Single images are downscaled for storage; EXIF rotation is applied.
- Slideshows accept up to 30 photos. Unreadable photos are skipped and
  reported, rather than failing the whole set.
- Slideshow order can be shuffled. Each round visits every photo once and
  avoids repeating the previous round's last photo at the seam.

## Storage and recovery

Files live under `filesDir/living_frames/`:

```
living_frames/
  .nomedia
  frames.json
  frames.json.bak
  <set-id>/
    frame_0000.webp
    ...
    cover.webp
  <set-id>.tmp/          # in-progress import
```

- `frames.json` is written to a temporary file, `fsync`ed, and renamed into
  place. A backup is kept and used automatically if the primary file cannot
  be decoded.
- Metadata uses additive-compatible JSON (`ignoreUnknownKeys = true`).
- On load, missing numbered frames are compacted; a set with no readable
  frames is left out of the metadata but its directory is not deleted.
- Legacy frame filenames created with locale-specific digits are still found.
- If the whole metadata file is unreadable, the library enters a **load
  failure** state. Frame directories and widget bindings are preserved, the
  UI offers **Try loading again**, and imports, pruning, and orphan cleanup
  are blocked until a good load succeeds.
- Orphan cleanup removes only expired staging/trash folders and directories
  that are known not to belong to the current library. It never runs while
  metadata is unreadable.
- Deleting a set writes metadata first, then removes files. If the metadata
  write fails, the files and the set remain.

The Storage panel shows current usage, the quota, free device space, and any
reclaimable orphan folders.

## Rendering and launcher limits

- The widget renders at the size the launcher reports, including orientation.
- Short loops can use the single-`RemoteViews` `ViewFlipper` path. Bitmap
  memory there is budgeted below 55% of the platform's RemoteViews ceiling
  (`6 × display width × display height`). If needed, Toolz reduces resolution,
  switches to 16-bit colour, samples frames, or eventually keeps one frame.
  The UI reports when a render was reduced.
- Longer or higher-rate clips stream one frame at a time through
  `LivingFramesRemoteViewsService` and an `AdapterViewFlipper`. Each frame is
  its own binder transaction, so the single-`RemoteViews` bitmap budget no
  longer forces downscaling. This is what lets a 900-frame clip play at the
  widget's native resolution.
- Playback engine choices: **Auto** streams only when the single-update path
  would reduce quality, **Standard** always uses the single-update path for
  launcher compatibility, and **High quality** always streams.
- Animated playback layouts are generated per supported rate
  (1/2/4/5/6/8/10/12/15/20/24/30/40/48/60 fps) because `flipInterval` is an
  XML-only attribute. The launcher animates the prepared frames; no
  foreground service runs per frame.
- Some launchers pause widget animations in battery saver, and a few do not
  animate `ViewFlipper` widgets or ignore collection widgets entirely. If a
  streamed widget stays blank, switch **Playback engine** to **Standard**;
  Toolz then sends every frame in one update (at reduced resolution for long
  clips). This is a launcher limitation, not a lost import.
- Resizes are debounced before re-rendering. A widget that cannot be rendered
  shows a short explanatory placeholder instead of a blank frame.

## Slideshow timing

- Slideshows use one-shot `RTC_WAKEUP` alarms, so an alarm remains meaningful
  after a reboot and can be re-armed idempotently.
- The next wall-clock fire time and the current position are persisted per
  widget.
- The watchdog re-arms alarms after a force-stop or OEM cleanup. Changing the
  interval starts a fresh interval; changing from a slideshow to an animation
  cancels the stale alarm.
- Tap actions can open the app, advance the slideshow, or do nothing.

## Failure messages

| Situation | What the user sees |
| --- | --- |
| Metadata unreadable | Load-failure card with **Try loading again**; files kept |
| Set was deleted | Placeholder asking to remove the widget |
| Library unreadable during refresh | "Couldn't read the Living Widget library" placeholder; binding kept |
| Launcher rejects bitmaps | Placeholder suggesting resize/re-add |
| Storage/quota/free-space limit | Import stops with the limit message; staged files are deleted |
| No motion in video window | Suggestion to choose another start point |
| Static GIF | Imported as a photo with an explanatory note |

## Verification

Pure planning, validation, shuffle, and slideshow-option coverage lives in:

```bash
./gradlew :app:testDebugUnitTest \
  --tests com.frerox.toolz.widget.LivingFrameEngineTest --offline
```

For a full build check:

```bash
./gradlew :app:compileDebugKotlin :app:assembleDebug --offline
```

## Release notes

### Living Widgets hardening

- Imports are streamed, staged, and only published after every frame is
  durable; cancelled imports no longer leave partial sets.
- Metadata writes are atomic with automatic backup recovery, and library
  reads fail closed without deleting files or widget bindings.
- Added exact per-widget set binding, a reconfigurable config activity, and a
  non-exported receiver for tap/slide/pin callbacks.
- Rendering now uses WorkManager, exact widget sizing, a RemoteViews memory
  budget, and reduced-quality fallbacks instead of relying on the broadcast
  window.
- Slideshow alarms survive reboot/force-stop and are repaired by the watchdog;
  RTC wall-clock scheduling replaces boot-relative timing.
- Added storage accounting, orphan cleanup, quota/free-space guards, and a
  Storage panel with load-failure recovery.
- Added launcher-animation limitations and per-widget render diagnostics.
