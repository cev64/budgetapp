// MCP tool definitions + handlers for the budget server.
// Handlers depend only on a BudgetStore (see store.ts), so they are tested against the
// in-memory store (tools_test.ts) and run against Supabase in production (index.ts).

import { type CallToolResult, type McpServer, z } from "./deps.ts";
import {
  type Budget,
  type Category,
  compareTransactionsDesc,
  compareYM,
  formatMoney,
  formatPct,
  formatShortDate,
  formatSignedPct,
  type HistoryChange,
  type HistoryRange,
  historyView,
  accountBalanceIn,
  type NetWorthSnapshot,
  type Kind,
  type MatchResult,
  matchByName,
  mealPlansView,
  type MonthCategoryRow,
  monthName,
  monthView,
  type MonthView,
  netWorthView,
  parseIsoDate,
  planNewMonth,
  r4,
  type Snapshot,
  sortCategories,
  TIMEZONE,
  todayParts,
  type Transaction,
  yearView,
} from "../_shared/domain.ts";
import type { BudgetStore } from "./store.ts";

// ---------------------------------------------------------------------------
// Context, errors, result helpers
// ---------------------------------------------------------------------------

export interface ToolContext {
  store: BudgetStore;
  /** Injectable clock (tests pin "today"). */
  now: () => Date;
  newId: () => string;
}

export function makeContext(store: BudgetStore, opts: Partial<Omit<ToolContext, "store">> = {}): ToolContext {
  return { store, now: opts.now ?? (() => new Date()), newId: opts.newId ?? (() => crypto.randomUUID()) };
}

/** An expected, user-facing failure (bad name, missing month...). Returned as isError tool output. */
export class ToolError extends Error {}

export interface ToolOutput {
  text: string;
  data: Record<string, unknown>;
}

// deno-lint-ignore no-explicit-any
type Shape = Record<string, any>;

export interface ToolDef {
  name: string;
  title: string;
  description: string;
  inputSchema: Shape;
  annotations: {
    readOnlyHint: boolean;
    destructiveHint?: boolean;
    idempotentHint?: boolean;
    openWorldHint: boolean;
  };
  // deno-lint-ignore no-explicit-any
  handler: (args: any, ctx: ToolContext) => Promise<ToolOutput>;
}

const READ = { readOnlyHint: true, openWorldHint: false } as const;
const WRITE = (o: { destructive?: boolean; idempotent?: boolean } = {}) => ({
  readOnlyHint: false,
  destructiveHint: o.destructive ?? false,
  idempotentHint: o.idempotent ?? false,
  openWorldHint: false,
});

// ---------------------------------------------------------------------------
// Shared schemas
// ---------------------------------------------------------------------------

const yearS = z.number().int().min(2000).max(2100);
const monthS = z.number().int().min(1).max(12);
const moneyS = z.number().finite();
const dateS = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Use YYYY-MM-DD");
const uuidRe = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;
const idS = z.string().regex(uuidRe, "Expected a transaction id (uuid) from list_transactions").describe("Transaction id (uuid) from list_transactions.");
const ledgerIdS = z.string().regex(uuidRe, "Expected a ledger entry id (uuid) from get_net_worth");
const categoryS = z.string().min(1).describe(
  'Category name, case-insensitive; prefixes and close spellings work (e.g. "food", "car insurance" for "Car Ins", "brokerage" for "Taxable Brokerage", "401k").',
);
const yearArg = yearS.optional().describe("Budget year, e.g. 2026. Defaults to the current year (America/New_York).");
const monthArg = monthS.optional().describe("Budget month 1-12. Defaults to the current month (America/New_York).");

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function today(ctx: ToolContext) {
  return todayParts(ctx.now(), TIMEZONE);
}

const label = (y: number, m: number) => `${monthName(m)} ${y}`;

function names<T extends { name: string }>(items: T[]): string {
  return items.map((i) => `"${i.name}"`).join(", ");
}

function explainMatch<T extends { name: string }>(what: string, query: string, res: MatchResult<T>, all: T[]): never {
  if (res.ok) throw new Error("unreachable");
  if (res.reason === "ambiguous") {
    throw new ToolError(`"${query}" matches more than one ${what}: ${names(res.candidates)}. Please use the exact name.`);
  }
  throw new ToolError(`No ${what} matches "${query}". Valid ${what} names: ${names(all)}.`);
}

function resolveCategory(query: string, cats: Category[]): Category {
  const sorted = sortCategories(cats);
  const res = matchByName(query, sorted);
  if (res.ok) return res.item;
  return explainMatch("category", query, res, sorted.filter((c) => !c.archived).length ? sorted.filter((c) => !c.archived) : sorted);
}

function parseDateArg(date: string | null | undefined): { year: number; month: number; day: number } | null {
  if (date === undefined || date === null) return null;
  const p = parseIsoDate(date);
  if (!p) throw new ToolError(`"${date}" is not a valid date. Use YYYY-MM-DD.`);
  return p;
}

async function monthSnapshot(ctx: ToolContext, y: number, m: number): Promise<Snapshot> {
  const [settings, categories, months, budgets, transactions] = await Promise.all([
    ctx.store.getSettings(),
    ctx.store.listCategories(),
    ctx.store.listMonths(),
    ctx.store.listBudgets({ year: y, month: m }),
    ctx.store.listTransactions({ year: y, month: m }),
  ]);
  return { settings, categories, months, budgets, transactions };
}

async function yearSnapshot(ctx: ToolContext, y: number): Promise<Snapshot> {
  const [settings, categories, months, budgets, transactions] = await Promise.all([
    ctx.store.getSettings(),
    ctx.store.listCategories(),
    ctx.store.listMonths(),
    ctx.store.listBudgets({ year: y }),
    ctx.store.listTransactions({ year: y }),
  ]);
  return { settings, categories, months, budgets, transactions };
}

/** §6: create (Y, M) if it does not exist. Returns a sentence describing what was created, or null. */
async function ensureMonth(ctx: ToolContext, y: number, m: number): Promise<string | null> {
  const [settings, categories, months, budgets, recurring_items] = await Promise.all([
    ctx.store.getSettings(),
    ctx.store.listCategories(),
    ctx.store.listMonths(),
    ctx.store.listBudgets(),
    ctx.store.listRecurringItems(),
  ]);
  const plan = planNewMonth(
    { settings, categories, months, budgets, transactions: [], recurring_items },
    y,
    m,
    ctx.newId,
  );
  if (!plan) return null;
  await ctx.store.upsertMonths([plan.month]);
  await ctx.store.upsertBudgets(plan.budgets);
  await ctx.store.upsertTransactions(plan.transactions);
  const copied = plan.budgets.filter((b) => b.expected !== null).length;
  const cat = new Map(categories.map((c) => [c.id, c.name]));
  const rec = plan.transactions.length
    ? ` and added ${plan.transactions.length} recurring transaction(s): ${
      plan.transactions.map((t) => `${t.item} ${formatMoney(t.amount)} (${cat.get(t.category_id) ?? "?"})`).join(", ")
    }`
    : "";
  return `${label(y, m)} did not exist, so it was created: copied ${copied} expected budget(s) forward from earlier months${rec}.`;
}

