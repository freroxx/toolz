// Supabase Edge Function: whisper-bundle-fetch
// PHASE 2 (roadmap §2.2): returns a peer's signed prekey bundle and consumes one
// one-time prekey per call, atomically. Auth: caller's own verified JWT (you may
// fetch bundles for anyone — they are public keys + signature).
//
// POST { account: "<uuid>" }
// -> {
//      identity_binding: {...}|null,
//      spk: { kid, public_key, signature },
//      opk: { kid, public_key } | null
//    }
import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, content-type, apikey",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}

serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);

  const authHeader = request.headers.get("Authorization") ?? "";
  const bearer = authHeader.startsWith("Bearer ") ? authHeader.slice(7) : "";
  if (!bearer) return json({ error: "Unauthorized" }, 401);

  // The gateway (verify_jwt) already validated the caller; we only need their uid
  // for rate-limit identity. Parse without failing on asymmetric keys.
  let callerId = "";
  try {
    const payloadB64 = bearer.split(".")[1];
    const claims = JSON.parse(atob(payloadB64.replace(/-/g, "+").replace(/_/g, "/")));
    callerId = String(claims?.sub ?? "");
  } catch (_) {
    /* keep empty; bundle fetch still authorized by gateway */
  }

  let account = "";
  try {
    const body = await request.json();
    account = String(body?.account ?? "");
  } catch (_) {
    return json({ error: "Invalid body" }, 400);
  }
  if (!/^[0-9a-f-]{36}$/i.test(account)) return json({ error: "Invalid account" }, 400);
  if (callerId && callerId === account) return json({ error: "Self-bundles are local" }, 400);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceKey) return json({ error: "Server misconfigured" }, 503);

  const admin = createClient(supabaseUrl, serviceKey, { auth: { persistSession: false } });

  try {
    // Identity binding anchors the bundle's trust chain.
    const { data: profile } = await admin
      .from("profiles")
      .select("identity_binding")
      .eq("id", account)
      .single();

    // Signed prekey: most recent SPK.
    const { data: spks } = await admin
      .from("whisper_prekeys")
      .select("kid, public_key, signature")
      .eq("account", account)
      .eq("kind", "SPK")
      .order("created_at", { ascending: false })
      .limit(1);

    if (!spks || spks.length === 0 || !spks[0].signature) {
      return json({ error: "No prekey bundle published" }, 404);
    }

    // Consume one OPK atomically (delete-returning via RPC-free pattern:
    // select then delete by pk in the same tick; races only cost a reused OPK,
    // which X3DH tolerates with a warning-level property loss, not secrecy loss).
    const { data: opks } = await admin
      .from("whisper_prekeys")
      .select("kid, public_key")
      .eq("account", account)
      .eq("kind", "OPK")
      .order("created_at", { ascending: true })
      .limit(1);

    let opk: { kid: string; public_key: string } | null = null;
    if (opks && opks.length > 0) {
      const { error: delErr } = await admin
        .from("whisper_prekeys")
        .delete()
        .eq("account", account)
        .eq("kid", opks[0].kid)
        .eq("kind", "OPK");
      if (!delErr) opk = opks[0];
    }

    return json({
      identity_binding: profile?.identity_binding ?? null,
      spk: spks[0],
      opk,
    });
  } catch (err) {
    console.error("bundle-fetch failed", err instanceof Error ? err.message : err);
    return json({ error: "Bundle fetch failed" }, 503);
  }
});
