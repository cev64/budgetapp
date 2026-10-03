package com.personal.budget.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.personal.budget.domain.model.MonthKey
import com.personal.budget.domain.usecase.HistoryPoint
import com.personal.budget.domain.usecase.Money
import com.personal.budget.ui.theme.Budget
import com.personal.budget.ui.theme.LocalReduceMotion
import com.personal.budget.ui.theme.Motion
import java.time.LocalDate

fun shortDay(d: LocalDate): String = MonthKey.MONTH_NAMES[d.monthValue - 1].take(3) + " " + d.dayOfMonth
fun longDay(d: LocalDate): String = shortDay(d) + ", " + d.year

private class ChartGeometry(
    val points: List<HistoryPoint>,
    val overlay: List<HistoryPoint>,
    val width: Float,
    val height: Float,
    val topPad: Float,
    val bottomPad: Float,
) {
    private val minDay = points.first().date.toEpochDay()
    private val maxDay = points.last().date.toEpochDay()
    private val values = points.map { it.value } + overlay.map { it.value }
    private val lo = values.min()
    private val hi = values.max()
    private val span = (hi - lo).takeIf { it > 0.0 } ?: maxOf(1.0, kotlin.math.abs(hi) * .1)
    private val vLo = lo - span * .08
    private val vHi = hi + span * .08

    fun x(p: HistoryPoint, index: Int, count: Int): Float =
        if (maxDay == minDay) (if (count <= 1) width / 2 else width * index / (count - 1))
        else ((p.date.toEpochDay() - minDay).toFloat() / (maxDay - minDay)) * width

    fun y(v: Double): Float = topPad + ((vHi - v) / (vHi - vLo)).toFloat() * (height - topPad - bottomPad)

    fun path(series: List<HistoryPoint>): Path {
        val path = Path()
        val xs = series.mapIndexed { i, p -> x(p, i, series.size) }
        val ys = series.map { y(it.value) }
        path.moveTo(xs[0], ys[0])
        for (i in 1 until series.size) {
            // Smooth: horizontal-tangent cubic segments (never overshoots between points).
            val mx = (xs[i - 1] + xs[i]) / 2
            path.cubicTo(mx, ys[i - 1], mx, ys[i], xs[i], ys[i])
        }
        return path
    }

    fun nearestIndex(px: Float): Int {
        var best = 0
        var bd = Float.MAX_VALUE
        points.forEachIndexed { i, p ->
            val d = kotlin.math.abs(x(p, i, points.size) - px)
            if (d < bd) {
                bd = d
                best = i
            }
        }
        return best
    }
}

/**
 * Net-worth history chart (UI_ANATOMY "Net worth"): smooth accent area line with a 12 % fill
 * fading to 0, a faint baseline, first/last date labels, and a scrub cursor (vertical line-2 rule,
 * dot and a glass tooltip) on touch/drag. [overlay] draws a second, dashed series (Super liquid).
 */
