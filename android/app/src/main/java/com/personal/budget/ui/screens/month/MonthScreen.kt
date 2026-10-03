package com.personal.budget.ui.screens.month

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.budget.domain.model.CategoryKind
import com.personal.budget.domain.model.MonthKey
import com.personal.budget.domain.usecase.CategoryLine
import com.personal.budget.domain.usecase.Money
import com.personal.budget.ui.MainViewModel
import com.personal.budget.ui.appViewModel
import com.personal.budget.ui.components.isScrolled
import com.personal.budget.ui.components.AnimatedList
import com.personal.budget.ui.components.AnimatedMoney
import com.personal.budget.ui.components.AppIcon
import com.personal.budget.ui.components.BudgetButton
import com.personal.budget.ui.components.BudgetCard
import com.personal.budget.ui.components.BudgetProgress
import com.personal.budget.ui.components.BudgetSwitch
import com.personal.budget.ui.components.ButtonKind
import com.personal.budget.ui.components.EmptyState
import com.personal.budget.ui.components.GhostIconButton
import com.personal.budget.ui.components.GlassTopBar
import com.personal.budget.ui.components.Hairline
import com.personal.budget.ui.components.LocalToast
import com.personal.budget.ui.components.Lucide
import com.personal.budget.ui.components.MicroLabel
import com.personal.budget.ui.components.Pick
import com.personal.budget.ui.components.Pill
import com.personal.budget.ui.components.ScreenFrame
import com.personal.budget.ui.components.SegmentedControl
import com.personal.budget.ui.components.tappable
import com.personal.budget.ui.screens.LocalWindowLayout
import com.personal.budget.ui.screens.MonthPickerDialog
import com.personal.budget.ui.screens.TrackingIcon
import com.personal.budget.ui.screens.categoryColor
import com.personal.budget.ui.screens.paletteFor
import com.personal.budget.ui.screens.diffColor
import com.personal.budget.ui.screens.home.TxnRow
import com.personal.budget.ui.screens.kindLabel
import com.personal.budget.ui.theme.Budget
import com.personal.budget.ui.theme.LocalReduceMotion
import com.personal.budget.ui.theme.Motion
import com.personal.budget.utilities.Haptics

/**
 * Month: Budget / Transactions tabs; tapping a category opens its detail. Compact pushes the
 * detail full-screen (back returns); medium/expanded show list + detail side by side. The
 * selected month, tab and category live in ViewModels, so unfolding while a category is open
 * simply reveals the list next to it, and folding collapses back to the detail.
 */
