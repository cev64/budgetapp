import type { Actions } from '../data/actions';
import type { Calc, YearSummary } from '../domain/calc';
import type { NetWorth } from '../domain/networth';
import { ledgerColumns, leftRows, sortLedger, blockHeight } from '../domain/sheet';
import { diffTone, formatMoney, formatPercent, parseAmount, amountInputText, monthClosedMessage, type Tone } from '../domain/format';
import { dateInputText, monthName, parseDateInput, shortDate } from '../domain/dates';
import type { Account, Category, LedgerEntry, Transaction, YM } from '../domain/types';

// The Sheet view as data: a matrix of cells laid out per docs/SHEET_VIEW.md, each wired to the existing
// actions. The React grid (Sheet.tsx) only renders and handles keys. No new math: every number comes
// from the domain calc.

export interface CellMenuItem {
  label: string;
  run: () => void;
  warn?: boolean;
}

export interface CellEdit {
  type: 'money' | 'text' | 'date';
  /** Text the editor starts with (the exact stored value, never the rounded display). */
  initial: string;
  placeholder?: string;
  /** Returns false when the input is invalid (the editor stays open). */
  commit: (text: string) => boolean;
}

export interface Cell {
  /** Stable identity across rebuilds (e.g. "tx:<id>:amount"), so the selection follows its cell. */
  key?: string;
  /** Add-row cells: create one row per pasted line (fields from this cell's column on); returns how many. */
  addRows?: (lines: string[][]) => number;
  /** void = outside any block (spacer columns, below a short block): no rules, not selectable. */
  kind: 'void' | 'blank' | 'header' | 'title' | 'label' | 'value';
  text?: string;
  align?: 'left' | 'right' | 'center';
  computed?: boolean;
  tone?: Tone;
  strong?: boolean;
  totalTop?: boolean;
  categoryId?: string;
  marker?: string;
  hint?: string;
  edit?: CellEdit;
  check?: { checked: boolean; label: string; toggle: () => void };
  menu?: CellMenuItem[];
  /** Muted add-row placeholder. */
  ghost?: boolean;
  /** Show the ⋮ button here (one per row); other cells of the row still open `menu` on right-click. */
  menuButton?: boolean;
  /** First cell of a block title row: its text spills over the block's other title cells. */
  lead?: boolean;
}

export interface Column {
  key: string;
  width: number;
  /** Pinned while the ledger area scrolls (sheet columns B–E). */
  pinned?: boolean;
  spacer?: boolean;
}

export interface Grid {
  columns: Column[];
  rows: Cell[][];
  /** Spreadsheet row number of each row (first row = sheet row 2). */
  rowNumbers: number[];
}

export interface SheetDeps {
  calc: Calc;
  money: (v: number | null) => string;
  actions: Pick<Actions,
    'setBudget' | 'setMonthClosed' | 'saveTransaction' | 'deleteTransaction' |
    'saveAccount' | 'saveLedgerEntry' | 'deleteLedgerEntry' | 'newLedgerEntry'>;
  newId: () => string;
  /** Ledger categories whose actual is being overridden right now (before a value is typed). */
  overriding: ReadonlySet<string>;
  setOverriding: (categoryId: string, on: boolean) => void;
  /** Toast, optionally with an Undo action. */
  notify: (text: string, undo?: () => void) => void;
  moveToMonth: (t: Transaction) => void;
  /** Records how to revert a change just written (Ctrl/Cmd+Z in the grid). */
  pushUndo?: (undo: () => void) => void;
  /** An add row created a row: lets the grid move on to that row's next cell. */
  onCreate?: (addKey: string, id: string) => void;
  /** The latest stored copy of a row: edits merge into it, so two edits to one row never undo each other. */
  fresh?: <T extends Transaction | LedgerEntry | Account>(table: 'transactions' | 'ledger_entries' | 'accounts', row: T) => T;
}

const undoable = (deps: SheetDeps, undo: () => void) => deps.pushUndo?.(undo);
const latest = <T extends Transaction | LedgerEntry | Account>(deps: SheetDeps, table: 'transactions' | 'ledger_entries' | 'accounts', row: T): T =>
  deps.fresh?.(table, row) ?? row;

const VOID: Cell = { kind: 'void' };
const BLANK: Cell = { kind: 'blank' };
const head = (text: string, align: Cell['align'] = 'right'): Cell => ({ kind: 'header', text, align });

