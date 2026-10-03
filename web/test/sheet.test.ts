import { describe, expect, it } from 'vitest';
import { blockHeight, ledgerColumns, leftRows, sortLedger } from '../src/domain/sheet';
import type { Category, CategoryKind, Transaction } from '../src/domain/types';

const DEFAULTS: [string, CategoryKind, 'ledger' | 'manual'][] = [
  ['Paychecks', 'income', 'ledger'], ['Rent', 'expense', 'manual'], ['Subscriptions', 'expense', 'ledger'],
  ['Food', 'expense', 'ledger'], ['Fun', 'expense', 'ledger'], ['Gas', 'expense', 'ledger'], ['Misc', 'expense', 'ledger'],
  ['Car Ins', 'expense', 'manual'], ['Utilities', 'expense', 'manual'], ['Phone Bill', 'expense', 'manual'],
  ['Roth', 'savings', 'manual'], ['401k', 'savings', 'manual'], ['Taxable Brokerage', 'savings', 'manual'], ['HSA', 'savings', 'manual'],
];
const cats: Category[] = DEFAULTS.map(([name, kind, tracking], i) => ({
  id: name.toLowerCase().replace(/\W/g, ''), name, kind, tracking, match_multiplier: name === '401k' ? 2 : 1,
  sort_order: i, icon: null, color: null, archived: false,
}));
const byRow = (rows: ReturnType<typeof leftRows>) =>
  Object.fromEntries(rows.map((r) => [r.row, r.type === 'category' ? r.category.name : r.type === 'total' ? r.total : r.type]));

describe('left block rows (SHEET_VIEW month tab)', () => {
  it('default categories land on the sheet rows', () => {
    const m = byRow(leftRows(cats, 'month'));
    expect(m[2]).toBe('header');
    expect(m[3]).toBe('Paychecks');
    expect(m[4]).toBe('spacer');
    expect([5, 6, 7, 8, 9, 10, 11, 12, 13].map((r) => m[r])).toEqual(
      ['Rent', 'Subscriptions', 'Food', 'Fun', 'Gas', 'Misc', 'Car Ins', 'Utilities', 'Phone Bill']);
    expect(m[14]).toBe('spacer');
    expect([15, 16, 17, 18].map((r) => m[r])).toEqual(['Roth', '401k', 'Taxable Brokerage', 'HSA']);
    expect(m[19]).toBe('spacer');
    expect([20, 21, 22].map((r) => m[r])).toEqual(['expenses', 'saved', 'leftover']);
    expect(m[23]).toBe('closed');
  });

  it('Summary adds a blank row and the savings-rate rows 24–26', () => {
    const m = byRow(leftRows(cats, 'summary'));
    expect(m[22]).toBe('leftover');
    expect(m[23]).toBe('spacer');
    expect([24, 25, 26].map((r) => m[r])).toEqual(['annualized', 'pctNet', 'pctGross']);
  });

  it('groups by kind in the given order; an extra category pushes the rows below it down', () => {
    const extra: Category = { ...cats[1]!, id: 'pets', name: 'Pets', sort_order: 99 };
    const m = byRow(leftRows([...cats, extra], 'month'));
    expect(m[14]).toBe('Pets');
    expect(m[15]).toBe('spacer');
    expect(m[24]).toBe('closed');
  });
});

describe('ledger columns (G H I · K L M · O P Q)', () => {
  const none = () => 0;
  it('known blocks go where the sheet had them; manual categories get no block', () => {
    const cols = ledgerColumns(cats, none).map((c) => c.map((x) => x.name));
    expect(cols).toEqual([['Paychecks', 'Subscriptions', 'Misc'], ['Food', 'Gas'], ['Fun']]);
  });

  it('matches names case-insensitively and skips missing ones', () => {
    const renamed = cats.filter((c) => c.name !== 'Gas').map((c) => (c.name === 'Food' ? { ...c, name: 'FOOD' } : c));
    expect(ledgerColumns(renamed, none).map((c) => c.map((x) => x.name))).toEqual([['Paychecks', 'Subscriptions', 'Misc'], ['FOOD'], ['Fun']]);
  });

  it('unknown ledger categories go to the bottom of the shortest column', () => {
    const counts: Record<string, number> = { paychecks: 2, subscriptions: 5, misc: 3, food: 12, gas: 2, fun: 1 };
    const a: Category = { ...cats[3]!, id: 'pets', name: 'Pets', sort_order: 50 };
    const b: Category = { ...cats[3]!, id: 'gifts', name: 'Gifts', sort_order: 51 };
    // Heights: G = 5+8+6 +2 blanks = 21, K = 15+5+1 = 21, O = 4 → Pets goes to O (4 → 4+1+3 = 8), Gifts to O again (8 < 21).
    const cols = ledgerColumns([...cats, a, b], (id) => counts[id] ?? 0).map((c) => c.map((x) => x.name));
    expect(cols[2]).toEqual(['Fun', 'Pets', 'Gifts']);
    expect(blockHeight(0)).toBe(3);
  });

  it('ties go to the leftmost column', () => {
    const only: Category[] = [{ ...cats[3]!, id: 'x', name: 'X' }];
    expect(ledgerColumns(only, none).map((c) => c.length)).toEqual([1, 0, 0]);
  });
});

describe('ledger row order', () => {
  const t = (id: string, date: string | null, created_at?: string): Transaction =>
    ({ id, year: 2026, month: 1, category_id: 'food', date, item: id, amount: 1, note: null, created_at });
  it('date ascending, undated last, then creation order (unsynced newest)', () => {
    const rows = [
      t('u2', null, '2026-01-03T00:00:00Z'), t('d2', '2026-01-09'), t('u1', null, '2026-01-01T00:00:00Z'),
      t('local', null), t('d1', '2026-01-02', '2026-01-05T00:00:00Z'), t('d1b', '2026-01-02', '2026-01-04T00:00:00Z'),
    ];
    expect(sortLedger(rows).map((x) => x.id)).toEqual(['d1b', 'd1', 'd2', 'u1', 'u2', 'local']);
  });
});