@Composable
fun MonthScreen(main: MainViewModel) {
    val vm = appViewModel { c, h -> MonthViewModel(c, h) }
    val snapshot by main.snapshot.collectAsStateWithLifecycle()
    val key by main.selectedMonth.collectAsStateWithLifecycle()
    val tabName by vm.tab.collectAsStateWithLifecycle()
    val selectedCategory by vm.selectedCategory.collectAsStateWithLifecycle()
    val layout = LocalWindowLayout.current
    val s = snapshot ?: return
    val ui = remember(s, key) { buildMonthUi(s, key) }
    val tab = MonthTab.valueOf(tabName)
    var picker by rememberSaveable { mutableStateOf(false) }
    val listScroll = rememberScrollState()
    val category = selectedCategory?.let { s.book.categoriesById[it] }

    if (picker) {
        MonthPickerDialog(
            selected = key,
            book = s.book,
            onPick = { main.selectMonth(it); picker = false },
            onCreate = { m -> vm.createMonth(m); main.selectMonth(m); picker = false },
            onDismiss = { picker = false },
        )
    }

    val topBar: @Composable (Boolean) -> Unit = { scrolled ->
        GlassTopBar(
            micro = "Month",
            title = key.label,
            scrolled = scrolled,
            titleContent = {
                Text(
                    key.label.uppercase(),
                    style = Budget.type.screenTitle,
                    color = Budget.colors.ink,
                    maxLines = 1,
                    modifier = Modifier.tappable(onClick = { picker = true }, label = "Choose month"),
                )
            },
            actions = {
                // Compact puts the month arrows in the content (the 40sp title needs the room).
                if (!layout.isCompact) {
                    GhostIconButton(Lucide.ChevronLeft, "Previous month", onClick = { main.selectMonth(key.previous()) })
                    GhostIconButton(Lucide.ChevronRight, "Next month", onClick = { main.selectMonth(key.next()) })
                }
            },
        )
    }

    if (layout.isCompact) {
        if (category != null) {
            BackHandler { vm.selectCategory(null) }
        }
        val reduce = LocalReduceMotion.current
        AnimatedContent(
            targetState = category?.id,
            transitionSpec = {
                if (reduce) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                else (fadeIn(tween(Motion.APPEAR, easing = Motion.Ease)) + slideInHorizontally(tween(Motion.APPEAR, easing = Motion.Ease)) { if (targetState != null) it / 6 else -it / 6 })
                    .togetherWith(fadeOut(tween(180)))
            },
            label = "monthDetail",
        ) { catId ->
            if (catId != null) {
                val detailScroll = rememberScrollState()
                ScreenFrame(topBar = {
                    GlassTopBar(
                        micro = key.label,
                        title = s.book.categoriesById[catId]?.name ?: "",
                        scrolled = detailScroll.isScrolled(),
                        navigation = { GhostIconButton(Lucide.ChevronLeft, "Back to month", onClick = { vm.selectCategory(null) }) },
                    )
                }) { padding ->
                    Column(Modifier.fillMaxSize().verticalScroll(detailScroll).padding(padding).padding(horizontal = 16.dp, vertical = 14.dp)) {
                        CategoryDetail(ui, catId, vm, main, showHeader = false)
                    }
                }
            } else {
                ScreenFrame(topBar = { topBar(listScroll.isScrolled()) }) { padding ->
                    Column(Modifier.fillMaxSize().verticalScroll(listScroll).padding(padding).padding(horizontal = 16.dp, vertical = 14.dp)) {
                        MonthList(ui, tab, vm, main, selectedId = null)
                    }
                }
            }
        }
    } else {
        val detailScroll = rememberScrollState()
        ScreenFrame(topBar = { topBar(listScroll.isScrolled() || detailScroll.isScrolled()) }) { padding ->
            Row(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).padding(horizontal = layout.gutter)) {
                Column(
                    Modifier
                        .weight(if (layout == com.personal.budget.ui.screens.WindowLayout.Expanded) 1.25f else 1.1f)
                        .fillMaxHeight()
                        .verticalScroll(listScroll)
                        .padding(top = 14.dp, bottom = padding.calculateBottomPadding()),
                ) {
                    MonthList(ui, tab, vm, main, selectedId = category?.id)
                }
                Spacer(Modifier.width(14.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(detailScroll)
                        .padding(top = 14.dp, bottom = padding.calculateBottomPadding()),
                ) {
                    if (category != null) {
                        BudgetCard(padding = PaddingValues(16.dp)) {
                            CategoryDetail(ui, category.id, vm, main, showHeader = true)
                        }
                    } else {
                        BudgetCard {
                            EmptyState("Select a category", "Its expected and actual amounts and the ${ui.key.name} ledger appear here.")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthList(ui: MonthUi, tab: MonthTab, vm: MonthViewModel, main: MainViewModel, selectedId: String?) {
    val c = Budget.colors
    val view = LocalView.current
    val toast = LocalToast.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ui.suggestion?.let { next ->
            BudgetCard(padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Ready for ${next.name}?", style = Budget.type.bodyStrong, color = c.ink)
                        Text("Copies budgets forward and adds subscriptions.", style = Budget.type.small, color = c.ink2)
                    }
                    BudgetButton("Start ${next.name}", {
                        vm.createMonth(next)
                        main.selectMonth(next)
                        toast.show("Started ${next.label}")
                    }, kind = ButtonKind.Primary)
                }
            }
        }
        if (!ui.exists) {
            if (LocalWindowLayout.current.isCompact) {
                Row {
                    GhostIconButton(Lucide.ChevronLeft, "Previous month", onClick = { main.selectMonth(ui.key.previous()) })
                    GhostIconButton(Lucide.ChevronRight, "Next month", onClick = { main.selectMonth(ui.key.next()) })
                }
            }
            BudgetCard {
                EmptyState(
                    "${ui.key.label} hasn't been started",
                    "Starting it copies each category's budget from the latest earlier month and adds active recurring items.",
                ) {
                    BudgetButton("Start ${ui.key.name}", { vm.createMonth(ui.key); toast.show("Started ${ui.key.label}") }, kind = ButtonKind.Primary, icon = Lucide.Plus)
                }
            }
            return@Column
        }
        // Closed toggle + tabs (stacked on compact so nothing shrinks)
        val compactLayout = LocalWindowLayout.current.isCompact
        val closedToggle: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(if (ui.summary.closed) Lucide.Lock else Lucide.LockOpen, null, tint = if (ui.summary.closed) c.accentInk else c.ink3, size = 18.dp)
                Spacer(Modifier.width(6.dp))
                Text("Closed", style = Budget.type.body, color = c.ink2)
                Spacer(Modifier.width(8.dp))
                BudgetSwitch(ui.summary.closed, onCheckedChange = { on ->
                    Haptics.toggle(view, on)
                    vm.setClosed(ui.key, on)
                    toast.show(if (on) "${ui.key.name} is closed. Your totals are saved." else "${ui.key.name} reopened")
                })
            }
        }
        val tabs: @Composable (Modifier) -> Unit = { m ->
            SegmentedControl(
                options = listOf(MonthTab.Budget to "Budget", MonthTab.Transactions to "Transactions"),
                selected = tab,
                onSelect = vm::setTab,
                fill = compactLayout,
                modifier = m,
            )
        }
        if (compactLayout) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GhostIconButton(Lucide.ChevronLeft, "Previous month", onClick = { main.selectMonth(ui.key.previous()) })
                GhostIconButton(Lucide.ChevronRight, "Next month", onClick = { main.selectMonth(ui.key.next()) })
                Spacer(Modifier.weight(1f))
                closedToggle()
            }
            tabs(Modifier.fillMaxWidth())
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                tabs(Modifier)
                Spacer(Modifier.weight(1f))
                closedToggle()
            }
        }
        if (ui.summary.closed) {
            Pill("Closed: summary uses actuals", accent = true, icon = Lucide.Lock)
        }
        val reduce = LocalReduceMotion.current
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                if (reduce) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                else (fadeIn(tween(420, easing = Motion.Ease)) + slideInVertically(tween(420, easing = Motion.Ease)) { 24 }) togetherWith fadeOut(tween(120))
            },
            label = "monthTab",
        ) { t ->
            when (t) {
                MonthTab.Budget -> BudgetTab(ui, vm, selectedId)
                MonthTab.Transactions -> TransactionsTab(ui, vm, main)
            }
        }
    }
}

