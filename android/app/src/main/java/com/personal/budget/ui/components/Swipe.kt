package com.personal.budget.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.personal.budget.ui.theme.Budget
import com.personal.budget.ui.theme.LocalReduceMotion
import com.personal.budget.ui.theme.Motion
import com.personal.budget.ui.theme.Radius
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Swipe to delete (FLUID_GLASS v2 §7, adapted from the Bets swipe-to-grade): drag a row left to
 * reveal a quiet `bad` underlay with a trash icon. The drag locks horizontally after the touch slop;
 * the action arms at 96dp or 30 % of the row (the icon grows and a haptic tick fires); past that the
 * row follows at 35 % (rubber band). Releasing armed slides the row out (220 ms) and calls
 * [onDelete] (the caller shows the Undo toast); otherwise it springs back (300 ms, spring-soft).
 * Dragging right only rubber-bands. Accessibility services get a "Delete" action instead.
 */
@Composable
fun SwipeToDelete(
    resetKey: Any,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val c = Budget.colors
    val view = LocalView.current
    val reduce = LocalReduceMotion.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offset = remember(resetKey) { Animatable(0f) }
    var width by remember { mutableIntStateOf(0) }
    var armed by remember(resetKey) { mutableStateOf(false) }
    val raw = remember(resetKey) { mutableFloatStateOf(0f) }
    val armPx = minOf(with(density) { 96.dp.toPx() }, width * .3f).coerceAtLeast(1f)
    val iconScale by animateFloatAsState(if (armed) 1.15f else .85f, tween(Motion.PRESS, easing = Motion.Spring), label = "armIcon")
    val underlayA by animateFloatAsState(if (armed) .16f else .09f, tween(Motion.PRESS, easing = Motion.Ease), label = "armTone")

    Box(
        modifier
            .onSizeChanged { width = it.width }
            .clip(RoundedCornerShape(Radius.row))
            .semantics { customActions = listOf(CustomAccessibilityAction("Delete") { onDelete(); true }) },
    ) {
        if (offset.value < -0.5f) {
            Box(Modifier.matchParentSize().background(c.bad.copy(alpha = underlayA))) {
                Icon(
                    Lucide.Trash2,
                    null,
                    tint = c.bad,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(horizontal = 22.dp)
                        .size(22.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                )
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .pointerInput(enabled, resetKey, armPx) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { raw.floatValue = offset.value },
                        onDragEnd = {
                            val wasArmed = armed
                            armed = false
                            scope.launch {
                                if (wasArmed) {
                                    if (!reduce) offset.animateTo(-width.toFloat(), tween(Motion.SHEET_OUT, easing = Motion.Ease)) else offset.snapTo(-width.toFloat())
                                    onDelete()
                                } else if (reduce) {
                                    offset.snapTo(0f)
                                } else {
                                    offset.animateTo(0f, tween(Motion.SWIPE_BACK, easing = Motion.SpringSoft))
                                }
                            }
                        },
                        onDragCancel = {
                            armed = false
                            scope.launch { offset.animateTo(0f, tween(Motion.SWIPE_BACK, easing = Motion.SpringSoft)) }
                        },
                    ) { change, dx ->
                        change.consume()
                        raw.floatValue += dx
                        val r = raw.floatValue
                        val shown = when {
                            r > 0f -> sqrt(r) * 4f // rightward: rubber band only
                            -r <= armPx -> r
                            else -> -(armPx + (-r - armPx) * .35f)
                        }
                        scope.launch { offset.snapTo(shown) }
                        val nowArmed = r <= -armPx
                        if (nowArmed != armed) {
                            armed = nowArmed
                            if (nowArmed) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }
                }
                // While dragged, the row needs an opaque-enough face so the underlay doesn't show through.
                .then(if (abs(offset.value) > 0.5f) Modifier.background(c.glassFill(false).compositeOver(c.page)) else Modifier),
        ) { content() }
    }
}
