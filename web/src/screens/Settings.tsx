import { useMemo, useRef, useState, type FormEvent } from 'react';
import {
  Archive, ArchiveRestore, ArrowDown, ArrowUp, Download, LogOut, Pencil, Plus, RefreshCw, Trash2, Upload,
} from 'lucide-react';
import { Page } from '../app/Shell';
import { useActions, useData, useSession, useStoreState } from '../app/session';
import { Seg } from '../ui/Seg';
import { Sheet, ask } from '../ui/Sheet';
import { Menu } from '../ui/Menu';
import { Field, MoneyField, Switch } from '../ui/controls';
import { FlipList } from '../ui/FlipList';
import { CardHead, CategoryDot, Empty, TrackingIcon, useMoney } from '../ui/bits';
import { toast } from '../ui/Toast';
import { getThemePref, setThemePref, type ThemePref } from '../ui/theme';
import { McpSection } from './McpSection';
import { APP_VERSION } from '../config';
import { BackupError, buildBackup, describeImport, parseBackup, planImport } from '../domain/backup';
import { byOrder, KIND_LABEL, KIND_ORDER } from '../domain/calc';
import { formatNumber, parseAmount } from '../domain/format';
import type { Category, CategoryKind, RecurringItem, Tracking } from '../domain/types';

export function SettingsScreen() {
  return (
    <Page label="Budget" title="Settings">
      <div className="settings-grid">
        <AccountCard />
        <IncomeCard />
        <CategoriesCard />
        <RecurringCard />
        <McpSection />
        <AppearanceCard />
        <DataCard />
        <AboutCard />
      </div>
    </Page>
  );
}

function AccountCard() {
  const { email, mode, signOut } = useSession();
  return (
    <section className="card settings-card">
      <CardHead title="Account" />
      <div className="setting-row">
        <div className="setting-text">
          <div className="setting-label">{mode === 'demo' ? 'Demo mode' : 'Signed in as'}</div>
          <div className={`muted${mode === 'demo' ? ' small' : ' ellipsis'}`}>{mode === 'demo' ? 'Synthetic data kept in memory. Nothing is saved.' : email}</div>
        </div>
        <button
          type="button"
          className="btn"
          onClick={async () => {
            const yes = await ask(
              mode === 'demo'
                ? { title: 'Exit demo mode?', body: 'Demo changes are discarded.', yes: 'Exit demo' }
                : { title: 'Sign out?', body: 'The cached copy of your budget is removed from this browser.', yes: 'Sign out' },
            );
            if (yes) await signOut();
          }}
        >
          <LogOut size={16} strokeWidth={1.75} /> {mode === 'demo' ? 'Exit demo' : 'Sign out'}
        </button>
      </div>
    </section>
  );
}

function IncomeCard() {
  const { calc } = useData();
  const actions = useActions();
  const s = calc.settings;
  const save = (patch: Partial<typeof s>) => void actions.updateSettings(patch);
  return (
    <section className="card settings-card">
      <CardHead title="Income" />
      <div className="setting-row">
        <label className="setting-text" htmlFor="set-net">
          <div className="setting-label">Annual net income</div>
          <div className="muted small">Take-home pay, for “% of net income”.</div>
        </label>
        <MoneyField id="set-net" className="setting-input" label="Annual net income" value={s.net_income} nullable={false}
          onCommit={(v) => v && v > 0 && save({ net_income: v })} />
      </div>
      <div className="setting-row">
        <label className="setting-text" htmlFor="set-gross">
          <div className="setting-label">Annual gross income</div>
          <div className="muted small">Before tax, for “% of gross income”.</div>
        </label>
        <MoneyField id="set-gross" className="setting-input" label="Annual gross income" value={s.gross_income} nullable={false}
          onCommit={(v) => v && v > 0 && save({ gross_income: v })} />
      </div>
    </section>
  );
}

// ---- categories ----

