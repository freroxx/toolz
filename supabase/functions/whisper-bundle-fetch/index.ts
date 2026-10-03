// Supabase Edge Function: whisper-bundle-fetch
// PHASE 2 (roadmap §2.2): returns a peer's signed prekey bundle and consumes one
// one-time prekey per call, atomically. Auth: caller's own verified JWT (you may
// fetch bundles for anyone — they are public keys + signature).
//
// POST { account: "<uuid>" }
// -> {
//      identity_binding: {...}|null,
//      spk: { kid, public_key, signature },
//      opk: { kid, public_key } | null
//    }
import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
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

serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);

  const authHeader = request.headers.get("Authorization") ?? "";
  const bearer = authHeader.startsWith("Bearer ") ? authHeader.slice(7) : "";
  if (!bearer) return json({ error: "Unauthorized" }, 401);

  // The gateway (verify_jwt) already validated the caller; we only need their uid
  // for rate-limit identity. Parse without failing on asymmetric keys.
  let callerId = "";
  try {
    const payloadB64 = bearer.split(".")[1];
    const claims = JSON.parse(atob(payloadB64.replace(/-/g, "+").replace(/_/g, "/")));
    callerId = String(claims?.sub ?? "");
  } catch (_) {
    /* keep empty; bundle fetch still authorized by gateway */
  }

  // Anti-mod gate: unofficial builds stop here (428), before any DB work.
  // whisper_attest_log records the denial best-effort inside the helper.
  const blocked = await attestBlockedResponse(request);
  if (blocked) return blocked;

  let account = "";
  try {
    const body = await request.json();
    account = String(body?.account ?? "");
  } catch (_) {
    return json({ error: "Invalid body" }, 400);
  }
  if (!/^[0-9a-f-]{36}$/i.test(account)) return json({ error: "Invalid account" }, 400);
  if (callerId && callerId === account) return json({ error: "Self-bundles are local" }, 400);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceKey) return json({ error: "Server misconfigured" }, 503);

  const admin = createClient(supabaseUrl, serviceKey, { auth: { persistSession: false } });

  try {
    // Identity binding anchors the bundle's trust chain.
    // rotation_cert/rotation_counter (20261006): the peer's
    // published ROTv2 rotation certificate, carried so clients
    // can chain a fresh bundle signer to a pinned key.
    const { data: profile } = await admin
      .from("profiles")
      .select("identity_binding, rotation_cert, rotation_counter")
      .eq("id", account)
      .single();

    // Signed prekey: most recent SPK.
    const { data: spks } = await admin
      .from("whisper_prekeys")
      .select("kid, public_key, signature")
      .eq("account", account)
      .eq("kind", "SPK")
      .order("created_at", { ascending: false })
      .limit(1);

    if (!spks || spks.length === 0 || !spks[0].signature) {
      return json({ error: "No prekey bundle published" }, 404);
    }

    // Consume one OPK atomically (delete-returning via RPC-free pattern:
    // select then delete by pk in the same tick; races only cost a reused OPK,
    // which X3DH tolerates with a warning-level property loss, not secrecy loss).
    const { data: opks } = await admin
      .from("whisper_prekeys")
      .select("kid, public_key")
      .eq("account", account)
      .eq("kind", "OPK")
      .order("created_at", { ascending: true })
      .limit(1);

    let opk: { kid: string; public_key: string } | null = null;
    if (opks && opks.length > 0) {
      const { error: delErr } = await admin
        .from("whisper_prekeys")
        .delete()
        .eq("account", account)
        .eq("kid", opks[0].kid)
        .eq("kind", "OPK");
      if (!delErr) opk = opks[0];
    }

    return json({
      identity_binding: profile?.identity_binding ?? null,
      rotation_cert: profile?.rotation_cert ?? null,
      rotation_counter: profile?.rotation_counter ?? null,
      spk: spks[0],
      opk,
    });
  } catch (err) {
    console.error("bundle-fetch failed", err instanceof Error ? err.message : err);
    return json({ error: "Bundle fetch failed" }, 503);
  }
});
