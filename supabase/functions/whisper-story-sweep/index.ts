// Supabase Edge Function: whisper-story-sweep (Daily Ups hosting hygiene)
//
// Deletes the hosted ImgBB copy of Daily Ups that have been hidden
// (poster delete / server expiry) and converges them to a terminal local
// state. There is no pg_cron in this project — the DB-side lifetime trigger
// already hides expired stories from every reader, and this function is the
// best-effort host-side delete net for the copy ImgBB itself may still serve:
//   1. Auth: verified user JWT (attest-gated) OR the service-role key.
//   2. Lease up to N due cleanup rows via whisper_story_cleanup_claim
//      (service-role-only RPC: FOR UPDATE SKIP LOCKED + attempt counting).
//      Rows in flight keep a 10-minute lease, so a killed isolate simply
//      re-queues instead of double-deleting.
//   3. Per leased story: resolve the hosted copy's delete capability through
//      the whisper_image_ownership ledger (unique delete_url, written
//      server-side by whisper-image-upload — the metastore row is never
//      trusted as a URL) and GET it with redirect:manual — any 3xx is a
//      failure, like whisper-image-delete. On success →
//      whisper_story_cleanup_result(ok=true) flips the story to
//      state='deleted'; on failure → result(ok=false) records the failure
//      class and schedules exponential backoff (max 20 attempts, then the row
//      stops being claimed until a poster retry resets it).
//   4. Report how much was done; never expose endpoints, keys or URLs in logs.
//
// Env: SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY (+ the attest trio
// OFFICIAL_PACKAGE / OFFICIAL_CERT_SHA256 / MIN_VERSION_CODE).
import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createRemoteJWKSet, jwtVerify } from "https://esm.sh/jose@5.9.6";
// -- INLINED from _shared/attest.ts (dashboard deploys are single-file;
//    keep in sync with the canonical copy) --
// Shared anti-mod attestation gate for Whisper edge functions.

const ATTEST_BLOCK_MESSAGE =
  "Unofficial build blocked - install official Toolz APK";

const HEADER_PACKAGE = "x-app-package";
const HEADER_CERT = "x-app-cert-sha256";
const HEADER_VERSION = "x-app-versioncode";

function normCert(raw: string): string {
  return raw.replace(/:/g, "").replace(/\s+/g, "").toUpperCase();
}

/** Pure header check — no I/O, safe to unit-test. */
function verifyAttestHeaders(req: Request): { ok: boolean; reason: string } {
  const officialPkg =
    Deno.env.get("OFFICIAL_PACKAGE") ?? "com.frerox.toolz";
  const officialCert = normCert(Deno.env.get("OFFICIAL_CERT_SHA256") ?? "");
  const minCode = parseInt(Deno.env.get("MIN_VERSION_CODE") ?? "18", 10) || 18;

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
  return { ok: true, reason: "ok" };
}

/** Returns a 428 Response when the caller fails attestation, else null. */
function attestBlockedResponse(req: Request): Response | null {
  const verdict = verifyAttestHeaders(req);
  if (verdict.ok) return null;
  console.error("attest denied", verdict.reason);
  return new Response(
    JSON.stringify({ error: ATTEST_BLOCK_MESSAGE, attest: false, reason: verdict.reason }),
    { status: 428, headers: { "Content-Type": "application/json", "Cache-Control": "no-store" } },
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

// One sweep claim per call: small enough that occasional client invocations
// converge quickly, bounded enough that no single call can fan out widely.
const CLAIM_LIMIT = 10;
const LEASE_SECONDS = 600;

interface LeasedStory {
  story_id: string;
  host_url: string;
  host_object_id: string;
  attempt_count: number;
  expires_at: string;
}

serve(async (request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: { "Access-Control-Allow-Origin": "*" } });
  }
  if (request.method !== "POST") {
    return json({ error: "Method not allowed" }, 405);
  }

  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  if (!serviceRoleKey || !supabaseUrl) return json({ error: "Server misconfigured" }, 500);

  const authHeader = request.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) return json({ error: "Unauthorized" }, 401);
  const bearer = authHeader.slice(7);
  const isServiceRole = timingSafeEqual(bearer, serviceRoleKey);
  // Human clients also open the sweep (opportunistic hygiene); they are attest-
  // gated. The service role path skips that gate (dashboards don't send x-app-*).
  if (!isServiceRole) {
    const userId = await extractVerifiedUserId(bearer);
    if (!userId) return json({ error: "Unauthorized" }, 401);
    const blocked = attestBlockedResponse(request);
    if (blocked) return blocked;
  }

  const serviceHeaders = {
    apikey: serviceRoleKey,
    Authorization: `Bearer ${serviceRoleKey}`,
    "Content-Type": "application/json",
  };

  // 1. Lease due cleanup rows.
  let leased: LeasedStory[] = [];
  try {
    const claimRes = await fetch(`${supabaseUrl}/rest/v1/rpc/whisper_story_cleanup_claim`, {
      method: "POST",
      headers: serviceHeaders,
      body: JSON.stringify({ p_limit: CLAIM_LIMIT, p_lease_seconds: LEASE_SECONDS }),
      signal: AbortSignal.timeout(10_000),
    });
    if (!claimRes.ok) {
      const errText = (await claimRes.text().catch(() => "")).slice(0, 120);
      console.error("story_cleanup_claim failed", claimRes.status, errText);
      return json({ error: "Cleanup lease unavailable" }, 503);
    }
    const rows = await claimRes.json();
    leased = Array.isArray(rows) ? (rows as LeasedStory[]) : [];
  } catch (e) {
    console.error("story_cleanup_claim threw", e instanceof Error ? e.message : e);
    return json({ error: "Cleanup lease unavailable" }, 503);
  }

  // 2. Work the lease: delete the hosted copy, then record the outcome.
  let deleted = 0;
  let failed = 0;
  for (const story of leased) {
    const failureClass = await deleteHostedCopy(supabaseUrl, serviceHeaders, story);
    if (failureClass === null) {
      if (await reportResult(supabaseUrl, serviceHeaders, story.story_id, true)) {
        deleted++;
      } else {
        failed++;
      }
    } else {
      await reportResult(supabaseUrl, serviceHeaders, story.story_id, false, failureClass);
      failed++;
    }
  }

  return json({ claimed: leased.length, deleted, failed }, 200);
});

