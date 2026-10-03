// Supabase Edge Function: upload encrypted Whisper image bytes to ImgBB.
import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
// V2-FIX (reviewwhisper.md): module-level declarations were spliced INSIDE the import
// braces below (invalid syntax); the helper now lives under the completed import.
import { createRemoteJWKSet, jwtVerify } from "https://esm.sh/jose@5.9.6";
// -- INLINED from _shared/attest.ts (dashboard deploys are single-file;
//    keep in sync with the canonical copy) --
// Shared anti-mod attestation gate for Whisper edge functions.
//
// Server-side counterpart of PlayIntegrityAttestor.kt (Android client).
// Runs WITHOUT any Google Cloud key: strict package + signing-cert + version
// check driven purely by request headers vs function secrets.
//
// Required secrets (supabase secrets set):
//   OFFICIAL_PACKAGE      e.g. "com.frerox.toolz" (default when unset)
//   OFFICIAL_CERT_SHA256  upper/lower/colon-separated accepted; enforced only
//                         when set (unset = warn + pass cert check)
//   MIN_VERSION_CODE      e.g. "17"; enforced only when > 0
// Optional (Play Integrity verdict path, used by whisper-attest):
//   PLAY_INTEGRITY_ENABLED  "true" to ALSO require X-Play-Integrity-Token
//                           presence (JWT verdict is verified server-side
//                           in whisper-attest; this shared gate only
//                           checks presence). OPTIONAL AND DISABLED BY
//                           DEFAULT: the enforced baseline is
//                           cert + package + version ONLY — no Google
//                           JWT required, no Google API dependency.
//                           Keep it unset/"false" for the FOSS-safe
//                           GitHub-APK build: the local-only Android
//                           attestor (PlayIntegrityAttestor.kt) never
//                           sends the token, so enabling this would
//                           reject every current client.
//
// Import from function code with a relative path:
//   import { attestBlockedResponse } from "../_shared/attest.ts";
//
// Usage (3 lines at the top of serve(), AFTER auth, BEFORE any quota/RPC):
//   const blocked = await attestBlockedResponse(request, { userId });
//   if (blocked) return blocked;
//
// Denied callers get HTTP 428 + { error, attest:false } and a best-effort row
// in public.whisper_attest_log (see supabase/functions/whisper-attest/README.md
// for the table SQL — intentionally NOT appended to main-whisper-sql.sql here
// to avoid migration-file ownership collisions).

export const ATTEST_BLOCK_MESSAGE =
  "Unofficial build blocked - install official Toolz APK";

export const HEADER_INTEGRITY_TOKEN = "x-play-integrity-token";
export const HEADER_PACKAGE = "x-app-package";
export const HEADER_CERT = "x-app-cert-sha256";
export const HEADER_VERSION = "x-app-versioncode";

export interface AttestVerdict {
  ok: boolean;
  reason: string;
}

function normCert(raw: string): string {
  return raw.replace(/:/g, "").replace(/\s+/g, "").toUpperCase();
}

/** Pure header check — no I/O, safe to unit-test. */
export function verifyAttestHeaders(req: Request): AttestVerdict {
  const officialPkg =
    Deno.env.get("OFFICIAL_PACKAGE") ?? "com.frerox.toolz";
  const officialCert = normCert(Deno.env.get("OFFICIAL_CERT_SHA256") ?? "");
  const minCode = parseInt(Deno.env.get("MIN_VERSION_CODE") ?? "0", 10) || 0;

  const pkg = (req.headers.get(HEADER_PACKAGE) ?? "").trim();
  const cert = normCert(req.headers.get(HEADER_CERT) ?? "");
  const vcode = parseInt(req.headers.get(HEADER_VERSION) ?? "0", 10) || 0;

  if (!pkg || pkg !== officialPkg) {
    return { ok: false, reason: `package_mismatch:${pkg.slice(0, 80)}` };
  }
  if (officialCert) {
    if (!cert || cert !== officialCert) {
      return { ok: false, reason: "cert_mismatch" };
    }
  } else {
    console.warn("attest: OFFICIAL_CERT_SHA256 unset — cert check skipped");
  }
  if (minCode > 0 && vcode < minCode) {
    return { ok: false, reason: `stale_version:${vcode}<${minCode}` };
  }
  // Optional Play token presence check — DISABLED BY DEFAULT (see header
  // comment). Full JWT verdict verification lives in whisper-attest (needs
  // the Google API call); the per-function gate only enforces presence so a
  // token-stripped replay cannot silently downgrade to headers-only.
  if ((Deno.env.get("PLAY_INTEGRITY_ENABLED") ?? "") === "true") {
    const token = (req.headers.get(HEADER_INTEGRITY_TOKEN) ?? "").trim();
    if (!token) return { ok: false, reason: "integrity_token_missing" };
  }
  return { ok: true, reason: "ok" };
}

