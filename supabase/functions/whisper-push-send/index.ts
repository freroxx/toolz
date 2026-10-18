// Supabase Edge Function: whisper-push-send (F-Droid swap: FCM → UnifiedPush)
//
// Push scaffold invoked by Supabase Database Webhooks (no cron). Flow:
//   1. Auth: accept EITHER the service-role key as bearer OR a shared webhook
//      secret header (x-whisper-push-secret) — whichever is configured.
//   2. Read the row from the webhook payload. 1:1 path: INSERT to
//      public.messages / public.friends. Group path (handleGroupPush): any
//      webhook op on whisper_group_invites / whisper_group_messages /
//      whisper_group_events, but ONLY type=INSERT is fanned out — a
//      CANCEL/decline invite DELETE (or a sender message wipe) must never
//      re-fire the push for the thing just withdrawn. Story path
//      (handleStoryPush): INSERT on whisper_story_recipients — one
//      per-recipient fan-out row, one sealed descriptor, one friend.
//      DASHBOARD CHECKLIST: one webhook per group table, INSERT-only; the
//      in-code op gate is defense-in-depth, not the primary filter.
//   3. Skip if the receiver was recently active (< 60 s last_seen_at): their app is
//      open and realtime already delivers the message.
//   4. Look up receiver push targets in whisper_push_targets via the service
//      role key. Group fan-out: invites → the invitee; messages → every member
//      except the sender (with senderName); join/leave/decline events → owner +
//      admins (declines owner-only); admin-action events
//      (remove/promote/demote/rename/picture/settings) → members except actor.
//   5. Deliver the DATA-ONLY ping — NO content (privacy) — per target kind:
//      - provider='webpush': RFC 8291-encrypted aes128gcm payload + RFC 8292
//        VAPID auth (the embedded FCM distributor and other webpush-capable
//        UnifiedPush distributors). The push service sees an opaque blob.
//      - provider='plain' : raw wake-ping JSON to the endpoint (ntfy / NextPush
//        UnifiedPush mode). Same id/name keys the old FCM pings already carried.
//      Group payloads carry group/actor NAMES (like 1:1 senderName): accepted
//      and documented in WHISPER.md §11.
//   6. Prune targets rejected as invalid/unregistered (HTTP 404/410).
//
// Env:
//   WHISPER_VAPID_JWK      — private P-256 JWK (x,y,d) signing the VAPID pings.
//                            Generate with scripts/generate-vapid-keys.sh; MUST
//                            match the app's WHISPER_VAPID_PUBLIC_KEY.
//   WHISPER_VAPID_SUBJECT  — optional contact URI for the JWT `sub` claim.
//   SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY / WHISPER_PUSH_WEBHOOK_SECRET
//
// Rate-limit friendly: exactly ONE send attempt per target; errors are logged
// generically (no payloads, no endpoints, no keys).
import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import {
  encryptWebPush,
  parseVapidJwk,
  type VapidJwk,
  vapidAuthorizationHeader,
} from "./webpush.ts";

const RECENT_SEEN_WINDOW_MS = 60_000;
const WEBHOOK_SECRET_HEADER = "x-whisper-push-secret";
const DEFAULT_VAPID_SUBJECT = "mailto:push@toolz.app";
// RFC 8030 requires a TTL on every push POST; a wake ping stays useful long
// after the original message (the device may be offline for a while) — 24 h.
const PUSH_TTL_SECONDS = "86400";

interface PushTarget {
  provider: string;
  endpoint: string;
  keysP256dh: string;
  keysAuth: string;
}

interface GroupPushEnv {
  supabaseUrl: string;
  serviceRoleKey: string;
  serviceHeaders: Record<string, string>;
  vapidJwk: VapidJwk | null;
  vapidSubject: string;
}

