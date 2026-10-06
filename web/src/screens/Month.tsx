import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router';
import { CalendarPlus, ChevronLeft, ChevronRight, Lock, LockOpen, Plus, Receipt } from 'lucide-react';
import { Page } from '../app/Shell';
import { useActions, useData, useSheets } from '../app/session';
import { Seg } from '../ui/Seg';
import { Num } from '../ui/Num';
import { Sheet } from '../ui/Sheet';
import { Progress, Switch } from '../ui/controls';
import { FlipList } from '../ui/FlipList';
import { CardHead, CategoryDot, Diff, Empty, TrackingIcon, useMoney } from '../ui/bits';
import { useGlide, useLayoutMode } from '../ui/hooks';
import { TxRow } from './TxRow';
import { rollText, haptic, reducedMotion } from '../ui/motion';
import { toast } from '../ui/Toast';
import { CategoryDetail } from './CategoryDetail';
import { compareTransactionsDesc } from './txSort';
import { KIND_LABEL, KIND_ORDER, type Line, type MonthSummary, type Totals } from '../domain/calc';
import { addMonths, compareYm, currentYm, monthName, monthShort, parseYm, ymKey, ymLabel } from '../domain/dates';
import { suggestedMonth } from '../domain/newMonth';
import { EMPTY_MONTH_MESSAGE, monthClosedMessage } from '../domain/format';
import type { CategoryKind, YM } from '../domain/types';

/** /month → the current calendar month if it exists, else the latest month. */
export function MonthIndex() {
  const { calc } = useData();
  const today = currentYm();
  const target = calc.month(today) ? today : (calc.latestMonth() ?? today);
  return <Navigate to={`/month/${ymKey(target)}`} replace />;
}

export function MonthScreen({ tab }: { tab: 'budget' | 'tx' }) {
  const params = useParams();
  const ym = parseYm(params.ym);
  const catId = params.catId;
  const { calc } = useData();
  const mode = useLayoutMode();
  const navigate = useNavigate();

  if (!ym) return <Navigate to="/month" replace />;
  const summary = calc.monthSummary(ym);
  const key = ymKey(ym);
  const twoPane = mode === 'expanded';
  const detailOnly = !twoPane && catId != null;

  return (
    <Page label="Month" title={<MonthTitle ym={ym} />} className="month-page">
      {!detailOnly && <MonthHeader summary={summary} />}

      {!summary.exists ? (
        <CreateMonth ym={ym} />
      ) : detailOnly ? (
        <CategoryDetail ym={ym} categoryId={catId} onBack={() => navigate(`/month/${key}`)} />
      ) : (
        <>
          <Seg
            className="month-tabs"
            label="Month view"
            value={tab}
            onChange={(t) => navigate(t === 'tx' ? `/month/${key}/tx` : `/month/${key}`)}
            options={[{ value: 'budget', label: 'Budget' }, { value: 'tx', label: 'Transactions' }]}
          />
          {tab === 'tx' ? (
            <TransactionsTab key={key} ym={ym} />
          ) : twoPane ? (
            <div className="two-pane">
              <BudgetTab summary={summary} selected={catId} />
              <div className="pane-detail">
                {catId ? (
                  <CategoryDetail key={`${key}:${catId}`} ym={ym} categoryId={catId} />
                ) : (
                  <div className="card pane-placeholder">
                    <Empty title="Pick a category" icon={<Receipt size={26} strokeWidth={1.5} />}>
                      Edit its budget, its actual and its transactions here.
                    </Empty>
                  </div>
                )}
              </div>
            </div>
          ) : (
            <BudgetTab summary={summary} selected={undefined} />
          )}
          <NextMonthOffer ym={ym} />
        </>
      )}
    </Page>
  );
}

/** Title that rolls in the direction of travel when the month changes. */
function MonthTitle({ ym }: { ym: YM }) {
  const ref = useRef<HTMLSpanElement>(null);
  const prev = useRef<YM | null>(null);
  const text = ymLabel(ym);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    rollText(el, text, prev.current && compareYm(ym, prev.current) < 0 ? 'down' : 'up');
    prev.current = ym;
  }, [text, ym]);
  return <span ref={ref} />;
}

