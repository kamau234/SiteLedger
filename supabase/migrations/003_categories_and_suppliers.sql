-- 003_categories_and_suppliers.sql
-- User-owned lookup tables. Suppliers must exist before the expenses migration.

create table public.expense_categories (
  id uuid primary key,
  owner_id uuid not null references auth.users (id) on delete cascade,
  name text not null,
  is_default boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (owner_id, name),
  unique (id, owner_id)
);

create trigger expense_categories_set_updated_at
  before insert or update on public.expense_categories
  for each row execute function public.set_updated_at();

create index expense_categories_owner_updated_idx
  on public.expense_categories (owner_id, updated_at);

alter table public.expense_categories enable row level security;

grant select, insert, update, delete on public.expense_categories to authenticated;

create policy "expense_categories_owner_all"
  on public.expense_categories
  for all to authenticated
  using (owner_id = (select auth.uid()))
  with check (owner_id = (select auth.uid()));

create table public.suppliers (
  id uuid primary key,
  owner_id uuid not null references auth.users (id) on delete cascade,
  name text not null,
  phone text,
  location text,
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (id, owner_id)
);

create trigger suppliers_set_updated_at
  before insert or update on public.suppliers
  for each row execute function public.set_updated_at();

create index suppliers_owner_updated_idx on public.suppliers (owner_id, updated_at);

alter table public.suppliers enable row level security;

grant select, insert, update, delete on public.suppliers to authenticated;

create policy "suppliers_owner_all"
  on public.suppliers
  for all to authenticated
  using (owner_id = (select auth.uid()))
  with check (owner_id = (select auth.uid()));

-- Seed the default category set for every new account at sign-up time. The app also
-- seeds defaults locally; name-based idempotency keeps the two paths from duplicating.
create or replace function public.seed_default_categories()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.expense_categories (id, owner_id, name, is_default)
  select gen_random_uuid(), new.id, default_name.value, true
  from unnest(array['Materials', 'Labour', 'Transport', 'Equipment', 'Permits', 'Other'])
    as default_name(value)
  on conflict (owner_id, name) do nothing;
  return new;
exception when others then
  -- Seeding is best-effort; it must never block account creation.
  return new;
end;
$$;

create trigger on_auth_user_created_seed_categories
  after insert on auth.users
  for each row execute function public.seed_default_categories();
