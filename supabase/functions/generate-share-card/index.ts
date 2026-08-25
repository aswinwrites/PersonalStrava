// supabase/functions/generate-share-card/index.ts
//
// Phase 2+ stub. The web client currently generates share cards entirely
// client-side (Canvas API, see web/src/features/share/shareCard.ts) since
// that needs no server round-trip and no secret. This Edge Function is
// reserved for a future server-rendered share card (e.g. for consistent
// font rendering or heavier map compositing) and intentionally does nothing
// yet — wired up here so the deployment/routing story is documented and the
// function name is reserved.

Deno.serve(async () => {
  return new Response(
    JSON.stringify({ error: "not implemented — share cards are generated client-side in web/ for now" }),
    { status: 501, headers: { "Content-Type": "application/json" } },
  );
});
