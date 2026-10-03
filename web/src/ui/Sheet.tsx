import { useEffect, useRef, useState, useSyncExternalStore, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { reducedMotion } from './motion';

const EXIT_MS = 270; // ~60% of the .45s entrance

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
 * Modal sheet (FLUID_GLASS_UI §7.4): un-hidden, then `.open` two frames later so it springs in;
 * exits faster. A bottom sheet under 600px. Escape and a backdrop click close it.
 */
export function Sheet({ open, onClose, title, footer, children, wide, ariaLabel }: SheetProps) {
  const [mounted, setMounted] = useState(open);
  const [shown, setShown] = useState(false);
  const sheetRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;

  useEffect(() => {
    if (open) {
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
  return createPortal(
    <div
      className={`modal${shown && open ? ' open' : ''}`}
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        ref={sheetRef}
        className={`sheet${wide ? ' wide' : ''}`}
        role="dialog"
        aria-modal="true"
        aria-label={ariaLabel ?? title}
        tabIndex={-1}
      >
        <div className="sheet-head">
          <h2 className="sheet-title">{title}</h2>
          <button type="button" className="icon-btn" aria-label="Close" onClick={onClose}>
            <X size={18} strokeWidth={1.75} />
          </button>
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
