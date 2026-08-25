-- Optional archived-export storage (spec section 34: "optional Storage").
-- Private bucket; access gated by RLS-style storage policies keyed on the
-- first path segment being the user's own uid, e.g. `{user_id}/2026-08.zip`.

insert into storage.buckets (id, name, public)
values ('export-archives', 'export-archives', false)
on conflict (id) do nothing;

drop policy if exists "export_archives_select_own" on storage.objects;
create policy "export_archives_select_own" on storage.objects
  for select using (
    bucket_id = 'export-archives'
    and auth.uid()::text = (storage.foldername(name))[1]
  );

drop policy if exists "export_archives_insert_own" on storage.objects;
create policy "export_archives_insert_own" on storage.objects
  for insert with check (
    bucket_id = 'export-archives'
    and auth.uid()::text = (storage.foldername(name))[1]
  );

drop policy if exists "export_archives_delete_own" on storage.objects;
create policy "export_archives_delete_own" on storage.objects
  for delete using (
    bucket_id = 'export-archives'
    and auth.uid()::text = (storage.foldername(name))[1]
  );
