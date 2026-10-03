import { describe, expect, it } from 'vitest';
import {
  amountInputText, diffTone, formatMoney, formatPercent, formatSignedMoney, monthClosedMessage, overBudgetMessage, parseAmount, savedMessage, vsPlanCaption,
} from '../src/domain/format';

describe('formatMoney (DOMAIN_RULES §8)', () => {
  it('matches every §8 test vector', () => {
    const vectors: [number, string][] = [
      [0, '$0'], [12, '$12'], [12.5, '$12.50'], [999.994, '$999.99'], [999.995, '$1,000'], [1000, '$1,000'],
      [1000.5, '$1,001'], [22560, '$22,560'], [2886.6667, '$2,887'], [-640.25, '\u2212$640.25'],
      [-1022.5, '\u2212$1,023'], [-0.004, '$0'],
    ];
    for (const [v, want] of vectors) expect(formatMoney(v), String(v)).toBe(want);
  });
  it('the §8 examples', () => {
    expect(formatMoney(1234.56)).toBe('$1,235');
    expect(formatMoney(-1500.4)).toBe('\u2212$1,500');
    expect(formatMoney(69.6667)).toBe('$69.67');
    expect(formatMoney(999.99)).toBe('$999.99');
  });
  it('cents below $1,000, binary noise removed', () => {
    expect(formatMoney(330.5)).toBe('$330.50');
    expect(formatMoney(0.1 + 0.2)).toBe('$0.30');
    expect(formatMoney(99.999)).toBe('$100');
    expect(formatMoney(-153)).toBe('\u2212$153');
    expect(formatMoney(1000000)).toBe('$1,000,000');
  });
  it('null renders blank', () => {
    expect(formatMoney(null)).toBe('');
    expect(formatMoney(undefined)).toBe('');
  });
  it('signed variant', () => {
    expect(formatSignedMoney(12)).toBe('+$12');
    expect(formatSignedMoney(1059.33)).toBe('+$1,059');
    expect(formatSignedMoney(-12)).toBe('\u2212$12');
    expect(formatSignedMoney(0)).toBe('$0');
    expect(formatSignedMoney(-0.001)).toBe('$0');
  });
});

describe('amountInputText (edit fields keep the exact value)', () => {
  it('does not round to whole dollars', () => {
    expect(amountInputText(1234.56)).toBe('1234.56');
    expect(amountInputText(-1022.5)).toBe('-1022.5');
    expect(amountInputText(69.6667)).toBe('69.6667');
    expect(amountInputText(0.1 + 0.2)).toBe('0.3');
    expect(amountInputText(null)).toBe('');
  });
});

describe('formatPercent', () => {
  it('one decimal', () => {
    expect(formatPercent(0.437)).toBe('43.7%');
    expect(formatPercent(0.35)).toBe('35.0%');
    expect(formatPercent(0.488175)).toBe('48.8%');
    expect(formatPercent(-0.05)).toBe('−5.0%');
  });
  it('blank for non-finite', () => {
    expect(formatPercent(Number.POSITIVE_INFINITY)).toBe('');
    expect(formatPercent(null)).toBe('');
  });
});

describe('diffTone', () => {
  it('expenses: over budget is bad', () => {
    expect(diffTone('expense', 10)).toBe('bad');
    expect(diffTone('expense', -10)).toBe('good');
    expect(diffTone('expense', 0)).toBe('neutral');
  });
  it('income, savings and leftover: above is good', () => {
    expect(diffTone('income', 100)).toBe('good');
    expect(diffTone('income', -1)).toBe('bad');
    expect(diffTone('savings', -0.5)).toBe('bad');
    expect(diffTone('leftover', 0.001)).toBe('neutral');
  });
});

describe('parseAmount', () => {
  it('parses common inputs', () => {
    expect(parseAmount('1,234.56')).toBe(1234.56);
    expect(parseAmount(' $12 ')).toBe(12);
    expect(parseAmount('-5')).toBe(-5);
    expect(parseAmount('−5')).toBe(-5);
    expect(parseAmount('.5')).toBe(0.5);
  });
  it('empty is null, junk is NaN', () => {
    expect(parseAmount('')).toBeNull();
    expect(parseAmount('abc')).toBeNaN();
    expect(parseAmount('1.2.3')).toBeNaN();
  });
});

describe('voice strings (UI_ANATOMY Brand v2)', () => {
  it('saved toast by kind and sign', () => {
    expect(savedMessage('expense', 12)).toBe('Expense saved. Your budget is up to date.');
    expect(savedMessage('income', 2000)).toBe('Income saved. Your budget is up to date.');
    expect(savedMessage('expense', -5)).toBe('Refund saved. Your budget is up to date.');
  });
  it('over budget and month closed', () => {
    expect(overBudgetMessage('Food', 42)).toBe('Food is $42 over budget. Review your recent expenses.');
    expect(overBudgetMessage('Rent', 1022.5)).toBe('Rent is $1,023 over budget. Review your recent expenses.');
    expect(monthClosedMessage('September')).toBe('September is closed. Your totals are saved.');
  });
});

describe('vs plan display (DOMAIN_RULES §4b)', () => {
  it('sign always shown, caption by sign', () => {
    expect(formatSignedMoney(1380)).toBe('+$1,380');
    expect(formatSignedMoney(-45)).toBe('\u2212$45');
    expect(formatSignedMoney(0.004)).toBe('$0');
    expect(vsPlanCaption(261.75)).toBe('ahead of plan');
    expect(vsPlanCaption(-45)).toBe('behind plan');
    expect(vsPlanCaption(-0.004)).toBe('on plan');
  });
});
