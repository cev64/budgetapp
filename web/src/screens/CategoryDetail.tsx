import { useRef, useState, type FormEvent } from 'react';
import { Navigate } from 'react-router';
import { ArrowLeft, Plus } from 'lucide-react';
import { useActions, useData, useSheets } from '../app/session';
import { newId } from '../data/actions';
import { Num } from '../ui/Num';
import { MoneyField, Progress } from '../ui/controls';
import { FlipList } from '../ui/FlipList';
import { CategoryDot, Diff, Empty, OverBudget, TrackingIcon, useMoney } from '../ui/bits';
import { toast } from '../ui/Toast';
import { haptic } from '../ui/motion';
import { compareTransactionsDesc } from './txSort';
import { KIND_LABEL } from '../domain/calc';
import { parseAmount, savedMessage } from '../domain/format';
import { shortDate, todayIso, ymKey, ymLabel } from '../domain/dates';
import type { CategoryKind, YM } from '../domain/types';

/** Category detail: big actual vs expected, editable expected, actual / override, and the ledger. */
export function CategoryDetail({ ym, categoryId, onBack }: { ym: YM; categoryId: string; onBack?: () => void }) {
  const { calc } = useData();
  const actions = useActions();
  const { editTransaction } = useSheets();
  const money = useMoney();
  const [overriding, setOverriding] = useState(false);

  const category = calc.categoryById.get(categoryId);
  if (!category) return <Navigate to={`/month/${ymKey(ym)}`} replace />;
  const line = calc.monthSummary(ym).lines.find((l) => l.category.id === categoryId);
  const expected = calc.expected(ym, categoryId);
  const actual = calc.actual(ym, categoryId);
  const ledgerSum = calc.ledgerSum(ym, categoryId);
  const budget = calc.budget(ym, categoryId);
  const override = category.tracking === 'ledger' && budget?.actual != null;
  const txs = calc.transactionsIn(ym, categoryId).sort(compareTransactionsDesc);

  return (
    <section className="card detail panel on" aria-label={`${category.name} details`}>
      <div className="detail-head">
        {onBack && (
          <button type="button" className="icon-btn" aria-label="Back to month" onClick={onBack}>
            <ArrowLeft size={20} strokeWidth={1.75} />
          </button>
        )}
        <div className="detail-title">
          <h2 className="card-title"><CategoryDot category={category} />{category.name}</h2>
          <div className="detail-meta muted">
            {KIND_LABEL[category.kind]} · <TrackingIcon category={category} /> {category.tracking === 'ledger' ? 'Ledger' : 'Manual'}
            {category.match_multiplier !== 1 && <> · match ×{category.match_multiplier}</>} · {ymLabel(ym)}
          </div>
        </div>
      </div>

      <div className="detail-big">
        <div className="detail-actual">
          <Num value={actual ?? 0} format={money} />
          {override && <span className="pill sm manual-pill">manual</span>}
        </div>
        <div className="muted">
          of {money(expected)} expected · <Diff kind={category.kind} value={line?.difference ?? (actual ?? 0) - expected} />
        </div>
        <Progress actual={actual ?? 0} expected={expected} invert={category.kind !== 'expense'} />
        {category.kind === 'expense' && (actual ?? 0) - expected >= 0.005 && (
          <OverBudget name={category.name} over={(actual ?? 0) - expected} />
        )}
      </div>

      <div className="detail-fields">
        <div className="field">
          <label className="field-label" htmlFor={`exp-${categoryId}`}>Expected</label>
          <MoneyField id={`exp-${categoryId}`} label="Expected" value={budget?.expected ?? null}
            onCommit={(v) => void actions.setBudget(ym, categoryId, { expected: v })} placeholder="0" />
        </div>
        <div className="field">
          <label className="field-label" htmlFor={`act-${categoryId}`}>Actual</label>
          {category.tracking === 'manual' ? (
            <MoneyField id={`act-${categoryId}`} label="Actual" value={budget?.actual ?? null} placeholder="Blank"
              onCommit={(v) => void actions.setBudget(ym, categoryId, { actual: v })} />
          ) : override || overriding ? (
            <>
              <MoneyField id={`act-${categoryId}`} label="Actual override" value={budget?.actual ?? null}
                placeholder={money(ledgerSum)} autoFocus={overriding && !override}
                onCommit={(v) => {
                  setOverriding(false);
                  void actions.setBudget(ym, categoryId, { actual: v });
                }} />
              <div className="field-hint">
                Ledger sum {money(ledgerSum)} ·{' '}
                <button type="button" className="link" onClick={() => {
                  setOverriding(false);
                  if (override) void actions.setBudget(ym, categoryId, { actual: null });
                }}>
                  {override ? 'Clear override' : 'Cancel'}
                </button>
              </div>
            </>
          ) : (
            <>
              <div className="ledger-sum">{money(ledgerSum)}</div>
              <div className="field-hint">
                Sum of the transactions ·{' '}
                <button type="button" className="link" onClick={() => setOverriding(true)}>Override</button>
              </div>
            </>
          )}
        </div>
      </div>

      <div className="detail-ledger">
        <div className="micro">Transactions</div>
        {category.tracking === 'manual' && (
          <p className="field-hint">Manual category: the actual is typed in above. Transactions here are kept for reference.</p>
        )}
        <AddRow ym={ym} categoryId={categoryId} kind={category.kind} />
        {txs.length === 0 ? (
          <Empty title="No transactions yet" />
        ) : (
          <FlipList className="rows" scope={`${ymKey(ym)}:${categoryId}`} signature={txs.map((t) => t.id).join()}>
            {txs.map((t) => (
              <button key={t.id} data-k={t.id} type="button" className="row tx-row" onClick={() => editTransaction(t)}>
                <span className="tx-date muted">{t.date ? shortDate(t.date) : '—'}</span>
                <span className="tx-main"><span className="tx-item ellipsis">{t.item || <span className="muted">No description</span>}</span></span>
                <span className={`tx-amount${t.amount < 0 ? ' tone-good' : ''}`}>{money(t.amount)}</span>
              </button>
            ))}
          </FlipList>
        )}
      </div>
    </section>
  );
}

