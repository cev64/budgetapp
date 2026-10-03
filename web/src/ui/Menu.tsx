import { useEffect, useId, useLayoutEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { EllipsisVertical } from 'lucide-react';

export interface MenuItem {
  label: string;
  icon?: ReactNode;
  onSelect: () => void;
  warn?: boolean;
  separatorBefore?: boolean;
}

interface Props {
  items: MenuItem[];
  label: string;
  /** Trigger content; defaults to a vertical ellipsis. */
  trigger?: ReactNode;
  className?: string;
}

/** Glass popover menu (FLUID_GLASS_UI §7.3): staggered items, closes on outside click, Escape and selection. */
export function Menu({ items, label, trigger, className }: Props) {
  const [open, setOpen] = useState(false);
  const [shift, setShift] = useState(0);
  const wrap = useRef<HTMLDivElement>(null);
  const menu = useRef<HTMLDivElement>(null);
  const id = useId();

  useEffect(() => {
    if (!open) return;
    const onDown = (e: PointerEvent) => {
      if (!wrap.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.stopPropagation();
        setOpen(false);
      }
    };
    document.addEventListener('pointerdown', onDown);
    document.addEventListener('keydown', onKey, true);
    return () => {
      document.removeEventListener('pointerdown', onDown);
      document.removeEventListener('keydown', onKey, true);
    };
  }, [open]);

  // Keep the menu on screen: measure with transitions off and nudge it in by 12px.
  useLayoutEffect(() => {
    const el = menu.current;
    if (!open || !el) return;
    el.classList.add('measure');
    const r = el.getBoundingClientRect();
    el.classList.remove('measure');
    if (r.left < 12) setShift(12 - r.left);
    else setShift(0);
  }, [open]);

  return (
    <div ref={wrap} className={`menu-wrap${className ? ` ${className}` : ''}`}>
      <button
        type="button"
        className="icon-btn"
        aria-label={label}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={id}
        onClick={() => setOpen((o) => !o)}
      >
        {trigger ?? <EllipsisVertical size={18} strokeWidth={1.75} />}
      </button>
      <div
        ref={menu}
        id={id}
        role="menu"
        className={`menu${open ? ' open' : ''}`}
        style={shift ? ({ right: `${-shift}px` } as CSSProperties) : undefined}
      >
        {items.map((item, i) => (
          <div key={item.label} style={{ '--i': i } as CSSProperties} className="menu-slot">
            {item.separatorBefore && <div className="menu-sep" />}
            <button
              type="button"
              role="menuitem"
              tabIndex={open ? 0 : -1}
              className={`menu-item${item.warn ? ' warn' : ''}`}
              onClick={() => {
                setOpen(false);
                item.onSelect();
              }}
            >
              {item.icon}
              <span>{item.label}</span>
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