interface DeliveryResult {
  sent: number;
  pruned: number;
  skipped?: string;
}

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
  // VAPID is parsed per request (cheap); a missing key only degrades webpush
  // targets — plain (ntfy/NextPush) targets still deliver.
  const vapidJwk = parseVapidJwk(Deno.env.get("WHISPER_VAPID_JWK"));
  const vapidSubject = Deno.env.get("WHISPER_VAPID_SUBJECT") ?? DEFAULT_VAPID_SUBJECT;
  if (!vapidJwk) {
    console.error("WHISPER_VAPID_JWK not configured — webpush targets will be skipped");
  }

  // 2. Parse the Database Webhook payload (INSERT on public.messages or public.friends,
  // or any operation on the Phase-2 group tables — group ops are gated below).
  let record: any;
  let table: string = "";
  let op: string = "";
  try {
    const payload = await request.json();
    record = payload?.record ?? payload?.new ?? null;
    table = payload?.table ?? "";
    op = typeof payload?.type === "string" ? payload.type : "";
  } catch {
    return json({ error: "Invalid request" }, 400);
  }

  const isFriendRequest = table === "friends" || (typeof record?.user_a === "string" && typeof record?.user_b === "string");
  let messageId = "";
  let senderId = "";
  let receiverId = "";
  let senderName = "";
  let isFriendPush = false;

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
    // (Group/story tables branch below before this guard — recipients rows
    // carry no receiver_id; the story's poster is resolved from its parent row.)
    if (!isGroupTable(table) && !isStoryPushTable(table)) {
      return json({ skipped: "not_a_pushable_event" }, 200);
    }
  }

  const serviceHeaders = {
    apikey: serviceRoleKey,
    Authorization: `Bearer ${serviceRoleKey}`,
    "Content-Type": "application/json",
  };
  const env: GroupPushEnv = { supabaseUrl, serviceRoleKey, serviceHeaders, vapidJwk, vapidSubject };

  // ── Phase-2 groups: invite / message / join-leave-decline fan-out ──
  // INSERT-only: a CANCEL/decline invite DELETE (or a sender message wipe)
  // must never re-fire the push for the thing just withdrawn.
  if (isGroupTable(table) && record && typeof record === "object") {
    if (op && op !== "INSERT") return json({ skipped: "not_insert_group_event" }, 200);
    return json(await handleGroupPush(record, table, env), 200);
  }

  // ── Daily Ups: per-recipient story fan-out ──
  // One recipient row = exactly one friend ping. The sealed descriptor never
  // travels here; only the poster identity (names already ride group pushes
  // under the same §11 visibility contract).
  if (isStoryPushTable(table) && record && typeof record === "object") {
    if (op && op !== "INSERT") return json({ skipped: "not_insert_story_event" }, 200);
    return json(await handleStoryPush(record, env), 200);
  }

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
    if (!receiverId) return json({ skipped: "not_a_pushable_event" }, 200);
  }

  // 3. Skip when the receiver was seen recently — the app is foregrounded and the
  //    message arrives via realtime anyway; a push would be redundant noise.
  const seenSkip = await recentlyActive(env, receiverId);
  if (seenSkip) return json({ skipped: seenSkip }, 200);

  // Data-only payload: NO content (privacy). messageId enables client-side
  // dedupe with the in-app realtime path.
  const dataPayload: Record<string, string> = isFriendPush
    ? { whisper_friend_request: "true", senderId, senderName, messageId }
    : { whisper_new_message: "true", senderId, messageId };

  const targets = await targetsForUser(env, receiverId);
  if (targets.length === 0) return json({ sent: 0, skipped: "no_push_targets" }, 200);
  return json(await deliver(dataPayload, targets, env), 200);
});

/** Group tables that fan out through handleGroupPush instead of the 1:1 path. */
function isGroupTable(table: string): boolean {
  return table === "whisper_group_invites" ||
    table === "whisper_group_messages" ||
    table === "whisper_group_events";
}

/** Daily Ups per-recipient fan-out rows (one INSERT = one friend ping). */
function isStoryPushTable(table: string): boolean {
  return table === "whisper_story_recipients";
}

/**
 * Service-role PostgREST GET; returns the parsed row array. Throws on network
 * or non-2xx — callers that can degrade (presence check, display names) catch
 * it, and only the table name (never filter values) reaches error logs.
 */
