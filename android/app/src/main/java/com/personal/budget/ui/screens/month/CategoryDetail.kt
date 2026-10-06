package com.personal.budget.ui.screens.month

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.budget.domain.model.CategoryKind
import com.personal.budget.domain.model.Tracking
import com.personal.budget.domain.usecase.Money
import com.personal.budget.ui.MainViewModel
import com.personal.budget.ui.components.AnimatedList
import com.personal.budget.ui.components.AnimatedMoney
import com.personal.budget.ui.components.BudgetButton
import com.personal.budget.ui.components.BudgetCard
import com.personal.budget.ui.components.BudgetProgress
import com.personal.budget.ui.components.ButtonKind
import com.personal.budget.ui.components.GhostIconButton
import com.personal.budget.ui.components.LocalToast
import com.personal.budget.ui.components.Lucide
import com.personal.budget.ui.components.MicroLabel
import com.personal.budget.ui.components.MoneyField
import com.personal.budget.ui.components.Pill
import com.personal.budget.ui.screens.TrackingIcon
import com.personal.budget.ui.screens.categoryColor
import com.personal.budget.ui.screens.diffColor
import com.personal.budget.ui.screens.home.TxnRow
import com.personal.budget.ui.screens.kindLabel
import com.personal.budget.ui.theme.Budget

/**
 * Category detail: big Actual vs Expected, editable Expected, Actual (manual field, or ledger sum
 * with an optional override), and this month's transactions for the category.
 */
@Composable
fun CategoryDetail(ui: MonthUi, categoryId: String, vm: MonthViewModel, main: MainViewModel, showHeader: Boolean) {
    val c = Budget.colors
    val book = ui.snapshot.book
    val category = book.categoriesById[categoryId] ?: return
    val line = book.line(ui.key, category)
    val overrideOpen by vm.overrideOpen.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    val txns = ui.transactions.filter { it.categoryId == categoryId }
    val delete = com.personal.budget.ui.screens.home.rememberTxnDeleter(main)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (showHeader) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    MicroLabel("${kindLabel(category.kind)} · ${ui.key.label}")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(category.name, style = Budget.type.title, color = c.ink)
                        Spacer(Modifier.width(6.dp))
                        TrackingIcon(category)
                    }
                }
                GhostIconButton(Lucide.X, "Close detail", onClick = { vm.selectCategory(null) })
            }
        } else {
            // One quiet meta line instead of a row of badges.
            Text(
                listOfNotNull(
                    kindLabel(category.kind),
                    if (category.tracking == Tracking.LEDGER) "Ledger" else "Manual",
                    if (category.matchMultiplier != 1.0 && category.kind == CategoryKind.SAVINGS) "×${Money.formatInput(category.matchMultiplier)} match" else null,
                ).joinToString(" · "),
                style = Budget.type.secondary,
                color = c.ink3,
            )
        }

        // Big numbers ------------------------------------------------------------------------------
        Column {
            MicroLabel("Actual")
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                AnimatedMoney(line.actual, style = if (showHeader) Budget.type.heroSmall else Budget.type.hero, color = c.ink, blank = "—")
                if (line.overridden) {
                    Spacer(Modifier.width(8.dp))
                    Pill("manual", modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            Row {
                Text("of ${Money.format(line.expected)} expected · ", style = Budget.type.secondary, color = c.ink2)
                Text(Money.formatSigned(line.difference), style = Budget.type.secondary, color = diffColor(category.kind, line.difference, c))
            }
            Spacer(Modifier.height(14.dp))
            BudgetProgress(
                line.actual ?: 0.0,
                line.expected,
                height = 8.dp,
                color = categoryColor(category, book),
                overColor = if (category.kind == CategoryKind.EXPENSE) c.bad else c.good,
            )
        }

        if (category.kind == CategoryKind.EXPENSE && line.difference > 0.005) {
            com.personal.budget.ui.screens.home.OverBudgetNote(category.name, line.difference)
        }

        // Editable fields -------------------------------------------------------------------------
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MoneyField(
                value = book.budget(ui.key, categoryId)?.expected,
                onCommit = { vm.setExpected(ui.key, categoryId, it) },
                label = "Expected",
                modifier = Modifier.weight(1f),
            )
            if (category.tracking == Tracking.MANUAL) {
                MoneyField(
                    value = book.budget(ui.key, categoryId)?.actual,
                    onCommit = { vm.setActual(ui.key, categoryId, it) },
                    label = "Actual",
                    modifier = Modifier.weight(1f),
                )
            } else if (line.overridden || overrideOpen) {
                MoneyField(
                    value = book.budget(ui.key, categoryId)?.actual,
                    onCommit = { v ->
                        vm.setActual(ui.key, categoryId, v)
                        if (v == null) vm.setOverrideOpen(false)
                    },
                    label = "Actual (override)",
                    modifier = Modifier.weight(1f),
                )
            } else {
                Column(Modifier.weight(1f)) {
                    MicroLabel("Actual (ledger sum)")
                    Spacer(Modifier.height(6.dp))
                    Text(Money.format(line.ledgerSum), style = Budget.type.bodyStrong, color = c.ink, modifier = Modifier.padding(vertical = 11.dp))
                }
            }
        }
        if (category.tracking == Tracking.LEDGER) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (line.overridden) {
                    Text("Ledger sum is ${Money.format(line.ledgerSum)}.", style = Budget.type.small, color = c.ink3, modifier = Modifier.weight(1f))
                    BudgetButton("Clear override", {
                        vm.setActual(ui.key, categoryId, null)
                        vm.setOverrideOpen(false)
                        toast.show("Back to the ledger sum")
                    }, kind = ButtonKind.Ghost, icon = Lucide.RotateCcw)
                } else if (!overrideOpen) {
                    Spacer(Modifier.weight(1f))
                    BudgetButton("Override", { vm.setOverrideOpen(true) }, kind = ButtonKind.Ghost, icon = Lucide.Pencil)
                } else {
                    Spacer(Modifier.weight(1f))
                    BudgetButton("Cancel", { vm.setOverrideOpen(false) }, kind = ButtonKind.Ghost)
                }
            }
        }

        // Transactions ---------------------------------------------------------------------------
        BudgetCard(padding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                MicroLabel("Transactions · ${txns.size}", Modifier.weight(1f))
                MicroLabel(Money.format(line.ledgerSum))
            }
            if (txns.isEmpty()) {
                Text(
                    if (category.tracking == Tracking.LEDGER) "No transactions yet." else "Manual category: transactions are optional.",
                    style = Budget.type.secondary,
                    color = c.ink3,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            AnimatedList(txns, key = { it.id }) { t ->
                TxnRow(t, category, book, onClick = { main.openEdit(t) }, showCategory = false, onDelete = { delete(t) })
            }
            Spacer(Modifier.height(4.dp))
            BudgetButton(
                "Add to ${category.name}",
                { main.openAdd(categoryId = categoryId, month = ui.key) },
                kind = ButtonKind.Secondary,
                icon = Lucide.Plus,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
