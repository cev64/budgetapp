package com.personal.budget.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.personal.budget.ui.theme.Budget
import com.personal.budget.ui.theme.LocalReduceMotion
import com.personal.budget.ui.theme.Motion
import com.personal.budget.ui.theme.Radius
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/** Drives a [GlassSheet]: [hide] plays the exit (220 ms ease) and suspends until it is off screen. */
@Stable
class GlassSheetState {
    internal val appear = Animatable(0f)
    internal val drag = Animatable(0f)
    internal var height by mutableFloatStateOf(0f)
    internal var reduce = false

    suspend fun hide() {
        if (reduce || height <= 0f) {
            appear.snapTo(0f)
            return
        }
        appear.animateTo(0f, tween(Motion.SHEET_OUT, easing = Motion.Ease))
    }
}

@Composable
fun rememberGlassSheetState(): GlassSheetState = remember { GlassSheetState() }

/**
 * Bottom sheet on strong glass, drawn inside the app window (FLUID_GLASS v2 §4, §7):
 * radius 28 (top corners), real backdrop blur of the app behind it on Android 12+, large soft shadow,
 * springs in (350 ms). **Drag to dismiss:** drag the grabber / header ([header]) down and the sheet
 * follows 1:1 while the scrim fades; dragging up rubber-bands (`−sqrt(|dy|)·4`). Releasing past
 * 120dp or faster than 0.6 dp/ms closes; otherwise it springs back (300 ms, spring-soft). Back and a
 * tap on the scrim close it too. Max width 640dp (centred on the inner screen).
 */
@Composable
fun GlassSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    state: GlassSheetState = rememberGlassSheetState(),
    header: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Budget.colors
    val density = LocalDensity.current
    val reduce = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    state.reduce = reduce
    var closing by remember { mutableStateOf(false) }
    val upPull = remember { mutableFloatStateOf(0f) }

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            state.hide()
            onDismiss()
        }
    }

    // Nothing under the sheet moves while it is open, so its blur is rendered as it slides, not re-rendered per drift tick.
    PauseAmbientDrift()
    LaunchedEffect(Unit) {
        if (reduce) state.appear.snapTo(1f) else state.appear.animateTo(1f, tween(Motion.SHEET_IN, easing = Motion.Spring))
    }
    BackHandler { close() }

    val shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet)
    val dragState = rememberDraggableState { dy ->
        scope.launch {
            val cur = state.drag.value
            if (dy > 0f || cur > 0f) {
                upPull.floatValue = 0f
                state.drag.snapTo((cur + dy).coerceAtLeast(0f))
            } else {
                upPull.floatValue += -dy
                state.drag.snapTo(-sqrt(upPull.floatValue) * 4f)
            }
        }
    }
    Box(modifier.fillMaxSize()) {
        // Scrim fades with the sheet's position.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val h = state.height.coerceAtLeast(1f)
                    alpha = (state.appear.value.coerceIn(0f, 1f) * (1f - (state.drag.value.coerceAtLeast(0f) / h))).coerceIn(0f, 1f)
                }
                .background(c.scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close") { close() }
                .semantics { contentDescription = "Close sheet" },
        )
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 24.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .onSizeChanged { state.height = it.height.toFloat() }
                    .graphicsLayer {
                        val h = if (state.height > 0f) state.height else size.height
                        translationY = (1f - state.appear.value) * (h + 24.dp.toPx()) + state.drag.value
                    }
                    .glassShadow(shape, large = true)
                    .blurredGlass(LocalShellHaze.current, shape)
                    .glassEdge(shape),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .draggable(
                            dragState,
                            Orientation.Vertical,
                            onDragStarted = { upPull.floatValue = 0f },
                            onDragStopped = { velocity ->
                                val v = velocity / density.density / 1000f // dp per ms
                                val d = state.drag.value / density.density
                                if (d > 120f || (v > .6f && d > 0f)) {
                                    close()
                                } else {
                                    upPull.floatValue = 0f
                                    if (reduce) state.drag.snapTo(0f) else state.drag.animateTo(0f, tween(Motion.SWIPE_BACK, easing = Motion.SpringSoft))
                                }
                            },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .padding(top = 10.dp, bottom = 6.dp)
                            .size(36.dp, 5.dp)
                            .background(c.ink3.copy(alpha = .35f), RoundedCornerShape(99.dp)),
                    )
                    header()
                }
                Column(Modifier.weight(1f, fill = false).fillMaxWidth()) { content() }
            }
        }
    }
}
