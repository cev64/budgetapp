import type { Dataset, Settings } from './types';
import { DEFAULT_SETTINGS } from './types';
import { COLUMNS, LIST_TABLES, normalizeRow, rowKey, type ListTable, type RowTypes } from './tables';
import { live } from './calc';

// Backup file format and import-merge rules from docs/SYNC.md.

export interface BackupFile {
  app: 'budget';
  version: 1;
  exported_at: string;
  settings: Settings;
  categories: RowTypes['categories'][];
  months: RowTypes['months'][];
  budgets: RowTypes['budgets'][];
  transactions: RowTypes['transactions'][];
  recurring_items: RowTypes['recurring_items'][];
  accounts: RowTypes['accounts'][];
  ledger_entries: RowTypes['ledger_entries'][];
  /** Optional in files (older exports have none). */
  net_worth_snapshots: RowTypes['net_worth_snapshots'][];
}

function strip<T extends ListTable>(table: T, row: RowTypes[T]): RowTypes[T] {
  const out: Record<string, unknown> = {};
  const src = row as unknown as Record<string, unknown>;
  for (const col of COLUMNS[table]) if (col !== 'deleted') out[col] = src[col] ?? null;
  return out as unknown as RowTypes[T];
}

/** The export file: live rows only, without user_id / updated_at / deleted. */
export function buildBackup(ds: Dataset, now: Date = new Date()): BackupFile {
  const s = ds.settings;
  const file = {
    app: 'budget',
    version: 1,
    exported_at: now.toISOString().replace(/\.\d{3}Z$/, 'Z'),
    settings: { net_income: s.net_income, gross_income: s.gross_income, currency: s.currency },
  } as BackupFile;
  for (const t of LIST_TABLES) {
    (file as unknown as Record<string, unknown>)[t] = live(ds[t] as RowTypes[typeof t][]).map((r) => strip(t, r));
  }
  return file;
}

export class BackupError extends Error {}

/**
 * Validates and normalises a parsed backup file. Throws BackupError with a readable message.
 * Keys this app does not know are ignored, e.g. `meal_plans` / `meal_items` in files from before
 * meals were removed (docs/SYNC.md).
 */
export function parseBackup(json: unknown): BackupFile {
  if (!json || typeof json !== 'object') throw new BackupError('The file is not a JSON object.');
  const obj = json as Record<string, unknown>;
  if (obj.app !== 'budget') throw new BackupError('This is not a Budget backup file.');
  if (obj.version !== 1) throw new BackupError(`Unsupported backup version: ${String(obj.version)}.`);
  const settingsIn = (obj.settings ?? {}) as Partial<Settings>;
  const file = {
    app: 'budget',
    version: 1,
    exported_at: typeof obj.exported_at === 'string' ? obj.exported_at : '',
    // Only today's settings columns: older files may carry days_per_month (meals were removed).
    settings: normalizeRow('settings', {
      net_income: settingsIn.net_income ?? DEFAULT_SETTINGS.net_income,
      gross_income: settingsIn.gross_income ?? DEFAULT_SETTINGS.gross_income,
      currency: settingsIn.currency ?? DEFAULT_SETTINGS.currency,
    }),
  } as BackupFile;
  for (const t of LIST_TABLES) {
    const rows = obj[t] ?? [];
    if (!Array.isArray(rows)) throw new BackupError(`"${t}" must be a list.`);
    (file as unknown as Record<string, unknown>)[t] = rows.map((r) => {
      if (!r || typeof r !== 'object') throw new BackupError(`"${t}" contains an invalid row.`);
      return normalizeRow(t, r as Record<string, unknown>);
    });
  }
  return file;
}

export type ImportCounts = Record<ListTable, { added: number; updated: number }>;

export interface ImportPlan {
  settings: Settings;
  rows: { [T in ListTable]: RowTypes[T][] };
  counts: ImportCounts;
  /** File categories matched to existing ones by name. */
  matchedCategories: number;
}

/**
 * Merge plan (never wipes):
 * - categories match existing ones by name (case-insensitive); matched ids are remapped everywhere,
 *   unmatched categories are inserted;
 * - months / budgets upsert by natural key, everything else by id;
 * - settings from the file overwrite current settings.
 * Tombstoned rows in the file are skipped, and duplicate keys keep the last row.
 */
