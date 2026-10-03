package com.personal.budget.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.personal.budget.BudgetApp
import com.personal.budget.domain.model.MonthKey
import com.personal.budget.screenshots.DemoData
import com.personal.budget.ui.navigation.AppShell
import com.personal.budget.ui.navigation.Routes
import com.personal.budget.ui.screens.sheet.SheetViewModel
import com.personal.budget.ui.theme.BudgetTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fold continuity for the Sheet destination: at compact width it shows the unfold prompt; when the
 * window grows to ≥ 600dp it switches to the sheet in place (no navigation); folding back shows the
 * prompt again, and unfolding again restores the same tab.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = BudgetApp::class, qualifiers = "w1240dp-h900dp")
class SheetFoldTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun seed() {
        val app = ApplicationProvider.getApplicationContext<BudgetApp>()
        runBlocking { DemoData.seed(app.container) }
    }

    @Test
    fun unfoldSwitchesInPlace_foldKeepsTab() {
        var width by mutableStateOf(412.dp)
        lateinit var sheetVm: SheetViewModel
        compose.setContent {
            val vm = appViewModel { c, h -> MainViewModel(c, h) }
            sheetVm = appViewModel { c, h -> SheetViewModel(c, h) }
            BudgetTheme {
                Box(Modifier.requiredWidth(width).fillMaxHeight()) {
                    AppShell(vm, navController = rememberNavController(), startRoute = Routes.SHEET)
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Unfold to see the spreadsheet").assertExists()

        // Unfold: the sheet replaces the prompt.
        width = 900.dp
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithText("Unfold to see the spreadsheet").assertDoesNotExist()
        compose.onNodeWithText("Summary").assertExists()

        // Pick a month tab, fold, unfold: still on that tab.
        val now = MonthKey.now()
        compose.onAllNodesWithText(now.shortName)[0].performClick()
        compose.waitForIdle()
        assertEquals(now.id, sheetVm.tab.value)

        width = 412.dp
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithText("Unfold to see the spreadsheet").assertExists()
        assertEquals(now.id, sheetVm.tab.value)

        width = 900.dp
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithText("Unfold to see the spreadsheet").assertDoesNotExist()
        compose.onAllNodesWithText(now.label.uppercase())[0].assertExists()
        assertEquals(now.id, sheetVm.tab.value)
    }
}
