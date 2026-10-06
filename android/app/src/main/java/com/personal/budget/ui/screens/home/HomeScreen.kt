package com.personal.budget.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.personal.budget.AppContainer
import com.personal.budget.data.repository.BudgetSnapshot
import com.personal.budget.domain.model.Category
import com.personal.budget.domain.model.CategoryKind
import com.personal.budget.domain.model.MonthKey
import com.personal.budget.domain.model.Txn
import com.personal.budget.domain.usecase.CategoryLine
import com.personal.budget.domain.usecase.HistoryRange
import com.personal.budget.domain.usecase.Money
import com.personal.budget.domain.usecase.NetWorthHistoryMath
import com.personal.budget.domain.usecase.MonthSummary
import com.personal.budget.domain.usecase.NetWorthMath
import com.personal.budget.domain.usecase.NetWorthSummary
import com.personal.budget.ui.MainViewModel
import com.personal.budget.ui.appViewModel
import com.personal.budget.ui.components.LargeTitle
import com.personal.budget.ui.components.SwipeToDelete
import com.personal.budget.ui.components.pastTitle
import com.personal.budget.ui.components.AnimatedList
import com.personal.budget.ui.components.AnimatedMoney
import com.personal.budget.ui.components.BudgetButton
import com.personal.budget.ui.components.BudgetCard
import com.personal.budget.ui.components.BudgetProgress
import com.personal.budget.ui.components.ButtonKind
import com.personal.budget.ui.components.CardHeader
import com.personal.budget.ui.components.Dot
import com.personal.budget.ui.components.EmptyState
import com.personal.budget.ui.components.GlassTopBar
import com.personal.budget.ui.components.Lucide
import com.personal.budget.ui.components.MicroLabel
import com.personal.budget.ui.components.Pill
import com.personal.budget.ui.components.ScreenFrame
import com.personal.budget.ui.components.StackedBar
import com.personal.budget.ui.components.tappable
import com.personal.budget.ui.screens.ContentMaxWidth
import com.personal.budget.ui.screens.LocalWindowLayout
import com.personal.budget.ui.screens.Tile
import com.personal.budget.ui.screens.WindowLayout
import com.personal.budget.ui.screens.categoryColor
import com.personal.budget.ui.screens.paletteFor
import com.personal.budget.ui.components.CategoryMark
import com.personal.budget.ui.screens.shortDate
import com.personal.budget.ui.theme.Budget
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecentRow(val txn: Txn, val category: Category?)

data class HomeUi(
    val month: MonthKey,
    val summary: MonthSummary?,
    val netWorth: NetWorthSummary,
    val budgets: List<CategoryLine>,
    val recent: List<RecentRow>,
    /** The current calendar month if it hasn't been started yet (banner). */
    val startMonth: MonthKey?,
    val snapshot: BudgetSnapshot,
)

class HomeViewModel(private val c: AppContainer, @Suppress("unused") handle: SavedStateHandle) : ViewModel() {
    val ui: StateFlow<HomeUi?> = c.snapshot.map { s -> s?.let(::build) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), c.snapshot.value?.let(::build))

    private fun build(s: BudgetSnapshot): HomeUi {
        val today = MonthKey.now()
        val book = s.book
        val key = if (book.exists(today)) today else book.latestMonth() ?: today
        val summary = if (book.exists(key)) book.monthSummary(key) else null
        val recent = s.transactions
            .sortedWith(compareByDescending<Txn> { it.year * 100 + it.month }.thenByDescending { it.date ?: "" }.thenByDescending { it.createdAt ?: "" })
            .take(8)
            .map { RecentRow(it, book.categoriesById[it.categoryId]) }
        return HomeUi(
            month = key,
            summary = summary,
            netWorth = NetWorthMath.compute(book, s.accounts, s.ledger),
            budgets = summary?.linesOf(CategoryKind.EXPENSE).orEmpty(),
            recent = recent,
            startMonth = if (!book.exists(today)) today else null,
            snapshot = s,
        )
    }

    fun startMonth(key: MonthKey) = viewModelScope.launch { c.repository.createMonth(key) }
}

