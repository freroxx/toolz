# 🛠️ Toolz

<p align="center">
  <strong>Your device, fully orchestrated.</strong>
</p>

<p align="center">
  Toolz is a modern Android toolkit that brings productivity, media, PDF, sensor, privacy, and system utilities into one polished app. It is built for people who want one fast home for the tools they actually use, not a folder full of single-purpose apps.
  Check out Toolz Website here : https://toolz-app.vercel.app
</p>

<p align="center">
  <img alt="Latest release" src="https://img.shields.io/github/v/release/freroxx/toolz?display_name=tag" />
  <img alt="Android 12+" src="https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white" />
  <img alt="Kotlin 2.4.0" src="https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?logo=kotlin&logoColor=white" />
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?logo=jetpackcompose&logoColor=white" />
</p>

---

## ✨ Why Toolz?

Toolz is designed to be a real daily driver, not a demo shelf. Instead of juggling dozens of bloated closed-source single-purpose apps, you get:

- **Zero Bloat, One Home** — Ditch the folder of 40+ single-purpose apps. Toolz provides a unified library of polished utilities in one optimized APK.
- **Privacy as a Standard** — Core tools work 100% offline. Sensitive data like passwords, notes, and notifications are stored locally using industrial-grade SQLCipher encryption.
- **Deep System Integration** — Native Quick Settings tiles, Homescreen Widgets, and Autofill support ensure the toolkit feels like a native extension of your Android OS, an ecosystem built for power users.
- **Polished tools** — Every tool is built for accuracy, utilizing FFmpeg for media, ML Kit for vision, and GPS-calibrated sensors for navigation... All wrapped inside a Material 3 expressive customisable experience.
- **Unified Dashboard** — A smart dashboard with pinned favorites and a floating "status pill" keeps your active timers, music, or recordings accessible from anywhere.

---

## 📦 Complete Tool Catalog

Toolz includes **48+ precision instruments** organized across 9 categories:

### ⏱️ Time & Productivity
- **Timer** — Precise countdown engine with background persistence and physics-based alarms
- **Stopwatch** — High-resolution performance timing with lap tracking and millisecond accuracy
- **Pomodoro** — Deep focus cycles (25/5/15) with session tracking and non-intrusive notifications
- **World Clock** — Global time synchronization and timezone management for international teams
- **Calendar** — Native scheduling and event management with Material 3 expressive design
- **Todo List** — Priority-driven task management with physics-based interactions and due date filters
- **Caffeinate** — System-level screen-awake utility with quick settings tile integration
- **Focus Flow** — Productivity analyzer that tracks app usage and provides actionable flow scores

### 🚀 AI & Utilities
- **AI Assistant** — Optional conversational agent for document summaries and contextual guidance
- **Smart Search** — Intent-matching dashboard search that routes queries directly to the correct tool
- **Web Search** — Privacy-focused browser with ad-blocking, custom DNS controls, and tab management
- **Notepad** — Multimedia capture tool for notes, reminders, and audio-linked memos

### 💾 Media & PDF
- **Music Player** — Local audio hub with lyrics, playlist management, and Media3 background playback
- **Media Downloader** — Save YouTube, TikTok, and Instagram Reels with quality picker, audio-only mode, and background downloads
- **Voice Recorder** — Studio-quality audio capture with pause/resume and searchable library
- **File Converter** — FFmpeg-powered media transformation for video, audio, and image formats
- **PDF Reader** — Native document viewer with text extraction and document summary support
- **Background Remover** — Offline AI-powered utility to automatically remove backgrounds from images (runs locally)
- **File Cleaner** — Storage management utility to reclaim space and remove redundant cache
- **Sound Meter** — Real-time decibel analysis for monitoring environmental noise levels

### 🔦 Light & Optics
- **Flashlight** — Dynamic LED control with steady, strobe, SOS, and disco modes
- **Screen Light** — High-brightness display utility with adjustable color temperature for soft lighting
- **Magnifier** — Digital zoom tool for reading micro-text and inspecting hardware components
- **Scanner** — High-speed recognition engine for QR codes and industrial barcodes
- **QR Generator** — Create shareable codes from text, credentials, or network configurations
- **Light Meter** — Photometric sensor utility for measuring ambient Lux levels

