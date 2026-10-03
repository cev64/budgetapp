# Budget

A personal budgeting app that replaces the "2026 Budget" Google Sheet, as one product on three
surfaces sharing one Supabase backend:

| Surface | What | Docs |
|---|---|---|
| **Android** | Native Kotlin / Jetpack Compose app, tuned for the Samsung Galaxy Z Fold 8 Ultra (cover + inner screen) | [docs/ANDROID.md](docs/ANDROID.md) |
| **Web** | React + Vite PWA, installable, live-updating | [docs/WEB.md](docs/WEB.md) |
| **AI assistant** | Remote MCP server so Claude (phone, web, desktop, Claude Code) can read and edit the budget | [docs/MCP.md](docs/MCP.md) |

All three use the same math, verified number-for-number against the original spreadsheet
([docs/DOMAIN_RULES.md](docs/DOMAIN_RULES.md)), and the same design language (Fluid Glass + the brand kit).

## What the app does

- **Months**: Expected vs Actual per category, grouped as Income / Expenses / Savings, with monthly
  expenses, saved (401k counts double for the employer match) and leftover. Mark a month **Closed** to
  make the year summary use its actuals.
- **Categories** are either *ledger* (actual = sum of transactions: Paychecks, Subscriptions, Food, Fun,
  Gas, Misc) or *manual* (type the actual: Rent, Car Ins, Utilities, Phone Bill, Roth, 401k, Brokerage, HSA).
- **New month** copies budgets forward and pre-fills recurring items (subscriptions).
- **Year summary**: totals, annualized savings, % of net and gross income, monthly chart.
- **Net worth**: accounts (401k / HSA computed from contributions), IOU ledger, super-liquid assets,
  and **net worth history** (a snapshot every night plus whenever balances change) with 1M–All charts.
- **Sync** between devices through Supabase with email/password auth. Android works fully offline and
  syncs when it can; the web updates live.
- **Backup**: JSON export/import (merge, never wipe) in both apps.
- **Home-screen widget** (Android) with quick Add, an "Add expense" shortcut and `budget://add`.

## Primary target device

Samsung Galaxy Z Fold 8 Ultra: single-pane bottom-bar layout on the cover screen, navigation rail and
list/detail panes on the inner screen, with state preserved across fold/unfold. Layout decisions use
window size classes, never the device model.

## Repository layout

```
android/                 native app (see docs/ANDROID.md)
web/                     web app (see docs/WEB.md)
supabase/migrations/     database schema (Postgres, RLS on every table)
supabase/functions/      budget-mcp Edge Function + shared budget math (see docs/MCP.md)
config/supabase.json     public client config (URL + publishable key; RLS protects data)
design/                  tokens.json (shared design tokens) and brand/ (logo, icons, brand guide)
docs/                    product rules, sync contract, UI anatomy, fixtures
tools/                   spreadsheet → backup converter, reference calculator (Python)
PRODUCT_SPEC.md          screens and workflows
AGENT_INSTRUCTIONS.md    standing instructions for AI agents working on the Android app
```

Key specs: [PRODUCT_SPEC.md](PRODUCT_SPEC.md) · [docs/DOMAIN_RULES.md](docs/DOMAIN_RULES.md) ·
[docs/SYNC.md](docs/SYNC.md) · [docs/UI_ANATOMY.md](docs/UI_ANATOMY.md) ·
[design/brand/brand-guide.md](design/brand/brand-guide.md)

## Backend (Supabase)

- Project `Budget` (`sygxozspiszqfawkmpzx`, us-east-1). Tables: settings, categories, months, budgets,
  transactions, recurring_items, accounts, ledger_entries, net_worth_snapshots, mcp_tokens.
- Every table has row-level security (owner only). Rows are never hard-deleted by clients (tombstones),
  `updated_at` is server-set, and pulls use per-table cursors ([docs/SYNC.md](docs/SYNC.md)).
- Sign-up automatically creates settings and the 14 default categories.
- `pg_cron` job `net-worth-daily` snapshots net worth nightly.
- Apply schema changes as new files in `supabase/migrations/` (never edit applied ones).

## How to build locally

