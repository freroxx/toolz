import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
// V2-FIX (reviewwhisper.md): switched from an HS256-only verifier to the same dual
// HS256→JWKS verification used by the sibling functions.
import { createRemoteJWKSet, jwtVerify } from "https://esm.sh/jose@5.9.6";

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

serve(async (request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, content-type, apikey", "Access-Control-Allow-Methods": "POST, OPTIONS" } });
  }
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);
  const auth = request.headers.get("Authorization");
  if (!auth?.startsWith("Bearer ")) return json({ error: "Unauthorized" }, 401);
  const userId = await extractVerifiedUserId(auth.slice(7));
  if (!userId) return json({ error: "Invalid token" }, 401);

  // V3-FIX (round 3): destructive-endpoint rate limiting. Identity = verified sub;
  // the limiter RPC runs BEFORE doing any work (size checks, JSON parsing, ImgBB).
  // Service-role env is resolved up front too: every later step needs it.
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const baseUrl = Deno.env.get("SUPABASE_URL");
  if (!serviceRole || !baseUrl) return json({ error: "Server misconfigured" }, 503);

  // V3-FIX (round 3): gate call with p_ok=false. For image-delete EVERY attempt
  // consumes budget (30 per identity per 15 min, any outcome), so a failed request
  // and a successful one count alike — a success therefore lands two rows (this
  // gate + the ok=true record below) and spends two budget units. Accepted cost of
  // keeping the flow identical to whisper-delete-account.
  // rate_limited → 429; other RPC errors fail closed → 503 abort.
  const gate = await destructiveAttempt(serviceRole, baseUrl, "image-delete", userId, false);
  if (!gate) return json({ error: "Rate limiter unavailable" }, 503);
  if (gate.limited) return json({ error: "Too many attempts — try again later" }, 429);

  const rawLen = request.headers.get("content-length");
  const len = rawLen ? parseInt(rawLen, 10) : 0;
  if (rawLen && isNaN(len)) return json({ error: "Invalid content length" }, 400);
  if (!isNaN(len) && len > 64 * 1024) return json({ error: "Request too large" }, 413);
  try {
    const { id } = await request.json();
    const deletionUrl = typeof id === "string" ? new URL(id) : null;
    if (!deletionUrl || deletionUrl.protocol !== "https:" || deletionUrl.hostname !== "ibb.co") {
      return json({ error: "Invalid deletion capability" }, 400);
    }

    // V2-FIX (reviewwhisper.md): OBJECT-LEVEL AUTHORIZATION. The delete capability URL
    // used to be a bearer token — anyone who obtained it could delete someone else's
    // image. The upload function now records ownership in whisper_image_ownership;
    // here we check the caller owns this capability before touching ImgBB. Lookup runs
    // under the service role so RLS can never hide a row from us.
    // V3-FIX (round 3): the earlier "rate limiting intentionally skipped" note is now
    // obsolete — the whisper_destructive_attempt gate above limits this endpoint.

    let ownerUserId: string | null | undefined;
    try {
      const ownershipRes = await fetch(
        `${baseUrl}/rest/v1/whisper_image_ownership?delete_url=eq.${encodeURIComponent(deletionUrl.toString())}&select=user_id`,
        {
          headers: { apikey: serviceRole, Authorization: `Bearer ${serviceRole}` },
          signal: AbortSignal.timeout(8000),
        },
      );
      // Fail closed on ledger errors: an outage must not become a free-delete window.
      if (!ownershipRes.ok) return json({ error: "Ownership check unavailable" }, 503);
      const rows = await ownershipRes.json();
      ownerUserId = Array.isArray(rows) && rows.length > 0 ? rows[0]?.user_id ?? null : null;
    } catch (_) {
      return json({ error: "Ownership check unavailable" }, 503);
    }
    if (ownerUserId !== null && ownerUserId !== userId) {
      // Row exists but belongs to someone else: answer exactly like an unknown
      // capability would, so the caller learns nothing about existence.
      return json({ error: "Not found" }, 404);
    }
    // ownerUserId === null → no ownership row: legacy image uploaded before the
    // ledger existed. Allow (the verified caller still had to present the secret
    // delete_url); new uploads are always recorded.

    // Redirects are followed nowhere: a redirect could escape the hostname allowlist
    // above, and deletion capability URLs never legitimately redirect. Any 3xx is
    // treated as a failure.
    // V3-FIX (round 3): record the eventual ok=true outcome BEFORE the irreversible
    // ImgBB call, mirroring whisper-delete-account's placement before its GoTrue
    // delete — an RPC failure here aborts (fail closed) with nothing deleted.
    const success = await destructiveAttempt(serviceRole, baseUrl, "image-delete", userId, true);
    if (!success) return json({ error: "Rate limiter unavailable" }, 503);
    if (success.limited) return json({ error: "Too many attempts — try again later" }, 429);
    const upstream = await fetch(deletionUrl, { method: "GET", redirect: "manual", signal: AbortSignal.timeout(8000) });
    if (!upstream.ok || upstream.status >= 300) return json({ error: "Image deletion failed" }, 502);
    return json({ success: true }, 200);
  } catch {
    return json({ error: "Invalid request" }, 400);
  }
});

