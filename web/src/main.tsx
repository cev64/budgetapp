import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Root } from './app/Root';
import { getSupabase } from './data/supabase';
import { applyTheme, getThemePref } from './ui/theme';
import '@fontsource-variable/inter/wght.css';
import '@fontsource/barlow-condensed/600.css';
import './styles/tokens.css';
import './styles/base.css';
import './styles/app.css';

async function boot() {
  applyTheme(getThemePref());
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => applyTheme(getThemePref()));

  // Let supabase-js consume auth redirects (#access_token=… / #error=…) before the hash router mounts.
  let urlAuthError: string | null = null;
  const client = getSupabase();
  if (client) {
    const { error } = await client.auth.initialize();
    if (error) {
      urlAuthError = error.message;
      if (/(^|[#&])(error|error_code)=/.test(window.location.hash)) history.replaceState(null, '', `${location.pathname}#/`);
    }
  }

  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <Root urlAuthError={urlAuthError} />
    </StrictMode>,
  );

  if (import.meta.env.PROD && 'serviceWorker' in navigator) {
    window.addEventListener('load', () => {
      navigator.serviceWorker.register(`${import.meta.env.BASE_URL}sw.js`).catch(() => {
        // Offline support is a nice-to-have: the app works without it.
      });
    });
  }
}

void boot();
