# Budget — Product Spec

## Purpose

Replace the "2026 Budget" Google Sheet with a personal budgeting app that runs natively on
a Samsung Galaxy Z Fold 8 Ultra (Android) and in the browser (web), with the same data
synced between them through Supabase. Both apps look and feel the same: the **Fluid Glass**
design language (`docs/FLUID_GLASS_UI.md`) with shared tokens (`design/tokens.json`).

All math follows `docs/DOMAIN_RULES.md`. Sync and auth follow `docs/SYNC.md`.

## Primary workflow

1. Spend money → open app (or widget/shortcut) → **Add** → pick category → amount → item → save (< 5 s).
2. Bills arrive → open the month → type the actual for Rent / Utilities / Car Ins / Phone Bill.
3. Paycheck lands → add it under Paychecks (or type the month's actual).
4. Investments go in → type the actual Roth / 401k / Brokerage / HSA contribution.
5. Month ends → mark the month **Closed**. The year summary now uses actuals for it.
6. Occasionally → update account balances (Net worth) and IOUs (Ledger).
7. Start the next month → budgets copy forward, subscriptions are pre-filled.

## Screens (both apps, same names, same order)

Navigation destinations: **Home · Month · Year · Net worth · Sheet**, plus **Settings**
(gear in the top bar). Global **Add** action (FAB on Android, primary button in the top bar
on web, plus keyboard shortcut `N`).

### Home (dashboard for the current month; falls back to the latest month)
- Hero: **Leftover this month**, actual vs expected, with a stacked progress bar (spent / saved / left).
- Tiles: Income (actual / expected), Expenses (actual / expected), Saved (actual / expected), Net worth (with 30-day change and a tiny sparkline).
- "Budgets" list: every expense category with a progress bar (actual / expected) and remaining amount.
- Recent transactions (last 8) across categories, tap to edit.
- Banner when the current calendar month doesn't exist yet: "Start October" button.

### Month
- Month picker: year + month segmented control / wheel; arrows for previous/next.
- **Closed** toggle (shows a "Closed: summary uses actuals" pill).
- Three sections: **Income**, **Expenses**, **Savings**, each row: category, Expected, Actual, Difference
  (coloured per rules), progress bar. Totals rows: Monthly Expenses, Saved (with match), Leftover.
- Tapping a row opens the **category detail**: editable Expected; Actual (manual categories:
  editable field; ledger categories: the ledger sum with an optional override); the transaction
  list for that category in this month (date, item, amount; add / edit / delete; negative amounts allowed).
- "All transactions" tab: every transaction this month, filter by category, sorted by date.

### Year
- **Hero (top of the page): Leftover vs plan** (DOMAIN_RULES §4b). Big signed number in good/bad
  ("−$45 · behind plan"), then Projected leftover and Planned leftover, a progress bar
  (projected / planned) and a collapsed **"By month" dropdown** that expands to list each month's vs-plan amount
  (closed months: amount, open months: "—").
- Year picker. Table: category, Expected, Actual (projected), Difference, grouped like the sheet.
- Totals: Expenses, Saved, Leftover.
- **Savings rate** card, below the table (no longer the headline): annualized savings, % of net and % of gross income (both columns).
- Chart: per-month bars of expenses vs budget plus leftover line (open months shown lighter as projected).
- Months list with closed/open status and each month's leftover.
### Net worth
- Net worth total, Super liquid assets, Net reconciliations, each with change vs 30 days ago.
- **History chart** (DOMAIN_RULES §5b): line/area chart of net worth over time with a segmented range
  1M · 3M · 6M · 1Y · All; scrub/hover shows date + value; change for the selected range (green/red).
  Toggle to overlay Super liquid. Tapping an account shows its own balance history (sparkline/chart).
- Accounts grouped (Cash, Investments, Assets, Debts); edit balance inline; linked accounts
  (401k, HSA) show "auto · base + contributions" and are not directly editable (edit base instead).
- Ledger (IOUs): name, amount (+ owed to me / − I owe), settle toggle, add/edit/delete.

### Sheet
- The spreadsheet, rebuilt: Summary + month tabs laid out exactly like the original Google Sheet,
  editable where the sheet had typed values. Needs ≥ 600dp: on the folded phone it shows an
  "Unfold to see the spreadsheet" prompt and switches to the sheet in place when unfolded.
  Full spec: `docs/SHEET_VIEW.md`.

### Settings
- Account: email, sign out.
- Income: annual net, annual gross (for savings %).
- Categories: add, rename, kind, tracking, match multiplier, reorder, archive.
- Recurring items (subscriptions): add/edit, day of month, active.
- Appearance: System / Light / Dark. Android also has: dynamic color (off by default so it matches web).
- Data: Export backup (JSON), Import backup (JSON, merge), last sync time, "Sync now".
- About: version.

## Android specifics (see AGENT_INSTRUCTIONS.md)
- Package / applicationId: `com.personal.budget` (permanent).
- Compact (cover screen): bottom navigation, single pane, FAB.
- Expanded (inner screen): navigation rail; Month = category list + category detail side by side;
  Home = 2–3 column dashboard; Year = table + chart side by side; Net worth = chart + accounts side by side.
- Fold continuity: selected month, selected category, open sheet and its text survive fold/unfold.
- Glance widget "Budget": leftover this month, top 3 categories remaining, **Add** button deep-linking
  to the add-transaction sheet. Sizes small / medium / large. Updated after every local change and sync.
- App shortcut "Add expense" (static shortcut) and deep link `budget://add`.
- Haptics on save, on closing a month, on settle toggle.
- No runtime permissions beyond INTERNET / ACCESS_NETWORK_STATE (and POST_NOTIFICATIONS is not needed in v1).
- Local-first: Room is the source of truth for the UI; sync per `docs/SYNC.md`.

## Web specifics
- React + TypeScript + Vite, `@supabase/supabase-js`. Hosted on GitHub Pages.
- Installable PWA (manifest + network-first service worker), works great at phone width.
- Realtime: changes from the phone appear live.
- Wide layout ≥ 1024 px: side navigation + multi-pane like the Fold inner screen.

## Data, backup, privacy
- All data lives in Supabase under RLS (only the owner can read/write). Android keeps a full
  local copy in Room.
- Backup/export JSON format in `docs/SYNC.md`; both apps import/export it.
- The repository is public: never commit personal data, keys other than the publishable key, or the keystore.

## Not ported from the sheet (deliberately)
- `Sheet6` (freelance client income scratch calculation).
- The entire `Food` tab (meal plans, recipes, rent-split scratch numbers, sleep log): out of scope for this app.
