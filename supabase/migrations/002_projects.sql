-- 002_projects.sql
-- Project profile and approved budget (KES whole shillings, non-negative).

create table public.projects (
  id uuid primary key,
  owner_id uuid not null references auth.users (id) on delete cascade,
  name text not null,
  client_name text,
  location text,
  budget_amount bigint not null default 0 check (budget_amount >= 0),
  start_date date,
  expected_end_date date,
  status text not null default 'active'
    check (status in ('planned', 'active', 'completed', 'archived')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  -- Referenced by the owner-consistent composite foreign key on expenses.
  unique (id, owner_id)
);

create trigger projects_set_updated_at
  before insert or update on public.projects
  for each row execute function public.set_updated_at();

create index projects_owner_updated_idx on public.projects (owner_id, updated_at);

alter table public.projects enable row level security;

grant select, insert, update, delete on public.projects to authenticated;

create policy "projects_owner_all"
  on public.projects
  for all to authenticated
  using (owner_id = (select auth.uid()))
  with check (owner_id = (select auth.uid()));
