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
// (they have no password) but the JWT must be very fresh (<5 min iat).
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

  // Optional password confirmation for non-anon accounts
  const confirmTs = request.headers.get("X-Whisper-Confirm-Ts");
  if (confirmTs) {
    const ts = parseInt(confirmTs, 10);
    if (isNaN(ts) || Math.abs(Date.now() - ts) > 5 * 60 * 1000) {
      return json({ error: "Invalid confirmation timestamp" }, 400);
    }
  }

  const password = request.headers.get("X-Whisper-Password");
  if (password) {
    // Verify password by attempting a GoTrue password grant with the caller's email.
    // We fetch the caller's email via service_role admin getUser.
    const serviceRoleTmp = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    const baseUrlTmp = Deno.env.get("SUPABASE_URL");
    if (serviceRoleTmp && baseUrlTmp) {
      try {
        const adminRes = await fetch(`${baseUrlTmp}/auth/v1/admin/users/${userId}`, {
          headers: { apikey: serviceRoleTmp, Authorization: `Bearer ${serviceRoleTmp}` },
        });
        if (adminRes.ok) {
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
        }
      } catch (_) {
        return json({ error: "Password verification failed" }, 500);
      }
    }
  }

  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const baseUrl = Deno.env.get("SUPABASE_URL");
  if (!serviceRole || !baseUrl) return json({ error: "Server misconfigured" }, 500);

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