# Sheet view: the spreadsheet, rebuilt

An **additional** view (nothing else changes) that lays the budget out exactly where the user's eyes
expect it from the original Google Sheet (`2026 Budget.xlsx`), but rendered natively and nicer.
All numbers come from the same domain code (DOMAIN_RULES); edits go through the same store/actions as
the rest of the app, so they sync like any other change.

## Navigation and availability

- New destination **Sheet**, Lucide icon `sheet` (fallback `table-2`), placed **last**:
  Home · Month · Year · Net worth · **Sheet**. Present in the compact bottom bar (5 items), the rail and
  the web side nav.
- **Minimum width 600dp/px.** The Sheet needs the inner screen.
  - **Android, compact width (Fold cover screen):** show the **Unfold prompt** instead of the sheet:
    a centred card with a simple unfold illustration (two rounded rectangles opening, drawn with
    Canvas/vector; animate the "opening" once on appear, respecting reduced motion), title
    **"Unfold to see the spreadsheet"**, body "The sheet view needs the inner screen. Open your phone
    and it will appear here.", and a secondary button **"Go to Year"**. The bottom bar stays visible.
    When the window becomes ≥ 600dp while this destination is selected (the user unfolds), the sheet
    replaces the prompt **in place** (no navigation, crossfade). Folding again returns to the prompt
    without losing the selected tab or scroll position.
  - **Web, width < 600px:** same card, but the title is **"Spreadsheet view needs a wider screen"** and
    the body is "Open Budget on a computer or tablet, or unfold your phone, to see the sheet view."
  - Width 600–(needed width): the grid scrolls horizontally inside the page; the left block (labels +
    Expected/Actual/Difference) stays pinned while ledgers scroll. Never shrink type to fit.

## Look

- Rendered as a real grid: white `card` surface, 1px `line` grid rules, header rows on `surface`
  with micro-label text (12/600 uppercase, ink-3), cell text **14/20** (`secondary` token, ink) with
  tabular + lining numerals, money right-aligned, cell padding 6×10, row height 36. Block titles
  (e.g. "Paychecks", "Food") are 15/600 ink on the block's top row, with the category's palette symbol.
