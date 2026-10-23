# Accessibility Statement for Toolz

Toolz is designed to be accessible to anyone who wants a clean, unified utility app on Android.

## What's Built In

- **Jetpack Compose Semantics**: Buttons, switches, sliders, and navigation items use standard Compose components with `contentDescription` attributes so screen readers (like TalkBack) can identify what controls do.
- **Touch Targets**: Interactive controls aim for standard Android touch targets (minimum 48×48 dp) with sensible padding.
- **Font & Display Scaling**: Layouts use `sp` units for text so system font scale settings apply properly.
- **Dark & Light Surfaces**: Visual elements use Material 3 color tokens for readability against background surfaces.

## Known Limitations

- **Camera & Sensor-Heavy Tools**: Instruments like the Color Picker, Magnifier, and Bubble Level rely heavily on visual cues from the camera and screen. While their buttons and settings are screen-reader accessible, real-time visual alignment feedback is still primarily visual.
- **Raw PDFs & Complex Waveforms**: Complex or untagged PDF documents render visually as pages; text extraction is available to help pull out plain text.

## Feedback & Barrier Reports

If you run into an accessibility barrier in any tool (e.g., missing TalkBack labels, text clipping at high font sizes, or contrast issues), please let me know:

- **Email**: `frerox.toolz@gmail.com` (subject: `[ACCESSIBILITY] Toolz`)
- **GitHub**: Open an issue labeled `accessibility` on [GitHub Issues](https://github.com/freroxx/toolz/issues)
