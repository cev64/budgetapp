import { useMemo, useState } from 'react';
import { Pencil } from 'lucide-react';
import { useData } from '../app/session';
import { Seg } from '../ui/Seg';
import { Num } from '../ui/Num';
import { Sheet } from '../ui/Sheet';
import { HistoryChart, dateLabel } from '../ui/HistoryChart';
import { useLayoutMode } from '../ui/hooks';
import { useMoney, type MoneyFormat } from '../ui/bits';
import { localGet, localSet } from '../data/safeStorage';
import { formatSignedMoney } from '../domain/format';
import {
  accountSeries, changeOver, rangePoints, RANGE_DAYS, RANGES, seriesOf, sortSnapshots, type Change, type RangeKey,
} from '../domain/history';
import type { AccountView } from '../domain/networth';

const RANGE_KEY = 'budget.nwRange';
const readRange = (): RangeKey => {
  const v = localGet(RANGE_KEY);
  return (RANGES as string[]).includes(v ?? '') ? (v as RangeKey) : '3M';
};

/** Live snapshots sorted by day, memoised on the data. */
export function useSnapshots() {
  const { ds } = useData();
  return useMemo(() => sortSnapshots(ds.net_worth_snapshots), [ds.net_worth_snapshots]);
}

/** "+$1,240 · 3M" (or "since Aug 30") in good/bad. */
export function ChangeLine({ change, label, currency }: { change: Change | null; label: string; currency: string }) {
  if (!change) return null;
  const tone = Math.round(change.change * 100) === 0 ? 'neutral' : change.change > 0 ? 'good' : 'bad';
  return (
    <span className={`change tone-${tone}`}>
      {formatSignedMoney(change.change, currency)}
      <span className="change-label"> · {change.since ? `since ${dateLabel(change.from.date, false)}` : label}</span>
    </span>
  );
}

/** Net worth hero: live value, change for the selected range, range control, history chart. */
export function NetWorthHero({ value, money }: { value: number; money: MoneyFormat }) {
  const { calc } = useData();
  const snaps = useSnapshots();
  const expanded = useLayoutMode() === 'expanded';
  const [range, setRange] = useState<RangeKey>(readRange);
  const [overlay, setOverlay] = useState(false);
  const days = RANGE_DAYS[range];
  const all = useMemo(() => seriesOf(snaps, 'net_worth'), [snaps]);
  const liquid = useMemo(() => seriesOf(snaps, 'super_liquid'), [snaps]);
  const points = useMemo(() => rangePoints(all, days), [all, days]);
  const liquidPoints = useMemo(() => rangePoints(liquid, days), [liquid, days]);
  const change = changeOver(all, days);
  const enough = all.length >= 2;

  return (
    <section className="card hero nw-hero">
      <div className="nw-hero-head">
        <div>
          <div className="micro">Net worth</div>
          <div className={`hero-num${value < 0 ? ' tone-bad' : ''}`}><Num value={value} format={money} /></div>
          <div className="hero-sub">
            {enough ? <ChangeLine change={change} label={range} currency={calc.settings.currency} /> : 'Accounts plus open IOUs'}
          </div>
        </div>
        {enough && (
          <Seg<RangeKey> className="range-seg" label="History range" value={range}
            onChange={(r) => { setRange(r); localSet(RANGE_KEY, r); }}
            options={RANGES.map((r) => ({ value: r, label: r }))} />
        )}
      </div>
      {enough ? (
        <>
          <div key={range} className="panel on">
            <HistoryChart points={points} overlay={overlay ? liquidPoints : undefined} overlayLabel="Super liquid"
              label={`Net worth history, ${range}`} format={money} height={expanded ? 240 : 180} />
          </div>
          <div className="nw-hero-foot">
            <button type="button" className={`pick chip${overlay ? ' on' : ''}`} aria-pressed={overlay} onClick={() => setOverlay((o) => !o)}>
              <i className="key overlay" />Super liquid
            </button>
            <span className="muted small">{all.length} daily snapshots</span>
          </div>
        </>
      ) : (
        <p className="muted small nw-empty">History starts today, a point is saved every day.</p>
      )}
    </section>
  );
}

/** Balance history of one account (tap an account on the Net worth screen). */
export function AccountHistorySheet({ view, onClose, onEdit }: { view: AccountView | null; onClose: () => void; onEdit: () => void }) {
  const { calc } = useData();
  const money = useMoney();
  const snaps = useSnapshots();
  const [range, setRange] = useState<RangeKey>('All');
  const [shown, setShown] = useState<AccountView | null>(view);
  if (view && view !== shown) setShown(view);
  const v = view ?? shown;
  const series = useMemo(() => (v ? accountSeries(snaps, v.account.id) : []), [snaps, v]);
  const days = RANGE_DAYS[range];
  const points = useMemo(() => rangePoints(series, days), [series, days]);
  const change = changeOver(series, days);

  return (
    <Sheet open={view != null} onClose={onClose} title={v?.account.name ?? ''} wide
      footer={<button type="button" className="btn" onClick={onEdit}><Pencil size={16} strokeWidth={1.75} /> Edit account</button>}>
      {v && (
        <div className="form-stack">
          <div>
            <div className="micro">Balance</div>
            <div className={`hl-big${v.balance < 0 ? ' tone-bad' : ''}`}>{money(v.balance)}</div>
            {series.length >= 2 && <ChangeLine change={change} label={range} currency={calc.settings.currency} />}
          </div>
          {series.length >= 2 ? (
            <>
              <Seg<RangeKey> className="fill" label="History range" value={range} onChange={setRange}
                options={RANGES.map((r) => ({ value: r, label: r }))} />
              <div key={range} className="panel on">
                <HistoryChart points={points} label={`${v.account.name} balance history`} format={money} height={180} />
              </div>
            </>
          ) : (
            <p className="muted small">History starts today, a point is saved every day.</p>
          )}
        </div>
      )}
    </Sheet>
  );
}
