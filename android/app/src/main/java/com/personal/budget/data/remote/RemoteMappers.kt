package com.personal.budget.data.remote

import com.personal.budget.data.local.AccountEntity
import com.personal.budget.data.local.BudgetEntity
import com.personal.budget.data.local.CategoryEntity
import com.personal.budget.data.local.LedgerEntryEntity
import com.personal.budget.data.local.MonthEntity
import com.personal.budget.data.local.NetWorthSnapshotEntity
import com.personal.budget.data.local.RecurringItemEntity
import com.personal.budget.data.local.SettingsEntity
import com.personal.budget.data.local.TransactionEntity
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/*
 * Entity <-> PostgREST row mapping. Pushed rows always carry user_id and never updated_at
 * (server-owned, docs/SYNC.md rule 2). Every column is sent explicitly, including nulls,
 * so clearing a value on one device clears it everywhere. Pulled rows are decoded
 * leniently: numeric columns may arrive as numbers or numeric strings.
 */

private fun JsonObject.prim(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }

private fun JsonObject.str(key: String): String? = prim(key)?.contentOrNull
private fun JsonObject.reqStr(key: String): String = str(key) ?: throw MalformedResponseException("Missing $key")
private fun JsonObject.dbl(key: String): Double? = prim(key)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
private fun JsonObject.int(key: String): Int? = prim(key)?.let { it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toInt() }
private fun JsonObject.bool(key: String, default: Boolean = false): Boolean = prim(key)?.booleanOrNull ?: default

fun JsonObject.updatedAt(): String? = str("updated_at")
fun JsonObject.createdAt(): String? = str("created_at")

private fun JsonObjectBuilder.putNullable(key: String, value: Double?) = put(key, value?.let { JsonPrimitive(it) } ?: JsonNull)
private fun JsonObjectBuilder.putNullable(key: String, value: Int?) = put(key, value?.let { JsonPrimitive(it) } ?: JsonNull)
private fun JsonObjectBuilder.putNullable(key: String, value: String?) = put(key, value?.let { JsonPrimitive(it) } ?: JsonNull)

object RemoteMappers {
    // settings ---------------------------------------------------------------
    fun toJson(e: SettingsEntity, userId: String) = buildJsonObject {
        put("user_id", userId)
        put("net_income", e.netIncome)
        put("gross_income", e.grossIncome)
        put("currency", e.currency)
        put("deleted", e.deleted)
    }

    fun settings(o: JsonObject) = SettingsEntity(
        netIncome = o.dbl("net_income") ?: 68000.0,
        grossIncome = o.dbl("gross_income") ?: 85000.0,
        currency = o.str("currency") ?: "USD",
        updatedAt = o.updatedAt(),
        deleted = o.bool("deleted"),
    )

    // categories -------------------------------------------------------------
    fun toJson(e: CategoryEntity, userId: String) = buildJsonObject {
        put("id", e.id)
        put("user_id", userId)
        put("name", e.name)
        put("kind", e.kind)
        put("tracking", e.tracking)
        put("match_multiplier", e.matchMultiplier)
        put("sort_order", e.sortOrder)
        putNullable("icon", e.icon)
        putNullable("color", e.color)
        put("archived", e.archived)
        put("deleted", e.deleted)
    }

    fun category(o: JsonObject) = CategoryEntity(
        id = o.reqStr("id"),
        name = o.str("name") ?: "",
        kind = o.str("kind") ?: "expense",
        tracking = o.str("tracking") ?: "manual",
        matchMultiplier = o.dbl("match_multiplier") ?: 1.0,
        sortOrder = o.int("sort_order") ?: 0,
        icon = o.str("icon"),
        color = o.str("color"),
        archived = o.bool("archived"),
        updatedAt = o.updatedAt(),
        deleted = o.bool("deleted"),
    )

    // months -----------------------------------------------------------------
    fun toJson(e: MonthEntity, userId: String) = buildJsonObject {
        put("user_id", userId)
        put("year", e.year)
        put("month", e.month)
        put("closed", e.closed)
        putNullable("note", e.note)
        put("deleted", e.deleted)
    }

    fun month(o: JsonObject) = MonthEntity(
        year = o.int("year") ?: throw MalformedResponseException("Missing year"),
        month = o.int("month") ?: throw MalformedResponseException("Missing month"),
        closed = o.bool("closed"),
        note = o.str("note"),
        updatedAt = o.updatedAt(),
        deleted = o.bool("deleted"),
    )

    // budgets ----------------------------------------------------------------
    fun toJson(e: BudgetEntity, userId: String) = buildJsonObject {
        put("user_id", userId)
        put("year", e.year)
        put("month", e.month)
        put("category_id", e.categoryId)
        putNullable("expected", e.expected)
        putNullable("actual", e.actual)
        put("deleted", e.deleted)
    }

    fun budget(o: JsonObject) = BudgetEntity(
        year = o.int("year") ?: throw MalformedResponseException("Missing year"),
        month = o.int("month") ?: throw MalformedResponseException("Missing month"),
        categoryId = o.reqStr("category_id"),
        expected = o.dbl("expected"),
        actual = o.dbl("actual"),
        updatedAt = o.updatedAt(),
        deleted = o.bool("deleted"),
    )

