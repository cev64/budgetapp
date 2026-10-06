package com.personal.budget.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.personal.budget.domain.model.Category
import com.personal.budget.domain.model.CategoryKind
import com.personal.budget.domain.model.MonthKey
import com.personal.budget.domain.model.Tracking
import com.personal.budget.domain.usecase.BudgetBook
import com.personal.budget.ui.components.AppIcon
import com.personal.budget.ui.components.BudgetButton
import com.personal.budget.ui.components.BudgetDialog
import com.personal.budget.ui.components.ButtonKind
import com.personal.budget.ui.components.Dot
import com.personal.budget.ui.components.GhostIconButton
import com.personal.budget.ui.components.Lucide
import com.personal.budget.ui.components.MicroLabel
import com.personal.budget.ui.components.softShadow
import com.personal.budget.ui.components.tappable
import com.personal.budget.ui.theme.Budget
import com.personal.budget.ui.theme.BudgetColors
import com.personal.budget.ui.theme.CategoryAssignment
import com.personal.budget.ui.theme.CategoryPalette
import com.personal.budget.ui.theme.PaletteEntry
import kotlin.math.abs

/** Window layout classes (docs/UI_ANATOMY.md): by window width, never by device model. */
enum class WindowLayout(val panes: Int) {
    /** < 600dp: Fold cover screen, phones, narrow split screen. */
    Compact(1),
    /** 600–1023dp: Fold inner screen, tablets portrait. */
    Medium(2),
    /** ≥ 1024dp: Fold inner landscape, tablets landscape, desktop windows. */
    Expanded(3),
    ;

    val isCompact get() = this == Compact
    val gutter: Dp get() = if (this == Compact) 16.dp else 24.dp

    companion object {
        fun fromWidth(widthDp: Float) = when {
            widthDp < 600f -> Compact
            widthDp < 1024f -> Medium
            else -> Expanded
        }
    }
}

val LocalWindowLayout = staticCompositionLocalOf { WindowLayout.Compact }

/** Content max width 1240dp. */
val ContentMaxWidth = 1240.dp

/** Difference colours (DOMAIN_RULES §8). Leftover follows income. Zero is neutral. */
fun diffColor(kind: CategoryKind, diff: Double, c: BudgetColors): Color = when {
    abs(diff) < 0.005 -> c.ink3
    kind == CategoryKind.EXPENSE -> if (diff > 0) c.bad else c.good
    else -> if (diff > 0) c.good else c.bad
}

/**
 * Palette entry for a category (tokens.json v2): `categories.color` holding a palette id wins;
 * else the default assignment by name; else the first palette id not used in its group
 * (income / expenses / savings), cycling. Stable across months.
 */
fun paletteFor(category: Category?, book: BudgetBook?): PaletteEntry {
    if (category == null) return CategoryPalette.last()
    fun byId(id: String?) = id?.let { want -> CategoryPalette.firstOrNull { it.id.equals(want.trim(), ignoreCase = true) } }
    byId(category.color)?.let { return it }
    byId(CategoryAssignment[category.name.trim().lowercase()])?.let { return it }
    val group = book?.categories?.filter { it.kind == category.kind }.orEmpty()
    val used = group.filter { it.id != category.id }.mapNotNull { other ->
        byId(other.color) ?: byId(CategoryAssignment[other.name.trim().lowercase()])
    }.map { it.id }.toMutableSet()
    // Unassigned categories earlier in the group take palette slots first.
    val unassigned = group.filter { byId(it.color) == null && byId(CategoryAssignment[it.name.trim().lowercase()]) == null }
    val position = unassigned.indexOfFirst { it.id == category.id }.coerceAtLeast(0)
    val free = CategoryPalette.filter { it.id !in used }.ifEmpty { CategoryPalette }
    return free[position % free.size]
}

fun categoryColor(category: Category?, book: BudgetBook?): Color = paletteFor(category, book).color

fun kindLabel(kind: CategoryKind) = when (kind) {
    CategoryKind.INCOME -> "Income"
    CategoryKind.EXPENSE -> "Expenses"
    CategoryKind.SAVINGS -> "Savings"
}

@Composable
fun TrackingIcon(category: Category, modifier: Modifier = Modifier) {
    AppIcon(
        if (category.tracking == Tracking.LEDGER) Lucide.List else Lucide.Pencil,
        if (category.tracking == Tracking.LEDGER) "Ledger category" else "Manual category",
        tint = Budget.colors.ink3,
        size = 13.dp,
        modifier = modifier,
    )
}

