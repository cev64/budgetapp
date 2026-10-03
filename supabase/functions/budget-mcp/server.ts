// HTTP layer of budget-mcp (imported by index.ts and tests): auth, CORS and MCP wiring.
//
//   POST https://<project>.supabase.co/functions/v1/budget-mcp?key=bgt_...
//   (or Authorization: Bearer bgt_...)
//
// Deployed with verify_jwt = false (see supabase/config.toml): authentication is the
// personal access token above, looked up by SHA-256 hash in public.mcp_tokens using the
// service role. The service role bypasses RLS, so every query is explicitly scoped to the
// token's user inside SupabaseStore. See docs/MCP.md.

import { createClient, McpServer, type SupabaseClient, WebStandardStreamableHTTPServerTransport } from "./deps.ts";
import type { BudgetStore } from "./store.ts";
import { SupabaseStore } from "./supabase_store.ts";
import { makeContext, registerTools, SERVER_INSTRUCTIONS } from "./tools.ts";

const SERVER_NAME = "budget";
const SERVER_VERSION = "1.0.0";
const TOKEN_RE = /^bgt_[A-Za-z0-9_-]{16,256}$/;
const LAST_USED_INTERVAL_MS = 60_000;

const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, DELETE, OPTIONS",
  "Access-Control-Allow-Headers":
    "authorization, content-type, accept, mcp-session-id, mcp-protocol-version, last-event-id, x-client-info, apikey",
  "Access-Control-Expose-Headers": "mcp-session-id, mcp-protocol-version, www-authenticate",
  "Access-Control-Max-Age": "86400",
};

function withCors(res: Response): Response {
  const headers = new Headers(res.headers);
  for (const [k, v] of Object.entries(CORS_HEADERS)) headers.set(k, v);
  return new Response(res.body, { status: res.status, statusText: res.statusText, headers });
}

function json(status: number, body: unknown, extra: Record<string, string> = {}): Response {
  return withCors(
    new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json", ...extra } }),
  );
}

function unauthorized(message: string): Response {
  return json(401, { error: "unauthorized", message }, { "WWW-Authenticate": 'Bearer realm="budget-mcp"' });
}

/** The raw token from ?key=... or Authorization: Bearer bgt_... (never logged). */
export function extractToken(req: Request): string | null {
  const key = new URL(req.url).searchParams.get("key");
  if (key) return key.trim();
  const auth = req.headers.get("authorization") ?? "";
  const m = /^Bearer\s+(\S+)$/i.exec(auth.trim());
  if (m && m[1].startsWith("bgt_")) return m[1];
  return null;
}

export async function sha256Hex(s: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, "0")).join("");
}

// ---------------------------------------------------------------------------
// Store resolution: real (Supabase + token) or fake (local testing only)
// ---------------------------------------------------------------------------

export type Resolve = (req: Request) => Promise<{ store: BudgetStore } | Response>;

let admin: SupabaseClient | null = null;

/**
 * Server-side key that bypasses RLS. Prefers the new secret key (SUPABASE_SECRET_KEYS JSON,
 * name "default"), falls back to the legacy SUPABASE_SERVICE_ROLE_KEY. Both are injected
 * automatically by Supabase into Edge Functions; neither ever leaves the server.
 */
export function serverKey(): string | null {
  const json = Deno.env.get("SUPABASE_SECRET_KEYS");
  if (json) {
    try {
      const k = JSON.parse(json)?.default;
      if (typeof k === "string" && k) return k;
    } catch {
      // fall through to the legacy key
    }
  }
  return Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || null;
}

function adminClient(): SupabaseClient {
  if (!admin) {
    const url = Deno.env.get("SUPABASE_URL");
    const key = serverKey();
    if (!url || !key) throw new Error("SUPABASE_URL and SUPABASE_SECRET_KEYS / SUPABASE_SERVICE_ROLE_KEY must be set");
    admin = createClient(url, key, { auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false } });
  }
  return admin;
}

function runInBackground(p: Promise<unknown>) {
  const guarded = p.catch((e) => console.error("[budget-mcp] background task failed:", e instanceof Error ? e.message : String(e)));
  // deno-lint-ignore no-explicit-any
  const rt = (globalThis as any).EdgeRuntime;
  if (rt?.waitUntil) rt.waitUntil(guarded);
}

