// Data-access interface used by the tool handlers. One instance is bound to ONE user:
// implementations must scope every read to that user and stamp user_id on every write.
// All list/get methods return only non-deleted rows (tombstones are invisible).
// Writes follow docs/SYNC.md: upsert by the documented keys, never send updated_at,
// deletion = upsert with deleted: true.

import type {
  Account,
  Budget,
  Category,
  LedgerEntry,
  Month,
  NetWorthSnapshot,
  RecurringItem,
  Settings,
  Transaction,
} from "../_shared/domain.ts";

export interface BudgetFilter {
  year?: number;
  month?: number;
  categoryIds?: string[];
}

export interface TransactionFilter {
  year?: number;
  month?: number;
  categoryId?: string;
}

export interface BudgetStore {
  getSettings(): Promise<Settings>;
  listCategories(): Promise<Category[]>;
  listMonths(): Promise<Month[]>;
  listBudgets(filter?: BudgetFilter): Promise<Budget[]>;
  listTransactions(filter?: TransactionFilter): Promise<Transaction[]>;
  getTransaction(id: string): Promise<Transaction | null>;
  listRecurringItems(): Promise<RecurringItem[]>;
  listAccounts(): Promise<Account[]>;
  listLedgerEntries(): Promise<LedgerEntry[]>;
  /** Live net worth snapshots (§5b), ascending by taken_on, optionally from a date (inclusive). */
  listNetWorthSnapshots(filter?: { from?: string }): Promise<NetWorthSnapshot[]>;

  upsertMonths(rows: Month[]): Promise<Month[]>;
  upsertBudgets(rows: Budget[]): Promise<Budget[]>;
  upsertTransactions(rows: Transaction[]): Promise<Transaction[]>;
  upsertAccounts(rows: Account[]): Promise<Account[]>;
  upsertLedgerEntries(rows: LedgerEntry[]): Promise<LedgerEntry[]>;

  /**
   * Recompute and upsert TODAY's (America/New_York) net worth snapshot, source 'manual'.
   * Supabase: RPC public.write_net_worth_snapshot(user, 'manual'), so the math stays in SQL.
   * Callers treat failures as non-fatal (see refreshSnapshotSafely in tools.ts).
   */
  refreshNetWorthSnapshot(): Promise<NetWorthSnapshot | null>;
}

// ---------------------------------------------------------------------------
// Column pickers: exactly the writable columns of each table (no updated_at,
// no created_at, no user_id; the store adds user_id itself).
// ---------------------------------------------------------------------------

export function monthRow(m: Month) {
  return { year: m.year, month: m.month, closed: !!m.closed, note: m.note ?? null, deleted: !!m.deleted };
}

export function budgetRow(b: Budget) {
  return {
    year: b.year,
    month: b.month,
    category_id: b.category_id,
    expected: b.expected ?? null,
    actual: b.actual ?? null,
    deleted: !!b.deleted,
  };
}

export function transactionRow(t: Transaction) {
  return {
    id: t.id,
    year: t.year,
    month: t.month,
    category_id: t.category_id,
    date: t.date ?? null,
    item: t.item ?? "",
    amount: t.amount,
    note: t.note ?? null,
    deleted: !!t.deleted,
  };
}

export function accountRow(a: Account) {
  return {
    id: a.id,
    name: a.name,
    account_group: a.account_group,
    liquid: !!a.liquid,
    balance: a.balance,
    linked_category_id: a.linked_category_id ?? null,
    base_amount: a.base_amount ?? 0,
    sort_order: a.sort_order ?? 0,
    archived: !!a.archived,
    deleted: !!a.deleted,
  };
}

export function ledgerEntryRow(e: LedgerEntry) {
  return {
    id: e.id,
    name: e.name,
    amount: e.amount,
    note: e.note ?? null,
    settled: !!e.settled,
    sort_order: e.sort_order ?? 0,
    deleted: !!e.deleted,
  };
}
