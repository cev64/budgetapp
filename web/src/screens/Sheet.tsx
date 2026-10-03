import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState, type CSSProperties, type KeyboardEvent, type MouseEvent } from 'react';
import { Navigate, useNavigate, useParams } from 'react-router';
import { ChevronLeft, ChevronRight, EllipsisVertical, Plus } from 'lucide-react';
import { Page } from '../app/Shell';
import { useActions, useData } from '../app/session';
import { newId } from '../data/actions';
import { sessionGet, sessionSet } from '../data/safeStorage';
import { Seg } from '../ui/Seg';
import { Sheet as Modal } from '../ui/Sheet';
import { toast } from '../ui/Toast';
import { useLayoutMode } from '../ui/hooks';
import { PaletteSymbol, useMoney } from '../ui/bits';
import { Field } from '../ui/controls';
import { buildMonthGrid, buildSummaryGrid, type Cell, type CellMenuItem, type Grid, type SheetDeps } from './sheetGrid';
import { computeNetWorth } from '../domain/networth';
import { byOrder } from '../domain/calc';
import { addMonths, compareYm, currentYm, monthShort, ymLabel } from '../domain/dates';
import type { PaletteEntry } from '../domain/categoryStyle';
import type { Transaction } from '../domain/types';

const TAB_KEY = 'budget.sheetTab';

/** #/sheet → the tab used last in this session, else this month (if it exists) or the year's Summary. */
export function SheetIndex() {
  const { calc } = useData();
  const saved = sessionGet(TAB_KEY);
  if (saved && /^\d{4}\/(summary|\d{1,2})$/.test(saved)) return <Navigate to={`/sheet/${saved}`} replace />;
  const today = currentYm();
  if (calc.month(today)) return <Navigate to={`/sheet/${today.year}/${today.month}`} replace />;
  const year = calc.latestMonth()?.year ?? today.year;
  return <Navigate to={`/sheet/${year}/summary`} replace />;
}

export function SheetScreen() {
  const params = useParams();
  const mode = useLayoutMode();
  const navigate = useNavigate();
  const year = Number(params.year);
  const tab = params.tab === 'summary' ? 'summary' : Number(params.tab);
  const valid = Number.isInteger(year) && (tab === 'summary' || (Number.isInteger(tab) && tab >= 1 && tab <= 12));

  useEffect(() => {
    if (valid) sessionSet(TAB_KEY, `${year}/${tab}`);
  }, [valid, year, tab]);

  if (!valid) return <Navigate to="/sheet" replace />;
  const title = tab === 'summary' ? `${year} summary` : ymLabel({ year, month: tab });

  return (
    <Page label="Sheet" title={title} className="sheet-page">
      {mode === 'compact' ? (
        <div className="card unfold-card arrive">
          <UnfoldArt />
          <h2 className="title">Spreadsheet view needs a wider screen</h2>
          <p className="muted">Open Budget on a computer or tablet, or unfold your phone, to see the sheet view.</p>
          <button type="button" className="btn" onClick={() => navigate('/year')}>Go to Year</button>
        </div>
      ) : (
        <SheetBody year={year} tab={tab} />
      )}
    </Page>
  );
}

/** Two rounded panels opening (drawn once; reduced motion shows it already open). */
function UnfoldArt() {
  return (
    <svg className="unfold-art" width="120" height="84" viewBox="0 0 120 84" aria-hidden="true">
      <rect x="18" y="8" width="40" height="68" rx="8" className="unfold-left" />
      <rect x="62" y="8" width="40" height="68" rx="8" className="unfold-right" />
      <line x1="60" y1="14" x2="60" y2="70" className="unfold-hinge" />
    </svg>
  );
}

