# Contributing to Toolz

Thanks for considering contributing to Toolz! Toolz is built to be a fast, zero-bloat, privacy-first utility app for Android.

## Ground Rules

1. **Local-first & No Bloat**: Core tools must run offline. No analytics SDKs, telemetry frameworks, or hidden tracking.
2. **Dependencies**: Don't add new libraries to `build.gradle.kts` without opening an issue or discussing on Discord first. Every dependency is maintenance overhead and attack surface.
3. **Quality over Quantity**: Toolz already has 50+ tools. Fixes, UI polish, and performance boosts to existing tools are prioritized over adding random single-use utilities.

## Legal & Licensing (Important)

Toolz is licensed under the **GNU General Public License v3.0 (GPL-3.0)**.

By opening a pull request, you confirm that:
- You wrote the code yourself, or have verified it comes from an appropriately licensed open-source project compatible with GPL-3.0.
- You agree to license your contribution under GPL-3.0.
- You do not introduce stolen, leaked, or proprietary code.

## Dev Setup

- **IDE**: Android Studio (Ladybug or newer recommended)
- **JDK**: JDK 17
- **SDK**: compileSdk 37, targetSdk 36, minSdk 31 (Android 12+)
- **UI**: Jetpack Compose only (Material 3 Expressive). No XML layouts.
- **DI / Architecture**: Hilt for injection, MVI/MVVM pattern with `StateFlow`.

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest
```

## Pull Requests

1. Keep PRs focused on one bug fix or feature.
2. If changing UI, include a screenshot or screen recording in the PR description.
3. Ensure `./gradlew assembleDebug` and tests pass before opening your PR.

## Developer Mode & Fast Reports

Toolz has a built-in Developer Mode console and crash capture pipeline.
- When diagnosing issues on-device, use **Fast report** from the post-crash popup or from **Settings → Developer Mode → Fast report**.
- This generates a privacy-preserving markdown file (`toolz-fast-report-*.md`) containing app/device metadata, active sandboxes, recent warning/error logs, and stack traces.
- You can drop this generated `.md` report directly into GitHub Issues using our [Fast Report issue template](.github/ISSUE_TEMPLATE/fast_report.yml).

## Security

Found a security vulnerability? Do not open a public PR or issue. Read [SECURITY.md](SECURITY.md) and email **frerox.toolz@gmail.com**.