### 📐 Sensors & Navigation
- **Compass** — Magnetic orientation tool with bearing tracking and heading visualization
- **Bubble Level** — Dual-axis spirit level for high-precision alignment and leveling
- **Speedometer** — GPS-based velocity tracking with peak speed and distance metrics
- **Altimeter** — Elevation tracking utilizing barometric pressure and GPS data
- **Step Counter** — Fitness pedometer with daily goals, trends, and distance analysis
- **Ruler** — Calibrated on-screen measurement for physical objects
- **Color Picker** — Visual color extraction tool that identifies HEX/RGB values via the camera

### 🧮 Math & Conversion
- **Calculator** — Scientific math engine with expression history and modular layouts
- **Unit Converter** — Universal conversion for hundreds of units across distance, weight, and energy
- **Tip Calculator** — Rapid bill splitting and tip calculation for group dining
- **BMI Calculator** — Health metric analyzer with TDEE and nutrition guidance
- **Equation Solver** — Advanced solver for linear, quadratic, and complex mathematical equations

### 🔐 Security & Privacy
- **Password Vault** — SQLCipher-encrypted storage for credentials with biometric unlock and autofill integration
- **Password Generator** — High-entropy random key generator with customizable complexity rules
- **Clipboard History** — Local archive for managing and retrieving past clipboard segments
- **Notification Vault** — Searchable local log of system notifications for privacy auditing
- **Smart Encrypter** — AES-256 text and file encryption utility for secure data transfer
- **Purge Shot** — Auto-expiring screenshot manager with 30 sec to 1 month presets, system-level detection via Accessibility/Shizuku/observer, triple-redundant queue, and one-tap undo

### 📊 Device & System
- **Device Info** — Deep hardware diagnostics and system property inspection
- **Battery Info** — Comprehensive statistics on health, cycles, and charging temperature
- **Periodic Table** — Interactive scientific reference for element properties and data
- **Flip Coin** — Physics-simulated decision maker for random selection
- **Network Tweaks** — Wi-Fi diagnostics and advanced connectivity optimization tools
- **Network Power Suite** — System-level DNS configuration, ad-blocking, and network monitoring

### 💬 Communication & Messaging
- **Whisper** — End-to-end encrypted, friends-only messenger with email-free sign-in, offline outbox delivery, and realtime chat (beta)

### 🏠 Widgets & Quick Settings (Experimental)
- **Homescreen Widgets** — Flashlight, notes, steps, compass, flip coin, music player
- **Quick Settings Tiles** — Clipboard and Caffeinate fast access

---

## 💬 Whisper — Encrypted Messaging (Beta)

Whisper is Toolz's built-in messenger: an end-to-end encrypted, friends-only chat layer with its own Supabase backend, so Whisper accounts and messages live apart from the rest of the app. Full architecture: [`WHISPER.md`](WHISPER.md).

The short version: you sign in with a username + password or a random 64-character token — no email for you to type (the app uses virtual emails under the hood for Supabase Auth). Add people as friends, and only then can you message them. Every message is encrypted on your phone before it's sent — the server stores ciphertext, not readable text.

### Key features

