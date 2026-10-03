// In-memory BudgetStore seeded from a backup JSON (docs/SYNC.md format).
// Used by unit tests and by local end-to-end runs with BUDGET_MCP_FAKE=1. Never in production.

import {
  type Account,
  type Budget,
  type Category,
  DEFAULT_SETTINGS,
  type LedgerEntry,
  type MealItem,
  type MealPlan,
  type Month,
  type RecurringItem,
  type Settings,
  type Transaction,
} from "../_shared/domain.ts";
import {
  accountRow,
  type BudgetFilter,
  budgetRow,
  type BudgetStore,
  ledgerEntryRow,
  monthRow,
  type TransactionFilter,
  transactionRow,
} from "./store.ts";

// deno-lint-ignore no-explicit-any
type Backup = Record<string, any>;

const clone = <T>(x: T): T => structuredClone(x);

export class InMemoryStore implements BudgetStore {
  settings: Settings;
  categories: Category[];
  months: Month[];
  budgets: Budget[];
  /** Kept as an array (not keyed by id) so a backup with a duplicated id behaves like the reference calc. */
  transactions: Transaction[];
  recurring: RecurringItem[];
  accounts: Account[];
  ledger: LedgerEntry[];
  mealPlans: MealPlan[];
  mealItems: MealItem[];
  /** Number of write calls, for tests. */
  writes = 0;
  private clock: number;

  constructor(backup: Backup) {
    const b = clone(backup);
    this.settings = { ...DEFAULT_SETTINGS, ...(b.settings ?? {}) };
    this.categories = b.categories ?? [];
    this.months = b.months ?? [];
    this.budgets = b.budgets ?? [];
    const base = Date.parse(b.exported_at ?? "2026-01-01T00:00:00Z");
    this.transactions = (b.transactions ?? []).map((t: Transaction, i: number) => ({
      ...t,
      created_at: t.created_at ?? new Date(base + i).toISOString(),
    }));
    this.recurring = b.recurring_items ?? [];
    this.accounts = b.accounts ?? [];
    this.ledger = b.ledger_entries ?? [];
    this.mealPlans = b.meal_plans ?? [];
    this.mealItems = b.meal_items ?? [];
    this.clock = Math.max(Date.now(), base + this.transactions.length + 1);
  }

  private live<T extends { deleted?: boolean }>(rows: T[]): T[] {
    return clone(rows.filter((r) => !r.deleted));
  }

  getSettings() {
    return Promise.resolve(clone(this.settings));
  }
  listCategories() {
    return Promise.resolve(this.live(this.categories));
  }
  listMonths() {
    return Promise.resolve(this.live(this.months));
  }
  listBudgets(f: BudgetFilter = {}) {
    return Promise.resolve(
      this.live(this.budgets).filter((b) =>
        (f.year === undefined || b.year === f.year) &&
        (f.month === undefined || b.month === f.month) &&
        (!f.categoryIds || f.categoryIds.includes(b.category_id))
      ),
    );
  }
  listTransactions(f: TransactionFilter = {}) {
    return Promise.resolve(
      this.live(this.transactions).filter((t) =>
        (f.year === undefined || t.year === f.year) &&
        (f.month === undefined || t.month === f.month) &&
        (f.categoryId === undefined || t.category_id === f.categoryId)
      ),
    );
  }
  getTransaction(id: string) {
    return Promise.resolve(this.live(this.transactions).find((t) => t.id === id) ?? null);
  }
  listRecurringItems() {
    return Promise.resolve(this.live(this.recurring));
  }
  listAccounts() {
    return Promise.resolve(this.live(this.accounts));
  }
  listLedgerEntries() {
    return Promise.resolve(this.live(this.ledger));
  }
  listMealPlans() {
    return Promise.resolve(this.live(this.mealPlans));
  }
  listMealItems() {
    return Promise.resolve(this.live(this.mealItems));
  }

  private upsert<T>(table: T[], rows: T[], same: (a: T, b: T) => boolean, merge?: (old: T, row: T) => T): T[] {
    this.writes++;
    for (const row of rows) {
      let found = false;
      for (let i = 0; i < table.length; i++) {
        if (same(table[i], row)) {
          table[i] = merge ? merge(table[i], row) : { ...table[i], ...row };
          found = true;
        }
      }
      if (!found) table.push(clone(row));
    }
    return clone(rows);
  }

  upsertMonths(rows: Month[]) {
    return Promise.resolve(this.upsert(this.months, rows.map(monthRow), (a, b) => a.year === b.year && a.month === b.month));
  }
  upsertBudgets(rows: Budget[]) {
    return Promise.resolve(
      this.upsert(
        this.budgets,
        rows.map(budgetRow),
        (a, b) => a.year === b.year && a.month === b.month && a.category_id === b.category_id,
      ),
    );
  }
  upsertTransactions(rows: Transaction[]) {
    const stamped = rows.map((r) => ({ ...transactionRow(r), created_at: new Date(this.clock++).toISOString() }));
    // created_at is server-owned on insert and never changes afterwards.
    const out = this.upsert(this.transactions, stamped, (a, b) => a.id === b.id, (old, row) => ({ ...old, ...row, created_at: old.created_at }));
    return Promise.resolve(out.map((r) => this.transactions.find((t) => t.id === r.id) ?? r).map(clone));
  }
  upsertAccounts(rows: Account[]) {
    return Promise.resolve(this.upsert(this.accounts, rows.map(accountRow) as Account[], (a, b) => a.id === b.id));
  }
  upsertLedgerEntries(rows: LedgerEntry[]) {
    return Promise.resolve(this.upsert(this.ledger, rows.map(ledgerEntryRow), (a, b) => a.id === b.id));
  }
}
