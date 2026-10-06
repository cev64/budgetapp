/**
 * Ambient field behind every screen (docs/FLUID_GLASS_UI.md §3): three large, softly drifting radial
 * blobs so the glass surfaces have something to refract. Static with reduced motion.
 */
export function Backdrop() {
  return (
    <div className="backdrop" aria-hidden="true">
      <i className="b1" />
      <i className="b2" />
      <i className="b3" />
    </div>
  );
}