/** "Oct 3" for a yyyy-MM-dd date; blank for null. */
fun shortDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching {
        val d = java.time.LocalDate.parse(iso)
        MonthKey.MONTH_NAMES[d.monthValue - 1].take(3) + " " + d.dayOfMonth
    }.getOrDefault(iso)
}

/** `‹ October 2026 ›` with a tappable label that opens the picker. */
@Composable
fun MonthStepper(
    month: MonthKey,
    onChange: (MonthKey) -> Unit,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        GhostIconButton(Lucide.ChevronLeft, "Previous month", onClick = { onChange(month.previous()) })
        GhostIconButton(Lucide.ChevronRight, "Next month", onClick = { onChange(month.next()) })
        GhostIconButton(Lucide.CalendarDays, "Pick month", onClick = onOpenPicker)
    }
}

/**
 * Year + 12-month grid. Existing months are solid; months not started show a "+" and create on
 * pick ([onCreate]); the current selection is accent.
 */
@Composable
fun MonthPickerDialog(
    selected: MonthKey,
    book: BudgetBook,
    onPick: (MonthKey) -> Unit,
    onCreate: (MonthKey) -> Unit,
    onDismiss: () -> Unit,
) {
    var year by remember { mutableIntStateOf(selected.year) }
    val c = Budget.colors
    val today = MonthKey.now()
    BudgetDialog(
        onDismiss = onDismiss,
        title = "Choose month",
        actions = { BudgetButton("Close", onDismiss, kind = ButtonKind.Secondary) },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GhostIconButton(Lucide.ChevronLeft, "Previous year", onClick = { year-- })
            Text(year.toString(), style = Budget.type.screenTitle, color = c.ink, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            GhostIconButton(Lucide.ChevronRight, "Next year", onClick = { year++ })
        }
        Spacer(Modifier.height(10.dp))
        (0 until 4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0 until 3).forEach { col ->
                    val m = MonthKey(year, row * 3 + col + 1)
                    val exists = book.exists(m)
                    val isSel = m == selected
                    val closed = book.month(m)?.closed == true
                    val tileShape = RoundedCornerShape(16.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(vertical = 3.dp)
                            .then(if (isSel) Modifier.softShadow(tileShape, c.shadow.copy(alpha = if (c.isDark) .4f else .12f), 12.dp, 4.dp, (-2).dp) else Modifier)
                            .tappable(shape = tileShape, label = if (exists) "Open ${m.label}" else "Start ${m.label}") { if (exists) onPick(m) else onCreate(m) }
                            .background(
                                when {
                                    isSel -> c.thumb
                                    exists -> c.fill
                                    else -> Color.Transparent
                                },
                                tileShape,
                            )
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            m.shortName,
                            style = if (isSel) Budget.type.bodyStrong.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) else Budget.type.bodyStrong,
                            color = if (exists) c.ink else c.ink3,
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when {
                                !exists -> AppIcon(Lucide.Plus, null, tint = c.ink3, size = 11.dp)
                                closed -> AppIcon(Lucide.Lock, null, tint = c.ink3, size = 11.dp)
                                else -> Dot(if (m == today) c.accent else c.line2, size = 6.dp)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        MicroLabel("+ = not started yet · tap to create")
    }
}

/** A metric tile: micro-label, value, and a small line under it. */
@Composable
fun Tile(
    label: String,
    modifier: Modifier = Modifier,
    wrapLabel: Boolean = false,
    /** Small trailing visual on the label row (e.g. a sparkline), so the value keeps the full width. */
    labelTrailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    com.personal.budget.ui.components.BudgetCard(modifier, padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        if (wrapLabel) {
            Text(label.uppercase(), style = Budget.type.micro, color = Budget.colors.ink3, maxLines = 2)
            Spacer(Modifier.weight(1f))
        } else if (labelTrailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MicroLabel(label, Modifier.weight(1f))
                labelTrailing()
            }
        } else {
            MicroLabel(label)
        }
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
fun ColumnHeaders(labels: List<String>, modifier: Modifier = Modifier, firstWeight: Float = 1f, colWidth: Dp = 76.dp) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(firstWeight))
        labels.forEach { l ->
            Box(Modifier.width(colWidth), contentAlignment = Alignment.CenterEnd) { MicroLabel(l) }
        }
    }
}

@Composable
fun Square(size: Dp, color: Color) {
    Box(Modifier.size(size).aspectRatio(1f).background(color, RoundedCornerShape(2.dp)))
}