function txOut(t: Transaction, cats: Map<string, string>) {
  return {
    id: t.id,
    date: t.date ?? null,
    year: t.year,
    month: t.month,
    category: cats.get(t.category_id) ?? "(unknown category)",
    item: t.item,
    amount: r4(Number(t.amount)),
    note: t.note ?? null,
  };
}

function txLine(t: ReturnType<typeof txOut>, withMonth = false): string {
  const when = t.date ?? "no date";
  const bm = withMonth ? ` [${monthName(t.month).slice(0, 3)} ${t.year} budget]` : "";
  return `${when}  ${t.item || "(no item)"}  ${formatMoney(t.amount)}  (${t.category})${bm}${t.note ? ` — ${t.note}` : ""}  id:${t.id}`;
}

function rowOut(r: MonthCategoryRow) {
  return {
    name: r.name,
    tracking: r.tracking,
    expected: r.expected,
    actual: r.actual,
    difference: r.difference,
    remaining: r.remaining,
    override: r.override,
    ledger_sum: r.tracking === "ledger" ? r.ledger_sum : undefined,
    match_multiplier: r.match_multiplier !== 1 ? r.match_multiplier : undefined,
  };
}

function rowLine(r: MonthCategoryRow): string {
  const actual = r.actual === null ? "not entered" : formatMoney(r.actual);
  let tail: string;
  if (r.kind === "expense") {
    tail = r.remaining >= 0 ? `${formatMoney(r.remaining)} left` : `${formatMoney(-r.remaining)} over`;
  } else {
    tail = r.remaining > 0 ? `${formatMoney(r.remaining)} still to come` : r.remaining < 0 ? `${formatMoney(-r.remaining)} above plan` : "on plan";
  }
  const flags = [
    r.override ? `manual override; transactions sum to ${formatMoney(r.ledger_sum)}` : "",
    r.tracking === "manual" && r.actual === null ? "manual" : "",
    r.match_multiplier !== 1 ? `×${r.match_multiplier} match` : "",
  ].filter(Boolean);
  return `  ${r.name}: ${actual} of ${formatMoney(r.expected)} expected · ${tail}${flags.length ? ` (${flags.join("; ")})` : ""}`;
}

const GROUPS: { kind: Kind; key: "income" | "expenses" | "savings"; title: string }[] = [
  { kind: "income", key: "income", title: "Income" },
  { kind: "expense", key: "expenses", title: "Expenses" },
  { kind: "savings", key: "savings", title: "Savings" },
];

function totalsLines(v: MonthView): string[] {
  const a = v.actual, e = v.expected;
  return [
    `Leftover: ${formatMoney(a.leftover)} actual vs ${formatMoney(e.leftover)} expected`,
    `Income ${formatMoney(a.income)} / ${formatMoney(e.income)} · Expenses ${formatMoney(a.expenses)} / ${formatMoney(e.expenses)} · Saved (incl. match) ${formatMoney(a.saved)} / ${formatMoney(e.saved)}  (actual / expected)`,
  ];
}

function categoryStatus(v: MonthView, categoryId: string) {
  const r = v.categories.find((c) => c.category_id === categoryId);
  return r ? rowOut(r) : null;
}

function categorySentence(v: MonthView, categoryId: string): string {
  const r = v.categories.find((c) => c.category_id === categoryId);
  return r ? `${label(v.year, v.month)} → ${rowLine(r).trim()}` : "";
}

/**
 * §5b: after any write that can change net worth, refresh today's snapshot. Never fails the
 * user's write: errors are logged and reported as `false`.
 */
async function refreshSnapshotSafely(ctx: ToolContext): Promise<boolean> {
  try {
    await ctx.store.refreshNetWorthSnapshot();
    return true;
  } catch (e) {
    console.error("[budget-mcp] net worth snapshot refresh failed:", e instanceof Error ? e.message : String(e));
    return false;
  }
}

const RANGE_LABEL: Record<Exclude<HistoryRange, "All">, string> = { "1M": "30 days", "3M": "3 months", "6M": "6 months", "1Y": "1 year" };

/** "up $1,240 (+5.8%) over 3 months" / "down $300 (−1.2%) since Aug 3" / "unchanged over 30 days" */
function describeChange(c: HistoryChange, range: HistoryRange, refYear: number): string {
  const span = range === "All" || c.partial ? `since ${formatShortDate(c.base.date, refYear)}` : `over ${RANGE_LABEL[range]}`;
  if (c.amount === 0) return `unchanged ${span}`;
  const pct = c.pct === null ? "" : ` (${formatSignedPct(c.pct)})`;
  return `${c.amount > 0 ? "up" : "down"} ${formatMoney(Math.abs(c.amount))}${pct} ${span}`;
}

function changeOut(c: HistoryChange, range: HistoryRange, refYear: number) {
  return {
    amount: c.amount,
    pct: c.pct,
    from_date: c.base.date,
    from_value: c.base.value,
    to_date: c.latest.date,
    to_value: c.latest.value,
    partial: c.partial,
    label: describeChange(c, range, refYear),
  };
}

async function linkedToAccount(ctx: ToolContext, categoryId: string): Promise<boolean> {
  return (await ctx.store.listAccounts()).some((a) => a.linked_category_id === categoryId);
}

// ---------------------------------------------------------------------------
// Tools
// ---------------------------------------------------------------------------

export const TOOLS: ToolDef[] = [];
const tool = (def: ToolDef) => TOOLS.push(def);

// 1. get_budget_overview ------------------------------------------------------
tool({
  name: "get_budget_overview",
  title: "Budget overview for a month",
  description:
    "Show one budget month: every category's expected vs actual, difference and amount remaining (grouped into income, expenses and savings), the month totals (income, expenses, saved incl. employer match, leftover) and the 5 most recent transactions. " +
    "Use it for questions like \"how much fun money do I have left?\" or \"how am I doing this month?\". Defaults to the current month (America/New_York), or the latest existing month if the current one has not been created.",
  inputSchema: { year: yearArg, month: monthArg },
  annotations: READ,
  handler: async (args: { year?: number; month?: number }, ctx) => {
    const t = today(ctx);
    const months = (await ctx.store.listMonths()).sort(compareYM);
    let y: number, m: number, note = "";
    if (args.year === undefined && args.month === undefined) {
      const cur = months.find((mo) => mo.year === t.year && mo.month === t.month);
      const latest = months[months.length - 1];
      if (cur || !latest) [y, m] = [t.year, t.month];
      else {
        [y, m] = [latest.year, latest.month];
        note = `${label(t.year, t.month)} has not been created yet (use create_month), so this shows the latest month.\n`;
      }
    } else {
      y = args.year ?? t.year;
      m = args.month ?? t.month;
    }
    if (!months.some((mo) => mo.year === y && mo.month === m)) {
      const list = months.map((mo) => label(mo.year, mo.month)).join(", ") || "none yet";
      return {
        text: `${label(y, m)} does not exist yet. Existing months: ${list}. Use create_month to start it (adding a transaction to it also creates it).`,
        data: { year: y, month: m, name: label(y, m), exists: false, existing_months: months.map((mo) => ({ year: mo.year, month: mo.month })) },
      };
    }
    const snap = await monthSnapshot(ctx, y, m);
    const v = monthView(snap, y, m);
    const cats = new Map(snap.categories.map((c) => [c.id, c.name]));
    const recent = [...snap.transactions].sort(compareTransactionsDesc).slice(0, 5).map((tx) => txOut(tx, cats));
    const lines = [note + `${v.name} (${v.closed ? "closed: year summary uses actuals" : "open"})`, ...totalsLines(v)];
    const groups: Record<string, unknown> = {};
    for (const g of GROUPS) {
      const rows = v.categories.filter((r) => r.kind === g.kind);
      groups[g.key] = rows.map(rowOut);
      if (rows.length) lines.push("", `${g.title}:`, ...rows.map(rowLine));
    }
    lines.push("", "Recent transactions:", ...(recent.length ? recent.map((r) => "  " + txLine(r)) : ["  (none)"]));
    return {
      text: lines.join("\n"),
      data: {
        year: y,
        month: m,
        name: v.name,
        exists: true,
        closed: v.closed,
        groups,
        totals: { expected: v.expected, actual: v.actual, difference: v.difference },
        recent_transactions: recent,
      },
    };
  },
});