/**
 * Resolves the hosted copy through the ownership ledger and deletes it.
 * The metastore handle is ONLY a ledger key (unique delete_url written by
 * whisper-image-upload); the GET always uses the ledger's own URL, so a
 * forged story row can never steer this function at an arbitrary address.
 * Returns null on success, else a short failure class for the retry ledger.
 * Never logs the URL itself.
 */
async function deleteHostedCopy(
  supabaseUrl: string,
  serviceHeaders: Record<string, string>,
  story: LeasedStory,
): Promise<string | null> {
  const handle = story.host_object_id;
  if (!handle) return "missing_delete_capability";
  let ledgerUrl: string;
  try {
    const res = await fetch(
      `${supabaseUrl}/rest/v1/whisper_image_ownership?delete_url=eq.${encodeURIComponent(handle)}&select=delete_url&limit=1`,
      { headers: serviceHeaders, signal: AbortSignal.timeout(8000) },
    );
    if (!res.ok) return "ownership_lookup_failed";
    const rows = await res.json().catch(() => []);
    const found = Array.isArray(rows) ? rows[0]?.delete_url : null;
    if (typeof found !== "string" || !found.startsWith("https://")) {
      return "ownership_missing";
    }
    ledgerUrl = found;
  } catch (e) {
    console.error("ownership lookup threw", e instanceof Error ? e.message : e);
    return "ownership_network";
  }
  try {
    const res = await fetch(ledgerUrl, {
      method: "GET",
      redirect: "manual",
      signal: AbortSignal.timeout(8000),
    });
    if (res.ok && res.status < 300) return null;
    return `imgbb_status_${res.status}`.slice(0, 64);
  } catch (e) {
    // Network failures: classify by cause, log the message only.
    console.error("hosted copy delete threw", e instanceof Error ? e.message : e);
    return e instanceof Error && /timeout/i.test(e.message) ? "imgbb_timeout" : "imgbb_network";
  }
}

/** Pushes one result to the registry RPC; returns true when accepted as ok. */
async function reportResult(
  supabaseUrl: string,
  serviceHeaders: Record<string, string>,
  storyId: string,
  ok: boolean,
  failureClass?: string,
): Promise<boolean> {
  try {
    const res = await fetch(`${supabaseUrl}/rest/v1/rpc/whisper_story_cleanup_result`, {
      method: "POST",
      headers: serviceHeaders,
      body: JSON.stringify({
        p_story_id: storyId,
        p_ok: ok,
        p_failure_class: failureClass ?? null,
      }),
      signal: AbortSignal.timeout(8000),
    });
    if (!res.ok) {
      console.error("story_cleanup_result failed", res.status);
      return false;
    }
    return (await res.json().catch(() => false)) === true;
  } catch (e) {
    console.error("story_cleanup_result threw", e instanceof Error ? e.message : e);
    return false;
  }
}

/** Constant-time string compare for secret/header checks. */
function timingSafeEqual(a: string, b: string): boolean {
  const ab = new TextEncoder().encode(a);
  const bb = new TextEncoder().encode(b);
  if (ab.length !== bb.length) return false;
  let diff = 0;
  for (let i = 0; i < ab.length; i++) diff |= ab[i] ^ bb[i];
  return diff === 0;
}

function json(body: unknown, status: number) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}
