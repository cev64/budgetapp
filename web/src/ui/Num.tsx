import { useLayoutEffect, useRef } from 'react';
import { replay, rollText } from './motion';

interface Props {
  value: number | null;
  format: (v: number | null) => string;
  className?: string;
  /** Also pop the number with the spring "bump". */
  bump?: boolean;
}

/** A number that rolls to its new value (and optionally bumps). Never animates on first render. */
export function Num({ value, format, className, bump }: Props) {
  const ref = useRef<HTMLSpanElement>(null);
  const prev = useRef<number | null | undefined>(undefined);
  const text = format(value);
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    const old = prev.current;
    prev.current = value;
    const dir = old == null || value == null || value >= old ? 'up' : 'down';
    rollText(el, text, dir);
    if (bump && old !== undefined && old !== value) replay(el, 'bump');
  }, [text, value, bump]);
  return <span ref={ref} className={`num${className ? ` ${className}` : ''}`} />;
}
