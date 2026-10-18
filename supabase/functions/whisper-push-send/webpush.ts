// WebPush (RFC 8290 "aes128gcm" content coding + RFC 8291 message encryption)
// and VAPID (RFC 8292) helpers for whisper-push-send.
//
// Everything is WebCrypto (crypto.subtle) — no external imports, one auditable
// file. Used to deliver Whisper wake pings through the embedded-fcm-distributor
// (and any other RFC 8291-capable UnifiedPush distributor):
//   POST <endpoint>
//     Authorization: vapid t=<JWT>, k=<ecdsa public point>
//     Content-Encoding: aes128gcm
//     TTL: 86400
//     Urgency: high
//   body: RFC 8290 payload = header(86) || AES-128-GCM(encrypted plaintext)
//
// The wake-ping is an RFC 8291-ENCRYPTED blob: the push service (Google's FCM
// endpoint included) cannot read even the id/name keys of the payload.

/** RFC 8291 key set returned by the app at registration (base64url). */
export interface WebPushKeys {
  /** 65-byte uncompressed P-256 subscriber public key, base64url. */
  p256dh: string;
  /** 16-byte authentication secret, base64url. */
  auth: string;
}

/** RFC 8292 signer with (x, y, d) as base64url JWK components. */
export interface VapidJwk {
  x: string;
  y: string;
  d: string;
}

// ── base64url / bytes ─────────────────────────────────────────────────────────

function utf8(s: string): Uint8Array {
  return new TextEncoder().encode(s);
}

export function b64uToBytes(s: string): Uint8Array {
  const norm = s.replace(/-/g, "+").replace(/_/g, "/");
  // Deno/browsers provide base64url-tolerant atob only after padding fix; do it manually.
  const padded = norm + "=".repeat((4 - (norm.length % 4)) % 4);
  const raw = atob(padded);
  const out = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out;
}