// 2. list_categories --------------------------------------------------------
tool({
  name: "list_categories",
  title: "List budget categories",
  description:
    "List all budget categories with kind (income / expense / savings), tracking (ledger = actual is the sum of its transactions; manual = actual is typed in with set_actual), employer-match multiplier (401k counts ×2 toward saved) and whether it is archived.",
  inputSchema: {},
  annotations: READ,
  handler: async (_args, ctx) => {
    const cats = sortCategories(await ctx.store.listCategories());
    const data = cats.map((c) => ({
      name: c.name,
      kind: c.kind,
      tracking: c.tracking,
      multiplier: Number(c.match_multiplier),
      archived: c.archived,
    }));
    const lines = cats.map((c) =>
      `${c.name} — ${c.kind}, ${c.tracking}${Number(c.match_multiplier) !== 1 ? `, ×${c.match_multiplier} match` : ""}${c.archived ? ", archived" : ""}`
    );
    return { text: lines.join("\n") || "No categories.", data: { categories: data } };
  },
});

// 3. add_transaction -------------------------------------------------------
tool({
  name: "add_transaction",
  title: "Add a transaction",
  description:
    'Record a spend, refund or paycheck in a category, e.g. "add $14 Chipotle to food" → {amount: 14, category: "food", item: "Chipotle"}. Negative amount = refund/reimbursement. ' +
    "The budget month defaults to the month of `date`, else the current month; pass year/month to file it under a different budget month (the date may fall outside it). `date` defaults to today when the budget month is the current month. " +
    "If the month does not exist yet it is created first (budgets copied forward, recurring items added). Transactions only drive the actual of ledger-tracked categories; for manual categories (Rent, bills, savings) use set_actual instead. Returns the new transaction and the category's updated actual vs expected.",
  inputSchema: {
    amount: moneyS.describe("Amount in dollars, e.g. 14 or 12.5. Negative = refund."),
    category: categoryS,
    item: z.string().min(1).max(200).describe('Short description, e.g. "Chipotle".'),
    date: dateS.optional().describe("Date it happened, YYYY-MM-DD. Defaults to today when filing into the current month."),
    year: yearS.optional().describe("Budget year to file under (defaults to the year of `date`, else current)."),
    month: monthS.optional().describe("Budget month 1-12 to file under (defaults to the month of `date`, else current)."),
    note: z.string().max(1000).optional().describe("Optional note."),
  },
  annotations: WRITE(),
  handler: async (args: { amount: number; category: string; item: string; date?: string; year?: number; month?: number; note?: string }, ctx) => {
    const t = today(ctx);
    const d = parseDateArg(args.date);
    const y = args.year ?? d?.year ?? t.year;
    const m = args.month ?? d?.month ?? t.month;
    const date = args.date ?? (y === t.year && m === t.month ? t.iso : null);
    const cat = resolveCategory(args.category, await ctx.store.listCategories());
    const created = await ensureMonth(ctx, y, m);
    const [row] = await ctx.store.upsertTransactions([{
      id: ctx.newId(),
      year: y,
      month: m,
      category_id: cat.id,
      date,
      item: args.item.trim(),
      amount: args.amount,
      note: args.note ?? null,
    }]);
    const snap = await monthSnapshot(ctx, y, m);
    const v = monthView(snap, y, m);
    const status = categoryStatus(v, cat.id);
    const warnings: string[] = [];
    if (cat.tracking === "manual") {
      warnings.push(`${cat.name} is tracked manually, so transactions do not change its actual. If this was the month's ${cat.name} amount, use set_actual instead (and delete this transaction if it is not wanted).`);
    } else if (status?.override) {
      warnings.push(`${cat.name} has a manual override actual for ${label(y, m)}, so this transaction does not change the displayed actual. Clear it with set_actual(actual: null) to use the transaction sum.`);
    }
    if (cat.archived) warnings.push(`${cat.name} is archived.`);
    if (v.closed) warnings.push(`${label(y, m)} is closed; the year summary already uses its actuals.`);
    const tx = txOut(row, new Map([[cat.id, cat.name]]));
    const lines = [
      ...(created ? [created] : []),
      `Added ${tx.item} ${formatMoney(tx.amount)} to ${cat.name} (${label(y, m)} budget, ${tx.date ?? "no date"}). id:${tx.id}`,
      categorySentence(v, cat.id),
      ...warnings.map((w) => `Note: ${w}`),
    ].filter(Boolean);
    return {
      text: lines.join("\n"),
      data: { transaction: tx, category: status, month_created: !!created, month_created_message: created, warnings },
    };
  },
});

// 4. list_transactions -----------------------------------------------------
tool({
  name: "list_transactions",
  title: "List / search transactions",
  description:
    "List transactions (with their ids, needed for update_transaction / delete_transaction), newest first (date desc, then creation time). " +
    "Filters: year and/or month (budget month; month alone means that month of the current year; omit both to search all months), category name, and `search` (case-insensitive text in item or note). Default limit 50.",
  inputSchema: {
    year: yearS.optional().describe("Budget year filter."),
    month: monthS.optional().describe("Budget month filter 1-12 (uses the current year if year is omitted)."),
    category: categoryS.optional(),
    search: z.string().max(200).optional().describe('Text to find in item or note, e.g. "chipotle".'),
    limit: z.number().int().min(1).max(500).default(50).describe("Max rows (default 50)."),
  },
  annotations: READ,
  handler: async (args: { year?: number; month?: number; category?: string; search?: string; limit: number }, ctx) => {
    const t = today(ctx);
    const cats = await ctx.store.listCategories();
    const cat = args.category ? resolveCategory(args.category, cats) : null;
    const year = args.year ?? (args.month !== undefined ? t.year : undefined);
    let rows = await ctx.store.listTransactions({ year, month: args.month, categoryId: cat?.id });
    if (args.search) {
      const s = args.search.toLowerCase();
      rows = rows.filter((r) => (r.item ?? "").toLowerCase().includes(s) || (r.note ?? "").toLowerCase().includes(s));
    }
    rows.sort(compareTransactionsDesc);
    const total = rows.length;
    const sum = r4(rows.reduce((a, r) => a + Number(r.amount), 0));
    const names = new Map(cats.map((c) => [c.id, c.name]));
    const out = rows.slice(0, args.limit ?? 50).map((r) => txOut(r, names));
    const scope = [
      year !== undefined && args.month !== undefined ? label(year, args.month) : year !== undefined ? String(year) : "all months",
      cat ? cat.name : null,
      args.search ? `"${args.search}"` : null,
    ].filter(Boolean).join(", ");
    const head = `${total} transaction(s) for ${scope}, totaling ${formatMoney(sum)}${total > out.length ? ` (showing ${out.length})` : ""}:`;
    return {
      text: [head, ...out.map((r) => "  " + txLine(r, year === undefined || args.month === undefined))].join("\n"),
      data: { count: total, total_amount: sum, transactions: out },
    };
  },
});

