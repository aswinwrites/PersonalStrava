-- 0005: add "jogging" as a fourth activity type, and — the bigger fix —
-- make daily_stats/monthly_stats actually get populated.
--
-- Root cause found while wiring up Android sync: activities were always
-- meant to roll up into daily_stats/monthly_stats (that's what
-- get_period_totals/get_lifetime_totals in 0004 read from), but no trigger
-- or job ever did that roll-up. Every dashboard number has been reading
-- from tables nothing writes to. This migration fixes that with an
-- AFTER INSERT/UPDATE/DELETE trigger on activities that recomputes the
-- affected day/month row(s) from scratch (idempotent — safe to fire
-- repeatedly for the same activity, which matters since sync retries can
-- re-upsert the same row).
--
-- Timezone note: date-bucketing below is fixed to Asia/Kolkata. This is a
-- single-user app with no per-user timezone column, so there's no other
-- signal Postgres could use to bucket "today" the way the device does.
-- Flagging this explicitly rather than silently assuming UTC (which would
-- shift late-night activities to the wrong day for an India-based user).

-- ---------------------------------------------------------------------------
-- 1. Add 'jogging' to the activity_type enum
-- ---------------------------------------------------------------------------
-- ADD VALUE IF NOT EXISTS (PG12+) is itself idempotent, and is safe to run
-- as a plain top-level statement (kept outside a DO block: ALTER TYPE ...
-- ADD VALUE cannot run inside a function/DO block in the same transaction
-- that might also reference the new value, and it's simplest to just not
-- risk it).
alter type public.activity_type add value if not exists 'jogging';

-- ---------------------------------------------------------------------------
-- 2. Add jogging columns to daily_stats / monthly_stats
-- ---------------------------------------------------------------------------
alter table public.daily_stats
  add column if not exists jogging_distance_meters double precision not null default 0,
  add column if not exists jogging_seconds integer not null default 0;

alter table public.monthly_stats
  add column if not exists jogging_distance_meters double precision not null default 0,
  add column if not exists jogging_seconds integer not null default 0;

-- ---------------------------------------------------------------------------
-- 3. Roll-up function: recompute one user's daily_stats + monthly_stats row
--    for a given date, from public.activities, from scratch.
-- ---------------------------------------------------------------------------
create or replace function public.recompute_stats_for_day(p_user_id uuid, p_local_date date)
returns void
language plpgsql
security definer set search_path = public
as $$
declare
  v_day_start timestamptz := (p_local_date::text || ' 00:00:00')::timestamp at time zone 'Asia/Kolkata';
  v_day_end timestamptz := v_day_start + interval '1 day';
  v_month date := date_trunc('month', p_local_date)::date;
begin
  -- daily_stats: full upsert, preserving `steps` (populated separately by
  -- Health Connect sync, not derived from activities).
  insert into public.daily_stats (
    user_id, date,
    walking_distance_meters, cycling_distance_meters, motorcycling_distance_meters, jogging_distance_meters,
    walking_seconds, cycling_seconds, motorcycling_seconds, jogging_seconds,
    elevation_gain_meters, activity_count
  )
  select
    p_user_id, p_local_date,
    coalesce(sum(distance_meters) filter (where activity_type = 'walking'), 0),
    coalesce(sum(distance_meters) filter (where activity_type = 'cycling'), 0),
    coalesce(sum(distance_meters) filter (where activity_type = 'motorcycling'), 0),
    coalesce(sum(distance_meters) filter (where activity_type = 'jogging'), 0),
    coalesce(sum(moving_seconds) filter (where activity_type = 'walking'), 0),
    coalesce(sum(moving_seconds) filter (where activity_type = 'cycling'), 0),
    coalesce(sum(moving_seconds) filter (where activity_type = 'motorcycling'), 0),
    coalesce(sum(moving_seconds) filter (where activity_type = 'jogging'), 0),
    coalesce(sum(elevation_gain_meters), 0),
    count(*)
  from public.activities
  where user_id = p_user_id
    and start_time >= v_day_start and start_time < v_day_end
    and sync_status != 'archived'
  on conflict (user_id, date) do update set
    walking_distance_meters = excluded.walking_distance_meters,
    cycling_distance_meters = excluded.cycling_distance_meters,
    motorcycling_distance_meters = excluded.motorcycling_distance_meters,
    jogging_distance_meters = excluded.jogging_distance_meters,
    walking_seconds = excluded.walking_seconds,
    cycling_seconds = excluded.cycling_seconds,
    motorcycling_seconds = excluded.motorcycling_seconds,
    jogging_seconds = excluded.jogging_seconds,
    elevation_gain_meters = excluded.elevation_gain_meters,
    activity_count = excluded.activity_count;

  -- monthly_stats: recompute the whole month from daily_stats (cheap — at
  -- most 31 rows), so it never drifts from the daily rows above.
  insert into public.monthly_stats (
    user_id, month,
    walking_distance_meters, cycling_distance_meters, motorcycling_distance_meters, jogging_distance_meters,
    walking_seconds, cycling_seconds, motorcycling_seconds, jogging_seconds,
    elevation_gain_meters, activity_count, steps
  )
  select
    p_user_id, v_month,
    coalesce(sum(walking_distance_meters), 0),
    coalesce(sum(cycling_distance_meters), 0),
    coalesce(sum(motorcycling_distance_meters), 0),
    coalesce(sum(jogging_distance_meters), 0),
    coalesce(sum(walking_seconds), 0),
    coalesce(sum(cycling_seconds), 0),
    coalesce(sum(motorcycling_seconds), 0),
    coalesce(sum(jogging_seconds), 0),
    coalesce(sum(elevation_gain_meters), 0),
    coalesce(sum(activity_count), 0),
    coalesce(sum(steps), 0)
  from public.daily_stats
  where user_id = p_user_id
    and date >= v_month and date < (v_month + interval '1 month')
  on conflict (user_id, month) do update set
    walking_distance_meters = excluded.walking_distance_meters,
    cycling_distance_meters = excluded.cycling_distance_meters,
    motorcycling_distance_meters = excluded.motorcycling_distance_meters,
    jogging_distance_meters = excluded.jogging_distance_meters,
    walking_seconds = excluded.walking_seconds,
    cycling_seconds = excluded.cycling_seconds,
    motorcycling_seconds = excluded.motorcycling_seconds,
    jogging_seconds = excluded.jogging_seconds,
    elevation_gain_meters = excluded.elevation_gain_meters,
    activity_count = excluded.activity_count,
    steps = excluded.steps;