async function restRows(env: GroupPushEnv, path: string): Promise<any[]> {
  const res = await fetch(`${env.supabaseUrl}/rest/v1/${path}`, {
    headers: { ...env.serviceHeaders, Accept: "application/json" },
  });
  if (!res.ok) {
    throw new Error(`PostgREST ${path.split("?")[0]} → ${res.status}`);
  }
  const rows = await res.json();
  return Array.isArray(rows) ? rows : [];
}

/** `skipped` reason when the user was seen in-app within the no-push window. */
async function recentlyActive(env: GroupPushEnv, userId: string): Promise<string | null> {
  if (!userId) return null;
  try {
    const rows = await restRows(env, `profiles?id=eq.${encodeURIComponent(userId)}&select=last_seen_at&limit=1`);
    const lastSeenAt = rows?.[0]?.last_seen_at;
    if (typeof lastSeenAt === "string") {
      const seenMs = Date.parse(lastSeenAt);
      if (!Number.isNaN(seenMs) && Date.now() - seenMs < RECENT_SEEN_WINDOW_MS) {
        return "receiver_recently_active";
      }
    }
  } catch (e) {
    console.error("profiles last_seen lookup failed", e instanceof Error ? e.message : e);
    // An unavailable presence check must not block delivery.
  }
  return null;
}

/** Push targets of one user via the service role (RLS bypass). */
async function targetsForUser(env: GroupPushEnv, userId: string): Promise<PushTarget[]> {
  const rows = await restRows(
    env,
    `whisper_push_targets?user_id=eq.${encodeURIComponent(userId)}&select=provider,endpoint,keys_p256dh,keys_auth`,
  );
  return (Array.isArray(rows) ? rows : [])
    .map((row: any) => ({
      provider: typeof row?.provider === "string" ? row.provider : "",
      endpoint: typeof row?.endpoint === "string" ? row.endpoint : "",
      keysP256dh: typeof row?.keys_p256dh === "string" ? row.keys_p256dh : "",
      keysAuth: typeof row?.keys_auth === "string" ? row.keys_auth : "",
    }))
    .filter((t: PushTarget) => t.endpoint.startsWith("https://"));
}

async function profileName(env: GroupPushEnv, userId: string): Promise<string> {
  try {
    const rows = await restRows(env, `profiles?id=eq.${encodeURIComponent(userId)}&select=display_name,username&limit=1`);
    return rows?.[0]?.display_name || rows?.[0]?.username || "";
  } catch {
    // A missing display name only degrades notification copy.
    return "";
  }
}

/**
 * Subject user id of admin events whose payload is a quoted id
 * (`"uuid"` for remove/promote/demote/invite/cancel). Empty for anything else.
 */
function eventSubjectId(payload: unknown): string {
  if (typeof payload !== "string") return "";
  const t = payload.trim();
  if (t.length >= 33 && t.startsWith('"') && t.endsWith('"')) {
    const inner = t.slice(1, -1);
    if (/^[0-9a-fA-F-]{10,64}$/.test(inner)) return inner;
  }
  return "";
}

/**
 * Phase-2 groups fan-out. Data-only payloads, no content — same privacy
 * contract as 1:1. Recipients mirror the client defaults (owner/admins for
 * join/leave/decline; the client suppresses anything its toggle excludes):
 * - whisper_group_invites INSERT → the invitee;
 * - whisper_group_messages INSERT → every member except the sender (now with
 *   senderName, like 1:1);
 * - whisper_group_events INSERT (join/leave/decline) → owner + admins;
 * - whisper_group_events INSERT (remove/promote/demote/rename/picture/
 *   settings/policy) → every member except the actor (remove also reaches the
 *   removed subject directly).
 * Other event types (create/invite/cancel) stay in-app only: they spam no
 * channel, and cancel is covered by the invite-row delete the invitee
 * already watches. A POLICY change is a kind of settings write — members get
 * the same data-only ping (kind=policy) so capability updates reach closed
 * apps, and the client then re-syncs the signed policy on open.
 */
