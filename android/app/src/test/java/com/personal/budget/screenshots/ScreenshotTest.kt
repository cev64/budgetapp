package com.personal.budget.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.personal.budget.BudgetApp
import com.personal.budget.data.local.ThemeMode
import com.personal.budget.domain.model.MonthKey
import com.personal.budget.ui.MainViewModel
import com.personal.budget.ui.appViewModel
import com.personal.budget.ui.navigation.AppShell
import com.personal.budget.ui.navigation.Routes
import com.personal.budget.ui.screens.auth.AuthScreen
import com.personal.budget.ui.theme.BudgetTheme
import com.personal.budget.widgets.BudgetWidget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the main screens headlessly (Robolectric native graphics) to PNGs for visual review.
 * Opt-in: ./gradlew testDebugUnitTest --tests '*ScreenshotTest*' -Pscreenshots=/abs/output/dir
 * Without -Pscreenshots every test is skipped, so CI never downloads the Robolectric runtime.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = BudgetApp::class)
class ScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val outDir: File? = System.getProperty("budget.screenshots.dir")?.let(::File)

    @Before
    fun setUp() {
        assumeTrue("screenshots disabled", outDir != null)
        outDir!!.mkdirs()
        val app = ApplicationProvider.getApplicationContext<BudgetApp>()
        runBlocking { DemoData.seed(app.container) }
    }

    private fun shoot(name: String, qualifiers: String, theme: ThemeMode, before: () -> Unit = {}, content: @Composable (MainViewModel) -> Unit) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        compose.activity.recreate()
        compose.setContent {
            val vm = appViewModel { c, h -> MainViewModel(c, h) }
            BudgetTheme(themeMode = theme) { content(vm) }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        before()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val roots = compose.onAllNodes(isRoot()).fetchSemanticsNodes()
        println("screenshot $name: ${roots.size} root(s)")
        if (roots.size > 1) {
            for (i in roots.indices) {
                val b = compose.onAllNodes(isRoot())[i].captureToImage().asAndroidBitmap()
                File(outDir, "$name-root$i.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        val bitmap = compose.onAllNodes(isRoot())[roots.size - 1].captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shell(route: String, setup: (MainViewModel) -> Unit = {}): @Composable (MainViewModel) -> Unit = { vm ->
        setup(vm)
        AppShell(vm, navController = rememberNavController(), startRoute = route)
    }

    private val compact = "w412dp-h915dp-xhdpi"
    private val medium = "w900dp-h820dp-xhdpi"
    private val expanded = "w1240dp-h820dp-xhdpi"

    private fun all(name: String, route: String, setup: (MainViewModel) -> Unit = {}) {
        for ((size, q) in listOf("compact" to compact, "fold" to medium)) {
            for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                shoot("$name-$size-${theme.key}", q, theme, content = shell(route, setup))
            }
        }
    }

    @Test fun home() {
        all("home", Routes.HOME)
        shoot("home-expanded-light", expanded, ThemeMode.LIGHT, content = shell(Routes.HOME))
    }

    @Test fun month() = all("month", Routes.MONTH) { it.selectMonth(MonthKey.now()) }

    /** Scrolled past the large title: the collapsed glass top bar with the compact title. */
    @Test fun scrolled() {
        for ((size, q) in listOf("compact" to compact, "fold" to medium)) {
            for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                shoot("month-scrolled-$size-${theme.key}", q, theme, before = {
                    compose.onRoot().performTouchInput { swipeUp(startY = bottom * .8f, endY = bottom * .35f) }
                }, content = shell(Routes.MONTH) { it.selectMonth(MonthKey.now()) })
            }
        }
    }

    @Test fun monthDetail() {
        val app = ApplicationProvider.getApplicationContext<BudgetApp>()
        val food = runBlocking { app.container.repository.categories.first() }.first { it.name == "Food" }
        val select: (MainViewModel) -> Unit = { vm ->
            vm.selectMonth(MonthKey.now())
        }
        for ((size, q) in listOf("compact" to compact, "fold" to medium)) {
            shoot("month-detail-$size-light", q, ThemeMode.LIGHT) { vm ->
                select(vm)
                val mvm = appViewModel { c, h -> com.personal.budget.ui.screens.month.MonthViewModel(c, h) }
                mvm.selectCategory(food.id)
                AppShell(vm, navController = rememberNavController(), startRoute = Routes.MONTH)
            }
        }
    }

    @Test fun year() {
        all("year", Routes.YEAR)
        for ((size, q) in listOf("compact" to compact, "fold" to medium)) {
            for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                shoot("year-bymonth-$size-${theme.key}", q, theme) { vm ->
                    androidx.compose.runtime.CompositionLocalProvider(com.personal.budget.ui.screens.year.LocalByMonthInitiallyOpen provides true) {
                        AppShell(vm, navController = rememberNavController(), startRoute = Routes.YEAR)
                    }
                }
            }
        }
    }

    @Test fun sheet() {
        for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            shoot("sheet-prompt-compact-${theme.key}", compact, theme, content = shell(Routes.SHEET))
        }
        val portrait = "w900dp-h1100dp-xhdpi"
        for ((size, q) in listOf("fold-portrait" to portrait, "landscape" to expanded)) {
            for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                shoot("sheet-summary-$size-${theme.key}", q, theme) { vm ->
                    val svm = appViewModel { c, h -> com.personal.budget.ui.screens.sheet.SheetViewModel(c, h) }
                    androidx.compose.runtime.LaunchedEffect(Unit) { svm.selectTab(com.personal.budget.ui.screens.sheet.SheetViewModel.SUMMARY) }
                    AppShell(vm, navController = rememberNavController(), startRoute = Routes.SHEET)
                }
                shoot("sheet-month-$size-${theme.key}", q, theme) { vm ->
                    val svm = appViewModel { c, h -> com.personal.budget.ui.screens.sheet.SheetViewModel(c, h) }
                    androidx.compose.runtime.LaunchedEffect(Unit) { svm.selectMonth(MonthKey.now()) }
                    AppShell(vm, navController = rememberNavController(), startRoute = Routes.SHEET)
                }
            }
        }
    }

    @Test fun netWorth() = all("networth", Routes.NET_WORTH)

    @Test fun settings() = all("settings", Routes.SETTINGS)

    @Test fun addSheet() {
        for ((size, q) in listOf("compact" to compact, "fold" to medium)) {
            for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                shoot("add-$size-${theme.key}", q, theme) { vm ->
                    // The add sheet is drawn inside the app window (GlassSheet), so the shell renders it.
                    androidx.compose.runtime.LaunchedEffect(Unit) { if (vm.addDraft.value == null) vm.openAdd() }
                    AppShell(vm, navController = rememberNavController(), startRoute = Routes.HOME)
                }
            }
        }
    }

    @Test fun auth() {
        shoot("auth-compact-light", compact, ThemeMode.LIGHT) { AuthScreen() }
        shoot("auth-compact-dark", compact, ThemeMode.DARK) { AuthScreen() }
    }

    @Test fun widget() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<BudgetApp>()
        for ((night, suffix) in listOf(false to "light", true to "dark")) {
            RuntimeEnvironment.setQualifiers(if (night) "+night" else "+notnight")
            for ((label, size) in listOf("small" to DpSize(130.dp, 120.dp), "medium" to DpSize(280.dp, 130.dp), "large" to DpSize(280.dp, 290.dp))) {
                val rv = (BudgetWidget() as GlanceAppWidget).compose(context, size = size)
                val parent = FrameLayout(context)
                val view: View = rv.apply(context, parent)
                val density = context.resources.displayMetrics.density
                val w = (size.width.value * density).toInt()
                val h = (size.height.value * density).toInt()
                parent.addView(view, ViewGroup.LayoutParams(w, h))
                parent.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                parent.layout(0, 0, w, h)
                val pad = (16 * density).toInt()
                val bmp = Bitmap.createBitmap(w + 2 * pad, h + 2 * pad, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(if (night) 0xFF202833.toInt() else 0xFFB8C4D6.toInt())
                canvas.translate(pad.toFloat(), pad.toFloat())
                parent.draw(canvas)
                File(outDir, "widget-$label-$suffix.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