/** Best-effort denial log. Never throws, never blocks the caller. */
export async function logAttestDenied(
  supabaseUrl: string,
  serviceKey: string,
  entry: { reason: string; userId?: string; pkg?: string; version?: string },
): Promise<void> {
  try {
    await fetch(`${supabaseUrl}/rest/v1/whisper_attest_log`, {
      method: "POST",
      headers: {
        apikey: serviceKey,
        Authorization: `Bearer ${serviceKey}`,
        "Content-Type": "application/json",
        Prefer: "return=minimal",
      },
      body: JSON.stringify({
        user_id: entry.userId ?? null,
        reason: entry.reason.slice(0, 120),
        pkg: (entry.pkg ?? "").slice(0, 160),
        version_code: entry.version ?? null,
      }),
      signal: AbortSignal.timeout(5000),
    });
  } catch (e) {
    // Logging must never fail the request path.
    console.error("attest log failed (best-effort)", e instanceof Error ? e.message : e);
  }
}

function attestJson(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": "*",
      "Cache-Control": "no-store",
    },
  });
}

/**
 * Returns a 428 Response when the caller fails attestation, else null.
 * Pass the already-verified userId (when the function has one) for the log row.
 */
export async function attestBlockedResponse(
  req: Request,
  opts: { userId?: string } = {},
): Promise<Response | null> {
  const verdict = verifyAttestHeaders(req);
  if (verdict.ok) return null;
  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  if (supabaseUrl && serviceKey) {
    // Fire-and-forget is wrong here (isolate may freeze); await with its own timeout.
    await logAttestDenied(supabaseUrl, serviceKey, {
      reason: verdict.reason,
      userId: opts.userId,
      pkg: req.headers.get(HEADER_PACKAGE) ?? "",
      version: req.headers.get(HEADER_VERSION) ?? "",
    });
  }
  console.error("attest denied", verdict.reason);
  return attestJson(
    { error: ATTEST_BLOCK_MESSAGE, attest: false, reason: verdict.reason },
    428,
  );
}
// -- END inlined attest helper --


// Lazily-built JWKS verifier: building at module-eval time would throw on a cold
// start if SUPABASE_URL were ever absent, turning EVERY request into a 500.
let _remoteJWKS: ReturnType<typeof createRemoteJWKSet> | null = null;
function jwks() {
  if (_remoteJWKS === null) {
    _remoteJWKS = createRemoteJWKSet(
      new URL(`${Deno.env.get("SUPABASE_URL") ?? ""}/auth/v1/.well-known/jwks.json`),
    );
  }
  return _remoteJWKS;
}

// M-19 FIX (reviewwhisper.md): 12 MB local abuse bound. The real client pipeline
// caps ciphertext at 5 MiB -> ~6.7 MiB PNG -> ~8.9 MiB base64, so this ceiling never
// rejects a legitimate payload. It is deliberately independent of ImgBB's own
// documented upload limit (~32 MB), which is not an abuse bound of ours.
// V2-FIX (reviewwhisper.md): stale comment claimed "lowered from 32 MB" as if ImgBB's
// acceptance window were the constraint — it never was.
const MAX_BASE64_LENGTH = 12 * 1024 * 1024;
const MIN_EXPIRY = 60;
const MAX_EXPIRY = 15_552_000;
// Daily upload cap per user (mirrored by the whisper_upload_quota table's RLS).
const QUOTA_PER_DAY = 50;