/** Inline "add a transaction to this category" row: item, amount (negative = refund), date. */
function AddRow({ ym, categoryId, kind }: { ym: YM; categoryId: string; kind: CategoryKind }) {
  const actions = useActions();
  const [item, setItem] = useState('');
  const [amount, setAmount] = useState('');
  const today = todayIso();
  // Default date: today when it falls in this month, else the first of the month.
  const inMonth = today.startsWith(`${ymKey(ym)}-`);
  const [date, setDate] = useState(inMonth ? today : `${ymKey(ym)}-01`);
  const [bad, setBad] = useState(false);
  const itemRef = useRef<HTMLInputElement>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const v = parseAmount(amount);
    if (v === null || Number.isNaN(v)) {
      setBad(true);
      return;
    }
    const ok = await actions.saveTransaction({
      id: newId(), year: ym.year, month: ym.month, category_id: categoryId,
      date: date || null, item: item.trim(), amount: v, note: null,
    });
    if (ok) {
      haptic(9);
      toast(savedMessage(kind, v));
    }
    setItem('');
    setAmount('');
    itemRef.current?.focus();
  };

  return (
    <form className="add-row" onSubmit={submit}>
      <input ref={itemRef} className="input add-item" placeholder="Item" aria-label="Item" value={item} onChange={(e) => setItem(e.target.value)} />
      <input className={`input money add-amount${bad ? ' invalid' : ''}`} placeholder="0.00" inputMode="decimal" aria-label="Amount (negative for a refund)"
        value={amount} onChange={(e) => { setAmount(e.target.value); setBad(false); }} />
      <input className="input add-date" type="date" aria-label="Date" value={date}
        onChange={(e) => setDate(e.target.value)} />
      <button type="submit" className="btn primary" aria-label="Add transaction"><Plus size={16} strokeWidth={2} /></button>
    </form>
  );
}
