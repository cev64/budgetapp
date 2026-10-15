import type { Transaction } from '../domain/types';

/** Sort key: the transaction date, else the first of its budget month (many sheet rows have no date). */
const dateKey = (t: Transaction) => t.date ?? `${t.year}-${String(t.month).padStart(2, '0')}-00`;

/** Rows created on this device have no created_at until the server answers: they are the newest. */
const createdKey = (t: Transaction) => t.created_at ?? '9999';

/** created_at as epoch ms (servers write "+00:00", Android "Z"); unsynced rows are the newest. */
const createdMs = (t: Transaction) => {
  const ms = t.created_at ? Date.parse(t.created_at) : NaN;
  return Number.isNaN(ms) ? Infinity : ms;
};

/** Newest first: date, then creation time, then id for stability. */
export function compareTransactionsDesc(a: Transaction, b: Transaction): number {
  return dateKey(b).localeCompare(dateKey(a)) || createdKey(b).localeCompare(createdKey(a)) || a.id.localeCompare(b.id);
}

/** Most recently entered first (when it was added, not the date on it), then by date. */
export function compareEnteredDesc(a: Transaction, b: Transaction): number {
  const ma = createdMs(a);
  const mb = createdMs(b);
  if (ma !== mb) return ma === Infinity ? -1 : mb === Infinity ? 1 : mb - ma;
  return compareTransactionsDesc(a, b);
}
