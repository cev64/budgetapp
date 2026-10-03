package com.personal.budget.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/*
 * Every DAO has the same sync surface:
 *   observe()        non-deleted rows for the UI
 *   dirty()          rows to push (tombstones included)
 *   markClean(...)   clears dirty only if local_version is unchanged since the push started
 *   byKeys(...)      local rows for a pulled page, to skip dirty ones
 *   upsert(...)      local edits and pulled rows
 */

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun observe(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun get(): SettingsEntity?

    @Query("SELECT * FROM settings WHERE dirty = 1")
    suspend fun dirty(): List<SettingsEntity>

    @Query("UPDATE settings SET dirty = 0, updated_at = :updatedAt WHERE id = 1 AND local_version = :version")
    suspend fun markClean(version: Long, updatedAt: String?): Int

    @Upsert
    suspend fun upsert(rows: List<SettingsEntity>)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY sort_order, name")
    fun observe(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY sort_order, name")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE dirty = 1")
    suspend fun dirty(): List<CategoryEntity>

    @Query("UPDATE categories SET dirty = 0, updated_at = :updatedAt WHERE id = :id AND local_version = :version")
    suspend fun markClean(id: String, version: Long, updatedAt: String?): Int

    @Query("SELECT * FROM categories WHERE id IN (:ids)")
    suspend fun byKeys(ids: List<String>): List<CategoryEntity>

    @Upsert
    suspend fun upsert(rows: List<CategoryEntity>)
}

@Dao
interface MonthDao {
    @Query("SELECT * FROM months WHERE deleted = 0 ORDER BY year, month")
    fun observe(): Flow<List<MonthEntity>>

    @Query("SELECT * FROM months WHERE deleted = 0 ORDER BY year, month")
    suspend fun all(): List<MonthEntity>

    @Query("SELECT * FROM months WHERE year = :year AND month = :month")
    suspend fun get(year: Int, month: Int): MonthEntity?

    @Query("SELECT * FROM months WHERE dirty = 1")
    suspend fun dirty(): List<MonthEntity>

    @Query("UPDATE months SET dirty = 0, updated_at = :updatedAt WHERE year = :year AND month = :month AND local_version = :version")
    suspend fun markClean(year: Int, month: Int, version: Long, updatedAt: String?): Int

    @Query("SELECT * FROM months WHERE year = :year")
    suspend fun byYear(year: Int): List<MonthEntity>

