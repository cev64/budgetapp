// deno test --allow-read --allow-env supabase/functions/
// Tool handlers against the in-memory BudgetStore seeded from docs/fixtures/sample-backup.json.

import { assert, assertAlmostEquals, assertEquals, assertMatch, assertStringIncludes } from "jsr:@std/assert@1.0.19";
import { InMemoryStore } from "./memory_store.ts";
import { makeContext, runTool, TOOLS, type ToolContext } from "./tools.ts";

const fixtureUrl = new URL("../../../docs/fixtures/sample-backup.json", import.meta.url);
const backup = JSON.parse(await Deno.readTextFile(fixtureUrl));
const expected = JSON.parse(await Deno.readTextFile(new URL("expected.json", fixtureUrl)));

// "Today" = Thursday 15 January 2026, noon in New York.
const NOW = new Date("2026-01-15T17:00:00Z");

function setup(now = NOW): { store: InMemoryStore; ctx: ToolContext } {
  const store = new InMemoryStore(backup, { now: () => now });
  let n = 0;
  const ctx = makeContext(store, {
    now: () => now,
    newId: () => `00000000-0000-4000-8000-${String(++n).padStart(12, "0")}`,
  });
  return { store, ctx };
}

// deno-lint-ignore no-explicit-any
type Any = any;

async function call(ctx: ToolContext, name: string, args: Record<string, unknown> = {}) {
  const res = await runTool(name, args, ctx);
  const text = (res.content as { text: string }[]).map((c) => c.text).join("\n");
  return { res, text, data: res.structuredContent as Any, isError: !!res.isError };
}

async function ok(ctx: ToolContext, name: string, args: Record<string, unknown> = {}) {
  const r = await call(ctx, name, args);
  assert(!r.isError, `${name} failed: ${r.text}`);
  return r;
}

const cat = (data: Any, group: string, name: string) => data.groups[group].find((c: Any) => c.name === name);

Deno.test("tool list: 16 tools, all with descriptions and annotations", () => {
  const names = TOOLS.map((t) => t.name);
  assertEquals(names, [
    "get_budget_overview", "list_categories", "add_transaction", "list_transactions", "update_transaction",
    "delete_transaction", "set_budget", "set_actual", "set_month_closed", "create_month", "get_year_summary",
    "get_net_worth", "get_net_worth_history", "update_account_balance", "add_ledger_entry", "settle_ledger_entry",
  ]);
  for (const t of TOOLS) {
    assert(t.description.length > 60, t.name);
    assertEquals(typeof t.annotations.readOnlyHint, "boolean");
  }
  assertEquals(TOOLS.find((t) => t.name === "delete_transaction")!.annotations.destructiveHint, true);
  for (const n of ["get_budget_overview", "list_categories", "list_transactions", "get_year_summary", "get_net_worth", "get_net_worth_history"]) {
    assertEquals(TOOLS.find((t) => t.name === n)!.annotations.readOnlyHint, true, n);
  }
});

Deno.test("get_budget_overview defaults to the current month and matches expected.json", async () => {
  const { ctx } = setup();
  const { data, text } = await ok(ctx, "get_budget_overview");
  assertEquals([data.year, data.month, data.name, data.closed], [2026, 1, "January 2026", false]);
  for (const col of ["expected", "actual"]) {
    for (const [k, v] of Object.entries(expected.months["2026-01"][col])) assertAlmostEquals(data.totals[col][k], v as number, 1e-6);
  }
  assertEquals(cat(data, "expenses", "Food").remaining, 330);
  assertEquals(cat(data, "income", "Paychecks").actual, 2100);
  assertEquals(cat(data, "savings", "Roth").actual, null);
  assertEquals(data.recent_transactions.length, 2);
  assertStringIncludes(text, "January 2026 (open)");
  assertStringIncludes(text, "Food: $120 of $450 expected · $330 left");
});

Deno.test("get_budget_overview falls back to the latest month when the current one is missing", async () => {
  const { ctx } = setup(new Date("2026-03-10T15:00:00Z"));
  const { data, text } = await ok(ctx, "get_budget_overview");
  assertEquals([data.year, data.month], [2026, 1]);
  assertStringIncludes(text, "March 2026 has not been created yet");
  const missing = await ok(ctx, "get_budget_overview", { year: 2026, month: 5 });
  assertEquals(missing.data.exists, false);
});

