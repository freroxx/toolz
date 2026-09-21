# Privacy Policy for Toolz

Toolz is local-first. No analytics, no ads, no tracking SDKs.
No accounts except Whisper (which uses Supabase Auth with email-free sign-in below).
Toolz does not sell data.

Contact: frerox.toolz@gmail.com

Rule for everything below: **network happens only when you tap the feature**.
Idle app makes no network calls except Whisper realtime/push (only if you signed
into Whisper) and periodic update checks (only if enabled in Settings).

## What runs 100% on-device (locally)

- **Background Remover** — ONNX Runtime Mobile 1.29.0 inference on your photos.
  Models download once from GitHub Releases (see `BR-MODELS.md`), then inference,
  guided-filter matting, and PNG export are fully offline.
- **Scanner / barcodes** — ZXing (`com.google.zxing:core` 3.5.4) on-device.
  No ML Kit, no cloud vision.
- **Text recognition** — Tesseract4Android 4.8.0 (Latin script), on-device.
- **Media conversion / audio tags** — FFmpeg (`ffmpeg-kit-lts-16kb` 6.1.7) + Media3,
  on-device. YouTube audio path uses yt-dlp binary on-device after stream URLs resolve.
- **Calculator / converters** — Exp4j math engine, on-device.
- **PDF text layer** — `android.graphics.pdf.PdfRenderer` + pdfbox-android 2.0.27.0,
  on-device. No cloud PDF service.
- **Passwords, notes, notifications, clipboard, tasks, focus data** — Room database
  encrypted with SQLCipher (`net.zetetic:sqlcipher-android` 4.18.0, key in
  `EncryptedSharedPreferences` AES256-SIV/GCM via `util/security/KeyManager.kt`),
  settings in DataStore `settings`, AI keys in `EncryptedSharedPreferences`
  (`ai_settings`). `allowBackup=false`.
- **Whisper keys** — P-256 ECDH identity + P-256 signing key in AndroidKeyStore;
  X25519 identity seed + SPK/OPK privates + ratchet sessions wrapped by a Keystore
  AES-256-GCM key (`whisper_protocol_wrap_key`), never plaintext on disk.
  Room `whisper_messages` / `whisper_outbox` caches hold **ciphertext only**.
- **LAN scan / traceroute probes** — subnet scan, ARP/mDNS, and `ping -c 1 -W 2 -t TTL`
  traceroute run locally. Traceroute sends ICMP/TCP packets to the target you typed;
  no per-hop ASN web lookup is performed by the traceroute path.

## Network — only when you tap

### Overview / Public IP
- `https://api.ipify.org?format=json` → `https://ipapi.co/json/` →
  `https://ipinfo.io/json` → `https://ifconfig.me/ip` (4 fallbacks, 5 s timeouts,
  `NetworkViewModel.fetchPublicIp`). Trigger: Overview → Public IP → Refresh / auto on
  screen open. Sends: your IP is inherently visible to the endpoint; response (IP/ISP/ASN)
  is shown and logged locally.

### Speed test / latency
- Download mirrors: `https://speed.cloudflare.com/__down?bytes=25000000`,
  `https://proof.ovh.net/files/10Mb.dat`, `https://speed.hetzner.de/100MB.bin`
  (10 s capped, `SpeedTestEngine`). Upload: 8 MiB random bytes to
  `https://speed.cloudflare.com/__up`. Latency: TCP connect to `1.1.1.1:443` ×5 idle
  + sampled loaded. Trigger: Diagnostics → Speed Test → Run.

### DNS / Wi-Fi
- DNS Benchmark: DoH JSON to the provider's `dohUrl` you selected
  (`data/network/DnsProviderLibrary.kt` — Cloudflare, Google, Quad9, AdGuard,
  NextDNS, Mullvad, OpenDNS, CleanBrowsing, ControlD, Yandex + family variants) +
  TCP `:53` fallback + optional DoT `:853` handshake. Web-search DoH factory
  (`data/search/dns/DohClientFactory.kt`) uses the same public resolvers when you set
  custom DNS in Web Search. Trigger: DNS → Benchmark / applying private DNS via
  `Settings.ACTION_WIFI_SETTINGS` / `Settings.ACTION_PRIVATE_DNS_SETTINGS` deep-links.
  Shizuku is not required; all features degrade gracefully without it.

### Web Search
- Search engines you query (DuckDuckGo `html.duckduckgo.com`/`lite.duckduckgo.com`,
  Brave, Bing, Mojeek, Qwant, Yahoo, Presearch, Marginalia) + suggestion APIs
  `https://api.bing.com/osjson.aspx`, `https://api.qwant.com/v3/suggest`.
  Trigger: typing/searching in Web Search. Your query goes to the engine you chose.

### AI Assistant (optional, your keys)
- Default provider Groq; supported: ChatGPT, Groq, DeepSeek, OpenRouter, OpenCode Zen/Go,
  Gemini, Anthropic/Claude (`data/ai/`). Keys you paste are stored in
  `EncryptedSharedPreferences` and sent only to the provider you selected, only when
  you ask. AI catalog fetches from `https://toolz-app.vercel.app/` (same backend as
  Device Specs). No bundled keys, no background AI calls.

