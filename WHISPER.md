# Whisper — Architecture & Design Guide

Whisper is the private-messenger module inside Toolz: a friends-only,
end-to-end encrypted chat with groups and 24-hour photo stories, running on
its own Supabase backend, fully decoupled from the rest of the app. This
document explains how the whole system works — the cryptography, the server,
the client, and the user experience — well enough that a reader who has never
seen the codebase can reason about it.

**How to read this document.** It describes the codebase as of the 1.1.7
release (October 2026), including the six client-side hardening rounds that
followed the initial release cut (§6, §9, §11.7, §12, §13 — October 2026).
It also records, without softening, the October 2026 field report that 1:1
messages could render the locked placeholder for every row, the three
independent code paths that produced it, and the fix that landed for each
(§3.9, §14).
The working tree is the source of truth; this document is not
machine-checked against code, so treat any specific constant or file name as a
pointer to verify, not a guarantee. Sections about cryptography name exact
primitives and constants on purpose; "server" means the Supabase project
Whisper operates (database, Row Level Security policies, trigger functions,
and hosted edge functions).

Contents:

1. What Whisper is
2. The privacy model: what the server can and cannot see
3. Cryptography
4. The life of a message (end-to-end walkthrough)
5. Images and avatars
6. The client architecture
7. Accounts and authentication
8. The backend
9. Push notifications
10. Background work and app wiring
11. Groups
12. Daily Ups
13. How it is verified
14. Limits, trade-offs, and the honest security statement

Appendix A — file map
Appendix B — glossary

---

## 1. What Whisper is

### 1.1 The product

Whisper is invite-only in the strict sense: you can only talk to people who
have accepted a friend request. There is no public directory, no phone-number
lookup, and no pre-existing identity to import. An account is either a
**username + password** or an opaque **whisper token** (a random 64-hex string
that *is* the credential — lose it without the access-file or vault backup
and the account cannot be logged into again).

Feature surface at 1.1.7:

- **1:1 chat** — text, images (with one-minute-to-180-day disappearing
  timers), replies, reactions, read receipts, typing and presence, in-chat
  search, clear-chat with undo, per-chat muting, blocking. Every message is
  end-to-end encrypted on top of a Signal-style session protocol.
- **Groups** *(Hidden from UI in this update)* — up to 12 members with owner/admin/member roles. Group history
  is an admin-**signed event log** that every client verifies independently;
  membership changes, polls, mentions, invites, and permission changes are all
  events in that log. Messages fan out pairwise: every member's copy is sealed
  through *their own* 1:1-quality session with the sender. (Note: Groups are
  currently not accessible in the UI; all backend and files remain preserved).
- **Daily Ups** *(Hidden from UI in this update)* — 24-hour photo stories posted to all friends at once, capped
  at 5 per UTC day per poster, with optional view receipts and a six-emoji
  reaction vocabulary. Photos are stripped of GPS/EXIF metadata before they
  ever leave the device. (Note: Daily Ups are currently not accessible in the
  UI; all backend and files remain preserved).
- **Cross-cutting** — offline-first (history reads from a local encrypted
  store; sends queue and drain), push notifications whose payloads never carry
  message content, screenshot protection with a server-gated bypass for
  accessibility cases, QR/safety-number key verification, account deletion
  that wipes the server and the device, and an access-file backup for
  credentials (never key material).

### 1.2 Design principles

These principles explain most specific decisions later in the document.

1. **The server is untrusted for confidentiality — and that means two
   adversaries.** A *malicious client* is fenced with Row Level Security and
   triggers (server-enforced invariants a foreign user or modified app
   cannot bypass). A *malicious operator* bypasses RLS by definition — they
   run the database — so the defense there is cryptography alone: sealed
   payloads and client-verified signatures the operator cannot forge (§2.1).
   The Postgres database is allowed to store *ciphertext, user IDs,
   timestamps, and friendship edges* — nothing more that could reveal
   content; a rule or client bug can leak presence and topology, never
   plaintext.
2. **Fail closed everywhere.** When verification is ambiguous — a bad
   signature, an unknown key, an unparseable event, a missing one-time prekey —
   the system refuses the action rather than improvising a downgrade. The one
   deliberate escape hatch (the legacy v2 envelope fallback for chats where a
   ratchet session cannot be established) is described precisely in §3.2, and
   blocked after repeated failures.
3. **Honest degradation over silent loss.** When a ratchet loses part of its
   history (a peer is offline longer than the out-of-order window allows), the
   message renders as an explicit locked placeholder, not as garbage or a
   crash; a group whose event log cannot be fully verified is flagged
   "degraded" in both the chat and its info screen.
4. **Ciphertext on disk.** The local Room database holds ciphertext only;
   decryption happens per render or on demand. The ratchet snapshots that
   necessarily sit on disk are wrapped by hardware-keyed Keystore AES.
5. **The server sees topology, never content.** Notifications are wake pings;
   name keys are stripped on any third-party hop; image hosts receive opaque
   PNGs. §2 spells out exactly what metadata remains visible.

---

## 2. The privacy model: what the server can and cannot see

This is the section to internalize first. Everything else in the design
follows from this split.

### 2.1 The adversaries the design assumes

| Adversary | What the design denies them |
|---|---|
| **The backend operator** | Message/poll/story content and keys. The operator can always bypass RLS (they run the database), so the defense against them is cryptography, not policies — sealed payloads and client-verified signatures they cannot forge (§11.1). Two honest residuals the operator *can* do: (a) they serve profiles and key bundles, so for a pair that has never verified safety numbers, first contact is TOFU-pinned *to what the operator serves* — only safety-number verification removes the operator from that path (§3.6); (b) they can withhold, delete, or truncate server rows undetectably — e.g. hide the tail of a group event log so a sender keeps sealing to a removed member. Censoring is a residual, not a confidentiality break (§14.4). |
| **A network adversary** | Everything on every hop: DB connections run over TLS, edge calls over HTTPS, and push payloads are either Web-Push-encrypted or name-scrubbed to IDs (§9). |
| **A malicious friend or group member** | A legitimate protocol participant who can read their own copies, screenshot them, and forward them — that is inherent and this design does not pretend otherwise (screenshots are a product surface, §11.7, not a security boundary). What they cannot do is open anyone else's legs (per-member keys), forge group events (signatures), or resurrect content the poster deleted. |
| **A thief with the device** | Nothing, if the device is unlocked and the app open — the app state *is* the plaintext window. Honest consequence: the ratchet's forward secrecy does not undo already-received traffic from a device compromise, because out-of-order tolerance deliberately persists consumed message keys (§3.4). Keystore-held keys are generated in the TEE and never exportable in plaintext — but the wrapping key has no user-authentication gate, so code running in the app's context while the device is unlocked can unwrap session state. The protection is against *keystore extraction*, not against local app compromise; the latter is the same residual the forward-secrecy honesty above covers. |
| **A repackager** | Not anymore than the build gate makes them (§8.3) — that gate is an availability speed bump, not a trust boundary. |

**Non-goals, stated as non-goals:** Whisper does not defend against metadata
analysis. Who talks to whom, when, how often, who is online, and who read what
are all visible to the operator by design — they are the feature model
(presence, receipts) and the routing model (§2.2). There is no cover traffic,
no batching to obscure timing. A friends-only messenger that hid its social
graph from its own operator could not deliver envelopes at all.

### 2.2 What the server can and cannot see

**Never visible to the server (and therefore to anyone but the endpoints):**

- Message and image content. All of it is ciphertext until the recipient's
  device opens it.
- Private keys. Keystore-bound hardware keys never leave the device. Software
  private halves (session keys, prekey halves, ratchet snapshots) never reach
  the server in any form either — the server stores only their *public*
  halves, while the private halves sit on-device wrapped with a Keystore AES
  key.
- Group message text, poll votes, mention targets — group payloads are the
  same wire frames as 1:1 messages, sealed per member.
- Story photo bytes and their AES keys — stories carry one sealed descriptor
  per friend; the database sees opaque descriptor buckets.

**Visible to the server by design:**

| Data | Why it must be visible |
|---|---|
| User IDs, timestamps, ciphertext, its marker column | Delivery requires routing and ordering |
| Usernames, display names, avatars, bios | Profiles are the address book |
| The friend and block graph | Friendship is the access-control primitive ("friends-only messenger") |
| Who talks to whom (1:1 chat rows; group membership rows) | You cannot route encrypted envelopes without a recipient list |
| Read receipts, typing signals, last-seen presence | Features, explicitly unconditional |
| Reaction rows (emoji + user) | Features |
| Prekey public halves and signatures | Session establishment |
| Push targets (endpoint + Web Push binding keys) | Delivery of wake pings |
| Group event log with admin signatures | It is by definition a public-in-group list of membership facts |
| Story state machine, quota counters, receipt/reaction presence rows | Enforcement and features |

The consequence, stated plainly: the operator of the backend knows the social
graph — who is friends with whom, who is in which group, who is online and
when. What the operator cannot do is read, forge, or reorder message content:
ciphertexts are sealed with keys the server never sees, and every
membership/permission fact that content cryptography relies on is
independently signed by device keys the server does not control.

Three nuances worth calling out:

- **Usernames reveal pairs.** A 1:1 chat row identifies both participants and
  their row timestamps; an observer with database access can infer
  conversation patterns. This is inherent to a friends-only model and is
  documented rather than hidden.
- **Push pings split by carrier.** The embedded FCM fallback receives
  wake pings whose entire payload is RFC 8291-encrypted Web Push (ciphertext
  only). An external distributor's server (ntfy, NextPush) must see *some*
  routing body; for those targets, the edge function strips every
  human-readable field — names of senders, groups, inviters, story posters —
  and sends IDs and event kinds only. The receiving device rebuilds the
  human parts from its own local caches. So a third-party distribution server
  learns "device X was pinged because of event kind Y in entity Z", and
  nothing about humans or content.
- **Image hosts see opaque PNGs.** Sealed containers (chat images, group
  pictures, stories) re-encode ciphertext into lossless PNG pixels, so the
  hosting service sees noise, not a picture. The one deliberate exception is
  avatars, which are real pictures under key-derived obfuscation (§5).

---

## 3. Cryptography

### 3.1 Keys on a device

Whisper has a deliberately split key hierarchy. Two kinds of hardware
identity live in AndroidKeyStore (keys generated with hardware-backed entropy
and never exportable in plaintext):

- An **ECDH identity** on the P-256 curve — the long-term "envelope identity"
  used to seal and open v2 static envelopes. Rotated on a 30-day schedule
  (with up to 6 hours of jitter, so all devices on an account do not rotate in
  the same minute), through a staged procedure: a new key is generated, its
  public half is published, the alias pointer is atomically committed, and
  only then is the old key destroyed. A startup "canary" self-check verifies
  that the identity is still usable, so a keystore repair or a restore that
  damaged it is detected, never silently reset.
- A **signing identity** (P-256, ECDSA) — the device certificate used to sign
  prekey material in 1:1 and, in groups, to sign the group event log. This key
  is per-install: it is regenerated when the app is reinstalled. Groups take
  that into account with a signer-history mechanism (§11.1) that preserves the
  validity of history signed by earlier installs of the same account.

Software keys (stored encrypted with the Keystore AES wrapper, and therefore
disk-resident but hardware-gated) sit on top:

- An **X25519 identity key** — the modern, constant-time curve used by the
  session protocol.
- A **signed prekey (SPK)**, rotated weekly, whose public half is signed by
  the P-256 signing key so nobody can substitute it.
- A pool of up to **50 one-time prekeys (OPKs)** — freshly generated, signed,
  published, and consumed one per session setup.

This division is intentional. Hardware keys guard trust and long-term
identity; software session-level keys cost milliseconds per handshake and gain
forward secrecy; the wrapper key ensures that nothing on disk is usable
without the device's secure hardware.

