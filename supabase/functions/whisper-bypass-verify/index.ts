// Supabase Edge Function: verifies the Whisper screenshot-bypass password.
//
// The bypass password is NEVER shipped in the APK. It lives only as this function's
// secret, so decompiling the app reveals nothing. The client posts a candidate
// password and gets an allow/deny verdict; the FLAG_SECURE toggle itself stays local.
//
// Hardening:
//  • Constant-time comparison via SHA-256 digests + timingSafeEqual (no length/byte leaks).
//  • DB-backed rate limit in whisper_bypass_attempts: max BYPASS_MAX_ATTEMPTS failures
//    per identity per window; lockout returns 429. Identity = authenticated sub if a
//    valid JWT is presented (optional), else the caller IP — so the pre-auth screen
//    can still use it while anonymous abuse stays bounded.
//  • Fail closed on any internal error: no verdict means no bypass.
import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import {
  timingSafeEqual,
} from "https://deno.land/std@0.224.0/crypto/timing_safe_equal.ts";
// V2-FIX (reviewwhisper.md): module-level declarations were spliced INSIDE the import
// braces below (invalid syntax); the helper now lives under the completed import.
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

const MAX_BODY_BYTES = 4 * 1024;
const MAX_PASSWORD_LENGTH = 256;
const WINDOW_MINUTES = 15;
const MAX_FAILURES_PER_WINDOW = 5;

function json(body: unknown, status: number) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}

async function sha256Hex(input: string): Promise<Uint8Array> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(input));
  return new Uint8Array(digest);
}

/** Digest-level compare: constant-time and length-normalized. */
async function passwordsMatch(candidate: string, expectedSecret: string): Promise<boolean> {
  const [a, b] = await Promise.all([sha256Hex(candidate), sha256Hex(expectedSecret)]);
  // std's timingSafeEqual instead of the non-standard crypto.subtle.timingSafeEqual
  // (which exists in newer Deno runtimes but is missing from Supabase's TS defs — TS2339).
  return timingSafeEqual(a, b);
}

// Key-type-agnostic (see whisper-image-upload): HS256 secret, then project JWKS.

async function extractVerifiedSub(jwt: string): Promise<string | null> {
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

serve(async (request) => {
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);

  const expectedSecret = Deno.env.get("WHISPER_BYPASS_PASSWORD");
  if (!expectedSecret) return json({ error: "Bypass is not configured" }, 503);

  const rawLen = request.headers.get("content-length");
  const len = rawLen ? parseInt(rawLen, 10) : 0;
  if (!isNaN(len) && len > MAX_BODY_BYTES) return json({ error: "Request too large" }, 413);

  let candidate: string | undefined;
  try {
    const body = await request.json();
    candidate = typeof body?.password === "string" ? body.password : undefined;
  } catch {
    return json({ error: "Invalid request" }, 400);
  }
  if (candidate === undefined || candidate.length === 0 || candidate.length > MAX_PASSWORD_LENGTH) {
    return json({ ok: false }, 200); // Don't distinguish malformed from wrong — same verdict shape.
  }

  // Identity for rate limiting: verified JWT sub when available, else client IP.
  // V2-FIX (reviewwhisper.md): use the LAST (rightmost) X-Forwarded-For entry — the
  // LEFTMOST entry is client-spoofable (anyone can send `X-Forwarded-For: 1.2.3.4` and
  // proxies typically append), so it would let attackers rotate fake identities to
  // dodge the limiter. The rightmost entry is the one appended by the trusted edge
  // proxy; fall back to x-real-ip, then "unknown".
  const authHeader = request.headers.get("Authorization") ?? "";
  const bearer = authHeader.startsWith("Bearer ") ? authHeader.slice(7) : "";
  const sub = bearer ? await extractVerifiedSub(bearer) : null;
  const forwardedFor = request.headers.get("x-forwarded-for");
  const rightmostXff = forwardedFor?.split(",").pop()?.trim() ?? "";
  const ip = rightmostXff || request.headers.get("x-real-ip")?.trim() || "unknown";
  const identity = sub ?? `ip:${ip}`;

  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const baseUrl = Deno.env.get("SUPABASE_URL");
  if (!serviceRole || !baseUrl) return json({ error: "Server misconfigured" }, 503);

  // M-4 FIX (reviewwhisper.md): the old check-then-insert (SELECT window, compare,
  // INSERT attempt) was two independent REST calls and raced under parallel guesses.
  // The atomic RPC (migration 20260826) performs count + lockout + insert + cleanup in
  // ONE transaction; it raises P0002 when the identity is locked out.
  // V2-FIX (reviewwhisper.md): the previous comment claimed a successful attempt also
  // consumes lockout budget — wrong: the SQL counts only `ok = false` rows toward the
  // threshold, so correct passwords never burn the failure budget.
  try {
    const match = await passwordsMatch(candidate, expectedSecret);

    const rpcRes = await fetch(`${baseUrl}/rest/v1/rpc/whisper_bypass_attempt`, {
      method: "POST",
      headers: {
        apikey: serviceRole,
        Authorization: `Bearer ${serviceRole}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ p_identity: identity, p_ok: match }),
    });

    if (!rpcRes.ok) {
      const bodyText = await rpcRes.text().catch(() => "");
      if (bodyText.includes("rate_limited")) {
        return json({ error: "Too many attempts — try again later" }, 429);
      }
      console.error("whisper_bypass_attempt failed", rpcRes.status, bodyText);
      return json({ error: "Verification unavailable" }, 503);
    }

    if (!match) return json({ ok: false }, 200);
    return json({ ok: true }, 200);
  } catch (err) {
    console.error("whisper-bypass-verify failed", err);
    return json({ error: "Verification unavailable" }, 503);
  }
});
