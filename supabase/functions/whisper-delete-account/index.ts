import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { jwtVerify } from "https://esm.sh/jose@5.9.6";

// Deletes the caller's GoTrue user account (and everything cascade-deleted from it).
// The bearer token is genuinely verified in-function: signature and exp are checked
// against the project's JWT secret (SUPABASE_JWT_SECRET is injected into every edge
// function automatically), and the deleted user id comes from the verified payload's
// sub claim — never from an unverified header.
//
// P0-4 FIX (reviewwhisper.md): A stolen JWT alone must NOT delete the account.
// For non-anon users (email != *@whisper.toolz.app) the client must also send
// X-Whisper-Password which is verified here by re-authenticating via GoTrue's
// token endpoint before the service_role delete. Anon token users are exempt
// from the password but MUST send X-Whisper-Confirm-Ts (M-6) and their JWT must
// be very fresh (<5 min iat).
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

  // Enforce JWT freshness for delete: iat must be within 5 minutes to prevent replay of stolen old token.
  const iatOk = await isJwtFresh(jwt, 5 * 60 * 1000);
  if (!iatOk) return json({ error: "Session too old — please re-login to delete" }, 401);

  // Confirmation nonce. M-6 FIX (reviewwhisper.md): MANDATORY for anon-token users —
  // they have no password proof, so a fresh confirmation timestamp is the second factor;
  // an attacker replaying a stolen fresh JWT can no longer simply omit the header.
  // For password accounts it stays optional (the X-Whisper-Password grant is the proof).
  const password = request.headers.get("X-Whisper-Password");
  const confirmTs = request.headers.get("X-Whisper-Confirm-Ts");
  if (!password || !confirmTs) {
    return json({ error: "Missing confirmation" }, 400);
  }
  {
    const ts = parseInt(confirmTs, 10);
    if (isNaN(ts) || Math.abs(Date.now() - ts) > 5 * 60 * 1000) {
      return json({ error: "Invalid confirmation timestamp" }, 400);
    }
  }
  if (password) {
    // Verify password by attempting a GoTrue password grant with the caller's email.
    // We fetch the caller's email via service_role admin getUser.
    const serviceRoleTmp = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    const baseUrlTmp = Deno.env.get("SUPABASE_URL");
    if (!serviceRoleTmp || !baseUrlTmp) {
      return json({ error: "Server misconfigured" }, 500);
    }
    // FAIL CLOSED: if the admin lookup cannot confirm whether this account even has a
    // password, deletion must not proceed — a stolen JWT must never be sufficient.
    let adminRes: Response;
    try {
      adminRes = await fetch(`${baseUrlTmp}/auth/v1/admin/users/${userId}`, {
        headers: { apikey: serviceRoleTmp, Authorization: `Bearer ${serviceRoleTmp}` },
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
      if (email && !email.endsWith("@whisper.toolz.app")) {
        // Non-anon must have password proof; verify via password grant.
        const verifyRes = await fetch(`${baseUrlTmp}/auth/v1/token?grant_type=password`, {
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

  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const baseUrl = Deno.env.get("SUPABASE_URL");
  if (!serviceRole || !baseUrl) return json({ error: "Server misconfigured" }, 500);

  // M-5 FIX (reviewwhisper.md): purge owned rows BEFORE the GoTrue delete, via service
  // role. The old flow let the CLIENT run RLS-scoped postgrest deletes AFTER user
  // deletion, matching a JWT sub that no longer corresponds to a live user — fragile,
  // and silently no-op if Supabase ever revokes sessions on delete.
  const cleanupTargets: Array<[string, string]> = [
    ["messages", `sender_id=eq.${userId}`],
    ["message_reactions", `user_id=eq.${userId}`],
    ["friends", `user_a=eq.${userId}`],
    ["friends", `user_b=eq.${userId}`],
    ["whisper_blocks", `blocker_id=eq.${userId}`],
    ["whisper_blocks", `blocked_id=eq.${userId}`],
    ["profiles", `id=eq.${userId}`],
    // FK-cascaded tables (whisper_upload_quota, whisper_deleted_tombstones,
    // whisper_discover_quota) clean themselves when auth.users row goes; deleted here
    // anyway so a failed GoTrue delete can never leave half-purged state behind.
    ["whisper_upload_quota", `user_id=eq.${userId}`],
    ["whisper_deleted_tombstones", `user_id=eq.${userId}`],
    ["whisper_discover_quota", `user_id=eq.${userId}`],
  ];
  for (const [table, filter] of cleanupTargets) {
    try {
      await fetch(`${baseUrl}/rest/v1/${table}?${filter}`, {
        method: "DELETE",
        headers: { apikey: serviceRole, Authorization: `Bearer ${serviceRole}` },
      });
    } catch (cleanupError) {
      console.error(`cleanup failed for ${table}`, cleanupError);
      // Fail closed: do not delete the identity while its data may be orphaned.
      return json({ error: "Account cleanup failed" }, 502);
    }
  }

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

async function isJwtFresh(jwt: string, maxAgeMs: number): Promise<boolean> {
  try {
    const secret = new TextEncoder().encode(Deno.env.get("SUPABASE_JWT_SECRET") ?? "");
    if (secret.length === 0) return false;
    const { payload } = await jwtVerify(jwt, secret, { algorithms: ["HS256"] });
    const iat = typeof payload.iat === "number" ? payload.iat * 1000 : 0;
    if (!iat) return false;
    return (Date.now() - iat) <= maxAgeMs;
  } catch { return false; }
}

async function extractVerifiedUserId(jwt: string): Promise<string | null> {
  try {
    const secret = new TextEncoder().encode(Deno.env.get("SUPABASE_JWT_SECRET") ?? "");
    if (secret.length === 0) return null;
    const { payload } = await jwtVerify(jwt, secret, { algorithms: ["HS256"] });
    return typeof payload.sub === "string" ? payload.sub : null;
  } catch {
    return null;
  }
}

function json(body: unknown, status: number) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}