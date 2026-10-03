// Pure TypeScript port of docs/DOMAIN_RULES.md (the 2026 Budget spreadsheet math).
// No Deno, Supabase or network imports: everything here is deterministic and unit-tested
// against docs/fixtures/expected.json (see domain_test.ts).

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/** "Today" and "this month" are always evaluated in this time zone. */
export const TIMEZONE = "America/New_York";

export const MONTH_NAMES = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December",
] as const;

export const DEFAULT_SETTINGS: Settings = {
  net_income: 68000,
  gross_income: 85000,
  currency: "USD",
  days_per_month: 30.5,
};

// ---------------------------------------------------------------------------
// Row types (columns of supabase/migrations/*_init.sql, minus user_id/updated_at)
// ---------------------------------------------------------------------------

export type Kind = "income" | "expense" | "savings";
export type Tracking = "ledger" | "manual";

export interface Settings {
  net_income: number;
  gross_income: number;
  currency: string;
  days_per_month: number;
}

export interface Category {
  id: string;
  name: string;
  kind: Kind;
  tracking: Tracking;
  match_multiplier: number;
  sort_order: number;
  icon?: string | null;
  color?: string | null;
  archived: boolean;
  deleted?: boolean;
}

export interface Month {
  year: number;
  month: number;
  closed: boolean;
  note?: string | null;
  deleted?: boolean;
}

export interface Budget {
  year: number;
  month: number;
  category_id: string;
  expected: number | null;
  actual: number | null;
  deleted?: boolean;
}

export interface Transaction {
  id: string;
  year: number;
  month: number;
  category_id: string;
  date: string | null;
  item: string;
  amount: number;
  note?: string | null;
  created_at?: string | null;
  deleted?: boolean;
}

export interface RecurringItem {
  id: string;
  category_id: string;
  item: string;
  amount: number;
  day_of_month: number | null;
  active: boolean;
  sort_order: number;
  deleted?: boolean;
}

export interface Account {
  id: string;
  name: string;
  account_group: "cash" | "investment" | "asset" | "debt";
  liquid: boolean;
  balance: number;
  linked_category_id: string | null;
  base_amount: number;
  sort_order: number;
  archived: boolean;
  deleted?: boolean;
}

export interface LedgerEntry {
  id: string;
  name: string;
  amount: number;
  note?: string | null;
  settled: boolean;
  sort_order: number;
  deleted?: boolean;
}

export interface MealPlan {
  id: string;
  name: string;
  kind: "day" | "recipe";
  label?: string | null;
  note?: string | null;
  sort_order: number;
  deleted?: boolean;
}

export interface MealItem {
  id: string;
  plan_id: string;
  time_label?: string | null;
  name: string;
  calories: number | null;
  protein: number | null;
  fiber: number | null;
  fat: number | null;
  cost: number | null;
  sort_order: number;
  deleted?: boolean;
}

/** Everything a computation may need. Pieces not needed by a function may be empty. */
export interface Snapshot {
  settings: Settings;
  categories: Category[];
  months: Month[];
  budgets: Budget[];
  transactions: Transaction[];
  recurring_items?: RecurringItem[];
  accounts?: Account[];
  ledger_entries?: LedgerEntry[];
  meal_plans?: MealPlan[];
  meal_items?: MealItem[];
}

// ---------------------------------------------------------------------------
// Small helpers
// ---------------------------------------------------------------------------

const live = <T extends { deleted?: boolean }>(rows: readonly T[] | undefined): T[] =>
  (rows ?? []).filter((r) => !r.deleted);

/** Round away floating point noise (storage is never rounded; this is for output). */
export function r4(x: number): number {
  return Math.round(x * 10000) / 10000;
}

const ymKey = (y: number, m: number) => `${y}-${m}`;
const bKey = (y: number, m: number, c: string) => `${y}-${m}-${c}`;

export function monthKey(y: number, m: number): string {
  return `${y}-${String(m).padStart(2, "0")}`;
}

export function monthName(m: number): string {
  return MONTH_NAMES[m - 1] ?? `Month ${m}`;
}

export function compareYM(a: { year: number; month: number }, b: { year: number; month: number }): number {
  return a.year - b.year || a.month - b.month;
}

