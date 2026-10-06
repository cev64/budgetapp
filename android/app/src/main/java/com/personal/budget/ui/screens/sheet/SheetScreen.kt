package com.personal.budget.ui.screens.sheet

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import com.personal.budget.ui.components.blurredGlass
import com.personal.budget.ui.components.glassEdge
import com.personal.budget.ui.components.glassShadow
import com.personal.budget.ui.theme.Radius
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.budget.data.repository.BudgetSnapshot
import com.personal.budget.domain.model.Category
import com.personal.budget.domain.model.LedgerEntry
import com.personal.budget.domain.model.MonthKey
import com.personal.budget.domain.model.Tracking
import com.personal.budget.domain.model.Txn
import com.personal.budget.domain.usecase.LeftRow
import com.personal.budget.domain.usecase.LedgerRow
import com.personal.budget.domain.usecase.Money
import com.personal.budget.domain.usecase.NetWorthMath
import com.personal.budget.domain.usecase.Percent
import com.personal.budget.domain.usecase.SheetModel
import com.personal.budget.ui.MainViewModel
import com.personal.budget.ui.appViewModel
import com.personal.budget.ui.components.BudgetButton
import com.personal.budget.ui.components.BudgetCard
import com.personal.budget.ui.components.ButtonKind
import com.personal.budget.ui.components.EmptyState
import com.personal.budget.ui.components.GhostIconButton
import com.personal.budget.ui.components.GlassTopBar
import com.personal.budget.ui.components.LocalShellPadding
import com.personal.budget.ui.components.LocalToast
import com.personal.budget.ui.components.Lucide
import com.personal.budget.ui.components.ScreenFrame
import com.personal.budget.ui.components.SegmentedControl
import com.personal.budget.ui.screens.LocalWindowLayout
import com.personal.budget.ui.screens.MonthPickerDialog
import com.personal.budget.ui.screens.diffColor
import com.personal.budget.ui.screens.paletteFor
import com.personal.budget.ui.theme.Budget
import com.personal.budget.ui.theme.LocalReduceMotion
import com.personal.budget.ui.theme.Motion
import com.personal.budget.utilities.Haptics
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The spreadsheet, rebuilt (docs/SHEET_VIEW.md). Needs ≥ 600dp: the cover screen shows the
 * [UnfoldPrompt]; unfolding crossfades to the sheet in place. The grid's scroll states and the
 * selected tab live above that switch, so folding and unfolding again keeps them.
 */
@Composable
fun SheetScreen(main: MainViewModel, onGoToYear: () -> Unit) {
    val vm = appViewModel { c, h -> SheetViewModel(c, h) }
    val layout = LocalWindowLayout.current
    val listState = rememberLazyListState()
    val hScroll = rememberScrollState()
    val reduce = LocalReduceMotion.current
    Crossfade(targetState = layout.isCompact, animationSpec = tween(if (reduce) 0 else Motion.APPEAR, easing = Motion.Ease), label = "sheetFold") { compact ->
        if (compact) {
            ScreenFrame(topBar = { GlassTopBar(title = "Sheet", scrolled = false) }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 4.dp)) {
                    com.personal.budget.ui.components.LargeTitle("Budget", "Sheet")
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { UnfoldPrompt(onGoToYear) }
                }
            }
        } else {
            SheetContent(main, vm, listState, hScroll)
        }
    }
}

/** Fixed B-column labels the column must fit unclipped (the widest sets its width). */
private val BLabels = com.personal.budget.domain.usecase.TotalKind.entries.map { it.label } + "Month closed"