### Music / lyrics / catalog
- Lyrics: `https://lrclib.net/api/`. Music catalog/stream resolve: YouTube InnerTube
  (public web key from `local.properties` `INNER_TUBE_API_KEY`, falls back to
  NewPipeExtractor on-device), direct `https://www.youtube.com/` stream URLs,
  `https://toolz-app.vercel.app/` for catalog/device specs.
- Media Downloader: TikTok / Instagram / remote YouTube via your deployed
  `toolz-downloadz-api` (`DOWNLOADZ_API_URL`, default
  `https://toolz-downloadz-api.vercel.app`, `DOWNLOADZ_API_KEY` required) —
  configured in `local.properties`; blank = on-device YouTube engine only + setup hint
  for TikTok/IG. YouTube fallback chain is InnerTube → NewPipe → yt-dlp on-device.

### Background Remover model downloads
- `https://github.com/danielgatis/rembg/releases/download/v0.0.0/…` (u2netp, ISNet,
  BiRefNet) + `https://github.com/PeterL1n/RobustVideoMatting/releases/…` (RVM).
  Trigger: Model Hub → Download (Pro/Ultra ask for Wi-Fi consent on metered
  connections). Verified by size + SHA-256 (see `BR-MODELS.md`); quarantine on mismatch.

### Updates
- `https://api.github.com/repos/freroxx/toolz/releases/latest` (GitHub Release API) +
  fallback `https://freroxx.github.io/toolz/update_manifest.json`
  (`data/update/UpdateConstants.kt`). Trigger: Settings → update check / periodic
  `UpdateCheckWorker` (only if enabled). Downloads APK assets from GitHub Releases.

### Whisper messaging (only if you sign in)
- Backend: your Supabase project (`SUPABASE_URL`, `SUPABASE_ANON_KEY` from
  `local.properties`) — PostgREST (`profiles`, `messages`, `message_reactions`,
  `friendships`, `whisper_blocks`, `whisper_prekeys`, `whisper_fcm_tokens`,
  `whisper_typing_signals`, quotas), Realtime (broadcast + postgres changes as
  fallback), Storage bucket `whisper-avatars`, Edge Functions
  (`whisper-bundle-fetch`, `whisper-bypass-verify`, `whisper-delete-account`,
  `whisper-image-upload`, `whisper-image-delete`, `whisper-push-send`).
- Push: Firebase Cloud Messaging data-only wake pings (`WhisperPushService`) carrying
  only `{whisper_new_message, senderId, messageId}` / friend-request pings — **no
  message text ever transits FCM**. Tokens stored in `whisper_fcm_tokens`.
- Images: plaintext is AES-256-GCM sealed on-device, PNG-wrapped
  (`WhisperImageCipherTransport`, magic `WZ1` + CRC32), uploaded via
  `whisper-image-upload` (ImgBB host `i.ibb.co`/`ibb.co`) or Supabase Storage for
  avatars. Downloads allowlisted to those two hosts only (SSRF guard).
- Encryption (honest): long-term identity is P-256 ECDH (AndroidKeyStore) + P-256
  signing for prekeys; sessions use X3DH (X25519) → Double Ratchet (X25519 + HKDF +
  AES-256-GCM, `WhisperX3DH-v1` / `WhisperRatchetRoot` info strings, direction-bound
  AAD). **v3 ratchet messages provide per-message forward secrecy; the v2 multi-key
  envelope fallback (static ECDH) does not** — prior wording claiming FS for every
  message was wrong. Server and FCM see *who* talks to *whom* (usernames/IDs,
  timestamps, ciphertext blobs) but never plaintext. Auth: `username@u.whisper.local`
  + password (≥10 chars), or 64-char hex token → `SHA-256(token)@whisper.toolz.app`.
  Lose the token, lose the account. Delete Account calls `whisper-delete-account`
  (server wipes data before GoTrue deletion) then wipes local sessions/Room.

## Privileged access (only when you enable it)

- **Accessibility (OFF by default, runs only during a run you start):**
  `FocusFlowAccessibilityService`, `PurgeShotAccessibilityService`,
  `CleanerAccessibilityService` ("Toolz Cleaner Auto-clear" opens each selected app's
  system Settings page and taps Clear cache). Revocable in system Accessibility
  settings. No screen-content harvesting, no background runs, no exfiltration.
- **Shizuku (optional):** read-only privileged listing (`Android/obb` leftovers) and
  `pm trim-caches`. Deletes always go through the app trash/restore pipeline, never
  over Shizuku.
- **Notification Listener (Notification Vault):** captures notifications locally into
  encrypted Room for your own auditing. Never uploaded.
- **Usage Access (Focus Flow / Cleaner):** per-app usage/cache sizes, on-device only.
- **All-files access + media access (Cleaner / Converter / Music / PDF):** finding
  leftovers/junk, indexing local media you picked. Each requested with an in-app
  explanation; features degrade gracefully without optional grants.
- **Autofill + Biometric (Password Vault):** credential filling and unlock, local only.
- **Alarms / boot / notifications:** `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`,
  `RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS`, foreground-service types
  (camera/specialUse, health, microphone, mediaPlayback, dataSync) for timers,
  recorder, music, clipboard, conversion. No background data collection.