// Key-type-agnostic JWT verification (same dual approach as whisper-bypass-verify):
//   1. Fast path — legacy symmetric HS256 via SUPABASE_JWT_SECRET.
//   2. Fallback — asymmetric ES256/RS256/EdDSA tokens verified against the project's
//      JWKS at /auth/v1/.well-known/jwks.json.

async function extractVerifiedUserId(jwt: string): Promise<string | null> {
  const secret = Deno.env.get("SUPABASE_JWT_SECRET");
  if (secret) {
    try {
      const { payload } = await jwtVerify(jwt, new TextEncoder().encode(secret), { algorithms: ["HS256"] });
      if (typeof payload.sub === "string") return payload.sub;
    } catch { /* fall through to asymmetric */ }
  }
  try {
    const { payload } = await jwtVerify(jwt, jwks(), { algorithms: ["ES256", "RS256", "EdDSA"] });
    return typeof payload.sub === "string" ? payload.sub : null;
  } catch {
    return null;
  }
}

function json(body: unknown, status: number) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store" } });
}

// V3-FIX (round 3): shared limiter call for destructive endpoints. Calls the
// service-role-only whisper_destructive_attempt RPC (migration 20260828), which
// counts + locks out + records the attempt atomically.
//   • { limited: true }  → caller must map to a 429 response.
//   • { limited: false } → attempt admitted; prior-window count is in `prior`.
//   • null               → RPC failed for any other reason — CALLERS MUST FAIL CLOSED
//                          (503 abort); no destructive work may happen untracked.
async function destructiveAttempt(
  serviceRole: string,
  baseUrl: string,
  endpoint: string,
  identity: string,
  ok: boolean,
): Promise<{ limited: boolean; prior: number } | null> {
  try {
    const res = await fetch(`${baseUrl}/rest/v1/rpc/whisper_destructive_attempt`, {
      method: "POST",
      headers: { apikey: serviceRole, Authorization: `Bearer ${serviceRole}`, "Content-Type": "application/json" },
      body: JSON.stringify({ p_endpoint: endpoint, p_identity: identity, p_ok: ok }),
      signal: AbortSignal.timeout(8000),
    });
    if (res.ok) {
      const count = await res.json().catch(() => 0);
      return { limited: false, prior: typeof count === "number" ? count : 0 };
    }
    const bodyText = await res.text().catch(() => "");
    if (bodyText.includes("rate_limited")) return { limited: true, prior: -1 };
    console.error("whisper_destructive_attempt failed", res.status, bodyText);
    return null;
  } catch (err) {
    console.error("whisper_destructive_attempt threw", err);
    return null;
  }
}
