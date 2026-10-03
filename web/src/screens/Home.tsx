import { useMemo } from 'react';
import { Link, useNavigate } from 'react-router';
import { CalendarPlus, Receipt } from 'lucide-react';
import { Page } from '../app/Shell';
import { useActions, useData, useSheets } from '../app/session';
import { Num } from '../ui/Num';
import { Progress } from '../ui/controls';
import { FlipList } from '../ui/FlipList';
import { CardHead, CategoryDot, Empty, OverBudget, useMoney, type MoneyFormat } from '../ui/bits';
import { toast } from '../ui/Toast';
import { computeNetWorth } from '../domain/networth';
import { currentYm, monthName, shortDate, ymKey, ymLabel } from '../domain/dates';
import { suggestedMonth } from '../domain/newMonth';
import { compareTransactionsDesc } from './txSort';
import { useLayoutMode } from '../ui/hooks';
import { Sparkline } from '../ui/HistoryChart';
import { ChangeLine, useSnapshots } from './NetWorthHistory';
import { changeOver, rangePoints, seriesOf } from '../domain/history';
import type { YM } from '../domain/types';
import type { MonthSummary } from '../domain/calc';

export function HomeScreen() {
  const { ds, calc } = useData();
  const actions = useActions();
  const { editTransaction } = useSheets();
  const money = useMoney();
  const navigate = useNavigate();

  const today = currentYm();
  const current = calc.month(today) ?? calc.latestMonth();
  const suggestion = suggestedMonth(calc.months, today);
  const showStart = suggestion && suggestion.year === today.year && suggestion.month === today.month;

  const summary = current ? calc.monthSummary(current) : null;
  const nw = useMemo(
    () => computeNetWorth({ accounts: ds.accounts, categories: ds.categories, budgets: ds.budgets, ledger: ds.ledger_entries }),
    [ds],
  );
  const snaps = useSnapshots();
  const nwSeries = useMemo(() => seriesOf(snaps, 'net_worth'), [snaps]);
  const nwChange = changeOver(nwSeries, 30);
  const nwSpark = useMemo(() => rangePoints(nwSeries, 30), [nwSeries]);
  const recent = useMemo(() => ds.transactions.slice().sort(compareTransactionsDesc).slice(0, 8), [ds.transactions]);

  const start = async (ym: YM) => {
    if (await actions.createMonth(ym)) {
      toast(`${ymLabel(ym)} started`);
      navigate(`/month/${ymKey(ym)}`);
    }
  };

  const title = current ? ymLabel(current) : 'Budget';
  const wide = useLayoutMode() === 'expanded';

  const recentCard = summary && (
    <section className="card">
      <CardHead title="Recent">
        <Link className="link" to={`/month/${ymKey(summary)}/tx`}>All</Link>
      </CardHead>
      {recent.length === 0 ? (
        <Empty title="No transactions yet" icon={<Receipt size={24} strokeWidth={1.5} />}>Press Add (or N) to log one.</Empty>
      ) : (
        <FlipList className="rows" signature={recent.map((t) => t.id).join()}>
          {recent.map((t) => {
            const cat = calc.categoryById.get(t.category_id);
            return (
              <button key={t.id} data-k={t.id} type="button" className="row tx-row" onClick={() => editTransaction(t)}>
                <span className="tx-main">
                  <span className="tx-item ellipsis">{t.item || <span className="muted">No description</span>}</span>
                  <span className="tx-meta">
                    {cat && <span className="pill sm cat-pill"><CategoryDot category={cat} />{cat.name}</span>}
                    <span className="muted">{t.date ? shortDate(t.date) : ymLabel(t)}</span>
                  </span>
                </span>
                <span className={`tx-amount${t.amount < 0 ? ' tone-good' : ''}`}>{money(t.amount)}</span>
              </button>
            );
          })}
        </FlipList>
      )}
    </section>
  );

  const budgetsCard = summary && (
    <section className="card home-budgets">
      <CardHead title="Budgets">
        <Link className="link" to={`/month/${ymKey(summary)}`}>{monthName(summary.month)}</Link>
      </CardHead>
      <div className="rows">
        {summary.lines
          .filter((l) => l.category.kind === 'expense')
          .map((l) => {
            const spent = l.actual ?? 0;
            const left = l.expected - spent;
            return (
              <Link key={l.category.id} to={`/month/${ymKey(summary)}/c/${l.category.id}`} className="row budget-row">
                <div className="budget-line">
                  <span className="budget-name ellipsis"><CategoryDot category={l.category} />{l.category.name}</span>
                  <span className="budget-of muted">{money(spent)} of {money(l.expected)}</span>
                  <span className={`budget-left ${left < 0 ? 'tone-bad' : 'muted'}`}>
                    {left < 0 ? `${money(-left)} over` : `${money(left)} left`}
                  </span>
                </div>
                <Progress actual={spent} expected={l.expected} thin />
                {left <= -0.005 && <OverBudget name={l.category.name} over={-left} />}
              </Link>
            );
          })}
      </div>
    </section>
  );

  return (
    <Page label="Home" title={title}>
      {showStart && (
        <div className="banner card">
          <CalendarPlus size={20} strokeWidth={1.75} className="banner-icon" />
          <div className="banner-text">
            <strong>{ymLabel(today)} hasn't started yet.</strong>
            <span>Budgets copy forward and recurring items are pre-filled.</span>
          </div>
          <button type="button" className="btn primary" onClick={() => start(today)}>
            Start {monthName(today.month)}
          </button>
        </div>
      )}

      {!summary ? (
        <div className="card">
          <Empty title="No months yet" icon={<CalendarPlus size={28} strokeWidth={1.5} />}>
            Start your first month to begin budgeting.
          </Empty>
        </div>
      ) : (
        <div className="home-grid">
          <div className="home-main">
            <Hero summary={summary} money={money} />
            <div className="tiles">
              <Tile label="Income" value={summary.actual.income} of={summary.expected.income} money={money} />
              <Tile label="Expenses" value={summary.actual.expenses} of={summary.expected.expenses} money={money} />
              <Tile label="Saved" value={summary.actual.saved} of={summary.expected.saved} money={money} />
              <Link to="/networth" className="tile card tile-link">
                <div className="micro">Net worth</div>
                <div className="tile-value"><Num value={nw.netWorth} format={money} /></div>
                <div className="tile-sub tile-trend">
                  {nwChange ? <ChangeLine change={nwChange} label="30d" currency={calc.settings.currency} /> : <>Liquid {money(nw.superLiquid)}</>}
                  <Sparkline points={nwSpark} />
                </div>
              </Link>
            </div>
            {!wide && budgetsCard}
            {recentCard}
          </div>

          {wide && budgetsCard}
        </div>
      )}
    </Page>
  );
}