@Composable
fun HistoryChart(
    points: List<HistoryPoint>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    overlay: List<HistoryPoint> = emptyList(),
    overlayLabel: String = "Super liquid",
    lineColor: Color = Budget.colors.accent,
    showDates: Boolean = true,
    animateKey: Any? = null,
) {
    val c = Budget.colors
    val density = LocalDensity.current
    val reduce = LocalReduceMotion.current
    if (points.size < 2) return
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var scrub by remember(points) { mutableStateOf<Int?>(null) }
    val reveal = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(animateKey) {
        if (reduce) return@LaunchedEffect
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(700, easing = Motion.Ease))
    }
    val first = points.first()
    val last = points.last()
    Column(modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(height)) {
            val boxW = maxWidth
            Canvas(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { boxSize = it }
                    .semantics {
                        contentDescription = "Chart from ${longDay(first.date)} (${Money.format(first.value)}) to ${longDay(last.date)} (${Money.format(last.value)})"
                    }
                    .pointerInput(points) {
                        detectTapGestures(
                            onPress = { o ->
                                val g = ChartGeometry(points, overlay, boxSize.width.toFloat(), boxSize.height.toFloat(), 10f, 6f)
                                scrub = g.nearestIndex(o.x)
                                tryAwaitRelease()
                            },
                        )
                    }
                    .pointerInput(points) {
                        detectDragGestures(
                            onDragStart = { o -> scrub = ChartGeometry(points, overlay, boxSize.width.toFloat(), boxSize.height.toFloat(), 10f, 6f).nearestIndex(o.x) },
                            onDragEnd = { },
                            onDragCancel = { },
                        ) { change, _ ->
                            scrub = ChartGeometry(points, overlay, boxSize.width.toFloat(), boxSize.height.toFloat(), 10f, 6f).nearestIndex(change.position.x)
                        }
                    },
            ) {
                val g = ChartGeometry(points, overlay, size.width, size.height, 10.dp.toPx(), 6.dp.toPx())
                // faint baseline
                drawLine(c.line, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1.dp.toPx())
                clipRect(right = size.width * reveal.value) {
                    if (overlay.size >= 2) {
                        drawPath(
                            g.path(overlay),
                            c.good.copy(alpha = .9f),
                            style = Stroke(1.75.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                        )
                    }
                    val line = g.path(points)
                    val area = Path().apply {
                        addPath(line)
                        lineTo(g.x(last, points.lastIndex, points.size), size.height)
                        lineTo(g.x(first, 0, points.size), size.height)
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(lineColor.copy(alpha = .12f), lineColor.copy(alpha = 0f))))
                    drawPath(line, lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                val idx = scrub
                if (idx != null && idx in points.indices) {
                    val p = points[idx]
                    val x = g.x(p, idx, points.size)
                    drawLine(c.line2, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                    drawCircle(c.card, 6.dp.toPx(), Offset(x, g.y(p.value)))
                    drawCircle(lineColor, 4.dp.toPx(), Offset(x, g.y(p.value)))
                } else {
                    drawCircle(lineColor, 3.5.dp.toPx(), Offset(g.x(last, points.lastIndex, points.size), g.y(last.value)))
                }
            }
            val idx = scrub
            if (idx != null && idx in points.indices && boxSize.width > 0) {
                val p = points[idx]
                val g = ChartGeometry(points, overlay, boxSize.width.toFloat(), boxSize.height.toFloat(), with(density) { 10.dp.toPx() }, with(density) { 6.dp.toPx() })
                val xDp = with(density) { g.x(p, idx, points.size).toDp() }
                val tipW = 150.dp
                val left = (xDp - tipW / 2).coerceIn(0.dp, (boxW - tipW).coerceAtLeast(0.dp))
                val ov = overlay.firstOrNull { it.date == p.date }
                Column(
                    Modifier
                        .offset(x = left, y = 0.dp)
                        .widthIn(max = tipW)
                        .shadow(10.dp, RoundedCornerShape(10.dp), ambientColor = c.shadow.copy(alpha = .14f), spotColor = c.shadow.copy(alpha = .18f))
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.glassCard)
                        .border(1.dp, c.shadow.copy(alpha = .08f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(longDay(p.date).uppercase(), style = Budget.type.micro, color = c.ink3)
                    Text(Money.format(p.value), style = Budget.type.bodyStrong, color = c.ink)
                    if (ov != null) Text("$overlayLabel ${Money.format(ov.value)}", style = Budget.type.small, color = c.good)
                }
            }
        }
        if (showDates) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                MicroLabel(shortDay(first.date) + if (first.date.year != last.date.year) " " + first.date.year else "", Modifier.weight(1f))
                MicroLabel(shortDay(last.date))
            }
        }
    }
}

/** Tiny sparkline (Home tile, account rows). Draws nothing with fewer than 2 points. */
@Composable
fun Sparkline(points: List<HistoryPoint>, modifier: Modifier = Modifier, color: Color = Budget.colors.accent) {
    if (points.size < 2) {
        Box(modifier)
        return
    }
    Canvas(modifier) {
        val g = ChartGeometry(points, emptyList(), size.width, size.height, 2.dp.toPx(), 2.dp.toPx())
        val line = g.path(points)
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = .14f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
