-- Budget app: initial schema
-- Every table is owned by one user (user_id) and protected by row-level security.
-- Sync contract (see docs/SYNC.md):
--   * updated_at is set by the server on every insert/update (clock_timestamp()).
--     Clients pull rows with updated_at > (last_cursor - 10s overlap).
--   * Rows are never hard-deleted by clients; they set deleted = true (tombstone).
--   * There are deliberately no foreign keys between budget tables, so clients can
--     push in any order and tombstones never violate constraints.

-- ---------------------------------------------------------------------------
-- updated_at trigger
-- ---------------------------------------------------------------------------
create or replace function public.touch_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at := clock_timestamp();
  return new;
end;
$$;

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------

-- One row per user.
create table public.settings (
  user_id         uuid primary key default auth.uid() references auth.users (id) on delete cascade,
  net_income      numeric not null default 68000,   -- annual take-home, for "% of net income"
  gross_income    numeric not null default 85000,   -- annual gross, for "% of gross income"
  currency        text    not null default 'USD',
  days_per_month  numeric not null default 30.5,    -- meal plan daily cost -> monthly cost
  updated_at      timestamptz not null default clock_timestamp(),
  deleted         boolean not null default false
);

create table public.categories (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name             text not null,
  kind             text not null check (kind in ('income', 'expense', 'savings')),
  tracking         text not null check (tracking in ('ledger', 'manual')),
  match_multiplier numeric not null default 1,      -- savings only: 401k = 2 (employer match)
  sort_order       integer not null default 0,
  icon             text,
  color            text,
  archived         boolean not null default false,
  updated_at       timestamptz not null default clock_timestamp(),
  deleted          boolean not null default false
);

-- A month that exists in the budget. Natural key (user_id, year, month).
create table public.months (
  user_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
  year        integer not null check (year between 2000 and 2100),
  month       integer not null check (month between 1 and 12),
  closed      boolean not null default false,           -- the sheet's C23 checkbox
  note        text,
  updated_at  timestamptz not null default clock_timestamp(),
  deleted     boolean not null default false,
  primary key (user_id, year, month)
);

-- Expected / manual actual per category per month. Natural key.
create table public.budgets (
  user_id      uuid not null default auth.uid() references auth.users (id) on delete cascade,
  year         integer not null check (year between 2000 and 2100),
  month        integer not null check (month between 1 and 12),
  category_id  uuid not null,
  expected     numeric,          -- null = not budgeted (treated as 0)
  actual       numeric,          -- manual actual; for ledger categories an override of the ledger sum
  updated_at   timestamptz not null default clock_timestamp(),
  deleted      boolean not null default false,
  primary key (user_id, year, month, category_id)
);

create table public.transactions (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null default auth.uid() references auth.users (id) on delete cascade,
  year         integer not null check (year between 2000 and 2100),
  month        integer not null check (month between 1 and 12),
  category_id  uuid not null,
  date         date,             -- optional, may fall outside the budget month
  item         text not null default '',
  amount       numeric not null default 0,   -- negative = refund / reimbursement
  note         text,
  created_at   timestamptz not null default clock_timestamp(),
  updated_at   timestamptz not null default clock_timestamp(),
  deleted      boolean not null default false
);

-- Templates copied into a new month (e.g. subscriptions).
create table public.recurring_items (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid not null default auth.uid() references auth.users (id) on delete cascade,
  category_id   uuid not null,
  item          text not null default '',
  amount        numeric not null default 0,
  day_of_month  integer check (day_of_month between 1 and 31),
  active        boolean not null default true,
  sort_order    integer not null default 0,
  updated_at    timestamptz not null default clock_timestamp(),
  deleted       boolean not null default false
);

-- Net worth accounts.
create table public.accounts (
  id                  uuid primary key default gen_random_uuid(),
  user_id             uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name                text not null,
  account_group       text not null default 'cash' check (account_group in ('cash', 'investment', 'asset', 'debt')),
  liquid              boolean not null default false,  -- counts toward "Super Liquid Assets"
  balance             numeric not null default 0,      -- signed; debts are negative
  linked_category_id  uuid,                            -- if set, balance = base_amount + multiplier * sum(actual)
  base_amount         numeric not null default 0,
  sort_order          integer not null default 0,
  archived            boolean not null default false,
  updated_at          timestamptz not null default clock_timestamp(),
  deleted             boolean not null default false
);

