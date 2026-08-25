# Exports

## What ships in Phase 1

Client-side CSV export from the web app (`web/src/pages/ExportPage.tsx`,
`web/src/lib/csvExport.ts`): activity summaries for This week / This month
/ This year / All time, filterable in a follow-up pass by activity type.
This works entirely off already-synced `activities` rows — no server round
trip, no secret needed — which is why it's the one export format that
could ship without the rest of the pipeline existing yet.

## Why GPX/JSON/ZIP are Phase 2/3

GPX needs full-resolution route points, and those live only on the Android
device (docs/database.md) — a GPX exporter has to run where the
`gps_points` table is, which means either (a) an on-device export flow in
the Android app, or (b) the Android app uploading a one-time detailed
archive to Supabase Storage right before a user-initiated export, which
then gets deleted per the verify-then-delete flow below. Phase 2 builds (a)
first since it needs no new cloud infrastructure; (b) is what the "Cloud
Storage Management" flow (spec section 34) formalizes for the case where
the user wants to *archive* a period, not just download a GPX from their
phone.

## The export → verify → delete-cloud-detail flow (spec sections 34-35)

This is intentionally never a two-step `export → delete`. The
`export_metadata` table (`supabase/migrations/0001_init_schema.sql`) exists
specifically to make deletion of detailed cloud data a separate, later,
explicitly-confirmed action gated on a *verified* prior export:

```
generate archive (zip: activities.csv, per-type CSVs, daily/monthly stats
                   CSVs, routes/*.gpx, metadata.json, manifest.json)
        │
        ▼
compute checksum + record counts  →  export_metadata row, status='generated'
        │
        ▼
user (or an automated check) verifies the archive opens and record counts
match  →  status='verified', verified_at set
        │
        ▼
explicit user confirmation in Settings → Cloud Data Management
        │
        ▼
delete detailed cloud rows for that period  →  status='detail_deleted'
(daily_stats / monthly_stats / personal_records for that period are
 NEVER touched by this step — only the detail, e.g. raw activities rows
 once Supabase ever stores anything beyond summaries for a period)
```

Note that today Supabase already only stores *activity summaries*, not raw
GPS — so "delete detailed cloud data" in Phase 1's data model mostly means
"delete/archive old `activities` summary rows while keeping their
already-rolled-up `daily_stats`/`monthly_stats`/`personal_records`
contribution intact," which is exactly why aggregation happens at
sync-time into `daily_stats`/`monthly_stats` rather than being computed
on-demand from `activities` — deleting old `activities` rows must never
change a lifetime total.

## Manifest format

```json
{
  "period": "2026-08",
  "activities": 31,
  "gps_points": 842391,
  "steps": 248421,
  "distance_km": 842.3,
  "schema_version": 1,
  "checksum": "sha256-...",
  "exported_at": "2026-08-25T10:00:00Z"
}
```

`schema_version` exists so a future export-format change can still be read
by whatever import/verify tooling exists at that point — see spec section
31's JSON export requirement ("Include schema version and metadata").

## Email reports

`supabase/functions/send-report/index.ts` is the deterministic weekly/
monthly report (spec section 33) — no AI, no generated prose, every number
a straight aggregate read from `daily_stats` (current period) compared
against the equivalent prior period. It's a real, runnable Edge Function
today (see docs/deployment.md for how to schedule it), independent of the
GPX/ZIP export pipeline above — reports only need `daily_stats`, which
already exists and is already synced.
