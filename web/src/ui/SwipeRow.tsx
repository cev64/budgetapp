import { useRef, useState, type ReactNode, type PointerEvent as RPointerEvent } from 'react';
import { haptic, reducedMotion } from './motion';
import { lockAxis, swipeOffset, swipeThreshold } from './gesture';

export interface SwipeAction {
  label: string;
  icon: ReactNode;
  /** Underlay tone: bad (delete) or good. */
  tone: 'bad' | 'good';
  run: () => void;
}

interface Props {
  children: ReactNode;
  /** Revealed by dragging left (the underlay sits on the right). */
  left?: SwipeAction;
  /** Revealed by dragging right. */
  right?: SwipeAction;
  /** FLIP key for the surrounding list. */
  k?: string;
  disabled?: boolean;
}

/**
 * Swipe to act on touch (docs/FLUID_GLASS_UI.md §7): the row follows the finger over a tinted underlay,
 * arms at swipeThreshold (haptic tick, the icon pops), rubber-bands past it, slides out on release when
 * armed and springs back otherwise. Mouse and pen are ignored: they use the row's sheet instead.
 */
export function SwipeRow({ children, left, right, k, disabled }: Props) {
  const ref = useRef<HTMLDivElement>(null);
  const start = useRef<{ x: number; y: number; id: number; axis: 'x' | 'y' | null; w: number } | null>(null);
  const armed = useRef<0 | 1 | -1>(0);
  const [dx, setDx] = useState(0);
  const [phase, setPhase] = useState<'idle' | 'drag' | 'back' | 'out'>('idle');
  const suppressClick = useRef(false);

  const down = (e: RPointerEvent<HTMLDivElement>) => {
    if (disabled || e.pointerType !== 'touch' || phase === 'out') return;
    start.current = { x: e.clientX, y: e.clientY, id: e.pointerId, axis: null, w: ref.current?.offsetWidth ?? 320 };
    armed.current = 0;
  };
  const move = (e: RPointerEvent<HTMLDivElement>) => {
    const s = start.current;
    if (!s || e.pointerId !== s.id) return;
    let mx = e.clientX - s.x;
    const my = e.clientY - s.y;
    if (!s.axis) {
      s.axis = lockAxis(mx, my);
      if (s.axis === 'x') {
        try {
          ref.current?.setPointerCapture(e.pointerId);
        } catch {
          // synthetic or already-released pointer: dragging still works without capture
        }
        setPhase('drag');
      }
    }
    if (s.axis !== 'x') return;
    // Only directions with an action move freely; the other one barely gives.
    if ((mx > 0 && !right) || (mx < 0 && !left)) mx = Math.sign(mx) * Math.sqrt(Math.abs(mx)) * 2;
    const t = swipeThreshold(s.w);
    const allowed = (mx > 0 && right) || (mx < 0 && left);
    const arm: 0 | 1 | -1 = allowed && Math.abs(mx) >= t ? (mx > 0 ? 1 : -1) : 0;
    if (arm !== armed.current) {
      armed.current = arm;
      if (arm) haptic(8);
    }
    setDx(allowed ? swipeOffset(mx, t) : mx);
  };
  const up = () => {
    const s = start.current;
    start.current = null;
    if (!s || s.axis !== 'x') return;
    suppressClick.current = true;
    setTimeout(() => (suppressClick.current = false), 0);
    const arm = armed.current;
    const action = arm > 0 ? right : arm < 0 ? left : undefined;
    if (!action) {
      setPhase(reducedMotion() || dx === 0 ? 'idle' : 'back');
      setDx(0);
      return;
    }
    setPhase('out');
    setDx(arm * (s.w + 24));
    setTimeout(() => {
      action.run();
      setPhase('idle');
      setDx(0);
    }, reducedMotion() ? 0 : 220);
  };

  const action = dx > 0 ? right : dx < 0 ? left : undefined;
  const isArmed = action != null && Math.abs(dx) >= swipeThreshold(ref.current?.offsetWidth ?? 320) - 0.5;
  return (
    <div data-k={k} className={`swipe${phase !== 'idle' ? ` ${phase}` : ''}`}>
      {action && (
        <div className={`swipe-under ${dx > 0 ? 'from-left' : 'from-right'} tone-${action.tone}${isArmed ? ' armed' : ''}`}
          style={{ width: Math.abs(dx) + 28 }} aria-hidden="true">
          <span className="swipe-act">{action.icon}{action.label}</span>
        </div>
      )}
      <div
        ref={ref}
        className="swipe-face"
        style={dx ? { transform: `translateX(${dx}px)` } : undefined}
        onPointerDown={down}
        onPointerMove={move}
        onPointerUp={up}
        onPointerCancel={up}
        onClickCapture={(e) => {
          if (suppressClick.current) {
            e.preventDefault();
            e.stopPropagation();
          }
        }}
        onTransitionEnd={(e) => e.target === e.currentTarget && phase === 'back' && setPhase('idle')}
      >
        {children}
      </div>
    </div>
  );
}
