import { useEffect, useRef, useState, useSyncExternalStore, type CSSProperties, type ReactNode, type PointerEvent as RPointerEvent } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { reducedMotion } from './motion';
import { sheetOffset, sheetShouldClose } from './gesture';

const EXIT_MS = 220; // docs/FLUID_GLASS_UI.md §2: sheet in 350 ms spring, out 220 ms ease

let openSheets = 0;
/** True while any sheet is open (keyboard shortcuts stay quiet). */
export const sheetOpen = () => openSheets > 0;

interface SheetProps {
  open: boolean;
  onClose: () => void;
  title: string;
  /** Sticky footer (actions). */
  footer?: ReactNode;
  children: ReactNode;
  wide?: boolean;
  /** Label for the dialog when the title is visually different. */
  ariaLabel?: string;
}

/**
 * Modal glass sheet (FLUID_GLASS_UI §5, §7): un-hidden, then `.open` two frames later so it springs in;
 * exits faster. A bottom sheet under 600px whose grabber / header can be dragged down to dismiss.
 * Escape and a backdrop click close it.
 */
export function Sheet({ open, onClose, title, footer, children, wide, ariaLabel }: SheetProps) {
  const [mounted, setMounted] = useState(open);
  const [shown, setShown] = useState(false);
  const sheetRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;

  // Drag-to-dismiss (phones): the grabber / header follows the finger; thresholds live in gesture.ts.
  const [drag, setDrag] = useState<{ dy: number; closing: boolean } | null>(null);
  const track = useRef<{ y: number; dy: number; t: number; v: number; id: number } | null>(null);
  const onDown = (e: RPointerEvent<HTMLDivElement>) => {
    if (e.button !== 0 || !window.matchMedia('(max-width: 599px)').matches) return;
    if ((e.target as HTMLElement).closest('button, a, input, select, textarea')) return;
    track.current = { y: e.clientY, dy: 0, t: performance.now(), v: 0, id: e.pointerId };
    try {
      e.currentTarget.setPointerCapture(e.pointerId);
    } catch {
      // no active pointer (synthetic event): the drag still works without capture
    }
    setDrag({ dy: 0, closing: false });
  };
  const onMove = (e: RPointerEvent<HTMLDivElement>) => {
    const tr = track.current;
    if (!tr || e.pointerId !== tr.id) return;
    const now = performance.now();
    const dy = e.clientY - tr.y;
    tr.v = (dy - tr.dy) / Math.max(1, now - tr.t);
    tr.dy = dy;
    tr.t = now;
    setDrag({ dy, closing: false });
  };
  const onUp = () => {
    const tr = track.current;
    track.current = null;
    if (!tr) return;
    if (tr.dy > 0 && sheetShouldClose(tr.dy, tr.v)) {
      setDrag({ dy: tr.dy, closing: true });
      setTimeout(() => closeRef.current(), reducedMotion() ? 0 : 200);
    } else setDrag(null);
  };

  useEffect(() => {
    if (open) {
      // A flung sheet keeps its drag position until it unmounts; reset when it opens again.
      setDrag(null);
      setMounted(true);
      let b = 0;
      const a = requestAnimationFrame(() => {
        b = requestAnimationFrame(() => setShown(true));
      });
      return () => {
        cancelAnimationFrame(a);
        cancelAnimationFrame(b);
      };
    }
    setShown(false);
    const t = setTimeout(() => setMounted(false), reducedMotion() ? 0 : EXIT_MS);
    return () => clearTimeout(t);
  }, [open]);

  useEffect(() => {
    if (!mounted) return;
    openSheets++;
    const previous = document.activeElement as HTMLElement | null;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.stopPropagation();
        closeRef.current();
      }
    };
    document.addEventListener('keydown', onKey);
    document.documentElement.classList.add('modal-lock');
    // Focus the first autofocus field, else the sheet itself.
    requestAnimationFrame(() => {
      const el = sheetRef.current;
      if (!el || el.contains(document.activeElement)) return;
      const target = el.querySelector<HTMLElement>('[data-autofocus]') ?? el;
      target.focus({ preventScroll: true });
    });
    return () => {
      openSheets--;
      document.removeEventListener('keydown', onKey);
      if (openSheets === 0) document.documentElement.classList.remove('modal-lock');
      previous?.focus?.({ preventScroll: true });
    };
  }, [mounted]);

  if (!mounted) return null;
  const dragY = drag ? (drag.closing ? null : sheetOffset(drag.dy)) : 0;
  const sheetStyle: CSSProperties | undefined = drag
    ? { transform: dragY == null ? 'translateY(110%)' : `translateY(${dragY}px)` }
    : undefined;
  // The scrim fades as the sheet is pulled down.
  const modalStyle = drag ? ({ '--scrim-o': drag.closing ? 0 : Math.max(0.15, 1 - Math.max(0, drag.dy) / 360) } as CSSProperties) : undefined;
  return createPortal(
    <div
      className={`modal${shown && open ? ' open' : ''}`}
      style={modalStyle}
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        ref={sheetRef}
        className={`sheet${wide ? ' wide' : ''}${drag && !drag.closing ? ' dragging' : ''}${drag?.closing ? ' flung' : ''}`}
        style={sheetStyle}
        role="dialog"
        aria-modal="true"
        aria-label={ariaLabel ?? title}
        tabIndex={-1}
      >
        <div className="sheet-grab" onPointerDown={onDown} onPointerMove={onMove} onPointerUp={onUp} onPointerCancel={onUp}>
          <span className="grabber" aria-hidden="true" />
          <div className="sheet-head">
            <h2 className="sheet-title">{title}</h2>
            <button type="button" className="icon-btn sheet-close" aria-label="Close" onClick={onClose}>
              <X size={18} strokeWidth={1.75} />
            </button>
          </div>
        </div>
        <div className="sheet-body">{children}</div>
        {footer && <div className="actions">{footer}</div>}
      </div>
    </div>,
    document.body,
  );
}

