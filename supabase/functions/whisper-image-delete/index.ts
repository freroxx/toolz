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
    // Rate limiting was considered but intentionally skipped here (Medium severity;
    // the bypass limiter must not be reused for this endpoint).
    const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    const baseUrl = Deno.env.get("SUPABASE_URL");
    if (!serviceRole || !baseUrl) return json({ error: "Server misconfigured" }, 503);

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