@Composable
private fun SheetContent(main: MainViewModel, vm: SheetViewModel, listState: LazyListState, hScroll: ScrollState) {
    val snapshot by main.snapshot.collectAsStateWithLifecycle()
    val selectedMonth by main.selectedMonth.collectAsStateWithLifecycle()
    val storedYear by vm.year.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val s = snapshot ?: return
    val year = if (storedYear > 0) storedYear else selectedMonth.year
    val months = s.book.months.filter { it.year == year }.map { it.key }
    val monthKey = if (tab == SheetViewModel.SUMMARY) null else months.firstOrNull { it.id == tab }
    val editing = remember { SheetEditing() }
    val title = if (monthKey != null) monthKey.label else "Summary · $year"

    // Glass the chrome, not the cells: the title and tab bar float on the ambient field; the grid
    // itself is one opaque, dense card so every cell stays crisp.
    ScreenFrame(topBar = {
        GlassTopBar(title = title, scrolled = false)
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            com.personal.budget.ui.components.LargeTitle("Sheet", title, Modifier.padding(start = 8.dp, end = 12.dp))
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(end = 12.dp)
                    .glassShadow(RoundedCornerShape(Radius.card))
                    .clip(RoundedCornerShape(Radius.card))
                    .background(Budget.colors.card)
                    .glassEdge(RoundedCornerShape(Radius.card)),
            ) {
                val cols = rememberSheetColumns(maxWidth, BLabels)
                CompositionLocalProvider(LocalSheetCols provides cols) {
                if (monthKey != null) {
                    MonthGrid(s, monthKey, vm, editing, listState, hScroll)
                } else if (months.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp)) {
                        BudgetCard { EmptyState("No months in $year", "Use + below to start one.") }
                    }
                } else {
                    SummaryGrid(s, year, vm, editing, listState, hScroll)
                }
                }
            }
            TabBar(year, months, tab, vm)
            Spacer(Modifier.height((LocalShellPadding.current.calculateBottomPadding() - 8.dp).coerceAtLeast(0.dp)))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Grid scaffolding: pinned B–E block + horizontally scrolling right area, one LazyColumn row each.
// ---------------------------------------------------------------------------------------------

@Composable
private fun Grid(
    rowCount: Int,
    listState: LazyListState,
    hScroll: ScrollState,
    left: @Composable (Int) -> Unit,
    right: @Composable (Int) -> Unit,
) {
    val c = Budget.colors
    val cols = LocalSheetCols.current
    // No outer gutter: B starts at the rail edge, like the web sheet. One LazyColumn row per sheet
    // row, so the pinned block and every ledger block share vertical scrolling.
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().background(c.card), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        items(rowCount, key = { it }) { r ->
            Row {
                left(r)
                Row(Modifier.weight(1f).horizontalScroll(hScroll)) {
                    Spacer(Modifier.width(cols.spacer))
                    right(r)
                    Spacer(Modifier.width(cols.spacer))
                }
            }
        }
    }
}

@Composable
private fun BlankLeft() = SheetCell(LocalSheetCols.current.left, style = CellStyle.Blank)

private fun moneyOrBlank(v: Double?) = v?.let { Money.format(it) } ?: ""

// ---------------------------------------------------------------------------------------------
// Month tab
// ---------------------------------------------------------------------------------------------

