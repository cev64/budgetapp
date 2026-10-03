import { parseBackup } from '../domain/backup';
import { DemoBackend } from './backend';
import { sessionGet, sessionSet } from './safeStorage';

const FLAG = 'budget.demo';

/** Demo mode is requested with #/?demo=1 (or the sign-in button) and lasts for the browser tab. */
export function demoRequested(): boolean {
  const hash = window.location.hash;
  const query = hash.includes('?') ? hash.slice(hash.indexOf('?') + 1) : '';
  if (new URLSearchParams(query).get('demo') === '1') {
    sessionSet(FLAG, '1');
    return true;
  }
  return sessionGet(FLAG) === '1';
}

export const setDemoFlag = (on: boolean) => sessionSet(FLAG, on ? '1' : null);

/** Loads the synthetic fixture (code-split: only fetched in demo mode). */
export async function createDemoBackend(): Promise<DemoBackend> {
  const mod = await import('../../../docs/fixtures/sample-backup.json');
  return new DemoBackend(parseBackup(mod.default));
}
