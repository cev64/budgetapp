package com.personal.budget.domain.usecase

import com.personal.budget.domain.model.Account
import com.personal.budget.domain.model.Budget
import com.personal.budget.domain.model.BudgetSettings
import com.personal.budget.domain.model.Category
import com.personal.budget.domain.model.CategoryKind
import com.personal.budget.domain.model.LedgerEntry
import com.personal.budget.domain.model.MonthInfo
import com.personal.budget.domain.model.MonthKey
import com.personal.budget.domain.model.Tracking
import com.personal.budget.domain.model.Txn

/** Totals of one column (expected or actual), docs/DOMAIN_RULES.md §2. */
data class Totals(
    val income: Double,
    val expenses: Double,
    val saved: Double,
    val contributions: Double,
) {
    val leftover: Double get() = income - expenses - contributions

    companion object {
        val ZERO = Totals(0.0, 0.0, 0.0, 0.0)

        /** Sums [value] over [categories] by kind. Savings apply the match multiplier to `saved` only. */
        fun of(categories: Collection<Category>, value: (Category) -> Double?): Totals {
            var income = 0.0
            var expenses = 0.0
            var saved = 0.0
            var contributions = 0.0
            for (c in categories) {
                val v = value(c) ?: 0.0
                when (c.kind) {
                    CategoryKind.INCOME -> income += v
                    CategoryKind.EXPENSE -> expenses += v
                    CategoryKind.SAVINGS -> {
                        saved += v * c.matchMultiplier
                        contributions += v
                    }
                }
            }
            return Totals(income, expenses, saved, contributions)
        }
    }
}

/** One category in one month, §1. */
data class CategoryLine(
    val category: Category,
    val expected: Double,
    /** null = blank (manual category with nothing typed). Counts as 0. */
    val actual: Double?,
    val ledgerSum: Double,
    /** A typed actual on a ledger category (the "manual" marker). */
    val overridden: Boolean,
    val hasBudgetRow: Boolean,
) {
    val difference: Double get() = (actual ?: 0.0) - expected
    /** actual / expected for progress bars; null when nothing is budgeted. */
    val progress: Double? get() = if (expected != 0.0) (actual ?: 0.0) / expected else null
}

data class MonthSummary(
    val key: MonthKey,
    val info: MonthInfo?,
    val lines: List<CategoryLine>,
    val expected: Totals,
    val actual: Totals,
) {
    val closed: Boolean get() = info?.closed == true
    fun linesOf(kind: CategoryKind) = lines.filter { it.category.kind == kind }
}

data class YearColumn(
    val totals: Totals,
    val perCategory: Map<String, Double>,
    val annualizedSavings: Double,
    val pctNetIncome: Double?,
    val pctGrossIncome: Double?,
)

data class YearMonthPoint(
    val key: MonthKey,
    val closed: Boolean,
    /** Expected totals for this month. */
    val expected: Totals,
    /** Projected totals (actuals for closed months, budget otherwise). */
    val projected: Totals,
)

data class YearSummary(
    val year: Int,
    val monthCount: Int,
    val categories: List<Category>,
    val expected: YearColumn,
    val actual: YearColumn,
    val months: List<YearMonthPoint>,
)

data class NetWorthSummary(
    /** Computed balance per non-archived account id. */
    val balances: Map<String, Double>,
    val netReconciliations: Double,
    val netWorth: Double,
    val superLiquid: Double,
    val investments: Double,
)

/**
 * The spreadsheet math (docs/DOMAIN_RULES.md §1–§5) over an in-memory snapshot.
 * Callers pass only non-deleted rows. Archived categories still count wherever they have data.
 */
