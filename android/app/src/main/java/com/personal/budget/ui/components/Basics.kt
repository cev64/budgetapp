package com.personal.budget.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.personal.budget.ui.theme.Budget
import com.personal.budget.ui.theme.LocalReduceMotion
import com.personal.budget.ui.theme.Motion
import com.personal.budget.ui.theme.Radius

/** Press feedback: scale to .97 (icon buttons .94) over 150 ms on the ease curve (FLUID_GLASS v2 §2, §7). */
fun Modifier.pressScale(interaction: MutableInteractionSource, scale: Float = Motion.PRESS_SCALE): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val s by animateFloatAsState(
        targetValue = if (pressed && !reduce) scale else 1f,
        animationSpec = tween(Motion.PRESS, easing = Motion.Ease),
        label = "press",
    )
    graphicsLayer {
        scaleX = s
        scaleY = s
    }
}

/**
 * The app's tap target: press scale + a quiet fill while pressed (no Material ripple, like the web
 * app's :active / hover).
 */
fun Modifier.tappable(
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(Radius.control),
    pressedFill: Color? = null,
    role: Role = Role.Button,
    label: String? = null,
    scale: Float = Motion.PRESS_SCALE,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill = pressedFill ?: Budget.colors.fill2
    val bg by animateColorAsState(if (pressed) fill else Color.Transparent, tween(Motion.HOVER, easing = Motion.Ease), label = "tapFill")
    this
        .pressScale(interaction, scale)
        .clip(shape)
        .background(bg)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role, onClickLabel = label, onClick = onClick)
}

/** Base content card (Fluid glass v2): translucent glass, hairline highlight, soft shadow, 20dp radius. */
@Composable
fun BudgetCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(Radius.card))
            .padding(padding),
        content = content,
    )
}

@Composable
fun CardHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Budget.type.cardTitle, color = Budget.colors.ink, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Micro label (v2): 12/500 uppercase, ink-3. */
@Composable
fun MicroLabel(text: String, modifier: Modifier = Modifier, color: Color = Budget.colors.ink3) {
    Text(text.uppercase(), style = Budget.type.micro, color = color, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    fill: Color? = null,
    ink: Color? = null,
    icon: ImageVector? = null,
) {
    val c = Budget.colors
    val bg = fill ?: if (accent) c.accentSoft else c.fill
    val fg = ink ?: if (accent) c.accentInk else c.ink2
    Row(
        modifier = modifier.clip(RoundedCornerShape(Radius.pill)).background(bg).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = Budget.type.pill, color = fg, maxLines = 1)
    }
}

@Composable
fun AppIcon(icon: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = Budget.colors.ink2, size: Dp = 18.dp) {
    Icon(icon, contentDescription, tint = tint, modifier = modifier.size(size))
}

/** 40dp ghost icon button with press scale. */
@Composable
fun GhostIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = Budget.colors.ink2,
    enabled: Boolean = true,
    size: Dp = 48.dp,
    iconSize: Dp = 22.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .tappable(enabled = enabled, shape = CircleShape, label = contentDescription, scale = Motion.ICON_PRESS_SCALE, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (enabled) tint else tint.copy(alpha = .4f), modifier = Modifier.size(iconSize))
    }
}

enum class ButtonKind { Primary, Secondary, Ghost, Danger, DangerSolid }

@Composable
fun BudgetButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Secondary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    large: Boolean = false,
) {
    val c = Budget.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // v2: primary = solid accent; everything else is quiet glass fill, no border.
    val (bg, fg, border) = when (kind) {
        ButtonKind.Primary -> Triple(if (pressed) c.accentInk else c.accent, c.onAccent, Color.Transparent)
        ButtonKind.Secondary -> Triple(if (pressed) c.fill2 else c.fill, c.ink, Color.Transparent)
        ButtonKind.Ghost -> Triple(if (pressed) c.fill2 else Color.Transparent, c.ink2, Color.Transparent)
        ButtonKind.Danger -> Triple(if (pressed) c.fill2 else c.fill, c.bad, Color.Transparent)
        ButtonKind.DangerSolid -> Triple(c.bad, if (c.isDark) c.bg else Color.White, Color.Transparent)
    }
    val bgAnim by animateColorAsState(bg, tween(Motion.HOVER, easing = Motion.Ease), label = "btnBg")
    val shape = RoundedCornerShape(Radius.control)
    Row(
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else .55f }
            .pressScale(interaction)
            .clip(shape)
            .background(bgAnim)
            .border(1.dp, border, shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = if (large) 18.dp else 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(if (large) 18.dp else 16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = Budget.type.button, color = fg, maxLines = 1)
    }
}

