package com.personal.budget.domain.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The backup / export JSON shared by the web and Android apps (docs/SYNC.md "Backup file format").
 * Unknown keys are ignored on read, so newer files still import and older files'
 * `meal_plans` / `meal_items` (meals were removed from the product) are silently skipped. `deleted` is accepted on read
 * (deleted rows are skipped) but never written.
 */
@Serializable
data class BackupFile(
    val app: String = "budget",
    val version: Int = 1,
    @SerialName("exported_at") val exportedAt: String? = null,
    val settings: BackupSettings? = null,
    val categories: List<BackupCategory> = emptyList(),
    val months: List<BackupMonth> = emptyList(),
    val budgets: List<BackupBudget> = emptyList(),
    val transactions: List<BackupTransaction> = emptyList(),
    @SerialName("recurring_items") val recurringItems: List<BackupRecurringItem> = emptyList(),
    val accounts: List<BackupAccount> = emptyList(),
    @SerialName("ledger_entries") val ledgerEntries: List<BackupLedgerEntry> = emptyList(),
    /** Optional (DOMAIN_RULES §5b). Imported with source = "import". */
    @SerialName("net_worth_snapshots") val netWorthSnapshots: List<BackupSnapshot> = emptyList(),
) {
    /** The same file without tombstoned rows. */
    fun withoutDeleted(): BackupFile = copy(
        categories = categories.filterNot { it.deleted },
        months = months.filterNot { it.deleted },
        budgets = budgets.filterNot { it.deleted },
        transactions = transactions.filterNot { it.deleted },
        recurringItems = recurringItems.filterNot { it.deleted },
        accounts = accounts.filterNot { it.deleted },
        ledgerEntries = ledgerEntries.filterNot { it.deleted },
        netWorthSnapshots = netWorthSnapshots.filterNot { it.deleted },
    )

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = true
            prettyPrint = true
            prettyPrintIndent = "  "
            coerceInputValues = true
        }

        fun parse(text: String): BackupFile = json.decodeFromString(serializer(), text)
        fun encode(file: BackupFile): String = json.encodeToString(serializer(), file)
    }
}

@Serializable
data class BackupSettings(
    @SerialName("net_income") val netIncome: Double = 68000.0,
    @SerialName("gross_income") val grossIncome: Double = 85000.0,
    val currency: String = "USD",
) {
    fun toDomain() = BudgetSettings(netIncome, grossIncome, currency)

    companion object {
        fun from(s: BudgetSettings) = BackupSettings(s.netIncome, s.grossIncome, s.currency)
    }
}

@Serializable
data class BackupCategory(
    val id: String,
    val name: String,
    val kind: String = "expense",
    val tracking: String = "manual",
    @SerialName("match_multiplier") val matchMultiplier: Double = 1.0,
    @SerialName("sort_order") val sortOrder: Int = 0,
    val icon: String? = null,
    val color: String? = null,
    val archived: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val deleted: Boolean = false,
) {
    fun toDomain() = Category(id, name, CategoryKind.from(kind), Tracking.from(tracking), matchMultiplier, sortOrder, icon, color, archived)

    companion object {
        fun from(c: Category) = BackupCategory(c.id, c.name, c.kind.wire, c.tracking.wire, c.matchMultiplier, c.sortOrder, c.icon, c.color, c.archived)
    }
}

@Serializable
data class BackupMonth(
    val year: Int,
    val month: Int,
    val closed: Boolean = false,
    val note: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val deleted: Boolean = false,
) {
    fun toDomain() = MonthInfo(year, month, closed, note)

    companion object {
        fun from(m: MonthInfo) = BackupMonth(m.year, m.month, m.closed, m.note)
    }
}

@Serializable
data class BackupBudget(
    val year: Int,
    val month: Int,
    @SerialName("category_id") val categoryId: String,
    val expected: Double? = null,
    val actual: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val deleted: Boolean = false,
) {
    fun toDomain() = Budget(year, month, categoryId, expected, actual)

    companion object {
        fun from(b: Budget) = BackupBudget(b.year, b.month, b.categoryId, b.expected, b.actual)
    }
}