// 5. update_transaction ----------------------------------------------------
tool({
  name: "update_transaction",
  title: "Edit a transaction",
  description:
    "Change fields of an existing transaction by id (get ids from list_transactions or get_budget_overview). Only the fields you pass change. " +
    "Changing `date` does NOT move it to another budget month; pass year/month to move it (the target month is created if needed). Pass date: null or note: null to clear them.",
  inputSchema: {
    id: idS,
    amount: moneyS.optional(),
    category: categoryS.optional(),
    item: z.string().min(1).max(200).optional(),
    date: dateS.nullable().optional(),
    note: z.string().max(1000).nullable().optional(),
    year: yearS.optional().describe("Move to this budget year."),
    month: monthS.optional().describe("Move to this budget month 1-12."),
  },
  annotations: WRITE({ idempotent: true }),
  handler: async (
    args: { id: string; amount?: number; category?: string; item?: string; date?: string | null; note?: string | null; year?: number; month?: number },
    ctx,
  ) => {
    const old = await ctx.store.getTransaction(args.id);
    if (!old) throw new ToolError(`No transaction with id ${args.id}. Use list_transactions to find the id.`);
    const { id: _id, ...fields } = args;
    if (Object.values(fields).every((v) => v === undefined)) throw new ToolError("Nothing to change: pass at least one field to update.");
    parseDateArg(args.date);
    const cats = await ctx.store.listCategories();
    const cat = args.category ? resolveCategory(args.category, cats) : cats.find((c) => c.id === old.category_id);
    const y = args.year ?? old.year;
    const m = args.month ?? old.month;
    const created = y !== old.year || m !== old.month ? await ensureMonth(ctx, y, m) : null;
    const next: Transaction = {
      ...old,
      amount: args.amount ?? old.amount,
      category_id: cat?.id ?? old.category_id,
      item: args.item?.trim() ?? old.item,
      date: args.date === undefined ? old.date : args.date,
      note: args.note === undefined ? old.note ?? null : args.note,
      year: y,
      month: m,
    };
    const [row] = await ctx.store.upsertTransactions([next]);
    const names = new Map(cats.map((c) => [c.id, c.name]));
    const before = txOut(old, names), after = txOut(row, names);
    const v = monthView(await monthSnapshot(ctx, y, m), y, m);
    const lines = [
      ...(created ? [created] : []),
      `Updated transaction ${after.id}.`,
      `Before: ${txLine(before, true)}`,
      `After:  ${txLine(after, true)}`,
      categorySentence(v, next.category_id),
    ].filter(Boolean);
    return { text: lines.join("\n"), data: { before, after, category: categoryStatus(v, next.category_id), month_created_message: created } };
  },
});

// 6. delete_transaction ----------------------------------------------------
tool({
  name: "delete_transaction",
  title: "Delete a transaction",
  description: "Delete a transaction by id (get ids from list_transactions). The row is tombstoned (deleted = true) so both apps remove it on their next sync.",
  inputSchema: { id: idS },
  annotations: WRITE({ destructive: true, idempotent: true }),
  handler: async (args: { id: string }, ctx) => {
    const old = await ctx.store.getTransaction(args.id);
    if (!old) throw new ToolError(`No transaction with id ${args.id} (it may already be deleted). Use list_transactions to find the id.`);
    await ctx.store.upsertTransactions([{ ...old, deleted: true }]);
    const cats = await ctx.store.listCategories();
    const tx = txOut(old, new Map(cats.map((c) => [c.id, c.name])));
    const v = monthView(await monthSnapshot(ctx, old.year, old.month), old.year, old.month);
    return {
      text: [`Deleted: ${txLine(tx, true)}`, categorySentence(v, old.category_id)].filter(Boolean).join("\n"),
      data: { deleted: tx, category: categoryStatus(v, old.category_id) },
    };
  },
});

// 7/8. set_budget / set_actual --------------------------------------------
async function setBudgetField(
  ctx: ToolContext,
  args: { category: string; year?: number; month?: number },
  field: "expected" | "actual",
  value: number | null,
): Promise<ToolOutput> {
  const t = today(ctx);
  const y = args.year ?? t.year, m = args.month ?? t.month;
  const cat = resolveCategory(args.category, await ctx.store.listCategories());
  const created = await ensureMonth(ctx, y, m);
  const [old] = await ctx.store.listBudgets({ year: y, month: m, categoryIds: [cat.id] });
  const before = old?.[field] ?? null;
  const row: Budget = { year: y, month: m, category_id: cat.id, expected: old?.expected ?? null, actual: old?.actual ?? null, [field]: value };
  await ctx.store.upsertBudgets([row]);
  // Linked accounts (401k, HSA) are base + multiplier × Σ budget.actual, so net worth moved.
  const snapshotRefreshed = field === "actual" && (await linkedToAccount(ctx, cat.id)) ? await refreshSnapshotSafely(ctx) : undefined;
  const v = monthView(await monthSnapshot(ctx, y, m), y, m);
  const status = categoryStatus(v, cat.id);
  const lines = [...(created ? [created] : [])];
  if (field === "expected") {
    lines.push(`Set ${cat.name} expected for ${label(y, m)} to ${formatMoney(value)} (was ${formatMoney(before)}).`);
  } else if (value === null) {
    lines.push(
      cat.tracking === "ledger"
        ? `Cleared the ${cat.name} override for ${label(y, m)}; its actual is the transaction sum again (${formatMoney(status?.actual ?? 0)}).`
        : `Cleared the ${cat.name} actual for ${label(y, m)} (was ${formatMoney(before)}).`,
    );
  } else {
    lines.push(
      `Set ${cat.name} actual for ${label(y, m)} to ${formatMoney(value)} (was ${formatMoney(before)}).` +
        (cat.tracking === "ledger"
          ? ` ${cat.name} is ledger-tracked, so this is a manual override of the transaction sum (${formatMoney(status?.ledger_sum ?? 0)}); set actual to null to go back to the sum.`
          : ""),
    );
  }
  lines.push(categorySentence(v, cat.id));
  if (v.closed) lines.push(`Note: ${label(y, m)} is closed, so the year summary reflects this change.`);
  return {
    text: lines.join("\n"),
    data: {
      year: y,
      month: m,
      category: status,
      previous: before,
      month_created_message: created,
      totals: { expected: v.expected, actual: v.actual },
      ...(snapshotRefreshed !== undefined ? { net_worth_snapshot_refreshed: snapshotRefreshed } : {}),
    },
  };
}