### 3.2 Two generations of the protocol

1:1 content travels in one of two shapes, negotiated per chat:

- **v2 static envelopes** — one JSON object carrying AES-256-GCM ciphertext
  under a key derived from the sender's *static* ECDH identity and the
  recipient's pinned identity key (multi-key envelopes allow several
  candidate keys for rotation tolerance). It is self-healing across key
  rotation and device reinstall. Its weakness: **no forward secrecy** — the
  compromise of either identity key exposes all v2 traffic ever sent between
  the pair; the 30-day rotation cycle bounds that window.
- **v3 ratchet frames** — Signal-style Double Ratchet messages, with an X3DH
  handshake on the first frame. Every frame is sealed under its own advancing
  symmetric chain. Honest scope of its forward secrecy: it defeats a *later*
  compromise of long-term identity keys and, once an attacker loses device
  access, frames derived after that point. It does **not** offer
  snapshot-resistance on a live device, because out-of-order tolerance
  deliberately persists consumed message keys (§3.4) — a full snapshot of the
  running app can decrypt already-received traffic.

The negotiation policy lives in a pure, fully-tested decision module
(`WireProtocol`): new contacts and live ratchet sessions use v3; an
establishment failure cools down v3 attempts for 30 seconds (so a transiently
unreachable prekey pool does not loop); a peer whose client provably cannot
parse v3 is allowed to receive v2 — once. Three consecutive handshake failures
with no working session block the fallback outright ("Handshake repeatedly
failed (3x) — blocked insecure fallback"), because a persistent failure is
indistinguishable from an attack. Proven-v3 peers never silently degrade to
v2; a broken ratchet session surfaces as an error, and the user is told rather
than downgraded through a weaker protection state.

A historical "insurance copy" (a v2 backup riding inside a v3 frame for
unsent-queue robustness) existed in older builds. It is decode-only: modern
frames never carry it, and rows past a setup cutoff (2026-12-01) that still do
are rejected rather than interpreted, since a post-cutoff insurance copy can
only be a replay or forgery.

### 3.3 Establishing a session: X3DH

Session setup follows the X3DH pattern from the Signal specification. The
initiator fetches the peer's **bundle** (identity binding, signed SPK, and one
OPK) from the `whisper-bundle-fetch` edge function and verifies it locally: the
identity binding must match the pinned trust state, and a bad SPK signature is
a hard, loud failure surfaced as key-change UI — never a silent acceptance. It
then computes three Diffie-Hellman
shared secrets (identity→SPK, ephemeral→identity, ephemeral→SPK), plus a
fourth through the consumed OPK when one is available.

KDF framing: one 32-byte `0xFF` pad is **prefixed once** to the concatenation
of the DH shared secrets (Signal-spec framing: `0xFF‖DH₁‖DH₂‖DH₃‖DH₄?`), run
through HKDF-SHA256 with a zero salt and info string
`WhisperX3DH-v1`, yielding a 32-byte root session key and a session ID that
both sides derive identically (the ID is what binds every later frame to the
session). The handshake header `{ik, ek, spkKid, opkKid}` rides on the first
outgoing frame.

The responder recovers its SPK and OPK private halves by key ID and recomputes
the same key. A missing OPK private half is a hard error — this exact path,
when mishandled silently in an earlier build, produced divergent keys and
permanently locked chats, which is why the rule is fail-closed today.

### 3.4 Forward secrecy: the Double Ratchet

After the handshake, every frame advances a Double Ratchet:

- **Root chain** (HKDF, info `WhisperRatchetRoot`) turns a new DH agreement —
  re-established whenever the peer rotates its ratchet identity — into the
  next chain root.
- **Message chain** (HMAC) turns a chain key into a per-message key with
  separating domain tags (`0x01` for the message key, `0x02` for the next
  chain key).
- Headers carry `{dhPub, pn, n}` and the AEAD rules bind the header bytes plus
  session and participant IDs — a forged or misrouted header is detected as an
  authentication failure before any key state moves.
- Decryption is **copy-commit**: state is advanced on a trial copy and only
  committed after AEAD verification succeeds. A forged header or tampered
  ciphertext therefore advances no chain and triggers no DH step — the state
  is snapshot-identical before and after the attempt, and this property is
  guarded by the security-test suite.
- **Out-of-order and replay tolerance:** up to 400 skipped (unused) message
  keys are held per chain with *no eviction* — a message further away than
  that window fails loudly instead of discarding honest keys under pressure from
  a flood. Consumed message keys are **retained, not erased**, so that a
  duplicate delivery re-opens the same frame — within a bounded FIFO of 400
  keys per session (same constant as the skipped window). Both collections
  persist with the session snapshot, so offline catch-up and duplicate
  deliveries survive process death.
- **What is on disk:** the entire session snapshot (X3DH secret, ratchet
  state, all skipped and consumed keys) is serialized and wrapped with the
  Keystore AES wrapper before being written. The honest framing of forward
  secrecy here, given that choice:
  - A full device compromise *can* decrypt already-received traffic (the
    snapshot plus the ciphertext-only Room cache is enough), and an attacker
    who has stolen the chain state keeps decrypting captured traffic until
    the next DH ratchet step brings in a fresh secret they lack.
  - A later leak of long-term identity keys never retroactively opens
    ratchet traffic.
  - "FS protects future traffic" is post-compromise protection, not snapshot
    resistance.
  Retention is bounded (the 400-key consumed window above), so that window is
  also how far back a re-open can reach; beyond it the honest locked
  placeholder applies. The render path memoizes decrypted text in memory, so
  ordinary scrolling does not depend on key retention. That memo covers
  *received* frames only. A device's own sent frames are re-rendered from the
  persistent self-read store described in §3.9, not from the ratchet: the
  sender never keeps the receive keys that would open its own ciphertext.

Unrecoverable gaps raise `WhisperRatchetLostMessage`, rendered as an honest
locked placeholder: "this message could not be opened because part of the
session history is missing." If the gap is the one kind that cannot catch up
(far-future counter beyond the window), the client additionally fires a fresh
handshake at most once per 30 seconds per peer and tells the user to ask the
peer to resend; other losses wait for the server to deliver the missing
frames, and the placeholder survives until then.

### 3.5 Wire formats

| Generation | Stored in | Shape |
|---|---|---|
| v3 ratchet frame | `messages.content`, marker `content_iv = "v3"` | `{"v":3,"sid","dh","pn","n","ct", "x3dh"?}` — one JSON per frame; `x3dh` rides only on the handshake frame |
| v2 envelope | `messages.content`, marker `content_iv = NULL` | `{"v":2,"k":[{"kid","iv","ct"}], "ik"?, "x3dh"?}` — the sealed body is a multi-key envelope over the static identities |
| v1 (legacy, decode-only) | `messages.content` + a separate `content_iv` column | raw base64 ciphertext with a standalone IV |

Notes that matter for interop reasoning:

- A **key ID** is the first 8 hex characters of SHA-256 over the base64 public
  key — no registry, purely derived.
- The sender's public key rides **inside** the envelope as `ik` so delivery
  never depends on the freshness of a profile row; receiving tries every
  candidate key, plus the in-band key on a probation basis (it is never
  adopted as trust on its own).
- Senders seal only to the pinned key plus keys provably chained to it by a
  rotation certificate — an "unproven new server key" gets dropped, and the
  send fails loudly with a key-change warning rather than sealing to a key
  nobody verified.
- AEAD association data is direction-sensitive: `whisper-message-v1` plus
  sender ID and receiver ID is bound into v2 ciphertext, so a message stolen
  from chat A cannot be replayed into chat B. Attachments use
  `whisper-attachment-v1`. Message size cap is 8,192 characters; clients whose
  rows predate the AAD era decrypt through a legacy fallback only for rows
  created before a cutoff date (2026-09-01), and the fallback itself has an
  administration-controlled retirement timeline.

### 3.6 Key trust: fingerprints, safety numbers, rotation certificates

Key verification is the piece a user actually interacts with, so the rules
matter more than the hashes they live in:

- **Trust anchor:** the first time a peer's identity is seen, it is pinned
  (trust-on-first-use). From then on, this pinned key is the reference every
  sealed envelope is validated against.
- **Safety numbers / fingerprints:** each party's public identity is hashed to
  an eight-group hex fingerprint, surfaced in the profile tab, verifiable via
  an in-app QR exchange, and the verification state is stored durably.
- **Key change is fail-closed.** If the pinned identity and the current
  published key differ, the chat is read-only with an explicit key-change
  banner until the user takes action. The one automatic re-acceptance path is
  a **rotation certificate** — a signed statement `ROTv2:<prev>:<new>:<counter>:<timestamp>`
  signed by the *old* key, with an anti-replay counter that must strictly
  increase per peer. Peers the user explicitly verified always re-verify
  manually even with a valid cert: an automatic acceptance would reduce
  verification to decoration.
- The same trust store maintains the counters used for anti-replay, so a
  spoofed cert replayed weeks later neither verifies nor advances the counter.

### 3.7 Key management: prekeys and rotation

The prekey manager publishes the identity binding to the server, rotates the
signed prekey weekly, tops the OPK pool up to 50, and keeps every private half
Keystore-wrapped on disk, so process death cannot strand half-prepared
handshakes. Rotations of the static identity publish a rotation certificate,
and every peer's policy engine consumes those certificates, so rotation is a
content-verified event rather than a trust gesture. A process-local
diagnostics buffer (last 60 protocol events, IDs and key material scrubbed)
aids support diagnostics without logging secrets.

### 3.8 The self-leg: how you receive your own group messages

One gap the 1:1 machinery cannot solve on its own: in group fan-out the
*sender is always one of the recipients* (their own copy is what renders their
history on this device, from the server rows, without a plaintext cache).
That leg cannot ride the normal ratchet path, because session establishment
fetches the peer's prekey bundle from the edge — and the edge refuses
self-bundles by contract (`WhisperSelfLegCrypto` exists to close exactly this
gap; without it every group send would fail on its own leg first).

Design, deliberately degenerate — one fresh session per frame:

- A **per-install X25519 anchor keypair** is generated once. To be precise
  about what "Keystore" means here (the same nuance as every wrapped secret
  in §3.1/§3.4): the anchor private is an ordinary *software* X25519 key; its
  wrapped form lives in this module's own preferences, wrapped by the shared
  Keystore AES key of §3.1, and it is unwrapped into memory whenever a
  seal/open runs. A dedicated anchor (rather than the live signed prekey)
  means weekly SPK rotation and OPK consumption can never orphan an
  already-sent self frame.
- Each frame gets a **fresh random 32-byte session key**, anchored through
  the standard ratchet initiator (DH'd against the sender's own anchor
  public key) and opened by rebuilding the responder from the same raw
  session key plus the anchor private — X25519 symmetry makes both halves
  derive identical chains, so the frame opens on the first receive key. No
  ratchet state advances or persists on open, so re-rendering cached history
  reopens the row deterministically forever. Honest note on the
  construction: since the per-frame key is persisted anyway (the point is
  permanent re-openability), the DH-to-anchor adds no extra secrecy property
  beyond what the wrap provides — the ratchet framing is reused for its
  AEAD/AAD machinery and key symmetry, which are already correct and tested.
  An equivalent design (a Keystore-wrapped per-install AES key with
  groupId/seq bound into its AAD) would work equally well; that is a
  simplification candidate, not a security gap.
- Each per-frame key is persisted **Keystore-wrapped** in the module prefs
  (`sk:<uid>:<sid>`, one small blob per frame). There is **no pruning
  today** — blobs accumulate (on the order of a hundred bytes per frame) —
  and, honestly, the account wipe (§7) does not currently clear this store
  either. Keys are user-scoped, so one account cannot use another's blobs,
  but the wrapped leftovers linger until reinstall or an OS data-clear. A
  prune-and-wipe pass for this prefs file belongs on the hygiene list.