function MonthHeader({ summary }: { summary: MonthSummary }) {
  const navigate = useNavigate();
  const actions = useActions();
  const [picker, setPicker] = useState(false);
  const go = (d: number) => navigate(`/month/${ymKey(addMonths(summary, d))}`);

  const toggleClosed = async (closed: boolean) => {
    if (await actions.setMonthClosed(summary, closed)) {
      haptic(closed ? [12, 60, 24] : 9);
      toast(closed ? monthClosedMessage(monthName(summary.month)) : `${monthName(summary.month)} is open again.`);
    }
  };

  return (
    <div className="month-head">
      <div className="month-nav">
        <button type="button" className="icon-btn" aria-label="Previous month" onClick={() => go(-1)}>
          <ChevronLeft size={20} strokeWidth={1.75} />
        </button>
        <button type="button" className="month-pick-btn" onClick={() => setPicker(true)} aria-haspopup="dialog">
          {monthName(summary.month)} {summary.year}
        </button>
        <button type="button" className="icon-btn" aria-label="Next month" onClick={() => go(1)}>
          <ChevronRight size={20} strokeWidth={1.75} />
        </button>
      </div>
      {summary.exists && (
        <div className="closed-toggle">
          <label className={`closed-label${summary.closed ? ' on' : ''}`}>
            {summary.closed ? <Lock size={16} strokeWidth={1.75} /> : <LockOpen size={16} strokeWidth={1.75} />}
            <span>Closed</span>
            <Switch checked={summary.closed} onChange={toggleClosed} label="Month closed" />
          </label>
        </div>
      )}
      <MonthPicker open={picker} onClose={() => setPicker(false)} current={summary} />
    </div>
  );
}

/**
 * Year + month grid. Existing months show their status; others open the "create" state. The selected
 * month sits on a raised thumb that glides to the tapped month before the sheet closes.
 */
function MonthPicker({ open, onClose, current }: { open: boolean; onClose: () => void; current: YM }) {
  const { calc } = useData();
  const navigate = useNavigate();
  const [year, setYear] = useState(current.year);
  const [picked, setPicked] = useState<YM>(current);
  const [ripple, setRipple] = useState(0);
  const gridRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (open) {
      setYear(current.year);
      setPicked(current);
      setRipple((r) => r + 1);
    }
  }, [open, current.year, current.month]);
  useGlide(gridRef, `${year}:${picked.year}-${picked.month}:${ripple}`, { grid: true, cls: 'month-thumb', selector: '[aria-current="true"]' });

  const go = (ym: YM) => {
    setPicked(ym);
    haptic(6);
    setTimeout(() => {
      onClose();
      navigate(`/month/${ymKey(ym)}`);
    }, reducedMotion() ? 0 : 200);
  };

  return (
    <Sheet open={open} onClose={onClose} title="Go to month">
      <div className="year-switch">
        <button type="button" className="icon-btn" aria-label="Previous year" onClick={() => setYear((y) => y - 1)}>
          <ChevronLeft size={20} strokeWidth={1.75} />
        </button>
        <span className="year-switch-label">{year}</span>
        <button type="button" className="icon-btn" aria-label="Next year" onClick={() => setYear((y) => y + 1)}>
          <ChevronRight size={20} strokeWidth={1.75} />
        </button>
      </div>
      <div key={`${year}:${ripple}`} ref={gridRef} className="month-grid grid enter">
        {Array.from({ length: 12 }, (_, i) => {
          const ym = { year, month: i + 1 };
          const m = calc.month(ym);
          const on = year === picked.year && i + 1 === picked.month;
          return (
            <button
              key={i}
              type="button"
              className={`pick month-cell${on ? ' on' : ''}${m ? '' : ' missing'}`}
              aria-current={on ? 'true' : undefined}
              style={{ '--n': (i % 4) + Math.floor(i / 4) } as React.CSSProperties}
              onClick={() => go(ym)}
            >
              <span className="month-cell-name">{monthShort(i + 1)}</span>
              <span className="month-cell-status">
                {m ? (m.closed ? <Lock size={12} strokeWidth={1.75} aria-label="closed" /> : <LockOpen size={12} strokeWidth={1.75} aria-label="open" />)
                  : <Plus size={12} strokeWidth={1.75} aria-label="not created" />}
              </span>
            </button>
          );
        })}
      </div>
      <p className="muted small picker-note">Months with + don't exist yet. Pick one to create it.</p>
    </Sheet>
  );
}

