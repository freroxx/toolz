// Supabase Edge Function: upload encrypted Whisper image bytes to ImgBB.
import { serve } from "https://deno.land/std@0.224.0/http/server.ts";

const MAX_BASE64_LENGTH = 32 * 1024 * 1024;
const MIN_EXPIRY = 60;
const MAX_EXPIRY = 15_552_000;

serve(async (request) => {
  // 1. Handle preflight CORS requests
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: { "Access-Control-Allow-Origin": "*" } });
  }

  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);

  const authHeader = request.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) return json({ error: "Unauthorized" }, 401);

  const imageKey = Deno.env.get("IMGBB_API_KEY");
  if (!imageKey) return json({ error: "Image hosting is not configured" }, 503);

  try {
    const payload = await request.json();
    const image = typeof payload.image === "string" ? payload.image : "";
    const name = typeof payload.name === "string" ? payload.name.replace(/[^a-zA-Z0-9_-]/g, "").slice(0, 80) : "whisper";
    const expiration = payload.expiration == null ? undefined : Number(payload.expiration);

    if (!image || image.length > MAX_BASE64_LENGTH || !/^[A-Za-z0-9+/=]+$/.test(image)) {
      return json({ error: "Invalid encrypted image payload" }, 400);
    }

    // Robustness: Validate expiration range
    if (expiration !== undefined && (!Number.isInteger(expiration) || expiration < MIN_EXPIRY || expiration > MAX_EXPIRY)) {
      return json({ error: "Invalid image expiration" }, 400);
    }

    const form = new FormData();
    form.set("image", image);
    form.set("name", name || "whisper");
    if (expiration !== undefined) form.set("expiration", String(expiration));

    const upstream = await fetch(`https://api.imgbb.com/1/upload?key=${encodeURIComponent(imageKey)}`, {
      method: "POST",
      body: form,
    });

    const result = await upstream.json();
    if (!upstream.ok || !result?.success) {
      console.error("ImgBB upload failed", upstream.status);
      return json({ error: "Encrypted image upload failed" }, 502);
    }

    return json({
      url: result.data.url,
      id: result.data.delete_hash // Keep the delete_hash for the deletion feature
    }, 200);
  } catch (error) {
    console.error("whisper-image-upload failed", error);
    return json({ error: "Invalid request" }, 400);
  }
});

function json(body: unknown, status: number) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": "*"
    },
  });
}
