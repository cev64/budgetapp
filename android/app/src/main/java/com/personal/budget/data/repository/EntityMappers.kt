package com.personal.budget.data.repository

import com.personal.budget.data.local.AccountEntity
import com.personal.budget.data.local.BudgetEntity
import com.personal.budget.data.local.CategoryEntity
import com.personal.budget.data.local.LedgerEntryEntity
import com.personal.budget.data.local.MonthEntity
import com.personal.budget.data.local.NetWorthSnapshotEntity
import com.personal.budget.domain.model.BackupSnapshotAccount
import com.personal.budget.domain.model.NetWorthSnapshot
import com.personal.budget.data.local.RecurringItemEntity
import com.personal.budget.data.local.SettingsEntity
import com.personal.budget.data.local.TransactionEntity
import com.personal.budget.domain.model.Account
import com.personal.budget.domain.model.AccountGroup
import com.personal.budget.domain.model.Budget
import com.personal.budget.domain.model.BudgetSettings
import com.personal.budget.domain.model.Category
import com.personal.budget.domain.model.CategoryKind
import com.personal.budget.domain.model.LedgerEntry
import com.personal.budget.domain.model.MonthInfo
import com.personal.budget.domain.model.RecurringItem
import com.personal.budget.domain.model.Tracking
import com.personal.budget.domain.model.Txn

fun SettingsEntity?.toDomain(): BudgetSettings =
    if (this == null || deleted) BudgetSettings() else BudgetSettings(netIncome, grossIncome, currency)

fun CategoryEntity.toDomain() = Category(id, name, CategoryKind.from(kind), Tracking.from(tracking), matchMultiplier, sortOrder, icon, color, archived)
fun MonthEntity.toDomain() = MonthInfo(year, month, closed, note)
fun BudgetEntity.toDomain() = Budget(year, month, categoryId, expected, actual)
fun TransactionEntity.toDomain() = Txn(id, year, month, categoryId, date, item, amount, note, createdAt)
fun RecurringItemEntity.toDomain() = RecurringItem(id, categoryId, item, amount, dayOfMonth, active, sortOrder)
fun AccountEntity.toDomain() = Account(id, name, AccountGroup.from(accountGroup), liquid, balance, linkedCategoryId, baseAmount, sortOrder, archived)
fun LedgerEntryEntity.toDomain() = LedgerEntry(id, name, amount, note, settled, sortOrder)
fun NetWorthSnapshotEntity.toDomain() = NetWorthSnapshot(takenOn, netWorth, superLiquid, reconciliations, BackupSnapshotAccount.parseList(accountsJson), source)