function CreateMonth({ ym }: { ym: YM }) {
  const actions = useActions();
  return (
    <div className="card create-month arrive">
      <Empty title={`${ymLabel(ym)} doesn't exist yet`} icon={<CalendarPlus size={28} strokeWidth={1.5} />}>
        Creating it copies each category's budget from the latest earlier month and pre-fills your active recurring items.
      </Empty>
      <button
        type="button"
        className="btn primary lg"
        onClick={async () => {
          if (await actions.createMonth(ym)) {
            haptic(9);
            toast(`${ymLabel(ym)} created`);
          }
        }}
      >
        Create {ymLabel(ym)}
      </button>
    </div>
  );
}

/** "Start November" when this is the latest month and the next one is due (DOMAIN_RULES §6). */
function NextMonthOffer({ ym }: { ym: YM }) {
  const { calc } = useData();
  const actions = useActions();
  const navigate = useNavigate();
  const latest = calc.latestMonth();
  const suggestion = suggestedMonth(calc.months, currentYm());
  if (!suggestion || !latest || compareYm(latest, ym) !== 0 || compareYm(suggestion, ym) <= 0) return null;
  return (
    <div className="banner card next-month">
      <span className="banner-icon"><CalendarPlus size={20} strokeWidth={1.75} /></span>
      <div className="banner-text">
        <strong>Ready for {monthName(suggestion.month)}?</strong>
        <span>Budgets copy forward and recurring items are pre-filled.</span>
      </div>
      <button
        type="button"
        className="btn"
        onClick={async () => {
          if (await actions.createMonth(suggestion)) {
            toast(`${ymLabel(suggestion)} started`);
            navigate(`/month/${ymKey(suggestion)}`);
          }
        }}
      >
        Start {monthName(suggestion.month)}
      </button>
    </div>
  );
}

// ---- Budget tab ----

function BudgetTab({ summary, selected }: { summary: MonthSummary; selected: string | undefined }) {
  const money = useMoney();
  const key = ymKey(summary);
  const total = (kind: CategoryKind, t: Totals) => (kind === 'income' ? t.income : kind === 'expense' ? t.expenses : t.contributions);

  const empty = summary.lines.every((l) => l.budget?.expected == null && l.budget?.actual == null);
  return (
    <div className="budget-tab">
      {empty && (
        <div className="card">
          <Empty title={EMPTY_MONTH_MESSAGE} icon={<CalendarPlus size={26} strokeWidth={1.5} />}>
            Open a category below to set what you expect to spend.
          </Empty>
        </div>
      )}
      {KIND_ORDER.map((kind) => {
        const lines = summary.lines.filter((l) => l.category.kind === kind);
        if (lines.length === 0) return null;
        const e = total(kind, summary.expected);
        const a = total(kind, summary.actual);
        return (
          <section key={kind} className="card group-card">
            <CardHead title={KIND_LABEL[kind]}>
              <ColHeads />
            </CardHead>
            <div className="rows">
              {lines.map((l) => (
                <BudgetRow key={l.category.id} line={l} to={`/month/${key}/c/${l.category.id}`} selected={l.category.id === selected} />
              ))}
            </div>
            <div className="num-row totals-row">
              <span className="num-name">Total</span>
              <span className="num-cell"><Num value={e} format={money} /></span>
              <span className="num-cell"><Num value={a} format={money} /></span>
              <span className="num-cell"><Diff kind={kind} value={a - e} /></span>
            </div>
          </section>
        );
      })}

      <section className="card group-card summary-card">
        <CardHead title="Summary">
          <ColHeads />
        </CardHead>
        <SummaryRow label="Monthly expenses" e={summary.expected.expenses} a={summary.actual.expenses} kind="expense" />
        <SummaryRow label="Saved (incl. match)" e={summary.expected.saved} a={summary.actual.saved} kind="savings" />
        <SummaryRow label="Leftover" e={summary.expected.leftover} a={summary.actual.leftover} kind="leftover" strong />
      </section>
    </div>
  );
}

