import { serve } from "https://deno.land/std@0.224.0/http/server.ts";

serve(async (request) => {
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);
  // Deploy with verify_jwt=true; duplicate this guard as defense in depth.
  if (!request.headers.get("Authorization")?.startsWith("Bearer ")) return json({ error: "Unauthorized" }, 401);
  try {
    const { id } = await request.json();
    const deletionUrl = typeof id === "string" ? new URL(id) : null;
    if (!deletionUrl || deletionUrl.protocol !== "https:" || deletionUrl.hostname !== "ibb.co") {
      return json({ error: "Invalid deletion capability" }, 400);
    }
    const upstream = await fetch(deletionUrl, { method: "GET", redirect: "follow" });
    if (!upstream.ok) return json({ error: "Image deletion failed" }, 502);
    return json({ success: true }, 200);
  } catch {
    return json({ error: "Invalid request" }, 400);
  }
});

function json(body: unknown, status: number) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store" } });
}