// Column widths fit the 14/20 text without shrinking it: B holds "Saved (Roth, 401k, Brokerage)" at 600
// weight, E the DIFFERENCE header. At 1280px (with the rail) B–E plus the G and K blocks are visible; from
// ~1440px the whole month fits; the Summary always fits at 1280.
const LEFT_COLS: Column[] = [
  { key: 'B', width: 232, pinned: true },
  { key: 'C', width: 92, pinned: true },
  { key: 'D', width: 96, pinned: true },
  { key: 'E', width: 104, pinned: true },
];
const SPACER = (key: string): Column => ({ key, width: 12, spacer: true });
const DATE_W = 70;
const ITEM_W = 92;
const AMOUNT_W = 92;

/** Rough width of a block title (15/600 + palette symbol + padding) so a block is never narrower than its title. */
const titleWidth = (name: string) => 20 + 13 + 7 + Math.ceil(name.length * 8.6);

/** Rough rendered width of 14px body text (cell padding 2 × 10 included). */
const textWidth = (text: string) => 24 + Math.ceil(text.length * 8.2);
const ITEM_MAX = 280;

/**
 * Item and Amount widths grow to fit the longest item / amount in the column (Item capped at 280px,
 * where it ellipsizes with the full text in the tooltip); the block is never narrower than its titles.
 */
const LEDGER_COLS = (keys: [string, string, string], blocks: { title: string; items: string[]; amounts: string[] }[]): Column[] => {
  const item = Math.min(ITEM_MAX, Math.max(ITEM_W, ...blocks.flatMap((b) => b.items.map(textWidth))));
  const amount = Math.max(AMOUNT_W, ...blocks.flatMap((b) => b.amounts.map((a) => textWidth(a) + 8)));
  const extra = Math.max(0, ...blocks.map((b) => titleWidth(b.title))) - (DATE_W + item + amount);
  return [
    { key: keys[0], width: DATE_W },
    { key: keys[1], width: item + Math.max(0, extra) },
    { key: keys[2], width: amount },
  ];
};

function parseMoney(text: string, nullable: boolean): number | null | undefined {
  // Spreadsheet-style negatives: "(12.50)".
  const paren = /^\s*\((.*)\)\s*$/.exec(text);
  const v = paren ? -(parseAmount(paren[1]!) ?? Number.NaN) : parseAmount(text);
  if (v !== null && Number.isNaN(v)) return undefined;
  return v === null ? (nullable ? null : 0) : v;
}

const moneyCell = (deps: SheetDeps, v: number | null, extra: Partial<Cell> = {}): Cell => ({
  kind: 'value', text: deps.money(v), align: 'right', ...extra,
});

function place(rows: Cell[][], r: number, c: number, cell: Cell) {
  while (rows.length <= r) rows.push([]);
  rows[r]![c] = cell;
}

function finish(columns: Column[], rows: Cell[][]): Grid {
  const out = rows.map((row) => columns.map((_, c) => row[c] ?? VOID));
  return { columns, rows: out, rowNumbers: out.map((_, i) => i + 2) };
}

// ---------------------------------------------------------------------------------------------
// Month tab

