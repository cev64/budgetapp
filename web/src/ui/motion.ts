// Fluid Glass motion kit (docs/FLUID_GLASS_UI.md §2, §7), typed for this app. Durations follow the v2 table.

export const EASE = 'cubic-bezier(.22,1,.36,1)';
export const TINT = { up: '21,128,61', down: '220,38,38', accent: '16,89,252' } as const;

const reduceQuery = typeof window !== 'undefined' ? window.matchMedia('(prefers-reduced-motion: reduce)') : null;
export const reducedMotion = (): boolean => reduceQuery?.matches ?? false;

/** Restart a CSS animation class (e.g. 'bump' on a changed number). */
export function replay(el: HTMLElement, cls: string): void {
  el.classList.remove(cls);
  void el.offsetWidth;
  el.classList.add(cls);
}

/** Fading colour wash. */
export function tint(el: HTMLElement, rgb: string, { delay = 0, a = 0.12, duration = 900 } = {}): void {
  if (reducedMotion() || typeof el.animate !== 'function') return;
  el.animate([{ backgroundColor: `rgba(${rgb},${a})` }, { backgroundColor: `rgba(${rgb},0)` }], { duration, easing: 'ease-out', delay });
}

/** One indicator that glides to the selected item (segmented controls, tabs, nav). */
export function glideIndicator(
  box: HTMLElement,
  { selector = '[aria-selected="true"],[aria-pressed="true"],[aria-current="page"]', cls = 'seg-ind', vertical = false, grid = false } = {},
): void {
  let ind = box.querySelector<HTMLElement>(`:scope > .${cls}`);
  if (!ind) {
    ind = document.createElement('span');
    ind.className = cls;
    ind.setAttribute('aria-hidden', 'true');
    box.prepend(ind);
  }
  const on = box.querySelector<HTMLElement>(selector);
  if (!on) {
    ind.style.opacity = '0';
    return;
  }
  ind.style.opacity = '';
  if (grid) {
    // Both axes (a grid of cells): the thumb takes the cell's box.
    ind.style.width = `${on.offsetWidth}px`;
    ind.style.height = `${on.offsetHeight}px`;
    ind.style.transform = `translate(${on.offsetLeft}px, ${on.offsetTop}px)`;
  } else if (vertical) {
    ind.style.height = `${on.offsetHeight}px`;
    ind.style.transform = `translateY(${on.offsetTop}px)`;
  } else {
    ind.style.width = `${on.offsetWidth}px`;
    ind.style.transform = `translateX(${on.offsetLeft}px)`;
  }
  // Transition only after the first placement, so nothing slides in on page load.
  if (!ind.classList.contains('ready')) {
    const el = ind;
    requestAnimationFrame(() => requestAnimationFrame(() => el.classList.add('ready')));
  }
}

type RollEl = HTMLElement & { _rt?: ReturnType<typeof setTimeout> };

/** Text that rolls to its new value in the direction of travel ('up' = larger / next). */
export function rollText(el: RollEl, text: string, dir: 'up' | 'down' = 'up'): void {
  const old = el.dataset.t;
  if (old === text) return;
  el.dataset.t = text;
  if (old == null || reducedMotion()) {
    el.textContent = text;
    return;
  }
  el.style.position = 'relative';
  el.style.display = 'inline-block';
  el.innerHTML = '';
  const o = Object.assign(document.createElement('span'), { textContent: old });
  const n = Object.assign(document.createElement('span'), { textContent: text });
  const d = dir === 'up' ? 'Up' : 'Down';
  Object.assign(o.style, {
    position: 'absolute', left: '0', top: '0', whiteSpace: 'nowrap', pointerEvents: 'none',
    animation: `rollOut${d} .3s var(--ease) both`,
  });
  Object.assign(n.style, { display: 'inline-block', animation: `rollIn${d} .38s var(--ease) both` });
  el.append(o, n);
  clearTimeout(el._rt);
  el._rt = setTimeout(() => {
    if (el.dataset.t === text) el.textContent = text;
  }, 450);
}

/** Positions of keyed rows ([data-k]) relative to their list, taken before a change. */
export type Snapshot = Map<string, { top: number; el: HTMLElement }>;

export function snapshot(root: HTMLElement | null): Snapshot | null {
  if (!root || !root.offsetParent || reducedMotion()) return null;
  const base = root.getBoundingClientRect().top;
  const m: Snapshot = new Map();
  root.querySelectorAll<HTMLElement>(':scope [data-k]').forEach((el) => {
    if (el.dataset.k) m.set(el.dataset.k, { top: el.getBoundingClientRect().top - base, el });
  });
  return m;
}

/**
 * FLIP after re-rendering: moved rows slide from their old place and wash green (up) or red (down);
 * new rows fade + rise and wash accent; removed rows sink away in red. `root` must be position:relative.
 */
export function flip(root: HTMLElement | null, before: Snapshot | null): void {
  if (!root || !before || reducedMotion()) return;
  const base = root.getBoundingClientRect().top;
  const now = new Set<string>();
  root.querySelectorAll<HTMLElement>(':scope [data-k]').forEach((el) => {
    const k = el.dataset.k;
    if (!k) return;
    now.add(k);
    const was = before.get(k);
    if (!was) {
      el.animate([{ opacity: 0, transform: 'translateY(6px)' }, { opacity: 1, transform: 'none' }], { duration: 300, easing: EASE });
      tint(el, TINT.accent);
      return;
    }
    const dy = was.top - (el.getBoundingClientRect().top - base);
    if (Math.abs(dy) > 1) {
      el.animate([{ transform: `translateY(${dy}px)` }, { transform: 'none' }], { duration: 350, easing: EASE, fill: 'backwards' });
      tint(el, dy > 0 ? TINT.up : TINT.down);
    }
  });
  before.forEach(({ top, el }, k) => {
    if (now.has(k)) return;
    const g = el.cloneNode(true) as HTMLElement;
    g.removeAttribute('data-k');
    Object.assign(g.style, { position: 'absolute', left: '0', right: '0', top: `${top}px`, pointerEvents: 'none', zIndex: '1' });
    root.appendChild(g);
    const anim = g.animate(
      [
        { opacity: 1, transform: 'none', backgroundColor: `rgba(${TINT.down},.18)` },
        { opacity: 0, transform: 'translateY(10px) scale(.98)', backgroundColor: `rgba(${TINT.down},0)` },
      ],
      { duration: 260, easing: EASE },
    );
    anim.onfinish = () => g.remove();
    anim.oncancel = () => g.remove();
  });
}

/** Light haptic tick on touch devices (FLUID_GLASS_UI §7.8). */
export function haptic(pattern: number | number[] = 6): void {
  if (reducedMotion()) return;
  if (typeof navigator.vibrate === 'function' && window.matchMedia('(pointer:coarse)').matches) navigator.vibrate(pattern);
}
