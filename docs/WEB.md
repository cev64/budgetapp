# Web app

The browser version of Budget: `web/`. It replaces the Google Sheet alongside the Android app,
shares its data through Supabase, and follows the same spec (`PRODUCT_SPEC.md`), math
(`docs/DOMAIN_RULES.md`), sync contract (`docs/SYNC.md`) and look (`docs/UI_ANATOMY.md`,
`docs/FLUID_GLASS_UI.md`, `design/tokens.json`).

Live: https://cev64.github.io/budgetapp/ (deployed from `main` by `.github/workflows/web.yml`).

## Stack

Vite + React 19 + TypeScript (strict), `@supabase/supabase-js` v2, `lucide-react` icons,
`react-router` (HashRouter, so GitHub Pages needs no rewrites), `vitest`. Charts are hand-rolled SVG.
Fonts are bundled (`@fontsource-variable/inter`, `@fontsource/barlow-condensed` 600); nothing is loaded
from a font CDN. No other runtime dependencies.

## Running locally

```sh
cd web
npm ci
npm run dev            # http://localhost:5173/        (dev server, base "/")
npm test               # vitest: domain math against docs/fixtures, store, backup, formatting
npm run typecheck      # tsc --noEmit
npm run build          # typecheck + production build into web/dist (base "/budgetapp/")
npx vite preview       # http://localhost:4173/budgetapp/  (serves the production build)
```

Node 22 is what CI uses.

## Demo mode