-- Reconciliations / IOUs (the sheet's "Ledger"). Positive = owed to me, negative = I owe.
create table public.ledger_entries (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name        text not null,
  amount      numeric not null default 0,
  note        text,
  settled     boolean not null default false,
  sort_order  integer not null default 0,
  updated_at  timestamptz not null default clock_timestamp(),
  deleted     boolean not null default false
);

-- Meal plans (a full day) and recipes (one dish broken into ingredients).
create table public.meal_plans (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name        text not null,
  kind        text not null default 'day' check (kind in ('day', 'recipe')),
  label       text,             -- e.g. 'Deficit', 'Maintenance'
  note        text,             -- free text, e.g. recipe steps
  sort_order  integer not null default 0,
  updated_at  timestamptz not null default clock_timestamp(),
  deleted     boolean not null default false
);

create table public.meal_items (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
  plan_id     uuid not null,
  time_label  text,             -- e.g. '6:00 AM'
  name        text not null default '',
  calories    numeric,
  protein     numeric,
  fiber       numeric,
  fat         numeric,
  cost        numeric,
  sort_order  integer not null default 0,
  updated_at  timestamptz not null default clock_timestamp(),
  deleted     boolean not null default false
);

-- ---------------------------------------------------------------------------
-- Triggers, indexes, RLS, realtime
-- ---------------------------------------------------------------------------
do $$
declare
  t text;
begin
  foreach t in array array['settings','categories','months','budgets','transactions',
                           'recurring_items','accounts','ledger_entries','meal_plans','meal_items']
  loop
    execute format('create trigger %I before insert or update on public.%I
                    for each row execute function public.touch_updated_at()', t || '_touch', t);
    execute format('create index %I on public.%I (user_id, updated_at)', t || '_user_updated_idx', t);
    execute format('alter table public.%I enable row level security', t);
    execute format('create policy "own rows select" on public.%I for select to authenticated
                    using (user_id = (select auth.uid()))', t);
    execute format('create policy "own rows insert" on public.%I for insert to authenticated
                    with check (user_id = (select auth.uid()))', t);
    execute format('create policy "own rows update" on public.%I for update to authenticated
                    using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()))', t);
    execute format('create policy "own rows delete" on public.%I for delete to authenticated
                    using (user_id = (select auth.uid()))', t);
    execute format('alter publication supabase_realtime add table public.%I', t);
  end loop;
end;
$$;

create index transactions_month_idx on public.transactions (user_id, year, month);

-- ---------------------------------------------------------------------------
-- New-user bootstrap: settings + the default categories from the spreadsheet.
-- Runs server-side so two devices signing in at once can never seed duplicates.
-- ---------------------------------------------------------------------------
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.settings (user_id) values (new.id) on conflict do nothing;

  insert into public.categories (user_id, name, kind, tracking, match_multiplier, sort_order) values
    (new.id, 'Paychecks',         'income',  'ledger', 1, 0),
    (new.id, 'Rent',              'expense', 'manual', 1, 10),
    (new.id, 'Subscriptions',     'expense', 'ledger', 1, 11),
    (new.id, 'Food',              'expense', 'ledger', 1, 12),
    (new.id, 'Fun',               'expense', 'ledger', 1, 13),
    (new.id, 'Gas',               'expense', 'ledger', 1, 14),
    (new.id, 'Misc',              'expense', 'ledger', 1, 15),
    (new.id, 'Car Ins',           'expense', 'manual', 1, 16),
    (new.id, 'Utilities',         'expense', 'manual', 1, 17),
    (new.id, 'Phone Bill',        'expense', 'manual', 1, 18),
    (new.id, 'Roth',              'savings', 'manual', 1, 20),
    (new.id, '401k',              'savings', 'manual', 2, 21),
    (new.id, 'Taxable Brokerage', 'savings', 'manual', 1, 22),
    (new.id, 'HSA',               'savings', 'manual', 1, 23);
  return new;
end;
$$;

revoke execute on function public.handle_new_user() from public, anon, authenticated;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();