export function buildMonthGrid(deps: SheetDeps, ym: YM): Grid {
  const { calc, actions } = deps;
  const summary = calc.monthSummary(ym);
  const rows: Cell[][] = [];
  const lines = new Map(summary.lines.map((l) => [l.category.id, l]));

  for (const lr of leftRows(summary.lines.map((l) => l.category), 'month')) {
    const r = lr.row - 2;
    if (lr.type === 'header') {
      place(rows, r, 0, { kind: 'header', text: monthName(ym.month), align: 'left', strong: true });
      place(rows, r, 1, head('Expected'));
      place(rows, r, 2, head('Actual'));
      place(rows, r, 3, head('Difference'));
    } else if (lr.type === 'category') {
      const line = lines.get(lr.category.id)!;
      const c = lr.category;
      place(rows, r, 0, { kind: 'label', text: c.name, categoryId: c.id, key: `cat:${c.id}:name` });
      const expected = line.budget?.expected ?? null;
      place(rows, r, 1, moneyCell(deps, expected, {
        key: `cat:${c.id}:expected`,
        edit: {
          type: 'money',
          initial: amountInputText(expected),
          commit: (text) => {
            const v = parseMoney(text, true);
            if (v === undefined) return false;
            if (v !== expected) {
              void actions.setBudget(ym, c.id, { expected: v });
              undoable(deps, () => void actions.setBudget(ym, c.id, { expected }));
            }
            return true;
          },
        },
      }));
      place(rows, r, 2, actualCell(deps, ym, c, line.budget?.actual ?? null, line.ledgerSum));
      place(rows, r, 3, moneyCell(deps, line.difference, { computed: true, tone: diffTone(c.kind, line.difference), key: `cat:${c.id}:diff` }));
    } else if (lr.type === 'spacer') {
      for (let c = 0; c < 4; c++) place(rows, r, c, BLANK);
    } else if (lr.type === 'total') {
      const [label, e, a, kind] =
        lr.total === 'expenses' ? ['Monthly Expenses', summary.expected.expenses, summary.actual.expenses, 'expense' as const]
          : lr.total === 'saved' ? ['Saved (Roth, 401k, Brokerage)', summary.expected.saved, summary.actual.saved, 'savings' as const]
            : ['Leftover', summary.expected.leftover, summary.actual.leftover, 'leftover' as const];
      const top = lr.total === 'expenses';
      place(rows, r, 0, { kind: 'label', text: label, strong: true, totalTop: top });
      place(rows, r, 1, moneyCell(deps, e, { computed: true, strong: true, totalTop: top }));
      place(rows, r, 2, moneyCell(deps, a, { computed: true, strong: true, totalTop: top }));
      place(rows, r, 3, moneyCell(deps, a - e, { computed: true, strong: true, totalTop: top, tone: diffTone(kind, a - e) }));
    } else if (lr.type === 'closed') {
      place(rows, r, 0, { kind: 'label', text: 'Month closed' });
      place(rows, r, 1, {
        kind: 'value', align: 'center', key: 'month:closed',
        check: {
          checked: summary.closed,
          label: 'Month closed',
          toggle: () => {
            const next = !summary.closed;
            void actions.setMonthClosed(ym, next).then((ok) => {
              if (ok) deps.notify(next ? monthClosedMessage(monthName(ym.month)) : `${monthName(ym.month)} is open again.`);
            });
            undoable(deps, () => void actions.setMonthClosed(ym, !next));
          },
        },
      });
      place(rows, r, 2, BLANK);
      place(rows, r, 3, BLANK);
    }
  }

  // Ledger blocks (G H I · K L M · O P Q), starting at sheet row 2.
  const txs = new Map<string, Transaction[]>();
  for (const t of calc.transactionsIn(ym)) {
    const list = txs.get(t.category_id) ?? [];
    list.push(t);
    txs.set(t.category_id, list);
  }
  const visible = calc.categories.filter((c) => !c.archived || (txs.get(c.id)?.length ?? 0) > 0);
  const cols = ledgerColumns(visible, (id) => txs.get(id)?.length ?? 0);
  const titles = (i: number) => cols[i]!.map((c) => ({
    title: c.name,
    items: (txs.get(c.id) ?? []).map((t) => t.item),
    amounts: (txs.get(c.id) ?? []).map((t) => deps.money(t.amount)),
  }));
  const columns = [
    ...LEFT_COLS, SPACER('F'), ...LEDGER_COLS(['G', 'H', 'I'], titles(0)), SPACER('J'),
    ...LEDGER_COLS(['K', 'L', 'M'], titles(1)), SPACER('N'), ...LEDGER_COLS(['O', 'P', 'Q'], titles(2)),
  ];
  const startCol = [5, 9, 13];
  cols.forEach((stack, i) => {
    let r = 0;
    for (const cat of stack) {
      const list = sortLedger(txs.get(cat.id) ?? []);
      ledgerBlock(deps, rows, r, startCol[i]!, ym, cat, list);
      r += blockHeight(list.length) + 1;
    }
  });

  return finish(columns, rows);
}

