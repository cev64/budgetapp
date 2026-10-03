// deno test --allow-read supabase/functions/_shared/
// Asserts every number in docs/fixtures/expected.json against the TypeScript port.

import { assert, assertAlmostEquals, assertEquals } from "jsr:@std/assert@1.0.19";
import {
  addDays,
  buildIndex,
  changeOver,
  formatShortDate,
  formatSignedPct,
  historyView,
  type NetWorthSnapshot,
  snapshotFromView,
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
  accountBalanceIn,
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

Deno.test("display formatting: DOMAIN_RULES §8 currency test vectors", () => {
  const vectors: [number, string][] = [
    [0, "$0"],
    [12, "$12"],
    [12.5, "$12.50"],
    [999.994, "$999.99"],
    [999.995, "$1,000"],
    [1000, "$1,000"],
    [1000.5, "$1,001"],
    [22560, "$22,560"],
    [2886.6667, "$2,887"],
    [-640.25, "\u2212$640.25"],
    [-1022.5, "\u2212$1,023"],
    [-0.004, "$0"],
  ];
  for (const [v, want] of vectors) assertEquals(formatMoney(v), want, `formatMoney(${v})`);
  // Examples given in the rule text.
  assertEquals(formatMoney(1234.56), "$1,235");
  assertEquals(formatMoney(-1500.4), "\u2212$1,500");
  assertEquals(formatMoney(69.6667), "$69.67");
  assertEquals(formatMoney(999.99), "$999.99");
  assertEquals(formatMoney(-153), "\u2212$153");
  assertEquals(formatMoney(-0), "$0");
  assertEquals(formatMoney(null), "—");
  assertEquals(formatPct(0.43701), "43.7%");
});

Deno.test("today is computed in America/New_York", () => {
  // 03:30 UTC on Oct 1 is still Sept 30 in New York.
  assertEquals(todayParts(new Date("2026-10-01T03:30:00Z")).iso, "2026-09-30");
  assertEquals(todayParts(new Date("2026-10-01T05:00:00Z")).iso, "2026-10-01");
});

// ---------------------------------------------------------------------------
// §5b net worth history
// ---------------------------------------------------------------------------

const snapAt = (taken_on: string, net_worth: number, accounts: NetWorthSnapshot["accounts"] = []): NetWorthSnapshot => ({
  taken_on,
  net_worth,
  super_liquid: net_worth / 2,
  reconciliations: 0,
  accounts,
  source: "auto",
});

Deno.test("snapshotFromView reproduces the §5 numbers for the fixture", () => {
  const s = snapshotFromView(netWorthView(snap), "2026-01-15", "manual");
  assertEquals(s.net_worth, expected.net_worth.net_worth);
  assertEquals(s.super_liquid, expected.net_worth.super_liquid);
  assertEquals(s.reconciliations, expected.net_worth.net_reconciliations);
  assertEquals(Object.fromEntries(s.accounts.map((a) => [a.name, a.balance])), expected.net_worth.accounts);
  assertEquals(s.accounts[0], { id: "167fc82d-3932-5e37-8500-a47624562490", name: "Checking", group: "cash", liquid: true, balance: 2500 });
});

Deno.test("addDays crosses month, year and leap boundaries", () => {
  assertEquals(addDays("2026-01-15", -91), "2025-10-16");
  assertEquals(addDays("2026-03-01", -1), "2026-02-28");
  assertEquals(addDays("2028-03-01", -1), "2028-02-29");
  assertEquals(addDays("2025-12-31", 1), "2026-01-01");
});

Deno.test("changeOver: on-or-before base, oldest fallback (partial), All, zero base", () => {
  const pts = [
    { date: "2025-10-01", value: 20000 },
    { date: "2025-10-14", value: 20900 },
    { date: "2025-11-20", value: 21500 },
    { date: "2026-01-10", value: 22800 },
    { date: "2026-01-15", value: 22560 },
  ];
  const m3 = changeOver(pts, "3M")!; // target 2025-10-16 → base 2025-10-14
  assertEquals([m3.base.date, m3.amount, m3.partial], ["2025-10-14", 1660, false]);
  assertAlmostEquals(m3.pct!, 1660 / 20900, 1e-6);
  const m1 = changeOver(pts, "1M")!; // target 2025-12-16 → base 2025-11-20
  assertEquals([m1.base.date, m1.amount], ["2025-11-20", 1060]);
  const y1 = changeOver([...pts].reverse(), "1Y")!; // nothing a year old → oldest, partial
  assertEquals([y1.base.date, y1.amount, y1.partial], ["2025-10-01", 2560, true]);
  const all = changeOver(pts, "All")!;
  assertEquals([all.base.date, all.partial], ["2025-10-01", false]);
  const exact = changeOver([{ date: "2025-12-16", value: 5 }, { date: "2026-01-15", value: 6 }], "1M")!;
  assertEquals(exact.base.date, "2025-12-16", "a snapshot exactly one period old is used");
  assertEquals(changeOver([{ date: "2026-01-01", value: 0 }, { date: "2026-01-15", value: 10 }], "All")!.pct, null);
  assertEquals(changeOver([], "3M"), null);
});

Deno.test("historyView: series from the base, low/high, tombstones and per-account values", () => {
  const acc = (bal: number) => [{ id: "chk", name: "Checking", group: "cash", liquid: true, balance: bal }];
  const snaps = [
    snapAt("2025-10-01", 20000, acc(100)),
    snapAt("2025-10-14", 20900, acc(300)),
    { ...snapAt("2025-10-20", 1, acc(1)), deleted: true },
    snapAt("2025-11-20", 21500),
    snapAt("2026-01-10", 22800, acc(250)),
    snapAt("2026-01-15", 22560, acc(275)),
  ];
  const v = historyView(snaps, "3M");
  assertEquals(v.series.map((p) => p.date), ["2025-10-14", "2025-11-20", "2026-01-10", "2026-01-15"]);
  assertEquals(v.low, { date: "2025-10-14", value: 20900 });
  assertEquals(v.high, { date: "2026-01-10", value: 22800 });
  const a = historyView(snaps, "All", (s) => accountBalanceIn(s, "chk"));
  assertEquals(a.series.map((p) => p.value), [100, 300, 250, 275]); // 2025-11-20 lacks the account
  assertEquals(a.change!.amount, 175);
  assertEquals(historyView([], "1M").change, null);
});

Deno.test("history display helpers", () => {
  assertEquals(formatSignedPct(0.0579), "+5.8%");
  assertEquals(formatSignedPct(-0.0123), "\u22121.2%");
  assertEquals(formatSignedPct(0), "0.0%");
  assertEquals(formatShortDate("2026-08-03", 2026), "Aug 3");
  assertEquals(formatShortDate("2025-10-14", 2026), "Oct 14, 2025");
});
