package com.personal.budget.domain.usecase

import com.personal.budget.domain.model.BudgetSettings
import com.personal.budget.domain.model.Category
import com.personal.budget.domain.model.CategoryKind
import com.personal.budget.domain.model.MonthKey
import com.personal.budget.domain.model.Tracking
import com.personal.budget.domain.model.Txn

/**
 * The Sheet view's row/column model (docs/SHEET_VIEW.md): where every block of the original
 * Google Sheet sits. Pure; all numbers come from [BudgetBook]. Grid row 0 = sheet row 2.
 */
enum class TotalKind(val label: String, val diffKind: CategoryKind) {
    EXPENSES("Monthly Expenses", CategoryKind.EXPENSE),
    YEAR_EXPENSES("Expenses", CategoryKind.EXPENSE),
    SAVED("Saved (Roth, 401k, Brokerage)", CategoryKind.SAVINGS),
    LEFTOVER("Leftover", CategoryKind.INCOME),
}

/** Left block, sheet columns B C D E. */
sealed interface LeftRow {
    data class Header(val title: String) : LeftRow

    /** Month tab: one category, with its full §1 line (expected / actual / override). */
    data class MonthCategory(val line: CategoryLine) : LeftRow

    /** Summary tab: one category over the year (§4: actual = projected). */
    data class YearCategory(val category: Category, val expected: Double, val actual: Double) : LeftRow {
        val difference: Double get() = actual - expected
    }

    data object Spacer : LeftRow

    data class Total(val kind: TotalKind, val expected: Double, val actual: Double) : LeftRow {
        val difference: Double get() = actual - expected
    }

    /** Month tab row 23: the sheet's C23 checkbox. */
    data class Closed(val closed: Boolean) : LeftRow

    /** Summary rows 24–26. [percent] values are fractions. */
    data class Metric(val label: String, val expected: Double?, val actual: Double?, val percent: Boolean) : LeftRow
}

/** One row of a ledger column (sheet columns G–I, K–M, O–Q). */
sealed interface LedgerRow {
    data class Title(val category: Category) : LedgerRow
    data class ColumnHeader(val category: Category) : LedgerRow
    data class Item(val category: Category, val txn: Txn) : LedgerRow
    /** Empty editable row: typing in it creates a transaction in [category]. */
    data class Add(val category: Category) : LedgerRow
    data object Blank : LedgerRow
}

data class LedgerBlock(val category: Category, val transactions: List<Txn>) {
    /** Title + header + one per transaction + the add row. */
    val height: Int get() = 3 + transactions.size
}

object SheetModel {
    /** The sheet's ledger columns, top to bottom (G H I · K L M · O P Q). */
    val DEFAULT_LEDGER_COLUMNS: List<List<String>> = listOf(
        listOf("Paychecks", "Subscriptions", "Misc"),
        listOf("Food", "Gas"),
        listOf("Fun"),
    )

    /** Date ascending, undated last, then created order, then id (stable). */
    fun orderTransactions(txns: List<Txn>): List<Txn> = txns.sortedWith(
        compareBy<Txn>({ it.date == null }, { it.date ?: "" }, { it.createdAt == null }, { it.createdAt ?: "" }, { it.id }),
    )

    private fun groupedLeft(categories: List<Category>, row: (Category) -> LeftRow): List<LeftRow> {
        val out = mutableListOf<LeftRow>()
        for (kind in listOf(CategoryKind.INCOME, CategoryKind.EXPENSE, CategoryKind.SAVINGS)) {
            val group = categories.filter { it.kind == kind }
            if (group.isEmpty()) continue
            group.forEach { out += row(it) }
            out += LeftRow.Spacer
        }
        return out
    }

