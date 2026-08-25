# Database

Two databases, two different jobs. Confusing them is the most likely way
this system rots, so the boundary is stated once here and referenced
everywhere else.

## The local/cloud data boundary

**Android (Room) is the detail store.** Every raw GPS point, every Health
Connect read, full sync history, export history — all of it lives in
`personalstrava.db` on the phone and nowhere else. This is deliberate: a
multi-year cycling habit at a few points per second produces millions of
rows, and none of that detail is needed anywhere except (a) recomputing the
route for the activity it belongs to, and (b) generating an export. Keeping
it off Supabase keeps the cloud project cheap indefinitely and keeps the
"your data isn't hostage to a cloud bill" property real, not just marketing
copy (spec section 4).

**Supabase is the summary/aggregate store.** One row per activity
(distance, duration, speeds, a *simplified* polyline — not the raw track),
one row per day, one row per month, one row per personal record. This is
enough to power every web dashboard, chart, and map view without ever
touching a GPS point table, and it's small enough that Supabase's free/
lowest paid tier holds years of data comfortably.

**The web browser is a cache, not a source of truth.** `web/src/lib/
localCache.ts` mirrors recently-fetched Supabase rows into IndexedDB via
Dexie purely for snappy repeat loads; clearing it never loses data, because
Supabase still has everything.

## Table-by-table

| Table | Where | Why it's shaped that way |
|---|---|---|
| `activities` | Room *and* Supabase (summary only) | Client-generated UUID primary key on both sides — this single decision is what makes sync idempotent (docs/sync.md). |
| `gps_points` | Room only | Indexed on `(activity_id, timestamp)`; never queried across activities, always loaded for exactly one activity's processing/export. |
| `daily_stats` | Room *and* Supabase | One row per calendar date. Cheap to sum for "this week"/"this month" without touching `activities`. |
| `monthly_stats` | Room *and* Supabase | Rolled up from `daily_stats`, never re-derived from raw activities — see `StatsAggregator.aggregateMonthly`. |
| `personal_records` | Room *and* Supabase | Key/value shape (`record_key` + `value_numeric` + `value_unit`) so a new record type (spec section 23: "design this flexibly") is a new key, not a migration. |
| `sync_queue` | Room only | The outbox — see docs/sync.md. |
| `export_history` | Room only | Local record of what's been exported and where the file landed on-device. |
| `export_metadata` | Supabase only | The cloud-side export → verify → delete-detail lifecycle (docs/exports.md). |
| `profiles` | Supabase only | Report preferences + display name; auto-created by a trigger on `auth.users` insert. |

## Units

Everything is stored in SI base units end to end: meters, meters/second,
seconds, UTC timestamps. Conversion to km, km/h, and formatted durations
happens only at the UI boundary (`web/src/lib/units.ts` on web; the
equivalent will live in a `ui/format` package on Android). This avoids the
classic bug class of a km value accidentally being treated as meters three
layers up the call stack.

## Regenerating the web TypeScript types

`web/src/types/database.ts` is currently hand-written to mirror the SQL
migrations exactly (see the comment at the top of that file). Once a real
Supabase project exists:

```bash
supabase gen types typescript --project-id <your-project-ref> > web/src/types/database.ts
```

and wire `createClient<Database>(...)` back into `web/src/lib/
supabaseClient.ts` for full end-to-end type inference on every query.