function CategoriesCard() {
  const { calc } = useData();
  const actions = useActions();
  const [sheet, setSheet] = useState<Category | null>(null);
  const active = calc.categories.filter((c) => !c.archived);
  const archived = calc.categories.filter((c) => c.archived);

  return (
    <section className="card settings-card">
      <CardHead title="Categories">
        <button type="button" className="btn" onClick={() => setSheet(actions.newCategory('expense', ''))}>
          <Plus size={16} strokeWidth={1.75} /> Category
        </button>
      </CardHead>
      {KIND_ORDER.map((kind) => {
        const group = active.filter((c) => c.kind === kind).sort(byOrder);
        if (group.length === 0) return null;
        return (
          <div key={kind} className="cat-group">
            <div className="micro group-label">{KIND_LABEL[kind]}</div>
            <FlipList className="rows" signature={group.map((c) => c.id).join()}>
              {group.map((c, i) => (
                <div key={c.id} data-k={c.id} className="row row-static cat-row">
                  <button type="button" className="cat-main" onClick={() => setSheet(c)}>
                    <CategoryDot category={c} />
                    <span className="ellipsis">{c.name}</span>
                    <span className="cat-meta muted small">
                      <TrackingIcon category={c} /> {c.tracking}{c.match_multiplier !== 1 ? ` · ×${formatNumber(c.match_multiplier)}` : ''}
                    </span>
                  </button>
                  <button type="button" className="icon-btn sm" aria-label={`Move ${c.name} up`} disabled={i === 0}
                    onClick={() => void actions.moveCategory(c, -1)}>
                    <ArrowUp size={16} strokeWidth={1.75} />
                  </button>
                  <button type="button" className="icon-btn sm" aria-label={`Move ${c.name} down`} disabled={i === group.length - 1}
                    onClick={() => void actions.moveCategory(c, 1)}>
                    <ArrowDown size={16} strokeWidth={1.75} />
                  </button>
                  <Menu label={`${c.name} options`} items={[
                    { label: 'Edit…', icon: <Pencil size={16} strokeWidth={1.75} />, onSelect: () => setSheet(c) },
                    {
                      label: 'Archive', icon: <Archive size={16} strokeWidth={1.75} />,
                      onSelect: async () => {
                        if (await actions.saveCategory({ ...c, archived: true })) toast(`${c.name} archived: hidden from new months`);
                      },
                    },
                    {
                      label: 'Delete', warn: true, separatorBefore: true, icon: <Trash2 size={16} strokeWidth={1.75} />,
                      onSelect: async () => {
                        if (actions.categoryHasData(c.id)) {
                          toast(`${c.name} has data. Archive it instead.`);
                          return;
                        }
                        if (await ask({ title: `Delete ${c.name}?`, yes: 'Delete', danger: true })) {
                          if (await actions.deleteCategory(c)) toast(`${c.name} deleted`);
                        }
                      },
                    },
                  ]} />
                </div>
              ))}
            </FlipList>
          </div>
        );
      })}
      {archived.length > 0 && (
        <details className="archived">
          <summary className="micro">Archived ({archived.length})</summary>
          <div className="rows">
            {archived.map((c) => (
              <div key={c.id} className="row row-static cat-row faded">
                <span className="cat-main static"><CategoryDot category={c} /><span className="ellipsis">{c.name}</span></span>
                <button type="button" className="icon-btn sm" aria-label={`Restore ${c.name}`} title="Restore"
                  onClick={() => void actions.saveCategory({ ...c, archived: false })}>
                  <ArchiveRestore size={16} strokeWidth={1.75} />
                </button>
              </div>
            ))}
          </div>
        </details>
      )}
      <CategorySheet category={sheet} onClose={() => setSheet(null)} />
    </section>
  );
}