@Composable
private fun BudgetTab(ui: MonthUi, vm: MonthViewModel, selectedId: String?) {
    val layout = LocalWindowLayout.current
    // Compact (cover screen): two-line rows instead of three columns, never smaller text.
    val compact = layout.isCompact
    val colW = 96.dp
    if (ui.summary.lines.isEmpty()) {
        BudgetCard { EmptyState("Set your first category to start this month’s budget.") }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(CategoryKind.INCOME, CategoryKind.EXPENSE, CategoryKind.SAVINGS).forEach { kind ->
            val lines = ui.summary.linesOf(kind)
            if (lines.isEmpty()) return@forEach
            BudgetCard(padding = PaddingValues(top = 12.dp, bottom = 4.dp)) {
                TableHeader(kindLabel(kind), compact, colW)
                lines.forEach { line ->
                    CategoryRow(line, compact, colW, selected = line.category.id == selectedId, ui = ui, onClick = { vm.selectCategory(line.category.id) })
                }
                Hairline()
                val exp = lines.sumOf { it.expected }
                val act = lines.sumOf { it.actual ?: 0.0 }
                NumbersRow("Total", exp, act, kind, compact, colW, bold = true)
            }
        }
        // Summary card: Monthly expenses / Saved (incl. match) / Leftover.
        BudgetCard(padding = PaddingValues(top = 12.dp, bottom = 4.dp)) {
            TableHeader("Summary", compact, colW)
            val e = ui.summary.expected
            val a = ui.summary.actual
            NumbersRow("Monthly expenses", e.expenses, a.expenses, CategoryKind.EXPENSE, compact, colW)
            NumbersRow("Saved (incl. match)", e.saved, a.saved, CategoryKind.SAVINGS, compact, colW)
            Hairline()
            NumbersRow("Leftover", e.leftover, a.leftover, CategoryKind.INCOME, compact, colW, bold = true, animate = true)
        }
    }
}