async function handleGroupPush(
  record: any,
  table: string,
  env: GroupPushEnv,
): Promise<DeliveryResult> {
  const str = (v: unknown) => (typeof v === "string" ? v : "");
  if (table === "whisper_group_invites") {
    const groupId = str(record?.group_id);
    const userId = str(record?.user_id);
    const invitedBy = str(record?.invited_by);
    if (!groupId || !userId) return { sent: 0, pruned: 0, skipped: "not_a_pushable_event" };
    const groupRows = await restRows(env, `whisper_groups?id=eq.${encodeURIComponent(groupId)}&select=name&limit=1`);
    const groupName = groupRows?.[0]?.name || "Group";
    const inviterName = invitedBy ? await profileName(env, invitedBy) : "";
    return deliverToUser(env, userId, {
      whisper_group_invite: "true",
      groupId,
      groupName: String(groupName).slice(0, 64),
      inviterName: inviterName.slice(0, 64),
    });
  }
  if (table === "whisper_group_messages") {
    const groupId = str(record?.group_id);
    const senderId = str(record?.sender_id);
    const clientId = str(record?.client_id);
    if (!groupId || !senderId || !clientId) return { sent: 0, pruned: 0, skipped: "not_a_pushable_event" };
    // Votes are tally data, not chat lines: realtime delivers them to open
    // chats silently, but they never fan out a push (quota + noise).
    if (str(record?.msg_kind) === "vote") return { sent: 0, pruned: 0, skipped: "vote_no_push" };
    const [members, groupRows] = await Promise.all([
      restRows(env, `whisper_group_members?group_id=eq.${encodeURIComponent(groupId)}&select=user_id`),
      restRows(env, `whisper_groups?id=eq.${encodeURIComponent(groupId)}&select=name&limit=1`),
    ]);
    const groupName = String(groupRows?.[0]?.name || "Group").slice(0, 64);
    // Sender name rides the push (like 1:1 senderName): the push service can
    // correlate who wrote in which named group, never message bodies.
    const senderName = (await profileName(env, senderId)).slice(0, 64);
    const dataPayload = {
      whisper_group_message: "true",
      groupId,
      groupName,
      senderName,
      senderId,
      clientId: clientId.slice(0, 64),
    };
    let sent = 0;
    let pruned = 0;
    for (const m of members) {
      const uid = str(m?.user_id);
      if (!uid || uid === senderId) continue;
      const r = await deliverToUser(env, uid, dataPayload);
      sent += r.sent;
      pruned += r.pruned;
    }
    return { sent, pruned };
  }
  if (table === "whisper_group_events") {
    const kind = str(record?.type);
    const PUSHABLE_EVENT_KINDS = new Set([
      "join", "leave", "decline",
      "remove", "promote", "demote", "rename", "picture", "settings",
      "policy",
    ]);
    if (!PUSHABLE_EVENT_KINDS.has(kind)) {
      return { sent: 0, pruned: 0, skipped: "event_not_pushable" };
    }
    const groupId = str(record?.group_id);
    const actor = str(record?.actor);
    const eventId = str(record?.id);
    if (!groupId || !actor) return { sent: 0, pruned: 0, skipped: "not_a_pushable_event" };
    const [members, groupRows] = await Promise.all([
      restRows(env, `whisper_group_members?group_id=eq.${encodeURIComponent(groupId)}&select=user_id,role`),
      restRows(env, `whisper_groups?id=eq.${encodeURIComponent(groupId)}&select=name,created_by&limit=1`),
    ]);
    const groupName = String(groupRows?.[0]?.name || "Group").slice(0, 64);
    const owner = String(groupRows?.[0]?.created_by || "");
    const actorName = (await profileName(env, actor)).slice(0, 64);
    // Declines ping the owner only; joins/leaves ping owner + admins;
    // admin-action events (remove/promote/demote/rename/picture/settings)
    // ping all current members except the actor (mute/open-chat still apply
    // client-side). Members who opted in get join/leave over realtime while open.
    const isAdminAction = kind !== "join" && kind !== "leave" && kind !== "decline";
    const targets = members
      .map((m) => ({ id: str(m?.user_id), role: str(m?.role) }))
      .filter((m) => m.id && m.id !== actor)
      .filter((m) => isAdminAction || m.id === owner || m.role === "admin")
      .filter((m) => kind !== "decline" || m.id === owner);
    // A removed member is already gone from the members table by the time
    // this runs (event publishes before the row delete, but ordering isn't
    // guaranteed): reach them directly via the event subject.
    if (kind === "remove") {
      const subject = eventSubjectId(record?.payload);
      if (subject && subject !== actor && !targets.some((t) => t.id === subject)) {
        targets.push({ id: subject, role: "member" });
      }
    }
    // Subject info for role-change events (remove/promote/demote carry a
    // quoted user id payload): clients render "you" vs "X" copy from this.
    let subjectId = "";
    let subjectName = "";
    if (kind === "remove" || kind === "promote" || kind === "demote") {
      subjectId = eventSubjectId(record?.payload);
      if (subjectId) subjectName = (await profileName(env, subjectId)).slice(0, 64);
    }
    const dataPayload = {
      whisper_group_event: "true",
      groupId,
      kind,
      actorId: actor,
      actorName,
      groupName,
      subjectId,
      subjectName,
      eventId: eventId.slice(0, 64),
    };
    let sent = 0;
    let pruned = 0;
    for (const t of targets) {
      const r = await deliverToUser(env, t.id, dataPayload);
      sent += r.sent;
      pruned += r.pruned;
    }
    return { sent, pruned };
  }
  return { sent: 0, pruned: 0, skipped: "not_a_pushable_event" };
}