function actualCell(deps: SheetDeps, ym: YM, c: Category, typed: number | null, ledgerSum: number): Cell {
  const { actions } = deps;
  const commitActual = (text: string) => {
    const v = parseMoney(text, true);
    if (v === undefined) return false;
    deps.setOverriding(c.id, false);
    if (v !== typed) {
      void actions.setBudget(ym, c.id, { actual: v });
      undoable(deps, () => void actions.setBudget(ym, c.id, { actual: typed }));
    }
    return true;
  };
  const key = `cat:${c.id}:actual`;
  if (c.tracking === 'manual') {
    return moneyCell(deps, typed, { key, edit: { type: 'money', initial: amountInputText(typed), commit: commitActual } });
  }
  if (typed != null || deps.overriding.has(c.id)) {
    return moneyCell(deps, typed ?? ledgerSum, {
      key,
      marker: 'override',
      menuButton: true,
      edit: { type: 'money', initial: amountInputText(typed ?? ledgerSum), commit: commitActual },
      menu: [{
        label: 'Clear override',
        run: () => {
          deps.setOverriding(c.id, false);
          if (typed != null) {
            void actions.setBudget(ym, c.id, { actual: null });
            undoable(deps, () => void actions.setBudget(ym, c.id, { actual: typed }));
          }
        },
      }],
    });
  }
  return moneyCell(deps, ledgerSum, {
    key,
    computed: true,
    hint: 'Sum of the ledger',
    menuButton: true,
    menu: [{ label: 'Override actual', run: () => deps.setOverriding(c.id, true) }],
  });
}

const TX_FIELDS = ['date', 'item', 'amount'] as const;
type TxField = (typeof TX_FIELDS)[number];

/** One pasted/typed ledger line → transaction fields; undefined when a field can't be read. */
function parseTxFields(fields: string[], start: number, ym: YM): Partial<Transaction> | undefined {
  const patch: Partial<Transaction> = {};
  for (let k = 0; k < fields.length && start + k < TX_FIELDS.length; k++) {
    const text = fields[k]!;
    const field = TX_FIELDS[start + k]!;
    if (field === 'date') {
      const d = parseDateInput(text, ym);
      if (d === undefined) return undefined;
      if (d) patch.date = d;
    } else if (field === 'item') {
      if (text.trim()) patch.item = text.trim();
    } else {
      const v = parseMoney(text, true);
      if (v === undefined) return undefined;
      if (v != null) patch.amount = v;
    }
  }
  return patch;
}

