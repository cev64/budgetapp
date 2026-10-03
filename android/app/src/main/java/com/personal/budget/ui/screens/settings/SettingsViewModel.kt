package com.personal.budget.ui.screens.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.budget.AppContainer
import com.personal.budget.data.repository.SignOutOutcome
import com.personal.budget.domain.model.BackupFile
import com.personal.budget.domain.model.BudgetSettings
import com.personal.budget.domain.model.Category
import com.personal.budget.domain.model.RecurringItem
import com.personal.budget.domain.usecase.BackupMerge
import com.personal.budget.domain.usecase.MergePlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

class SettingsViewModel(private val c: AppContainer, private val handle: SavedStateHandle) : ViewModel() {
    val config get() = c.config
    val lastSyncAt = c.prefsStore.prefs
    val pending = c.repository.pendingCount

    /** "category:new", "category:<id>", "recurring:new", "recurring:<id>" — survives fold/unfold. */
    val editing: StateFlow<String?> = handle.getStateFlow(KEY_EDIT, null)

    fun edit(target: String?) {
        handle[KEY_EDIT] = target
    }

    private val _importPreview = MutableStateFlow<MergePlan?>(null)
    val importPreview: StateFlow<MergePlan?> = _importPreview.asStateFlow()

    private val _signOutPending = MutableStateFlow<Int?>(null)
    /** Non-null = the final push left this many changes unsynced; ask before discarding. */
    val signOutPending: StateFlow<Int?> = _signOutPending.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun saveSettings(s: BudgetSettings) = viewModelScope.launch { c.repository.saveSettings(s) }

    fun saveCategory(cat: Category) = viewModelScope.launch { c.repository.saveCategory(cat) }
    fun setArchived(id: String, archived: Boolean) = viewModelScope.launch { c.repository.setCategoryArchived(id, archived) }
    suspend fun deleteCategory(id: String): Boolean = c.repository.deleteCategory(id)
    suspend fun categoryHasData(id: String): Boolean = c.repository.categoryHasData(id)

    fun move(categories: List<Category>, id: String, delta: Int) = viewModelScope.launch {
        val ids = categories.map { it.id }.toMutableList()
        val i = ids.indexOf(id)
        val j = i + delta
        if (i < 0 || j !in ids.indices) return@launch
        ids.add(j, ids.removeAt(i))
        c.repository.reorderCategories(ids)
    }

    fun saveRecurring(r: RecurringItem) = viewModelScope.launch { c.repository.saveRecurring(r) }
    fun deleteRecurring(id: String) = viewModelScope.launch { c.repository.deleteRecurring(id) }

    fun syncNow() = c.syncScheduler.requestSync()

    // Backup -----------------------------------------------------------------------------------

    suspend fun export(resolver: ContentResolver, uri: Uri): Result<Int> = runCatching {
        val file = c.repository.exportBackup()
        withContext(Dispatchers.IO) {
            resolver.openOutputStream(uri, "wt")?.use { it.write(BackupFile.encode(file).toByteArray()) }
                ?: error("Could not open the file")
        }
        file.transactions.size
    }

    suspend fun preview(resolver: ContentResolver, uri: Uri): Result<MergePlan> = runCatching {
        val text = withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("Could not open the file")
        }
        val file = try {
            BackupFile.parse(text)
        } catch (e: SerializationException) {
            throw IllegalArgumentException("That file isn't a Budget backup (${e.message?.take(80)})")
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("That file isn't a Budget backup")
        }
        val plan = withContext(Dispatchers.Default) { BackupMerge.plan(file, c.repository.existingKeys()) }
        _importPreview.value = plan
        plan
    }

    fun cancelImport() {
        _importPreview.value = null
    }

    suspend fun applyImport(): Boolean {
        val plan = _importPreview.value ?: return false
        _busy.value = true
        try {
            c.repository.applyImport(plan)
        } finally {
            _busy.value = false
            _importPreview.value = null
        }
        return true
    }

    // Sign out ---------------------------------------------------------------------------------

    suspend fun signOut(force: Boolean): Boolean {
        _busy.value = true
        try {
            return when (val r = c.accounts.signOut(force)) {
                SignOutOutcome.Done -> {
                    _signOutPending.value = null
                    true
                }
                is SignOutOutcome.Unsynced -> {
                    _signOutPending.value = r.pending
                    false
                }
            }
        } finally {
            _busy.value = false
        }
    }

    fun dismissSignOutWarning() {
        _signOutPending.value = null
    }

    companion object {
        private const val KEY_EDIT = "settings_edit"
    }
}
