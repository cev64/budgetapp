import { useEffect, useState, useSyncExternalStore } from 'react';
import { createPortal } from 'react-dom';

// Dark-glass toast: auto-hides after 2.6s (5s when it offers an action), a new toast replaces the current one.

interface ToastAction {
  label: string;
  run: () => void;
}

interface ToastMsg {
  id: number;
  text: string;
  action?: ToastAction;
}

let msg: ToastMsg | null = null;
let seq = 0;
const listeners = new Set<() => void>();

export function toast(text: string, action?: ToastAction): void {
  msg = { id: ++seq, text, action };
  listeners.forEach((fn) => fn());
}

export function ToastHost() {
  const m = useSyncExternalStore(
    (fn) => {
      listeners.add(fn);
      return () => listeners.delete(fn);
    },
    () => msg,
  );
  const [on, setOn] = useState(false);
  useEffect(() => {
    if (!m) return;
    setOn(false);
    let b = 0;
    const a = requestAnimationFrame(() => {
      b = requestAnimationFrame(() => setOn(true));
    });
    const t = setTimeout(() => setOn(false), m.action ? 5000 : 2600);
    return () => {
      cancelAnimationFrame(a);
      cancelAnimationFrame(b);
      clearTimeout(t);
    };
  }, [m]);
  return createPortal(
    <div className={`toast${on ? ' on' : ''}${m?.action ? ' has-action' : ''}`} role="status" aria-live="polite">
      <span>{m?.text}</span>
      {m?.action && (
        <button
          type="button"
          className="toast-action"
          tabIndex={on ? 0 : -1}
          onClick={() => {
            m.action!.run();
            setOn(false);
          }}
        >
          {m.action.label}
        </button>
      )}
    </div>,
    document.body,
  );
}
