import { useState, type FormEvent } from 'react';
import { CircleCheck, Mail, Sparkles } from 'lucide-react';
import { backendConfigured } from '../config';
import { getSupabase, redirectUrl } from '../data/supabase';
import { Seg } from '../ui/Seg';
import { toast } from '../ui/Toast';
import { Field } from '../ui/controls';
import { BrandMark } from '../ui/Brand';
import { SIGN_IN_HEADLINE } from '../domain/format';

type Mode = 'signin' | 'signup' | 'reset';
type Done = { kind: 'confirm' | 'reset'; email: string } | null;

export function AuthScreen({ urlAuthError, onDemo }: { urlAuthError: string | null; onDemo: () => void }) {
  const [mode, setMode] = useState<Mode>('signin');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(urlAuthError);
  const [done, setDone] = useState<Done>(null);
  const client = getSupabase();

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!client) return;
    setBusy(true);
    setError(null);
    try {
      if (mode === 'signin') {
        const { error: err } = await client.auth.signInWithPassword({ email: email.trim(), password });
        if (err) setError(err.message === 'Email not confirmed' ? 'Confirm your email first: check your inbox for the link.' : err.message);
      } else if (mode === 'signup') {
        const { data, error: err } = await client.auth.signUp({
          email: email.trim(),
          password,
          options: { emailRedirectTo: redirectUrl() },
        });
        if (err) setError(err.message);
        else if (!data.session) {
          // With email confirmation on, an existing address returns a user without identities.
          if (data.user && data.user.identities?.length === 0) setError('An account with this email already exists. Sign in instead.');
          else setDone({ kind: 'confirm', email: email.trim() });
        }
      } else {
        const { error: err } = await client.auth.resetPasswordForEmail(email.trim(), { redirectTo: redirectUrl() });
        if (err) setError(err.message);
        else setDone({ kind: 'reset', email: email.trim() });
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Network error. Check your connection.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth">
      <div className="auth-card card panel on">
        <div className="auth-brand">
          <BrandMark size={32} />
          <h1 className="title">{SIGN_IN_HEADLINE}</h1>
          {mode === 'reset' && <h2 className="card-title">Reset your password</h2>}
        </div>

        {!backendConfigured && (
          <div className="notice" role="note">
            <strong>Backend not configured.</strong> Add the Supabase URL and publishable key to{' '}
            <code>config/supabase.json</code> (or set <code>VITE_SUPABASE_URL</code> /{' '}
            <code>VITE_SUPABASE_PUBLISHABLE_KEY</code>). Until then you can explore the app with demo data.
          </div>
        )}

        {done ? (
          <div className="auth-done arrive">
            {done.kind === 'confirm' ? <Mail size={28} strokeWidth={1.75} /> : <CircleCheck size={28} strokeWidth={1.75} />}
            <h2>Check your email</h2>
            <p>
              {done.kind === 'confirm'
                ? <>We sent a confirmation link to <strong>{done.email}</strong>. Open it to finish creating your account, then sign in.</>
                : <>We sent a password reset link to <strong>{done.email}</strong>. Open it on this device to choose a new password.</>}
            </p>
            <button type="button" className="btn" onClick={() => { setDone(null); setMode('signin'); }}>
              Back to sign in
            </button>
          </div>
        ) : (
          <form className="auth-form" onSubmit={submit}>
            {mode !== 'reset' && (
              <Seg
                label="Account"
                value={mode}
                onChange={(m) => { setMode(m); setError(null); }}
                options={[{ value: 'signin', label: 'Sign in' }, { value: 'signup', label: 'Create account' }]}
              />
            )}
            <Field label="Email">
              {(id) => (
                <input id={id} className="input" type="email" autoComplete="email" required disabled={!backendConfigured}
                  value={email} onChange={(e) => setEmail(e.target.value)} />
              )}
            </Field>
            {mode !== 'reset' && (
              <Field label="Password" hint={mode === 'signup' ? 'At least 6 characters.' : undefined}>
                {(id) => (
                  <input id={id} className="input" type="password" required minLength={6} disabled={!backendConfigured}
                    autoComplete={mode === 'signup' ? 'new-password' : 'current-password'}
                    value={password} onChange={(e) => setPassword(e.target.value)} />
                )}
              </Field>
            )}
            {error && <div className="form-error" role="alert">{error}</div>}
            <button type="submit" className="btn primary block" disabled={busy || !backendConfigured}>
              {busy ? 'Please wait…' : mode === 'signin' ? 'Sign in' : mode === 'signup' ? 'Create account' : 'Send reset link'}
            </button>
            <div className="auth-links">
              {mode === 'reset' ? (
                <button type="button" className="link" onClick={() => { setMode('signin'); setError(null); }}>Back to sign in</button>
              ) : (
                <button type="button" className="link" disabled={!backendConfigured} onClick={() => { setMode('reset'); setError(null); }}>
                  Forgot password?
                </button>
              )}
            </div>
          </form>
        )}

        <div className="auth-demo">
          <button type="button" className={`btn block${backendConfigured ? '' : ' primary'}`} onClick={onDemo}>
            <Sparkles size={16} strokeWidth={1.75} /> Try demo mode
          </button>
          <p className="muted small">Synthetic sample data, kept in memory. Nothing is saved.</p>
        </div>
      </div>
    </div>
  );
}

/** Shown after opening a password-recovery link: the user is signed in and picks a new password. */
export function NewPasswordScreen({ email, onDone }: { email: string; onDone: () => void }) {
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (password !== confirm) {
      setError("The passwords don't match.");
      return;
    }
    setBusy(true);
    setError(null);
    const { error: err } = await getSupabase()!.auth.updateUser({ password });
    setBusy(false);
    if (err) setError(err.message);
    else {
      toast('Password updated');
      onDone();
    }
  };

  return (
    <div className="auth">
      <div className="auth-card card panel on">
        <div className="auth-brand">
          <BrandMark size={32} />
          <h1 className="title">Choose a new password</h1>
        </div>
        <p className="muted">Choose a new password for <strong>{email}</strong>.</p>
        <form className="auth-form" onSubmit={submit}>
          <Field label="New password" hint="At least 6 characters.">
            {(id) => (
              <input id={id} className="input" type="password" autoComplete="new-password" required minLength={6}
                value={password} onChange={(e) => setPassword(e.target.value)} data-autofocus />
            )}
          </Field>
          <Field label="Repeat password">
            {(id) => (
              <input id={id} className="input" type="password" autoComplete="new-password" required minLength={6}
                value={confirm} onChange={(e) => setConfirm(e.target.value)} />
            )}
          </Field>
          {error && <div className="form-error" role="alert">{error}</div>}
          <button type="submit" className="btn primary block" disabled={busy}>
            {busy ? 'Saving…' : 'Save password'}
          </button>
        </form>
      </div>
    </div>
  );
}
