package com.personal.budget.data.repository

import androidx.room.withTransaction
import com.personal.budget.data.local.AccountEntity
import com.personal.budget.data.local.BudgetDatabase
import com.personal.budget.data.local.BudgetEntity
import com.personal.budget.data.local.CategoryEntity
import com.personal.budget.data.local.LedgerEntryEntity
import com.personal.budget.data.local.MonthEntity
import com.personal.budget.data.local.NetWorthSnapshotEntity
import com.personal.budget.data.local.RecurringItemEntity
import com.personal.budget.data.local.SettingsEntity
import com.personal.budget.data.local.TransactionEntity
import com.personal.budget.domain.model.Account
import com.personal.budget.domain.model.BackupFile
import com.personal.budget.domain.model.BackupAccount
import com.personal.budget.domain.model.BackupBudget
import com.personal.budget.domain.model.BackupCategory
import com.personal.budget.domain.model.BackupLedgerEntry
import com.personal.budget.domain.model.BackupMonth
import com.personal.budget.domain.model.BackupRecurringItem
import com.personal.budget.domain.model.BackupSettings
import com.personal.budget.domain.model.BackupSnapshot
import com.personal.budget.domain.model.BackupSnapshotAccount
import com.personal.budget.domain.model.NetWorthSnapshot
import com.personal.budget.domain.model.BackupTransaction
import com.personal.budget.domain.model.BudgetSettings
import com.personal.budget.domain.model.Category
import com.personal.budget.domain.model.LedgerEntry
import com.personal.budget.domain.model.MonthKey
import com.personal.budget.domain.model.RecurringItem
import com.personal.budget.domain.model.Txn
import com.personal.budget.domain.usecase.BudgetBook
import com.personal.budget.domain.usecase.ExistingKeys
import com.personal.budget.domain.usecase.MergePlan
import com.personal.budget.domain.usecase.NewMonthPlanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