Deno.test("get_budget_overview for a past month uses the override and shows the closed flag", async () => {
  const { ctx } = setup();
  const { data } = await ok(ctx, "get_budget_overview", { year: 2025, month: 11 });
  assertEquals(data.closed, true);
  const pay = cat(data, "income", "Paychecks");
  assertEquals([pay.actual, pay.override, pay.ledger_sum], [4100, true, 4050]);
  assertEquals(cat(data, "expenses", "Fun").actual, 150);
});

Deno.test("add_transaction: current month, date defaults to today, category actual updates", async () => {
  const { ctx, store } = setup();
  const { data, text } = await ok(ctx, "add_transaction", { amount: 14, category: "food", item: "Chipotle" });
  assertEquals(data.transaction.category, "Food");
  assertEquals(data.transaction.date, "2026-01-15");
  assertEquals([data.transaction.year, data.transaction.month], [2026, 1]);
  assertEquals(data.category.actual, 134);
  assertEquals(data.category.expected, 450);
  assertEquals(data.category.remaining, 316);
  assertEquals(data.month_created, false);
  assertEquals(data.warnings, []);
  assertStringIncludes(text, "Added Chipotle $14 to Food");
  const row = store.transactions.find((t) => t.id === data.transaction.id)!;
  assertEquals(row.deleted, false);
  assert(!("updated_at" in row));
});

Deno.test("add_transaction: auto-creates a missing month per §6 and says so", async () => {
  const { ctx, store } = setup();
  const { data, text } = await ok(ctx, "add_transaction", { amount: 23.5, category: "fun", item: "Movie", date: "2026-02-03" });
  assertEquals(data.month_created, true);
  assertStringIncludes(text, "February 2026 did not exist, so it was created");
  assertStringIncludes(text, "Streaming $15.99 (Fun)");
  assert(store.months.some((m) => m.year === 2026 && m.month === 2 && !m.closed));
  const feb = store.budgets.filter((b) => b.year === 2026 && b.month === 2);
  assertEquals(feb.length, 8);
  assertEquals(feb.find((b) => b.category_id === backup.categories[0].id)!.expected, 4200);
  assertEquals(data.category.actual, 39.49); // 15.99 recurring + 23.50
  assertEquals(data.category.expected, 350);
  const streaming = store.transactions.find((t) => t.year === 2026 && t.month === 2 && t.item === "Streaming")!;
  assertEquals(streaming.date, "2026-02-28");
  // A second add into February does not re-create it.
  const again = await ok(ctx, "add_transaction", { amount: 5, category: "fun", item: "Popcorn", year: 2026, month: 2 });
  assertEquals(again.data.month_created, false);
  assertEquals(again.data.transaction.date, null); // not the current month → no default date
  assertEquals(store.transactions.filter((t) => t.item === "Streaming" && t.month === 2).length, 1);
});

Deno.test("add_transaction: manual category is allowed with a warning; refunds are negative", async () => {
  const { ctx } = setup();
  const r = await ok(ctx, "add_transaction", { amount: 1193, category: "rent", item: "Rent" });
  assertEquals(r.data.warnings.length, 1);
  assertStringIncludes(r.text, "use set_actual instead");
  assertEquals(r.data.category.actual, 1250); // unchanged: manual actual
  const refund = await ok(ctx, "add_transaction", { amount: -20, category: "Food", item: "Refund" });
  assertEquals(refund.data.category.actual, 100);
  const bad = await call(ctx, "add_transaction", { amount: 5, category: "food", item: "x", date: "2026-02-30" });
  assert(bad.isError);
  assertStringIncludes(bad.text, "not a valid date");
});

Deno.test("add_transaction: override active on a ledger category is called out", async () => {
  const { ctx } = setup();
  const r = await ok(ctx, "add_transaction", { amount: 10, category: "paychecks", item: "bonus", year: 2025, month: 11 });
  assertEquals(r.data.category.actual, 4100);
  assertStringIncludes(r.text, "manual override");
  assertStringIncludes(r.text, "November 2025 is closed");
});

