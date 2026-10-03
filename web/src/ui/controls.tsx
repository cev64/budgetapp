import { useEffect, useId, useRef, useState, type InputHTMLAttributes, type ReactNode } from 'react';
import { amountInputText, parseAmount } from '../domain/format';

/** Toggle switch (role=switch): solid accent when on. */
export function Switch({
  checked, onChange, label, disabled,
}: { checked: boolean; onChange: (v: boolean) => void; label: string; disabled?: boolean }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      className={`switch${checked ? ' on' : ''}`}
      onClick={() => onChange(!checked)}
    >
      <span className="switch-knob" />
    </button>
  );
}

/**
 * actual / expected, capped visually at 100% with an over-budget tail in `bad` (DOMAIN_RULES §8).
 * `invert` is for income/savings, where going over is good (no red tail).
 */
export function Progress({ actual, expected, invert, thin }: { actual: number; expected: number; invert?: boolean; thin?: boolean }) {
  const a = Math.max(0, actual);
  const e = Math.max(0, expected);
  let fill: number;
  let tail = 0;
  if (a <= e) fill = e === 0 ? 0 : a / e;
  else if (invert) fill = 1;
  else {
    // Over budget: the budgeted share stays normal, the overspend is a red tail.
    fill = e / a;
    tail = 1 - fill;
  }
  return (
    <div className={`bar${thin ? ' thin' : ''}`} aria-hidden="true">
      <span className="bar-fill" style={{ width: `${fill * 100}%` }} />
      {tail > 0 && <span className="bar-tail" style={{ width: `${tail * 100}%` }} />}
    </div>
  );
}

interface MoneyFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange' | 'onBlur'> {
  value: number | null;
  /** Called on blur / Enter when the parsed value changed. Blank commits null when `nullable`. */
  onCommit: (v: number | null) => void;
  nullable?: boolean;
  label: string;
}

/** Inline amount input: commits on blur or Enter, Escape reverts, invalid input shakes back. */
export function MoneyField({ value, onCommit, nullable = true, label, className, ...rest }: MoneyFieldProps) {
  const [text, setText] = useState(amountInputText(value));
  const [bad, setBad] = useState(false);
  const focused = useRef(false);
  useEffect(() => {
    if (!focused.current) setText(amountInputText(value));
  }, [value]);

  const commit = () => {
    const parsed = parseAmount(text);
    if (parsed !== null && Number.isNaN(parsed)) {
      setBad(true);
      return false;
    }
    setBad(false);
    const next = parsed === null ? (nullable ? null : 0) : parsed;
    if (next !== value) onCommit(next);
    setText(amountInputText(next));
    return true;
  };

  return (
    <input
      {...rest}
      className={`input money${bad ? ' invalid' : ''}${className ? ` ${className}` : ''}`}
      inputMode="decimal"
      autoComplete="off"
      aria-label={label}
      aria-invalid={bad}
      value={text}
      onFocus={(e) => {
        focused.current = true;
        e.currentTarget.select();
      }}
      onChange={(e) => {
        setText(e.target.value);
        if (bad) setBad(false);
      }}
      onBlur={() => {
        focused.current = false;
        if (!commit()) setText(amountInputText(value));
        setBad(false);
      }}
      onKeyDown={(e) => {
        if (e.key === 'Enter') {
          e.preventDefault();
          if (commit()) e.currentTarget.blur();
        } else if (e.key === 'Escape') {
          e.stopPropagation();
          setText(amountInputText(value));
          setBad(false);
          e.currentTarget.blur();
        }
      }}
    />
  );
}

/** Labelled form field. */
export function Field({ label, hint, children, className }: { label: string; hint?: ReactNode; children: (id: string) => ReactNode; className?: string }) {
  const id = useId();
  return (
    <div className={`field${className ? ` ${className}` : ''}`}>
      <label className="field-label" htmlFor={id}>{label}</label>
      {children(id)}
      {hint && <div className="field-hint">{hint}</div>}
    </div>
  );
}

/** Text input that commits on blur / Enter (inline rename etc.). */
export function TextCommit({
  value, onCommit, label, placeholder, className, required,
}: { value: string; onCommit: (v: string) => void; label: string; placeholder?: string; className?: string; required?: boolean }) {
  const [text, setText] = useState(value);
  const focused = useRef(false);
  useEffect(() => {
    if (!focused.current) setText(value);
  }, [value]);
  const commit = () => {
    const v = text.trim();
    if (required && !v) {
      setText(value);
      return;
    }
    if (v !== value) onCommit(v);
  };
  return (
    <input
      className={`input${className ? ` ${className}` : ''}`}
      aria-label={label}
      placeholder={placeholder}
      value={text}
      onFocus={() => (focused.current = true)}
      onChange={(e) => setText(e.target.value)}
      onBlur={() => {
        focused.current = false;
        commit();
      }}
      onKeyDown={(e) => {
        if (e.key === 'Enter') e.currentTarget.blur();
        if (e.key === 'Escape') {
          e.stopPropagation();
          setText(value);
          focused.current = false;
          requestAnimationFrame(() => (e.target as HTMLInputElement).blur());
        }
      }}
    />
  );
}
