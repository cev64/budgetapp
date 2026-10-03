import { createContext, useContext, useMemo, useSyncExternalStore } from 'react';
import type { Store, StoreState } from '../data/store';
import type { Actions } from '../data/actions';
import { createCalc, type Calc } from '../domain/calc';
import { categoryStyles, type PaletteEntry } from '../domain/categoryStyle';
import type { Dataset, Transaction, YM } from '../domain/types';

export interface AppSession {
  mode: 'supabase' | 'demo';
  userId: string;
  email: string;
  store: Store;
  actions: Actions;
  signOut: () => Promise<void>;
}

export const SessionContext = createContext<AppSession | null>(null);

export function useSession(): AppSession {
  const s = useContext(SessionContext);
  if (!s) throw new Error('useSession outside of a session');
  return s;
}

export function useStoreState(): StoreState {
  const { store } = useSession();
  return useSyncExternalStore(store.subscribe, store.getState);
}

/** Live rows, the domain calculator and category styles, recomputed only when the data changes. */
export function useData(): { ds: Dataset; calc: Calc; styles: Map<string, PaletteEntry> } {
  const { store } = useSession();
  const { tables } = useStoreState();
  return useMemo(() => {
    const ds = store.dataset();
    return { ds, calc: createCalc(ds), styles: categoryStyles(ds.categories) };
  }, [tables, store]);
}

export function useActions(): Actions {
  return useSession().actions;
}

/** Opens the add / edit transaction sheet from anywhere. */
export interface SheetApi {
  openAdd: (defaults?: { ym?: YM; categoryId?: string }) => void;
  editTransaction: (t: Transaction) => void;
}

export const SheetContext = createContext<SheetApi>({ openAdd: () => {}, editTransaction: () => {} });
export const useSheets = () => useContext(SheetContext);
