# Domain Rules — the math, exactly as the 2026 Budget spreadsheet does it

Both apps (web + Android) MUST implement these rules identically. They are a faithful
port of the Google Sheet `2026 Budget.xlsx`. The reference cell for each rule is given
so it can be checked against the sheet. `docs/fixtures/` contains a synthetic test
fixture with the expected outputs; both apps' unit tests run against it.

All money is a decimal number (no rounding in storage). Round only for display.

## Entities (see `supabase/migrations/*_init.sql` for columns)

| Entity | Sheet equivalent |
|---|---|
| `categories` (kind `income` / `expense` / `savings`, tracking `ledger` / `manual`) | Row labels B3..B18 on each month tab |
| `months` (year, month, `closed`) | One month tab; `closed` = checkbox C23 |
| `budgets` (year, month, category → `expected`, `actual`) | Columns C (Expected) and D (Actual, when typed in by hand) |
| `transactions` (year, month, category, date?, item, amount) | The small Date/Item/Amount tables (Paychecks, Subscriptions, Misc, Food, Fun, Gas) |
| `accounts` | Summary G3:H13 (Net worth) |
| `ledger_entries` | Summary J3:K13 ("Ledger", net reconciliations) |
| `meal_plans` / `meal_items` | The `Food` tab |
| `recurring_items` | The subscription rows pre-filled into future months |
| `settings` | Hard-coded 68000 / 85000 in Summary C25:D26, 30.5 in Food!G28 |

Default categories (seeded server-side on sign-up), in sort order:

| Name | kind | tracking | match_multiplier |
|---|---|---|---|
| Paychecks | income | ledger | 1 |
| Rent | expense | manual | 1 |
| Subscriptions | expense | ledger | 1 |
| Food | expense | ledger | 1 |
| Fun | expense | ledger | 1 |
| Gas | expense | ledger | 1 |
| Misc | expense | ledger | 1 |
| Car Ins | expense | manual | 1 |
| Utilities | expense | manual | 1 |
| Phone Bill | expense | manual | 1 |
| Roth | savings | manual | 1 |
| 401k | savings | manual | **2** (employer match) |
| Taxable Brokerage | savings | manual | 1 |
| HSA | savings | manual | 1 |

Users can add, rename, reorder and archive categories. Archived categories are hidden
from new months but still count wherever they have data.

Rows with `deleted = true` are ignored everywhere.

## 1. Per category, per month

```
expected(m, c)  = budget(m, c).expected ?? 0
ledgerSum(m, c) = Σ transactions(m, c).amount              (negative amounts allowed = refunds)
actual(m, c)    = budget(m, c).actual                       if not null   (manual entry / override)
                = ledgerSum(m, c)                           if c.tracking == 'ledger'
                = null                                      otherwise      (shown blank, counts as 0)
difference(m,c) = (actual ?? 0) − expected                  (sheet column E: =D−C)
```

`ledger` categories normally show the ledger sum. The user may still type an override
(July Paychecks: typed 5275 while its two checks summed to 5266). Clearing the override
returns to the ledger sum. When an override is active, the UI shows a small "manual" marker.

## 2. Month totals (sheet rows 20–22)

```
income(m)        = Σ over income categories
expenses(m)      = Σ over expense categories                         (C20 / D20)
saved(m)         = Σ over savings categories of value × match_multiplier   (C21 = Roth + 401k*2 + Brokerage + HSA)
contributions(m) = Σ over savings categories of value                (no multiplier)
leftover(m)      = income − expenses − contributions                 (C22 = C3 − C20 − C21 + C16)
```

Compute each for the **expected** column (using expected) and the **actual** column (using
`actual ?? 0`). Differences are actual − expected.

Note `leftover = income − expenses − saved + 401k` in the sheet is algebraically identical
to the formula above when 401k has multiplier 2: the match counts as *saved* but never
came out of the paycheck.

Example (July 2026, expected): 5275 − 2900.67 − (625 + 215 + 400 + 75) = **1059.33** ✓

## 3. Projected value — used by the Year Summary (Summary column D)

```
projected(m, c) = m.closed && (actual(m,c) ?? 0) != 0  ?  actual(m, c)  :  expected(m, c)
```

That is: an open month contributes its *budget*; a closed month contributes what actually
happened, falling back to the budget for any line that has no actual. (Sheet:
`IF(Month!$C$23, IF(N(D)=0, C, D), C)`.)

## 4. Year summary (Summary B2:E26), for a chosen year Y

Let `M` = the non-deleted months that exist in year Y, `n = |M|`.