@Composable
private fun TableHeader(title: String, compact: Boolean, colW: Dp) {
    Row(Modifier.padding(horizontal = 14.dp).padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Budget.type.cardTitle, color = Budget.colors.ink, modifier = Modifier.weight(1f))
        if (compact) {
            MicroLabel("Actual")
        } else {
            listOf("Expected", "Actual", "Diff").forEach { Box(Modifier.width(colW), contentAlignment = Alignment.CenterEnd) { MicroLabel(it) } }
        }
    }
}

@Composable
private fun CategoryRow(line: CategoryLine, compact: Boolean, colW: Dp, selected: Boolean, ui: MonthUi, onClick: () -> Unit) {
    val c = Budget.colors
    val bg = if (selected) c.accentSoft else Color.Transparent
    val num = Budget.type.tableNumber
    Column(
        Modifier
            .fillMaxWidth()
            .tappable(shape = RectangleShape, onClick = onClick, label = "Open ${line.category.name}")
            .background(bg)
            .then(if (selected) Modifier.selectedBar(c.accent) else Modifier)
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                com.personal.budget.ui.components.CategoryMark(paletteFor(line.category, ui.snapshot.book))
                Spacer(Modifier.width(8.dp))
                Text(
                    line.category.name,
                    style = Budget.type.body,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                TrackingIcon(line.category)
                if (line.overridden) {
                    Spacer(Modifier.width(4.dp))
                    Pill("manual", fill = c.surface2)
                }
            }
            if (compact) {
                AnimatedMoney(line.actual, style = num, color = c.ink, blank = "—", textAlign = TextAlign.End)
            } else {
                NumCell(line.expected, colW, c.ink2, num)
                NumCell(line.actual, colW, c.ink, num)
                DiffCell(line.difference, line.category.kind, colW, num)
            }
        }
        if (compact) {
            Row(Modifier.padding(start = 20.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("of ${Money.format(line.expected)}", style = Budget.type.secondary, color = c.ink2, modifier = Modifier.weight(1f))
                AnimatedMoney(line.difference, style = Budget.type.secondary, color = diffColor(line.category.kind, line.difference, c), format = { Money.formatSigned(it) })
            }
        }
        if (line.expected > 0 || (line.actual ?: 0.0) > 0) {
            Spacer(Modifier.height(6.dp))
            BudgetProgress(
                line.actual ?: 0.0,
                line.expected,
                height = 4.dp,
                color = categoryColor(line.category, ui.snapshot.book),
                overColor = if (line.category.kind == CategoryKind.EXPENSE) c.bad else c.good,
            )
        }
    }
}

