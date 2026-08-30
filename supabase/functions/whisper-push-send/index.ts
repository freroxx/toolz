// Supabase Edge Function: whisper-push-send (V3-FIX, task F)
//
// FCM push scaffold invoked by a Supabase Database Webhook on INSERT to
// public.messages (no cron). Flow:
//   1. Auth: accept EITHER the service-role key as bearer OR a shared webhook
//      secret header (x-whisper-push-secret) — whichever is configured.
//   2. Read the inserted message row from the webhook payload.
//   3. Skip if the receiver was recently active (< 60 s last_seen_at): their app is
//      open and realtime already delivers the message.
//   4. Look up receiver FCM tokens in whisper_fcm_tokens via the service role key.
//   5. Send a DATA-ONLY FCM HTTP v1 message per token ({whisper_new_message:true,
//      senderId}) — NO message content (privacy), android priority HIGH,
//      collapse_key = senderId.
//   6. Prune tokens rejected by FCM as invalid/unregistered.
//
// Rate-limit friendly: exactly ONE send attempt per token; errors are logged
// generically (no payloads, no tokens). Client SDK integration is intentionally
// deferred — see docs/WHISPER_PUSH_SETUP.md.
import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { SignJWT, importPKCS8 } from "https://esm.sh/jose@5.9.6";

const RECENT_SEEN_WINDOW_MS = 60_000;
const WEBHOOK_SECRET_HEADER = "x-whisper-push-secret";
const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

// Cached Google OAuth2 access token minted from the service account JWT
// (module-level so warm isolates reuse it until shortly before expiry).
let _cachedAccessToken: { token: string; expMs: number; projectId: string } | null = null;

