-- 0006: activity photos (for personal memory — captions/notes already had
-- a home on `activities.notes`/`activities.title`, this adds pictures) and
-- an avatars bucket so "proper profile" has somewhere to put a picture.
--
-- Design notes:
--  * activity_photos rows are metadata; the bytes live in Supabase Storage
--    under private bucket `activity-photos`, path `{user_id}/{activity_id}/{photo_id}.jpg`
--    — same per-user-folder RLS pattern as the existing `export-archives`
--    bucket in 0003, so storage policies just key off the first path segment.
--  * `avatars` is a SEPARATE bucket and made PUBLIC (unlike activity photos,
--    which stay private): a profile picture is low-sensitivity, and a public
--    bucket means the web/Android clients can just use the plain URL rather
--    than minting a signed URL on every render. Flagging this as a
--    deliberate asymmetry, not an oversight.

-- ---------------------------------------------------------------------------
-- activity_photos
-- ---------------------------------------------------------------------------
create table if not exists public.activity_photos (
  id uuid primary key, -- client-generated (Android) so upload retries are idempotent, same pattern as activities.id
  user_id uuid not null references auth.users (id) on delete cascade,
  activity_id uuid not null references public.activities (id) on delete cascade,

  storage_path text not null, -- path within the `activity-photos` bucket
  caption text,
  position integer not null default 0, -- display order within the activity

  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_activity_photos_user on public.activity_photos (user_id);
create index if not exists idx_activity_photos_activity on public.activity_photos (activity_id, position);

comment on table public.activity_photos is 'Personal-memory photos attached to an activity. Bytes live in the activity-photos Storage bucket at {user_id}/{activity_id}/{id}.jpg; this table is metadata + caption + ordering.';

alter table public.activity_photos enable row level security;

drop policy if exists "activity_photos_select_own" on public.activity_photos;
create policy "activity_photos_select_own" on public.activity_photos
  for select using (auth.uid() = user_id);

drop policy if exists "activity_photos_insert_own" on public.activity_photos;
create policy "activity_photos_insert_own" on public.activity_photos
  for insert with check (auth.uid() = user_id);

drop policy if exists "activity_photos_update_own" on public.activity_photos;
create policy "activity_photos_update_own" on public.activity_photos
  for update using (auth.uid() = user_id);

drop policy if exists "activity_photos_delete_own" on public.activity_photos;
create policy "activity_photos_delete_own" on public.activity_photos
  for delete using (auth.uid() = user_id);

drop trigger if exists set_updated_at on public.activity_photos;
create trigger set_updated_at before update on public.activity_photos
  for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------------------
-- activity-photos bucket (private — same per-user-folder RLS as export-archives)
-- ---------------------------------------------------------------------------
insert into storage.buckets (id, name, public)
values ('activity-photos', 'activity-photos', false)
on conflict (id) do nothing;

drop policy if exists "activity_photos_bucket_select_own" on storage.objects;
create policy "activity_photos_bucket_select_own" on storage.objects
  for select using (
    bucket_id = 'activity-photos'
    and auth.uid()::text = (storage.foldername(name))[1]
  );

drop policy if exists "activity_photos_bucket_insert_own" on storage.objects;
create policy "activity_photos_bucket_insert_own" on storage.objects
  for insert with check (
    bucket_id = 'activity-photos'
    and auth.uid()::text = (storage.foldername(name))[1]
  );

drop policy if exists "activity_photos_bucket_delete_own" on storage.objects;
create policy "activity_photos_bucket_delete_own" on storage.objects
  for delete using (
    bucket_id = 'activity-photos'
    and auth.uid()::text = (storage.foldername(name))[1]
  );

-- ---------------------------------------------------------------------------
-- avatars bucket (public read, owner-only write — path {user_id}.jpg)
-- ---------------------------------------------------------------------------
insert into storage.buckets (id, name, public)
values ('avatars', 'avatars', true)
on conflict (id) do nothing;

drop policy if exists "avatars_public_read" on storage.objects;
create policy "avatars_public_read" on storage.objects
  for select using (bucket_id = 'avatars');

drop policy if exists "avatars_insert_own" on storage.objects;
create policy "avatars_insert_own" on storage.objects
  for insert with check (
    bucket_id = 'avatars'
    and auth.uid()::text = (storage.foldername(name))[1]
  );

drop policy if exists "avatars_update_own" on storage.objects;
create policy "avatars_update_own" on storage.objects
  for update using (
    bucket_id = 'avatars'
    and auth.uid()::text = (storage.foldername(name))[1]
  );

drop policy if exists "avatars_delete_own" on storage.objects;
create policy "avatars_delete_own" on storage.objects
  for delete using (
    bucket_id = 'avatars'
    and auth.uid()::text = (storage.foldername(name))[1]
  );
