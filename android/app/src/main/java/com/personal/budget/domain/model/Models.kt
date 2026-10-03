package com.personal.budget.domain.model

/**
 * Pure domain models (no Android, no Room). They mirror the Supabase tables in
 * supabase/migrations (init schema) minus sync bookkeeping. Rows with deleted = true are
 * never turned into domain models: the data layer filters them out.
 */

enum class CategoryKind(val wire: String) {
    INCOME("income"), EXPENSE("expense"), SAVINGS("savings");

    companion object {
        fun from(wire: String): CategoryKind = entries.firstOrNull { it.wire == wire } ?: EXPENSE
    }
}

enum class Tracking(val wire: String) {
    LEDGER("ledger"), MANUAL("manual");

    companion object {
        fun from(wire: String): Tracking = entries.firstOrNull { it.wire == wire } ?: MANUAL
    }
}

enum class AccountGroup(val wire: String, val label: String) {
    CASH("cash", "Cash"), INVESTMENT("investment", "Investments"), ASSET("asset", "Assets"), DEBT("debt", "Debts");

    companion object {
        fun from(wire: String): AccountGroup = entries.firstOrNull { it.wire == wire } ?: CASH
    }
}

/** A budget month. Ordered chronologically. */
data class MonthKey(val year: Int, val month: Int) : Comparable<MonthKey> {
    init {
        require(month in 1..12) { "month must be 1..12, was $month" }
    }

    override fun compareTo(other: MonthKey): Int = compareValuesBy(this, other, { it.year }, { it.month })

    fun plus(months: Int): MonthKey {
        val index = year * 12 + (month - 1) + months
        return MonthKey(Math.floorDiv(index, 12), Math.floorMod(index, 12) + 1)
    }

    fun next() = plus(1)
    fun previous() = plus(-1)

    val lengthOfMonth: Int get() = java.time.YearMonth.of(year, month).lengthOfMonth()
    val name: String get() = MONTH_NAMES[month - 1]
    val shortName: String get() = MONTH_NAMES[month - 1].take(3)
    val label: String get() = "$name $year"
    val id: String get() = "%04d-%02d".format(year, month)

    companion object {
        val MONTH_NAMES = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        )

        fun now(clock: java.time.Clock = java.time.Clock.systemDefaultZone()): MonthKey {
            val d = java.time.LocalDate.now(clock)
            return MonthKey(d.year, d.monthValue)
        }
    }
}

data class Category(
    val id: String,
    val name: String,
    val kind: CategoryKind,
    val tracking: Tracking,
    val matchMultiplier: Double = 1.0,
    val sortOrder: Int = 0,
    val icon: String? = null,
    val color: String? = null,
    val archived: Boolean = false,
)

data class MonthInfo(
    val year: Int,
    val month: Int,
    val closed: Boolean = false,
    val note: String? = null,
) {
    val key: MonthKey get() = MonthKey(year, month)
}

data class Budget(
    val year: Int,
    val month: Int,
    val categoryId: String,
    val expected: Double? = null,
    val actual: Double? = null,
) {
    val key: MonthKey get() = MonthKey(year, month)
}

data class Txn(
    val id: String,
    val year: Int,
    val month: Int,
    val categoryId: String,
    val date: String? = null,
    val item: String = "",
    val amount: Double = 0.0,
    val note: String? = null,
    val createdAt: String? = null,
) {
    val key: MonthKey get() = MonthKey(year, month)
}

data class RecurringItem(
    val id: String,
    val categoryId: String,
    val item: String = "",
    val amount: Double = 0.0,
    val dayOfMonth: Int? = null,
    val active: Boolean = true,
    val sortOrder: Int = 0,
)

data class Account(
    val id: String,
    val name: String,
    val group: AccountGroup = AccountGroup.CASH,
    val liquid: Boolean = false,
    val balance: Double = 0.0,
    val linkedCategoryId: String? = null,
    val baseAmount: Double = 0.0,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
)

data class LedgerEntry(
    val id: String,
    val name: String,
    val amount: Double = 0.0,
    val note: String? = null,
    val settled: Boolean = false,
    val sortOrder: Int = 0,
)

data class BudgetSettings(
    val netIncome: Double = 68000.0,
    val grossIncome: Double = 85000.0,
    val currency: String = "USD",
)
