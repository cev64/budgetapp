# UI Anatomy — shared by web and Android

The two apps must feel like the same product. This file pins down the details that make
them match. Visual language: `docs/FLUID_GLASS_UI.md`. Tokens: `design/tokens.json`.

## Icons
Use **Lucide** icons (ISC license) on both platforms, 1.75px stroke, 20px in nav, 18px inline.
Web: `lucide-react`. Android: the same SVG paths converted to vector drawables / `ImageVector`.

| Use | Lucide name |
|---|---|
| Home | `house` |
| Month | `calendar-days` |
| Year | `chart-column` |
| Net worth | `landmark` |
| Meals | `utensils` |
| Settings | `settings` |
| Add | `plus` |
| Closed month | `lock` / open `lock-open` |
| Sync | `refresh-cw` |
| Prev / next | `chevron-left` / `chevron-right` |
| Ledger category | `list` · Manual category | `pencil` |

## Type scale (Inter unless noted; tabular numerals everywhere)
| Role | Size / weight |
|---|---|
| Screen title (e.g. "OCTOBER 2026") | Barlow Condensed 28 / 700, uppercase, letter-spacing .02em |
| Hero number (leftover) | Inter 34 / 700, tight letter-spacing −.02em |
| Card title | 15 / 650 |
| Body / rows | 14 / 500 |
| Secondary | 13 / 500 `ink-2` |
| Micro-label | 11 / 700 uppercase, letter-spacing .08em, `ink-3` |

## Layout breakpoints (window width, not device)
| Name | Width | Navigation | Panes |
|---|---|---|---|
| Compact (Fold cover / phone / narrow browser) | < 600 | glass bottom bar (5 items), Add = FAB (Android) / round accent button centred above bar (web) | 1 |
| Medium (Fold inner portrait-ish, tablet) | 600–1023 | navigation rail 80px wide, Add at top of rail | 2 where useful |
| Expanded (Fold inner landscape / desktop) | ≥ 1024 | rail (Android) / 220px side nav with labels (web) | 2–3 |

Content max width 1240px, side gutter 16px (compact) / 24px (medium+).

## Top bar (glass)
Sticky, `glassBar`. Left: micro-label (e.g. "BUDGET") above the screen title. Right: sync
status dot (green = synced, pulsing accent = syncing, amber = offline/pending), Settings gear.
Shadow appears only once content scrolls under it.

## Home
1. **Hero card** (`card`): micro-label "LEFTOVER · OCTOBER", hero number (actual leftover),
   under it "of $350 planned" in `ink-2`. A stacked 8px bar: expenses (`ink` 80%), saved (`accent`),
   leftover (`good`) as shares of income.
2. **Tiles row** (2 columns compact, 4 expanded): Income, Expenses, Saved, Net worth. Each: micro-label,
   value, small "of $X" or delta line.
3. **Budgets card**: one row per expense category: name, "$128 of $400", right-aligned remaining
   ("$272 left" in `ink-2` or "$45 over" in `bad`), 6px progress bar under it.
4. **Recent** card: last 8 transactions: item, category pill, date, amount (FLIP-animated list).

## Month
Header: `‹ October 2026 ›` with year/month picker, "Closed" switch with lock icon.
Segmented control (gliding pill): **Budget · Transactions**.
Budget tab: three cards, Income / Expenses / Savings. Row = name (+ small ledger/manual icon),
three right-aligned numeric columns Expected · Actual · Diff (diff coloured). Under each card a
totals row. Then a summary card: Monthly expenses, Saved (incl. match), **Leftover**, each
Expected / Actual / Diff.
Tapping a row → category detail (compact: pushes a screen; expanded: right pane).
Category detail: big Actual vs Expected, editable Expected, Actual field (manual) or ledger sum +
"Override" link, transaction list with add row.

## Add transaction (modal sheet / bottom sheet)
Fields, in order: **Amount** (large numeric input, autofocus, `inputmode=decimal`), **Category**
(pick grid of ledger categories first, then others), **Item**, **Date** (defaults today),
**Month** (defaults to the month being viewed, else current), Note (collapsed). Primary "Add" button
full width. Negative amount allowed via a "Refund" toggle. On save: toast "Added $12 to Food",
haptic on Android, the new row tints accent where it lands.

## Year
Year switcher, then a card per group with Expected · Actual · Diff, totals, then a highlight
card: Annualized savings (both columns), % of net, % of gross. A bar chart (12 slots; months that
don't exist are empty): bar = expenses actual (projected), thin marker line = expected; open
months drawn with 45% opacity.

## Net worth
Hero: Net worth. Tiles: Super liquid, Investments, Net reconciliations. Accounts card grouped
(micro-label per group); row: name, balance (tap to edit inline); linked accounts show an "auto" pill.
Ledger card: name, amount (green if owed to me, red if I owe), settle checkbox (settled rows fade to .5 and strike through).

## Meals
Segmented: **Plans · Recipes**. A plan card: title + label pill, rows grouped under time labels,
each row: name, kcal, P, F(iber), fat, $; totals row; footer "≈ $284 / month".
Expanded width shows plans side by side (2–4 columns).

## Settings
Grouped list cards: Account, Income & targets, Categories, Recurring, Appearance, Data, About.

## Motion (both platforms use the same curves and durations from tokens.json)
- Press: scale .97 over 160ms ease.
- Tab/segment changes: gliding indicator 450ms ease; content riseIn 420ms.
- Numbers that change: roll/bump (spring). Not on first render.
- Lists: new rows fade+rise and tint accent; removed rows sink and tint red.
- Sheets/dialogs: spring in, exit at 60% duration.
- Reduced motion (web `prefers-reduced-motion`, Android "Remove animations") disables all of it.
