package com.personal.budget.ui.components

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.personal.budget.ui.theme.Budget
import com.personal.budget.ui.theme.BudgetColors
import com.personal.budget.ui.theme.LocalReduceMotion
import com.personal.budget.ui.theme.Radius
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import kotlinx.coroutines.flow.first

/*
 * Fluid glass v2 (docs/FLUID_GLASS_UI.md §3–4): a soft ambient field behind everything, content on
 * translucent glass. Real backdrop blur (Haze, RenderEffect) is used for the chrome that floats over
 * scrolling content (condensed top bar, bottom nav pill) on Android 12+. Cards sit over the ambient
 * field, where a blur would look identical, so they are translucent glass without a render layer.
 * Below Android 12 every glass surface becomes the opaque brand card colour (the spec's fallback).
 */

/** Robolectric renders headless screenshots: no RenderEffect blur and no endless drift there. */
val isRobolectric: Boolean = Build.FINGERPRINT == "robolectric"

/** RenderEffect blur and translucent glass (API 31+); below that, the opaque fallback. */
val glassSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Shell-level blur source (content + backdrop) for the floating bottom nav. */
val LocalShellHaze = staticCompositionLocalOf<HazeState?> { null }

/** Screen-level blur source (the scrolling content) for the collapsing top bar. */
val LocalFrameHaze = staticCompositionLocalOf<HazeState?> { null }

fun BudgetColors.glassFill(strong: Boolean): Color = when {
    !glassSupported -> card
    strong -> glassStrong
    else -> glass
}

/**
 * Soft, diffuse shadow drawn *outside* [shape] only (clip-out), so translucent glass never shows a
 * grey cast through itself. [spread] is negative like CSS `0 12px 32px -12px`.
 */
fun Modifier.softShadow(shape: Shape, color: Color, blur: Dp, offsetY: Dp = 0.dp, spread: Dp = 0.dp): Modifier = drawBehind {
    if (color.alpha <= 0f) return@drawBehind
    val outline = shape.createOutline(size, layoutDirection, this)
    val outer = Path().apply { addOutline(outline) }
    val s = spread.toPx()
    val inner = shape.createOutline(Size((size.width + 2 * s).coerceAtLeast(1f), (size.height + 2 * s).coerceAtLeast(1f)), layoutDirection, this)
    val shadowPath = Path().apply { addOutline(inner) }
    clipPath(outer, ClipOp.Difference) {
        translate(-s, -s) {
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = android.graphics.Color.BLACK
                    // CSS blur radius ≈ 2σ; setShadowLayer radius → σ ≈ 0.577·r + 0.5.
                    setShadowLayer((blur.toPx() * 0.85f).coerceAtLeast(0.5f), 0f, offsetY.toPx(), color.toArgb())
                }
                canvas.nativeCanvas.drawPath(shadowPath.asAndroidPath(), paint)
            }
        }
    }
}

/** `--glass-shadow` (cards, tiles) or `--glass-shadow-lg` (sheets, menus, floating nav). */
fun Modifier.glassShadow(shape: Shape, large: Boolean = false): Modifier = composed {
    val c = Budget.colors
    if (large) {
        softShadow(shape, c.shadow.copy(alpha = c.shadowLgAlpha), 64.dp, 24.dp, (-16).dp)
    } else {
        this
            .softShadow(shape, c.shadow.copy(alpha = if (c.isDark) .25f else .04f), 2.dp, 1.dp)
            .softShadow(shape, c.shadow.copy(alpha = c.shadowAlpha), if (c.isDark) 40.dp else 32.dp, if (c.isDark) 16.dp else 12.dp, if (c.isDark) (-14).dp else (-12).dp)
    }
}

/** Hairline ring with the inset top highlight (`--glass-edge` + `--glass-hi`). */
fun Modifier.glassEdge(shape: Shape): Modifier = composed {
    val c = Budget.colors
    border(1.dp, Brush.verticalGradient(0f to c.glassHi, .18f to c.glassEdge, 1f to c.glassEdge), shape)
}

/**
 * Sheets, dialogs, menus and tooltips either live in their own window (no backdrop to blur) or sit
 * over busy content, so their `glass-strong` is composited onto the page tone: the same colour,
 * fully readable.
 */
fun BudgetColors.glassSolid(): Color = if (glassSupported) glassStrong.compositeOver(page) else card

