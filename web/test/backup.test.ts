import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { BackupError, buildBackup, datasetFromBackup, describeImport, parseBackup, planImport } from '../src/domain/backup';
import { DEFAULT_SETTINGS, type Dataset } from '../src/domain/types';

const raw = () => JSON.parse(readFileSync(new URL('../../docs/fixtures/sample-backup.json', import.meta.url), 'utf8'));

const empty = (): Dataset => ({
  settings: { ...DEFAULT_SETTINGS },
  categories: [
    { id: 'mine-food', name: 'food', kind: 'expense', tracking: 'ledger', match_multiplier: 1, sort_order: 12, icon: null, color: null, archived: false },
    { id: 'mine-401k', name: '401K', kind: 'savings', tracking: 'manual', match_multiplier: 2, sort_order: 21, icon: null, color: null, archived: false },
  ],
  months: [],
  budgets: [],
  transactions: [],
  recurring_items: [],
  accounts: [],
  ledger_entries: [],
  net_worth_snapshots: [],
});

describe('parseBackup', () => {
  it('accepts the fixture', () => {
    const f = parseBackup(raw());
    expect(f.categories).toHaveLength(8);
    expect(f.settings.net_income).toBe(60000);
  });
  it('rejects other files', () => {
    expect(() => parseBackup({ app: 'other' })).toThrow(BackupError);
    expect(() => parseBackup({ app: 'budget', version: 2 })).toThrow(/version/);
    expect(() => parseBackup({ app: 'budget', version: 1, months: 'x' })).toThrow(/list/);
  });
  it('coerces numeric strings', () => {
    const f = parseBackup({ app: 'budget', version: 1, budgets: [{ year: '2026', month: '1', category_id: 'c', expected: '12.5', actual: null }] });
    expect(f.budgets[0]).toMatchObject({ year: 2026, month: 1, expected: 12.5, actual: null });
  });
});

describe('planImport (docs/SYNC.md merge rules)', () => {
  const file = parseBackup(raw());

  it('matches categories by name case-insensitively and remaps ids', () => {
    const plan = planImport(empty(), file);
    expect(plan.matchedCategories).toBe(2);
    expect(plan.rows.categories.map((c) => c.name)).not.toContain('Food');
    expect(plan.rows.categories).toHaveLength(6);
    const foodId = file.categories.find((c) => c.name === 'Food')!.id;
    expect(plan.rows.transactions.some((t) => t.category_id === foodId)).toBe(false);
    expect(plan.rows.transactions.filter((t) => t.category_id === 'mine-food')).toHaveLength(4);
    expect(plan.rows.budgets.filter((b) => b.category_id === 'mine-food')).toHaveLength(3);
    const k401 = plan.rows.accounts.find((a) => a.name === '401k')!;
    expect(k401.linked_category_id).toBe('mine-401k');
  });

  it('skips tombstones', () => {
    const plan = planImport(empty(), file);
    // 12 rows in the file, 1 of them a tombstone.
    expect(plan.rows.transactions).toHaveLength(11);
    expect(plan.counts.transactions).toEqual({ added: 11, updated: 0 });
  });

  it('collapses duplicate keys, last row wins', () => {
    const dup = { ...file, transactions: [...file.transactions, { ...file.transactions[0]!, amount: 1 }] };
    const plan = planImport(empty(), dup);
    expect(plan.rows.transactions).toHaveLength(11);
    expect(plan.rows.transactions.find((t) => t.id === file.transactions[0]!.id)!.amount).toBe(1);
  });

  it('counts updates for rows that already exist', () => {
    const current = datasetFromBackup(file);
    const plan = planImport(current, file);
    expect(plan.matchedCategories).toBe(8);
    expect(plan.counts.months).toEqual({ added: 0, updated: 3 });
    expect(plan.counts.categories).toEqual({ added: 0, updated: 0 });
    expect(describeImport(plan)).toMatch(/^This will update 3 months/);
  });

  it('describes additions', () => {
    const text = describeImport(planImport(empty(), file));
    expect(text).toContain('This will add 3 months, 11 transactions, 24 budget lines, 6 categories, 1 recurring item');
    expect(text).toContain('Settings are replaced');
  });
});

describe('files from before meals were removed', () => {
  it('ignores meal_plans / meal_items and days_per_month', () => {
    const old = { ...raw(), settings: { ...raw().settings, days_per_month: 30.5 },
      meal_plans: [{ id: 'p', name: 'Day', kind: 'day', label: null, note: null, sort_order: 0 }],
      meal_items: [{ id: 'i', plan_id: 'p', name: 'Oats', calories: 375, sort_order: 0 }] };
    const f = parseBackup(old);
    expect(Object.keys(f)).not.toContain('meal_plans');
    expect(f.settings).toEqual({ net_income: 60000, gross_income: 80000, currency: 'USD', deleted: false });
    const plan = planImport(empty(), f);
    expect(Object.keys(plan.rows)).not.toContain('meal_items');
    expect(buildBackup(datasetFromBackup(f)).settings).toEqual({ net_income: 60000, gross_income: 80000, currency: 'USD' });
  });
});

describe('net worth snapshots in backups', () => {
  const snapshot = { taken_on: '2026-10-02', net_worth: '22560', super_liquid: 6748, reconciliations: -5,
    accounts: [{ id: 'x', name: 'SoFi', group: 'cash', liquid: true, balance: '5780' }], source: 'auto' };

  it('are optional and parsed with numeric values', () => {
    expect(parseBackup(raw()).net_worth_snapshots).toEqual([]);
    const f = parseBackup({ ...raw(), net_worth_snapshots: [snapshot] });
    expect(f.net_worth_snapshots[0]).toMatchObject({ taken_on: '2026-10-02', net_worth: 22560, accounts: [{ balance: 5780 }] });
  });

  it('import upserts them by day with source "import"', () => {
    const plan = planImport(empty(), parseBackup({ ...raw(), net_worth_snapshots: [snapshot] }));
    expect(plan.rows.net_worth_snapshots).toHaveLength(1);
    expect(plan.rows.net_worth_snapshots[0]!.source).toBe('import');
    expect(plan.counts.net_worth_snapshots).toEqual({ added: 1, updated: 0 });
  });

  it('export includes them without sync columns', () => {
    const ds = datasetFromBackup(parseBackup({ ...raw(), net_worth_snapshots: [snapshot] }));
    const out = buildBackup(ds);
    expect(out.net_worth_snapshots).toEqual([
      { taken_on: '2026-10-02', net_worth: 22560, super_liquid: 6748, reconciliations: -5,
        accounts: [{ id: 'x', name: 'SoFi', group: 'cash', liquid: true, balance: 5780 }], source: 'auto' },
    ]);
  });
});

describe('buildBackup', () => {
  it('round-trips through parseBackup without tombstones or sync columns', () => {
    const ds = datasetFromBackup(parseBackup(raw()));
    ds.months = ds.months.map((m) => ({ ...m, user_id: 'u', updated_at: '2026-01-01T00:00:00Z' }));
    const out = buildBackup(ds, new Date('2026-10-02T12:00:00.123Z'));
    expect(out.exported_at).toBe('2026-10-02T12:00:00Z');
    expect(out.transactions).toHaveLength(11);
    expect(out.months[0]).toEqual({ year: 2025, month: 11, closed: true, note: null });
    const again = parseBackup(JSON.parse(JSON.stringify(out)));
    expect(again.categories).toEqual(parseBackup(raw()).categories.map((c) => ({ ...c, deleted: false })));
  });
});