- Session IDs carry the `sg1-` prefix and the open path refuses anything
  else, so a genuine peer frame can never be demoted into this path.

Honest properties: there is **no forward-secrecy chain across self frames**
(each frame's key is independent — that is the point, it buys permanent
re-renderability), and reinstall or data wipe locks past self frames with no
server fallback, same parity as the 1:1 "reinstall loses old sealed frames"
posture.

### 3.9 The locked-message field report and its fix (October 2026)

The October 2026 field report was that 1:1 history could render the locked
placeholder — the localized `st_Whisper_Locked_Message` ("This message can no
longer be decrypted on this device") — for every row, that the user's own
messages flickered locked and then showed their text, and that switching
accounts locked everything. Those were three independent failures collapsing
into one user-visible line, and all three are now fixed in the working tree.
The distinction still matters in the rest of this document: "locked" is only
honest for genuinely unrecoverable history (§3.4), never for "not read yet".

**Failure 1 — own sent v3 text had no persistent self-read path.** The Double
Ratchet is send-only on the sender's side: the device keeps the chain that
*produced* the frame, never the receive keys that would open it. Re-rendering
a sent text row depended entirely on `WhisperRepository.sentV3PlaintextMemo`,
an in-process 1,000-entry LRU written at send time and never persisted, while
Room stays ciphertext-only (§6.2). After process death or LRU eviction a sent
v3 row had no re-read path and rendered locked permanently.

*Fix: the persistent self-read store (`WhisperSelfReadStore`).* At send time
the plaintext is sealed with the hardware-backed Keystore AES key
(`WhisperCrypto.wrapWithKeystoreAes`) and written to the Room table
`whisper_self_read` (schema v66), which itself lives inside the
SQLCipher-encrypted database — double-protected at rest, and never the
peer-facing ciphertext. The sealed payload is `SR1|messageId|plaintext`, so a
blob cannot be swapped onto another message; rows are keyed by
`(ownerId, messageId)` with a SHA-256 `contentHash` of the wire
ciphertext, which is how conversation previews (the `get_conversations` RPC
carries no message id) resolve their own last message. Retention is bounded at
1,500 copies per owner. The read paths preload the conversation's own rows in
one indexed query (`getMessagesFlow`, `getMessages`) and consult the in-process
memo first; realtime echoes and previews use a single targeted lookup.
Deleting a message, clearing a history, or a tombstone also forgets its copy;
sign-out keeps copies (the account must still render its history when it signs
back in) and account deletion wipes them.

**Failure 2 — the locked placeholder was also the first paint.** The Room flow
emits ciphertext rows and decrypts on read, so an own row whose memo was
written a beat after the emission landed painted the locked marker first;
`ChatMessageMerger` then healed locked → plaintext on the next emission, which
is exactly the reported flicker. The realtime echo of an own row took the
normal decrypt path, which can never open the sender's own frame.

*Fix: write before the network insert, resolve own rows first.* `sendMessage`
now writes the memo and the sealed self-read copy **before** the network insert
(and before the queued/offline path's Room insert), so neither the echo nor the
first Room emission can beat it. `decryptRealtimeMessage` and the conversation
preview paths resolve an own row from the memo/self-read copy before any
ratchet work. The merger's locked → plaintext healing rule stays as a safety
net for old rows, but new own sends no longer reach the locked state at all.

**Failure 3 — stores were install-scoped, not account-scoped.** Room rows, the
`whisper_sessions/` snapshots and the key trust store were keyed by peer, never
by the signed-in account. Sign-out clears only in-memory caches (§7) and
deliberately leaves those stores in place, so two accounts on one device
shared one session snapshot per peer: whichever account advanced the session
last owned the file, and the other account's cached rows then failed AEAD and
rendered locked. The session store's recovery rule ("a corrupt file is treated
as absent") could not help — the file was valid, it simply belonged to the
other account.

*Fix: account-scoped session state.* Session files now live at
`whisper_sessions/<sha256(accountId)>/<sha256(peerId)>.json`; the in-memory
maps and the per-peer mutex are keyed by `(accountId, peerId)`; and each loaded
`Live` pins the account it came from, so a save that spans a sign-out can never
land in the next account's file. Sign-out calls
`WhisperSessionStore.clearMemory()`, which drops live ratchets, the last-written
cache and establish-failure counters while leaving the per-account files on
disk.

Upgrading cannot lose existing sessions, and it cannot hand them to the wrong
account either. Pre-scoping builds wrote `<sha256(peerId)>.json` directly under
`whisper_sessions/` with no owner in the name, so adoption is **per peer and
lazy**: the first time an account opens a conversation with peer P, the file
for P is moved into that account's directory. Two accounts A and B on one
device chatting with *each other* own different files (`<sha256(B)>` for A,
`<sha256(A)>` for B), so both keep their own ratchet — the case a whole-
directory sweep broke, because it dumped every file, including B's session with
A, into whichever account happened to run first. A file for peer P found
inside account P's own directory is also reclaimed: that location can never be
legitimate (an account has no session whose peer is itself), so it can only be
the counterpart account's session for P. Already-mis-swept installs repair
themselves on the next open of the affected conversation.

One honest limit: if two accounts on one install have both chatted with the
same third peer P, the pre-scoping layout kept only one snapshot for P, so it
can be attributed to only one of them. The account that claims it keeps its
history; the other one has no session for P and re-handshakes on its next send.
That ambiguity is inherent to the old file name (it records the peer, never the
owner) — it is not introduced by the fix, and unlike the same-install pair it
cannot be resolved from the layout alone.

The other half of the same-install case is handshake acceptance. Two accounts
on one install share the device identity key, so the usual "peer identity
changed ⇒ reinstall ⇒ accept the fresh session" signal never fires between
them, and the deterministic session-id tie-break (which makes two *racing*
initiators converge, §13) would reject a legitimate new session roughly half
the time. The losing side then keeps sending pure frames on a session the other
side never adopted — permanently locked, with no handshake left to retry.
Because only one account can be signed in at a time, two accounts on one
install cannot race, so a handshake whose identity is this device's own
identity is always accepted when its session id differs. Cross-device traffic
keeps the tie-break unchanged.

The key trust store stays install-wide on purpose: a peer's identity key is a
property of the peer, not of the local account, so a TOFU pin recorded while
signed in as A remains valid and useful for B on the same device. What *must*
not cross accounts — the ratchet chains and the sent plaintext — is scoped.

**Honest residuals.** (a) Rows sent before this build have no self-read copy;
if their ratchet state is already gone they stay locked, and no update can
recover them. (b) Sign-out clears the sent-plaintext memo and every live
ratchet from RAM (§7), but the sealed copies remain on disk scoped to their
owner, by design. (c) The chat still does not render a separate "decrypting"
state, so a *received* row whose session has not hydrated yet can briefly show
the locked marker before its first successful decrypt; the merger heals it and
the pipeline now hydrates keys before emitting history, but the tri-state
render remains a future polish item.

---

## 4. The life of a message (end-to-end walkthrough)

This section ties the earlier pieces together. Follow one text message from
Alice's keyboard to Bob's screen.

**On Alice's device:**

1. **Compose.** Alice types. The composer carries a reply target, optional
   mentions, and message-length safeguards. Nothing has left the device yet.
2. **Seal.** On send, the repository asks the session module for Bob's
   session:
   - If a live v3 session exists, the message is sealed with the Double
     Ratchet under a mutex (one writer at a time per peer). The ratchet state
     is *persisted before the frame is released* — an on-send crash can cost
     delivery, never a usable chain.
   - If no session exists, the X3DH handshake runs first (fetching Bob's
     bundle, consuming one OPK if available) and the frame goes out with the
     handshake header.
   - If both fail after the block threshold, the send **fails loudly and
     nothing is queued — there is no ciphertext to queue.** The send error is
     explicit (v3-downgrade blocked / no key / repeated-handshake), shown to
     the user as a key-state problem, and retried by hand once the session or
     key state heals. The outbox never holds plaintext, and never pretends a
     send happened.
3. **Queue and deliver.** The sealed row enters the local outbox (FIFO,
   capped) with a client-generated UUID used for idempotent delivery: a
   retried send cannot double-post. A network-constrained scheduler with
   exponential backoff drains the queue; the database `INSERT` carries the
   sender ID, receiver ID, timestamps, and the formatted ciphertext. If the
   INSERT itself fails (offline, server error), the already-sealed ciphertext
   row goes to the outbox and is retried by the delivery scheduler — that is
   the queue-and-retry path, and it only ever applies after sealing
   succeeded. Once the server acknowledges the insert, the outbox row is
   settled — no further round-trip is needed for delivery.

**On the server:**

4. The row lands in `messages`. RLS ensures the sender owns the row and Bob
   (a friend) may select it. A content-hash replay guard refuses identical
   duplicate rows, and a block check refuses rows between blocked pairs.
5. An INSERT **database webhook** wakes `whisper-push-send`, which checks
   Bob's last-seen and his registered push targets. If Bob was seen in the
   last 60 seconds, it does nothing (the app is already alive); otherwise it
   sends a wake ping shaped by Bob's target type (§9). The ping carries IDs
   and event kinds; on FCM-fallback targets the whole ping is additionally
   RFC 8291-encrypted so even the carrier sees only ciphertext.

**On Bob's device:**

6. **Realtime lane.** If the app is open, a Supabase realtime subscription on
   the pair's postgres channel delivers the row in milliseconds; the row is
   decrypted through the unified pipeline (v3 frame → envelope → legacy) and
   appended to the bubble list. In-band sender keys can heal a rotated trust
   pin; a fresh non-v3 row from a session peer tears down the stale session.
7. **Or via wake ping.** If the app was closed, the ping contains nothing
   displayable — the client renders a generic content-free notification, and
   the human-readable content only ever appears once the app has opened,
   synced, and decrypted locally.
8. **Render.** The Bubble UI displays the decrypted content — or a locked
   placeholder if the history genuinely cannot catch up (§3.4). Own sent rows
   are resolved from the persistent self-read copy before any ratchet work
   (§3.9), so they no longer render locked after a restart or on an account
   switch. A *received* row whose session has not hydrated yet can still show
   the placeholder for one emission; `ChatMessageMerger` heals locked →
   plaintext on the next emission and never the reverse, so "locked" on screen
   is not proof of history loss. Read receipts, typing, and presence flow back
   over their own channels; reactions and deletes travel as their own protocol
   objects.

---

## 5. Images and avatars

All binary media share one philosophy: **seal on-device, host untrusted,
transport opaque containers.**

**The sealed-image container.** AES-256-GCM ciphertext is not stored as raw
bytes; it is *re-encoded* into a lossless PNG whose pixels carry the
ciphertext, with a `WZ1` magic header, version byte, and CRC32 (the pixel
shape defeats image-compressing hosts that re-encode "real" pictures; forced
sRGB defeats desktop/mobile premultiplication quirks). Caps: 5 MiB of cipher
bytes; decode capped at 8 megapixels (4 on low-RAM devices) with a
pre-decode bounds check against dimension-bomb images. Download side has an
https-only allowlist (the ImgBB image domains and
the project's own Storage folder), accepts a single redirect, and maps a
403/404 to an "expired/unavailable" state rather than treating it as a fault.

**Chat photos.** Compress on-device to a 1920-px long edge at JPEG quality 82
before sealing. Every recipient — plus a **self-addressed copy** — receives
their own sealed frame, so the sender keeps access to the photo even after
the peer rotates keys. Disappearing timers (60 seconds to 180 days) live
inside the reference payload; the local loader honors expiry and renders the
expired state without ever decrypting late bytes.

**Group pictures (avatars) and story photos** use a *fresh AES key per
image*; the key rides inside the admin-signed group event that publishes the
new picture URL, and inside the sealed Daily-Ups descriptor. Group pictures do
not use the ratchet at all, so they cannot perturb live sessions.

**Avatars.** Avatars are one deliberate exception, marked honestly: they are
**obfuscated, not end-to-end encrypted.** The obfuscation key is derived
from the owner's public identity, so *anyone with the public key* could
derive it; its job is to hide image bytes from the hosting provider and
casual third parties, not from holders of the (public) key. On-disk caches
(either for avatar or chat images) remain encrypted containers with
generation-scoped guards so a signed-out state cannot keep population artifacts.
The avatar loader also keeps a **bounded negative cache** (per URL, ten-minute
re-eligibility, wiped on sign-out): a permanently dead remote avatar (its
hosted image was deleted) is remembered as failed instead of re-hitting the
network on every re-render — the honest consequence is a silent
rendered-initials fallback for at most ten minutes after a transient outage
or reconnect.

---

## 6. The client architecture

### 6.1 The repository layer

`WhisperRepository` is the load-bearing orchestrator: one singleton-facing API
for send, receive, realtime, friends, profiles, reactions, and deletes. It
defines the protocol constants (8,192-char cap, image expirations from 60
seconds to 180 days, a 30-second/1024-ID dedupe window for realtime events,
up to 6 realtime resubscribes at least 2.5 seconds apart, 8-second typing
freshness, 256-px avatars, 5-minute profile cache) and delegates:

| File | Role |
|---|---|
| `WhisperRepository.kt` | core orchestration: health, send, receive, realtime, deletes, images |
| `WhisperRepositorySocial.kt` | friend requests, blocks, discover (paged + quota), hide/mute |
| `WhisperRepositoryProfile.kt` | profile CRUD, avatar hosting, username availability |
| `WhisperRepositoryGroups.kt` | the entire group plane: event log, sealing, receipts/reactions, offline outbox |
| `WhisperRepositoryStories.kt` | the Daily Ups plane: publish, sync, receipts, local expiry (§12) |
| `WireProtocol.kt` | pure negotiation + key-change decisions, no I/O — fully unit-tested |

Send/receive are the heart of §4 and do not need repetition here. Two pieces
of the *health* machinery are worth naming:

- **Identity republish:** startup verifies that the local identity matches
  what the server has, repairing a divergent row before anything else writes.
- **Prekey health:** failed prekey publishes retry later. An **exhausted
  peer OPK pool does not block anything**: establishment proceeds with the
  three DHs and no OPK factor (§3.3 is explicit that the OPK slot is
  optional), and a received handshake that *names* a consumed or
  no-longer-held OPK hard-fails (§3.3's missing-OPK rule). There is never a
  silent acceptance on either branch — but "empty OPK pool → envelope
  fallback" is *not* what happens, and this document no longer claims it.

### 6.2 What lives on the device

Local state is split across purpose-built stores, all read in full by the app
(so the picture below is the complete on-device footprint):

| Store | Holds | Notably does NOT hold |
|---|---|---|
| Room (messages, local tombstones, outbox) | ciphertext-only messages (latest 500), delete-for-me tombstones, FIFO outbox (cap 100, 200-entry drop ledger) | plaintext before render; no key material |
| `whisper_sessions/` | one wrapped session snapshot per peer, under `<sha256(accountId)>/` | anything that cannot be Keystore-wrapped |
| Self-read store (`whisper_self_read`, Room v66) | the user's OWN sent plaintext, sealed with the Keystore AES key as `SR1\|messageId\|plaintext`, keyed by `(ownerId, messageId)`, with a SHA-256 hash of the wire ciphertext for previews; capped at 1,500 rows per owner | peer-facing ciphertext; incoming messages; anything readable by another account |
| Key trust store | pinned/verified fingerprints + rotation counters | private halves |
| Mute/hidden-chats/delete-for-me/undo/preferences stores | UX state with expiries (24h eviction for delete-for-me, 30s undo window, hidden chat auto-return) | — |
| Daily Ups seen store (`whisper_story_seen`) | opaque `storyId → expiresAtMs` pairs for device-local ring state, capped at 512, expired entries filtered on load and pruned on refresh/write | who viewed what; account id; media or keys |
| Access-file backup (`WhisperAubupManager`) | credential-only payload (username, auth type, credential, display name) | session/ratchet state, key material — never |
| Sealed image disk caches | encrypted containers keyed by URL hash, generation-guarded | plaintext pixels |

A scoping note on that table, because the row titles hide it. Whisper state is
split by *what it is*, not uniformly by account. Room message rows are filtered
by `senderId`/`receiverId`; session files are namespaced by
`sha256(accountId)`; the self-read store is keyed by `ownerId`; the key trust
store stays *install-scoped* on purpose, because a peer's identity key is a
property of the peer rather than of the local account (§3.9). The in-process
`sentV3PlaintextMemo` remains a 1,000-entry LRU keyed by message ID and is
cleared on sign-out; it is only a hot-path cache in front of the sealed
self-read rows, not the durable copy.

The Room outbox and delivery scheduler work together: FIFO delivery of
ciphertext-only rows, duplicate-delivery detection (a server replay guard),
exhausted attempts into the drop ledger, and WorkManager-side 15-second
exponential backoff. The network-constrained immediate scheduler covers the
common "send while online" path in one step.

### 6.3 Realtime connectivity

Postgres-changes lanes are per chat (`chatpg_<sortedPair>`), plus an inbox
broadcast lane per user, a per-pair broadcast lane, typing and presence lanes.
Channels are cached, health-watched, and resubscribed with a backoff ladder;
an event dedupe window (30 s, max 1024 IDs) neutralizes replays. The realtime
channel's last-seen pings map presence to online (≤2 min), recent (≤1 h), and
offline. The per-user inbox lane scopes itself to the owner's rows via RLS
plus single-column filters, and deliberately does not consume its message
broadcasts — the Postgres-change lane ordering is authoritative.

---

## 7. Accounts and authentication

Whisper accounts live in the Supabase GoTrue identity service behind two
facade paths (`WhisperAuthManager`):

**Username + password.** 3–20 lowercase alphanumeric/underscore username,
10+-character password, display name 1–60. Internally this maps to a virtual
email `user@u.whisper.local`, so the user-facing concept stays a username even
though GoTrue requires an email.

**Whisper tokens.** A token is 64 hex characters of freshly generated entropy;
the GoTrue "email" is SHA-256 of it and the password another hash of it. A
login is a **single** attempt with the current full-hash derivation. If that
misses with a credential error (detected structurally, never by bare status
code), the manager probes each of the three legacy derivations once — before
failing loudly — and, on a legacy hit, **does not log the legacy session
in.** It surfaces a "Legacy account found — tap Migrate" banner with a
30-day window; the migration edge function copies the profile to the
modern hash-based identity and retires the legacy one through the Auth admin
API, recording it in the legacy-disable registry so legacy login is
permanently refused afterward. Without a token (and without the access-file
or vault backup) an account cannot be logged into again — that is the
documented trade-off of token-style identities.

Support features: restore from the Toolz password vault or an encrypted
access file plus whisper code; automatic clipboard expiry for displayed
tokens; username availability checks; signed-out and full-wipe account
deletion (re-auth for password accounts, edge deletion with a password
header and a 5-minute confirmation timestamp, then local wipe of sessions,
Room, trust store, outbox, avatar caches, and push registration).

**Sign-out is not a wipe, and that difference matters.** Deleting an account
runs `clearAllLocalData()` — everything above, including the ratchet session
store, the self-read copies and the key trust store. A plain sign-out runs
`clearSessionScopedCaches()`: it drops in-memory state only (profile / peer-key
/ block caches, the decrypt memo, the sent-plaintext memo, live ratchet
sessions, avatars, realtime channels) and deliberately leaves Room,
`whisper_sessions/`, the self-read store, the key trust store and the outbox on
disk, because they are classed as account data rather than session data. That
is safe because the durable state that could poison another account is scoped:
session files and live ratchets are namespaced by account id, and self-read
copies are keyed by owner id, so a second account on the same device cannot
read or overwrite the first account's chains (§3.9). The key trust store stays
install-wide by design — peer identity keys are not account state.

---

## 8. The backend

### 8.1 The database

`supabase/migrations/main-whisper-sql.sql` is the single canonical,
idempotent baseline (safe to re-run; previous migrations are squashed into
it). The shape:

| Domain | Tables / constructs |
|---|---|
| Messaging | `messages` (`content_hash` replay guard, block-aware insert guard, sender-authorized tombstone exception), `message_reactions` |
| People | `profiles` (identity binding, keys, presence, discoverability), `friendships`/`friends` (pending-only transitions, block-aware), `whisper_blocks` |
| Keying | `whisper_prekeys` (signed SPK + OPKs), rotation cert columns, identity archive triggers |
| Presence/signals | `whisper_typing_signals` (8-second freshness), presence stamps on profiles (opt-out switches deliberately removed — these features are unconditional and uniform) |
| Push | `whisper_push_targets` (endpoint + provider + Web Push binding keys) |
| Abuse control | upload / discover / bypass / destructive / username-check / signup (5/day/IP) quotas |
| Lifecycle | delete tombstones, image ownership ledger (unique delete URL per upload), account-purge RPC, legacy-sunset registry |
| Groups | §11.6 |
| Stories | §12.3 |
| Views/RPCs | `whisper_public_profiles` (opt-in visibility only), discover paging, username-availability, signup gate, purge |

All of it is wrapped in **owner-scoped RLS**: a row's owner (and their
accepted friends, where applicable) is the only identity the policy admits;
the service role bypasses RLS only for the sweeper/migration/webhook edges.
Realtime publication exists for exactly the tables the app subscribes to,
with full replica identity so deletes carry their old rows.

Two server-side behaviors deserve explicit notice:

- **Presence and typing are unconditional.** Earlier opt-out switches were
  removed deliberately: last-seen stamps and typing signals are now uniform
  for every account — the same information, visible the same way, with no
  per-user asymmetries. This is a feature data model, not a hidden switch
  matrix; if a user is uncomfortable with presence being visible, that is a
  documented product trade-off (§14).
- **Signup is IP-budgeted at 5/day/IP** to blunt Sybil-style abuse of the
  friends-only model, advisory-locked so a burst of parallel signups shares
  one counter, and enforced pre-auth in the edge function so an unbudgeted
  request never reaches GoTrue.

### 8.2 Edge functions

Every function runs in `supabase/functions/whisper-*/index.ts` (languages:
TypeScript/Deno, single-file — deliberately so that all shareable
verification code is inlined into exactly one deployable JS file per
function).

| Function | Role |
|---|---|
| `whisper-attest` | Returns the caller's build-integrity verdict (`{ok, mode: "cert"\|"integrity"}`); 428 for unofficial builds; optional, env-gated Play Integrity strong path |
| `whisper-bundle-fetch` | Returns a peer's identity binding, latest signed SPK, consumes one OPK, and serves the signer history archive that makes group verification reinstall-proof (§11.1). Rejects self-bundles |
| `whisper-bypass-verify` | Constant-time check for the screenshot-bypass password; 5 failures per 15 minutes locks out |
| `whisper-delete-account` | Verifies JWT + password header + confirmation timestamp, wipes all rows, then deletes the GoTrue user |
| `whisper-image-upload` | Stores a sealed PNG on ImgBB, binds the ownership ledger entry that later resolves deletes; returns URL + opaque ID |
| `whisper-image-delete` | Deletes an ImgBB blob by ID (ledger-gated) |
| `whisper-push-send` | The wake-ping fan-out (§9). Database-webhook triggered; scrub path external-distributor targets |
| `whisper-signup-gate` | Pre-auth IP-budget gate (5/day/IP) |
| `whisper-story-sweep` | Service-role cleanup of expired/deleted story images, lease-based (SKIP LOCKED), no scheduled-cron dependency |
| `whisper-legacy-migrate` | One-shot legacy-account migration (copies profile to the full-hash identity, deletes legacy user, records the sunset) |

`EdgeFunctionClient.kt` is the app's single hardened HTTP lane (Ktor over
OkHttp; mandatory timeouts; bearer selection per lane; no response-body
logging). When a build carries no Supabase credentials at all (open-source
variants self-hosting elsewhere), the DI module plugs in an invalid URL so
Whisper sits cleanly offline with one log line instead of crashing.

### 8.3 The build-integrity gate (and what it is *not*)

Every `whisper-*` edge call carries `X-App-Package` / `X-App-Cert-Sha256` /
`X-App-VersionCode` headers, checked server-side against pinned constants
(official package name, the SHA-256 of the release signing certificate, and a
minimum version code). Unofficial builds get HTTP 428. On-device, a runtime
gate re-checks the release cert pin, refuses debuggable non-debug builds, and
enforces a version floor — a blocked build shows a full-screen card with the
reason and the official download URL, so no chat UI is reachable.

Say this twice: **this gate is an availability/abuse speed bump, not a trust
boundary.** The header values are readable from any official APK, so a
determined repackager can replay them; nothing in the cryptography treats
this as an authentication factor, and no security claim in this document
depends on it. The cryptographically meaningful variant — remote Play
Integrity attestation — exists but is disabled by default since it would
make the FOSS baseline dependent on Google services.

---

## 9. Push notifications

Wake logic is coordinated over **UnifiedPush** with an embedded FCM fallback:

- The default **distributor** is auto-picked (no user setup): an installed
  external one (ntfy, NextPush) wins; otherwise the embedded FCM fallback,
  which requires Play Services or microG on-device. With neither, the app
  simply runs without pings — chat still works synchronously, you just do not
  get poked on a background device.
- Registers itself into `whisper_push_targets` (endpoint, provider, and — for
  Web Push targets — the RFC 8291 binding keys). A registration arriving
  before login is parked locally and flushed on the next launch.
- The edge function fans out **one wake ping per registered target** —
  skipping anyone seen within the last 60 seconds — with the following parts:
  - FCM fallback targets: RFC 8291-**encrypted** payload (binding keys pinned
    at registration), so the FCM hop itself is opaque.
  - External distributors: a plaintext but **name-scrubbed** JSON containing
    only IDs and event kinds. Any human-readable field — senderName,
    groupName, inviterName, actorName, subjectName, posterName — is stripped
    server-side. The client reassembles the human bits from its friends and
    groups caches *before* rendering, so what a user sees is normal full
    copy but what a third-party distributor sees is structure only.
- Dead endpoints (404/410) are pruned so a stale phone does not generate push
  noise forever. The push function never logs tokens or payloads.

One deployment dependency stated openly: the encryption and the name-scrub
live in the *deployed* edge function, not in the client. An outdated
`whisper-push-send` deploy silently reverts both to payload behavior this
document doesn't describe; the post-deploy smoke runbook
(`supabase/POST_DEPLOY_SMOKE.md`) exists to catch exactly that.

Incoming pings show a generic, content-free notification keyed by sender and
message ID for dedupe. Tapping opens the relevant surface; combined with the
master mute switch and per-chat mutes, the user controls exactly which
surfaces can wake the phone.

Group events ride the same discipline, verified line-by-line during the
October 2026 hardening: group message and event notifications honor the
per-group notification toggle and the master mute inside the notification
manager, are suppressed while the app is in the foreground *with the relevant
surface already open*, carry their own dedupe keys (never two pings for one
group event), post at `VISIBILITY_PRIVATE` so lock screens see "Whisper
message" rather than content, and every inward tap is deep-linked through
validated actions — a malformed or foreign payload routes to an honest
dead-end screen, never a crash or a wrong surface.

---

## 10. Background work and app wiring

`ToolzApplication` schedules the two periodic workers:

- **Local cleanup** every 24 hours (device-side tombstone expiry and expired
  local-media pruning).
- **Outbox delivery** every 15 minutes (plus immediate on-send), with up to
  five WorkManager attempts per row; permanent rejections end their retry
  life in the drop ledger, which is the only place "we gave up" is recorded —
  again, ciphertext-side, never as a leaked message.

`MainActivity` re-flushes parked push registrations, injects the avatar
loader, owns the `whisper-auth://login` deep link (the access-file restore
path), and wires generic share intents. The manifest declares the deep link
and the UnifiedPush service entry point.

---

## 11. Groups

> [!NOTE]
> **Update Status:** Whisper Groups are currently hidden from the UI and not accessible in this update (`WhisperGroupsConfig.ENABLED = false`). All backend infrastructure, database tables, edge functions, crypto fan-out routines, and UI screen files are preserved intact for a future release.

Groups are the most intricate subsystem: a cryptographically authenticated
membership log, pairwise message fan-out, and a permission model, all inside
the existing 1:1 trust machinery. Up to 12 members, three roles
(**owner / admin / member**), and one event log that is the single source of
membership truth.

### 11.1 The event log: membership is signed, not stored

The server tables reflect membership, but the client *never trusts them*
directly. Instead, every structural change is an event in
`whisper_group_events`: create, invite, join, leave, remove, promote, demote,
make-owner, rename, picture, settings. Each event carries:

- a **sequence number** (total order, monotonic per group),
- an **epoch number** (bumped when membership changes, so every message is
  pinned to the exact membership it was sealed for),
- the actor's ID, the payload, and an **admin signature** over the full
  payload.

A client applies events only after verifying the signature AND a certificate
history. That history is the piece that survives reinstallation: each event
row publishes the exact signer certificate that made its signature, and the
database separately archives **every signer cert the account has ever
published** (an archive trigger on the identity binding). Verification accepts
a row only when (a) its signature verifies under the row's cert and (b) that
cert belongs to the actor's published set. So if the group creator reinstalls
— new device key, same account — the new device can still replay and accept
the old log its predecessor signed, because both devices' certs are in the
published set. Never-published certs fail, no matter how well-formed the
signature.

The same deployment caveat as §9 applies: the archive trigger is the canonical
SQL baseline and the history serving lives in the deployed
`whisper-bundle-fetch`; a stale edge deploy silently drops reinstall-proofing.
The post-deploy smoke runbook verifies this pairing.

Applying is **tolerant**: a bad event does not brick the group — the client
truncates to the verified prefix and flags the membership "degraded"
(surfaced by a banner in the chat and info screens; history at or after the
bad sequence is dropped, never trusted). A bad *create* event fails the whole
log, so the log opens with certainty. Unknown event types still arrive as
decode markers that fail closed inside the apply loop, rather than throwing
before the verification even starts — future clients can extend the log and
old clients will not be corrupted by it.

### 11.2 Sealing: pairwise fan-out and epochs

The sender fans out one inner frame **per member, including self**, sealing
through the 1:1 machinery: the sender→peer Double Ratchet for peers, and the
dedicated self-leg anchor for the sender's own frame (§3.8 — the self leg
cannot use the standard path because the edge refuses self-bundles, so
`WhisperSelfLegCrypto` seals it with a per-install anchor and a fresh
per-frame session key). Group ID and epoch ride *inside* the AEAD plaintext,
so a frame cannot be retargeted from one group to another.

Opening is equally strict: a member opens only the frame addressed to them
(their own session), and a row only when the reader's membership range covers
that epoch (`isMemberAt` ranges). Someone who left and rejoined keeps a gap
for messages in missing epochs and is locked out of them, honestly. Per-leg
seal failure is fail-closed over the whole fan-out — a partially sealed group
message is never sent.

Offline messages queue their sealed bundles in the local outbox with a
per-group marker; group pictures queue differently (they re-run from
persisted original bytes) — both drained by the group flush on hub/chat open,
while the periodic worker stays 1:1-only (a blind worker replay would burn
attempts on stale-epoch rows).

### 11.3 Membership, invites, and join requests

Creating a group sends **persistent invites** (there are no auto-adds). An
invite row can only be removed by joining (the row is trigger-consumed),
declining (the recipient), or being explicitly canceled (any admin).
Recipients see the invite card pinned on the Chats tab and can also *read*
the group's event log while pending (necessary: otherwise joining would
arrive with no context). Separately, **join requests** live in
`whisper_group_join_requests`: a friend who knows the group's ID (or scanned
its QR) can ask to join; the request lands in the admins' join-request inbox,
where it is approved into a real invite or denied.

