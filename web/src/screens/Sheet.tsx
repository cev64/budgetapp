import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState, type ClipboardEvent, type CSSProperties, type KeyboardEvent, type MouseEvent } from 'react';
import { Navigate, useNavigate, useParams } from 'react-router';
import { ChevronLeft, ChevronRight, EllipsisVertical, Plus } from 'lucide-react';
import { Page } from '../app/Shell';
import { useActions, useData, useSession } from '../app/session';
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
  const { store } = useSession();
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

  // Undo steps for this sheet (Ctrl/Cmd+Z), newest last; a paste is one step. Cleared on tab change.
  const undoStack = useRef<(() => void)[][]>([]);
  const undoGroup = useRef<(() => void)[] | null>(null);
  const created = useRef<{ addKey: string; id: string } | null>(null);
  useEffect(() => {
    undoStack.current = [];
  }, [year, tab]);
  const undo = useMemo<UndoApi>(() => ({
    push: (fn) => {
      if (undoGroup.current) undoGroup.current.push(fn);
      else undoStack.current = [...undoStack.current.slice(-99), [fn]];
    },
    begin: () => { undoGroup.current = []; },
    end: () => {
      const group = undoGroup.current;
      undoGroup.current = null;
      if (group?.length) undoStack.current = [...undoStack.current.slice(-99), group];
    },
    undo: () => {
      const step = undoStack.current.pop();
      if (!step) return false;
      step.slice().reverse().forEach((fn) => fn());
      return true;
    },
  }), []);

  const deps: SheetDeps = useMemo(() => ({
    calc,
    money,
    actions,
    newId,
    overriding,
    setOverriding,
    notify: (text, undo) => toast(text, undo ? { label: 'Undo', run: undo } : undefined),
    moveToMonth: setMoving,
    pushUndo: undo.push,
    onCreate: (addKey, id) => { created.current = { addKey, id }; },
    fresh: (table, row) => (store.row(table, row.id) as typeof row | undefined) ?? row,
  }), [calc, money, actions, overriding, setOverriding, undo, store]);

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
        <SheetTable key={`${year}/${tab}`} grid={grid} styles={styles} label={tab === 'summary' ? `${year} summary` : ymLabel({ year, month: tab })}
          undo={undo} created={created} />
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

      <MoveSheet tx={moving} onClose={() => setMoving(null)} onMoved={(before) => undo.push(() => void actions.saveTransaction(before))} />
    </div>
  );
}

