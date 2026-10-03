package com.personal.budget.domain.usecase

import com.personal.budget.domain.model.Budget
import com.personal.budget.domain.model.Category
import com.personal.budget.domain.model.MonthInfo
import com.personal.budget.domain.model.MonthKey
import com.personal.budget.domain.model.RecurringItem
import com.personal.budget.domain.model.Txn

/** What creating a month adds. Ids for transactions come from [newId]. */
data class NewMonthPlan(
    val month: MonthInfo,
    val budgets: List<Budget>,
    val transactions: List<Txn>,
)

/** docs/DOMAIN_RULES.md §6. Pure: the repository applies the returned plan. */
object NewMonthPlanner {
    fun plan(
        key: MonthKey,
        existingMonths: List<MonthInfo>,
        categories: List<Category>,
        budgets: List<Budget>,
        recurring: List<RecurringItem>,
        newId: () -> String,
    ): NewMonthPlan? {
        if (existingMonths.any { it.year == key.year && it.month == key.month }) return null // §6.4

        val earlierByCategory = budgets
            .filter { it.key < key }
            .groupBy { it.categoryId }
            .mapValues { (_, rows) -> rows.maxBy { it.key } }

        val newBudgets = categories
            .filter { !it.archived }
            .map { c ->
                Budget(
                    year = key.year,
                    month = key.month,
                    categoryId = c.id,
                    expected = earlierByCategory[c.id]?.expected,
                    actual = null,
                )
            }

        val txns = recurring
            .filter { it.active }
            .sortedBy { it.sortOrder }
            .map { r ->
                Txn(
                    id = newId(),
                    year = key.year,
                    month = key.month,
                    categoryId = r.categoryId,
                    date = r.dayOfMonth?.let { day ->
                        val d = day.coerceIn(1, key.lengthOfMonth)
                        "%04d-%02d-%02d".format(key.year, key.month, d)
                    },
                    item = r.item,
                    amount = r.amount,
                )
            }

        return NewMonthPlan(MonthInfo(key.year, key.month, closed = false), newBudgets, txns)
    }

    /**
     * The month the app offers to start: the one after the latest existing month, when the
     * latest month is the current calendar month or earlier (§6). With no months at all, the
     * current month.
     */
    fun suggestedNextMonth(latest: MonthKey?, today: MonthKey): MonthKey? = when {
        latest == null -> today
        latest <= today -> latest.next()
        else -> null
    }
}
