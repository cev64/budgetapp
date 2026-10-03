-- Net worth over time.
-- One snapshot per user per day (America/New_York calendar day). Snapshots are written by:
--   * a nightly pg_cron job for every user that has accounts (public.snapshot_all_net_worth), and
--   * clients / the MCP server calling public.take_net_worth_snapshot() right after they change
--     balances, ledger entries or linked contributions, so "today" is always current.
-- The math lives in ONE place, public.compute_net_worth(), and mirrors DOMAIN_RULES §5.

create extension if not exists pg_cron;

create table public.net_worth_snapshots (
  user_id          uuid not null default auth.uid() references auth.users (id) on delete cascade,
  taken_on         date not null,
  net_worth        numeric not null,
  super_liquid     numeric not null,
  reconciliations  numeric not null,
  -- [{ "id", "name", "group", "liquid", "balance" }, ...] for every non-archived account
  accounts         jsonb not null default '[]'::jsonb,
  source           text not null default 'auto' check (source in ('auto', 'manual', 'import')),
  updated_at       timestamptz not null default clock_timestamp(),
  deleted          boolean not null default false,
  primary key (user_id, taken_on)
);

create trigger net_worth_snapshots_touch before insert or update on public.net_worth_snapshots
  for each row execute function public.touch_updated_at();
create index net_worth_snapshots_user_updated_idx on public.net_worth_snapshots (user_id, updated_at);

alter table public.net_worth_snapshots enable row level security;
create policy "own rows select" on public.net_worth_snapshots for select to authenticated
  using (user_id = (select auth.uid()));
create policy "own rows insert" on public.net_worth_snapshots for insert to authenticated
  with check (user_id = (select auth.uid()));
create policy "own rows update" on public.net_worth_snapshots for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy "own rows delete" on public.net_worth_snapshots for delete to authenticated
  using (user_id = (select auth.uid()));

alter publication supabase_realtime add table public.net_worth_snapshots;

-- DOMAIN_RULES §5, in SQL. Internal helper: callers pass a user id, so it is not exposed.
create or replace function public.compute_net_worth(p_user uuid)
returns table (net_worth numeric, super_liquid numeric, reconciliations numeric, accounts jsonb)
language sql
stable
security definer
set search_path = ''
as $$
  with sums as (
    select b.category_id, sum(coalesce(b.actual, 0)) as total
    from public.budgets b
    where b.user_id = p_user and not b.deleted
    group by b.category_id
  ),
  bal as (
    select a.id, a.name, a.account_group, a.liquid, a.sort_order,
           case
             when a.linked_category_id is null then a.balance
             else a.base_amount + coalesce(c.match_multiplier, 1) * coalesce(s.total, 0)
           end as balance
    from public.accounts a
    left join public.categories c
      on c.id = a.linked_category_id and c.user_id = p_user and not c.deleted
    left join sums s on s.category_id = a.linked_category_id
    where a.user_id = p_user and not a.deleted and not a.archived
  ),
  rec as (
    select coalesce(sum(e.amount), 0) as total
    from public.ledger_entries e
    where e.user_id = p_user and not e.deleted and not e.settled
  )
  select
    (select coalesce(sum(balance), 0) from bal) + rec.total,
    (select coalesce(sum(balance), 0) from bal where liquid),
    rec.total,
    coalesce((select jsonb_agg(jsonb_build_object(
                'id', id, 'name', name, 'group', account_group, 'liquid', liquid, 'balance', balance)
              order by sort_order, name) from bal), '[]'::jsonb)
  from rec;
$$;

revoke execute on function public.compute_net_worth(uuid) from public, anon, authenticated;

create or replace function public.write_net_worth_snapshot(p_user uuid, p_source text)
returns public.net_worth_snapshots
language plpgsql
security definer
set search_path = ''
as $$
declare
  r public.net_worth_snapshots;
begin
  insert into public.net_worth_snapshots
    (user_id, taken_on, net_worth, super_liquid, reconciliations, accounts, source, deleted)
  select p_user, (now() at time zone 'America/New_York')::date,
         n.net_worth, n.super_liquid, n.reconciliations, n.accounts, p_source, false
  from public.compute_net_worth(p_user) n
  on conflict (user_id, taken_on) do update set
    net_worth = excluded.net_worth,
    super_liquid = excluded.super_liquid,
    reconciliations = excluded.reconciliations,
    accounts = excluded.accounts,
    source = excluded.source,
    deleted = false
  returning * into r;
  return r;
end;
$$;

revoke execute on function public.write_net_worth_snapshot(uuid, text) from public, anon, authenticated;

-- Called by the apps (signed-in user) after any change that affects net worth.
create or replace function public.take_net_worth_snapshot()
returns public.net_worth_snapshots
language plpgsql
security definer
set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'not signed in';
  end if;
  return public.write_net_worth_snapshot(auth.uid(), 'manual');
end;
$$;

revoke execute on function public.take_net_worth_snapshot() from public, anon;
grant execute on function public.take_net_worth_snapshot() to authenticated;

-- Nightly job: snapshot every user that has at least one live account.
create or replace function public.snapshot_all_net_worth()
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  u uuid;
  n integer := 0;
begin
  for u in select distinct a.user_id from public.accounts a where not a.deleted loop
    perform public.write_net_worth_snapshot(u, 'auto');
    n := n + 1;
  end loop;
  return n;
end;
$$;

revoke execute on function public.snapshot_all_net_worth() from public, anon, authenticated;

-- 23:55 America/New_York in EDT (03:55 UTC); 22:55 in EST. Either way the same NY calendar day.
select cron.schedule('net-worth-daily', '55 3 * * *', $$select public.snapshot_all_net_worth()$$);