/**
 * Daily Ups fan-out. The recipient row names the friend and the story; the
 * story row names the poster (and its state — a story deleted or expired
 * between the fan-out insert and this push is skipped, refusing to wake a
 * device for content that can no longer be read). Data-only payload, same
 * privacy contract as groups; the push service may correlate that a named
 * friend posted a Daily Up, never the image or its sealed key material.
 */
async function handleStoryPush(
  record: any,
  env: GroupPushEnv,
): Promise<DeliveryResult> {
  const str = (v: unknown) => (typeof v === "string" ? v : "");
  const storyId = str(record?.story_id);
  const recipientId = str(record?.recipient_id);
  if (!storyId || !recipientId) return { sent: 0, pruned: 0, skipped: "not_a_pushable_event" };

  let storyRows: any[];
  try {
    storyRows = await restRows(
      env,
      `whisper_stories?id=eq.${encodeURIComponent(storyId)}&select=poster_id,state&limit=1`,
    );
  } catch (e) {
    console.error("story lookup threw", e instanceof Error ? e.message : e);
    return { sent: 0, pruned: 0, skipped: "story_lookup_failed" };
  }
  const posterId = str(storyRows?.[0]?.poster_id);
  const storyState = str(storyRows?.[0]?.state);
  // Only a live, published story justifies waking the receiving device.
  if (!posterId || storyState !== "published") {
    return { sent: 0, pruned: 0, skipped: "story_not_live" };
  }
  const posterName = (await profileName(env, posterId)).slice(0, 64);
  return deliverToUser(env, recipientId, {
    whisper_story_new: "true",
    posterId,
    posterName,
    storyId: storyId.slice(0, 64),
  });
}

/**
 * Single-recipient delivery (shared by 1:1 and group fan-out): recent-seen
 * skip → target lookup → exactly one send attempt per target. Mirrors the
 * old FCM deliverTo semantics exactly.
 */
async function deliverToUser(
  env: GroupPushEnv,
  receiverId: string,
  dataPayload: Record<string, string>,
): Promise<DeliveryResult> {
  const seenSkip = await recentlyActive(env, receiverId);
  if (seenSkip) return { sent: 0, pruned: 0, skipped: seenSkip };
  let targets: PushTarget[];
  try {
    targets = await targetsForUser(env, receiverId);
  } catch (e) {
    console.error("target lookup threw", e instanceof Error ? e.message : e);
    return { sent: 0, pruned: 0, skipped: "target_lookup_failed" };
  }
  if (targets.length === 0) return { sent: 0, pruned: 0, skipped: "no_push_targets" };
  return { ...await deliver(dataPayload, targets, env) };
}