tool({
  name: "set_budget",
  title: "Set a category's expected budget",
  description:
    'Set the expected (budgeted) amount of a category for a month, e.g. "budget 500 for food in November". Defaults to the current month; the month is created if needed. expected: null clears it.',
  inputSchema: {
    category: categoryS,
    expected: moneyS.nullable().describe("Budgeted amount in dollars (null clears)."),
    year: yearArg,
    month: monthArg,
  },
  annotations: WRITE({ idempotent: true }),
  handler: (args: { category: string; expected: number | null; year?: number; month?: number }, ctx) =>
    setBudgetField(ctx, args, "expected", args.expected),
});

tool({
  name: "set_actual",
  title: "Set a category's actual amount",
  description:
    'Type in the actual amount for a category and month, e.g. "rent was 1193 this month" → {category: "rent", actual: 1193}. This is how manual categories (Rent, Utilities, Car Ins, Phone Bill, Roth, 401k, Brokerage, HSA) get their actual. ' +
    "For ledger categories (Paychecks, Food, Fun, ...) it sets a manual override of the transaction sum. actual: null clears it (ledger categories return to the transaction sum). Defaults to the current month; the month is created if needed.",
  inputSchema: {
    category: categoryS,
    actual: moneyS.nullable().describe("Actual amount in dollars, or null to clear."),
    year: yearArg,
    month: monthArg,
  },
  annotations: WRITE({ idempotent: true }),
  handler: (args: { category: string; actual: number | null; year?: number; month?: number }, ctx) =>
    setBudgetField(ctx, args, "actual", args.actual),
});

// 9. set_month_closed ------------------------------------------------------
tool({
  name: "set_month_closed",
  title: "Close or reopen a month",
  description:
    'Mark a month closed (done) or open, e.g. "close September". Closed months contribute their actuals to the year summary (falling back to the budget for lines with no actual); open months contribute their budget. ' +
    "If year is omitted, the most recent existing month with that number is used.",
  inputSchema: {
    year: yearS.optional().describe("Year; defaults to the most recent existing month with this number."),
    month: monthS.describe("Month 1-12."),
    closed: z.boolean().describe("true = close, false = reopen."),
  },
  annotations: WRITE({ idempotent: true }),
  handler: async (args: { year?: number; month: number; closed: boolean }, ctx) => {
    const t = today(ctx);
    const months = (await ctx.store.listMonths()).sort(compareYM);
    let mo = args.year !== undefined
      ? months.find((x) => x.year === args.year && x.month === args.month)
      : [...months].reverse().find((x) => x.month === args.month && compareYM(x, t) <= 0) ??
        [...months].reverse().find((x) => x.month === args.month);
    if (!mo) {
      throw new ToolError(
        `${label(args.year ?? t.year, args.month)} does not exist. Existing months: ${months.map((x) => label(x.year, x.month)).join(", ") || "none"}.`,
      );
    }
    const y = mo.year;
    const before = yearView(await yearSnapshot(ctx, y), y);
    const was = mo.closed;
    mo = { ...mo, closed: args.closed };
    await ctx.store.upsertMonths([mo]);
    const after = yearView(await yearSnapshot(ctx, y), y);
    const v = monthView(await monthSnapshot(ctx, y, mo.month), y, mo.month);
    const verb = args.closed ? "Closed" : "Reopened";
    const lines = [
      was === args.closed
        ? `${label(y, mo.month)} was already ${args.closed ? "closed" : "open"}.`
        : `${verb} ${label(y, mo.month)}. The ${y} summary now uses its ${args.closed ? "actuals" : "budget"}.`,
      `${label(y, mo.month)} leftover: ${formatMoney(v.actual.leftover)} actual vs ${formatMoney(v.expected.leftover)} expected.`,
    ];
    if (before && after) {
      lines.push(
        `${y} projected leftover ${formatMoney(before.actual.leftover)} → ${formatMoney(after.actual.leftover)}; annualized savings ${formatMoney(before.actual.annualized_savings)} → ${formatMoney(after.actual.annualized_savings)} (${formatPct(after.actual.pct_net)} of net).`,
      );
    }
    return {
      text: lines.join("\n"),
      data: {
        year: y,
        month: mo.month,
        closed: args.closed,
        changed: was !== args.closed,
        month_totals: { expected: v.expected, actual: v.actual },
        year_projected_before: before?.actual ?? null,
        year_projected_after: after?.actual ?? null,
      },
    };
  },
});

// 10. create_month ---------------------------------------------------------
tool({
  name: "create_month",
  title: "Start a new month",
  description:
    "Create a budget month: copies each active category's expected amount from the most recent earlier month and adds the recurring items (subscriptions) as transactions. Does nothing if the month already exists.",
  inputSchema: { year: yearS.describe("Year, e.g. 2026."), month: monthS.describe("Month 1-12.") },
  annotations: WRITE({ idempotent: true }),
  handler: async (args: { year: number; month: number }, ctx) => {
    const created = await ensureMonth(ctx, args.year, args.month);
    if (!created) {
      return { text: `${label(args.year, args.month)} already exists; nothing changed.`, data: { year: args.year, month: args.month, created: false } };
    }
    const v = monthView(await monthSnapshot(ctx, args.year, args.month), args.year, args.month);
    return {
      text: [created.replace(" did not exist, so it was created:", " created:"), ...totalsLines(v)].join("\n"),
      data: { year: args.year, month: args.month, created: true, message: created, totals: { expected: v.expected, actual: v.actual } },
    };
  },
});

// 11. get_year_summary -----------------------------------------------------
tool({
  name: "get_year_summary",
  title: "Year summary",
  description:
    "Year totals per category: expected vs projected actual (closed months use actuals, open months their budget) and difference; totals for income, expenses, saved (incl. 401k match) and leftover; annualized savings and its % of net and gross income; plus each month's status and leftover. Defaults to the current year (or the latest year with months).",
  inputSchema: { year: yearS.optional().describe("Year, e.g. 2026.") },
  annotations: READ,
  handler: async (args: { year?: number }, ctx) => {
    const t = today(ctx);
    const months = (await ctx.store.listMonths()).sort(compareYM);
    let y = args.year ?? t.year;
    if (args.year === undefined && !months.some((m) => m.year === y) && months.length) y = months[months.length - 1].year;
    const v = yearView(await yearSnapshot(ctx, y), y);
    if (!v) {
      const years = [...new Set(months.map((m) => m.year))];
      return { text: `No months exist in ${y}. Years with data: ${years.join(", ") || "none"}.`, data: { year: y, month_count: 0, years } };
    }
    const lines = [`${y} summary — ${v.month_count} month(s): ${v.months.map((m) => `${m.name.slice(0, 3)} ${m.closed ? "closed" : "open"}`).join(", ")}`];
    for (const g of GROUPS) {
      const rows = v.categories.filter((c) => c.kind === g.kind);
      if (!rows.length) continue;
      lines.push(`${g.title}:`);
      for (const r of rows) lines.push(`  ${r.name}: ${formatMoney(r.actual)} projected vs ${formatMoney(r.expected)} expected (diff ${formatMoney(r.difference)})`);
    }
    const col = (name: string, c: typeof v.expected) =>
      `${name}: income ${formatMoney(c.income)}, expenses ${formatMoney(c.expenses)}, saved ${formatMoney(c.saved)}, leftover ${formatMoney(c.leftover)}; annualized savings ${formatMoney(c.annualized_savings)} = ${formatPct(c.pct_net)} of net, ${formatPct(c.pct_gross)} of gross`;
    lines.push(col("Expected", v.expected), col("Projected actual", v.actual));
    lines.push("Months: " + v.months.map((m) => `${m.name.slice(0, 3)} leftover ${formatMoney(m.leftover_actual)}`).join(" · "));
    const strip = ({ categories: _c, ...rest }: typeof v.expected) => rest;
    return {
      text: lines.join("\n"),
      data: {
        year: y,
        month_count: v.month_count,
        months: v.months,
        categories: v.categories.map(({ category_id: _id, ...r }) => r),
        totals: { expected: strip(v.expected), actual: strip(v.actual) },
      },
    };
  },
});

