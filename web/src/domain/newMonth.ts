import type { Budget, Dataset, Month, Transaction, YM } from './types';
import { addMonths, clampedDate, compareYm } from './dates';
import { live } from './calc';

// DOMAIN_RULES §6: creating a month.

export interface NewMonthRows {
  month: Month;
  budgets: Budget[];
  transactions: Transaction[];
}

/**
 * Rows to write for a new month (Y, M), or null when the month already exists.
 * - every non-archived category copies `expected` from the most recent earlier month
 *   that has a budget row for it (categories with nothing to copy get no row: expected stays null);
 * - every active recurring item becomes a transaction dated on its day, clamped to the month length.
 */
export function planNewMonth(ds: Dataset, ym: YM, newId: () => string): NewMonthRows | null {
  const { year, month } = ym;
  if (live(ds.months).some((m) => m.year === year && m.month === month)) return null;

  const categories = live(ds.categories).filter((c) => !c.archived);
  const activeCategoryIds = new Set(live(ds.categories).map((c) => c.id));

  const latestEarlier = new Map<string, Budget>();
  for (const b of live(ds.budgets)) {
    if (compareYm(b, ym) >= 0) continue;
    const prev = latestEarlier.get(b.category_id);
    if (!prev || compareYm(b, prev) > 0) latestEarlier.set(b.category_id, b);
  }

  const budgets: Budget[] = [];
  for (const c of categories) {
    const expected = latestEarlier.get(c.id)?.expected ?? null;
    if (expected == null) continue;
    budgets.push({ year, month, category_id: c.id, expected, actual: null, deleted: false });
  }

  const transactions: Transaction[] = live(ds.recurring_items)
    .filter((r) => r.active && activeCategoryIds.has(r.category_id))
    .sort((a, b) => a.sort_order - b.sort_order)
    .map((r) => ({
      id: newId(),
      year,
      month,
      category_id: r.category_id,
      date: r.day_of_month == null ? null : clampedDate(year, month, r.day_of_month),
      item: r.item,
      amount: r.amount,
      note: null,
      deleted: false,
    }));

  return { month: { year, month, closed: false, note: null, deleted: false }, budgets, transactions };
}

/**
 * Which month the app should offer to start: the current calendar month when it doesn't exist yet,
 * otherwise the month after it when the latest month is the current one. Null when nothing to offer.
 */
export function suggestedMonth(months: readonly Month[], today: YM): YM | null {
  const existing = live(months);
  if (!existing.some((m) => m.year === today.year && m.month === today.month)) return today;
  const latest = existing.reduce<YM | null>((acc, m) => (!acc || compareYm(m, acc) > 0 ? m : acc), null);
  if (latest && compareYm(latest, today) <= 0) return addMonths(today, 1);
  return null;
}
