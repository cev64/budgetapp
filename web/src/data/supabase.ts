import { createClient, type SupabaseClient } from '@supabase/supabase-js';
import { backendConfigured, supabaseKey, supabaseUrl } from '../config';

let client: SupabaseClient | null = null;

/** The shared Supabase client, or null when the backend is not configured. */
export function getSupabase(): SupabaseClient | null {
  if (!backendConfigured) return null;
  client ??= createClient(supabaseUrl, supabaseKey, {
    auth: {
      persistSession: true,
      autoRefreshToken: true,
      // Implicit flow: confirmation and recovery links work from any browser (tokens arrive in the URL hash,
      // which supabase-js reads and clears before the hash router mounts).
      detectSessionInUrl: true,
      flowType: 'implicit',
    },
  });
  return client;
}

/** Where auth emails send the user back to: this page, without the hash route. */
export const redirectUrl = (): string => window.location.origin + window.location.pathname;