class BudgetBook(
    categories: List<Category>,
    months: List<MonthInfo>,
    budgets: List<Budget>,
    transactions: List<Txn>,
) {
    val categories: List<Category> = categories.sortedWith(compareBy<Category>({ it.sortOrder }, { it.name }))
    val categoriesById: Map<String, Category> = this.categories.associateBy { it.id }
    val months: List<MonthInfo> = months.sortedBy { it.key }
    private val monthsByKey: Map<MonthKey, MonthInfo> = this.months.associateBy { it.key }
    private val budgetIndex: Map<Triple<Int, Int, String>, Budget> = budgets.associateBy { Triple(it.year, it.month, it.categoryId) }
    private val budgetsAll: List<Budget> = budgets
    private val ledgerIndex: Map<Triple<Int, Int, String>, Double>
    private val txnsByMonth: Map<MonthKey, List<Txn>>

    init {
        val ledger = HashMap<Triple<Int, Int, String>, Double>()
        for (t in transactions) {
            val k = Triple(t.year, t.month, t.categoryId)
            ledger[k] = (ledger[k] ?: 0.0) + t.amount
        }
        ledgerIndex = ledger
        txnsByMonth = transactions.groupBy { it.key }
    }

    fun month(key: MonthKey): MonthInfo? = monthsByKey[key]
    fun exists(key: MonthKey): Boolean = monthsByKey.containsKey(key)
    fun latestMonth(): MonthKey? = months.lastOrNull()?.key
    fun budget(key: MonthKey, categoryId: String): Budget? = budgetIndex[Triple(key.year, key.month, categoryId)]
    fun transactions(key: MonthKey): List<Txn> = txnsByMonth[key].orEmpty()

    fun expected(key: MonthKey, categoryId: String): Double = budget(key, categoryId)?.expected ?: 0.0

    fun ledgerSum(key: MonthKey, categoryId: String): Double = ledgerIndex[Triple(key.year, key.month, categoryId)] ?: 0.0

    /** §1: manual entry / override wins, then the ledger sum for ledger categories, else null. */
    fun actual(key: MonthKey, categoryId: String): Double? {
        budget(key, categoryId)?.actual?.let { return it }
        val c = categoriesById[categoryId] ?: return null
        return if (c.tracking == Tracking.LEDGER) ledgerSum(key, categoryId) else null
    }

    /** §3: closed months use the actual unless it is 0/blank; open months use the budget. */
    fun projected(key: MonthKey, categoryId: String): Double {
        val a = actual(key, categoryId) ?: 0.0
        val closed = monthsByKey[key]?.closed == true
        return if (closed && a != 0.0) a else expected(key, categoryId)
    }

    fun line(key: MonthKey, category: Category): CategoryLine {
        val b = budget(key, category.id)
        return CategoryLine(
            category = category,
            expected = b?.expected ?: 0.0,
            actual = actual(key, category.id),
            ledgerSum = ledgerSum(key, category.id),
            overridden = category.tracking == Tracking.LEDGER && b?.actual != null,
            hasBudgetRow = b != null,
        )
    }

    /** Categories shown for a month: non-archived ones plus archived ones that have data there. */
    fun visibleCategories(key: MonthKey): List<Category> = categories.filter { c ->
        !c.archived || budget(key, c.id) != null || transactions(key).any { it.categoryId == c.id }
    }

    fun monthSummary(key: MonthKey): MonthSummary {
        val lines = visibleCategories(key).map { line(key, it) }
        return MonthSummary(
            key = key,
            info = monthsByKey[key],
            lines = lines,
            expected = Totals.of(categories) { expected(key, it.id) },
            actual = Totals.of(categories) { actual(key, it.id) },
        )
    }

    fun years(): List<Int> = months.map { it.year }.distinct()

    /** §4. Returns null when the year has no months (empty state). */
    fun yearSummary(year: Int, settings: BudgetSettings): YearSummary? {
        val ms = months.filter { it.year == year }.map { it.key }
        val n = ms.size
        if (n == 0) return null

        fun column(value: (MonthKey, String) -> Double): YearColumn {
            val per = categories.associate { c -> c.id to ms.sumOf { m -> value(m, c.id) } }
            val totals = Totals.of(categories) { per[it.id] }
            val annualized = (totals.saved + totals.leftover) * 12.0 / n
            return YearColumn(
                totals = totals,
                perCategory = per,
                annualizedSavings = annualized,
                pctNetIncome = if (settings.netIncome != 0.0) annualized / settings.netIncome else null,
                pctGrossIncome = if (settings.grossIncome != 0.0) annualized / settings.grossIncome else null,
            )
        }

        val points = ms.map { m ->
            YearMonthPoint(
                key = m,
                closed = monthsByKey[m]?.closed == true,
                expected = Totals.of(categories) { expected(m, it.id) },
                projected = Totals.of(categories) { projected(m, it.id) },
            )
        }
        val usedCategories = categories.filter { c -> !c.archived || ms.any { m -> budget(m, c.id) != null || ledgerSum(m, c.id) != 0.0 } }
        return YearSummary(
            year = year,
            monthCount = n,
            categories = usedCategories,
            expected = column { m, c -> expected(m, c) },
            actual = column { m, c -> projected(m, c) },
            months = points,
        )
    }

    /** §5: Σ of raw typed actuals for a category over all months and years (for linked accounts). */
    fun sumOfTypedActuals(categoryId: String): Double = budgetsAll
        .asSequence()
        .filter { it.categoryId == categoryId }
        .distinctBy { Triple(it.year, it.month, it.categoryId) }
        .sumOf { it.actual ?: 0.0 }
}

object NetWorthMath {
    /** §5. Archived accounts are excluded everywhere. */
    fun compute(book: BudgetBook, accounts: List<Account>, ledger: List<LedgerEntry>): NetWorthSummary {
        val active = accounts.filter { !it.archived }
        val balances = active.associate { it.id to balanceOf(book, it) }
        val recon = ledger.filter { !it.settled }.sumOf { it.amount }
        val liquid = active.filter { it.liquid }.sumOf { balances.getValue(it.id) }
        val investments = active.filter { it.group == com.personal.budget.domain.model.AccountGroup.INVESTMENT }
            .sumOf { balances.getValue(it.id) }
        return NetWorthSummary(
            balances = balances,
            netReconciliations = recon,
            netWorth = balances.values.sum() + recon,
            superLiquid = liquid,
            investments = investments,
        )
    }

    fun balanceOf(book: BudgetBook, account: Account): Double {
        val linked = account.linkedCategoryId ?: return account.balance
        val multiplier = book.categoriesById[linked]?.matchMultiplier ?: 1.0
        return account.baseAmount + multiplier * book.sumOfTypedActuals(linked)
    }
}
