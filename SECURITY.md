# Security Policy

Toolz handles private data locally (vault passwords, notes, notifications) and contains end-to-end encrypted messaging (Whisper). If you find a security hole, please report it responsibly so it can be fixed before anyone is harmed.

## Supported Versions

Only the current active release line receives updates:

| Version | Status |
|---|---|
| Latest Release (1.1.x) | Supported |
| Older builds | Unsupported — please update |

## Reporting a Security Issue

**Do not open a public GitHub issue or discuss vulnerabilities on Discord. (Especially for Whisper).**

Send an email to:
- **Email**: `frerox.toolz@gmail.com`
- **Subject**: `[SECURITY] Toolz vulnerability report`

Include:
- What the issue is and where it is in the code.
- Steps to reproduce or a minimal proof of concept.
- The device and Android version you tested on.

I will acknowledge your email as soon as I review it and work on a fix. Please give me reasonable time to investigate and push an update before making anything public.

## What's in Scope

- Flaws in SQLCipher vault encryption or key handling.
- Cryptographic bypasses or key leakage in Whisper (X3DH, Double Ratchet, AES-GCM).
- Arbitrary code execution or sandbox escapes via media/document parsers (FFmpeg, PDF, ONNX).
- Biometric unlock bypasses or credential leakage.
- Leaking sensitive user data to system logs (logcat) or unencrypted storage.

## What's NOT in Scope

- Issues requiring physical access to an unlocked or already-rooted device.
- Local denial of service caused by user actions (e.g. allocating gigabytes of local files).
- Bugs in third-party custom ROMs or unpatched OEM vendor kernels.
- Automated scanner reports without a working proof of concept.

## Safe Harbor

If you discover a vulnerability and report it in good faith according to this policy, I will not pursue legal action against you. Please do not access or tamper with genuine user data, and give me a fair chance to fix the issue before public disclosure.
