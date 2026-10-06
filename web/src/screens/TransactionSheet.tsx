import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { ChevronDown, List, Pencil, Trash2 } from 'lucide-react';
import { useActions, useData } from '../app/session';
import { newId } from '../data/actions';
import { Sheet } from '../ui/Sheet';
import { Field, Switch } from '../ui/controls';
import { toast } from '../ui/Toast';
import { haptic } from '../ui/motion';
import { compareYm, currentYm, todayIso, ymLabel } from '../domain/dates';
import { parseAmount, amountInputText, savedMessage } from '../domain/format';
import type { Category, Transaction, YM } from '../domain/types';
import { CategoryDot } from '../ui/bits';
import { useDeleteTransaction } from './TxRow';

export interface TxSheetState {
  open: boolean;
  defaults?: { ym?: YM; categoryId?: string };
  editing?: Transaction;
}

const ymValue = (ym: YM) => `${ym.year}-${ym.month}`;

/** Add / edit transaction (UI_ANATOMY "Add transaction"): amount, category, item, date, month, note. */
export function TransactionSheet({ state, onClose }: { state: TxSheetState; onClose: () => void }) {
  const { calc } = useData();
  const actions = useActions();
  const editing = state.editing;

  const [amount, setAmount] = useState('');
  const [refund, setRefund] = useState(false);
  const [categoryId, setCategoryId] = useState<string | null>(null);
  const [item, setItem] = useState('');
  const [date, setDate] = useState(todayIso());
  const [month, setMonth] = useState('');
  const [note, setNote] = useState('');
  const [noteOpen, setNoteOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const amountRef = useRef<HTMLInputElement>(null);

  const months = useMemo(() => calc.months.slice().sort((a, b) => compareYm(b, a)), [calc.months]);
  const pickable = useMemo(() => {
    const active = calc.categories.filter((c) => !c.archived || c.id === editing?.category_id);
    // Ledger categories first, then manual ones.
    return [...active.filter((c) => c.tracking === 'ledger'), ...active.filter((c) => c.tracking === 'manual')];
  }, [calc.categories, editing?.category_id]);

  // Reset the form each time the sheet opens.
  useEffect(() => {
    if (!state.open) return;
    setError(null);
    setBusy(false);
    if (editing) {
      setAmount(amountInputText(Math.abs(editing.amount)));
      setRefund(editing.amount < 0);
      setCategoryId(editing.category_id);
      setItem(editing.item);
      setDate(editing.date ?? '');
      setMonth(ymValue(editing));
      setNote(editing.note ?? '');
      setNoteOpen(Boolean(editing.note));
      return;
    }
    const d = state.defaults ?? {};
    const today = currentYm();
    const exists = (ym: YM | undefined) => ym && calc.month(ym);
    const target = exists(d.ym) ? d.ym! : exists(today) ? today : calc.latestMonth();
    setAmount('');
    setRefund(false);
    setCategoryId(d.categoryId && calc.categoryById.has(d.categoryId) ? d.categoryId : null);
    setItem('');
    setDate(todayIso());
    setMonth(target ? ymValue(target) : '');
    setNote('');
    setNoteOpen(false);
  }, [state.open, editing]);

  const category: Category | undefined = categoryId ? calc.categoryById.get(categoryId) : undefined;

  const submit = async (e?: FormEvent) => {
    e?.preventDefault();
    const parsed = parseAmount(amount);
    if (parsed === null || Number.isNaN(parsed)) {
      setError('Enter an amount.');
      amountRef.current?.focus();
      return;
    }
    if (!category) {
      setError('Pick a category.');
      return;
    }
    const [y, m] = month.split('-').map(Number);
    if (!y || !m) {
      setError('Pick a month.');
      return;
    }
    const value = refund ? -Math.abs(parsed) : Math.abs(parsed);
    const row: Transaction = {
      ...(editing ?? { id: newId(), note: null }),
      year: y,
      month: m,
      category_id: category.id,
      date: date || null,
      item: item.trim(),
      amount: value,
      note: note.trim() || null,
    };
    setBusy(true);
    onClose();
    const ok = await actions.saveTransaction(row);
    if (ok) {
      haptic(9);
      toast(savedMessage(category.kind, value));
    }
  };

  const deleteTransaction = useDeleteTransaction();
  const remove = async () => {
    if (!editing) return;
    onClose();
    await deleteTransaction(editing);
  };

  const createCurrent = async () => {
    const ym = currentYm();
    if (await actions.createMonth(ym)) {
      setMonth(ymValue(ym));
      toast(`${ymLabel(ym)} started`);
    }
  };

  return (
    <Sheet
      open={state.open}
      onClose={onClose}
      title={editing ? 'Edit transaction' : 'Add transaction'}
      footer={
        <div className="tx-actions">
          {editing && (
            <button type="button" className="btn danger" onClick={remove}>
              <Trash2 size={16} strokeWidth={1.75} /> Delete
            </button>
          )}
          <button type="submit" form="tx-form" className="btn primary lg grow" disabled={busy || months.length === 0}>
            {editing ? 'Save' : 'Add'}
          </button>
        </div>
      }
    >
      <form id="tx-form" className="tx-form" onSubmit={submit} noValidate>
        <div className="amount-row">
          <span className={`amount-sign${refund ? ' neg' : ''}`}>{refund ? '−$' : '$'}</span>
          <input
            ref={amountRef}
            className="amount-input"
            inputMode="decimal"
            autoComplete="off"
            placeholder="0"
            aria-label="Amount"
            data-autofocus
            value={amount}
            onChange={(e) => {
              setAmount(e.target.value);
              setError(null);
            }}
          />
          <label className="refund-toggle">
            <span>Refund</span>
            <Switch checked={refund} onChange={setRefund} label="Refund (negative amount)" />
          </label>
        </div>

        <div className="field">
          <div className="field-label" id="tx-cat-label">Category</div>
          <div className="pick-grid" role="radiogroup" aria-labelledby="tx-cat-label">
            {pickable.map((c) => (
              <button
                key={c.id}
                type="button"
                role="radio"
                aria-checked={c.id === categoryId}
                className={`pick cat-pick${c.id === categoryId ? ' on pop' : ''}`}
                onClick={() => {
                  setCategoryId(c.id);
                  setError(null);
                }}
              >
                <CategoryDot category={c} />
                <span className="ellipsis">{c.name}</span>
                {c.tracking === 'manual' && <Pencil size={12} strokeWidth={1.75} className="pick-meta" aria-label="manual" />}
              </button>
            ))}
          </div>
          {category?.tracking === 'manual' && (
            <div className="field-hint">
              {category.name} is a manual category: its actual is typed in on the month, so this transaction is kept for reference only.
            </div>
          )}
        </div>

        <Field label="Item">
          {(id) => (
            <input id={id} className="input" value={item} placeholder="What was it?" autoComplete="off"
              onChange={(e) => setItem(e.target.value)} />
          )}
        </Field>

        <div className="field-row">
          <Field label="Date">
            {(id) => <input id={id} className="input" type="date" value={date} onChange={(e) => setDate(e.target.value)} />}
          </Field>
          <Field label="Month">
            {(id) =>
              months.length ? (
                <select id={id} className="input" value={month} onChange={(e) => setMonth(e.target.value)}>
                  {months.map((m) => (
                    <option key={ymValue(m)} value={ymValue(m)}>{ymLabel(m)}</option>
                  ))}
                </select>
              ) : (
                <button id={id} type="button" className="btn" onClick={createCurrent}>Start {ymLabel(currentYm())}</button>
              )
            }
          </Field>
        </div>

        <div className={`disclosure${noteOpen ? ' open' : ''}`}>
          <button type="button" className="link disclosure-btn" aria-expanded={noteOpen} onClick={() => setNoteOpen((o) => !o)}>
            <ChevronDown size={16} strokeWidth={1.75} className="chev" /> Note
          </button>
          <div className="expand">
            <div>
              <div className="content">
                <textarea className="input" rows={2} value={note} aria-label="Note" tabIndex={noteOpen ? 0 : -1}
                  onChange={(e) => setNote(e.target.value)} />
              </div>
            </div>
          </div>
        </div>

        {error && <div className="form-error" role="alert">{error}</div>}
        {category?.tracking === 'ledger' && month && (
          <div className="field-hint tx-landing">
            <List size={13} strokeWidth={1.75} /> Counts toward {category.name} in {ymLabel({ year: Number(month.split('-')[0]), month: Number(month.split('-')[1]) })}
          </div>
        )}
      </form>
    </Sheet>
  );
}
