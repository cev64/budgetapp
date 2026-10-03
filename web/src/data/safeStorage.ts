// localStorage / sessionStorage can be missing or throw (private windows, blocked site data).

function get(store: () => Storage, key: string): string | null {
  try {
    return store().getItem(key);
  } catch {
    return null;
  }
}

function set(store: () => Storage, key: string, value: string | null): void {
  try {
    if (value === null) store().removeItem(key);
    else store().setItem(key, value);
  } catch {
    // Quota exceeded or storage blocked: the app keeps working without the cache.
  }
}

const local = () => window.localStorage;
const session = () => window.sessionStorage;

export const localGet = (key: string) => get(local, key);
export const localSet = (key: string, value: string | null) => set(local, key, value);
export const sessionGet = (key: string) => get(session, key);
export const sessionSet = (key: string, value: string | null) => set(session, key, value);

/** Removes every localStorage key with the given prefix. */
export function localClearPrefix(prefix: string): void {
  try {
    const keys: string[] = [];
    for (let i = 0; i < window.localStorage.length; i++) {
      const k = window.localStorage.key(i);
      if (k?.startsWith(prefix)) keys.push(k);
    }
    keys.forEach((k) => window.localStorage.removeItem(k));
  } catch {
    // ignore
  }
}
