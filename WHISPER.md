# Whisper Architecture

Friends-only, end-to-end encrypted messenger inside Toolz. Own Supabase backend,
separate from the rest of the app. This file is verified line-by-line from code;
every section names the source file.

Contents:

1. Overview and honesty notes
2. Cryptography
3. Wire formats
4. Backend
5. Client repository
6. Auth
7. UI and view models
8. Push, workers, app wiring
9. Tests
10. What the server sees
11. File inventory

## 1. Overview and honesty notes

Whisper has a hybrid identity: long-term P-256 ECDH and P-256 signing keys in
AndroidKeyStore, plus software X25519 identity, signed prekeys, and one-time
prekeys for sessions. Messages travel as v2 multi-key envelopes (static ECDH,
self-healing across key drift) or v3 Double Ratchet frames (X3DH handshake, then
per-message forward secrecy). New chats try v3 first and fall back to v2 on any
seal failure, so a message is never blocked by session problems.

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
Storage or ImgBB with deterministic per-owner keys. Push is FCM data-only
(sender and message IDs, no content). Local Room caches hold ciphertext only.

## 2. Cryptography

### 2.1 Primitives

`crypto/SessionCrypto.kt` (192 lines) is pure-software X25519 (Montgomery ladder
per RFC 7748, BigInteger, documented as not constant-time), HKDF-SHA256
(RFC 5869), HMAC-SHA256, SHA-256, and AES-256-GCM (12-byte IV, 128-bit tag,
packed as IV + ciphertext + tag). `sharedSecret` returns null on an all-zero
peer point; callers treat that as an invalid key. Covered by
`SessionCryptoVectorTest` with RFC vectors.

`data/whisper/WhisperCrypto.kt` (866 lines) is the hardware identity:

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

`data/whisper/WhisperSessionFactory.kt` (288 lines):

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

`data/whisper/session/WhisperRatchet.kt` (296 lines) implements Signal sections
1-5. Root KDF uses HKDF with info `WhisperRatchetRoot` (64 bytes split into new
root and chain key). Chain KDF uses HMAC with `0x01` for the message key and
`0x02` for the next chain key. Headers carry `{dhPub, pn, n}` and the AAD binds
header bytes plus session and participant IDs, so routing tampering fails
authentication. Out-of-order keys are kept up to `MAX_SKIPPED = 400` (oldest
evicted), consumed keys are remembered so duplicates still open, and the last
eight retired remote keys are tracked. Unrecoverable messages throw
`WhisperRatchetLostMessage`, rendered as an honest locked placeholder.

`data/whisper/session/WhisperSessionStore.kt` (296 lines) persists one session
per peer at `filesDir/whisper_sessions/<sha256(peerId)>.json` plus an in-memory
map, guarded by a per-peer mutex so two threads never advance one ratchet. Only
framing (session ID, pending header, peer identity key) is plaintext; the X3DH
key and the whole ratchet snapshot are Keystore-wrapped. A corrupt file is
deleted and treated as no session, which falls back to envelopes plus a fresh
handshake. Incoming handshakes are accepted on same-session replay or peer
identity change, otherwise the lower session ID wins so racing initiators
converge. `WhisperSessionSecretProtector.kt` (36 lines) is the wrap/unwrap seam
(Keystore in production, fake in tests).

`data/whisper/WireProtocol.kt` (146 lines) holds the pure decisions: negotiated
version never above what this build speaks, peer floor is the lowest version the
peer proved, v3 is used for live sessions or fresh contacts unless the peer
proved it cannot parse v3 or an establish failed within the last 30 seconds,
key changes classify as match / auto-rotated (server row fresh within 30
minutes, or known key older than interval minus 24h) / changed, and stale
sessions tear down only on in-window fresh rows. `WhisperProtocolConfig.kt`
(20 lines) sets live version 2 (envelope fallback), ratchet version 3, ratchet
enabled.

### 2.4 Key management

- `WhisperPrekeyManager.kt` (272): publishes the identity binding (X25519
  identity key plus P-256 signer) to `profiles`, rotates the signed prekey
  weekly, tops the one-time pool to 50 (persisted cap 120). Private halves are
  Keystore-wrapped and persisted so process death cannot break handshakes.
- `WhisperKeyTrustStore.kt` (176): TOFU anchors plus user-verified keys in
  `EncryptedSharedPreferences` (migrated once from the old plaintext file),
  durable fsync writes off the main thread.
- `WhisperKeyRotationStore.kt` (77): 30-day rotation interval with up to 6 hours
  of jitter, single source of truth shared with key-change classification.
  A 30-minute fresh-rotation window distinguishes routine rotation from
  suspicious change.
- `ProtocolDiagnostics.kt` (66): process-local ring buffer (last 60 events) plus
  counters. Never carries content, IDs, or key material. Exported only through
  the debug diagnostics button.

## 3. Wire formats