/** A glass surface: soft shadow, translucent fill, hairline highlight. */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(Radius.card),
    strong: Boolean = false,
    largeShadow: Boolean = false,
    shadow: Boolean = true,
): Modifier = composed {
    val c = Budget.colors
    this
        .then(if (shadow) Modifier.glassShadow(shape, largeShadow) else Modifier)
        .clip(shape)
        .background(if (strong) c.glassSolid() else c.glassFill(false))
        .glassEdge(shape)
}

/**
 * Chrome glass with real backdrop blur over [state]'s sources (24dp, tinted `--glass-strong`).
 * Falls back to the plain glass fill when blur is unavailable.
 */
fun Modifier.blurredGlass(state: HazeState?, shape: Shape, alpha: Float = 1f): Modifier = composed {
    val c = Budget.colors
    val blur = state != null && glassSupported && !isRobolectric
    val base = this.clip(shape)
    if (blur) {
        base.hazeBlur(
            HazeInput.Sources(state!!),
            HazeBlurStyle {
                blurRadius(24.dp)
                noiseFactor(0f)
                backgroundColor(c.page)
                colorEffects(listOf(HazeColorEffect.tint(c.glassStrong)))
                alpha(alpha)
            },
        )
    } else {
        // No blur behind it: the strong glass tone made opaque, so scrolling content never shows through.
        base.background(c.glassSolid().copy(alpha = alpha))
    }
}

/** `--bar-fade`: how far below the top bar its tint and blur ease out to nothing. */
val BarFade = 28.dp

/**
 * What sits behind the condensed top bar (v2 §5): a progressive fade, not a panel. The tint (page
 * 92% → 84% at the bar's bottom edge) and the 24dp blur run solid behind the bar (status bar
 * included), then ease out to fully transparent over [BarFade] below it, so content dissolves under
 * the bar instead of being cut at a line. No shadow, no hairline, no hard bottom edge.
 *
 * Android 12+: Haze blur of [state] masked with the same fade. Below 12 (and in headless
 * screenshots) the tint gradient alone: the page tone, opaque behind the bar since nothing is
 * blurred under it, easing out over the same [BarFade].
 * [alpha] (the condense transition) is Haze's own alpha or the gradient's draw alpha: never a
 * graphicsLayer around the blurred layer.
 */
@Composable
fun BoxScope.TopBarBackdrop(state: HazeState?, alpha: () -> Float) {
    val c = Budget.colors
    val blur = state != null && glassSupported && !isRobolectric
    val fadePx = with(LocalDensity.current) { BarFade.toPx() }
    val area = Modifier.matchParentSize().extendBelow(BarFade)
    if (blur) {
        val a = alpha()
        val tint = remember(c.page, fadePx) { BarFadeBrush(fadePx, c.page.copy(alpha = .92f), c.page.copy(alpha = .84f)) }
        val mask = remember(fadePx) { BarFadeBrush(fadePx, Color.Black, Color.Black) }
        Box(
            area.hazeBlur(
                HazeInput.Sources(state!!),
                HazeBlurStyle {
                    blurRadius(24.dp)
                    noiseFactor(0f)
                    backgroundColor(c.page)
                    colorEffects(listOf(HazeColorEffect.tint(tint)))
                    mask(mask)
                    alpha(a)
                },
            ),
        )
    } else {
        // Without a blur nothing may show through behind the bar; below it the tint fades the way tint × mask does above.
        val fade = remember(c.page, fadePx) { BarFadeBrush(fadePx, c.page, c.page, squared = true) }
        Box(
            area.drawBehind {
                val a = alpha()
                if (a > 0f) drawRect(fade, alpha = a)
            },
        )
    }
}

/** Lays the node out [extra] taller than its parent, overflowing downwards; the parent keeps its size. */
private fun Modifier.extendBelow(extra: Dp): Modifier = layout { measurable, constraints ->
    val w = constraints.maxWidth
    val h = constraints.maxHeight
    val placeable = measurable.measure(Constraints.fixed(w, h + extra.roundToPx()))
    layout(w, h) { placeable.place(0, 0) }
}

/**
 * Vertical fade sized to whatever it paints: [top] → [edge] down to the bar's bottom edge (the
 * height less [fadePx]), then out to transparent over [fadePx]. [squared] eases the fade like a
 * linear tint under a linear mask (the blurred path), for the tint-only fallback.
 */
