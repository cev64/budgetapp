import { useMemo, useState, type FormEvent } from 'react';
import { Archive, ArchiveRestore, ChartLine, Check, Landmark, Pencil, Plus, Trash2 } from 'lucide-react';
import { Page } from '../app/Shell';
import { useActions, useData } from '../app/session';
import { Num } from '../ui/Num';
import { Sheet, ask } from '../ui/Sheet';
import { Menu } from '../ui/Menu';
import { Field, MoneyField, Switch } from '../ui/controls';
import { FlipList } from '../ui/FlipList';
import { CardHead, Empty, useMoney } from '../ui/bits';
import { toast } from '../ui/Toast';
import { haptic } from '../ui/motion';
import { computeNetWorth, GROUP_LABEL, GROUPS, type AccountView } from '../domain/networth';
import { amountInputText, parseAmount } from '../domain/format';
import { byOrder } from '../domain/calc';
import type { Account, AccountGroup, LedgerEntry } from '../domain/types';
import { changeOver, groupSeries, seriesOf } from '../domain/history';
import { AccountHistorySheet, ChangeLine, NetWorthHero, useSnapshots } from './NetWorthHistory';

export function NetWorthScreen() {
  const { ds } = useData();
  const actions = useActions();
  const money = useMoney();
  const nw = useMemo(
    () => computeNetWorth({ accounts: ds.accounts, categories: ds.categories, budgets: ds.budgets, ledger: ds.ledger_entries }),
    [ds],
  );
  const archived = ds.accounts.filter((a) => a.archived).sort(byOrder);
  const ledger = ds.ledger_entries.slice().sort(byOrder);
  const [accountSheet, setAccountSheet] = useState<Account | null>(null);
  const [ledgerSheet, setLedgerSheet] = useState<LedgerEntry | null>(null);
  const [historyOf, setHistoryOf] = useState<string | null>(null);
  const snaps = useSnapshots();
  const currency = useData().calc.settings.currency;
  const historyView = historyOf ? nw.accounts.find((v) => v.account.id === historyOf) ?? null : null;

  const toggleSettled = async (e: LedgerEntry) => {
    if (await actions.saveLedgerEntry({ ...e, settled: !e.settled })) haptic(9);
  };

  return (
    <Page label="Budget" title="Net worth">
      <div className="nw-grid">
        <div className="nw-top">
          <NetWorthHero value={nw.netWorth} money={money} />
          <div className="tiles three">
            <div className="tile card">
              <div className="micro">Super liquid</div>
              <div className="tile-value"><Num value={nw.superLiquid} format={money} /></div>
              <div className="tile-sub"><ChangeLine change={changeOver(seriesOf(snaps, 'super_liquid'), 30)} label="30d" currency={currency} /></div>
            </div>
            <div className="tile card">
              <div className="micro">Investments</div>
              <div className="tile-value"><Num value={nw.investments} format={money} /></div>
              <div className="tile-sub"><ChangeLine change={changeOver(groupSeries(snaps, 'investment'), 30)} label="30d" currency={currency} /></div>
            </div>
            <div className="tile card">
              <div className="micro">Net reconciliations</div>
              <div className={`tile-value ${nw.netReconciliations < 0 ? 'tone-bad' : nw.netReconciliations > 0 ? 'tone-good' : ''}`}><Num value={nw.netReconciliations} format={money} /></div>
              <div className="tile-sub"><ChangeLine change={changeOver(seriesOf(snaps, 'reconciliations'), 30)} label="30d" currency={currency} /></div>
            </div>
          </div>
        </div>

        <section className="card">
          <CardHead title="Accounts">
            <button type="button" className="btn" onClick={() => setAccountSheet(actions.newAccount())}>
              <Plus size={16} strokeWidth={1.75} /> Account
            </button>
          </CardHead>
          {nw.accounts.length === 0 && <Empty title="No accounts yet" icon={<Landmark size={24} strokeWidth={1.5} />} />}
          {GROUPS.map((g) => nw.byGroup[g].length > 0 && (
            <div key={g} className="acct-group">
              <div className="micro group-label">{GROUP_LABEL[g]}</div>
              <div className="rows">
                {nw.byGroup[g].map((v) => (
                  <AccountRow key={v.account.id} view={v} onEdit={() => setAccountSheet(v.account)} onHistory={() => setHistoryOf(v.account.id)} />
                ))}
              </div>
            </div>
          ))}
          {archived.length > 0 && (
            <details className="archived">
              <summary className="micro">Archived ({archived.length})</summary>
              <div className="rows">
                {archived.map((a) => (
                  <div key={a.id} className="row row-static acct-row faded">
                    <span className="acct-name ellipsis">{a.name}</span>
                    <span className="acct-balance">{money(a.balance)}</span>
                    <button type="button" className="icon-btn sm" aria-label={`Restore ${a.name}`} title="Restore"
                      onClick={() => void actions.saveAccount({ ...a, archived: false })}>
                      <ArchiveRestore size={16} strokeWidth={1.75} />
                    </button>
                  </div>
                ))}
              </div>
            </details>
          )}
        </section>

        <section className="card">
          <CardHead title="Ledger">
            <button type="button" className="btn" onClick={() => setLedgerSheet(actions.newLedgerEntry())}>
              <Plus size={16} strokeWidth={1.75} /> Entry
            </button>
          </CardHead>
          {ledger.length === 0 ? (
            <Empty title="No IOUs">Positive = owed to you, negative = you owe.</Empty>
          ) : (
            <FlipList className="rows" signature={ledger.map((e) => e.id).join()}>
              {ledger.map((e) => (
                <div key={e.id} data-k={e.id} className={`row ledger-row${e.settled ? ' settled' : ''}`}>
                  <button type="button" role="checkbox" aria-checked={e.settled} aria-label={`Settled: ${e.name}`}
                    className={`check${e.settled ? ' on' : ''}`} onClick={() => void toggleSettled(e)}>
                    {e.settled && <Check size={14} strokeWidth={2.5} className="check-icon" />}
                  </button>
                  <button type="button" className="ledger-main" onClick={() => setLedgerSheet(e)}>
                    <span className="ledger-name ellipsis">{e.name || 'Untitled'}</span>
                    {e.note && <span className="muted small ellipsis">{e.note}</span>}
                  </button>
                  <span className={`ledger-amount ${e.amount > 0 ? 'tone-good' : e.amount < 0 ? 'tone-bad' : ''}`}>{money(e.amount)}</span>
                </div>
              ))}
            </FlipList>
          )}
          <div className="num-row totals-row simple">
            <span className="num-name">Net reconciliations (open)</span>
            <span className="num-cell"><Num value={nw.netReconciliations} format={money} /></span>
          </div>
        </section>
      </div>

      <AccountSheet account={accountSheet} onClose={() => setAccountSheet(null)} />
      <AccountHistorySheet view={historyView} onClose={() => setHistoryOf(null)}
        onEdit={() => {
          const a = historyView?.account ?? null;
          setHistoryOf(null);
          setAccountSheet(a);
        }} />
      <LedgerSheet entry={ledgerSheet} onClose={() => setLedgerSheet(null)} />
    </Page>
  );
}

