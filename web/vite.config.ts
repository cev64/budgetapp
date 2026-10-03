/// <reference types="vitest/config" />
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { defineConfig, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';

const pkg = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf8')) as { version: string };
const repoRoot = fileURLToPath(new URL('..', import.meta.url));

/**
 * Emits sw.js next to the build with a versioned cache name and the list of
 * hashed assets to precache, so the app shell is readable offline after the first visit.
 */
function serviceWorker(): Plugin {
  return {
    name: 'budget-service-worker',
    apply: 'build',
    generateBundle(_options, bundle) {
      // Precache the shell plus the Latin font subsets; other subsets are cached on first use.
      const files = Object.keys(bundle).filter((f) => {
        if (f.endsWith('.map')) return false;
        if (/\.woff2?$/.test(f)) return f.endsWith('.woff2') && /-latin-(?!ext)/.test(f);
        return true;
      });
      const version = `${pkg.version}-${Date.now().toString(36)}`;
      const source = readFileSync(new URL('./sw/sw.js', import.meta.url), 'utf8')
        .replace('__SW_VERSION__', version)
        .replace('__PRECACHE__', JSON.stringify(['./', ...files]));
      this.emitFile({ type: 'asset', fileName: 'sw.js', source });
    },
  };
}

export default defineConfig(({ command, isPreview }) => ({
  // GitHub Pages project site: https://cev64.github.io/budgetapp/
  base: command === 'build' || isPreview ? '/budgetapp/' : '/',
  plugins: [react(), serviceWorker()],
  define: {
    __APP_VERSION__: JSON.stringify(pkg.version),
  },
  server: {
    // config/supabase.json and docs/fixtures live outside web/
    fs: { allow: [repoRoot] },
  },
  build: {
    target: 'es2022',
    sourcemap: false,
    rolldownOptions: {
      output: {
        // Vendor chunks change rarely, so they stay cached across app releases.
        codeSplitting: {
          groups: [
            { name: 'react', test: /node_modules[\\/](react|react-dom|react-router|scheduler)[\\/]/ },
            { name: 'supabase', test: /node_modules[\\/]@supabase[\\/]/ },
            { name: 'icons', test: /node_modules[\\/]lucide-react[\\/]/ },
          ],
        },
      },
    },
  },
  test: {
    environment: 'node',
    include: ['test/**/*.test.ts'],
  },
}));
