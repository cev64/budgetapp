import type { YM } from './types';

export const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
] as const;

export const monthName = (month: number): string => MONTH_NAMES[month - 1] ?? '';
export const monthShort = (month: number): string => monthName(month).slice(0, 3);
export const ymLabel = ({ year, month }: YM): string => `${monthName(month)} ${year}`;

/** "2026-01" — used in routes and as map keys. */
export const ymKey = ({ year, month }: YM): string => `${year}-${String(month).padStart(2, '0')}`;

export function parseYm(key: string | undefined | null): YM | null {
  const m = /^(\d{4})-(\d{1,2})$/.exec(key ?? '');
  if (!m) return null;
  const year = Number(m[1]);
  const month = Number(m[2]);
  return month >= 1 && month <= 12 ? { year, month } : null;
}

export const ymIndex = ({ year, month }: YM): number => year * 12 + (month - 1);
export const compareYm = (a: YM, b: YM): number => ymIndex(a) - ymIndex(b);

export function addMonths({ year, month }: YM, delta: number): YM {
  const i = year * 12 + (month - 1) + delta;
  return { year: Math.floor(i / 12), month: (i % 12) + 1 };
}

export const daysInMonth = (year: number, month: number): number => new Date(Date.UTC(year, month, 0)).getUTCDate();

const pad = (n: number) => String(n).padStart(2, '0');

/** ISO date (YYYY-MM-DD) for a day in a month, clamping the day to the month length. */
export function clampedDate(year: number, month: number, day: number): string {
  const d = Math.min(Math.max(1, Math.trunc(day)), daysInMonth(year, month));
  return `${year}-${pad(month)}-${pad(d)}`;
}

/** Today's local calendar date as YYYY-MM-DD. */
export function todayIso(now: Date = new Date()): string {
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

export const currentYm = (now: Date = new Date()): YM => ({ year: now.getFullYear(), month: now.getMonth() + 1 });

/** "Oct 3" style label for an ISO date. */
export function shortDate(iso: string | null): string {
  if (!iso) return '';
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (!m) return iso;
  return `${monthShort(Number(m[2]))} ${Number(m[3])}`;
}
