import { Trash2 } from 'lucide-react';
import { useActions, useData, useSheets } from '../app/session';
import { CategoryDot, useMoney } from '../ui/bits';
import { SwipeRow } from '../ui/SwipeRow';
import { toast } from '../ui/Toast';
import { haptic } from '../ui/motion';
import { shortDate, ymLabel } from '../domain/dates';
import type { Transaction } from '../domain/types';

/** Deletes a transaction with a haptic tick and an Undo toast (no confirm: Undo is the safety net). */
export function useDeleteTransaction() {
  const actions = useActions();
  return async (t: Transaction) => {
    if (!(await actions.deleteTransaction(t))) return;
    haptic(9);
    toast('Transaction deleted', { label: 'Undo', run: () => void actions.saveTransaction(t) });
  };
}

/**
 * One transaction, kept minimal (docs/FLUID_GLASS_UI.md §8): item over "● Category · date", the amount
 * on the right (refunds in good, with their minus sign). Tap opens the edit sheet; on touch, swipe left
 * to delete.
 */
export function TxRow({ t, showCategory = true }: { t: Transaction; showCategory?: boolean }) {
  const { calc } = useData();
  const { editTransaction } = useSheets();
  const money = useMoney();
  const remove = useDeleteTransaction();
  const cat = showCategory ? calc.categoryById.get(t.category_id) : undefined;
  const when = t.date ? shortDate(t.date) : ymLabel(t);
  return (
    <SwipeRow k={t.id} left={{ label: 'Delete', icon: <Trash2 size={18} strokeWidth={2} />, tone: 'bad', run: () => void remove(t) }}>
      <button type="button" className="row tx-row" onClick={() => editTransaction(t)}>
        <span className="tx-main">
          <span className="tx-item ellipsis">{t.item || <span className="muted">No description</span>}</span>
          <span className="tx-meta">
            {cat && <span className="tx-cat"><CategoryDot category={cat} size={10} /><span className="ellipsis">{cat.name}</span></span>}
            {cat && <span aria-hidden="true">·</span>}
            <span className="tx-when">{when}</span>
          </span>
        </span>
        <span className={`tx-amount${t.amount < 0 ? ' tone-good' : ''}`}>{money(t.amount)}</span>
      </button>
    </SwipeRow>
  );
}
