import type { Budget, Category, CategoryKind, Dataset, Month, Settings, Transaction, YM } from './types';
import { compareYm } from './dates';

// DOMAIN_RULES §1–§4: per-category values, month totals, projection and the year summary.

export const live = <T extends { deleted?: boolean }>(rows: readonly T[]): T[] => rows.filter((r) => !r.deleted);

export const budgetKey = (year: number, month: number, categoryId: string): string => `${year}-${month}-${categoryId}`;
export const monthKey = (year: number, month: number): string => `${year}-${month}`;

export const byOrder = <T extends { sort_order: number; name?: string }>(a: T, b: T): number =>
  a.sort_order - b.sort_order || (a.name ?? '').localeCompare(b.name ?? '');

export interface Totals {
  income: number;
  expenses: number;
  saved: number;
  contributions: number;
  leftover: number;
}

/** §2 applied to any per-category value function. `null` counts as 0. */
export function totalsFor(categories: readonly Category[], value: (c: Category) => number | null): Totals {
  const t: Totals = { income: 0, expenses: 0, saved: 0, contributions: 0, leftover: 0 };
  for (const c of categories) {
    const v = value(c) ?? 0;
    if (c.kind === 'income') t.income += v;
    else if (c.kind === 'expense') t.expenses += v;
    else {
      t.saved += v * c.match_multiplier;
      t.contributions += v;
    }
  }
  t.leftover = t.income - t.expenses - t.contributions;
  return t;
}

export interface Line {
  category: Category;
  expected: number;
  /** null = blank (manual category with nothing typed in); counts as 0. */
  actual: number | null;
  ledgerSum: number;
  /** A typed-in actual on a ledger category (shown with a "manual" marker). */
  override: boolean;
  difference: number;
  budget: Budget | undefined;
  transactionCount: number;
}

export interface MonthSummary extends YM {
  exists: boolean;
  closed: boolean;
  monthRow: Month | undefined;
  /** Lines to display: active categories plus archived ones that hold data this month. */
  lines: Line[];
  expected: Totals;
  actual: Totals;
}

export interface YearLine {
  category: Category;
  expected: number;
  /** "Actual" column on the summary = projected (§3). */
  actual: number;
  difference: number;
}

export interface YearColumn extends Totals {
  annualizedSavings: number;
  pctNet: number;
  pctGross: number;
}

/** DOMAIN_RULES §4b: one month's contribution to the year's leftover vs plan. */
export interface MonthVsPlan {
  month: Month;
  closed: boolean;
  projectedLeftover: number;
  plannedLeftover: number;
  /** projectedLeftover − plannedLeftover; always 0 for open months. */
  vsPlan: number;
}

export interface YearSummary {
  year: number;
  months: Month[];
  n: number;
  /** §4b headline: yearActual.leftover − yearExpected.leftover. */
  leftoverVsPlan: number;
  /** yearActual.leftover / yearExpected.leftover, only when the planned leftover is > 0. */
  progress: number | null;
  /** Per month, oldest first; Σ vsPlan = leftoverVsPlan. */
  monthsVsPlan: MonthVsPlan[];
  lines: YearLine[];
  expected: YearColumn;
  actual: YearColumn;
}

export interface Calc {
  settings: Settings;
  /** Non-deleted categories in display order (including archived). */
  categories: Category[];
  categoryById: Map<string, Category>;
  /** Non-deleted months, oldest first. */
  months: Month[];
  month(ym: YM): Month | undefined;
  latestMonth(): Month | undefined;
  years(): number[];
  budget(ym: YM, categoryId: string): Budget | undefined;
  transactionsIn(ym: YM, categoryId?: string): Transaction[];
  expected(ym: YM, categoryId: string): number;
  ledgerSum(ym: YM, categoryId: string): number;
  actual(ym: YM, categoryId: string): number | null;
  projected(ym: YM, categoryId: string): number;
  monthSummary(ym: YM): MonthSummary;
  /** §4b: §2 leftover with projected(m, c) for every category. */
  projectedLeftover(ym: YM): number;
  /** §4b: projectedLeftover(m) − expected leftover(m). */
  monthVsPlan(ym: YM): number;
  yearSummary(year: number): YearSummary | null;
}

