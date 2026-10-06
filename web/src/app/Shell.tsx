import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { NavLink, Outlet, useLocation, useSearchParams } from 'react-router';
import { CalendarDays, ChartColumn, House, Landmark, Plus, Settings, Sheet as SheetIcon, type LucideIcon } from 'lucide-react';
import { SheetContext, useSession, useSheets, useStoreState, type SheetApi } from './session';
import { TransactionSheet, type TxSheetState } from '../screens/TransactionSheet';
import { useGlide, useLayoutMode } from '../ui/hooks';
import { sheetOpen } from '../ui/Sheet';
import { BrandLockup, BrandMark } from '../ui/Brand';

interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  end?: boolean;
}

/** Same names, same order as Android (PRODUCT_SPEC, UI_ANATOMY): five destinations. */
const NAV: NavItem[] = [
  { to: '/', label: 'Home', icon: House, end: true },
  { to: '/month', label: 'Month', icon: CalendarDays },
  { to: '/year', label: 'Year', icon: ChartColumn },
  { to: '/networth', label: 'Net worth', icon: Landmark },
  { to: '/sheet', label: 'Sheet', icon: SheetIcon },
];

const ICON = { size: 20, strokeWidth: 1.75 } as const;

/** Add-sheet defaults from the route: the month (and category) being viewed. */
function addDefaultsFor(pathname: string): Parameters<SheetApi['openAdd']>[0] {
  const m = /^\/month\/(\d{4})-(\d{2})(?:\/c\/([^/]+))?/.exec(pathname);
  const sheet = /^\/sheet\/(\d{4})\/(\d{1,2})$/.exec(pathname);
  if (sheet) return { ym: { year: Number(sheet[1]), month: Number(sheet[2]) } };
  return m ? { ym: { year: Number(m[1]), month: Number(m[2]) }, categoryId: m[3] } : {};
}

function isTyping(el: EventTarget | null): boolean {
  if (!(el instanceof HTMLElement)) return false;
  return el.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(el.tagName);
}

export function Shell() {
  const mode = useLayoutMode();
  const [sheet, setSheet] = useState<TxSheetState>({ open: false });
  const [params, setParams] = useSearchParams();

  const api = useMemo<SheetApi>(
    () => ({
      openAdd: (defaults) => setSheet({ open: true, defaults }),
      editTransaction: (t) => setSheet({ open: true, editing: t }),
    }),
    [],
  );

  // The month being viewed is the default month of the add sheet.
  const location = useLocation();
  const addDefaultsRef = useRef(addDefaultsFor(location.pathname));
  addDefaultsRef.current = addDefaultsFor(location.pathname);

  // Keyboard shortcut N opens the add sheet.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key.toLowerCase() !== 'n' || e.metaKey || e.ctrlKey || e.altKey || e.repeat) return;
      if (isTyping(e.target) || sheetOpen()) return;
      e.preventDefault();
      api.openAdd(addDefaultsRef.current);
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [api]);

  // PWA shortcut / deep link: #/?add=1
  useEffect(() => {
    if (params.get('add') === '1') {
      api.openAdd();
      params.delete('add');
      setParams(params, { replace: true });
    }
  }, [params, setParams, api]);

  // The Sheet wants every pixel: the 220px side nav collapses to the 80px rail there (restored on leaving).
  const navMode = useNavMode();
  const openAdd = useCallback(() => api.openAdd(addDefaultsRef.current), [api]);

  return (
    <SheetContext.Provider value={api}>
      <div className={`shell ${navMode}`}>
        {navMode !== 'compact' && <SideNav mode={navMode} onAdd={openAdd} />}
        <div className="main">
          <Outlet />
        </div>
        {mode === 'compact' && <BottomNav onAdd={openAdd} />}
      </div>
      <TransactionSheet state={sheet} onClose={() => setSheet((s) => ({ ...s, open: false }))} />
    </SheetContext.Provider>
  );
}

function SideNav({ mode, onAdd }: { mode: 'medium' | 'expanded'; onAdd: () => void }) {
  const ref = useRef<HTMLDivElement>(null);
  const { pathname } = useLocation();
  useGlide(ref, `${pathname}:${mode}`, { vertical: true, cls: 'nav-ind' });
  return (
    <nav className={`side-nav ${mode}`} aria-label="Main">
      <NavLink to="/" className="side-brand" aria-label="Budget home">
        {mode === 'expanded' ? <BrandLockup label="" /> : <BrandMark size={32} label="" />}
      </NavLink>
      {mode === 'medium' && (
        <button type="button" className="rail-add" onClick={onAdd} aria-label="Add transaction (N)" title="Add transaction (N)">
          <Plus size={22} strokeWidth={2} />
        </button>
      )}
      <div ref={ref} className="nav-list">
        {NAV.map((n) => (
          <NavLink key={n.to} to={n.to} end={n.end} className="nav-item" title={mode === 'medium' ? n.label : undefined}>
            <n.icon {...ICON} />
            <span>{n.label}</span>
          </NavLink>
        ))}
      </div>
    </nav>
  );
}