Deno.test("name errors: ambiguous and unknown categories list valid names", async () => {
  const { ctx, store } = setup();
  const amb = await call(ctx, "add_transaction", { amount: 3, category: "f", item: "x" });
  assert(amb.isError);
  assertStringIncludes(amb.text, 'matches more than one category: "Food", "Fun"');
  const none = await call(ctx, "set_budget", { category: "groceries", expected: 10 });
  assert(none.isError);
  assertStringIncludes(none.text, 'No category matches "groceries"');
  assertStringIncludes(none.text, '"Paychecks", "Rent", "Food", "Fun", "Utilities", "Roth", "401k", "HSA"');
  assertEquals(store.writes, 0, "no writes on errors");
  const fuzzy = await ok(ctx, "set_budget", { category: "utilites", expected: 100 });
  assertEquals(fuzzy.data.category.name, "Utilities");
  const acct = await call(ctx, "update_account_balance", { account: "savings account", balance: 1 });
  assert(acct.isError);
  assertStringIncludes(acct.text, "Valid account names");
  assertStringIncludes(acct.text, '"Checking"');
});

Deno.test("set_actual: manual value, ledger override and clearing it", async () => {
  const { ctx, store } = setup();
  const rent = await ok(ctx, "set_actual", { category: "rent", actual: 1193 });
  assertEquals(rent.data.category.actual, 1193);
  assertEquals(rent.data.previous, 1250);
  const bud = store.budgets.find((b) => b.year === 2026 && b.month === 1 && b.category_id === backup.categories[1].id)!;
  assertEquals([bud.expected, bud.actual], [1250, 1193]); // expected preserved
  const roth = await ok(ctx, "set_actual", { category: "roth", actual: 500 });
  assertEquals(roth.data.totals.actual.contributions, 500);
  const k = await ok(ctx, "set_actual", { category: "401k", actual: 350 });
  assertEquals(k.data.totals.actual.saved, 500 + 700); // employer match ×2
  const cleared = await ok(ctx, "set_actual", { category: "paychecks", actual: null, year: 2025, month: 11 });
  assertEquals(cleared.data.category.actual, 4050);
  assertEquals(cleared.data.category.override, false);
  assertStringIncludes(cleared.text, "transaction sum again");
  const over = await ok(ctx, "set_actual", { category: "food", actual: 200 });
  assertStringIncludes(over.text, "manual override of the transaction sum ($120)");
});

Deno.test("set_budget: sets expected (keeps actual) and auto-creates the month", async () => {
  const { ctx, store } = setup();
  const r = await ok(ctx, "set_budget", { category: "Food", expected: 500, year: 2026, month: 3 });
  assertStringIncludes(r.text, "March 2026 did not exist");
  assertEquals(r.data.category.expected, 500);
  const jan = await ok(ctx, "set_budget", { category: "rent", expected: 1300 });
  assertEquals([jan.data.category.expected, jan.data.category.actual], [1300, 1250]);
  assertEquals(store.months.length, 4);
});

Deno.test("set_month_closed: closing January changes the year summary", async () => {
  const { ctx } = setup();
  const before = await ok(ctx, "get_year_summary", { year: 2026 });
  assertEquals(before.data.totals.actual.leftover, 1205);
  const closed = await ok(ctx, "set_month_closed", { month: 1, closed: true });
  assertEquals([closed.data.year, closed.data.month, closed.data.changed], [2026, 1, true]);
  const after = await ok(ctx, "get_year_summary", { year: 2026 });
  // Projected: Paychecks 2100, Rent 1250, Food 120, Fun 0→350, Utilities ∅→95, Roth ∅→500, 401k ∅→350, HSA 0.
  assertEquals(after.data.totals.actual.income, 2100);
  assertEquals(after.data.totals.actual.expenses, 1815);
  assertEquals(after.data.totals.actual.saved, 1200);
  assertEquals(after.data.totals.actual.leftover, -565);
  assertEquals(after.data.totals.actual.annualized_savings, 7620);
  assertEquals(after.data.totals.expected.annualized_savings, 28860); // expected column unchanged
  assertStringIncludes(closed.text, "annualized savings $28,860 → $7,620");
  const again = await ok(ctx, "set_month_closed", { year: 2026, month: 1, closed: true });
  assertEquals(again.data.changed, false);
  const missing = await call(ctx, "set_month_closed", { year: 2026, month: 7, closed: true });
  assert(missing.isError);
  // Reopen → back to the budget projection.
  await ok(ctx, "set_month_closed", { year: 2026, month: 1, closed: false });
  assertEquals((await ok(ctx, "get_year_summary", {})).data.totals.actual.leftover, 1205);
});

