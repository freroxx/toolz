import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { jwtVerify } from "https://esm.sh/jose@5.9.6";

// Deletes the caller's GoTrue user account (and everything cascade-deleted from it).
// The bearer token is genuinely verified in-function: signature and exp are checked
// against the project's JWT secret (SUPABASE_JWT_SECRET is injected into every edge
// function automatically), and the deleted user id comes from the verified payload's
// sub claim — never from an unverified header.
serve(async (request) => {
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);
  const auth = request.headers.get("Authorization");
  if (!auth?.startsWith("Bearer ")) return json({ error: "Unauthorized" }, 401);

  const userId = await extractVerifiedUserId(auth.slice(7));
  if (!userId) return json({ error: "Invalid token" }, 401);

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