Open `#/?demo=1` (for example http://localhost:5173/#/?demo=1) or press **Try demo mode** on the
sign-in screen. The app loads `docs/fixtures/sample-backup.json` into an in-memory backend: no
network, nothing saved, a "Demo data" pill in the top bar. For the net worth chart it also synthesizes
~400 days of history in memory (`src/data/demoHistory.ts`: a seeded random walk per account with upward
drift that ends at the fixture's current balances); it is never written anywhere, and demo mode never
calls the snapshot RPC, so the history does not move when you edit balances. Writes, import and export all work, but
vanish on reload. Demo mode lasts for the browser tab (sessionStorage); **Settings → Exit demo**
leaves it. The fixture is code-split, so normal users never download it.

## Configuration

`config/supabase.json` (repo root) holds the project URL and the **publishable** key; Vite inlines it
at build time. `VITE_SUPABASE_URL` / `VITE_SUPABASE_PUBLISHABLE_KEY` override it (for example in a
`.env.local`, which is git-ignored). When both are empty the sign-in screen shows "Backend not
configured" and offers demo mode. Never put a secret / service-role key in either place.

Auth emails (confirmation, password reset) redirect to the current page URL, so add the deployed
URL (and `http://localhost:5173/` for development) to Supabase **Auth → URL configuration → Redirect
URLs**. The client uses the implicit flow: tokens arrive in the URL hash and supabase-js consumes them
before the hash router mounts, so a link opened in any browser works. A recovery link opens the
"New password" screen.

## Architecture

```
web/
  index.html                 theme applied before first paint, fonts, manifest
  public/                    brand favicon.svg, app icons (1024 any, 512 maskable, 192, 180 apple-touch),
                             manifest.webmanifest
  sw/sw.js                   service worker template (vite.config.ts fills version + asset list)
  src/
    main.tsx                 boot: theme, auth redirect handling, render, SW registration (prod only)
    config.ts                Supabase URL/key resolution, app version
    domain/                  pure TS, no React, no I/O
      types.ts, tables.ts    row types, table keys, on_conflict targets, column lists, normalisation
      calc.ts                DOMAIN_RULES §1–§4b: per-category values, month totals, projection, year summary,
                             leftover vs plan (projectedLeftover, monthVsPlan)
      networth.ts            §5 net worth, super liquid, reconciliations, linked accounts
      history.ts             §5b net worth history: changeOver, ranges (1M/3M/6M/1Y/All), series, per-account
      categoryStyle.ts       category colour + symbol from design/tokens.json (palette + assignment)
      sheet.ts               Sheet view layout: left-block rows, ledger block → column mapping, ledger order
      newMonth.ts            §6 new-month rows (budgets copied forward, recurring items, day clamped)
      format.ts              §8 money (whole dollars from $1,000) / percent / tone, parsing, brand voice strings
      backup.ts              backup JSON build / parse / merge plan (SYNC.md)
      dates.ts
    data/
      backend.ts             Backend interface; SupabaseBackend (pull/upsert/realtime) and DemoBackend
      store.ts               the client store: snapshot, optimistic writes, merges, cache
      actions.ts             every user-facing write (set budget, create month, CRUD, import …)
      mcp.ts                 MCP connection tokens (Settings → AI assistant)
      demoHistory.ts         synthetic net worth history for demo mode
      supabase.ts, demo.ts, safeStorage.ts
    app/                     Root (auth gate), routes, Shell (nav, top bar, add sheet), session context
    screens/                 Home, Month (+ CategoryDetail), Year, NetWorth (+ NetWorthHistory), Sheet (+ sheetGrid),
                             Settings, Auth,
                             TransactionSheet, McpSection
    ui/                      Fluid Glass kit: motion.ts (tint, glideIndicator, rollText, FLIP), Seg,
                             Sheet + ask(), Toast, Menu, FlipList, Num, controls, theme, Brand (logo),
                             HistoryChart (area chart + Sparkline)
    styles/                  tokens.css (light + dark), base.css (guide §5–§7), app.css (layout, screens)
  test/                      vitest suites
```

Screens use `useData()` for `{ ds, calc }` (live rows + a memoised domain calculator) and
`useActions()` for writes. All numbers come from `src/domain`, which is tested against
`docs/fixtures/expected.json` (every month, year and net-worth number).

### Store, sync and realtime

`Store` (`src/data/store.ts`) holds every table keyed by row identity (id, or the natural key for
`months`, `budgets`, `settings`). Tombstones stay in the store so later merges are correct; the UI
only sees live rows.

- **Load:** on start the store paints the cached snapshot from localStorage
  (`budget.cache.<user id>`), then pulls every table in full from Supabase, 1000 rows per page,
  ordered by `updated_at` plus the key.
- **Realtime:** one channel subscribes to `postgres_changes` on all nine tables with the filter
  `user_id=eq.<uid>`. Each change is merged as it arrives, so edits on the phone show up live.
- **Catch-up:** on window focus / tab visible, on the browser `online` event and whenever the realtime
  channel re-subscribes, the store pulls rows with `updated_at > cursor − 10 s` per table (SYNC.md rule 3).
- **Merge rules:** a remote row never replaces a row with a local write in flight, and an older
  `updated_at` never replaces a newer one.
- **Writes are optimistic:** the store updates local state first, then upserts with the SYNC.md
  `on_conflict` keys, always sending `user_id` and never `updated_at`. The returned row (with the
  server's `updated_at`) replaces the local one, unless the row was edited again meanwhile. On
  error the rows revert and a toast explains why. Deletes are tombstones (`deleted: true`); a
  category with data can only be archived. Ids come from
  `crypto.randomUUID()`.
- **Status dot** in the top bar: green synced, pulsing blue syncing, amber offline. Clicking it
  syncs now (also in Settings → Data).
- **Sign-out** deletes the cached snapshot from the browser.

### Net worth history

`net_worth_snapshots` (DOMAIN_RULES §5b) is loaded, cached and merged from realtime like the other
tables, but it is **pull-only**: `Store.write` refuses it, except for backup import (rows marked
`source: "import"`). After a successful write that can change net worth (accounts, ledger entries, or
budgets / categories that an account links to) the store calls RPC `take_net_worth_snapshot()`,
debounced 2 s, and merges the returned row; it also calls it after an import. The server computes every
snapshot value. The Net worth screen shows the live total, the change for the selected range
(1M/3M/6M/1Y/All, remembered per browser), an SVG area chart with scrub tooltip and a Super liquid
overlay, 30-day changes on the tiles, and per-account history when you tap an account. Home's Net worth
tile shows the 30-day change and a sparkline. With fewer than 2 snapshots the chart shows
"History starts today, a point is saved every day."

### Backup import / export

Settings → Data. Export writes the SYNC.md JSON (live rows only, no sync columns). Import parses and
validates the file, plans a merge (categories matched by name case-insensitively and remapped
everywhere, months/budgets by natural key, the rest by id, settings replaced, tombstones in the file
skipped), shows a preview such as "This will add 6 months, 158 transactions …", and only writes after
confirmation. It never deletes anything.

### AI assistant (MCP)

Settings → **AI assistant (MCP)** creates personal connector URLs for the `budget-mcp` Edge Function
(`docs/MCP.md`). A token is `bgt_` + 32 random bytes (base64url); only its SHA-256 hex and last four
characters go into `public.mcp_tokens`. The full URL
`<SUPABASE_URL>/functions/v1/budget-mcp?key=<token>` is shown once with a Copy button and the
Claude setup steps. The list shows each connection's name, hint, created and last-used dates, with
Revoke (sets `revoked = true`). `mcp_tokens` is not a sync table: it loads only when Settings opens,
has no realtime subscription and is not in backups. In demo mode the section is disabled.

## Layout and design

Fluid Glass + brand tokens (`design/tokens.json` v2) are CSS variables (`src/styles/tokens.css`).
Labels on accent fills use `--on-accent` (white in light, navy in dark), toasts `--on-toast`, inputs
`--control-border`, focus is a 2px `--focus-ring` with 2px offset, sheets and floating bars have radius 24.
Type follows the v2 scale: body 16/24, table numbers 16/500 with tabular lining figures, micro 12/600,
display Barlow Condensed 600 40/44, titles 24/30, hero numbers 32/40. Type never shrinks to fit: narrow
cards switch layout instead (names move above the number columns under ~340px of card width).
Hit targets are at least 44px (small controls get an invisible 44px hit area).

Brand: the side nav shows the logo lockup and the rail / sign-in screen show the mark, inlined from
`design/brand/logo-lockup.svg` and `logo-mark.svg` with their two colours mapped to `--ink` / `--accent`
(which equal the dark lockup's colours in dark mode). Categories get their colour **and symbol** from
`categoryPalette` via `categoryAssignment` (`src/domain/categoryStyle.ts`); labels stay in ink. Copy uses
the exact strings from UI_ANATOMY "Brand (v2)" (saved / over-budget / month-closed / empty month / sign-in).
Money display follows DOMAIN_RULES §8: whole dollars from $1,000 up; edit fields show the exact value. The dark theme follows
`prefers-color-scheme` unless Settings → Appearance sets Light or Dark (`data-theme` on `<html>`,
stored in localStorage). Breakpoints follow UI_ANATOMY: under 600px a floating glass bottom bar (Home, Month, Year, Net worth, Sheet) with a
round Add button above it; 600–1023px an 80px rail with Add on top; from 1024px a 220px side nav, an Add button
in the top bar, and multi-pane screens (Month list + category detail, Year tables + chart, Net worth accounts and
ledger side by side). Phone width (412px, Galaxy Fold cover) is a primary target, and nothing scrolls
sideways at 360px. Keyboard: **N** opens the add sheet; Escape closes sheets and menus.

Motion: gliding segmented/nav indicators, rolling numbers, FLIP lists that tint new rows accent and
removed rows red, spring sheets and toasts. `prefers-reduced-motion` turns all of it off.

## PWA

`manifest.webmanifest` + icons make it installable ("Add expense" shortcut opens `#/?add=1`). The
service worker (`sw/sw.js`) is registered only in production builds. It precaches the app shell, serves
same-origin requests network-first with a versioned cache (`budget-<version>-<build>`, bumped on every
build), precaching the Latin font subsets, and leaves Supabase requests to the network. Together with the localStorage snapshot,
the app opens and stays readable offline.

## Deploy

`.github/workflows/web.yml` runs on pushes and PRs that touch `web/**`, `docs/fixtures/**` or
`config/**`: `npm ci`, typecheck, `vitest run`, build. On `main` it uploads `web/dist` with
`actions/upload-pages-artifact` and publishes it with `actions/deploy-pages`. One-time setup: repo
**Settings → Pages → Source: GitHub Actions**.

## Sheet view

`#/sheet` (and `#/sheet/:year/:tab`, tab = `summary` or a month number; the last tab is remembered for the
session) rebuilds the original spreadsheet per `docs/SHEET_VIEW.md`. `src/domain/sheet.ts` decides where
things go (rows 3 / 5–13 / 15–18 / 20–22 / 23 with the default categories; ledger blocks in G·K·O, unknown
ledger categories appended to the shortest column; ledger rows by date, undated last, then creation order).
`src/screens/sheetGrid.ts` turns that into a cell matrix wired to the existing actions (budgets, overrides,
transactions with undo, month closed, account balances, ledger entries + settle, "+" = next month); the
numbers all come from the domain calc, and Summary row 22's Difference is the leftover vs plan (§4b).
`Sheet.tsx` renders a real `<table role="grid">` with roving tabindex, arrows/Tab/Enter/F2/Escape, typing
to edit, right-click / ⋮ / Shift+F10 menus, sticky B–E columns while the ledgers scroll, and a bottom glass
tab bar (year switcher, Summary + months, "+"). Below 600px it shows the "needs a wider screen" card.

## Removed: Meals

Meal plans are not part of the product. The web app has no Meals screen, tables or setting; backup import
silently ignores `meal_plans` / `meal_items` (and `days_per_month`) in older files.

## Known limitations

- Offline writes are not queued: while offline a change is reverted with a toast (the data stays
  readable). Android is the local-first client.
- The password-reset and confirmation links need the site URL in Supabase's redirect allow-list.
- Supabase's built-in email sender is rate limited (a few emails per hour); the sign-up screen shows
  the error when it hits the limit.
- Sign-up (including the "check your email" state) and sign-in were checked against the live project, but a full signed-in
  session (live load, realtime, writes, MCP tokens) has not been run end to end yet, because test
  accounts need email confirmation. The store logic is covered by unit tests with a fake backend.
