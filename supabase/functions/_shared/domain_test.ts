// deno test --allow-read supabase/functions/_shared/
// Asserts every number in docs/fixtures/expected.json against the TypeScript port.

import { assert, assertAlmostEquals, assertEquals } from "jsr:@std/assert@1.0.19";
import {
  buildIndex,
  type Category,
  formatMoney,
  formatPct,
  matchByName,
  mealPlansView,
  monthView,
  netWorthView,
  planNewMonth,
  type Snapshot,
  todayParts,
  yearView,
} from "./domain.ts";

const root = new URL("../../../docs/fixtures/", import.meta.url);
const backup = JSON.parse(await Deno.readTextFile(new URL("sample-backup.json", root)));
const expected = JSON.parse(await Deno.readTextFile(new URL("expected.json", root)));
const snap: Snapshot = backup;

const near = (actual: number | null, exp: number | null, msg: string) => {
  if (exp === null) return assertEquals(actual, null, msg);
  assert(actual !== null, `${msg}: got null, expected ${exp}`);
  assertAlmostEquals(actual, exp, 1e-6, msg);
};

const TOTAL_KEYS = ["income", "expenses", "saved", "contributions", "leftover"] as const;

Deno.test("months: per-category expected/actual and totals match expected.json", () => {
  const ix = buildIndex(snap);
  const keys = Object.keys(expected.months);
  assertEquals(keys.length, 3);
  for (const key of keys) {
    const [y, m] = key.split("-").map(Number);
    const exp = expected.months[key];
    const v = monthView(snap, y, m, ix);
    assertEquals(v.closed, exp.closed, `${key} closed`);
    assertEquals(v.exists, true);
    for (const [name, e] of Object.entries(exp.categories) as [string, { expected: number; actual: number | null }][]) {
      const row = v.categories.find((r) => r.name === name);
      assert(row, `${key} ${name} row`);
      near(row.expected, e.expected, `${key} ${name} expected`);
      near(row.actual, e.actual, `${key} ${name} actual`);
      near(row.difference, (e.actual ?? 0) - e.expected, `${key} ${name} difference`);
    }
    for (const col of ["expected", "actual"] as const) {
      for (const k of TOTAL_KEYS) near(v[col][k], exp[col][k], `${key} ${col}.${k}`);
    }
  }
});

Deno.test("months: ledger override, refunds, tombstones, out-of-month dates", () => {
  const v = monthView(snap, 2025, 11);
  const pay = v.categories.find((r) => r.name === "Paychecks")!;
  assertEquals(pay.override, true); // typed 4100 while checks sum to 4050
  assertEquals(pay.ledger_sum, 4050);
  const fun = v.categories.find((r) => r.name === "Fun")!;
  assertEquals(fun.actual, 150); // 200 concert − 50 refund; the deleted 999 is ignored
  assertEquals(fun.override, false);
  const food = v.categories.find((r) => r.name === "Food")!;
  assertEquals(food.actual, 330.5); // includes the 2025-10-30 dinner filed under November
  assertEquals(food.remaining, 69.5);
});

Deno.test("years: expected and projected columns match expected.json", () => {
  for (const [ys, exp] of Object.entries(expected.years) as [string, Record<string, Record<string, unknown>>][]) {
    const v = yearView(snap, Number(ys));
    assert(v, `year ${ys}`);
    for (const col of ["expected", "actual"] as const) {
      const e = exp[col] as Record<string, number> & { categories: Record<string, number> };
      for (const k of [...TOTAL_KEYS, "annualized_savings"] as const) near(v[col][k], e[k], `${ys} ${col}.${k}`);
      near(v[col].pct_net, e.pct_net, `${ys} ${col}.pct_net`);
      near(v[col].pct_gross, e.pct_gross, `${ys} ${col}.pct_gross`);
      for (const [name, val] of Object.entries(e.categories)) near(v[col].categories[name], val, `${ys} ${col} ${name}`);
    }
  }
  assertEquals(yearView(snap, 2024), null);
});

Deno.test("net worth matches expected.json", () => {
  const v = netWorthView(snap);
  const e = expected.net_worth;
  assertEquals(v.accounts.map((a) => a.name), Object.keys(e.accounts)); // archived "Old account" excluded
  for (const a of v.accounts) near(a.balance, e.accounts[a.name], `account ${a.name}`);
  near(v.net_reconciliations, e.net_reconciliations, "net_reconciliations");
  near(v.net_worth, e.net_worth, "net_worth");
  near(v.super_liquid, e.super_liquid, "super_liquid");
  assertEquals(v.unsettled.map((x) => x.name), ["Friend owes", "I owe"]);
});

Deno.test("meal plans match expected.json", () => {
  const plans = mealPlansView(snap);
  for (const p of plans) {
    const e = expected.meal_plans[p.name];
    for (const k of Object.keys(e)) near((p.totals as unknown as Record<string, number>)[k], e[k], `${p.name}.${k}`);
    if (p.kind === "recipe") assertEquals(p.totals.monthly_cost, undefined);
  }
  assertEquals(plans.length, Object.keys(expected.meal_plans).length);
});