serve(async (request) => {
  // 1. Handle preflight CORS requests
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: { "Access-Control-Allow-Origin": "*" } });
  }

  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);

  // Reject oversized bodies before parsing them: a declared Content-Length over the
  // ceiling never gets buffered into memory. Chunked requests without the header are
  // still bounded by the base64 payload validation below.
  const rawLen = request.headers.get("content-length");
  const contentLength = rawLen ? parseInt(rawLen, 10) : 0;
  if (rawLen && isNaN(contentLength)) return json({ error: "Invalid content length" }, 400);
  if (!isNaN(contentLength) && contentLength > 40 * 1024 * 1024) return json({ error: "Request too large" }, 413);

  const authHeader = request.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) return json({ error: "Unauthorized" }, 401);

  const imageKey = Deno.env.get("IMGBB_API_KEY");
  if (!imageKey) return json({ error: "Image hosting is not configured" }, 503);

  // Verify JWT in-function (defense-in-depth even with verify_jwt=true at gateway)
  // to prevent forged sub claims if gateway is bypassed or misconfigured.
  const userId = await extractVerifiedUserId(authHeader.slice(7));
  if (!userId) return json({ error: "Unauthorized" }, 401);

  // Anti-mod gate: unofficial builds stop here (428), BEFORE quota is consumed.
  const blocked = await attestBlockedResponse(request, { userId });
  if (blocked) return blocked;

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  // V2-FIX (reviewwhisper.md): quota RPCs are now service-role-only (uid-parameterized),
  // so the anon key is no longer needed here; the service role key is mandatory.
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl) return json({ error: "Server misconfigured" }, 500);
  // Fail closed: without the service role key the uid-parameterized RPCs are
  // unreachable, so uploads must not proceed.
  if (!serviceRoleKey) return json({ error: "Server misconfigured" }, 503);

  // Parse + validate the payload BEFORE consuming quota (M-3 FIX part 1): invalid or
  // oversized bodies no longer burn one of the caller's scarce daily slots.
  let image = "";
  let name = "whisper";
  let expiration: number | undefined = undefined;
  try {
    const payload = await request.json();
    image = typeof payload.image === "string" ? payload.image : "";
    name = typeof payload.name === "string" ? payload.name.replace(/[^a-zA-Z0-9_-]/g, "").slice(0, 80) : "whisper";
    expiration = payload.expiration == null ? undefined : Number(payload.expiration);

    if (!image || image.length > MAX_BASE64_LENGTH || image.length % 4 !== 0) {
      return json({ error: "Invalid encrypted image payload" }, 400);
    }
    // Validate base64 without giant regex (avoid 50-200ms isolate block for large payloads)
    let validB64 = true;
    for (let i = 0; i < image.length; i++) {
      const c = image.charCodeAt(i);
      if (!(c >= 65 && c <= 90 || c >= 97 && c <= 122 || c >= 48 && c <= 57 || c === 43 || c === 47 || c === 61)) { validB64 = false; break; }
    }
    if (!validB64) return json({ error: "Invalid encrypted image payload" }, 400);

    // Robustness: Validate expiration range
    if (expiration !== undefined && (!Number.isInteger(expiration) || expiration < MIN_EXPIRY || expiration > MAX_EXPIRY)) {
      return json({ error: "Invalid image expiration" }, 400);
    }
  } catch {
    return json({ error: "Invalid request" }, 400);
  }

  // Atomic per-user daily quota via RPC — serializes concurrent bursts, no undercount.
  // FAIL CLOSED (was fail-open): an RPC outage must not become an unlimited upload
  // window. The RPC ships with migration 20260822 (uid-parameterized variant: 20260827);
  // if it is missing, uploads stay down.
  // V2-FIX (reviewwhisper.md): called with the SERVICE ROLE key and the verified userId
  // as p_uid — the old user-token forwarding let any authenticated caller increment an
  // arbitrary row once the RPC took p_uid; the RPC itself now rejects non-service callers.
  const today = new Date().toISOString().slice(0, 10);

  // Best-effort compensating decrement (M-3 FIX part 2): an ImgBB outage must not eat
  // the caller's daily slots. Floor-at-zero semantics live in the SQL function; a rare
  // concurrent double-refund only makes the counter slightly more generous. Declared
  // before the increment so the over-limit path can refund too.
  // V2-FIX (reviewwhisper.md): takes its dependencies as parameters — TS/Deno do not
  // propagate null-narrowing of captured env consts into nested closures, so relying
  // on the outer guards alone type-checks as string | undefined.
  async function refundQuota(baseUrl: string, serviceKey: string, uid: string, day: string): Promise<void> {
    try {
      await fetch(`${baseUrl}/rest/v1/rpc/whisper_refund_upload_quota`, {
        method: "POST",
        headers: {
          apikey: serviceKey,
          Authorization: `Bearer ${serviceKey}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ p_uid: uid, p_day: day }),
      });
    } catch (refundError) {
      console.error("whisper_refund_upload_quota failed (best-effort)", refundError);
    }
  }

  try {
    const quotaRes = await fetch(`${supabaseUrl}/rest/v1/rpc/whisper_increment_upload_quota`, {
      method: "POST",
      headers: {
        apikey: serviceRoleKey,
        Authorization: `Bearer ${serviceRoleKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ p_uid: userId, p_day: today }),
    });
    if (!quotaRes.ok) {
      // V2-FIX (reviewwhisper.md): log status code + first 120 chars only — full
      // PostgREST bodies can carry row data or internal details into the logs.
      const errText = (await quotaRes.text().catch(() => "")).slice(0, 120);
      console.error("whisper_increment_upload_quota failed", quotaRes.status, errText);
      return json({ error: "Upload quota service unavailable" }, 503);
    }
    const count = Number(await quotaRes.json());
    if (count > QUOTA_PER_DAY) {
      // V2-FIX (reviewwhisper.md): the increment already happened, so an over-limit
      // attempt must be refunded or every rejected upload permanently inflates the
      // counter and locks the user out early.
      await refundQuota(supabaseUrl, serviceRoleKey, userId, today);
      return json({ error: "Daily upload limit reached" }, 429);
    }
  } catch (quotaError) {
    console.error("whisper_increment_upload_quota failed", quotaError);
    return json({ error: "Upload quota service unavailable" }, 503);
  }

  const form = new FormData();
  form.set("image", image);
  form.set("name", name || "whisper");
  // The API key travels as a form field (ImgBB accepts `key` there), keeping it out
  // of the URL where it could leak into access logs and referrer headers.
  form.set("key", imageKey);
  if (expiration !== undefined) form.set("expiration", String(expiration));

  let upstream: Response;
  try {
    upstream = await fetch("https://api.imgbb.com/1/upload", {
      method: "POST",
      body: form,
      signal: AbortSignal.timeout(15000),
    });
  } catch (uploadError) {
    console.error("ImgBB upload threw", uploadError);
    await refundQuota(supabaseUrl, serviceRoleKey, userId, today);
    return json({ error: "Encrypted image upload failed" }, 502);
  }

  let result: any;
  try { result = await upstream.json(); } catch { await refundQuota(supabaseUrl, serviceRoleKey, userId, today); return json({ error: "Encrypted image upload failed" }, 502); }
  if (!upstream.ok || !result?.success) {
    console.error("ImgBB upload failed", upstream.status);
    await refundQuota(supabaseUrl, serviceRoleKey, userId, today);
    return json({ error: "Encrypted image upload failed" }, 502);
  }

  // V2-FIX (reviewwhisper.md): record ownership so whisper-image-delete can authorize
  // deletions object-level (the delete capability alone was a bearer token anyone could
  // replay). Best-effort: a bookkeeping failure must never fail a completed upload.
  try {
    const ownershipRes = await fetch(`${supabaseUrl}/rest/v1/whisper_image_ownership`, {
      method: "POST",
      headers: {
        apikey: serviceRoleKey,
        Authorization: `Bearer ${serviceRoleKey}`,
        "Content-Type": "application/json",
        Prefer: "return=minimal",
      },
      body: JSON.stringify({
        user_id: userId,
        image_id: result.data.id ?? null,
        delete_url: result.data.delete_url,
        url: result.data.url,
      }),
    });
    if (!ownershipRes.ok) console.error("whisper_image_ownership insert failed", ownershipRes.status);
  } catch (ownershipError) {
    console.error("whisper_image_ownership insert threw (best-effort)", ownershipError);
  }

  return json({
    url: result.data.url,
    // This capability URL is encrypted inside the Whisper attachment envelope.
    id: result.data.delete_url
  }, 200);
});