Deno.test("get_year_summary matches expected.json for 2025", async () => {
  const { ctx } = setup();
  const { data } = await ok(ctx, "get_year_summary", { year: 2025 });
  for (const col of ["expected", "actual"]) {
    for (const [k, v] of Object.entries(expected.years["2025"][col])) {
      if (k === "categories") continue;
      assertAlmostEquals(data.totals[col][k], v as number, 1e-6, `${col}.${k}`);
    }
  }
  const food = data.categories.find((c: Any) => c.name === "Food");
  assertEquals([food.expected, food.actual, food.difference], [800, 763.75, -36.25]);
  assertEquals(data.months.map((m: Any) => m.closed), [true, true]);
  const none = await ok(ctx, "get_year_summary", { year: 2030 });
  assertEquals(none.data.month_count, 0);
});

Deno.test("delete_transaction tombstones the row and updates the actual", async () => {
  const { ctx, store } = setup();
  const list = await ok(ctx, "list_transactions", { year: 2026, month: 1, category: "food" });
  assertEquals(list.data.count, 1);
  const id = list.data.transactions[0].id;
  const del = await ok(ctx, "delete_transaction", { id });
  assertEquals(del.data.category.actual, 0);
  const row = store.transactions.find((t) => t.id === id)!;
  assertEquals(row.deleted, true, "row is kept as a tombstone");
  assertEquals((await ok(ctx, "list_transactions", { year: 2026, month: 1, category: "food" })).data.count, 0);
  const again = await call(ctx, "delete_transaction", { id });
  assert(again.isError);
  const bad = await call(ctx, "delete_transaction", { id: "not-a-uuid" });
  assert(bad.isError);
  assertStringIncludes(bad.text, "Invalid arguments");
});

Deno.test("update_transaction: change fields, move month, keep created_at", async () => {
  const { ctx, store } = setup();
  const add = await ok(ctx, "add_transaction", { amount: 14, category: "food", item: "Chipotle" });
  const id = add.data.transaction.id;
  const created = store.transactions.find((t) => t.id === id)!.created_at;
  const up = await ok(ctx, "update_transaction", { id, amount: 16.25, category: "fun", note: "with friends" });
  assertEquals(up.data.after.category, "Fun");
  assertEquals(up.data.after.amount, 16.25);
  assertEquals(up.data.after.note, "with friends");
  assertEquals(up.data.category.actual, 16.25);
  assertEquals(store.transactions.find((t) => t.id === id)!.created_at, created);
  const moved = await ok(ctx, "update_transaction", { id, year: 2026, month: 2 });
  assertStringIncludes(moved.text, "February 2026 did not exist");
  assertEquals([moved.data.after.year, moved.data.after.month], [2026, 2]);
  const cleared = await ok(ctx, "update_transaction", { id, date: null, note: null });
  assertEquals([cleared.data.after.date, cleared.data.after.note], [null, null]);
  const nothing = await call(ctx, "update_transaction", { id });
  assert(nothing.isError);
  const missing = await call(ctx, "update_transaction", { id: "11111111-1111-4111-8111-111111111111", amount: 1 });
  assert(missing.isError);
  assertStringIncludes(missing.text, "No transaction with id");
});

Deno.test("list_transactions: sorting, search, limits, all months", async () => {
  const { ctx } = setup();
  const all = await ok(ctx, "list_transactions");
  assertEquals(all.data.count, 11); // 12 rows in the fixture, one tombstone excluded
  const keys = all.data.transactions.map((t: Any) => `${t.year}-${t.month}:${t.date ?? "-"}`);
  // Undated rows sort at the start of their budget month: Jan 2026 (undated) > 2025-12-31 > 2025-12-15 > ...
  assertEquals(keys.slice(0, 4), ["2026-1:-", "2026-1:-", "2025-12:2025-12-31", "2025-12:2025-12-15"]);
  const s = await ok(ctx, "list_transactions", { search: "GROC" });
  assertEquals(s.data.count, 3);
  assertEquals(s.data.total_amount, 803.75);
  const lim = await ok(ctx, "list_transactions", { limit: 2 });
  assertEquals(lim.data.transactions.length, 2);
  assertStringIncludes(lim.text, "(showing 2)");
  await ok(ctx, "add_transaction", { amount: 1, category: "food", item: "a" });
  await ok(ctx, "add_transaction", { amount: 2, category: "food", item: "b" });
  const jan = await ok(ctx, "list_transactions", { month: 1 });
  assertEquals(jan.data.transactions.slice(0, 2).map((t: Any) => t.item), ["b", "a"]); // same date → created_at desc
});

