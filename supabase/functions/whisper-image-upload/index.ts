// Supabase Edge Function: upload encrypted Whisper image bytes to ImgBB.
import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { jwtVerify } from "https://esm.sh/jose@5.9.6";

// M-19 FIX (reviewwhisper.md): lowered from 32 MB to 12 MB. The real client pipeline
// caps ciphertext at 5 MiB -> ~6.7 MiB PNG -> ~8.9 MiB base64, so 12 MB bounds abuse
// without ever rejecting a legitimate payload.
const MAX_BASE64_LENGTH = 12 * 1024 * 1024;
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
  const rawLen = request.headers.get("content-length");
  const contentLength = rawLen ? parseInt(rawLen, 10) : 0;
  if (rawLen && isNaN(contentLength)) return json({ error: "Invalid content length" }, 400);
  if (!isNaN(contentLength) && contentLength > 40 * 1024 * 1024) return json({ error: "Request too large" }, 413);

  const authHeader = request.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) return json({ error: "Unauthorized" }, 401);

  const imageKey = Deno.env.get("IMGBB_API_KEY");
  if (!imageKey) return json({ error: "Image hosting is not configured" }, 503);

  // Verify JWT in-function (defense-in-depth even with verify_jwt=true at gateway)
  // to prevent forged sub claims if gateway is bypassed or misconfigured.
  const userId = await extractVerifiedUserId(authHeader.slice(7));
  if (!userId) return json({ error: "Unauthorized" }, 401);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  if (!supabaseUrl || !anonKey) return json({ error: "Server misconfigured" }, 500);

  // Parse + validate the payload BEFORE consuming quota (M-3 FIX part 1): invalid or
  // oversized bodies no longer burn one of the caller's scarce daily slots.
  let image = "";
  let name = "whisper";
  let expiration: number | undefined = undefined;
  try {
    const payload = await request.json();
    image = typeof payload.image === "string" ? payload.image : "";
    name = typeof payload.name === "string" ? payload.name.replace(/[^a-zA-Z0-9_-]/g, "").slice(0, 80) : "whisper";
    expiration = payload.expiration == null ? undefined : Number(payload.expiration);

    if (!image || image.length > MAX_BASE64_LENGTH || image.length % 4 !== 0) {
      return json({ error: "Invalid encrypted image payload" }, 400);
    }
    // Validate base64 without giant regex (avoid 50-200ms isolate block for large payloads)
    let validB64 = true;
    for (let i = 0; i < image.length; i++) {
      const c = image.charCodeAt(i);
      if (!(c >= 65 && c <= 90 || c >= 97 && c <= 122 || c >= 48 && c <= 57 || c === 43 || c === 47 || c === 61)) { validB64 = false; break; }
    }
    if (!validB64) return json({ error: "Invalid encrypted image payload" }, 400);

    // Robustness: Validate expiration range
    if (expiration !== undefined && (!Number.isInteger(expiration) || expiration < MIN_EXPIRY || expiration > MAX_EXPIRY)) {
      return json({ error: "Invalid image expiration" }, 400);
    }
  } catch {
    return json({ error: "Invalid request" }, 400);
  }

  // Atomic per-user daily quota via RPC — serializes concurrent bursts, no undercount.
  // FAIL CLOSED (was fail-open): an RPC outage must not become an unlimited upload
  // window. The RPC ships with migration 20260822; if it is missing, uploads stay down.
  const today = new Date().toISOString().slice(0, 10);
  try {
    const quotaRes = await fetch(`${supabaseUrl}/rest/v1/rpc/whisper_increment_upload_quota`, {
      method: "POST",
      headers: {
        apikey: anonKey,
        Authorization: authHeader,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ p_day: today }),
    });
    if (!quotaRes.ok) {
      console.error("whisper_increment_upload_quota failed", quotaRes.status, await quotaRes.text());
      return json({ error: "Upload quota service unavailable" }, 503);
    }
    const count = Number(await quotaRes.json());
    if (count > QUOTA_PER_DAY) {
      return json({ error: "Daily upload limit reached" }, 429);
    }
  } catch (quotaError) {
    console.error("whisper_increment_upload_quota failed", quotaError);
    return json({ error: "Upload quota service unavailable" }, 503);
  }

  // Best-effort compensating decrement (M-3 FIX part 2): an ImgBB outage must not eat
  // the caller's daily slots. Floor-at-zero semantics live in the SQL function; a rare
  // concurrent double-refund only makes the counter slightly more generous.
  async function refundQuota(): Promise<void> {
    try {
      await fetch(`${supabaseUrl}/rest/v1/rpc/whisper_refund_upload_quota`, {
        method: "POST",
        headers: { apikey: anonKey, Authorization: authHeader, "Content-Type": "application/json" },
        body: JSON.stringify({ p_day: today }),
      });
    } catch (refundError) {
      console.error("whisper_refund_upload_quota failed (best-effort)", refundError);
    }
  }

  const form = new FormData();
  form.set("image", image);
  form.set("name", name || "whisper");
  // The API key travels as a form field (ImgBB accepts `key` there), keeping it out
  // of the URL where it could leak into access logs and referrer headers.
  form.set("key", imageKey);
  if (expiration !== undefined) form.set("expiration", String(expiration));

  let upstream: Response;
  try {
    upstream = await fetch("https://api.imgbb.com/1/upload", {
      method: "POST",
      body: form,
      signal: AbortSignal.timeout(15000),
    });
  } catch (uploadError) {
    console.error("ImgBB upload threw", uploadError);
    await refundQuota();
    return json({ error: "Encrypted image upload failed" }, 502);
  }

  let result: any;
  try { result = await upstream.json(); } catch { await refundQuota(); return json({ error: "Encrypted image upload failed" }, 502); }
  if (!upstream.ok || !result?.success) {
    console.error("ImgBB upload failed", upstream.status);
    await refundQuota();
    return json({ error: "Encrypted image upload failed" }, 502);
  }

  return json({
    url: result.data.url,
    // This capability URL is encrypted inside the Whisper attachment envelope.
    id: result.data.delete_url
  }, 200);
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
    headers: {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": "*"
    },
  });
}
