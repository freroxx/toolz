import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
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

// Deletes the caller's GoTrue user account (and everything cascade-deleted from it).
// The bearer token is genuinely verified in-function: signature and exp are checked
// against the project's JWT secret (SUPABASE_JWT_SECRET is injected into every edge
// function automatically), and the deleted user id comes from the verified payload's
// sub claim — never from an unverified header.
//
// P0-4 FIX (reviewwhisper.md): A stolen JWT alone must NOT delete the account.
// For non-anon users (email != *@whisper.toolz.app) the client must also send
// X-Whisper-Password which is verified here by re-authenticating via GoTrue's
// token endpoint before the service_role delete. V2-FIX (reviewwhisper.md): the
// code requires BOTH headers from every caller — X-Whisper-Password is mandatory
// too (it is only *verified* against GoTrue for non-anon accounts); anon-token
// users additionally rely on X-Whisper-Confirm-Ts (M-6) plus a very fresh
// (<5 min iat) JWT.
//
// M-5 FIX: ALL application-data cleanup happens HERE, under service role, BEFORE
// the GoTrue identity is deleted — no more fragile client-side post-delete cleanup.
serve(async (request) => {
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);
  const auth = request.headers.get("Authorization");
  if (!auth?.startsWith("Bearer ")) return json({ error: "Unauthorized" }, 401);

  const jwt = auth.slice(7);
  const userId = await extractVerifiedUserId(jwt);
  if (!userId) return json({ error: "Invalid token" }, 401);

  // V3-FIX (round 3): destructive-endpoint rate limiting. Identity is the verified
  // sub (always available post-auth), and the limiter RPC runs BEFORE doing any work.
  // Service-role env is resolved up front too: every later step needs it.
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const baseUrl = Deno.env.get("SUPABASE_URL");
  if (!serviceRole || !baseUrl) return json({ error: "Server misconfigured" }, 503);

  // V3-FIX (round 3): gate call with p_ok=false — a failure row stands unless the
  // request ultimately succeeds, in which case an ok=true row is recorded right
  // before the GoTrue admin delete below. rate_limited → 429; any other RPC error
  // fails closed → 503 abort.
  const gate = await destructiveAttempt(serviceRole, baseUrl, "delete-account", userId, false);
  if (!gate) return json({ error: "Rate limiter unavailable" }, 503);
  if (gate.limited) return json({ error: "Too many attempts — try again later" }, 429);

  // Enforce JWT freshness for delete: iat must be within 5 minutes to prevent replay of stolen old token.
  const iatOk = await isJwtFresh(jwt, 5 * 60 * 1000);
  if (!iatOk) return json({ error: "Session too old — please re-login to delete" }, 401);

  // Confirmation nonce. M-6 FIX (reviewwhisper.md): MANDATORY for anon-token users —
  // they have no verified password proof, so a fresh confirmation timestamp is the
  // second factor; an attacker replaying a stolen fresh JWT can no longer simply omit
  // the header. V2-FIX (reviewwhisper.md): the header comment above used to claim the
  // password was optional for anon accounts — the code has always required both
  // headers, so the comments now match the code (required for ALL callers; the GoTrue
  // password grant below only runs for non-anon accounts).
  const password = request.headers.get("X-Whisper-Password");
  const confirmTs = request.headers.get("X-Whisper-Confirm-Ts");
  if (!password || !confirmTs) {
    return json({ error: "Missing confirmation" }, 400);
  }
  {
    // V2-FIX (reviewwhisper.md): reject non-digit input before parseInt — values like
    // "12abc" or "0x10" must not parse partially into a plausible-looking timestamp.
    const ts = /^\d+$/.test(confirmTs) ? parseInt(confirmTs, 10) : NaN;
    if (isNaN(ts) || Math.abs(Date.now() - ts) > 5 * 60 * 1000) {
      return json({ error: "Invalid confirmation timestamp" }, 400);
    }
  }
  if (password) {
    // Verify password by attempting a GoTrue password grant with the caller's email.
    // We fetch the caller's email via service_role admin getUser.
    // V3-FIX (round 3): serviceRole/baseUrl are hoisted above (the limiter gate needs
    // them before any work), so the temporary copies are gone.
    // FAIL CLOSED: if the admin lookup cannot confirm whether this account even has a
    // password, deletion must not proceed — a stolen JWT must never be sufficient.
    let adminRes: Response;
    try {
      adminRes = await fetch(`${baseUrl}/auth/v1/admin/users/${userId}`, {
        headers: { apikey: serviceRole, Authorization: `Bearer ${serviceRole}` },
      });
    } catch (_) {
      return json({ error: "Password verification unavailable" }, 503);
    }
    if (!adminRes.ok) {
      return json({ error: "Password verification unavailable" }, 503);
    }
    try {
      const adminJson = await adminRes.json();
      const email = adminJson?.email as string | undefined;
      // V2-FIX (reviewwhisper.md): FAIL CLOSED — the old `if (email && ...)` skipped
      // password proof entirely when the lookup returned no email, so any account
      // GoTrue answered without an email could be deleted with a stolen JWT alone.
      // A missing email means we cannot tell which identity type this is: refuse.
      if (!email) {
        return json({ error: "Cannot verify identity type" }, 403);
      }
      if (!email.endsWith("@whisper.toolz.app")) {
        // Non-anon must have password proof; verify via password grant.
        const verifyRes = await fetch(`${baseUrl}/auth/v1/token?grant_type=password`, {
          method: "POST",
          headers: { apikey: Deno.env.get("SUPABASE_ANON_KEY") ?? "", "Content-Type": "application/json" },
          body: JSON.stringify({ email, password }),
        });
        if (!verifyRes.ok) return json({ error: "Invalid password confirmation" }, 403);
      }
    } catch (_) {
      return json({ error: "Password verification failed" }, 503);
    }
  }

  // M-5 FIX (reviewwhisper.md): purge owned rows BEFORE the GoTrue delete, under service
  // role. The old flow let the CLIENT run RLS-scoped postgrest deletes AFTER user
  // deletion, matching a JWT sub that no longer corresponds to a live user — fragile,
  // and silently no-op if Supabase ever revokes sessions on delete.
  // V2-FIX (reviewwhisper.md): the per-table REST loop (two tables needed OR filters it
  // could not express atomically) is replaced by ONE service-role-only RPC
  // (migration 20260827) that deletes from every application table in a single
  // transaction — either everything is purged or nothing is.
  let purgeRes: Response;
  try {
    purgeRes = await fetch(`${baseUrl}/rest/v1/rpc/whisper_purge_account_data`, {
      method: "POST",
      headers: { apikey: serviceRole, Authorization: `Bearer ${serviceRole}`, "Content-Type": "application/json" },
      body: JSON.stringify({ p_uid: userId }),
    });
  } catch (purgeError) {
    console.error("whisper_purge_account_data threw", purgeError);
    // Fail closed: do not delete the identity while its data may be orphaned.
    return json({ error: "Account cleanup failed" }, 502);
  }
  // Fail closed: do not delete the identity while its data may be orphaned.
  if (!purgeRes.ok) {
    console.error("whisper_purge_account_data failed", purgeRes.status);
    return json({ error: "Account cleanup failed" }, 502);
  }

  // V3-FIX (round 3): the request has passed every check — flip the attempt to
  // ok=true BEFORE the irreversible GoTrue admin delete. Same mapping as the gate:
  // rate_limited → 429, any other RPC error fails closed (503) with nothing deleted.
  const success = await destructiveAttempt(serviceRole, baseUrl, "delete-account", userId, true);
  if (!success) return json({ error: "Rate limiter unavailable" }, 503);
  if (success.limited) return json({ error: "Too many attempts — try again later" }, 429);

  const upstream = await fetch(`${baseUrl}/auth/v1/admin/users/${userId}`, {
    method: "DELETE",
    headers: {
      apikey: serviceRole,
      Authorization: `Bearer ${serviceRole}`,
    },
  });
  if (!upstream.ok) {
    // 404 means the account is already gone — treat it as success.
    if (upstream.status === 404) return json({ success: true }, 200);
    return json({ error: "Account deletion failed" }, 502);
  }
  return json({ success: true }, 200);
});

