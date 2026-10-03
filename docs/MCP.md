# Budget MCP server (talk to your budget from Claude)

`budget-mcp` is a remote [Model Context Protocol](https://modelcontextprotocol.io) server that
runs as a Supabase Edge Function. Once you connect it, Claude (on claude.ai, the Claude mobile
app, Claude Desktop or Claude Code) can read and change your budget:

- "add $14 Chipotle to food"
- "rent was 1193 this month"
- "how much fun money do I have left?"
- "close September"
- "what's my net worth?"

Changes are written to the same Supabase tables the apps use, following `docs/SYNC.md`
(upserts by the sync keys, tombstones instead of deletes, server-owned `updated_at`). The web app
shows them right away through realtime, and Android picks them up on its next sync.

All numbers come from `supabase/functions/_shared/domain.ts`, a TypeScript port of
`docs/DOMAIN_RULES.md` that is tested against `docs/fixtures/expected.json`.

## 1. Create a token

1. Open the web app and go to **Settings → AI assistant**.
2. Click **Create token**, give it a name (for example "Claude"), and copy the token. It looks
   like `bgt_…` and is shown **once**. Only its SHA-256 hash is stored (`public.mcp_tokens`).
3. Your connector URL is:

   ```
   https://<project-ref>.supabase.co/functions/v1/budget-mcp?key=<token>
   ```

Treat this URL like a password: anyone who has it can read and change your budget.

## 2. Connect Claude

### claude.ai and the Claude mobile app

1. **Settings → Connectors → Add custom connector**.
2. Name: `Budget`. URL: paste the connector URL from step 1, including `?key=…`.
3. Leave the OAuth fields empty and click **Add**.
4. In a chat, enable the Budget connector from the tools menu and ask: "how much fun money do I
   have left?"

Connectors added on claude.ai also show up in the Claude mobile apps signed in to the same
account. The key is in the URL because custom connectors cannot send custom headers.

### Claude Code

```bash
claude mcp add --transport http budget "https://sygxozspiszqfawkmpzx.supabase.co/functions/v1/budget-mcp?key=<token>"
```

Or keep the token out of the URL with a header (any `bgt_` token is accepted as a bearer token):

```bash
claude mcp add --transport http budget https://<project-ref>.supabase.co/functions/v1/budget-mcp \
  --header "Authorization: Bearer <token>"
```

### Claude Desktop

Add it the same way as on claude.ai (**Settings → Connectors → Add custom connector**). If you
use a local config file instead, put the connector URL in `claude_desktop_config.json` through a
remote-MCP bridge such as `npx mcp-remote <url>`.

### Other clients and the MCP Inspector

Any client that supports **Streamable HTTP** works. The server is stateless and answers
`POST` with JSON (no SSE stream; `GET` returns 405). CORS is open, so browser-based inspectors work:

```bash
npx @modelcontextprotocol/inspector
# Transport: Streamable HTTP, URL: the connector URL
```

## 3. Tools

Category, account and ledger names are matched case-insensitively, falling back to prefix,
substring and close-spelling matches ("food", "car insurance" → Car Ins, "brokerage" → Taxable
Brokerage, "401k"). If a name is ambiguous or unknown, the tool returns the valid names. Dates
are `YYYY-MM-DD`. "Today" and "this month" use **America/New_York**. Amounts are dollars, and
negative transaction amounts are refunds.

| Tool | What it does | Example prompt |
|---|---|---|
| `get_budget_overview` (read-only) | One month: expected / actual / difference / remaining per category, grouped into income, expenses and savings; totals (income, expenses, saved incl. match, leftover); last 5 transactions. Defaults to the current month, or the latest month if the current one doesn't exist. | "How much fun money do I have left?" |
| `list_categories` (read-only) | Names, kind, tracking (ledger/manual), match multiplier, archived. | "What categories do I have?" |
| `add_transaction` | Adds a transaction. The budget month comes from `date`, else the current month. A missing month is created first (§6), and the result says so. Warns for manual categories. Returns the category's new actual vs expected. | "Add $14 Chipotle to food" |
| `list_transactions` (read-only) | Filter by year/month, category or text, newest first. Shows the ids the edit tools need. No year/month means all months. | "What did I spend at Target in September?" |
| `update_transaction` | Changes any field by id. year/month moves it to another budget month. | "Make the Chipotle one $16.25" |
| `delete_transaction` (destructive) | Tombstones a transaction (`deleted = true`). | "Delete the duplicate gas charge" |
| `set_budget` | Sets a category's expected amount for a month. | "Budget 500 for food in November" |
| `set_actual` | Sets the actual of a manual category, or the override of a ledger category. `null` clears it. | "Rent was 1193 this month" |
| `set_month_closed` | Closes or reopens a month. Closed months feed their actuals into the year summary. Year is optional. | "Close September" |
| `create_month` | Starts a month: copies expected amounts forward and adds the recurring items. Does nothing if the month exists. | "Start November" |
| `get_year_summary` (read-only) | Per-category expected vs projected actual, totals, annualized savings, % of net and gross income, and each month's status and leftover. | "How's 2026 looking?" |
| `get_net_worth` (read-only) | Accounts (linked ones computed), net worth, super liquid assets, net reconciliations, unsettled IOUs, plus the 30-day change when snapshots exist. | "What's my net worth?" |
| `get_net_worth_history` (read-only) | Daily snapshot series (`range` 1M / 3M (default) / 6M / 1Y / All; optional `account` for one account's balance), the change over the range per DOMAIN_RULES §5b, and the low and high. With fewer than 2 snapshots it says history starts today. | "How has my net worth changed over 6 months?" |
| `update_account_balance` | Sets an account balance. Linked accounts (401k, HSA) refuse a balance and take `base_amount` instead. | "Checking is 2,340 now" |
| `add_ledger_entry` | Adds an IOU (positive = owed to me, negative = I owe). | "Sam owes me 40 for tickets" |
| `settle_ledger_entry` | Settles an IOU by name or id. `settled: false` reopens it. | "Sam paid me back" |
| `get_meal_plans` (read-only) | Meal plans and recipes with totals and monthly cost (× days per month). | "What does my deficit day cost per month?" |

Net worth snapshots (§5b): after every write that can change net worth (`update_account_balance`,
`add_ledger_entry`, `settle_ledger_entry`, and `set_actual` on a category linked to an account,
such as 401k or HSA), the server refreshes today's snapshot by calling the SQL function
`write_net_worth_snapshot(user, 'manual')` with the server key, so the math stays in SQL. If that
call fails, the error is logged and the user's write still succeeds. The result's
`net_worth_snapshot_refreshed` field reports `false` in that case.

Every tool returns a short human-readable text and `structuredContent` (JSON). Tools carry
MCP annotations (`readOnlyHint`, `destructiveHint`, `idempotentHint`). The server also sends
`instructions` that explain the budget model (expected vs actual, ledger vs manual tracking,
closed months) so the assistant chooses the right tool.

## 4. Security

- **The connector URL is a password.** Don't share it or paste it into chats. If it leaks, revoke
  the token in **Settings → AI assistant** (it stops working immediately) and create a new one.
  Use one token per client so you can revoke them separately. "Last used" updates at most once
  a minute.
- Tokens are random `bgt_…` strings. Only `sha256(token)` is stored, and the function looks tokens up
  by hash. The function never logs tokens or request URLs. Supabase's own edge-function
  invocation logs, which only project owners can see, may record request URLs including the query
  string. Clients that can send headers can use `Authorization: Bearer <token>` instead.
- The function is deployed with `verify_jwt = false` because MCP clients can't send a Supabase
  JWT. It authenticates every request with the token and answers `401` (JSON) otherwise.
- It uses the server-side secret key (`SUPABASE_SECRET_KEYS.default`, or the legacy
  `SUPABASE_SERVICE_ROLE_KEY`). Supabase injects these automatically, and they never leave the
  server. Because that key bypasses row-level security, `SupabaseStore` filters **every** query by
  `user_id = <token owner>` and stamps `user_id` on every write. The test suite checks this.
- Nothing is ever hard-deleted. Deletes are tombstones, just as in the apps.

## 5. Deploy

Files (everything is under `supabase/functions/`, with relative imports only):

```
budget-mcp/index.ts           entry point (Deno.serve)
budget-mcp/server.ts          HTTP: CORS, token auth, MCP Streamable HTTP transport
budget-mcp/tools.ts           tool definitions + handlers + server instructions
budget-mcp/store.ts           BudgetStore interface + column pickers
budget-mcp/supabase_store.ts  BudgetStore on Supabase (service role, user-scoped)
budget-mcp/memory_store.ts    in-memory BudgetStore (tests / local fake mode only)
budget-mcp/deps.ts            pinned third-party imports
budget-mcp/deno.json          import map mirror, lint config, tasks
_shared/domain.ts             the budget math (pure TS)
```

With the Supabase CLI:

```bash
supabase functions deploy budget-mcp --no-verify-jwt   # verify_jwt=false is also in supabase/config.toml
```

With the Supabase MCP `deploy_edge_function` tool (how the live function is deployed today),
upload a one-line shim as `index.ts` (name `budget-mcp`, `verify_jwt: false`) that imports
this exact source from GitHub, pinned to a commit so the deployed code is immutable and
matches what was reviewed:

```ts
import "https://raw.githubusercontent.com/cev64/budgetapp/<commit-sha>/supabase/functions/budget-mcp/index.ts";
```

To ship a change: push it, then redeploy the shim with the new commit SHA. Do not point the
shim at a branch name.

No extra secrets are needed. `SUPABASE_URL` and the secret / service-role key are injected
automatically. The `mcp_tokens` table comes from `supabase/migrations/20261003000000_mcp_tokens.sql`.
Net worth history needs `supabase/migrations/20261003010000_net_worth_history.sql`, which creates
`net_worth_snapshots` and `write_net_worth_snapshot()`. That function is executable by
service_role and revoked from anon and authenticated.

Smoke test after deploying (with a real token):

```bash
curl -s -X POST "https://sygxozspiszqfawkmpzx.supabase.co/functions/v1/budget-mcp?key=<token>" \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_budget_overview","arguments":{}}}'
```

## 6. Develop and test

Install Deno 2, then, from `supabase/functions/budget-mcp/`:

```bash
deno task test     # domain math vs docs/fixtures/expected.json, tool handlers, HTTP/auth/user scoping
deno task check    # type-check
deno task lint
deno task dev:fake # local server on :8000 with an in-memory store seeded from the fixture
```

Fake mode (`BUDGET_MCP_FAKE=1`) accepts only the token `bgt_fake_local_token_0000`. It refuses to
start when any Supabase server key or hosted-runtime variable is present, so it cannot turn on in
production.

```bash
curl -s -X POST 'http://localhost:8000/?key=bgt_fake_local_token_0000' \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

Libraries: `@modelcontextprotocol/sdk` 1.31.0 (`McpServer` + `WebStandardStreamableHTTPServerTransport`,
which is the approach Supabase's "Deploy MCP servers" guide recommends), `zod` 4.6.5, `@supabase/supabase-js` 2.117.2.
