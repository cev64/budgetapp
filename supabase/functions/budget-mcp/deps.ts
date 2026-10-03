// Third-party dependencies, pinned, in one place. Full npm: specifiers (not bare names)
// so the function works whether or not the deployer uploads deno.json as an import map.
export { McpServer } from "npm:@modelcontextprotocol/sdk@1.31.0/server/mcp.js";
export { WebStandardStreamableHTTPServerTransport } from "npm:@modelcontextprotocol/sdk@1.31.0/server/webStandardStreamableHttp.js";
export type { CallToolResult } from "npm:@modelcontextprotocol/sdk@1.31.0/types.js";
export { z } from "npm:zod@4.6.5";
export { createClient } from "npm:@supabase/supabase-js@2.117.2";
export type { SupabaseClient } from "npm:@supabase/supabase-js@2.117.2";