function ColHeads() {
  return (
    <div className="col-heads" aria-hidden="true">
      <span>Expected</span>
      <span>Actual</span>
      <span>Diff</span>
    </div>
  );
}

function SummaryRow({ label, e, a, kind, strong }: { label: string; e: number; a: number; kind: CategoryKind | 'leftover'; strong?: boolean }) {
  const money = useMoney();
  return (
    <div className={`num-row${strong ? ' strong' : ''}`}>
      <span className="num-name">{label}</span>
      <span className="num-cell"><Num value={e} format={money} /></span>
      <span className="num-cell"><Num value={a} format={money} bump={strong} /></span>
      <span className="num-cell"><Diff kind={kind} value={a - e} /></span>
    </div>
  );
}

function BudgetRow({ line, to, selected }: { line: Line; to: string; selected: boolean }) {
  const money = useMoney();
  const { category: c } = line;
  return (
    <Link to={to} className={`row budget-line-row${selected ? ' selected' : ''}`} aria-current={selected ? 'true' : undefined}>
      <div className="num-row">
        <span className="num-name">
          <CategoryDot category={c} />
          <span className="ellipsis">{c.name}</span>
          <TrackingIcon category={c} />
          {line.override && <span className="manual-pill">manual</span>}
        </span>
        <span className="num-cell">{money(line.expected)}</span>
        <span className="num-cell"><Num value={line.actual} format={money} /></span>
        <span className="num-cell"><Diff kind={c.kind} value={line.difference} /></span>
      </div>
      <Progress actual={line.actual ?? 0} expected={line.expected} invert={c.kind !== 'expense'} thin />
    </Link>
  );
}

// ---- Transactions tab ----

function TransactionsTab({ ym }: { ym: YM }) {
  const { calc } = useData();
  const { openAdd } = useSheets();
  const money = useMoney();
  const [filter, setFilter] = useState<string | null>(null);
  const all = useMemo(() => calc.transactionsIn(ym).sort(compareTransactionsDesc), [calc, ym]);
  const used = useMemo(() => calc.categories.filter((c) => all.some((t) => t.category_id === c.id)), [calc.categories, all]);
  const list = filter ? all.filter((t) => t.category_id === filter) : all;
  const sum = list.reduce((s, t) => s + t.amount, 0);

  return (
    <section className="card tx-card">
      <div className="chips" role="toolbar" aria-label="Filter by category">
        <button type="button" className={`pick chip${filter == null ? ' on' : ''}`} onClick={() => setFilter(null)}>All</button>
        {used.map((c) => (
          <button key={c.id} type="button" className={`pick chip${filter === c.id ? ' on' : ''}`} onClick={() => setFilter(c.id)}>
            <CategoryDot category={c} />
            {c.name}
          </button>
        ))}
      </div>
      <div className="tx-summary">
        <span className="muted">{list.length} {list.length === 1 ? 'transaction' : 'transactions'}</span>
        <span className="tx-sum">{money(sum)}</span>
        <button type="button" className="btn" onClick={() => openAdd({ ym, categoryId: filter ?? undefined })}>
          <Plus size={16} strokeWidth={1.75} /> Add
        </button>
      </div>
      {list.length === 0 ? (
        <Empty title="No transactions" icon={<Receipt size={24} strokeWidth={1.5} />}>Nothing logged in {ymLabel(ym)} yet.</Empty>
      ) : (
        <FlipList className="rows" scope={filter ?? 'all'} signature={list.map((t) => t.id).join()}>
          {list.map((t) => <TxRow key={t.id} t={t} showCategory={!filter} />)}
        </FlipList>
      )}
    </section>
  );
}