Deno.test("create_month: creates once, then no-op", async () => {
  const { ctx } = setup();
  const r = await ok(ctx, "create_month", { year: 2026, month: 2 });
  assertEquals(r.data.created, true);
  assertEquals(r.data.totals.expected.income, 4200);
  assertEquals(r.data.totals.actual.expenses, 15.99);
  const again = await ok(ctx, "create_month", { year: 2026, month: 2 });
  assertEquals(again.data.created, false);
  assertStringIncludes(again.text, "already exists");
});

Deno.test("net worth tools: matches expected.json; linked accounts refuse balance", async () => {
  const { ctx } = setup();
  const nw = await ok(ctx, "get_net_worth");
  assertEquals(nw.data.net_worth, expected.net_worth.net_worth);
  assertEquals(nw.data.super_liquid, expected.net_worth.super_liquid);
  assertEquals(nw.data.net_reconciliations, expected.net_worth.net_reconciliations);
  assertEquals(Object.fromEntries(nw.data.accounts.map((a: Any) => [a.name, a.balance])), expected.net_worth.accounts);

  const linked = await call(ctx, "update_account_balance", { account: "401k", balance: 5000 });
  assert(linked.isError);
  assertStringIncludes(linked.text, "linked account");
  assertStringIncludes(linked.text, "base_amount");
  const base = await ok(ctx, "update_account_balance", { account: "401k", base_amount: 1500 });
  assertEquals(base.data.balance, 2700);
  const chk = await ok(ctx, "update_account_balance", { account: "checking", balance: 3000 });
  assertEquals(chk.data.net_worth, expected.net_worth.net_worth + 500 + 500);
  const cc = await ok(ctx, "update_account_balance", { account: "credit", balance: -100 });
  assertEquals(cc.data.balance, -100);
  const notLinked = await call(ctx, "update_account_balance", { account: "checking", base_amount: 1 });
  assert(notLinked.isError);
});

Deno.test("ledger tools: add, settle by name, already settled, unsettle by id", async () => {
  const { ctx, store } = setup();
  const add = await ok(ctx, "add_ledger_entry", { name: "Sam", amount: 40, note: "tickets" });
  assertEquals(add.data.net_reconciliations, 114.5);
  assertEquals(store.ledger.find((e) => e.name === "Sam")!.sort_order, 3);
  const settle = await ok(ctx, "settle_ledger_entry", { name: "friend" });
  assertEquals(settle.data.entry.name, "Friend owes");
  assertEquals(settle.data.net_reconciliations, -5.5);
  const again = await ok(ctx, "settle_ledger_entry", { name: "friend owes" });
  assertEquals(again.data.changed, false);
  const reopen = await ok(ctx, "settle_ledger_entry", { id: settle.data.entry.id, settled: false });
  assertEquals(reopen.data.net_reconciliations, 114.5);
  const missing = await call(ctx, "settle_ledger_entry", { name: "nobody" });
  assert(missing.isError);
  assertMatch(missing.text, /No unsettled ledger entry matches/);
});

Deno.test("list_categories", async () => {
  const { ctx } = setup();
  const cats = await ok(ctx, "list_categories");
  assertEquals(cats.data.categories.find((c: Any) => c.name === "401k"), {
    name: "401k", kind: "savings", tracking: "manual", multiplier: 2, archived: false,
  });
});

// ---------------------------------------------------------------------------
// Net worth history (§5b)
// ---------------------------------------------------------------------------

const CHK = "167fc82d-3932-5e37-8500-a47624562490";
function seedHistory(store: InMemoryStore) {
  const s = (taken_on: string, net_worth: number, chk: number | null) => ({
    taken_on,
    net_worth,
    super_liquid: net_worth / 10,
    reconciliations: 0,
    accounts: chk === null ? [] : [{ id: CHK, name: "Checking", group: "cash", liquid: true, balance: chk }],
    source: "auto" as const,
  });
  store.snapshots.push(
    s("2025-10-01", 20000, 1000),
    s("2025-10-14", 20900, 1200),
    s("2025-11-20", 21500, null),
    s("2025-12-20", 22100, 2600),
    s("2026-01-10", 22800, 2400),
    s("2026-01-15", 22560, 2500),
  );
}

