import { Component, createRef, type ReactNode } from 'react';
import { flip, snapshot, type Snapshot } from './motion';

interface Props {
  /** Changes whenever rows are added, removed or reordered (e.g. the joined row keys). */
  signature: string;
  /** Changing the scope (another month, another filter) re-renders without animating. */
  scope?: string;
  className?: string;
  role?: string;
  children: ReactNode;
}

/**
 * List wrapper with FLIP animations (FLUID_GLASS_UI §7.1). Rows need a stable `data-k`.
 * A class component because getSnapshotBeforeUpdate reads positions right before React mutates the DOM.
 */
export class FlipList extends Component<Props> {
  private ref = createRef<HTMLDivElement>();

  override getSnapshotBeforeUpdate(prev: Props): Snapshot | null {
    if (prev.signature === this.props.signature || prev.scope !== this.props.scope) return null;
    return snapshot(this.ref.current);
  }

  override componentDidUpdate(_prev: Props, _state: unknown, snap: Snapshot | null): void {
    if (snap) flip(this.ref.current, snap);
  }

  override render() {
    const { className, role, children } = this.props;
    return (
      <div ref={this.ref} className={`flip${className ? ` ${className}` : ''}`} role={role}>
        {children}
      </div>
    );
  }
}
