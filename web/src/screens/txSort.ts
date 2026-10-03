import type { Transaction } from '../domain/types';

/** Sort key: the transaction date, else the first of its budget month (many sheet rows have no date). */
const dateKey = (t: Transaction) => t.date ?? `${t.year}-${String(t.month).padStart(2, '0')}-00`;

/** Rows created on this device have no created_at until the server answers: they are the newest. */
const createdKey = (t: Transaction) => t.created_at ?? '9999';

/** Newest first: date, then creation time, then id for stability. */
export function compareTransactionsDesc(a: Transaction, b: Transaction): number {
  return dateKey(b).localeCompare(dateKey(a)) || createdKey(b).localeCompare(createdKey(a)) || a.id.localeCompare(b.id);
}