    /** Month tab B–E: header [2], income [3], expenses [5–13], savings [15–18], totals [20–22], closed [23]. */
    fun monthLeftRows(book: BudgetBook, key: MonthKey): List<LeftRow> {
        val summary = book.monthSummary(key)
        val visible = book.visibleCategories(key)
        return buildList {
            add(LeftRow.Header(key.name))
            addAll(groupedLeft(visible) { LeftRow.MonthCategory(book.line(key, it)) })
            add(LeftRow.Total(TotalKind.EXPENSES, summary.expected.expenses, summary.actual.expenses))
            add(LeftRow.Total(TotalKind.SAVED, summary.expected.saved, summary.actual.saved))
            add(LeftRow.Total(TotalKind.LEFTOVER, summary.expected.leftover, summary.actual.leftover))
            add(LeftRow.Closed(summary.closed))
        }
    }

    /** Summary tab B–E for a year; row 22's difference is the leftover vs plan (§4b). Null = no months. */
    fun summaryLeftRows(book: BudgetBook, year: Int, settings: BudgetSettings): List<LeftRow>? {
        val y = book.yearSummary(year, settings) ?: return null
        return buildList {
            add(LeftRow.Header(year.toString()))
            addAll(
                groupedLeft(y.categories) {
                    LeftRow.YearCategory(it, y.expected.perCategory[it.id] ?: 0.0, y.actual.perCategory[it.id] ?: 0.0)
                },
            )
            add(LeftRow.Total(TotalKind.YEAR_EXPENSES, y.expected.totals.expenses, y.actual.totals.expenses))
            add(LeftRow.Total(TotalKind.SAVED, y.expected.totals.saved, y.actual.totals.saved))
            add(LeftRow.Total(TotalKind.LEFTOVER, y.expected.totals.leftover, y.actual.totals.leftover))
            add(LeftRow.Spacer)
            add(LeftRow.Metric("Annualized Savings", y.expected.annualizedSavings, y.actual.annualizedSavings, percent = false))
            add(LeftRow.Metric("Percent of Net Income", y.expected.pctNetIncome, y.actual.pctNetIncome, percent = true))
            add(LeftRow.Metric("Percent of Gross Income", y.expected.pctGrossIncome, y.actual.pctGrossIncome, percent = true))
        }
    }

    /**
     * Ledger blocks per column: the sheet's fixed map first (by category name, case-insensitive);
     * ledger categories not in the map (user-created) go to the bottom of the shortest column
     * (ties → leftmost), in sort order. Archived categories appear only with data that month.
     */
    fun ledgerBlocks(book: BudgetBook, key: MonthKey): List<List<LedgerBlock>> {
        val ledgerCats = book.visibleCategories(key).filter { it.tracking == Tracking.LEDGER }
        val txns = book.transactions(key).groupBy { it.categoryId }
        fun block(c: Category) = LedgerBlock(c, orderTransactions(txns[c.id].orEmpty()))
        val placed = HashSet<String>()
        val columns = DEFAULT_LEDGER_COLUMNS.map { names ->
            names.mapNotNull { name -> ledgerCats.firstOrNull { it.name.trim().equals(name, ignoreCase = true) } }
                .onEach { placed += it.id }
                .map(::block)
                .toMutableList()
        }
        fun height(col: List<LedgerBlock>) = col.sumOf { it.height } + (col.size - 1).coerceAtLeast(0)
        for (c in ledgerCats) {
            if (c.id in placed) continue
            columns.minBy(::height).add(block(c))
        }
        return columns
    }

    /** Flattens [ledgerBlocks] into grid rows: one blank row between stacked blocks. */
    fun ledgerColumns(book: BudgetBook, key: MonthKey): List<List<LedgerRow>> = ledgerBlocks(book, key).map { col ->
        buildList {
            col.forEachIndexed { i, b ->
                if (i > 0) add(LedgerRow.Blank)
                add(LedgerRow.Title(b.category))
                add(LedgerRow.ColumnHeader(b.category))
                b.transactions.forEach { add(LedgerRow.Item(b.category, it)) }
                add(LedgerRow.Add(b.category))
            }
        }
    }
}