    // transactions -----------------------------------------------------------
    fun toJson(e: TransactionEntity, userId: String, nowIso: String) = buildJsonObject {
        put("id", e.id)
        put("user_id", userId)
        put("year", e.year)
        put("month", e.month)
        put("category_id", e.categoryId)
        putNullable("date", e.date)
        put("item", e.item)
        put("amount", e.amount)
        putNullable("note", e.note)
        // created_at is NOT NULL server-side: always send a value.
        put("created_at", e.createdAt ?: nowIso)
        put("deleted", e.deleted)
    }

    fun transaction(o: JsonObject) = TransactionEntity(
        id = o.reqStr("id"),
        year = o.int("year") ?: throw MalformedResponseException("Missing year"),
        month = o.int("month") ?: throw MalformedResponseException("Missing month"),
        categoryId = o.reqStr("category_id"),
        date = o.str("date"),
        item = o.str("item") ?: "",
        amount = o.dbl("amount") ?: 0.0,
        note = o.str("note"),
        createdAt = o.createdAt(),
        updatedAt = o.updatedAt(),
        deleted = o.bool("deleted"),
    )

    // recurring_items --------------------------------------------------------
    fun toJson(e: RecurringItemEntity, userId: String) = buildJsonObject {
        put("id", e.id)
        put("user_id", userId)
        put("category_id", e.categoryId)
        put("item", e.item)
        put("amount", e.amount)
        putNullable("day_of_month", e.dayOfMonth)
        put("active", e.active)
        put("sort_order", e.sortOrder)
        put("deleted", e.deleted)
    }

    fun recurring(o: JsonObject) = RecurringItemEntity(
        id = o.reqStr("id"),
        categoryId = o.reqStr("category_id"),
        item = o.str("item") ?: "",
        amount = o.dbl("amount") ?: 0.0,
        dayOfMonth = o.int("day_of_month"),
        active = o.bool("active", default = true),
        sortOrder = o.int("sort_order") ?: 0,
        updatedAt = o.updatedAt(),
        deleted = o.bool("deleted"),
    )

    // accounts ---------------------------------------------------------------
    fun toJson(e: AccountEntity, userId: String) = buildJsonObject {
        put("id", e.id)
        put("user_id", userId)
        put("name", e.name)
        put("account_group", e.accountGroup)
        put("liquid", e.liquid)
        put("balance", e.balance)
        putNullable("linked_category_id", e.linkedCategoryId)
        put("base_amount", e.baseAmount)
        put("sort_order", e.sortOrder)
        put("archived", e.archived)
        put("deleted", e.deleted)
    }

    fun account(o: JsonObject) = AccountEntity(
        id = o.reqStr("id"),
        name = o.str("name") ?: "",
        accountGroup = o.str("account_group") ?: "cash",
        liquid = o.bool("liquid"),
        balance = o.dbl("balance") ?: 0.0,
        linkedCategoryId = o.str("linked_category_id"),
        baseAmount = o.dbl("base_amount") ?: 0.0,
        sortOrder = o.int("sort_order") ?: 0,
        archived = o.bool("archived"),
        updatedAt = o.updatedAt(),
        deleted = o.bool("deleted"),
    )

    // ledger_entries ---------------------------------------------------------
    fun toJson(e: LedgerEntryEntity, userId: String) = buildJsonObject {
        put("id", e.id)
        put("user_id", userId)
        put("name", e.name)
        put("amount", e.amount)
        putNullable("note", e.note)
        put("settled", e.settled)
        put("sort_order", e.sortOrder)
        put("deleted", e.deleted)
    }

    fun ledgerEntry(o: JsonObject) = LedgerEntryEntity(
        id = o.reqStr("id"),
        name = o.str("name") ?: "",
        amount = o.dbl("amount") ?: 0.0,
        note = o.str("note"),
        settled = o.bool("settled"),
        sortOrder = o.int("sort_order") ?: 0,
        updatedAt = o.updatedAt(),
        deleted = o.bool("deleted"),
    )

    // net_worth_snapshots (pull-only; pushed only after a backup import) ----------------------
    fun toJson(e: NetWorthSnapshotEntity, userId: String) = buildJsonObject {
        put("user_id", userId)
        put("taken_on", e.takenOn)
        put("net_worth", e.netWorth)
        put("super_liquid", e.superLiquid)
        put("reconciliations", e.reconciliations)
        put("accounts", runCatching { RemoteJson.parseToJsonElement(e.accountsJson) }.getOrElse { JsonArray(emptyList()) })
        put("source", e.source)
        put("deleted", e.deleted)
    }

    fun snapshot(o: JsonObject) = NetWorthSnapshotEntity(
        takenOn = o.reqStr("taken_on").take(10),
        netWorth = o.dbl("net_worth") ?: 0.0,
        superLiquid = o.dbl("super_liquid") ?: 0.0,
        reconciliations = o.dbl("reconciliations") ?: 0.0,
        accountsJson = when (val a = o["accounts"]) {
            is JsonArray -> a.toString()
            is JsonPrimitive -> a.contentOrNull ?: "[]"
            else -> "[]"
        },
        source = o.str("source") ?: "auto",
        updatedAt = o.updatedAt(),
        deleted = o.bool("deleted"),
    )
}