function ledgerBlock(deps: SheetDeps, rows: Cell[][], r0: number, c0: number, ym: YM, cat: Category, list: Transaction[]) {
  const { actions } = deps;
  place(rows, r0, c0, { kind: 'title', text: cat.name, categoryId: cat.id, align: 'left', lead: true, key: `block:${cat.id}:title` });
  place(rows, r0, c0 + 1, { kind: 'title' });
  place(rows, r0, c0 + 2, { kind: 'title' });
  place(rows, r0 + 1, c0, head('Date', 'left'));
  place(rows, r0 + 1, c0 + 1, head('Item', 'left'));
  place(rows, r0 + 1, c0 + 2, head('Amount'));

  list.forEach((t, i) => {
    const r = r0 + 2 + i;
    /** Merges into the latest copy of the row; undo restores exactly what was there before. */
    const save = (patch: Partial<Transaction>) => {
      const before = latest(deps, 'transactions', t);
      void actions.saveTransaction({ ...before, ...patch });
      undoable(deps, () => void actions.saveTransaction(before));
    };
    const menu: CellMenuItem[] = [
      { label: 'Move to month…', run: () => deps.moveToMonth(t) },
      {
        label: 'Delete', warn: true,
        run: () => {
          const restore = () => void actions.saveTransaction({ ...t, deleted: false });
          void actions.deleteTransaction(t).then((ok) => {
            if (ok) deps.notify(`Deleted ${t.item || 'transaction'}`, restore);
          });
          undoable(deps, restore);
        },
      },
    ];
    place(rows, r, c0, {
      kind: 'value', text: shortDate(t.date), align: 'left', menu, key: `tx:${t.id}:date`,
      edit: {
        type: 'date', initial: dateInputText(t.date, ym), placeholder: 'm/d',
        commit: (text) => {
          const v = parseDateInput(text, ym);
          if (v === undefined) return false;
          if (v !== t.date) save({ date: v });
          return true;
        },
      },
    });
    place(rows, r, c0 + 1, {
      kind: 'value', text: t.item, align: 'left', menu, key: `tx:${t.id}:item`,
      edit: { type: 'text', initial: t.item, commit: (v) => { if (v.trim() !== t.item) save({ item: v.trim() }); return true; } },
    });
    place(rows, r, c0 + 2, moneyCell(deps, t.amount, {
      tone: t.amount < 0 ? 'good' : undefined, menu, menuButton: true, key: `tx:${t.id}:amount`,
      edit: {
        type: 'money', initial: amountInputText(t.amount),
        commit: (text) => {
          const v = parseMoney(text, false);
          if (v === undefined) return false;
          if (v !== t.amount) save({ amount: v ?? 0 });
          return true;
        },
      },
    }));
  });

  // Add row: typing in any cell creates the transaction; pasting several lines creates one per line.
  const r = r0 + 2 + list.length;
  const blank = (): Transaction => ({
    id: deps.newId(), year: ym.year, month: ym.month, category_id: cat.id, date: null, item: '', amount: 0, note: null,
  });
  const addKey = (f: TxField) => `add:${cat.id}:${f}`;
  const create = (field: TxField, patch: Partial<Transaction>) => {
    const t = { ...blank(), ...patch };
    void actions.saveTransaction(t);
    undoable(deps, () => void actions.deleteTransaction(t));
    deps.onCreate?.(addKey(field), t.id);
  };
  const addRows = (start: number) => (lines: string[][]) => {
    const made = lines
      .map((fields) => parseTxFields(fields, start, ym))
      .filter((p): p is Partial<Transaction> => !!p && (p.item != null || p.amount != null || p.date != null))
      .map((p) => ({ ...blank(), ...p }));
    made.forEach((t) => void actions.saveTransaction(t));
    if (made.length) undoable(deps, () => made.forEach((t) => void actions.deleteTransaction(t)));
    return made.length;
  };
  const addCell = (f: TxField, cell: Partial<Cell>, commit: (text: string) => boolean): Cell => ({
    kind: 'value', ghost: true, key: addKey(f), addRows: addRows(TX_FIELDS.indexOf(f)), ...cell,
    edit: { type: f === 'amount' ? 'money' : f === 'date' ? 'date' : 'text', initial: '', ...cell.edit, commit },
  });
  place(rows, r, c0, addCell('date', { align: 'left', edit: { type: 'date', initial: '', placeholder: 'm/d', commit: () => true } }, (text) => {
    const v = parseDateInput(text, ym);
    if (v === undefined) return false;
    if (v) create('date', { date: v });
    return true;
  }));
  place(rows, r, c0 + 1, addCell('item', {
    text: '+ Add', align: 'left',
    edit: { type: 'text', initial: '', placeholder: `New ${cat.name} item`, commit: () => true },
  }, (v) => {
    if (v.trim()) create('item', { item: v.trim() });
    return true;
  }));
  place(rows, r, c0 + 2, addCell('amount', { align: 'right' }, (text) => {
    const v = parseMoney(text, true);
    if (v === undefined) return false;
    if (v != null) create('amount', { amount: v });
    return true;
  }));
}

// ---------------------------------------------------------------------------------------------
// Summary tab

const ACCOUNT_ROWS_END = 13; // the sheet's G3:H13 / J3:K13 areas

