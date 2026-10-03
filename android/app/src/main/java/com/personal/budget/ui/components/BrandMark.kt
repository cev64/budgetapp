package com.personal.budget.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.personal.budget.R
import com.personal.budget.ui.theme.Budget

/** The split-ledger B (design/brand/logo-mark.svg): two even-odd paths on a 24×24 grid. */
object BrandPaths {
    const val UPPER = "M4 2H13A4.5 4.5 0 0 1 13 11H4Z M8 5V8H13A1.5 1.5 0 0 0 13 5Z"
    const val LOWER = "M4 13H16A4.5 4.5 0 0 1 16 22H4Z M8 16V19H16A1.5 1.5 0 0 0 16 16Z"

    fun vector(upper: Color, lower: Color): ImageVector =
        ImageVector.Builder("BudgetMark", 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes(UPPER), pathFillType = PathFillType.EvenOdd, fill = SolidColor(upper))
            .addPath(addPathNodes(LOWER), pathFillType = PathFillType.EvenOdd, fill = SolidColor(lower))
            .build()
}

/**
 * The brand mark. Light: navy upper / blue lower band; dark: near-white / lighter blue. Never
 * recoloured with category colours, never stretched. Min 16dp, preferred 24–32dp.
 */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 28.dp, contentDescription: String? = null) {
    val c = Budget.colors
    val vector = remember(c.markUpper, c.markLower) { BrandPaths.vector(c.markUpper, c.markLower) }
    Image(vector, contentDescription, modifier.size(size))
}

/** The horizontal lockup (mark + outlined Inter wordmark), 136×36 aspect; min 109dp wide. */
@Composable
fun BrandLockup(modifier: Modifier = Modifier, width: Dp = 136.dp) {
    val dark = Budget.colors.isDark
    Image(
        painterResource(if (dark) R.drawable.logo_lockup_dark else R.drawable.logo_lockup),
        contentDescription = "Budget",
        modifier = modifier.size(width, width * 36f / 136f),
    )
}
