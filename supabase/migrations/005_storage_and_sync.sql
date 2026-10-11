-- 005_storage_and_sync.sql
-- Private receipt storage and the sync idempotency table.
--
-- Receipts live in a private bucket and never leave the user's own prefix:
--   {user_uuid}/{project_uuid}/{expense_uuid}.jpg
-- Storage policies constrain both the bucket and the first path segment.

insert into storage.buckets (id, name, public)
values ('receipts', 'receipts', false)
on conflict (id) do nothing;

create policy "receipts_owner_select"
  on storage.objects
  for select to authenticated
  using (
    bucket_id = 'receipts'
    and (storage.foldername(name))[1] = (select auth.uid())::text
  );

create policy "receipts_owner_insert"
  on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'receipts'
    and (storage.foldername(name))[1] = (select auth.uid())::text
  );

create policy "receipts_owner_update"
  on storage.objects
  for update to authenticated
  using (
    bucket_id = 'receipts'
    and (storage.foldername(name))[1] = (select auth.uid())::text
  )
  with check (
    bucket_id = 'receipts'
    and (storage.foldername(name))[1] = (select auth.uid())::text
  );

create policy "receipts_owner_delete"
  on storage.objects
  for delete to authenticated
  using (
    bucket_id = 'receipts'
    and (storage.foldername(name))[1] = (select auth.uid())::text
  );

-- Idempotency record for a future transactional sync RPC. The current client is already
-- idempotent because every business row uses a locally generated UUID upserted by its
-- primary key; this table is provisioned so a server-side apply-and-acknowledge RPC can
-- be added without a schema change (blueprint section 04, "stronger guarantees").
create table public.sync_changes (
  change_id uuid primary key,
  owner_id uuid not null references auth.users (id) on delete cascade,
  entity_type text not null,
  entity_id uuid not null,
  operation text not null,
  processed_at timestamptz not null default now()
);

create index sync_changes_owner_idx on public.sync_changes (owner_id, processed_at);

alter table public.sync_changes enable row level security;

grant select, insert, update, delete on public.sync_changes to authenticated;

create policy "sync_changes_owner_all"
  on public.sync_changes
  for all to authenticated
  using (owner_id = (select auth.uid()))
  with check (owner_id = (select auth.uid()));
