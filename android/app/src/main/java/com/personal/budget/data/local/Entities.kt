package com.personal.budget.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Room entities. Each one mirrors its Supabase table (column names match) plus local sync
 * bookkeeping:
 *   dirty          the row has local changes that have not reached the server yet
 *   local_version  bumped on every local edit; a push only clears `dirty` if the version is
 *                  unchanged since the push started, so an edit made mid-push is never lost
 *   updated_at     the server's timestamp from the last push/pull (null = never synced)
 * Deleting is a tombstone (deleted = 1) that is pushed like any other edit.
 *
 * DB version 1. Any change here needs a Migration in BudgetDatabase plus a committed schema
 * JSON (android/app/schemas). Never use destructive migrations.
 */

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "net_income") val netIncome: Double = 68000.0,
    @ColumnInfo(name = "gross_income") val grossIncome: Double = 85000.0,
    val currency: String = "USD",
    @ColumnInfo(name = "updated_at") val updatedAt: String? = null,
    val deleted: Boolean = false,
    val dirty: Boolean = false,
    @ColumnInfo(name = "local_version") val localVersion: Long = 0,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val tracking: String,
    @ColumnInfo(name = "match_multiplier") val matchMultiplier: Double = 1.0,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    val icon: String? = null,
    val color: String? = null,
    val archived: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: String? = null,
    val deleted: Boolean = false,
    val dirty: Boolean = false,
    @ColumnInfo(name = "local_version") val localVersion: Long = 0,
)

@Entity(tableName = "months", primaryKeys = ["year", "month"])
data class MonthEntity(
    val year: Int,
    val month: Int,
    val closed: Boolean = false,
    val note: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: String? = null,
    val deleted: Boolean = false,
    val dirty: Boolean = false,
    @ColumnInfo(name = "local_version") val localVersion: Long = 0,
)

@Entity(tableName = "budgets", primaryKeys = ["year", "month", "category_id"])
data class BudgetEntity(
    val year: Int,
    val month: Int,
    @ColumnInfo(name = "category_id") val categoryId: String,
    val expected: Double? = null,
    val actual: Double? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: String? = null,
    val deleted: Boolean = false,
    val dirty: Boolean = false,
    @ColumnInfo(name = "local_version") val localVersion: Long = 0,
)

@Entity(tableName = "transactions", indices = [Index(value = ["year", "month"])])
data class TransactionEntity(
    @PrimaryKey val id: String,
    val year: Int,
    val month: Int,
    @ColumnInfo(name = "category_id") val categoryId: String,
    val date: String? = null,
    val item: String = "",
    val amount: Double = 0.0,
    val note: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: String? = null,
    val deleted: Boolean = false,
    val dirty: Boolean = false,
    @ColumnInfo(name = "local_version") val localVersion: Long = 0,
)

@Entity(tableName = "recurring_items")
data class RecurringItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    val item: String = "",
    val amount: Double = 0.0,
    @ColumnInfo(name = "day_of_month") val dayOfMonth: Int? = null,
    val active: Boolean = true,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: String? = null,
    val deleted: Boolean = false,
    val dirty: Boolean = false,
    @ColumnInfo(name = "local_version") val localVersion: Long = 0,
)

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "account_group") val accountGroup: String = "cash",
    val liquid: Boolean = false,
    val balance: Double = 0.0,
    @ColumnInfo(name = "linked_category_id") val linkedCategoryId: String? = null,
    @ColumnInfo(name = "base_amount") val baseAmount: Double = 0.0,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    val archived: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: String? = null,
    val deleted: Boolean = false,
    val dirty: Boolean = false,
    @ColumnInfo(name = "local_version") val localVersion: Long = 0,
)

@Entity(tableName = "ledger_entries")
data class LedgerEntryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val amount: Double = 0.0,
    val note: String? = null,
    val settled: Boolean = false,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: String? = null,
    val deleted: Boolean = false,
    val dirty: Boolean = false,
    @ColumnInfo(name = "local_version") val localVersion: Long = 0,
)

/**
 * Net worth history (DOMAIN_RULES §5b). Pull-only: the server computes and writes rows (nightly
 * job / take_net_worth_snapshot RPC). The only local writes are backup imports (source "import"),
 * which are pushed like any dirty row. One row per America/New_York day; the DB holds one user.
 */
@Entity(tableName = "net_worth_snapshots")
data class NetWorthSnapshotEntity(
    /** yyyy-MM-dd */
    @PrimaryKey @ColumnInfo(name = "taken_on") val takenOn: String,
    @ColumnInfo(name = "net_worth") val netWorth: Double,
    @ColumnInfo(name = "super_liquid") val superLiquid: Double,
    val reconciliations: Double,
    /** JSON array [{id,name,group,liquid,balance}], stored verbatim. */
    @ColumnInfo(name = "accounts_json") val accountsJson: String = "[]",
    val source: String = "auto",
    @ColumnInfo(name = "updated_at") val updatedAt: String? = null,
    val deleted: Boolean = false,
    val dirty: Boolean = false,
    @ColumnInfo(name = "local_version") val localVersion: Long = 0,
)

/** Pull cursor per remote table: the max server updated_at seen. */
@Entity(tableName = "sync_cursors")
data class SyncCursorEntity(
    @PrimaryKey @ColumnInfo(name = "table_name") val tableName: String,
    val cursor: String,
)
