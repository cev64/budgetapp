package com.personal.budget.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.personal.budget.domain.usecase.Money
import com.personal.budget.ui.theme.Budget
import com.personal.budget.ui.theme.LocalReduceMotion
import com.personal.budget.ui.theme.Motion

/**
 * A number that rolls to its new value in the direction of travel (380 ms) with a small spring
 * bump (scale 1.04) when it changes. Nothing animates on first composition or with reduced motion.
 */
@Composable
fun AnimatedMoney(
    value: Double?,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    blank: String = "",
    format: (Double) -> String = { Money.format(it) },
    textAlign: TextAlign? = null,
) {
    val text = value?.let(format) ?: blank
    RollingText(text = text, numeric = value ?: 0.0, style = style, color = color, modifier = modifier, textAlign = textAlign)
}

@Composable
fun RollingText(
    text: String,
    numeric: Double,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    val reduce = LocalReduceMotion.current
    if (reduce) {
        Text(text, style = style, color = color, modifier = modifier, maxLines = 1, textAlign = textAlign)
        return
    }
    val previous = remember { doubleArrayOf(numeric) }
    val up = numeric >= previous[0]
    val scale = remember { Animatable(1f) }
    val firstText = remember { mutableStateOf(true) }
    LaunchedEffect(text) {
        if (firstText.value) {
            firstText.value = false
            return@LaunchedEffect
        }
        scale.snapTo(1f)
        scale.animateTo(1.04f, tween(160, easing = Motion.Spring))
        scale.animateTo(1f, tween(220, easing = Motion.Spring))
    }
    AnimatedContent(
        targetState = text,
        modifier = modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
        transitionSpec = {
            val dir = if (up) 1 else -1
            (slideInVertically(tween(Motion.NUMBER_ROLL, easing = Motion.Ease)) { h -> (h * .55f).toInt() * dir } + fadeIn(tween(Motion.NUMBER_ROLL, easing = Motion.Ease)))
                .togetherWith(slideOutVertically(tween(Motion.NUMBER_ROLL, easing = Motion.Ease)) { h -> -(h * .55f).toInt() * dir } + fadeOut(tween(260, easing = Motion.Ease)))
                .using(SizeTransform(clip = false))
        },
        label = "roll",
    ) { t ->
        Text(t, style = style, color = color, maxLines = 1, textAlign = textAlign)
    }
    previous[0] = numeric
}

/**
 * actual / expected bar, capped visually at 100 %. Past 100 % the last part of the bar is the
 * over-budget tail ([overColor], `bad` for expenses). Width animates over 600 ms ease.
 */
@Composable
fun BudgetProgress(
    actual: Double,
    expected: Double,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    color: Color = Budget.colors.accent,
    overColor: Color = Budget.colors.bad,
) {
    val c = Budget.colors
    val reduce = LocalReduceMotion.current
    val a = actual.coerceAtLeast(0.0)
    val e = expected.coerceAtLeast(0.0)
    val (main, tail) = when {
        e <= 0.0 && a <= 0.0 -> 0f to 0f
        e <= 0.0 -> 0f to 1f
        a <= e -> (a / e).toFloat() to 0f
        else -> (e / a).toFloat() to (1f - (e / a).toFloat())
    }
    val mainAnim by animateFloatAsState(main, if (reduce) tween(0) else tween(Motion.PROGRESS, easing = Motion.Ease), label = "progMain")
    val tailAnim by animateFloatAsState(tail, if (reduce) tween(0) else tween(Motion.PROGRESS, easing = Motion.Ease), label = "progTail")
    val shape = RoundedCornerShape(99.dp)
    BoxWithConstraints(modifier.fillMaxWidth().height(height).clip(shape).background(c.fill2)) {
        val w = maxWidth
        Box(Modifier.fillMaxHeight().width(w * mainAnim).clip(shape).background(color))
        if (tailAnim > 0f) {
            Box(Modifier.fillMaxHeight().offset(x = w * (1f - tailAnim)).width(w * tailAnim).clip(shape).background(overColor))
        }
    }
}

