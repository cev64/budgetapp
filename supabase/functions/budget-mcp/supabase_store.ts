// BudgetStore backed by Supabase (PostgREST) using the SERVICE ROLE client.
// The service role bypasses row-level security, so this class is the security boundary:
// every read filters `user_id = this.userId` and every write sets `user_id = this.userId`.

import type { SupabaseClient } from "./deps.ts";
import {
  type Account,
  type Budget,
  type Category,
  DEFAULT_SETTINGS,
  type LedgerEntry,
  type MealItem,
  type MealPlan,
  type Month,
  type NetWorthSnapshot,
  type RecurringItem,
  type Settings,
  type Transaction,
} from "../_shared/domain.ts";
import {
  accountRow,
  type BudgetFilter,
  budgetRow,
  type BudgetStore,
  ledgerEntryRow,
  monthRow,
  type TransactionFilter,
  transactionRow,
} from "./store.ts";

const PAGE = 1000;

// PostgREST returns numeric columns as JSON numbers, but be defensive (strings, null).
const num = (v: unknown): number => (v === null || v === undefined ? 0 : Number(v));
const numOrNull = (v: unknown): number | null => (v === null || v === undefined ? null : Number(v));

// deno-lint-ignore no-explicit-any
type Row = Record<string, any>;

function toCategory(r: Row): Category {
  return { ...r, match_multiplier: num(r.match_multiplier ?? 1), sort_order: num(r.sort_order) } as Category;
}
function toBudget(r: Row): Budget {
  return { ...r, expected: numOrNull(r.expected), actual: numOrNull(r.actual) } as Budget;
}
function toTransaction(r: Row): Transaction {
  return { ...r, amount: num(r.amount) } as Transaction;
}
function toRecurring(r: Row): RecurringItem {
  return { ...r, amount: num(r.amount) } as RecurringItem;
}
function toAccount(r: Row): Account {
  return { ...r, balance: num(r.balance), base_amount: num(r.base_amount) } as Account;
}
function toLedger(r: Row): LedgerEntry {
  return { ...r, amount: num(r.amount) } as LedgerEntry;
}
function toMealItem(r: Row): MealItem {
  return {
    ...r,
    calories: numOrNull(r.calories),
    protein: numOrNull(r.protein),
    fiber: numOrNull(r.fiber),
    fat: numOrNull(r.fat),
    cost: numOrNull(r.cost),
  } as MealItem;
}

function toSnapshot(r: Row): NetWorthSnapshot {
  return {
    taken_on: String(r.taken_on),
    net_worth: num(r.net_worth),
    super_liquid: num(r.super_liquid),
    reconciliations: num(r.reconciliations),
    accounts: (Array.isArray(r.accounts) ? r.accounts : []).map((a: Row) => ({
      id: String(a.id),
      name: String(a.name ?? ""),
      group: String(a.group ?? ""),
      liquid: !!a.liquid,
      balance: num(a.balance),
    })),
    source: r.source ?? "auto",
    deleted: !!r.deleted,
  };
}

export class StoreError extends Error {}

export class SupabaseStore implements BudgetStore {
  constructor(private readonly db: SupabaseClient, private readonly userId: string) {
    if (!userId) throw new Error("SupabaseStore requires a user id");
  }

  /** Base query for `table`, always scoped to this user and to live (non-deleted) rows. */
  private q(table: string) {
    return this.db.from(table).select("*").eq("user_id", this.userId).eq("deleted", false);
  }

  private async all(
    table: string,
    // deno-lint-ignore no-explicit-any
    refine: (q: any) => any = (q) => q,
    order: string[] = ["id"],
  ): Promise<Row[]> {
    const out: Row[] = [];
    for (let from = 0;; from += PAGE) {
      let query = refine(this.q(table));
      for (const col of order) query = query.order(col, { ascending: true });
      const { data, error } = await query.range(from, from + PAGE - 1);
      if (error) throw new StoreError(`Database error reading ${table}: ${error.message}`);
      out.push(...(data ?? []));
      if (!data || data.length < PAGE) return out;
    }
  }