export function daysInMonth(y: number, m: number): number {
  return new Date(Date.UTC(y, m, 0)).getUTCDate();
}

export function sortCategories(cats: Category[]): Category[] {
  return [...cats].sort((a, b) => a.sort_order - b.sort_order || a.name.localeCompare(b.name));
}

// ---------------------------------------------------------------------------
// §1 Per category, per month
// ---------------------------------------------------------------------------

export interface Index {
  cats: Map<string, Category>;
  budgets: Map<string, Budget>;
  ledger: Map<string, number>;
  months: Map<string, Month>;
}

export function buildIndex(s: Snapshot): Index {
  const cats = new Map(live(s.categories).map((c) => [c.id, c] as const));
  const budgets = new Map(live(s.budgets).map((b) => [bKey(b.year, b.month, b.category_id), b] as const));
  const ledger = new Map<string, number>();
  for (const t of live(s.transactions)) {
    const k = bKey(t.year, t.month, t.category_id);
    ledger.set(k, (ledger.get(k) ?? 0) + Number(t.amount));
  }
  const months = new Map(live(s.months).map((m) => [ymKey(m.year, m.month), m] as const));
  return { cats, budgets, ledger, months };
}

export function expectedOf(ix: Index, y: number, m: number, c: string): number {
  return ix.budgets.get(bKey(y, m, c))?.expected ?? 0;
}

export function ledgerSumOf(ix: Index, y: number, m: number, c: string): number {
  return ix.ledger.get(bKey(y, m, c)) ?? 0;
}

export function actualOf(ix: Index, y: number, m: number, c: string): number | null {
  const b = ix.budgets.get(bKey(y, m, c));
  if (b && b.actual !== null && b.actual !== undefined) return b.actual;
  if (ix.cats.get(c)?.tracking === "ledger") return ledgerSumOf(ix, y, m, c);
  return null;
}

/** §3: an open month contributes its budget; a closed month its actual (falling back to budget when 0/blank). */
export function projectedOf(ix: Index, y: number, m: number, c: string): number {
  const closed = ix.months.get(ymKey(y, m))?.closed ?? false;
  const a = actualOf(ix, y, m, c) ?? 0;
  return closed && a !== 0 ? a : expectedOf(ix, y, m, c);
}

// ---------------------------------------------------------------------------
// §2 Totals
// ---------------------------------------------------------------------------

export interface Totals {
  income: number;
  expenses: number;
  saved: number;
  contributions: number;
  leftover: number;
}

export function totalsOf(cats: Iterable<Category>, value: (c: Category) => number | null): Totals {
  const t = { income: 0, expenses: 0, saved: 0, contributions: 0, leftover: 0 };
  for (const c of cats) {
    const v = value(c) ?? 0;
    if (c.kind === "income") t.income += v;
    else if (c.kind === "expense") t.expenses += v;
    else {
      t.saved += v * Number(c.match_multiplier ?? 1);
      t.contributions += v;
    }
  }
  t.leftover = t.income - t.expenses - t.contributions;
  return roundTotals(t);
}

function roundTotals(t: Totals): Totals {
  return {
    income: r4(t.income),
    expenses: r4(t.expenses),
    saved: r4(t.saved),
    contributions: r4(t.contributions),
    leftover: r4(t.leftover),
  };
}

function diffTotals(a: Totals, e: Totals): Totals {
  return roundTotals({
    income: a.income - e.income,
    expenses: a.expenses - e.expenses,
    saved: a.saved - e.saved,
    contributions: a.contributions - e.contributions,
    leftover: a.leftover - e.leftover,
  });
}

export interface MonthCategoryRow {
  category_id: string;
  name: string;
  kind: Kind;
  tracking: Tracking;
  match_multiplier: number;
  archived: boolean;
  expected: number;
  /** null = blank (manual category with nothing entered); counts as 0. */
  actual: number | null;
  ledger_sum: number;
  /** true when budget.actual is set on a ledger category (manual override of the ledger sum). */
  override: boolean;
  /** (actual ?? 0) − expected */
  difference: number;
  /** expected − (actual ?? 0): budget left to spend (expenses) or still to come (income/savings). */
  remaining: number;
  transaction_count: number;
}

