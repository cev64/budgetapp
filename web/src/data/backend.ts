import type { SupabaseClient } from '@supabase/supabase-js';
import { LIST_TABLES, ON_CONFLICT, TABLES, normalizeRow, pickColumns, type RowTypes, type TableName } from '../domain/tables';
import type { BackupFile } from '../domain/backup';
import type { NetWorthSnapshot } from '../domain/types';
import { computeNetWorth } from '../domain/networth';
import { todayIso } from '../domain/dates';
import { synthesizeHistory } from './demoHistory';

/** Where rows come from and go to. The store is the same for Supabase and demo mode. */
export interface Backend {
  readonly kind: 'supabase' | 'demo';
  /** Rows changed after `since` (all rows when null), paginated. */
  pull<T extends TableName>(table: T, since: string | null): Promise<RowTypes[T][]>;
  /** Upserts rows and returns them as stored (with the server's updated_at). */
  upsert<T extends TableName>(table: T, rows: RowTypes[T][]): Promise<RowTypes[T][]>;
  /** Live changes. Returns an unsubscribe function. */
  subscribe(handlers: RealtimeHandlers): () => void;
  /**
   * Asks the server to (re)write today's net worth snapshot (RPC take_net_worth_snapshot) and returns it.
   * Absent in demo mode.
   */
  snapshot?(): Promise<NetWorthSnapshot | null>;
}

export interface RealtimeHandlers {
  onRow<T extends TableName>(table: T, row: RowTypes[T]): void;
  onStatus(connected: boolean): void;
}

const PAGE = 1000;
const CHUNK = 500;

/** Stable secondary ordering so pages never overlap or skip rows that share an updated_at. */
const ORDER: Record<TableName, string[]> = {
  settings: ['user_id'],
  months: ['year', 'month'],
  budgets: ['year', 'month', 'category_id'],
  categories: ['id'],
  transactions: ['id'],
  recurring_items: ['id'],
  accounts: ['id'],
  ledger_entries: ['id'],
  net_worth_snapshots: ['taken_on'],
};

export class SupabaseBackend implements Backend {
  readonly kind = 'supabase';

  constructor(
    private readonly client: SupabaseClient,
    private readonly userId: string,
  ) {}

  async pull<T extends TableName>(table: T, since: string | null): Promise<RowTypes[T][]> {
    const out: RowTypes[T][] = [];
    for (let from = 0; ; from += PAGE) {
      let q = this.client.from(table).select('*').eq('user_id', this.userId);
      if (since) q = q.gt('updated_at', since);
      q = q.order('updated_at', { ascending: true });
      for (const col of ORDER[table]) q = q.order(col, { ascending: true });
      const { data, error } = await q.range(from, from + PAGE - 1);
      if (error) throw new Error(error.message);
      const rows = (data ?? []) as Record<string, unknown>[];
      for (const r of rows) out.push(normalizeRow(table, r));
      if (rows.length < PAGE) return out;
    }
  }

  async upsert<T extends TableName>(table: T, rows: RowTypes[T][]): Promise<RowTypes[T][]> {
    const out: RowTypes[T][] = [];
    for (let i = 0; i < rows.length; i += CHUNK) {
      // Never send updated_at (server-owned); always send user_id.
      const payload = rows.slice(i, i + CHUNK).map((r) => ({ ...pickColumns(table, r), user_id: this.userId }));
      const { data, error } = await this.client.from(table).upsert(payload, { onConflict: ON_CONFLICT[table] }).select();
      if (error) throw new Error(error.message);
      for (const r of (data ?? []) as Record<string, unknown>[]) out.push(normalizeRow(table, r));
    }
    return out;
  }

  subscribe({ onRow, onStatus }: RealtimeHandlers): () => void {
    let channel = this.client.channel(`budget-sync-${this.userId}`);
    for (const table of TABLES) {
      channel = channel.on(
        'postgres_changes',
        { event: '*', schema: 'public', table, filter: `user_id=eq.${this.userId}` },
        (payload) => {
          // Clients never hard-delete; a DELETE only carries the primary key, so there is nothing to merge.
          if (payload.eventType === 'DELETE') return;
          onRow(table, normalizeRow(table, payload.new as Record<string, unknown>));
        },
      );
    }
    channel.subscribe((status) => {
      if (status === 'SUBSCRIBED') onStatus(true);
      else if (status === 'CHANNEL_ERROR' || status === 'TIMED_OUT' || status === 'CLOSED') onStatus(false);
    });
    return () => {
      void this.client.removeChannel(channel);
    };
  }

  async snapshot(): Promise<NetWorthSnapshot | null> {
    const { data, error } = await this.client.rpc('take_net_worth_snapshot');
    if (error) throw new Error(error.message);
    return data ? normalizeRow('net_worth_snapshots', data as Record<string, unknown>) : null;
  }
}

/**
 * In-memory backend for demo mode: serves the synthetic fixture (plus a synthesized net worth
 * history), writes never leave the page. No snapshot RPC.
 */
export class DemoBackend implements Backend {
  readonly kind = 'demo';
  private served = new Set<TableName>();

  constructor(private readonly file: BackupFile) {}

  async pull<T extends TableName>(table: T, since: string | null): Promise<RowTypes[T][]> {
    if (since && this.served.has(table)) return [];
    this.served.add(table);
    const stamp = '2026-01-01T00:00:00Z';
    if (table === 'settings') return [{ ...this.file.settings, updated_at: stamp, deleted: false } as RowTypes[T]];
    if (!(LIST_TABLES as TableName[]).includes(table)) return [];
    if (table === 'net_worth_snapshots') {
      // A synthetic year of history ending at the fixture's net worth; memory only.
      const f = this.file;
      const nw = computeNetWorth({ accounts: f.accounts, categories: f.categories, budgets: f.budgets, ledger: f.ledger_entries });
      return synthesizeHistory(nw, todayIso()).map((r) => ({ ...r, updated_at: stamp })) as RowTypes[T][];
    }
    const rows = (this.file as unknown as Record<string, Record<string, unknown>[]>)[table] ?? [];
    return rows.map((r) => normalizeRow(table, { ...r, updated_at: stamp }));
  }

  async upsert<T extends TableName>(_table: T, rows: RowTypes[T][]): Promise<RowTypes[T][]> {
    const now = new Date().toISOString();
    return rows.map((r) => ({ ...r, user_id: 'demo', updated_at: now }));
  }

  subscribe({ onStatus }: RealtimeHandlers): () => void {
    onStatus(true);
    return () => {};
  }
}
