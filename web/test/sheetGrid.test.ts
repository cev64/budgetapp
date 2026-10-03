import { readFileSync } from 'node:fs';
import { describe, expect, it, vi } from 'vitest';
import { buildMonthGrid, buildSummaryGrid, type Cell, type Grid, type SheetDeps } from '../src/screens/sheetGrid';
import { createCalc } from '../src/domain/calc';
import { datasetFromBackup, parseBackup } from '../src/domain/backup';
import { computeNetWorth } from '../src/domain/networth';
import { formatMoney } from '../src/domain/format';

const ds = datasetFromBackup(parseBackup(JSON.parse(readFileSync(new URL('../../docs/fixtures/sample-backup.json', import.meta.url), 'utf8'))));
const calc = createCalc(ds);
const id = (name: string) => calc.categories.find((c) => c.name === name)!.id;

function deps(over: Partial<SheetDeps> = {}) {
  const actions = {
    setBudget: vi.fn(async () => true), setMonthClosed: vi.fn(async () => true),
    saveTransaction: vi.fn(async () => true), deleteTransaction: vi.fn(async () => true),
    saveAccount: vi.fn(async () => true), saveLedgerEntry: vi.fn(async () => true), deleteLedgerEntry: vi.fn(async () => true),
    newLedgerEntry: vi.fn(() => ({ id: 'new', name: '', amount: 0, note: null, settled: false, sort_order: 9 })),
  };
  const d: SheetDeps = {
    calc, money: (v) => formatMoney(v), actions: actions as unknown as SheetDeps['actions'], newId: () => 'tx-new',
    overriding: new Set(), setOverriding: vi.fn(), notify: vi.fn(), moveToMonth: vi.fn(), ...over,
  };
  return { d, actions };
}

/** Cell by sheet column letter and spreadsheet row number. */
// The fixture has 8 categories (rows 3 · 5–8 · 10–12 · totals 14–16 · closed 17); the 14-category
// layout landing on the sheet's exact rows is covered in sheet.test.ts.
const at = (g: Grid, col: string, row: number): Cell => g.rows[row - 2]![g.columns.findIndex((c) => c.key === col)]!;

describe('month grid (SHEET_VIEW month tab)', () => {
  const nov = { year: 2025, month: 11 };

  it('editing an Expected cell calls setBudget for that month and category', () => {
    const { d, actions } = deps();
    const g = buildMonthGrid(d, nov);
    // Fixture order: Paychecks [3], Rent [5], Food [6] …
    expect(at(g, 'B', 6).text).toBe('Food');
    const cell = at(g, 'C', 6);
    expect(cell.edit?.initial).toBe('400');
    expect(cell.edit!.commit('450.5')).toBe(true);
    expect(actions.setBudget).toHaveBeenCalledWith(nov, id('Food'), { expected: 450.5 });
    expect(cell.edit!.commit('abc')).toBe(false);
    expect(actions.setBudget).toHaveBeenCalledTimes(1);
  });

  it('manual actuals are editable (blank = null); ledger actuals are computed with an override menu', () => {
    const { d, actions } = deps();
    const g = buildMonthGrid(d, { year: 2025, month: 12 });
    const util = at(g, 'D', 8); // Utilities, Dec 2025 actual 104.5
    expect(at(g, 'B', 8).text).toBe('Utilities');
    util.edit!.commit('');
    expect(actions.setBudget).toHaveBeenCalledWith({ year: 2025, month: 12 }, id('Utilities'), { actual: null });
    const food = at(g, 'D', 6);
    expect(food.computed).toBe(true);
    expect(food.edit).toBeUndefined();
    expect(food.menu!.map((m) => m.label)).toEqual(['Override actual']);
    food.menu![0]!.run();
    expect(d.setOverriding).toHaveBeenCalledWith(id('Food'), true);
  });

  it('an active override is editable, marked, and can be cleared', () => {
    const { d, actions } = deps();
    const pay = at(buildMonthGrid(d, nov), 'D', 3); // Paychecks typed 4100 over a 4050 ledger sum
    expect(pay.marker).toBe('override');
    expect(pay.text).toBe('$4,100');
    pay.menu!.find((m) => m.label === 'Clear override')!.run();
    expect(actions.setBudget).toHaveBeenCalledWith(nov, id('Paychecks'), { actual: null });
  });

  it('totals rows and the Closed checkbox under them', () => {
    const { d, actions } = deps();
    const g = buildMonthGrid(d, nov);
    expect([14, 15, 16].map((r) => at(g, 'B', r).text)).toEqual(['Monthly Expenses', 'Saved (Roth, 401k, Brokerage)', 'Leftover']);
    expect(at(g, 'D', 16).text).toBe('$1,520'); // 1519.5 → whole dollars (§8)
    expect(at(g, 'E', 16).tone).toBe('good');
    expect(at(g, 'B', 17).text).toBe('Month closed');
    const closed = at(g, 'C', 17);
    expect(closed.check!.checked).toBe(true);
    closed.check!.toggle();
    expect(actions.setMonthClosed).toHaveBeenCalledWith(nov, false);
  });

  it('ledger blocks sit in G/K/O with title, header, rows and an add row', () => {
    const { d, actions } = deps();
    const g = buildMonthGrid(d, nov);
    expect(at(g, 'G', 2).text).toBe('Paychecks');
    expect([at(g, 'G', 3).text, at(g, 'H', 3).text, at(g, 'I', 3).text]).toEqual(['Date', 'Item', 'Amount']);
    expect(at(g, 'K', 2).text).toBe('Food');
    expect(at(g, 'O', 2).text).toBe('Fun');
    // Food: dated "dinner" (Oct 30) before the undated "groceries"
    expect([at(g, 'L', 4).text, at(g, 'L', 5).text]).toEqual(['dinner', 'groceries']);
    // The Paychecks block (2 txs) spans rows 2–6; the fixture has no Subscriptions/Misc, so G7 stays empty
    expect(at(g, 'H', 6).text).toBe('+ Add');
    expect(at(g, 'G', 7).kind).toBe('void');
    // Add row under Fun's 2 rows (title 2, header 3, rows 4–5, add 6)
    at(g, 'P', 6).edit!.commit('Movie');
    expect(actions.saveTransaction).toHaveBeenCalledWith(expect.objectContaining({ id: 'tx-new', item: 'Movie', category_id: id('Fun'), year: 2025, month: 11, amount: 0 }));
  });

  it('deleting a ledger row tombstones it and offers undo', async () => {
    const { d, actions } = deps();
    const cell = at(buildMonthGrid(d, nov), 'Q', 4);
    cell.menu!.find((m) => m.label === 'Delete')!.run();
    await Promise.resolve();
    await Promise.resolve();
    expect(actions.deleteTransaction).toHaveBeenCalled();
    const [, undo] = (d.notify as ReturnType<typeof vi.fn>).mock.calls[0]!;
    (undo as () => void)();
    expect(actions.saveTransaction).toHaveBeenCalledWith(expect.objectContaining({ deleted: false }));
  });
});