serve(async (request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: { "Access-Control-Allow-Origin": "*" } });
  }
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const webhookSecret = Deno.env.get("WHISPER_PUSH_WEBHOOK_SECRET");

  // 1. Auth: service-role bearer OR shared webhook secret (accept either).
  const authHeader = request.headers.get("Authorization");
  const bearer = authHeader?.startsWith("Bearer ") ? authHeader.slice(7) : "";
  const providedSecret = request.headers.get(WEBHOOK_SECRET_HEADER);
  const isServiceRole = !!serviceRoleKey && bearer.length > 0 && timingSafeEqual(bearer, serviceRoleKey);
  const isWebhookSecret =
    !!webhookSecret && !!providedSecret && timingSafeEqual(providedSecret, webhookSecret);
  if (!isServiceRole && !isWebhookSecret) return json({ error: "Unauthorized" }, 401);

  if (!supabaseUrl || !serviceRoleKey) return json({ error: "Server misconfigured" }, 500);
  const saJson = Deno.env.get("FCM_SERVICE_ACCOUNT_JSON");
  if (!saJson) return json({ error: "Push is not configured" }, 503);

  // 2. Parse the Database Webhook payload (INSERT on public.messages or public.friends).
  let record: any;
  let table: string = "";
  try {
    const payload = await request.json();
    record = payload?.record ?? payload?.new ?? null;
    table = payload?.table ?? "";
  } catch {
    return json({ error: "Invalid request" }, 400);
  }

  const isFriendRequest = table === "friends" || (typeof record?.user_a === "string" && typeof record?.user_b === "string");
  let messageId = "";
  let senderId = "";
  let receiverId = "";
  let isFriendPush = false;
  let senderName = "";

  if (isFriendRequest) {
    if (record?.status !== "pending") {
      return json({ skipped: "not_pending_friend_request" }, 200);
    }
    senderId = typeof record?.user_a === "string" ? record.user_a : "";
    receiverId = typeof record?.user_b === "string" ? record.user_b : "";
    messageId = typeof record?.id === "string" ? record.id : "";
    isFriendPush = true;
  } else {
    messageId = typeof record?.id === "string" ? record.id : "";
    senderId = typeof record?.sender_id === "string" ? record.sender_id : "";
    receiverId = typeof record?.receiver_id === "string" ? record.receiver_id : "";
  }

  if (!senderId || !receiverId || senderId === receiverId) {
    // Webhooks must acknowledge with 2xx or Supabase retries forever.
    return json({ skipped: "not_a_pushable_event" }, 200);
  }

  const serviceHeaders = {
    apikey: serviceRoleKey,
    Authorization: `Bearer ${serviceRoleKey}`,
    "Content-Type": "application/json",
  };

  if (isFriendPush) {
    try {
      const senderProfileRes = await fetch(
        `${supabaseUrl}/rest/v1/profiles?id=eq.${encodeURIComponent(senderId)}&select=display_name,username&limit=1`,
        { headers: serviceHeaders },
      );
      if (senderProfileRes.ok) {
        const pRows = await senderProfileRes.json();
        const p = pRows?.[0];
        senderName = p?.display_name || p?.username || "";
      }
    } catch {
      // Best effort profile name resolution
    }
  }

  // 3. Skip when the receiver was seen recently — the app is foregrounded and the
  //    message arrives via realtime anyway; a push would be redundant noise.
  try {
    const profileRes = await fetch(
      `${supabaseUrl}/rest/v1/profiles?id=eq.${encodeURIComponent(receiverId)}&select=last_seen_at&limit=1`,
      { headers: serviceHeaders },
    );
    if (profileRes.ok) {
      const rows = await profileRes.json();
      const lastSeenAt = rows?.[0]?.last_seen_at;
      if (typeof lastSeenAt === "string") {
        const seenMs = Date.parse(lastSeenAt);
        if (!Number.isNaN(seenMs) && Date.now() - seenMs < RECENT_SEEN_WINDOW_MS) {
          return json({ skipped: "receiver_recently_active" }, 200);
        }
      }
    }
  } catch (profileError) {
    console.error("profiles last_seen lookup failed", profileError);
    // Fall through: an unavailable presence check must not block delivery.
  }

  // 4. Recipient tokens (service role bypasses RLS).
  let tokens: string[];
  try {
    const tokenRes = await fetch(
      `${supabaseUrl}/rest/v1/whisper_fcm_tokens?user_id=eq.${encodeURIComponent(receiverId)}&select=token`,
      { headers: serviceHeaders },
    );
    if (!tokenRes.ok) {
      console.error("token lookup failed", tokenRes.status);
      return json({ error: "Token lookup failed" }, 502);
    }
    tokens = (await tokenRes.json())
      .map((row: any) => (typeof row?.token === "string" ? row.token : ""))
      .filter((t: string) => t.length > 0);
  } catch (tokenError) {
    console.error("token lookup threw", tokenError);
    return json({ error: "Token lookup failed" }, 502);
  }
  if (tokens.length === 0) return json({ sent: 0, skipped: "no_tokens" }, 200);

  // 5. Mint the OAuth2 access token for FCM HTTP v1.
  let accessToken: string;
  let projectId: string;
  try {
    ({ accessToken, projectId } = await getFcmAccessToken(saJson));
  } catch (authError) {
    console.error("FCM access-token mint failed", authError);
    return json({ error: "FCM authentication failed" }, 503);
  }

   // Single attempt per token; data-only payload carries NO content (privacy).
   // FIX: include messageId for client-side dedupe and collapse handling.
   let sent = 0;
   let pruned = 0;
   const fcmDataPayload: Record<string, string> = isFriendPush
     ? { whisper_friend_request: "true", senderId, senderName, messageId }
     : { whisper_new_message: "true", senderId, messageId };

   for (const token of tokens) {
     try {
       const fcmRes = await fetch(
         `https://fcm.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/messages:send`,
         {
           method: "POST",
           headers: {
             Authorization: `Bearer ${accessToken}`,
             "Content-Type": "application/json",
           },
           body: JSON.stringify({
             message: {
               token,
               data: fcmDataPayload,
               android: { priority: "HIGH", collapse_key: isFriendPush ? `friend_${senderId}` : senderId },
             },
           }),
           signal: AbortSignal.timeout(10_000),
         },
       );
      if (fcmRes.ok) {
        sent++;
        continue;
      }
      // Log status + tiny body slice only; never log tokens or payload details.
      console.error("FCM send failed", fcmRes.status, (await fcmRes.text().catch(() => "")).slice(0, 120));
      if (await isInvalidTokenResponse(fcmRes)) {
        // user_id is the PK: deleting by it removes this stale token row.
        const delRes = await fetch(
          `${supabaseUrl}/rest/v1/whisper_fcm_tokens?user_id=eq.${encodeURIComponent(receiverId)}`,
          { method: "DELETE", headers: serviceHeaders },
        );
        if (delRes.ok || delRes.status === 404) pruned++;
        else console.error("stale token prune failed", delRes.status);
      }
    } catch (sendError) {
      console.error("FCM send threw", sendError);
    }
  }

  return json({ sent, pruned }, 200);
});

