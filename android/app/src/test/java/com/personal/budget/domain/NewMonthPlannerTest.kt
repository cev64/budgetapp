package com.personal.budget.domain

import com.personal.budget.domain.model.Budget
import com.personal.budget.domain.model.Category
import com.personal.budget.domain.model.CategoryKind
import com.personal.budget.domain.model.MonthInfo
import com.personal.budget.domain.model.MonthKey
import com.personal.budget.domain.model.RecurringItem
import com.personal.budget.domain.model.Tracking
import com.personal.budget.domain.usecase.NewMonthPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class NewMonthPlannerTest {
    private val file = Fixtures.backup.withoutDeleted()
    private val categories = file.categories.map { it.toDomain() }
    private val months = file.months.map { it.toDomain() }
    private val budgets = file.budgets.map { it.toDomain() }
    private val recurring = file.recurringItems.map { it.toDomain() }
    private var n = 0
    private fun id() = "id-${n++}"

    @Test
    fun copiesExpectedFromMostRecentEarlierMonth_andAddsRecurring() {
        val plan = NewMonthPlanner.plan(MonthKey(2026, 2), months, categories, budgets, recurring, ::id)!!
        assertFalse(plan.month.closed)
        assertEquals(categories.size, plan.budgets.size)
        val byName = plan.budgets.associateBy { b -> categories.single { it.id == b.categoryId }.name }
        assertEquals(4200.0, byName.getValue("Paychecks").expected!!, 0.0)
        assertEquals(1250.0, byName.getValue("Rent").expected!!, 0.0)
        // Jan 2026 HSA budget row exists with expected = null -> copies null
        assertNull(byName.getValue("HSA").expected)
        plan.budgets.forEach { assertNull(it.actual) }
        // Streaming on day 31 is clamped to Feb 28 2026
        assertEquals(1, plan.transactions.size)
        val t = plan.transactions.single()
        assertEquals("2026-02-28", t.date)
        assertEquals("Streaming", t.item)
        assertEquals(15.99, t.amount, 0.0)
        assertEquals(2026, t.year)
        assertEquals(2, t.month)
    }

    @Test
    fun usesEarlierMonthsOnly_andSkipsArchived() {
        val archived = categories.first { it.name == "Fun" }.copy(archived = true)
        val cats = categories.map { if (it.id == archived.id) archived else it }
        val plan = NewMonthPlanner.plan(MonthKey(2025, 12).let { MonthKey(2025, 10) }, emptyList(), cats, budgets, emptyList(), ::id)!!
        // Nothing earlier than Oct 2025 -> every expected is null
        plan.budgets.forEach { assertNull(it.expected) }
        assertFalse(plan.budgets.any { it.categoryId == archived.id })
    }

    @Test
    fun noDayOfMonth_givesNullDate_inactiveSkipped() {
        val cat = Category("c", "Subs", CategoryKind.EXPENSE, Tracking.LEDGER)
        val items = listOf(
            RecurringItem("r1", "c", "No day", 5.0, null, true, 0),
            RecurringItem("r2", "c", "Inactive", 9.0, 3, false, 1),
        )
        val plan = NewMonthPlanner.plan(MonthKey(2026, 4), emptyList(), listOf(cat), listOf(Budget(2026, 3, "c", 50.0, 49.0)), items, ::id)!!
        assertEquals(1, plan.transactions.size)
        assertNull(plan.transactions.single().date)
        assertEquals(50.0, plan.budgets.single().expected!!, 0.0)
    }

    @Test
    fun existingMonth_doesNothing() {
        assertNull(NewMonthPlanner.plan(MonthKey(2026, 1), months, categories, budgets, recurring, ::id))
        assertNull(NewMonthPlanner.plan(MonthKey(2026, 1), listOf(MonthInfo(2026, 1)), categories, budgets, recurring, ::id))
    }

    @Test
    fun suggestion() {
        assertEquals(MonthKey(2026, 11), NewMonthPlanner.suggestedNextMonth(MonthKey(2026, 10), MonthKey(2026, 10)))
        assertEquals(MonthKey(2026, 9), NewMonthPlanner.suggestedNextMonth(MonthKey(2026, 8), MonthKey(2026, 10)))
        assertNull(NewMonthPlanner.suggestedNextMonth(MonthKey(2026, 11), MonthKey(2026, 10)))
        assertEquals(MonthKey(2026, 10), NewMonthPlanner.suggestedNextMonth(null, MonthKey(2026, 10)))
        assertEquals(MonthKey(2027, 1), MonthKey(2026, 12).next())
        assertEquals(MonthKey(2025, 12), MonthKey(2026, 1).previous())
    }
}
