import { serve } from "https://deno.land/std@0.224.0/http/server.ts";

serve(async (request) => {
  if (request.method !== "POST") return new Response("Method not allowed", { status: 405 });

  const authHeader = request.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) return new Response("Unauthorized", { status: 401 });

  try {
    const { id } = await request.json();
    if (!id) return new Response("Missing ID", { status: 400 });

    // ImgBB doesn't have a public programmatic delete API that works with just a hash
    // in the same way as upload. Usually deletion is done via the delete_url.
    // This is a placeholder for future remote deletion logic.
    console.log(`Requested deletion of attachment: ${id}`);

    return new Response(JSON.stringify({ success: true }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch (error) {
    return new Response("Invalid request", { status: 400 });
  }
});
