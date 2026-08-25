# Web

## Stack

Vite + React 19 + TypeScript, React Router 7, Tailwind CSS v4 (via the
`@tailwindcss/vite` plugin — no separate `postcss.config`/`tailwind.config`
needed, tokens live in `src/index.css` under `@theme`), MapLibre GL,
`vite-plugin-pwa`, Dexie for the IndexedDB cache, `date-fns` for date-range
math, `recharts` (installed, not yet used — reserved for Phase 2's trend
charts).

## Verified

```bash
cd web
npm install
npm run build   # tsc -b && vite build — passes clean
npm test        # vitest — 10/10 passing (units.ts + polyline.ts)
```

Bundle: the main chunk is ~137KB gzipped; MapLibre GL (by far the largest
dependency) is code-split into its own lazily-loaded chunk behind the
`/map` route (see `src/App.tsx`'s `lazy(() => import('./pages/MapPage'))`),
so signing in and checking today's stats doesn't pay for the map engine.

## Structure

- `src/features/auth/` — `AuthProvider` (Supabase session context),
  `SignInScreen`, `RequireAuth` (route guard), `AuthCallback` (OAuth
  redirect landing page).
- `src/pages/` — one file per top-level route. `HomePage` and
  `AnalyticsPage` call the `get_period_totals`/`get_lifetime_totals`
  Postgres RPCs (docs/database.md) rather than summing rows client-side.
  `ActivitiesPage` is a paginated, filterable list straight off `activities`.
  `MapPage` decodes each activity's `route_polyline` and renders it with
  MapLibre. `ExportPage` does client-side CSV generation today (ships in
  Phase 1 because it only needs already-synced summary rows); GPX/JSON/ZIP
  are Phase 2 (docs/exports.md — they need an Edge Function talking to the
  Android-generated archive). `SettingsPage` toggles the two report
  preference flags on `profiles`.
- `src/lib/` — `supabaseClient.ts`, `units.ts` (SI-to-display formatting),
  `polyline.ts` (Google/OSRM polyline decoder, hand-rolled, no dependency),
  `useSupabaseQuery.ts` (the fetch-on-mount hook every page uses),
  `localCache.ts` (Dexie schema for the IndexedDB cache), `csvExport.ts`.

## Why no react-query (yet)

The Phase 1 data surface is six pages, each with one or two straightforward
Supabase queries keyed on simple dependencies (a date range, a filter). A
~30-line hook (`useSupabaseQuery`) covers that without a new dependency.
The natural trigger to bring in react-query (or swr) is when a query needs
to be shared/de-duplicated across more than one component, or when
background revalidation actually matters — reasonable to revisit once
Analytics' trend charts and the Activity Detail page both want the same
`activities` slice.

## Typing against Supabase

`web/src/types/database.ts` is hand-written today (mirrors the SQL
migrations) rather than generated, because there's no live Supabase project
to generate against yet. `web/src/lib/supabaseClient.ts` intentionally does
**not** parameterize `createClient` with that type yet — see the comment
there — because doing so before the schema is battle-tested produces a lot
of type friction for not much benefit. Regenerate and wire it back in per
docs/database.md once the project exists.

## Design system

Tokens are defined once in `src/index.css` (`@theme` block): an ink/paper
pair that flips for dark mode via both `prefers-color-scheme` and an
`html.dark`/`html.light` class override (so a future manual theme toggle
works without re-deriving the palette), plus three accent colors — one per
activity type (walking/cycling/motorcycling) — used consistently across
`StatTile`, the Map's route colors, and (Phase 2) chart series colors. This
was a deliberate choice to avoid the generic-SaaS-dashboard look the spec
explicitly warns against (section 37) — one palette, reused everywhere,
rather than a component library's default theme.
