import type { NetWorth } from '../domain/networth';
import type { NetWorthSnapshot, SnapshotAccount } from '../domain/types';
import { addDays } from '../domain/history';

// Demo mode only: a plausible, deterministic net worth history that ends at the fixture's current
// balances, so the history chart has something to show. Lives in memory; never written anywhere.

/** mulberry32: small seeded PRNG. */
function rng(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** Standard normal from two uniforms (Box–Muller). */
const normal = (r: () => number) => Math.sqrt(-2 * Math.log(1 - r())) * Math.cos(2 * Math.PI * r());

const round2 = (v: number) => Math.round(v * 100) / 100;

/**
 * Walks every account backwards from today with an upward drift (debts shrink over time, assets grow),
 * then derives net worth / super liquid per day exactly like §5: Σ balances + reconciliations.
 */
export function synthesizeHistory(current: NetWorth, today: string, days = 400, seed = 20261003): NetWorthSnapshot[] {
  const r = rng(seed);
  const n = days;
  const balances: number[][] = current.accounts.map((v) => {
    const b = v.balance;
    const scale = Math.max(40, Math.abs(b));
    const drift = scale * 0.0011;
    const sigma = scale * 0.007;
    const series = new Array<number>(n);
    series[n - 1] = b;
    for (let i = n - 2; i >= 0; i--) {
      // Occasional bigger moves (a bill, a bonus) keep the line from looking synthetic.
      const shock = r() < 0.03 ? normal(r) * scale * 0.04 : 0;
      let prev = series[i + 1]! - (drift + normal(r) * sigma + shock);
      if (b >= 0) prev = Math.max(0, prev);
      series[i] = prev;
    }
    return series.map(round2);
  });

  const out: NetWorthSnapshot[] = [];
  for (let i = 0; i < n; i++) {
    const accounts: SnapshotAccount[] = current.accounts.map((v, k) => ({
      id: v.account.id,
      name: v.account.name,
      group: v.account.account_group,
      liquid: v.account.liquid,
      balance: balances[k]![i]!,
    }));
    const total = accounts.reduce((s, a) => s + a.balance, 0);
    const liquid = accounts.filter((a) => a.liquid).reduce((s, a) => s + a.balance, 0);
    out.push({
      taken_on: addDays(today, i - (n - 1)),
      net_worth: round2(total + current.netReconciliations),
      super_liquid: round2(liquid),
      reconciliations: current.netReconciliations,
      accounts,
      source: 'auto',
      deleted: false,
    });
  }
  return out;
}
