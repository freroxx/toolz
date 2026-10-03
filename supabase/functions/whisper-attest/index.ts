// Supabase Edge Function: whisper-attest
//
// Standalone attestation-verdict endpoint. The per-function gate
// (supabase/functions/_shared/attest.ts) already blocks unofficial builds on
// every client-called function; this endpoint exists so the Android client can
// proactively check its own standing (e.g. at Whisper onboarding) and so
// operators have one place where the FULL Play Integrity verdict path lives.
//
// POST (authenticated, any valid user JWT):
//   headers: X-App-Package, X-App-Cert-Sha256, X-App-VersionCode,
//            X-Play-Integrity-Token (optional unless PLAY_INTEGRITY_ENABLED=true)
// -> 200 { ok:true,  mode:"cert"|"integrity", reason:"ok" }
// -> 428 { ok:false, error:"Unofficial build blocked - install official Toolz APK", reason }
// -> 401/503 for auth / misconfiguration
//
// Secrets (supabase secrets set):
//   OFFICIAL_PACKAGE, OFFICIAL_CERT_SHA256, MIN_VERSION_CODE (see _shared/attest.ts)
//   PLAY_INTEGRITY_ENABLED=true to switch the strong path on.
//   GOOGLE_SERVICE_ACCOUNT_JSON — service-account JSON of a GCP project with the
//     Play Integrity API enabled. When present AND a token is supplied, the token
//     is decoded via playintegrity.googleapis.com and device/app verdicts enforced.
//     When absent, the function stays fully runnable in cert+version strict mode.
//
// NOTE on threat model: header checks alone are spoofable by a determined modder
// (anyone can send headers). They raise the bar to "must repackage + steal the
// release cert", which stops casual mod APKs. The Play Integrity path (hardware
// verdict) is the strong check — enable it for production.
import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createRemoteJWKSet, importPKCS8, jwtVerify, SignJWT } from "https://esm.sh/jose@5.9.6";
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


const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, content-type, apikey, x-play-integrity-token, x-app-package, x-app-cert-sha256, x-app-versioncode",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}

let _remoteJWKS: ReturnType<typeof createRemoteJWKSet> | null = null;
function jwks() {
  if (_remoteJWKS === null) {
    _remoteJWKS = createRemoteJWKSet(
      new URL(`${Deno.env.get("SUPABASE_URL") ?? ""}/auth/v1/.well-known/jwks.json`),
    );
  }
  return _remoteJWKS;
}

async function extractVerifiedUserId(jwt: string): Promise<string | null> {
  const secret = Deno.env.get("SUPABASE_JWT_SECRET");
  if (secret) {
    try {
      const { payload } = await jwtVerify(jwt, new TextEncoder().encode(secret), {
        algorithms: ["HS256"],
      });
      if (typeof payload.sub === "string") return payload.sub;
    } catch { /* fall through to JWKS */ }
  }
  try {
    const { payload } = await jwtVerify(jwt, jwks(), {
      algorithms: ["ES256", "RS256", "EdDSA"],
    });
    return typeof payload.sub === "string" ? payload.sub : null;
  } catch {
    return null;
  }
}