function SheetBody({ year, tab }: { year: number; tab: 'summary' | number }) {
  const { ds, calc, styles } = useData();
  const actions = useActions();
  const money = useMoney();
  const navigate = useNavigate();
  const [overriding, setOverridingState] = useState<ReadonlySet<string>>(new Set());
  const [moving, setMoving] = useState<Transaction | null>(null);

  const setOverriding = useCallback((id: string, on: boolean) => {
    setOverridingState((cur) => {
      if (cur.has(id) === on) return cur;
      const next = new Set(cur);
      if (on) next.add(id);
      else next.delete(id);
      return next;
    });
  }, []);

  const deps: SheetDeps = useMemo(() => ({
    calc,
    money,
    actions,
    newId,
    overriding,
    setOverriding,
    notify: (text, undo) => toast(text, undo ? { label: 'Undo', run: undo } : undefined),
    moveToMonth: setMoving,
  }), [calc, money, actions, overriding, setOverriding]);

  const grid = useMemo<Grid | null>(() => {
    if (tab === 'summary') {
      const summary = calc.yearSummary(year);
      if (!summary) return null;
      const nw = computeNetWorth({ accounts: ds.accounts, categories: ds.categories, budgets: ds.budgets, ledger: ds.ledger_entries });
      return buildSummaryGrid(deps, summary, nw, ds.ledger_entries.slice().sort(byOrder));
    }
    if (!calc.month({ year, month: tab })) return null;
    return buildMonthGrid(deps, { year, month: tab });
  }, [deps, calc, ds, year, tab]);

  const years = calc.years();
  const monthsInYear = calc.months.filter((m) => m.year === year);

  const addMonth = async () => {
    const latest = calc.latestMonth();
    const next = latest ? addMonths(latest, 1) : currentYm();
    if (await actions.createMonth(next)) {
      toast(`${ymLabel(next)} created`);
      navigate(`/sheet/${next.year}/${next.month}`);
    }
  };

  const go = (y: number, t: 'summary' | number) => navigate(`/sheet/${y}/${t}`);
  const yearIndex = years.indexOf(year);

  return (
    <div className="sheet-body">
      {grid ? (
        <SheetTable key={`${year}/${tab}`} grid={grid} styles={styles} label={tab === 'summary' ? `${year} summary` : ymLabel({ year, month: tab })} />
      ) : (
        <div className="card sheet-missing">
          <p className="muted">
            {tab === 'summary' ? `No months in ${year} yet.` : `${ymLabel({ year, month: tab })} doesn't exist yet. Use + to create the next month.`}
          </p>
        </div>
      )}

      <div className="sheet-tabbar glass-card">
        <div className="sheet-year" role="group" aria-label="Year">
          <button type="button" className="icon-btn" aria-label="Previous year" disabled={yearIndex <= 0}
            onClick={() => go(years[yearIndex - 1]!, 'summary')}>
            <ChevronLeft size={18} strokeWidth={1.75} />
          </button>
          <span className="sheet-year-label">{year}</span>
          <button type="button" className="icon-btn" aria-label="Next year" disabled={yearIndex < 0 || yearIndex >= years.length - 1}
            onClick={() => go(years[yearIndex + 1]!, 'summary')}>
            <ChevronRight size={18} strokeWidth={1.75} />
          </button>
        </div>
        <div className="sheet-tabs">
          <Seg<string>
            label="Sheet tabs"
            value={String(tab)}
            onChange={(v) => go(year, v === 'summary' ? 'summary' : Number(v))}
            options={[{ value: 'summary', label: 'Summary' }, ...monthsInYear.map((m) => ({ value: String(m.month), label: monthShort(m.month) }))]}
          />
        </div>
        <button type="button" className="icon-btn sheet-add" aria-label="Create the next month" title="New month" onClick={() => void addMonth()}>
          <Plus size={18} strokeWidth={2} />
        </button>
      </div>

      <MoveSheet tx={moving} onClose={() => setMoving(null)} />
    </div>
  );
}