describe('summary grid (SHEET_VIEW summary tab)', () => {
  const summary = calc.yearSummary(2025)!;
  const nw = computeNetWorth({ accounts: ds.accounts, categories: ds.categories, budgets: ds.budgets, ledger: ds.ledger_entries });

  it('the Leftover row Difference is the leftover vs plan; savings-rate rows follow', () => {
    const { d } = deps();
    const g = buildSummaryGrid(d, summary, nw, ds.ledger_entries);
    expect(at(g, 'B', 16).text).toBe('Leftover');
    expect(at(g, 'E', 16).text).toBe(formatMoney(summary.leftoverVsPlan));
    expect(at(g, 'E', 16).text).toBe('$261.75');
    expect([18, 19, 20].map((r) => at(g, 'B', r).text)).toEqual(['Annualized Savings', 'Percent of Net Income', 'Percent of Gross Income']);
    expect(at(g, 'D', 19).text).toBe('48.8%');
  });

  it('net worth G/H and ledger J/K, totals at row 14, Super Liquid at 16', () => {
    const { d, actions } = deps();
    const g = buildSummaryGrid(d, summary, nw, ds.ledger_entries);
    expect(at(g, 'G', 3).text).toBe('Checking');
    expect(at(g, 'G', 14).text).toBe('Net Worth');
    expect(at(g, 'H', 14).text).toBe('$14,584');
    expect(at(g, 'G', 16).text).toBe('Super Liquid Assets');
    const linked = g.rows.flat().find((c) => c.marker === 'auto');
    expect(linked?.computed).toBe(true);
    at(g, 'H', 3).edit!.commit('2600');
    expect(actions.saveAccount).toHaveBeenCalledWith(expect.objectContaining({ name: 'Checking', balance: 2600 }));
    // Two unsettled entries, then the add row, then Net Reconciliations at row 14
    expect([at(g, 'J', 3).text, at(g, 'J', 4).text, at(g, 'J', 5).text]).toEqual(['Friend owes', 'I owe', '+ Add']);
    expect(at(g, 'J', 14).text).toBe('Net Reconciliations');
    at(g, 'J', 3).menu!.find((m) => m.label === 'Settle')!.run();
    expect(actions.saveLedgerEntry).toHaveBeenCalledWith(expect.objectContaining({ name: 'Friend owes', settled: true }));
  });
});
