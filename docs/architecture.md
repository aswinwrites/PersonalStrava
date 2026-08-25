# Architecture

This is the answer to the "first task" brief: repo assessment, then the
14-point architecture, then implementation phases. Phase 1 (this commit) is
implemented; everything else is scoped but not yet built.

## 1. Repository assessment

`PersonalStrava` existed but was empty (no commits, no branches). Nothing to
migrate or reconcile — this is a greenfield build inside the existing repo,
per the instructions. All work below lives in this one repository; no second
repo was created.

## 2. Monorepo structure

```
PersonalStrava/
├── android/            Kotlin + Jetpack Compose app (Gradle project)
│   ├── app/src/main/kotlin/com/personalstrava/app/
│   │   ├── data/local/          Room entities + DAOs + AppDatabase
│   │   ├── domain/              Pure logic: GPS processing, aggregation, models
│   │   ├── healthconnect/       Health Connect read integration
│   │   ├── record/              Foreground recording service
│   │   ├── sync/                Supabase client + SyncManager
│   │   └── ui/                  Compose screens + ViewModels
│   └── app/src/test/            JUnit tests for the domain layer
├── web/                 React + TypeScript + Vite PWA
│   └── src/
│       ├── features/auth/       Supabase Google OAuth
│       ├── pages/                Home, Activities, Analytics, Map, Export, Settings
│       └── lib/                  Supabase client, unit formatting, polyline decode, IndexedDB cache
├── supabase/
│   ├── migrations/               Schema, RLS, storage policies, aggregate RPCs
│   └── functions/                send-report (Resend), generate-share-card (stub)
├── docs/                 This directory
├── README.md
└── .env.example
```

This matches the brief's recommended structure without changes — it was
already the right shape for a two-client-one-backend app.

## 3. Android architecture

Single-activity, Compose-only UI (no XML layouts). Layered:

