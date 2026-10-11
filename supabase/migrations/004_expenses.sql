-- 004_expenses.sql
-- Project expense ledger. Composite (child_id, owner_id) foreign keys guarantee at the
-- database level that a project, category and supplier referenced by an expense all
-- belong to the same owner as the expense itself (blueprint sections 02 and 03).

create table public.expenses (
  id uuid primary key,
  owner_id uuid not null references auth.users (id) on delete cascade,
  project_id uuid not null,
  category_id uuid not null,
  supplier_id uuid,
  description text not null,
  quantity_milli bigint,
  unit_price_kes bigint,
  amount_kes bigint not null check (amount_kes >= 0),
  payment_method text not null default 'other'
    check (payment_method in ('cash', 'mobile_money', 'bank', 'other')),
  -- Calendar date of the expense (never a UTC timestamp).
  expense_date date not null,
  notes text,
  receipt_path text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  check (quantity_milli is null or quantity_milli > 0),
  check (unit_price_kes is null or unit_price_kes >= 0),
  foreign key (project_id, owner_id) references public.projects (id, owner_id),
  foreign key (category_id, owner_id) references public.expense_categories (id, owner_id),
  foreign key (supplier_id, owner_id) references public.suppliers (id, owner_id)
);

create trigger expenses_set_updated_at
  before insert or update on public.expenses
  for each row execute function public.set_updated_at();

create index expenses_project_date_idx
  on public.expenses (owner_id, project_id, expense_date);

create index expenses_owner_updated_idx
  on public.expenses (owner_id, updated_at);

alter table public.expenses enable row level security;

grant select, insert, update, delete on public.expenses to authenticated;

create policy "expenses_owner_all"
  on public.expenses
  for all to authenticated
  using (owner_id = (select auth.uid()))
  with check (owner_id = (select auth.uid()));
