import { useEffect, useState, useSyncExternalStore } from 'react';
import { createPortal } from 'react-dom';

// Dark-glass toast: auto-hides after 2.6s, a new toast replaces the current one.

interface ToastMsg {
  id: number;
  text: string;
}

let msg: ToastMsg | null = null;
let seq = 0;
const listeners = new Set<() => void>();

export function toast(text: string): void {
  msg = { id: ++seq, text };
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
    const t = setTimeout(() => setOn(false), 2600);
    return () => {
      cancelAnimationFrame(a);
      cancelAnimationFrame(b);
      clearTimeout(t);
    };
  }, [m]);
  return createPortal(
    <div className={`toast${on ? ' on' : ''}`} role="status" aria-live="polite">
      {m?.text}
    </div>,
    document.body,
  );
}
