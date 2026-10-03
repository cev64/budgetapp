package com.personal.budget.ui.screens.month

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.budget.AppContainer
import com.personal.budget.data.repository.BudgetSnapshot
import com.personal.budget.domain.model.MonthKey
import com.personal.budget.domain.model.Txn
import com.personal.budget.domain.usecase.MonthSummary
import com.personal.budget.domain.usecase.NewMonthPlanner
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class MonthTab { Budget, Transactions }

data class MonthUi(
    val key: MonthKey,
    val exists: Boolean,
    val summary: MonthSummary,
    val transactions: List<Txn>,
    /** "Start {next}" offer (DOMAIN_RULES §6), shown while viewing the latest month. */
    val suggestion: MonthKey?,
    val snapshot: BudgetSnapshot,
)

fun buildMonthUi(s: BudgetSnapshot, key: MonthKey): MonthUi {
    val book = s.book
    val latest = book.latestMonth()
    val suggestion = NewMonthPlanner.suggestedNextMonth(latest, MonthKey.now())?.takeIf { latest == null || key == latest }
    return MonthUi(
        key = key,
        exists = book.exists(key),
        summary = book.monthSummary(key),
        transactions = book.transactions(key).sortedWith(compareBy<Txn>({ it.date == null }, { it.date ?: "" }, { it.createdAt ?: "" })),
        suggestion = suggestion,
        snapshot = s,
    )
}

/** Month screen state that must survive fold/unfold: tab, selected category, filter. */
class MonthViewModel(private val c: AppContainer, private val handle: SavedStateHandle) : ViewModel() {
    val tab: StateFlow<String> = handle.getStateFlow(KEY_TAB, MonthTab.Budget.name)
    val selectedCategory: StateFlow<String?> = handle.getStateFlow(KEY_CAT, null)
    val filter: StateFlow<String?> = handle.getStateFlow(KEY_FILTER, null)
    val overrideOpen: StateFlow<Boolean> = handle.getStateFlow(KEY_OVERRIDE, false)

    fun setTab(t: MonthTab) {
        handle[KEY_TAB] = t.name
    }

    fun selectCategory(id: String?) {
        handle[KEY_CAT] = id
        handle[KEY_OVERRIDE] = false
    }

    fun setFilter(id: String?) {
        handle[KEY_FILTER] = id
    }

    fun setOverrideOpen(open: Boolean) {
        handle[KEY_OVERRIDE] = open
    }

    fun setClosed(key: MonthKey, closed: Boolean) = viewModelScope.launch { c.repository.setClosed(key, closed) }
    fun createMonth(key: MonthKey) = viewModelScope.launch { c.repository.createMonth(key) }
    fun setExpected(key: MonthKey, categoryId: String, v: Double?) = viewModelScope.launch { c.repository.setExpected(key, categoryId, v) }
    fun setActual(key: MonthKey, categoryId: String, v: Double?) = viewModelScope.launch { c.repository.setActual(key, categoryId, v) }
    fun deleteTransaction(id: String) = viewModelScope.launch { c.repository.deleteTransaction(id) }
    fun restoreTransaction(t: Txn) = viewModelScope.launch { c.repository.restoreTransaction(t) }

    companion object {
        private const val KEY_TAB = "month_tab"
        private const val KEY_CAT = "month_category"
        private const val KEY_FILTER = "month_filter"
        private const val KEY_OVERRIDE = "month_override_open"
    }
}
