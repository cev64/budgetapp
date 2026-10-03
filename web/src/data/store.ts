import type { Backend } from './backend';
import { localGet, localSet } from './safeStorage';
import { LIST_TABLES, PULL_ONLY, TABLES, rowKey, type RowTypes, type TableName } from '../domain/tables';
import { DEFAULT_SETTINGS, type Dataset } from '../domain/types';

/** Every table keyed by row identity (id or natural key). Tombstones are kept so merges stay correct. */
export type Tables = { [T in TableName]: Record<string, RowTypes[T]> };

export type SyncStatus = 'syncing' | 'synced' | 'offline';

export interface StoreState {
  tables: Tables;
  /** True once rows are available (from the cache or the server). */
  ready: boolean;
  /** True after the first successful load from the backend. */
  loaded: boolean;
  status: SyncStatus;
  lastSync: string | null;
  pendingWrites: number;
  error: string | null;
}

const OVERLAP_MS = 10_000;
const SNAPSHOT_DEBOUNCE_MS = 2000;
const CACHE_VERSION = 1;
export const CACHE_PREFIX = 'budget.cache.';

const isOffline = () => typeof navigator !== 'undefined' && navigator.onLine === false;

const emptyTables = (): Tables => Object.fromEntries(TABLES.map((t) => [t, {}])) as unknown as Tables;

interface CacheFile {
  v: number;
  tables: Tables;
  cursors: Partial<Record<TableName, string>>;
  lastSync: string | null;
}

/**
 * Client-side copy of the user's data.
 * - loads every table (paginated), then pulls incrementally on focus / reconnect / realtime resubscribe;
 * - merges realtime rows as they arrive;
 * - writes optimistically: local state first, then upsert; on error the rows revert and `onError` is called;
 * - caches the last snapshot in localStorage so the app paints instantly and stays readable offline.
 */
export class Store {
  private state: StoreState = {
    tables: emptyTables(),
    ready: false,
    loaded: false,
    status: 'syncing',
    lastSync: null,
    pendingWrites: 0,
    error: null,
  };
  private listeners = new Set<() => void>();
  private cursors: Partial<Record<TableName, string>> = {};
  /** `${table}:${key}` → token of the newest write in flight. Remote rows never overwrite these. */
  private pending = new Map<string, number>();
  private seq = 0;
  private stopFns: (() => void)[] = [];
  private refreshing: Promise<void> | null = null;
  private refreshAgain = false;
  private lastRefreshAt = 0;
  private saveTimer: ReturnType<typeof setTimeout> | undefined;
  private stopped = false;
  private snapshotTimer: ReturnType<typeof setTimeout> | undefined;
  private datasetCache: { tables: Tables; ds: Dataset } | null = null;

  constructor(
    readonly backend: Backend,
    private readonly cacheKey: string | null,
    private readonly onError: (message: string) => void,
  ) {}

  // ---- external store protocol (useSyncExternalStore) ----

  getState = (): StoreState => this.state;

  subscribe = (fn: () => void): (() => void) => {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  };

  private set(patch: Partial<StoreState>): void {
    this.state = { ...this.state, ...patch };
    this.listeners.forEach((fn) => fn());
    if (patch.tables) this.scheduleSave();
  }

  /** Live (non-deleted) rows as plain arrays, memoised per snapshot. */
  dataset(): Dataset {
    const { tables } = this.state;
    if (this.datasetCache?.tables === tables) return this.datasetCache.ds;
    const all = <T extends TableName>(t: T) => Object.values(tables[t]) as RowTypes[T][];
    const settings = all('settings').find((s) => !s.deleted) ?? DEFAULT_SETTINGS;
    const ds = { settings } as Dataset;
    for (const t of LIST_TABLES) (ds as unknown as Record<string, unknown>)[t] = all(t).filter((r) => !r.deleted);
    this.datasetCache = { tables, ds };
    return ds;
  }

  row<T extends TableName>(table: T, key: string): RowTypes[T] | undefined {
    return this.table(table)[key];
  }

  private table<T extends TableName>(table: T): Record<string, RowTypes[T]> {
    return this.state.tables[table] as Record<string, RowTypes[T]>;
  }

  // ---- lifecycle ----