function MoveSheet({ tx, onClose }: { tx: Transaction | null; onClose: () => void }) {
  const { calc } = useData();
  const actions = useActions();
  const [target, setTarget] = useState('');
  const [shown, setShown] = useState<Transaction | null>(null);
  if (tx && tx !== shown) {
    setShown(tx);
    setTarget(`${tx.year}-${tx.month}`);
  }
  const months = calc.months.slice().sort((a, b) => compareYm(b, a));
  return (
    <Modal open={tx != null} onClose={onClose} title="Move to month"
      footer={
        <button type="button" className="btn primary lg grow" onClick={() => {
          const t = tx ?? shown;
          if (!t) return;
          const [y, m] = target.split('-').map(Number);
          onClose();
          if (y && m && (y !== t.year || m !== t.month)) {
            void actions.saveTransaction({ ...t, year: y, month: m }).then((ok) => ok && toast(`Moved to ${ymLabel({ year: y, month: m })}`));
          }
        }}>Move</button>
      }>
      <Field label="Month">
        {(id) => (
          <select id={id} className="input" value={target} onChange={(e) => setTarget(e.target.value)} data-autofocus>
            {months.map((m) => <option key={`${m.year}-${m.month}`} value={`${m.year}-${m.month}`}>{ymLabel(m)}</option>)}
          </select>
        )}
      </Field>
    </Modal>
  );
}

// ---------------------------------------------------------------------------------------------
// The grid: real <table role="grid">, roving tabindex, spreadsheet keys, in-place editors.

interface Pos {
  r: number;
  c: number;
}

const selectable = (cell: Cell | undefined) => cell != null && cell.kind !== 'void';

