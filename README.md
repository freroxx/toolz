# 🛠️ Toolz

<p align="center">
  <strong>Your device, fully orchestrated.</strong>
</p>

<p align="center">
  Toolz is a modern Android toolkit that brings productivity, media, PDF, sensor, privacy, and system utilities into one polished app. It is built for people who want one fast home for the tools they actually use, not a folder full of single-purpose apps.
</p>

<p align="center">
  <img alt="Latest release" src="https://img.shields.io/github/v/release/freroxx/toolz?display_name=tag" />
  <img alt="Android 12+" src="https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white" />
  <img alt="Kotlin 2.1.10" src="https://img.shields.io/badge/Kotlin-2.1.10-7F52FF?logo=kotlin&logoColor=white" />
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?logo=jetpackcompose&logoColor=white" />
</p>

---

## ✨ Why Toolz?

Toolz is designed to feel like a real daily driver, not a demo shelf. Instead of juggling dozens of single-purpose apps, you get:

- **One app for everything**: timers, notes, PDFs, media, utilities, sensors, and device tools
- **Smart dashboard** with quick access, pinned tools, recent tools, and a universal floating status pill
- **Strong local capabilities** for vaults, clipboard history, PDF work, conversion, measurement, and sensors
- **Optional AI** that actually helps, including assistant flows, smart matching, and document summaries
- **Native Android integrations** like Autofill, notification listening, accessibility-powered focus controls, quick settings tiles, and app widgets

---

## 📦 Complete Tool Catalog

Toolz includes **30+ precision instruments** organized across 8 categories:

### ⏱️ Time & Productivity
- **Timer** — Classic countdown with notifications and quick presets
- **Stopwatch** — Lap tracking and performance timing
- **Pomodoro** — 25/5/15 minute cycles with session tracking
- **World Clock** — Timezone management and international time display
- **Calendar** — Event management and scheduling
- **Todo** — Task management with priorities, due dates, and filters
- **Caffeinate** — Keep screen awake for focused work sessions

### 🤖 Smart Flow & AI
- **AI Assistant** — Configurable providers (ChatGPT, Claude, Groq, DeepSeek, OpenRouter, Gemini) with model selection
- **Smart Search** — Intent-matching dashboard search that routes you to the right tool
- **Web Search** — DuckDuckGo HTML results, bookmarks, quick links, DNS/ad-block controls
- **Quick Capture** — Notepad and Todo for notes, reminders, and lightweight planning

### 💾 Media & PDF
- **Music Player** — Local playback with lyrics, Media3 controls, and catalog/download flow
- **Voice Recorder** — Audio capture with playback and library management
- **File Converter** — Video, audio, image, PDF conversion with FFmpeg-backed processing
- **PDF Reader** — Open files directly, scan documents with OCR, extract text, AI summaries
- **File Cleaner** — Remove unused files and reclaim storage

### 🔦 Light & Optics
- **Flashlight** — Quick access to device LED
- **Screen Light** — Adjustable screen brightness without changing settings
- **Magnifier** — Digital magnification for reading small text
- **QR/Barcode Scanner** — Scan codes with instant results
- **QR Generator** — Create QR codes from text or URLs
- **Light Meter** — Measure ambient light levels

### 📐 Sensors & Navigation
- **Compass** — Digital compass with heading display
- **Bubble Level** — Spirit level for leveling objects
- **Speedometer** — Real-time speed tracking
- **Altimeter** — Altitude and elevation data
- **Step Counter** — Pedometer with activity tracking
- **Ruler** — On-screen measurement tool
- **Sound Meter** — Decibel level measurement

### 🧮 Math & Conversion
- **Calculator** — Standard and scientific modes with history
- **Unit Converter** — Convert between hundreds of units (distance, weight, temperature, etc.)
- **Tip Calculator** — Quick tip calculations and bill splitting
- **BMI Calculator** — Body metrics with healthy ranges, TDEE, and nutrition guidance
- **Equation Solver** — Solve mathematical equations
- **Color Picker** — Extract colors from images or create custom palettes

### 🔐 Security & Privacy
- **Password Vault** — Encrypted SQLCipher storage, biometric unlock, Autofill integration, password generation, CSV import, vault health checks
- **Password Generator** — Create strong passwords with customizable rules
- **Clipboard History** — Archive and retrieve past clipboard items
- **Notification Vault** — Capture selected notifications into searchable local archive
- **Smart Encrypter** — Encrypt/decrypt text and files locally

### 📊 Device & System
- **Device Info** — Comprehensive system information (processor, RAM, storage, battery health)
- **Battery Info** — Detailed battery statistics and health metrics
- **Periodic Table** — Interactive periodic table with element properties
- **Flip Coin** — Random coin flip decision maker
- **Wi-Fi Tweaks** — Advanced network settings and diagnostics
- **Network Power Suite** — DNS configuration, ad-blocking, network monitoring