export interface MonthView {
  year: number;
  month: number;
  key: string;
  name: string;
  exists: boolean;
  closed: boolean;
  categories: MonthCategoryRow[];
  expected: Totals;
  actual: Totals;
  difference: Totals;
}

/**
 * Full view of one month. Totals include every non-deleted category (archived ones too,
 * wherever they have data); `categories` lists non-archived categories plus archived ones
 * that have a budget row or transactions in this month.
 */
export function monthView(s: Snapshot, y: number, m: number, ix: Index = buildIndex(s)): MonthView {
  const cats = sortCategories([...ix.cats.values()]);
  const txCount = new Map<string, number>();
  for (const t of live(s.transactions)) {
    if (t.year === y && t.month === m) txCount.set(t.category_id, (txCount.get(t.category_id) ?? 0) + 1);
  }
  const rows: MonthCategoryRow[] = [];
  for (const c of cats) {
    const hasData = ix.budgets.has(bKey(y, m, c.id)) || (txCount.get(c.id) ?? 0) > 0;
    if (c.archived && !hasData) continue;
    const expected = expectedOf(ix, y, m, c.id);
    const actual = actualOf(ix, y, m, c.id);
    const b = ix.budgets.get(bKey(y, m, c.id));
    rows.push({
      category_id: c.id,
      name: c.name,
      kind: c.kind,
      tracking: c.tracking,
      match_multiplier: Number(c.match_multiplier),
      archived: c.archived,
      expected: r4(expected),
      actual: actual === null ? null : r4(actual),
      ledger_sum: r4(ledgerSumOf(ix, y, m, c.id)),
      override: c.tracking === "ledger" && b?.actual !== null && b?.actual !== undefined,
      difference: r4((actual ?? 0) - expected),
      remaining: r4(expected - (actual ?? 0)),
      transaction_count: txCount.get(c.id) ?? 0,
    });
  }
  const expected = totalsOf(cats, (c) => expectedOf(ix, y, m, c.id));
  const actual = totalsOf(cats, (c) => actualOf(ix, y, m, c.id));
  const mo = ix.months.get(ymKey(y, m));
  return {
    year: y,
    month: m,
    key: monthKey(y, m),
    name: `${monthName(m)} ${y}`,
    exists: !!mo,
    closed: mo?.closed ?? false,
    categories: rows,
    expected,
    actual,
    difference: diffTotals(actual, expected),
  };
}

// ---------------------------------------------------------------------------
// §4 Year summary
// ---------------------------------------------------------------------------

export interface YearColumn extends Totals {
  annualized_savings: number;
  pct_net: number;
  pct_gross: number;
  /** category name -> value */
  categories: Record<string, number>;
}

export interface YearCategoryRow {
  category_id: string;
  name: string;
  kind: Kind;
  expected: number;
  /** Σ projected (open months use budget, closed months use actual) */
  actual: number;
  difference: number;
}

export interface YearView {
  year: number;
  month_count: number;
  months: { year: number; month: number; name: string; closed: boolean; leftover_expected: number; leftover_actual: number }[];
  categories: YearCategoryRow[];
  expected: YearColumn;
  actual: YearColumn;
}

export function yearView(s: Snapshot, y: number, ix: Index = buildIndex(s)): YearView | null {
  const ms = live(s.months).filter((m) => m.year === y).sort(compareYM);
  const n = ms.length;
  if (n === 0) return null;
  const cats = sortCategories([...ix.cats.values()]);
  const col = (fn: (yy: number, mm: number, c: string) => number): YearColumn => {
    const per = new Map<string, number>();
    for (const c of cats) per.set(c.id, ms.reduce((acc, m) => acc + (fn(m.year, m.month, c.id) ?? 0), 0));
    const t = totalsOf(cats, (c) => per.get(c.id) ?? 0);
    const ann = ((t.saved + t.leftover) * 12) / n;
    const net = Number(s.settings.net_income);
    const gross = Number(s.settings.gross_income);
    return {
      ...t,
      annualized_savings: r4(ann),
      pct_net: net ? Math.round((ann / net) * 1e6) / 1e6 : 0,
      pct_gross: gross ? Math.round((ann / gross) * 1e6) / 1e6 : 0,
      categories: Object.fromEntries(cats.map((c) => [c.name, r4(per.get(c.id) ?? 0)])),
    };
  };
  const expected = col((yy, mm, c) => expectedOf(ix, yy, mm, c));
  const actual = col((yy, mm, c) => projectedOf(ix, yy, mm, c));
  const categories: YearCategoryRow[] = cats
    .filter((c) => !c.archived || expected.categories[c.name] !== 0 || actual.categories[c.name] !== 0)
    .map((c) => ({
      category_id: c.id,
      name: c.name,
      kind: c.kind,
      expected: expected.categories[c.name],
      actual: actual.categories[c.name],
      difference: r4(actual.categories[c.name] - expected.categories[c.name]),
    }));
  const months = ms.map((m) => {
    const v = monthView(s, m.year, m.month, ix);
    return {
      year: m.year,
      month: m.month,
      name: monthName(m.month),
      closed: m.closed,
      leftover_expected: v.expected.leftover,
      leftover_actual: v.actual.leftover,
    };
  });
  return { year: y, month_count: n, months, categories, expected, actual };
}

