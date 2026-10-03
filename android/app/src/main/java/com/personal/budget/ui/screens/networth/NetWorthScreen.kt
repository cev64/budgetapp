package com.personal.budget.ui.screens.networth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.personal.budget.AppContainer
import com.personal.budget.data.repository.BudgetSnapshot
import com.personal.budget.domain.model.Account
import com.personal.budget.domain.model.AccountGroup
import com.personal.budget.domain.model.CategoryKind
import com.personal.budget.domain.model.LedgerEntry
import com.personal.budget.domain.usecase.HistoryRange
import com.personal.budget.domain.usecase.Money
import com.personal.budget.domain.usecase.NetWorthHistoryMath
import com.personal.budget.ui.components.isScrolled
import com.personal.budget.ui.components.HistoryChart
import com.personal.budget.ui.components.SegmentedControl
import com.personal.budget.ui.components.Sparkline
import com.personal.budget.ui.components.shortDay
import com.personal.budget.domain.usecase.NetWorthMath
import com.personal.budget.domain.usecase.NetWorthSummary
import com.personal.budget.ui.appViewModel
import com.personal.budget.ui.components.AnimatedList
import com.personal.budget.ui.components.AnimatedMoney
import com.personal.budget.ui.components.BudgetButton
import com.personal.budget.ui.components.BudgetCard
import com.personal.budget.ui.components.BudgetDialog
import com.personal.budget.ui.components.BudgetSwitch
import com.personal.budget.ui.components.BudgetTextField
import com.personal.budget.ui.components.ButtonKind
import com.personal.budget.ui.components.CardHeader
import com.personal.budget.ui.components.ConfirmDialog
import com.personal.budget.ui.components.GhostIconButton
import com.personal.budget.ui.components.GlassTopBar
import com.personal.budget.ui.components.LocalToast
import com.personal.budget.ui.components.Lucide
import com.personal.budget.ui.components.MicroLabel
import com.personal.budget.ui.components.MoneyField
import com.personal.budget.ui.components.Pick
import com.personal.budget.ui.components.PickRow
import com.personal.budget.ui.components.Pill
import com.personal.budget.ui.components.ScreenFrame
import com.personal.budget.ui.components.tappable
import com.personal.budget.ui.screens.LocalWindowLayout
import com.personal.budget.ui.screens.Tile
import com.personal.budget.ui.theme.Budget
import com.personal.budget.utilities.Haptics
import kotlinx.coroutines.launch

class NetWorthViewModel(private val c: AppContainer, private val handle: SavedStateHandle) : ViewModel() {
    /** Open editor, kept across fold/unfold: "account:<id>", "account:new", "ledger:<id>", "ledger:new". */
    val editing = handle.getStateFlow<String?>(KEY_EDIT, null)

    fun edit(target: String?) {
        handle[KEY_EDIT] = target
    }

    val range = handle.getStateFlow(KEY_RANGE, HistoryRange.M3.name)
    val overlay = handle.getStateFlow(KEY_OVERLAY, false)

    fun setRange(r: HistoryRange) {
        handle[KEY_RANGE] = r.name
    }

    fun setOverlay(on: Boolean) {
        handle[KEY_OVERLAY] = on
    }

    fun saveAccount(a: Account) = viewModelScope.launch { c.repository.saveAccount(a) }
    fun deleteAccount(id: String) = viewModelScope.launch { c.repository.deleteAccount(id) }
    fun setBalance(id: String, v: Double) = viewModelScope.launch { c.repository.setAccountBalance(id, v) }
    fun saveLedger(e: LedgerEntry) = viewModelScope.launch { c.repository.saveLedgerEntry(e) }
    fun deleteLedger(id: String) = viewModelScope.launch { c.repository.deleteLedgerEntry(id) }
    fun restoreLedger(e: LedgerEntry) = viewModelScope.launch { c.repository.saveLedgerEntry(e) }
    fun setSettled(id: String, settled: Boolean) = viewModelScope.launch { c.repository.setSettled(id, settled) }

    companion object {
        private const val KEY_EDIT = "networth_edit"
        private const val KEY_RANGE = "networth_range"
        private const val KEY_OVERLAY = "networth_overlay"
    }
}

