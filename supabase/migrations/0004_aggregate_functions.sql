-- Read-only aggregate RPCs backing the web dashboard (Home + Analytics).
-- security invoker (the default) means these run as the calling user, so
-- RLS on daily_stats/activities still applies — no service-role bypass.

create or replace function public.get_period_totals(period_start date, period_end date)
returns table (
  steps bigint,
  walking_distance_meters double precision,
  cycling_distance_meters double precision,
  motorcycling_distance_meters double precision,
  walking_seconds bigint,
  cycling_seconds bigint,
  motorcycling_seconds bigint,
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
    coalesce(sum(walking_seconds), 0)::bigint,
    coalesce(sum(cycling_seconds), 0)::bigint,
    coalesce(sum(motorcycling_seconds), 0)::bigint,
    coalesce(sum(elevation_gain_meters), 0),
    coalesce(sum(activity_count), 0)::bigint
  from public.daily_stats
  where user_id = auth.uid()
    and date between period_start and period_end;
$$;

comment on function public.get_period_totals is 'Sums daily_stats for auth.uid() over an inclusive date range. Powers Home (today/week/month) and Analytics custom ranges.';

create or replace function public.get_lifetime_totals()
returns table (
  walking_distance_meters double precision,
  cycling_distance_meters double precision,
  motorcycling_distance_meters double precision,
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
    coalesce(sum(walking_distance_meters + cycling_distance_meters + motorcycling_distance_meters), 0),
    coalesce(sum(steps), 0)::bigint,
    coalesce(sum(activity_count), 0)::bigint
  from public.daily_stats
  where user_id = auth.uid();
$$;

comment on function public.get_lifetime_totals is 'All-time totals for auth.uid(). Cheap because it reads daily_stats (one row/day), never raw activities or GPS points.';

grant execute on function public.get_period_totals(date, date) to authenticated;
grant execute on function public.get_lifetime_totals() to authenticated;
