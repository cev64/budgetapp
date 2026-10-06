package com.personal.budget.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.personal.budget.ui.MainViewModel
import com.personal.budget.ui.components.AmbientBackdrop
import com.personal.budget.ui.components.FloatingNavBar
import com.personal.budget.ui.components.FloatingNavHeight
import com.personal.budget.ui.components.GlassRail
import com.personal.budget.ui.components.LocalShellHaze
import com.personal.budget.ui.components.LocalShellPadding
import com.personal.budget.ui.components.LocalTopBarActions
import com.personal.budget.ui.components.Lucide
import com.personal.budget.ui.components.NavItem
import com.personal.budget.ui.components.ProvideToast
import com.personal.budget.ui.components.ToastHost
import com.personal.budget.ui.components.ToastState
import com.personal.budget.ui.components.TopBarActions
import com.personal.budget.ui.components.floatingNavItemWidth
import com.personal.budget.ui.components.railWidth
import com.personal.budget.ui.screens.LocalWindowLayout
import com.personal.budget.ui.screens.WindowLayout
import com.personal.budget.ui.screens.add.AddTransactionSheet
import com.personal.budget.ui.screens.home.HomeScreen
import com.personal.budget.ui.screens.month.MonthScreen
import com.personal.budget.ui.screens.networth.NetWorthScreen
import com.personal.budget.ui.screens.settings.SettingsScreen
import com.personal.budget.ui.screens.year.YearScreen
import com.personal.budget.ui.theme.LocalReduceMotion
import com.personal.budget.ui.theme.Motion
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

object Routes {
    const val HOME = "home"
    const val MONTH = "month"
    const val YEAR = "year"
    const val NET_WORTH = "networth"
    const val SHEET = "sheet"
    const val SETTINGS = "settings"
}

val NavItems = listOf(
    NavItem(Routes.HOME, "Home", Lucide.House),
    NavItem(Routes.MONTH, "Month", Lucide.CalendarDays),
    NavItem(Routes.YEAR, "Year", Lucide.ChartColumn),
    NavItem(Routes.NET_WORTH, "Net worth", Lucide.Landmark),
    NavItem(Routes.SHEET, "Sheet", Lucide.Sheet),
)

fun NavHostController.navigateTop(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * The signed-in app (FLUID_GLASS v2 §5). Layout is chosen from the window width (never the device
 * model): compact = floating glass pill nav + round Add beside it; medium/expanded = a floating
 * glass rail (76dp, or 220dp with the lockup at ≥ 1024dp) with Add at the top. Everything sits on
 * the ambient backdrop, which (with the screens) is the blur source for the nav and the Add sheet.
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
    val reduce = LocalReduceMotion.current
    val shellHaze = rememberHazeState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layout = WindowLayout.fromWidth(maxWidth.value)
        val wideRail = layout == WindowLayout.Expanded
        val backStack by navController.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        val selected = if (route == Routes.SETTINGS) null else route ?: startRoute
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val actions = remember(indicator) {
            TopBarActions(onSync = { main.syncNow() }, onSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } }, sync = indicator)
        }
        // Content padding: the floating nav (60 + 12 + inset) plus breathing room on the cover screen.
        val bottomReserve = if (layout.isCompact) FloatingNavHeight + 12.dp + navBottom + 20.dp else navBottom + 20.dp
        CompositionLocalProvider(
            LocalWindowLayout provides layout,
            LocalTopBarActions provides actions,
            LocalShellHaze provides shellHaze,
        ) {
            ProvideToast(toast) {
                // Blur source for the floating nav and the sheet: the ambient field plus the screens.
                Box(Modifier.fillMaxSize().hazeSource(shellHaze)) {
                    AmbientBackdrop()
                    Row(Modifier.fillMaxSize()) {
                        if (!layout.isCompact) {
                            // The rail floats over the field: inset 12 + its width + 12 gap.
                            Spacer(
                                Modifier
                                    .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.navigationBars).only(WindowInsetsSides.Start))
                                    .width(railWidth(wideRail) + 12.dp),
                            )
                        }
                        Box(Modifier.weight(1f).fillMaxSize()) {
                            CompositionLocalProvider(LocalShellPadding provides PaddingValues(bottom = bottomReserve)) {
                                NavHost(
                                    navController = navController,
                                    startDestination = startRoute,
                                    enterTransition = {
                                        if (reduce) fadeIn(tween(0)) else fadeIn(tween(320, easing = Motion.Ease)) + slideInVertically(tween(320, easing = Motion.Ease)) { 24 }
                                    },
                                    exitTransition = { fadeOut(tween(if (reduce) 0 else 140, easing = Motion.Ease)) },
                                    popEnterTransition = { fadeIn(tween(if (reduce) 0 else 260, easing = Motion.Ease)) },
                                    popExitTransition = { fadeOut(tween(if (reduce) 0 else 140, easing = Motion.Ease)) },
                                ) {
                                    composable(Routes.HOME) { HomeScreen(main, onOpenMonth = { m -> main.selectMonth(m); navController.navigateTop(Routes.MONTH) }) }
                                    composable(Routes.MONTH) { MonthScreen(main) }
                                    composable(Routes.YEAR) { YearScreen(main, onOpenMonth = { m -> main.selectMonth(m); navController.navigateTop(Routes.MONTH) }) }
                                    composable(Routes.NET_WORTH) { NetWorthScreen() }
                                    composable(Routes.SHEET) { com.personal.budget.ui.screens.sheet.SheetScreen(main, onGoToYear = { navController.navigateTop(Routes.YEAR) }) }
                                    composable(Routes.SETTINGS) { SettingsScreen(main, onBack = { navController.popBackStack() }) }
                                }
                            }
                        }
                    }
                }
                if (layout.isCompact) {
                    FloatingNavBar(
                        NavItems,
                        selected,
                        onSelect = { navController.navigateTop(it.route) },
                        onAdd = { main.openAdd() },
                        showAdd = route != Routes.SETTINGS,
                        itemWidth = floatingNavItemWidth(maxWidth, NavItems.size),
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                } else {
                    GlassRail(NavItems, selected, onSelect = { navController.navigateTop(it.route) }, onAdd = { main.openAdd() }, wide = wideRail, modifier = Modifier.align(Alignment.TopStart))
                }
                // The sheet floats over everything; toasts sit above both.
                AddTransactionSheet(main)
                ToastHost(
                    toast,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (layout.isCompact) FloatingNavHeight + 12.dp + navBottom + 14.dp else navBottom + 26.dp)
                        .padding(start = if (layout.isCompact) 16.dp else railWidth(wideRail) + 24.dp, end = 16.dp),
                )
            }
        }
    }
}