// Key-type-agnostic JWT verification (FIX for "Unauthorized" on projects using the
// newer asymmetric JWT signing keys):
//   1. Fast path — legacy symmetric HS256 via SUPABASE_JWT_SECRET.
//   2. Fallback — asymmetric ES256/RS256/EdDSA tokens verified against the project's
//      JWKS at /auth/v1/.well-known/jwks.json (createRemoteJWKSet fetches lazily and
//      caches, so this costs one request per cold start).
// The gateway's verify_jwt=true already rejected invalid tokens before us; this
// in-function check stays as defense-in-depth but must not depend on key type.

async function extractVerifiedUserId(jwt: string): Promise<string | null> {
  const secret = Deno.env.get("SUPABASE_JWT_SECRET");
  if (secret) {
    try {
      const { payload } = await jwtVerify(jwt, new TextEncoder().encode(secret), { algorithms: ["HS256"] });
      if (typeof payload.sub === "string") return payload.sub;
    } catch { /* not an HS256 token or secret mismatch — try asymmetric below */ }
  }
  try {
    const { payload } = await jwtVerify(jwt, jwks(), { algorithms: ["ES256", "RS256", "EdDSA"] });
    return typeof payload.sub === "string" ? payload.sub : null;
  } catch {
    return null;
  }
}

function json(body: unknown, status: number) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": "*"
    },
  });
}
