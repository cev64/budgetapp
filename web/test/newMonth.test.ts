import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { datasetFromBackup, parseBackup } from '../src/domain/backup';
import { planNewMonth, suggestedMonth } from '../src/domain/newMonth';
import { clampedDate, daysInMonth } from '../src/domain/dates';
import type { Dataset } from '../src/domain/types';

const fixture = () =>
  datasetFromBackup(parseBackup(JSON.parse(readFileSync(new URL('../../docs/fixtures/sample-backup.json', import.meta.url), 'utf8'))));

let n = 0;
const newId = () => `id-${++n}`;
const name = (ds: Dataset, id: string) => ds.categories.find((c) => c.id === id)!.name;

describe('planNewMonth (DOMAIN_RULES §6)', () => {
  it('does nothing when the month exists', () => {
    expect(planNewMonth(fixture(), { year: 2026, month: 1 }, newId)).toBeNull();
  });

  it('copies expected from the most recent earlier month with a budget row', () => {
    const ds = fixture();
    const plan = planNewMonth(ds, { year: 2026, month: 2 }, newId)!;
    expect(plan.month).toMatchObject({ year: 2026, month: 2, closed: false });
    const byName = Object.fromEntries(plan.budgets.map((b) => [name(ds, b.category_id), b]));
    expect(byName.Paychecks!.expected).toBe(4200);
    expect(byName.Rent!.expected).toBe(1250);
    expect(byName['401k']!.expected).toBe(350);
    // Jan 2026 HSA row has expected = null: nothing to copy.
    expect(byName.HSA).toBeUndefined();
    for (const b of plan.budgets) expect(b.actual).toBeNull();
  });

  it('skips archived categories', () => {
    const ds = fixture();
    ds.categories = ds.categories.map((c) => (c.name === 'Rent' ? { ...c, archived: true } : c));
    const plan = planNewMonth(ds, { year: 2026, month: 2 }, newId)!;
    expect(plan.budgets.some((b) => name(ds, b.category_id) === 'Rent')).toBe(false);
  });

  it('uses the latest earlier month, not a later one', () => {
    const ds = fixture();
    // A month created between existing ones copies from the month before it, never from a later one.
    ds.months = ds.months.filter((m) => !(m.year === 2025 && m.month === 12));
    const plan = planNewMonth(ds, { year: 2025, month: 12 }, newId)!;
    const rent = plan.budgets.find((b) => name(ds, b.category_id) === 'Rent')!;
    expect(rent.expected).toBe(1200);
  });

  it('adds active recurring items with the day clamped to the month length', () => {
    const ds = fixture();
    const feb = planNewMonth(ds, { year: 2026, month: 2 }, newId)!;
    expect(feb.transactions).toHaveLength(1);
    expect(feb.transactions[0]).toMatchObject({ item: 'Streaming', amount: 15.99, date: '2026-02-28', year: 2026, month: 2 });

    const febLeap = planNewMonth(ds, { year: 2028, month: 2 }, newId)!;
    expect(febLeap.transactions[0]!.date).toBe('2028-02-29');
    const apr = planNewMonth(ds, { year: 2026, month: 4 }, newId)!;
    expect(apr.transactions[0]!.date).toBe('2026-04-30');
    const mar = planNewMonth(ds, { year: 2026, month: 3 }, newId)!;
    expect(mar.transactions[0]!.date).toBe('2026-03-31');
  });

  it('null day gives a null date, inactive items are skipped, ids are fresh', () => {
    const ds = fixture();
    const base = ds.recurring_items[0]!;
    ds.recurring_items = [
      { ...base, id: 'r1', day_of_month: null, item: 'No day' },
      { ...base, id: 'r2', active: false, item: 'Paused' },
      { ...base, id: 'r3', deleted: true, item: 'Gone' },
    ];
    const plan = planNewMonth(ds, { year: 2026, month: 2 }, newId)!;
    expect(plan.transactions.map((t) => t.item)).toEqual(['No day']);
    expect(plan.transactions[0]!.date).toBeNull();
    expect(plan.transactions[0]!.id).toMatch(/^id-/);
  });

  it('re-creates a tombstoned month', () => {
    const ds = fixture();
    ds.months = ds.months.map((m) => (m.year === 2026 ? { ...m, deleted: true } : m));
    expect(planNewMonth(ds, { year: 2026, month: 1 }, newId)).not.toBeNull();
  });
});

describe('dates', () => {
  it('daysInMonth and clamping', () => {
    expect(daysInMonth(2026, 2)).toBe(28);
    expect(daysInMonth(2024, 2)).toBe(29);
    expect(daysInMonth(2026, 12)).toBe(31);
    expect(clampedDate(2026, 9, 31)).toBe('2026-09-30');
    expect(clampedDate(2026, 1, 5)).toBe('2026-01-05');
  });
});

describe('suggestedMonth', () => {
  const months = fixture().months;
  it('offers the current calendar month when it does not exist', () => {
    expect(suggestedMonth(months, { year: 2026, month: 10 })).toEqual({ year: 2026, month: 10 });
  });
  it('offers the next month when the latest month is the current one', () => {
    expect(suggestedMonth(months, { year: 2026, month: 1 })).toEqual({ year: 2026, month: 2 });
  });
  it('offers nothing when a later month exists', () => {
    expect(suggestedMonth(months, { year: 2025, month: 12 })).toBeNull();
  });
});