export function bytesToB64u(bytes: Uint8Array): string {
  let raw = "";
  for (const byte of bytes) raw += String.fromCharCode(byte);
  return btoa(raw).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function concat(...arrays: Uint8Array[]): Uint8Array {
  const out = new Uint8Array(arrays.reduce((n, a) => n + a.length, 0));
  let offset = 0;
  for (const a of arrays) {
    out.set(a, offset);
    offset += a.length;
  }
  return out;
}

// ── HKDF / ECDH primitives ────────────────────────────────────────────────────

/** HKDF-SHA-256 (RFC 5869) via WebCrypto; `length` in bytes. */
async function hkdf(
  salt: Uint8Array,
  ikm: Uint8Array,
  info: Uint8Array,
  length: number,
): Promise<Uint8Array> {
  const key = await crypto.subtle.importKey("raw", ikm, "HKDF", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits(
    { name: "HKDF", hash: "SHA-256", salt, info },
    key,
    length * 8,
  );
  return new Uint8Array(bits);
}

/** JWK for the subscriber public key carried as an uncompressed point. */
function subscriberJwk(pubPoint65: Uint8Array): JsonWebKey {
  if (pubPoint65.length !== 65 || pubPoint65[0] !== 0x04) {
    throw new Error("keys_p256dh is not a 65-byte uncompressed P-256 point");
  }
  return {
    kty: "EC",
    crv: "P-256",
    x: bytesToB64u(pubPoint65.subarray(1, 33)),
    y: bytesToB64u(pubPoint65.subarray(33, 65)),
    ext: true,
  };
}

// ── RFC 8290 + RFC 8291 encryption ───────────────────────────────────────────

const RECORD_SIZE = 4096;
// aes128gcm overhead: 16 (tag) + 1 (padding delimiter) per record; the binary
// header prefixes the body: salt(16) || record-size(4) || idlen(1) || keyid(65).
const RECORD_OVERHEAD = 17;
// RFC 8290 §2 + RFC 8291 §4.1: the sender's ephemeral ECDH public key rides in
// the keyid field (65-byte uncompressed point) — the convention node web-push,
// pywebpush/http-ece and Chromium all interoperate on.
const HEADER_LENGTH = 21 + 65;

/**
 * Encrypts one wake-ping to a subscriber (RFC 8291), encoded as aes128gcm
 * (RFC 8290). Small payloads are always a single padded record.
 */
export async function encryptWebPush(
  payload: Uint8Array,
  keys: WebPushKeys,
): Promise<Uint8Array> {
  if (payload.length > RECORD_SIZE - RECORD_OVERHEAD) {
    throw new Error("wake ping larger than one aes128gcm record — refusing");
  }
  const subscriberPublicKey = b64uToBytes(keys.p256dh);
  const authSecret = b64uToBytes(keys.auth);
  if (authSecret.length !== 16) {
    throw new Error("keys_auth is not a 16-byte authentication secret");
  }

  // RFC 8291 §4.1: ephemeral ECDH against the subscriber public key.
  const subscriberKey = await crypto.subtle.importKey(
    "jwk",
    subscriberJwk(subscriberPublicKey),
    { name: "ECDH", namedCurve: "P-256" },
    false,
    [],
  );
  const ephemeral = await crypto.subtle.generateKey(
    { name: "ECDH", namedCurve: "P-256" },
    true,
    ["deriveBits"],
  );
  const rawSharedSecret = new Uint8Array(
    await crypto.subtle.deriveBits(
      { name: "ECDH", public: subscriberKey },
      ephemeral.privateKey,
      256,
    ),
  );
  const ephemeralPublicKey = new Uint8Array(
    await crypto.subtle.exportKey("raw", ephemeral.publicKey),
  );

  // RFC 8291 §4.1 "key derivation": mix the ecdh output with the auth secret.
  //   ikm = HKDF(salt = auth, ikm = NZ(ecdh), info = "WebPush: info" || 0x00
  //              || subscriberPublicKey || ephemeralPublicKey, L = 32)
  const keyInfo = concat(
    utf8("WebPush: info"),
    Uint8Array.of(0x00),
    subscriberPublicKey,
    ephemeralPublicKey,
  );
  const ikm = await hkdf(authSecret, rawSharedSecret, keyInfo, 32);

  // Fresh per-message salt (RFC 8290: aes128gcm salt travels in the header).
  const salt = crypto.getRandomValues(new Uint8Array(16));
  // RFC 8291 §4.2 content encryption key + nonce:
  //   cek   = HKDF(salt, ikm, info = "Content-Encoding: aes128gcm" + 0x00, L = 16)
  //   nonce = HKDF(salt, ikm, info = "Content-Encoding: nonce"         + 0x00, L = 12)
  const contentEncryptionKey = await hkdf(
    salt,
    ikm,
    concat(utf8("Content-Encoding: aes128gcm"), Uint8Array.of(0x00)),
    16,
  );
  const nonce = await hkdf(
    salt,
    ikm,
    concat(utf8("Content-Encoding: nonce"), Uint8Array.of(0x00)),
    12,
  );

  // RFC 8188 §2 record framing: plaintext || 0x02 delimiter || zero padding.
  // No AAD: "The additional data passed to each invocation of AEAD_AES_128_GCM
  // is a zero-length octet sequence." (this is what node web-push/http-ece do).
  const padded = new Uint8Array(payload.length + 1);
  padded.set(payload, 0);
  padded[payload.length] = 0x02;

  const ciphertext = new Uint8Array(
    await crypto.subtle.encrypt(
      { name: "AES-GCM", iv: nonce, tagLength: 128 },
      await crypto.subtle.importKey("raw", contentEncryptionKey, "AES-GCM", false, ["encrypt"]),
      padded,
    ),
  );

  // RFC 8188 header: salt(16) || rs(4, BE) || idlen(1)=65 || ephemeral pubkey(65).
  const header = new Uint8Array(HEADER_LENGTH);
  header.set(salt, 0);
  new DataView(header.buffer).setUint32(16, RECORD_SIZE);
  header[20] = 0x41;
  header.set(ephemeralPublicKey, 21);
  return concat(header, ciphertext);
}

// ── RFC 8292 VAPID ───────────────────────────────────────────────────────────

/**
 * Builds the RFC 8292 Authorization header for one endpoint:
 * `vapid t=<ES256 JWT>, k=<uncompressed public point>`.
 * WebCrypto's ECDSA sign already returns r||s (IEEE P1363), the format the
 * spec requires — no DER conversion needed.
 */
export async function vapidAuthorizationHeader(
  endpoint: string,
  jwk: VapidJwk,
  subject: string,
): Promise<string> {
  const audience = new URL(endpoint).origin;
  const expiration = Math.floor(Date.now() / 1000) + 12 * 3600; // RFC 8292: ≤ 24h
  const protectedHeader = bytesToB64u(
    utf8(JSON.stringify({ typ: "JWT", alg: "ES256" })),
  );
  const claims = bytesToB64u(
    utf8(JSON.stringify({ aud: audience, exp: expiration, sub: subject })),
  );
  const signingInput = `${protectedHeader}.${claims}`;

  const privateKey = await crypto.subtle.importKey(
    "jwk",
    { kty: "EC", crv: "P-256", x: jwk.x, y: jwk.y, d: jwk.d },
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["sign"],
  );
  const signature = new Uint8Array(
    await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, privateKey, utf8(signingInput)),
  );
  if (signature.length !== 64) {
    throw new Error(`unexpected ES256 signature length ${signature.length}`);
  }

  const publicPoint = new Uint8Array(65);
  publicPoint[0] = 0x04;
  publicPoint.set(b64uToBytes(jwk.x), 1);
  publicPoint.set(b64uToBytes(jwk.y), 33);
  return `vapid t=${signingInput}.${bytesToB64u(signature)}, k=${bytesToB64u(publicPoint)}`;
}

/** Parses and shape-checks WHISPER_VAPID_JWK; null when absent from env. */
export function parseVapidJwk(raw: string | undefined): VapidJwk | null {
  if (!raw || raw.trim().length === 0) return null;
  try {
    const parsed = JSON.parse(raw) as Partial<VapidJwk>;
    if (
      typeof parsed.x !== "string" || typeof parsed.y !== "string" || typeof parsed.d !== "string"
    ) {
      console.error("WHISPER_VAPID_JWK is malformed (needs x, y, d as base64url strings)");
      return null;
    }
    return { x: parsed.x, y: parsed.y, d: parsed.d };
  } catch (error) {
    console.error("WHISPER_VAPID_JWK is not valid JSON", error);
    return null;
  }
}
