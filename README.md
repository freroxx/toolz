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

Toolz includes **45+ precision instruments** organized across 8 categories:

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
- **Voice Recorder** — Studio-quality audio capture with pause/resume and searchable library
- **File Converter** — FFmpeg-powered media transformation for video, audio, and image formats
- **PDF Reader** — Native document viewer with text extraction and document summary support
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

### 📊 Device & System
- **Device Info** — Deep hardware diagnostics and system property inspection
- **Battery Info** — Comprehensive statistics on health, cycles, and charging temperature
- **Periodic Table** — Interactive scientific reference for element properties and data
- **Flip Coin** — Physics-simulated decision maker for random selection
- **Network Tweaks** — Wi-Fi diagnostics and advanced connectivity optimization tools
- **Network Power Suite** — System-level DNS configuration, ad-blocking, and network monitoring

### 🏠 Widgets & Quick Settings (Experimental)
- **Homescreen Widgets** — Flashlight, notes, steps, compass, flip coin, music player
- **Quick Settings Tiles** — Clipboard and Caffeinate fast access

---

## 📥 Install Toolz

Toolz is distributed through **GitHub Releases**:

- **Releases page:** [github.com/freroxx/toolz/releases](https://github.com/freroxx/toolz/releases)
- **Current version:** `1.1.0`

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

- **Kotlin 2.4.0** — Modern Android development
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
| **Search, Assistant, Updates** | Internet/Network | DuckDuckGo search, Assistant providers, update checks, music catalog |
| **Camera Tools** | Camera, Flashlight | Scanning, magnifier, light detection |
| **Audio Tools** | Microphone, Media | Voice recording, playback, music library |
| **Motion Tools** | Activity Recognition, Location, Sensors | Step counting, compass, altitude, speed |
| **File Tools** | Media, Document | PDF opening, music indexing, conversion, cleanup |

### Important Notes

- **Toolz is not fully offline.** Many tools work entirely on-device, but connected features (Assistant, web search, catalog downloads, updates) require network access by default, however it does feature an offline mode that turns the app fully offline
- **Assistant features are optional.** You can use all core utilities without configuring personal provider keys
- **No tracking, no ads.** Toolz is built for privacy-conscious users who want full control over their data

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


**Made with ❤️ by frerox**