export function buildSummaryGrid(deps: SheetDeps, summary: YearSummary, nw: NetWorth, ledger: LedgerEntry[]): Grid {
  const { actions } = deps;
  const columns: Column[] = [...LEFT_COLS, SPACER('F'), { key: 'G', width: 184 }, { key: 'H', width: 112 }, SPACER('I'), { key: 'J', width: 184 }, { key: 'K', width: 112 }];
  const rows: Cell[][] = [];
  const lines = new Map(summary.lines.map((l) => [l.category.id, l]));
  const pct = (v: number) => formatPercent(v);

  for (const lr of leftRows(summary.lines.map((l) => l.category), 'summary')) {
    const r = lr.row - 2;
    switch (lr.type) {
      case 'header':
        place(rows, r, 0, { kind: 'header', text: String(summary.year), align: 'left', strong: true });
        place(rows, r, 1, head('Expected'));
        place(rows, r, 2, head('Actual'));
        place(rows, r, 3, head('Difference'));
        break;
      case 'category': {
        const l = lines.get(lr.category.id)!;
        place(rows, r, 0, { kind: 'label', text: lr.category.name, categoryId: lr.category.id });
        place(rows, r, 1, moneyCell(deps, l.expected, { computed: true }));
        place(rows, r, 2, moneyCell(deps, l.actual, { computed: true }));
        place(rows, r, 3, moneyCell(deps, l.difference, { computed: true, tone: diffTone(lr.category.kind, l.difference) }));
        break;
      }
      case 'spacer':
        for (let c = 0; c < 4; c++) place(rows, r, c, BLANK);
        break;
      case 'total': {
        const e = summary.expected;
        const a = summary.actual;
        const [label, ev, av, kind] =
          lr.total === 'expenses' ? ['Expenses', e.expenses, a.expenses, 'expense' as const]
            : lr.total === 'saved' ? ['Saved (Roth, 401k, Brokerage)', e.saved, a.saved, 'savings' as const]
              // Row 22's Difference is the leftover vs plan (DOMAIN_RULES §4b).
              : ['Leftover', e.leftover, a.leftover, 'leftover' as const];
        const diff = lr.total === 'leftover' ? summary.leftoverVsPlan : av - ev;
        const top = lr.total === 'expenses';
        place(rows, r, 0, { kind: 'label', text: label, strong: true, totalTop: top });
        place(rows, r, 1, moneyCell(deps, ev, { computed: true, strong: true, totalTop: top }));
        place(rows, r, 2, moneyCell(deps, av, { computed: true, strong: true, totalTop: top }));
        place(rows, r, 3, moneyCell(deps, diff, { computed: true, strong: true, totalTop: top, tone: diffTone(kind, diff) }));
        break;
      }
      case 'annualized':
        place(rows, r, 0, { kind: 'label', text: 'Annualized Savings' });
        place(rows, r, 1, moneyCell(deps, summary.expected.annualizedSavings, { computed: true }));
        place(rows, r, 2, moneyCell(deps, summary.actual.annualizedSavings, { computed: true }));
        place(rows, r, 3, BLANK);
        break;
      case 'pctNet':
      case 'pctGross': {
        const net = lr.type === 'pctNet';
        place(rows, r, 0, { kind: 'label', text: net ? 'Percent of Net Income' : 'Percent of Gross Income' });
        place(rows, r, 1, { kind: 'value', align: 'right', computed: true, text: pct(net ? summary.expected.pctNet : summary.expected.pctGross) });
        place(rows, r, 2, { kind: 'value', align: 'right', computed: true, text: pct(net ? summary.actual.pctNet : summary.actual.pctGross) });
        place(rows, r, 3, BLANK);
        break;
      }
      default:
        break;
    }
  }

  // Net worth G H: header [2], accounts [3–13], Net Worth [14], blank, Super Liquid Assets [16].
  place(rows, 0, 5, { kind: 'header', text: 'Net worth', align: 'left' });
  place(rows, 0, 6, head('Balance'));
  let r = 1;
  for (const v of nw.accounts) {
    const a = v.account;
    place(rows, r, 5, { kind: 'label', text: a.name, key: `acct:${a.id}:name` });
    place(rows, r, 6, a.linked_category_id
      ? moneyCell(deps, v.balance, { key: `acct:${a.id}:balance`, computed: true, marker: 'auto', hint: `auto · base ${formatMoney(a.base_amount)} + contributions` })
      : moneyCell(deps, v.balance, {
        key: `acct:${a.id}:balance`,
        tone: v.balance < 0 ? 'bad' : undefined,
        edit: {
          type: 'money', initial: amountInputText(a.balance),
          commit: (text) => {
            const n = parseMoney(text, false);
            if (n === undefined) return false;
            if (n !== a.balance) {
              const before = latest(deps, 'accounts', a);
              void actions.saveAccount({ ...before, balance: n ?? 0 });
              undoable(deps, () => void actions.saveAccount(before));
            }
            return true;
          },
        },
      }));
    r++;
  }
  for (; r <= ACCOUNT_ROWS_END - 2; r++) { place(rows, r, 5, BLANK); place(rows, r, 6, BLANK); }
  place(rows, r, 5, { kind: 'label', text: 'Net Worth', strong: true, totalTop: true });
  place(rows, r, 6, moneyCell(deps, nw.netWorth, { computed: true, strong: true, totalTop: true }));
  place(rows, r + 1, 5, BLANK);
  place(rows, r + 1, 6, BLANK);
  place(rows, r + 2, 5, { kind: 'label', text: 'Super Liquid Assets', strong: true });
  place(rows, r + 2, 6, moneyCell(deps, nw.superLiquid, { computed: true, strong: true }));

  // Ledger J K: header [2], unsettled entries + add row [3–13], Net Reconciliations [14].
  place(rows, 0, 8, { kind: 'header', text: 'Ledger', align: 'left' });
  place(rows, 0, 9, head('Amount'));
  r = 1;
  for (const e of ledger.filter((x) => !x.settled)) {
    const save = (patch: Partial<LedgerEntry>) => {
      const before = latest(deps, 'ledger_entries', e);
      void actions.saveLedgerEntry({ ...before, ...patch });
      undoable(deps, () => void actions.saveLedgerEntry(before));
    };
    const menu: CellMenuItem[] = [
      {
        label: 'Settle',
        run: () => {
          const reopen = () => void actions.saveLedgerEntry({ ...e, settled: false });
          void actions.saveLedgerEntry({ ...e, settled: true }).then((ok) => ok && deps.notify(`${e.name} settled`, reopen));
          undoable(deps, reopen);
        },
      },
      {
        label: 'Delete', warn: true,
        run: () => {
          const restore = () => void actions.saveLedgerEntry({ ...e, deleted: false });
          void actions.deleteLedgerEntry(e).then((ok) => ok && deps.notify(`Deleted ${e.name}`, restore));
          undoable(deps, restore);
        },
      },
    ];
    place(rows, r, 8, {
      kind: 'value', text: e.name, align: 'left', menu, key: `iou:${e.id}:name`,
      edit: { type: 'text', initial: e.name, commit: (v) => { if (v.trim() && v.trim() !== e.name) save({ name: v.trim() }); return true; } },
    });
    place(rows, r, 9, moneyCell(deps, e.amount, {
      tone: e.amount > 0 ? 'good' : e.amount < 0 ? 'bad' : undefined, menu, menuButton: true, key: `iou:${e.id}:amount`,
      edit: {
        type: 'money', initial: amountInputText(e.amount),
        commit: (text) => {
          const n = parseMoney(text, false);
          if (n === undefined) return false;
          if (n !== e.amount) save({ amount: n ?? 0 });
          return true;
        },
      },
    }));
    r++;
  }
  const createEntry = (field: 'name' | 'amount', patch: Partial<LedgerEntry>) => {
    const e = { ...actions.newLedgerEntry(), name: 'New entry', ...patch };
    void actions.saveLedgerEntry(e);
    undoable(deps, () => void actions.deleteLedgerEntry(e));
    deps.onCreate?.(`add:iou:${field}`, e.id);
  };
  const addEntries = (start: number) => (lines: string[][]) => {
    let made: LedgerEntry[] = [];
    for (const fields of lines) {
      const [name, amount] = start === 0 ? [fields[0] ?? '', fields[1] ?? ''] : ['', fields[0] ?? ''];
      const n = parseMoney(amount, true);
      if (n === undefined || (!name.trim() && n == null)) continue;
      // newLedgerEntry orders after the stored entries: bump past the ones made in this paste.
      const base = actions.newLedgerEntry();
      made = [...made, { ...base, sort_order: base.sort_order + made.length, name: name.trim() || 'New entry', amount: n ?? 0 }];
    }
    made.forEach((e) => void actions.saveLedgerEntry(e));
    if (made.length) undoable(deps, () => made.forEach((e) => void actions.deleteLedgerEntry(e)));
    return made.length;
  };
  place(rows, r, 8, {
    kind: 'value', text: '+ Add', align: 'left', ghost: true, key: 'add:iou:name', addRows: addEntries(0),
    edit: { type: 'text', initial: '', placeholder: 'Who / what', commit: (v) => { if (v.trim()) createEntry('name', { name: v.trim() }); return true; } },
  });
  place(rows, r, 9, {
    kind: 'value', align: 'right', ghost: true, key: 'add:iou:amount', addRows: addEntries(1),
    edit: {
      type: 'money', initial: '',
      commit: (text) => {
        const n = parseMoney(text, true);
        if (n === undefined) return false;
        if (n != null) createEntry('amount', { amount: n });
        return true;
      },
    },
  });
  r++;
  for (; r <= ACCOUNT_ROWS_END - 2; r++) { place(rows, r, 8, BLANK); place(rows, r, 9, BLANK); }
  place(rows, r, 8, { kind: 'label', text: 'Net Reconciliations', strong: true, totalTop: true });
  place(rows, r, 9, moneyCell(deps, nw.netReconciliations, { computed: true, strong: true, totalTop: true }));

  return finish(columns, rows);
}