function MoveSheet({ tx, onClose, onMoved }: { tx: Transaction | null; onClose: () => void; onMoved: (before: Transaction) => void }) {
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
            void actions.saveTransaction({ ...t, year: y, month: m }).then((ok) => ok && toast(`Moved to ${ymLabel({ year: y, month: m })}`, { label: 'Undo', run: () => void actions.saveTransaction(t) }));
            onMoved(t);
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
//
// Click selects, a second click / double-click / Enter / typing edits (one tap on touch, and the "+ Add"
// row edits on the first click). Delete clears, Ctrl/Cmd+Z undoes, Ctrl/Cmd+C/V copy and paste (a
// block of lines pasted into an add row becomes one row per line). The selection is tracked by cell key,
// so it stays on its cell when rows are added, removed or re-sorted.

interface Pos {
  r: number;
  c: number;
}

export interface UndoApi {
  push: (undo: () => void) => void;
  /** Collects every push until `end` into one undo step (a paste). */
  begin: () => void;
  end: () => void;
  undo: () => boolean;
}

const selectable = (cell: Cell | undefined) => cell != null && cell.kind !== 'void';

function findKey(grid: Grid, key: string): Pos | null {
  for (let r = 0; r < grid.rows.length; r++) {
    const c = grid.rows[r]!.findIndex((cell) => cell.key === key);
    if (c >= 0) return { r, c };
  }
  return null;
}

/** Add-row keys are "add:<scope>:<field>"; the row it creates uses "<tx|iou>:<id>:<field>". */
function createdKey(addKey: string, id: string, field: string): string {
  const scope = addKey.split(':')[1];
  return `${scope === 'iou' ? 'iou' : 'tx'}:${id}:${field}`;
}

/** Tab/newline separated clipboard text → lines of fields (a trailing newline is not a line). */
function parseClipboard(text: string): string[][] {
  const lines = text.replace(/\r\n?/g, '\n').split('\n');
  if (lines.length > 1 && lines[lines.length - 1] === '') lines.pop();
  return lines.map((l) => l.split('\t'));
}

function copyText(cell: Cell): string {
  if (cell.check) return cell.check.checked ? 'TRUE' : 'FALSE';
  if (cell.edit && !cell.ghost) return cell.edit.initial;
  return cell.ghost ? '' : cell.text ?? '';
}

function SheetTable({ grid, styles, label, undo, created }: {
  grid: Grid;
  styles: Map<string, PaletteEntry>;
  label: string;
  undo: UndoApi;
  /** Set by deps.onCreate while a commit runs: the add-row key and the new row's id. */
  created: { current: { addKey: string; id: string } | null };
}) {
  const { rows, columns } = grid;
  const [sel, setSelState] = useState<Pos>(() => firstSelectable(grid));
  const [editing, setEditingState] = useState<(Pos & { text: string; invalid?: boolean }) | null>(null);
  const [menu, setMenu] = useState<(Pos & { x: number; y: number; items: CellMenuItem[] }) | null>(null);
  const [pendingEdit, setPendingEdit] = useState<Pos | null>(null);
  const tableRef = useRef<HTMLTableElement>(null);
  const wantFocus = useRef(false);
  const scrollRef = useRef<HTMLDivElement>(null);
  const [scrolled, setScrolled] = useState(false);
  /** Mirrors of state for event handlers that run between renders (blur after a click elsewhere). */
  const editingRef = useRef(editing);
  const selKey = useRef<string | undefined>(undefined);
  /** A cell to select once it exists (the row an add-row edit just created). */
  const wantKey = useRef<string | null>(null);
  const pointer = useRef<string>('mouse');

  const cellAt = (p: Pos) => rows[p.r]?.[p.c];
  const setEditing = (e: typeof editing) => {
    editingRef.current = e;
    setEditingState(e);
  };
  /** `g` is the grid `p` refers to (the effect below runs with a newer grid than this render's rows). */
  const setSel = (p: Pos, g: Grid = grid) => {
    selKey.current = g.rows[p.r]?.[p.c]?.key;
    setSelState(p);
  };

  const left = useMemo(() => {
    const offsets: number[] = [];
    let x = 0;
    columns.forEach((col, i) => {
      offsets[i] = x;
      if (col.pinned) x += col.width;
    });
    return offsets;
  }, [columns]);
  const pinnedWidth = columns.reduce((w, col) => w + (col.pinned ? col.width : 0), 0);
  const lastPinned = columns.reduce((last, col, i) => (col.pinned ? i : last), -1);

  // The grid was rebuilt (an edit, a sync, a new row): keep the selection on its cell.
  useLayoutEffect(() => {
    const key = wantKey.current ?? selKey.current;
    wantKey.current = null;
    const found = key ? findKey(grid, key) : null;
    if (found) {
      if (found.r !== sel.r || found.c !== sel.c) setSel(found, grid);
      else selKey.current = key;
      return;
    }
    if (!selectable(grid.rows[sel.r]?.[sel.c])) {
      const r = Math.min(sel.r, grid.rows.length - 1);
      const row = grid.rows[r] ?? [];
      let c = Math.min(sel.c, columns.length - 1);
      while (c > 0 && !selectable(row[c])) c--;
      setSel(selectable(row[c]) ? { r, c } : firstSelectable(grid), grid);
    }
  }, [grid]);

  /** Scrolls a cell into view inside the sheet box, never leaving it under the pinned B–E columns. */
  const reveal = useCallback((el: HTMLElement) => {
    const box = scrollRef.current;
    if (!box) return;
    if (!el.classList.contains('pinned')) {
      const l = el.offsetLeft;
      const r = l + el.offsetWidth;
      if (l < box.scrollLeft + pinnedWidth) box.scrollLeft = l - pinnedWidth;
      else if (r > box.scrollLeft + box.clientWidth) box.scrollLeft = r - box.clientWidth;
    }
    const t = el.offsetTop;
    const b = t + el.offsetHeight;
    if (t < box.scrollTop) box.scrollTop = t;
    else if (b > box.scrollTop + box.clientHeight) box.scrollTop = b - box.clientHeight;
    // The box itself may be partly off screen (page scrolled): bring the cell into the viewport too.
    const rect = el.getBoundingClientRect();
    if (rect.top < 60 || rect.bottom > window.innerHeight - 80) el.scrollIntoView({ block: 'nearest', inline: 'nearest' });
  }, [pinnedWidth]);

  const cellEl = (p: Pos) => tableRef.current?.querySelector<HTMLElement>(`[data-r="${p.r}"][data-c="${p.c}"]`);

  useLayoutEffect(() => {
    if (wantFocus.current && !editing) {
      wantFocus.current = false;
      const el = cellEl(sel);
      if (el) {
        el.focus({ preventScroll: true });
        reveal(el);
      }
    }
  }, [sel, editing, reveal]);

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

  /** Like moveFrom, but runs to the last selectable cell in that direction (or `steps` cells). */
  const jumpFrom = (p: Pos, dr: number, dc: number, steps = Infinity): Pos | null => {
    let last: Pos | null = null;
    let cur: Pos | null = p;
    for (let i = 0; i < steps && (cur = moveFrom(cur, dr, dc)); i++) last = cur;
    return last;
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

  /**
   * Writes the open editor. Enter/Tab with unreadable input keep it open (marked); leaving it any other
   * way (a click elsewhere, blur) drops the change with a toast instead of a stuck invalid editor.
   */
  const commitEdit = (move: [number, number] | null, blur = false): boolean => {
    const ed = editingRef.current;
    if (!ed) return true;
    const cell = cellAt(ed);
    created.current = null;
    const ok = cell?.edit ? cell.edit.commit(ed.text) : true;
    if (!ok && !blur) {
      setEditing({ ...ed, invalid: true });
      return false;
    }
    if (!ok) toast(`Couldn't read “${ed.text.trim()}”, so it wasn't saved`);
    const from: Pos = { r: ed.r, c: ed.c };
    setEditing(null);
    if (blur) return ok;
    wantFocus.current = true;
    const target = move ? moveFrom(from, move[0], move[1]) ?? from : from;
    const made = created.current as { addKey: string; id: string } | null;
    created.current = null;
    if (made && cell?.key === made.addKey) {
      if (move && move[0] !== 0) {
        // Enter in the add row: stay on it (it moves down under the new row) to keep adding.
        wantKey.current = made.addKey;
      } else {
        // Tab: on to the next field of the row just created.
        const key = rows[target.r]?.[target.c]?.key;
        const sameAddRow = key?.startsWith(made.addKey.slice(0, made.addKey.lastIndexOf(':') + 1));
        wantKey.current = key && sameAddRow ? createdKey(made.addKey, made.id, key.split(':')[2]!) : null;
      }
      setSel(target);
      return true;
    }
    setSel(target);
    return true;
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

  const openMenuAtCell = (p: Pos) => {
    const r = cellEl(p)?.getBoundingClientRect();
    if (r) openMenu(p, r.right - 8, r.bottom);
  };

  const runUndo = () => {
    if (undo.undo()) toast('Undone');
    else toast('Nothing to undo');
  };

  const clearCell = (p: Pos) => {
    const cell = cellAt(p);
    if (!cell?.edit || cell.ghost || cell.edit.initial === '') return;
    if (!cell.edit.commit('')) toast("This cell can't be blank");
  };

  const onKeyDown = (e: KeyboardEvent<HTMLTableElement>) => {
    if (editing || menu) return;
    const cell = cellAt(sel);
    const k = e.key;
    const mod = e.ctrlKey || e.metaKey;
    const moves: Record<string, [number, number]> = { ArrowUp: [-1, 0], ArrowDown: [1, 0], ArrowLeft: [0, -1], ArrowRight: [0, 1] };
    if (mod && k.toLowerCase() === 'z' && !e.shiftKey && !e.altKey) {
      e.preventDefault();
      runUndo();
    } else if (moves[k]) {
      e.preventDefault();
      select(mod ? jumpFrom(sel, ...moves[k]!) : moveFrom(sel, ...moves[k]!));
    } else if (k === 'Home' || k === 'End') {
      e.preventDefault();
      const dir = k === 'Home' ? -1 : 1;
      select(mod ? jumpFrom(jumpFrom(sel, dir, 0) ?? sel, 0, dir) ?? jumpFrom(sel, dir, 0) : jumpFrom(sel, 0, dir));
    } else if (k === 'PageUp' || k === 'PageDown') {
      e.preventDefault();
      select(jumpFrom(sel, k === 'PageUp' ? -1 : 1, 0, 10));
    } else if (k === 'Tab') {
      if (select(moveFrom(sel, 0, e.shiftKey ? -1 : 1))) e.preventDefault();
    } else if (k === 'Enter' || k === 'F2') {
      e.preventDefault();
      if (cell?.check) cell.check.toggle();
      else if (cell?.edit) startEdit(sel);
      else if (cell?.menu) openMenuAtCell(sel);
      else if (k === 'Enter') select(moveFrom(sel, e.shiftKey ? -1 : 1, 0));
    } else if (k === ' ' && cell?.check) {
      e.preventDefault();
      cell.check.toggle();
    } else if (k === 'Delete' || k === 'Backspace') {
      e.preventDefault();
      clearCell(sel);
    } else if (k === 'ContextMenu' || (k === 'F10' && e.shiftKey)) {
      e.preventDefault();
      openMenuAtCell(sel);
    } else if (k.length === 1 && !mod && !e.altKey && cell?.edit) {
      e.preventDefault();
      startEdit(sel, k);
    }
  };

  const onCopy = (e: ClipboardEvent<HTMLTableElement>) => {
    if (editingRef.current) return;
    const cell = cellAt(sel);
    if (!cell) return;
    e.preventDefault();
    e.clipboardData.setData('text/plain', copyText(cell));
  };

  const onPaste = (e: ClipboardEvent<HTMLTableElement>) => {
    if (editingRef.current) return;
    const text = e.clipboardData.getData('text/plain');
    if (!text) return;
    e.preventDefault();
    const lines = parseClipboard(text);
    const cell = cellAt(sel);
    if (cell?.addRows) {
      undo.begin();
      const n = cell.addRows(lines);
      undo.end();
      toast(n ? `Added ${n} row${n === 1 ? '' : 's'}` : "Couldn't read the pasted rows");
      return;
    }
    let done = 0;
    let skipped = 0;
    undo.begin();
    lines.forEach((fields, i) => fields.forEach((value, j) => {
      const target = rows[sel.r + i]?.[sel.c + j];
      if (!target?.edit || target.ghost) return;
      if (target.edit.commit(value)) done++;
      else skipped++;
    }));
    undo.end();
    if (skipped) toast(`Pasted ${done} cell${done === 1 ? '' : 's'}, skipped ${skipped} that couldn't be read`);
    else if (!done) toast("Can't paste here");
  };

  return (
    <div className="sheet-card card">
      <div ref={scrollRef} className={`sheet-scroll${scrolled ? ' scrolled' : ''}`} onScroll={(e) => setScrolled(e.currentTarget.scrollLeft > 0)}>
        <table ref={tableRef} className="sheet-grid" role="grid" aria-label={label} onKeyDown={onKeyDown} onCopy={onCopy} onPaste={onPaste}
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
                      onPointerDown={(e) => { pointer.current = e.pointerType; }}
                      onMouseDown={(e: MouseEvent) => {
                        if (e.button !== 0 || isEditing) return;
                        if ((e.target as HTMLElement).closest('.sc-menu')) return;
                        // Close another open editor first, so its blur can't land after this click.
                        if (editingRef.current) commitEdit(null, true);
                        const here = { r, c };
                        if (cell.check) {
                          select(here);
                          cell.check.toggle();
                        } else if (cell.edit && (isSel || cell.ghost || pointer.current === 'touch')) {
                          e.preventDefault();
                          startEdit(here);
                        } else {
                          select(here, false);
                        }
                      }}
                      onDoubleClick={() => {
                        if (cell.edit && !isEditing) startEdit({ r, c });
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
                          onCommit={(move) => commitEdit(move)}
                          onBlur={() => commitEdit(null, true)}
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

function CellEditor({ cell, text, invalid, onChange, onCommit, onBlur, onCancel }: {
  cell: Cell;
  text: string;
  invalid?: boolean;
  onChange: (t: string) => void;
  onCommit: (move: [number, number] | null) => void;
  onBlur: () => void;
  onCancel: () => void;
}) {
  const ref = useRef<HTMLInputElement>(null);
  const done = useRef(false);
  const type = cell.edit!.type;
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    el.focus({ preventScroll: true });
    const end = el.value.length;
    // A typed first character replaces the content; Enter/click keeps it and selects all.
    if (text.length === 1 && cell.edit!.initial !== text) el.setSelectionRange(end, end);
    else el.select();
  }, []);
  return (
    <input
      ref={ref}
      className={`sc-editor${type === 'money' ? ' money' : ''}${invalid ? ' invalid' : ''}`}
      type="text"
      inputMode={type === 'money' ? 'decimal' : undefined}
      autoComplete="off"
      spellCheck={type === 'text'}
      value={text}
      placeholder={cell.edit!.placeholder}
      aria-label={cell.edit!.placeholder ?? 'Edit cell'}
      aria-invalid={invalid}
      title={type === 'date' ? 'Type a date like 10/3, Oct 3 or 3 (day of this month); t = today' : undefined}
      onChange={(e) => onChange(e.target.value)}
      onKeyDown={(e) => {
        e.stopPropagation();
        if (e.key === 'Enter') {
          e.preventDefault();
          onCommit([e.shiftKey ? -1 : 1, 0]);
        } else if (e.key === 'Tab') {
          e.preventDefault();
          onCommit([0, e.shiftKey ? -1 : 1]);
        } else if (e.key === 'Escape') {
          e.preventDefault();
          done.current = true;
          onCancel();
        }
      }}
      onBlur={() => {
        if (!done.current) onBlur();
      }}
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