- **Editable cells** (where the sheet had typed values): normal ink on `bg`, hover `surface`
  (web, `@media(hover:hover)`), focus ring 2px `focusRing`, tap/click to edit in place
  (numeric keyboard for money/date pickers for dates), Enter/Tab commits and moves down/right,
  Escape cancels. **Computed cells** (the sheet's formulas): `surface` fill, ink-2 text, not editable,
  with a tiny ƒ hint on hover/long-press ("Calculated").
- Difference cells coloured per DOMAIN_RULES §8 (good/bad, zero ink-3). Totals rows weight 600 with a
  2px top rule in `line2`.
- Money uses the §8 formatter (whole dollars at ≥ $1,000). Empty manual actuals show blank, not $0.
- Tabs along the **bottom of the grid**, Google-Sheets style, inside a glass bar (radius 24 per tokens):
  **Summary** first, then every existing month of the selected year in chronological order
  (e.g. July … December), then a "+" tab that creates the next month (DOMAIN_RULES §6).
  A year switcher sits at the left end of the tab bar. The selected tab uses the gliding pill.
  Selected tab survives fold/unfold and app restarts within a session.

## Month tab (mirrors each month sheet, columns B–Q)

Left block, sheet columns **B C D E**, sheet rows in brackets:

| Row | B (label) | C Expected | D Actual | E Difference |
|---|---|---|---|---|
| [2] header | **{Month name}** (e.g. "October") | EXPECTED | ACTUAL | DIFFERENCE |
| [3] | Paychecks (income categories, in sort order) | editable | ledger sum or editable override (see below) | computed |
| [4] | *(blank spacer row)* | | | |
| [5–13] | Rent, Subscriptions, Food, Fun, Gas, Misc, Car Ins, Utilities, Phone Bill (expense categories, sort order) | editable | see below | computed |
| [14] | *(blank spacer row)* | | | |
| [15–18] | Roth, 401k, Taxable Brokerage, HSA (savings categories, sort order) | editable | editable | computed |
| [19] | *(blank spacer row)* | | | |
| [20] | **Monthly Expenses** | computed | computed | computed |
| [21] | **Saved (Roth, 401k, Brokerage)** (incl. match) | computed | computed | computed |
| [22] | **Leftover** | computed | computed | computed |
| [23] | **Closed** checkbox in column C (the sheet's C23), label "Month closed" in B | | | |

Actual cell rules: manual categories → editable typed actual (blank = null). Ledger categories → shows
the ledger sum as a computed cell; long-press / right-click / ⋯ menu offers **"Override actual"**
(then it becomes an editable cell with a small "override" marker and "Clear override" in the same menu).
Archived categories appear only if they have data that month.

Ledger blocks to the right, each block = title row, header row (DATE · ITEM · AMOUNT), one row per
transaction (sorted by date ascending, undated last, then created order), then an **add row** (empty
editable cells; typing in any cell creates the transaction), with one blank row between stacked blocks:

| Sheet columns | Blocks, top to bottom |
|---|---|
| **G H I** | Paychecks [rows 2–5] → Subscriptions [7–13] → Misc [15–29] |
| **K L M** | Food [2–22] → Gas [24–29] |
| **O P Q** | Fun [2–29] |

There is one empty spacer column between B–E and G, between I and K, and between M and O (like the
sheet's columns F, J and N). Ledger categories not in this map (user-created) are appended as new blocks
at the bottom of the shortest column. Block heights grow with their transactions (no fixed row limits).
Ledger cells: date (editable, date picker, optional), item (editable text), amount (editable, negative
allowed). Row ⋯ menu / right-click: **Delete** (tombstone, with undo toast) and "Move to month…".
Below the Food block's last row, no per-block totals are shown (the sheet had none; the totals are in
column D of the left block).

## Summary tab (mirrors the Summary sheet, columns B–K)

Left block **B C D E**, for the selected year (DOMAIN_RULES §4, Actual = projected):

| Row | B | C Expected | D Actual | E Difference |
|---|---|---|---|---|
| [2] header | **{Year}** | EXPECTED | ACTUAL | DIFFERENCE |
| [3] | Paychecks | computed | computed | computed |
| [4] | *(blank)* | | | |
| [5–13] | expense categories | computed | computed | computed |
| [14] | *(blank)* | | | |
| [15–18] | savings categories | computed | computed | computed |
| [19] | *(blank)* | | | |
| [20] | **Expenses** | computed | computed | computed |
| [21] | **Saved (Roth, 401k, Brokerage)** | computed | computed | computed |
| [22] | **Leftover** | computed | computed | computed (this is the leftover vs plan, DOMAIN_RULES §4b) |
| [23] | *(blank)* | | | |
| [24] | Annualized Savings | computed | computed | |
| [25] | Percent of Net Income | computed % | computed % | |
| [26] | Percent of Gross Income | computed % | computed % | |

Middle block **G H**: header [2] **{Year}** / "Net worth" micro-label, then one row per non-archived account
in sort order [3–13] (name, balance; plain accounts editable, linked accounts computed with an "auto"
tooltip), then **Net Worth** [14→15] computed (bold), blank, **Super Liquid Assets** [16→17] computed.

Right block **J K**: header [2] **Ledger**, one row per *unsettled* ledger entry [3–13] (name editable,
amount editable, ⋯ menu: Settle, Delete), an add row, then **Net Reconciliations** [14→15] computed (bold).

Spacer columns F and I between the blocks, like the sheet.

## Behaviour

- Same data, same rules, same sync: an edit here appears instantly on the other screens and devices.
- Keyboard (web and hardware keyboards on Android): arrow keys move the selected cell; Enter edits /
  commits; Tab/Shift-Tab move right/left; typing a character on a selected editable cell starts editing.
- No formulas are user-editable; the app's calculations are the formulas.
- Light and dark themes per tokens.
