package com.personal.budget.domain

import com.personal.budget.domain.model.BackupFile
import com.personal.budget.domain.model.Category
import com.personal.budget.domain.model.CategoryKind
import com.personal.budget.domain.model.Tracking
import com.personal.budget.domain.usecase.BackupFormatException
import com.personal.budget.domain.usecase.BackupMerge
import com.personal.budget.domain.usecase.ExistingKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMergeTest {
    private val empty = ExistingKeys(emptyList(), emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), emptySet())

    @Test
    fun intoEmptyDatabase_addsEverything_skippingTombstones() {
        val plan = BackupMerge.plan(Fixtures.backup, empty)
        val byTable = plan.changes.associateBy { it.table }
        assertEquals(8, byTable.getValue("categories").added)
        assertEquals(3, byTable.getValue("months").added)
        assertEquals(24, byTable.getValue("budgets").added)
        // 12 rows, 1 tombstone -> 11
        assertEquals(11, byTable.getValue("transactions").added)
        assertEquals(6, byTable.getValue("accounts").added)
        assertTrue(plan.summary().startsWith("This will add 8 categories, 3 months, 24 budgets, 11 transactions"))
        assertTrue(plan.overwritesSettings)
    }

    @Test
    fun categoriesMatchedByName_caseInsensitive_areRemappedEverywhere() {
        val existingFood = Category("local-food", "FOOD", CategoryKind.EXPENSE, Tracking.LEDGER)
        val existing401k = Category("local-401k", "401K", CategoryKind.SAVINGS, Tracking.MANUAL, 2.0)
        val plan = BackupMerge.plan(Fixtures.backup, empty.copy(categories = listOf(existingFood, existing401k)))
        val fileFood = Fixtures.backup.categories.single { it.name == "Food" }.id
        val file401k = Fixtures.backup.categories.single { it.name == "401k" }.id
        assertEquals("local-food", plan.categoryRemap[fileFood])
        assertFalse(plan.file.categories.any { it.id == fileFood })
        assertEquals(6, plan.file.categories.size)
        assertFalse(plan.file.budgets.any { it.categoryId == fileFood })
        assertTrue(plan.file.budgets.any { it.categoryId == "local-food" })
        assertTrue(plan.file.transactions.any { it.categoryId == "local-food" })
        assertEquals("local-401k", plan.file.accounts.single { it.name == "401k" }.linkedCategoryId)
        assertFalse(plan.file.accounts.any { it.linkedCategoryId == file401k })
    }

    @Test
    fun existingRows_countAsUpdates() {
        val f = Fixtures.backup.withoutDeleted()
        val existing = empty.copy(
            months = setOf(2025 to 11),
            transactionIds = setOf(f.transactions.first().id),
        )
        val plan = BackupMerge.plan(Fixtures.backup, existing)
        val months = plan.changes.single { it.table == "months" }
        assertEquals(2, months.added)
        assertEquals(1, months.updated)
        assertEquals(1, plan.changes.single { it.table == "transactions" }.updated)
    }

    @Test
    fun netWorthSnapshots_optional_parsedAndCounted() {
        val text = """{"app":"budget","version":1,"net_worth_snapshots":[{"taken_on":"2026-10-02","net_worth":22560,"super_liquid":6748,
            "reconciliations":-5,"accounts":[{"id":"a","name":"SoFi","group":"cash","liquid":true,"balance":5780}],"source":"auto"}]}"""
        val f = BackupFile.parse(text)
        assertEquals(1, f.netWorthSnapshots.size)
        assertEquals(5780.0, f.netWorthSnapshots[0].accounts[0].balance, 0.0)
        val plan = BackupMerge.plan(f, empty.copy(snapshotDays = setOf("2026-10-02")))
        assertEquals(1, plan.changes.single { it.table == "net_worth_snapshots" }.updated)
        // Files without the key still parse (older exports, the shared fixture).
        assertTrue(Fixtures.backup.netWorthSnapshots.isEmpty())
    }

    @Test
    fun olderFiles_withMealKeys_importSilently() {
        val text = """{"app":"budget","version":1,"settings":{"net_income":1,"gross_income":2,"currency":"USD","days_per_month":30.5},
            "meal_plans":[{"id":"p","name":"Day","kind":"day"}],"meal_items":[{"id":"i","plan_id":"p","name":"Oats"}]}"""
        val plan = BackupMerge.plan(BackupFile.parse(text), empty)
        assertTrue(plan.changes.none { it.table.startsWith("meal") })
        assertEquals(1.0, plan.file.settings!!.netIncome, 0.0)
    }

    @Test(expected = BackupFormatException::class)
    fun rejectsOtherApps() {
        BackupMerge.plan(BackupFile(app = "other"), empty)
    }

    @Test
    fun roundTrip_encodeDecode() {
        val f = Fixtures.backup.withoutDeleted()
        val text = BackupFile.encode(f)
        assertFalse("deleted is never written", text.contains("\"deleted\""))
        assertTrue(text.contains("\"recurring_items\""))
        assertFalse(text.contains("meal_"))
        assertTrue(text.contains("\"icon\": null"))
        assertEquals(f, BackupFile.parse(text))
    }
}