@Composable
private fun MonthGrid(s: BudgetSnapshot, key: MonthKey, vm: SheetViewModel, editing: SheetEditing, listState: LazyListState, hScroll: ScrollState) {
    val c = Budget.colors
    val cols = LocalSheetCols.current
    val book = s.book
    val toast = LocalToast.current
    val view = LocalView.current
    val leftRows = remember(s, key) { SheetModel.monthLeftRows(book, key) }
    val columns = remember(s, key) { SheetModel.ledgerColumns(book, key) }
    var overrideOpen by remember(key) { mutableStateOf(setOf<String>()) }
    var datePick by remember { mutableStateOf<Pair<Txn?, Category>?>(null) }
    var moving by remember { mutableStateOf<Txn?>(null) }
    val calculated = { toast.show("ƒ  Calculated") }

    // Edit order for Next/Enter/Tab: column C top→down, then D, then each ledger column (item, amount).
    val cats = leftRows.filterIsInstance<LeftRow.MonthCategory>().map { it.line }
    val actualEditable = { l: com.personal.budget.domain.usecase.CategoryLine ->
        l.category.tracking == Tracking.MANUAL || l.overridden || l.category.id in overrideOpen
    }
    editing.order = cats.map { "exp:${it.category.id}" } + cats.filter(actualEditable).map { "act:${it.category.id}" } +
        columns.flatMap { col ->
            col.flatMap { r ->
                when (r) {
                    is LedgerRow.Item -> listOf("t:${r.txn.id}:item", "t:${r.txn.id}:amount")
                    is LedgerRow.Add -> listOf("add:${r.category.id}:item", "add:${r.category.id}:amount")
                    else -> emptyList()
                }
            }
        }

    fun newTxn(cat: Category, item: String = "", amount: Double = 0.0, date: String? = null) =
        vm.saveTransaction(Txn("", key.year, key.month, cat.id, date, item, amount))

    val rowCount = maxOf(leftRows.size, columns.maxOf { it.size })
    Grid(
        rowCount = rowCount,
        listState = listState,
        hScroll = hScroll,
        left = { r ->
            when (val row = leftRows.getOrNull(r)) {
                null, LeftRow.Spacer -> BlankLeft()
                is LeftRow.Header -> Row {
                    SheetCell(cols.b, row.title, CellStyle.Title)
                    listOf("Expected", "Actual", "Difference").forEach { SheetCell(cols.num, it, CellStyle.Header, align = TextAlign.End) }
                }
                is LeftRow.MonthCategory -> {
                    val l = row.line
                    val cat = l.category
                    val budget = book.budget(key, cat.id)
                    Row {
                        SheetCell(cols.b, cat.name, CellStyle.Label, mark = paletteFor(cat, book))
                        SheetCell(
                            cols.num, moneyOrBlank(budget?.expected), CellStyle.Editable, TextAlign.End,
                            edit = EditSpec("exp:${cat.id}", Money.formatInput(budget?.expected), numeric = true) { vm.setExpected(key, cat.id, Money.parse(it)) },
                            editing = editing,
                            description = "${cat.name} expected",
                        )
                        if (actualEditable(l)) {
                            SheetCell(
                                cols.num, moneyOrBlank(budget?.actual), CellStyle.Editable, TextAlign.End,
                                marker = if (cat.tracking == Tracking.LEDGER) "override" else null,
                                edit = EditSpec("act:${cat.id}", Money.formatInput(budget?.actual), numeric = true) { vm.setActual(key, cat.id, Money.parse(it)) },
                                editing = editing,
                                actions = if (cat.tracking == Tracking.LEDGER) {
                                    listOf(
                                        CellAction("Clear override") {
                                            vm.setActual(key, cat.id, null)
                                            overrideOpen = overrideOpen - cat.id
                                        },
                                    )
                                } else {
                                    emptyList()
                                },
                                description = "${cat.name} actual",
                            )
                        } else {
                            SheetCell(
                                cols.num, Money.format(l.ledgerSum), CellStyle.Computed, TextAlign.End,
                                actions = listOf(
                                    CellAction("Override actual") {
                                        overrideOpen = overrideOpen + cat.id
                                        editing.editingId = "act:${cat.id}"
                                    },
                                ),
                                description = "${cat.name} actual, ledger sum",
                            )
                        }
                        SheetCell(
                            cols.num, Money.formatSigned(l.difference), CellStyle.Computed, TextAlign.End,
                            color = diffColor(cat.kind, l.difference, c), onCalculatedHint = calculated,
                        )
                    }
                }
                is LeftRow.Total -> Row {
                    SheetCell(cols.b, row.kind.label, CellStyle.TotalLabel)
                    SheetCell(cols.num, Money.format(row.expected), CellStyle.Total, TextAlign.End, onCalculatedHint = calculated)
                    SheetCell(cols.num, Money.format(row.actual), CellStyle.Total, TextAlign.End, onCalculatedHint = calculated)
                    SheetCell(cols.num, Money.formatSigned(row.difference), CellStyle.Total, TextAlign.End, color = diffColor(row.kind.diffKind, row.difference, c), onCalculatedHint = calculated)
                }
                is LeftRow.Closed -> Row {
                    SheetCell(cols.b, "Month closed", CellStyle.Label)
                    SheetCell(cols.num, style = CellStyle.Editable, description = "Month closed") {
                        Checkbox(
                            checked = row.closed,
                            onCheckedChange = { on ->
                                Haptics.toggle(view, on)
                                vm.setClosed(key, on)
                                toast.show(if (on) "${key.name} is closed. Your totals are saved." else "${key.name} reopened")
                            },
                            colors = CheckboxDefaults.colors(checkedColor = c.accent, uncheckedColor = c.controlBorder, checkmarkColor = c.onAccent),
                        )
                    }
                    SheetCell(cols.num * 2, style = CellStyle.Blank)
                }
                else -> BlankLeft()
            }
        },
        right = { r ->
            columns.forEachIndexed { ci, col ->
                if (ci > 0) Spacer(Modifier.width(cols.spacer))
                when (val row = col.getOrNull(r)) {
                    null, LedgerRow.Blank -> SheetCell(cols.block, style = CellStyle.Blank)
                    is LedgerRow.Title -> SheetCell(cols.block, row.category.name, CellStyle.Title, mark = paletteFor(row.category, book))
                    is LedgerRow.ColumnHeader -> Row {
                        SheetCell(cols.date, "Date", CellStyle.Header)
                        SheetCell(cols.item, "Item", CellStyle.Header)
                        SheetCell(cols.amount, "Amount", CellStyle.Header, align = TextAlign.End)
                    }
                    is LedgerRow.Item -> {
                        val t = row.txn
                        val actions = listOf(
                            CellAction("Move to month…") { moving = t },
                            CellAction("Delete transaction", destructive = true) {
                                vm.deleteTransaction(t.id)
                                toast.show("Transaction deleted", "Undo") { vm.saveTransaction(t) }
                            },
                        )
                        Row {
                            SheetCell(
                                cols.date, com.personal.budget.ui.screens.shortDate(t.date), CellStyle.Editable,
                                onTap = { datePick = t to row.category }, actions = actions, description = "Date ${t.date ?: "none"}",
                            )
                            SheetCell(
                                cols.item, t.item, CellStyle.Editable,
                                edit = EditSpec("t:${t.id}:item", t.item, numeric = false) { vm.saveTransaction(t.copy(item = it.trim())) },
                                editing = editing, actions = actions, menuTitle = t.item,
                            )
                            SheetCell(
                                cols.amount, Money.format(t.amount), CellStyle.Editable, TextAlign.End,
                                color = if (t.amount < 0) c.good else null,
                                edit = EditSpec("t:${t.id}:amount", Money.formatInput(t.amount), numeric = true) { v ->
                                    Money.parse(v)?.let { vm.saveTransaction(t.copy(amount = it)) }
                                },
                                editing = editing, actions = actions,
                            )
                        }
                    }
                    is LedgerRow.Add -> Row {
                        val cat = row.category
                        SheetCell(cols.date, "", CellStyle.Editable, onTap = { datePick = null to cat }, description = "New ${cat.name} date")
                        SheetCell(
                            cols.item, "", CellStyle.Editable,
                            edit = EditSpec("add:${cat.id}:item", "", numeric = false) { if (it.isNotBlank()) newTxn(cat, item = it.trim()) },
                            editing = editing, description = "New ${cat.name} item",
                        )
                        SheetCell(
                            cols.amount, "", CellStyle.Editable, TextAlign.End,
                            edit = EditSpec("add:${cat.id}:amount", "", numeric = true) { v -> Money.parse(v)?.let { newTxn(cat, amount = it) } },
                            editing = editing, description = "New ${cat.name} amount",
                        )
                    }
                }
            }
        },
    )

    datePick?.let { (txn, cat) ->
        SheetDatePicker(
            initial = txn?.date,
            onPick = { date ->
                if (txn != null) vm.saveTransaction(txn.copy(date = date)) else if (date != null) newTxn(cat, date = date)
                datePick = null
            },
            onDismiss = { datePick = null },
        )
    }
    moving?.let { t ->
        MonthPickerDialog(
            selected = key,
            book = book,
            onPick = { m ->
                vm.moveTransaction(t, m, create = false)
                toast.show("Moved to ${m.label}")
                moving = null
            },
            onCreate = { m ->
                vm.moveTransaction(t, m, create = true)
                toast.show("Moved to ${m.label}")
                moving = null
            },
            onDismiss = { moving = null },
        )
    }
}

