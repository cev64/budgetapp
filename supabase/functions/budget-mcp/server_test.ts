// HTTP-level tests: CORS, 401s, MCP JSON-RPC over Streamable HTTP, and the Supabase-backed
// path (token hash lookup + per-user scoping) against a stubbed PostgREST via fetch.
// deno test --allow-read --allow-env supabase/functions/

import { assert, assertEquals, assertStringIncludes } from "jsr:@std/assert@1.0.19";
import { InMemoryStore } from "./memory_store.ts";
import { createHandler, extractToken, fakeResolver, type Resolve, resolveSupabase, serverKey, sha256Hex } from "./server.ts";

const backup = JSON.parse(await Deno.readTextFile(new URL("../../../docs/fixtures/sample-backup.json", import.meta.url)));
const MCP_HEADERS = { "Content-Type": "application/json", Accept: "application/json, text/event-stream" };

function memoryResolver(): Resolve {
  const store = new InMemoryStore(backup);
  return (req) => {
    const t = extractToken(req);
    return Promise.resolve(t === "bgt_test_token_aaaaaaaaaaaa" ? { store } : new Response("no", { status: 401 }));
  };
}

async function rpc(handler: (r: Request) => Promise<Response>, url: string, method: string, params: unknown, id = 1, headers = {}) {
  const res = await handler(new Request(url, { method: "POST", headers: { ...MCP_HEADERS, ...headers }, body: JSON.stringify({ jsonrpc: "2.0", id, method, params }) }));
  return { res, body: res.headers.get("content-type")?.includes("json") ? await res.json() : await res.text() };
}

Deno.test("extractToken: query param, bearer bgt_ only", () => {
  assertEquals(extractToken(new Request("https://x/budget-mcp?key=bgt_abc")), "bgt_abc");
  assertEquals(extractToken(new Request("https://x/budget-mcp", { headers: { Authorization: "Bearer bgt_xyz" } })), "bgt_xyz");
  assertEquals(extractToken(new Request("https://x/budget-mcp", { headers: { Authorization: "Bearer eyJhbGciOi.jwt" } })), null);
  assertEquals(extractToken(new Request("https://x/budget-mcp")), null);
});

Deno.test("serverKey prefers SUPABASE_SECRET_KEYS.default, falls back to the legacy key", () => {
  Deno.env.set("SUPABASE_SERVICE_ROLE_KEY", "legacy");
  assertEquals(serverKey(), "legacy");
  Deno.env.set("SUPABASE_SECRET_KEYS", JSON.stringify({ default: "sb_secret_x" }));
  assertEquals(serverKey(), "sb_secret_x");
  Deno.env.set("SUPABASE_SECRET_KEYS", "not json");
  assertEquals(serverKey(), "legacy");
  Deno.env.delete("SUPABASE_SECRET_KEYS");
  Deno.env.delete("SUPABASE_SERVICE_ROLE_KEY");
  assertEquals(serverKey(), null);
});

Deno.test("fake mode never activates next to real credentials", async () => {
  Deno.env.set("BUDGET_MCP_FAKE", "1");
  Deno.env.set("SUPABASE_SECRET_KEYS", JSON.stringify({ default: "sb_secret_x" }));
  try {
    assertEquals(await fakeResolver(), null);
  } finally {
    Deno.env.delete("BUDGET_MCP_FAKE");
    Deno.env.delete("SUPABASE_SECRET_KEYS");
  }
});

