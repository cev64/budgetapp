import file from '../../config/supabase.json';

// Public client config: config/supabase.json, overridable with VITE_SUPABASE_URL / VITE_SUPABASE_PUBLISHABLE_KEY.

const env = import.meta.env;

export const supabaseUrl = (env.VITE_SUPABASE_URL || file.url || '').trim();
export const supabaseKey = (env.VITE_SUPABASE_PUBLISHABLE_KEY || file.publishableKey || '').trim();
export const backendConfigured = Boolean(supabaseUrl && supabaseKey);

export const APP_VERSION = __APP_VERSION__;