Group joining follows an event-first rule with a heal path: if the log says
"joined" but the member row is missing, the row is recreated rather than
locked out. Reported members are blocked globally and leave the group, and
the report path pre-checks last-admin boundaries so no one can strand a group
without an owner.

### 11.4 Content: messages, polls, mentions, replies

Structured content rides inside the sealed frames as `wg1:` JSON — text,
image references (with optional expiries), reply quotes, poll cards and votes,
poll closes, and mention lists. Everything stays client-side:

- **Polls** tally on the client (latest vote per sender wins; admin close
  freezes the tally) — no server table, no server semantics. An admin close
  is confirmed in an `AlertDialog` first; the sealed close marker carries the
  poll id (which, not its human text, drives the frozen state) and takes that
  text from a string resource.
- **@mentions** render as highlighted chips with an on-device picker; the
  server cannot read them, so they ride message payloads. Badge chips ("you
  were mentioned") are computed locally from recent decrypted rows.
- **Replies** freeze a quote snapshot at send time.
- **Images** follow the same sealed-container pipeline as 1:1 chat (§5),
  hosted and expiring per the owned reference.
- **Receipts, reactions, typing, join requests** are member-RLS "presence"
  tables on the realtime publication — small, high-frequency, absolutely
  content-free.

### 11.5 Live updates

Realtime rides the same publication as everything else, but the open chat
does something smarter than "refetch on ping":

- **A message INSERT carries its sealed row.** The open chat decrypts that
  one row locally (one ratchet open through the exact same helper the bulk
  reads use) and merges it into the timeline. No outbox flush, no re-sync, no
  full-history scan — a live relay rather than a reload.
- **Reaction changes** arrive as typed events (applied / removed) and are
  merged into the chip map in place, with replace semantics matching the
  server's one-emoji-per-user rule; a rare unresolvable delete falls back to
  a bulk refetch. Receipt events ("seen by") merge in place as they arrive.
- **Fallbacks are bounded.** The belt-and-suspenders lane for send/vote echo
  misses is a 16-row head refresh, never a full reload. Membership/log
  changes and wipes still full-reload, because those *are* structural.
- **Name resolution rides a TTL cache** (60 s) so the message path never pays
  two server round trips to relearn what has not changed.
- **Reloads are coalesced, and watchers self-heal.** Bursty triggers (open,
  join, realtime pings, quiet resyncs) collapse into one in-flight pass plus
  at most one queued re-run — concurrent full-load passes whose state writes
  interleave were a real interleaving bug class, now structurally impossible.
  The six realtime collectors in the group chat (log, messages, deletes,
  typing, reactions, receipts) resubscribe in a capped-backoff loop after any
  error instead of going silently dead after the first throw.

### 11.6 Server rules

`whisper_groups`, `whisper_group_members`, `whisper_group_events`,
`whisper_group_messages`, `whisper_group_invites`, plus presence tables…
guarded by triggers that make the client's rules server-enforced:

- the 12-member cap (on both members and members+pending invites),
- a chained epoch rule (you cannot renumber membership history),
- message guard: sender must be a current member of the current epoch, and
  admin-only-send mirrored faithfully from the signed log (trigger + backfill
  — a modified client cannot bypass what it *cannot sign*),
- fan-out **quota: 600 sealed envelopes per UTC day per sender** (~50
  messages in a full 12-member group; proportionally more in smaller ones —
  see §14, item 2),
- invite-gated member inserts with consume-on-join,
- last-member disband protection (a live group can never be deleted by
  accident),
- cleanup of receipts/reactions when a message is wiped,
- direct-add events accepted only in historic rows (legacy degrade, never
  resurrect).

Server-side group writes additionally require rows to declare a minimum
client version code (a SQL `CHECK` on `client_version_code`). Being honest
about what that is worth: the value is client-supplied, so it is the same
tier as the build gate (§8.3) — it fences honest-but-outdated clients, nothing
stronger. The integrity guarantee against a *modified* client is not this
column; it is that honest clients reject events whose signatures or signer
certificates fail verification (§11.1), so an injected row never enters a
verified log.

### 11.7 In the app

**Entry.** The Chats tab's Groups section lists rows with picture,
time-stamped last-message preview, unread badge, an "@You" mention chip, and
pinned invite cards. A stories bar above it shows my "Daily Ups" bubble with
a bottom-right `+`, then one bubble per live friend poster — unseen rings
bright, ≥2-unseen count badge — horizontally scrollable (§12.4). Both Daily
Ups rails expose each friend bubble as one merged TalkBack node labelled
"name, N live ups, seen/unseen", with the ring and count badge treated as
decorative. Tapping a friend bubble routes to the hub already aimed at that
poster; the `+` opens the composer directly. Creation offers friend picking,
an optional picture, and an invite-mode choice that is fixed at creation (see
the permissions edge note below).

**Hub controls and states.** The groups header keeps the title/count to one
ellipsized line and puts exactly two actions at the end: a tonal Add icon
button ("New group") and a More overflow whose only item is Join with ID (the
paste sheet and its validation are unchanged). With six or more groups, an
inline search field appears under the header and filters rows live on group
name or latest preview text, case-insensitively and after trimming; invites
stay visible and unfiltered, and clearing restores the list. The empty hub
offers a New group button inside its panel; a query with no matches shows a
search-specific empty panel with no action. The fullscreen group image viewer
distinguishes loading, ready, and decode failure: loading is a centered
indicator on `surfaceContainerHigh`, corrupt bytes land on BrokenImage +
"Couldn't load this photo" with Retry and Close, the top chrome respects
status-bar insets, and Save is offered only after decoded pixels exist.
Avatars keep their initials fallback through the same loading/failure path.

**Chat screen.** Mirrors the 1:1 experience deliberately — same composer
shape, send-button anatomy, and bubble geometry (fixed per-side corner
shapes, uniform padding, width-capped bubbles with sender names), because the
model here is "a 1:1 you can multi-party". On top of that sit
group-exclusive pieces: in-timeline **system bubbles** generated from verified
log events, the unread separator with jump-to-latest FAB, reaction chips with
an emoji picker, long-press actions (quote, react, poll vote, close for
admins), full-history search (decrypts page-by-page; the server never sees a
plaintext lookup), draft persistence, and a degraded-history banner when the
log could not be fully verified. Seen-by info is available per message. An
empty conversation lands on a centered "No messages yet — say hello." panel
while the composer stays visible and usable; a search with no hits says "No
messages match your search." instead of leaving a blank list. Copy is a
text-only action: it is absent for image-only messages, poll cards, votes, and
close markers, and the "Copied" notice appears only after the clipboard write.
Admins close a poll through a confirmation dialog; a closed card keeps the
"Closed" label, disables every option, and exposes selected state to TalkBack
through `selectable` rows with 48 dp targets.

**Info screen.** The power surface: picture header with permission-gated
edit, description, rename (admin; enforced server-side too), add/invite with
multi-select and search, pending-invite management, join-request inbox
(approve → real invite), promote/demote/make-owner/remove/block/report, mute
with durations, per-group notification toggle, the settings card
(admin-only-send, member-edit, description), share sheet with QR of the group
ID, a shared-photos gallery, leave, and owner-flow disband. Actions are
admin-gated client-side AND server-side where the server can see the rule
(which is nearly everywhere structural). Names fall back through the shared
`You` / `Member` / `Group` / `Someone` strings; the hero picture and
description edits carry explicit action labels, the member-count and invite
chips wrap instead of overflowing at 200% font scale, and decorative avatars
are removed from the accessibility tree so the adjacent title is not
duplicated.

**Permissions v1 edge accepted:** invite mode is fixed at creation; QR/ID
join makes a *request*, not a token-link auto-join; message editing does not
exist (in 1:1 either); voice/video/documents require pipelines that neither
surface has yet. The group outbox drains on open rather than by worker, and
search decrypts up to 500 rows per query — documented costs, not hidden
ones.

**1.1.7 hardening (client surfaces).** The post-release review hardened
every group screen without touching the server plane:

- **Screenshot protection now covers the group surfaces.** Group chat and
  group info set `FLAG_SECURE`; the chat shares the 1:1's *server-gated*
  accessibility bypass (the same password-approved override, verified
  server-side by the 5-per-15-minutes edge check), while info reads plain
  (no override lane there — same choice as Daily Ups).
- **Save-to-gallery requires the same explicit confirm (S-3) the 1:1 viewer
  has.** A group image that leaves the E2EE boundary — chat-bubble
  fullscreen, hub picture, or shared-photos gallery — is confirmed by the
  user before any MediaStore write, reusing the 1:1 dialog.
- **Every structural write is same-frame double-tap-proof in the ViewModel**
  — group-card navigation, poll votes/closes and poll/photo send
  confirmations, reactions, creation, join-by-id requests, story deletion,
  permission saves, and every info-screen action
  (invite, promote, demote, transfer, remove, report, picture set, leave,
  disband, request approve/deny) hold a one-write-at-a-time guard, so two
  taps in one frame can never fire two signed events. Sheeted confirmations
  debounce on payload identity inside a short window instead of a shared
  busy flag, so a genuinely different send made while an upload is still in
  flight is never silently dropped. The stories-bar poster/compose lanes use
  the same ViewModel-owned navigation gate.
- **Media that fails stays honest:** a failed chat bubble or gallery thumb
  renders an explicit broken-image state (the gallery thumb is tap-to-retry);
  a failed gallery *list* shows an honest retry state instead of looking like
  "no shared photos"; a failed viewer story shows an inline retry instead of
  an eternal spinner. Decoded bitmaps hold bounded windows (8 in chat, 6 in
  the viewer) instead of growing unbounded across a session.
- **Notification taps route through validated actions** — including a
  "removed-from-group" tap landing on an explicit dead-end card — and group
  cards/coalesced loads/realtime merges were re-verified against the
  cross-screen races they opened.

---

## 12. Daily Ups

> [!NOTE]
> **Update Status:** Daily Ups are currently hidden from the UI and not accessible in this update (`WhisperDailyUpsConfig.ENABLED = false`). All backend infrastructure, database tables, edge functions, photo pipelines, and UI screen files are preserved intact for a future release.

Daily Ups are 24-hour **photo stories** posted to all friends at once — never
addressed to one person, capped at 5 per poster per UTC day, and bound to the
friend graph **at publish time** (friends added later literally have no row to
read; the server validates the audience against the live friend graph and the
block ledger).

### 12.1 What a story is

One photo + one fresh AES-256-GCM key. The photo is:
preprocessed, sealed (`nonce‖ciphertext` framing), and uploaded once to the
media host; the host returns a URL. The **descriptor**
`whisper:story:{v,u,k,x,r,a}` — version, host URL, base64 AES key, server
expiry, receipts-on flag, and the delete capability — is then sealed **per
friend** with the static-envelope machinery (the same v2 envelope from §3.2,
but statelessly: stories must never consume or perturb ratchet state, which is
precisely why they do not use frames). The poster's own copy is self-sealed
through the same machinery and carries the `a` capability, since a story's
owner retains delete rights no one else does.

Ids are deliberately absent from the descriptor: identifiers always come from
server rows, which are the source of truth (a client cannot fabricate an
identifier and have the server trust it).

### 12.2 The photo pipeline

Strictly fail-closed, rejecting rather than degrading:

- jpeg/png/webp sources up to 12 MiB;
- decode capped at 8 MP (4 MP on low-RAM devices), long edge scaled down to
  1920 px (aspect preserved, never upscaled);
- re-encoded to JPEG q82 (alpha sources become lossless PNG), with a plain
  byte cap before sealing;
- **EXIF/GPS stripping is verified, not assumed.** After re-encode, the output
  is *reopened and audited* for a fixed list of tags (make, model, dates,
  GPS latitude/longitude/altitude/timestamps). Any surviving tag fails the
  publish (`METADATA_REMAINS`). An instrumented test proves this on the exact
  publish bytes with a geotagged, device-labeled source photo.

### 12.3 The server plane

Rows: `whisper_stories` (poster, per-poster-unique `client_id`, host URL, an
opaque ledger key, receipts flag, state machine.published → delete_pending →
deleted / cleanup_failed, a trigger rewriting `expires_at` on insert so the
24-hour lifetime cannot be bent by any caller), `whisper_story_recipients`
(story + recipient, opaque sealed descriptor), `whisper_story_views`,
`whisper_story_reactions` (story + user, one toggleable emoji from a fixed
six-emoji whitelist rendered verbatim by the client), `whisper_story_cleanup`
(leases), and `whisper_story_publish_quota` (poster + UTC day, ≤5, enforced
on the row update itself).

Actions are all **version-gated** to the release floor (see §11.6 for what
that caveat is honestly worth — the value is client-supplied):
`whisper_story_publish` (atomic: story + audience + quota in one transaction;
idempotent on `client_id`, so a retry neither re-charges quota nor reshapes
the audience; bounded housekeeping in-transaction); `whisper_story_view`
(receipts-gated, idempotent); `whisper_story_react`
(receipts-**independent** — deliberately, so the poster's reaction wall works
even when their own viewing receipts are off; whitelist- and audience-checked);
`whisper_story_delete` (poster-only; state flip; RLS hides the instant flip);
read helpers + RLS mirrors. There is deliberately **no raw delete URL** on
story rows: the ledger key resolves through the image-ownership ledger, so
even a forged story row can only point at a hosting capability Supabase
itself recorded.

### 12.4 Viewing, receipts, reactions

The **hub** opens with a **Your day** card — my avatar, a live-up count
("`N` live today") or a one-line share prompt, `View` only when I have a live
up, `Add` always, and the day's quota line exactly once. Below it is one
64dp ring per live friend **poster** (not per story): the poster's picture
with the stable initials fallback, unseen rings bright, a count badge when
two or more are unseen, one line when nobody else has shared. Each ring is one
merged TalkBack node whose action label carries the name, live-story count,
and `seen`/`unseen` state; the ring and badge themselves are decorative. The
old full-width empty panel and separate quota chip are gone; a successful
empty day still shows the card, while a failed sync with no cache is an
explicit dead-end panel with Retry. Opening a ring
launches the in-place **viewer overlay**: a flat horizontal pager over an
immutable poster order, one progress segment per story of the current poster,
5-second ready-gated auto-advance, decrypt-on-demand images with next-story
preload, and one failure snackbar across hub + viewer. **View receipts
(optional per poster)**, reactions, and who deleted/expired what are all
resolved server-side (RLS-visible only for rows the viewer genuinely may see —
the poster sees reaction counts and names resolved on-device on demand).

**Poster-grouped viewing.** The viewer is ordered per open gesture: my card
opens only my own ups; a friend's ring starts with that friend and then the
other friends in rail order (never crossing into my stories); an owner-less
open (no UI entry today) keeps mine-first rail order. The selected poster
starts at its first unseen story, or at `startStoryId` when that story is
still live in the poster, else its first story. Each open is a snapshot; a
story that disappears underneath the reader closes the overlay instead of
letting them react into the void. "Seen" here is **device-local only** — an
opaque `storyId → expiresAtMs` entry capped at 512, filtered on load, pruned
from disk on every hub refresh and write, and cleared on account deletion. It
holds no names, account ids, media, or view receipts — only ids and expiry
timestamps (§6.2). It is ring state, not a synced read receipt: another device
may still show the ring bright, and the server learns nothing from it. A
friend's story is marked seen only once its decoded pixels are ready, the
pager has settled on it, and it is still current; failed/loading pages and my
own ups never write. The same moment sends at most one view receipt per open
(when the poster enabled receipts), which is separate from the local seen set.

**Viewer mechanics.** The overlay is a flat pager over the immutable order
built at open. The top progress row has one segment per story of the CURRENT
poster only: past solid, current animated, future a 25% track; its
accessibility state reads "Story x of y" over the whole open order. The header
carries the poster avatar, name (or "Your day" for my own), relative expiry
("3h left"), and a 48dp top-right X; status and navigation insets are
respected. A story's 5-second clock starts only when its image is decoded AND
the pager has settled; a spinner shows until then, and a failed image shows
honest inline copy + Retry with no timer. Holding anywhere on the image pauses
at the exact fraction and shows a `Paused` pill; release resumes with only the
remaining time, never from zero. Scrolling, swipe-down, and the delete-confirm
dialog also hold the clock. At the end of the last story the overlay closes —
no loop and no long delay. Next-story preload warms one frame inside the same
immutable order (segment boundary included), still inside the six-frame
decoded window.

**Gestures, stats, and accessibility.** Tap right two-thirds = next (closes
after the last story), left third = previous (clamps at the very first); taps
land on loading and failed pages too, while the header and bottom chrome
swallow their own taps. Next/Previous are exposed as custom accessibility
actions; each segment cross and every explicit close path (X, back, swipe,
end) ticks haptics. Swipe down
translates the content with damping past 280dp while the scrim fades; release
past 120dp or at >1000px/s closes, otherwise a spring returns it. The
horizontal pager keeps horizontal drags. On my own up, the bottom bar is inset
for navigation bars: receipts on → "N views" plus a reaction count ("No
reactions yet." at zero); receipts off → `View receipts are off for this up.`
and never a fabricated "0 views"; delete always keeps its confirm dialog. On a
friend's story, the reaction palette appears only after that image is ready
(never while loading or failed, so a reaction cannot claim a view that never
appeared), with six 48dp targets, `selected` semantics, and "React with
<emoji>" descriptions. The emoji glyphs themselves keep a 26dp physical size
at any font scale, so the six targets still fit at 200%. The `Paused` pill uses
`surfaceContainerHigh`/`labelSmall` as a fixed overlay, so pausing never shifts
the chrome.

**1.1.7 hardening (viewing surfaces).** The hub pulls to refresh — one
gesture, coalesced with the open-time sync and the re-open resync through a
single in-flight guard, so the indicator, the top-bar spinner and the gesture
share one truth (the hub content scrolls, which the pull also requires). A
stories-bar tap carries the owner into the hub route and opens that poster
exactly once after the first reconcile; a cold frame can never consume the
hand-off before Room has the rows, and a failed cold sync leaves it pending
for Retry. A story image that fails to open renders an inline "Couldn't load
this up." + Retry instead of a silent eternal spinner, with the failure
snackbar drawn *above* the fullscreen overlay so it is actually visible while
viewing. Reactions are double-tap-guarded (the second tap of one frame can no
longer toggle the reaction right back off), decoded viewer images hold a
bounded six-frame window (paging back re-decodes), and the hub's own/compose
lanes plus the Chats-bar poster/compose lanes use the same ViewModel-owned
navigation gate as the group cards.

### 12.5 Publishing

The composer opens from the hub's Add / Your-day actions and the groups
stories strip (the old Chats-tab entry row is gone). Its photo canvas mirrors
the viewer's 9:16 Fit framing on `surfaceContainerHighest`, with a 24 dp radius
and a 1 dp `outlineVariant` border — a letterboxed portrait is not cropped into
a different promise. Tapping the canvas re-opens the picker, and the bottom-end
"Tap to change" chip says so; the canvas is one merged accessibility node, so
TalkBack announces the chip together with the tap action. The pick is read with
a bounded 12-MiB stream (mirroring the pipeline), decoded into an in-memory
oriented preview, and never written to disk before the publish pipeline.
Receipts stay an explicit switch.

Audience is **all current friends minus explicit exclusions**. A summary row
always shows the final count and opens a searchable modal sheet with avatar,
checkbox rows, Select all and Done; sheet edits write straight to the
ViewModel. Loading is a small inline spinner, a failed load is an honest retry
row, and zero friends is distinct from either. At 200% font scale friend names
wrap to two lines and the list takes only the sheet's remaining height, so
Select all and Done stay reachable. The primary action is pinned in the
Scaffold bottom bar with the quota label; it keeps a 52dp minimum height and
grows with the text instead of clipping. Its own label never changes
(`Share`), publishing swaps in a spinner + `Sharing…`, and a short reason line
appears only for the first blocker in Busy → NoPhoto → NoAudience → NoQuota
order (missing photo, an all-excluded audience, or exhausted quota — each
stated exactly once). The content scrolls above it, so the action cannot be
pushed off-screen.

Back (system or top bar) on a picked draft opens Discard / Keep editing instead
of silently dropping it; a successful publish clears the guard and hands off
through `onPublished`, which lands on the hub with a confirmation. Upload
chain: preprocess → seal → host → one sealed leg per friend → publish RPC →
self-sealed own row → opportunistic sweep kick. Any failure up-chain maps to a
fixed vocabulary of errors; a failure *before* the publish RPC leaves zero
server truth (nothing partial ever exists). Retries keep the draft. A
friends-list load that fails in the audience step shows an honest retry state
instead of reading as "no friends yet", and a failed photo decode cancels the
previous decode so the last pick always wins (two quick picks could otherwise
land the slower, older image into the draft).

### 12.6 Cleanup and honest notes

Expiry/deletion goes through two paths that reinforce each other: the state
flip hides the story instantly (RLS), and the **sweep worker** (service role,
lease-based with SKIP LOCKED, 10-minute leases, ≤20 attempts with backoff)
converges the hosted copy to deletion through the ownership ledger — no
database cron dependency. Deleted/expired stories prune locally even when the
sweep is unreachable.

Honesty notes: the `a` capability travels inside the descriptor and Room rows;
a viewer who somehow acquired it still cannot delete (poster-only RPC +
ledger-only sweep), so capability possession is not authority. Quota is
server-enforced; a stale local counter can neither permit nor deny a publish.
And: received ups keep their sealed bytes for the 24 hours, and there is no
"keep forever" tier — 24 hours is the whole contract, for everyone. W6's
accessibility and font-scale pass changed no wire format, RLS policy, or server
endpoint: the seen index stays device-local, and view receipts remain the
optional per-poster server row described above.

Story push pings carry poster ID and story ID; the poster's name rides only inside
encrypted Web Push payloads on the FCM-fallback path (external distributors
get IDs and resolve the name locally) — content-free either way.

---

## 13. How it is verified

Whisper's cryptography is pinned with a deliberately paranoid test stack:

- **External reference vectors** — every expected value in the core crypto
  tests comes from a specification or an independent implementation (RFC
  7748 curve vectors in the X3DH arrangement, RFC 5869 Appendix A HKDF cases,
  cross-checked Double-Ratchet KATs, wire-cutoff boundaries). Nothing is
  verified against this repo's own output.
- **Security-matrix tests** for the ratchet (out-of-order, replay,
  reinstall desync, key-change strictness, skipped-limit behavior, and the
  forged-header-no-mutation property from §3.4), plus a chaos suite that
  asserts zero wrong-plaintext openings.
- **Pure-decision pins** for version negotiation, rotation certs
  (counter/timestamp anti-replay), and the handshake-failure block.
- **Self-interop honesty:** the interop suite only proves *this codebase*
  talks to *itself*; a systematic framing bug shared by both ends would pass
  there unnoticed — that gap is why the external-vector suite exists, and why
  broader signal-compat review remains on the roadmap (§14.5).
- **Model invariants & UI plumbing** — serialization round-trips, ordering,
  tombstone behavior, chat reactions, error vocabularies.
- **UI navigation and honest-state pins.** `WhisperUiGuardsTest` fixes the
  1.2 s `ElapsedTapGate` window (first accept, same-frame reject, exact
  boundary accept, boundary-minus-one reject, reset re-arm). The gate is owned
  by the Groups and Daily Ups view models, so a double tap cannot push the same
  destination twice across recompositions, and neither screen carries a
  composition-locked timer anymore. Hub sync failures render inline (failure
  panel with no cache, banner over stale content with a cache); only
  viewer-scoped failures reach the snackbar.
- **Compose share-block pins.** `WhisperComposeCtaTest` fixes the pinned
  CTA precedence — Busy (publishing or published) wins over NoPhoto, which wins
  over NoAudience, which wins over NoQuota; only a ready draft returns None.
  This is pure state math, no ViewModel or device required.
- **Groups hub search pins.** `WhisperGroupSearchTest` fixes the pure predicate:
  blank/whitespace always passes, name matches ignore case and surrounding
  space, the latest preview matches, and an unrelated query rejects.
- **Group chat copy pins.** `WhisperGroupChatCopyTest` fixes the text-only
  clipboard predicate: a non-blank body is copyable; blank or whitespace-only
  bodies are not. The screen combines that predicate with poll/vote/close
  exclusion, and the action sheet takes the resulting flag rather than
  re-deriving it.
- **W6 accessibility, font-scale, and recomposition review.** Both Daily Ups
  rails merge each ring into one node whose action label carries the name,
  live-story count and seen state; composer previews announce the "Tap to
  change" chip as part of the canvas; group avatars are decorative beside their
  titles; and the info hero actions carry explicit labels. At 200% font scale
  the share CTA grows from a 52dp minimum, audience names wrap, the info chips
  wrap, and the six reaction emoji keep a 26dp physical size. The hub's
  own/friend split is remembered per `posterGroups` emission, and the viewer's
  progress fill lives in its own row, so the pager and chrome do not recompose
  on every animation frame. This is a static code/constraint audit, not a
  device frame-rate measurement; the six-frame decoded viewer window and
  one-frame preload are unchanged, and no performance numbers are promised
  from it.
- **Instrumented media test** for Daily Ups: a fully geotagged, device-labeled
  test photo must publish with *zero* surviving EXIF/GPS tags on the exact
  publish bytes — device-only, like all Bitmap work.
- **An enforced per-commit gate for UI work.** The October 2026 hardening
  cycle (six rounds over the group/Daily-Ups screens and their shared
  loaders) runs on a fixed invariant: every round compiles with
  `:app:compileDebugKotlin` and finishes with the same seven pre-existing
  failures (all in unrelated engine suites, none Whisper-related). The frozen
  starting point was 530 tests; each wave records its exact total in its gate
  log, and no new name may fail. W5 and W6 land at 561 tests with the same
  frozen seven (W6 adds no tests, so the total is unchanged). Nothing
  behavioral lands outside that gate;
  the SQL/edge surfaces were deliberately untouched and still belong to the
  ship-day runbook (`supabase/POST_DEPLOY_SMOKE.md`).
- **Server-plane invariants unchanged by the cycle.** Client-side hardening
  changed no cryptography and no SQL: quotas (600/day fan-out, 5/day/IP
  story, 5/day/IP signup), signed-log verification, epochs, and descriptor
  shapes are byte-identical to the release cut — the freeze was deliberate.
- **Regression cover from the October 2026 locked-message defect (§3.9).** The
  existing suite pins copy-commit ratchet behavior and the merger's
  locked → plaintext rule. The fix added `WhisperSelfReadStoreTest`, which
  exercises the durable self-read path through its sealing seam: a copy written
  at send time is recalled by message id after a cold start, is unreadable by
  another owner account, resolves conversation previews by wire-ciphertext
  hash, rejects (and prunes) a sealed blob moved onto another message id, is
  forgotten on delete, and is bounded by `MAX_PER_OWNER`. The account-scoping
  side is covered by `WhisperSessionFilesTest`, which pins the on-disk layout
  and the upgrade rules with real files: two accounts on one install each adopt
  the legacy snapshot whose peer they are, a file mis-swept into the peer's own
  directory is reclaimed by the counterpart account, an existing scoped file is
  never overwritten, and nothing is adopted for a self-peer. The store itself
  still needs an Android context and a Supabase client, so its wiring is
  covered by the compile plus the Whisper suites; the file-attribution rules
  that caused the account-switch regression are now tested directly instead of
  passing by omission.

---

## 14. Limits, trade-offs, and the honest security statement

A few design constraints documented as *decisions*, not surprises:

1. **Ratchet gap-loss residual.** Forged headers and far-future counters
   fail closed *before* any state mutation, so an attacker cannot spend
   honest keys. A residual remains: a malicious server can force a
   gap-handshake re-connection attempt at most once per 30 s per peer — a
   bounded availability annoyance, not a confidentiality or loss path.
2. **Group fan-out budget (600/day/sender)** is a working calibration, not a
   limitation: ~50 messages/day in a full 12-member group, more in smaller
   ones — generous chat volume; what it curbs is scripted 1→N spam. The knob
   is one SQL value plus a mirrored client constant, owned by the operator
   and surfaced honestly to users whose group hits it (a daily-limit message,
   not an error).
3. **Signup gate (5/day/IP)** is the only server-side brake on Sybil
   accounts, appropriate for a friends-only audience. Real-world caveat:
   carrier CGNAT can put many genuine users behind one IP; the remediation is
   a function-side allow-list of that carrier range if the logs ever show it.
4. **Group signing keys and reinstalls** were resolved in 1.1.7 by the
   account-owned signer history (§11.1). Residual: the trust boundary of that
   set is served by the *bundle endpoint*, same as 1:1 — the operator is the
   boundary of last resort; but content forgeries still require signing keys
   the operator never has. Pre-1.1.7 dev groups may keep unresolvable
   histories from before the archive trigger existed (accepted: groups were
   not public before this release). A candidate hardening for a later
   release, recorded here so it isn't lost: seal the verified log head
   (sequence number + hash) inside the AEAD plaintext next to groupId and
   epoch, so members could *detect* a divergent view of the log (the
   truncation residual §2.1 admits). Not implemented in 1.1.7.
5. **External review before any "audited" claim.** Whisper uses a custom
   end-to-end construction; it must not be publicly described as
   "audited"/"secure" until either a third-party security review or a
   libsignal production-parity audit happens. Until then the honest copy is
   "end-to-end encrypted", designed as described above, with its residual
   limits listed — and an explicit honesty note in-app: uses *custom E2EE,
   not independently audited*. This document's job is to state the
   architecture plainly enough that such a review can find its way around.
6. **Unconditional presence and typing is the design's most debatable
   product stance.** Last-seen, typing, and read receipts are deliberately
   uniform — no per-user opt-out (§8.1). Every serious reviewer will cite
   this first against a privacy messenger, and that is acknowledged rather
   than buried here: the reversal path exists (the opt-out columns were once
   in the schema and can be re-introduced without touching the crypto),
   nothing in §2.1's "never visible" list depends on these signals being
   unconditional. It is a product calibration call, owned by the operator,
   not an architectural constraint.
7. **Locked-message field report (October 2026) — fixed, with residuals.**
   The report was that 1:1 history could render "This message can no longer be
   decrypted on this device" for every row, that own messages flickered, and
   that switching accounts locked everything. Three independent causes stacked
   (§3.9) and all three were fixed in the same cycle: a persistent, per-owner,
   Keystore-sealed self-read copy for the user's own sends (written before the
   network insert, so no echo can race it); own-row resolution ahead of any
   ratchet attempt on the history, realtime and conversation-preview paths; and
   account-scoped session files and in-memory ratchets with a pinned scope on
   every live session, lazy per-peer adoption of the pre-scoping files (a
   whole-directory sweep is what handed one account the other's sessions), and
   same-install handshake acceptance so the account-switch pair survives a
   session restart, plus a sign-out that clears those in-memory chains and
   the sent-plaintext memo. The key trust store intentionally remains
   install-wide, because peer identity keys are not account state. Residuals
   are stated honestly rather than hidden: rows sent before this build have no
   self-read copy and cannot be recovered if their ratchet state is already
   gone, and the chat still has no explicit "decrypting" state, so a received
   row can briefly show the locked marker before its first successful decrypt
   (the merger heals it).

Deployment/operational runbooks (rollout, smoke tests, edge redeploys) live
outside this document, on purpose: `supabase/POST_DEPLOY_SMOKE.md` and the
release checklist cover them.

---

## Appendix A — file map

Paths relative to `app/src/main/java/com/frerox/toolz/`.

**Core data — `data/whisper/`:**

`WhisperRepository.kt` (orchestrator) + `WhisperRepositoryGroups.kt`,
`WhisperRepositoryStories.kt`, `WhisperRepositorySocial.kt`,
`WhisperRepositoryProfile.kt` · `WhisperCrypto.kt` (hardware identity),
`WhisperSelfLegCrypto.kt` (self-leg anchor), `session/` (`WhisperRatchet`,
`WhisperSessionFactory`, `WhisperSessionStore`, `WhisperSessionFiles`
(account-scoped on-disk layout + legacy adoption),
`WhisperSessionSecretProtector`, `WhisperV3Codec` in `data/whisper/session/`),
`WhisperEnvelope.kt` (wire codecs), `WireProtocol.kt` (pure decisions),
`WhisperPrekeyManager.kt`, `WhisperKeyTrustStore.kt`,
`WhisperKeyRotationStore.kt`, `WhisperProtocolConfig.kt`,
`WhisperAuthManager.kt`, `WhisperModels.kt` (shared data models),
`WhisperGroupEntities.kt`, `WhisperGroupPolicy.kt`,
`WhisperGroupNotifications.kt`, `WhisperGroupReadStore.kt`,
`WhisperStorySeenStore.kt` (device-local Daily Ups seen set + pure codec),
`WhisperStoryLocalEntity.kt`, `WhisperDailyUpPhotoPreprocessor.kt`,
`WhisperOutgoingQueue.kt` (Room outbox + delivery), `WhisperDeliveryScheduler.kt`,
`WhisperMessageDao.kt`/`WhisperMessageEntity.kt`/
`WhisperLocalTombstoneEntity.kt`,
`WhisperSelfReadStore.kt` (device-sealed self-read copies for own sends +
`WhisperSelfReadSealer` seam), `WhisperEncryptedImageHost.kt`,
`WhisperImageCipherTransport.kt` (WZ1 container), `WhisperImagePreprocess.kt`,
`WhisperAvatarCodec.kt`, `WhisperAvatarLoader.kt`,
`WhisperImageDiskCache.kt`, `WhisperDeletedMessagesStore.kt`,
`WhisperHiddenChatsStore.kt`, `WhisperMutePreferences.kt`,
`WhisperUndoBufferStore.kt`, `WhisperAubupManager.kt` (access-file backup),
`WhisperNotificationManager.kt`, `WhisperErrorMapper.kt`,
`WhisperPinConfig.kt` (identity constants), `PlayIntegrityAttestor.kt`,
`EdgeFunctionClient.kt`, `EncryptedBlobHost.kt`, `ProtocolDiagnostics.kt`.

**UI — `ui/screens/whisper/`:** `WhisperMainScreen.kt` (hub: chats/friends/
requests/discover/profile tabs) + `WhisperMainScreenComponents.kt` (row
cards), `ChatMessageMerger.kt`; `WhisperChatScreen.kt` +
`WhisperChatViewModel.kt` (1:1 chat), `WhisperGroupChatScreen.kt` +
`WhisperGroupChatViewModel.kt` + `WhisperGroupChatParts.kt` (group chat),
`WhisperGroupInfoScreen.kt` + `WhisperGroupInfoViewModel.kt` +
`WhisperGroupInfoParts.kt`, `WhisperGroupPermissionsScreen.kt` +
`WhisperGroupPermissionsViewModel.kt`, `WhisperGroupUiKit.kt`,
`WhisperGroupsSection.kt` + `WhisperGroupsViewModel.kt` (Chats-tab section),
`WhisperDailyUpsScreen.kt` + `WhisperDailyUpsViewModel.kt`,
`WhisperDailyUpsComposeScreen.kt` + `WhisperDailyUpsComposeViewModel.kt`,
`WhisperDailyUpsStoriesBar.kt`, `WhisperStoryGrouping.kt` (pure poster
grouping, rail order, viewer order), `WhisperProfileTab.kt`,
`WhisperUserProfileScreen.kt` + `WhisperUserProfileViewModel.kt`,
`WhisperVerifyQrDialog.kt`, `WhisperAuthScreen.kt` +
`WhisperAuthViewModel.kt`, `WhisperOnboardingScreen.kt`,
`WhisperBuildGate.kt`, `WhisperBypassValidator.kt`,
`BypassScreenshotHandler.kt`, `SecureWindow.kt`, `WhisperToast.kt`,
`WhisperPermissionBanner.kt`.

**Push — `push/`:** `WhisperPushService.kt`, `WhisperPushRegistrar.kt`,
`WhisperPushNotifier.kt`.

**Backend — `supabase/`:** `migrations/main-whisper-sql.sql` (canonical
baseline), `functions/` (see §8.2), `whisper-sql-info.md`,
`POST_DEPLOY_SMOKE.md` (ops runbook).

**Workers:** `WhisperDeliveryWorker.kt`, `WhisperLocalCleanupWorker.kt`,
`WhisperClipboardClearWorker.kt`.

---

## Appendix B — glossary

| Term | Meaning here |
|---|---|
| **AAD** | Additional authenticated data — extra bytes bound into AEAD ciphertext so a valid ciphertext cannot be replayed into a different context |
| **Bundle** | A peer's published key package: identity binding, signed SPK, an OPK |
| **Descriptor** | The sealed, self-contained story reference: photo URL + key + expiry + receipts flag (+ poster's delete capability) |
| **Envelope (v2)** | The static-identity multi-key sealed body; self-healing, no forward secrecy |
| **Epoch** | A group's membership version; every message is sealed for exactly one |
| **Event log** | The admin-signed sequence that defines group membership and permissions |
| **Fan-out** | Sealing one group message once per recipient (≤12 members) |
| **Frame (v3)** | A Double Ratchet message; forward-secretive |
| **GoTrue** | Supabase's auth service, here fronted by username/token facades |
| **OPK / SPK** | One-time prekey / signed prekey in a peer's bundle |
| **RLS** | Row-level security — Postgres policies that decide which rows a database identity may touch |
| **Realtime publication** | The set of tables whose changes stream to subscribed clients |
| **Remote wake ping** | A push notification carrying no content — only IDs + event kinds (encrypted on FCM-fallback hops) |
| **ratchet lost / placeholder** | The honest UI state for a message that cannot open because part of the session history is unrecoverable |
| **Self-read store** | `whisper_self_read` (Room v66) + `WhisperSelfReadStore` — the durable, Keystore-sealed copy of this device's own sent plaintext, keyed by `(ownerId, messageId)` and bound to the message id; the in-process `sentV3PlaintextMemo` is only its hot-path cache (§3.9) |
| **Static envelope** | §3.2's v2 sealed body; called "static" because it uses pinned long-term identities |
| **TOFU** | Trust-on-first-use — pin the first key seen; subsequent changes are fail-closed |
| **Web Push (RFC 8291/8292)** | Standard browser-push encryption/auth; used on the FCM-fallback hop |
| **Whisper code (access file)** | The credential-only backup artifact for account moves, restored via deep link or vault |