  start(): void {
    this.hydrate();
    void this.refresh(true);
    this.stopFns.push(
      this.backend.subscribe({
        onRow: (table, row) => this.merge(table, [row]),
        onStatus: (connected) => {
          if (connected) {
            // Catch up on anything missed while the channel was down.
            if (this.state.loaded) void this.refresh(false);
          } else if (isOffline()) {
            this.set({ status: 'offline' });
          }
        },
      }),
    );
    const onFocus = () => {
      if (document.visibilityState === 'visible' && Date.now() - this.lastRefreshAt > 3000) void this.refresh(false);
    };
    const onOnline = () => void this.refresh(false);
    const onOffline = () => this.set({ status: 'offline' });
    window.addEventListener('focus', onFocus);
    document.addEventListener('visibilitychange', onFocus);
    window.addEventListener('online', onOnline);
    window.addEventListener('offline', onOffline);
    this.stopFns.push(() => {
      window.removeEventListener('focus', onFocus);
      document.removeEventListener('visibilitychange', onFocus);
      window.removeEventListener('online', onOnline);
      window.removeEventListener('offline', onOffline);
    });
  }

  /** Stops syncing. Flushes the cache unless `discardCache` (sign-out), which also deletes it. */
  stop({ discardCache = false } = {}): void {
    this.stopFns.forEach((fn) => fn());
    this.stopFns = [];
    if (this.stopped) return;
    if (this.snapshotTimer) {
      clearTimeout(this.snapshotTimer);
      if (!discardCache) void this.takeSnapshot();
    }
    if (discardCache) {
      this.stopped = true;
      clearTimeout(this.saveTimer);
      if (this.cacheKey) localSet(this.cacheKey, null);
    } else if (this.saveTimer) {
      clearTimeout(this.saveTimer);
      this.saveCache();
    }
  }

  /** Pull from the backend. `full` reloads every row; otherwise only rows changed since each table's cursor. */
  refresh(full = false): Promise<void> {
    if (this.refreshing) {
      this.refreshAgain = true;
      return this.refreshing;
    }
    this.lastRefreshAt = Date.now();
    this.set({ status: 'syncing' });
    this.refreshing = (async () => {
      try {
        const results = await Promise.all(
          TABLES.map(async (t) => [t, await this.backend.pull(t, full ? null : this.since(t))] as const),
        );
        for (const [t, rows] of results) {
          this.merge(t, rows);
          for (const r of rows) this.advanceCursor(t, r.updated_at);
        }
        this.set({ loaded: true, ready: true, status: this.idleStatus(), lastSync: new Date().toISOString(), error: null });
      } catch (e) {
        const message = e instanceof Error ? e.message : String(e);
        this.set({ status: 'offline', error: message });
      } finally {
        this.refreshing = null;
        if (this.refreshAgain) {
          this.refreshAgain = false;
          void this.refresh(false);
        }
      }
    })();
    return this.refreshing;
  }

  private since(t: TableName): string | null {
    const c = this.cursors[t];
    return c ? new Date(Date.parse(c) - OVERLAP_MS).toISOString() : null;
  }

  private advanceCursor(t: TableName, updatedAt: string | undefined): void {
    if (!updatedAt) return;
    const cur = this.cursors[t];
    if (!cur || Date.parse(updatedAt) > Date.parse(cur)) this.cursors[t] = updatedAt;
  }

  private idleStatus(): SyncStatus {
    if (isOffline()) return 'offline';
    return this.pending.size > 0 ? 'syncing' : 'synced';
  }

  /** Applies remote rows. Rows with a local write in flight are skipped, and older versions never replace newer ones. */
  private merge<T extends TableName>(table: T, rows: RowTypes[T][]): void {
    if (rows.length === 0) return;
    const current = this.table(table);
    let next: Record<string, RowTypes[T]> | null = null;
    for (const row of rows) {
      const key = rowKey(table, row);
      if (this.pending.has(`${table}:${key}`)) continue;
      const have = current[key];
      if (have?.updated_at && row.updated_at && Date.parse(row.updated_at) < Date.parse(have.updated_at)) continue;
      next ??= { ...current };
      next[key] = row;
      this.advanceCursor(table, row.updated_at);
    }
    if (next) this.set({ tables: { ...this.state.tables, [table]: next } });
  }

  // ---- writes ----