/** Stacked 8px bar of shares (e.g. spent / saved / left as shares of income). */
@Composable
fun StackedBar(segments: List<Pair<Float, Color>>, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val c = Budget.colors
    val reduce = LocalReduceMotion.current
    val shape = RoundedCornerShape(99.dp)
    BoxWithConstraints(modifier.fillMaxWidth().height(height).clip(shape).background(c.fill2)) {
        val w = maxWidth
        var start = 0f
        segments.forEach { (share, color) ->
            val s = share.coerceIn(0f, 1f - start)
            val anim by animateFloatAsState(s, if (reduce) tween(0) else tween(Motion.PROGRESS, easing = Motion.Ease), label = "seg")
            val off by animateFloatAsState(start, if (reduce) tween(0) else tween(Motion.PROGRESS, easing = Motion.Ease), label = "segOff")
            Box(Modifier.fillMaxHeight().offset(x = w * off).width(w * anim).background(color))
            start += s
        }
    }
}

/**
 * Segmented control (FLUID_GLASS v2 §6): pill track in `fill` with 3dp padding; the selection is a
 * raised `thumb` (white in light) that slides on the spring-soft curve (350 ms). The thumb is placed
 * without animation first, so nothing slides in on first show.
 */
@Composable
fun <T> SegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
    /** Reports the selected segment's x and width within this control, e.g. to scroll it into view. */
    onSelectedPlaced: ((x: Dp, width: Dp) -> Unit)? = null,
    itemPadding: Dp = 14.dp,
) {
    val c = Budget.colors
    val density = LocalDensity.current
    val reduce = LocalReduceMotion.current
    val positions = remember { mutableStateMapOf<Int, Pair<Dp, Dp>>() }
    var ready by remember { mutableStateOf(false) }
    val selIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val target = positions[selIndex]
    val x by animateDpAsState(target?.first ?: 0.dp, if (ready && !reduce) tween(Motion.THUMB, easing = Motion.SpringSoft) else tween(0), label = "segX")
    val w by animateDpAsState(target?.second ?: 0.dp, if (ready && !reduce) tween(Motion.THUMB, easing = Motion.SpringSoft) else tween(0), label = "segW")
    LaunchedEffect(target != null) {
        if (target != null) {
            kotlinx.coroutines.delay(32)
            ready = true
        }
    }
    if (onSelectedPlaced != null) {
        LaunchedEffect(target) { target?.let { (tx, tw) -> onSelectedPlaced(tx + 3.dp, tw) } }
    }
    val shape = RoundedCornerShape(99.dp)
    Box(modifier.clip(shape).background(c.fill).padding(3.dp)) {
        if (target != null) {
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .offset { IntOffset(x.roundToPx(), 0) }
                        .width(w)
                        .fillMaxHeight()
                        .softShadow(shape, c.shadow.copy(alpha = if (c.isDark) .4f else .12f), 12.dp, 4.dp, (-2).dp)
                        .softShadow(shape, c.shadow.copy(alpha = if (c.isDark) .3f else .08f), 2.dp, 1.dp)
                        .clip(shape)
                        .background(c.thumb),
                )
            }
        }
        Row(if (fill) Modifier.fillMaxWidth() else Modifier) {
            options.forEachIndexed { i, (value, label) ->
                val interaction = remember { MutableInteractionSource() }
                val isSel = i == selIndex
                val color by androidx.compose.animation.animateColorAsState(if (isSel) c.ink else c.ink2, tween(Motion.HOVER, easing = Motion.Ease), label = "segInk")
                Box(
                    modifier = (if (fill) Modifier.weight(1f) else Modifier)
                        .onPlaced { coords ->
                            with(density) { positions[i] = coords.positionInParent().x.toDp() to coords.size.width.toDp() }
                        }
                        .pressScale(interaction, .96f)
                        .clip(shape)
                        .clickable(interactionSource = interaction, indication = null, role = Role.Tab) { onSelect(value) }
                        .semantics { this.selected = isSel }
                        .heightIn(min = 40.dp)
                        .padding(horizontal = itemPadding, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = Budget.type.segment, color = color, maxLines = 1)
                }
            }
        }
    }
}