// ---------------------------------------------------------------------------
// §5 Net worth
// ---------------------------------------------------------------------------

export interface AccountView {
  id: string;
  name: string;
  account_group: Account["account_group"];
  liquid: boolean;
  linked: boolean;
  linked_category: string | null;
  base_amount: number;
  balance: number;
}

export interface NetWorthView {
  accounts: AccountView[];
  net_reconciliations: number;
  net_worth: number;
  super_liquid: number;
  unsettled: LedgerEntry[];
}

export function accountBalance(a: Account, cats: Map<string, Category>, actualSums: Map<string, number>): number {
  if (!a.linked_category_id) return Number(a.balance);
  const mult = Number(cats.get(a.linked_category_id)?.match_multiplier ?? 1);
  return Number(a.base_amount) + mult * (actualSums.get(a.linked_category_id) ?? 0);
}

/** Needs `budgets` for all years of the linked categories (raw budget.actual, not projected). */
export function netWorthView(s: Snapshot): NetWorthView {
  const cats = new Map(live(s.categories).map((c) => [c.id, c] as const));
  const sums = new Map<string, number>();
  for (const b of live(s.budgets)) sums.set(b.category_id, (sums.get(b.category_id) ?? 0) + (b.actual ?? 0));
  const accounts = live(s.accounts)
    .filter((a) => !a.archived)
    .sort((a, b) => a.sort_order - b.sort_order)
    .map((a): AccountView => ({
      id: a.id,
      name: a.name,
      account_group: a.account_group,
      liquid: a.liquid,
      linked: !!a.linked_category_id,
      linked_category: a.linked_category_id ? cats.get(a.linked_category_id)?.name ?? null : null,
      base_amount: Number(a.base_amount),
      balance: r4(accountBalance(a, cats, sums)),
    }));
  const unsettled = live(s.ledger_entries).filter((e) => !e.settled).sort((a, b) => a.sort_order - b.sort_order);
  const recon = unsettled.reduce((acc, e) => acc + Number(e.amount), 0);
  const total = accounts.reduce((acc, a) => acc + a.balance, 0);
  const liquid = accounts.filter((a) => a.liquid).reduce((acc, a) => acc + a.balance, 0);
  return {
    accounts,
    net_reconciliations: r4(recon),
    net_worth: r4(total + recon),
    super_liquid: r4(liquid),
    unsettled,
  };
}

// ---------------------------------------------------------------------------
// §7 Meal plans
// ---------------------------------------------------------------------------

export interface MealTotals {
  calories: number;
  protein: number;
  fiber: number;
  fat: number;
  cost: number;
  monthly_cost?: number;
}

export interface MealPlanView {
  id: string;
  name: string;
  kind: "day" | "recipe";
  label: string | null;
  note: string | null;
  items: MealItem[];
  totals: MealTotals;
}