export const resolveSupabase: Resolve = async (req) => {
  const token = extractToken(req);
  if (!token) return unauthorized("Missing token. Connect with .../functions/v1/budget-mcp?key=<token> (create one in the web app: Settings → AI assistant).");
  if (!TOKEN_RE.test(token)) return unauthorized("Invalid or revoked token.");
  const hash = await sha256Hex(token);
  const db = adminClient();
  // Lookup by hash: the raw token is never stored or compared, so no timing side channel on it.
  const { data, error } = await db
    .from("mcp_tokens")
    .select("id, user_id, last_used_at")
    .eq("token_hash", hash)
    .eq("revoked", false)
    .maybeSingle();
  if (error) {
    console.error("[budget-mcp] token lookup failed:", error.message);
    return json(500, { error: "server_error", message: "Token lookup failed." });
  }
  if (!data?.user_id) return unauthorized("Invalid or revoked token.");
  const last = data.last_used_at ? Date.parse(data.last_used_at) : 0;
  if (Date.now() - last > LAST_USED_INTERVAL_MS) {
    runInBackground(
      Promise.resolve(
        db.from("mcp_tokens").update({ last_used_at: new Date().toISOString() }).eq("id", data.id).eq("user_id", data.user_id),
      ),
    );
  }
  return { store: new SupabaseStore(db, data.user_id) };
};

/**
 * Local-only fake: BUDGET_MCP_FAKE=1 serves an in-memory store seeded from the synthetic
 * fixture and accepts only the token "bgt_fake_local_token_0000". Refuses to activate when any
 * server key or hosted-runtime marker is present (always the case on Supabase), so it can never
 * run in production.
 */
export async function fakeResolver(): Promise<Resolve | null> {
  if (Deno.env.get("BUDGET_MCP_FAKE") !== "1") return null;
  const hosted = ["SUPABASE_SERVICE_ROLE_KEY", "SUPABASE_SECRET_KEYS", "SB_EXECUTION_ID", "DENO_DEPLOYMENT_ID"]
    .some((k) => !!Deno.env.get(k));
  if (hosted) {
    console.error("[budget-mcp] BUDGET_MCP_FAKE ignored: running with real Supabase credentials.");
    return null;
  }
  const { InMemoryStore } = await import("./memory_store.ts");
  const seedPath = Deno.env.get("BUDGET_MCP_FAKE_SEED") ??
    new URL("../../../docs/fixtures/sample-backup.json", import.meta.url).pathname;
  const store = new InMemoryStore(JSON.parse(await Deno.readTextFile(seedPath)));
  console.log(`[budget-mcp] FAKE MODE: in-memory store seeded from ${seedPath}`);
  return (req) => {
    const token = extractToken(req);
    if (!token) return Promise.resolve(unauthorized("Missing token."));
    if (token !== "bgt_fake_local_token_0000") return Promise.resolve(unauthorized("Invalid or revoked token."));
    return Promise.resolve({ store });
  };
}

// ---------------------------------------------------------------------------
// HTTP handler
// ---------------------------------------------------------------------------

export function createServer(store: BudgetStore, now?: () => Date): McpServer {
  const server = new McpServer(
    { name: SERVER_NAME, title: "Budget", version: SERVER_VERSION },
    { instructions: SERVER_INSTRUCTIONS, capabilities: { tools: {} } },
  );
  registerTools(server, makeContext(store, now ? { now } : {}));
  return server;
}

export function createHandler(resolve: Resolve) {
  return async (req: Request): Promise<Response> => {
    if (req.method === "OPTIONS") return withCors(new Response(null, { status: 204 }));
    try {
      const resolved = await resolve(req);
      if (resolved instanceof Response) return resolved;
      if (req.method !== "POST") {
        // Stateless server: no standalone SSE stream (GET) and no sessions to DELETE.
        return json(405, { jsonrpc: "2.0", error: { code: -32000, message: "Method not allowed. Use POST." }, id: null }, {
          Allow: "POST, OPTIONS",
        });
      }
      // A fresh server + transport per request (stateless mode, JSON responses instead of SSE).
      const server = createServer(resolved.store);
      const transport = new WebStandardStreamableHTTPServerTransport({ sessionIdGenerator: undefined, enableJsonResponse: true });
      await server.connect(transport);
      const res = await transport.handleRequest(req);
      return withCors(res);
    } catch (e) {
      console.error("[budget-mcp] request failed:", e instanceof Error ? e.message : String(e));
      return json(500, { jsonrpc: "2.0", error: { code: -32603, message: "Internal server error" }, id: null });
    }
  };
}
