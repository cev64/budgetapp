package com.personal.budget.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.personal.budget.ui.theme.CategorySymbol
import com.personal.budget.ui.theme.PaletteEntry

/**
 * The category colour is always paired with its symbol (brand guide "Categories"): ≥12dp, filled
 * shapes or 1.75dp rings in the category colour. Labels next to it stay in ink.
 */
@Composable
fun CategoryMark(entry: PaletteEntry, modifier: Modifier = Modifier, size: Dp = 12.dp, description: String? = null) {
    Canvas(
        modifier
            .size(size)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) { drawSymbol(entry) }
}

fun DrawScope.drawSymbol(entry: PaletteEntry) {
    val s = size.minDimension
    val c = entry.color
    val stroke = Stroke(1.75.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val half = stroke.width / 2
    fun triangle(inset: Float) = Path().apply {
        moveTo(s / 2, inset)
        lineTo(s - inset, s - inset)
        lineTo(inset, s - inset)
        close()
    }
    fun diamond(inset: Float) = Path().apply {
        moveTo(s / 2, inset)
        lineTo(s - inset, s / 2)
        lineTo(s / 2, s - inset)
        lineTo(inset, s / 2)
        close()
    }
    when (entry.symbol) {
        CategorySymbol.CIRCLE -> drawCircle(c, s / 2, Offset(s / 2, s / 2))
        CategorySymbol.SQUARE -> drawRect(c, Offset(s * .06f, s * .06f), Size(s * .88f, s * .88f))
        CategorySymbol.TRIANGLE -> drawPath(triangle(0f), c)
        CategorySymbol.DIAMOND -> drawPath(diamond(0f), c)
        CategorySymbol.PLUS -> {
            val t = s * .32f
            drawRect(c, Offset((s - t) / 2, 0f), Size(t, s))
            drawRect(c, Offset(0f, (s - t) / 2), Size(s, t))
        }
        CategorySymbol.CROSS -> {
            val w = s * .26f
            drawLine(c, Offset(w / 2, w / 2), Offset(s - w / 2, s - w / 2), w, StrokeCap.Butt)
            drawLine(c, Offset(s - w / 2, w / 2), Offset(w / 2, s - w / 2), w, StrokeCap.Butt)
        }
        CategorySymbol.RING -> drawCircle(c, s / 2 - half, Offset(s / 2, s / 2), style = stroke)
        CategorySymbol.SQUARE_RING -> drawRect(c, Offset(half + s * .04f, half + s * .04f), Size(s - stroke.width - s * .08f, s - stroke.width - s * .08f), style = stroke)
        CategorySymbol.TRIANGLE_RING -> drawPath(triangle(half), c, style = stroke)
        CategorySymbol.DIAMOND_RING -> drawPath(diamond(half), c, style = stroke)
    }
}
