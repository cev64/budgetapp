import type { CategoryKind } from './types';

// DOMAIN_RULES §8 display rules. Storage is never rounded; only display is.

const MINUS = '−';
const formatters = new Map<string, Intl.NumberFormat>();

function currencyFormatter(currency: string, digits: 0 | 2): Intl.NumberFormat {
  const key = `${currency}:${digits}`;
  let f = formatters.get(key);
  if (!f) {
    try {
      f = new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency,
        minimumFractionDigits: digits,
        maximumFractionDigits: digits,
      });
    } catch {
      f = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', minimumFractionDigits: digits, maximumFractionDigits: digits });
    }
    formatters.set(key, f);
  }
  return f;
}

// A hair above half a unit so values like 999.995 (stored in binary as 999.99499…) round half away from zero.
const NUDGE = 1e-6;

/** Whole cents, half away from zero, without binary noise (0.1 + 0.2 → 30). */
export function toCents(value: number): number {
  const c = Math.round(Math.abs(value) * 100 + NUDGE);
  return value < 0 ? -c : c;
}

/**
 * DOMAIN_RULES §8 (display only):
 * - |value| ≥ 1,000 → whole dollars, half away from zero: `$1,235`, `−$1,500`;
 * - below that `$12` when whole, `$12.34` otherwise;
 * - negatives `−$153` (U+2212), zero `$0` (never signed).
 * `null`/`undefined` render as an empty string (a blank cell, like the sheet).
 */
export function formatMoney(value: number | null | undefined, currency = 'USD'): string {
  if (value == null || Number.isNaN(value)) return '';
  const cents = toCents(value);
  const absCents = Math.abs(cents);
  let text: string;
  if (absCents >= 100000) {
    const dollars = Math.max(1000, Math.round(Math.abs(value) + NUDGE / 100));
    text = currencyFormatter(currency, 0).format(dollars);
  } else {
    text = currencyFormatter(currency, absCents % 100 === 0 ? 0 : 2).format(absCents / 100);
  }
  return cents < 0 ? MINUS + text : text;
}

/** Like formatMoney but with an explicit + for positive values (used for differences). */
export function formatSignedMoney(value: number, currency = 'USD'): string {
  const text = formatMoney(value, currency);
  return toCents(value) > 0 ? `+${text}` : text;
}

/** One decimal: 0.437 → "43.7%". */
export function formatPercent(ratio: number | null | undefined): string {
  if (ratio == null || !Number.isFinite(ratio)) return '';
  const v = Math.round(ratio * 1000) / 10;
  const text = `${Math.abs(v).toFixed(1)}%`;
  return v < 0 ? MINUS + text : text;
}

/** Plain number with up to 2 decimals and grouping (e.g. a match multiplier). */
export function formatNumber(value: number | null | undefined, maxDigits = 2): string {
  if (value == null || Number.isNaN(value)) return '';
  const text = new Intl.NumberFormat('en-US', { maximumFractionDigits: maxDigits }).format(Math.abs(value));
  return value < 0 && Math.abs(value) >= 0.5 * 10 ** -maxDigits ? MINUS + text : text;
}

export type Tone = 'good' | 'bad' | 'neutral';

/**
 * Difference colouring. Expense: over budget (diff > 0) is bad, under is good.
 * Income, savings and leftover: diff ≥ 0 is good, < 0 is bad. Zero is neutral.
 */
export function diffTone(kind: CategoryKind | 'leftover', diff: number): Tone {
  const c = toCents(diff);
  if (c === 0) return 'neutral';
  if (kind === 'expense') return c > 0 ? 'bad' : 'good';
  return c > 0 ? 'good' : 'bad';
}

/**
 * Parses user input such as "1,234.56", "$12", "-5" or "−5".
 * Returns null for an empty string and NaN for anything unparseable.
 */
export function parseAmount(input: string): number | null {
  const s = input.trim().replace(/[\s,$€£]/g, '').replace(MINUS, '-');
  if (s === '') return null;
  if (!/^[-+]?(\d+\.?\d*|\.\d+)$/.test(s)) return Number.NaN;
  return Number(s);
}

/** Editable text for a number input: the exact stored value (no rounding, grouping or symbol). */
export function amountInputText(value: number | null | undefined): string {
  if (value == null) return '';
  return String(Number(value.toPrecision(15)));
}

// ---- voice (docs/UI_ANATOMY.md "Brand (v2)": exact strings) ----

/** Toast after a transaction write succeeded. */
export function savedMessage(kind: CategoryKind, amount: number): string {
  const what = amount < 0 ? 'Refund' : kind === 'income' ? 'Income' : 'Expense';
  return `${what} saved. Your budget is up to date.`;
}

/** "{Category} is {amount} over budget. Review your recent expenses." */
export const overBudgetMessage = (category: string, over: number, currency = 'USD'): string =>
  `${category} is ${formatMoney(over, currency)} over budget. Review your recent expenses.`;

/** "{Month} is closed. Your totals are saved." */
export const monthClosedMessage = (monthName: string): string => `${monthName} is closed. Your totals are saved.`;

export const EMPTY_MONTH_MESSAGE = 'Set your first category to start this month\u2019s budget.';
export const SIGN_IN_HEADLINE = 'A clear view of your money.';