| Version | Stored in | Shape |
|---|---|---|
| v1 legacy | `messages.content` + separate `content_iv` | raw base64 ciphertext + IV column |
| v2 envelope | `messages.content`, `content_iv = NULL` | `{"v":2,"k":[{"kid","iv","ct"}], "ik"?, "x3dh"?}` |
| v3 ratchet | `messages.content`, `content_iv = "v3"` | `{"v":3,"sid","dh","pn","n","ct", "x3dh"?, "env"?}` |

`WhisperEnvelope.kt` (133 lines): key IDs are the first 8 hex chars of
SHA-256 of the base64 public key. Senders seal to every known recipient key
(pinned plus fresher server key); receivers try each. The sender public key
rides in-band as `ik` so delivery does not depend on a fresh profile row. The
X3DH header rides as a sibling `x3dh` object on the first session message.
Detection is prefix-based, so v1 and v2 rows coexist.

`session/WhisperV3Codec.kt` (125 lines): one JSON object per frame, never a
multi-key envelope. `x3dh` appears only on the first frame. `env` is a parallel
v2 copy on unproven sessions (handshake insurance); old clients ignore it and
proven sessions stop sending it.

## 4. Images and avatars

- `WhisperImageCipherTransport.kt` (183): ciphertext becomes lossless PNG pixels
  (RGB payload, opaque alpha to defeat premultiplication, forced sRGB). v2
  container is magic `WZ1`, version byte 1, CRC32, length, then cipher bytes,
  with a legacy length-only fallback. Caps: 5 MiB cipher bytes, 8 MP decode
  (4 MP on low-RAM devices), bounds pre-pass against dimension bombs.
