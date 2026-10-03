import { localGet, localSet } from '../data/safeStorage';

export type ThemePref = 'system' | 'light' | 'dark';
const KEY = 'budget.theme';

export function getThemePref(): ThemePref {
  const v = localGet(KEY);
  return v === 'light' || v === 'dark' ? v : 'system';
}

/** Applies the theme: data-theme overrides prefers-color-scheme; "system" removes it. */
export function applyTheme(pref: ThemePref): void {
  const root = document.documentElement;
  if (pref === 'system') root.removeAttribute('data-theme');
  else root.setAttribute('data-theme', pref);
  const dark = pref === 'dark' || (pref === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches);
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', dark ? '#0A1122' : '#FFFFFF');
}

export function setThemePref(pref: ThemePref): void {
  localSet(KEY, pref === 'system' ? null : pref);
  applyTheme(pref);
}
