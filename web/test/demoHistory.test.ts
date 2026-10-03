import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { synthesizeHistory } from '../src/data/demoHistory';
import { datasetFromBackup, parseBackup } from '../src/domain/backup';
import { computeNetWorth } from '../src/domain/networth';

const ds = datasetFromBackup(parseBackup(JSON.parse(readFileSync(new URL('../../docs/fixtures/sample-backup.json', import.meta.url), 'utf8'))));
const nw = computeNetWorth({ accounts: ds.accounts, categories: ds.categories, budgets: ds.budgets, ledger: ds.ledger_entries });

describe('demo net worth history', () => {
  const h = synthesizeHistory(nw, '2026-10-03');
  it('400 consecutive days ending today at the current net worth', () => {
    expect(h).toHaveLength(400);
    expect(h[399]!.taken_on).toBe('2026-10-03');
    expect(h[0]!.taken_on).toBe('2025-08-30');
    expect(h[399]!.net_worth).toBeCloseTo(nw.netWorth, 2);
    expect(h[399]!.super_liquid).toBeCloseTo(nw.superLiquid, 2);
  });
  it('is deterministic and drifts upward', () => {
    expect(synthesizeHistory(nw, '2026-10-03')).toEqual(h);
    expect(h[0]!.net_worth).toBeLessThan(h[399]!.net_worth);
  });
  it('each day sums its accounts like §5', () => {
    for (const s of [h[0]!, h[200]!]) {
      const sum = s.accounts.reduce((a, b) => a + b.balance, 0) + s.reconciliations;
      expect(s.net_worth).toBeCloseTo(sum, 2);
    }
  });
});
