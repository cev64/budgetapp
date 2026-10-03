import type { AccountGroup, NetWorthSnapshot } from './types';
import { live } from './calc';

// DOMAIN_RULES §5b: net worth history, display-side derivations only.
// Snapshot values are computed by the server; clients never compute or write them.

export type RangeKey = '1M' | '3M' | '6M' | '1Y' | 'All';

/** Range lengths in days; All = since the first snapshot. */
export const RANGE_DAYS: Record<RangeKey, number | null> = { '1M': 30, '3M': 91, '6M': 182, '1Y': 365, All: null };
export const RANGES: RangeKey[] = ['1M', '3M', '6M', '1Y', 'All'];

export interface Point {
  /** ISO day, YYYY-MM-DD */
  date: string;
  value: number;
}

/** Live snapshots, oldest first (one per day). */
export function sortSnapshots(rows: readonly NetWorthSnapshot[]): NetWorthSnapshot[] {
  return live(rows).sort((a, b) => a.taken_on.localeCompare(b.taken_on));
}

export type Metric = 'net_worth' | 'super_liquid' | 'reconciliations';

export const seriesOf = (snapshots: readonly NetWorthSnapshot[], metric: Metric): Point[] =>
  snapshots.map((s) => ({ date: s.taken_on, value: s[metric] }));

/** perAccountSeries(id): the account's balance on every snapshot that contains it. */
export function accountSeries(snapshots: readonly NetWorthSnapshot[], accountId: string): Point[] {
  const out: Point[] = [];
  for (const s of snapshots) {
    const a = s.accounts.find((x) => x.id === accountId);
    if (a) out.push({ date: s.taken_on, value: a.balance });
  }
  return out;
}

/** Σ balances of one account group per snapshot (e.g. the Investments tile). */
export function groupSeries(snapshots: readonly NetWorthSnapshot[], group: AccountGroup): Point[] {
  return snapshots.map((s) => ({
    date: s.taken_on,
    value: s.accounts.filter((a) => a.group === group).reduce((sum, a) => sum + a.balance, 0),
  }));
}

/** ISO day shifted by whole days (UTC arithmetic, so DST never matters). */
export function addDays(iso: string, days: number): string {
  const [y, m, d] = iso.split('-').map(Number) as [number, number, number];
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
}

export interface Change {
  from: Point;
  to: Point;
  change: number;
  /** True when no snapshot is as old as the period: the change runs from the oldest one ("since <date>"). */
  since: boolean;
}

/**
 * changeOver(period) = latest − (point on or before latest.date − period); if no point is that old,
 * the oldest point is used and `since` is set. `days = null` (All) measures from the first point.
 * Needs at least two points; returns null otherwise.
 */
export function changeOver(points: readonly Point[], days: number | null): Change | null {
  if (points.length < 2) return null;
  const to = points[points.length - 1]!;
  let from = points[0]!;
  let since = false;
  if (days != null) {
    const cutoff = addDays(to.date, -days);
    let base: Point | undefined;
    for (const p of points) {
      if (p.date <= cutoff) base = p;
      else break;
    }
    if (base) from = base;
    else since = true;
  }
  return { from, to, change: to.value - from.value, since };
}

/** The points a range chart draws: from the point changeOver measures from, through the latest. */
export function rangePoints(points: readonly Point[], days: number | null): Point[] {
  const c = changeOver(points, days);
  if (!c) return points.slice();
  return points.filter((p) => p.date >= c.from.date);
}
