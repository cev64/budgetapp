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

/** The year that puts month/day closest to the budget month `near` (Dec 30 typed in January = last year). */
function nearestYear(month: number, day: number, near: YM): number {
  let best = near.year;
  let bestGap = Infinity;
  for (const y of [near.year - 1, near.year, near.year + 1]) {
    const gap = Math.abs(ymIndex({ year: y, month }) - ymIndex(near)) * 31 + Math.abs(day - 15) / 100;
    if (gap < bestGap) [best, bestGap] = [y, gap];
  }
  return best;
}

/**
 * Typed date → ISO. Accepts "10/3", "10-3", "10/3/26", "10/3/2026", "2026-10-03", "Oct 3", "3"
 * (day of the budget month) and "today"/"t"; a missing year is the one nearest the budget month.
 * Returns null for blank and undefined for anything that isn't a real date.
 */
export function parseDateInput(input: string, near: YM, today: Date = new Date()): string | null | undefined {
  const s = input.trim().toLowerCase();
  if (s === '') return null;
  if (s === 't' || s === 'today') return todayIso(today);
  let y: number | undefined;
  let m: number;
  let d: number;
  let r: RegExpExecArray | null;
  if ((r = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(s))) [y, m, d] = [Number(r[1]), Number(r[2]), Number(r[3])];
  else if ((r = /^(\d{1,2})[/.-](\d{1,2})(?:[/.-](\d{2}|\d{4}))?$/.exec(s))) {
    [m, d] = [Number(r[1]), Number(r[2])];
    if (r[3]) y = r[3].length === 2 ? 2000 + Number(r[3]) : Number(r[3]);
  } else if ((r = /^([a-z]{3,9})\.?\s+(\d{1,2})(?:,?\s+(\d{4}))?$/.exec(s))) {
    const i = MONTH_NAMES.findIndex((n) => n.toLowerCase().startsWith(r![1]!));
    if (i < 0 || r[1]!.length < 3) return undefined;
    [m, d] = [i + 1, Number(r[2])];
    if (r[3]) y = Number(r[3]);
  } else if ((r = /^(\d{1,2})$/.exec(s))) [y, m, d] = [near.year, near.month, Number(r[1])];
  else return undefined;
  if (m < 1 || m > 12) return undefined;
  const year = y ?? nearestYear(m, d, near);
  if (d < 1 || d > daysInMonth(year, m)) return undefined;
  return `${year}-${pad(m)}-${pad(d)}`;
}

/** Editor text for a stored date: "10/3", with the year only when parseDateInput wouldn't infer it. */
export function dateInputText(iso: string | null, near: YM): string {
  const r = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso ?? '');
  if (!r) return iso ?? '';
  const [y, m, d] = [Number(r[1]), Number(r[2]), Number(r[3])];
  return nearestYear(m, d, near) === y ? `${m}/${d}` : `${m}/${d}/${y}`;
}
