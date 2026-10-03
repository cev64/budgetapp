import { describe, expect, it, vi } from 'vitest';
import { Store } from '../src/data/store';
import type { Backend, RealtimeHandlers } from '../src/data/backend';
import type { RowTypes, TableName } from '../src/domain/tables';
import type { Transaction } from '../src/domain/types';
import { generateToken, sha256Hex } from '../src/data/mcp';

/** Backend whose upserts resolve or reject on demand, with a controllable realtime feed. */
class FakeBackend implements Backend {
  readonly kind = 'demo' as const;
  rows: Partial<Record<TableName, unknown[]>> = {};
  pending: { resolve: (rows: unknown[]) => void; reject: (e: Error) => void; rows: unknown[] }[] = [];
  handlers: RealtimeHandlers | null = null;

  async pull<T extends TableName>(table: T): Promise<RowTypes[T][]> {
    return (this.rows[table] ?? []) as RowTypes[T][];
  }
  upsert<T extends TableName>(_table: T, rows: RowTypes[T][]): Promise<RowTypes[T][]> {
    return new Promise((resolve, reject) => this.pending.push({ resolve: resolve as (r: unknown[]) => void, reject, rows }));
  }
  subscribe(h: RealtimeHandlers) {
    this.handlers = h;
    return () => {};
  }
}

const tx = (over: Partial<Transaction> = {}): Transaction => ({
  id: 't1', year: 2026, month: 1, category_id: 'c', date: null, item: 'coffee', amount: 5, note: null, ...over,
});

function setup() {
  const backend = new FakeBackend();
  const onError = vi.fn();
  const store = new Store(backend, null, onError);
  return { backend, store, onError };
}

describe('Store optimistic writes', () => {
  it('applies locally first, then keeps the server row', async () => {
    const { backend, store } = setup();
    const done = store.write('transactions', [tx()]);
    expect(store.row('transactions', 't1')?.amount).toBe(5);
    expect(store.getState().status).toBe('syncing');
    backend.pending[0]!.resolve([{ ...tx(), updated_at: '2026-01-01T00:00:01Z' }]);
    expect(await done).toBe(true);
    expect(store.row('transactions', 't1')?.updated_at).toBe('2026-01-01T00:00:01Z');
    expect(store.getState().pendingWrites).toBe(0);
  });

  it('reverts and reports on error', async () => {
    const { backend, store, onError } = setup();
    const first = store.write('transactions', [tx()]);
    backend.pending[0]!.resolve([{ ...tx(), updated_at: '2026-01-01T00:00:01Z' }]);
    await first;
    const second = store.write('transactions', [tx({ amount: 99 })]);
    expect(store.row('transactions', 't1')?.amount).toBe(99);
    backend.pending[1]!.reject(new Error('boom'));
    expect(await second).toBe(false);
    expect(store.row('transactions', 't1')?.amount).toBe(5);
    expect(onError).toHaveBeenCalledWith(expect.stringContaining('boom'));
  });

  it('removes a never-saved row on error', async () => {
    const { backend, store } = setup();
    const p = store.write('transactions', [tx({ id: 'new' })]);
    backend.pending[0]!.reject(new Error('nope'));
    await p;
    expect(store.row('transactions', 'new')).toBeUndefined();
  });

  it('a newer local edit wins over an older write response', async () => {
    const { backend, store } = setup();
    const a = store.write('transactions', [tx({ amount: 1 })]);
    const b = store.write('transactions', [tx({ amount: 2 })]);
    backend.pending[0]!.resolve([{ ...tx({ amount: 1 }), updated_at: '2026-01-01T00:00:01Z' }]);
    await a;
    expect(store.row('transactions', 't1')?.amount).toBe(2);
    backend.pending[1]!.resolve([{ ...tx({ amount: 2 }), updated_at: '2026-01-01T00:00:02Z' }]);
    await b;
    expect(store.row('transactions', 't1')?.amount).toBe(2);
  });
});