// Key-type-agnostic verification (same fix as whisper-image-upload): legacy HS256
// secret first, then asymmetric signing keys via the project JWKS.

async function verifyProjectJwt(jwt: string): Promise<{ sub?: string; iat?: number } | null> {
  const secret = Deno.env.get("SUPABASE_JWT_SECRET");
  if (secret) {
    try {
      const { payload } = await jwtVerify(jwt, new TextEncoder().encode(secret), { algorithms: ["HS256"] });
      return payload as { sub?: string; iat?: number };
    } catch { /* fall through to asymmetric */ }
  }
  try {
    const { payload } = await jwtVerify(jwt, jwks(), { algorithms: ["ES256", "RS256", "EdDSA"] });
    return payload as { sub?: string; iat?: number };
  } catch {
    return null;
  }
}

async function isJwtFresh(jwt: string, maxAgeMs: number): Promise<boolean> {
  const payload = await verifyProjectJwt(jwt);
  const iat = typeof payload?.iat === "number" ? payload.iat * 1000 : 0;
  if (!iat) return false;
  return (Date.now() - iat) <= maxAgeMs;
}

async function extractVerifiedUserId(jwt: string): Promise<string | null> {
  const payload = await verifyProjectJwt(jwt);
  return typeof payload?.sub === "string" ? payload.sub : null;
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

function json(body: unknown, status: number) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}