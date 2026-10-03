package com.personal.budget.ui.screens.sheet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.personal.budget.ui.components.BudgetButton
import com.personal.budget.ui.components.BudgetCard
import com.personal.budget.ui.components.ButtonKind
import com.personal.budget.ui.components.Lucide
import com.personal.budget.ui.theme.Budget
import com.personal.budget.ui.theme.LocalReduceMotion
import com.personal.budget.ui.theme.Motion

/**
 * Cover-screen stand-in for the Sheet (docs/SHEET_VIEW.md): the sheet needs ≥ 600dp, so the folded
 * phone shows this card. The illustration "opens" once on appear (skipped with Remove animations).
 */
@Composable
fun UnfoldPrompt(onGoToYear: () -> Unit, modifier: Modifier = Modifier) {
    val c = Budget.colors
    BudgetCard(modifier.widthIn(max = 440.dp).fillMaxWidth(), padding = PaddingValues(24.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            UnfoldIllustration()
            Spacer(Modifier.height(18.dp))
            Text("Unfold to see the spreadsheet", style = Budget.type.title, color = c.ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "The sheet view needs the inner screen. Open your phone and it will appear here.",
                style = Budget.type.body,
                color = c.ink2,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            BudgetButton("Go to Year", onGoToYear, kind = ButtonKind.Secondary, icon = Lucide.ChartColumn)
        }
    }
}

@Composable
private fun UnfoldIllustration() {
    val c = Budget.colors
    val reduce = LocalReduceMotion.current
    val open = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduce) return@LaunchedEffect
        kotlinx.coroutines.delay(250)
        open.animateTo(1f, tween(1100, easing = Motion.Spring))
    }
    Canvas(
        Modifier
            .size(150.dp, 104.dp)
            .padding(4.dp)
            .semantics { contentDescription = "A phone unfolding into a wide inner screen" },
    ) {
        val p = open.value.coerceIn(0f, 1.05f)
        val halfW = size.width * .42f
        val h = size.height * .9f
        val top = (size.height - h) / 2
        val hinge = size.width / 2
        val r = CornerRadius(12.dp.toPx())
        val stroke = Stroke(2.dp.toPx())
        // Left half: fixed.
        drawRoundRect(c.accentSoft, Offset(hinge - halfW, top), Size(halfW, h), r)
        drawRoundRect(c.accent, Offset(hinge - halfW, top), Size(halfW, h), r, style = stroke)
        // Right half: swings open from folded (on top of the left half) to flat.
        val w = halfW * p.coerceAtMost(1f)
        val folded = halfW * (1f - p.coerceAtMost(1f))
        val x = hinge - folded
        drawRoundRect(c.card, Offset(x, top), Size(maxOf(w, folded).coerceAtLeast(2f), h), r)
        drawRoundRect(c.accent, Offset(x, top), Size(maxOf(w, folded).coerceAtLeast(2f), h), r, style = stroke)
        // Grid hint on the opened screen.
        if (p > .6f) {
            val a = ((p - .6f) / .4f).coerceIn(0f, 1f)
            val grid = c.line2.copy(alpha = a)
            for (i in 1..3) {
                val y = top + h * i / 4f
                drawLine(grid, Offset(hinge - halfW + 8.dp.toPx(), y), Offset(hinge + halfW - 8.dp.toPx(), y), 1.dp.toPx())
            }
            for (i in 1..3) {
                val gx = hinge - halfW + 2 * halfW * i / 4f
                drawLine(grid, Offset(gx, top + 8.dp.toPx()), Offset(gx, top + h - 8.dp.toPx()), 1.dp.toPx())
            }
        }
        // Hinge.
        drawLine(c.line2, Offset(hinge, top + 6.dp.toPx()), Offset(hinge, top + h - 6.dp.toPx()), 1.5.dp.toPx())
    }
}
