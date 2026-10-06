import { useId, useMemo, useState, type PointerEvent } from 'react';
import type { Point } from '../domain/history';
import { useWidth } from './hooks';

// Hand-rolled SVG area chart for net worth history (UI_ANATOMY "Net worth"): smooth accent line that
// draws in once per range (the area fades in after it), 14% accent fill fading to 0, faint baseline, a
// dashed zero line when the series crosses $0, first/last date labels, scrub cursor + glass tooltip.
// Remount it (key) to replay the draw-in, e.g. when the range changes.

type XY = [number, number];

const pad = { t: 10, b: 22, x: 4 };

/** Monotone cubic (Fritsch–Carlson) path: smooth, never overshoots the data. */
export function monotonePath(pts: XY[]): string {
  const n = pts.length;
  if (n === 0) return '';
  if (n === 1) return `M${pts[0]![0]},${pts[0]![1]}`;
  const dx: number[] = [];
  const m: number[] = [];
  for (let i = 0; i < n - 1; i++) {
    dx[i] = pts[i + 1]![0] - pts[i]![0];
    m[i] = dx[i]! === 0 ? 0 : (pts[i + 1]![1] - pts[i]![1]) / dx[i]!;
  }
  const t: number[] = [m[0]!];
  for (let i = 1; i < n - 1; i++) {
    const a = m[i - 1]!;
    const b = m[i]!;
    t[i] = a * b <= 0 ? 0 : (3 * (dx[i - 1]! + dx[i]!)) / ((2 * dx[i]! + dx[i - 1]!) / a + (dx[i]! + 2 * dx[i - 1]!) / b);
  }
  t[n - 1] = m[n - 2]!;
  const f = (v: number) => Math.round(v * 10) / 10;
  let d = `M${f(pts[0]![0])},${f(pts[0]![1])}`;
  for (let i = 0; i < n - 1; i++) {
    const [x0, y0] = pts[i]!;
    const [x1, y1] = pts[i + 1]!;
    const h = dx[i]! / 3;
    d += `C${f(x0 + h)},${f(y0 + t[i]! * h)} ${f(x1 - h)},${f(y1 - t[i + 1]! * h)} ${f(x1)},${f(y1)}`;
  }
  return d;
}

const dayMs = (iso: string) => Date.parse(`${iso}T00:00:00Z`);

export function dateLabel(iso: string, withYear: boolean): string {
  const d = new Date(`${iso}T12:00:00Z`);
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', ...(withYear ? { year: 'numeric' } : {}), timeZone: 'UTC' });
}

interface Props {
  points: Point[];
  /** Optional second line (e.g. Super liquid), drawn without fill. */
  overlay?: Point[];
  overlayLabel?: string;
  label: string;
  height: number;
  format: (v: number) => string;
}

