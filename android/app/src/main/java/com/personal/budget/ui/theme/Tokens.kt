package com.personal.budget.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * design/tokens.json, verbatim. These are the same values the web app uses as CSS variables.
 * Material's ColorScheme is derived from them (Theme.kt); the extra tokens Material has no
 * slot for live in [BudgetColors] / [LocalBudgetColors].
 */
@Immutable
data class BudgetColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val line2: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val accent: Color,
    /** Accent text on soft fills; pressed/hover primary (brand "accentPressed"). */
    val accentInk: Color,
    val accentSoft: Color,
    /** Label colour ON accent fills: white in light, navy in dark (white on #4A82FF fails contrast). */
    val onAccent: Color,
    val good: Color,
    val bad: Color,
    /** Amber: offline / changes pending. */
    val warn: Color,
    val card: Color,
    val glassBar: Color,
    val glassCard: Color,
    val toast: Color,
    val onToast: Color,
    val scrim: Color,
    val focusRing: Color,
    /** Boundaries that matter (inputs). `line` is decorative only. */
    val controlBorder: Color,
    /** Navy-tinted shadow base (rgba(8,32,79,a) light, black dark). */
    val shadow: Color,
    val isDark: Boolean,
    // Fluid glass v2 (docs/FLUID_GLASS_UI.md §3–4) ------------------------------------------------
    /** Ambient backdrop base and its three drifting blobs. */
    val page: Color,
    val blobA: Color,
    val blobB: Color,
    val blobC: Color,
    /** Cards, tiles, side rail. */
    val glass: Color,
    /** Sheets, menus, bottom nav, condensed top bar, tooltip. */
    val glassStrong: Color,
    /** Inset top highlight and hairline ring of every glass surface. */
    val glassHi: Color,
    val glassEdge: Color,
    /** Quiet control fill (seg track, chips, ghost buttons, inputs) and its pressed tone. */
    val fill: Color,
    val fill2: Color,
    /** Raised selection (seg thumb, selected chip). */
    val thumb: Color,
    /** Alpha of the soft card shadow / large shadow (shadow colour = [shadow]). */
    val shadowAlpha: Float,
    val shadowLgAlpha: Float,
) {
    val tintUp = Color(21, 128, 61)
    val tintDown = Color(220, 38, 38)
    val tintAccent = Color(16, 89, 252)
    /** Brand mark colours: upper band / lower band. */
    val markUpper: Color get() = if (isDark) Color(0xFFF5F8FF) else Color(0xFF08204F)
    val markLower: Color get() = if (isDark) Color(0xFF4A82FF) else Color(0xFF1059FC)
}

/** design/tokens.json v2 (brand kit merged into Fluid Glass), light. */
val LightTokens = BudgetColors(
    bg = Color(0xFFFFFFFF),
    surface = Color(0xFFF6F8FC),
    surface2 = Color(0xFFEDF1F7),
    line = Color(0xFFDCE3EE),
    line2 = Color(0xFFC9D3E1),
    ink = Color(0xFF08204F),
    ink2 = Color(0xFF415373),
    ink3 = Color(0xFF63718A),
    accent = Color(0xFF1059FC),
    accentInk = Color(0xFF0A45CC),
    accentSoft = Color(0xFFEDF3FF),
    onAccent = Color(0xFFFFFFFF),
    good = Color(0xFF15803D),
    bad = Color(0xFFDC2626),
    warn = Color(0xFFB45309),
    card = Color(0xFFFFFFFF),
    glassBar = Color(1f, 1f, 1f, .88f),
    glassCard = Color(1f, 1f, 1f, .88f),
    toast = Color(8, 32, 79).copy(alpha = .88f),
    onToast = Color(0xFFFFFFFF),
    scrim = Color(8, 32, 79).copy(alpha = .28f),
    focusRing = Color(0xFF1059FC),
    controlBorder = Color(0xFF63718A),
    shadow = Color(8, 32, 79),
    isDark = false,
    page = Color(0xFFF4F6FB),
    blobA = Color(16, 89, 252).copy(alpha = .07f),
    blobB = Color(99, 102, 241).copy(alpha = .05f),
    blobC = Color(56, 189, 248).copy(alpha = .04f),
    glass = Color.White.copy(alpha = .74f),
    glassStrong = Color.White.copy(alpha = .86f),
    glassHi = Color.White.copy(alpha = .55f),
    glassEdge = Color(8, 32, 79).copy(alpha = .06f),
    fill = Color(8, 32, 79).copy(alpha = .05f),
    fill2 = Color(8, 32, 79).copy(alpha = .08f),
    thumb = Color.White,
    shadowAlpha = .14f,
    shadowLgAlpha = .28f,
)

