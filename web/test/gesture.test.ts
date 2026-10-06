import { describe, expect, it } from 'vitest';
import { lockAxis, sheetOffset, sheetShouldClose, swipeOffset, swipeThreshold } from '../src/ui/gesture';

describe('gestures (docs/FLUID_GLASS_UI.md §7)', () => {
  it('swipe arms at 96px or 30% of the row', () => {
    expect(swipeThreshold(358)).toBe(96);
    expect(swipeThreshold(200)).toBe(60);
    expect(swipeThreshold(100)).toBe(48);
  });
  it('follows 1:1 up to the threshold, then rubber-bands at 35%', () => {
    expect(swipeOffset(50, 96)).toBe(50);
    expect(swipeOffset(-96, 96)).toBe(-96);
    expect(swipeOffset(196, 96)).toBeCloseTo(131, 6);
    expect(swipeOffset(-196, 96)).toBeCloseTo(-131, 6);
  });
  it('locks the axis after 8px', () => {
    expect(lockAxis(3, 4)).toBeNull();
    expect(lockAxis(12, 3)).toBe('x');
    expect(lockAxis(3, 12)).toBe('y');
    expect(lockAxis(10, 9)).toBe('y');
  });
  it('sheet: down 1:1, up rubber band, closes past 120px or on a flick', () => {
    expect(sheetOffset(80)).toBe(80);
    expect(sheetOffset(-100)).toBe(-40);
    expect(sheetShouldClose(130, 0)).toBe(true);
    expect(sheetShouldClose(40, 0.8)).toBe(true);
    expect(sheetShouldClose(60, 0.2)).toBe(false);
  });
});