@Composable
private fun SheetDatePicker(initial: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val c = Budget.colors
    val start = initial?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
    val state = rememberDatePickerState(initialSelectedDateMillis = start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    com.personal.budget.ui.components.PauseAmbientDrift()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            BudgetButton("Set date", {
                onPick(state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() })
            }, kind = ButtonKind.Primary, modifier = Modifier.padding(end = 8.dp, bottom = 8.dp))
        },
        dismissButton = {
            Row {
                if (initial != null) BudgetButton("No date", { onPick(null) }, kind = ButtonKind.Ghost, modifier = Modifier.padding(bottom = 8.dp))
                BudgetButton("Cancel", onDismiss, kind = ButtonKind.Ghost, modifier = Modifier.padding(bottom = 8.dp))
            }
        },
        colors = DatePickerDefaults.colors(containerColor = c.card),
    ) {
        DatePicker(state = state, colors = DatePickerDefaults.colors(containerColor = c.card, selectedDayContainerColor = c.accent, todayDateBorderColor = c.accent))
    }
}

// ---------------------------------------------------------------------------------------------
// Summary tab: B–E year block, G H net worth, J K ledger (IOUs)
// ---------------------------------------------------------------------------------------------

private sealed interface SummaryRight {
    data object Header : SummaryRight
    data class AccountRow(val account: com.personal.budget.domain.model.Account) : SummaryRight
    data class Total(val label: String, val value: Double) : SummaryRight
    data object Blank : SummaryRight
}

