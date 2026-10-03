package com.personal.budget.ui.screens.add

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.budget.domain.model.Category
import com.personal.budget.domain.model.MonthKey
import com.personal.budget.domain.model.Tracking
import com.personal.budget.domain.model.Txn
import com.personal.budget.ui.AddDraft
import com.personal.budget.ui.MainViewModel
import com.personal.budget.ui.components.BudgetButton
import com.personal.budget.ui.components.BudgetTextField
import com.personal.budget.ui.components.ButtonKind
import com.personal.budget.ui.components.GhostIconButton
import com.personal.budget.ui.components.LocalToast
import com.personal.budget.ui.components.Lucide
import com.personal.budget.ui.components.MicroLabel
import com.personal.budget.ui.components.Pick
import com.personal.budget.ui.components.tappable
import com.personal.budget.ui.screens.paletteFor
import com.personal.budget.ui.screens.shortDate
import com.personal.budget.ui.theme.Budget
import com.personal.budget.utilities.Haptics
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Add / edit transaction (docs/UI_ANATOMY.md "Add transaction"): Amount (autofocus), Category
 * (ledger categories first), Item, Date (today), Month (the viewed month), Note (collapsed).
 * The whole form lives in MainViewModel's SavedStateHandle, so it survives fold/unfold.
 */
