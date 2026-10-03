import { supabaseUrl } from '../config';
import { getSupabase } from './supabase';

// Personal access tokens for the budget MCP server (supabase/functions/budget-mcp).
// Not a synced table: loaded only when Settings opens, never exported in backups.
// Only the SHA-256 of a token is stored; the raw token is shown to the user once.

export interface McpToken {
  id: string;
  name: string;
  token_hint: string | null;
  created_at: string;
  last_used_at: string | null;
}

function base64url(bytes: Uint8Array): string {
  let s = '';
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** `bgt_` + 32 random bytes, base64url-encoded. */
export function generateToken(): string {
  return `bgt_${base64url(crypto.getRandomValues(new Uint8Array(32)))}`;
}

/** SHA-256 as lowercase hex. */
export async function sha256Hex(text: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

export const connectorUrl = (token: string): string =>
  `${supabaseUrl.replace(/\/+$/, '')}/functions/v1/budget-mcp?key=${encodeURIComponent(token)}`;

function client() {
  const c = getSupabase();
  if (!c) throw new Error('Backend not configured.');
  return c;
}

export async function listTokens(): Promise<McpToken[]> {
  const { data, error } = await client()
    .from('mcp_tokens')
    .select('id,name,token_hint,created_at,last_used_at')
    .eq('revoked', false)
    .order('created_at', { ascending: false });
  if (error) throw new Error(error.message);
  return (data ?? []) as McpToken[];
}

/** Creates a token and returns the connector URL (the only time the raw token exists). */
export async function createToken(userId: string, name: string): Promise<{ token: McpToken; url: string }> {
  const raw = generateToken();
  const { data, error } = await client()
    .from('mcp_tokens')
    .insert({ user_id: userId, name, token_hash: await sha256Hex(raw), token_hint: raw.slice(-4) })
    .select('id,name,token_hint,created_at,last_used_at')
    .single();
  if (error) throw new Error(error.message);
  return { token: data as McpToken, url: connectorUrl(raw) };
}

export async function revokeToken(id: string): Promise<void> {
  const { error } = await client().from('mcp_tokens').update({ revoked: true }).eq('id', id);
  if (error) throw new Error(error.message);
}