Deno.test("get_net_worth_history: fewer than 2 snapshots → history starts today", async () => {
  const { ctx, store } = setup();
  const none = await ok(ctx, "get_net_worth_history");
  assertStringIncludes(none.text, "history starts today");
  assertEquals([none.data.snapshot_count, none.data.change, none.data.history_starts], [0, null, "2026-01-15"]);
  await store.refreshNetWorthSnapshot();
  const one = await ok(ctx, "get_net_worth_history", { range: "1M" });
  assertStringIncludes(one.text, "History starts today");
  assertEquals(one.data.series, [{ date: "2026-01-15", net_worth: 14584.25, super_liquid: 1859.75 }]);
});

Deno.test("get_net_worth_history: series, change, low/high per range", async () => {
  const { ctx, store } = setup();
  seedHistory(store);
  const m3 = await ok(ctx, "get_net_worth_history"); // default 3M: base = 2025-10-14
  assertEquals(m3.data.range, "3M");
  assertEquals(m3.data.change.amount, 1660);
  assertEquals(m3.data.change.from_date, "2025-10-14");
  assertEquals(m3.data.change.partial, false);
  assertEquals(m3.data.series.length, 5);
  assertEquals(m3.data.series[0], { date: "2025-10-14", net_worth: 20900, super_liquid: 2090 });
  assertEquals(m3.text.split(";")[0], "Net worth $22,560, up $1,660 (+7.9%) over 3 months");
  assertStringIncludes(m3.text, "low $20,900 on Oct 14, 2025, high $22,800 on Jan 10.");
  const m1 = await ok(ctx, "get_net_worth_history", { range: "1M" }); // base = 2025-12-16 or before → 2025-11-20
  assertEquals([m1.data.change.from_date, m1.data.change.amount], ["2025-11-20", 1060]);
  const y1 = await ok(ctx, "get_net_worth_history", { range: "1Y" });
  assertEquals(y1.data.change.partial, true);
  assertStringIncludes(y1.text, "up $2,560 (+12.8%) since Oct 1, 2025");
  const all = await ok(ctx, "get_net_worth_history", { range: "All" });
  assertEquals(all.data.series.length, 6);
  const bad = await call(ctx, "get_net_worth_history", { range: "2W" });
  assert(bad.isError);
});

Deno.test("get_net_worth_history: one account's balance series", async () => {
  const { ctx, store } = setup();
  seedHistory(store);
  const r = await ok(ctx, "get_net_worth_history", { account: "check", range: "All" });
  assertEquals(r.data.account, "Checking");
  assertEquals(r.data.series.map((p: Any) => p.balance), [1000, 1200, 2600, 2400, 2500]); // 2025-11-20 lacks it
  assertEquals(r.data.change.amount, 1500);
  assertEquals(r.data.high, { date: "2025-12-20", value: 2600 });
  assertStringIncludes(r.text, "Checking $2,500, up $1,500 (+150.0%) since Oct 1, 2025");
  const hsa = await ok(ctx, "get_net_worth_history", { account: "hsa" });
  assertStringIncludes(hsa.text, "No HSA history yet: history starts today");
  const unknown = await call(ctx, "get_net_worth_history", { account: "savings" });
  assert(unknown.isError);
  assertStringIncludes(unknown.text, "Valid account names");
});

Deno.test("text rounds amounts ≥ $1,000 to whole dollars (§8); structuredContent stays exact", async () => {
  const { ctx } = setup();
  const nw = await ok(ctx, "get_net_worth");
  assertEquals(nw.data.net_worth, 14584.25);
  assertStringIncludes(nw.text, "Net worth: $14,584 · Super liquid: $1,860 · Net reconciliations: $74.50");
  assertStringIncludes(nw.text, "Credit card (debt, liquid): \u2212$640.25");
  const pay = await ok(ctx, "set_actual", { category: "paychecks", actual: 2100.6 });
  assertEquals(pay.data.category.actual, 2100.6);
  assertStringIncludes(pay.text, "Set Paychecks actual for January 2026 to $2,101 (was —)");
  const y = await ok(ctx, "get_year_summary", { year: 2025 });
  assertEquals(y.data.totals.actual.leftover, 2481.75);
  assertStringIncludes(y.text, "leftover $2,482; annualized savings $29,291 = 48.8% of net");
});

