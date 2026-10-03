import { useRef } from 'react';
import { useGlide } from './hooks';

interface Props<T extends string> {
  value: T;
  options: { value: T; label: string }[];
  onChange: (v: T) => void;
  label: string;
  className?: string;
}

/** Segmented control: one pill glides to the selection. */
export function Seg<T extends string>({ value, options, onChange, label, className }: Props<T>) {
  const ref = useRef<HTMLDivElement>(null);
  useGlide(ref, value);
  return (
    <div ref={ref} className={`seg${className ? ` ${className}` : ''}`} role="tablist" aria-label={label}>
      {options.map((o) => (
        <button key={o.value} type="button" role="tab" aria-selected={o.value === value} onClick={() => onChange(o.value)}>
          {o.label}
        </button>
      ))}
    </div>
  );
}
