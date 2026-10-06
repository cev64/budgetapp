// Gesture maths shared by swipe-to-delete and sheet drag (docs/FLUID_GLASS_UI.md §7). Pure, tested.

/** Distance at which a horizontal swipe arms its action: 96px or 30% of the row, whichever is smaller. */
export const swipeThreshold = (width: number): number => Math.min(96, Math.max(48, width * 0.3));

/** Past the threshold the row follows the finger at 35% (rubber band). */
export function swipeOffset(dx: number, threshold: number): number {
  const a = Math.abs(dx);
  if (a <= threshold) return dx;
  return Math.sign(dx) * (threshold + (a - threshold) * 0.35);
}

/** Direction lock: undecided until 8px of travel, then horizontal only if clearly sideways. */
export function lockAxis(dx: number, dy: number): 'x' | 'y' | null {
  if (Math.hypot(dx, dy) < 8) return null;
  return Math.abs(dx) > Math.abs(dy) * 1.2 ? 'x' : 'y';
}

/** Sheet drag: down follows 1:1, up rubber-bands. */
export const sheetOffset = (dy: number): number => (dy >= 0 ? dy : -Math.sqrt(-dy) * 4);

/** Release decision for the sheet: past 120px or flicked faster than 0.6 px/ms closes it. */
export const sheetShouldClose = (dy: number, velocity: number): boolean => dy > 120 || velocity > 0.6;