@Composable
fun HomeScreen(main: MainViewModel, onOpenMonth: (MonthKey) -> Unit) {
    val vm = appViewModel { c, h -> HomeViewModel(c, h) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val layout = LocalWindowLayout.current
    val scope = rememberCoroutineScope()
    val toast = com.personal.budget.ui.components.LocalToast.current
    val title = ui?.month?.label ?: MonthKey.now().label
    ScreenFrame(topBar = { GlassTopBar(title = title, scrolled = scroll.pastTitle()) }) { padding ->
        val state = ui ?: return@ScreenFrame
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(padding)
                .padding(horizontal = layout.gutter, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LargeTitle("Budget", title)
                state.startMonth?.let { m ->
                    StartBanner(m) {
                        vm.startMonth(m)
                        main.selectMonth(m)
                        toast.show("${m.name} started. Budgets carried over and subscriptions added.")
                    }
                }
                if (state.summary == null) {
                    BudgetCard {
                        EmptyState(
                            "No months yet",
                            "Start a month to copy budgets forward and pre-fill subscriptions.",
                        )
                    }
                    return@Column
                }
                when (layout) {
                    WindowLayout.Compact -> {
                        Hero(state)
                        TilesGrid(state, columns = 2)
                        BudgetsCard(state, onOpen = { onOpenMonth(state.month) })
                        RecentCard(state, main)
                    }
                    WindowLayout.Medium -> Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Hero(state)
                            TilesGrid(state, columns = 2)
                            RecentCard(state, main)
                        }
                        Column(Modifier.weight(1f)) { BudgetsCard(state, onOpen = { onOpenMonth(state.month) }) }
                    }
                    WindowLayout.Expanded -> Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Hero(state)
                            TilesGrid(state, columns = 2)
                        }
                        Column(Modifier.weight(1f)) { BudgetsCard(state, onOpen = { onOpenMonth(state.month) }) }
                        Column(Modifier.weight(1f)) { RecentCard(state, main) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StartBanner(month: MonthKey, onStart: () -> Unit) {
    val c = Budget.colors
    BudgetCard(padding = PaddingValues(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${month.name} hasn't started yet", style = Budget.type.bodyStrong, color = c.ink)
            }
            Spacer(Modifier.width(10.dp))
            BudgetButton("Start ${month.name}", onStart, kind = ButtonKind.Primary)
        }
    }
}

@Composable
private fun Hero(state: HomeUi) {
    val c = Budget.colors
    val s = state.summary ?: return
    val income = maxOf(s.actual.income, s.actual.expenses + s.actual.contributions + maxOf(s.actual.leftover, 0.0), 1.0)
    BudgetCard(padding = PaddingValues(20.dp)) {
        MicroLabel("Leftover · ${state.month.name}")
        Spacer(Modifier.height(6.dp))
        AnimatedMoney(s.actual.leftover, style = Budget.type.hero, color = if (s.actual.leftover < 0) c.bad else c.ink)
        Spacer(Modifier.height(2.dp))
        Text("of ${Money.format(s.expected.leftover)} planned", style = Budget.type.secondary, color = c.ink2)
        Spacer(Modifier.height(18.dp))
        StackedBar(
            listOf(
                (s.actual.expenses / income).toFloat() to c.ink.copy(alpha = .8f),
                (s.actual.contributions / income).toFloat() to c.accent,
                (maxOf(s.actual.leftover, 0.0) / income).toFloat() to c.good,
            ),
        )
        Spacer(Modifier.height(10.dp))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Legend("Spent", Money.format(s.actual.expenses), c.ink.copy(alpha = .8f))
            Legend("Saved", Money.format(s.actual.contributions), c.accent)
            Legend("Left", Money.format(maxOf(s.actual.leftover, 0.0)), c.good)
        }
    }
}

@Composable
private fun Legend(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(color, size = 7.dp)
        Spacer(Modifier.width(5.dp))
        Text("$label ", style = Budget.type.small, color = Budget.colors.ink3)
        Text(value, style = Budget.type.small, color = Budget.colors.ink2)
    }
}