/**
 * v2 switch (§6): 48×28 track (`fill-2` off, accent on), 24dp white thumb that slides on the
 * spring-soft curve and stretches to 28dp while pressed.
 */
@Composable
fun BudgetSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Budget.colors
    val reduce = LocalReduceMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val spec = if (reduce) tween<Float>(0) else tween(Motion.THUMB, easing = Motion.SpringSoft)
    val pos by animateFloatAsState(if (checked) 1f else 0f, spec, label = "switchPos")
    val stretch by animateFloatAsState(if (pressed && !reduce) 1f else 0f, tween(Motion.PRESS, easing = Motion.Ease), label = "switchStretch")
    val track by animateColorAsState(if (checked) c.accent else c.fill2, tween(Motion.HOVER, easing = Motion.Ease), label = "switchTrack")
    // 48dp tall touch target around the 28dp track.
    Box(
        modifier
            .size(52.dp, 48.dp)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .semantics { stateDescription = if (checked) "On" else "Off" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(48.dp, 28.dp)
                .graphicsLayer { alpha = if (enabled) 1f else .5f }
                .clip(CircleShape)
                .background(track),
        ) {
            val thumbW = 24.dp + 4.dp * stretch
            Box(
                Modifier
                    .padding(2.dp)
                    .offset { IntOffset(((48.dp - 4.dp - thumbW) * pos).roundToPx(), 0) }
                    .size(thumbW, 24.dp)
                    .softShadow(CircleShape, Color.Black.copy(alpha = .18f), 4.dp, 1.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

/** Thin, quiet divider (v2: rarely used; space separates rows). Kept for totals rules. */
@Composable
fun Hairline(modifier: Modifier = Modifier, inset: Dp = 0.dp) {
    val c = Budget.colors
    Box(modifier.fillMaxWidth().padding(start = inset).height(1.dp).background(c.glassEdge.copy(alpha = if (c.isDark) .08f else .07f)))
}

/**
 * Fading colour wash (FLUID_GLASS `tint`): plays when [trigger] changes, never on first
 * composition. Use for "arrived" (accent), "went up" (green) and "went down" (red).
 */
fun Modifier.tintWash(trigger: Any?, color: Color, alpha: Float = .16f, shape: Shape = RoundedCornerShape(0.dp), playInitially: Boolean = false): Modifier = composed {
    val reduce = LocalReduceMotion.current
    val a = remember { Animatable(0f) }
    val first = remember { booleanArrayOf(!playInitially) }
    LaunchedEffect(trigger) {
        if (first[0]) {
            first[0] = false
            return@LaunchedEffect
        }
        if (reduce) return@LaunchedEffect
        a.snapTo(alpha)
        a.animateTo(0f, tween(Motion.TINT, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
    }
    this.clip(shape).drawBehind { if (a.value > 0f) drawRect(color.copy(alpha = a.value)) }
}

@Composable
fun EmptyState(title: String, body: String? = null, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = Budget.type.cardTitle, color = Budget.colors.ink)
        if (body != null) {
            Spacer(Modifier.height(4.dp))
            Text(body, style = Budget.type.secondary, color = Budget.colors.ink2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        if (action != null) {
            Spacer(Modifier.height(14.dp))
            action()
        }
    }
}

/** A small coloured dot for categories. */
@Composable
fun Dot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier, valueStyle: TextStyle = Budget.type.bodyStrong, valueColor: Color = Budget.colors.ink) {
    Column(modifier) {
        MicroLabel(label)
        Spacer(Modifier.height(2.dp))
        Text(value, style = valueStyle, color = valueColor, maxLines = 1)
    }
}