/**
 * One delivery round over a user's targets; each target gets exactly one
 * attempt, stale endpoints are pruned (404/410 only).
 */
async function deliver(
  dataPayload: Record<string, string>,
  targets: PushTarget[],
  env: GroupPushEnv,
): Promise<DeliveryResult> {
  const payloadBytes = new TextEncoder().encode(JSON.stringify(dataPayload));
  let sent = 0;
  let pruned = 0;
  for (const target of targets) {
    try {
      const res = await sendToTarget(target, payloadBytes, env);
      if (res.ok) {
        sent++;
        continue;
      }
      // Log status + tiny body slice only; never log endpoints or payloads.
      console.error("push send failed", res.status, (await res.text().catch(() => "")).slice(0, 120));
      if (isInvalidEndpointResponse(res)) pruned += await pruneTarget(target, env);
    } catch (sendError) {
      console.error("push send threw", sendError);
    }
  }
  return { sent, pruned };
}

/** Routes one target to its delivery flavor (encrypted webpush or plain POST). */
async function sendToTarget(
  target: PushTarget,
  payloadBytes: Uint8Array,
  env: GroupPushEnv,
): Promise<Response> {
  if (target.provider === "webpush") {
    if (!env.vapidJwk) {
      // Configuration issue, not a dead endpoint: must NOT be reported as
      // 404/410, which would wrongly prune the target.
      return new Response("vapid not configured", { status: 412 });
    }
    if (!target.keysP256dh || !target.keysAuth) {
      return new Response("missing webpush key set", { status: 412 });
    }
    const [authorization, encrypted] = await Promise.all([
      vapidAuthorizationHeader(target.endpoint, env.vapidJwk, env.vapidSubject),
      encryptWebPush(payloadBytes, { p256dh: target.keysP256dh, auth: target.keysAuth }),
    ]);
    return fetch(target.endpoint, {
      method: "POST",
      headers: {
        Authorization: authorization,            // RFC 8292 VAPID
        "Content-Encoding": "aes128gcm",         // RFC 8290 record encoding
        "Content-Type": "application/octet-stream",
        TTL: PUSH_TTL_SECONDS,                   // RFC 8030
        Urgency: "high",
      },
      body: encrypted,
      signal: AbortSignal.timeout(10_000),
    });
  }
  // plain (ntfy / NextPush UnifiedPush): body IS the wake-ping JSON.
  return fetch(target.endpoint, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      TTL: PUSH_TTL_SECONDS,
      Urgency: "high",
    },
    body: payloadBytes,
    signal: AbortSignal.timeout(10_000),
  });
}

/** Deletes the stale target row behind one dead endpoint. */
async function pruneTarget(target: PushTarget, env: GroupPushEnv): Promise<number> {
  try {
    const delRes = await fetch(
      `${env.supabaseUrl}/rest/v1/whisper_push_targets?endpoint=eq.${encodeURIComponent(target.endpoint)}`,
      { method: "DELETE", headers: env.serviceHeaders },
    );
    if (delRes.ok || delRes.status === 404) return 1;
    console.error("stale target prune failed", delRes.status);
    return 0;
  } catch (e) {
    console.error("stale target prune threw", e instanceof Error ? e.message : e);
    return 0;
  }
}

/**
 * True when the push service rejected the ENDPOINT (not the request): HTTP
 * 404/410 covers FCM webpush unregistered/gone and ntfy-style dead topics.
 */
function isInvalidEndpointResponse(res: Response): boolean {
  return res.status === 404 || res.status === 410;
}

/** Constant-time string compare for secret/header checks. */
function timingSafeEqual(a: string, b: string): boolean {
  const ab = new TextEncoder().encode(a);
  const bb = new TextEncoder().encode(b);
  if (ab.length !== bb.length) return false;
  let diff = 0;
  for (let i = 0; i < ab.length; i++) diff |= ab[i] ^ bb[i];
  return diff === 0;
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
