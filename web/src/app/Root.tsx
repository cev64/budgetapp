import { useEffect, useMemo, useState } from 'react';
import type { Session } from '@supabase/supabase-js';
import { SessionContext, type AppSession } from './session';
import { AppRoutes } from './AppRoutes';
import { AuthScreen, NewPasswordScreen } from '../screens/Auth';
import { Store, CACHE_PREFIX } from '../data/store';
import { SupabaseBackend, type Backend } from '../data/backend';
import { createActions } from '../data/actions';
import { getSupabase } from '../data/supabase';
import { createDemoBackend, demoRequested, setDemoFlag } from '../data/demo';
import { localClearPrefix } from '../data/safeStorage';
import { toast, ToastHost } from '../ui/Toast';
import { AskHost } from '../ui/Sheet';
import { Backdrop } from '../ui/Backdrop';

interface Props {
  /** Error from an auth redirect (expired confirmation / recovery link). */
  urlAuthError: string | null;
}

type Auth =
  | { kind: 'loading' }
  | { kind: 'signedOut' }
  | { kind: 'demo'; backend: Backend }
  | { kind: 'signedIn'; session: Session; recovery: boolean };

/** Auth gate: sign-in screen, demo mode, or the signed-in app with its store. */
export function Root({ urlAuthError }: Props) {
  const [auth, setAuth] = useState<Auth>({ kind: 'loading' });
  const [demo, setDemo] = useState(() => demoRequested());

  useEffect(() => {
    if (demo) {
      let cancelled = false;
      void createDemoBackend().then((backend) => {
        if (!cancelled) setAuth({ kind: 'demo', backend });
      });
      return () => {
        cancelled = true;
      };
    }
    const client = getSupabase();
    if (!client) {
      setAuth({ kind: 'signedOut' });
      return;
    }
    let recovery = false;
    void client.auth.getSession().then(({ data }) => {
      setAuth((cur) =>
        cur.kind === 'loading' ? (data.session ? { kind: 'signedIn', session: data.session, recovery } : { kind: 'signedOut' }) : cur,
      );
    });
    const { data } = client.auth.onAuthStateChange((event, session) => {
      if (event === 'PASSWORD_RECOVERY') recovery = true;
      if (!session) {
        recovery = false;
        setAuth({ kind: 'signedOut' });
        return;
      }
      setAuth((cur) => {
        // Keep the same object for token refreshes so the store isn't rebuilt.
        if (cur.kind === 'signedIn' && cur.session.user.id === session.user.id && cur.recovery === recovery) {
          return cur.session.access_token === session.access_token ? cur : { ...cur, session };
        }
        return { kind: 'signedIn', session, recovery };
      });
    });
    return () => data.subscription.unsubscribe();
  }, [demo]);

  const userId = auth.kind === 'signedIn' ? auth.session.user.id : auth.kind === 'demo' ? 'demo' : null;
  const email = auth.kind === 'signedIn' ? (auth.session.user.email ?? '') : 'demo@example.com';
  const demoBackend = auth.kind === 'demo' ? auth.backend : null;

  const appSession = useMemo<AppSession | null>(() => {
    if (!userId) return null;
    const client = getSupabase();
    const backend = demoBackend ?? (client ? new SupabaseBackend(client, userId) : null);
    if (!backend) return null;
    const store = new Store(backend, demoBackend ? null : `${CACHE_PREFIX}${userId}`, toast);
    const actions = createActions(store, toast);
    const signOut = async () => {
      if (demoBackend) {
        setDemoFlag(false);
        window.location.hash = '#/';
        setDemo(false);
        setAuth({ kind: 'loading' });
        return;
      }
      const { error } = await getSupabase()!.auth.signOut();
      if (error) {
        toast(`Couldn't sign out: ${error.message}`);
        return;
      }
      // The cached snapshot holds personal data: drop it.
      store.stop({ discardCache: true });
      localClearPrefix(CACHE_PREFIX);
    };
    return { mode: demoBackend ? 'demo' : 'supabase', userId, email, store, actions, signOut };
  }, [userId, email, demoBackend]);

  useEffect(() => {
    if (!appSession) return;
    appSession.store.start();
    return () => appSession.store.stop();
  }, [appSession]);

  let body: React.ReactNode;
  if (auth.kind === 'loading') body = <div className="boot" aria-busy="true" />;
  else if (auth.kind === 'signedIn' && auth.recovery) {
    body = (
      <NewPasswordScreen
        email={email}
        onDone={() => setAuth({ ...auth, recovery: false })}
      />
    );
  } else if (appSession) {
    body = (
      <SessionContext.Provider value={appSession}>
        <AppRoutes />
      </SessionContext.Provider>
    );
  } else {
    body = (
      <AuthScreen
        urlAuthError={urlAuthError}
        onDemo={() => {
          setDemoFlag(true);
          setDemo(true);
        }}
      />
    );
  }

  return (
    <>
      <Backdrop />
      {body}
      <AskHost />
      <ToastHost />
    </>
  );
}
