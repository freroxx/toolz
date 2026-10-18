# Whisper Architecture

Friends-only, end-to-end encrypted messenger inside Toolz. Own Supabase backend,
separate from the rest of the app. Section names point at the source files that
implement them; the working tree is truth (this doc is not verified by CI, so
read claims against code before trusting them).

Contents:

1. Overview and honesty notes
2. Cryptography
3. Wire formats
4. Images and avatars
5. Backend
6. Client repository
7. Auth
8. UI and view models
9. Push, workers, app wiring
10. Tests
11. What the server sees
12. File inventory
13. Groups (Phase 2, ships in 1.1.7)
14. Daily Ups (stories, ships in the same 1.1.7)
15. Known limitations and calibration notes (external review, 2026-10)

## 1. Overview and honesty notes

Whisper has a hybrid identity: long-term P-256 ECDH and P-256 signing keys in
AndroidKeyStore, plus software X25519 identity, signed prekeys, and one-time
prekeys for sessions. Messages travel as v2 multi-key envelopes (static ECDH,
self-healing across key drift) or v3 Double Ratchet frames (X3DH handshake, then
per-message forward secrecy). New chats try v3 first; proven v3 peers never
silently downgrade to v2 (seal failure blocks with a warning instead), and only
unproven peers may fall back to v2 envelopes. New v3 frames never carry a v2
insurance copy.

Three corrections to older docs:

- Keys are not "P-256 only". Chat confidentiality is X25519 (sessions and ratchet)
  with P-256 for identity and prekey signatures.
- Forward secrecy is not on every message. Only v3 ratchet frames have it. The v2
  envelope fallback uses static ECDH with no forward secrecy; a 30-day key rotation
  bounds exposure.
- Sign-in is email-free for the user but not for the backend. Usernames map to
  `user@u.whisper.local` and tokens map to `SHA-256(token)@whisper.toolz.app`
  through Supabase GoTrue virtual emails.

Images are AES-256-GCM sealed on device, wrapped as lossless PNG (`WZ1` header
with CRC32), and hosted on ImgBB through an edge function. Avatars use Supabase
Storage or ImgBB with deterministic per-owner keys. Push is UnifiedPush wake
pings (IDs and event kinds, no content) via the embedded FCM fallback
(RFC 8291-encrypted — the push service sees only ciphertext) or an external
distributor such as ntfy/NextPush; the latter receives ID-only pings, because
any human-readable name rides solely inside encrypted webpush payloads (§11).
Local Room caches hold ciphertext only.

Uses custom E2EE, not independently audited.

## 2. Cryptography

### 2.1 Primitives

`crypto/SessionCrypto.kt` is X25519 via Tink `subtle.X25519` (constant-time
curve25519-donna; the old BigInteger Montgomery ladder is removed),
HKDF-SHA256 (RFC 5869), HMAC-SHA256, SHA-256, and AES-256-GCM (12-byte IV,
128-bit tag, packed as IV + ciphertext + tag). `sharedSecret` returns null on
banned/low-order or all-zero peer points (Tink throws InvalidKeyException;
mapped to null); callers treat that as an invalid key. Covered by
`SessionCryptoVectorTest` with RFC 7748 §5.2/§6.1 vectors.

`data/whisper/WhisperCrypto.kt` is the hardware identity:

- Active ECDH key on `secp256r1` in AndroidKeyStore. Legacy alias
  `whisper_e2ee_ec_key`, new keys under
  `whisper_e2ee_ec_key_staged_<millis>_<entropy>`. Rotation is staged:
  `stageNewKeyPair`, publish, then `commitStagedKeyPair` (pointer is committed
  before the old key is deleted; history under the old key becomes unreadable,
  accepted trade-off) or `abortStagedKeyPair`. Startup sweeps orphaned staged
  aliases while protecting the active one, repairs dangling pointers, runs a
  3-attempt canary (never resets identity implicitly), and detects unexpected
  public-key changes.
- `deriveSharedKey` does ECDH, then HKDF-extract with a zero 32-byte salt
  (documented deviation from RFC 5869, stable, must not change without a
  migration), then HKDF-expand with info `whisper-e2ee-v1` to AES-256. No forward
  secrecy at this layer, as noted in code.
- `encryptMessage` / `decryptMessage` use AES-GCM with AAD of
  `whisper-message-v1` plus sender and receiver IDs, so ciphertext replayed into
  another chat fails authentication. Pre-AAD rows decrypt through a legacy
  fallback only if created before 2026-09-01 (`LEGACY_AAD_CUTOFF_EPOCH_MS`) while
  the fallback flag is on (earliest retirement 2026-12-01, gated on fleet
  counters). Message cap is 8,192 chars. Attachments add `whisper-attachment-v1`
  to the AAD.
- Signing identity `whisper_protocol_sign_key` (P-256, SHA256withECDSA) signs
  prekeys. The signed payload is always `SPK:<kid><publicKey>` built through one
  shared helper so signer and verifiers cannot drift.
- Keystore AES wrapper `whisper_protocol_wrap_key` protects X25519 seeds,
  SPK/OPK private halves, session keys, and ratchet snapshots. Fingerprints are
  SHA-256 of the public key rendered as eight groups of four hex chars.

### 2.2 X3DH session establishment

`data/whisper/WhisperSessionFactory.kt`:

- Initiator fetches the peer bundle from `whisper-bundle-fetch`, requires the
  `identity_binding` and a signed SPK, and fails hard on a bad signature (shown
  as key-change UI, never silent). It generates an ephemeral key and computes
  three Diffie-Hellman shared secrets (identity-to-SPK, ephemeral-to-identity,
  ephemeral-to-SPK), plus a fourth with a one-time prekey when one is available.
  The KDF concatenates them after a 32-byte `0xFF` pad through HKDF-SHA256 with
  salt of 32 zero bytes and info `WhisperX3DH-v1`, producing a 32-byte session
  key. The session ID is `s` plus 12 hex chars of SHA-256 of the key, derived
  identically on both sides. The header `{ik, ek, spkKid, opkKid}` must ride on
  the next outgoing message.
- Responder recovers its SPK and OPK private halves by key ID and recomputes the
  same key. A missing OPK private half is a hard failure (a silent downgrade once
  caused divergent keys and permanently locked chats).

### 2.3 Double Ratchet

`data/whisper/session/WhisperRatchet.kt` implements Signal sections
1-5. Root KDF uses HKDF with info `WhisperRatchetRoot` (64 bytes split into new
root and chain key). Chain KDF uses HMAC with `0x01` for the message key and
`0x02` for the next chain key. Headers carry `{dhPub, pn, n}` and the AAD binds
header bytes plus session and participant IDs, so routing tampering fails
authentication. Decrypt is copy-commit (`tryDecryptOnCopy`): the primary path
derives on a trial copy and commits only after AEAD verifies, so a forged
header/ciphertext advances no chain and triggers no DH step (snapshot-identical
before/after, pinned by `forged-header-no-mutation`). Out-of-order keys are
kept up to `MAX_SKIPPED = 400` with NO eviction: far-future `n` (gap beyond the
window) or a full window is REJECTED with `WhisperRatchetLostMessage`, leaving
the existing window intact. Consumed keys are remembered so duplicates still
open, and the last eight retired remote keys are tracked. Unrecoverable
messages throw `WhisperRatchetLostMessage`, rendered as an honest locked
placeholder. A gap-overflow lock (skipped-key gap beyond `MAX_SKIPPED`, which
can never catch up) additionally triggers a fresh handshake fire-and-forget,
rate-limited to one attempt per 30s per peer (`ratchet.gapHandshake`,
`WireProtocol.isGapLoss`/`shouldRefreshOnGap`), with the UI notice "Missed too
many messages - starting fresh session, ask peer to resend"; other losses keep
the placeholder and rely on server catch-up. Skipped AND consumed message keys
persist WITH the session (the whole snapshot is serialized and Keystore-wrapped
before it touches disk), so offline history and duplicate delivery survive
restarts. The honest forward-secrecy corollary: a device compromise yields that
wrapped snapshot, so FS protects keys derived AFTER the snapshot was taken
(future traffic), not messages whose keys were already on disk at compromise
time.