private sealed interface LedgerSide {
    data object Header : LedgerSide
    data class Entry(val e: LedgerEntry) : LedgerSide
    data object Add : LedgerSide
    data class Total(val value: Double) : LedgerSide
}

@Composable
private fun SummaryGrid(s: BudgetSnapshot, year: Int, vm: SheetViewModel, editing: SheetEditing, listState: LazyListState, hScroll: ScrollState) {
    val c = Budget.colors
    val cols = LocalSheetCols.current
    val toast = LocalToast.current
    val book = s.book
    val calculated = { toast.show("ƒ  Calculated") }
    val leftRows = remember(s, year) { SheetModel.summaryLeftRows(book, year, s.settings).orEmpty() }
    val nw = remember(s) { NetWorthMath.compute(book, s.accounts, s.ledger) }
    val accounts = s.accounts.filter { !it.archived }
    val gh: List<SummaryRight> = listOf(SummaryRight.Header) + accounts.map { SummaryRight.AccountRow(it) } +
        listOf(SummaryRight.Total("Net Worth", nw.netWorth), SummaryRight.Blank, SummaryRight.Total("Super Liquid Assets", nw.superLiquid))
    val open = s.ledger.filter { !it.settled }
    val jk: List<LedgerSide> = listOf(LedgerSide.Header) + open.map { LedgerSide.Entry(it) } + listOf(LedgerSide.Add, LedgerSide.Total(nw.netReconciliations))

    editing.order = accounts.filter { it.linkedCategoryId == null }.map { "acct:${it.id}" } +
        open.flatMap { listOf("led:${it.id}:name", "led:${it.id}:amount") } + listOf("ledadd:name", "ledadd:amount")

    Grid(
        rowCount = maxOf(leftRows.size, gh.size, jk.size),
        listState = listState,
        hScroll = hScroll,
        left = { r ->
            when (val row = leftRows.getOrNull(r)) {
                null, LeftRow.Spacer -> BlankLeft()
                is LeftRow.Header -> Row {
                    SheetCell(cols.b, row.title, CellStyle.Title)
                    listOf("Expected", "Actual", "Difference").forEach { SheetCell(cols.num, it, CellStyle.Header, align = TextAlign.End) }
                }
                is LeftRow.YearCategory -> Row {
                    SheetCell(cols.b, row.category.name, CellStyle.Label, mark = paletteFor(row.category, book))
                    SheetCell(cols.num, Money.format(row.expected), CellStyle.Computed, TextAlign.End, onCalculatedHint = calculated)
                    SheetCell(cols.num, Money.format(row.actual), CellStyle.Computed, TextAlign.End, onCalculatedHint = calculated)
                    SheetCell(cols.num, Money.formatSigned(row.difference), CellStyle.Computed, TextAlign.End, color = diffColor(row.category.kind, row.difference, c), onCalculatedHint = calculated)
                }
                is LeftRow.Total -> Row {
                    SheetCell(cols.b, row.kind.label, CellStyle.TotalLabel)
                    SheetCell(cols.num, Money.format(row.expected), CellStyle.Total, TextAlign.End, onCalculatedHint = calculated)
                    SheetCell(cols.num, Money.format(row.actual), CellStyle.Total, TextAlign.End, onCalculatedHint = calculated)
                    SheetCell(
                        cols.num, Money.formatSigned(row.difference), CellStyle.Total, TextAlign.End,
                        color = diffColor(row.kind.diffKind, row.difference, c), onCalculatedHint = calculated,
                        description = if (row.kind == com.personal.budget.domain.usecase.TotalKind.LEFTOVER) "Leftover vs plan ${Money.formatSigned(row.difference)}" else null,
                    )
                }
                is LeftRow.Metric -> Row {
                    val fmt = { v: Double? -> if (row.percent) Percent.format(v) else moneyOrBlank(v) }
                    SheetCell(cols.b, row.label, CellStyle.Label)
                    SheetCell(cols.num, fmt(row.expected), CellStyle.Computed, TextAlign.End, onCalculatedHint = calculated)
                    SheetCell(cols.num, fmt(row.actual), CellStyle.Computed, TextAlign.End, onCalculatedHint = calculated)
                    SheetCell(cols.num, style = CellStyle.Blank)
                }
                else -> BlankLeft()
            }
        },
        right = { r ->
            when (val g = gh.getOrNull(r)) {
                null, SummaryRight.Blank -> SheetCell(cols.name + cols.value, style = CellStyle.Blank)
                SummaryRight.Header -> Row {
                    SheetCell(cols.name, "Net worth", CellStyle.Header)
                    SheetCell(cols.value, year.toString(), CellStyle.Header, align = TextAlign.End)
                }
                is SummaryRight.AccountRow -> Row {
                    val a = g.account
                    val bal = nw.balances[a.id] ?: a.balance
                    SheetCell(cols.name, a.name, CellStyle.Label)
                    if (a.linkedCategoryId == null) {
                        SheetCell(
                            cols.value, Money.format(bal), CellStyle.Editable, TextAlign.End,
                            color = if (bal < 0) c.bad else null,
                            edit = EditSpec("acct:${a.id}", Money.formatInput(a.balance), numeric = true) { v -> Money.parse(v)?.let { vm.setBalance(a.id, it) } },
                            editing = editing, description = "${a.name} balance",
                        )
                    } else {
                        SheetCell(
                            cols.value, Money.format(bal), CellStyle.Computed, TextAlign.End, marker = "auto",
                            onCalculatedHint = { toast.show("auto · base + contributions (edit the base in Net worth)") },
                        )
                    }
                }
                is SummaryRight.Total -> Row {
                    SheetCell(cols.name, g.label, CellStyle.TotalLabel)
                    SheetCell(cols.value, Money.format(g.value), CellStyle.Total, TextAlign.End, onCalculatedHint = calculated)
                }
            }
            Spacer(Modifier.width(cols.spacer))
            when (val j = jk.getOrNull(r)) {
                null -> SheetCell(cols.name + cols.value, style = CellStyle.Blank)
                LedgerSide.Header -> Row {
                    SheetCell(cols.name, "Ledger", CellStyle.Header)
                    SheetCell(cols.value, "Amount", CellStyle.Header, align = TextAlign.End)
                }
                is LedgerSide.Entry -> Row {
                    val e = j.e
                    val actions = listOf(
                        CellAction("Settle") {
                            vm.setSettled(e.id, true)
                            toast.show("${e.name} settled", "Undo") { vm.setSettled(e.id, false) }
                        },
                        CellAction("Delete", destructive = true) {
                            vm.deleteLedger(e.id)
                            toast.show("IOU deleted", "Undo") { vm.saveLedger(e) }
                        },
                    )
                    SheetCell(
                        cols.name, e.name, CellStyle.Editable,
                        edit = EditSpec("led:${e.id}:name", e.name, numeric = false) { if (it.isNotBlank()) vm.saveLedger(e.copy(name = it.trim())) },
                        editing = editing, actions = actions,
                    )
                    SheetCell(
                        cols.value, Money.format(e.amount), CellStyle.Editable, TextAlign.End,
                        color = if (e.amount > 0) c.good else if (e.amount < 0) c.bad else null,
                        edit = EditSpec("led:${e.id}:amount", Money.formatInput(e.amount), numeric = true) { v -> Money.parse(v)?.let { vm.saveLedger(e.copy(amount = it)) } },
                        editing = editing, actions = actions,
                    )
                }
                LedgerSide.Add -> Row {
                    SheetCell(
                        cols.name, "", CellStyle.Editable,
                        edit = EditSpec("ledadd:name", "", numeric = false) { if (it.isNotBlank()) vm.saveLedger(LedgerEntry("", it.trim())) },
                        editing = editing, description = "New IOU name",
                    )
                    SheetCell(
                        cols.value, "", CellStyle.Editable, TextAlign.End,
                        edit = EditSpec("ledadd:amount", "", numeric = true) { v -> Money.parse(v)?.let { vm.saveLedger(LedgerEntry("", "IOU", it)) } },
                        editing = editing, description = "New IOU amount",
                    )
                }
                is LedgerSide.Total -> Row {
                    SheetCell(cols.name, "Net Reconciliations", CellStyle.TotalLabel)
                    SheetCell(cols.value, Money.format(j.value), CellStyle.Total, TextAlign.End, onCalculatedHint = calculated)
                }
            }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Bottom tab bar (Google-Sheets style, glass, radius 24): year switcher · Summary · months · +
// ---------------------------------------------------------------------------------------------

@Composable
private fun TabBar(year: Int, months: List<MonthKey>, tab: String, vm: SheetViewModel) {
    val c = Budget.colors
    val options = listOf(SheetViewModel.SUMMARY to "Summary") + months.map { it.id to it.shortName }
    val selected = options.firstOrNull { it.first == tab }?.first ?: SheetViewModel.SUMMARY
    val next = months.maxOrNull()?.next() ?: MonthKey(year, 1)
    val pill = RoundedCornerShape(Radius.pill)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 0.dp, end = 12.dp, top = 10.dp, bottom = 4.dp)
            .glassShadow(pill, large = true)
            .blurredGlass(com.personal.budget.ui.components.LocalShellHaze.current, pill)
            .glassEdge(pill)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GhostIconButton(Lucide.ChevronLeft, "Previous year", onClick = { vm.setYear(year - 1) })
        Text(year.toString(), style = Budget.type.bodyStrong, color = c.ink)
        GhostIconButton(Lucide.ChevronRight, "Next year", onClick = { vm.setYear(year + 1) })
        Spacer(Modifier.width(6.dp))
        TabStrip(options, selected, vm::selectTab, Modifier.weight(1f))
        GhostIconButton(Lucide.Plus, "Start ${next.label}", onClick = { vm.createMonth(next) })
    }
}

/**
 * Summary + month tabs in a horizontal scroller. The selected tab is centred (clamped at the ends):
 * instantly on first show, then with the same 350 ms spring-soft glide as the thumb. All 13 tabs
 * are always composed, so a plain scroller with measured positions centres exactly.
 */
@Composable
private fun TabStrip(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val reduce = LocalReduceMotion.current
    var viewport by remember { mutableIntStateOf(0) }
    var placed by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(placed, viewport) {
        val (x, w) = placed ?: return@LaunchedEffect
        if (viewport == 0) return@LaunchedEffect
        val target = (x + w / 2 - viewport / 2).coerceIn(0, scroll.maxValue)
        if (!settled || reduce) {
            scroll.scrollTo(target)
            settled = true
        } else {
            scroll.animateScrollTo(target, tween(Motion.THUMB, easing = Motion.SpringSoft))
        }
    }
    Box(modifier.onSizeChanged { viewport = it.width }.horizontalScroll(scroll)) {
        SegmentedControl(options, selected, onSelect, onSelectedPlaced = { x, w ->
            placed = with(density) { x.roundToPx() to w.roundToPx() }
        })
    }
}