function CategorySheet({ category, onClose }: { category: Category | null; onClose: () => void }) {
  const { calc, ds } = useData();
  const actions = useActions();
  const [draft, setDraft] = useState<Category | null>(category);
  const [shown, setShown] = useState<Category | null>(category);
  const [mult, setMult] = useState('1');
  if (category !== shown) {
    setShown(category);
    if (category) {
      setDraft(category);
      setMult(String(category.match_multiplier));
    }
  }
  const isNew = draft ? !ds.categories.some((c) => c.id === draft.id) : false;
  const set = (patch: Partial<Category>) => setDraft((d) => (d ? { ...d, ...patch } : d));
  const name = draft?.name.trim() ?? '';
  const duplicate = calc.categories.some((c) => c.id !== draft?.id && c.name.trim().toLowerCase() === name.toLowerCase());
  const multValue = parseAmount(mult);
  const valid = Boolean(name) && !duplicate && multValue !== null && !Number.isNaN(multValue) && multValue > 0;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!draft || !valid) return;
    let row: Category = { ...draft, name, match_multiplier: draft.kind === 'savings' ? multValue! : 1 };
    if (isNew) row = { ...actions.newCategory(row.kind, name), tracking: row.tracking, match_multiplier: row.match_multiplier, id: row.id };
    onClose();
    if (await actions.saveCategory(row)) toast(isNew ? `${name} added` : `${name} saved`);
  };

  return (
    <Sheet open={category != null} onClose={onClose} title={isNew ? 'New category' : 'Edit category'}
      footer={<button type="submit" form="cat-form" className="btn primary lg grow" disabled={!valid}>Save</button>}>
      {draft && (
        <form id="cat-form" className="form-stack" onSubmit={submit}>
          <Field label="Name" hint={duplicate ? 'A category with this name already exists.' : undefined}>
            {(id) => <input id={id} className="input" value={draft.name} data-autofocus onChange={(e) => set({ name: e.target.value })} />}
          </Field>
          <div className="field">
            <div className="field-label">Kind</div>
            <Seg<CategoryKind> className="fill" label="Kind" value={draft.kind} onChange={(kind) => set({ kind })}
              options={KIND_ORDER.map((k) => ({ value: k, label: KIND_LABEL[k] }))} />
          </div>
          <div className="field">
            <div className="field-label">Tracking</div>
            <Seg<Tracking> className="fill" label="Tracking" value={draft.tracking} onChange={(tracking) => set({ tracking })}
              options={[{ value: 'ledger', label: 'Ledger' }, { value: 'manual', label: 'Manual' }]} />
            <div className="field-hint">
              {draft.tracking === 'ledger' ? 'Actual = sum of the transactions (an override can be typed in).' : 'Actual is typed in each month.'}
            </div>
          </div>
          {draft.kind === 'savings' && (
            <Field label="Match multiplier" hint="2 for a 401k with a 100% employer match: the match counts as saved.">
              {(id) => <input id={id} className="input money" inputMode="decimal" value={mult} onChange={(e) => setMult(e.target.value)} />}
            </Field>
          )}
        </form>
      )}
    </Sheet>
  );
}

// ---- recurring ----

function RecurringCard() {
  const { ds, calc } = useData();
  const actions = useActions();
  const money = useMoney();
  const [sheet, setSheet] = useState<RecurringItem | null>(null);
  const items = useMemo(() => ds.recurring_items.slice().sort((a, b) => a.sort_order - b.sort_order), [ds.recurring_items]);
  const firstLedger = calc.categories.find((c) => !c.archived && c.tracking === 'ledger' && c.kind === 'expense') ?? calc.categories[0];

  return (
    <section className="card settings-card">
      <CardHead title="Recurring">
        <button type="button" className="btn" disabled={!firstLedger} onClick={() => firstLedger && setSheet(actions.newRecurring(firstLedger.id))}>
          <Plus size={16} strokeWidth={1.75} /> Item
        </button>
      </CardHead>
      <p className="muted small card-note">Subscriptions and other items added to every new month.</p>
      {items.length === 0 ? (
        <Empty title="No recurring items" />
      ) : (
        <FlipList className="rows" signature={items.map((r) => r.id).join()}>
          {items.map((r) => {
            const c = calc.categoryById.get(r.category_id);
            return (
              <div key={r.id} data-k={r.id} className={`row row-static rec-row${r.active ? '' : ' faded'}`}>
                <button type="button" className="rec-main" onClick={() => setSheet(r)}>
                  <span className="ellipsis rec-name">{r.item || 'Untitled'}</span>
                  <span className="muted small ellipsis">
                    {c && <><CategoryDot category={c} />{c.name} · </>}{r.day_of_month ? `day ${r.day_of_month}` : 'no date'}
                  </span>
                </button>
                <span className="rec-amount">{money(r.amount)}</span>
                <Switch checked={r.active} label={`${r.item} active`} onChange={(active) => void actions.saveRecurring({ ...r, active })} />
              </div>
            );
          })}
        </FlipList>
      )}
      <RecurringSheet item={sheet} onClose={() => setSheet(null)} />
    </section>
  );
}

