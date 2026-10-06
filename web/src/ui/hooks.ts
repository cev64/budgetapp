import { useEffect, useLayoutEffect, useRef, useState, type RefObject } from 'react';
import { glideIndicator } from './motion';

export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(() => window.matchMedia(query).matches);
  useEffect(() => {
    const mq = window.matchMedia(query);
    const on = () => setMatches(mq.matches);
    on();
    mq.addEventListener('change', on);
    return () => mq.removeEventListener('change', on);
  }, [query]);
  return matches;
}

export type LayoutMode = 'compact' | 'medium' | 'expanded';

/** UI_ANATOMY breakpoints (window width): < 600 compact, 600–1023 medium, ≥ 1024 expanded. */
export function useLayoutMode(): LayoutMode {
  const medium = useMediaQuery('(min-width: 600px)');
  const expanded = useMediaQuery('(min-width: 1024px)');
  return expanded ? 'expanded' : medium ? 'medium' : 'compact';
}

/**
 * Keeps a gliding indicator on the selected child of `ref` (segmented controls, nav).
 * Re-measures on selection change, on resize and after web fonts load.
 */
export function useGlide(
  ref: RefObject<HTMLElement | null>,
  selected: unknown,
  options?: { vertical?: boolean; grid?: boolean; cls?: string; selector?: string },
) {
  const vertical = options?.vertical ?? false;
  const grid = options?.grid ?? false;
  const cls = options?.cls ?? 'seg-ind';
  const selector = options?.selector;
  useLayoutEffect(() => {
    const box = ref.current;
    if (box) glideIndicator(box, { vertical, grid, cls, ...(selector ? { selector } : {}) });
  }, [ref, selected, vertical, grid, cls, selector]);
  useEffect(() => {
    const box = ref.current;
    if (!box) return;
    const update = () => glideIndicator(box, { vertical, grid, cls, ...(selector ? { selector } : {}) });
    const ro = new ResizeObserver(update);
    ro.observe(box);
    void document.fonts?.ready.then(update);
    return () => ro.disconnect();
  }, [ref, vertical, grid, cls, selector]);
}

/** A ref plus the element's current width (ResizeObserver), for charts drawn in pixels. */
export function useWidth<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [width, setWidth] = useState(0);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const ro = new ResizeObserver(() => setWidth(el.clientWidth));
    ro.observe(el);
    setWidth(el.clientWidth);
    return () => ro.disconnect();
  }, []);
  return [ref, width] as const;
}
