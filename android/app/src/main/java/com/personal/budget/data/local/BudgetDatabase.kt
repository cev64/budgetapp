package com.personal.budget.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * The local source of truth for the UI and widgets.
 *
 * Schema history (JSON for every version is committed under android/app/schemas):
 *   1  initial schema (2026-10), incl. net_worth_snapshots (added before the first release)
 *
 * To change the schema: bump [VERSION], add a Migration to [MIGRATIONS], commit the new
 * schema JSON produced by the build, and extend MigrationTest. Destructive migration is
 * deliberately NOT enabled: an unmigrated schema change crashes in development instead of
 * silently wiping user data.
 */
@Database(
    entities = [
        SettingsEntity::class,
        CategoryEntity::class,
        MonthEntity::class,
        BudgetEntity::class,
        TransactionEntity::class,
        RecurringItemEntity::class,
        AccountEntity::class,
        LedgerEntryEntity::class,
        NetWorthSnapshotEntity::class,
        SyncCursorEntity::class,
    ],
    version = BudgetDatabase.VERSION,
    exportSchema = true,
)
abstract class BudgetDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun categoryDao(): CategoryDao
    abstract fun monthDao(): MonthDao
    abstract fun budgetDao(): BudgetDao
    abstract fun transactionDao(): TransactionDao
    abstract fun recurringItemDao(): RecurringItemDao
    abstract fun accountDao(): AccountDao
    abstract fun ledgerEntryDao(): LedgerEntryDao
    abstract fun netWorthSnapshotDao(): NetWorthSnapshotDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        const val VERSION = 1
        const val NAME = "budget.db"

        /** Every migration ever shipped, oldest first (1→2, 2→3, …). */
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun build(context: Context): BudgetDatabase =
            Room.databaseBuilder(context.applicationContext, BudgetDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
