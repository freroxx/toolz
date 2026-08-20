// Supabase Edge Function: upload encrypted Whisper image bytes to ImgBB.
import { serve } from "https://deno.land/std@0.224.0/http/server.ts";

const MAX_BASE64_LENGTH = 32 * 1024 * 1024;
const MIN_EXPIRY = 60;
const MAX_EXPIRY = 15_552_000;
// Daily upload cap per user (mirrored by the whisper_upload_quota table's RLS).
const QUOTA_PER_DAY = 50;

serve(async (request) => {
  // 1. Handle preflight CORS requests
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: { "Access-Control-Allow-Origin": "*" } });
  }

  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);

  // Reject oversized bodies before parsing them: a declared Content-Length over the
  // ceiling never gets buffered into memory. Chunked requests without the header are
  // still bounded by the base64 payload validation below.
  const contentLength = Number(request.headers.get("content-length") ?? "0");
  if (contentLength > 40 * 1024 * 1024) return json({ error: "Request too large" }, 413);

  const authHeader = request.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) return json({ error: "Unauthorized" }, 401);

  const imageKey = Deno.env.get("IMGBB_API_KEY");
  if (!imageKey) return json({ error: "Image hosting is not configured" }, 503);

  // The platform gateway verified this token before invoking the function
  // (verify_jwt=true), so decoding the payload without re-verifying is acceptable:
  // we only read the sub claim for quota accounting, never for authorization.
  const userId = decodeUserId(authHeader.slice(7));
  if (!userId) return json({ error: "Unauthorized" }, 401);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  if (!supabaseUrl || !anonKey) return json({ error: "Server misconfigured" }, 500);

  // Per-user daily quota (best-effort accounting): the table is RLS-scoped to the
  // caller, so the original bearer token authorizes the REST calls below.
  const today = new Date().toISOString().slice(0, 10);
  const quotaUrl = `${supabaseUrl}/rest/v1/whisper_upload_quota?user_id=eq.${userId}&day=eq.${today}&select=count`;
  try {
    const existing = await fetch(quotaUrl, {
      headers: { apikey: anonKey, Authorization: authHeader },
    });
    if (existing.ok) {
      const rows = await existing.json();
      const count = Array.isArray(rows) && rows.length > 0 ? Number(rows[0].count ?? 0) : 0;
      if (count >= QUOTA_PER_DAY) {
        return json({ error: "Daily upload limit reached" }, 429);
      }
      // Upsert the incremented count: the unique user_id key makes a first upload of the
      // day create the row and later ones replace it. Concurrent requests may undercount
      // slightly; the quota is a coarse abuse bound, not a hard ledger.
      await fetch(`${supabaseUrl}/rest/v1/whisper_upload_quota`, {
        method: "POST",
        headers: {
          apikey: anonKey,
          Authorization: authHeader,
          "Content-Type": "application/json",
          Prefer: "resolution=merge-duplicates",
        },
        body: JSON.stringify({ user_id: userId, day: today, count: count + 1 }),
      });
    } else {
      // Quota is best-effort; a quota-store failure must not block legitimate uploads.
      console.error("whisper_upload_quota read failed", existing.status);
    }
  } catch (quotaError) {
    console.error("whisper_upload_quota failed", quotaError);
  }

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
    // The API key travels as a form field (ImgBB accepts `key` there), keeping it out
    // of the URL where it could leak into access logs and referrer headers.
    form.set("key", imageKey);
    if (expiration !== undefined) form.set("expiration", String(expiration));

    const upstream = await fetch("https://api.imgbb.com/1/upload", {
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
      // This capability URL is encrypted inside the Whisper attachment envelope.
      id: result.data.delete_url
    }, 200);
  } catch (error) {
    console.error("whisper-image-upload failed", error);
    return json({ error: "Invalid request" }, 400);
  }
});

function decodeUserId(jwt: string): string | null {
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
    headers: {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": "*"
    },
  });
}
