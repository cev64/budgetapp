import { useMemo, useState } from 'react';
import { useWidth } from '../ui/hooks';
import { Link, Navigate, useNavigate, useParams } from 'react-router';
import { ChartColumn, ChevronLeft, ChevronRight, Lock, LockOpen } from 'lucide-react';
import { Page } from '../app/Shell';
import { useData } from '../app/session';
import { Num } from '../ui/Num';
import { CardHead, CategoryDot, Diff, Empty, useMoney, type MoneyFormat } from '../ui/bits';
import { totalsFor, KIND_LABEL, KIND_ORDER, type Calc, type YearSummary } from '../domain/calc';
import { currentYm, monthName, monthShort, ymKey } from '../domain/dates';
import { formatPercent } from '../domain/format';
import type { CategoryKind } from '../domain/types';

export function YearScreen() {
  const params = useParams();
  const { calc } = useData();
  const navigate = useNavigate();
  const money = useMoney();
  const years = calc.years();
  const fallback = years.includes(currentYm().year) ? currentYm().year : (years[years.length - 1] ?? currentYm().year);
  const year = params.year ? Number(params.year) : fallback;
  if (!Number.isInteger(year)) return <Navigate to="/year" replace />;
  const summary = calc.yearSummary(year);

  return (
    <Page label="Year" title={String(year)}>
      <div className="year-head">
        <div className="month-nav">
          <button type="button" className="icon-btn" aria-label="Previous year" onClick={() => navigate(`/year/${year - 1}`)}>
            <ChevronLeft size={20} strokeWidth={1.75} />
          </button>
          <span className="month-pick-btn static">{year}</span>
          <button type="button" className="icon-btn" aria-label="Next year" onClick={() => navigate(`/year/${year + 1}`)}>
            <ChevronRight size={20} strokeWidth={1.75} />
          </button>
        </div>
        {years.length > 1 && (
          <div className="chips year-chips">
            {years.map((y) => (
              <Link key={y} to={`/year/${y}`} className={`pick chip${y === year ? ' on' : ''}`}>{y}</Link>
            ))}
          </div>
        )}
      </div>

      {!summary ? (
        <div className="card">
          <Empty title={`No months in ${year}`} icon={<ChartColumn size={28} strokeWidth={1.5} />}>
            Create a month in {year} from the Month screen to see its summary.
          </Empty>
        </div>
      ) : (
        <div className="year-grid">
          <div className="year-side">
            <Highlight summary={summary} money={money} />
            <section className="card chart-card">
              <CardHead title="Spending by month" />
              <YearChart calc={calc} year={year} money={money} />
            </section>
            <MonthsList calc={calc} summary={summary} money={money} />
          </div>
          <div className="year-tables">
            {KIND_ORDER.map((kind) => (
              <GroupTable key={kind} kind={kind} summary={summary} money={money} />
            ))}
            <section className="card group-card summary-card">
              <CardHead title="Totals"><ColHeads /></CardHead>
              <TotalRow label="Expenses" e={summary.expected.expenses} a={summary.actual.expenses} kind="expense" money={money} />
              <TotalRow label="Saved (incl. match)" e={summary.expected.saved} a={summary.actual.saved} kind="savings" money={money} />
              <TotalRow label="Leftover" e={summary.expected.leftover} a={summary.actual.leftover} kind="leftover" money={money} strong />
            </section>
          </div>
        </div>
      )}
    </Page>
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

function TotalRow({ label, e, a, kind, money, strong }: { label: string; e: number; a: number; kind: CategoryKind | 'leftover'; money: MoneyFormat; strong?: boolean }) {
  return (
    <div className={`num-row${strong ? ' strong' : ''}`}>
      <span className="num-name">{label}</span>
      <span className="num-cell"><Num value={e} format={money} /></span>
      <span className="num-cell"><Num value={a} format={money} bump={strong} /></span>
      <span className="num-cell"><Diff kind={kind} value={a - e} /></span>
    </div>
  );
}

function GroupTable({ kind, summary, money }: { kind: CategoryKind; summary: YearSummary; money: MoneyFormat }) {
  const lines = summary.lines.filter((l) => l.category.kind === kind);
  if (lines.length === 0) return null;
  const e = lines.reduce((s, l) => s + l.expected, 0);
  const a = lines.reduce((s, l) => s + l.actual, 0);
  return (
    <section className="card group-card">
      <CardHead title={KIND_LABEL[kind]}><ColHeads /></CardHead>
      <div className="rows">
        {lines.map((l) => (
          <div key={l.category.id} className="num-row row-static">
            <span className="num-name"><CategoryDot category={l.category} /><span className="ellipsis">{l.category.name}</span></span>
            <span className="num-cell">{money(l.expected)}</span>
            <span className="num-cell"><Num value={l.actual} format={money} /></span>
            <span className="num-cell"><Diff kind={kind} value={l.difference} /></span>
          </div>
        ))}
      </div>
      <div className="num-row totals-row">
        <span className="num-name">Total</span>
        <span className="num-cell">{money(e)}</span>
        <span className="num-cell"><Num value={a} format={money} /></span>
        <span className="num-cell"><Diff kind={kind} value={a - e} /></span>
      </div>
    </section>
  );
}

/** Annualized savings and % of net / gross income, both columns (DOMAIN_RULES §4). */
function Highlight({ summary, money }: { summary: YearSummary; money: MoneyFormat }) {
  const pct = (v: number | null) => formatPercent(v);
  return (
    <section className="card highlight">
      <div className="micro">Annualized savings · {summary.n} {summary.n === 1 ? 'month' : 'months'}</div>
      <div className="highlight-grid">
        <div />
        <div className="micro">Expected</div>
        <div className="micro">Actual</div>
        <div className="hl-label">Per year</div>
        <div className="hl-big"><Num value={summary.expected.annualizedSavings} format={money} /></div>
        <div className="hl-big accent"><Num value={summary.actual.annualizedSavings} format={money} bump /></div>
        <div className="hl-label">% of net income</div>
        <div><Num value={summary.expected.pctNet} format={pct} /></div>
        <div><Num value={summary.actual.pctNet} format={pct} /></div>
        <div className="hl-label">% of gross income</div>
        <div><Num value={summary.expected.pctGross} format={pct} /></div>
        <div><Num value={summary.actual.pctGross} format={pct} /></div>
      </div>
    </section>
  );
}

interface Slot {
  month: number;
  exists: boolean;
  closed: boolean;
  spent: number;
  budget: number;
  leftover: number;
}

function useSlots(calc: Calc, year: number): Slot[] {
  return useMemo(
    () =>
      Array.from({ length: 12 }, (_, i) => {
        const ym = { year, month: i + 1 };
        const m = calc.month(ym);
        if (!m) return { month: i + 1, exists: false, closed: false, spent: 0, budget: 0, leftover: 0 };
        const projected = totalsFor(calc.categories, (c) => calc.projected(ym, c.id));
        const expected = totalsFor(calc.categories, (c) => calc.expected(ym, c.id));
        return { month: i + 1, exists: true, closed: m.closed, spent: projected.expenses, budget: expected.expenses, leftover: projected.leftover };
      }),
    [calc, year],
  );
}

/** Nice round step for ~4 grid lines. */
function niceStep(max: number): number {
  const raw = max / 4;
  const pow = 10 ** Math.floor(Math.log10(raw || 1));
  const n = raw / pow;
  return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 2.5 ? 2.5 : n <= 5 ? 5 : 10) * pow;
}

const compact = (v: number) => {
  const a = Math.abs(v);
  const s = a >= 1000 ? `${Math.round(a / 100) / 10}k` : String(Math.round(a));
  return v < 0 ? `−${s}` : s;
};

/**
 * 12 slots: bar = expenses (projected), thin marker = budgeted expenses, line = leftover.
 * Open months are drawn at 45% opacity (projected); missing months stay empty.
 */
function YearChart({ calc, year, money }: { calc: Calc; year: number; money: MoneyFormat }) {
  const slots = useSlots(calc, year);
  const [ref, width] = useWidth<HTMLDivElement>();
  const [hover, setHover] = useState<number | null>(null);
  const H = 200;
  const pad = { l: 34, r: 6, t: 10, b: 22 };
  const live = slots.filter((s) => s.exists);
  const hi = Math.max(1, ...live.flatMap((s) => [s.spent, s.budget, s.leftover]));
  const lo = Math.min(0, ...live.map((s) => s.leftover));
  const step = niceStep(hi - lo);
  const top = Math.ceil(hi / step) * step;
  const bottom = Math.floor(lo / step) * step;
  const w = Math.max(0, width - pad.l - pad.r);
  const slotW = w / 12;
  const y = (v: number) => pad.t + ((top - v) / (top - bottom || 1)) * (H - pad.t - pad.b);
  const ticks: number[] = [];
  for (let v = bottom; v <= top + 1e-9; v += step) ticks.push(v);
  const barW = Math.max(4, Math.min(28, slotW * 0.56));
  const cx = (i: number) => pad.l + slotW * i + slotW / 2;
  const linePts = slots.map((s, i) => (s.exists ? `${cx(i)},${y(s.leftover)}` : null)).filter(Boolean);
  const shown = hover != null ? slots[hover] : null;

  return (
    <div className="chart" ref={ref}>
      {width > 0 && (
        <svg width={width} height={H} role="img" aria-label={`Expenses, budget and leftover per month in ${year}`}>
          {ticks.map((v) => (
            <g key={v}>
              <line x1={pad.l} x2={width - pad.r} y1={y(v)} y2={y(v)} className={v === 0 ? 'axis zero' : 'axis'} />
              <text x={pad.l - 6} y={y(v) + 4} className="tick" textAnchor="end">{compact(v)}</text>
            </g>
          ))}
          {slots.map((s, i) => (
            <g key={s.month} className={`slot${s.exists ? '' : ' missing'}${s.closed ? ' closed' : ' open'}`}
              onPointerEnter={() => setHover(i)} onPointerLeave={() => setHover(null)}>
              <rect x={pad.l + slotW * i} y={pad.t} width={slotW} height={H - pad.t - pad.b} className="hit" />
              {s.exists && (
                <>
                  <rect x={cx(i) - barW / 2} y={Math.min(y(s.spent), y(0))} width={barW}
                    height={Math.max(1, Math.abs(y(0) - y(s.spent)))} rx={3} className="bar-spent" />
                  <line x1={cx(i) - barW / 2 - 3} x2={cx(i) + barW / 2 + 3} y1={y(s.budget)} y2={y(s.budget)} className="budget-mark" />
                </>
              )}
              <text x={cx(i)} y={H - 6} className={`tick month-tick${hover === i ? ' on' : ''}`} textAnchor="middle">
                {slotW < 26 ? monthShort(s.month)[0] : monthShort(s.month)}
              </text>
            </g>
          ))}
          {linePts.length > 1 && <polyline points={linePts.join(' ')} className="leftover-line" />}
          {slots.map((s, i) => s.exists && <circle key={s.month} cx={cx(i)} cy={y(s.leftover)} r={3} className={`leftover-dot${s.closed ? '' : ' open'}`} />)}
        </svg>
      )}
      <div className="chart-legend">
        {shown && shown.exists ? (
          <span className="chart-readout">
            <strong>{monthName(shown.month)}</strong> · spent {money(shown.spent)} of {money(shown.budget)} · leftover {money(shown.leftover)}
            {shown.closed ? '' : ' (projected)'}
          </span>
        ) : (
          <>
            <span><i className="key spent" />Expenses</span>
            <span><i className="key mark" />Budget</span>
            <span><i className="key left" />Leftover</span>
            <span className="muted">Lighter = open month</span>
          </>
        )}
      </div>
    </div>
  );
}

function MonthsList({ calc, summary, money }: { calc: Calc; summary: YearSummary; money: MoneyFormat }) {
  return (
    <section className="card">
      <CardHead title="Months" />
      <div className="rows">
        {summary.months.map((m) => {
          const t = totalsFor(calc.categories, (c) => calc.projected(m, c.id));
          return (
            <Link key={m.month} to={`/month/${ymKey(m)}`} className="row month-row">
              <span className="month-row-name">
                {m.closed ? <Lock size={14} strokeWidth={1.75} /> : <LockOpen size={14} strokeWidth={1.75} className="muted" />}
                {monthName(m.month)}
              </span>
              <span className="muted small">{m.closed ? 'Closed' : 'Open · projected'}</span>
              <span className={`month-row-left ${t.leftover < 0 ? 'tone-bad' : ''}`}>{money(t.leftover)}</span>
            </Link>
          );
        })}
      </div>
    </section>
  );
}
