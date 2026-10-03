import type { Account, AccountGroup, Budget, Category, LedgerEntry } from './types';
import { byOrder, live } from './calc';

// DOMAIN_RULES §5: net worth, super liquid assets, net reconciliations.

export interface AccountView {
  account: Account;
  balance: number;
  linked: Category | undefined;
  /** Σ raw budget.actual for the linked category (before the multiplier). */
  contributions: number;
}

export interface NetWorth {
  accounts: AccountView[];
  byGroup: Record<AccountGroup, AccountView[]>;
  netReconciliations: number;
  netWorth: number;
  superLiquid: number;
  investments: number;
}

export const GROUPS: AccountGroup[] = ['cash', 'investment', 'asset', 'debt'];
export const GROUP_LABEL: Record<AccountGroup, string> = {
  cash: 'Cash',
  investment: 'Investments',
  asset: 'Assets',
  debt: 'Debts',
};

/** Σ over all months and years of the manually entered budget.actual, per category. */
export function actualSums(budgets: readonly Budget[]): Map<string, number> {
  const sums = new Map<string, number>();
  for (const b of live(budgets)) sums.set(b.category_id, (sums.get(b.category_id) ?? 0) + (b.actual ?? 0));
  return sums;
}

export function computeNetWorth(input: {
  accounts: readonly Account[];
  categories: readonly Category[];
  budgets: readonly Budget[];
  ledger: readonly LedgerEntry[];
}): NetWorth {
  const cats = new Map(live(input.categories).map((c) => [c.id, c]));
  const sums = actualSums(input.budgets);
  const accounts = live(input.accounts)
    .filter((a) => !a.archived)
    .sort(byOrder)
    .map((account): AccountView => {
      if (!account.linked_category_id) return { account, balance: account.balance, linked: undefined, contributions: 0 };
      const linked = cats.get(account.linked_category_id);
      const contributions = sums.get(account.linked_category_id) ?? 0;
      const multiplier = linked?.match_multiplier ?? 1;
      return { account, balance: account.base_amount + multiplier * contributions, linked, contributions };
    });

  const byGroup = { cash: [], investment: [], asset: [], debt: [] } as Record<AccountGroup, AccountView[]>;
  for (const v of accounts) (byGroup[v.account.account_group] ?? byGroup.cash).push(v);

  const netReconciliations = live(input.ledger)
    .filter((e) => !e.settled)
    .reduce((s, e) => s + e.amount, 0);
  const total = accounts.reduce((s, v) => s + v.balance, 0);
  return {
    accounts,
    byGroup,
    netReconciliations,
    netWorth: total + netReconciliations,
    superLiquid: accounts.filter((v) => v.account.liquid).reduce((s, v) => s + v.balance, 0),
    investments: byGroup.investment.reduce((s, v) => s + v.balance, 0),
  };
}