@Composable
private fun TilesGrid(state: HomeUi, columns: Int) {
    val c = Budget.colors
    val s = state.summary ?: return
    val tiles: List<@Composable (Modifier) -> Unit> = listOf(
        { m -> Tile("Income", m) { Amount(s.actual.income); Of(s.expected.income) } },
        { m -> Tile("Expenses", m) { Amount(s.actual.expenses, if (s.actual.expenses > s.expected.expenses && s.expected.expenses > 0) c.bad else c.ink); Of(s.expected.expenses) } },
        { m -> Tile("Saved", m) { Amount(s.actual.saved); Of(s.expected.saved) } },
        { m ->
            val history = state.snapshot.netWorthHistory
            Tile(
                "Net worth",
                m,
                labelTrailing = if (history.size >= 2) {
                    {
                        com.personal.budget.ui.components.Sparkline(
                            NetWorthHistoryMath.series(history, HistoryRange.M1),
                            Modifier.width(48.dp).height(16.dp),
                        )
                    }
                } else {
                    null
                },
            ) {
                val change = if (history.size >= 2) NetWorthHistoryMath.changeOver(history, HistoryRange.M1) else null
                Amount(state.netWorth.netWorth)
                if (change != null) {
                    val tone = when {
                        kotlin.math.abs(change.amount) < 0.005 -> c.ink3
                        change.amount > 0 -> c.good
                        else -> c.bad
                    }
                    Text(
                        Money.formatSigned(change.amount) + if (change.sinceFallback) " since ${com.personal.budget.ui.components.shortDay(change.base.date)}" else " · 30d",
                        style = Budget.type.small,
                        color = tone,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text("${Money.format(state.netWorth.superLiquid)} liquid", style = Budget.type.small, color = c.ink2, maxLines = 1)
                }
            }
        },
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { it(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Amount(v: Double, color: Color = Budget.colors.ink) {
    AnimatedMoney(v, style = Budget.type.number, color = color)
}

@Composable
private fun Of(v: Double) {
    Text("of ${Money.format(v)}", style = Budget.type.small, color = Budget.colors.ink2, maxLines = 1)
}

@Composable
private fun BudgetsCard(state: HomeUi, onOpen: () -> Unit) {
    val c = Budget.colors
    BudgetCard {
        CardHeader("Budgets") {
            Text(
                "Open month",
                style = Budget.type.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                color = c.ink2,
                modifier = Modifier.tappable(onClick = onOpen).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        if (state.budgets.isEmpty()) {
            Text("Set your first category to start this month’s budget.", style = Budget.type.secondary, color = c.ink3)
        }
        state.budgets.firstOrNull { (it.actual ?: 0.0) - it.expected > 0.005 }?.let { over ->
            OverBudgetNote(over.category.name, (over.actual ?: 0.0) - over.expected)
            Spacer(Modifier.height(4.dp))
        }
        state.budgets.forEachIndexed { i, line ->
            val actual = line.actual ?: 0.0
            val remaining = line.expected - actual
            Column(Modifier.padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryMark(paletteFor(line.category, state.snapshot.book), size = 14.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(line.category.name, style = Budget.type.body, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${Money.format(actual)} of ${Money.format(line.expected)}", style = Budget.type.small, color = c.ink3, maxLines = 1)
                    }
                    if (remaining < -0.005) {
                        Text("${Money.format(-remaining)} over", style = Budget.type.secondary.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = c.bad)
                    } else {
                        Text("${Money.format(remaining)} left", style = Budget.type.secondary, color = c.ink2)
                    }
                }
                Spacer(Modifier.height(8.dp))
                BudgetProgress(actual, line.expected, color = com.personal.budget.ui.screens.categoryColor(line.category, state.snapshot.book))
            }
        }
    }
}

/** Brand voice: "{Category} is {amount} over budget. Review your recent expenses." */
@Composable
fun OverBudgetNote(category: String, over: Double, modifier: Modifier = Modifier) {
    val c = Budget.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(com.personal.budget.ui.theme.Radius.control))
            .background(c.bad.copy(alpha = if (c.isDark) .12f else .07f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        com.personal.budget.ui.components.AppIcon(Lucide.CircleAlert, null, tint = c.bad, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text("$category is ${Money.format(over)} over budget. Review your recent expenses.", style = Budget.type.secondary, color = c.ink)
    }
}

@Composable
private fun RecentCard(state: HomeUi, main: MainViewModel) {
    val c = Budget.colors
    val delete = rememberTxnDeleter(main)
    BudgetCard(padding = PaddingValues(top = 16.dp, bottom = 6.dp, start = 4.dp, end = 4.dp)) {
        CardHeader("Recent", Modifier.padding(horizontal = 12.dp))
        if (state.recent.isEmpty()) {
            Text("No transactions yet. Tap + to add one.", style = Budget.type.secondary, color = c.ink3, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }
        AnimatedList(state.recent, key = { it.txn.id }) { row ->
            TxnRow(row.txn, row.category, state.snapshot.book, onClick = { main.openEdit(row.txn) }, onDelete = { delete(row.txn) })
        }
    }
}

/** Deletes a transaction (haptic + "Transaction deleted" toast with Undo, which restores the same row). */
@Composable
fun rememberTxnDeleter(main: MainViewModel): (Txn) -> Unit {
    val scope = rememberCoroutineScope()
    val toast = com.personal.budget.ui.components.LocalToast.current
    val view = androidx.compose.ui.platform.LocalView.current
    return androidx.compose.runtime.remember(main, toast, view) {
        { t: Txn ->
            scope.launch {
                main.deleteTransaction(t.id)
                com.personal.budget.utilities.Haptics.confirm(view)
                toast.show("Transaction deleted", "Undo") { scope.launch { main.restoreTransaction(t) } }
            }
            Unit
        }
    }
}

/**
 * One transaction (FLUID_GLASS v2 §8): item, one quiet meta line `■ Category · Oct 3`, amount on
 * the right (refunds green, with their sign). Rounded press fill, no dividers. With [onDelete] the
 * row swipes left to delete.
 */
@Composable
fun TxnRow(
    t: Txn,
    category: Category?,
    book: com.personal.budget.domain.usecase.BudgetBook?,
    onClick: () -> Unit,
    showCategory: Boolean = true,
    onDelete: (() -> Unit)? = null,
) {
    if (onDelete != null) {
        SwipeToDelete(resetKey = t.id, onDelete = onDelete) { TxnRowContent(t, category, book, onClick, showCategory) }
    } else {
        TxnRowContent(t, category, book, onClick, showCategory)
    }
}

@Composable
private fun TxnRowContent(t: Txn, category: Category?, book: com.personal.budget.domain.usecase.BudgetBook?, onClick: () -> Unit, showCategory: Boolean) {
    val c = Budget.colors
    Row(
        Modifier
            .fillMaxWidth()
            .tappable(shape = androidx.compose.foundation.shape.RoundedCornerShape(com.personal.budget.ui.theme.Radius.row), pressedFill = c.fill, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(t.item.ifBlank { "(no item)" }, style = Budget.type.body, color = if (t.item.isBlank()) c.ink3 else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(1.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showCategory && category != null) {
                    CategoryMark(paletteFor(category, book), size = 11.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(category.name, style = Budget.type.small, color = c.ink2, maxLines = 1)
                    Text(" · ", style = Budget.type.small, color = c.ink3)
                }
                Text(
                    shortDate(t.date).ifBlank { MonthKey(t.year, t.month).shortName + " · no date" },
                    style = Budget.type.small,
                    color = c.ink3,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            Money.format(t.amount),
            style = Budget.type.bodyStrong,
            color = if (t.amount < 0) c.good else c.ink,
        )
    }
}
