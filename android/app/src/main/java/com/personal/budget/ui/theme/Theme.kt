package com.personal.budget.ui.theme

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.personal.budget.data.local.ThemeMode

/** True when the system "Remove animations" setting is on: every animation is skipped. */
val LocalReduceMotion = staticCompositionLocalOf { false }

fun BudgetColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSoft,
        onPrimaryContainer = accentInk,
        inversePrimary = accentInk,
        secondary = ink2,
        onSecondary = bg,
        secondaryContainer = surface2,
        onSecondaryContainer = ink,
        tertiary = good,
        onTertiary = onAccent,
        background = bg,
        onBackground = ink,
        surface = bg,
        onSurface = ink,
        surfaceVariant = surface2,
        onSurfaceVariant = ink2,
        surfaceTint = Color.Transparent,
        inverseSurface = toast.copy(alpha = 1f),
        inverseOnSurface = onToast,
        error = bad,
        onError = Color.White,
        errorContainer = bad.copy(alpha = .12f),
        onErrorContainer = bad,
        outline = line2,
        outlineVariant = line,
        scrim = scrim,
        surfaceBright = card,
        surfaceDim = surface,
        surfaceContainerLowest = card,
        surfaceContainerLow = card,
        surfaceContainer = card,
        surfaceContainerHigh = card,
        surfaceContainerHighest = surface2,
    )
}

/** Dynamic colour (opt-in): keep the token structure, take hues from the wallpaper scheme. */
private fun BudgetColors.withDynamic(s: ColorScheme): BudgetColors = copy(
    accent = s.primary,
    accentInk = if (isDark) s.primary else s.onPrimaryContainer,
    accentSoft = s.primaryContainer,
    ink = s.onSurface,
    ink2 = s.onSurfaceVariant,
    bg = s.surface,
    card = s.surfaceContainerLowest,
    surface = s.surfaceContainerLow,
    surface2 = s.surfaceContainerHigh,
    line = s.outlineVariant,
    line2 = s.outline,
    glassBar = s.surface.copy(alpha = glassBar.alpha),
    glassCard = s.surfaceContainerLow.copy(alpha = glassCard.alpha),
)

@Composable
fun BudgetTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val tokens = if (dark) DarkTokens else LightTokens
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        tokens.withDynamic(if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context))
    } else {
        tokens
    }
    val type = remember { BudgetType() }
    val reduceMotion = remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
    CompositionLocalProvider(
        LocalBudgetColors provides colors,
        LocalBudgetType provides type,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
            typography = materialTypography(type),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(Radius.control),
                small = RoundedCornerShape(Radius.control),
                medium = RoundedCornerShape(Radius.card),
                large = RoundedCornerShape(Radius.sheet),
                extraLarge = RoundedCornerShape(Radius.sheet),
            ),
            content = content,
        )
    }
}

/** Shorthands. */
object Budget {
    val colors: BudgetColors @Composable get() = LocalBudgetColors.current
    val type: BudgetType @Composable get() = LocalBudgetType.current
}