export function HistoryChart({ points, overlay, overlayLabel, label, height, format }: Props) {
  const [ref, width] = useWidth<HTMLDivElement>();
  const [hover, setHover] = useState<number | null>(null);
  const gid = useId();

  const geo = useMemo(() => {
    if (width === 0 || points.length === 0) return null;
    const x0 = dayMs(points[0]!.date);
    const x1 = dayMs(points[points.length - 1]!.date);
    const values = [...points, ...(overlay ?? [])].map((p) => p.value);
    let lo = Math.min(...values);
    let hi = Math.max(...values);
    if (hi === lo) {
      hi += 1;
      lo -= 1;
    }
    const span = hi - lo;
    lo -= span * 0.08;
    hi += span * 0.08;
    const innerH = height - pad.t - pad.b;
    const x = (iso: string) => pad.x + ((dayMs(iso) - x0) / (x1 - x0 || 1)) * (width - pad.x * 2);
    const y = (v: number) => pad.t + ((hi - v) / (hi - lo)) * innerH;
    const xy = (ps: Point[]) => ps.filter((p) => dayMs(p.date) >= x0).map((p): XY => [x(p.date), y(p.value)]);
    const main = xy(points);
    const line = monotonePath(main);
    const base = height - pad.b;
    const area = `${line}L${main[main.length - 1]![0]},${base}L${main[0]![0]},${base}Z`;
    const over = overlay && overlay.length > 1 ? monotonePath(xy(overlay)) : null;
    const zero = lo < 0 && hi > 0 ? y(0) : null;
    return { x, y, main, line, area, over, base, zero };
  }, [width, points, overlay, height]);

  const overlayAt = (date: string) => overlay?.find((p) => p.date === date);

  const onMove = (e: PointerEvent<SVGSVGElement>) => {
    if (!geo) return;
    const r = e.currentTarget.getBoundingClientRect();
    const px = e.clientX - r.left;
    let best = 0;
    let bd = Infinity;
    geo.main.forEach(([mx], i) => {
      const d = Math.abs(mx - px);
      if (d < bd) {
        bd = d;
        best = i;
      }
    });
    setHover(best);
  };

  const first = points[0];
  const last = points[points.length - 1];
  const crossYear = first && last && first.date.slice(0, 4) !== last.date.slice(0, 4);
  const hp = hover != null ? points[hover] : null;
  const hxy = hover != null && geo ? geo.main[hover] : null;
  const ov = hp ? overlayAt(hp.date) : undefined;

  return (
    <div className="hchart" ref={ref} style={{ height }}>
      {geo && (
        <svg width={width} height={height} role="img" aria-label={label}
          onPointerMove={onMove} onPointerDown={onMove} onPointerLeave={() => setHover(null)} onPointerCancel={() => setHover(null)}>
          <defs>
            <linearGradient id={gid} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" className="hchart-fill-top" />
              <stop offset="1" className="hchart-fill-bottom" />
            </linearGradient>
          </defs>
          <line x1={0} x2={width} y1={geo.base} y2={geo.base} className="hchart-base" />
          <path d={geo.area} fill={`url(#${gid})`} className="hchart-area" />
          {geo.zero != null && <line x1={0} x2={width} y1={geo.zero} y2={geo.zero} className="hchart-zero" />}
          {geo.over && <path d={geo.over} className="hchart-overlay" />}
          <path d={geo.line} className="hchart-line draw" pathLength={1} />
          {hxy && (
            <g className="hchart-cursor">
              <line x1={hxy[0]} x2={hxy[0]} y1={pad.t - 4} y2={geo.base} />
              {ov && <circle cx={hxy[0]} cy={geo.y(ov.value)} r={3.5} className="hchart-dot overlay" />}
              <circle cx={hxy[0]} cy={hxy[1]} r={4.5} className="hchart-dot" />
            </g>
          )}
        </svg>
      )}
      {first && last && (
        <div className="hchart-dates micro" aria-hidden="true">
          <span>{dateLabel(first.date, Boolean(crossYear))}</span>
          <span>{dateLabel(last.date, Boolean(crossYear))}</span>
        </div>
      )}
      {hp && hxy && (
        <div
          className={`hchart-tip${hxy[1] < 84 ? ' below' : ''}`}
          style={{ left: Math.min(Math.max(hxy[0], 76), width - 76), top: hxy[1] < 84 ? hxy[1] + 14 : hxy[1] - 14 }}
        >
          <div className="micro">{dateLabel(hp.date, true)}</div>
          <div className="hchart-tip-value">{format(hp.value)}</div>
          {ov && <div className="hchart-tip-sub"><i className="key overlay" />{overlayLabel} {format(ov.value)}</div>}
        </div>
      )}
    </div>
  );
}

/** Tiny trend line for tiles. */
export function Sparkline({ points, width = 64, height = 22 }: { points: Point[]; width?: number; height?: number }) {
  if (points.length < 2) return null;
  const vs = points.map((p) => p.value);
  const lo = Math.min(...vs);
  const hi = Math.max(...vs);
  const xy = points.map((p, i): XY => [
    1 + (i / (points.length - 1)) * (width - 2),
    1 + (hi === lo ? (height - 2) / 2 : ((hi - p.value) / (hi - lo)) * (height - 2)),
  ]);
  const up = vs[vs.length - 1]! >= vs[0]!;
  return (
    <svg className={`spark ${up ? 'up' : 'down'}`} width={width} height={height} aria-hidden="true">
      <path d={monotonePath(xy)} pathLength={1} />
    </svg>
  );
}
