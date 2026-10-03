import type { ReactNode } from 'react';
import { List, Pencil, TriangleAlert } from 'lucide-react';
import { useData } from '../app/session';
import { diffTone, formatMoney, formatSignedMoney, overBudgetMessage } from '../domain/format';
import type { Category, CategoryKind } from '../domain/types';
import { PALETTE, type PaletteEntry } from '../domain/categoryStyle';

const FALLBACK: PaletteEntry = PALETTE[PALETTE.length - 1]!;

/** One palette symbol (≥12px) in the category colour. Shapes: filled, then 1.75px outlines. */
export function PaletteSymbol({ entry, size = 12 }: { entry: PaletteEntry; size?: number }) {
  const c = entry.hex;
  const stroke = { fill: 'none', stroke: c, strokeWidth: 1.75, strokeLinejoin: 'round' as const, strokeLinecap: 'round' as const };
  let shape;
  switch (entry.symbol) {
    case 'circle': shape = <circle cx={6} cy={6} r={5} fill={c} />; break;
    case 'square': shape = <rect x={1.5} y={1.5} width={9} height={9} rx={1} fill={c} />; break;
    case 'triangle': shape = <path d="M6 1.2 11 10.6H1Z" fill={c} />; break;
    case 'diamond': shape = <path d="M6 .6 11.4 6 6 11.4.6 6Z" fill={c} />; break;
    case 'plus': shape = <path d="M6 1.4v9.2M1.4 6h9.2" {...stroke} strokeWidth={2.2} />; break;
    case 'cross': shape = <path d="m2.2 2.2 7.6 7.6m0-7.6-7.6 7.6" {...stroke} strokeWidth={2.2} />; break;
    case 'ring': shape = <circle cx={6} cy={6} r={4.4} {...stroke} />; break;
    case 'square-ring': shape = <rect x={2} y={2} width={8} height={8} rx={.8} {...stroke} />; break;
    case 'triangle-ring': shape = <path d="M6 2.1 10.3 9.9H1.7Z" {...stroke} />; break;
    case 'diamond-ring': shape = <path d="M6 1.4 10.6 6 6 10.6 1.4 6Z" {...stroke} />; break;
  }
  return <svg className="cat-sym" width={size} height={size} viewBox="0 0 12 12" aria-hidden="true">{shape}</svg>;
}

/** A category's palette entry (tokens.json categoryPalette via categoryAssignment). */
export function useCategoryStyle(category: Category): PaletteEntry {
  return useData().styles.get(category.id) ?? FALLBACK;
}

/** Category marker: palette symbol in the category colour. The label next to it stays in ink. */
export function CategoryDot({ category, size }: { category: Category; size?: number }) {
  return <PaletteSymbol entry={useCategoryStyle(category)} size={size} />;
}

export function TrackingIcon({ category }: { category: Category }) {
  return category.tracking === 'ledger'
    ? <List size={13} strokeWidth={1.75} className="track-icon" aria-label="ledger" />
    : <Pencil size={13} strokeWidth={1.75} className="track-icon" aria-label="manual" />;
}

/** Difference coloured per DOMAIN_RULES §8. */
export function Diff({ kind, value }: { kind: CategoryKind | 'leftover'; value: number }) {
  const { calc } = useData();
  return <span className={`tone-${diffTone(kind, value)}`}>{formatSignedMoney(value, calc.settings.currency)}</span>;
}

/** Over-budget warning with an icon (status is never colour alone). */
export function OverBudget({ name, over }: { name: string; over: number }) {
  const { calc } = useData();
  return (
    <div className="over-budget" role="status">
      <TriangleAlert size={16} strokeWidth={1.75} aria-hidden="true" />
      <span>{overBudgetMessage(name, over, calc.settings.currency)}</span>
    </div>
  );
}

export function Empty({ icon, title, children }: { icon?: ReactNode; title: string; children?: ReactNode }) {
  return (
    <div className="empty">
      {icon}
      <div className="empty-title">{title}</div>
      {children && <div className="empty-body">{children}</div>}
    </div>
  );
}

export function CardHead({ title, children }: { title: ReactNode; children?: ReactNode }) {
  return (
    <div className="card-head">
      <h2 className="card-title">{title}</h2>
      {children && <div className="card-head-actions">{children}</div>}
    </div>
  );
}

export type MoneyFormat = (v: number | null) => string;

/** Formatter bound to the user's currency (for <Num>). */
export function useMoney(): MoneyFormat {
  const { calc } = useData();
  const currency = calc.settings.currency;
  return (v) => formatMoney(v, currency);
}
