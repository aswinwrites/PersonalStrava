# Sync

## State machine

```
   record stops
        │
        ▼
     LOCAL  ──────────────────────────┐
        │  (SyncManager picks it up)  │ (no network / Supabase down)
        ▼                             │
  PENDING_SYNC ◄───────────────────────┘
        │
        ▼
     SYNCING
       │   │
   success  failure
       │   │
       ▼   ▼
   SYNCED  SYNC_FAILED ──(WorkManager backoff retry)──► PENDING_SYNC
```

`ARCHIVED` is a separate terminal state reached only through the export →
verify → delete-cloud-detail flow (docs/exports.md), not through the sync
retry loop above.

## Why sync is idempotent

Every `ActivityEntity`/`activities` row uses a **client-generated UUID**
(`IdGenerator.newActivityId()`) as its primary key, on both Room and
Supabase. `SyncManager.syncPending()` always calls Supabase's `upsert`
(`on conflict (id) do update`), never `insert`. That means:

- If a sync request times out on the network but actually succeeded
  server-side, the retry's upsert just overwrites the row with identical
  data — no duplicate.
- If the app crashes mid-sync, the activity stays `syncing` locally; the
  next `SyncManager` pass sees it, re-upserts, and moves it to `synced` —
  same idempotency guarantee covers the crash-recovery path.
- Because RLS scopes every row to `user_id = auth.uid()`, a retried upsert
  can never accidentally touch another user's row even in the (Phase 1
  single-user) theoretical multi-account case.

This is the concrete mechanism behind spec section 19 ("Do not duplicate
activities when sync retries") and section 46 ("Duplicate sync: Never
duplicate an activity").

## Retry policy

`SyncQueueEntity` tracks `attemptCount` and `lastError` per activity.
`SyncManager` gives up (moves to `SYNC_FAILED`, stops auto-retrying) after
6 attempts; the WorkManager periodic job that invokes `SyncManager.
syncPending()` is configured (Phase 2) with `BackoffPolicy.EXPONENTIAL` and
a network-connected constraint, so retries space themselves out
automatically rather than hammering a flaky connection.

## What actually gets synced

Only `activities` summary rows (plus, once Phase 2's aggregation job is
wired up, `daily_stats`/`monthly_stats`/`personal_records`). `gps_points`
never sync — see docs/database.md for why. The `route_polyline` column on
`activities` is a Ramer–Douglas–Peucker–simplified encoded polyline
computed on-device before upload (Phase 2 — Phase 1's `activities` table
and DTO already have the column, the simplification step itself isn't
wired into the recording pipeline yet).

## Offline behavior

Recording never checks network state — GPS points are written to Room
continuously regardless of connectivity (spec section 46: "Activity
continues" / "record locally, queue sync"). The only network-dependent step
is the `SyncManager` pass at the end, which no-ops gracefully (see the
`userId == null` / connectivity-constraint guards) rather than blocking or
crashing when Supabase is unreachable.