// 12. get_net_worth --------------------------------------------------------
tool({
  name: "get_net_worth",
  title: "Net worth",
  description:
    "Net worth: every account balance (linked accounts such as 401k/HSA are computed as base + multiplier × all contributions entered for their category), net worth, super liquid assets, net reconciliations, and the unsettled ledger (IOU) entries.",
  inputSchema: {},
  annotations: READ,
  handler: async (_args, ctx) => {
    const [settings, categories, accounts, ledger_entries] = await Promise.all([
      ctx.store.getSettings(),
      ctx.store.listCategories(),
      ctx.store.listAccounts(),
      ctx.store.listLedgerEntries(),
    ]);
    const linked = [...new Set(accounts.map((a) => a.linked_category_id).filter((x): x is string => !!x))];
    const budgets = linked.length ? await ctx.store.listBudgets({ categoryIds: linked }) : [];
    const v = netWorthView({ settings, categories, months: [], budgets, transactions: [], accounts, ledger_entries });
    const snaps = await ctx.store.listNetWorthSnapshots();
    const t = today(ctx);
    const hist = snaps.length >= 2 ? historyView(snaps, "1M") : null;
    const change30 = hist?.change ? changeOut(hist.change, "1M", t.year) : null;
    const lines = [
      `Net worth: ${formatMoney(v.net_worth)} · Super liquid: ${formatMoney(v.super_liquid)} · Net reconciliations: ${formatMoney(v.net_reconciliations)}`,
      ...(change30 ? [`30-day change (daily snapshots): ${change30.label}. See get_net_worth_history for the trend.`] : []),
      "Accounts:",
      ...v.accounts.map((a) =>
        `  ${a.name} (${a.account_group}${a.liquid ? ", liquid" : ""}): ${formatMoney(a.balance)}${a.linked ? ` — auto: base ${formatMoney(a.base_amount)} + ${a.linked_category} contributions` : ""}`
      ),
      "Unsettled ledger:",
      ...(v.unsettled.length
        ? v.unsettled.map((e) => `  ${e.name}: ${formatMoney(e.amount)} (${e.amount >= 0 ? "owed to me" : "I owe"})${e.note ? ` — ${e.note}` : ""}  id:${e.id}`)
        : ["  (none)"]),
    ];
    return {
      text: lines.join("\n"),
      data: {
        net_worth: v.net_worth,
        super_liquid: v.super_liquid,
        net_reconciliations: v.net_reconciliations,
        accounts: v.accounts.map(({ id: _id, ...a }) => a),
        unsettled_ledger: v.unsettled.map((e) => ({ id: e.id, name: e.name, amount: Number(e.amount), note: e.note ?? null })),
        change_30d: change30,
      },
    };
  },
});

// 12b. get_net_worth_history ---------------------------------------------
tool({
  name: "get_net_worth_history",
  title: "Net worth history",
  description:
    "Net worth over time from the daily snapshots (one per America/New_York day: taken nightly and whenever balances, IOUs or linked contributions change). " +
    'Returns the series, the change over the range (latest vs the snapshot on or before latest − range; "since <date>" when history is shorter), and the low and high. ' +
    'range: 1M = 30 days, 3M = 91 (default), 6M = 182, 1Y = 365, All. Pass `account` for one account\'s balance instead of total net worth. E.g. "how has my net worth changed this year?" → {range: "1Y"}.',
  inputSchema: {
    range: z.enum(["1M", "3M", "6M", "1Y", "All"]).default("3M").describe("1M, 3M (default), 6M, 1Y or All."),
    account: z.string().min(1).optional().describe('Optional account name (case-insensitive, fuzzy), e.g. "checking" or "401k".'),
  },
  annotations: READ,
  handler: async (args: { range?: HistoryRange; account?: string }, ctx) => {
    const range: HistoryRange = args.range ?? "3M";
    const t = today(ctx);
    const [snaps, accounts] = await Promise.all([ctx.store.listNetWorthSnapshots(), args.account ? ctx.store.listAccounts() : Promise.resolve([])]);
    let acct: { id: string; name: string } | null = null;
    if (args.account) {
      const sorted = [...accounts].sort((a, b) => a.sort_order - b.sort_order);
      const res = matchByName(args.account, sorted);
      if (!res.ok) explainMatch("account", args.account, res, sorted.filter((a) => !a.archived));
      acct = res.item;
    }
    const subject = acct ? acct.name : "Net worth";
    const value = acct ? (s: NetWorthSnapshot) => accountBalanceIn(s, acct!.id) : (s: NetWorthSnapshot) => s.net_worth;
    const withValue = snaps.filter((s) => value(s) !== null);
    if (withValue.length < 2) {
      const only = withValue[0];
      const startsToday = !only || only.taken_on >= t.iso;
      const text = !only
        ? `No ${acct ? `${acct.name} ` : "net worth "}history yet: history starts today. A snapshot is taken every night and whenever balances change, so a trend appears from tomorrow.`
        : startsToday
        ? `${subject} ${formatMoney(value(only))}. History starts today (first snapshot), so there is no change to show yet; check back tomorrow.`
        : `${subject} ${formatMoney(value(only))} as of ${formatShortDate(only.taken_on, t.year)}, the only snapshot so far. History started then; a trend appears once there are two daily snapshots.`;
      return {
        text,
        data: {
          range,
          account: acct?.name ?? null,
          snapshot_count: withValue.length,
          history_starts: only?.taken_on ?? t.iso,
          latest: only ? { date: only.taken_on, value: value(only) } : null,
          change: null,
          low: null,
          high: null,
          series: only ? [acct ? { date: only.taken_on, balance: value(only) } : { date: only.taken_on, net_worth: only.net_worth, super_liquid: only.super_liquid }] : [],
        },
      };
    }
    const hv = historyView(withValue, range, value);
    const c = hv.change!;
    const inRange = withValue.filter((s) => s.taken_on >= c.base.date).sort((a, b) => (a.taken_on < b.taken_on ? -1 : 1));
    const series = inRange.map((s) =>
      acct ? { date: s.taken_on, balance: value(s) } : { date: s.taken_on, net_worth: Number(s.net_worth), super_liquid: Number(s.super_liquid) }
    );
    const asOf = c.latest.date < t.iso ? ` (as of ${formatShortDate(c.latest.date, t.year)})` : "";
    const text = `${subject} ${formatMoney(c.latest.value)}${asOf}, ${describeChange(c, range, t.year)}; ` +
      `low ${formatMoney(hv.low!.value)} on ${formatShortDate(hv.low!.date, t.year)}, high ${formatMoney(hv.high!.value)} on ${formatShortDate(hv.high!.date, t.year)}. ` +
      `${series.length} snapshot(s) from ${formatShortDate(series[0].date, t.year)} to ${formatShortDate(c.latest.date, t.year)}.`;
    return {
      text,
      data: {
        range,
        account: acct?.name ?? null,
        snapshot_count: withValue.length,
        latest: c.latest,
        change: changeOut(c, range, t.year),
        low: hv.low,
        high: hv.high,
        series,
      },
    };
  },
});