/** Constant-time string compare for secret/header checks. */
function timingSafeEqual(a: string, b: string): boolean {
  const ab = new TextEncoder().encode(a);
  const bb = new TextEncoder().encode(b);
  if (ab.length !== bb.length) return false;
  let diff = 0;
  for (let i = 0; i < ab.length; i++) diff |= ab[i] ^ bb[i];
  return diff === 0;
}

/**
 * Exchanges the service-account JWT (RS256, signed via jose) at Google's OAuth2
 * token endpoint for a short-lived FCM access token. Cached until exp − 60 s.
 */
async function getFcmAccessToken(saJson: string): Promise<{ accessToken: string; projectId: string }> {
  const cached = _cachedAccessToken;
  if (cached && Date.now() < cached.expMs - 60_000) {
    return { accessToken: cached.token, projectId: cached.projectId };
  }
  const sa = JSON.parse(saJson);
  if (typeof sa.private_key !== "string" || typeof sa.client_email !== "string") {
    throw new Error("service account JSON missing private_key/client_email");
  }
  const projectId = typeof sa.project_id === "string" ? sa.project_id : "";
  if (!projectId) throw new Error("service account JSON missing project_id");
  const tokenUri = typeof sa.token_uri === "string" ? sa.token_uri : "https://oauth2.googleapis.com/token";
  const nowSec = Math.floor(Date.now() / 1000);
  // JSON.parse already unescapes \n in the PEM; importPKCS8 accepts that directly.
  const privateKey = await importPKCS8(sa.private_key, "RS256");
  const assertion = await new SignJWT({ scope: FCM_SCOPE })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" })
    .setIssuer(sa.client_email)
    .setAudience(tokenUri)
    .setIssuedAt(nowSec)
    .setExpirationTime(nowSec + 3600)
    .sign(privateKey);

  const res = await fetch(tokenUri, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }).toString(),
    signal: AbortSignal.timeout(10_000),
  });
  if (!res.ok) throw new Error(`oauth exchange failed (${res.status})`);
  const out = await res.json();
  const expiresInSec = Number(out.expires_in ?? 3600);
  _cachedAccessToken = {
    token: String(out.access_token),
    expMs: Date.now() + expiresInSec * 1000,
    projectId,
  };
  return { accessToken: String(out.access_token), projectId };
}

/**
 * True when FCM rejected the TOKEN (not the request): HTTP 404/410, or an error
 * body whose `error.status` marks unregistered/not-found/invalid-registration.
 */
async function isInvalidTokenResponse(res: Response): Promise<boolean> {
  if (res.status === 404 || res.status === 410) return true;
  try {
    const body = await res.json();
    const status = body?.error?.status;
    return (
      status === "UNREGISTERED" ||
      status === "NOT_FOUND" ||
      (status === "INVALID_ARGUMENT" && /registration|token/i.test(String(body?.error?.message ?? "")))
    );
  } catch {
    return false;
  }
}

function json(body: unknown, statusCode: number) {
  return new Response(JSON.stringify(body), {
    status: statusCode,
    headers: {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": "*",
    },
  });
}