describe('Store merges', () => {
  it('realtime rows never overwrite a write in flight, and stale rows are ignored', async () => {
    const { backend, store } = setup();
    vi.stubGlobal('window', { addEventListener() {}, removeEventListener() {} });
    vi.stubGlobal('document', { addEventListener() {}, removeEventListener() {}, visibilityState: 'visible' });
    store.start();
    await store.refresh(true);
    const p = store.write('transactions', [tx({ amount: 7 })]);
    backend.handlers!.onRow('transactions', { ...tx({ amount: 3 }), updated_at: '2026-01-01T00:00:05Z' });
    expect(store.row('transactions', 't1')?.amount).toBe(7);
    backend.pending[0]!.resolve([{ ...tx({ amount: 7 }), updated_at: '2026-01-01T00:00:06Z' }]);
    await p;
    backend.handlers!.onRow('transactions', { ...tx({ amount: 3 }), updated_at: '2026-01-01T00:00:05Z' });
    expect(store.row('transactions', 't1')?.amount).toBe(7);
    backend.handlers!.onRow('transactions', { ...tx({ amount: 8 }), updated_at: '2026-01-01T00:00:09Z' });
    expect(store.row('transactions', 't1')?.amount).toBe(8);
    // Tombstones stay in the table but leave the dataset.
    backend.handlers!.onRow('transactions', { ...tx({ amount: 8 }), deleted: true, updated_at: '2026-01-01T00:00:10Z' });
    expect(store.dataset().transactions).toHaveLength(0);
    store.stop();
    vi.unstubAllGlobals();
  });
});

describe('net worth snapshots', () => {
  it('pull-only: plain writes are refused', async () => {
    const { store } = setup();
    await expect(store.write('net_worth_snapshots', [])).resolves.toBe(true);
    await expect(
      store.write('net_worth_snapshots', [{ taken_on: '2026-10-03', net_worth: 1, super_liquid: 1, reconciliations: 0, accounts: [], source: 'manual' }]),
    ).rejects.toThrow(/pull-only/);
  });

  it('net-worth writes request one debounced snapshot RPC; others do not', async () => {
    vi.useFakeTimers();
    const { backend, store } = setup();
    const row = { taken_on: '2026-10-03', net_worth: 10, super_liquid: 5, reconciliations: 0, accounts: [], source: 'manual' as const, updated_at: '2026-10-03T00:00:00Z' };
    const snapshot = vi.fn(async () => row);
    (backend as FakeBackend & { snapshot: typeof snapshot }).snapshot = snapshot;
    const settle = async (p: Promise<boolean>, i: number) => {
      backend.pending[i]!.resolve(backend.pending[i]!.rows);
      await p;
    };
    const account = { id: 'a', name: 'Checking', account_group: 'cash' as const, liquid: true, balance: 1, linked_category_id: 'k', base_amount: 0, sort_order: 0, archived: false };
    await settle(store.write('accounts', [account]), 0);
    await settle(store.write('accounts', [{ ...account, balance: 2 }]), 1);
    await settle(store.write('transactions', [tx()]), 2);
    await vi.advanceTimersByTimeAsync(1500);
    expect(snapshot).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(600);
    expect(snapshot).toHaveBeenCalledTimes(1);
    expect(store.dataset().net_worth_snapshots).toHaveLength(1);

    // A budget of the linked category counts; one of another category doesn't.
    const budget = { year: 2026, month: 1, category_id: 'other', expected: 1, actual: 2 };
    await settle(store.write('budgets', [budget]), 3);
    await vi.advanceTimersByTimeAsync(2100);
    expect(snapshot).toHaveBeenCalledTimes(1);
    await settle(store.write('budgets', [{ ...budget, category_id: 'k' }]), 4);
    await vi.advanceTimersByTimeAsync(2100);
    expect(snapshot).toHaveBeenCalledTimes(2);
    vi.useRealTimers();
  });
});

describe('MCP tokens', () => {
  it('bgt_ + 32 random bytes as base64url', () => {
    const t = generateToken();
    expect(t).toMatch(/^bgt_[A-Za-z0-9_-]{43}$/);
    expect(generateToken()).not.toBe(t);
  });
  it('sha256 lowercase hex', async () => {
    expect(await sha256Hex('abc')).toBe('ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
  });
});
