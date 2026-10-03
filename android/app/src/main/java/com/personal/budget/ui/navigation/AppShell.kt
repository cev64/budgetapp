package com.personal.budget.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.personal.budget.ui.MainViewModel
import com.personal.budget.ui.components.AddFab
import com.personal.budget.ui.components.GlassBottomBar
import com.personal.budget.ui.components.GlassRail
import com.personal.budget.ui.components.LocalShellPadding
import com.personal.budget.ui.components.LocalTopBarActions
import com.personal.budget.ui.components.Lucide
import com.personal.budget.ui.components.NavItem
import com.personal.budget.ui.components.ProvideToast
import com.personal.budget.ui.components.ToastHost
import com.personal.budget.ui.components.ToastState
import com.personal.budget.ui.components.TopBarActions
import com.personal.budget.ui.screens.LocalWindowLayout
import com.personal.budget.ui.screens.WindowLayout
import com.personal.budget.ui.screens.add.AddTransactionSheet
import com.personal.budget.ui.screens.home.HomeScreen
import com.personal.budget.ui.screens.month.MonthScreen
import com.personal.budget.ui.screens.networth.NetWorthScreen
import com.personal.budget.ui.screens.settings.SettingsScreen
import com.personal.budget.ui.screens.year.YearScreen
import com.personal.budget.ui.theme.Budget
import com.personal.budget.ui.theme.LocalReduceMotion
import com.personal.budget.ui.theme.Motion

object Routes {
    const val HOME = "home"
    const val MONTH = "month"
    const val YEAR = "year"
    const val NET_WORTH = "networth"
    const val SETTINGS = "settings"
}

val NavItems = listOf(
    NavItem(Routes.HOME, "Home", Lucide.House),
    NavItem(Routes.MONTH, "Month", Lucide.CalendarDays),
    NavItem(Routes.YEAR, "Year", Lucide.ChartColumn),
    NavItem(Routes.NET_WORTH, "Net worth", Lucide.Landmark),
)

fun NavHostController.navigateTop(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * The signed-in app. Layout is chosen from the window width (never the device model):
 * compact = glass bottom bar + FAB, medium/expanded = navigation rail with Add at the top.
 * Navigation state lives in the NavController (saved across fold/unfold); per-screen state lives
 * in activity-scoped ViewModels, so changing layout never resets what you were looking at.
 */
@Composable
fun AppShell(
    main: MainViewModel,
    navController: NavHostController = rememberNavController(),
    startRoute: String = Routes.HOME,
    toast: ToastState = remember { ToastState() },
) {
    val indicator by main.syncIndicator.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val reduce = LocalReduceMotion.current
    BoxWithConstraints(Modifier.fillMaxSize().background(Budget.colors.bg)) {
        val layout = WindowLayout.fromWidth(maxWidth.value)
        val backStack by navController.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        val selected = if (route == Routes.SETTINGS) null else route ?: startRoute
        var bottomBarHeight by remember { mutableStateOf(0.dp) }
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val actions = remember(indicator) {
            TopBarActions(onSync = { main.syncNow() }, onSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } }, sync = indicator)
        }
        CompositionLocalProvider(
            LocalWindowLayout provides layout,
            LocalTopBarActions provides actions,
            com.personal.budget.ui.components.LocalShowMarkInTopBar provides layout.isCompact,
        ) {
            ProvideToast(toast) {
                Row(Modifier.fillMaxSize()) {
                    if (!layout.isCompact) {
                        GlassRail(NavItems, selected, onSelect = { navController.navigateTop(it.route) }, onAdd = { main.openAdd() })
                    }
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        CompositionLocalProvider(
                            LocalShellPadding provides PaddingValues(bottom = if (layout.isCompact) bottomBarHeight + 72.dp else navBottom + 16.dp),
                        ) {
                            NavHost(
                                navController = navController,
                                startDestination = startRoute,
                                enterTransition = {
                                    if (reduce) fadeIn(tween(0)) else fadeIn(tween(420, easing = Motion.Ease)) + slideInVertically(tween(420, easing = Motion.Ease)) { 30 }
                                },
                                exitTransition = { fadeOut(tween(if (reduce) 0 else 160, easing = Motion.Ease)) },
                                popEnterTransition = { fadeIn(tween(if (reduce) 0 else 300, easing = Motion.Ease)) },
                                popExitTransition = { fadeOut(tween(if (reduce) 0 else 160, easing = Motion.Ease)) },
                            ) {
                                composable(Routes.HOME) { HomeScreen(main, onOpenMonth = { m -> main.selectMonth(m); navController.navigateTop(Routes.MONTH) }) }
                                composable(Routes.MONTH) { MonthScreen(main) }
                                composable(Routes.YEAR) { YearScreen(main, onOpenMonth = { m -> main.selectMonth(m); navController.navigateTop(Routes.MONTH) }) }
                                composable(Routes.NET_WORTH) { NetWorthScreen() }
                                composable(Routes.SETTINGS) { SettingsScreen(main, onBack = { navController.popBackStack() }) }
                            }
                        }
                        if (layout.isCompact) {
                            GlassBottomBar(
                                NavItems,
                                selected,
                                onSelect = { navController.navigateTop(it.route) },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .onSizeChanged { bottomBarHeight = with(density) { it.height.toDp() } },
                            )
                            AddFab(
                                visible = route != Routes.SETTINGS,
                                onClick = { main.openAdd() },
                                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = bottomBarHeight + 14.dp),
                            )
                        }
                        ToastHost(
                            toast,
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = if (layout.isCompact) bottomBarHeight + 84.dp else navBottom + 26.dp)
                                .padding(horizontal = 16.dp),
                        )
                    }
                }
                AddTransactionSheet(main)
            }
        }
    }
}

@Suppress("unused")
private fun Modifier.fullWidth() = this.fillMaxWidth()