- `WhisperEncryptedImageHost.kt` (233): uploads PNG-wrapped bytes through
  `whisper-image-upload` (bearer token, 15/45 s timeouts, one refresh-and-retry
  on 401) and returns URL plus ID. Deletes go to Supabase `whisper-avatars`
  (path-checked to the caller's own folder, rejects `..`) or to
  `whisper-image-delete` for ImgBB blobs. Downloads require `https://`, strip
  query and fragment, allowlist `i.ibb.co`, `ibb.co`, and the project's
  `whisper-avatars` storage path (one redirect followed), cap 7 MiB, and map
  403/404 to an expired-image signal.
- `WhisperAvatarCodec.kt` (58): per-owner key from
  `HKDF(SHA-256(ownerPub + ":whisper-avatar-v1"), info "whisper-avatar-key")`,
  owner key as AAD. Anyone holding the profile row can derive it; the host sees
  ciphertext only. Fails closed on mismatch.
- `WhisperAvatarLoader.kt` (142): memory LRU plus in-flight dedupe plus sealed
  disk cache under `filesDir/whisper_avatars` (SHA-256 of URL, max 64 entries),
  with PNG and double-wrap healing and a generation guard so sign-out cannot
  repopulate the cache mid-flight.

Chat images are downscaled to 1920 px max dimension at JPEG quality 82 before
sealing (`WhisperChatViewModel.compressImageForUpload`). Avatars are cropped to
256 px (`WhisperRepository.AVATAR_SIDE_PX`).

## 5. Backend

`supabase/migrations/main-whisper-sql.sql` (1417 lines) is the canonical single
baseline, squashing 14 migrations (policy in `whisper-sql-info.md`). Tables:
`messages` (with `content_hash` replay guard and block-aware insert guard),
`message_reactions`, `friendships`/`friends` (pending-only transitions,
block-aware), `profiles` (identity binding, public key, last seen, avatar),
`whisper_blocks`, `whisper_prekeys` (account, key ID, kind, public key,
signature), `whisper_fcm_tokens` (user ID primary key), `whisper_typing_signals`
(8-second freshness), upload/discover/bypass/destructive quotas, deleted
tombstones, image ownership, and the `whisper_public_profiles` view. Includes
the discover, bypass-attempt, quota-refund, and account-purge RPCs, owner-scoped
RLS, realtime publication, and full replica identity. Idempotent, safe to rerun.

Edge functions in `supabase/functions/whisper-*/index.ts`:

| Function | Lines | Role |
|---|---|---|
| `whisper-bundle-fetch` | 114 | POST `{account}` returns identity binding, latest signed SPK, and consumes one OPK. Rejects self-bundles, no-store. |
| `whisper-bypass-verify` | 151 | Constant-time screenshot-bypass password check, 5 failures per 15 minutes locks out with 429. |
| `whisper-delete-account` | 243 | Verifies JWT plus password header plus 5-minute confirmation timestamp, wipes server data before deleting the GoTrue user. |
| `whisper-image-upload` | 252 | Accepts base64 PNG with the ImgBB key held server-side, returns URL and ID. |
| `whisper-image-delete` | 164 | Deletes an ImgBB blob by ID. |
| `whisper-push-send` | 299 | Database webhook on message and friend inserts. Skips receivers seen within 60 seconds, sends one data-only FCM message per token, prunes dead tokens. Never logs tokens or payloads. |

`data/whisper/EdgeFunctionClient.kt` (127) is the single hardened transport
(Ktor over OkHttp, anonymous/user/explicit-token bearers, mandatory timeouts, no
body logging). `di/SupabaseModule.kt` falls back to an invalid URL when the
build has no Supabase credentials, keeping Whisper offline with one log line.

## 6. Client repository

`data/whisper/WhisperRepository.kt` (3129 lines) orchestrates everything.
Constants: 8,192-char message cap, JPEG/PNG/WebP images, disappearing images
from 60 seconds to 180 days, event dedupe TTL 30 seconds (max 1024 IDs), up to
6 realtime resubscribes at least 2.5 seconds apart, 8-second typing freshness,
256 px avatars, 5-minute profile cache.

- Health: republishes the local key when the server row diverges (repairing a
  broken identity first), retries prekey publication on failure.
- Send: tries the ratchet under the per-peer mutex (establishing the session on
  a miss), otherwise builds a multi-key envelope to every candidate key. Inserts
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
- Social (`WhisperRepositorySocial.kt`, 467) and profile
  (`WhisperRepositoryProfile.kt`, 398) splits cover friend requests, blocks
  (client plus DB enforcement), discover paging with quota, hide/mute, typing
  broadcast, profile CRUD, avatar upload/delete under
  `whisper-avatars/<userId>/`, and QR key verification.
- Deletes and reactions: delete-for-everyone writes a
  `[deleted_by_sender:<name>]` tombstone and deletes the hosted image,
  delete-for-me stays device-local, clear-chat supports 24h/7d/30d/all/custom
  with 30-second undo, reactions send delta toggles reconciled by authoritative
  snapshots, and the outbox flushes FIFO (duplicate keys count as delivered,
  exhausted attempts go to the drop ledger).

Supporting stores, all read in full: access-file backup and restore
(`WhisperAubupManager`, 526), device-only delete-for-me with 24-hour eviction
(`WhisperDeletedMessagesStore`, 167), auto-returning hidden chats
(`WhisperHiddenChatsStore`, 99), mute with expiry (`WhisperMutePreferences`,
129), 30-second clear-chat undo (`WhisperUndoBufferStore`, 104), grouped
deduplicated suppressed notifications (`WhisperNotificationManager`, 360),
permanent-vs-transient error mapping for WorkManager (`WhisperErrorMapper`,
286), sealed PNG disk cache (`WhisperImageDiskCache`, 228), local tombstone
entity (59), message entity with numeric sort epoch and protocol inference
(127), message DAO capped at the latest 500 rows (56), ciphertext-only Room
outbox FIFO capped at 100 oldest-kept with a 200-entry drop ledger plus legacy
prefs migration (93 + 146), and the immediate network-constrained delivery
scheduler with 15-second exponential backoff (42).

## 7. Auth

`data/whisper/WhisperAuthManager.kt` (307 lines):

- Username path: 3-20 lowercase letters/digits/underscores, password of at least
  10 chars, display name 1-60 chars, mapped to `user@u.whisper.local` through
  GoTrue email auth (confirmation off, immediate session, one retry on transient
  post-signup failure).
- Token path: 32 random bytes as 64 hex. Email is `SHA-256(token)` at
  `whisper.toolz.app`, password is `SHA-256("pwd_" + token)`. Login tries four
  frozen historical derivations (current 256-bit first, three legacy 32-bit
  variants) with 500 ms pacing. Tokens are normalized (non-hex stripped,
  lowercased) and validated as 64 hex. Credential errors are detected
  structurally, not by bare status code.
- Extras: restore from the Toolz password vault or an encrypted access file
  plus whisper code, clipboard auto-expiry for displayed tokens, username
  availability check, sign-out, and account deletion (re-auth for password
  accounts, edge deletion with password header plus confirmation timestamp,
  then local wipe of sessions, Room, trust, outbox, avatars, and FCM token).

`ui/screens/whisper/WhisperAuthViewModel.kt` (601) drives these flows with
clipboard expiry, access-file import, and frozen candidate ordering.

## 8. UI and view models

All 18 files under `ui/screens/whisper/`:

- `WhisperViewModel.kt` (910): hub state (conversations, friends, single-source
  incoming requests, outgoing requests, search, recommended and paged discover,
  mutes, error plus info channel), conversation hide and history clear, block
  and mute toggles, profile update, avatar upload and delete, access-file
  creation, 30-day key rotation heartbeat, account deletion and sign-out with
  full wipe, screenshot-bypass flag, beta and onboarding flags.
- `WhisperChatViewModel.kt` (1465): per-chat state (message flow capped at 500,
  friend and block status, typing, online state, last seen, mute, reply target,
  search matches, key trust, upload state, decrypted image bytes, realtime
  status, errors) plus an isolated 30-second undo state that does not recompose
  the list. Operations include message load and paging, optimistic send, image
  pick/compress/send, encrypted image load through the allowlist and caches,
  reactions, both delete paths, clear-chat with undo, mute and block, friend
  actions, QR key verification, drafts, realtime reconnect, and visibility
  pausing. History remains readable offline.
- Screens: hub with chats/friends/requests/discover/profile tabs, unread badges,
  presence dots, key-change banners, and pull-refresh (`WhisperMainScreen`,
  1945); row cards (`WhisperMainScreenComponents`, 376); full chat with bubbles,
  reply snippets, reactions, in-message search, typing indicator, online header,
  mute/block menus, expiring attachments, tombstone and locked placeholders,
  and the undo bar (`WhisperChatScreen`, 2563); self profile with editing,
  avatar picker, fingerprints, rotation, access file, sign-out, deletion, and a
  debug diagnostics export (`WhisperProfileTab`, 1199); peer profile with QR
  verification and block/unfriend (562 + 232 + 522); friends-only onboarding
  explainer (629); username/password plus token auth UI with vault restore,
  access-file import, and token-loss warnings (1888); re-auth banner (155);
  toasts (219); FLAG_SECURE window control (76) with a server-gated screenshot
  bypass (116); and the bypass verifier mapping Granted/Denied/RateLimited/
  Unavailable with fail-closed behavior (125).

## 9. Push, workers, app wiring

- `push/WhisperPushService.kt` (142): new FCM tokens upsert into
  `whisper_fcm_tokens` (user ID primary key), parked locally in
  `whisper_push_pending` when they arrive before login and retried on next
  launch. Incoming data messages show generic content-free notifications keyed
  by sender and message ID for dedupe.
- `ToolzApplication` schedules local tombstone cleanup every 24 hours and
  encrypted outbox delivery every 15 minutes, plus immediate delivery on send.
  Delivery retries transient failures with backoff up to five WorkManager
  attempts; permanent rejections end as success because the drop ledger already
  handled them. `MainActivity` retries parked push tokens, injects the avatar
  loader, and owns the `whisper-auth://login` deep link alongside the generic
  share intents. The manifest declares that deep link and the FCM entry point.

## 10. Tests

Handshake math and SessionCrypto vectors (RFC 7748 sections 5.2 and 6.1, the
`WhisperX3DH-v1` HKDF framing, AES-GCM round trip), model invariants (names,
avatars, pending/sent/read ordering, image prefix, tombstones, snake-case
serialization), chat reaction merging, bypass verdicts (blank and oversize
inputs), image transport (v2 magic, legacy fallback, CRC rejection, pixel cap),
and crypto AAD (direction binding, fingerprints, null safety).

## 11. What the server sees

Ciphertext and IV markers, sender and receiver IDs, timestamps, profile fields
(username, display name, bio, avatar URL), the friend and block graph, last-seen
presence, typing signals, reaction rows, FCM tokens, prekey material and
signatures, and image URLs with IDs. Usernames reveal who talks to whom;
message and image bytes stay opaque. FCM sees sender/message IDs and friend
pings. ImgBB sees opaque PNGs with expiry. Passwords travel only to GoTrue over
TLS at auth and deletion time; the bypass password goes only to the edge secret
check.

## 12. File inventory

Client `data/whisper` (33 files): EdgeFunctionClient 127, ProtocolDiagnostics
66, WhisperAubupManager 526, WhisperAuthManager 307, WhisperAvatarCodec 58,
WhisperAvatarLoader 142, WhisperCrypto 866, WhisperDeletedMessagesStore 167,
WhisperDeliveryScheduler 42, WhisperEncryptedImageHost 233, WhisperEnvelope 133,
WhisperErrorMapper 286, WhisperHiddenChatsStore 99, WhisperImageCipherTransport
183, WhisperImageDiskCache 228, WhisperKeyRotationStore 77, WhisperKeyTrustStore
176, WhisperLocalTombstoneEntity 59, WhisperMessageDao 56, WhisperMessageEntity
127, WhisperModels 540, WhisperMutePreferences 129, WhisperNotificationManager
360, WhisperOutboxEntity 93, WhisperOutgoingQueue 146, WhisperPrekeyManager 272,
WhisperProtocolConfig 20, WhisperRepository 3129, WhisperRepositoryProfile 398,
WhisperRepositorySocial 467, WhisperSessionFactory 288, WhisperUndoBufferStore
104, WireProtocol 146.

Session subdir (4 files): WhisperRatchet 296, WhisperSessionSecretProtector 36,
WhisperSessionStore 296, WhisperV3Codec 125.

Also: `crypto/SessionCrypto` 192, `push/WhisperPushService` 142, UI screens
(18 files from 76 to 2563 lines, see section 8), workers for delivery (49),
local cleanup (43), and token clipboard expiry (151), six edge functions
(114/151/243/164/252/299 lines), and the 1417-line SQL baseline.
# Whisper Architecture

Friends-only, end-to-end encrypted messenger inside Toolz. Own Supabase backend,
separate from the rest of the app. This file is verified line-by-line from code;
every section names the source file.

Contents:

1. Overview and honesty notes
2. Cryptography
3. Wire formats
4. Backend
5. Client repository
6. Auth
7. UI and view models
8. Push, workers, app wiring
9. Tests
10. What the server sees
11. File inventory

## 1. Overview and honesty notes

Whisper has a hybrid identity: long-term P-256 ECDH and P-256 signing keys in
AndroidKeyStore, plus software X25519 identity, signed prekeys, and one-time
prekeys for sessions. Messages travel as v2 multi-key envelopes (static ECDH,
self-healing across key drift) or v3 Double Ratchet frames (X3DH handshake, then
per-message forward secrecy). New chats try v3 first and fall back to v2 on any
seal failure, so a message is never blocked by session problems.

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
Storage or ImgBB with deterministic per-owner keys. Push is FCM data-only
(sender and message IDs, no content). Local Room caches hold ciphertext only.

## 2. Cryptography

### 2.1 Primitives

`crypto/SessionCrypto.kt` (192 lines) is pure-software X25519 (Montgomery ladder
per RFC 7748, BigInteger, documented as not constant-time), HKDF-SHA256
(RFC 5869), HMAC-SHA256, SHA-256, and AES-256-GCM (12-byte IV, 128-bit tag,
packed as IV + ciphertext + tag). `sharedSecret` returns null on an all-zero
peer point; callers treat that as an invalid key. Covered by
`SessionCryptoVectorTest` with RFC vectors.

`data/whisper/WhisperCrypto.kt` (866 lines) is the hardware identity:

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

`data/whisper/WhisperSessionFactory.kt` (288 lines):

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

`data/whisper/session/WhisperRatchet.kt` (296 lines) implements Signal sections
1-5. Root KDF uses HKDF with info `WhisperRatchetRoot` (64 bytes split into new
root and chain key). Chain KDF uses HMAC with `0x01` for the message key and
`0x02` for the next chain key. Headers carry `{dhPub, pn, n}` and the AAD binds
header bytes plus session and participant IDs, so routing tampering fails
authentication. Out-of-order keys are kept up to `MAX_SKIPPED = 400` (oldest
evicted), consumed keys are remembered so duplicates still open, and the last
eight retired remote keys are tracked. Unrecoverable messages throw
`WhisperRatchetLostMessage`, rendered as an honest locked placeholder.

`data/whisper/session/WhisperSessionStore.kt` (296 lines) persists one session
per peer at `filesDir/whisper_sessions/<sha256(peerId)>.json` plus an in-memory
map, guarded by a per-peer mutex so two threads never advance one ratchet. Only
framing (session ID, pending header, peer identity key) is plaintext; the X3DH
key and the whole ratchet snapshot are Keystore-wrapped. A corrupt file is
deleted and treated as no session, which falls back to envelopes plus a fresh
handshake. Incoming handshakes are accepted on same-session replay or peer
identity change, otherwise the lower session ID wins so racing initiators
converge. `WhisperSessionSecretProtector.kt` (36 lines) is the wrap/unwrap seam
(Keystore in production, fake in tests).

`data/whisper/WireProtocol.kt` (146 lines) holds the pure decisions: negotiated
version never above what this build speaks, peer floor is the lowest version the
peer proved, v3 is used for live sessions or fresh contacts unless the peer
proved it cannot parse v3 or an establish failed within the last 30 seconds,
key changes classify as match / auto-rotated (server row fresh within 30
minutes, or known key older than interval minus 24h) / changed, and stale
sessions tear down only on in-window fresh rows. `WhisperProtocolConfig.kt`
(20 lines) sets live version 2 (envelope fallback), ratchet version 3, ratchet
enabled.

### 2.4 Key management

- `WhisperPrekeyManager.kt` (272): publishes the identity binding (X25519
  identity key plus P-256 signer) to `profiles`, rotates the signed prekey
  weekly, tops the one-time pool to 50 (persisted cap 120). Private halves are
  Keystore-wrapped and persisted so process death cannot break handshakes.
- `WhisperKeyTrustStore.kt` (176): TOFU anchors plus user-verified keys in
  `EncryptedSharedPreferences` (migrated once from the old plaintext file),
  durable fsync writes off the main thread.
- `WhisperKeyRotationStore.kt` (77): 30-day rotation interval with up to 6 hours
  of jitter, single source of truth shared with key-change classification.
  A 30-minute fresh-rotation window distinguishes routine rotation from
  suspicious change.
- `ProtocolDiagnostics.kt` (66): process-local ring buffer (last 60 events) plus
  counters. Never carries content, IDs, or key material. Exported only through
  the debug diagnostics button.

## 3. Wire formats

| Version | Stored in | Shape |
|---|---|---|
| v1 legacy | `messages.content` + separate `content_iv` | raw base64 ciphertext + IV column |
| v2 envelope | `messages.content`, `content_iv = NULL` | `{"v":2,"k":[{"kid","iv","ct"}], "ik"?, "x3dh"?}` |
| v3 ratchet | `messages.content`, `content_iv = "v3"` | `{"v":3,"sid","dh","pn","n","ct", "x3dh"?, "env"?}` |

`WhisperEnvelope.kt` (133 lines): key IDs are the first 8 hex chars of
SHA-256 of the base64 public key. Senders seal to every known recipient key
(pinned plus fresher server key); receivers try each. The sender public key
rides in-band as `ik` so delivery does not depend on a fresh profile row. The
X3DH header rides as a sibling `x3dh` object on the first session message.
Detection is prefix-based, so v1 and v2 rows coexist.

`session/WhisperV3Codec.kt` (125 lines): one JSON object per frame, never a
multi-key envelope. `x3dh` appears only on the first frame. `env` is a parallel
v2 copy on unproven sessions (handshake insurance); old clients ignore it and
proven sessions stop sending it.

## 4. Images and avatars

- `WhisperImageCipherTransport.kt` (183): ciphertext becomes lossless PNG pixels
  (RGB payload, opaque alpha to defeat premultiplication, forced sRGB). v2
  container is magic `WZ1`, version byte 1, CRC32, length, then cipher bytes,
  with a legacy length-only fallback. Caps: 5 MiB cipher bytes, 8 MP decode
  (4 MP on low-RAM devices), bounds pre-pass against dimension bombs.
- `WhisperEncryptedImageHost.kt` (233): uploads PNG-wrapped bytes through
  `whisper-image-upload` (bearer token, 15/45 s timeouts, one refresh-and-retry
  on 401) and returns URL plus ID. Deletes go to Supabase `whisper-avatars`
  (path-checked to the caller's own folder, rejects `..`) or to
  `whisper-image-delete` for ImgBB blobs. Downloads require `https://`, strip
  query and fragment, allowlist `i.ibb.co`, `ibb.co`, and the project's
  `whisper-avatars` storage path (one redirect followed), cap 7 MiB, and map
  403/404 to an expired-image signal.
- `WhisperAvatarCodec.kt` (58): per-owner key from
  `HKDF(SHA-256(ownerPub + ":whisper-avatar-v1"), info "whisper-avatar-key")`,
  owner key as AAD. Anyone holding the profile row can derive it; the host sees
  ciphertext only. Fails closed on mismatch.
- `WhisperAvatarLoader.kt` (142): memory LRU plus in-flight dedupe plus sealed
  disk cache under `filesDir/whisper_avatars` (SHA-256 of URL, max 64 entries),
  with PNG and double-wrap healing and a generation guard so sign-out cannot
  repopulate the cache mid-flight.

Chat images are downscaled to 1920 px max dimension at JPEG quality 82 before
sealing (`WhisperChatViewModel.compressImageForUpload`). Avatars are cropped to
256 px (`WhisperRepository.AVATAR_SIDE_PX`).

## 5. Backend

`supabase/migrations/main-whisper-sql.sql` (1417 lines) is the canonical single
baseline, squashing 14 migrations (policy in `whisper-sql-info.md`). Tables:
`messages` (with `content_hash` replay guard and block-aware insert guard),
`message_reactions`, `friendships`/`friends` (pending-only transitions,
block-aware), `profiles` (identity binding, public key, last seen, avatar),
`whisper_blocks`, `whisper_prekeys` (account, key ID, kind, public key,
signature), `whisper_fcm_tokens` (user ID primary key), `whisper_typing_signals`
(8-second freshness), upload/discover/bypass/destructive quotas, deleted
tombstones, image ownership, and the `whisper_public_profiles` view. Includes
the discover, bypass-attempt, quota-refund, and account-purge RPCs, owner-scoped
RLS, realtime publication, and full replica identity. Idempotent, safe to rerun.

Edge functions in `supabase/functions/whisper-*/index.ts`:

| Function | Lines | Role |
|---|---|---|
| `whisper-bundle-fetch` | 114 | POST `{account}` returns identity binding, latest signed SPK, and consumes one OPK. Rejects self-bundles, no-store. |
| `whisper-bypass-verify` | 151 | Constant-time screenshot-bypass password check, 5 failures per 15 minutes locks out with 429. |
| `whisper-delete-account` | 243 | Verifies JWT plus password header plus 5-minute confirmation timestamp, wipes server data before deleting the GoTrue user. |
| `whisper-image-upload` | 252 | Accepts base64 PNG with the ImgBB key held server-side, returns URL and ID. |
| `whisper-image-delete` | 164 | Deletes an ImgBB blob by ID. |
| `whisper-push-send` | 299 | Database webhook on message and friend inserts. Skips receivers seen within 60 seconds, sends one data-only FCM message per token, prunes dead tokens. Never logs tokens or payloads. |

`data/whisper/EdgeFunctionClient.kt` (127) is the single hardened transport
(Ktor over OkHttp, anonymous/user/explicit-token bearers, mandatory timeouts, no
body logging). `di/SupabaseModule.kt` falls back to an invalid URL when the
build has no Supabase credentials, keeping Whisper offline with one log line.

## 6. Client repository

`data/whisper/WhisperRepository.kt` (3129 lines) orchestrates everything.
Constants: 8,192-char message cap, JPEG/PNG/WebP images, disappearing images
from 60 seconds to 180 days, event dedupe TTL 30 seconds (max 1024 IDs), up to
6 realtime resubscribes at least 2.5 seconds apart, 8-second typing freshness,
256 px avatars, 5-minute profile cache.

- Health: republishes the local key when the server row diverges (repairing a
  broken identity first), retries prekey publication on failure.
- Send: tries the ratchet under the per-peer mutex (establishing the session on
  a miss), otherwise builds a multi-key envelope to every candidate key. Inserts
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
- Social (`WhisperRepositorySocial.kt`, 467) and profile
  (`WhisperRepositoryProfile.kt`, 398) splits cover friend requests, blocks
  (client plus DB enforcement), discover paging with quota, hide/mute, typing
  broadcast, profile CRUD, avatar upload/delete under
  `whisper-avatars/<userId>/`, and QR key verification.
- Deletes and reactions: delete-for-everyone writes a
  `[deleted_by_sender:<name>]` tombstone and deletes the hosted image,
  delete-for-me stays device-local, clear-chat supports 24h/7d/30d/all/custom
  with 30-second undo, reactions send delta toggles reconciled by authoritative
  snapshots, and the outbox flushes FIFO (duplicate keys count as delivered,
  exhausted attempts go to the drop ledger).

Supporting stores, all read in full: access-file backup and restore
(`WhisperAubupManager`, 526), device-only delete-for-me with 24-hour eviction
(`WhisperDeletedMessagesStore`, 167), auto-returning hidden chats
(`WhisperHiddenChatsStore`, 99), mute with expiry (`WhisperMutePreferences`,
129), 30-second clear-chat undo (`WhisperUndoBufferStore`, 104), grouped
deduplicated suppressed notifications (`WhisperNotificationManager`, 360),
permanent-vs-transient error mapping for WorkManager (`WhisperErrorMapper`,
286), sealed PNG disk cache (`WhisperImageDiskCache`, 228), local tombstone
entity (59), message entity with numeric sort epoch and protocol inference
(127), message DAO capped at the latest 500 rows (56), ciphertext-only Room
outbox FIFO capped at 100 oldest-kept with a 200-entry drop ledger plus legacy
prefs migration (93 + 146), and the immediate network-constrained delivery
scheduler with 15-second exponential backoff (42).

## 7. Auth

`data/whisper/WhisperAuthManager.kt` (307 lines):

- Username path: 3-20 lowercase letters/digits/underscores, password of at least
  10 chars, display name 1-60 chars, mapped to `user@u.whisper.local` through
  GoTrue email auth (confirmation off, immediate session, one retry on transient
  post-signup failure).
- Token path: 32 random bytes as 64 hex. Email is `SHA-256(token)` at
  `whisper.toolz.app`, password is `SHA-256("pwd_" + token)`. Login tries four
  frozen historical derivations (current 256-bit first, three legacy 32-bit
  variants) with 500 ms pacing. Tokens are normalized (non-hex stripped,
  lowercased) and validated as 64 hex. Credential errors are detected
  structurally, not by bare status code.
- Extras: restore from the Toolz password vault or an encrypted access file
  plus whisper code, clipboard auto-expiry for displayed tokens, username
  availability check, sign-out, and account deletion (re-auth for password
  accounts, edge deletion with password header plus confirmation timestamp,
  then local wipe of sessions, Room, trust, outbox, avatars, and FCM token).

`ui/screens/whisper/WhisperAuthViewModel.kt` (601) drives these flows with
clipboard expiry, access-file import, and frozen candidate ordering.

## 8. UI and view models

All 18 files under `ui/screens/whisper/`:

- `WhisperViewModel.kt` (910): hub state (conversations, friends, single-source
  incoming requests, outgoing requests, search, recommended and paged discover,
  mutes, error plus info channel), conversation hide and history clear, block
  and mute toggles, profile update, avatar upload and delete, access-file
  creation, 30-day key rotation heartbeat, account deletion and sign-out with
  full wipe, screenshot-bypass flag, beta and onboarding flags.
- `WhisperChatViewModel.kt` (1465): per-chat state (message flow capped at 500,
  friend and block status, typing, online state, last seen, mute, reply target,
  search matches, key trust, upload state, decrypted image bytes, realtime
  status, errors) plus an isolated 30-second undo state that does not recompose
  the list. Operations include message load and paging, optimistic send, image
  pick/compress/send, encrypted image load through the allowlist and caches,
  reactions, both delete paths, clear-chat with undo, mute and block, friend
  actions, QR key verification, drafts, realtime reconnect, and visibility
  pausing. History remains readable offline.
- Screens: hub with chats/friends/requests/discover/profile tabs, unread badges,
  presence dots, key-change banners, and pull-refresh (`WhisperMainScreen`,
  1945); row cards (`WhisperMainScreenComponents`, 376); full chat with bubbles,
  reply snippets, reactions, in-message search, typing indicator, online header,
  mute/block menus, expiring attachments, tombstone and locked placeholders,
  and the undo bar (`WhisperChatScreen`, 2563); self profile with editing,
  avatar picker, fingerprints, rotation, access file, sign-out, deletion, and a
  debug diagnostics export (`WhisperProfileTab`, 1199); peer profile with QR
  verification and block/unfriend (562 + 232 + 522); friends-only onboarding
  explainer (629); username/password plus token auth UI with vault restore,
  access-file import, and token-loss warnings (1888); re-auth banner (155);
  toasts (219); FLAG_SECURE window control (76) with a server-gated screenshot
  bypass (116); and the bypass verifier mapping Granted/Denied/RateLimited/
  Unavailable with fail-closed behavior (125).

## 9. Push, workers, app wiring

- `push/WhisperPushService.kt` (142): new FCM tokens upsert into
  `whisper_fcm_tokens` (user ID primary key), parked locally in
  `whisper_push_pending` when they arrive before login and retried on next
  launch. Incoming data messages show generic content-free notifications keyed
  by sender and message ID for dedupe.
- `ToolzApplication` schedules local tombstone cleanup every 24 hours and
  encrypted outbox delivery every 15 minutes, plus immediate delivery on send.
  Delivery retries transient failures with backoff up to five WorkManager
  attempts; permanent rejections end as success because the drop ledger already
  handled them. `MainActivity` retries parked push tokens, injects the avatar
  loader, and owns the `whisper-auth://login` deep link alongside the generic
  share intents. The manifest declares that deep link and the FCM entry point.

## 10. Tests

Handshake math and SessionCrypto vectors (RFC 7748 sections 5.2 and 6.1, the
`WhisperX3DH-v1` HKDF framing, AES-GCM round trip), model invariants (names,
avatars, pending/sent/read ordering, image prefix, tombstones, snake-case
serialization), chat reaction merging, bypass verdicts (blank and oversize
inputs), image transport (v2 magic, legacy fallback, CRC rejection, pixel cap),
and crypto AAD (direction binding, fingerprints, null safety).

## 11. What the server sees

Ciphertext and IV markers, sender and receiver IDs, timestamps, profile fields
(username, display name, bio, avatar URL), the friend and block graph, last-seen
presence, typing signals, reaction rows, FCM tokens, prekey material and
signatures, and image URLs with IDs. Usernames reveal who talks to whom;
message and image bytes stay opaque. FCM sees sender/message IDs and friend
pings. ImgBB sees opaque PNGs with expiry. Passwords travel only to GoTrue over
TLS at auth and deletion time; the bypass password goes only to the edge secret
check.

## 12. File inventory

Client `data/whisper` (33 files): EdgeFunctionClient 127, ProtocolDiagnostics
66, WhisperAubupManager 526, WhisperAuthManager 307, WhisperAvatarCodec 58,
WhisperAvatarLoader 142, WhisperCrypto 866, WhisperDeletedMessagesStore 167,
WhisperDeliveryScheduler 42, WhisperEncryptedImageHost 233, WhisperEnvelope 133,
WhisperErrorMapper 286, WhisperHiddenChatsStore 99, WhisperImageCipherTransport
183, WhisperImageDiskCache 228, WhisperKeyRotationStore 77, WhisperKeyTrustStore
176, WhisperLocalTombstoneEntity 59, WhisperMessageDao 56, WhisperMessageEntity
127, WhisperModels 540, WhisperMutePreferences 129, WhisperNotificationManager
360, WhisperOutboxEntity 93, WhisperOutgoingQueue 146, WhisperPrekeyManager 272,
WhisperProtocolConfig 20, WhisperRepository 3129, WhisperRepositoryProfile 398,
WhisperRepositorySocial 467, WhisperSessionFactory 288, WhisperUndoBufferStore
104, WireProtocol 146.

Session subdir (4 files): WhisperRatchet 296, WhisperSessionSecretProtector 36,
WhisperSessionStore 296, WhisperV3Codec 125.

Also: `crypto/SessionCrypto` 192, `push/WhisperPushService` 142, UI screens
(18 files from 76 to 2563 lines, see section 8), workers for delivery (49),
local cleanup (43), and token clipboard expiry (151), six edge functions
(114/151/243/164/252/299 lines), and the 1417-line SQL baseline.