- **Web**: `cd web && npm ci && npm run dev` (demo mode needs no backend: `#/?demo=1`). Tests: `npm test`.
- **Android**: Android SDK + JDK 17, then `cd android && ./gradlew testDebugUnitTest assembleDebug`.
  See [docs/ANDROID.md](docs/ANDROID.md#building-locally).
- **MCP server**: Deno 2, `cd supabase/functions/budget-mcp && deno task test`.
- **Reference math**: `python3 tools/reference_calc.py docs/fixtures/sample-backup.json`.

## How to generate an APK

Push to GitHub: the **Android** workflow builds, tests and uploads an APK artifact on every change to
`android/`. For a release, bump `android/version.properties`, push a tag `v<version>` (e.g. `v1.0.0`):
the **Android release** workflow builds a signed APK, verifies the signature and attaches
`budget-v<version>.apk` to a GitHub Release. Download it on the phone and open it to install/update.

## How release signing works

One permanent release keystore (`budget-release.jks`, alias `budget`, PKCS12) signs every release. It is
**not** in this repository. GitHub Actions rebuilds it from repository secrets for the duration of a run:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | base64 of `budget-release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | `budget` |
| `ANDROID_KEY_PASSWORD` | key password |

Certificate SHA-256: `4E:22:28:1C:B2:F8:F9:C5:24:2C:18:CB:00:40:CA:22:83:4F:C4:8F:42:8E:78:0E:2D:31:7A:C8:E1:22:8F:28`.
Back up the keystore and passwords (password manager + a second location). Losing them means future
APKs can't update the installed app. Restoring on a new machine: [docs/ANDROID.md](docs/ANDROID.md#restoring-signing-on-a-new-machine).
Without the secrets, builds fall back to the debug key and are named `…-UNSIGNED-DEBUGKEY.apk`;
those **cannot** update a release install.

## How upgrade-in-place works

An APK updates the installed app only with the same applicationId (`com.personal.budget`, permanent),
the same signing certificate, and a higher versionCode. If Android refuses an update, check those three
first. **Do not uninstall** (that deletes local, possibly unsynced, data).

## Versioning strategy

- `versionName` lives in `android/version.properties` (e.g. `1.0.0`), bumped by hand per release.
- `versionCode` = number of commits (`git rev-list --count HEAD`), so it only grows on `main`.
  **Merge pull requests with a merge commit (not squash/rebase)** so the count never goes down, and
  install only builds from `main` or tags over your primary app.

## Data migration strategy

Server: additive SQL migrations in `supabase/migrations/`. Android: Room DB version 1 with exported
schemas; every schema change ships a real `Migration` (destructive migration is never enabled) and a
migration test. DataStore keys are never renamed. Details: [docs/ANDROID.md](docs/ANDROID.md#data-migration-strategy).

## Backup / export

Settings → Data → **Export** (JSON, same format in both apps, [docs/SYNC.md](docs/SYNC.md#backup-file-format-both-apps-importexport-the-same-json)).
**Import** merges by category name and natural keys and never deletes. Your original spreadsheet can be
converted with `python3 tools/xlsx_to_backup.py "2026 Budget.xlsx" budget-import.json`
(the output holds personal data: never commit it; `*.xlsx` and `budget-import.json` are git-ignored).

## Required permissions (Android)

`INTERNET`, `ACCESS_NETWORK_STATE` (sync). WorkManager adds `WAKE_LOCK` and `RECEIVE_BOOT_COMPLETED`
for the 6-hourly background sync. No runtime permission prompts.

## External APIs

Only Supabase (Auth, PostgREST, Realtime, Edge Functions). No secret keys ship in any client: the
publishable key is public by design, and row-level security restricts every row to its owner. The MCP
server uses the service role server-side only and scopes every query to the token's owner.

## Known limitations

- Android glass bars are translucent, not blurred (no backdrop blur in Compose).
- Android syncs on events and a schedule; it has no realtime websocket (the web does).
- Net worth history starts on the first snapshot (no back-fill from the spreadsheet).
- Not ported from the sheet: the `Food` tab (meal plans, recipes, rent split, sleep log) and `Sheet6`.

## Planned features

Optional biometric lock, over-budget notifications, quick-add from the widget without opening the app,
realtime updates on Android.