@Composable
fun AddTransactionSheet(main: MainViewModel, inline: Boolean = false) {
    val draft by main.addDraft.collectAsStateWithLifecycle()
    val d = draft ?: return
    val snapshot by main.snapshot.collectAsStateWithLifecycle()
    val book = snapshot?.book
    val c = Budget.colors
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    val view = LocalView.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var showDate by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }

    fun close() {
        scope.launch {
            sheetState.hide()
            main.closeAdd()
            error = null
        }
    }

    fun save() {
        if (saving) return
        saving = true
        scope.launch {
            main.saveDraft(d)
                .onSuccess { msg ->
                    Haptics.confirm(view)
                    toast.show(msg)
                    sheetState.hide()
                    main.closeAdd()
                    error = null
                }
                .onFailure { error = it.message }
            saving = false
        }
    }

    val body: @Composable () -> Unit = {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (d.isEdit) "Edit transaction" else "Add transaction", style = Budget.type.cardTitle, color = c.ink, modifier = Modifier.weight(1f))
                GhostIconButton(Lucide.X, "Close", onClick = { close() })
            }
            Spacer(Modifier.height(6.dp))

            // Amount -------------------------------------------------------------------------
            MicroLabel("Amount")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (d.refund) "−$" else "$", style = Budget.type.hero, color = if (d.refund) c.good else c.ink3)
                BasicTextField(
                    value = d.amount,
                    onValueChange = { s -> main.updateDraft(d.copy(amount = s.filter { it.isDigit() || it == '.' || it == ',' })) },
                    textStyle = Budget.type.hero.copy(color = if (d.refund) c.good else c.ink),
                    cursorBrush = SolidColor(c.accent),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(amountFocus)
                        .semantics { contentDescription = "Amount" },
                    decorationBox = { inner ->
                        Box {
                            if (d.amount.isEmpty()) Text("0", style = Budget.type.hero, color = c.line2)
                            inner()
                        }
                    },
                )
                Pick("Refund", d.refund, { main.updateDraft(d.copy(refund = !d.refund)) }, icon = Lucide.Undo2)
            }
            LaunchedEffect(Unit) {
                if (!d.isEdit) runCatching { amountFocus.requestFocus() }
            }
            Spacer(Modifier.height(14.dp))

            // Category -------------------------------------------------------------------------
            MicroLabel("Category")
            Spacer(Modifier.height(6.dp))
            val cats = book?.categories.orEmpty().filter { !it.archived || it.id == d.categoryId }
            val ordered = cats.filter { it.tracking == Tracking.LEDGER } + cats.filter { it.tracking != Tracking.LEDGER }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ordered.forEach { cat: Category ->
                    Pick(
                        cat.name,
                        selected = cat.id == d.categoryId,
                        onClick = { main.updateDraft(d.copy(categoryId = cat.id)) },
                        mark = paletteFor(cat, book),
                        dimmed = d.categoryId != null,
                    )
                }
            }
            if (ordered.isEmpty()) Text("No categories yet. Add them in Settings.", style = Budget.type.secondary, color = c.ink3)
            Spacer(Modifier.height(14.dp))

            // Item -----------------------------------------------------------------------------
            BudgetTextField(
                value = d.item,
                onValueChange = { main.updateDraft(d.copy(item = it)) },
                label = "Item",
                placeholder = "What was it?",
                imeAction = ImeAction.Done,
            )
            Spacer(Modifier.height(14.dp))

            // Date + month -----------------------------------------------------------------------
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    MicroLabel("Date")
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val label = when {
                            d.date.isNullOrBlank() -> "No date"
                            d.date == LocalDate.now().toString() -> "Today · " + shortDate(d.date)
                            else -> shortDate(d.date)
                        }
                        Pick(label, selected = !d.date.isNullOrBlank(), onClick = { showDate = true }, icon = Lucide.Calendar)
                        if (!d.date.isNullOrBlank()) {
                            GhostIconButton(Lucide.X, "Clear date", iconSize = 20.dp, onClick = { main.updateDraft(d.copy(date = null)) })
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    MicroLabel("Month")
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GhostIconButton(Lucide.ChevronLeft, "Previous month", iconSize = 20.dp, onClick = {
                            val m = d.key.previous()
                            main.updateDraft(d.copy(year = m.year, month = m.month))
                        })
                        Text(d.key.shortName + " " + d.year, style = Budget.type.bodyStrong, color = c.ink)
                        GhostIconButton(Lucide.ChevronRight, "Next month", iconSize = 20.dp, onClick = {
                            val m = d.key.next()
                            main.updateDraft(d.copy(year = m.year, month = m.month))
                        })
                    }
                }
            }
            if (book != null && !book.exists(d.key)) {
                Spacer(Modifier.height(4.dp))
                Text("${d.key.label} hasn't been started. It will be created.", style = Budget.type.small, color = c.ink3)
            }
            Spacer(Modifier.height(12.dp))

            // Note -----------------------------------------------------------------------------
            if (d.noteOpen) {
                BudgetTextField(
                    value = d.note,
                    onValueChange = { main.updateDraft(d.copy(note = it)) },
                    label = "Note",
                    placeholder = "Optional",
                    singleLine = false,
                    minLines = 2,
                    imeAction = ImeAction.Default,
                )
            } else {
                Text(
                    "+ Add note",
                    style = Budget.type.secondary,
                    color = c.accentInk,
                    modifier = Modifier.tappable(onClick = { main.updateDraft(d.copy(noteOpen = true)) }).padding(vertical = 6.dp, horizontal = 4.dp),
                )
            }

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, style = Budget.type.secondary, color = c.bad)
            }
            Spacer(Modifier.height(16.dp))
            BudgetButton(
                if (d.isEdit) "Save" else "Add",
                onClick = { save() },
                kind = ButtonKind.Primary,
                large = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            )
            if (d.isEdit) {
                Spacer(Modifier.height(8.dp))
                BudgetButton(
                    "Delete",
                    onClick = {
                        val id = d.txnId ?: return@BudgetButton
                        val original = snapshot?.transactions?.firstOrNull { it.id == id }
                        scope.launch {
                            main.deleteTransaction(id)
                            sheetState.hide()
                            main.closeAdd()
                            toast.show("Transaction deleted", "Undo") {
                                if (original != null) scope.launch { main.restoreTransaction(original) }
                            }
                        }
                    },
                    kind = ButtonKind.Danger,
                    icon = Lucide.Trash2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (inline) {
        // Same sheet drawn in-window (previews / screenshot tests cannot capture dialog windows).
        Box(Modifier.fillMaxSize().background(c.scrim), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(if (c.isDark) c.surface else Color.White),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(36.dp, 4.dp).background(c.line2, RoundedCornerShape(99.dp)))
                body()
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = { main.closeAdd() },
            sheetState = sheetState,
            containerColor = if (c.isDark) c.surface else Color.White,
            scrimColor = c.scrim,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(36.dp, 4.dp).background(c.line2, RoundedCornerShape(99.dp))) },
        ) {
            body()
        }
    }

    if (showDate) {
        val initial = d.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                BudgetButton("Set date", {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        main.updateDraft(d.copy(date = date.toString()))
                    }
                    showDate = false
                }, kind = ButtonKind.Primary, modifier = Modifier.padding(end = 8.dp, bottom = 8.dp))
            },
            dismissButton = { BudgetButton("Cancel", { showDate = false }, kind = ButtonKind.Ghost, modifier = Modifier.padding(bottom = 8.dp)) },
            colors = DatePickerDefaults.colors(containerColor = c.card),
        ) {
            DatePicker(state = state, colors = DatePickerDefaults.colors(containerColor = c.card, selectedDayContainerColor = c.accent, todayDateBorderColor = c.accent))
        }
    }
}