// 13. update_account_balance -----------------------------------------------
tool({
  name: "update_account_balance",
  title: "Update an account balance",
  description:
    'Set an account\'s current balance for net worth, e.g. "checking is 2,340" or "credit card balance is -512.40" (debts are negative). ' +
    "Linked accounts (e.g. 401k, HSA) are computed from contributions and cannot be set directly; pass base_amount instead to change their starting value.",
  inputSchema: {
    account: z.string().min(1).describe('Account name, case-insensitive with prefix/fuzzy matching (e.g. "checking", "401k").'),
    balance: moneyS.optional().describe("New balance (negative for debt)."),
    base_amount: moneyS.optional().describe("Linked accounts only: the base the contributions are added to."),
  },
  annotations: WRITE({ idempotent: true }),
  handler: async (args: { account: string; balance?: number; base_amount?: number }, ctx) => {
    if (args.balance === undefined && args.base_amount === undefined) throw new ToolError("Pass balance (or base_amount for a linked account).");
    const accounts = (await ctx.store.listAccounts()).sort((a, b) => a.sort_order - b.sort_order);
    const res = matchByName(args.account, accounts);
    if (!res.ok) explainMatch("account", args.account, res, accounts.filter((a) => !a.archived));
    const acct = res.item;
    const cats = await ctx.store.listCategories();
    const catName = cats.find((c) => c.id === acct.linked_category_id)?.name ?? "its category";
    if (acct.linked_category_id && args.balance !== undefined) {
      throw new ToolError(
        `${acct.name} is a linked account: its balance is computed as base ${formatMoney(acct.base_amount)} + contributions entered for ${catName} (set_actual). ` +
          `It cannot be set directly. To adjust it, pass base_amount (e.g. base_amount = desired balance − contributions so far; see get_net_worth).`,
      );
    }
    if (!acct.linked_category_id && args.base_amount !== undefined) {
      throw new ToolError(`${acct.name} is not a linked account; base_amount only applies to linked accounts. Pass balance instead.`);
    }
    const next = { ...acct, ...(args.balance !== undefined ? { balance: args.balance } : {}), ...(args.base_amount !== undefined ? { base_amount: args.base_amount } : {}) };
    await ctx.store.upsertAccounts([next]);
    const snapshotRefreshed = await refreshSnapshotSafely(ctx);
    const [settings, categories, accounts2, ledger_entries] = await Promise.all([
      ctx.store.getSettings(),
      Promise.resolve(cats),
      ctx.store.listAccounts(),
      ctx.store.listLedgerEntries(),
    ]);
    const linked = [...new Set(accounts2.map((a) => a.linked_category_id).filter((x): x is string => !!x))];
    const budgets = linked.length ? await ctx.store.listBudgets({ categoryIds: linked }) : [];
    const v = netWorthView({ settings, categories, months: [], budgets, transactions: [], accounts: accounts2, ledger_entries });
    const bal = v.accounts.find((a) => a.id === acct.id)?.balance ?? next.balance;
    const what = args.balance !== undefined
      ? `Set ${acct.name} balance to ${formatMoney(args.balance)} (was ${formatMoney(acct.balance)}).`
      : `Set ${acct.name} base amount to ${formatMoney(args.base_amount)} (was ${formatMoney(acct.base_amount)}); balance is now ${formatMoney(bal)}.`;
    return {
      text: `${what}${acct.archived ? " (This account is archived and does not count toward net worth.)" : ""}\nNet worth is now ${formatMoney(v.net_worth)} · Super liquid ${formatMoney(v.super_liquid)}.`,
      data: {
        account: acct.name,
        balance: bal,
        base_amount: next.base_amount,
        net_worth: v.net_worth,
        super_liquid: v.super_liquid,
        net_worth_snapshot_refreshed: snapshotRefreshed,
      },
    };
  },
});

// 14. add_ledger_entry / settle_ledger_entry -------------------------------
tool({
  name: "add_ledger_entry",
  title: "Add an IOU (ledger entry)",
  description:
    'Add a reconciliation / IOU to the ledger. Positive amount = someone owes me; negative = I owe. E.g. "Sam owes me 40 for tickets" → {name: "Sam", amount: 40, note: "tickets"}. Unsettled entries count toward net worth.',
  inputSchema: {
    name: z.string().min(1).max(200).describe("Who / what, e.g. \"Sam\" or \"Citi\"."),
    amount: moneyS.describe("Positive = owed to me, negative = I owe."),
    note: z.string().max(1000).optional(),
  },
  annotations: WRITE(),
  handler: async (args: { name: string; amount: number; note?: string }, ctx) => {
    const entries = await ctx.store.listLedgerEntries();
    const sort = entries.reduce((mx, e) => Math.max(mx, e.sort_order ?? 0), -1) + 1;
    const [row] = await ctx.store.upsertLedgerEntries([
      { id: ctx.newId(), name: args.name.trim(), amount: args.amount, note: args.note ?? null, settled: false, sort_order: sort },
    ]);
    const recon = r4([...entries, row].filter((e) => !e.settled).reduce((a, e) => a + Number(e.amount), 0));
    const snapshotRefreshed = await refreshSnapshotSafely(ctx);
    return {
      text: `Added ledger entry ${row.name}: ${formatMoney(row.amount)} (${row.amount >= 0 ? "owed to me" : "I owe"}). id:${row.id}\nNet reconciliations now ${formatMoney(recon)}.`,
      data: {
        entry: { id: row.id, name: row.name, amount: Number(row.amount), note: row.note ?? null, settled: false },
        net_reconciliations: recon,
        net_worth_snapshot_refreshed: snapshotRefreshed,
      },
    };
  },
});

