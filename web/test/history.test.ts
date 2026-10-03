import { describe, expect, it } from 'vitest';
import { accountSeries, addDays, changeOver, rangePoints, RANGE_DAYS, seriesOf, sortSnapshots, type Point } from '../src/domain/history';
import type { NetWorthSnapshot } from '../src/domain/types';

const snap = (taken_on: string, net_worth: number, extra: Partial<NetWorthSnapshot> = {}): NetWorthSnapshot => ({
  taken_on, net_worth, super_liquid: net_worth / 2, reconciliations: 0, accounts: [], source: 'auto', ...extra,
});

/** Daily points from start, value = index * 10. */
const daily = (start: string, n: number): Point[] => Array.from({ length: n }, (_, i) => ({ date: addDays(start, i), value: i * 10 }));

describe('addDays', () => {
  it('handles month and year boundaries and leap days', () => {
    expect(addDays('2026-10-03', -30)).toBe('2026-09-03');
    expect(addDays('2026-01-01', -1)).toBe('2025-12-31');
    expect(addDays('2028-03-01', -1)).toBe('2028-02-29');
    expect(addDays('2026-10-03', -365)).toBe('2025-10-03');
  });
});

describe('changeOver (DOMAIN_RULES §5b)', () => {
  it('needs two points', () => {
    expect(changeOver([], 30)).toBeNull();
    expect(changeOver([{ date: '2026-10-03', value: 5 }], 30)).toBeNull();
  });

  it('measures from the point exactly one period back', () => {
    const pts = daily('2026-01-01', 100); // 2026-01-01 .. 2026-04-10, last value 990
    const c = changeOver(pts, RANGE_DAYS['1M'])!;
    expect(c.to.date).toBe('2026-04-10');
    expect(c.from.date).toBe('2026-03-11');
    expect(c.change).toBe(990 - 690);
    expect(c.since).toBe(false);
  });

  it('uses the last point on or before the cutoff when days are missing', () => {
    const pts: Point[] = [
      { date: '2026-08-01', value: 100 },
      { date: '2026-08-20', value: 150 },
      { date: '2026-09-10', value: 170 },
      { date: '2026-10-03', value: 200 },
    ];
    // cutoff 2026-09-03 -> 2026-08-20
    const c = changeOver(pts, 30)!;
    expect(c.from).toEqual({ date: '2026-08-20', value: 150 });
    expect(c.change).toBe(50);
  });

  it('falls back to the oldest point ("since <date>") when history is shorter than the period', () => {
    const pts = daily('2026-09-20', 14);
    const c = changeOver(pts, RANGE_DAYS['1Y'])!;
    expect(c.since).toBe(true);
    expect(c.from.date).toBe('2026-09-20');
    expect(c.change).toBe(130);
  });

  it('All measures from the first point and is never "since"', () => {
    const pts = daily('2025-01-01', 400);
    const c = changeOver(pts, RANGE_DAYS.All)!;
    expect(c.from.date).toBe('2025-01-01');
    expect(c.since).toBe(false);
    expect(c.change).toBe(3990);
  });

  it('negative changes', () => {
    const c = changeOver([{ date: '2026-09-01', value: 500 }, { date: '2026-10-03', value: 420.5 }], 30)!;
    expect(c.change).toBe(-79.5);
  });
});

describe('rangePoints', () => {
  it('starts at the base point of the change', () => {
    const pts = daily('2026-01-01', 100);
    const r = rangePoints(pts, 30);
    expect(r[0]!.date).toBe('2026-03-11');
    expect(r).toHaveLength(31);
    expect(rangePoints(pts, null)).toHaveLength(100);
  });
});

describe('series', () => {
  const rows = [
    snap('2026-10-02', 200, { accounts: [{ id: 'a', name: 'Checking', group: 'cash', liquid: true, balance: 50 }] }),
    snap('2026-09-30', 100, { accounts: [] }),
    snap('2026-10-01', 150, { deleted: true }),
    snap('2026-10-03', 250, { accounts: [{ id: 'a', name: 'Checking', group: 'cash', liquid: true, balance: 75 }] }),
  ];
  it('sorts by day and drops tombstones', () => {
    expect(sortSnapshots(rows).map((s) => s.taken_on)).toEqual(['2026-09-30', '2026-10-02', '2026-10-03']);
  });
  it('metric and per-account series', () => {
    const s = sortSnapshots(rows);
    expect(seriesOf(s, 'super_liquid').map((p) => p.value)).toEqual([50, 100, 125]);
    expect(accountSeries(s, 'a')).toEqual([{ date: '2026-10-02', value: 50 }, { date: '2026-10-03', value: 75 }]);
    expect(accountSeries(s, 'missing')).toEqual([]);
  });
});