/** Net worth (DOMAIN_RULES §5): accounts grouped, linked accounts auto-computed, IOU ledger. */
@Composable
fun NetWorthScreen() {
    val vm = appViewModel { c, h -> NetWorthViewModel(c, h) }
    val main = appViewModel { c, h -> com.personal.budget.ui.MainViewModel(c, h) }
    val snapshot by main.snapshot.collectAsStateWithLifecycle()
    val editing by vm.editing.collectAsStateWithLifecycle()
    val layout = LocalWindowLayout.current
    val scroll = rememberScrollState()
    val s = snapshot ?: return
    val nw = remember(s) { NetWorthMath.compute(s.book, s.accounts, s.ledger) }

    ScreenFrame(topBar = { GlassTopBar(micro = "Budget", title = "Net worth", scrolled = scroll.isScrolled()) }) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(padding).padding(horizontal = layout.gutter, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (layout.isCompact) {
                HistoryCard(s, nw, vm, chartHeight = 180.dp)
                Summary(s, nw, compact = true)
                AccountsCard(s, nw, vm)
                LedgerCard(s, nw, vm)
            } else {
                // Expanded: the history chart sits beside the accounts list.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HistoryCard(s, nw, vm, chartHeight = 240.dp)
                        Summary(s, nw, compact = LocalWindowLayout.current != com.personal.budget.ui.screens.WindowLayout.Expanded)
                        LedgerCard(s, nw, vm)
                    }
                    Column(Modifier.weight(1f)) { AccountsCard(s, nw, vm) }
                }
            }
        }
    }

    when {
        editing == null -> Unit
        editing!!.startsWith("account:") -> {
            val id = editing!!.removePrefix("account:")
            AccountDialog(s, s.accounts.firstOrNull { it.id == id }, vm)
        }
        editing!!.startsWith("ledger:") -> {
            val id = editing!!.removePrefix("ledger:")
            LedgerDialog(s.ledger.firstOrNull { it.id == id }, vm)
        }
    }
}

@Composable
private fun HistoryCard(s: BudgetSnapshot, nw: NetWorthSummary, vm: NetWorthViewModel, chartHeight: androidx.compose.ui.unit.Dp) {
    val c = Budget.colors
    val rangeName by vm.range.collectAsStateWithLifecycle()
    val overlayOn by vm.overlay.collectAsStateWithLifecycle()
    val range = HistoryRange.valueOf(rangeName)
    val history = s.netWorthHistory
    val change = NetWorthHistoryMath.changeOver(history, range)
    val points = remember(history, range) { NetWorthHistoryMath.series(history, range) }
    val overlay = remember(history, range, overlayOn) { if (overlayOn) NetWorthHistoryMath.series(history, range) { it.superLiquid } else emptyList() }
    BudgetCard(padding = PaddingValues(18.dp)) {
        MicroLabel("Net worth")
        Spacer(Modifier.height(4.dp))
        AnimatedMoney(nw.netWorth, style = Budget.type.hero, color = if (nw.netWorth < 0) c.bad else c.ink)
        if (change != null && history.size >= 2) {
            ChangeLine(change.amount, if (change.sinceFallback) "since ${shortDay(change.base.date)}" else range.label)
        } else {
            Text("Accounts + unsettled IOUs", style = Budget.type.secondary, color = c.ink2)
        }
        Spacer(Modifier.height(14.dp))
        if (history.size < 2) {
            Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AppIconLarge()
                Spacer(Modifier.height(8.dp))
                Text("History starts today, a point is saved every day.", style = Budget.type.secondary, color = c.ink2)
            }
            return@BudgetCard
        }
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            SegmentedControl(HistoryRange.entries.map { it to it.label }, range, vm::setRange)
            Pick("Super liquid", overlayOn, { vm.setOverlay(!overlayOn) }, dotColor = c.good)
        }
        Spacer(Modifier.height(12.dp))
        HistoryChart(points, height = chartHeight, overlay = overlay, animateKey = range)
    }
}

@Composable
private fun AppIconLarge() {
    com.personal.budget.ui.components.AppIcon(Lucide.ChartColumn, null, tint = Budget.colors.ink3, size = 28.dp)
}

@Composable
private fun ChangeLine(amount: Double, label: String) {
    val c = Budget.colors
    val color = when {
        kotlin.math.abs(amount) < 0.005 -> c.ink3
        amount > 0 -> c.good
        else -> c.bad
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(Money.formatSigned(amount), style = Budget.type.secondary.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = color)
        Text(" · $label", style = Budget.type.secondary, color = c.ink3)
    }
}