/** design/tokens.json v2, dark. */
val DarkTokens = BudgetColors(
    bg = Color(0xFF0A1122),
    surface = Color(0xFF131F35),
    surface2 = Color(0xFF1A2842),
    line = Color(0xFF2A3A55),
    line2 = Color(0xFF36496A),
    ink = Color(0xFFF5F8FF),
    ink2 = Color(0xFFC2CEE2),
    ink3 = Color(0xFF91A2BF),
    accent = Color(0xFF4A82FF),
    accentInk = Color(0xFF7BA3FF),
    accentSoft = Color(0xFF172B52),
    onAccent = Color(0xFF0A1122),
    good = Color(0xFF4ADE80),
    bad = Color(0xFFFF8585),
    warn = Color(0xFFF2A93B),
    card = Color(0xFF0A1122),
    glassBar = Color(10, 17, 34).copy(alpha = .88f),
    glassCard = Color(19, 31, 53).copy(alpha = .88f),
    toast = Color(245, 248, 255).copy(alpha = .94f),
    onToast = Color(0xFF0A1122),
    scrim = Color.Black.copy(alpha = .5f),
    focusRing = Color(0xFF4A82FF),
    controlBorder = Color(0xFF91A2BF),
    shadow = Color.Black,
    isDark = true,
    page = Color(0xFF0A1122),
    blobA = Color(74, 130, 255).copy(alpha = .12f),
    blobB = Color(99, 102, 241).copy(alpha = .08f),
    blobC = Color(56, 189, 248).copy(alpha = .05f),
    glass = Color(20, 32, 56).copy(alpha = .72f),
    glassStrong = Color(17, 27, 48).copy(alpha = .88f),
    glassHi = Color.White.copy(alpha = .08f),
    glassEdge = Color.White.copy(alpha = .06f),
    fill = Color.White.copy(alpha = .06f),
    fill2 = Color.White.copy(alpha = .10f),
    thumb = Color.White.copy(alpha = .14f),
    shadowAlpha = .6f,
    shadowLgAlpha = .7f,
)

enum class CategorySymbol { CIRCLE, SQUARE, TRIANGLE, DIAMOND, PLUS, CROSS, RING, SQUARE_RING, TRIANGLE_RING, DIAMOND_RING }

data class PaletteEntry(val id: String, val color: Color, val symbol: CategorySymbol)

/** tokens.json v2 categoryPalette (same colours in both themes; always paired with the symbol). */
val CategoryPalette = listOf(
    PaletteEntry("housing", Color(0xFF2978C9), CategorySymbol.CIRCLE),
    PaletteEntry("food", Color(0xFFC96523), CategorySymbol.SQUARE),
    PaletteEntry("transport", Color(0xFF178879), CategorySymbol.TRIANGLE),
    PaletteEntry("fun", Color(0xFFA65DA8), CategorySymbol.DIAMOND),
    PaletteEntry("subscriptions", Color(0xFFA87914), CategorySymbol.PLUS),
    PaletteEntry("health", Color(0xFFC65172), CategorySymbol.CROSS),
    PaletteEntry("roth", Color(0xFF6B75C6), CategorySymbol.RING),
    PaletteEntry("401k", Color(0xFF648631), CategorySymbol.SQUARE_RING),
    PaletteEntry("brokerage", Color(0xFFB46B51), CategorySymbol.TRIANGLE_RING),
    PaletteEntry("hsa", Color(0xFF778190), CategorySymbol.DIAMOND_RING),
)

/** tokens.json v2 categoryAssignment: default palette id by category name (case-insensitive). */
val CategoryAssignment = mapOf(
    "paychecks" to "401k",
    "rent" to "housing",
    "subscriptions" to "subscriptions",
    "food" to "food",
    "fun" to "fun",
    "gas" to "transport",
    "misc" to "hsa",
    "car ins" to "health",
    "utilities" to "roth",
    "phone bill" to "brokerage",
    "roth" to "roth",
    "401k" to "401k",
    "taxable brokerage" to "brokerage",
    "hsa" to "hsa",
)

val LocalBudgetColors = staticCompositionLocalOf { LightTokens }

/** Radii (FLUID_GLASS_UI v2 §4): cards 20, sheets 28, menus 18, inputs/buttons 12, pills 999. */
object Radius {
    val card = 20.dp
    val control = 12.dp
    val pick = 999.dp
    val seg = 999.dp
    val menu = 18.dp
    val sheet = 28.dp
    val floatingBar = 999.dp
    val rail = 24.dp
    val pill = 999.dp
    /** Rows inside cards (tappable rows get a soft rounded press / selection fill). */
    val row = 14.dp
}

/** Spacing scale 4 / 6 / 8 / 10 / 14 / 18 / 28. */
object Space {
    val xxs = 4.dp
    val xs = 6.dp
    val s = 8.dp
    val m = 10.dp
    val l = 14.dp
    val xl = 18.dp
    val xxl = 28.dp
}

/** The curves and durations (tokens.json "motion", FLUID_GLASS_UI v2 §2). */
object Motion {
    val Ease = CubicBezierEasing(.22f, 1f, .36f, 1f)
    val Spring = CubicBezierEasing(.34f, 1.4f, .64f, 1f)
    /** Thumbs and indicators that slide (segmented, nav, switch) and swipe/drag spring-back. */
    val SpringSoft = CubicBezierEasing(.3f, 1.25f, .5f, 1f)
    const val PRESS_SCALE = .97f
    const val ICON_PRESS_SCALE = .94f
    const val PRESS = 150
    const val HOVER = 200
    /** Segmented / nav thumb glide, switch thumb. */
    const val THUMB = 350
    const val SHEET_IN = 350
    const val SHEET_OUT = 220
    const val NUMBER_ROLL = 380
    const val SWIPE_BACK = 300
    const val CHART_DRAW = 700
    const val BAR_CONDENSE = 200
    const val GLIDE = 350
    /** List rows in / out (FLIP). */
    const val APPEAR = 300
    const val EXIT = 260
    const val ARRIVE = 350
    const val REORDER = 300
    const val TINT = 1400
    const val EXIT_FACTOR = .6f
    const val PROGRESS = 600
}
