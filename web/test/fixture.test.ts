import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { createCalc, totalsFor, type Totals } from '../src/domain/calc';
import { datasetFromBackup, parseBackup } from '../src/domain/backup';
import { computeNetWorth } from '../src/domain/networth';

// Every number in docs/fixtures/expected.json (produced by tools/reference_calc.py)
// must be reproduced by the web domain layer.

const read = (p: string) => JSON.parse(readFileSync(new URL(p, import.meta.url), 'utf8'));
const backup = parseBackup(read('../../docs/fixtures/sample-backup.json'));
const expected = read('../../docs/fixtures/expected.json') as {
  months: Record<string, {
    closed: boolean;
    categories: Record<string, { expected: number; actual: number | null }>;
    expected: Totals;
    actual: Totals;
  }>;
  years: Record<string, Record<'expected' | 'actual', Totals & {
    annualized_savings: number;
    pct_net: number;
    pct_gross: number;
    categories: Record<string, number>;
  }>>;
  net_worth: { accounts: Record<string, number>; net_reconciliations: number; net_worth: number; super_liquid: number };
};

const ds = datasetFromBackup(backup);
const calc = createCalc(ds);
const catByName = new Map(calc.categories.map((c) => [c.name, c]));
const TOTAL_KEYS = ['income', 'expenses', 'saved', 'contributions', 'leftover'] as const;

function expectTotals(actual: Totals, want: Totals) {
  for (const k of TOTAL_KEYS) expect(actual[k], k).toBeCloseTo(want[k], 4);
}

describe('months (DOMAIN_RULES §1–§2)', () => {
  for (const [key, want] of Object.entries(expected.months)) {
    const [year, month] = key.split('-').map(Number) as [number, number];
    const ym = { year, month };

    it(`${key}: closed flag`, () => {
      expect(calc.month(ym)?.closed).toBe(want.closed);
    });

    it(`${key}: per-category expected and actual`, () => {
      for (const [name, v] of Object.entries(want.categories)) {
        const c = catByName.get(name)!;
        expect(c, name).toBeDefined();
        expect(calc.expected(ym, c.id), `${name} expected`).toBeCloseTo(v.expected, 4);
        const a = calc.actual(ym, c.id);
        if (v.actual === null) expect(a, `${name} actual`).toBeNull();
        else expect(a, `${name} actual`).toBeCloseTo(v.actual, 4);
      }
    });

    it(`${key}: totals`, () => {
      const s = calc.monthSummary(ym);
      expectTotals(s.expected, want.expected);
      expectTotals(s.actual, want.actual);
    });
  }

  it('lines carry differences and the override marker', () => {
    const nov = calc.monthSummary({ year: 2025, month: 11 });
    const pay = nov.lines.find((l) => l.category.name === 'Paychecks')!;
    expect(pay.override).toBe(true);
    expect(pay.ledgerSum).toBe(4050);
    expect(pay.difference).toBe(100);
    const fun = nov.lines.find((l) => l.category.name === 'Fun')!;
    expect(fun.override).toBe(false);
    expect(fun.actual).toBe(150);
    expect(fun.difference).toBe(-150);
  });
});

describe('years (DOMAIN_RULES §3–§4)', () => {
  for (const [y, cols] of Object.entries(expected.years)) {
    for (const col of ['expected', 'actual'] as const) {
      it(`${y} ${col} column`, () => {
        const s = calc.yearSummary(Number(y))!;
        const want = cols[col];
        const got = s[col];
        expectTotals(got, want);
        expect(got.annualizedSavings).toBeCloseTo(want.annualized_savings, 4);
        expect(got.pctNet).toBeCloseTo(want.pct_net, 6);
        expect(got.pctGross).toBeCloseTo(want.pct_gross, 6);
        for (const [name, v] of Object.entries(want.categories)) {
          const line = s.lines.find((l) => l.category.name === name)!;
          expect(line, name).toBeDefined();
          expect(line[col], `${name} ${col}`).toBeCloseTo(v, 4);
        }
      });
    }
  }

  it('returns null for a year without months', () => {
    expect(calc.yearSummary(2030)).toBeNull();
  });

  it('closing a month switches its contribution from expected to actual', () => {
    const open = createCalc({ ...ds, months: ds.months.map((m) => (m.year === 2026 ? { ...m, closed: true } : m)) });
    const y = open.yearSummary(2026)!;
    // Jan 2026 closed: Paychecks actual 2100 replaces 4200; Food 120 replaces 450; Rent 1250 = 1250.
    expect(y.lines.find((l) => l.category.name === 'Paychecks')!.actual).toBe(2100);
    expect(y.lines.find((l) => l.category.name === 'Food')!.actual).toBe(120);
    expect(y.actual.income).toBe(2100);
  });
});

describe('net worth (DOMAIN_RULES §5)', () => {
  const nw = computeNetWorth({ accounts: ds.accounts, categories: ds.categories, budgets: ds.budgets, ledger: ds.ledger_entries });
  const want = expected.net_worth;

  it('account balances (linked accounts computed)', () => {
    const got = Object.fromEntries(nw.accounts.map((v) => [v.account.name, v.balance]));
    expect(Object.keys(got).sort()).toEqual(Object.keys(want.accounts).sort());
    for (const [name, v] of Object.entries(want.accounts)) expect(got[name], name).toBeCloseTo(v, 4);
  });

  it('totals', () => {
    expect(nw.netReconciliations).toBeCloseTo(want.net_reconciliations, 4);
    expect(nw.netWorth).toBeCloseTo(want.net_worth, 4);
    expect(nw.superLiquid).toBeCloseTo(want.super_liquid, 4);
  });
});

describe('sheet reference example (DOMAIN_RULES §2)', () => {
  it('July 2026 expected leftover = 1059.33', () => {
    const cats = [
      { id: 'p', kind: 'income', match_multiplier: 1 },
      { id: 'e', kind: 'expense', match_multiplier: 1 },
      { id: 'r', kind: 'savings', match_multiplier: 1 },
      { id: 'k', kind: 'savings', match_multiplier: 2 },
      { id: 'b', kind: 'savings', match_multiplier: 1 },
      { id: 'h', kind: 'savings', match_multiplier: 1 },
    ] as unknown as Parameters<typeof totalsFor>[0];
    const v: Record<string, number> = { p: 5275, e: 2900.67, r: 625, k: 215, b: 400, h: 75 };
    const t = totalsFor(cats, (c) => v[c.id] ?? 0);
    expect(t.leftover).toBeCloseTo(1059.33, 6);
    expect(t.saved).toBeCloseTo(625 + 430 + 400 + 75, 6);
  });
});