`data/whisper/session/WhisperSessionStore.kt` persists one session
per peer at `filesDir/whisper_sessions/<sha256(peerId)>.json` plus an in-memory
map, guarded by a per-peer mutex so two threads never advance one ratchet. It
also keeps an in-memory-only consecutive establish-failure count per peer
(incremented on every X3DH establish failure, reset on success or process
restart — never persisted, so a block can never wedge across reboots). Only
framing (session ID, pending header, peer identity key) is plaintext; the X3DH
key and the whole ratchet snapshot are Keystore-wrapped. A corrupt file is
deleted and treated as no session, which falls back to envelopes plus a fresh
handshake. Incoming handshakes are accepted on same-session replay or peer
identity change, otherwise the lower session ID wins so racing initiators
converge. `WhisperSessionSecretProtector.kt` is the wrap/unwrap seam
(Keystore in production, fake in tests).

`data/whisper/WireProtocol.kt` holds the pure decisions: negotiated
version never above what this build speaks, peer floor is the lowest version the
peer proved, v3 is used for live sessions or fresh contacts unless the peer
proved it cannot parse v3 or an establish failed within the last 30 seconds,
key changes are fail-closed (MATCH iff pinned == server; any difference is
CHANGED unless a ROTv2 rotation cert `ROTv2:<prev>:<new>:<counter>:<ts>`
chains the new key to the pin — counter must strictly exceed the peer's last
accepted counter (`WhisperKeyTrustStore` `rotation_counter_*`, default 0) and
|now-ts| must be within 24h; v1 `sign(prev,new)` certs are still accepted for
compat — verified peers always need manual re-verify; time windows never
auto-accept), stale sessions tear down only on in-window fresh rows, and after
3 consecutive establish failures with no live/proven session the envelope
fallback blocks ("Handshake repeatedly failed (3x) - blocked insecure fallback,
retry later", `send.blocked.downgrade_N`) instead of silently downgrading under
persistent failure. `WhisperProtocolConfig.kt`
 sets live version 2 (envelope fallback), ratchet version 3, ratchet
enabled.

### 2.4 Key management

- `WhisperPrekeyManager.kt`: publishes the identity binding (X25519
  identity key plus P-256 signer) to `profiles`, rotates the signed prekey
  weekly, tops the one-time pool to 50 (persisted cap 120). Private halves are
  Keystore-wrapped and persisted so process death cannot break handshakes.
- Rotation cert transport (`20261006` SQL: `profiles.rotation_cert` +
  `rotation_counter`): `WhisperPrekeyManager` publishes the ROTv2 cert
  (`ROTv2:<prev>:<new>:<counter>:<ts>`, old-key P-256 signed) on rotation;
  peers fetch it via bundle/profile rows and `rotationCertFor` feeds
  `classifyKeyChangeStrict` — counter replay and stale timestamps reject.
  RLS owner-write, authenticated-read.
- `WhisperKeyTrustStore.kt`: TOFU anchors plus user-verified keys in
  `EncryptedSharedPreferences` (migrated once from the old plaintext file),
  durable fsync writes off the main thread, plus the ROTv2 anti-replay counter
  (`rotation_counter_*` per peer, default 0 — only a cert with a strictly
  greater counter and a fresh timestamp chains).
- `WhisperKeyRotationStore.kt`: 30-day rotation interval with up to 6 hours
  of jitter, single source of truth shared with key-change classification.
  Rotation trust is cert-based (`sign(prev,new)` verified against the pin),
  never time-window based; verified safety numbers always re-verify manually.
- `ProtocolDiagnostics.kt`: process-local ring buffer (last 60 events) plus
  counters. Never carries content, IDs, or key material. Exported only through
  the debug diagnostics button.

## 3. Wire formats

| Version | Stored in | Shape |
|---|---|---|
| v1 legacy | `messages.content` + separate `content_iv` | raw base64 ciphertext + IV column |
| v2 envelope | `messages.content`, `content_iv = NULL` | `{"v":2,"k":[{"kid","iv","ct"}], "ik"?, "x3dh"?}` |
| v3 ratchet | `messages.content`, `content_iv = "v3"` | `{"v":3,"sid","dh","pn","n","ct", "x3dh"?}` (no `env` on new sends; legacy rows may still carry one for decode) |

`WhisperEnvelope.kt`: key IDs are the first 8 hex chars of
SHA-256 of the base64 public key. Senders seal only to the pinned key plus keys
chaining to it via a valid rotation cert (fail-closed — an unproven server key
is dropped and the send blocks with a key-change warning); receivers try each
decrypt candidate including the trial-only in-band sender key `ik` (never
adopted into trust without a cert). The sender public key
rides in-band as `ik` so delivery does not depend on a fresh profile row. The
X3DH header rides as a sibling `x3dh` object on the first session message.
Detection is prefix-based, so v1 and v2 rows coexist.

`session/WhisperV3Codec.kt`: one JSON object per frame, never a
multi-key envelope. `x3dh` appears only on the first frame. Legacy `env`
(decode-only) may still ride on pre-Phase-1A rows for backward compat, but only
for rows created at or before 2026-12-01 (`LEGACY_ENV_CUTOFF_MS`): post-cutoff
`env` copies are rejected fail-closed in `tryInsurance` (`v3.insuranceCutoff` —
a post-cutoff `env` is a replay or a misbehaving peer, since new sends never
populate it); new sends never populate it, and per-message FS state
(`fsProtected`, v + sessionProven) drives the lock badge in chat bubbles.

## 4. Images and avatars

- `WhisperImageCipherTransport.kt`: ciphertext becomes lossless PNG pixels
  (RGB payload, opaque alpha to defeat premultiplication, forced sRGB). v2
  container is magic `WZ1`, version byte 1, CRC32, length, then cipher bytes,
  with a legacy length-only fallback. Caps: 5 MiB cipher bytes, 8 MP decode
  (4 MP on low-RAM devices), bounds pre-pass against dimension bombs.
- `WhisperEncryptedImageHost.kt`: uploads PNG-wrapped bytes through
  `whisper-image-upload` (bearer token, 15/45 s timeouts, one refresh-and-retry
  on 401) and returns URL plus ID. Deletes go to Supabase `whisper-avatars`
  (path-checked to the caller's own folder, rejects `..`) or to
  `whisper-image-delete` for ImgBB blobs. Downloads require `https://`, strip
  query and fragment, allowlist `i.ibb.co`, `ibb.co`, and the project's
  `whisper-avatars` storage path (one redirect followed), cap 7 MiB, and map
  403/404 to an expired-image signal.
- `EncryptedBlobHost.kt`: interface (upload/delete/download) decoupling
  callers (`WhisperRepository`, `WhisperAvatarLoader` hold the interface
  type) from the ImgBB implementation; R2/B2 is a future impl behind the
  same seam.
- `WhisperAvatarCodec.kt`: deterministic per-owner OBFUSCATION (sealed blobs),
  not encryption — key from `HKDF(SHA-256(ownerPub + ":whisper-avatar-v1"),
  info "whisper-avatar-key")`, owner key as AAD. Sealed: anyone with your
  public key can derive the key; the host sees ciphertext only. Hides bytes
  from third-party hosts only, fails closed on mismatch. Never label "encrypted"
  in UI/docs; `strings.xml` carries no avatar-encryption claims (verified).
- `WhisperAvatarLoader.kt`: memory LRU plus in-flight dedupe plus sealed
  disk cache under `filesDir/whisper_avatars` (SHA-256 of URL, max 64 entries),
  with PNG and double-wrap healing and a generation guard so sign-out cannot
  repopulate the cache mid-flight.

Chat images are downscaled to 1920 px max dimension at JPEG quality 82 before
sealing (`WhisperChatViewModel.compressImageForUpload`). Avatars are cropped to
256 px (`WhisperRepository.AVATAR_SIDE_PX`).

## 5. Backend

`supabase/migrations/main-whisper-sql.sql` is the canonical single
baseline, squashing 18 migrations (policy in `whisper-sql-info.md`; header banner
lists all 17, `20260820`…`20261005_whisper_signup_discover_hardening.sql`).
Tables:
`messages` (with `content_hash` replay guard, block-aware insert guard, and a
sender-authorized delete-for-everyone tombstone exception in the content guard),
`message_reactions`, `friendships`/`friends` (pending-only transitions,
block-aware), `profiles` (identity binding, public key, last seen, avatar),
`whisper_blocks`, `whisper_prekeys` (account, key ID, kind, public key,
signature), `whisper_push_targets` (user ID primary key: push endpoint,
provider, Web Push `p256dh`/`auth` keys), `whisper_typing_signals`
(8-second freshness),
upload/discover/bypass/destructive/username-check/signup quotas, deleted
tombstones, image ownership, the legacy-sunset registry
(`whisper_legacy_disabled`), and the `whisper_public_profiles` view
(with direct `last_seen_at`). Includes
the discover, bypass-attempt, quota-refund, username-availability,
account-purge, signup-gate, and legacy-disable RPCs, owner-scoped
RLS, realtime publication, and full replica identity. Latest sections
(per the banner order): `20261003` server enforcement (sender-authorized
tombstone UPDATE, `whisper_check_username_available()` RPC at
10 checks/hour/caller, purge coverage), `20261004` presence/typing
opt-out removal (the `presence_enabled`/`typing_enabled` columns and
the typing-suppress trigger are dropped — stamps and typing signals are
unconditional again, same for everyone), and `20261005` signup Sybil
quota (`whisper_signup_quota` + `whisper_check_signup_allowed()`,
5 signups/day/IP, advisory-lock serialized, pre-auth executable),
Discover opt-in (`profiles.hide_from_discover` now defaults to true —
new rows are hidden from Discover/search until the owner enables it;
existing rows deliberately NOT backfilled), and the legacy-disable
registry (`whisper_legacy_disabled` + `whisper_is_legacy_disabled()`,
service-role only). Idempotent, safe to rerun.

Edge functions in `supabase/functions/whisper-*/index.ts`:

| Function | Role |
|---|---|
| `whisper-attest` | POST (user JWT) returns the caller's attestation verdict `{ok, mode:"cert"|"integrity"}`; 428 for unofficial builds. Single-file: the `_shared/attest.ts` gate is INLINED (dashboard deploys are single-file), no `_shared` imports. Optional Play Integrity strong path (env-gated, off by default). |
| `whisper-bundle-fetch` | POST `{account}` returns identity binding, latest signed SPK, and consumes one OPK; also returns `signer_history` — every signer the account ever published (archive trigger; group events verify against this set, which is what makes a reinstall NOT lock out the log's author). Rejects self-bundles, no-store. Attest-gated (inlined `_shared/attest.ts`). |
| `whisper-bypass-verify` | Constant-time screenshot-bypass password check, 5 failures per 15 minutes locks out with 429. |
| `whisper-delete-account` | Verifies JWT plus password header plus 5-minute confirmation timestamp, wipes server data before deleting the GoTrue user. |
| `whisper-image-upload` | Accepts base64 PNG with the ImgBB key held server-side, returns URL and ID. Attest-gated (inlined `_shared/attest.ts`). |
| `whisper-image-delete` | Deletes an ImgBB blob by ID. |
| `whisper-push-send` | Database webhook on message, friend, group-table and story-recipient inserts. Skips receivers seen within 60 seconds, then one wake ping per registered target: `provider='webpush'` targets get an RFC 8291-encrypted (aes128gcm) payload with RFC 8292 VAPID auth (names ride inside the ciphertext); `provider='plain'` targets (external distributors) get a NAME-SCRUBBED JSON ping — IDs and event kinds only, every human-readable name key stripped (see §11). Prunes dead endpoints on 404/410. Never logs tokens or payloads. Intentionally NOT attest-gated (webhook auth, never sees client headers). |
| `whisper-signup-gate` | Pre-auth IP-budgeted signup gate (5/day/IP via `whisper_check_signup_allowed`); server-side IP hash, 429 over quota. Attest-gated (inlined `_shared/attest.ts`). |
| `whisper-story-sweep` | Service-role hosted-copy sweeper for expired/deleted Daily Ups (leases via SKIP LOCKED, no pg_cron) — see §14. |
| `whisper-legacy-migrate` | Service-role legacy token migration (copies the profile to the full-hash user, deletes the legacy GoTrue user, records `whisper_legacy_disabled`). |

Anti-mod gate (a speed bump, NOT a trust boundary — read this twice): every
`whisper-*` edge call carries
`X-App-Package` / `X-App-Cert-Sha256` / `X-App-VersionCode`
headers (`data/whisper/PlayIntegrityAttestor.kt` —
local-only, FOSS-safe: PackageManager signing-cert SHA-256 vs
`WhisperPinConfig.OFFICIAL_CERT_SHA256`, cached 1h; no Google API,
no Play Integrity dependency). `supabase/functions/_shared/attest.ts`
enforces package + cert + version (`MIN_VERSION_CODE`)
server-side and answers 428 for unofficial builds; it is inlined
verbatim into `whisper-bundle-fetch` and `whisper-image-upload`
(single-file dashboard deploys). Honesty, per external review: all of this is
client-supplied header checking against values an attacker can read from an
official APK — it stops lazy reuse of the official label, not a determined
repackager. It is an availability/abuse gate, never an E2EE participant-vetting
mechanism, and NO security statement in this document depends on it. The only
cryptographically real path is the OPTIONAL Play Integrity verification
(`PLAY_INTEGRITY_ENABLED`, off by default — a Google-dependent strong gate that
cannot ship in the FOSS baseline). `WhisperPinConfig.kt` holds the identity constants
(`OFFICIAL_PACKAGE=com.frerox.toolz`, `OFFICIAL_CERT_SHA256`
pinned to the release signing cert (`58A16BCD6E9CEF75706A957B19A15BED19BD47F0B7FDB602364D9EBFC5C369F9` — public value, readable from any official APK; the private key never leaves the release machine), `MIN_VERSION_CODE=17L`, synced with
`update_manifest.json` `minimumVersionCode`). `update_manifest.json`
also carries an Ed25519 `signature` over the canonical manifest JSON
(`UpdateRepository` verifies it fail-closed — the Ed25519 keypair is
provisioned (pubkey embedded, manifest signed); unverified manifests are
ignored and the GitHub Release API path is unaffected). Locally, `ui/screens/whisper/WhisperBuildGate.kt`
 gates Whisper access at runtime: release-cert pin,
`FLAG_DEBUGGABLE` in a non-debug build, and the version floor
(max of the signature-verified manifest floor and
`WhisperPinConfig.MIN_VERSION_CODE`) — blocked builds see a full-screen
card with the reason and the official download URL, so no chat UI is
reachable.

`data/whisper/EdgeFunctionClient.kt` is the single hardened transport
(Ktor over OkHttp, anonymous/user/explicit-token bearers, mandatory timeouts, no
body logging). `di/SupabaseModule.kt` falls back to an invalid URL when the
build has no Supabase credentials, keeping Whisper offline with one log line.

## 6. Client repository

`data/whisper/WhisperRepository.kt` orchestrates everything.
Constants: 8,192-char message cap, JPEG/PNG/WebP images, disappearing images
from 60 seconds to 180 days, event dedupe TTL 30 seconds (max 1024 IDs), up to
6 realtime resubscribes at least 2.5 seconds apart, 8-second typing freshness,
256 px avatars, 5-minute profile cache.

- Health: republishes the local key when the server row diverges (repairing a
  broken identity first), retries prekey publication on failure.
- Send: tries the ratchet under the per-peer mutex (establishing the session on
  a miss), otherwise builds a multi-key envelope to every candidate key. The
  advanced ratchet state is persisted BEFORE the frame leaves the mutex
  (`sealWithRatchet`: encrypt → clear pending header → `sessionStore.save` —
  verified, no code change in Phase 1B). Inserts
  the row with a client UUID (idempotent retries), queues ciphertext-only on
  failure (never plaintext), and schedules delivery. Images are sealed per
  recipient plus a self-addressed copy so the sender keeps access after partner
  rotation, hosted, then referenced by an encrypted `whisper:image:{...}`
  message.
- Receive: server rows flow through unified decryption (v3 frame path with
  handshake gate and insurance fallback, else v2 envelope trying every key ID
  and the in-band sender key with a 512-entry memo, else legacy v1 with
  pre-cutoff fallback), then tombstone and image enrichment, then the
  ciphertext-only Room guard (anything non-encrypted becomes a legacy
  placeholder), then reaction snapshots. In-band keys can heal trust; a fresh
  non-v3 row from a session peer tears the stale session down.
- Realtime: `chatpg_<sortedPair>` postgres lane per chat,
  `whisper-user-inbox-<userId>` incoming lane (message broadcasts deliberately
  not consumed there; RLS plus two single-column filters scope it to the
  user's rows), `chat_<sortedPair>` broadcast lane, `typing_` and `presence_`
  lanes, cached channels with health watching and resubscribe, dedupe IDs,
  poll backoff ladder, sorted-pair conversation keys, last-seen pings mapping
  to online (2 min) / recent (1 h) / offline.
- Social (`WhisperRepositorySocial.kt`) and profile
  (`WhisperRepositoryProfile.kt`) splits cover friend requests, blocks
  (client plus DB enforcement), discover paging with quota, hide/mute, typing
  broadcast, profile CRUD,
  username-availability checks (migrating to the quota-guarded RPC),
  avatar upload/delete under
  `whisper-avatars/<userId>/`, and QR key verification.
- Deletes and reactions: delete-for-everyone writes a
  `[deleted_by_sender:<name>]` tombstone and deletes the hosted image (the
  server content guard permits only this sender-authorized tombstone write),
  delete-for-me stays device-local, clear-chat supports 24h/7d/30d/all/custom
  with 30-second undo, reactions send delta toggles reconciled by authoritative
  snapshots, and the outbox flushes FIFO (duplicate keys count as delivered,
  exhausted attempts go to the drop ledger).

Supporting stores, all read in full: access-file backup and restore
(`WhisperAubupManager`, credential-only `WhisperAccessPayload` — username,
authType, credential, displayName; ratchet/session state is NEVER included,
verified Phase 1B, no code change), device-only delete-for-me with 24-hour
eviction (`WhisperDeletedMessagesStore`), auto-returning hidden chats
(`WhisperHiddenChatsStore`), mute with expiry (`WhisperMutePreferences`),
30-second clear-chat undo (`WhisperUndoBufferStore`), grouped deduplicated
suppressed notifications (`WhisperNotificationManager`), permanent-vs-transient
error mapping for WorkManager (`WhisperErrorMapper`), sealed PNG disk cache
(`WhisperImageDiskCache`), the local tombstone entity, message entity with
numeric sort epoch and protocol inference, message DAO capped at the latest
500 rows, ciphertext-only Room outbox FIFO capped at 100 oldest-kept with a
200-entry drop ledger plus legacy prefs migration, and the immediate
network-constrained delivery scheduler with 15-second exponential backoff.

## 7. Auth

`data/whisper/WhisperAuthManager.kt`:

- Username path: 3-20 lowercase letters/digits/underscores, password of at least
  10 chars, display name 1-60 chars, mapped to `user@u.whisper.local` through
  GoTrue email auth (confirmation off, immediate session, one retry on transient
  post-signup failure).
- Token path: 32 random bytes as 64 hex. Email is `SHA-256(token)` at
  `whisper.toolz.app`, password is `SHA-256("pwd_" + token)`. Login makes a
  SINGLE attempt with the current full-hash derivation; only when that misses
  with invalid credentials does it probe the three pre-P1-15 legacy
  derivations ONCE each (truncated-email + SHA-512/SHA-256 password
  variants, paced 500 ms apart — `migrateLegacyTokenAccounts`) before
  failing. A legacy hit does NOT log the legacy session in: it signs the
  legacy session straight back out and fails with
  `LegacyMigrationNeededException` (carrying the legacy username/display
  name), which the UI surfaces as the "Legacy account found — tap Migrate"
  banner (`st_Whisper_Legacy_Banner`) with a 30-day migration window
  (`st_Whisper_Legacy_Warning`); the retiring edge function
  (`whisper-legacy-migrate`) disables the legacy GoTrue identity via the
  Auth admin API and records it in the `whisper_legacy_disabled` registry.
  No legacy hit fails closed with "Invalid login credentials". Tokens are
  normalized (non-hex stripped, lowercased) and validated as 64 hex.
  Credential errors are detected structurally, not by bare status code.
- Extras: restore from the Toolz password vault or an encrypted access file
  plus whisper code, clipboard auto-expiry for displayed tokens, username
  availability check, sign-out, and account deletion (re-auth for password
  accounts, edge deletion with password header plus confirmation timestamp,
  then local wipe of sessions, Room, trust, outbox, avatars, and push
  registration).

`ui/screens/whisper/WhisperAuthViewModel.kt` drives these flows with
clipboard expiry, access-file import, and single-attempt token login.

## 8. UI and view models

All 19 files under `ui/screens/whisper/`:

- `WhisperViewModel.kt`: hub state (conversations, friends, single-source
  incoming requests, outgoing requests, search, recommended and paged discover,
  mutes, error plus info channel), conversation hide and history clear, block
  and mute toggles, profile update, avatar upload and delete, access-file
  creation, 30-day key rotation heartbeat, account deletion and sign-out with
  full wipe, screenshot-bypass flag, beta and onboarding flags.
- `WhisperChatViewModel.kt`: per-chat state (message flow capped at 500,
  friend and block status, typing, online state, last seen, mute, reply target,
  search matches, key trust, upload state, decrypted image bytes, realtime
  status, errors) plus an isolated 30-second undo state that does not recompose
  the list. Operations include message load and paging, optimistic send, image
  pick/compress/send, encrypted image load through the allowlist and caches,
  reactions, both delete paths, clear-chat with undo, mute and block, friend
  actions, QR key verification, drafts, realtime reconnect, and visibility
  pausing. History remains readable offline.
- Screens: hub with chats/friends/requests/discover/profile tabs, unread badges,
  presence dots, key-change banners, and pull-refresh (`WhisperMainScreen`);
  row cards (`WhisperMainScreenComponents`) and chat-list
  message merging (`ChatMessageMerger`); full chat with bubbles,
  reply snippets, reactions, in-message search, typing indicator, online header,
  mute/block menus, expiring attachments, tombstone and locked placeholders,
  the per-message FS lock badge (`message.fsProtected` → Lock/LockOpen icon,
  `WhisperChatScreen`), and the undo bar (`WhisperChatScreen`); self profile
  with editing, avatar picker, fingerprints, rotation, access file, sign-out,
  deletion, and a debug diagnostics export (`WhisperProfileTab`); peer profile
  with QR verification and block/unfriend; friends-only onboarding explainer;
  username/password plus token auth UI with vault restore, access-file import,
  token-loss warnings, and the legacy-migration banner; re-auth banner;
  toasts; FLAG_SECURE window control with a server-gated screenshot bypass;
  the LOCAL runtime tamper gate (`WhisperBuildGate` — release-cert pin,
  non-debug `FLAG_DEBUGGABLE`, version floor; blocked
  builds get a full-screen card with the reason and the official download
  URL, so no chat UI is reachable); and the bypass verifier mapping Granted/Denied/RateLimited/
  Unavailable with fail-closed behavior.

## 9. Push, workers, app wiring

- `push/WhisperPushService.kt` (`PushService`): UnifiedPush registration events
  upsert into `whisper_push_targets` (user ID primary key: endpoint, provider,
  Web Push `p256dh`/`auth` keys), parked locally in `whisper_push_pending`
  when they arrive before login and retried on next launch. Incoming wake
  pings (`push/WhisperPushNotifier.kt`) show generic content-free
  notifications keyed by sender and message ID for dedupe. Registration is
  zero-UX (`push/WhisperPushRegistrar.kt`): the default distributor is
  auto-picked (external distributor first, else the embedded FCM fallback,
  which requires Google Play Services or microG on-device); with none
  available the app runs without pings.
- `ToolzApplication` schedules local tombstone cleanup every 24 hours and
  encrypted outbox delivery every 15 minutes, plus immediate delivery on send.
  Delivery retries transient failures with backoff up to five WorkManager
  attempts; permanent rejections end as success because the drop ledger already
  handled them. `MainActivity` retries parked push registrations, injects the
  avatar loader, and owns the `whisper-auth://login` deep link alongside the
  generic share intents. The manifest declares that deep link and the
  UnifiedPush `PUSH_EVENT` service entry point.

## 10. Tests

Handshake math and SessionCrypto vectors (RFC 7748 sections 5.2 and 6.1, the
`WhisperX3DH-v1` HKDF framing, AES-GCM round trip), ratchet security matrix
(`WhisperRatchetSecurityTest`: out-of-order, replay, reinstall desync,
key-change strict, skipped-limit rejected, forged-header-no-mutation
snapshot-identical), interop/persistence/reinstall gates (`WhisperV3InteropTest` —
self-interop only: both ends are this code, so a systematic framing bug would
pass there unnoticed), EXTERNAL reference vectors (`WhisperRefVectorTest`: RFC
7748 DH pairs in Signal X3DH arrangement, RFC 5869 Appendix A.2 HKDF,
Python-cross-checked Double-Ratchet root/chain KATs, env-cutoff boundary, and
the no-`env`-on-new-sends pin — every expected value comes from an RFC or an
independent implementation, never from this repo's output; external review of
the full Signal-compat claim is still needed), pure-rule pins for the
residual-downgrade N-block, ROTv2 counter/timestamp anti-replay, and
gap-refresh rate-limiting (`WireProtocolTest`),
chaos with zero wrong plaintexts (`WhisperRatchetChaosTest`, far-future
rejected, window never evicted), model invariants (names,
avatars, pending/sent/read ordering, image prefix, tombstones, snake-case
serialization), chat reaction merging, bypass verdicts (blank and oversize
inputs), image transport (v2 magic, legacy fallback, CRC rejection, pixel cap),
and crypto AAD (direction binding, fingerprints, null safety).

Daily Ups adds 20 JVM tests (`WhisperStoryDescriptorTest`: roundtrip, wire
prefix/field contract, default elision, tamper matrix, delete-capability
placement; `WhisperDailyUpErrorMapperTest`: the full server-literal vocabulary
plus config parity with the SQL plane — emoji palette and bounds), and the
instrumented `WhisperDailyUpPhotoPreprocessorTest`: a geotagged,
device-labeled JPEG publishes as Ready with ZERO GPS/make/model/datetime EXIF
on the exact publish bytes, the 1920 long edge only shrinks, alpha sources
re-encode to PNG, and unsupported/empty/garbage sources are rejected —
device-only, like all Bitmap work.

## 11. What the server sees

Ciphertext and IV markers, sender and receiver IDs, timestamps, profile fields
(username, display name, bio, avatar URL), the friend and block graph, last-seen
presence, typing signals, reaction rows, push targets (endpoint + Web Push
keys), prekey material and
signatures, and image URLs with IDs. Usernames reveal who talks to whom;
message and image bytes stay opaque. Group rows additionally reveal group IDs,
member lists, epochs, per-member frame sizes, a per-row traffic label
(`msg_kind`: chat/poll/vote/image), the admin-only-send mirror flag, and
reaction/request/receipt presence rows (frames and bodies themselves stay
opaque — one v3 frame per member, each sealed through that member's 1:1
session). The push path splits by distributor: the embedded FCM fallback
receives RFC 8291-encrypted pings (ciphertext only), while an external
distributor's server (ntfy/NextPush) sees a name-SCRUBBED wake JSON —
external review (2026-10) flagged that names on a plain third-party hop let
that server correlate who-wrote-where, so every human-readable key
(senderName, groupName, inviterName, actorName, subjectName, posterName) is
stripped for `provider='plain'` targets; those pings carry IDs and event kinds
only, and the CLIENT rebuilds each name on-device (friends cache + local group
table) before rendering the notification. Webpush targets keep the names —
their payload is RFC 8291-encrypted end-to-end, so the push service sees only
opaque bytes (invite payloads additionally carry `inviterId` so the client can
resolve the inviter without a name key). Group push bodies still never carry
message text. ImgBB sees opaque PNGs with expiry. Passwords travel only to
GoTrue over TLS at auth and deletion time; the bypass password goes only to the
edge secret check.

Daily Ups rows additionally reveal: poster/recipient/viewer/reaction-author IDs
and timestamps, the `receipts_enabled` flag, the story state machine
(published → delete_pending → deleted / cleanup_failed), the poster's 5/UTC-day
quota counter, and the six-emoji reaction vocabulary. One deliberate, explicit
exception to "reactions hide when receipts are off": REACTION rows are visible
to the story poster ALWAYS (even with view receipts off) and to the reaction
author — the server-side §14 policy keeps a poster's reaction wall working
without timeline overlap. Sealed descriptors on recipient rows are opaque
buckets; stories carry NO delete capability column — the
`whisper_image_ownership` ledger (unique delete_url, written by the upload
edge) is what resolves the sweep's deletes. The story push ping carries
poster ID and story ID — and the poster NAME only on encrypted webpush
targets (plain targets get IDs and resolve the name locally); image- and
key-free either way (§14 has the payload).

## 12. File inventory

> Line counts are deliberately NOT listed here: they rot on every edit (and
> sections of older revisions disagreed with each other — one reason this list
> dropped them). Names and roles stay accurate; the working tree is truth.

Client `data/whisper` (45 files + `session/`): EdgeFunctionClient,
EncryptedBlobHost, PlayIntegrityAttestor, ProtocolDiagnostics, session/
(WhisperRatchet, WhisperSessionSecretProtector, WhisperSessionStore,
WhisperV3Codec), WhisperAubupManager, WhisperAuthManager, WhisperAvatarCodec,
WhisperAvatarLoader, WhisperCrypto, WhisperDailyUpPhotoPreprocessor,
WhisperDeletedMessagesStore, WhisperDeliveryScheduler,
WhisperEncryptedImageHost, WhisperEnvelope, WhisperErrorMapper,
WhisperGroupEntities, WhisperGroupNotifications, WhisperGroupPolicy,
WhisperGroupReadStore, WhisperHiddenChatsStore, WhisperImageCipherTransport,
WhisperImageDiskCache, WhisperImagePreprocess, WhisperKeyRotationStore,
WhisperKeyTrustStore, WhisperLocalTombstoneEntity, WhisperMessageDao,
WhisperMessageEntity, WhisperModels, WhisperMutePreferences,
WhisperNotificationManager, WhisperOutboxEntity, WhisperOutgoingQueue,
WhisperPinConfig, WhisperPrekeyManager, WhisperProtocolConfig,
WhisperRepository + WhisperRepositoryGroups / WhisperRepositoryProfile /
WhisperRepositorySocial / WhisperRepositoryStories, WhisperSessionFactory,
WhisperStoryLocalEntity, WhisperUndoBufferStore, WireProtocol.

Groups + Daily Ups UI (`ui/screens/whisper`): WhisperGroupsSection,
WhisperGroupsViewModel, WhisperGroupChatScreen, WhisperGroupChatViewModel,
WhisperGroupInfoScreen, WhisperGroupInfoViewModel, WhisperDailyUpsScreen,
WhisperDailyUpsViewModel, WhisperDailyUpsComposeScreen,
WhisperDailyUpsComposeViewModel. Tests: `WhisperGroupsTest` (43 tests, incl.
the reinstall-proof signer-set cases), the Daily Ups suite in §10.

Also: `crypto/SessionCrypto`, `push/` (WhisperPushNotifier,
WhisperPushRegistrar, WhisperPushService), the whisper workers
(WhisperDeliveryWorker, WhisperLocalCleanupWorker, WhisperClipboardClearWorker),
ten edge functions (whisper-attest, whisper-bundle-fetch,
whisper-bypass-verify, whisper-delete-account, whisper-image-delete,
whisper-image-upload, whisper-legacy-migrate, whisper-push-send with the Daily
Ups story branch, whisper-signup-gate, whisper-story-sweep) plus the inlined
`_shared/attest.ts` gate, and the SQL baseline (groups + Daily Ups sections
included, idempotent re-runnable).

## 13. Groups (Phase 2, ships in 1.1.7)

`WhisperGroupsConfig.ENABLED = true` in dev builds (1.1.7 is unshipped at doc
time, so only dev devices carry it); every entry point still requires it, and the
Chats-tab section returns early otherwise. Server-side, every group write
requires `client_version_code >= 18`, and the ship-day floor flip blocks
<1.1.7 clients from all of Whisper — so the flag stays true after ship.
1:1 behavior is unchanged (no shared code path was
rewritten — four properties were widened private→internal (`outgoingQueue`,
`deliveryScheduler`, `deletedStore`, `imageDiskCache`), one DAO was added
to the repository constructor; `VerifiedBundle` fields; the 1:1 outbox
flush additionally skips `groupId != null` rows, a pure guard).

Transport (`WhisperRepositoryGroups.kt`): pairwise fan-out, ≤12
members. The sender seals one inner frame per member (self included, so the
sender's own lines open through the same path) through the unmodified 1:1
`sealWithRatchet`; groupId + epoch ride inside the AEAD plaintext
(`WhisperGroupFrameBody`, version field required on the wire). Each member
opens only their frame (`openV3Frame` + `openGroupFrameBody`) and rows open
only for epochs the reader belonged to (`isMemberAt` ranges — re-added
members keep a gap for missed epochs). Membership derives from verified
admin-signed events only (`applyEvents`, fail-closed: bad sig, non-admin op,
last-admin removal, over-cap, stale epoch, unordered log, legacy `add`).
Signatures use the
admin's protocol signing key (`signProtocol`/`verifyProtocol`). Since the
20261019 signer-history section, each event row carries the signer cert
that made its `admin_sig`, and verification accepts a row cert ONLY if it
is in the actor's PUBLISHED signer set (`whisper_group_signer_history`
archive trigger, served by `whisper-bundle-fetch.signer_history`; self
reads its own archive rows over RLS) — so a creator reinstall (new
device key in the same account set) replays the log it authored before
the keystore key was destroyed, and a cert the account never published
fails closed even over a well-formed signature. Rows without a row cert
(pre-1.1.7 data only) fall back to legacy current-bundle verification.
Event payloads are TEXT
(jsonb would normalize key order and break signature round-trips).

Resilience (correctness + resilience passes): a poisoned event no longer
bricks the group — `applyEventsTolerant` truncates to the verified prefix and
flags the membership `degraded` (chat + info banner it; history at/after
`badSeq` is dropped, never trusted). A bad CREATE still fails the whole log.
Sync is incremental off the local event cache (watermark = max cached seq;
a degraded watermark drops the cache and refetches full). Unknown future event
types decode to an `UNKNOWN` sentinel that fails closed inside `applyEvents`
(truncatable) instead of bricking the sync before verification starts. Seq
races between admins retry with re-sign of seq AND epoch (3 attempts, backoff)
instead of surfacing 23505. Typing upserts set `updated_at` explicitly (a
bare PK-conflict upsert would keep the stale stamp). Pagination cursors are
strictly-older (`lt`, never `lte`). Report pre-checks last-admin BEFORE
blocking (no stranded block-without-exit). Per-leg seal failures log the
self-vs-peer split and stay fail-closed (partial fan-out never sends).
`syncGroups` isolates per-group failures (one bad group never hides the
healthy ones). Offline sends queue sealed bundles in the Room outbox
(`groupId` legs, `content_iv = v3-group:<epoch>`; pictures re-run from
persisted original bytes under `gpic`) drained by `flushGroupOutbox` on hub
and chat loads; the worker lane stays 1:1-only. Join is event-first with a
heal path (log-knows-me-but-row-missing restores the row); create tears down
partial state (orphan rows, phantom invites) on mid-way failure. Transport
errors map to strings (`mapGroupError`) — raw server English never reaches UI.

Server (`main-whisper-sql.sql` groups sections): `whisper_groups` /
`whisper_group_members` / `whisper_group_events` / `whisper_group_messages` /
`whisper_group_invites` + fan-out quota `whisper_check_group_send_allowed`
(600 envelopes/day/sender) + guard triggers (12-cap on members AND
members+invites, epoch chain, member+epoch+admin-send+quota message guard
(`admin_only_send` mirrors the signed SETTINGS log via trigger + backfill,
so modded clients cannot bypass it), invite-gated
member inserts, consume-on-join, all fail-closed 42501) + last-member disband
DELETE policy (a live group can never be nuked) + receipt/reaction cleanup on
message wipe + request-consumed-on-invite. `whisper_group_reactions`
(one emoji per user per message) and `whisper_group_join_requests`
(friends ask in, admins approve/deny) are member-RLS presence tables on the
realtime publication. Message rows carry `msg_kind` (chat/poll/vote/image) so
votes stay push-silent. The direct-`add` event type
is retired from the CHECK (legacy rows degrade, never resurrect). Group
writes require `client_version_code >= 18`.

UI: Chats-tab `WhisperGroupsSection` (rows with picture, last-message
preview, time, unread badge (decrypt-free count, no 20-cap), @You mention chip
+ persistent invite cards with Join/Decline + withdrawn-invite retraction +
in-flight guards + create dialog with friend picker, picture picker, and
invite mode + join-with-ID request dialog), `WhisperGroupChatScreen`
(in-timeline system bubbles from the verified log, unread separator +
jump-to-latest FAB (opens at first unread), bubbles with sender names,
reaction chips + emoji picker, expiring images with save-to-gallery viewer,
quote replies, poll cards with admin close, @mention highlighting, send +
photo attach with disappearing-photo picker, mute, refresh, full-history
search (decrypted page-by-page, server rows are opaque), draft persistence,
degraded banner, live refresh on events/messages/wipes/reactions, typing line,
long-press actions, seen-by info, paging), `WhisperGroupInfoScreen`
(picture header with permission-gated change, description, rename (admin-only:
server RLS), multi-pick add/invite with search, member search, pending
invites with admin cancel, inbound join-request inbox with approve/deny,
promote, demote, make-owner, remove, block-a-member, report (= block + leave,
last-admin pre-checked), mute with durations, per-group notifications toggle,
settings card (admin-only send, member-edit flag, description), share sheet
with QR, shared-photos gallery with save, leave, last-member disband — all
admin-gated client-side AND server-side where the server can see the rule).
Group mutes reuse `WhisperMutePreferences` under `group:<id>` (durations
supported by the store).

Invites: creating a group sends persistent invites (never direct adds).
Invitees can also READ the event log (SELECT policy mirrors the invite exception — otherwise join fails with "no history"). Invite rows die only by join (trigger-consumed), decline (invitee), or cancel
(any admin — explicit decision: owner-only was considered, any-admin chosen
to match WhatsApp-style admin teams). No expiry. Invitees see the card on the
Chats tab (survives restarts) and get pinged (realtime while open, UnifiedPush
ping while closed). Owner gets join/leave/decline pings with randomized copy;
per-group toggle defaults ON for owner/admins (join/leave scope, +declines
for the owner) and OFF for members; admin-action events
(remove/promote/demote/rename/picture/settings) ping like message pings
(mute + open-chat + self only, no toggle), with you-vs-other copy. Message
pushes carry the sender name (like 1:1). Votes never push (tally refreshes
silently in open chats). Withdrawn invites retract their notification.
Push fan-out lives in `whisper-push-send`
(single-file, dashboard deploys; webhooks must be INSERT-only on the three
group tables — the in-code op gate is defense-in-depth; NEVER add webhooks on
receipts/typing/reactions/requests); realtime publication
now includes all group tables. Push payloads carry names ONLY on encrypted
webpush targets: plain (ntfy/NextPush) targets are scrubbed to IDs and event
kinds, and the client rebuilds every name locally (§11 — the review-prompted
scrub, deployed server-side at ship).

Group picture: EXIF-strip + 1920 + JPEG82 (shared preprocess, 1:1 untouched),
fresh AES-256-GCM key per picture, PNG-wrapped for the image host, uploaded
via `EncryptedBlobHost`, key sealed per member into a signed `picture` event
(`{"url","frames"}` — no IV field; the nonce rides inside the sealed bytes).
URL is server-visible metadata; bytes + key stay opaque. Picture changes do
not bump the epoch; removed members keep bytes they already fetched (can't
un-send) but never get new keys.

Report/block: info-screen Block is 1:1-level (does not remove the member from
the group nor stop their lines); Report = block everywhere + leave the group
(confirmed, no server moderator queue — documented as the v1 semantic).

Social slice: frames carry `wg1:<json>` structured bodies (text, image ref
with optional expiry, reply quote, poll, vote, poll-close, mentions) with
legacy bare-text fallback; polls tally client-side (latest vote per sender
wins, admin close freezes the tally, no server change); @mentions render
highlighted with an @-picker at compose time (no special push — the server
cannot see mentions; unread @You chips ride the list rows); replies freeze a
quote snapshot (best-effort scroll to the quoted client_id); `demote` /
`settings` (admin-only send + member-edit flag + description) are signed
events in the same log (settings bump no epoch); picture edits follow the
member-edit flag (rename stays admin-only: server RLS); ownership moves via
promote + `created_by` update; receipts (`whisper_group_receipts`), reactions
(`whisper_group_reactions`, one emoji per user per message, toggle to remove),
typing (`whisper_group_typing`), and join requests
(`whisper_group_join_requests`, friends ask in via ID/QR, admins approve into
an invite or deny) are member-RLS presence tables on the realtime
publication. Admin-only groups reject member sends fail-closed client AND
server side. Chat photos support disappearing timers (1m/1h/1d, same bounds
as 1:1); expired images render expired and never decrypt.

Dashboard checklist (ship day): (1) four INSERT-only webhooks
(whisper_group_invites, whisper_group_messages, whisper_group_events,
whisper_story_recipients) →
whisper-push-send, and NO webhooks on whisper_group_receipts /
whisper_group_typing / whisper_group_reactions / whisper_group_join_requests /
whisper_story_views / whisper_story_reactions;
(2) redeploy whisper-push-send — REQUIRED this ship even without new story
machinery: the plain-target name scrub, the `inviterId` invite field, and the
story branch all live in that function, and an outdated deploy ships name
leaks to external distributors by omission; ALSO redeploy
whisper-bundle-fetch — REQUIRED, its new `signer_history` field is what makes
group verification reinstall-proof (an outdated deploy serves empty history
and the OLD current-cert-only behavior returns silently);
deploy
whisper-story-sweep; (3) re-run the full
main-whisper-sql.sql (idempotent, additive — this ship it adds
whisper_group_signer_history(+ archiving trigger + backfill) and
whisper_group_events.signer_cert); (4) publish 1.1.7 APKs + signed
manifest with floor 18; (5) set edge MIN_VERSION_CODE=18; (6) run the
post-deploy smoke (supabase/POST_DEPLOY_SMOKE.md) — SQL trigger health,
bundle-fetch `signer_history`, and the plain-push name-scrub, end to
end; do NOT proceed with the rollout on a failed part.

v1 limits (documented, not bugs): invite mode is fixed at creation;
QR/ID join creates a REQUEST (admins approve into an invite — no token-link
auto-join); voice notes, video, docs, and forwards need new pipelines/tables
(neither 1:1 nor groups have them); message editing is absent; the group
outbox drains on app open (the worker lane stays 1:1-only — a blind worker
replay would burn attempts on stale-epoch rows); a queued vote replays its own
kind now: the outbox row persists `groupMsgKind` (drain sends kind=vote, no
misaddressed push; historic rows predating the column fall back to the default
kind, the old behavior); unread @You detection covers the last 20
decrypted rows (counts are exact); search decrypts up to 500 rows per query.

## 14. Daily Ups (stories, ships in the same 1.1.7)

24-hour photo "ups" posted to all friends at once, taxed 5 per UTC day and bound
to the friend graph AT POST TIME. Entry: `WhisperDailyUpsConfig.ENABLED`
(compile-time flag in `WhisperRepositoryStories`, the same gating pattern as
§13; the Chats entry row and both nav routes no-op while off and stay true
after ship because the server floor is 18).

### Photo pipeline (`WhisperDailyUpPhotoPreprocessor`)

`preprocessDailyUpPhoto(context, source, mime)` produces uploadable ciphertext
or rejects — never falls back to raw input:

-accepts jpeg/png/webp sources ≤ 12 MiB (else `INPUT_TOO_LARGE`/`UNSUPPORTED`);
-decodes capped at 8 MP (4 MP on low-RAM devices via `ActivityManager`);
-scales the long edge to 1920, only ever DOWN, aspect preserved;
-re-encodes JPEG q82 (alpha sources → lossless PNG), capping plain bytes at
`WhisperImageCipherTransport.MAX_CIPHER_BYTES − 16` (5 MiB; `OUTPUT_TOO_LARGE`);
-EXIF/GPS strip verified, not assumed: the re-encode drops metadata by
construction AND the output is reopened and audited against
`DAILY_UP_EXIF_TAGS` (make/model/dates/GPS lat-lon-alt-timestamps) — any
survivor is `METADATA_REMAINS` and the headline is never published;
-seals with `newGroupPictureSeal` (`nonce || ciphertext`), same shape as group
pictures — the descriptor therefore needs NO IV field.

The instrumented test proves the privacy promise on the exact publish bytes.

### Distribution: descriptors, not ratchet frames

A story is ONE photo + ONE fresh AES-256-GCM key. The descriptor
`whisper:story:{v,u,k,x,r,a}` — v (version gate; decode rejects explicit
non-1), u (host URL), k (32-byte base64 AES key, exactly 44 chars or tampered),
x (server expiry, reject ≤ 0), r (receipts on; omitted = off), a (ImgBB delete
capability — ONLY in the poster's self-sealed copy) — carries NO story id by
design: ids always come from rows (server truth). One descriptor is sealed per
friend with `sealStaticEnvelope` (V2 static envelope over the pinned 1:1 key —
NEVER Double-Ratchet frames, so posts never touch protocol state;
`openStaticEnvelope` is likewise stateless). The poster's own copy is
self-sealed (`crypto.encryptMessage(json, myPub, me, me)` +
`WhisperEnvelope.encode(..., myPub)`; in-band key opens because there is no
self pin) and lives in Room with state `own`; viewer rows carry state
`active` and no capability. The audience snaps at publish: whisper_story_publish
validates every recipient against the CURRENT accepted-friend graph AND the
block ledger, so friends added later have no row to read and blocks stop
further distribution server-side.

### Server plane (`20261018_whisper_stories_actions.sql` in main-whisper-sql.sql)

Tables: `whisper_stories` (poster, per-poster-unique `client_id`, `host_url`,
`host_object_id`, `receipts_enabled`, state
published → delete_pending → deleted / cleanup_failed; the
`whisper_story_enforce_server_expiry` trigger rewrites created_at/expires_at on
every insert, making the 24-hour lifetime an invariant no caller can bend),
`whisper_story_recipients` (PK story+recipient, opaque sealed descriptor
≤ 32768 = `DESCRIPTOR_MAX_CHARS`), `whisper_story_views`,
`whisper_story_reactions` (PK story+user, one toggleable emoji from the
whitelist the client renders verbatim),
`whisper_story_cleanup` (lease registry), `whisper_story_publish_quota`
(poster+UTC-day, ≤ 5 enforced on the row UPDATE itself).

Write routes (all version-gated ≥ `MIN_VERSION_CODE` 18 = the ship floor):
`whisper_story_publish(client_id, host_url, host_object_id, receipts_enabled,
recipients jsonb, version)` — idempotent on client_id (a retry returns the live
story without re-counting quota or re-shaping the audience), atomic
(story + audience + quota in ONE transaction), bounded housekeeping inside each
publish (quota-row prune, cleanup backfill for expired stories). No
`delete_url` column exists on stories BY DESIGN: `host_object_id` is a ledger
key the sweep resolves through `whisper_image_ownership` (unique delete_url,
written by whisper-image-upload at upload time — the client never holds the raw
ImgBB object id), so a forged row can only point at a capability Supabase
itself recorded. `whisper_story_view` is receipts-gated and idempotent;
`whisper_story_react` is receipts-INDEPENDENT (poster sees reactions even when
view receipts are off — §11), enforces the whitelist + audience-only; 
`whisper_story_delete` flips state (RLS hides the story the same instant) and
re-queues hosting cleanup. Read helpers `whisper_story_can_read /
_read_receipts / _is_poster`; RLS mirrors them on every SELECT (state + expiry +
recipient snapshot + block edges re-checked per query).

### Client plane (`WhisperRepositoryStories`, Room v64)

`publishDailyUp`: preprocess → seal → upload (EncryptedBlobHost;
whisper-image-upload stamps the ownership ledger and asks ImgBB for
`expiration=86400` as defense-in-depth behind the server lifecycle) → one
sealed descriptor leg per friend → `whisper_story_publish` → self-sealed own
row → opportunistic `kickDailyUpSweep` ping. Fail-closed: every step's failure
maps through `mapDailyUpError` (vocabulary pinned by tests) and NOTHING partial
is ever exposed — a failure before the RPC leaves zero server truth. 
`syncDailyUps` refreshes via an embedded inner-join select on
whisper_story_recipients (RLS-invisible rows drop out of the join outright) and
prunes `deleteDue(nowMs, 50)` locally. Local expiry pruning runs without
network. `dailyUpsPostedToday` reads the quota row as an advisory counter —
the authoritative cap is the publish transaction. `deleteDailyUp` reads the
own copy BEFORE the server RPC (torn order would orphan the ack), then
best-effort host delete + sweep ping: the state flip already hides everything,
and whisper-story-sweep (service-role lease with SKIP LOCKED, 10-min leases,
≤ 20 attempts with backoff, retry-classes) converges the hosted copy so no
pg_cron is needed.

### UI

Chats entry row → hub (`WhisperDailyUpsScreen`): my-strip with
`+ New up` ring and Mine stats, friends' rings rendered as INITIALS (the hub
NEVER decrypts media — identity chips stay business-agnostic), quota chip,
empty state, in-place full-screen viewer overlay (no third nav route):
HorizontalPager, 7-second auto-advance paused while pressed, progress segments,
decrypt-on-demand images (2 MP display budget), one snackbar failure surface
across hub + viewer, `SecureWindow()` on both surfaces. Reactions are one
toggleable emoji per viewer per story. Mine shows receipts + reaction counts on
demand (`fetchDailyUpReceipts` — names resolved client-side).

Compose (`WhisperDailyUpsComposeScreen`): photo picker with a
12 MiB bounded read mirroring the preprocessor, in-memory draft + downsampled
preview (bytes never touch disk before the pipeline), receipts switch,
audience = all current friends with per-friend exclusion checkboxes (summary
text mirrors the final audience size), quota-aware share button with in-button
progress; retries keep the draft; success returns to the hub.

### Push

`whisper-push-send` grew a story branch: INSERT on whisper_story_recipients
(fourth INSERT-only webhook) → looks up the story poster + state → refuses to
wake devices for anything no longer `published` → data-only ping
`{whisper_story_new:"true", posterId, posterName, storyId}` — content-free
media-wise; per §11, the poster NAME only rides encrypted webpush targets —
plain targets get the `plainTargetBody()` scrub (IDs only; the client resolves
the name from the friend cache). The client renders the ping in
`WhisperPushNotifier` → `WhisperNotificationManager.showDailyUpNotification`:
deduplicated per story (a re-fanout can't double-post), silenced by the Master
Whisper toggle AND the poster's chat mute, tapping opens the Daily Ups hub via
the `OPEN_WHISPER_DAILY_UPS` intent; reaction/view events deliberately have NO
webhook (receipts are read in-app only).

### Honesty notes

The client-side capability `a` travels in the descriptor and Room rows; a viewer
who somehow obtained a descriptor with `a` still cannot delete —
whisper_story_delete authorizes posters only, and the sweep only talks to the
ownership ledger. Quota is server-enforced; a stale local read can neither
permit nor deny a publish. Received ups keep their sealed bytes; unlike 1:1
messages there is no "keep forever" tier — 24 hours is the whole contract, and
deleted/expired stories prune locally even when the sweep is unreachable.

## 15. Known limitations and calibration notes (external review, 2026-10)

Verified against code after the October 2026 external review, so the next
reviewer doesn't rediscover them. Where a number below is a choice, it is a
calibration knob with an owner (the operator), not a silent default.

1. **Ratchet gap-loss residual.** All far-future/forged-header
   `WhisperRatchet` rejects fire BEFORE any state mutation (pinned:
   `WhisperRatchetSecurityTest` — skipped-limit, forged-header-no-mutation,
   key-change-fail-closed), so a forged/missing frame never costs honest
   frames. Residual: a malicious server can still force a GAP-HANDSHAKE
   re-connection at most once per 30 s per peer (`shouldRefreshOnGap`
   cooldown) — a bounded availability annoyance, not a confidentiality or
   message-loss path. Accepted.
2. **Groups fan-out budget (600 envelopes/day/sender) — working calibration,
   not a limitation.** The SQL limiter charges each group message its fan-out
   size (members count) and stops a sender at 600 envelopes per UTC day —
   ~50 messages/day in a full 12-member group, proportionally more in small
   ones. That is deliberately generous chat-scale volume; what it curbs is the
   abnormal case (scripted 1→N spam reshaping everyone's server row), so
   1:1 messaging stays unlimited while groups stay honest. The refusal copy
   tells the user it is a DAILY limit that resets tomorrow
   (`st_Whisper_Groups_ErrQuota`). If a real group ever rubs against it, the
   change is one SQL value (`v_limit` in
   `whisper_check_group_send_allowed`, advisory-locked, 7-day retention) plus
   the mirror constant — cheap and owned by the operator.
3. **Signup gate (5 accounts/day/IP) — appropriate for the audience.** A
   friends-only messenger has no anonymous-account use case, and this is the
   only server-side Sybil brake. One real-world wrinkle to watch: carrier
   CGNAT (common for mobile-heavy audiences, e.g. Morocco) shares IPs across
   many users, so a handful of legit classmates on one carrier tower share
   slots. Recommended during the beta: watch 429 counts in the
   whisper-signup-gate function logs — a cluster of denials at one /24 is the
   signal to allow-list that carrier range (function-side list, no SQL
   change). Nothing to change before ship.
4. **Group-event signing keys and reinstalls — RESOLVED in 1.1.7
   (account-owned signer history).** The per-install ECDSA cert (Android
   Keystore; regenerated only on reinstall, the 30-day heartbeat rotates
   only the 1:1 envelope identity) used to make a group creator's OWN log
   unverifiable to their replacement device: verification checked every
   historical event against the actor's CURRENT published cert. Now each
   event row carries the signer cert that made its signature
   (`whisper_group_events.signer_cert`, written at publish time after a
   self-check against that same published key), and an archive trigger on
   `profiles.identity_binding` records every signer the account ever
   published (`whisper_group_signer_history`; served via
   `whisper-bundle-fetch` as `signer_history`). The publish-time gate
   (third-pass review) runs BEFORE any insert: it compares the local
   signer against the prekey manager's record of the last successfully
   published binding (`publishedSignerCert`, written to prefs only after
   the profiles UPDATE returns), best-effort re-publishes on mismatch
   (one `ensurePublished`, ROTv2-chained), and fails closed with a
   user-actionable error while still unpublished — so a keystore repair
   or a crashed rotation can never sign an event with an unpublished
   cert (every verifier, the author on resync included, would reject
   it). A cheap in-loop guard re-checks mid-retry signer stability.
   Verification rule:
   signature under the ROW cert, and the row cert must belong to the
   actor's published set — so the new device (new key in the same set)
   replays the full history the OLD device signed. Fail-closed edges:
   a row cert the account never published is rejected even if its
   signature would verify; rows without a signer cert (pre-1.1.7 data
   only — groups were not publicly shipped before this release) fall back
   to legacy current-cert-only; a failed own-history read degrades to
   current-only and can never ADD an unpublished key. Honest residual:
   the set-served trust boundary is the bundle endpoint's, same as 1:1 —
   an operator-level compromise can present its own keys (true before
   this change too); signatures over the full event body still prevent
   content forgeries without private keys. Pinned by
   `WhisperGroupsTest` (row cert from published history verifies after
   reinstall; foreign row cert fails closed).
   Backfill boundary (review-closed): the archive trigger starts at
   this ship — a creator who ALREADY reinstalled before 1.1.7 has lost
   the old install's signer everywhere beyond that (old prefs died
   with the install; nothing server-side recorded it), so any
   pre-existing dev/beta group from that window stays unresolvable for
   its old events. Accepted: groups were not public before this ship.
5. **External review before any "secure" marketing.** Whisper must not be
   publicly promoted as audited/secure until either a third-party security
   review or a libsignal production-parity audit happens — claimed security
   without review is exactly the kind of overstatement FOSS gets hammered for.
   Until then, user-facing copy should say end-to-end encrypted + open
   description, never "audited".