function AccountRow({ view, onEdit, onHistory }: { view: AccountView; onEdit: () => void; onHistory: () => void }) {
  const actions = useActions();
  const money = useMoney();
  const [editing, setEditing] = useState(false);
  const a = view.account;
  const linked = a.linked_category_id != null;
  return (
    <div className="row row-static acct-row">
      <button type="button" className="acct-main" onClick={onHistory} title="Balance history">
        <span className="acct-name ellipsis">{a.name}</span>
        {linked && (
          <span className="acct-auto">
            <span className="tag">auto</span>
            <span className="muted small ellipsis">
              {money(a.base_amount)} + {view.linked?.match_multiplier !== 1 ? `${view.linked?.match_multiplier}× ` : ''}{view.linked?.name ?? 'contributions'}
            </span>
          </span>
        )}
      </button>
      {linked ? (
        <span className={`acct-balance${view.balance < 0 ? ' tone-bad' : ''}`}><Num value={view.balance} format={money} /></span>
      ) : editing ? (
        <MoneyField label={`${a.name} balance`} value={a.balance} nullable={false} autoFocus className="acct-input"
          onCommit={(v) => {
            setEditing(false);
            void actions.saveAccount({ ...a, balance: v ?? 0 });
          }}
          onKeyUp={(e) => { if (e.key === 'Escape') setEditing(false); }} />
      ) : (
        <button type="button" className={`acct-balance editable${view.balance < 0 ? ' tone-bad' : ''}`} title="Edit balance"
          onClick={() => setEditing(true)}>
          <Num value={view.balance} format={money} />
        </button>
      )}
      <Menu label={`${a.name} options`} items={[
        { label: 'Edit…', icon: <Pencil size={16} strokeWidth={1.75} />, onSelect: onEdit },
        { label: 'History', icon: <ChartLine size={16} strokeWidth={1.75} />, onSelect: onHistory },
        { label: 'Archive', icon: <Archive size={16} strokeWidth={1.75} />, onSelect: () => void actions.saveAccount({ ...a, archived: true }) },
        {
          label: 'Delete', warn: true, separatorBefore: true, icon: <Trash2 size={16} strokeWidth={1.75} />,
          onSelect: async () => {
            if (await ask({ title: `Delete ${a.name}?`, body: 'The account is removed from net worth on every device.', yes: 'Delete', danger: true })) {
              if (await actions.deleteAccount(a)) toast(`${a.name} deleted`);
            }
          },
        },
      ]} />
    </div>
  );
}

