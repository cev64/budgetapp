import lockupSvg from '../../../design/brand/logo-lockup.svg?raw';
import markSvg from '../../../design/brand/logo-mark.svg?raw';

// The brand kit's own geometry (design/brand), inlined so it follows the theme: the brand's light
// colours (navy #08204F, blue #1059FC) map to the ink / accent tokens, whose dark values are exactly
// the dark lockup's colours (#F5F8FF, #4A82FF). No CSS letter spacing ever touches the outlined wordmark.

const themed = (svg: string) =>
  svg
    .replace(/fill="#08204F"/g, 'fill="var(--ink)"')
    .replace(/fill="#1059FC"/g, 'fill="var(--accent)"')
    .replace('<svg ', '<svg aria-hidden="true" focusable="false" width="100%" height="100%" ');

const LOCKUP = themed(lockupSvg);
const MARK = themed(markSvg);

/** Mark + "Budget" wordmark, 136×36 by default (minimum 109px wide). */
export function BrandLockup({ width = 136, label = 'Budget' }: { width?: number; label?: string }) {
  return (
    <span className="brand-lockup" role="img" aria-label={label}
      style={{ width, height: (width * 36) / 136 }} dangerouslySetInnerHTML={{ __html: LOCKUP }} />
  );
}

/** The split-ledger B on its own (16px minimum, 24–32px preferred). */
export function BrandMark({ size = 32, label = 'Budget' }: { size?: number; label?: string }) {
  return (
    <span className="brand-mark" role="img" aria-label={label}
      style={{ width: size, height: size }} dangerouslySetInnerHTML={{ __html: MARK }} />
  );
}
