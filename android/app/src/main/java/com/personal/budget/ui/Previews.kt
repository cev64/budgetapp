package com.personal.budget.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.personal.budget.domain.usecase.HistoryPoint
import com.personal.budget.domain.usecase.Money
import com.personal.budget.ui.components.BrandLockup
import com.personal.budget.ui.components.BudgetButton
import com.personal.budget.ui.components.BudgetCard
import com.personal.budget.ui.components.BudgetProgress
import com.personal.budget.ui.components.ButtonKind
import com.personal.budget.ui.components.CardHeader
import com.personal.budget.ui.components.CategoryMark
import com.personal.budget.ui.components.GlassTopBar
import com.personal.budget.ui.components.HistoryChart
import com.personal.budget.ui.components.MicroLabel
import com.personal.budget.ui.components.SegmentedControl
import com.personal.budget.ui.components.StackedBar
import com.personal.budget.ui.theme.Budget
import com.personal.budget.ui.theme.BudgetTheme
import com.personal.budget.ui.theme.CategoryPalette
import java.time.LocalDate

/*
 * Design previews of the shared building blocks at the cover-screen (412dp) and inner-screen
 * (900dp) widths, light and dark. Full screens are rendered headlessly by
 * app/src/test/.../screenshots/ScreenshotTest (they need the database and ViewModels).
 */

private val samplePoints = (0..90).map { i ->
    HistoryPoint(LocalDate.of(2026, 7, 4).plusDays(i.toLong()), 28_000.0 + i * 55 + (i % 7) * 120)
}

@Composable
private fun Kit() {
    val c = Budget.colors
    Column(Modifier.fillMaxSize().background(c.bg)) {
        GlassTopBar(micro = "Budget", title = "October 2026", scrolled = false)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BudgetCard {
                MicroLabel("Leftover · October")
                Text(Money.format(657.54), style = Budget.type.hero, color = c.ink)
                Text("of ${Money.format(1205.0)} planned", style = Budget.type.secondary, color = c.ink2)
                Spacer(Modifier.height(12.dp))
                StackedBar(listOf(.55f to c.ink.copy(alpha = .8f), .15f to c.accent, .2f to c.good))
            }
            BudgetCard {
                CardHeader("Budgets")
                Row {
                    CategoryPalette.forEach { CategoryMark(it, Modifier.padding(end = 8.dp), size = 14.dp) }
                }
                Spacer(Modifier.height(10.dp))
                BudgetProgress(480.0, 400.0, height = 6.dp, color = CategoryPalette[1].color)
            }
            BudgetCard {
                SegmentedControl(listOf(1 to "1M", 2 to "3M", 3 to "6M", 4 to "1Y", 5 to "All"), 2, {})
                Spacer(Modifier.height(10.dp))
                HistoryChart(samplePoints, height = 180.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BudgetButton("Add", {}, kind = ButtonKind.Primary)
                BudgetButton("Export backup", {})
                BudgetButton("Sign out", {}, kind = ButtonKind.Danger)
            }
            BrandLockup(width = 136.dp)
            Spacer(Modifier.width(1.dp))
        }
    }
}

@Preview(name = "Kit · cover 412dp", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun KitCompactLight() = BudgetTheme { Kit() }

@Preview(name = "Kit · cover 412dp · dark", widthDp = 412, heightDp = 915, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun KitCompactDark() = BudgetTheme { Kit() }

@Preview(name = "Kit · inner 900dp", widthDp = 900, heightDp = 820, showBackground = true)
@Composable
private fun KitExpandedLight() = BudgetTheme { Kit() }

@Preview(name = "Kit · inner 900dp · dark", widthDp = 900, heightDp = 820, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun KitExpandedDark() = BudgetTheme { Kit() }
