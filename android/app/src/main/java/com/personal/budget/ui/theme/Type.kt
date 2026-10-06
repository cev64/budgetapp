package com.personal.budget.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.personal.budget.R

/** Inter (body) and Barlow Condensed (big uppercase titles), bundled under res/font (OFL). */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
)

/** Tabular, lining numerals everywhere, so changing numbers never jiggle. */
const val TNUM = "tnum, lnum"

private val base = TextStyle(
    fontFamily = Inter,
    fontFeatureSettings = TNUM,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** letterSpacing from tokens (px at size) → em. */
private fun track(px: Double, size: Int) = (px / size).em

/**
 * The type scale from design/tokens.json v2 (sp, so Android font scaling applies). Colours are
 * applied at the call site. Never shrink these for the cover screen: change the layout instead.
 */
@Immutable
data class BudgetType(
    /** display: Barlow Condensed 600 40/44 +0.8, uppercase (screen titles). */
    val screenTitle: TextStyle = TextStyle(
        fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 44.sp,
        letterSpacing = track(0.8, 40), fontFeatureSettings = TNUM,
    ),
    /** title: Inter 600 24/30 −0.48. */
    val title: TextStyle = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = track(-0.48, 24)),
    /** v2 hero numbers: Inter 600 48/52, −1px tracking (generous space around them). */
    val hero: TextStyle = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 48.sp, lineHeight = 52.sp, letterSpacing = track(-1.0, 48)),
    /** Secondary hero (detail panes, sheet amount): Inter 600 36/42, −0.8px. */
    val heroSmall: TextStyle = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = track(-0.8, 36)),
    /** Tile values: title size with number features. */
    val number: TextStyle = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = track(-0.48, 24)),
    /** v2 card titles: Inter 600 17/24. */
    val cardTitle: TextStyle = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    /** Compact top-bar title: Inter 600 17/22. */
    val barTitle: TextStyle = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    /** Segmented labels and chips: Inter 500 15/20. */
    val segment: TextStyle = base.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    /** body: Inter 400 16/24. */
    val body: TextStyle = base.copy(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    /** control: Inter 500 16/24 (buttons, selected navigation, emphasised rows). */
    val bodyStrong: TextStyle = base.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    /** tableNumber: Inter 500 16/24, tnum + lnum. */
    val tableNumber: TextStyle = base.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    /** secondary: Inter 400 14/20. */
    val secondary: TextStyle = base.copy(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    /** Short metadata (dates under rows); same size as secondary. */
    val small: TextStyle = base.copy(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    /** micro (v2): Inter 500 12/16 +0.72, uppercase, ink-3. */
    val micro: TextStyle = base.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = track(0.72, 12)),
    val button: TextStyle = base.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    val pill: TextStyle = base.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    /** Navigation labels (bar/rail). */
    val nav: TextStyle = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
)

val LocalBudgetType = staticCompositionLocalOf { BudgetType() }

/** Material typography mapped onto the same scale, so stock components (fields, dialogs) match. */
fun materialTypography(t: BudgetType) = Typography(
    displayLarge = t.hero.copy(fontSize = 44.sp),
    displayMedium = t.hero,
    displaySmall = t.title,
    headlineLarge = t.screenTitle,
    headlineMedium = t.title,
    headlineSmall = t.number,
    titleLarge = t.title,
    titleMedium = t.cardTitle,
    titleSmall = t.bodyStrong,
    bodyLarge = t.body,
    bodyMedium = t.body,
    bodySmall = t.secondary,
    labelLarge = t.button,
    labelMedium = t.small,
    labelSmall = t.micro,
)