function AccountSheet({ account, onClose }: { account: Account | null; onClose: () => void }) {
  const { ds } = useData();
  const actions = useActions();
  const [draft, setDraft] = useState<Account | null>(account);
  const [shown, setShown] = useState<Account | null>(account);
  if (account !== shown) {
    setShown(account);
    if (account) setDraft(account);
  }
  const isNew = draft ? !ds.accounts.some((a) => a.id === draft.id) : false;
  const savings = ds.categories.filter((c) => c.kind === 'savings');

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!draft || !draft.name.trim()) return;
    onClose();
    if (await actions.saveAccount({ ...draft, name: draft.name.trim() })) toast(isNew ? 'Account added' : 'Account saved');
  };
  const set = (patch: Partial<Account>) => setDraft((d) => (d ? { ...d, ...patch } : d));

  return (
    <Sheet open={account != null} onClose={onClose} title={isNew ? 'New account' : 'Edit account'}
      footer={<button type="submit" form="acct-form" className="btn primary lg grow" disabled={!draft?.name.trim()}>Save</button>}>
      {draft && (
        <form id="acct-form" className="form-stack" onSubmit={submit}>
          <Field label="Name">
            {(id) => <input id={id} className="input" value={draft.name} data-autofocus onChange={(e) => set({ name: e.target.value })} />}
          </Field>
          <Field label="Group">
            {(id) => (
              <select id={id} className="input" value={draft.account_group} onChange={(e) => set({ account_group: e.target.value as AccountGroup })}>
                {GROUPS.map((g) => <option key={g} value={g}>{GROUP_LABEL[g]}</option>)}
              </select>
            )}
          </Field>
          <Field label="Linked to savings category" hint="Linked accounts compute their balance: base + multiplier × every month's typed-in actual.">
            {(id) => (
              <select id={id} className="input" value={draft.linked_category_id ?? ''} onChange={(e) => set({ linked_category_id: e.target.value || null })}>
                <option value="">Not linked (enter the balance)</option>
                {savings.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            )}
          </Field>
          {draft.linked_category_id ? (
            <Field label="Base amount">
              {(id) => <MoneyField id={id} label="Base amount" value={draft.base_amount} nullable={false} onCommit={(v) => set({ base_amount: v ?? 0 })} />}
            </Field>
          ) : (
            <Field label="Balance" hint="Debts are negative.">
              {(id) => <MoneyField id={id} label="Balance" value={draft.balance} nullable={false} onCommit={(v) => set({ balance: v ?? 0 })} />}
            </Field>
          )}
          <label className="switch-row">
            <span>Super liquid</span>
            <Switch checked={draft.liquid} onChange={(v) => set({ liquid: v })} label="Super liquid" />
          </label>
        </form>
      )}
    </Sheet>
  );
}

function LedgerSheet({ entry, onClose }: { entry: LedgerEntry | null; onClose: () => void }) {
  const { ds } = useData();
  const actions = useActions();
  const [draft, setDraft] = useState<LedgerEntry | null>(entry);
  const [amount, setAmount] = useState('');
  const [shown, setShown] = useState<LedgerEntry | null>(entry);
  if (entry !== shown) {
    setShown(entry);
    if (entry) {
      setDraft(entry);
      setAmount(amountInputText(entry.amount));
    }
  }
  const isNew = draft ? !ds.ledger_entries.some((x) => x.id === draft.id) : false;
  const parsed = parseAmount(amount);
  const valid = Boolean(draft?.name.trim()) && parsed !== null && !Number.isNaN(parsed);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!draft || !valid) return;
    onClose();
    if (await actions.saveLedgerEntry({ ...draft, name: draft.name.trim(), amount: parsed ?? 0 })) toast(isNew ? 'Entry added' : 'Entry saved');
  };
  const remove = async () => {
    if (!draft) return;
    if (!(await ask({ title: `Delete ${draft.name || 'entry'}?`, yes: 'Delete', danger: true }))) return;
    onClose();
    if (await actions.deleteLedgerEntry(draft)) toast('Entry deleted');
  };
  const set = (patch: Partial<LedgerEntry>) => setDraft((d) => (d ? { ...d, ...patch } : d));

  return (
    <Sheet open={entry != null} onClose={onClose} title={isNew ? 'New ledger entry' : 'Edit ledger entry'}
      footer={
        <div className="tx-actions">
          {!isNew && <button type="button" className="btn danger" onClick={remove}><Trash2 size={16} strokeWidth={1.75} /> Delete</button>}
          <button type="submit" form="ledger-form" className="btn primary lg grow" disabled={!valid}>Save</button>
        </div>
      }>
      {draft && (
        <form id="ledger-form" className="form-stack" onSubmit={submit}>
          <Field label="Name">
            {(id) => <input id={id} className="input" value={draft.name} data-autofocus onChange={(e) => set({ name: e.target.value })} />}
          </Field>
          <Field label="Amount" hint="Positive = owed to you, negative = you owe.">
            {(id) => <input id={id} className="input money" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} />}
          </Field>
          <Field label="Note">
            {(id) => <input id={id} className="input" value={draft.note ?? ''} onChange={(e) => set({ note: e.target.value || null })} />}
          </Field>
          <label className="switch-row">
            <span>Settled</span>
            <Switch checked={draft.settled} onChange={(v) => set({ settled: v })} label="Settled" />
          </label>
        </form>
      )}
    </Sheet>
  );
}