@Composable
private fun Summary(s: BudgetSnapshot, nw: NetWorthSummary, compact: Boolean) {
    val c = Budget.colors
    val h = s.netWorthHistory
    fun ch(v: (com.personal.budget.domain.model.NetWorthSnapshot) -> Double) =
        if (h.size >= 2) NetWorthHistoryMath.changeOver(h, HistoryRange.M1, v) else null
    val investments: (com.personal.budget.domain.model.NetWorthSnapshot) -> Double = { snap ->
        snap.accounts.filter { it.group == AccountGroup.INVESTMENT }.sumOf { it.balance }
    }
    val tiles = listOf(
        Triple("Super liquid", nw.superLiquid, ch { it.superLiquid }),
        Triple("Investments", nw.investments, ch(investments)),
        Triple("Net reconciliations", nw.netReconciliations, ch { it.reconciliations }),
    )
    if (compact) {
        // Cover screen: one row per metric (label left, value + 30-day change right).
        BudgetCard(padding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
            tiles.forEachIndexed { i, (label, v, change) ->
                val color = if (i == 2) (if (v < 0) c.bad else if (v > 0) c.good else c.ink) else c.ink
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = Budget.type.body, color = c.ink, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        AnimatedMoney(v, style = Budget.type.tableNumber.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = color)
                        if (change != null) ChangeLine(change.amount, if (change.sinceFallback) "since ${shortDay(change.base.date)}" else "30d")
                    }
                }
                if (i < tiles.lastIndex) com.personal.budget.ui.components.Hairline()
            }
        }
    } else {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            tiles.forEachIndexed { i, (label, v, change) ->
                Tile(label, Modifier.weight(1f).fillMaxHeight(), wrapLabel = true) {
                    val color = if (i == 2) (if (v < 0) c.bad else if (v > 0) c.good else c.ink) else c.ink
                    AnimatedMoney(v, style = Budget.type.number, color = color)
                    if (change != null) {
                        ChangeLine(change.amount, if (change.sinceFallback) "since ${shortDay(change.base.date)}" else "30d")
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountsCard(s: BudgetSnapshot, nw: NetWorthSummary, vm: NetWorthViewModel) {
    val c = Budget.colors
    val active = s.accounts.filter { !it.archived }
    BudgetCard(padding = PaddingValues(top = 14.dp, bottom = 6.dp)) {
        CardHeader("Accounts", Modifier.padding(horizontal = 14.dp)) {
            BudgetButton("Add", { vm.edit("account:new") }, kind = ButtonKind.Ghost, icon = Lucide.Plus)
        }
        if (active.isEmpty()) Text("No accounts yet.", style = Budget.type.secondary, color = c.ink3, modifier = Modifier.padding(14.dp))
        AccountGroup.entries.forEach { group ->
            val rows = active.filter { it.group == group }
            if (rows.isEmpty()) return@forEach
            Row(Modifier.padding(horizontal = 14.dp).padding(top = 8.dp, bottom = 2.dp)) {
                MicroLabel(group.label, Modifier.weight(1f))
                MicroLabel(Money.format(rows.sumOf { nw.balances[it.id] ?: 0.0 }))
            }
            AnimatedList(rows, key = { it.id }) { a ->
                val linked = a.linkedCategoryId?.let { s.book.categoriesById[it] }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .tappable(shape = RectangleShape, onClick = { vm.edit("account:${a.id}") })
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(a.name, style = Budget.type.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (a.linkedCategoryId != null) {
                                Spacer(Modifier.width(6.dp))
                                Pill("auto", accent = true)
                            }
                            if (a.liquid) {
                                Spacer(Modifier.width(6.dp))
                                Pill("liquid")
                            }
                        }
                        if (a.linkedCategoryId != null) {
                            Text(
                                "auto · ${Money.format(a.baseAmount)} base + ${linked?.name ?: "category"} contributions" +
                                    if ((linked?.matchMultiplier ?: 1.0) != 1.0) " ×${Money.formatInput(linked?.matchMultiplier)}" else "",
                                style = Budget.type.small,
                                color = c.ink3,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    val bal = nw.balances[a.id] ?: a.balance
                    AnimatedMoney(bal, style = Budget.type.bodyStrong, color = if (bal < 0) c.bad else c.ink)
                }
            }
        }
        val archived = s.accounts.count { it.archived }
        if (archived > 0) {
            Text("$archived archived account${if (archived == 1) "" else "s"} hidden", style = Budget.type.small, color = c.ink3, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun LedgerCard(s: BudgetSnapshot, nw: NetWorthSummary, vm: NetWorthViewModel) {
    val c = Budget.colors
    val view = LocalView.current
    BudgetCard(padding = PaddingValues(top = 14.dp, bottom = 6.dp)) {
        CardHeader("Ledger (IOUs)", Modifier.padding(horizontal = 14.dp)) {
            BudgetButton("Add", { vm.edit("ledger:new") }, kind = ButtonKind.Ghost, icon = Lucide.Plus)
        }
        Row(Modifier.padding(horizontal = 14.dp, vertical = 2.dp)) {
            MicroLabel("+ owed to me · − I owe", Modifier.weight(1f))
            MicroLabel("Net ${Money.format(nw.netReconciliations)}")
        }
        if (s.ledger.isEmpty()) Text("Nothing owed either way.", style = Budget.type.secondary, color = c.ink3, modifier = Modifier.padding(14.dp))
        AnimatedList(s.ledger, key = { it.id }) { e ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .tappable(shape = RectangleShape, onClick = { vm.edit("ledger:${e.id}") })
                    .padding(start = 6.dp, end = 14.dp, top = 4.dp, bottom = 4.dp)
                    .graphicsLayer { alpha = if (e.settled) .5f else 1f },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettleCheck(e.settled) { on ->
                    Haptics.toggle(view, on)
                    vm.setSettled(e.id, on)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        e.name,
                        style = Budget.type.body.copy(textDecoration = if (e.settled) TextDecoration.LineThrough else null),
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!e.note.isNullOrBlank()) Text(e.note, style = Budget.type.small, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    Money.format(e.amount),
                    style = Budget.type.bodyStrong.copy(textDecoration = if (e.settled) TextDecoration.LineThrough else null),
                    color = when {
                        e.amount > 0 -> c.good
                        e.amount < 0 -> c.bad
                        else -> c.ink
                    },
                )
            }
        }
    }
}

@Composable
private fun SettleCheck(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Budget.colors
    Box(
        Modifier
            .size(44.dp)
            .tappable(shape = CircleShape, role = Role.Checkbox, onClick = { onChange(!checked) })
            .semantics {
                contentDescription = "Settled"
                stateDescription = if (checked) "Settled" else "Not settled"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .background(if (checked) c.accent else Color.Transparent, CircleShape)
                .border(1.5.dp, if (checked) c.accent else c.line2, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Lucide.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
        }
    }
}

@Composable
private fun AccountDialog(s: BudgetSnapshot, existing: Account?, vm: NetWorthViewModel) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var group by rememberSaveable { mutableStateOf(existing?.group ?: AccountGroup.CASH) }
    var liquid by rememberSaveable { mutableStateOf(existing?.liquid ?: false) }
    var balance by rememberSaveable { mutableStateOf(existing?.balance) }
    var linked by rememberSaveable { mutableStateOf(existing?.linkedCategoryId) }
    var base by rememberSaveable { mutableStateOf(existing?.baseAmount ?: 0.0) }
    var archived by rememberSaveable { mutableStateOf(existing?.archived ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val toast = LocalToast.current
    val c = Budget.colors
    val savingsCats = s.book.categories.filter { it.kind == CategoryKind.SAVINGS }
    val accountHistory = remember(s.netWorthHistory, existing?.id) {
        existing?.let { NetWorthHistoryMath.accountSeries(s.netWorthHistory, it.id) }.orEmpty()
    }
    BudgetDialog(
        onDismiss = { vm.edit(null) },
        title = if (existing == null) "New account" else "Edit account",
        actions = {
            if (existing != null) BudgetButton("Delete", { confirmDelete = true }, kind = ButtonKind.Danger)
            Spacer(Modifier.weight(1f))
            BudgetButton("Cancel", { vm.edit(null) })
            BudgetButton("Save", {
                if (name.isBlank()) return@BudgetButton
                vm.saveAccount(
                    Account(
                        id = existing?.id.orEmpty(), name = name, group = group, liquid = liquid,
                        balance = balance ?: 0.0, linkedCategoryId = linked, baseAmount = base,
                        sortOrder = existing?.sortOrder ?: (s.accounts.maxOfOrNull { it.sortOrder } ?: -1) + 1, archived = archived,
                    ),
                )
                vm.edit(null)
            }, kind = ButtonKind.Primary, enabled = name.isNotBlank())
        },
    ) {
        if (existing != null) {
            MicroLabel("Balance history")
            Spacer(Modifier.height(4.dp))
            if (accountHistory.size >= 2) {
                NetWorthHistoryMath.seriesChange(accountHistory)?.let { ChangeLine(it, "since ${shortDay(accountHistory.first().date)}") }
                Spacer(Modifier.height(8.dp))
                HistoryChart(accountHistory, height = 120.dp, animateKey = existing.id)
            } else {
                Text("History starts today, a point is saved every day.", style = Budget.type.small, color = c.ink3)
            }
            Spacer(Modifier.height(16.dp))
        }
        BudgetTextField(name, { name = it }, label = "Name", placeholder = "Checking")
        Spacer(Modifier.height(12.dp))
        MicroLabel("Group")
        Spacer(Modifier.height(6.dp))
        PickRow(AccountGroup.entries.map { it to it.label }, group, { group = it })
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Super liquid", style = Budget.type.body, color = c.ink)
                Text("Counts toward super liquid assets", style = Budget.type.small, color = c.ink3)
            }
            BudgetSwitch(liquid, { liquid = it })
        }
        Spacer(Modifier.height(12.dp))
        MicroLabel("Balance source")
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pick("Manual balance", linked == null, { linked = null })
            savingsCats.forEach { cat -> Pick("Linked: ${cat.name}", linked == cat.id, { linked = cat.id }) }
        }
        Spacer(Modifier.height(12.dp))
        if (linked == null) {
            MoneyField(balance, { balance = it ?: 0.0 }, label = "Balance (debts negative)", allowBlank = false, textAlign = androidx.compose.ui.text.style.TextAlign.Start)
        } else {
            MoneyField(base, { base = it ?: 0.0 }, label = "Base amount", allowBlank = false, textAlign = androidx.compose.ui.text.style.TextAlign.Start)
            Spacer(Modifier.height(4.dp))
            Text("Balance = base + multiplier × every typed actual for that category.", style = Budget.type.small, color = c.ink3)
        }
        if (existing != null) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Archived (hidden, excluded from net worth)", style = Budget.type.body, color = c.ink, modifier = Modifier.weight(1f))
                BudgetSwitch(archived, { archived = it })
            }
        }
    }
    if (confirmDelete && existing != null) {
        ConfirmDialog(
            title = "Delete ${existing.name}?",
            body = "The account is removed on every device. Archive it instead to keep its history.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                vm.deleteAccount(existing.id)
                confirmDelete = false
                vm.edit(null)
                toast.show("Account deleted", "Undo") { vm.saveAccount(existing) }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun LedgerDialog(existing: LedgerEntry?, vm: NetWorthViewModel) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(existing?.amount?.let { kotlin.math.abs(it) }) }
    var owedToMe by rememberSaveable { mutableStateOf((existing?.amount ?: 1.0) >= 0) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var settled by rememberSaveable { mutableStateOf(existing?.settled ?: false) }
    val toast = LocalToast.current
    val c = Budget.colors
    BudgetDialog(
        onDismiss = { vm.edit(null) },
        title = if (existing == null) "New IOU" else "Edit IOU",
        actions = {
            if (existing != null) {
                BudgetButton("Delete", {
                    vm.deleteLedger(existing.id)
                    vm.edit(null)
                    toast.show("IOU deleted", "Undo") { vm.restoreLedger(existing) }
                }, kind = ButtonKind.Danger)
            }
            Spacer(Modifier.weight(1f))
            BudgetButton("Cancel", { vm.edit(null) })
            BudgetButton("Save", {
                val a = amount ?: 0.0
                vm.saveLedger(
                    LedgerEntry(
                        id = existing?.id.orEmpty(), name = name, amount = if (owedToMe) a else -a,
                        note = note.ifBlank { null }, settled = settled, sortOrder = existing?.sortOrder ?: 0,
                    ),
                )
                vm.edit(null)
            }, kind = ButtonKind.Primary, enabled = name.isNotBlank())
        },
    ) {
        BudgetTextField(name, { name = it }, label = "Who / what", placeholder = "Alex (concert tickets)")
        Spacer(Modifier.height(12.dp))
        PickRow(listOf(true to "Owed to me (+)", false to "I owe (−)"), owedToMe, { owedToMe = it })
        Spacer(Modifier.height(12.dp))
        MoneyField(amount, { amount = it }, label = "Amount", textAlign = androidx.compose.ui.text.style.TextAlign.Start)
        Spacer(Modifier.height(12.dp))
        BudgetTextField(note, { note = it }, label = "Note", placeholder = "Optional")
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Settled", style = Budget.type.body, color = c.ink, modifier = Modifier.weight(1f))
            BudgetSwitch(settled, { settled = it })
        }
    }
}