export function mealPlansView(s: Snapshot): MealPlanView[] {
  const items = live(s.meal_items);
  return live(s.meal_plans)
    .sort((a, b) => a.sort_order - b.sort_order)
    .map((p) => {
      const its = items.filter((i) => i.plan_id === p.id).sort((a, b) => a.sort_order - b.sort_order);
      const sum = (k: "calories" | "protein" | "fiber" | "fat" | "cost") =>
        r4(its.reduce((acc, i) => acc + Number(i[k] ?? 0), 0));
      const totals: MealTotals = {
        calories: sum("calories"),
        protein: sum("protein"),
        fiber: sum("fiber"),
        fat: sum("fat"),
        cost: sum("cost"),
      };
      if (p.kind === "day") totals.monthly_cost = r4(totals.cost * Number(s.settings.days_per_month));
      return { id: p.id, name: p.name, kind: p.kind, label: p.label ?? null, note: p.note ?? null, items: its, totals };
    });
}

// ---------------------------------------------------------------------------
// §6 Creating a month
// ---------------------------------------------------------------------------

export interface NewMonthPlan {
  month: Month;
  budgets: Budget[];
  transactions: Transaction[];
}

/**
 * Rows to write for "New month" (Y, M), or null if the month already exists.
 * Needs categories, months, budgets (at least those before Y-M) and recurring_items.
 */
export function planNewMonth(s: Snapshot, y: number, m: number, newId: () => string): NewMonthPlan | null {
  if (live(s.months).some((mo) => mo.year === y && mo.month === m)) return null;
  const target = { year: y, month: m };
  const latest = new Map<string, Budget>();
  const existing = new Set<string>();
  for (const b of live(s.budgets)) {
    if (b.year === y && b.month === m) existing.add(b.category_id);
    if (compareYM(b, target) >= 0) continue;
    const cur = latest.get(b.category_id);
    if (!cur || compareYM(b, cur) > 0) latest.set(b.category_id, b);
  }
  const budgets: Budget[] = sortCategories(live(s.categories).filter((c) => !c.archived))
    .filter((c) => !existing.has(c.id))
    .map((c) => ({ year: y, month: m, category_id: c.id, expected: latest.get(c.id)?.expected ?? null, actual: null }));
  const dim = daysInMonth(y, m);
  const transactions: Transaction[] = live(s.recurring_items)
    .filter((r) => r.active)
    .sort((a, b) => a.sort_order - b.sort_order)
    .map((r) => ({
      id: newId(),
      year: y,
      month: m,
      category_id: r.category_id,
      date: r.day_of_month ? isoDate(y, m, Math.min(r.day_of_month, dim)) : null,
      item: r.item,
      amount: Number(r.amount),
      note: null,
    }));
  return { month: { year: y, month: m, closed: false, note: null }, budgets, transactions };
}

// ---------------------------------------------------------------------------
// Dates
// ---------------------------------------------------------------------------

export function isoDate(y: number, m: number, d: number): string {
  return `${String(y).padStart(4, "0")}-${String(m).padStart(2, "0")}-${String(d).padStart(2, "0")}`;
}

/** Validates YYYY-MM-DD (a real calendar date) and returns its parts. */
export function parseIsoDate(s: string): { year: number; month: number; day: number } | null {
  const mt = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s);
  if (!mt) return null;
  const [year, month, day] = [Number(mt[1]), Number(mt[2]), Number(mt[3])];
  if (month < 1 || month > 12 || day < 1 || day > daysInMonth(year, month)) return null;
  return { year, month, day };
}

/** Calendar date of `now` in `tz` (default America/New_York). */
export function todayParts(now: Date = new Date(), tz: string = TIMEZONE): { year: number; month: number; day: number; iso: string } {
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone: tz, year: "numeric", month: "2-digit", day: "2-digit" })
    .formatToParts(now);
  const get = (t: string) => Number(parts.find((p) => p.type === t)?.value);
  const year = get("year"), month = get("month"), day = get("day");
  return { year, month, day, iso: isoDate(year, month, day) };
}

/** Sort key: date desc, then created_at desc. Undated rows sort below dated rows of their budget month. */
export function compareTransactionsDesc(a: Transaction, b: Transaction): number {
  const ka = a.date ?? `${monthKey(a.year, a.month)}-00`;
  const kb = b.date ?? `${monthKey(b.year, b.month)}-00`;
  if (ka !== kb) return ka < kb ? 1 : -1;
  const ca = a.created_at ?? "", cb = b.created_at ?? "";
  if (ca !== cb) return ca < cb ? 1 : -1;
  return 0;
}