// ---- ask(): promise-based confirm dialog ----

export interface AskOptions {
  title: string;
  body?: ReactNode;
  yes?: string;
  no?: string;
  /** Destructive confirm: solid red button. */
  danger?: boolean;
}

interface AskRequest extends AskOptions {
  resolve: (ok: boolean) => void;
}

let current: AskRequest | null = null;
const askListeners = new Set<() => void>();
const emitAsk = () => askListeners.forEach((fn) => fn());

/** Shows a confirm sheet. Resolves true on confirm, false on cancel / Escape / backdrop. */
export function ask(options: AskOptions): Promise<boolean> {
  current?.resolve(false);
  return new Promise((resolve) => {
    current = { ...options, resolve };
    emitAsk();
  });
}

export function AskHost() {
  const req = useSyncExternalStore(
    (fn) => {
      askListeners.add(fn);
      return () => askListeners.delete(fn);
    },
    () => current,
  );
  const [last, setLast] = useState<AskRequest | null>(null);
  useEffect(() => {
    if (req) setLast(req);
  }, [req]);
  const shown = req ?? last;
  const done = (ok: boolean) => {
    if (!current) return;
    const r = current;
    current = null;
    emitAsk();
    r.resolve(ok);
  };
  return (
    <Sheet
      open={req != null}
      onClose={() => done(false)}
      title={shown?.title ?? ''}
      footer={
        <>
          <button type="button" className="btn" data-autofocus onClick={() => done(false)}>
            {shown?.no ?? 'Cancel'}
          </button>
          <button type="button" className={`btn ${shown?.danger ? 'destroy' : 'primary'}`} onClick={() => done(true)}>
            {shown?.yes ?? 'OK'}
          </button>
        </>
      }
    >
      {shown?.body != null && <div className="ask-body">{shown.body}</div>}
    </Sheet>
  );
}