- **ui/** — Compose screens + `ViewModel`s (Home is implemented; Activities/
  Recording/Settings screens are the Phase 2 follow-on, same pattern).
- **domain/** — Pure Kotlin, zero Android framework dependencies:
  `GpsProcessor` (cleaning + distance/speed/elevation formulas),
  `StatsAggregator` (daily/monthly rollups), `ActivityType`/`SyncStatus`
  enums, `IdGenerator`. This is the layer with unit tests, deliberately kept
  framework-free so it stays fast to test and easy to reason about.
- **data/local/** — Room: entities, DAOs, `AppDatabase`. The authoritative
  detailed store (spec section 4).
- **healthconnect/** — `HealthConnectManager`, read-only, permission-gated.
- **record/** — `ActivityRecordingService`, the foreground service that owns
  GPS collection during a ride.
- **sync/** — `SupabaseClientProvider` + `SyncManager`, the outbox drain that
  pushes local activity summaries to Supabase.

State management is `StateFlow` from `ViewModel`s, collected with
`collectAsState()` — no third-party state library needed at this scale.

## 4. Web architecture

Vite + React 19 + TypeScript, React Router for the six top-level routes
(Home/Activities/Analytics/Map/Export/Settings), Tailwind v4 (via
`@tailwindcss/vite`) for styling, MapLibre GL for maps (code-split into its
own chunk — see `web/src/App.tsx` — since it's the single largest
dependency and most visits won't touch the Map tab), Dexie for the
IndexedDB cache described in spec section 4/9, `vite-plugin-pwa` for the
installable-PWA shell.

Data fetching is a small hand-rolled `useSupabaseQuery` hook (fetch-on-mount
+ dependency array) rather than react-query — the data surface is small
enough in Phase 1 that a caching library would be premature; documented as
a swap-in point if the app grows more interactive query patterns.

## 5. Supabase architecture

Four building blocks, each with a single job:

- **Auth**: Google OAuth only, `auth.users` is the identity source of truth
  for both clients.
- **Postgres**: `profiles`, `activities`, `daily_stats`, `monthly_stats`,
  `personal_records`, `export_metadata` — summary/aggregate tables only
  (see docs/database.md for why).
- **RLS**: every user-owned table, every operation (select/insert/update/
  delete), scoped to `auth.uid() = user_id`. No exceptions, no service-role
  bypass reachable from a client.
- **Edge Functions**: `send-report` (deterministic weekly/monthly email via
  Resend, spec section 33), `generate-share-card` (reserved, currently a
  501 stub — share cards are generated client-side in Phase 1, see
  docs/exports.md).

## 6. Local database schema (Android/Room)

See `android/app/src/main/kotlin/.../data/local/entity/*.kt` for the exact
columns. Seven tables: `activities`, `gps_points`, `daily_stats`,
`monthly_stats`, `personal_records`, `sync_queue`, `export_history` — one
table per concern (spec section 18 explicitly rules out one giant table).
Indexes: `gps_points(activity_id, timestamp)`, `activities(activity_type)`,
`activities(start_time)`, `activities(sync_status)`, unique indexes on
`daily_stats.date` and `monthly_stats.month`.

## 7. Supabase schema

See `supabase/migrations/0001_init_schema.sql`. Mirrors the Room summary
tables (not `gps_points` — that never leaves the device) plus `profiles` and
`export_metadata`. `0002_rls_policies.sql` locks every table down.
`0003_storage_buckets.sql` adds the optional private archive bucket.
`0004_aggregate_functions.sql` adds two `security invoker` SQL functions
(`get_period_totals`, `get_lifetime_totals`) so the web dashboard doesn't
have to pull and sum rows client-side — Postgres does the arithmetic, RLS
still applies because the functions run as the calling user.

## 8. Sync architecture

See docs/sync.md for the full state machine and retry policy. Summary: an
activity is written to Room as `local` the instant recording stops (before
any network attempt), moves to `pending_sync`, and a `SyncManager` pass
(triggered by WorkManager on a network-available constraint, or immediately
after stop-recording if online) upserts it to Supabase keyed on its
client-generated UUID — so a retried sync after an ambiguous failure
(timeout, but the write actually landed) overwrites itself instead of
duplicating.

## 9. Health Connect integration

`HealthConnectManager` requests only `READ_STEPS` today (distance/exercise
read permissions are declared in the manifest but not requested until a
feature actually consumes them — spec section 11's "do not request
unnecessary permissions"). Steps are summed per device-local calendar day
and folded into `daily_stats` by `StatsAggregator.aggregateDaily`. No
fabrication path exists — if Health Connect is unavailable or permission is
denied, `todaySteps` is `null` and the UI shows an empty state, never a
fake number.

## 10. GPS / background tracking approach

`ActivityRecordingService` is a foreground service
(`foregroundServiceType="location"`) started only by an explicit "Start
cycling"/"Start motorcycle" tap. It requests location updates at
`PRIORITY_HIGH_ACCURACY` on a 3-second interval — a deliberate middle
ground between route fidelity and battery drain (spec section 12). Raw
fixes are cleaned by `GpsProcessor` (accuracy filter, jump detection,
duplicate-timestamp removal) before being used for any metric. See
docs/architecture.md §"GPS Processing formulas" below and
`GpsProcessor.kt`'s doc comments for the exact math.

### GPS processing formulas

- **Distance**: Haversine great-circle distance summed over consecutive
  cleaned points. `d = 2R·atan2(√a, √(1−a))`, `a = sin²(Δφ/2) +
  cos(φ1)cos(φ2)sin²(Δλ/2)`.
- **Moving time**: sum of inter-point time deltas where the *derived*
  segment speed (distance/time between consecutive points) is
  ≥ 0.5 m/s — not the raw GPS speed field, which is noisier at low speed.
- **Average speed**: `distance / elapsed_seconds`. **Moving average speed**:
  `distance / moving_seconds`.
- **Max speed**: max of the GPS-reported speed field where present and
  plausible (≤ 55 m/s), falling back to derived segment speed.
- **Elevation gain/loss**: 5-sample moving-average smoothing over altitude
  readings, then sum positive/negative deltas between consecutive smoothed
  samples, discarding deltas under a 1m noise floor.

All of the above is unit-tested in
`android/app/src/test/.../domain/gps/GpsProcessorTest.kt`.

## 11. Google OAuth configuration

One Google OAuth client (Web application type, since Supabase's hosted
`/auth/v1/callback` is the redirect target for both clients), configured
once in Supabase Auth → Providers → Google. The Android app receives the
session via a custom-scheme deep link (`personalstrava://auth-callback`,
declared in `AndroidManifest.xml`); the web app receives it via
`window.location.origin + /auth/callback` (see
`web/src/features/auth/AuthCallback.tsx`). Both resolve to the same
`auth.users` row for the same Google account — see docs/deployment.md for
the exact Google Cloud Console + Supabase dashboard steps.

## 12. Environment variables

See `.env.example` at the repo root (canonical list) — `VITE_SUPABASE_URL`/
`VITE_SUPABASE_ANON_KEY` for web, `SUPABASE_URL`/`SUPABASE_ANON_KEY` for
Android (via `android/local.properties`, gitignored), and
`SUPABASE_SERVICE_ROLE_KEY`/`RESEND_API_KEY` for Edge Functions only —
never shipped in either client bundle.

## 13. APK build approach

No Play Store signing required. `android/app/build.gradle.kts`'s `release`
build type is signed with the debug keystore deliberately (this is a
personal, sideloaded APK) — see docs/android.md for the exact `gradlew`
command and how to switch to a real release keystore later without
touching any other file.

## 14. Vercel deployment approach

`web/vercel.json` configures SPA rewrite (all paths → `index.html`, so
client-side routing works on refresh/deep link) and baseline security
headers. Environment variables are set in the Vercel project dashboard, not
committed. See docs/deployment.md for the full checklist including the
Supabase and Google Cloud redirect URL updates a production domain
requires.

## 15. Implementation phases

**Phase 1 (this commit)** — repo scaffold; Supabase schema + RLS + aggregate
RPCs + `send-report` Edge Function; web app (auth, Home with real
today/week/month/lifetime stats, Activities list, Analytics with range
filters, global Map with route rendering, CSV export, report-preference
Settings) — builds and passes its unit tests; Android app scaffold (Gradle
project, Room schema for all 7 tables, `GpsProcessor` + `StatsAggregator`
domain logic with unit tests, Health Connect steps read, Home screen,
foreground recording service skeleton, Supabase client + sync manager
skeleton) — **not build-verified**, see the note in docs/android.md about
why (no Android SDK / network access to Google's Maven repo in this
sandbox).

**Phase 2** — Android: wire the recording service's live GPS stream into
Room writes + a live-metrics recording screen; route simplification
(Ramer–Douglas–Peucker) before sync; WorkManager periodic sync + Health
Connect ingestion jobs; permission-request flow UI; crash-recovery for an
in-progress activity. Web: trend charts (period-over-period), personal
records screen, activity detail with title/notes editing and GPX export,
share-card generation (Canvas), archive manager (export → verify → delete
cloud detail).

**Phase 3** — GPX/JSON/ZIP export pipeline (needs an Android-side archive
builder since raw GPS points live there); scheduled `send-report` cron;
cloud storage archive lifecycle; heatmap map mode; instrumented Android
tests (recording-while-locked, offline-then-sync) on a real device, since
that's the one thing that genuinely can't be verified any other way.