function RecurringSheet({ item, onClose }: { item: RecurringItem | null; onClose: () => void }) {
  const { ds, calc } = useData();
  const actions = useActions();
  const [draft, setDraft] = useState<RecurringItem | null>(item);
  const [day, setDay] = useState('');
  const [shown, setShown] = useState<RecurringItem | null>(item);
  if (item !== shown) {
    setShown(item);
    if (item) {
      setDraft(item);
      setDay(item.day_of_month == null ? '' : String(item.day_of_month));
    }
  }
  const isNew = draft ? !ds.recurring_items.some((r) => r.id === draft.id) : false;
  const set = (patch: Partial<RecurringItem>) => setDraft((d) => (d ? { ...d, ...patch } : d));
  const dayNum = day.trim() === '' ? null : Number(day);
  const dayValid = dayNum === null || (Number.isInteger(dayNum) && dayNum >= 1 && dayNum <= 31);
  const valid = Boolean(draft?.item.trim()) && dayValid;
  const categories = calc.categories.filter((c) => !c.archived || c.id === draft?.category_id);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!draft || !valid) return;
    onClose();
    if (await actions.saveRecurring({ ...draft, item: draft.item.trim(), day_of_month: dayNum })) toast(isNew ? 'Recurring item added' : 'Saved');
  };
  const remove = async () => {
    if (!draft) return;
    onClose();
    if (await actions.deleteRecurring(draft)) toast('Recurring item deleted');
  };

  return (
    <Sheet open={item != null} onClose={onClose} title={isNew ? 'New recurring item' : 'Edit recurring item'}
      footer={
        <div className="tx-actions">
          {!isNew && <button type="button" className="btn danger" onClick={remove}><Trash2 size={16} strokeWidth={1.75} /> Delete</button>}
          <button type="submit" form="rec-form" className="btn primary lg grow" disabled={!valid}>Save</button>
        </div>
      }>
      {draft && (
        <form id="rec-form" className="form-stack" onSubmit={submit}>
          <Field label="Item">
            {(id) => <input id={id} className="input" value={draft.item} data-autofocus placeholder="Streaming" onChange={(e) => set({ item: e.target.value })} />}
          </Field>
          <Field label="Category">
            {(id) => (
              <select id={id} className="input" value={draft.category_id} onChange={(e) => set({ category_id: e.target.value })}>
                {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            )}
          </Field>
          <div className="field-row">
            <Field label="Amount">
              {(id) => <MoneyField id={id} label="Amount" value={draft.amount} nullable={false} onCommit={(v) => set({ amount: v ?? 0 })} />}
            </Field>
            <Field label="Day of month" hint={dayValid ? 'Clamped to short months.' : '1 to 31, or blank.'}>
              {(id) => <input id={id} className={`input${dayValid ? '' : ' invalid'}`} inputMode="numeric" value={day} placeholder="None" onChange={(e) => setDay(e.target.value)} />}
            </Field>
          </div>
          <label className="switch-row">
            <span>Active</span>
            <Switch checked={draft.active} onChange={(active) => set({ active })} label="Active" />
          </label>
        </form>
      )}
    </Sheet>
  );
}

// ---- appearance, data, about ----