function Hero({ summary, money }: { summary: MonthSummary; money: MoneyFormat }) {
  const a = summary.actual;
  const total = Math.max(a.income, a.expenses + a.contributions, 1);
  const share = (v: number) => `${(Math.max(0, v) / total) * 100}%`;
  return (
    <section className="card hero">
      <div className="micro">Leftover · {monthName(summary.month)}</div>
      <div className={`hero-num${a.leftover < 0 ? ' tone-bad' : ''}`}>
        <Num value={a.leftover} format={money} />
      </div>
      <div className="hero-sub">of {money(summary.expected.leftover)} planned</div>
      <div className="stack-bar" aria-hidden="true">
        <span className="seg-spent" style={{ width: share(a.expenses) }} />
        <span className="seg-saved" style={{ width: share(a.contributions) }} />
        <span className="seg-left" style={{ width: share(a.leftover) }} />
      </div>
      <div className="stack-legend">
        <span><i className="key spent" />Spent {money(a.expenses)}</span>
        <span><i className="key saved" />Saved {money(a.contributions)}</span>
        <span><i className="key left" />Left {money(a.leftover)}</span>
      </div>
    </section>
  );
}

function Tile({ label, value, of, money }: { label: string; value: number; of: number; money: MoneyFormat }) {
  return (
    <div className="tile card">
      <div className="micro">{label}</div>
      <div className="tile-value"><Num value={value} format={money} /></div>
      <div className="tile-sub">of {money(of)}</div>
    </div>
  );
}
