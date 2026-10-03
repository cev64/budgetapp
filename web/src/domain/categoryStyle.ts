import tokens from '../../../design/tokens.json';
import type { Category, CategoryKind } from './types';

// Category colour + symbol (design/tokens.json v2: categoryPalette, categoryAssignment).
// The colour is never used alone: every dot, legend and series pairs it with its symbol.

export type SymbolShape =
  | 'circle' | 'square' | 'triangle' | 'diamond' | 'plus' | 'cross' | 'ring' | 'square-ring' | 'triangle-ring' | 'diamond-ring';

export interface PaletteEntry {
  id: string;
  hex: string;
  symbol: SymbolShape;
}

export const PALETTE = tokens.color.categoryPalette as PaletteEntry[];
const BY_ID = new Map(PALETTE.map((p) => [p.id, p]));
const ASSIGNMENT = new Map(Object.entries(tokens.color.categoryAssignment).map(([name, id]) => [name.toLowerCase(), id]));

/**
 * Resolves every category's palette entry:
 * 1. `categories.color` holding a palette id overrides;
 * 2. else the default assignment by name (case-insensitive);
 * 3. else (user-added) the first palette id not yet used in its group (income / expenses / savings),
 *    cycling through the palette when the group has used them all.
 */
export function categoryStyles(categories: readonly Category[]): Map<string, PaletteEntry> {
  const out = new Map<string, PaletteEntry>();
  const used = new Map<CategoryKind, Set<string>>();
  const pending: Category[] = [];
  const ordered = categories.filter((c) => !c.deleted).sort((a, b) => a.sort_order - b.sort_order || a.name.localeCompare(b.name));
  for (const c of ordered) {
    const id = (c.color && BY_ID.has(c.color) ? c.color : undefined) ?? ASSIGNMENT.get(c.name.trim().toLowerCase());
    const entry = id ? BY_ID.get(id) : undefined;
    if (entry) {
      out.set(c.id, entry);
      if (!used.has(c.kind)) used.set(c.kind, new Set());
      used.get(c.kind)!.add(entry.id);
    } else pending.push(c);
  }
  for (const c of pending) {
    const group = used.get(c.kind) ?? new Set<string>();
    used.set(c.kind, group);
    const free = PALETTE.find((p) => !group.has(p.id));
    const entry = free ?? PALETTE[group.size % PALETTE.length]!;
    group.add(entry.id);
    out.set(c.id, entry);
  }
  return out;
}
