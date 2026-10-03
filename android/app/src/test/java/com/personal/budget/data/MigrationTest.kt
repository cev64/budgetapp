package com.personal.budget.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.personal.budget.data.local.BudgetDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room migration scaffold (MigrationTestHelper reads android/app/schemas/<version>.json).
 *
 * When the schema moves to v2:
 *  1. bump BudgetDatabase.VERSION, add MIGRATION_1_2 to BudgetDatabase.MIGRATIONS;
 *  2. build once so app/schemas/…/2.json is generated, and commit it;
 *  3. copy [v1_survivesOpeningWithAllMigrations] into `migrate1To2()` using
 *     `helper.runMigrationsAndValidate(NAME, 2, true, MIGRATION_1_2)` and assert the rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    private val name = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BudgetDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun v1_survivesOpeningWithAllMigrations() {
        helper.createDatabase(name, 1).use { db ->
            db.execSQL(
                "INSERT INTO transactions (id, year, month, category_id, date, item, amount, note, created_at, updated_at, deleted, dirty, local_version) " +
                    "VALUES ('t1', 2026, 10, 'c1', '2026-10-03', 'Coffee', 5.75, NULL, NULL, NULL, 0, 1, 3)",
            )
            db.execSQL(
                "INSERT INTO net_worth_snapshots (taken_on, net_worth, super_liquid, reconciliations, accounts_json, source, updated_at, deleted, dirty, local_version) " +
                    "VALUES ('2026-10-02', 22560, 6748, -5, '[]', 'auto', NULL, 0, 0, 0)",
            )
        }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val room = Room.databaseBuilder(context, BudgetDatabase::class.java, name)
            .addMigrations(*BudgetDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            val t = room.transactionDao().get("t1")!!
            assertEquals(5.75, t.amount, 0.0)
            assertEquals(true, t.dirty)
            assertEquals(3L, t.localVersion)
            assertEquals(22560.0, room.netWorthSnapshotDao().all().single().netWorth, 0.0)
        }
        room.close()
    }
}
