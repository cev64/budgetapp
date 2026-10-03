package com.personal.budget.widgets

import com.personal.budget.data.repository.BudgetSnapshot
import com.personal.budget.domain.model.CategoryKind
import com.personal.budget.domain.model.MonthKey

data class WidgetCategory(val name: String, val actual: Double, val expected: Double) {
    val remaining: Double get() = expected - actual
    val progress: Float get() = if (expected > 0) (actual / expected).toFloat().coerceIn(0f, 1f) else if (actual > 0) 1f else 0f
}

data class WidgetState(
    val monthLabel: String,
    val leftover: Double,
    val plannedLeftover: Double,
    val top: List<WidgetCategory>,
    val hasData: Boolean,
    /** Net worth over the last 30 days (DOMAIN_RULES §5b), for the large size's sparkline. */
    val netWorthTrend: List<Double> = emptyList(),
    val netWorth: Double? = null,
) {
    companion object {
        /** Current calendar month if it exists, else the latest month. Top 3 = biggest spend this month. */
        fun from(s: BudgetSnapshot, today: MonthKey = MonthKey.now()): WidgetState {
            val book = s.book
            val key = if (book.exists(today)) today else book.latestMonth()
                ?: return WidgetState(today.name, 0.0, 0.0, emptyList(), hasData = false)
            val summary = book.monthSummary(key)
            val top = summary.linesOf(CategoryKind.EXPENSE)
                .sortedWith(compareByDescending<com.personal.budget.domain.usecase.CategoryLine> { it.actual ?: 0.0 }.thenByDescending { it.expected })
                .take(3)
                .map { WidgetCategory(it.category.name, it.actual ?: 0.0, it.expected) }
            return WidgetState(
                monthLabel = key.name,
                leftover = summary.actual.leftover,
                plannedLeftover = summary.expected.leftover,
                top = top,
                hasData = true,
                netWorthTrend = com.personal.budget.domain.usecase.NetWorthHistoryMath
                    .series(s.netWorthHistory, com.personal.budget.domain.usecase.HistoryRange.M1).map { it.value },
                netWorth = s.netWorthHistory.lastOrNull()?.netWorth,
            )
        }
    }
}
