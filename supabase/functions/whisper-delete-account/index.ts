import { serve } from "https://deno.land/std@0.224.0/http/server.ts";

// Deletes the caller's GoTrue user account (and everything cascade-deleted from it).
// Deploy with verify_jwt=true; the platform gateway already validates the bearer token,
// the payload parsing below is defense in depth.
serve(async (request) => {
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);
  const auth = request.headers.get("Authorization");
  if (!auth?.startsWith("Bearer ")) return json({ error: "Unauthorized" }, 401);

  const userId = extractUserId(auth.slice(7));
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

function extractUserId(jwt: string): string | null {
  try {
    const payload = jwt.split(".")[1];
    const decoded = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
    const json = JSON.parse(decoded);
    return typeof json.sub === "string" ? json.sub : null;
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