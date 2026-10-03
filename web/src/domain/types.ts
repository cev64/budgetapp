// Row types mirror supabase/migrations/*.sql (20261003002806_init.sql and later migrations) and the backup format in docs/SYNC.md.
// `user_id`, `updated_at` and `deleted` are optional because backups omit them.

export type CategoryKind = 'income' | 'expense' | 'savings';
export type Tracking = 'ledger' | 'manual';
export type AccountGroup = 'cash' | 'investment' | 'asset' | 'debt';

interface SyncFields {
  user_id?: string;
  updated_at?: string;
  deleted?: boolean;
}

export interface Settings extends SyncFields {
  net_income: number;
  gross_income: number;
  currency: string;
}

export interface Category extends SyncFields {
  id: string;
  name: string;
  kind: CategoryKind;
  tracking: Tracking;
  match_multiplier: number;
  sort_order: number;
  icon: string | null;
  color: string | null;
  archived: boolean;
}

export interface Month extends SyncFields {
  year: number;
  month: number;
  closed: boolean;
  note: string | null;
}

export interface Budget extends SyncFields {
  year: number;
  month: number;
  category_id: string;
  expected: number | null;
  actual: number | null;
}

export interface Transaction extends SyncFields {
  id: string;
  year: number;
  month: number;
  category_id: string;
  date: string | null;
  item: string;
  amount: number;
  note: string | null;
  created_at?: string;
}

export interface RecurringItem extends SyncFields {
  id: string;
  category_id: string;
  item: string;
  amount: number;
  day_of_month: number | null;
  active: boolean;
  sort_order: number;
}

export interface Account extends SyncFields {
  id: string;
  name: string;
  account_group: AccountGroup;
  liquid: boolean;
  balance: number;
  linked_category_id: string | null;
  base_amount: number;
  sort_order: number;
  archived: boolean;
}

export interface LedgerEntry extends SyncFields {
  id: string;
  name: string;
  amount: number;
  note: string | null;
  settled: boolean;
  sort_order: number;
}

/** One account inside a net worth snapshot (DOMAIN_RULES §5b). */
export interface SnapshotAccount {
  id: string;
  name: string;
  group: AccountGroup;
  liquid: boolean;
  balance: number;
}

/** One row per user per America/New_York day, computed server-side. Pull-only for clients. */
export interface NetWorthSnapshot extends SyncFields {
  taken_on: string;
  net_worth: number;
  super_liquid: number;
  reconciliations: number;
  accounts: SnapshotAccount[];
  source: 'auto' | 'manual' | 'import';
}

/** Every table of the budget, as plain arrays. Rows may include tombstones. */
export interface Dataset {
  settings: Settings;
  categories: Category[];
  months: Month[];
  budgets: Budget[];
  transactions: Transaction[];
  recurring_items: RecurringItem[];
  accounts: Account[];
  ledger_entries: LedgerEntry[];
  net_worth_snapshots: NetWorthSnapshot[];
}

export const DEFAULT_SETTINGS: Settings = {
  net_income: 68000,
  gross_income: 85000,
  currency: 'USD',
};

/** A (year, month) pair. month is 1–12. */
export interface YM {
  year: number;
  month: number;
}
