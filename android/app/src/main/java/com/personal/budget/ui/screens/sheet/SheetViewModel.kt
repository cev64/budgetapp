package com.personal.budget.ui.screens.sheet

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.budget.AppContainer
import com.personal.budget.domain.model.Account
import com.personal.budget.domain.model.LedgerEntry
import com.personal.budget.domain.model.MonthKey
import com.personal.budget.domain.model.Txn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Sheet view state that survives fold/unfold and process death (SavedStateHandle): the selected
 * year and tab ("summary" or "yyyy-MM"). All writes go through BudgetRepository (Room + dirty flag
 * + sync), exactly like the other screens.
 */
class SheetViewModel(private val c: AppContainer, private val handle: SavedStateHandle) : ViewModel() {
    /** 0 = follow the selected month's year. */
    val year: StateFlow<Int> = handle.getStateFlow(KEY_YEAR, 0)
    val tab: StateFlow<String> = handle.getStateFlow(KEY_TAB, SUMMARY)

    fun setYear(y: Int) {
        handle[KEY_YEAR] = y
        handle[KEY_TAB] = SUMMARY
    }

    fun selectTab(t: String) {
        handle[KEY_TAB] = t
    }

    fun selectMonth(m: MonthKey) {
        handle[KEY_YEAR] = m.year
        handle[KEY_TAB] = m.id
    }

    fun setExpected(key: MonthKey, categoryId: String, v: Double?) = viewModelScope.launch { c.repository.setExpected(key, categoryId, v) }
    fun setActual(key: MonthKey, categoryId: String, v: Double?) = viewModelScope.launch { c.repository.setActual(key, categoryId, v) }
    fun setClosed(key: MonthKey, closed: Boolean) = viewModelScope.launch { c.repository.setClosed(key, closed) }
    fun saveTransaction(t: Txn) = viewModelScope.launch { c.repository.saveTransaction(t) }
    fun deleteTransaction(id: String) = viewModelScope.launch { c.repository.deleteTransaction(id) }

    /** Move to another month, creating it first (DOMAIN_RULES §6) when it doesn't exist yet. */
    fun moveTransaction(t: Txn, to: MonthKey, create: Boolean) = viewModelScope.launch {
        if (create) c.repository.createMonth(to)
        c.repository.saveTransaction(t.copy(year = to.year, month = to.month))
    }

    /** The "+" tab: create a month and select it. */
    fun createMonth(m: MonthKey) = viewModelScope.launch {
        c.repository.createMonth(m)
        selectMonth(m)
    }

    fun setBalance(id: String, v: Double) = viewModelScope.launch { c.repository.setAccountBalance(id, v) }
    fun saveAccount(a: Account) = viewModelScope.launch { c.repository.saveAccount(a) }
    fun saveLedger(e: LedgerEntry) = viewModelScope.launch { c.repository.saveLedgerEntry(e) }
    fun setSettled(id: String, settled: Boolean) = viewModelScope.launch { c.repository.setSettled(id, settled) }
    fun deleteLedger(id: String) = viewModelScope.launch { c.repository.deleteLedgerEntry(id) }

    companion object {
        const val SUMMARY = "summary"
        private const val KEY_YEAR = "sheet_year"
        private const val KEY_TAB = "sheet_tab"
    }
}
