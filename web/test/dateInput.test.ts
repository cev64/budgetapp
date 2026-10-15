import { describe, expect, it } from 'vitest';
import { dateInputText, parseDateInput } from '../src/domain/dates';

describe('parseDateInput', () => {
  const oct = { year: 2026, month: 10 };
  it('reads the usual spreadsheet forms', () => {
    expect(parseDateInput('10/3', oct)).toBe('2026-10-03');
    expect(parseDateInput('10-3-25', oct)).toBe('2025-10-03');
    expect(parseDateInput('10/3/2024', oct)).toBe('2024-10-03');
    expect(parseDateInput('2026-10-03', oct)).toBe('2026-10-03');
    expect(parseDateInput('Oct 3', oct)).toBe('2026-10-03');
    expect(parseDateInput('sept 9', oct)).toBe('2026-09-09');
    expect(parseDateInput('7', oct)).toBe('2026-10-07');
    expect(parseDateInput('t', oct, new Date(2026, 9, 15))).toBe('2026-10-15');
    expect(parseDateInput('  ', oct)).toBeNull();
  });
  it('picks the year nearest the budget month', () => {
    expect(parseDateInput('12/30', { year: 2026, month: 1 })).toBe('2025-12-30');
    expect(parseDateInput('1/2', { year: 2025, month: 12 })).toBe('2026-01-02');
  });
  it('rejects impossible dates', () => {
    expect(parseDateInput('2/30', oct)).toBeUndefined();
    expect(parseDateInput('13/1', oct)).toBeUndefined();
    expect(parseDateInput('soon', oct)).toBeUndefined();
  });
  it('editor text round-trips', () => {
    expect(dateInputText('2026-10-03', oct)).toBe('10/3');
    expect(dateInputText('2024-10-03', oct)).toBe('10/3/2024');
    expect(parseDateInput(dateInputText('2025-12-30', { year: 2026, month: 1 }), { year: 2026, month: 1 })).toBe('2025-12-30');
  });
});