    @Upsert
    suspend fun upsert(rows: List<MonthEntity>)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE deleted = 0")
    fun observe(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE deleted = 0")
    suspend fun all(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE year = :year AND month = :month AND category_id = :categoryId")
    suspend fun get(year: Int, month: Int, categoryId: String): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE dirty = 1")
    suspend fun dirty(): List<BudgetEntity>

    @Query(
        "UPDATE budgets SET dirty = 0, updated_at = :updatedAt " +
            "WHERE year = :year AND month = :month AND category_id = :categoryId AND local_version = :version",
    )
    suspend fun markClean(year: Int, month: Int, categoryId: String, version: Long, updatedAt: String?): Int

    @Query("SELECT * FROM budgets WHERE year = :year AND month = :month")
    suspend fun byMonth(year: Int, month: Int): List<BudgetEntity>

    @Query("SELECT COUNT(*) FROM budgets WHERE deleted = 0 AND category_id = :categoryId AND (expected IS NOT NULL OR actual IS NOT NULL)")
    suspend fun countWithData(categoryId: String): Int

    @Upsert
    suspend fun upsert(rows: List<BudgetEntity>)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE deleted = 0")
    fun observe(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE deleted = 0")
    suspend fun all(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE dirty = 1")
    suspend fun dirty(): List<TransactionEntity>

    @Query("UPDATE transactions SET dirty = 0, updated_at = :updatedAt WHERE id = :id AND local_version = :version")
    suspend fun markClean(id: String, version: Long, updatedAt: String?): Int

    @Query("SELECT * FROM transactions WHERE id IN (:ids)")
    suspend fun byKeys(ids: List<String>): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE deleted = 0 AND category_id = :categoryId")
    suspend fun countForCategory(categoryId: String): Int

    @Upsert
    suspend fun upsert(rows: List<TransactionEntity>)
}

@Dao
interface RecurringItemDao {
    @Query("SELECT * FROM recurring_items WHERE deleted = 0 ORDER BY sort_order, item")
    fun observe(): Flow<List<RecurringItemEntity>>

    @Query("SELECT * FROM recurring_items WHERE deleted = 0 ORDER BY sort_order, item")
    suspend fun all(): List<RecurringItemEntity>

    @Query("SELECT * FROM recurring_items WHERE id = :id")
    suspend fun get(id: String): RecurringItemEntity?

    @Query("SELECT * FROM recurring_items WHERE dirty = 1")
    suspend fun dirty(): List<RecurringItemEntity>

    @Query("UPDATE recurring_items SET dirty = 0, updated_at = :updatedAt WHERE id = :id AND local_version = :version")
    suspend fun markClean(id: String, version: Long, updatedAt: String?): Int

    @Query("SELECT * FROM recurring_items WHERE id IN (:ids)")
    suspend fun byKeys(ids: List<String>): List<RecurringItemEntity>

    @Query("SELECT COUNT(*) FROM recurring_items WHERE deleted = 0 AND category_id = :categoryId")
    suspend fun countForCategory(categoryId: String): Int

    @Upsert
    suspend fun upsert(rows: List<RecurringItemEntity>)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE deleted = 0 ORDER BY sort_order, name")
    fun observe(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE deleted = 0 ORDER BY sort_order, name")
    suspend fun all(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE dirty = 1")
    suspend fun dirty(): List<AccountEntity>

    @Query("UPDATE accounts SET dirty = 0, updated_at = :updatedAt WHERE id = :id AND local_version = :version")
    suspend fun markClean(id: String, version: Long, updatedAt: String?): Int

    @Query("SELECT * FROM accounts WHERE id IN (:ids)")
    suspend fun byKeys(ids: List<String>): List<AccountEntity>

    @Query("SELECT COUNT(*) FROM accounts WHERE deleted = 0 AND linked_category_id = :categoryId")
    suspend fun countLinked(categoryId: String): Int

    @Upsert
    suspend fun upsert(rows: List<AccountEntity>)
}

@Dao
interface LedgerEntryDao {
    @Query("SELECT * FROM ledger_entries WHERE deleted = 0 ORDER BY sort_order, name")
    fun observe(): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entries WHERE deleted = 0 ORDER BY sort_order, name")
    suspend fun all(): List<LedgerEntryEntity>

    @Query("SELECT * FROM ledger_entries WHERE id = :id")
    suspend fun get(id: String): LedgerEntryEntity?

    @Query("SELECT * FROM ledger_entries WHERE dirty = 1")
    suspend fun dirty(): List<LedgerEntryEntity>

    @Query("UPDATE ledger_entries SET dirty = 0, updated_at = :updatedAt WHERE id = :id AND local_version = :version")
    suspend fun markClean(id: String, version: Long, updatedAt: String?): Int

    @Query("SELECT * FROM ledger_entries WHERE id IN (:ids)")
    suspend fun byKeys(ids: List<String>): List<LedgerEntryEntity>

    @Upsert
    suspend fun upsert(rows: List<LedgerEntryEntity>)
}

@Dao
interface NetWorthSnapshotDao {
    @Query("SELECT * FROM net_worth_snapshots WHERE deleted = 0 ORDER BY taken_on")
    fun observe(): Flow<List<NetWorthSnapshotEntity>>

    @Query("SELECT * FROM net_worth_snapshots WHERE deleted = 0 ORDER BY taken_on")
    suspend fun all(): List<NetWorthSnapshotEntity>

    @Query("SELECT * FROM net_worth_snapshots WHERE dirty = 1")
    suspend fun dirty(): List<NetWorthSnapshotEntity>

    @Query("UPDATE net_worth_snapshots SET dirty = 0, updated_at = :updatedAt WHERE taken_on = :takenOn AND local_version = :version")
    suspend fun markClean(takenOn: String, version: Long, updatedAt: String?): Int

    @Query("SELECT * FROM net_worth_snapshots WHERE taken_on IN (:days)")
    suspend fun byKeys(days: List<String>): List<NetWorthSnapshotEntity>

    @Upsert
    suspend fun upsert(rows: List<NetWorthSnapshotEntity>)
}

@Dao
interface SyncStateDao {
    @Query("SELECT cursor FROM sync_cursors WHERE table_name = :table")
    suspend fun cursor(table: String): String?

    @Upsert
    suspend fun setCursor(row: SyncCursorEntity)

    /** Total rows waiting to be pushed, across all tables (for the top-bar status). */
    @Query(
        "SELECT (SELECT COUNT(*) FROM settings WHERE dirty = 1) + (SELECT COUNT(*) FROM categories WHERE dirty = 1) + " +
            "(SELECT COUNT(*) FROM months WHERE dirty = 1) + (SELECT COUNT(*) FROM budgets WHERE dirty = 1) + " +
            "(SELECT COUNT(*) FROM transactions WHERE dirty = 1) + (SELECT COUNT(*) FROM recurring_items WHERE dirty = 1) + " +
            "(SELECT COUNT(*) FROM accounts WHERE dirty = 1) + (SELECT COUNT(*) FROM ledger_entries WHERE dirty = 1) + " +
            "(SELECT COUNT(*) FROM net_worth_snapshots WHERE dirty = 1)",
    )
    fun observePendingCount(): Flow<Int>

    @Query(
        "SELECT (SELECT COUNT(*) FROM settings WHERE dirty = 1) + (SELECT COUNT(*) FROM categories WHERE dirty = 1) + " +
            "(SELECT COUNT(*) FROM months WHERE dirty = 1) + (SELECT COUNT(*) FROM budgets WHERE dirty = 1) + " +
            "(SELECT COUNT(*) FROM transactions WHERE dirty = 1) + (SELECT COUNT(*) FROM recurring_items WHERE dirty = 1) + " +
            "(SELECT COUNT(*) FROM accounts WHERE dirty = 1) + (SELECT COUNT(*) FROM ledger_entries WHERE dirty = 1) + " +
            "(SELECT COUNT(*) FROM net_worth_snapshots WHERE dirty = 1)",
    )
    suspend fun pendingCount(): Int
}
