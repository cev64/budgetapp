package com.personal.budget.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.personal.budget.BudgetApp
import com.personal.budget.domain.usecase.TotalKind
import com.personal.budget.ui.screens.sheet.SheetColumns
import com.personal.budget.ui.screens.sheet.SheetCols
import com.personal.budget.ui.screens.sheet.rememberSheetColumns
import com.personal.budget.ui.theme.Budget
import com.personal.budget.ui.theme.BudgetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Sheet column model: B fits its longest label; whole ledger blocks fit the Fold widths. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = BudgetApp::class)
class SheetColumnsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val b = 226.dp

    /** Right edge of the n-th ledger block (1-based) inside the grid. */
    private fun SheetColumns.blockEnd(n: Int): Dp = left + (spacer + block) * n

    @Test fun foldPortraitShowsLeftBlockAndWholeG() {
        val c = SheetCols.fit(b, 900.dp - 80.dp) // window minus the rail
        assertTrue("G fits: ${c.blockEnd(1)}", c.blockEnd(1) <= 820.dp)
        assertTrue(c.item >= SheetCols.ITEM_MIN)
        assertEquals(98.dp, c.num)
        assertEquals(12.dp, c.spacer)
    }

    @Test fun landscapeShowsGAndKWithOPeeking() {
        val c = SheetCols.fit(b, 1240.dp - 80.dp)
        assertEquals(SheetCols.ITEM, c.item)
        assertTrue("K fits: ${c.blockEnd(2)}", c.blockEnd(2) <= 1160.dp)
        assertTrue("O peeks in", c.blockEnd(2) + c.spacer < 1160.dp)
    }

    @Test fun summaryPortraitShowsNetWorthAndPartOfLedger() {
        val c = SheetCols.fit(b, 820.dp)
        val ghEnd = c.left + c.spacer + c.name + c.value
        assertTrue("G/H fits: $ghEnd", ghEnd <= 820.dp)
        assertTrue("J starts on screen", ghEnd + c.spacer < 820.dp - 8.dp)
    }

    @Test fun narrowWidthKeepsMinimumItemAndScrolls() {
        val c = SheetCols.fit(b, 700.dp)
        assertEquals(SheetCols.ITEM_MIN, c.item)
    }

    @Test fun largerFontScaleWidensFixedColumns() {
        val c = SheetCols.fit(b, 2000.dp, scale = 1.3f)
        assertEquals(98f * 1.3f, c.num.value, 0.01f)
    }

    @Test fun measuredBFitsLongestLabelAt14sp() {
        var cols: SheetColumns? = null
        var labelWidth = 0.dp
        compose.setContent {
            BudgetTheme {
                val style = Budget.type.secondary.copy(fontWeight = FontWeight.SemiBold)
                assertEquals(14f, style.fontSize.value, 0f)
                val m = rememberTextMeasurer()
                val d = LocalDensity.current
                labelWidth = with(d) { m.measure(TotalKind.SAVED.label, style).size.width.toDp() }
                cols = rememberSheetColumns(820.dp, TotalKind.entries.map { it.label })
            }
        }
        compose.waitForIdle()
        val c = cols!!
        assertTrue("B ${c.b} fits label $labelWidth", c.b - SheetCols.PAD_H * 2 >= labelWidth)
        assertTrue("B stays near 210–230dp: ${c.b}", c.b in 200.dp..240.dp)
    }
}
