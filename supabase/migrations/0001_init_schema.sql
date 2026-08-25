-- PersonalStrava — initial cloud schema
-- Cloud stores SUMMARIES + AGGREGATES only. Raw GPS points live on-device.
-- See docs/database.md for the full rationale and docs/sync.md for the
-- local -> cloud sync lifecycle these tables participate in.

-- ---------------------------------------------------------------------------
-- Extensions
-- ---------------------------------------------------------------------------
create extension if not exists "pgcrypto"; -- gen_random_uuid()

-- ---------------------------------------------------------------------------
-- profiles — one row per Supabase Auth user (Google OAuth identity)
-- ---------------------------------------------------------------------------
create table if not exists public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  display_name text,
  avatar_url text,
  weekly_report_enabled boolean not null default false,
  monthly_report_enabled boolean not null default false,
  report_email text, -- defaults to auth email if null; overridable
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

comment on table public.profiles is 'One row per authenticated user. Created via trigger on auth.users insert.';

-- Auto-create a profile row whenever a new auth user signs in for the first time.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
  insert into public.profiles (id, display_name, avatar_url)
  values (
    new.id,
    coalesce(new.raw_user_meta_data ->> 'full_name', new.raw_user_meta_data ->> 'name'),
    new.raw_user_meta_data ->> 'avatar_url'
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------------
-- activity_type enum — exactly three types, per spec section 14
-- ---------------------------------------------------------------------------
do $$
begin
  if not exists (select 1 from pg_type where typname = 'activity_type') then
    create type public.activity_type as enum ('walking', 'cycling', 'motorcycling');
  end if;
end$$;

do $$
begin
  if not exists (select 1 from pg_type where typname = 'sync_status') then
    create type public.sync_status as enum ('local', 'pending_sync', 'syncing', 'synced', 'sync_failed', 'archived');
  end if;
end$$;

-- ---------------------------------------------------------------------------
-- activities — SUMMARY rows only. Client generates the UUID (idempotent sync).
-- ---------------------------------------------------------------------------
create table if not exists public.activities (
  id uuid primary key, -- client-generated (Android), NOT gen_random_uuid()
  user_id uuid not null references auth.users (id) on delete cascade,
  activity_type public.activity_type not null,

  start_time timestamptz not null,
  end_time timestamptz not null,

  elapsed_seconds integer not null check (elapsed_seconds >= 0),
  moving_seconds integer not null check (moving_seconds >= 0),

  distance_meters double precision not null default 0 check (distance_meters >= 0),

  elevation_gain_meters double precision not null default 0,
  elevation_loss_meters double precision not null default 0,

  average_speed_mps double precision,
  moving_average_speed_mps double precision,
  max_speed_mps double precision,

  start_latitude double precision,
  start_longitude double precision,
  end_latitude double precision,
  end_longitude double precision,

  -- Encoded polyline (Google/OSRM-style precision-5), simplified client-side
  -- (Ramer–Douglas–Peucker) before upload. Raw points never leave the device.
  route_polyline text,

  title text,
  notes text,

  sync_status public.sync_status not null default 'synced',

  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_activities_user_start on public.activities (user_id, start_time desc);
create index if not exists idx_activities_user_type on public.activities (user_id, activity_type);

comment on table public.activities is 'Activity summaries synced from the Android client. Raw GPS points stay on-device; route_polyline is a simplified representation for map rendering.';

-- ---------------------------------------------------------------------------
-- daily_stats — one row per user per calendar date
-- ---------------------------------------------------------------------------
create table if not exists public.daily_stats (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  date date not null,

  steps integer not null default 0,

  walking_distance_meters double precision not null default 0,
  cycling_distance_meters double precision not null default 0,
  motorcycling_distance_meters double precision not null default 0,

  walking_seconds integer not null default 0,
  cycling_seconds integer not null default 0,
  motorcycling_seconds integer not null default 0,

  elevation_gain_meters double precision not null default 0,
  activity_count integer not null default 0,

  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  unique (user_id, date)
);

create index if not exists idx_daily_stats_user_date on public.daily_stats (user_id, date desc);

-- ---------------------------------------------------------------------------
-- monthly_stats — one row per user per calendar month (first-of-month date)
-- ---------------------------------------------------------------------------
create table if not exists public.monthly_stats (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  month date not null, -- always the 1st of the month, e.g. 2026-08-01

  steps integer not null default 0,

  walking_distance_meters double precision not null default 0,
  cycling_distance_meters double precision not null default 0,
  motorcycling_distance_meters double precision not null default 0,

  walking_seconds integer not null default 0,
  cycling_seconds integer not null default 0,
  motorcycling_seconds integer not null default 0,

  elevation_gain_meters double precision not null default 0,
  activity_count integer not null default 0,

  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  unique (user_id, month),
  check (date_trunc('month', month) = month)
);

create index if not exists idx_monthly_stats_user_month on public.monthly_stats (user_id, month desc);

-- ---------------------------------------------------------------------------
-- personal_records — flexible key/value so new record types don't need DDL
-- ---------------------------------------------------------------------------
create table if not exists public.personal_records (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  record_key text not null, -- e.g. 'longest_cycling_ride', 'most_steps_day'
  activity_type public.activity_type, -- null for cross-type records (e.g. most_active_day)

  value_numeric double precision not null,
  value_unit text not null, -- 'meters', 'mps', 'steps', 'seconds', etc.

  activity_id uuid references public.activities (id) on delete set null,
  achieved_on date,

  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  unique (user_id, record_key)
);

create index if not exists idx_personal_records_user on public.personal_records (user_id);

comment on table public.personal_records is 'One row per (user, record_key). Upserted by the client/aggregation job whenever a record is broken. New record types are just new record_key values — no migration needed.';

-- ---------------------------------------------------------------------------
-- export_metadata — tracks generated archives + the export/verify/delete flow
-- (spec sections 34-35). The archive ZIP itself may optionally live in
-- Supabase Storage (private bucket); this table always tracks the metadata.
-- ---------------------------------------------------------------------------
do $$
begin
  if not exists (select 1 from pg_type where typname = 'export_status') then
    create type public.export_status as enum ('generated', 'verified', 'detail_deleted');
  end if;
end$$;

create table if not exists public.export_metadata (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,

  period_label text not null, -- e.g. '2026-08', 'custom_2026-01-01_2026-03-31'
  period_start date not null,
  period_end date not null,

  activity_count integer not null,
  gps_point_count bigint not null default 0,
  steps integer not null default 0,
  distance_meters double precision not null default 0,

  schema_version integer not null default 1,
  checksum text not null, -- sha256 of the archive
  storage_path text, -- path in Supabase Storage bucket, if archived there

  status public.export_status not null default 'generated',
  verified_at timestamptz,
  detail_deleted_at timestamptz,

  exported_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);

create index if not exists idx_export_metadata_user on public.export_metadata (user_id, period_start desc);

comment on table public.export_metadata is 'Tracks the export -> verify -> delete-cloud-detail lifecycle. Never used to delete without a prior verified export (spec section 35).';

-- ---------------------------------------------------------------------------
-- updated_at maintenance trigger (generic, reused by every table above)
-- ---------------------------------------------------------------------------
create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists set_updated_at on public.profiles;
create trigger set_updated_at before update on public.profiles
  for each row execute function public.set_updated_at();

drop trigger if exists set_updated_at on public.activities;
create trigger set_updated_at before update on public.activities
  for each row execute function public.set_updated_at();

drop trigger if exists set_updated_at on public.daily_stats;
create trigger set_updated_at before update on public.daily_stats
  for each row execute function public.set_updated_at();

drop trigger if exists set_updated_at on public.monthly_stats;
create trigger set_updated_at before update on public.monthly_stats
  for each row execute function public.set_updated_at();

drop trigger if exists set_updated_at on public.personal_records;
create trigger set_updated_at before update on public.personal_records
  for each row execute function public.set_updated_at();
