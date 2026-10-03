package com.personal.budget.ui.components

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.personal.budget.ui.theme.Budget
import com.personal.budget.ui.theme.LocalReduceMotion
import com.personal.budget.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The FLIP list from FLUID_GLASS §7.1, for small keyed lists inside cards:
 *  - rows that change place glide from their old position (650 ms ease) and wash green if they
 *    moved up / red if they moved down (relative order among surviving rows);
 *  - new rows fade + rise 6 px and wash [arrivalTint] (accent by default);
 *  - removed rows linger for 900 ms, sinking 14 px and fading out with a red wash.
 * Nothing animates on first composition or with reduced motion.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun <T> AnimatedList(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    arrivalTint: Color = Budget.colors.tintAccent,
    content: @Composable (T) -> Unit,
) {
    val reduce = LocalReduceMotion.current
    if (reduce) {
        Column(modifier) { items.forEach { item -> key(key(item)) { content(item) } } }
        return
    }
    val c = Budget.colors
    val state = remember { ListMemory<T>() }
    val tick = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val observeTick = tick.intValue
    val currentKeys = items.map(key)

    // Diff against the previous composition.
    val firstFrame = state.previousOrder == null
    val prevOrder = state.previousOrder.orEmpty()
    val survivors = currentKeys.filter { it in prevOrder }
    val prevSurvivorOrder = prevOrder.filter { it in currentKeys.toSet() }
    val moved = HashMap<Any, Boolean>() // key -> movedUp
    if (!firstFrame) {
        survivors.forEachIndexed { newRank, k ->
            val oldRank = prevSurvivorOrder.indexOf(k)
            if (oldRank != newRank) moved[k] = newRank < oldRank
        }
        prevOrder.forEachIndexed { index, k ->
            if (k !in currentKeys && state.exiting.none { it.key == k }) {
                state.lastItems[k]?.let { state.exiting += Exiting(k, it, index, System.nanoTime()) }
            }
        }
    }
    val arrivals = if (firstFrame) emptySet() else currentKeys.filter { it !in prevOrder }.toSet()

    // Merge exiting rows back at their old index so they can sink away in place.
    val rows = ArrayList<Pair<Any, T>>(items.size + state.exiting.size)
    items.forEach { rows += key(it) to it }
    state.exiting.sortedBy { it.index }.forEach { ex ->
        if (rows.none { it.first == ex.key }) rows.add(ex.index.coerceAtMost(rows.size), ex.key to ex.item)
    }

    LookaheadScope {
        Column(modifier) {
            rows.forEach { (k, item) ->
                key(k) {
                    val exiting = state.exiting.firstOrNull { it.key == k }
                    val isArrival = k in arrivals
                    val movedUp = moved[k]
                    RowAnimator(
                        exiting = exiting != null,
                        arrival = isArrival,
                        movedUp = movedUp,
                        moveToken = state.generation,
                        arrivalTint = arrivalTint,
                        upTint = c.tintUp,
                        downTint = c.tintDown,
                        onExitDone = {
                            state.exiting.removeAll { it.key == k }
                            tick.intValue++
                        },
                        modifier = Modifier.animateBounds(
                            lookaheadScope = this@LookaheadScope,
                            boundsTransform = BoundsTransform { _, _ -> tween(Motion.REORDER, easing = Motion.Ease) },
                        ),
                    ) { content(item) }
                }
            }
        }
    }

    // Remember this composition for the next diff.
    state.previousOrder = currentKeys
    items.forEach { state.lastItems[key(it)] = it }
    if (moved.isNotEmpty() || arrivals.isNotEmpty()) state.generation++
}

private class Exiting<T>(val key: Any, val item: T, val index: Int, val startedAt: Long)

private class ListMemory<T> {
    var previousOrder: List<Any>? = null
    val lastItems = HashMap<Any, T>()
    val exiting = ArrayList<Exiting<T>>()
    var generation = 0
}

@Composable
private fun RowAnimator(
    exiting: Boolean,
    arrival: Boolean,
    movedUp: Boolean?,
    moveToken: Int,
    arrivalTint: Color,
    upTint: Color,
    downTint: Color,
    onExitDone: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val alpha = remember { Animatable(if (arrival) 0f else 1f) }
    val dy = remember { Animatable(if (arrival) with(density) { 6.dp.toPx() } else 0f) }
    val wash = remember { Animatable(0f) }
    val washColor = remember { arrayOf(arrivalTint) }

    LaunchedEffect(arrival) {
        if (!arrival) return@LaunchedEffect
        washColor[0] = arrivalTint
        launch { alpha.animateTo(1f, tween(Motion.APPEAR, easing = Motion.Ease)) }
        launch { dy.animateTo(0f, tween(Motion.APPEAR, easing = Motion.Ease)) }
        wash.snapTo(.16f)
        wash.animateTo(0f, tween(Motion.TINT, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
    }
    LaunchedEffect(movedUp, moveToken) {
        if (movedUp == null) return@LaunchedEffect
        washColor[0] = if (movedUp) upTint else downTint
        wash.snapTo(.16f)
        wash.animateTo(0f, tween(Motion.TINT, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
    }
    LaunchedEffect(exiting) {
        if (!exiting) return@LaunchedEffect
        washColor[0] = downTint
        wash.snapTo(.18f)
        launch { wash.animateTo(0f, tween(900, easing = Motion.Ease)) }
        launch { dy.animateTo(with(density) { 14.dp.toPx() }, tween(900, easing = Motion.Ease)) }
        alpha.animateTo(0f, tween(900, easing = Motion.Ease))
        delay(16)
        onExitDone()
    }
    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alpha.value
                translationY = dy.value
            }
            .drawBehind {
                val a = wash.value
                if (a > 0f) drawRect(washColor[0].copy(alpha = a))
            },
    ) { content() }
}
