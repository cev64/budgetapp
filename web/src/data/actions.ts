import type { Store } from './store';
import { planNewMonth } from '../domain/newMonth';
import { byOrder } from '../domain/calc';
import type { ImportPlan } from '../domain/backup';
import { LIST_TABLES, type RowTypes } from '../domain/tables';
import type {
  Account, Budget, Category, CategoryKind, LedgerEntry, RecurringItem, Settings, Transaction, YM,
} from '../domain/types';

/** UUID v4. crypto.randomUUID needs a secure context, so fall back to getRandomValues (e.g. dev over a LAN IP). */
export function newId(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID();
  const b = crypto.getRandomValues(new Uint8Array(16));
  b[6] = (b[6]! & 0x0f) | 0x40;
  b[8] = (b[8]! & 0x3f) | 0x80;
  const h = [...b].map((x) => x.toString(16).padStart(2, '0')).join('');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

const nextOrder = (rows: { sort_order: number }[]) => rows.reduce((m, r) => Math.max(m, r.sort_order), -1) + 1;

export type Actions = ReturnType<typeof createActions>;

/** Every user-facing write. Each one goes through the optimistic store. */
export function createActions(store: Store, notify: (message: string) => void) {
  const ds = () => store.dataset();

  async function setBudget(ym: YM, categoryId: string, patch: Partial<Pick<Budget, 'expected' | 'actual'>>) {
    const existing = store.row('budgets', `${ym.year}-${ym.month}-${categoryId}`);
    const base: Budget = existing && !existing.deleted
      ? existing
      : { year: ym.year, month: ym.month, category_id: categoryId, expected: null, actual: null };
    return store.write('budgets', [{ ...base, ...patch, deleted: false }]);
  }

  async function createMonth(ym: YM) {
    const plan = planNewMonth(ds(), ym, newId);
    if (!plan) return true;
    const ok = await store.write('months', [plan.month]);
    if (!ok) return false;
    const [b, t] = await Promise.all([store.write('budgets', plan.budgets), store.write('transactions', plan.transactions)]);
    return b && t;
  }

  async function setMonthClosed(ym: YM, closed: boolean) {
    const m = store.row('months', `${ym.year}-${ym.month}`);
    if (!m) return false;
    return store.write('months', [{ ...m, closed }]);
  }

  // ---- transactions ----

  const saveTransaction = (t: Transaction) => store.write('transactions', [{ ...t, deleted: false }]);
  const deleteTransaction = (t: Transaction) => store.write('transactions', [{ ...t, deleted: true }]);

  // ---- categories ----

  function categoryHasData(id: string): boolean {
    const d = ds();
    return (
      d.transactions.some((t) => t.category_id === id) ||
      d.budgets.some((b) => b.category_id === id && (b.expected != null || b.actual != null)) ||
      d.recurring_items.some((r) => r.category_id === id) ||
      d.accounts.some((a) => a.linked_category_id === id)
    );
  }

  function newCategory(kind: CategoryKind, name: string): Category {
    const sameKind = ds().categories.filter((c) => c.kind === kind);
    const fallback = { income: 0, expense: 10, savings: 20 }[kind];
    return {
      id: newId(),
      name,
      kind,
      tracking: kind === 'savings' ? 'manual' : 'ledger',
      match_multiplier: 1,
      sort_order: sameKind.length ? nextOrder(sameKind) : fallback,
      icon: null,
      color: null,
      archived: false,
    };
  }

  const saveCategory = (c: Category) => store.write('categories', [{ ...c, deleted: false }]);

  async function deleteCategory(c: Category) {
    if (categoryHasData(c.id)) {
      notify(`${c.name} has data. Archive it instead.`);
      return false;
    }
    return store.write('categories', [{ ...c, deleted: true }]);
  }

  /** Moves a category one step within its kind, renumbering that kind's sort orders when needed. */
  async function moveCategory(c: Category, dir: -1 | 1) {
    const group = ds().categories.filter((x) => x.kind === c.kind).sort(byOrder);
    const i = group.findIndex((x) => x.id === c.id);
    const j = i + dir;
    if (i < 0 || j < 0 || j >= group.length) return true;
    const orders = group.map((x) => x.sort_order);
    const distinct = new Set(orders).size === orders.length;
    const slots = distinct ? orders : group.map((_, k) => (orders[0] ?? 0) + k);
    const reordered = group.slice();
    [reordered[i], reordered[j]] = [reordered[j]!, reordered[i]!];
    const changed = reordered.flatMap((x, k) => (x.sort_order === slots[k] ? [] : [{ ...x, sort_order: slots[k]! }]));
    return store.write('categories', changed);
  }

  // ---- recurring ----

  function newRecurring(categoryId: string): RecurringItem {
    return { id: newId(), category_id: categoryId, item: '', amount: 0, day_of_month: 1, active: true, sort_order: nextOrder(ds().recurring_items) };
  }
  const saveRecurring = (r: RecurringItem) => store.write('recurring_items', [{ ...r, deleted: false }]);
  const deleteRecurring = (r: RecurringItem) => store.write('recurring_items', [{ ...r, deleted: true }]);

  // ---- accounts & ledger ----

  function newAccount(): Account {
    return {
      id: newId(), name: '', account_group: 'cash', liquid: true, balance: 0, linked_category_id: null,
      base_amount: 0, sort_order: nextOrder(ds().accounts), archived: false,
    };
  }
  const saveAccount = (a: Account) => store.write('accounts', [{ ...a, deleted: false }]);
  const deleteAccount = (a: Account) => store.write('accounts', [{ ...a, deleted: true }]);

  function newLedgerEntry(): LedgerEntry {
    return { id: newId(), name: '', amount: 0, note: null, settled: false, sort_order: nextOrder(ds().ledger_entries) };
  }
  const saveLedgerEntry = (e: LedgerEntry) => store.write('ledger_entries', [{ ...e, deleted: false }]);
  const deleteLedgerEntry = (e: LedgerEntry) => store.write('ledger_entries', [{ ...e, deleted: true }]);

  // ---- settings & import ----

  const updateSettings = (patch: Partial<Settings>) => store.write('settings', [{ ...ds().settings, ...patch, deleted: false }]);

  async function importBackup(plan: ImportPlan) {
    let ok = await store.write('settings', [plan.settings]);
    for (const t of LIST_TABLES) {
      // Import is the one time a client writes net_worth_snapshots (SYNC.md).
      ok = (await store.write(t, plan.rows[t] as RowTypes[typeof t][], { allowPullOnly: t === 'net_worth_snapshots' })) && ok;
    }
    store.requestSnapshot();
    return ok;
  }

  return {
    setBudget, createMonth, setMonthClosed,
    saveTransaction, deleteTransaction,
    newCategory, saveCategory, deleteCategory, moveCategory, categoryHasData,
    newRecurring, saveRecurring, deleteRecurring,
    newAccount, saveAccount, deleteAccount,
    newLedgerEntry, saveLedgerEntry, deleteLedgerEntry,
    updateSettings, importBackup,
  };
}