export function planImport(current: Dataset, file: BackupFile): ImportPlan {
  const byName = new Map(live(current.categories).map((c) => [c.name.trim().toLowerCase(), c.id]));
  const remap = new Map<string, string>();
  const newCategories: RowTypes['categories'][] = [];
  let matched = 0;
  for (const c of live(file.categories)) {
    const existing = byName.get(c.name.trim().toLowerCase());
    if (existing) {
      remap.set(c.id, existing);
      matched++;
    } else {
      newCategories.push(c);
      byName.set(c.name.trim().toLowerCase(), c.id);
    }
  }
  const cat = (id: string) => remap.get(id) ?? id;

  const mapped: { [T in ListTable]: RowTypes[T][] } = {
    categories: newCategories,
    months: live(file.months),
    budgets: live(file.budgets).map((b) => ({ ...b, category_id: cat(b.category_id) })),
    transactions: live(file.transactions).map((t) => ({ ...t, category_id: cat(t.category_id) })),
    recurring_items: live(file.recurring_items).map((r) => ({ ...r, category_id: cat(r.category_id) })),
    accounts: live(file.accounts).map((a) => ({
      ...a,
      linked_category_id: a.linked_category_id ? cat(a.linked_category_id) : null,
    })),
    ledger_entries: live(file.ledger_entries),
    // The only client write to this table: imported history is marked as such.
    net_worth_snapshots: live(file.net_worth_snapshots).map((n) => ({ ...n, source: 'import' as const })),
  };

  const counts = {} as ImportCounts;
  const rows = {} as ImportPlan['rows'];
  for (const t of LIST_TABLES) {
    const existing = new Set(live(current[t] as RowTypes[typeof t][]).map((r) => rowKey(t, r)));
    const unique = new Map<string, RowTypes[typeof t]>();
    for (const r of mapped[t] as RowTypes[typeof t][]) unique.set(rowKey(t, r), { ...r, deleted: false });
    let added = 0;
    for (const k of unique.keys()) if (!existing.has(k)) added++;
    counts[t] = { added, updated: unique.size - added };
    (rows as Record<string, unknown>)[t] = [...unique.values()];
  }
  return { settings: { ...file.settings, deleted: false }, rows, counts, matchedCategories: matched };
}

const NOUNS: Record<ListTable, [string, string]> = {
  categories: ['category', 'categories'],
  months: ['month', 'months'],
  budgets: ['budget line', 'budget lines'],
  transactions: ['transaction', 'transactions'],
  recurring_items: ['recurring item', 'recurring items'],
  accounts: ['account', 'accounts'],
  ledger_entries: ['ledger entry', 'ledger entries'],
  net_worth_snapshots: ['net worth snapshot', 'net worth snapshots'],
};

const DESCRIBE_ORDER: ListTable[] = [
  'months', 'transactions', 'budgets', 'categories', 'recurring_items',
  'accounts', 'ledger_entries', 'net_worth_snapshots',
];

const plural = (n: number, [one, many]: [string, string]) => `${n} ${n === 1 ? one : many}`;

/** "This will add 3 months, 11 transactions and update 2 budget lines." */
export function describeImport(plan: ImportPlan): string {
  const adds: string[] = [];
  const updates: string[] = [];
  for (const t of DESCRIBE_ORDER) {
    const { added, updated } = plan.counts[t];
    if (added) adds.push(plural(added, NOUNS[t]));
    if (updated) updates.push(plural(updated, NOUNS[t]));
  }
  const join = (xs: string[]) => (xs.length < 2 ? xs.join('') : `${xs.slice(0, -1).join(', ')} and ${xs[xs.length - 1]}`);
  const parts: string[] = [];
  if (adds.length) parts.push(`add ${join(adds)}`);
  if (updates.length) parts.push(`update ${join(updates)}`);
  const body = parts.length ? `This will ${parts.join(', and ')}.` : 'Nothing new to import.';
  return `${body} Settings are replaced by the file's settings.`;
}

/** The tables of a backup file as a Dataset (used by demo mode and tests). */
export function datasetFromBackup(file: BackupFile): Dataset {
  const { settings, categories, months, budgets, transactions, recurring_items, accounts, ledger_entries, net_worth_snapshots } = file;
  return { settings, categories, months, budgets, transactions, recurring_items, accounts, ledger_entries, net_worth_snapshots };
}
