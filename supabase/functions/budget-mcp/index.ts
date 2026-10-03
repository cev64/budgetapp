// budget-mcp: remote MCP server (Streamable HTTP, stateless) for the budget app.
// Entry point only; the logic lives in server.ts (HTTP/auth), tools.ts (MCP tools),
// supabase_store.ts (data access) and ../_shared/domain.ts (budget math). See docs/MCP.md.

import "jsr:@supabase/functions-js@2.5.0/edge-runtime.d.ts";
import { createHandler, fakeResolver, resolveSupabase } from "./server.ts";

const resolve = (await fakeResolver()) ?? resolveSupabase;
Deno.serve(createHandler(resolve));