### 🏠 Widgets & Quick Settings
- **Homescreen Widgets** — Flashlight, notes, steps, compass, flip coin, music player
- **Quick Settings Tiles** — Clipboard and Caffeinate fast access

---

## 🎯 Standout Features

### Focus Flow
Tracks app usage, scores productivity, supports daily app limits, and can use accessibility-powered hard locks for advanced focus modes.

### Password Vault
Stores credentials in an encrypted SQLCipher-backed database with biometric unlock, Autofill support, password generation, CSV import, and vault health checks.

### Notification Vault
Captures selected notifications into a searchable local archive with filters and app-level controls.

### PDF Reader
Opens PDF files directly from Android share/open flows, scans documents with OCR (supports Latin, Chinese, Japanese, Korean, Devanagari), extracts text, and supports AI-assisted summaries.

### File Converter
Handles video, audio, image, and PDF conversion workflows with FFmpeg-backed processing and PDF-to-image support.

### Music Player
Supports local playback, synced lyrics, Media3 controls, karaoke mode, visualizer, sleep timer, and a catalog/download flow for expanding your library.

### Smart Dashboard
Responsive grid layout with tool pinning, recent tools access, category filtering, and unified search powered by AI intent matching.

### Backup & Restore
Export and import your data (notes, passwords, settings, AI history, etc.) with manifest versioning and integrity checks.

---

## 📥 Install Toolz

Toolz is distributed through **GitHub Releases**:

- **Releases page:** [github.com/freroxx/toolz/releases](https://github.com/freroxx/toolz/releases)
- **Current version:** `1.0.9`

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
- **Android SDK** — compileSdk 36, targetSdk 36
- **JDK 17**
- **Device/Emulator** — Android 12+ (minSdk 31)

### Optional: AI Provider Keys

Toolz runs core utilities without AI keys, but AI features work best with your own provider keys.

Create a `.env` file in the project root if you want to bake default keys into your build:

```env
GEMINI_DEFAULT=
CHATGPT_DEFAULT=
GROQ_DEFAULT=
OPENROUTER_DEFAULT=
CLAUDE_DEFAULT=
DEEPSEEK_DEFAULT=
```

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

- **Kotlin 2.1.10** — Modern Android development
- **Jetpack Compose** — Declarative UI framework with Material 3 Expressive design
- **Hilt** — Dependency injection
- **Room + SQLCipher** — Encrypted local storage
- **WorkManager** — Scheduled background tasks
- **Media3** — Audio playback and session handling
- **ML Kit** — Barcode scanning and text recognition
- **FFmpegKit** — Media conversion
- **Retrofit + OkHttp + Moshi** — Networking and JSON serialization
- **AndroidX PDF Viewer** — In-app PDF rendering
- **Coil 3** — Image loading and caching

---

## 📱 Permissions & Privacy

Toolz uses different Android permissions depending on which tools you enable. The app asks for powerful access because some of its features are deeply integrated with the system, not because every permission is always needed.

| Feature Area | Permissions | Why It's Needed |
|---|---|---|
| **Focus Flow** | Usage Access, Accessibility, Overlay | App usage analysis, app limits, advanced focus controls |
| **Notification Vault** | Notification Listener | Capture and organize notifications |
| **Password Vault** | Biometric Auth, Autofill | Secure unlock and credential filling |
| **Search, AI, Updates** | Internet/Network | DuckDuckGo search, AI providers, update checks, music catalog |
| **Camera Tools** | Camera, Flashlight | Scanning, magnifier, light detection |
| **Audio Tools** | Microphone, Media | Voice recording, playback, music library |
| **Motion Tools** | Activity Recognition, Location, Sensors | Step counting, compass, altitude, speed |
| **File Tools** | Media, Document | PDF opening, music indexing, conversion, cleanup |

### Important Notes

- **Toolz is not fully offline.** Many tools work entirely on-device, but connected features (AI, web search, catalog downloads, updates) require network access by default, however it does feature an offline mode that turns the app fully offline
- **AI is optional.** You can use all core utilities without configuring personal provider keys
- **No tracking, no ads.** Toolz is built for privacy-conscious users who want full control over their data

---

## ✨ Quick Start Tips

1. **Pin Your Favorites** — Long-press tools on the dashboard to keep your most-used items front and center
2. **Use Smart Search** — The dashboard search is powered by AI intent matching; describe what you want in natural language
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

Toolz is open source. See the repository for license details.

---

**Made with ❤️ by frerox**