function SheetTable({ grid, styles, label }: { grid: Grid; styles: Map<string, PaletteEntry>; label: string }) {
  const { rows, columns } = grid;
  const [sel, setSel] = useState<Pos>(() => firstSelectable(grid));
  const [editing, setEditing] = useState<(Pos & { text: string; invalid?: boolean }) | null>(null);
  const [menu, setMenu] = useState<(Pos & { x: number; y: number; items: CellMenuItem[] }) | null>(null);
  const [pendingEdit, setPendingEdit] = useState<Pos | null>(null);
  const tableRef = useRef<HTMLTableElement>(null);
  const wantFocus = useRef(false);
  const scrollRef = useRef<HTMLDivElement>(null);
  const [scrolled, setScrolled] = useState(false);

  const cellAt = (p: Pos) => rows[p.r]?.[p.c];
  const left = useMemo(() => {
    const offsets: number[] = [];
    let x = 0;
    columns.forEach((col, i) => {
      offsets[i] = x;
      if (col.pinned) x += col.width;
    });
    return offsets;
  }, [columns]);
  const lastPinned = columns.reduce((last, col, i) => (col.pinned ? i : last), -1);

  const focusCell = useCallback((p: Pos) => {
    const el = tableRef.current?.querySelector<HTMLElement>(`[data-r="${p.r}"][data-c="${p.c}"]`);
    el?.focus({ preventScroll: false });
  }, []);

  useLayoutEffect(() => {
    if (wantFocus.current && !editing) {
      wantFocus.current = false;
      focusCell(sel);
    }
  }, [sel, editing, focusCell]);

  // After "Override actual" the cell becomes editable on the next render: open its editor.
  useEffect(() => {
    if (!pendingEdit) return;
    const cell = cellAt(pendingEdit);
    if (cell?.edit) {
      setEditing({ ...pendingEdit, text: cell.edit.initial });
      setPendingEdit(null);
    }
  });

  const moveFrom = (p: Pos, dr: number, dc: number): Pos | null => {
    let r = p.r + dr;
    let c = p.c + dc;
    while (r >= 0 && r < rows.length && c >= 0 && c < columns.length) {
      if (selectable(rows[r]![c])) return { r, c };
      r += dr;
      c += dc;
    }
    return null;
  };

  const select = (p: Pos | null, focus = true) => {
    if (!p) return false;
    wantFocus.current = focus;
    setSel(p);
    return true;
  };

  const startEdit = (p: Pos, text?: string) => {
    const cell = cellAt(p);
    if (!cell?.edit) return false;
    setMenu(null);
    setSel(p);
    setEditing({ ...p, text: text ?? cell.edit.initial });
    return true;
  };

  const commit = (move: [number, number] | null) => {
    if (!editing) return;
    const cell = cellAt(editing);
    const ok = cell?.edit ? cell.edit.commit(editing.text) : true;
    if (!ok) {
      setEditing({ ...editing, invalid: true });
      return;
    }
    const from: Pos = { r: editing.r, c: editing.c };
    setEditing(null);
    wantFocus.current = true;
    if (move) setSel(moveFrom(from, move[0], move[1]) ?? from);
    else setSel(from);
  };

  const cancel = () => {
    setEditing(null);
    wantFocus.current = true;
    setSel({ ...sel });
  };

  const openMenu = (p: Pos, x: number, y: number) => {
    const items = cellAt(p)?.menu;
    if (!items?.length) return;
    setSel(p);
    setMenu({ ...p, x, y, items });
  };

  const onKeyDown = (e: KeyboardEvent<HTMLTableElement>) => {
    if (editing || menu) return;
    const cell = cellAt(sel);
    const k = e.key;
    const moves: Record<string, [number, number]> = { ArrowUp: [-1, 0], ArrowDown: [1, 0], ArrowLeft: [0, -1], ArrowRight: [0, 1] };
    if (moves[k]) {
      e.preventDefault();
      select(moveFrom(sel, ...moves[k]!));
    } else if (k === 'Tab') {
      if (select(moveFrom(sel, 0, e.shiftKey ? -1 : 1))) e.preventDefault();
    } else if (k === 'Enter' || k === 'F2') {
      e.preventDefault();
      if (cell?.check) cell.check.toggle();
      else if (cell?.edit) startEdit(sel);
      else if (cell?.menu) openMenuAtCell(sel);
    } else if (k === ' ' && cell?.check) {
      e.preventDefault();
      cell.check.toggle();
    } else if (k === 'ContextMenu' || (k === 'F10' && e.shiftKey)) {
      e.preventDefault();
      openMenuAtCell(sel);
    } else if (k.length === 1 && !e.ctrlKey && !e.metaKey && !e.altKey && cell?.edit && cell.edit.type !== 'date') {
      e.preventDefault();
      startEdit(sel, k);
    }
  };

  const openMenuAtCell = (p: Pos) => {
    const el = tableRef.current?.querySelector<HTMLElement>(`[data-r="${p.r}"][data-c="${p.c}"]`);
    const r = el?.getBoundingClientRect();
    if (r) openMenu(p, r.right - 8, r.bottom);
  };

  return (
    <div className="sheet-card card">
      <div ref={scrollRef} className={`sheet-scroll${scrolled ? ' scrolled' : ''}`} onScroll={(e) => setScrolled(e.currentTarget.scrollLeft > 0)}>
        <table ref={tableRef} className="sheet-grid" role="grid" aria-label={label} onKeyDown={onKeyDown}
          style={{ width: columns.reduce((w, col) => w + col.width, 0) }}>
          <colgroup>
            {columns.map((col) => <col key={col.key} style={{ width: col.width }} />)}
          </colgroup>
          <tbody>
            {rows.map((row, r) => (
              <tr key={r} role="row" aria-rowindex={grid.rowNumbers[r]}>
                {row.map((cell, c) => {
                  const col = columns[c]!;
                  const isSel = sel.r === r && sel.c === c;
                  const isEditing = editing?.r === r && editing.c === c;
                  const style: CSSProperties | undefined = col.pinned ? { left: left[c] } : undefined;
                  if (cell.kind === 'void') {
                    return <td key={c} role="presentation" className={`sc void${col.pinned ? ' pinned' : ''}${c === lastPinned ? ' pin-edge' : ''}`} style={style} />;
                  }
                  const cls = [
                    'sc', cell.kind, cell.align ?? 'left',
                    cell.computed ? 'computed' : '', cell.edit || cell.check ? 'editable' : '',
                    cell.strong ? 'strong' : '', cell.totalTop ? 'total-top' : '', cell.ghost ? 'ghost' : '', cell.lead ? 'lead' : '',
                    cell.tone ? `tone-${cell.tone}` : '', cell.menuButton ? 'has-menu' : '', cell.marker ? 'marked' : '', isSel ? 'sel' : '', isEditing ? 'editing' : '',
                    col.pinned ? 'pinned' : '', c === lastPinned ? 'pin-edge' : '',
                  ].filter(Boolean).join(' ');
                  return (
                    <td
                      key={c}
                      role={cell.kind === 'header' ? 'columnheader' : 'gridcell'}
                      className={cls}
                      style={style}
                      data-r={r}
                      data-c={c}
                      tabIndex={isSel && !isEditing ? 0 : -1}
                      aria-selected={isSel}
                      aria-readonly={!(cell.edit || cell.check)}
                      title={[cell.marker === 'override' ? 'Override (typed actual)' : cell.marker === 'auto' ? 'Auto' : '', cell.computed ? 'Calculated' : '', cell.hint ?? (!cell.computed && (cell.kind === 'value' || cell.kind === 'label') ? cell.text : '')].filter(Boolean).join(' · ') || undefined}
                      onMouseDown={(e: MouseEvent) => {
                        if (e.button !== 0 || isEditing) return;
                        if ((e.target as HTMLElement).closest('.sc-menu')) return;
                        if (cell.check) {
                          setSel({ r, c });
                          cell.check.toggle();
                        } else if (cell.edit) {
                          e.preventDefault();
                          startEdit({ r, c });
                        } else {
                          setSel({ r, c });
                        }
                      }}
                      onContextMenu={(e) => {
                        if (!cell.menu?.length) return;
                        e.preventDefault();
                        openMenu({ r, c }, e.clientX, e.clientY);
                      }}
                    >
                      {isEditing && editing ? (
                        <CellEditor
                          cell={cell}
                          text={editing.text}
                          invalid={editing.invalid}
                          onChange={(text) => setEditing({ ...editing, text, invalid: false })}
                          onCommit={commit}
                          onCancel={cancel}
                        />
                      ) : (
                        <CellContent cell={cell} styles={styles} onMenu={(x, y) => openMenu({ r, c }, x, y)} />
                      )}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {menu && (
        <CellMenu
          x={menu.x}
          y={menu.y}
          items={menu.items}
          onClose={() => {
            setMenu(null);
            wantFocus.current = true;
            setSel({ r: menu.r, c: menu.c });
          }}
          onRun={(item) => {
            const at = { r: menu.r, c: menu.c };
            setMenu(null);
            item.run();
            if (item.label === 'Override actual') setPendingEdit(at);
            else {
              wantFocus.current = true;
              setSel(at);
            }
          }}
        />
      )}
    </div>
  );
}

function firstSelectable(grid: Grid): Pos {
  for (let r = 0; r < grid.rows.length; r++) {
    for (let c = 0; c < grid.columns.length; c++) {
      const cell = grid.rows[r]![c];
      if (cell?.edit || cell?.check) return { r, c };
    }
  }
  return { r: 0, c: 0 };
}

function CellContent({ cell, styles, onMenu }: { cell: Cell; styles: Map<string, PaletteEntry>; onMenu: (x: number, y: number) => void }) {
  const entry = cell.categoryId ? styles.get(cell.categoryId) : undefined;
  if (cell.check) {
    return (
      <span className={`sc-check${cell.check.checked ? ' on' : ''}`} role="checkbox" aria-checked={cell.check.checked} aria-label={cell.check.label}>
        {cell.check.checked && (
          <svg width="14" height="14" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12.5 10 17 19 7" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" /></svg>
        )}
      </span>
    );
  }
  return (
    <span className="sc-inner">
      {entry && <PaletteSymbol entry={entry} size={cell.kind === 'title' ? 13 : 12} />}
      <span className="sc-text">{cell.text}</span>
      {cell.marker && <span className="sr-only">({cell.marker})</span>}
      {cell.menu?.length && cell.menuButton ? (
        <button
          type="button"
          className="sc-menu"
          tabIndex={-1}
          aria-label="Cell options"
          onMouseDown={(e) => e.stopPropagation()}
          onClick={(e) => {
            const r = e.currentTarget.getBoundingClientRect();
            onMenu(r.right, r.bottom);
          }}
        >
          <EllipsisVertical size={14} strokeWidth={1.75} />
        </button>
      ) : null}
    </span>
  );
}

function CellEditor({ cell, text, invalid, onChange, onCommit, onCancel }: {
  cell: Cell;
  text: string;
  invalid?: boolean;
  onChange: (t: string) => void;
  onCommit: (move: [number, number] | null) => void;
  onCancel: () => void;
}) {
  const ref = useRef<HTMLInputElement>(null);
  const done = useRef(false);
  const type = cell.edit!.type;
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    el.focus({ preventScroll: true });
    if (type !== 'date') {
      const end = el.value.length;
      // A typed first character replaces the content; Enter/click keeps it and selects all.
      if (text.length === 1 && cell.edit!.initial !== text) el.setSelectionRange(end, end);
      else el.select();
    }
  }, []);
  const finish = (move: [number, number] | null) => {
    if (done.current) return;
    onCommit(move);
  };
  return (
    <input
      ref={ref}
      className={`sc-editor${type === 'money' ? ' money' : ''}${invalid ? ' invalid' : ''}`}
      type={type === 'date' ? 'date' : 'text'}
      inputMode={type === 'money' ? 'decimal' : undefined}
      value={text}
      placeholder={cell.edit!.placeholder}
      aria-label={cell.edit!.placeholder ?? 'Edit cell'}
      aria-invalid={invalid}
      onChange={(e) => onChange(e.target.value)}
      onKeyDown={(e) => {
        e.stopPropagation();
        if (e.key === 'Enter') {
          e.preventDefault();
          finish([1, 0]);
        } else if (e.key === 'Tab') {
          e.preventDefault();
          finish([0, e.shiftKey ? -1 : 1]);
        } else if (e.key === 'Escape') {
          e.preventDefault();
          done.current = true;
          onCancel();
        }
      }}
      onBlur={() => finish(null)}
    />
  );
}

function CellMenu({ x, y, items, onClose, onRun }: { x: number; y: number; items: CellMenuItem[]; onClose: () => void; onRun: (i: CellMenuItem) => void }) {
  const ref = useRef<HTMLDivElement>(null);
  const [pos, setPos] = useState({ left: x, top: y });
  const [open, setOpen] = useState(false);
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    const r = el.getBoundingClientRect();
    setPos({ left: Math.max(12, Math.min(x - r.width, window.innerWidth - r.width - 12)), top: Math.min(y + 4, window.innerHeight - r.height - 12) });
    requestAnimationFrame(() => setOpen(true));
    el.querySelector<HTMLElement>('button')?.focus();
  }, [x, y]);
  useEffect(() => {
    const down = (e: PointerEvent) => {
      if (!ref.current?.contains(e.target as Node)) onClose();
    };
    const key = (e: globalThis.KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.stopPropagation();
        onClose();
      }
    };
    document.addEventListener('pointerdown', down);
    document.addEventListener('keydown', key, true);
    return () => {
      document.removeEventListener('pointerdown', down);
      document.removeEventListener('keydown', key, true);
    };
  }, [onClose]);
  return (
    <div ref={ref} role="menu" className={`menu cell-menu${open ? ' open' : ''}`} style={{ left: pos.left, top: pos.top }}
      onKeyDown={(e) => {
        const btns = [...(ref.current?.querySelectorAll<HTMLElement>('button') ?? [])];
        const i = btns.indexOf(document.activeElement as HTMLElement);
        if (e.key === 'ArrowDown') { e.preventDefault(); btns[(i + 1) % btns.length]?.focus(); }
        if (e.key === 'ArrowUp') { e.preventDefault(); btns[(i - 1 + btns.length) % btns.length]?.focus(); }
      }}>
      {items.map((item, i) => (
        <div key={item.label} style={{ '--i': i } as CSSProperties}>
          <button type="button" role="menuitem" className={`menu-item${item.warn ? ' warn' : ''}`} onClick={() => onRun(item)}>
            {item.label}
          </button>
        </div>
      ))}
    </div>
  );
}
