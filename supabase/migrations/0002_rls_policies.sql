-- PersonalStrava — Row Level Security
-- Every user-owned table: RLS enabled, and a user may only read/insert/
-- update/delete rows where user_id = auth.uid(). No table is ever readable
-- cross-user. profiles uses id = auth.uid() (its own primary key is the user id).

alter table public.profiles enable row level security;
alter table public.activities enable row level security;
alter table public.daily_stats enable row level security;
alter table public.monthly_stats enable row level security;
alter table public.personal_records enable row level security;
alter table public.export_metadata enable row level security;

-- profiles --------------------------------------------------------------
drop policy if exists "profiles_select_own" on public.profiles;
create policy "profiles_select_own" on public.profiles
  for select using (auth.uid() = id);

drop policy if exists "profiles_update_own" on public.profiles;
create policy "profiles_update_own" on public.profiles
  for update using (auth.uid() = id) with check (auth.uid() = id);

-- Note: insert is handled by the handle_new_user() trigger (security definer),
-- not by client-side inserts, so no insert policy is granted to authenticated.

-- activities --------------------------------------------------------------
drop policy if exists "activities_select_own" on public.activities;
create policy "activities_select_own" on public.activities
  for select using (auth.uid() = user_id);

drop policy if exists "activities_insert_own" on public.activities;
create policy "activities_insert_own" on public.activities
  for insert with check (auth.uid() = user_id);

drop policy if exists "activities_update_own" on public.activities;
create policy "activities_update_own" on public.activities
  for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists "activities_delete_own" on public.activities;
create policy "activities_delete_own" on public.activities
  for delete using (auth.uid() = user_id);

-- daily_stats --------------------------------------------------------------
drop policy if exists "daily_stats_select_own" on public.daily_stats;
create policy "daily_stats_select_own" on public.daily_stats
  for select using (auth.uid() = user_id);

drop policy if exists "daily_stats_insert_own" on public.daily_stats;
create policy "daily_stats_insert_own" on public.daily_stats
  for insert with check (auth.uid() = user_id);

drop policy if exists "daily_stats_update_own" on public.daily_stats;
create policy "daily_stats_update_own" on public.daily_stats
  for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists "daily_stats_delete_own" on public.daily_stats;
create policy "daily_stats_delete_own" on public.daily_stats
  for delete using (auth.uid() = user_id);

-- monthly_stats --------------------------------------------------------------
drop policy if exists "monthly_stats_select_own" on public.monthly_stats;
create policy "monthly_stats_select_own" on public.monthly_stats
  for select using (auth.uid() = user_id);

drop policy if exists "monthly_stats_insert_own" on public.monthly_stats;
create policy "monthly_stats_insert_own" on public.monthly_stats
  for insert with check (auth.uid() = user_id);

drop policy if exists "monthly_stats_update_own" on public.monthly_stats;
create policy "monthly_stats_update_own" on public.monthly_stats
  for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists "monthly_stats_delete_own" on public.monthly_stats;
create policy "monthly_stats_delete_own" on public.monthly_stats
  for delete using (auth.uid() = user_id);

-- personal_records --------------------------------------------------------------
drop policy if exists "personal_records_select_own" on public.personal_records;
create policy "personal_records_select_own" on public.personal_records
  for select using (auth.uid() = user_id);

drop policy if exists "personal_records_insert_own" on public.personal_records;
create policy "personal_records_insert_own" on public.personal_records
  for insert with check (auth.uid() = user_id);

drop policy if exists "personal_records_update_own" on public.personal_records;
create policy "personal_records_update_own" on public.personal_records
  for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists "personal_records_delete_own" on public.personal_records;
create policy "personal_records_delete_own" on public.personal_records
  for delete using (auth.uid() = user_id);

-- export_metadata --------------------------------------------------------------
drop policy if exists "export_metadata_select_own" on public.export_metadata;
create policy "export_metadata_select_own" on public.export_metadata
  for select using (auth.uid() = user_id);

drop policy if exists "export_metadata_insert_own" on public.export_metadata;
create policy "export_metadata_insert_own" on public.export_metadata
  for insert with check (auth.uid() = user_id);

drop policy if exists "export_metadata_update_own" on public.export_metadata;
create policy "export_metadata_update_own" on public.export_metadata
  for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists "export_metadata_delete_own" on public.export_metadata;
create policy "export_metadata_delete_own" on public.export_metadata
  for delete using (auth.uid() = user_id);