  /**
   * Optimistic upsert. Rows are complete rows of `table`. Resolves true when the backend accepted them;
   * on failure the rows revert to their previous state, `onError` is called and it resolves false.
   */
  async write<T extends TableName>(table: T, rows: RowTypes[T][], { allowPullOnly = false } = {}): Promise<boolean> {
    if (rows.length === 0) return true;
    if (PULL_ONLY.has(table) && !allowPullOnly) throw new Error(`${table} is pull-only`);
    const netWorthChange = this.affectsNetWorth(table, rows);
    const token = ++this.seq;
    const before = this.table(table);
    const next = { ...before };
    const keys: string[] = [];
    const prev = new Map<string, RowTypes[T] | undefined>();
    for (const row of rows) {
      const key = rowKey(table, row);
      keys.push(key);
      if (!prev.has(key)) prev.set(key, before[key]);
      next[key] = { ...before[key], ...row };
      this.pending.set(`${table}:${key}`, token);
    }
    this.set({
      tables: { ...this.state.tables, [table]: next },
      pendingWrites: this.state.pendingWrites + 1,
      status: 'syncing',
    });

    try {
      const saved = await this.backend.upsert(table, rows);
      const cur = { ...this.table(table) };
      for (const row of saved) {
        const key = rowKey(table, row);
        if (this.pending.get(`${table}:${key}`) !== token) continue; // edited again since: keep the newer local row
        this.pending.delete(`${table}:${key}`);
        cur[key] = row;
        this.advanceCursor(table, row.updated_at);
      }
      for (const key of keys) if (this.pending.get(`${table}:${key}`) === token) this.pending.delete(`${table}:${key}`);
      this.set({ tables: { ...this.state.tables, [table]: cur }, pendingWrites: this.state.pendingWrites - 1, status: this.idleStatus() });
      if (netWorthChange) this.requestSnapshot();
      return true;
    } catch (e) {
      const cur = { ...this.table(table) };
      for (const key of keys) {
        if (this.pending.get(`${table}:${key}`) !== token) continue;
        this.pending.delete(`${table}:${key}`);
        const old = prev.get(key);
        if (old) cur[key] = old;
        else delete cur[key];
      }
      this.set({ tables: { ...this.state.tables, [table]: cur }, pendingWrites: this.state.pendingWrites - 1, status: this.idleStatus() });
      const message = e instanceof Error ? e.message : String(e);
      this.onError(isOffline() ? "You're offline. The change wasn't saved." : `Couldn't save: ${message}`);
      return false;
    }
  }

  // ---- net worth snapshots (DOMAIN_RULES §5b) ----

  /** Accounts, IOUs, and budgets / categories that an account links to change net worth. */
  private affectsNetWorth<T extends TableName>(table: T, rows: RowTypes[T][]): boolean {
    if (table === 'accounts' || table === 'ledger_entries') return true;
    if (table !== 'budgets' && table !== 'categories') return false;
    const linked = new Set(this.dataset().accounts.map((a) => a.linked_category_id).filter(Boolean));
    if (linked.size === 0) return false;
    return rows.some((r) => linked.has(table === 'budgets' ? (r as RowTypes['budgets']).category_id : (r as RowTypes['categories']).id));
  }

  /** Debounced RPC take_net_worth_snapshot(); its row arrives here directly and via realtime. */
  requestSnapshot(): void {
    if (!this.backend.snapshot || this.stopped) return;
    clearTimeout(this.snapshotTimer);
    this.snapshotTimer = setTimeout(() => void this.takeSnapshot(), SNAPSHOT_DEBOUNCE_MS);
  }

  private async takeSnapshot(): Promise<void> {
    this.snapshotTimer = undefined;
    try {
      const row = await this.backend.snapshot?.();
      if (row) this.merge('net_worth_snapshots', [row]);
    } catch {
      // Not critical: the next change or the nightly job writes today's snapshot.
    }
  }

  // ---- cache ----

  private hydrate(): void {
    if (!this.cacheKey) return;
    const raw = localGet(this.cacheKey);
    if (!raw) return;
    try {
      const file = JSON.parse(raw) as CacheFile;
      if (file.v !== CACHE_VERSION || !file.tables) return;
      const tables = emptyTables();
      for (const t of TABLES) (tables as Record<string, unknown>)[t] = file.tables[t] ?? {};
      this.cursors = file.cursors ?? {};
      this.set({ tables, ready: true, lastSync: file.lastSync ?? null });
    } catch {
      localSet(this.cacheKey, null);
    }
  }

  private scheduleSave(): void {
    if (!this.cacheKey || this.stopped) return;
    clearTimeout(this.saveTimer);
    this.saveTimer = setTimeout(() => this.saveCache(), 600);
  }

  private saveCache(): void {
    this.saveTimer = undefined;
    if (!this.cacheKey || !this.state.ready) return;
    const file: CacheFile = { v: CACHE_VERSION, tables: this.state.tables, cursors: this.cursors, lastSync: this.state.lastSync };
    localSet(this.cacheKey, JSON.stringify(file));
  }
}