end;
$$;

-- ---------------------------------------------------------------------------
-- 4. Trigger on activities: recompute the affected day(s) after any change.
--    Handles insert, update (including a start_time edit moving an activity
--    to a different day — recomputes both the old and new day), and delete.
-- ---------------------------------------------------------------------------
create or replace function public.trg_activities_recompute_stats()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
  if tg_op = 'DELETE' then
    perform public.recompute_stats_for_day(old.user_id, (old.start_time at time zone 'Asia/Kolkata')::date);
    return old;
  end if;

  perform public.recompute_stats_for_day(new.user_id, (new.start_time at time zone 'Asia/Kolkata')::date);

  if tg_op = 'UPDATE' and old.start_time is distinct from new.start_time then
    perform public.recompute_stats_for_day(old.user_id, (old.start_time at time zone 'Asia/Kolkata')::date);
  end if;

  return new;
end;
$$;

drop trigger if exists activities_recompute_stats on public.activities;
create trigger activities_recompute_stats
  after insert or update or delete on public.activities
  for each row execute function public.trg_activities_recompute_stats();

-- ---------------------------------------------------------------------------
-- 5. One-off backfill: recompute stats for every day that already has
--    activities but no matching stats row (covers rides synced before this
--    trigger existed).
-- ---------------------------------------------------------------------------
do $$
declare
  r record;
begin
  for r in
    select distinct user_id, (start_time at time zone 'Asia/Kolkata')::date as local_date
    from public.activities
    where sync_status != 'archived'
  loop
    perform public.recompute_stats_for_day(r.user_id, r.local_date);
  end loop;
end$$;

-- ---------------------------------------------------------------------------
-- 6. get_period_totals / get_lifetime_totals: add jogging columns.
--    Return shape changed -> drop + recreate rather than create or replace.
-- ---------------------------------------------------------------------------
drop function if exists public.get_period_totals(date, date);

create function public.get_period_totals(period_start date, period_end date)
returns table (
  steps bigint,
  walking_distance_meters double precision,
  cycling_distance_meters double precision,
  motorcycling_distance_meters double precision,
  jogging_distance_meters double precision,
  walking_seconds bigint,
  cycling_seconds bigint,
  motorcycling_seconds bigint,
  jogging_seconds bigint,
  elevation_gain_meters double precision,
  activity_count bigint
)
language sql
stable
as $$
  select
    coalesce(sum(steps), 0)::bigint,
    coalesce(sum(walking_distance_meters), 0),
    coalesce(sum(cycling_distance_meters), 0),
    coalesce(sum(motorcycling_distance_meters), 0),
    coalesce(sum(jogging_distance_meters), 0),
    coalesce(sum(walking_seconds), 0)::bigint,
    coalesce(sum(cycling_seconds), 0)::bigint,
    coalesce(sum(motorcycling_seconds), 0)::bigint,
    coalesce(sum(jogging_seconds), 0)::bigint,
    coalesce(sum(elevation_gain_meters), 0),
    coalesce(sum(activity_count), 0)::bigint
  from public.daily_stats
  where user_id = auth.uid()
    and date between period_start and period_end;
$$;

comment on function public.get_period_totals is 'Sums daily_stats for auth.uid() over an inclusive date range. Powers Home (today/week/month) and Analytics custom ranges.';

drop function if exists public.get_lifetime_totals();

create function public.get_lifetime_totals()
returns table (
  walking_distance_meters double precision,
  cycling_distance_meters double precision,
  motorcycling_distance_meters double precision,
  jogging_distance_meters double precision,
  total_distance_meters double precision,
  steps bigint,
  activity_count bigint
)
language sql
stable
as $$
  select
    coalesce(sum(walking_distance_meters), 0),
    coalesce(sum(cycling_distance_meters), 0),
    coalesce(sum(motorcycling_distance_meters), 0),
    coalesce(sum(jogging_distance_meters), 0),
    coalesce(sum(walking_distance_meters + cycling_distance_meters + motorcycling_distance_meters + jogging_distance_meters), 0),
    coalesce(sum(steps), 0)::bigint,
    coalesce(sum(activity_count), 0)::bigint
  from public.daily_stats
  where user_id = auth.uid();
$$;

comment on function public.get_lifetime_totals is 'All-time totals for auth.uid(). Cheap because it reads daily_stats (one row/day), never raw activities or GPS points.';

grant execute on function public.get_period_totals(date, date) to authenticated;
grant execute on function public.get_lifetime_totals() to authenticated;
