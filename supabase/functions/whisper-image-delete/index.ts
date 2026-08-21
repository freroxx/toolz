import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { jwtVerify } from "https://esm.sh/jose@5.9.6";

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
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store" } });
}