Deno.test("sha256Hex is lowercase hex", async () => {
  assertEquals(await sha256Hex("abc"), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
});

Deno.test("CORS preflight, 401 without token, 405 on GET", async () => {
  const h = createHandler(memoryResolver());
  const pre = await h(new Request("https://x/budget-mcp", { method: "OPTIONS" }));
  assertEquals(pre.status, 204);
  assertEquals(pre.headers.get("access-control-allow-origin"), "*");
  assertStringIncludes(pre.headers.get("access-control-allow-headers")!, "mcp-protocol-version");
  const get = await h(new Request("https://x/budget-mcp?key=bgt_test_token_aaaaaaaaaaaa"));
  assertEquals(get.status, 405);
  await get.body?.cancel();
});

Deno.test("MCP over HTTP: initialize, tools/list, tools/call (in-memory)", async () => {
  const h = createHandler(memoryResolver());
  const url = "https://x/budget-mcp?key=bgt_test_token_aaaaaaaaaaaa";
  const init = await rpc(h, url, "initialize", { protocolVersion: "2025-06-18", capabilities: {}, clientInfo: { name: "test", version: "0" } });
  assertEquals(init.res.status, 200);
  assertEquals(init.res.headers.get("access-control-allow-origin"), "*");
  assertEquals(init.body.result.serverInfo.name, "budget");
  assert(init.body.result.capabilities.tools);
  assertStringIncludes(init.body.result.instructions, "ledger");
  const list = await rpc(h, url, "tools/list", {}, 2, { "mcp-protocol-version": "2025-06-18" });
  const tools = list.body.result.tools;
  assertEquals(tools.length, 16);
  const add = tools.find((t: { name: string }) => t.name === "add_transaction");
  assertEquals(add.inputSchema.type, "object");
  assertEquals(add.inputSchema.required.sort(), ["amount", "category", "item"]);
  assertEquals(add.annotations.readOnlyHint, false);
  const del = tools.find((t: { name: string }) => t.name === "delete_transaction");
  assertEquals(del.annotations.destructiveHint, true);
  const call = await rpc(h, url, "tools/call", { name: "get_budget_overview", arguments: { year: 2025, month: 11 } }, 3);
  assertEquals(call.body.result.isError, undefined);
  assertEquals(call.body.result.structuredContent.totals.actual.leftover, 1519.5);
  assertStringIncludes(call.body.result.content[0].text, "November 2025 (closed");
  const bad = await rpc(h, url, "tools/call", { name: "add_transaction", arguments: { amount: "lots", category: "food", item: "x" } }, 4);
  assert(bad.body.result?.isError || bad.body.error, "invalid args are rejected");
});

// ---------------------------------------------------------------------------
// Supabase path with a fake PostgREST
// ---------------------------------------------------------------------------

Deno.test("Supabase store: token lookup by hash, 401s, and every query scoped to the user", async () => {
  const USER = "11111111-2222-4333-8444-555555555555";
  const TOKEN = "bgt_" + "A".repeat(43);
  const HASH = await sha256Hex(TOKEN);
  const FOOD = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
  Deno.env.set("SUPABASE_URL", "http://fake-supabase.test");
  Deno.env.set("SUPABASE_SERVICE_ROLE_KEY", "service-role-test-key");

  const calls: { method: string; url: URL; body: unknown }[] = [];
  const realFetch = globalThis.fetch;
  globalThis.fetch = async (input: string | URL | Request, init?: RequestInit) => {
    const req = new Request(input, init);
    const url = new URL(req.url);
    const text = req.method === "GET" || req.method === "HEAD" ? "" : await req.text();
    const body = text ? JSON.parse(text) : null;
    calls.push({ method: req.method, url, body });
    const table = url.pathname.replace("/rest/v1/", "");
    const wantsObject = (req.headers.get("accept") ?? "").includes("vnd.pgrst.object");
    const reply = (rows: unknown[]) => {
      if (wantsObject) {
        return rows.length === 1
          ? new Response(JSON.stringify(rows[0]), { headers: { "content-type": "application/json" } })
          : new Response(JSON.stringify({ code: "PGRST116", message: "0 rows" }), { status: 406, headers: { "content-type": "application/json" } });
      }
      return new Response(JSON.stringify(rows), { headers: { "content-type": "application/json" } });
    };
    if (table === "mcp_tokens" && req.method === "GET") {
      const ok = url.searchParams.get("token_hash") === `eq.${HASH}` && url.searchParams.get("revoked") === "eq.false";
      return reply(ok ? [{ id: "tok-1", user_id: USER, last_used_at: null }] : []);
    }
    if (req.method === "GET" && table === "categories") {
      return reply([{ id: FOOD, user_id: USER, name: "Food", kind: "expense", tracking: "ledger", match_multiplier: "1", sort_order: 12, archived: false, deleted: false }]);
    }
    if (req.method === "POST" && url.pathname === "/rest/v1/rpc/write_net_worth_snapshot") {
      // Simulate an RPC failure: the user's write must still succeed.
      return new Response(JSON.stringify({ code: "42501", message: "permission denied (simulated)" }), {
        status: 500,
        headers: { "content-type": "application/json" },
      });
    }
    if (req.method === "GET") return reply([]);
    if (req.method === "POST") return reply(Array.isArray(body) ? body : [body]);
    return new Response(null, { status: 204 });
  };

  try {
    const h = createHandler(resolveSupabase);
    // Missing / malformed / unknown tokens → 401 JSON
    for (const url of ["http://f/budget-mcp", "http://f/budget-mcp?key=nope", "http://f/budget-mcp?key=bgt_" + "B".repeat(43)]) {
      const { res, body } = await rpc(h, url, "tools/list", {});
      assertEquals(res.status, 401, url);
      assertEquals(body.error, "unauthorized");
      assertEquals(res.headers.get("access-control-allow-origin"), "*");
    }
    calls.length = 0;

    // Valid token via Authorization header → add a transaction (creates the month).
    const { res, body } = await rpc(h, "http://f/budget-mcp", "tools/call", {
      name: "add_transaction",
      arguments: { amount: 14, category: "food", item: "Chipotle", date: "2026-10-03" },
    }, 7, { Authorization: `Bearer ${TOKEN}` });
    assertEquals(res.status, 200);
    assert(!body.result.isError, JSON.stringify(body));
    assertStringIncludes(body.result.content[0].text, "October 2026 did not exist");
    assertEquals(body.result.structuredContent.transaction.amount, 14);
    await new Promise((r) => setTimeout(r, 20)); // let the last_used_at update run

    // A net-worth write refreshes today's snapshot via RPC; the (simulated) RPC failure is swallowed.
    const errors: unknown[][] = [];
    const origError = console.error;
    console.error = (...a: unknown[]) => errors.push(a);
    let ledger;
    try {
      ledger = await rpc(h, `http://f/budget-mcp?key=${TOKEN}`, "tools/call", { name: "add_ledger_entry", arguments: { name: "Sam", amount: 40 } }, 8);
    } finally {
      console.error = origError;
    }
    assert(!ledger.body.result.isError, JSON.stringify(ledger.body));
    assertEquals(ledger.body.result.structuredContent.net_worth_snapshot_refreshed, false);
    assertStringIncludes(String(errors[0]?.[1]), "permission denied (simulated)");
    const rpcCall = calls.find((c) => c.url.pathname === "/rest/v1/rpc/write_net_worth_snapshot");
    assert(rpcCall, "RPC called");
    assertEquals(rpcCall.method, "POST");
    assertEquals(rpcCall.body, { p_user: USER, p_source: "manual" });

    const hist = await rpc(h, `http://f/budget-mcp?key=${TOKEN}`, "tools/call", { name: "get_net_worth_history", arguments: {} }, 9);
    assertStringIncludes(hist.body.result.content[0].text, "history starts today");
    assert(calls.some((c) => c.url.pathname === "/rest/v1/net_worth_snapshots" && c.method === "GET"));

    const budgetTables = ["settings", "categories", "months", "budgets", "transactions", "recurring_items", "accounts", "ledger_entries", "net_worth_snapshots"];
    const relevant = calls.filter((c) => budgetTables.includes(c.url.pathname.replace("/rest/v1/", "")));
    assert(relevant.length > 5);
    for (const c of relevant) {
      if (c.method === "GET") {
        assertEquals(c.url.searchParams.get("user_id"), `eq.${USER}`, `scoped read ${c.url}`);
        assertEquals(c.url.searchParams.get("deleted"), "eq.false", `tombstones hidden ${c.url}`);
      } else {
        assertEquals(c.method, "POST");
        for (const row of c.body as Record<string, unknown>[]) {
          assertEquals(row.user_id, USER, `write sets user_id ${c.url}`);
          assert(!("updated_at" in row), "never sends updated_at");
          assert(!("created_at" in row), "never sends created_at");
        }
      }
    }
    const writes = relevant.filter((c) => c.method === "POST").map((c) => `${c.url.pathname.replace("/rest/v1/", "")}?${c.url.searchParams.get("on_conflict")}`);
    assertEquals(writes, ["months?user_id,year,month", "budgets?user_id,year,month,category_id", "transactions?id", "ledger_entries?id"]);
    const tx = relevant.find((c) => c.method === "POST" && c.url.pathname.endsWith("/transactions"))!.body as Record<string, unknown>[];
    assertEquals(tx[0].category_id, FOOD);
    assertEquals(tx[0].deleted, false);
    const touch = calls.find((c) => c.url.pathname.endsWith("/mcp_tokens") && c.method === "PATCH");
    assert(touch, "last_used_at updated");
    assertEquals(touch.url.searchParams.get("user_id"), `eq.${USER}`);
    // The raw token never leaves the function: only its hash is sent to the database.
    for (const c of calls) {
      assert(!c.url.toString().includes(TOKEN));
      assert(!JSON.stringify(c.body ?? "").includes(TOKEN));
    }
  } finally {
    globalThis.fetch = realFetch;
    Deno.env.delete("SUPABASE_URL");
    Deno.env.delete("SUPABASE_SERVICE_ROLE_KEY");
  }
});