private class BarFadeBrush(
    private val fadePx: Float,
    private val top: Color,
    private val edge: Color,
    private val squared: Boolean = false,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val solid = if (size.height > 0f) ((size.height - fadePx) / size.height).coerceIn(0f, 1f) else 0f
        val colors = if (squared) {
            listOf(top, edge, edge.copy(alpha = edge.alpha * .25f), edge.copy(alpha = 0f))
        } else {
            listOf(top, edge, edge.copy(alpha = 0f))
        }
        val stops = if (squared) listOf(0f, solid, solid + (1f - solid) * .5f, 1f) else listOf(0f, solid, 1f)
        return LinearGradientShader(Offset.Zero, Offset(0f, size.height), colors, stops)
    }

    override fun equals(other: Any?): Boolean =
        other is BarFadeBrush && other.fadePx == fadePx && other.top == top && other.edge == edge && other.squared == squared

    override fun hashCode(): Int = ((fadePx.hashCode() * 31 + top.hashCode()) * 31 + edge.hashCode()) * 31 + squared.hashCode()
}

/**
 * Sheets, dialogs and menus that are open right now. The ambient drift pauses while any is, so the
 * blurred chrome and sheet glass aren't re-rendered under an opening overlay (that re-render, every
 * tick of the drift, is what made the sheet's blur step instead of glide in).
 */
object OpenOverlays {
    var count by mutableIntStateOf(0)
        private set

    internal fun opened() { count++ }
    internal fun closed() { count = (count - 1).coerceAtLeast(0) }
}

/** Marks an overlay open while this is composed and [open]: the ambient drift holds still meanwhile. */
@Composable
fun PauseAmbientDrift(open: Boolean = true) {
    if (!open) return
    DisposableEffect(Unit) {
        OpenOverlays.opened()
        onDispose { OpenOverlays.closed() }
    }
}

/**
 * The ambient field (§3): page base plus three soft radial blobs drifting slowly (36 / 44 / 52 s,
 * alternate, ease-in-out). Static with "Remove animations". The drift is so slow (≈ 0.1 px per
 * frame) that it is sampled at ~12 fps: the field only redraws (no recomposition), and the device
 * isn't asked for 60 fps just for the background. It holds still while a sheet, dialog or menu is
 * open ([PauseAmbientDrift]) and resumes where it left off.
 */
@Composable
fun AmbientBackdrop(modifier: Modifier = Modifier) {
    val c = Budget.colors
    val still = LocalReduceMotion.current || isRobolectric
    val clock = remember { mutableLongStateOf(0L) }
    if (!still) {
        LaunchedEffect(Unit) {
            var elapsed = clock.longValue
            var prev = withFrameMillis { it }
            while (true) {
                if (OpenOverlays.count > 0) {
                    snapshotFlow { OpenOverlays.count }.first { it == 0 }
                    prev = withFrameMillis { it }
                }
                withFrameMillis { now ->
                    elapsed += now - prev
                    prev = now
                    if (elapsed - clock.longValue >= 83L) clock.longValue = elapsed
                }
            }
        }
    }
    Box(modifier.fillMaxSize().background(c.page)) {
        Canvas(Modifier.fillMaxSize()) {
            val t = clock.longValue
            val vmax = maxOf(size.width, size.height)
            fun blob(color: Color, diameter: Float, cx: Float, cy: Float, period: Long, dx: Float, dy: Float) {
                val p = pingPong(t, period)
                val r = diameter / 2 * (1f + .1f * p)
                val center = Offset(cx + dx * p * .06f * vmax, cy + dy * p * .06f * vmax)
                drawCircle(Brush.radialGradient(0f to color, .6f to color.copy(alpha = 0f), center = center, radius = r), r, center)
            }
            blob(c.blobA, .70f * vmax, size.width * .1f, size.height * .08f, 36_000L, 1f, .6f)
            blob(c.blobB, .60f * vmax, size.width * .95f, size.height * .92f, 44_000L, -.8f, -1f)
            blob(c.blobC, .50f * vmax, size.width * .5f, size.height * .72f, 52_000L, .7f, -.5f)
        }
    }
}

/** 0 → 1 → 0 over 2 × [period] ms, eased in and out (CSS `alternate` + ease-in-out). */
private fun pingPong(t: Long, period: Long): Float {
    if (t <= 0L) return 0f
    val phase = (t % (2 * period)).toFloat() / period
    val x = if (phase <= 1f) phase else 2f - phase
    return x * x * (3f - 2f * x)
}
