package com.personal.budget.domain

import com.personal.budget.domain.model.Category
import com.personal.budget.domain.model.CategoryKind
import com.personal.budget.domain.model.MonthInfo
import com.personal.budget.domain.model.MonthKey
import com.personal.budget.domain.model.Tracking
import com.personal.budget.domain.model.Txn
import com.personal.budget.domain.usecase.BudgetBook
import com.personal.budget.domain.usecase.LeftRow
import com.personal.budget.domain.usecase.LedgerRow
import com.personal.budget.domain.usecase.SheetModel
import com.personal.budget.domain.usecase.TotalKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetModelTest {
    private val key = MonthKey(2026, 10)

    private fun cat(name: String, kind: CategoryKind, tracking: Tracking, sort: Int, archived: Boolean = false) =
        Category("id-$name", name, kind, tracking, if (name == "401k") 2.0 else 1.0, sort, archived = archived)

    /** The 14 default categories of the spreadsheet. */
    private val defaults = listOf(
        cat("Paychecks", CategoryKind.INCOME, Tracking.LEDGER, 0),
        cat("Rent", CategoryKind.EXPENSE, Tracking.MANUAL, 10),
        cat("Subscriptions", CategoryKind.EXPENSE, Tracking.LEDGER, 11),
        cat("Food", CategoryKind.EXPENSE, Tracking.LEDGER, 12),
        cat("Fun", CategoryKind.EXPENSE, Tracking.LEDGER, 13),
        cat("Gas", CategoryKind.EXPENSE, Tracking.LEDGER, 14),
        cat("Misc", CategoryKind.EXPENSE, Tracking.LEDGER, 15),
        cat("Car Ins", CategoryKind.EXPENSE, Tracking.MANUAL, 16),
        cat("Utilities", CategoryKind.EXPENSE, Tracking.MANUAL, 17),
        cat("Phone Bill", CategoryKind.EXPENSE, Tracking.MANUAL, 18),
        cat("Roth", CategoryKind.SAVINGS, Tracking.MANUAL, 20),
        cat("401k", CategoryKind.SAVINGS, Tracking.MANUAL, 21),
        cat("Taxable Brokerage", CategoryKind.SAVINGS, Tracking.MANUAL, 22),
        cat("HSA", CategoryKind.SAVINGS, Tracking.MANUAL, 23),
    )

    private fun book(categories: List<Category> = defaults, txns: List<Txn> = emptyList()) =
        BudgetBook(categories, listOf(MonthInfo(2026, 10)), emptyList(), txns)

    @Test
    fun monthLeftBlock_matchesSheetRows2to23() {
        val rows = SheetModel.monthLeftRows(book(), key)
        // [2] header, [3] Paychecks, [4] blank, [5–13] 9 expenses, [14] blank, [15–18] savings, [19] blank, [20–22] totals, [23] closed
        assertEquals(22, rows.size)
        assertEquals(LeftRow.Header("October"), rows[0])
        assertEquals("Paychecks", (rows[1] as LeftRow.MonthCategory).line.category.name)
        assertEquals(LeftRow.Spacer, rows[2])
        assertEquals(
            listOf("Rent", "Subscriptions", "Food", "Fun", "Gas", "Misc", "Car Ins", "Utilities", "Phone Bill"),
            rows.subList(3, 12).map { (it as LeftRow.MonthCategory).line.category.name },
        )
        assertEquals(LeftRow.Spacer, rows[12])
        assertEquals(listOf("Roth", "401k", "Taxable Brokerage", "HSA"), rows.subList(13, 17).map { (it as LeftRow.MonthCategory).line.category.name })
        assertEquals(LeftRow.Spacer, rows[17])
        assertEquals(listOf(TotalKind.EXPENSES, TotalKind.SAVED, TotalKind.LEFTOVER), rows.subList(18, 21).map { (it as LeftRow.Total).kind })
        assertTrue(rows[21] is LeftRow.Closed)
    }

    @Test
    fun ledgerBlocks_followTheSheetColumns() {
        val cols = SheetModel.ledgerBlocks(book(), key).map { col -> col.map { it.category.name } }
        assertEquals(listOf(listOf("Paychecks", "Subscriptions", "Misc"), listOf("Food", "Gas"), listOf("Fun")), cols)
    }

    @Test
    fun unknownLedgerCategories_goToTheShortestColumn() {
        val txns = (1..5).map { Txn("f$it", 2026, 10, "id-Fun", "2026-10-0$it", "x", 1.0) }
        val extra1 = cat("Coffee", CategoryKind.EXPENSE, Tracking.LEDGER, 30)
        val extra2 = cat("Gifts", CategoryKind.EXPENSE, Tracking.LEDGER, 31)
        val cols = SheetModel.ledgerBlocks(book(defaults + extra1 + extra2, txns), key)
        // Heights before extras: col0 = 3 blocks (3+3+3 + 2 blanks = 11), col1 = 7, col2 = Fun with 5 txns = 8.
        assertEquals(listOf("Food", "Gas", "Coffee"), cols[1].map { it.category.name })
        // After Coffee col1 = 11, col2 = 8 → Gifts goes under Fun.
        assertEquals(listOf("Fun", "Gifts"), cols[2].map { it.category.name })
    }

    @Test
    fun archivedLedgerCategory_onlyWithDataThatMonth() {
        val cats = defaults.map { if (it.name == "Gas") it.copy(archived = true) else it }
        assertEquals(listOf("Food"), SheetModel.ledgerBlocks(book(cats), key)[1].map { it.category.name })
        val withData = SheetModel.ledgerBlocks(book(cats, listOf(Txn("g", 2026, 10, "id-Gas", null, "fill", 40.0))), key)
        assertEquals(listOf("Food", "Gas"), withData[1].map { it.category.name })
    }

    @Test
    fun transactionOrdering_dateAscUndatedLastThenCreated() {
        val t = listOf(
            Txn("a", 2026, 10, "c", null, "undated-late", 1.0, createdAt = "2026-10-05T00:00:00Z"),
            Txn("b", 2026, 10, "c", "2026-10-09", "ninth", 1.0, createdAt = "2026-10-01T00:00:00Z"),
            Txn("c", 2026, 10, "c", null, "undated-early", 1.0, createdAt = "2026-10-01T00:00:00Z"),
            Txn("d", 2026, 10, "c", "2026-10-02", "second-b", 1.0, createdAt = "2026-10-03T00:00:00Z"),
            Txn("e", 2026, 10, "c", "2026-10-02", "second-a", 1.0, createdAt = "2026-10-02T00:00:00Z"),
        )
        assertEquals(listOf("second-a", "second-b", "ninth", "undated-early", "undated-late"), SheetModel.orderTransactions(t).map { it.item })
    }

    @Test
    fun ledgerColumns_titleHeaderItemsAddRow_blankBetweenBlocks() {
        val txns = listOf(Txn("p1", 2026, 10, "id-Paychecks", "2026-10-15", "check", 2000.0))
        val col0 = SheetModel.ledgerColumns(book(txns = txns), key)[0]
        assertTrue(col0[0] is LedgerRow.Title)
        assertTrue(col0[1] is LedgerRow.ColumnHeader)
        assertEquals("check", (col0[2] as LedgerRow.Item).txn.item)
        assertTrue(col0[3] is LedgerRow.Add)
        assertEquals(LedgerRow.Blank, col0[4])
        assertEquals("Subscriptions", (col0[5] as LedgerRow.Title).category.name)
    }

    @Test
    fun summaryRow22_differenceIsLeftoverVsPlan() {
        val b = Fixtures.book()
        val settings = Fixtures.backup.settings!!.toDomain()
        for (year in b.years()) {
            val rows = SheetModel.summaryLeftRows(b, year, settings)!!
            val leftover = rows.filterIsInstance<LeftRow.Total>().single { it.kind == TotalKind.LEFTOVER }
            assertEquals(b.yearSummary(year, settings)!!.leftoverVsPlan, leftover.difference, 1e-9)
            assertEquals(3, rows.filterIsInstance<LeftRow.Metric>().size)
        }
        assertEquals(261.75, SheetModel.summaryLeftRows(b, 2025, settings)!!.filterIsInstance<LeftRow.Total>().last().difference, 1e-9)
        assertNull(SheetModel.summaryLeftRows(b, 2031, settings))
    }
}
