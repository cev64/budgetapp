-- Personal access tokens for the budget MCP server (supabase/functions/budget-mcp).
-- The raw token is shown to the user once (web Settings -> AI assistant); only its
-- SHA-256 hex digest is stored. The Edge Function looks tokens up with the service role.

create table public.mcp_tokens (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name          text not null default 'Assistant',
  token_hash    text not null unique,          -- sha256(token) as lowercase hex
  token_hint    text,                          -- last 4 chars, for display only
  created_at    timestamptz not null default now(),
  last_used_at  timestamptz,
  revoked       boolean not null default false
);

create index mcp_tokens_user_idx on public.mcp_tokens (user_id);

alter table public.mcp_tokens enable row level security;

create policy "own tokens select" on public.mcp_tokens for select to authenticated
  using (user_id = (select auth.uid()));
create policy "own tokens insert" on public.mcp_tokens for insert to authenticated
  with check (user_id = (select auth.uid()));
-- Users may only revoke/rename; the hash never changes after creation.
create policy "own tokens update" on public.mcp_tokens for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy "own tokens delete" on public.mcp_tokens for delete to authenticated
  using (user_id = (select auth.uid()));
