package com.personal.budget.ui.screens.sheet

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.personal.budget.ui.components.CategoryMark
import com.personal.budget.ui.theme.Budget
import com.personal.budget.ui.theme.PaletteEntry

/**
 * Column-width model (dp, at font scale 1). Grid row 0 = sheet row 2; every row is [ROW_H] tall.
 * Widths follow the web sheet. B is measured at runtime from the longest fixed label
 * ("Saved (Roth, 401k, Brokerage)" at 14sp semibold = 209dp, so 225dp with padding); the
 * ledger Item column flexes between [ITEM_MIN] and [ITEM] so whole ledger blocks fit the window
 * (Fold portrait: B–E + G; landscape: B–E + G + K with O peeking in). See [fit].
 */
object SheetCols {
    val ROW_H = 36.dp
    /** Horizontal cell padding (each side). */
    val PAD_H = 8.dp
    /** Expected / Actual / Difference: fits "DIFFERENCE" (81dp at 12sp) and "−$123,456". */
    val NUM = 98.dp
    val SPACER = 12.dp
    /** Fits "Oct 16" (46dp). */
    val DATE = 72.dp
    val ITEM = 140.dp
    val ITEM_MIN = 100.dp
    /** Fits "−$999.99" / "−$1,213" (amounts ≥ $1,000 show whole dollars). */
    val AMOUNT = 88.dp
    /** Summary G/J name: fits "Super Liquid Assets" semibold (139dp). */
    val NAME = 156.dp
    /** Fits "$78,500" plus the "auto" marker on linked accounts. */
    val VALUE = 100.dp
    /** Fallback B before measuring. */
    val B = 226.dp

    /**
     * Columns for a grid [available] wide. [b] is the measured label column; [scale] is the font
     * scale (≥ 1), applied to every fixed width so larger text never clips. Item takes what is left
     * after fitting as many whole ledger blocks as possible (at least one), within [ITEM_MIN]..[ITEM].
     */
    fun fit(b: Dp, available: Dp, scale: Float = 1f): SheetColumns {
        val s = scale.coerceAtLeast(1f)
        val num = NUM * s
        val date = DATE * s
        val amount = AMOUNT * s
        val minItem = ITEM_MIN * s
        val maxItem = ITEM * s
        val left = b + num * 3
        val perBlock = SPACER + date + amount
        var n = 1
        while (n < 3 && left + (perBlock + minItem) * (n + 1) <= available) n++
        val item = ((available - left) / n - perBlock).coerceIn(minItem, maxItem)
        return SheetColumns(
            b = b, num = num, spacer = SPACER, date = date, item = Dp(kotlin.math.floor(item.value)), amount = amount,
            name = NAME * s, value = VALUE * s,
        )
    }
}

@Immutable
data class SheetColumns(
    val b: Dp,
    val num: Dp,
    val spacer: Dp,
    val date: Dp,
    val item: Dp,
    val amount: Dp,
    val name: Dp,
    val value: Dp,
) {
    /** The pinned B–E block. */
    val left: Dp get() = b + num * 3
    /** One ledger block (Date · Item · Amount). */
    val block: Dp get() = date + item + amount
}

val LocalSheetCols = staticCompositionLocalOf { SheetCols.fit(SheetCols.B, 1200.dp) }

/** Measures the B column from [labels] (semibold 14sp, the widest style used there) and fits the rest. */
@Composable
fun rememberSheetColumns(available: Dp, labels: List<String>): SheetColumns {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = Budget.type.secondary.copy(fontWeight = FontWeight.SemiBold)
    return remember(available, labels, density, style) {
        val widest = labels.maxOfOrNull { measurer.measure(it, style, maxLines = 1).size.width } ?: 0
        val b = with(density) { (widest.toDp() + SheetCols.PAD_H * 2).value }
        val measuredB = Dp(kotlin.math.ceil(b) + 1f).coerceIn(160.dp, 300.dp)
        SheetCols.fit(measuredB, available, density.fontScale)
    }
}

enum class CellStyle { Header, Editable, Computed, Title, Label, TotalLabel, Total, Blank }

