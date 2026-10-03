import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Bot, Check, Copy, Plus, TriangleAlert } from 'lucide-react';
import { useSession } from '../app/session';
import { createToken, listTokens, revokeToken, type McpToken } from '../data/mcp';
import { Sheet, ask } from '../ui/Sheet';
import { Field } from '../ui/controls';
import { FlipList } from '../ui/FlipList';
import { CardHead, Empty } from '../ui/bits';
import { toast } from '../ui/Toast';

const dateLabel = (iso: string | null) =>
  iso ? new Date(iso).toLocaleDateString([], { year: 'numeric', month: 'short', day: 'numeric' }) : 'Never';

/**
 * "AI assistant (MCP)": personal connector URLs for the budget-mcp Edge Function.
 * Tokens live in public.mcp_tokens (not synced, not exported) and load only when Settings opens.
 */
export function McpSection() {
  const { mode, userId } = useSession();
  const enabled = mode === 'supabase';
  const [tokens, setTokens] = useState<McpToken[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [naming, setNaming] = useState(false);
  const [name, setName] = useState('Claude');
  const [busy, setBusy] = useState(false);
  const [reveal, setReveal] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const load = useCallback(async () => {
    try {
      setTokens(await listTokens());
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, []);

  useEffect(() => {
    if (enabled) void load();
  }, [enabled, load]);

  const create = async (e: FormEvent) => {
    e.preventDefault();
    const n = name.trim() || 'Claude';
    setBusy(true);
    try {
      const { token, url } = await createToken(userId, n);
      setTokens((t) => [token, ...(t ?? [])]);
      setNaming(false);
      setCopied(false);
      setReveal(url);
    } catch (err) {
      toast(`Couldn't create the connection: ${err instanceof Error ? err.message : String(err)}`);
    } finally {
      setBusy(false);
    }
  };

  const revoke = async (t: McpToken) => {
    const yes = await ask({
      title: `Revoke ${t.name}?`,
      body: 'Assistants using this connector URL lose access immediately. This cannot be undone.',
      yes: 'Revoke',
      danger: true,
    });
    if (!yes) return;
    try {
      await revokeToken(t.id);
      setTokens((list) => (list ?? []).filter((x) => x.id !== t.id));
      toast(`${t.name} revoked`);
    } catch (err) {
      toast(`Couldn't revoke: ${err instanceof Error ? err.message : String(err)}`);
    }
  };

  const copy = async () => {
    if (!reveal) return;
    try {
      await navigator.clipboard.writeText(reveal);
      setCopied(true);
      toast('Connector URL copied');
    } catch {
      toast('Copy failed: select the URL and copy it manually');
    }
  };

  return (
    <section className={`card settings-card${enabled ? '' : ' disabled-card'}`} aria-disabled={!enabled}>
      <CardHead title="AI assistant (MCP)">
        <button type="button" className="btn" disabled={!enabled} onClick={() => { setName('Claude'); setNaming(true); }}>
          <Plus size={16} strokeWidth={1.75} /> Connection
        </button>
      </CardHead>
      <p className="muted small card-note">
        Connect Claude or another AI agent to your budget through a personal connector URL. It can read and edit your data.
      </p>
      {!enabled ? (
        <div className="notice subtle">Available when signed in.</div>
      ) : error ? (
        <div className="form-error" role="alert">
          Couldn't load connections: {error}{' '}
          <button type="button" className="link" onClick={() => void load()}>Retry</button>
        </div>
      ) : tokens === null ? (
        <div className="muted small loading-row">Loading…</div>
      ) : tokens.length === 0 ? (
        <Empty title="No connections" icon={<Bot size={24} strokeWidth={1.5} />} />
      ) : (
        <FlipList className="rows" signature={tokens.map((t) => t.id).join()}>
          {tokens.map((t) => (
            <div key={t.id} data-k={t.id} className="row row-static mcp-row">
              <div className="mcp-main">
                <span className="setting-label ellipsis">{t.name}</span>
                <span className="muted small">
                  …{t.token_hint ?? '????'} · created {dateLabel(t.created_at)} · last used {dateLabel(t.last_used_at)}
                </span>
              </div>
              <button type="button" className="btn danger" onClick={() => void revoke(t)}>Revoke</button>
            </div>
          ))}
        </FlipList>
      )}

      <Sheet open={naming} onClose={() => setNaming(false)} title="New connection"
        footer={
          <>
            <button type="button" className="btn" onClick={() => setNaming(false)}>Cancel</button>
            <button type="submit" form="mcp-form" className="btn primary" disabled={busy}>{busy ? 'Creating…' : 'Create'}</button>
          </>
        }>
        <form id="mcp-form" className="form-stack" onSubmit={create}>
          <Field label="Name" hint="So you can recognise it later, e.g. the assistant or device.">
            {(id) => <input id={id} className="input" value={name} data-autofocus maxLength={60} onChange={(e) => setName(e.target.value)} />}
          </Field>
        </form>
      </Sheet>

      <Sheet open={reveal != null} onClose={() => setReveal(null)} title="Connector URL" wide
        footer={
          <>
            <button type="button" className="btn" onClick={() => setReveal(null)}>Done</button>
            <button type="button" className="btn primary" onClick={() => void copy()}>
              {copied ? <Check size={16} strokeWidth={2} /> : <Copy size={16} strokeWidth={1.75} />} {copied ? 'Copied' : 'Copy'}
            </button>
          </>
        }>
        <div className="form-stack">
          <div className="notice warn-notice">
            <TriangleAlert size={16} strokeWidth={1.75} />
            <span>This URL is shown only once. Copy it now.</span>
          </div>
          <textarea className="input connector-url" readOnly rows={3} value={reveal ?? ''} aria-label="Connector URL"
            onFocus={(e) => e.currentTarget.select()} />
          <p className="small">
            In Claude: <strong>Settings → Connectors → Add custom connector</strong>, then paste this URL.
          </p>
          <p className="muted small">
            Anyone with this URL can read and edit your budget, so keep it private. You can revoke it here anytime.
          </p>
        </div>
      </Sheet>
    </section>
  );
}
