import type { Category, CategoryKind, Transaction } from './types';

// docs/SHEET_VIEW.md: where every block of the original spreadsheet goes. Pure layout, no math.

/** Rows of the left block (sheet columns B–E). `row` is the spreadsheet row number. */
export type LeftRow =
  | { row: number; type: 'header' }
  | { row: number; type: 'category'; category: Category }
  | { row: number; type: 'spacer' }
  | { row: number; type: 'total'; total: 'expenses' | 'saved' | 'leftover' }
  | { row: number; type: 'closed' }
  | { row: number; type: 'annualized' | 'pctNet' | 'pctGross' };

const GROUPS: CategoryKind[] = ['income', 'expense', 'savings'];

/**
 * Header [2]; income categories [3]; blank; expense categories [5–13]; blank; savings [15–18]; blank;
 * totals [20–22]; then the Closed row [23] (month tab) or blank + savings-rate rows [24–26] (Summary).
 * With the 14 default categories this lands exactly on the sheet's rows; extra categories push rows down.
 * `categories` must already be in display order and filtered (archived ones only when they hold data).
 */
export function leftRows(categories: readonly Category[], tab: 'month' | 'summary'): LeftRow[] {
  const rows: LeftRow[] = [];
  let r = 2;
  rows.push({ row: r++, type: 'header' });
  for (const kind of GROUPS) {
    for (const c of categories.filter((x) => x.kind === kind)) rows.push({ row: r++, type: 'category', category: c });
    rows.push({ row: r++, type: 'spacer' });
  }
  for (const total of ['expenses', 'saved', 'leftover'] as const) rows.push({ row: r++, type: 'total', total });
  if (tab === 'month') rows.push({ row: r++, type: 'closed' });
  else {
    rows.push({ row: r++, type: 'spacer' });
    for (const type of ['annualized', 'pctNet', 'pctGross'] as const) rows.push({ row: r++, type });
  }
  return rows;
}

/** Ledger columns: G H I, K L M, O P Q, each a stack of category blocks (top to bottom). */
export const LEDGER_LAYOUT: string[][] = [
  ['Paychecks', 'Subscriptions', 'Misc'],
  ['Food', 'Gas'],
  ['Fun'],
];

/** Rows a ledger block occupies: title + header + one per transaction + the add row. */
export const blockHeight = (transactionCount: number): number => 2 + transactionCount + 1;

/**
 * Assigns ledger categories to the three ledger columns. Known names (case-insensitive) go where the
 * sheet had them; any other ledger category is appended to the bottom of the currently shortest column
 * (ties: leftmost), in display order. Column height counts one blank row between stacked blocks.
 */
export function ledgerColumns(categories: readonly Category[], countFor: (categoryId: string) => number): Category[][] {
  const ledger = categories.filter((c) => c.tracking === 'ledger');
  const byName = new Map(ledger.map((c) => [c.name.trim().toLowerCase(), c]));
  const used = new Set<string>();
  const cols: Category[][] = LEDGER_LAYOUT.map((names) =>
    names.flatMap((n) => {
      const c = byName.get(n.toLowerCase());
      if (!c) return [];
      used.add(c.id);
      return [c];
    }),
  );
  const height = (col: Category[]) => col.reduce((h, c, i) => h + blockHeight(countFor(c.id)) + (i > 0 ? 1 : 0), 0);
  for (const c of ledger) {
    if (used.has(c.id)) continue;
    let best = 0;
    cols.forEach((col, i) => {
      if (height(col) < height(cols[best]!)) best = i;
    });
    cols[best]!.push(c);
  }
  return cols;
}

/** Ledger rows: by date ascending, undated last, then creation order (unsynced rows are newest). */
export function sortLedger(transactions: readonly Transaction[]): Transaction[] {
  return transactions.slice().sort((a, b) => {
    if (a.date && b.date && a.date !== b.date) return a.date < b.date ? -1 : 1;
    if (!a.date !== !b.date) return a.date ? -1 : 1;
    const ca = a.created_at ?? '9999';
    const cb = b.created_at ?? '9999';
    if (ca !== cb) return ca < cb ? -1 : 1;
    return a.id.localeCompare(b.id);
  });
}
