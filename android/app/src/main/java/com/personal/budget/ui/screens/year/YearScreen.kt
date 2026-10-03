package com.personal.budget.ui.screens.year

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.heightIn
import com.personal.budget.ui.components.BudgetProgress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.budget.AppContainer
import com.personal.budget.domain.model.CategoryKind
import com.personal.budget.domain.model.MonthKey
import com.personal.budget.domain.usecase.Money
import com.personal.budget.domain.usecase.Percent
import com.personal.budget.domain.usecase.YearColumn
import com.personal.budget.domain.usecase.YearSummary
import com.personal.budget.ui.MainViewModel
import com.personal.budget.ui.appViewModel
import com.personal.budget.ui.components.isScrolled
import com.personal.budget.ui.components.AnimatedMoney
import com.personal.budget.ui.components.AppIcon
import com.personal.budget.ui.components.BudgetCard
import com.personal.budget.ui.components.CardHeader
import com.personal.budget.ui.components.EmptyState
import com.personal.budget.ui.components.GhostIconButton
import com.personal.budget.ui.components.GlassTopBar
import com.personal.budget.ui.components.Hairline
import com.personal.budget.ui.components.Lucide
import com.personal.budget.ui.components.MicroLabel
import com.personal.budget.ui.components.ScreenFrame
import com.personal.budget.ui.components.tappable
import com.personal.budget.ui.screens.LocalWindowLayout
import com.personal.budget.ui.screens.Square
import com.personal.budget.ui.screens.diffColor
import com.personal.budget.ui.screens.kindLabel
import com.personal.budget.ui.theme.Budget
import com.personal.budget.ui.theme.LocalReduceMotion
import com.personal.budget.ui.theme.Motion
import kotlinx.coroutines.flow.StateFlow

class YearViewModel(@Suppress("unused") c: AppContainer, private val handle: SavedStateHandle) : ViewModel() {
    /** 0 = follow the selected month's year. */
    val year: StateFlow<Int> = handle.getStateFlow(KEY_YEAR, 0)

    fun setYear(y: Int) {
        handle[KEY_YEAR] = y
    }

    companion object {
        private const val KEY_YEAR = "year"
    }
}

/**
 * Year summary (DOMAIN_RULES §4): tables grouped like the sheet, totals, annualized savings and
 * % of net / gross, a per-month chart and the months list. Medium/expanded: tables and chart
 * side by side.
 */
@Composable
fun YearScreen(main: MainViewModel, onOpenMonth: (MonthKey) -> Unit) {
    val vm = appViewModel { c, h -> YearViewModel(c, h) }
    val snapshot by main.snapshot.collectAsStateWithLifecycle()
    val selectedMonth by main.selectedMonth.collectAsStateWithLifecycle()
    val storedYear by vm.year.collectAsStateWithLifecycle()
    val year = if (storedYear > 0) storedYear else selectedMonth.year
    val layout = LocalWindowLayout.current
    val s = snapshot ?: return
    val summary = remember(s, year) { s.book.yearSummary(year, s.settings) }
    val leftScroll = rememberScrollState()

    ScreenFrame(topBar = {
        GlassTopBar(
            micro = "Year",
            title = year.toString(),
            scrolled = leftScroll.isScrolled(),
            actions = {
                GhostIconButton(Lucide.ChevronLeft, "Previous year", onClick = { vm.setYear(year - 1) })
                GhostIconButton(Lucide.ChevronRight, "Next year", onClick = { vm.setYear(year + 1) })
            },
        )
    }) { padding ->
        if (summary == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(layout.gutter)) {
                BudgetCard { EmptyState("No months in $year", "Start a month in $year to see its summary.") }
            }
            return@ScreenFrame
        }
        if (layout.isCompact) {
            Column(
                Modifier.fillMaxSize().verticalScroll(leftScroll).padding(padding).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LeftoverVsPlanHero(summary)
                Tables(summary, colW = 84.dp)
                SavingsRateCard(summary)
                ChartCard(summary, chartHeight = 190.dp)
                MonthsCard(summary, onOpenMonth)
            }
        } else {
            // Expanded: the hero spans the full width above the table / chart panes.
            Column(
                Modifier.fillMaxSize().verticalScroll(leftScroll).padding(padding).padding(horizontal = layout.gutter, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LeftoverVsPlanHero(summary)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.weight(0.6f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Tables(summary, colW = 84.dp)
                        SavingsRateCard(summary)
                    }
                    Column(Modifier.weight(0.4f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ChartCard(summary, chartHeight = 260.dp)
                        MonthsCard(summary, onOpenMonth)
                    }
                }
            }
        }
    }
}

