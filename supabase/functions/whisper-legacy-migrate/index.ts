// Supabase Edge Function: whisper-legacy-migrate
// One-shot server-side migration for pre-P1-15 truncated-email token accounts.
// The client proves ownership by sending the raw token over TLS; the edge
// tries the current scheme first (already migrated -> no-op), then the 3
// legacy derivations via GoTrue sign-in. On a legacy hit it copies the
// profile handle to a fresh full-hash user, randomizes the legacy password
// (history under the legacy id is preserved — E2EE rows cannot be
// re-encrypted server-side), and records whisper_legacy_disabled.
// Pre-auth (verify_jwt = false): the token itself is the proof.
//
// POST { token: "<64 hex>" }
// -> 200 { migrated: true, username } | 200 { migrated: false, reason }
// -> 400 invalid token | 404 no legacy account | 428 unofficial | 503 backend error
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

async function hexDigest(alg: string, text: string): Promise<string> {
  const d = await crypto.subtle.digest(alg, new TextEncoder().encode(text));
  return [...new Uint8Array(d)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function randomHex(nBytes: number): string {
  return [...crypto.getRandomValues(new Uint8Array(nBytes))]
    .map((b) => b.toString(16).padStart(2, "0")).join("");
}

serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);

  const blocked = await attestBlockedResponse(request);
  if (blocked) return blocked;

  let token = "";
  try {
    const body = await request.json();
    token = String(body?.token ?? "");
  } catch (_) {
    return json({ error: "Invalid body" }, 400);
  }
  token = token.replace(/[^0-9a-fA-F]/g, "").toLowercase();
  if (!/^[0-9a-f]{64}$/.test(token)) return json({ error: "Invalid token" }, 400);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const anonKey = request.headers.get("apikey") ?? "";
  if (!supabaseUrl || !serviceKey || !anonKey) {
    return json({ error: "Server misconfigured" }, 503);
  }
  const anon = createClient(supabaseUrl, anonKey, { auth: { persistSession: false } });
  const admin = createClient(supabaseUrl, serviceKey, { auth: { persistSession: false } });

  try {
    const fullHash = await hexDigest("SHA-256", token);
    const curEmail = fullHash + "@whisper.toolz.app";
    const curPwd = await hexDigest("SHA-256", "pwd_" + token);
    const truncEmail = fullHash.slice(0, 32) + "@whisper.toolz.app";
    const legacy = [
      { email: truncEmail, pwd: await hexDigest("SHA-256", "pwd_" + token) },
      { email: truncEmail, pwd: await hexDigest("SHA-512", "pwd_" + token) },
      { email: truncEmail, pwd: await hexDigest("SHA-256", token) },
    ];

    const curTry = await anon.auth.signInWithPassword({ email: curEmail, password: curPwd });
    if (!curTry.error) {
      await anon.auth.signOut();
      return json({ migrated: false, reason: "already_current" });
    }

    for (const cand of legacy) {
      const attempt = await anon.auth.signInWithPassword({ email: cand.email, password: cand.pwd });
      if (attempt.error || !attempt.data.user) continue;
      const legacyId = attempt.data.user.id;
      const { data: profile } = await admin
        .from("profiles")
        .select("username, display_name, avatar_url, bio")
        .eq("id", legacyId)
        .single();
      await anon.auth.signOut();
      const username = String(profile?.username ?? attempt.data.user.user_metadata?.username ?? "");
      const displayName = String(profile?.display_name ?? attempt.data.user.user_metadata?.display_name ?? username);
      const created = await admin.auth.admin.createUser({
        email: curEmail,
        password: curPwd,
        email_confirm: true,
        user_metadata: { username, display_name: displayName },
      });
      if (created.error || !created.data.user) {
        console.error("legacy-migrate createUser failed", created.error?.message);
        return json({ error: "Migration failed" }, 503);
      }
      const newId = created.data.user.id;
      await admin.from("profiles").insert({
        id: newId,
        username,
        display_name: displayName,
        avatar_url: profile?.avatar_url ?? null,
        bio: profile?.bio ?? null,
      });
      await admin.auth.admin.updateUserById(legacyId, { password: "migrated-" + randomHex(32) });
      await admin.from("whisper_legacy_disabled").upsert(
        { email: cand.email },
        { onConflict: "email" },
      );
      return json({ migrated: true, username });
    }
    return json({ migrated: false, reason: "no_legacy_account" }, 404);
  } catch (err) {
    console.error("legacy-migrate failed", err instanceof Error ? err.message : err);
    return json({ error: "Migration failed" }, 503);
  }
});