```
yearExpected(c)   = Σ_{m∈M} expected(m, c)
yearActual(c)     = Σ_{m∈M} projected(m, c)          ("Actual" column on the summary = projected)
yearDiff(c)       = yearActual(c) − yearExpected(c)

expenses, saved, contributions, leftover: same formulas as §2, applied to the yearly
per-category values (Expected column uses yearExpected, Actual column uses yearActual).

annualizedSavings = (saved + leftover) × 12 / n      (sheet: ×2 because it holds 6 months)
pctNetIncome      = annualizedSavings / settings.net_income
pctGrossIncome    = annualizedSavings / settings.gross_income
```

Both columns get annualized savings and percentages. If n = 0, show empty state.

Reference values from the real sheet (July–Dec 2026): Expected annualized savings 29,712
(43.7 % of net, 35.0 % of gross); Actual 29,622 (43.6 %, 34.8 %).

## 5. Net worth (Summary G2:H17, J2:K15)

```
accountBalance(a) = a.linked_category_id == null
                      ? a.balance
                      : a.base_amount + multiplier(linked category) × Σ_{all months, all years} (budget.actual ?? 0) for that category
netReconciliations = Σ ledger_entries.amount where !settled
netWorth           = Σ accountBalance(non-archived accounts) + netReconciliations
superLiquid        = Σ accountBalance(a) where a.liquid
```

Linked accounts use the raw manually-entered `budget.actual` (not projected, not the ledger
sum). Sheet: `401k = SUM(D16 of every month) * 2`, `HSA = 500 + SUM(D18 of every month)`.
Balances are signed: credit-card debt is entered as a negative number.

Reference: Net worth 22,560, Super liquid 6,748, Net reconciliations −5.

## 5b. Net worth history

Table `net_worth_snapshots`, one row per user per **America/New_York calendar day**
(key `user_id, taken_on`), holding `net_worth`, `super_liquid`, `reconciliations` and an
`accounts` JSON array (`id, name, group, liquid, balance`) exactly as computed by §5.

- The math is done **server-side** by `public.compute_net_worth(user)` (SQL port of §5, verified
  against `docs/fixtures/expected.json`). Clients never compute or write snapshot values themselves.
- **Nightly**: pg_cron job `net-worth-daily` snapshots every user with accounts (source `auto`).
- **On change**: after any write that can change net worth (accounts, ledger_entries, or budgets of a
  category linked to an account), clients call RPC `take_net_worth_snapshot()` (debounced ~2s; Android
  after the push succeeds), which upserts today's row (source `manual`). Today's row therefore always
  reflects the latest balances; earlier days are frozen.
- History is never back-filled: the series starts on the first snapshot.

Derived values for display:

```
changeOver(period) = latest.net_worth − (snapshot on or before (latest.taken_on − period)).net_worth
                     (if no snapshot that old exists, use the oldest snapshot and label it "since <date>")
periods: 1M = 30 days, 3M = 91, 6M = 182, 1Y = 365, All = since first snapshot
perAccountSeries(id) = [(taken_on, accounts[id].balance)] for snapshots containing that account id
```

## 6. Creating a month

"New month" for (Y, M):
1. Create `months(Y, M, closed=false)`.
2. For every non-archived category, copy `expected` from the most recent earlier month that
   has a budget row for it (or leave null). `actual` starts null.
3. For every active `recurring_items` row add a transaction in (Y, M) with
   `date = Y-M-day_of_month` (clamped to the month length) or null if no day.
4. Do nothing if the month already exists.

The app offers "Start {next month}" when the latest month is the current calendar month or
earlier, and lets the user create any month of any year from the month picker.

## 7. Meal plans (Food tab)

```
planTotal.{calories, protein, fiber, fat, cost} = Σ items (null = 0)
monthlyCost = planTotal.cost × settings.days_per_month (30.5)
```

`kind = 'day'` plans are full days of eating (shown with time labels, totals and monthly
cost). `kind = 'recipe'` plans are one dish broken into ingredients (totals, no monthly cost),
`note` holds the steps.

## 8. Display rules (both apps)

- Currency: `$1,234` when the value is a whole number, `$1,234.56` otherwise. Negative as
  `−$153` (true minus sign). Use tabular numerals.
- Percentages: one decimal (`43.7%`).
- Difference colouring for **expense** categories: over budget (diff > 0) = `bad`, under = `good`.
  For **income and savings**: diff ≥ 0 = `good`, < 0 = `bad`. Leftover follows income.
  Zero = neutral `ink-3`.
- Progress bars: actual / expected, capped visually at 100 % with an over-budget tail in `bad`.
- Month names in full ("October"), short where space is tight ("Oct").