private fun Modifier.selectedBar(color: Color) = drawBehind {
    drawRect(color, size = Size(3.dp.toPx(), size.height))
}

@Composable
private fun NumCell(v: Double?, w: Dp, color: Color, style: TextStyle) {
    Box(Modifier.width(w), contentAlignment = Alignment.CenterEnd) {
        AnimatedMoney(v, style = style, color = color, blank = "—", textAlign = TextAlign.End)
    }
}

@Composable
private fun DiffCell(diff: Double, kind: CategoryKind, w: Dp, numStyle: TextStyle, bold: Boolean = false) {
    val c = Budget.colors
    Box(Modifier.width(w), contentAlignment = Alignment.CenterEnd) {
        AnimatedMoney(
            diff,
            style = if (bold) numStyle.copy(fontWeight = FontWeight.SemiBold) else numStyle,
            color = diffColor(kind, diff, c),
            format = { Money.formatSigned(it) },
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun NumbersRow(label: String, expected: Double, actual: Double, kind: CategoryKind, compact: Boolean, colW: Dp, bold: Boolean = false, animate: Boolean = false) {
    val c = Budget.colors
    val num = Budget.type.tableNumber
    val style = if (bold) num.copy(fontWeight = FontWeight.SemiBold) else num
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = if (bold) Budget.type.cardTitle else Budget.type.body, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (compact) {
                AnimatedMoney(actual, style, if (animate && actual < 0) c.bad else c.ink, textAlign = TextAlign.End)
            } else {
                Box(Modifier.width(colW), contentAlignment = Alignment.CenterEnd) { AnimatedMoney(expected, style, c.ink2, textAlign = TextAlign.End) }
                Box(Modifier.width(colW), contentAlignment = Alignment.CenterEnd) {
                    AnimatedMoney(actual, style, if (animate && actual < 0) c.bad else c.ink, textAlign = TextAlign.End)
                }
                DiffCell(actual - expected, kind, colW, num, bold)
            }
        }
        if (compact) {
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("of ${Money.format(expected)}", style = Budget.type.secondary, color = c.ink2, modifier = Modifier.weight(1f))
                AnimatedMoney(actual - expected, style = Budget.type.secondary, color = diffColor(kind, actual - expected, c), format = { Money.formatSigned(it) })
            }
        }
    }
}

@Composable
private fun TransactionsTab(ui: MonthUi, vm: MonthViewModel, main: MainViewModel) {
    val c = Budget.colors
    val filter by vm.filter.collectAsStateWithLifecycle()
    val book = ui.snapshot.book
    val usedCats = ui.transactions.map { it.categoryId }.distinct().mapNotNull { book.categoriesById[it] }.sortedBy { it.sortOrder }
    val shown = ui.transactions.filter { filter == null || it.categoryId == filter }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Pick("All", filter == null, { vm.setFilter(null) })
            usedCats.forEach { cat -> Pick(cat.name, filter == cat.id, { vm.setFilter(if (filter == cat.id) null else cat.id) }, mark = paletteFor(cat, book)) }
        }
        BudgetCard(padding = PaddingValues(vertical = 6.dp)) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                MicroLabel("${shown.size} transaction${if (shown.size == 1) "" else "s"}", Modifier.weight(1f))
                MicroLabel(Money.format(shown.sumOf { it.amount }))
            }
            if (shown.isEmpty()) {
                EmptyState("No transactions", "Tap + to add one to ${ui.key.name}.")
            }
            AnimatedList(shown, key = { it.id }) { t ->
                TxnRow(t, book.categoriesById[t.categoryId], book, onClick = { main.openEdit(t) })
            }
        }
        BudgetButton("Add transaction", { main.openAdd(categoryId = filter, month = ui.key) }, icon = Lucide.Plus, modifier = Modifier.fillMaxWidth())
    }
}