// ---------------------------------------------------------------------------
// Name matching ("food", "car insurance" -> "Car Ins", "brokerage" -> "Taxable Brokerage")
// ---------------------------------------------------------------------------

export type MatchResult<T> =
  | { ok: true; item: T; how: "exact" | "prefix" | "contains" | "fuzzy" }
  | { ok: false; reason: "none" | "ambiguous"; candidates: T[] };

export function normalizeName(s: string): string {
  return s.toLowerCase().normalize("NFKD").replace(/[^a-z0-9]+/g, "");
}

function words(s: string): string[] {
  return s.toLowerCase().split(/[^a-z0-9]+/).filter(Boolean);
}

export function levenshtein(a: string, b: string): number {
  if (a === b) return 0;
  const prev = Array.from({ length: b.length + 1 }, (_, i) => i);
  for (let i = 1; i <= a.length; i++) {
    let diag = prev[0];
    prev[0] = i;
    for (let j = 1; j <= b.length; j++) {
      const tmp = prev[j];
      prev[j] = Math.min(prev[j] + 1, prev[j - 1] + 1, diag + (a[i - 1] === b[j - 1] ? 0 : 1));
      diag = tmp;
    }
  }
  return prev[b.length];
}

/**
 * Case-insensitive name lookup with fallbacks, in order: exact, prefix (either direction,
 * or word-by-word), substring, then fuzzy (edit distance). The first stage with exactly one
 * hit wins; a stage with several hits is ambiguous. Archived items lose ties to active ones.
 */
export function matchByName<T extends { name: string; archived?: boolean }>(query: string, items: readonly T[]): MatchResult<T> {
  const q = normalizeName(query);
  const qw = words(query);
  if (!q) return { ok: false, reason: "none", candidates: [...items] };
  const decide = (hits: T[], how: "exact" | "prefix" | "contains" | "fuzzy"): MatchResult<T> | null => {
    if (hits.length === 0) return null;
    const active = hits.filter((h) => !h.archived);
    const pool = active.length > 0 ? active : hits;
    if (pool.length === 1) return { ok: true, item: pool[0], how };
    return { ok: false, reason: "ambiguous", candidates: pool };
  };
  const norm = items.map((it) => ({ it, n: normalizeName(it.name), w: words(it.name) }));

  const exact = decide(norm.filter((x) => x.n === q).map((x) => x.it), "exact");
  if (exact) return exact;

  const wordPrefix = (a: string[], b: string[]) =>
    a.length === b.length && a.every((w, i) => w.startsWith(b[i]) || b[i].startsWith(w));
  const prefix = decide(
    norm.filter((x) => x.n.startsWith(q) || q.startsWith(x.n) || wordPrefix(x.w, qw)).map((x) => x.it),
    "prefix",
  );
  if (prefix) return prefix;

  const contains = decide(norm.filter((x) => x.n.includes(q) || (x.n.length >= 3 && q.includes(x.n))).map((x) => x.it), "contains");
  if (contains) return contains;

  const scored = norm.map((x) => ({ ...x, d: levenshtein(x.n, q) }));
  const limit = Math.max(1, Math.floor(q.length / 4));
  const best = Math.min(...scored.map((x) => x.d));
  if (best <= limit) {
    const fuzzy = decide(scored.filter((x) => x.d === best).map((x) => x.it), "fuzzy");
    if (fuzzy) return fuzzy;
  }
  return { ok: false, reason: "none", candidates: [...items] };
}

// ---------------------------------------------------------------------------
// §8 Display
// ---------------------------------------------------------------------------

/** `$1,234` for whole numbers, `$1,234.56` otherwise; negatives with a true minus sign. */
export function formatMoney(x: number | null | undefined): string {
  if (x === null || x === undefined) return "—";
  const v = r4(x);
  const abs = Math.abs(v);
  const whole = Math.abs(abs - Math.round(abs)) < 0.005;
  const s = abs.toLocaleString("en-US", {
    minimumFractionDigits: whole ? 0 : 2,
    maximumFractionDigits: whole ? 0 : 2,
  });
  return `${v < 0 && !(whole && Math.round(abs) === 0) ? "−" : ""}$${s}`;
}

export function formatPct(x: number): string {
  return `${(x * 100).toFixed(1)}%`;
}