- **End-to-end encryption** — P-256 identity + signing keys in AndroidKeyStore, X25519 sessions via X3DH → Double Ratchet, all sealed with AES-256-GCM (direction-bound AAD). New chats try the ratchet first (per-message forward secrecy); if the handshake can't complete the message still delivers via the self-healing multi-key envelope fallback (static ECDH, no FS) — a message is never blocked by session problems. Keys rotate every 30 days; verify fingerprints in person via QR
- **No-email sign-in** — two ways in: username + password (≥10 chars, auto-saved to the Toolz Vault), or a random 64-char hex token (`SHA-256(token)@whisper.toolz.app`, hashed server-side). Save your token — lose it, lose the account
- **Friend-gated chats** — no messaging until the friend request is accepted; blocks are enforced client + DB-level, both sides know when they're blocked
- **Realtime delivery** — messages, reactions, typing (8 s fresh), and presence arrive over broadcast channels, with postgres-change polling as the reliable fallback; FCM data-only wake pings (senderId/messageId, no content) cover killed apps
- **Read receipts & presence** — pending/sent/read states, unread badges, online (≤2 min) / recent / last-seen
- **Offline-first messaging** — messages sent offline are sealed, stored ciphertext-only in a Room outbox (FIFO ≤100), and auto-flushed by WorkManager when online; chat history stays readable from a ciphertext-only local cache
- **Replies & reactions** — quote-to-reply with snippets and per-message emoji reactions (optimistic + snapshot reconcile)
- **Images** — sealed on-device, PNG-wrapped (`WZ1`+CRC32, ≤5 MiB), hosted via edge function (ImgBB) or Supabase Storage for avatars; allowlisted downloads, expiry labels
- **Delete tools** — delete for everyone (leaves a "Message deleted" tombstone + remote image delete), delete for you (device-only), and clear-chat by time range (24h / 7d / 30d / all / custom) with a 30-second undo
- **Hide chats** — remove a conversation from the chats tab; it comes back automatically when a new message arrives
- **Mute & block** — silence a conversation or block a user entirely
- **In-app notifications** — grouped per conversation, deduplicated across realtime+FCM, suppressed while you're inside that chat or when muted/hidden

Whisper is early-access software: expect rough edges. It is centralized — the server can see *who* talks to *whom* (usernames/IDs, timestamps, ciphertext blobs) but never plaintext. See [`WHISPER.md`](WHISPER.md) + [`PRIVACY.md`](PRIVACY.md) for the full info.

---

## 📥 Install Toolz

Toolz is distributed through **GitHub Releases**:

- **Releases page:** [github.com/freroxx/toolz/releases](https://github.com/freroxx/toolz/releases)
- **Current version:** `1.1.5`

### Choose your architecture:

- **arm64-v8a** — Most modern Android phones (recommended)
- **armeabi-v7a** — Older 32-bit ARM devices
- **x86_64** — Many emulators and x86_64 environments
- **x86** — Older x86 emulator/device setups

### In-App Updates

Toolz includes a built-in updater that checks GitHub releases and update manifests for compatible builds, so you can update directly from the app.

---

## 🔨 Build From Source

### Requirements

- **Android Studio** (latest)
- **Android SDK** — compileSdk 37, targetSdk 36
- **JDK 17**
- **Device/Emulator** — Android 12+ (minSdk 31)

### Build Steps

**macOS/Linux:**
```bash
./gradlew assembleDebug
```

**Windows:**
```powershell
.\gradlew.bat assembleDebug
```

Import the project into Android Studio, let Gradle sync, and the debug APK will be generated through the standard Android build pipeline.

---

## 🛠️ Tech Stack

Verified from `gradle/libs.versions.toml` + `app/build.gradle.kts`
(compileSdk 37, targetSdk 36, minSdk 31, JDK 17, AGP 9.4.0).

- **Language / Build** — Kotlin 2.4.0, KSP 2.3.10, Hilt 2.60.1 (DI + WorkManager),
  kotlinx-coroutines 1.11.0, kotlinx-serialization 1.11.0, desugar JDK libs
- **UI** — Jetpack Compose BOM 2026.08.00, Material 3 Expressive
  (`1.5.0-alpha27`) + adaptive/navigation-suite, Navigation Compose 2.9.8,
  Coil 3.5.0 (compose/okhttp/video), Glance 1.3.0-alpha02 (widgets),
  graphics-shapes, Accompanist permissions, Material 1.14.0
- **Data (encrypted local-first)** — Room 2.8.4 (schemas exported in
  `app/schemas/`), SQLCipher 4.18.0 + sqlite 2.7.0, DataStore preferences,
  Security Crypto 1.1.0 (`EncryptedSharedPreferences` AES256-SIV/GCM),
  WorkManager 2.11.2
- **Network** — Retrofit 3.0.0 + Moshi 1.15.2 (codegen) + kotlinx-serialization
  converter, OkHttp 5.5.0 + logging + DnsOverHttps, Ktor 3.5.2 (Supabase edge calls)
- **Media / Camera** — Media3 1.11.0 (exoplayer/ui/session/DASH), CameraX
  1.7.0-alpha03 (camera2/lifecycle/view/core), ExifInterface 1.4.2, Palette,
  FFmpeg `ffmpeg-kit-lts-16kb` 6.1.7 + `youtubedl-android` 0.18.1 (yt-dlp binary),
  NewPipeExtractor 0.26.5 + InnerTube (public web key, fallback chain intact)
- **Vision / Docs (on-device, no ML Kit)** — ZXing core 3.5.4 (QR/barcode —
  replaces ML Kit barcode), Tesseract4Android 4.8.0 (OCR Latin — replaces ML Kit
  text), `android.graphics.pdf.PdfRenderer` + pdfbox-android 2.0.27.0 (text/TOC —
  `androidx.pdf` viewer removed), AndroidSVG 1.4, CommonMark 0.30.0, Jsoup 1.23.1,
  Exp4j 0.4.8, AndroidX WebKit 1.14.0
- **Background Remover** — ONNX Runtime Mobile 1.29.0 (`onnxruntime-android`, MIT),
  per-ABI `.so` via splits (~+32 MB arm64-v8a). Models download on demand,
  SHA-256 pinned (see `BR-MODELS.md`)
- **Backend (Whisper only)** — Supabase BOM 3.7.0
  (auth-kt/postgrest-kt/realtime-kt/storage-kt), Firebase BOM 34.18.0
  (`firebase-messaging` data-only wake pings, no content), google-services 4.5.0
- **System integration** — Shizuku 13.1.5 (optional privileged ops), Biometric
  1.4.0-alpha07 + Autofill, google GenerativeAI 0.9.0 (optional assistant providers)

> Corrections vs older README: ML Kit is gone (ZXing + Tesseract), FFmpeg is the
> `ffmpeg-kit-lts-16kb` fork (not `FFmpegKit` upstream), PDF is PdfRenderer+pdfbox
> (not AndroidX PDF Viewer), and Supabase/Firebase/Shizuku/Media3/CameraX/Glance
> are first-class — see `PRIVACY.md` for what actually hits the network.

---

## 📱 Permissions & Privacy

Source of truth: `app/src/main/AndroidManifest.xml`. Toolz declares powerful access
because some tools are deeply integrated with the system — each is requested with an
in-app explanation and features degrade gracefully without optional grants.
See `PRIVACY.md` for what actually leaves the device.

| Feature Area | Manifest permissions | Why it's needed |
|---|---|---|
| **Core / Network** | `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `NEARBY_WIFI_DEVICES` (neverForLocation), `WAKE_LOCK` | Web Search, AI providers, catalog, updates, model downloads, Wi-Fi diagnostics |
| **Camera / Light** | `CAMERA`, `FLASHLIGHT` | Scanner, magnifier, color picker, flashlight + QS tile; foregroundServiceType `camera` for flashlight service |
| **Audio / Music** | `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS` | Voice recorder (FGS `microphone`), sound meter; `mediaPlayback` FGS for Media3 player |
| **Location / Motion** | `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACTIVITY_RECOGNITION`, `HIGH_SAMPLING_RATE_SENSORS` | Speedometer, altimeter, compass, step counter (FGS `health`), bubble/light meters |
| **Files / Media** | `READ_MEDIA_AUDIO/IMAGES/VIDEO`, `READ/WRITE_EXTERNAL_STORAGE` (maxSdk 32), `MANAGE_EXTERNAL_STORAGE` | Music indexing, PDF open, converter, cleaner leftovers/junk; FileProvider `com.frerox.toolz.fileprovider` for share |
| **Productivity / Alarms** | `SCHEDULE_EXACT_ALARM` (maxSdk 32), `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE`, `USE_FULL_SCREEN_INTENT`, `SYSTEM_ALERT_WINDOW` | Timer/Pomodoro/calendar/task alarms + boot restore, overlay controls |
| **Background work** | `FOREGROUND_SERVICE` + `HEALTH/MICROPHONE/SPECIAL_USE/MEDIA_PLAYBACK/DATA_SYNC/CAMERA` | Tool/step/voice/music/clipboard/caffeinate/conversion/purgeshot services; WorkManager `SystemForegroundService` (dataSync) |
| **Focus Flow** | `PACKAGE_USAGE_STATS`, `BIND_ACCESSIBILITY_SERVICE` (`FocusFlowAccessibilityService`), `SYSTEM_ALERT_WINDOW` | App-usage analysis, limits, focus controls |
| **Notification Vault** | `BIND_NOTIFICATION_LISTENER_SERVICE` (`NotificationVaultService`) | Local notification log/auditing, stays on-device |
| **Cleaner Auto-clear** | `BIND_ACCESSIBILITY_SERVICE` (`CleanerAccessibilityService`), `QUERY_ALL_PACKAGES`, `KILL_BACKGROUND_PROCESSES`, Shizuku `rikka.shizuku.permission.API_V23` | Opens app Settings → taps Clear cache per run; read-only `Android/obb` listing + `pm trim-caches`; deletes via trash pipeline only |
| **Purge Shot** | `BIND_ACCESSIBILITY_SERVICE` (`PurgeShotAccessibilityService`), screenshot observer JobService | Auto-expiring screenshots, triple-redundant queue, undo |
| **Password Vault** | `USE_BIOMETRIC`, Autofill service (`BIND_AUTOFILL_SERVICE`, `ToolzAutofillService`) | Biometric unlock + credential filling, SQLCipher-backed |
| **Updates / Sharing** | `REQUEST_INSTALL_PACKAGES`, `QUERY_ALL_PACKAGES` | In-app GitHub Release updates; browser/share intents (`http/https`, `SEND text/plain`, audio/PDF/image/tzbk/enc handlers, `whisper-auth://login`) |
| **Whisper push** | FCM service (`WhisperPushService`, `MESSAGING_EVENT`) | Data-only wake pings (senderId/messageId, no content) |
| **System extras** | `WRITE_SETTINGS` (Caffeinate screen timeout, manual grant), `BLUETOOTH`/`BLUETOOTH_CONNECT` | Keep-screen-on, device/sensor accessories |

### Important Notes

- **Local-first, not offline-only.** Background remover inference, OCR, QR, FFmpeg,
  calculator, PDF render, vault/notifications/clipboard storage are fully on-device;
  connected features (Assistant with your keys, web search, catalog, model downloads,
  updates, Whisper) require network — plus an offline mode that disables them.
- **Assistant is optional.** All core utilities work without provider keys.
- **No tracking, no ads.** No analytics SDKs. See `PRIVACY.md` for endpoint-by-endpoint truth.

---

## ✨ Quick Start Tips

1. **Pin Your Favorites** — Long-press tools on the dashboard to keep your most-used items front and center
2. **Use Smart Search** — The dashboard search is optimized for intent matching; describe what you want in plain language
3. **Enable Widgets** — Add homescreen widgets for quick access to flashlight, notes, music, and more
4. **Set Up Your Vault** — Configure your password vault with biometric unlock for seamless Autofill
5. **Explore Focus Flow** — Track your app usage and set meaningful productivity goals
6. **Backup Your Data** — Regularly export your data to protect notes, passwords, and settings

---

## 🐛 Support & Contribution

- **Repository:** [github.com/freroxx/toolz](https://github.com/freroxx/toolz)
- **Releases:** [github.com/freroxx/toolz/releases](https://github.com/freroxx/toolz/releases)
- **Discord Community:** [discord.gg/aAswRUerwh](https://discord.gg/aAswRUerwh)

### Report Issues or Contribute

Have a bug report, feature suggestion, or want to contribute? Open an issue or pull request in this repository, or join the Discord server for fast fixes and suggestions.

---

## 📄 License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.

---

## Early access 

Want to test bleeding-edge features? Check out our [Early Access Hub]([https://github.com/freroxx/toolz](https://github.com/freroxx/toolz/discussions/7))!


**Made with ❤️ by freroxx**
