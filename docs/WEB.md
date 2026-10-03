# Web app

The browser version of Budget: `web/`. It replaces the Google Sheet alongside the Android app,
shares its data through Supabase, and follows the same spec (`PRODUCT_SPEC.md`), math
(`docs/DOMAIN_RULES.md`), sync contract (`docs/SYNC.md`) and look (`docs/UI_ANATOMY.md`,
`docs/FLUID_GLASS_UI.md`, `design/tokens.json`).

Live: https://cev64.github.io/budgetapp/ (deployed from `main` by `.github/workflows/web.yml`).

## Stack

Vite + React 19 + TypeScript (strict), `@supabase/supabase-js` v2, `lucide-react` icons,
`react-router` (HashRouter, so GitHub Pages needs no rewrites), `vitest`. Charts are hand-rolled SVG.
Fonts: Inter and Barlow Condensed from Google Fonts. No other runtime dependencies.

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
network, nothing saved, a "Demo data" pill in the top bar. Writes, import and export all work, but
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
  public/                    icon.svg (+ maskable), PNG icons, manifest.webmanifest
  sw/sw.js                   service worker template (vite.config.ts fills version + asset list)
  src/
    main.tsx                 boot: theme, auth redirect handling, render, SW registration (prod only)
    config.ts                Supabase URL/key resolution, app version
    domain/                  pure TS, no React, no I/O
      types.ts, tables.ts    row types, table keys, on_conflict targets, column lists, normalisation
      calc.ts                DOMAIN_RULES §1–§4: per-category values, month totals, projection, year summary
      networth.ts            §5 net worth, super liquid, reconciliations, linked accounts
      meals.ts               §7 plan totals and monthly cost
      newMonth.ts            §6 new-month rows (budgets copied forward, recurring items, day clamped)
      format.ts              §8 money / percent / difference tone, amount parsing
      backup.ts              backup JSON build / parse / merge plan (SYNC.md)
      dates.ts
    data/
      backend.ts             Backend interface; SupabaseBackend (pull/upsert/realtime) and DemoBackend
      store.ts               the client store: snapshot, optimistic writes, merges, cache
      actions.ts             every user-facing write (set budget, create month, CRUD, import …)
      mcp.ts                 MCP connection tokens (Settings → AI assistant)
      supabase.ts, demo.ts, safeStorage.ts
    app/                     Root (auth gate), routes, Shell (nav, top bar, add sheet), session context
    screens/                 Home, Month (+ CategoryDetail), Year, NetWorth, Meals, Settings, Auth,
                             TransactionSheet, McpSection
    ui/                      Fluid Glass kit: motion.ts (tint, glideIndicator, rollText, FLIP), Seg,
                             Sheet + ask(), Toast, Menu, FlipList, Num, controls, theme
    styles/                  tokens.css (light + dark), base.css (guide §5–§7), app.css (layout, screens)
  test/                      vitest suites
```

Screens use `useData()` for `{ ds, calc }` (live rows + a memoised domain calculator) and
`useActions()` for writes. All numbers come from `src/domain`, which is tested against
`docs/fixtures/expected.json` (every month, year, net-worth and meal-plan number).

### Store, sync and realtime

`Store` (`src/data/store.ts`) holds every table keyed by row identity (id, or the natural key for
`months`, `budgets`, `settings`). Tombstones stay in the store so later merges are correct; the UI
only sees live rows.

- **Load:** on start the store paints the cached snapshot from localStorage
  (`budget.cache.<user id>`), then pulls every table in full from Supabase, 1000 rows per page,
  ordered by `updated_at` plus the key.
- **Realtime:** one channel subscribes to `postgres_changes` on all ten tables with the filter
  `user_id=eq.<uid>`. Each change is merged as it arrives, so edits on the phone show up live.
- **Catch-up:** on window focus / tab visible, on the browser `online` event and whenever the realtime
  channel re-subscribes, the store pulls rows with `updated_at > cursor − 10 s` per table (SYNC.md rule 3).
- **Merge rules:** a remote row never replaces a row with a local write in flight, and an older
  `updated_at` never replaces a newer one.
- **Writes are optimistic:** the store updates local state first, then upserts with the SYNC.md
  `on_conflict` keys, always sending `user_id` and never `updated_at`. The returned row (with the
  server's `updated_at`) replaces the local one, unless the row was edited again meanwhile. On
  error the rows revert and a toast explains why. Deletes are tombstones (`deleted: true`); deleting
  a meal plan tombstones its items; a category with data can only be archived. Ids come from
  `crypto.randomUUID()`.
- **Status dot** in the top bar: green synced, pulsing blue syncing, amber offline. Clicking it
  syncs now (also in Settings → Data).
- **Sign-out** deletes the cached snapshot from the browser.

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

Fluid Glass tokens are CSS variables (`src/styles/tokens.css`). The dark theme follows
`prefers-color-scheme` unless Settings → Appearance sets Light or Dark (`data-theme` on `<html>`,
stored in localStorage). Breakpoints follow UI_ANATOMY: under 600px a glass bottom bar with a round Add
button above it; 600–1023px an 80px rail with Add on top; from 1024px a 220px side nav, an Add button
in the top bar, and multi-pane screens (Month list + category detail, Year tables + chart, Net worth and
Meals side by side). Phone width (412px, Galaxy Fold cover) is a primary target, and nothing scrolls
sideways at 360px. Keyboard: **N** opens the add sheet; Escape closes sheets and menus.

Motion: gliding segmented/nav indicators, rolling numbers, FLIP lists that tint new rows accent and
removed rows red, spring sheets and toasts. `prefers-reduced-motion` turns all of it off.

## PWA

`manifest.webmanifest` + icons make it installable ("Add expense" shortcut opens `#/?add=1`). The
service worker (`sw/sw.js`) is registered only in production builds. It precaches the app shell, serves
same-origin requests network-first with a versioned cache (`budget-<version>-<build>`, bumped on every
build), and leaves Supabase and font requests to the network. Together with the localStorage snapshot,
the app opens and stays readable offline.

## Deploy

`.github/workflows/web.yml` runs on pushes and PRs that touch `web/**`, `docs/fixtures/**` or
`config/**`: `npm ci`, typecheck, `vitest run`, build. On `main` it uploads `web/dist` with
`actions/upload-pages-artifact` and publishes it with `actions/deploy-pages`. One-time setup: repo
**Settings → Pages → Source: GitHub Actions**.

## Known limitations

- Offline writes are not queued: while offline a change is reverted with a toast (the data stays
  readable). Android is the local-first client.
- The password-reset and confirmation links need the site URL in Supabase's redirect allow-list.
- Supabase's built-in email sender is rate limited (a few emails per hour); the sign-up screen shows
  the error when it hits the limit.
- Sign-up (including the "check your email" state) and sign-in were checked against the live project, but a full signed-in
  session (live load, realtime, writes, MCP tokens) has not been run end to end yet, because test
  accounts need email confirmation. The store logic is covered by unit tests with a fake backend.