Deno.test("new month: copies latest expected, adds recurring items clamped to month length", () => {
  let n = 0;
  const plan = planNewMonth(snap, 2026, 2, () => `id-${n++}`);
  assert(plan);
  assertEquals(plan.month, { year: 2026, month: 2, closed: false, note: null });
  const byName = new Map(snap.categories.map((c) => [c.id, c.name]));
  const exp = Object.fromEntries(plan.budgets.map((b) => [byName.get(b.category_id), b.expected]));
  assertEquals(exp, { Paychecks: 4200, Rent: 1250, Food: 450, Fun: 350, Utilities: 95, Roth: 500, "401k": 350, HSA: null });
  assert(plan.budgets.every((b) => b.actual === null));
  assertEquals(plan.transactions.length, 1);
  assertEquals(plan.transactions[0].date, "2026-02-28"); // day 31 clamped
  assertEquals(plan.transactions[0].amount, 15.99);
  assertEquals(plan.transactions[0].item, "Streaming");
  assertEquals(planNewMonth(snap, 2026, 1, () => "x"), null, "existing month is a no-op");
});

Deno.test("new month: only EARLIER months are copied from", () => {
  const plan = planNewMonth(snap, 2025, 10, () => "x")!; // before every existing month
  assertEquals(plan.budgets.length, 8);
  assert(plan.budgets.every((b) => b.expected === null));
  assertEquals(planNewMonth(snap, 2025, 12, () => "x"), null);
});

Deno.test("new month: archived categories are skipped, inactive / deleted recurring ignored", () => {
  const cats: Category[] = snap.categories.map((c) => c.name === "Fun" ? { ...c, archived: true } : c);
  const plan = planNewMonth(
    {
      ...snap,
      categories: cats,
      recurring_items: [
        ...(snap.recurring_items ?? []),
        { id: "r2", category_id: cats[0].id, item: "off", amount: 1, day_of_month: null, active: false, sort_order: 1 },
        { id: "r3", category_id: cats[0].id, item: "gone", amount: 1, day_of_month: 3, active: true, sort_order: 2, deleted: true },
      ],
    },
    2026,
    4,
    () => "x",
  )!;
  assertEquals(plan.budgets.length, 7);
  assertEquals(plan.transactions.map((t) => [t.item, t.date]), [["Streaming", "2026-04-30"]]);
});

Deno.test("name matching: exact, prefix, word prefix, contains, fuzzy, ambiguity", () => {
  const defaults = [
    "Paychecks", "Rent", "Subscriptions", "Food", "Fun", "Gas", "Misc", "Car Ins", "Utilities",
    "Phone Bill", "Roth", "401k", "Taxable Brokerage", "HSA",
  ].map((name) => ({ name }));
  const hit = (q: string) => {
    const r = matchByName(q, defaults);
    return r.ok ? r.item.name : `!${r.reason}:${r.candidates.map((c) => c.name).join("|")}`;
  };
  assertEquals(hit("food"), "Food");
  assertEquals(hit("FOOD"), "Food");
  assertEquals(hit("fun"), "Fun"); // exact beats prefix of "Fun..."
  assertEquals(hit("car insurance"), "Car Ins");
  assertEquals(hit("brokerage"), "Taxable Brokerage");
  assertEquals(hit("401k"), "401k");
  assertEquals(hit("401(k)"), "401k");
  assertEquals(hit("paycheck"), "Paychecks");
  assertEquals(hit("subs"), "Subscriptions");
  assertEquals(hit("phone"), "Phone Bill");
  assertEquals(hit("utilites"), "Utilities"); // typo → fuzzy
  assertEquals(hit("fun money"), "Fun");
  assertEquals(hit("hsa"), "HSA");
  assertEquals(hit("f"), "!ambiguous:Food|Fun");
  assertEquals(hit("groceries"), `!none:${defaults.map((d) => d.name).join("|")}`);
  // Archived items lose ties to active ones.
  const withArchived = [{ name: "Food", archived: true }, { name: "Food delivery", archived: false }];
  const r = matchByName("food", withArchived);
  assert(r.ok && r.item.name === "Food"); // exact still wins
  const r2 = matchByName("fo", withArchived);
  assert(r2.ok && r2.item.name === "Food delivery");
});

Deno.test("display formatting", () => {
  assertEquals(formatMoney(1234), "$1,234");
  assertEquals(formatMoney(1234.5), "$1,234.50");
  assertEquals(formatMoney(-153), "−$153");
  assertEquals(formatMoney(0), "$0");
  assertEquals(formatMoney(null), "—");
  assertEquals(formatPct(0.43701), "43.7%");
});

Deno.test("today is computed in America/New_York", () => {
  // 03:30 UTC on Oct 1 is still Sept 30 in New York.
  assertEquals(todayParts(new Date("2026-10-01T03:30:00Z")).iso, "2026-09-30");
  assertEquals(todayParts(new Date("2026-10-01T05:00:00Z")).iso, "2026-10-01");
});
