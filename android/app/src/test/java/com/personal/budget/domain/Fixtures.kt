package com.personal.budget.domain

import com.personal.budget.domain.model.BackupFile
import com.personal.budget.domain.usecase.BudgetBook
import java.io.File

/** Loads docs/fixtures JSON files (shared with the web app) from the test classpath. */
object Fixtures {
    fun text(name: String): String {
        val res = javaClass.classLoader?.getResource(name)
        if (res != null) return res.readText()
        val dir = System.getProperty("budget.fixtures.dir") ?: error("fixtures dir not configured")
        return File(dir, name).readText()
    }

    val backup: BackupFile by lazy { BackupFile.parse(text("sample-backup.json")) }

    fun book(file: BackupFile = backup): BudgetBook {
        val f = file.withoutDeleted()
        return BudgetBook(
            categories = f.categories.map { it.toDomain() },
            months = f.months.map { it.toDomain() },
            budgets = f.budgets.map { it.toDomain() },
            transactions = f.transactions.map { it.toDomain() },
        )
    }
}