tool({
  name: "settle_ledger_entry",
  title: "Settle (or unsettle) an IOU",
  description:
    'Mark a ledger entry settled (paid back), e.g. "Sam paid me back". Identify it by name (case-insensitive, fuzzy) or id. settled defaults to true; pass false to reopen it. Settled entries stop counting toward net worth.',
  inputSchema: {
    name: z.string().min(1).optional().describe("Ledger entry name."),
    id: ledgerIdS.optional().describe("Ledger entry id (from get_net_worth)."),
    settled: z.boolean().default(true),
  },
  annotations: WRITE({ idempotent: true }),
  handler: async (args: { name?: string; id?: string; settled: boolean }, ctx) => {
    const settled = args.settled ?? true;
    const entries = (await ctx.store.listLedgerEntries()).sort((a, b) => a.sort_order - b.sort_order);
    let entry;
    if (args.id) {
      entry = entries.find((e) => e.id === args.id);
      if (!entry) throw new ToolError(`No ledger entry with id ${args.id}. Use get_net_worth to list them.`);
    } else if (args.name) {
      const pool = entries.filter((e) => e.settled !== settled);
      const res = matchByName(args.name, pool);
      if (res.ok) entry = res.item;
      else {
        const any = matchByName(args.name, entries);
        if (any.ok) {
          return {
            text: `${any.item.name} is already ${settled ? "settled" : "unsettled"}; nothing changed.`,
            data: { entry: { id: any.item.id, name: any.item.name, amount: Number(any.item.amount), settled: any.item.settled }, changed: false },
          };
        }
        explainMatch(settled ? "unsettled ledger entry" : "settled ledger entry", args.name, res, pool);
      }
    } else {
      throw new ToolError("Pass the entry's name or id.");
    }
    if (entry.settled === settled) {
      return { text: `${entry.name} is already ${settled ? "settled" : "unsettled"}; nothing changed.`, data: { entry, changed: false } };
    }
    await ctx.store.upsertLedgerEntries([{ ...entry, settled }]);
    const snapshotRefreshed = await refreshSnapshotSafely(ctx);
    const after = await ctx.store.listLedgerEntries();
    const recon = r4(after.filter((e) => !e.settled).reduce((a, e) => a + Number(e.amount), 0));
    return {
      text: `${settled ? "Settled" : "Reopened"} ${entry.name} (${formatMoney(entry.amount)}). Net reconciliations now ${formatMoney(recon)}.`,
      data: {
        entry: { id: entry.id, name: entry.name, amount: Number(entry.amount), settled },
        changed: true,
        net_reconciliations: recon,
        net_worth_snapshot_refreshed: snapshotRefreshed,
      },
    };
  },
});

// 15. get_meal_plans -------------------------------------------------------
tool({
  name: "get_meal_plans",
  title: "Meal plans and recipes",
  description:
    "Meal plans (full days of eating) and recipes with their items and totals (calories, protein, fiber, fat, cost). Day plans also show monthly cost = daily cost × days per month (30.5 by default).",
  inputSchema: {},
  annotations: READ,
  handler: async (_args, ctx) => {
    const [settings, meal_plans, meal_items] = await Promise.all([
      ctx.store.getSettings(),
      ctx.store.listMealPlans(),
      ctx.store.listMealItems(),
    ]);
    const plans = mealPlansView({ settings, categories: [], months: [], budgets: [], transactions: [], meal_plans, meal_items });
    const lines: string[] = [];
    for (const p of plans) {
      const tt = p.totals;
      lines.push(
        `${p.name} (${p.kind}${p.label ? `, ${p.label}` : ""}): ${tt.calories} kcal, ${tt.protein} g protein, ${tt.fiber} g fiber, ${tt.fat} g fat, ${formatMoney(tt.cost)}${
          tt.monthly_cost !== undefined ? `/day → ${formatMoney(tt.monthly_cost)}/month` : ""
        }`,
      );
      for (const i of p.items) {
        lines.push(`  ${i.time_label ? i.time_label + " " : ""}${i.name}: ${i.calories ?? 0} kcal, ${i.protein ?? 0} g protein, ${formatMoney(i.cost ?? 0)}`);
      }
      if (p.kind === "recipe" && p.note) lines.push(`  Steps: ${p.note}`);
    }
    return {
      text: lines.join("\n") || "No meal plans yet.",
      data: {
        plans: plans.map((p) => ({
          name: p.name,
          kind: p.kind,
          label: p.label,
          note: p.note,
          totals: p.totals,
          items: p.items.map((i) => ({ time: i.time_label ?? null, name: i.name, calories: i.calories, protein: i.protein, fiber: i.fiber, fat: i.fat, cost: i.cost })),
        })),
      },
    };
  },
});

// ---------------------------------------------------------------------------
// Execution + MCP wiring
// ---------------------------------------------------------------------------

export function getTool(name: string): ToolDef {
  const def = TOOLS.find((t) => t.name === name);
  if (!def) throw new Error(`Unknown tool ${name}`);
  return def;
}

/** Validates raw arguments with the tool's schema and runs it, converting failures to isError results. */
export async function runTool(name: string, rawArgs: unknown, ctx: ToolContext): Promise<CallToolResult> {
  const def = getTool(name);
  const parsed = z.object(def.inputSchema).safeParse(rawArgs ?? {});
  if (!parsed.success) {
    const msg = parsed.error.issues.map((i) => `${i.path.join(".") || "arguments"}: ${i.message}`).join("; ");
    return { isError: true, content: [{ type: "text", text: `Invalid arguments for ${name}: ${msg}` }] };
  }
  return await execute(def, parsed.data, ctx);
}

async function execute(def: ToolDef, args: unknown, ctx: ToolContext): Promise<CallToolResult> {
  try {
    const out = await def.handler(args, ctx);
    return { content: [{ type: "text", text: out.text }], structuredContent: out.data };
  } catch (e) {
    if (e instanceof ToolError) return { isError: true, content: [{ type: "text", text: e.message }] };
    // Unexpected (e.g. database) error: log the message only (never request data or tokens).
    console.error(`[budget-mcp] ${def.name} failed:`, e instanceof Error ? e.message : String(e));
    return {
      isError: true,
      content: [{ type: "text", text: `Something went wrong running ${def.name}: ${e instanceof Error ? e.message : String(e)}` }],
    };
  }
}

export function registerTools(server: McpServer, ctx: ToolContext): void {
  for (const def of TOOLS) {
    server.registerTool(
      def.name,
      { title: def.title, description: def.description, inputSchema: def.inputSchema, annotations: { title: def.title, ...def.annotations } },
      // deno-lint-ignore no-explicit-any
      ((args: any) => execute(def, args, ctx)) as any,
    );
  }
}

export const SERVER_INSTRUCTIONS = `Personal monthly budget (a port of the user's budget spreadsheet). Amounts are US dollars; "today" and "this month" are evaluated in ${TIMEZONE}.

Model:
- Each month (year + month) has categories of three kinds: income (Paychecks), expenses (Rent, Food, Fun, ...) and savings (Roth, 401k, Taxable Brokerage, HSA). Call list_categories for the exact names; tools accept names case-insensitively with prefix/fuzzy matching.
- Every category has an EXPECTED amount (the budget, set_budget) and an ACTUAL amount.
- Tracking "ledger" categories (Paychecks, Subscriptions, Food, Fun, Gas, Misc): actual = sum of their transactions (add_transaction). A typed actual (set_actual) overrides the sum; set_actual(null) clears the override.
- Tracking "manual" categories (Rent, Car Ins, Utilities, Phone Bill and all savings): actual is typed in with set_actual (e.g. "rent was 1193 this month"). Transactions do not affect them.
- Totals: leftover = income − expenses − savings contributions. "Saved" counts the 401k employer match (multiplier 2) but leftover does not.
- Closing a month (set_month_closed) means it is final: the year summary then uses its actuals; open months contribute their budget (projection).
- Months are created from the previous month's budgets plus recurring subscriptions (create_month); add_transaction / set_budget / set_actual create a missing month automatically and say so.
- Net worth: accounts (update_account_balance; 401k/HSA-style linked accounts are computed from contributions) plus unsettled IOUs in the ledger (add_ledger_entry, settle_ledger_entry; positive = owed to me). A snapshot is kept per day (nightly and after changes); get_net_worth_history shows the trend.

Tips: for "how much X do I have left" use get_budget_overview (remaining = expected − actual). To fix or remove a transaction, find its id with list_transactions first. Negative transaction amounts are refunds. Confirm what changed using the numbers the tools return.`;