  private async write(table: string, onConflict: string, rows: Row[]): Promise<Row[]> {
    if (rows.length === 0) return [];
    const payload = rows.map((r) => ({ ...r, user_id: this.userId }));
    const { data, error } = await this.db.from(table).upsert(payload, { onConflict }).select();
    if (error) throw new StoreError(`Database error writing ${table}: ${error.message}`);
    return data ?? [];
  }

  async getSettings(): Promise<Settings> {
    const { data, error } = await this.q("settings").maybeSingle();
    if (error) throw new StoreError(`Database error reading settings: ${error.message}`);
    if (!data) return { ...DEFAULT_SETTINGS };
    return {
      net_income: num(data.net_income),
      gross_income: num(data.gross_income),
      currency: data.currency ?? "USD",
      days_per_month: num(data.days_per_month),
    };
  }

  async listCategories() {
    return (await this.all("categories")).map(toCategory);
  }

  async listMonths() {
    return (await this.all("months", undefined, ["year", "month"])) as Month[];
  }

  async listBudgets(f: BudgetFilter = {}) {
    if (f.categoryIds && f.categoryIds.length === 0) return [];
    const rows = await this.all("budgets", (q) => {
      if (f.year !== undefined) q = q.eq("year", f.year);
      if (f.month !== undefined) q = q.eq("month", f.month);
      if (f.categoryIds) q = q.in("category_id", f.categoryIds);
      return q;
    }, ["year", "month", "category_id"]);
    return rows.map(toBudget);
  }

  async listTransactions(f: TransactionFilter = {}) {
    const rows = await this.all("transactions", (q) => {
      if (f.year !== undefined) q = q.eq("year", f.year);
      if (f.month !== undefined) q = q.eq("month", f.month);
      if (f.categoryId !== undefined) q = q.eq("category_id", f.categoryId);
      return q;
    });
    return rows.map(toTransaction);
  }

  async getTransaction(id: string) {
    const { data, error } = await this.q("transactions").eq("id", id).maybeSingle();
    if (error) throw new StoreError(`Database error reading transaction: ${error.message}`);
    return data ? toTransaction(data) : null;
  }

  async listRecurringItems() {
    return (await this.all("recurring_items")).map(toRecurring);
  }
  async listAccounts() {
    return (await this.all("accounts")).map(toAccount);
  }
  async listLedgerEntries() {
    return (await this.all("ledger_entries")).map(toLedger);
  }
  async listMealPlans() {
    return (await this.all("meal_plans")) as MealPlan[];
  }
  async listMealItems() {
    return (await this.all("meal_items")).map(toMealItem);
  }

  async listNetWorthSnapshots(f: { from?: string } = {}) {
    const rows = await this.all("net_worth_snapshots", (q) => (f.from ? q.gte("taken_on", f.from) : q), ["taken_on"]);
    return rows.map(toSnapshot);
  }

  async refreshNetWorthSnapshot() {
    // SECURITY DEFINER SQL function; executable by service_role only (revoked from anon/authenticated).
    const { data, error } = await this.db.rpc("write_net_worth_snapshot", { p_user: this.userId, p_source: "manual" });
    if (error) throw new StoreError(`Database error refreshing the net worth snapshot: ${error.message}`);
    const row = Array.isArray(data) ? data[0] : data;
    return row ? toSnapshot(row) : null;
  }

  async upsertMonths(rows: Month[]) {
    return (await this.write("months", "user_id,year,month", rows.map(monthRow))) as Month[];
  }
  async upsertBudgets(rows: Budget[]) {
    return (await this.write("budgets", "user_id,year,month,category_id", rows.map(budgetRow))).map(toBudget);
  }
  async upsertTransactions(rows: Transaction[]) {
    return (await this.write("transactions", "id", rows.map(transactionRow))).map(toTransaction);
  }
  async upsertAccounts(rows: Account[]) {
    return (await this.write("accounts", "id", rows.map(accountRow))).map(toAccount);
  }
  async upsertLedgerEntries(rows: LedgerEntry[]) {
    return (await this.write("ledger_entries", "id", rows.map(ledgerEntryRow))).map(toLedger);
  }
}