/**
 * DOMAIN_RULES §4b / UI_ANATOMY "Year": the headline is how far the projected leftover is ahead of
 * or behind the plan, with projected / planned rows, a progress bar and one chip per month.
 */
@Composable
private fun LeftoverVsPlanHero(y: YearSummary) {
    val c = Budget.colors
    val vs = y.leftoverVsPlan
    val onPlan = kotlin.math.abs(vs) < 0.005
    val tone = when {
        onPlan -> c.ink
        vs > 0 -> c.good
        else -> c.bad
    }
    val caption = when {
        onPlan -> "on plan"
        vs > 0 -> "ahead of plan"
        else -> "behind plan"
    }
    val projected = y.actual.totals.leftover
    val planned = y.expected.totals.leftover
    BudgetCard(padding = PaddingValues(18.dp)) {
        MicroLabel("Leftover vs plan · ${y.year}")
        Spacer(Modifier.height(4.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            itemVerticalAlignment = Alignment.Bottom,
        ) {
            AnimatedMoney(vs, style = Budget.type.hero, color = tone, format = { Money.formatSigned(it) })
            Text(caption, style = Budget.type.body, color = c.ink2, modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(10.dp))
        listOf("Projected" to projected, "Planned" to planned).forEach { (label, v) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = Budget.type.body, color = c.ink2, modifier = Modifier.weight(1f))
                AnimatedMoney(v, style = Budget.type.tableNumber, color = if (v < 0) c.bad else c.ink, textAlign = TextAlign.End)
            }
        }
        y.leftoverProgress?.let { p ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BudgetProgress(
                    // Capped at 100 % (a full good bar); the percent label carries the rest.
                    actual = projected.coerceIn(0.0, planned),
                    expected = planned,
                    height = 8.dp,
                    color = if (p >= 1.0) c.good else c.accent,
                    overColor = c.good,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Text(com.personal.budget.domain.usecase.Percent.format(p).replace(".0%", "%"), style = Budget.type.secondary, color = c.ink2)
            }
        }
        Spacer(Modifier.height(12.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            y.months.forEach { m ->
                val text: String
                val ink: androidx.compose.ui.graphics.Color
                if (m.closed) {
                    val d = m.vsPlan
                    text = "${m.key.shortName} ${Money.formatSigned(d)}"
                    ink = when {
                        kotlin.math.abs(d) < 0.005 -> c.ink2
                        d > 0 -> c.good
                        else -> c.bad
                    }
                } else {
                    text = "${m.key.shortName} · open"
                    ink = c.ink3
                }
                Text(
                    text,
                    style = Budget.type.secondary.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                    color = ink,
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(99.dp))
                        .background(c.surface2)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SavingsRateCard(y: YearSummary) {
    val c = Budget.colors
    BudgetCard(padding = PaddingValues(16.dp)) {
        CardHeader("Savings rate")
        MicroLabel("Annualized savings · ${y.monthCount} month${if (y.monthCount == 1) "" else "s"}")
        Spacer(Modifier.height(8.dp))
        // Side by side when there's room for two 32sp totals; otherwise stacked (never shrunk).
        androidx.compose.foundation.layout.BoxWithConstraints {
            if (maxWidth >= 300.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    HighlightColumn("Expected", y.expected, Modifier.weight(1f), c.ink2)
                    HighlightColumn("Actual", y.actual, Modifier.weight(1f), c.ink)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HighlightColumn("Actual", y.actual, Modifier.fillMaxWidth(), c.ink)
                    HighlightColumn("Expected", y.expected, Modifier.fillMaxWidth(), c.ink2)
                }
            }
        }
    }
}

@Composable
private fun HighlightColumn(label: String, col: YearColumn, modifier: Modifier, color: Color) {
    val c = Budget.colors
    Column(modifier) {
        Text(label, style = Budget.type.secondary, color = c.ink3)
        AnimatedMoney(col.annualizedSavings, style = Budget.type.number, color = color)
        Spacer(Modifier.height(2.dp))
        Text("${Percent.format(col.pctNetIncome)} of net", style = Budget.type.small, color = c.ink2)
        Text("${Percent.format(col.pctGrossIncome)} of gross", style = Budget.type.small, color = c.ink2)
    }
}

@Composable
private fun Tables(y: YearSummary, colW: Dp) {
    val c = Budget.colors
    listOf(CategoryKind.INCOME, CategoryKind.EXPENSE, CategoryKind.SAVINGS).forEach { kind ->
        val cats = y.categories.filter { it.kind == kind }
        if (cats.isEmpty()) return@forEach
        BudgetCard(padding = PaddingValues(vertical = 10.dp)) {
            HeaderRow(kindLabel(kind), colW)
            cats.forEach { cat ->
                val e = y.expected.perCategory[cat.id] ?: 0.0
                val a = y.actual.perCategory[cat.id] ?: 0.0
                ValueRow(cat.name, e, a, kind, colW)
            }
        }
    }
    BudgetCard(padding = PaddingValues(vertical = 10.dp)) {
        HeaderRow("Totals", colW)
        ValueRow("Income", y.expected.totals.income, y.actual.totals.income, CategoryKind.INCOME, colW)
        ValueRow("Expenses", y.expected.totals.expenses, y.actual.totals.expenses, CategoryKind.EXPENSE, colW)
        ValueRow("Saved (incl. match)", y.expected.totals.saved, y.actual.totals.saved, CategoryKind.SAVINGS, colW)
        Hairline()
        ValueRow("Leftover", y.expected.totals.leftover, y.actual.totals.leftover, CategoryKind.INCOME, colW, bold = true)
        Text(
            "Actual = closed months' actuals + open months' budgets (projected).",
            style = Budget.type.small,
            color = c.ink3,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun HeaderRow(title: String, colW: Dp) {
    val compact = LocalWindowLayout.current.isCompact
    Row(Modifier.padding(horizontal = 14.dp).padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Budget.type.cardTitle, color = Budget.colors.ink, modifier = Modifier.weight(1f))
        if (compact) {
            MicroLabel("Actual")
        } else {
            listOf("Expected", "Actual", "Diff").forEach { Box(Modifier.width(colW), contentAlignment = Alignment.CenterEnd) { MicroLabel(it) } }
        }
    }
}

/** Compact: two lines (label + actual, then "of expected" + diff) so text never shrinks. */
@Composable
private fun ValueRow(label: String, expected: Double, actual: Double, kind: CategoryKind, colW: Dp, bold: Boolean = false) {
    val c = Budget.colors
    val compact = LocalWindowLayout.current.isCompact
    val num = Budget.type.tableNumber
    val style = if (bold) num.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) else num
    val d = actual - expected
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = if (bold) Budget.type.cardTitle else Budget.type.body, color = c.ink, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (compact) {
                AnimatedMoney(actual, style, c.ink, textAlign = TextAlign.End)
            } else {
                Box(Modifier.width(colW), contentAlignment = Alignment.CenterEnd) { AnimatedMoney(expected, style, c.ink2, textAlign = TextAlign.End) }
                Box(Modifier.width(colW), contentAlignment = Alignment.CenterEnd) { AnimatedMoney(actual, style, c.ink, textAlign = TextAlign.End) }
                Box(Modifier.width(colW), contentAlignment = Alignment.CenterEnd) {
                    AnimatedMoney(d, style, diffColor(kind, d, c), format = { Money.formatSigned(it) }, textAlign = TextAlign.End)
                }
            }
        }
        if (compact) {
            Row(Modifier.padding(top = 2.dp)) {
                Text("of ${Money.format(expected)}", style = Budget.type.secondary, color = c.ink2, modifier = Modifier.weight(1f))
                AnimatedMoney(d, Budget.type.secondary, diffColor(kind, d, c), format = { Money.formatSigned(it) })
            }
        }
    }
}

/**
 * 12 month slots. Bar = expenses (projected); thin marker = expected expenses; accent line =
 * projected leftover. Open months are drawn at 45 % opacity; missing months are empty.
 */
@Composable
private fun ChartCard(y: YearSummary, chartHeight: Dp) {
    val c = Budget.colors
    val measurer = rememberTextMeasurer()
    val reduce = LocalReduceMotion.current
    val grow = remember(y.year) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(y.year) { grow.animateTo(1f, tween(700, easing = Motion.Ease)) }
    val points = y.months.associateBy { it.key.month }
    val maxV = (y.months.flatMap { listOf(it.projected.expenses, it.expected.expenses, it.projected.leftover) }.maxOrNull() ?: 0.0)
        .coerceAtLeast(1.0) * 1.1
    val minV = (y.months.minOfOrNull { it.projected.leftover } ?: 0.0).coerceAtMost(0.0) * 1.1
    val labelStyle = Budget.type.micro.copy(color = c.ink3)
    BudgetCard {
        CardHeader("Expenses by month")
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .semantics { contentDescription = "Bar chart of monthly expenses against budget, with leftover line" },
        ) {
            val axisH = 18.dp.toPx()
            val leftPad = 34.dp.toPx()
            val plotH = size.height - axisH
            val plotW = size.width - leftPad
            val range = (maxV - minV).coerceAtLeast(1.0)
            fun yOf(v: Double) = (plotH * (1 - (v - minV) / range)).toFloat()
            // Gridlines + labels
            listOf(0.0, maxV / 2, maxV / 1.1).forEach { g ->
                val gy = yOf(g)
                drawLine(c.line, Offset(leftPad, gy), Offset(size.width, gy), 1.dp.toPx(), pathEffect = if (g == 0.0) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                val t = measurer.measure(Money.formatCompact(g), labelStyle)
                drawText(t, topLeft = Offset(0f, gy - t.size.height / 2f))
            }
            val slot = plotW / 12f
            val barW = slot * .56f
            val leftover = Path()
            var started = false
            for (m in 1..12) {
                val x = leftPad + slot * (m - 1) + (slot - barW) / 2
                val p = points[m]
                if (p != null) {
                    val alpha = if (p.closed) 1f else .45f
                    val top = yOf(p.projected.expenses * grow.value)
                    val base = yOf(0.0)
                    drawRoundRect(
                        c.accent.copy(alpha = alpha),
                        topLeft = Offset(x, top),
                        size = Size(barW, (base - top).coerceAtLeast(0f)),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    )
                    val ey = yOf(p.expected.expenses)
                    drawLine(c.ink.copy(alpha = .75f * alpha + .25f), Offset(x - 3.dp.toPx(), ey), Offset(x + barW + 3.dp.toPx(), ey), 2.dp.toPx(), cap = StrokeCap.Round)
                    val lx = x + barW / 2
                    val ly = yOf(p.projected.leftover * grow.value)
                    if (!started) {
                        leftover.moveTo(lx, ly)
                        started = true
                    } else {
                        leftover.lineTo(lx, ly)
                    }
                    drawCircle(c.good, 3.5.dp.toPx(), Offset(lx, ly))
                }
                val t = measurer.measure(MonthKey.MONTH_NAMES[m - 1].take(1), labelStyle)
                drawText(t, topLeft = Offset(leftPad + slot * (m - 1) + slot / 2 - t.size.width / 2f, size.height - t.size.height))
            }
            if (started) drawPath(leftover, c.good, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        Spacer(Modifier.height(10.dp))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LegendItem(c.accent, "Expenses")
            LegendItem(c.accent.copy(alpha = .45f), "Open (projected)")
            LegendItem(c.ink, "Budget")
            LegendItem(c.good, "Leftover")
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Square(9.dp, color)
        Spacer(Modifier.width(4.dp))
        Text(label, style = Budget.type.small, color = Budget.colors.ink2, maxLines = 1)
    }
}

@Composable
private fun MonthsCard(y: YearSummary, onOpenMonth: (MonthKey) -> Unit) {
    val c = Budget.colors
    BudgetCard(padding = PaddingValues(vertical = 8.dp)) {
        CardHeader("Months", Modifier.padding(horizontal = 14.dp))
        y.months.forEach { p ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .tappable(shape = RectangleShape, onClick = { onOpenMonth(p.key) })
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(if (p.closed) Lucide.Lock else Lucide.LockOpen, if (p.closed) "Closed" else "Open", tint = if (p.closed) c.accentInk else c.ink3, size = 15.dp)
                Spacer(Modifier.width(10.dp))
                Text(p.key.name, style = Budget.type.body, color = c.ink, modifier = Modifier.weight(1f))
                Text(if (p.closed) "Closed" else "Open", style = Budget.type.small, color = c.ink3)
                Spacer(Modifier.width(12.dp))
                Text(
                    Money.format(p.projected.leftover),
                    style = Budget.type.bodyStrong,
                    color = if (p.projected.leftover < 0) c.bad else c.ink,
                    modifier = Modifier.width(84.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