function AppearanceCard() {
  const [theme, setTheme] = useState<ThemePref>(getThemePref);
  return (
    <section className="card settings-card">
      <CardHead title="Appearance" />
      <div className="setting-row">
        <div className="setting-text"><div className="setting-label">Theme</div></div>
        <Seg<ThemePref> label="Theme" value={theme}
          onChange={(t) => { setTheme(t); setThemePref(t); }}
          options={[{ value: 'system', label: 'System' }, { value: 'light', label: 'Light' }, { value: 'dark', label: 'Dark' }]} />
      </div>
    </section>
  );
}

function DataCard() {
  const { store, mode } = useSession();
  const { lastSync, status } = useStoreState();
  const { ds } = useData();
  const actions = useActions();
  const fileRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);

  const exportJson = () => {
    const file = buildBackup(ds);
    const blob = new Blob([JSON.stringify(file, null, 1)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `budget-backup-${file.exported_at.slice(0, 10)}.json`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    toast('Backup downloaded');
  };

  const importJson = async (f: File) => {
    try {
      const file = parseBackup(JSON.parse(await f.text()));
      // Plan against the server's current rows, never a cache or a half-finished first load:
      // categories are matched by name, so planning against an empty list would duplicate them.
      if (mode !== 'demo') {
        setBusy(true);
        await store.refresh(true);
        setBusy(false);
        const st = store.getState();
        if (!st.loaded || st.status === 'offline') {
          toast("Couldn't load your current data, so nothing was imported. Check your connection and try again.");
          return;
        }
      }
      const plan = planImport(store.dataset(), file);
      const yes = await ask({
        title: 'Import backup?',
        body: (
          <>
            <p>{describeImport(plan)}</p>
            <p className="muted small">
              {plan.matchedCategories} {plan.matchedCategories === 1 ? 'category matches' : 'categories match'} existing ones by name. Nothing is deleted.
            </p>
          </>
        ),
        yes: 'Import',
      });
      if (!yes) return;
      setBusy(true);
      // Re-plan in case data changed (e.g. realtime) while the dialog was open.
      const ok = await actions.importBackup(planImport(store.dataset(), file));
      toast(ok ? 'Backup imported' : 'Import finished with errors');
    } catch (e) {
      toast(e instanceof BackupError ? e.message : e instanceof SyntaxError ? 'That file is not valid JSON.' : 'Could not read the file.');
    } finally {
      setBusy(false);
      if (fileRef.current) fileRef.current.value = '';
    }
  };

  return (
    <section className="card settings-card">
      <CardHead title="Data" />
      <div className="setting-row">
        <div className="setting-text">
          <div className="setting-label">Backup</div>
          <div className="muted small">JSON in the shared format. Import merges, it never wipes.</div>
        </div>
        <div className="btn-row">
          <button type="button" className="btn" onClick={exportJson}><Download size={16} strokeWidth={1.75} /> Export</button>
          <button type="button" className="btn" disabled={busy} onClick={() => fileRef.current?.click()}>
            <Upload size={16} strokeWidth={1.75} /> Import
          </button>
          <input ref={fileRef} type="file" accept="application/json,.json" hidden
            onChange={(e) => { const f = e.target.files?.[0]; if (f) void importJson(f); }} />
        </div>
      </div>
      <div className="setting-row">
        <div className="setting-text">
          <div className="setting-label">Sync</div>
          <div className="muted small">
            {mode === 'demo' ? 'Demo mode: nothing is synced.'
              : status === 'syncing' ? 'Syncing…'
              : lastSync ? `Last synced ${new Date(lastSync).toLocaleString()}` : 'Not synced yet'}
          </div>
        </div>
        <button type="button" className="btn" disabled={mode === 'demo' || status === 'syncing'} onClick={() => void store.refresh(false)}>
          <RefreshCw size={16} strokeWidth={1.75} className={status === 'syncing' ? 'spin' : ''} /> Sync now
        </button>
      </div>
    </section>
  );
}

function AboutCard() {
  return (
    <section className="card settings-card">
      <CardHead title="About" />
      <div className="setting-row">
        <div className="setting-text">
          <div className="setting-label">Budget for web</div>
          <div className="muted small">Version {APP_VERSION}</div>
        </div>
      </div>
    </section>
  );
}
