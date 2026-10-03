package com.personal.budget.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.budget.AppContainer
import com.personal.budget.data.local.AppPrefs
import com.personal.budget.data.local.ThemeMode
import com.personal.budget.data.repository.AuthState
import com.personal.budget.data.repository.BudgetSnapshot
import com.personal.budget.data.repository.SyncPhase
import com.personal.budget.domain.model.MonthKey
import com.personal.budget.domain.model.Txn
import com.personal.budget.domain.usecase.Money
import com.personal.budget.ui.components.SyncIndicator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * The add/edit-transaction form. Kept in SavedStateHandle (as JSON) so the open sheet and
 * everything typed into it survive fold/unfold, rotation and process death.
 */
@Serializable
data class AddDraft(
    val txnId: String? = null,
    val amount: String = "",
    val categoryId: String? = null,
    val item: String = "",
    val date: String? = null,
    val year: Int,
    val month: Int,
    val note: String = "",
    val noteOpen: Boolean = false,
    val refund: Boolean = false,
    val createdAt: String? = null,
) {
    val key: MonthKey get() = MonthKey(year, month)
    val isEdit: Boolean get() = txnId != null
}

/** Activity-scoped state shared by every screen: auth, sync, theme, selected month, add sheet. */
class MainViewModel(private val c: AppContainer, private val handle: SavedStateHandle) : ViewModel() {

    val auth: StateFlow<AuthState> = c.auth.state

    val prefs: StateFlow<AppPrefs?> = c.prefsStore.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** null until Room has emitted once (the splash screen stays up meanwhile). */
    val snapshot: StateFlow<BudgetSnapshot?> = c.snapshot

    val syncIndicator: StateFlow<SyncIndicator> = combine(c.syncEngine.phase, c.repository.pendingCount, c.auth.state) { phase, pending, auth ->
        when {
            !c.config.isConfigured -> SyncIndicator.LocalOnly
            auth is AuthState.SignedIn && !auth.sessionValid -> SyncIndicator.SignIn
            phase == SyncPhase.Syncing -> SyncIndicator.Syncing
            phase is SyncPhase.Error -> SyncIndicator.Error(phase.message)
            phase == SyncPhase.NeedsSignIn -> SyncIndicator.SignIn
            phase is SyncPhase.Offline -> SyncIndicator.Pending(pending, offline = true)
            pending > 0 -> SyncIndicator.Pending(pending, offline = false)
            else -> SyncIndicator.Synced
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SyncIndicator.Synced)

    // Selected month (Month screen + default for Add) ---------------------------------------------

    private val selYear = handle.getStateFlow(KEY_SEL_YEAR, 0)
    private val selMonth = handle.getStateFlow(KEY_SEL_MONTH, 0)

    /** The month the user is looking at; defaults to the current month, else the latest month. */
    val selectedMonth: StateFlow<MonthKey> = combine(selYear, selMonth, c.snapshot) { y, m, snap ->
        if (y > 0 && m in 1..12) MonthKey(y, m) else defaultMonth(snap)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MonthKey.now())

    fun defaultMonth(snap: BudgetSnapshot?): MonthKey {
        val today = MonthKey.now()
        val book = snap?.book ?: return today
        return if (book.exists(today)) today else book.latestMonth() ?: today
    }

    fun selectMonth(key: MonthKey) {
        handle[KEY_SEL_YEAR] = key.year
        handle[KEY_SEL_MONTH] = key.month
    }

    // Add / edit sheet ---------------------------------------------------------------------------

    val addDraft: StateFlow<AddDraft?> = handle.getStateFlow<String?>(KEY_DRAFT, null)
        .map { s -> s?.let { runCatching { json.decodeFromString(AddDraft.serializer(), it) }.getOrNull() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun openAdd(categoryId: String? = null, month: MonthKey? = null) {
        val m = month ?: selectedMonth.value
        setDraft(AddDraft(categoryId = categoryId, date = LocalDate.now().toString(), year = m.year, month = m.month))
    }

    fun openEdit(t: Txn) {
        setDraft(
            AddDraft(
                txnId = t.id,
                amount = Money.formatInput(kotlin.math.abs(t.amount)),
                categoryId = t.categoryId,
                item = t.item,
                date = t.date,
                year = t.year,
                month = t.month,
                note = t.note.orEmpty(),
                noteOpen = !t.note.isNullOrBlank(),
                refund = t.amount < 0,
                createdAt = t.createdAt,
            ),
        )
    }

    fun updateDraft(d: AddDraft) = setDraft(d)

    fun closeAdd() {
        handle[KEY_DRAFT] = null
    }

    private fun setDraft(d: AddDraft) {
        handle[KEY_DRAFT] = json.encodeToString(AddDraft.serializer(), d)
    }

    /** Validates and saves the draft. Returns a toast message, or an error in [Result.failure]. */
    suspend fun saveDraft(d: AddDraft): Result<String> {
        val amount = Money.parse(d.amount) ?: return Result.failure(IllegalArgumentException("Enter an amount"))
        val categoryId = d.categoryId ?: return Result.failure(IllegalArgumentException("Pick a category"))
        val signed = if (d.refund) -kotlin.math.abs(amount) else amount
        val snap = c.snapshot.value
        if (snap != null && !snap.book.exists(d.key)) c.repository.createMonth(d.key)
        c.repository.saveTransaction(
            Txn(
                id = d.txnId.orEmpty(),
                year = d.year,
                month = d.month,
                categoryId = categoryId,
                date = d.date?.takeIf { it.isNotBlank() },
                item = d.item.trim(),
                amount = signed,
                note = d.note.trim().takeIf { it.isNotEmpty() },
                createdAt = d.createdAt,
            ),
        )
        // Brand voice (UI_ANATOMY Brand v2). Shown only after the Room write succeeded.
        val kind = snap?.book?.categoriesById?.get(categoryId)?.kind
        val what = when {
            signed < 0 -> "Refund"
            kind == com.personal.budget.domain.model.CategoryKind.INCOME -> "Income"
            else -> "Expense"
        }
        return Result.success("$what saved. Your budget is up to date.")
    }

    suspend fun deleteTransaction(id: String) = c.repository.deleteTransaction(id)
    suspend fun restoreTransaction(t: Txn) = c.repository.restoreTransaction(t)

    // Misc ---------------------------------------------------------------------------------------

    fun syncNow() = c.syncScheduler.requestSync()

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { c.prefsStore.setTheme(mode) }
    fun setDynamicColor(on: Boolean) = viewModelScope.launch { c.prefsStore.setDynamicColor(on) }

    companion object {
        private const val KEY_SEL_YEAR = "selected_year"
        private const val KEY_SEL_MONTH = "selected_month"
        private const val KEY_DRAFT = "add_draft"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