export function createCalc(ds: Dataset): Calc {
  const settings = ds.settings;
  const categories = live(ds.categories).sort(byOrder);
  const categoryById = new Map(categories.map((c) => [c.id, c]));
  const months = live(ds.months).sort(compareYm);
  const monthMap = new Map(months.map((m) => [monthKey(m.year, m.month), m]));

  const budgets = new Map<string, Budget>();
  for (const b of live(ds.budgets)) budgets.set(budgetKey(b.year, b.month, b.category_id), b);

  const sums = new Map<string, number>();
  const txByMonth = new Map<string, Transaction[]>();
  for (const t of live(ds.transactions)) {
    const k = budgetKey(t.year, t.month, t.category_id);
    sums.set(k, (sums.get(k) ?? 0) + t.amount);
    const mk = monthKey(t.year, t.month);
    const list = txByMonth.get(mk);
    if (list) list.push(t);
    else txByMonth.set(mk, [t]);
  }

  const month = (ym: YM) => monthMap.get(monthKey(ym.year, ym.month));
  const budget = (ym: YM, c: string) => budgets.get(budgetKey(ym.year, ym.month, c));
  const expected = (ym: YM, c: string) => budget(ym, c)?.expected ?? 0;
  const ledgerSum = (ym: YM, c: string) => sums.get(budgetKey(ym.year, ym.month, c)) ?? 0;

  function actual(ym: YM, c: string): number | null {
    const typed = budget(ym, c)?.actual;
    if (typed != null) return typed;
    if (categoryById.get(c)?.tracking === 'ledger') return ledgerSum(ym, c);
    return null;
  }

  function projected(ym: YM, c: string): number {
    const a = actual(ym, c) ?? 0;
    return month(ym)?.closed && a !== 0 ? a : expected(ym, c);
  }

  function transactionsIn(ym: YM, categoryId?: string): Transaction[] {
    const list = txByMonth.get(monthKey(ym.year, ym.month)) ?? [];
    return categoryId ? list.filter((t) => t.category_id === categoryId) : list.slice();
  }

  function monthSummary(ym: YM): MonthSummary {
    const m = month(ym);
    const lines: Line[] = [];
    for (const c of categories) {
      const b = budget(ym, c.id);
      const txCount = transactionsIn(ym, c.id).length;
      const hasData = b != null && (b.expected != null || b.actual != null);
      if (c.archived && !hasData && txCount === 0) continue;
      const exp = expected(ym, c.id);
      const act = actual(ym, c.id);
      lines.push({
        category: c,
        expected: exp,
        actual: act,
        ledgerSum: ledgerSum(ym, c.id),
        override: c.tracking === 'ledger' && b?.actual != null,
        difference: (act ?? 0) - exp,
        budget: b,
        transactionCount: txCount,
      });
    }
    return {
      ...ym,
      exists: m != null,
      closed: m?.closed ?? false,
      monthRow: m,
      lines,
      expected: totalsFor(categories, (c) => expected(ym, c.id)),
      actual: totalsFor(categories, (c) => actual(ym, c.id)),
    };
  }

  const projectedLeftover = (ym: YM) => totalsFor(categories, (c) => projected(ym, c.id)).leftover;
  const plannedLeftover = (ym: YM) => totalsFor(categories, (c) => expected(ym, c.id)).leftover;
  const monthVsPlan = (ym: YM) => projectedLeftover(ym) - plannedLeftover(ym);

  function yearSummary(year: number): YearSummary | null {
    const inYear = months.filter((m) => m.year === year);
    const n = inYear.length;
    if (n === 0) return null;
    const perExpected = new Map<string, number>();
    const perActual = new Map<string, number>();
    for (const c of categories) {
      let e = 0;
      let a = 0;
      for (const m of inYear) {
        e += expected(m, c.id);
        a += projected(m, c.id);
      }
      perExpected.set(c.id, e);
      perActual.set(c.id, a);
    }
    const column = (per: Map<string, number>): YearColumn => {
      const t = totalsFor(categories, (c) => per.get(c.id) ?? 0);
      const annualizedSavings = ((t.saved + t.leftover) * 12) / n;
      return {
        ...t,
        annualizedSavings,
        pctNet: annualizedSavings / settings.net_income,
        pctGross: annualizedSavings / settings.gross_income,
      };
    };
    const lines: YearLine[] = categories
      .filter((c) => !c.archived || (perExpected.get(c.id) ?? 0) !== 0 || (perActual.get(c.id) ?? 0) !== 0)
      .map((c) => {
        const e = perExpected.get(c.id) ?? 0;
        const a = perActual.get(c.id) ?? 0;
        return { category: c, expected: e, actual: a, difference: a - e };
      });
    const exp = column(perExpected);
    const act = column(perActual);
    const monthsVsPlan = inYear.map((m): MonthVsPlan => {
      const p = projectedLeftover(m);
      const e = plannedLeftover(m);
      return { month: m, closed: m.closed, projectedLeftover: p, plannedLeftover: e, vsPlan: p - e };
    });
    return {
      year, months: inYear, n, lines, expected: exp, actual: act,
      leftoverVsPlan: act.leftover - exp.leftover,
      progress: exp.leftover > 0 ? act.leftover / exp.leftover : null,
      monthsVsPlan,
    };
  }

  return {
    settings,
    categories,
    categoryById,
    months,
    month,
    latestMonth: () => months[months.length - 1],
    years: () => [...new Set(months.map((m) => m.year))],
    budget,
    transactionsIn,
    expected,
    ledgerSum,
    actual,
    projected,
    monthSummary,
    projectedLeftover,
    monthVsPlan,
    yearSummary,
  };
}

export const KIND_ORDER: CategoryKind[] = ['income', 'expense', 'savings'];
export const KIND_LABEL: Record<CategoryKind, string> = { income: 'Income', expense: 'Expenses', savings: 'Savings' };