/** Decode a Play Integrity token via the Google API. Throws on any failure. */
async function verifyPlayIntegrityToken(
  token: string,
  packageName: string,
  saJson: string,
): Promise<{ deviceOk: boolean; appOk: boolean; accountOk: boolean }> {
  const sa = JSON.parse(saJson);
  if (typeof sa.private_key !== "string" || typeof sa.client_email !== "string") {
    throw new Error("service account JSON missing private_key/client_email");
  }
  // Reuse the OAuth2 JWT-bearer flow (same as whisper-push-send FCM mint).
  const tokenUri =
    typeof sa.token_uri === "string" ? sa.token_uri : "https://oauth2.googleapis.com/token";
  const nowSec = Math.floor(Date.now() / 1000);
  const privateKey = await importPKCS8(sa.private_key, "RS256");
  const assertion = await new SignJWT({
    scope: "https://www.googleapis.com/auth/playintegrity",
  })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" })
    .setIssuer(sa.client_email)
    .setAudience(tokenUri)
    .setIssuedAt(nowSec)
    .setExpirationTime(nowSec + 3600)
    .sign(privateKey);
  const oauthRes = await fetch(tokenUri, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }).toString(),
    signal: AbortSignal.timeout(10_000),
  });
  if (!oauthRes.ok) throw new Error(`playintegrity oauth failed (${oauthRes.status})`);
  const oauth = await oauthRes.json();

  const decodeRes = await fetch(
    `https://playintegrity.googleapis.com/v1/${encodeURIComponent(packageName)}:decodeIntegrityToken`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${oauth.access_token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ integrityToken: token }),
      signal: AbortSignal.timeout(10_000),
    },
  );
  if (!decodeRes.ok) throw new Error(`playintegrity decode failed (${decodeRes.status})`);
  const verdict = await decodeRes.json();
  const device = (verdict?.deviceIntegrity?.deviceRecognitionVerdict ?? []) as string[];
  const app = (verdict?.appIntegrity?.appRecognitionVerdict ?? "") as string;
  const account = (verdict?.accountDetails?.appLicensingVerdict ?? "") as string;
  return {
    deviceOk: device.includes("MEETS_DEVICE_INTEGRITY"),
    appOk: app === "PLAY_RECOGNIZED",
    // LICENSED for Play installs; UNLICENSED covers sideloaded official APKs —
    // do NOT hard-require it or the GitHub-APK install path breaks.
    accountOk: account === "LICENSED" || account === "UNLICENSED" || account === "",
  };
}

serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);

  const authHeader = request.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) return json({ error: "Unauthorized" }, 401);
  const userId = await extractVerifiedUserId(authHeader.slice(7));
  if (!userId) return json({ error: "Unauthorized" }, 401);

  // 1. Baseline: package/cert/version gate (always enforced).
  const base = verifyAttestHeaders(request);
  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  async function deny(reason: string): Promise<Response> {
    if (supabaseUrl && serviceKey) {
      await logAttestDenied(supabaseUrl, serviceKey, {
        reason,
        userId,
        pkg: request.headers.get(HEADER_PACKAGE) ?? "",
        version: request.headers.get(HEADER_VERSION) ?? "",
      });
    }
    console.error("whisper-attest denied", reason);
    return json({ ok: false, error: ATTEST_BLOCK_MESSAGE, reason }, 428);
  }
  if (!base.ok) return deny(base.reason);

  // 2. Strong path: Play Integrity verdict (only when fully configured).
  const integrityEnabled = (Deno.env.get("PLAY_INTEGRITY_ENABLED") ?? "") === "true";
  const saJson = Deno.env.get("GOOGLE_SERVICE_ACCOUNT_JSON") ?? "";
  const token = (request.headers.get(HEADER_INTEGRITY_TOKEN) ?? "").trim();
  if (integrityEnabled) {
    if (!token) return deny("integrity_token_missing");
    if (!saJson) {
      // Configured to require Integrity but cannot verify — fail closed.
      console.error("whisper-attest: PLAY_INTEGRITY_ENABLED without GOOGLE_SERVICE_ACCOUNT_JSON");
      return json({ error: "Server misconfigured" }, 503);
    }
    try {
      const pkg = (request.headers.get(HEADER_PACKAGE) ?? "").trim();
      const v = await verifyPlayIntegrityToken(token, pkg, saJson);
      if (!v.deviceOk || !v.appOk) {
        return deny(`integrity_verdict_failed:device=${v.deviceOk},app=${v.appOk}`);
      }
      return json({ ok: true, mode: "integrity", reason: "ok" });
    } catch (e) {
      console.error("whisper-attest integrity verify threw", e instanceof Error ? e.message : e);
      return deny("integrity_verify_error");
    }
  }

  // Integrity not enabled (or token absent while optional): cert+version verdict.
  // Log the cert fingerprint state at debug level only — never the token.
  console.log(
    "whisper-attest ok",
    `pkg=${request.headers.get(HEADER_PACKAGE) ?? ""}`,
    `vcode=${request.headers.get(HEADER_VERSION) ?? ""}`,
    `cert_present=${(request.headers.get(HEADER_CERT) ?? "").length > 0}`,
  );
  return json({ ok: true, mode: "cert", reason: "ok" });
});