/** Everything the UI needs, as pure domain objects. Recomputed whenever any table changes. */
data class BudgetSnapshot(
    val book: BudgetBook,
    val settings: BudgetSettings,
    val transactions: List<Txn>,
    val recurring: List<RecurringItem>,
    val accounts: List<Account>,
    val ledger: List<LedgerEntry>,
    /** Net worth history, oldest first (DOMAIN_RULES §5b). */
    val netWorthHistory: List<NetWorthSnapshot> = emptyList(),
) {
    companion object {
        val EMPTY = BudgetSnapshot(BudgetBook(emptyList(), emptyList(), emptyList(), emptyList()), BudgetSettings(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}

/**
 * The only writer of user data. Every mutation runs in a Room transaction, marks the row dirty
 * and bumps its local_version (so an in-flight push can't clear an edit made after it started),
 * then notifies [onLocalChange] (debounced sync + widget refresh).
 */
class BudgetRepository(
    private val db: BudgetDatabase,
    private val onLocalChange: () -> Unit = {},
    /** After an import: ask the server for a fresh net-worth snapshot on the next sync. */
    private val onImported: () -> Unit = {},
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val settingsDao = db.settingsDao()
    private val categoryDao = db.categoryDao()
    private val monthDao = db.monthDao()
    private val budgetDao = db.budgetDao()
    private val txnDao = db.transactionDao()
    private val recurringDao = db.recurringItemDao()
    private val accountDao = db.accountDao()
    private val ledgerDao = db.ledgerEntryDao()
    private val snapshotDao = db.netWorthSnapshotDao()

    // ------------------------------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------------------------------

    val snapshot: Flow<BudgetSnapshot> = combine(
        categoryDao.observe(),
        monthDao.observe(),
        budgetDao.observe(),
        txnDao.observe(),
        settingsDao.observe(),
        recurringDao.observe(),
        accountDao.observe(),
        ledgerDao.observe(),
        snapshotDao.observe(),
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        val cats = (arr[0] as List<CategoryEntity>).map { it.toDomain() }
        @Suppress("UNCHECKED_CAST")
        val months = (arr[1] as List<MonthEntity>).map { it.toDomain() }
        @Suppress("UNCHECKED_CAST")
        val budgets = (arr[2] as List<BudgetEntity>).map { it.toDomain() }
        @Suppress("UNCHECKED_CAST")
        val txns = (arr[3] as List<TransactionEntity>).map { it.toDomain() }
        val settings = (arr[4] as SettingsEntity?).toDomain()
        @Suppress("UNCHECKED_CAST")
        BudgetSnapshot(
            book = BudgetBook(cats, months, budgets, txns),
            settings = settings,
            transactions = txns,
            recurring = (arr[5] as List<RecurringItemEntity>).map { it.toDomain() },
            accounts = (arr[6] as List<AccountEntity>).map { it.toDomain() },
            ledger = (arr[7] as List<LedgerEntryEntity>).map { it.toDomain() },
            netWorthHistory = (arr[8] as List<NetWorthSnapshotEntity>).map { it.toDomain() },
        )
    }

    val pendingCount: Flow<Int> = db.syncStateDao().observePendingCount().distinctUntilChanged()

    val categories: Flow<List<Category>> = categoryDao.observe().map { l -> l.map { it.toDomain() } }

    // ------------------------------------------------------------------------------------------
    // Writes
    // ------------------------------------------------------------------------------------------

    private suspend fun <T> write(block: suspend () -> T): T {
        val r = db.withTransaction { block() }
        onLocalChange()
        return r
    }

    private fun now() = Instant.now().toString()

    // Transactions ------------------------------------------------------------------------------

    /** Inserts or updates. Returns the id. */
    suspend fun saveTransaction(t: Txn): String = write {
        val id = t.id.ifBlank { newId() }
        val old = txnDao.get(id)
        txnDao.upsert(
            listOf(
                TransactionEntity(
                    id = id, year = t.year, month = t.month, categoryId = t.categoryId, date = t.date,
                    item = t.item, amount = t.amount, note = t.note,
                    createdAt = old?.createdAt ?: t.createdAt ?: now(),
                    updatedAt = old?.updatedAt, deleted = false, dirty = true,
                    localVersion = (old?.localVersion ?: 0) + 1,
                ),
            ),
        )
        id
    }

    suspend fun deleteTransaction(id: String) = write {
        val old = txnDao.get(id) ?: return@write
        txnDao.upsert(listOf(old.copy(deleted = true, dirty = true, localVersion = old.localVersion + 1)))
    }

    suspend fun restoreTransaction(t: Txn) = saveTransaction(t)

    // Budgets ---------------------------------------------------------------------------------

    private suspend fun editBudget(key: MonthKey, categoryId: String, change: (BudgetEntity) -> BudgetEntity) {
        val old = budgetDao.get(key.year, key.month, categoryId)
        val base = old?.takeIf { !it.deleted } ?: BudgetEntity(key.year, key.month, categoryId, updatedAt = old?.updatedAt, localVersion = old?.localVersion ?: 0)
        budgetDao.upsert(listOf(change(base).copy(deleted = false, dirty = true, localVersion = base.localVersion + 1)))
    }

    suspend fun setExpected(key: MonthKey, categoryId: String, value: Double?) = write {
        editBudget(key, categoryId) { it.copy(expected = value) }
    }

    /** Manual actual, or an override for a ledger category. null clears it. */
    suspend fun setActual(key: MonthKey, categoryId: String, value: Double?) = write {
        editBudget(key, categoryId) { it.copy(actual = value) }
    }

    // Months ----------------------------------------------------------------------------------

    suspend fun setClosed(key: MonthKey, closed: Boolean) = write {
        val old = monthDao.get(key.year, key.month)
        val base = old ?: MonthEntity(key.year, key.month)
        monthDao.upsert(listOf(base.copy(closed = closed, deleted = false, dirty = true, localVersion = base.localVersion + 1)))
    }

    suspend fun setMonthNote(key: MonthKey, note: String?) = write {
        val old = monthDao.get(key.year, key.month) ?: return@write
        monthDao.upsert(listOf(old.copy(note = note?.takeIf { it.isNotBlank() }, dirty = true, localVersion = old.localVersion + 1)))
    }

    /** DOMAIN_RULES §6. Returns false if the month already exists. */
    suspend fun createMonth(key: MonthKey): Boolean = write {
        val months = monthDao.all().map { it.toDomain() }
        val plan = NewMonthPlanner.plan(
            key = key,
            existingMonths = months,
            categories = categoryDao.all().map { it.toDomain() },
            budgets = budgetDao.all().map { it.toDomain() },
            recurring = recurringDao.all().map { it.toDomain() },
            newId = newId,
        ) ?: return@write false
        val oldMonth = monthDao.get(key.year, key.month)
        monthDao.upsert(
            listOf(
                MonthEntity(
                    plan.month.year, plan.month.month, closed = false, note = null, updatedAt = oldMonth?.updatedAt,
                    dirty = true, localVersion = (oldMonth?.localVersion ?: 0) + 1,
                ),
            ),
        )
        val existingBudgets = budgetDao.byMonth(key.year, key.month).associateBy { it.categoryId }
        budgetDao.upsert(
            plan.budgets.map { b ->
                val old = existingBudgets[b.categoryId]
                BudgetEntity(
                    b.year, b.month, b.categoryId, b.expected, b.actual, updatedAt = old?.updatedAt,
                    dirty = true, localVersion = (old?.localVersion ?: 0) + 1,
                )
            },
        )
        val createdAt = now()
        txnDao.upsert(
            plan.transactions.map { t ->
                TransactionEntity(t.id, t.year, t.month, t.categoryId, t.date, t.item, t.amount, t.note, createdAt = createdAt, dirty = true, localVersion = 1)
            },
        )
        true
    }

    // Categories ------------------------------------------------------------------------------

    suspend fun saveCategory(c: Category): String = write {
        val id = c.id.ifBlank { newId() }
        val old = categoryDao.get(id)
        val sort = if (old == null && c.sortOrder == 0) (categoryDao.all().maxOfOrNull { it.sortOrder } ?: 0) + 1 else c.sortOrder
        categoryDao.upsert(
            listOf(
                CategoryEntity(
                    id = id, name = c.name.trim(), kind = c.kind.wire, tracking = c.tracking.wire,
                    matchMultiplier = c.matchMultiplier, sortOrder = sort, icon = c.icon, color = c.color,
                    archived = c.archived, updatedAt = old?.updatedAt, deleted = false, dirty = true,
                    localVersion = (old?.localVersion ?: 0) + 1,
                ),
            ),
        )
        id
    }

    suspend fun setCategoryArchived(id: String, archived: Boolean) = write {
        val old = categoryDao.get(id) ?: return@write
        categoryDao.upsert(listOf(old.copy(archived = archived, dirty = true, localVersion = old.localVersion + 1)))
    }

    /** Persists a new order: sort_order follows the list position (spaced by 1). */
    suspend fun reorderCategories(orderedIds: List<String>) = write {
        val all = categoryDao.all().associateBy { it.id }
        val changed = orderedIds.mapIndexedNotNull { index, id ->
            val c = all[id] ?: return@mapIndexedNotNull null
            if (c.sortOrder == index) null else c.copy(sortOrder = index, dirty = true, localVersion = c.localVersion + 1)
        }
        if (changed.isNotEmpty()) categoryDao.upsert(changed)
    }

    /** Deleting is only allowed while the category has no data (archive instead). */
    suspend fun categoryHasData(id: String): Boolean =
        txnDao.countForCategory(id) > 0 || budgetDao.countWithData(id) > 0 ||
            recurringDao.countForCategory(id) > 0 || accountDao.countLinked(id) > 0

    suspend fun deleteCategory(id: String): Boolean {
        if (categoryHasData(id)) return false
        write {
            val old = categoryDao.get(id) ?: return@write
            categoryDao.upsert(listOf(old.copy(deleted = true, dirty = true, localVersion = old.localVersion + 1)))
        }
        return true
    }

    // Recurring -------------------------------------------------------------------------------

    suspend fun saveRecurring(r: RecurringItem): String = write {
        val id = r.id.ifBlank { newId() }
        val old = recurringDao.get(id)
        recurringDao.upsert(
            listOf(
                RecurringItemEntity(
                    id, r.categoryId, r.item, r.amount, r.dayOfMonth?.coerceIn(1, 31), r.active, r.sortOrder,
                    updatedAt = old?.updatedAt, dirty = true, localVersion = (old?.localVersion ?: 0) + 1,
                ),
            ),
        )
        id
    }

    suspend fun deleteRecurring(id: String) = write {
        val old = recurringDao.get(id) ?: return@write
        recurringDao.upsert(listOf(old.copy(deleted = true, dirty = true, localVersion = old.localVersion + 1)))
    }

    // Accounts --------------------------------------------------------------------------------

    suspend fun saveAccount(a: Account): String = write {
        val id = a.id.ifBlank { newId() }
        val old = accountDao.get(id)
        accountDao.upsert(
            listOf(
                AccountEntity(
                    id, a.name.trim(), a.group.wire, a.liquid, a.balance, a.linkedCategoryId, a.baseAmount, a.sortOrder, a.archived,
                    updatedAt = old?.updatedAt, dirty = true, localVersion = (old?.localVersion ?: 0) + 1,
                ),
            ),
        )
        id
    }

    suspend fun setAccountBalance(id: String, balance: Double) = write {
        val old = accountDao.get(id) ?: return@write
        accountDao.upsert(listOf(old.copy(balance = balance, dirty = true, localVersion = old.localVersion + 1)))
    }

    suspend fun setAccountBase(id: String, base: Double) = write {
        val old = accountDao.get(id) ?: return@write
        accountDao.upsert(listOf(old.copy(baseAmount = base, dirty = true, localVersion = old.localVersion + 1)))
    }

    suspend fun deleteAccount(id: String) = write {
        val old = accountDao.get(id) ?: return@write
        accountDao.upsert(listOf(old.copy(deleted = true, dirty = true, localVersion = old.localVersion + 1)))
    }

    // Ledger ----------------------------------------------------------------------------------

    suspend fun saveLedgerEntry(e: LedgerEntry): String = write {
        val id = e.id.ifBlank { newId() }
        val old = ledgerDao.get(id)
        val sort = if (old == null && e.sortOrder == 0) (ledgerDao.all().maxOfOrNull { it.sortOrder } ?: -1) + 1 else e.sortOrder
        ledgerDao.upsert(
            listOf(
                LedgerEntryEntity(
                    id, e.name.trim(), e.amount, e.note, e.settled, sort,
                    updatedAt = old?.updatedAt, dirty = true, localVersion = (old?.localVersion ?: 0) + 1,
                ),
            ),
        )
        id
    }

    suspend fun setSettled(id: String, settled: Boolean) = write {
        val old = ledgerDao.get(id) ?: return@write
        ledgerDao.upsert(listOf(old.copy(settled = settled, dirty = true, localVersion = old.localVersion + 1)))
    }

    suspend fun deleteLedgerEntry(id: String) = write {
        val old = ledgerDao.get(id) ?: return@write
        ledgerDao.upsert(listOf(old.copy(deleted = true, dirty = true, localVersion = old.localVersion + 1)))
    }

    // Settings --------------------------------------------------------------------------------

    suspend fun saveSettings(s: BudgetSettings) = write {
        val old = settingsDao.get()
        settingsDao.upsert(
            listOf(
                SettingsEntity(
                    netIncome = s.netIncome, grossIncome = s.grossIncome, currency = s.currency,
                    updatedAt = old?.updatedAt, deleted = false, dirty = true, localVersion = (old?.localVersion ?: 0) + 1,
                ),
            ),
        )
    }

    // Backup ----------------------------------------------------------------------------------

    suspend fun exportBackup(): BackupFile = db.withTransaction {
        BackupFile(
            exportedAt = Instant.now().toString(),
            settings = BackupSettings.from(settingsDao.get().toDomain()),
            categories = categoryDao.all().map { BackupCategory.from(it.toDomain()) },
            months = monthDao.all().map { BackupMonth.from(it.toDomain()) },
            budgets = budgetDao.all().map { BackupBudget.from(it.toDomain()) },
            transactions = txnDao.all().sortedWith(compareBy({ it.year }, { it.month }, { it.date ?: "" }, { it.createdAt ?: "" }))
                .map { BackupTransaction.from(it.toDomain()) },
            recurringItems = recurringDao.all().map { BackupRecurringItem.from(it.toDomain()) },
            accounts = accountDao.all().map { BackupAccount.from(it.toDomain()) },
            ledgerEntries = ledgerDao.all().map { BackupLedgerEntry.from(it.toDomain()) },
            netWorthSnapshots = snapshotDao.all().map { BackupSnapshot.from(it.toDomain()) },
        )
    }

    suspend fun existingKeys(): ExistingKeys = db.withTransaction {
        ExistingKeys(
            categories = categoryDao.all().map { it.toDomain() },
            months = monthDao.all().map { it.year to it.month }.toSet(),
            budgets = budgetDao.all().map { Triple(it.year, it.month, it.categoryId) }.toSet(),
            transactionIds = txnDao.all().map { it.id }.toSet(),
            recurringIds = recurringDao.all().map { it.id }.toSet(),
            accountIds = accountDao.all().map { it.id }.toSet(),
            ledgerIds = ledgerDao.all().map { it.id }.toSet(),
            snapshotDays = snapshotDao.all().map { it.takenOn }.toSet(),
        )
    }

    /** Applies a merge plan (docs/SYNC.md import rules). Every written row becomes dirty and syncs. */
    suspend fun applyImport(plan: MergePlan) = write {
        val f = plan.file
        val createdAt = now()
        f.settings?.let { s ->
            val old = settingsDao.get()
            settingsDao.upsert(
                listOf(
                    SettingsEntity(
                        netIncome = s.netIncome, grossIncome = s.grossIncome, currency = s.currency,
                        updatedAt = old?.updatedAt, dirty = true, localVersion = (old?.localVersion ?: 0) + 1,
                    ),
                ),
            )
        }
        if (f.categories.isNotEmpty()) {
            val olds = categoryDao.byKeys(f.categories.map { it.id }).associateBy { it.id }
            categoryDao.upsert(
                f.categories.map { c ->
                    val o = olds[c.id]
                    CategoryEntity(
                        c.id, c.name, c.kind, c.tracking, c.matchMultiplier, c.sortOrder, c.icon, c.color, c.archived,
                        updatedAt = o?.updatedAt, dirty = true, localVersion = (o?.localVersion ?: 0) + 1,
                    )
                },
            )
        }
        monthDao.upsert(
            f.months.distinctBy { it.year to it.month }.map { m ->
                val o = monthDao.get(m.year, m.month)
                MonthEntity(m.year, m.month, m.closed, m.note, updatedAt = o?.updatedAt, dirty = true, localVersion = (o?.localVersion ?: 0) + 1)
            },
        )
        budgetDao.upsert(
            f.budgets.associateBy { Triple(it.year, it.month, it.categoryId) }.values.map { b ->
                val o = budgetDao.get(b.year, b.month, b.categoryId)
                BudgetEntity(b.year, b.month, b.categoryId, b.expected, b.actual, updatedAt = o?.updatedAt, dirty = true, localVersion = (o?.localVersion ?: 0) + 1)
            },
        )
        val txns = f.transactions.associateBy { it.id }.values.toList()
        val oldTxns = txns.map { it.id }.chunked(500).flatMap { txnDao.byKeys(it) }.associateBy { it.id }
        txnDao.upsert(
            txns.map { t ->
                val o = oldTxns[t.id]
                TransactionEntity(
                    t.id, t.year, t.month, t.categoryId, t.date, t.item, t.amount, t.note,
                    createdAt = o?.createdAt ?: createdAt, updatedAt = o?.updatedAt, dirty = true, localVersion = (o?.localVersion ?: 0) + 1,
                )
            },
        )
        val oldRec = recurringDao.byKeys(f.recurringItems.map { it.id }).associateBy { it.id }
        recurringDao.upsert(
            f.recurringItems.map { r ->
                val o = oldRec[r.id]
                RecurringItemEntity(r.id, r.categoryId, r.item, r.amount, r.dayOfMonth, r.active, r.sortOrder, updatedAt = o?.updatedAt, dirty = true, localVersion = (o?.localVersion ?: 0) + 1)
            },
        )
        val oldAcc = accountDao.byKeys(f.accounts.map { it.id }).associateBy { it.id }
        accountDao.upsert(
            f.accounts.map { a ->
                val o = oldAcc[a.id]
                AccountEntity(
                    a.id, a.name, a.accountGroup, a.liquid, a.balance, a.linkedCategoryId, a.baseAmount, a.sortOrder, a.archived,
                    updatedAt = o?.updatedAt, dirty = true, localVersion = (o?.localVersion ?: 0) + 1,
                )
            },
        )
        val oldLedger = ledgerDao.byKeys(f.ledgerEntries.map { it.id }).associateBy { it.id }
        ledgerDao.upsert(
            f.ledgerEntries.map { e ->
                val o = oldLedger[e.id]
                LedgerEntryEntity(e.id, e.name, e.amount, e.note, e.settled, e.sortOrder, updatedAt = o?.updatedAt, dirty = true, localVersion = (o?.localVersion ?: 0) + 1)
            },
        )
        importSnapshots(f.netWorthSnapshots)
        onImported()
    }

    /** Net-worth snapshots from the file, upserted with source "import" (the only client write to that table). */
    private suspend fun importSnapshots(rows: List<com.personal.budget.domain.model.BackupSnapshot>) {
        if (rows.isEmpty()) return
        val byDay = rows.associateBy { it.takenOn.take(10) }
        val olds = snapshotDao.byKeys(byDay.keys.toList()).associateBy { it.takenOn }
        snapshotDao.upsert(
            byDay.map { (day, r) ->
                val o = olds[day]
                NetWorthSnapshotEntity(
                    takenOn = day, netWorth = r.netWorth, superLiquid = r.superLiquid, reconciliations = r.reconciliations,
                    accountsJson = BackupSnapshotAccount.encodeList(r.accounts.map { it.toDomain() }), source = "import",
                    updatedAt = o?.updatedAt, dirty = true, localVersion = (o?.localVersion ?: 0) + 1,
                )
            },
        )
    }

    /** Wipes every local table (sign-out of an account / switching accounts). */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        // clearAllTables() must not run inside a transaction; it is atomic by itself.
        db.clearAllTables()
    }
}
