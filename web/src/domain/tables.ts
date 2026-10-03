import type {
  Account, Budget, Category, LedgerEntry, Month, NetWorthSnapshot, RecurringItem, Settings, Transaction,
} from './types';

// Table metadata shared by the store, the Supabase adapter and backup import/export.

export interface RowTypes {
  settings: Settings;
  categories: Category;
  months: Month;
  budgets: Budget;
  transactions: Transaction;
  recurring_items: RecurringItem;
  accounts: Account;
  ledger_entries: LedgerEntry;
  net_worth_snapshots: NetWorthSnapshot;
}

export type TableName = keyof RowTypes;
export type ListTable = Exclude<TableName, 'settings'>;

/** Push order from docs/SYNC.md (cosmetic: there are no foreign keys). */
export const TABLES: TableName[] = [
  'settings', 'categories', 'months', 'budgets', 'accounts', 'recurring_items',
  'ledger_entries', 'transactions', 'net_worth_snapshots',
];

/**
 * Tables clients only read. net_worth_snapshots is written by the server (RPC take_net_worth_snapshot
 * and the nightly job); the one exception is backup import (DOMAIN_RULES §5b, SYNC.md).
 */
export const PULL_ONLY: ReadonlySet<TableName> = new Set<TableName>(['net_worth_snapshots']);
export const LIST_TABLES = TABLES.filter((t): t is ListTable => t !== 'settings');

/** Upsert conflict targets (docs/SYNC.md). */
export const ON_CONFLICT: Record<TableName, string> = {
  settings: 'user_id',
  categories: 'id',
  months: 'user_id,year,month',
  budgets: 'user_id,year,month,category_id',
  transactions: 'id',
  recurring_items: 'id',
  accounts: 'id',
  ledger_entries: 'id',
  net_worth_snapshots: 'user_id,taken_on',
};

/** Client-writable columns per table (everything except user_id, updated_at, created_at). */
export const COLUMNS: { [T in TableName]: (keyof RowTypes[T] & string)[] } = {
  settings: ['net_income', 'gross_income', 'currency', 'deleted'],
  categories: ['id', 'name', 'kind', 'tracking', 'match_multiplier', 'sort_order', 'icon', 'color', 'archived', 'deleted'],
  months: ['year', 'month', 'closed', 'note', 'deleted'],
  budgets: ['year', 'month', 'category_id', 'expected', 'actual', 'deleted'],
  transactions: ['id', 'year', 'month', 'category_id', 'date', 'item', 'amount', 'note', 'deleted'],
  recurring_items: ['id', 'category_id', 'item', 'amount', 'day_of_month', 'active', 'sort_order', 'deleted'],
  accounts: ['id', 'name', 'account_group', 'liquid', 'balance', 'linked_category_id', 'base_amount', 'sort_order', 'archived', 'deleted'],
  ledger_entries: ['id', 'name', 'amount', 'note', 'settled', 'sort_order', 'deleted'],
  net_worth_snapshots: ['taken_on', 'net_worth', 'super_liquid', 'reconciliations', 'accounts', 'source', 'deleted'],
};

/** Numeric columns (Postgres `numeric` may arrive as strings from some paths). */
const NUMERIC: Partial<Record<TableName, string[]>> = {
  settings: ['net_income', 'gross_income'],
  categories: ['match_multiplier', 'sort_order'],
  months: ['year', 'month'],
  budgets: ['year', 'month', 'expected', 'actual'],
  transactions: ['year', 'month', 'amount'],
  recurring_items: ['amount', 'day_of_month', 'sort_order'],
  accounts: ['balance', 'base_amount', 'sort_order'],
  ledger_entries: ['amount', 'sort_order'],
  net_worth_snapshots: ['net_worth', 'super_liquid', 'reconciliations'],
};

/** The jsonb accounts array of a snapshot: parsed if it arrives as text, balances as numbers. */
function normalizeSnapshotAccounts(v: unknown): unknown[] {
  let list = v;
  if (typeof list === 'string') {
    try {
      list = JSON.parse(list);
    } catch {
      list = [];
    }
  }
  if (!Array.isArray(list)) return [];
  return list.map((a: Record<string, unknown>) => ({ ...a, balance: Number(a.balance ?? 0), liquid: Boolean(a.liquid) }));
}

/** Coerces numeric columns to numbers (null stays null) and fills `deleted`. */
export function normalizeRow<T extends TableName>(table: T, raw: Record<string, unknown>): RowTypes[T] {
  const row: Record<string, unknown> = { ...raw };
  for (const col of NUMERIC[table] ?? []) {
    const v = row[col];
    if (v == null || v === '') row[col] = null;
    else if (typeof v !== 'number') row[col] = Number(v);
  }
  if (table === 'net_worth_snapshots') {
    row.accounts = normalizeSnapshotAccounts(row.accounts);
    if (typeof row.taken_on === 'string') row.taken_on = row.taken_on.slice(0, 10);
  }
  row.deleted = Boolean(row.deleted);
  return row as unknown as RowTypes[T];
}

/** Local identity of a row: id, or the natural key for months / budgets / settings. */
export function rowKey(table: TableName, row: object): string {
  const r = row as Record<string, unknown>;
  switch (table) {
    case 'settings':
      return 'settings';
    case 'months':
      return `${r.year}-${r.month}`;
    case 'budgets':
      return `${r.year}-${r.month}-${r.category_id}`;
    case 'net_worth_snapshots':
      return String(r.taken_on);
    default:
      return String(r.id);
  }
}

/** Only the client-writable columns of a row. */
export function pickColumns<T extends TableName>(table: T, row: RowTypes[T]): Partial<RowTypes[T]> {
  const out: Record<string, unknown> = {};
  const src = row as unknown as Record<string, unknown>;
  for (const col of COLUMNS[table]) if (col in src) out[col] = src[col];
  return out as Partial<RowTypes[T]>;
}
