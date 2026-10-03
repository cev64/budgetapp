package com.personal.budget.screenshots

import com.personal.budget.AppContainer
import com.personal.budget.domain.Fixtures
import com.personal.budget.domain.model.Account
import com.personal.budget.domain.model.AccountGroup
import com.personal.budget.domain.model.MonthKey
import com.personal.budget.domain.model.Txn
import com.personal.budget.domain.usecase.BackupMerge
import kotlinx.coroutines.flow.first

/** Synthetic data for screenshots: the shared fixture plus months up to today. No personal data. */
object DemoData {
    suspend fun seed(c: AppContainer) {
        val repo = c.repository
        repo.applyImport(BackupMerge.plan(Fixtures.backup, repo.existingKeys()))
        val today = MonthKey.now()
        var m = MonthKey(2026, 2)
        while (m <= today) {
            repo.createMonth(m)
            m = m.next()
        }
        val cats = repo.categories.first().associateBy { it.name }
        var k = MonthKey(2026, 2)
        var i = 0
        while (k < today) {
            repo.setActual(k, cats.getValue("Rent").id, 1250.0)
            repo.setActual(k, cats.getValue("Utilities").id, 88.0 + (i % 4) * 7.5)
            repo.setActual(k, cats.getValue("Roth").id, 500.0)
            repo.setActual(k, cats.getValue("401k").id, 350.0)
            repo.setActual(k, cats.getValue("HSA").id, if (i % 2 == 0) 100.0 else 0.0)
            repo.saveTransaction(Txn("", k.year, k.month, cats.getValue("Paychecks").id, "%04d-%02d-15".format(k.year, k.month), "Paycheck", 2100.0))
            repo.saveTransaction(Txn("", k.year, k.month, cats.getValue("Paychecks").id, "%04d-%02d-28".format(k.year, k.month), "Paycheck", 2100.0))
            repo.saveTransaction(Txn("", k.year, k.month, cats.getValue("Food").id, "%04d-%02d-06".format(k.year, k.month), "Groceries", 180.0 + i * 12))
            repo.saveTransaction(Txn("", k.year, k.month, cats.getValue("Fun").id, "%04d-%02d-12".format(k.year, k.month), "Concert", 60.0 + (i % 3) * 40))
            repo.setClosed(k, true)
            k = k.next()
            i++
        }
        val d = { day: Int -> "%04d-%02d-%02d".format(today.year, today.month, day) }
        repo.saveTransaction(Txn("", today.year, today.month, cats.getValue("Paychecks").id, d(1), "Paycheck", 2100.0))
        repo.saveTransaction(Txn("", today.year, today.month, cats.getValue("Food").id, d(1), "Trader Joe's", 86.42))
        repo.saveTransaction(Txn("", today.year, today.month, cats.getValue("Food").id, d(2), "Coffee", 5.75))
        repo.saveTransaction(Txn("", today.year, today.month, cats.getValue("Fun").id, d(2), "Movie tickets", 32.0))
        repo.saveTransaction(Txn("", today.year, today.month, cats.getValue("Fun").id, d(3), "Refund", -12.0))
        repo.saveTransaction(Txn("", today.year, today.month, cats.getValue("Food").id, d(3), "Dinner out", 64.3))
        repo.setActual(today, cats.getValue("Rent").id, 1250.0)
        repo.saveAccount(Account("", "Savings", AccountGroup.CASH, liquid = true, balance = 4200.0, sortOrder = 1))
        repo.saveAccount(Account("", "Car", AccountGroup.ASSET, balance = 8500.0, sortOrder = 9))
        // Net worth history as the server would have written it: ~5 months of daily snapshots.
        val start = java.time.LocalDate.now().minusDays(150)
        val rnd = java.util.Random(7)
        var nw = 26_000.0
        var liquid = 4_800.0
        val rows = (0..150).map { i ->
            nw += 45 + rnd.nextGaussian() * 160
            liquid += 8 + rnd.nextGaussian() * 90
            com.personal.budget.data.local.NetWorthSnapshotEntity(
                takenOn = start.plusDays(i.toLong()).toString(),
                netWorth = Math.round(nw * 100) / 100.0,
                superLiquid = Math.round(liquid * 100) / 100.0,
                reconciliations = 74.5,
                accountsJson = "[{\"id\":\"x\",\"name\":\"Checking\",\"group\":\"cash\",\"liquid\":true,\"balance\":${2500 + i * 3}}]",
            )
        }
        c.database.netWorthSnapshotDao().upsert(rows)
    }
}
