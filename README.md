# PersonalStrava

A private, single-user activity tracker — walking, cycling, motorcycling.
Not a social network: no feeds, no followers, no public profiles. One
person's movement data, captured on Android, analyzed on the web.

```
CAPTURE → STORE LOCALLY → PROCESS → SYNC → ANALYZE → VISUALIZE → EXPORT → SHARE
```

## Repository layout

```
android/     Kotlin + Jetpack Compose app — GPS recording, Health Connect,
             the detailed on-device database. Sideloaded as an APK, not
             published to Play Store.
web/         React + TypeScript PWA — dashboards, analytics, maps, exports.
             Deploys to Vercel.
supabase/    Postgres schema + RLS + Edge Functions — the shared backend.
docs/        Architecture, database, sync, Android, web, deployment, exports.
```

Full architecture writeup: [`docs/architecture.md`](docs/architecture.md).

## Status

**Phase 1** — see [`docs/architecture.md` §15](docs/architecture.md#15-implementation-phases)
for the full phase breakdown. In short: Supabase schema/RLS/reports and the
web app are built and verified (typecheck, build, and unit tests all pass —
see [`docs/web.md`](docs/web.md)); the Android app is scaffolded with the
same care but **not build-verified**, because this environment has no
Android SDK — see [`docs/android.md`](docs/android.md) for exactly what
that means and the one command to run first on a real machine.

## Quick start

**Web**
```bash
cd web
cp .env.example .env.local   # fill in your Supabase project URL + anon key
npm install
npm run dev
```

**Android**
```bash
cd android
cp local.properties.example local.properties   # fill in sdk.dir + Supabase values
./gradlew assembleDebug    # or open in Android Studio
```
(See [`docs/android.md`](docs/android.md) if `gradlew` doesn't exist yet —
one-line fix.)

**Supabase**
```bash
supabase link --project-ref <your-project-ref>
supabase db push
```
Full setup, including Google OAuth and Vercel: [`docs/deployment.md`](docs/deployment.md).

## Data ownership, in one paragraph

The Android phone holds every raw GPS point and is the only place that
data ever lives in full detail. Supabase holds activity *summaries* and
pre-computed daily/monthly aggregates — small enough to stay cheap
indefinitely, RLS-scoped so no data is ever readable across accounts. The
web app is a cache and an analysis surface, never a source of truth. Full
detail: [`docs/database.md`](docs/database.md).

## What's explicitly not here

No AI integration anywhere — every report, export, and analytics number is
a deterministic aggregate (spec section 36). No social features. No
Google Play Store distribution path.
