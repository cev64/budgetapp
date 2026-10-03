package com.personal.budget.domain.usecase

import com.personal.budget.domain.model.BackupFile
import com.personal.budget.domain.model.Category

/** What already exists locally, by the keys the merge rules use. */
data class ExistingKeys(
    val categories: List<Category>,
    val months: Set<Pair<Int, Int>>,
    val budgets: Set<Triple<Int, Int, String>>,
    val transactionIds: Set<String>,
    val recurringIds: Set<String>,
    val accountIds: Set<String>,
    val ledgerIds: Set<String>,
    val snapshotDays: Set<String> = emptySet(),
)

data class TableChange(val table: String, val added: Int, val updated: Int) {
    val total get() = added + updated
}

/** A validated, remapped import ready to apply, plus the preview counts. */
data class MergePlan(
    val file: BackupFile,
    /** file category id -> existing category id (matched by name, case-insensitive). */
    val categoryRemap: Map<String, String>,
    val changes: List<TableChange>,
    val overwritesSettings: Boolean,
) {
    /** "This will add 6 months, 158 transactions … and update 2 budgets." */
    fun summary(): String {
        val adds = changes.filter { it.added > 0 }.map { "${it.added} ${noun(it.table, it.added)}" }
        val updates = changes.filter { it.updated > 0 }.map { "${it.updated} ${noun(it.table, it.updated)}" }
        val parts = mutableListOf<String>()
        if (adds.isNotEmpty()) parts += "add " + adds.joinToString(", ")
        if (updates.isNotEmpty()) parts += "update " + updates.joinToString(", ")
        val matched = categoryRemap.size
        val tail = buildList {
            if (matched > 0) add("$matched ${if (matched == 1) "category matches" else "categories match"} an existing one by name")
            if (overwritesSettings) add("settings will be replaced by the file's")
        }
        val head = if (parts.isEmpty()) "Nothing new to import." else "This will " + parts.joinToString(" and ") + "."
        return if (tail.isEmpty()) head else head + " " + tail.joinToString("; ").replaceFirstChar { it.uppercase() } + "."
    }

    val isEmpty: Boolean get() = changes.all { it.total == 0 } && !overwritesSettings

    private fun noun(table: String, n: Int): String {
        val singular = when (table) {
            "categories" -> "category"
            "months" -> "month"
            "budgets" -> "budget"
            "transactions" -> "transaction"
            "recurring_items" -> "recurring item"
            "accounts" -> "account"
            "ledger_entries" -> "ledger entry"
            "net_worth_snapshots" -> "net worth snapshot"
            else -> table
        }
        if (n == 1) return singular
        return when {
            singular.endsWith("y") && !singular.endsWith("ey") -> singular.dropLast(1) + "ies"
            singular == "ledger entry" -> "ledger entries"
            else -> singular + "s"
        }
    }
}

class BackupFormatException(message: String) : Exception(message)

/** docs/SYNC.md "Import = merge, never wipe". Pure planning; the repository applies the plan. */
object BackupMerge {
    fun plan(raw: BackupFile, existing: ExistingKeys): MergePlan {
        if (raw.app != "budget") throw BackupFormatException("Not a Budget backup (app = \"${raw.app}\").")
        if (raw.version > 1) throw BackupFormatException("Backup version ${raw.version} is newer than this app understands. Update the app first.")
        val file = raw.withoutDeleted()

        val byName = existing.categories.associateBy { it.name.trim().lowercase() }
        val remap = LinkedHashMap<String, String>()
        val newCategories = file.categories.filter { c ->
            val match = byName[c.name.trim().lowercase()]
            if (match != null) {
                remap[c.id] = match.id
                false
            } else {
                true
            }
        }
        fun cat(id: String) = remap[id] ?: id

        val remapped = file.copy(
            categories = newCategories,
            budgets = file.budgets.map { it.copy(categoryId = cat(it.categoryId)) },
            transactions = file.transactions.map { it.copy(categoryId = cat(it.categoryId)) },
            recurringItems = file.recurringItems.map { it.copy(categoryId = cat(it.categoryId)) },
            accounts = file.accounts.map { a -> a.copy(linkedCategoryId = a.linkedCategoryId?.let(::cat)) },
        )

        val existingCategoryIds = existing.categories.map { it.id }.toSet()
        fun <T, K> change(table: String, rows: List<T>, key: (T) -> K, exists: (K) -> Boolean): TableChange {
            val keys = rows.map(key).distinct()
            val updated = keys.count(exists)
            return TableChange(table, keys.size - updated, updated)
        }

        val changes = listOf(
            change("categories", remapped.categories, { it.id }, { it in existingCategoryIds }),
            change("months", remapped.months, { it.year to it.month }, { it in existing.months }),
            change("budgets", remapped.budgets, { Triple(it.year, it.month, it.categoryId) }, { it in existing.budgets }),
            change("transactions", remapped.transactions, { it.id }, { it in existing.transactionIds }),
            change("recurring_items", remapped.recurringItems, { it.id }, { it in existing.recurringIds }),
            change("accounts", remapped.accounts, { it.id }, { it in existing.accountIds }),
            change("ledger_entries", remapped.ledgerEntries, { it.id }, { it in existing.ledgerIds }),
            change("net_worth_snapshots", remapped.netWorthSnapshots, { it.takenOn.take(10) }, { it in existing.snapshotDays }),
        )
        return MergePlan(remapped, remap, changes, overwritesSettings = file.settings != null)
    }
}
