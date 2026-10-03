import { describe, expect, it } from 'vitest';
import { categoryStyles, PALETTE } from '../src/domain/categoryStyle';
import type { Category, CategoryKind } from '../src/domain/types';

let n = 0;
const cat = (name: string, kind: CategoryKind, extra: Partial<Category> = {}): Category => ({
  id: `c${++n}`, name, kind, tracking: 'ledger', match_multiplier: 1, sort_order: n, icon: null, color: null, archived: false, ...extra,
});

describe('categoryStyles (tokens.json v2)', () => {
  it('ten palette entries, each with a distinct symbol', () => {
    expect(PALETTE).toHaveLength(10);
    expect(new Set(PALETTE.map((p) => p.symbol)).size).toBe(10);
  });

  it('default assignment by name, case-insensitive', () => {
    const cats = [cat('Rent', 'expense'), cat('food', 'expense'), cat('401K', 'savings'), cat('Paychecks', 'income')];
    const s = categoryStyles(cats);
    expect(s.get(cats[0]!.id)).toMatchObject({ id: 'housing', hex: '#2978C9', symbol: 'circle' });
    expect(s.get(cats[1]!.id)!.id).toBe('food');
    expect(s.get(cats[2]!.id)!.symbol).toBe('square-ring');
    expect(s.get(cats[3]!.id)!.id).toBe('401k');
  });

  it('categories.color holding a palette id overrides', () => {
    const c = cat('Rent', 'expense', { color: 'fun' });
    expect(categoryStyles([c]).get(c.id)!.id).toBe('fun');
  });

  it('user-added categories take the first id unused in their group, then cycle', () => {
    const rent = cat('Rent', 'expense');
    const pets = cat('Pets', 'expense');
    const side = cat('Side gig', 'income');
    const s = categoryStyles([rent, pets, side]);
    expect(s.get(pets.id)!.id).toBe('food'); // housing is taken in expenses
    expect(s.get(side.id)!.id).toBe('housing'); // income group is empty
    const many = Array.from({ length: 12 }, (_, i) => cat(`X${i}`, 'expense'));
    const m = categoryStyles(many);
    expect(new Set(many.slice(0, 10).map((c) => m.get(c.id)!.id)).size).toBe(10);
    expect(m.get(many[10]!.id)).toBeDefined();
  });
});