/** A menu entry on long-press. */
data class CellAction(val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

/** How an editable cell edits in place. */
data class EditSpec(
    val id: String,
    val initial: String,
    val numeric: Boolean,
    val commit: (String) -> Unit,
)

/** Editing state shared by the grid: which cell is open, and the order Next/Tab/Enter walks. */
class SheetEditing {
    var editingId by mutableStateOf<String?>(null)
    var order: List<String> = emptyList()

    fun next(from: String, backwards: Boolean = false) {
        val i = order.indexOf(from)
        editingId = if (i < 0) null else order.getOrNull(if (backwards) i - 1 else i + 1)
    }
}

/**
 * One grid cell. Editable cells sit on `bg` with ink text and open an inline editor on tap;
 * computed cells use `surface` + ink-2. Grid rules are 1px `line`; totals get a 2px `line2` top rule.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SheetCell(
    width: Dp,
    text: String = "",
    style: CellStyle = CellStyle.Computed,
    align: TextAlign = TextAlign.Start,
    color: Color? = null,
    mark: PaletteEntry? = null,
    marker: String? = null,
    edit: EditSpec? = null,
    editing: SheetEditing? = null,
    onTap: (() -> Unit)? = null,
    actions: List<CellAction> = emptyList(),
    onCalculatedHint: (() -> Unit)? = null,
    description: String? = null,
    /** Shown, unclipped, at the top of the long-press menu (e.g. the full Item text). */
    menuTitle: String? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val c = Budget.colors
    if (style == CellStyle.Blank) {
        Spacer(Modifier.width(width).height(SheetCols.ROW_H))
        return
    }
    val bg = when (style) {
        CellStyle.Header -> c.surface
        CellStyle.Computed, CellStyle.Total -> c.surface
        else -> c.bg
    }
    val textStyle = when (style) {
        CellStyle.Header -> Budget.type.micro
        CellStyle.Title -> Budget.type.cardTitle.copy(fontSize = Budget.type.secondary.fontSize * 15f / 14f)
        CellStyle.TotalLabel, CellStyle.Total -> Budget.type.secondary.copy(fontWeight = FontWeight.SemiBold)
        else -> Budget.type.secondary
    }
    val ink = color ?: when (style) {
        CellStyle.Header -> c.ink3
        CellStyle.Computed -> c.ink2
        else -> c.ink
    }
    val isEditing = edit != null && editing?.editingId == edit.id
    var menuOpen by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val tap: (() -> Unit)? = when {
        edit != null && editing != null -> ({ editing.editingId = edit.id })
        else -> onTap
    }
    val longPress: (() -> Unit)? = when {
        actions.isNotEmpty() -> ({ menuOpen = true })
        style == CellStyle.Computed || style == CellStyle.Total -> onCalculatedHint
        else -> null
    }
    val topRule = style == CellStyle.Total || style == CellStyle.TotalLabel
    Box(
        Modifier
            .width(width)
            .height(SheetCols.ROW_H)
            .background(bg)
            .drawBehind {
                val px = 1.dp.toPx()
                drawLine(c.line, Offset(size.width - px / 2, 0f), Offset(size.width - px / 2, size.height), px)
                drawLine(c.line, Offset(0f, size.height - px / 2), Offset(size.width, size.height - px / 2), px)
                if (topRule) drawLine(c.line2, Offset(0f, 1.dp.toPx()), Offset(size.width, 1.dp.toPx()), 2.dp.toPx())
                if (isEditing) {
                    val f = 2.dp.toPx()
                    drawRect(c.focusRing, style = androidx.compose.ui.graphics.drawscope.Stroke(f))
                }
            }
            .then(
                if (tap != null || longPress != null) {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        role = if (edit != null) Role.Button else null,
                        onClickLabel = if (edit != null) "Edit" else null,
                        onLongClickLabel = if (actions.isNotEmpty()) "More actions" else null,
                        onLongClick = longPress,
                        onClick = tap ?: {},
                    )
                } else {
                    Modifier
                },
            )
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = SheetCols.PAD_H, vertical = 6.dp),
        contentAlignment = when (align) {
            TextAlign.End -> Alignment.CenterEnd
            TextAlign.Center -> Alignment.Center
            else -> Alignment.CenterStart
        },
    ) {
        when {
            isEditing -> InlineEditor(edit!!, editing!!, textStyle.copy(color = c.ink, textAlign = align))
            content != null -> content()
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                if (mark != null) {
                    CategoryMark(mark, size = 12.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (style == CellStyle.Header) text.uppercase() else text,
                    style = textStyle,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = align,
                    modifier = if (align == TextAlign.End) Modifier.weight(1f, fill = false) else Modifier,
                )
                if (marker != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(marker, style = Budget.type.micro.copy(fontSize = Budget.type.micro.fontSize * .85f), color = c.accentInk)
                }
            }
        }
        if (actions.isNotEmpty()) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = if (c.isDark) c.surface else Color.White) {
                if (!menuTitle.isNullOrBlank()) {
                    Text(
                        menuTitle,
                        style = Budget.type.secondary,
                        color = c.ink2,
                        modifier = Modifier.widthIn(max = 280.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                actions.forEach { a ->
                    DropdownMenuItem(
                        text = { Text(a.label, style = Budget.type.body, color = if (a.destructive) c.bad else c.ink) },
                        onClick = {
                            menuOpen = false
                            a.onClick()
                        },
                    )
                }
            }
        }
    }
}

/** In-place editor: numeric keyboard for money; Enter/Next commits and moves down, Tab right, Escape cancels. */
@Composable
private fun InlineEditor(spec: EditSpec, editing: SheetEditing, style: androidx.compose.ui.text.TextStyle) {
    val c = Budget.colors
    val focus = remember { FocusRequester() }
    var value by remember(spec.id) { mutableStateOf(TextFieldValue(spec.initial, TextRange(0, spec.initial.length))) }
    var done by remember(spec.id) { mutableStateOf(false) }
    fun commit(move: Int) {
        if (done) return
        done = true
        if (value.text != spec.initial) spec.commit(value.text)
        when (move) {
            1 -> editing.next(spec.id)
            -1 -> editing.next(spec.id, backwards = true)
            else -> if (editing.editingId == spec.id) editing.editingId = null
        }
    }
    LaunchedEffect(spec.id) { runCatching { focus.requestFocus() } }
    BasicTextField(
        value = value,
        onValueChange = { v ->
            value = if (spec.numeric) v.copy(text = v.text.filter { it.isDigit() || it in ".,-−" }) else v
        },
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(keyboardType = if (spec.numeric) KeyboardType.Decimal else KeyboardType.Text, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { commit(1) }, onDone = { commit(0) }),
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .onFocusChanged { if (!it.isFocused && !done && editing.editingId == spec.id) commit(0) }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.Enter, Key.NumPadEnter -> { commit(1); true }
                    Key.Tab -> { commit(if (e.isShiftPressed) -1 else 1); true }
                    Key.Escape -> {
                        done = true
                        editing.editingId = null
                        true
                    }
                    else -> false
                }
            },
    )
}

/** Small "ƒ" helper so callers can show the "Calculated" hint consistently. */
@Composable
fun CalculatedGlyph() {
    Text("ƒ", style = Budget.type.micro, color = Budget.colors.ink3, modifier = Modifier.size(10.dp))
}