@Serializable
data class BackupTransaction(
    val id: String,
    val year: Int,
    val month: Int,
    @SerialName("category_id") val categoryId: String,
    val date: String? = null,
    val item: String = "",
    val amount: Double = 0.0,
    val note: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val deleted: Boolean = false,
) {
    fun toDomain() = Txn(id, year, month, categoryId, date, item, amount, note)

    companion object {
        fun from(t: Txn) = BackupTransaction(t.id, t.year, t.month, t.categoryId, t.date, t.item, t.amount, t.note)
    }
}

@Serializable
data class BackupRecurringItem(
    val id: String,
    @SerialName("category_id") val categoryId: String,
    val item: String = "",
    val amount: Double = 0.0,
    @SerialName("day_of_month") val dayOfMonth: Int? = null,
    val active: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val deleted: Boolean = false,
) {
    fun toDomain() = RecurringItem(id, categoryId, item, amount, dayOfMonth, active, sortOrder)

    companion object {
        fun from(r: RecurringItem) = BackupRecurringItem(r.id, r.categoryId, r.item, r.amount, r.dayOfMonth, r.active, r.sortOrder)
    }
}

@Serializable
data class BackupAccount(
    val id: String,
    val name: String,
    @SerialName("account_group") val accountGroup: String = "cash",
    val liquid: Boolean = false,
    val balance: Double = 0.0,
    @SerialName("linked_category_id") val linkedCategoryId: String? = null,
    @SerialName("base_amount") val baseAmount: Double = 0.0,
    @SerialName("sort_order") val sortOrder: Int = 0,
    val archived: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val deleted: Boolean = false,
) {
    fun toDomain() = Account(id, name, AccountGroup.from(accountGroup), liquid, balance, linkedCategoryId, baseAmount, sortOrder, archived)

    companion object {
        fun from(a: Account) = BackupAccount(a.id, a.name, a.group.wire, a.liquid, a.balance, a.linkedCategoryId, a.baseAmount, a.sortOrder, a.archived)
    }
}

@Serializable
data class BackupLedgerEntry(
    val id: String,
    val name: String,
    val amount: Double = 0.0,
    val note: String? = null,
    val settled: Boolean = false,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val deleted: Boolean = false,
) {
    fun toDomain() = LedgerEntry(id, name, amount, note, settled, sortOrder)

    companion object {
        fun from(e: LedgerEntry) = BackupLedgerEntry(e.id, e.name, e.amount, e.note, e.settled, e.sortOrder)
    }
}


@Serializable
data class BackupSnapshotAccount(
    val id: String,
    val name: String = "",
    val group: String = "cash",
    val liquid: Boolean = false,
    val balance: Double = 0.0,
) {
    fun toDomain() = SnapshotAccount(id, name, AccountGroup.from(group), liquid, balance)

    companion object {
        fun from(a: SnapshotAccount) = BackupSnapshotAccount(a.id, a.name, a.group.wire, a.liquid, a.balance)
        private val listSerializer = kotlinx.serialization.builtins.ListSerializer(serializer())

        /** Parses the `accounts` jsonb array of a snapshot; malformed input gives an empty list. */
        fun parseList(json: String): List<SnapshotAccount> =
            runCatching { BackupFile.json.decodeFromString(listSerializer, json).map { it.toDomain() } }.getOrDefault(emptyList())

        fun encodeList(accounts: List<SnapshotAccount>): String =
            Json.encodeToString(listSerializer, accounts.map(::from))
    }
}

@Serializable
data class BackupSnapshot(
    @SerialName("taken_on") val takenOn: String,
    @SerialName("net_worth") val netWorth: Double = 0.0,
    @SerialName("super_liquid") val superLiquid: Double = 0.0,
    val reconciliations: Double = 0.0,
    val accounts: List<BackupSnapshotAccount> = emptyList(),
    val source: String = "auto",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val deleted: Boolean = false,
) {
    fun toDomain() = NetWorthSnapshot(takenOn.take(10), netWorth, superLiquid, reconciliations, accounts.map { it.toDomain() }, source)

    companion object {
        fun from(s: NetWorthSnapshot) = BackupSnapshot(s.takenOn, s.netWorth, s.superLiquid, s.reconciliations, s.accounts.map(BackupSnapshotAccount::from), s.source)
    }
}