Deno.test("get_net_worth includes the 30-day change only when snapshots exist", async () => {
  const { ctx, store } = setup();
  assertEquals((await ok(ctx, "get_net_worth")).data.change_30d, null);
  seedHistory(store);
  const r = await ok(ctx, "get_net_worth");
  assertEquals(r.data.change_30d.amount, 1060);
  assertEquals(r.data.change_30d.from_date, "2025-11-20");
  assertStringIncludes(r.text, "30-day change (daily snapshots): up $1,060 (+4.9%) over 30 days");
  assertEquals(r.data.net_worth, expected.net_worth.net_worth, "live value still comes from §5");
});

Deno.test("writes that change net worth refresh today's snapshot", async () => {
  const { ctx, store } = setup();
  seedHistory(store); // includes a stale row for today (2026-01-15)
  const today = () => store.snapshots.filter((s) => s.taken_on === "2026-01-15");
  const base = expected.net_worth.net_worth;

  const bal = await ok(ctx, "update_account_balance", { account: "checking", balance: 3000 });
  assertEquals(bal.data.net_worth_snapshot_refreshed, true);
  assertEquals(today().length, 1, "upserted, not duplicated");
  assertEquals([today()[0].net_worth, today()[0].source], [base + 500, "manual"]);
  assertEquals(today()[0].accounts.find((a) => a.name === "Checking")!.balance, 3000);

  await ok(ctx, "add_ledger_entry", { name: "Sam", amount: 40 });
  assertEquals(today()[0].net_worth, base + 540);
  await ok(ctx, "settle_ledger_entry", { name: "sam" });
  assertEquals(today()[0].net_worth, base + 500);

  const k = await ok(ctx, "set_actual", { category: "401k", actual: 100 }); // linked, ×2 match
  assertEquals(k.data.net_worth_snapshot_refreshed, true);
  assertEquals(today()[0].net_worth, base + 700);
  assertEquals(today()[0].accounts.find((a) => a.name === "401k")!.balance, 2400);

  const before = structuredClone(store.snapshots);
  const rent = await ok(ctx, "set_actual", { category: "rent", actual: 1193 }); // not linked
  assertEquals(rent.data.net_worth_snapshot_refreshed, undefined);
  await ok(ctx, "set_budget", { category: "401k", expected: 400 }); // expected never affects net worth
  await ok(ctx, "add_transaction", { amount: 5, category: "food", item: "x" });
  assertEquals(store.snapshots, before);
  assertEquals(store.snapshots.filter((s) => s.taken_on < "2026-01-15").length, 5, "earlier days are frozen");
});

Deno.test("a failing snapshot refresh never fails the user's write", async () => {
  class FlakyStore extends InMemoryStore {
    override refreshNetWorthSnapshot(): Promise<never> {
      return Promise.reject(new Error("rpc down"));
    }
  }
  const store = new FlakyStore(backup, { now: () => NOW });
  const ctx = makeContext(store, { now: () => NOW });
  const errors: unknown[][] = [];
  const orig = console.error;
  console.error = (...a: unknown[]) => errors.push(a);
  try {
    const r = await ok(ctx, "update_account_balance", { account: "checking", balance: 3000 });
    assertEquals(r.data.net_worth_snapshot_refreshed, false);
    assertEquals(store.accounts.find((a) => a.name === "Checking")!.balance, 3000);
    assertEquals((await ok(ctx, "add_ledger_entry", { name: "Sam", amount: 1 })).data.net_worth_snapshot_refreshed, false);
    assertEquals((await ok(ctx, "settle_ledger_entry", { name: "Sam" })).data.net_worth_snapshot_refreshed, false);
    assertEquals((await ok(ctx, "set_actual", { category: "hsa", actual: 50 })).data.net_worth_snapshot_refreshed, false);
  } finally {
    console.error = orig;
  }
  assertEquals(errors.length, 4);
  assertStringIncludes(String(errors[0][1]), "rpc down");
});