/** Phones: a detached floating glass pill (five destinations) with a gliding indicator, Add beside it. */
function BottomNav({ onAdd }: { onAdd: () => void }) {
  const ref = useRef<HTMLDivElement>(null);
  const { pathname } = useLocation();
  useGlide(ref, pathname, { cls: 'nav-ind' });
  return (
    <div className="dock">
      <nav className="bottom-nav" aria-label="Main">
        <div ref={ref} className="nav-list">
          {NAV.map((n) => (
            <NavLink key={n.to} to={n.to} end={n.end} className="nav-item">
              <n.icon {...ICON} />
              <span>{n.label}</span>
            </NavLink>
          ))}
        </div>
      </nav>
      <button type="button" className="fab" onClick={onAdd} aria-label="Add transaction">
        <Plus size={24} strokeWidth={2} />
      </button>
    </div>
  );
}

interface PageProps {
  /** Micro-label above the title. */
  label: string;
  title: ReactNode;
  /** Extra controls on the right of the top bar (before the sync dot). */
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
}

/** Layout mode for navigation: like useLayoutMode, but the Sheet route uses the rail instead of the side nav. */
function useNavMode() {
  const mode = useLayoutMode();
  const { pathname } = useLocation();
  return mode === 'expanded' && pathname.startsWith('/sheet') ? 'medium' : mode;
}

/**
 * Screen frame. A large title block scrolls with the content; once it passes under the 52px top bar,
 * the bar turns to glass and a compact title fades in (docs/FLUID_GLASS_UI.md §5).
 */
export function Page({ label, title, actions, children, className }: PageProps) {
  const sentinel = useRef<HTMLDivElement>(null);
  const [condensed, setCondensed] = useState(false);
  const navMode = useNavMode();
  const { openAdd } = useSheets();
  const location = useLocation();
  useEffect(() => {
    const el = sentinel.current;
    if (!el) return;
    const io = new IntersectionObserver(([entry]) => setCondensed(!entry!.isIntersecting), { rootMargin: '-52px 0px 0px 0px' });
    io.observe(el);
    return () => io.disconnect();
  }, []);

  return (
    <>
      <header className={`topbar${condensed ? ' condensed' : ''}`}>
        <div className="topbar-inner">
          <div className="topbar-compact" aria-hidden={!condensed}>{title}</div>
          <div className="topbar-actions">
            {actions}
            <SyncIndicator />
            {navMode === 'expanded' && (
              <button
                type="button"
                className="btn primary add-top"
                title="Add transaction (N)"
                onClick={() => openAdd(addDefaultsFor(location.pathname))}
              >
                <Plus size={16} strokeWidth={2} /> Add
              </button>
            )}
            <NavLink to="/settings" className="icon-btn" aria-label="Settings" title="Settings">
              <Settings size={20} strokeWidth={1.75} />
            </NavLink>
          </div>
        </div>
      </header>
      <main className={`page panel on${className ? ` ${className}` : ''}`}>
        <div className="page-head">
          <div className="micro">{label}</div>
          <h1 className="display-title">{title}</h1>
          <div ref={sentinel} className="top-sentinel" aria-hidden="true" />
        </div>
        {children}
      </main>
    </>
  );
}

/** Sync status dot (green synced, pulsing accent syncing, amber offline) or the demo pill. */
function SyncIndicator() {
  const { mode, store } = useSession();
  const { status, lastSync } = useStoreState();
  if (mode === 'demo') return <span className="pill demo-pill">Demo data</span>;
  const label =
    status === 'synced' ? `Synced${lastSync ? ` · ${new Date(lastSync).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}` : ''}`
      : status === 'syncing' ? 'Syncing…' : 'Offline · changes may not be saved';
  return (
    <button type="button" className="sync-btn" title={`${label}. Click to sync now.`} aria-label={label} onClick={() => void store.refresh(false)}>
      <span className={`sync-dot ${status}${status === 'syncing' ? ' live-dot' : ''}`} />
    </button>
  );
}
