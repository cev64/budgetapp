package com.personal.budget.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.personal.budget.domain.usecase.Money
import com.personal.budget.ui.theme.Budget
import com.personal.budget.ui.theme.LocalReduceMotion
import com.personal.budget.ui.theme.Motion
import com.personal.budget.ui.theme.Radius

/** Text input: card fill, line-2 border, 8dp radius; the border turns accent on focus. */
@Composable
fun BudgetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onIme: (() -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    password: Boolean = false,
    textStyle: TextStyle = Budget.type.body,
    textAlign: TextAlign = TextAlign.Start,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    enabled: Boolean = true,
    onFocusLost: (() -> Unit)? = null,
) {
    val c = Budget.colors
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(if (focused) c.focusRing else c.controlBorder, tween(Motion.HOVER, easing = Motion.Ease), label = "fieldBorder")
    val focusManager = LocalFocusManager.current
    Column(modifier) {
        if (label != null) {
            MicroLabel(label)
            Spacer(Modifier.height(6.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = textStyle.copy(color = c.ink, textAlign = textAlign),
            cursorBrush = SolidColor(c.accent),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction, capitalization = capitalization),
            keyboardActions = KeyboardActions(onAny = {
                if (onIme != null) onIme() else if (imeAction == ImeAction.Done) focusManager.clearFocus() else focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Next)
            }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    if (focused && !it.isFocused) onFocusLost?.invoke()
                    focused = it.isFocused
                },
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(Radius.control))
                        .background(if (enabled) c.card else c.surface)
                        .border(if (focused) 2.dp else 1.dp, border, RoundedCornerShape(Radius.control))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                ) {
                    if (leading != null) {
                        leading()
                        Spacer(Modifier.size(8.dp))
                    }
                    Box(Modifier.weight(1f), contentAlignment = if (textAlign == TextAlign.End) Alignment.CenterEnd else Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(placeholder, style = textStyle, color = c.ink3, textAlign = textAlign, modifier = Modifier.fillMaxWidth())
                        }
                        inner()
                    }
                    if (trailing != null) {
                        Spacer(Modifier.size(8.dp))
                        trailing()
                    }
                }
            },
        )
    }
}

/**
 * An editable money value that commits on Done or when focus leaves (and only if it changed).
 * Blank commits null. Used for Expected / Actual / balances.
 */
@Composable
fun MoneyField(
    value: Double?,
    onCommit: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "—",
    allowBlank: Boolean = true,
    textStyle: TextStyle = Budget.type.bodyStrong,
    textAlign: TextAlign = TextAlign.End,
) {
    var text by rememberSaveable(value) { mutableStateOf(Money.formatInput(value)) }
    val commit = {
        val parsed = Money.parse(text)
        val newValue = if (text.isBlank()) null else parsed
        if (text.isNotBlank() && parsed == null) {
            text = Money.formatInput(value)
        } else if (newValue != value && (allowBlank || newValue != null)) {
            onCommit(newValue)
        } else if (!allowBlank && newValue == null) {
            text = Money.formatInput(value)
        }
    }
    BudgetTextField(
        value = text,
        onValueChange = { s -> text = s.filter { it.isDigit() || it in ".,-−" } },
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        keyboardType = KeyboardType.Decimal,
        imeAction = ImeAction.Done,
        textStyle = textStyle,
        textAlign = textAlign,
        leading = { Text("$", style = textStyle, color = Budget.colors.ink3) },
        onFocusLost = commit,
        onIme = null,
    )
}

/** Selectable tile ("pick"): accent-soft fill + accent border when on. */
@Composable
fun Pick(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    dotColor: Color? = null,
    mark: com.personal.budget.ui.theme.PaletteEntry? = null,
    dimmed: Boolean = false,
) {
    val c = Budget.colors
    val bg by animateColorAsState(if (selected) c.accentSoft else c.surface, tween(250, easing = Motion.Ease), label = "pickBg")
    val border by animateColorAsState(if (selected) c.accent else Color.Transparent, tween(250, easing = Motion.Ease), label = "pickBorder")
    val ink by animateColorAsState(if (selected) c.accentInk else c.ink, tween(250, easing = Motion.Ease), label = "pickInk")
    Row(
        modifier
            .graphicsLayer { alpha = if (dimmed && !selected) .5f else 1f }
            .tappable(shape = RoundedCornerShape(Radius.pick), pressedFill = c.surface2, onClick = onClick)
            .background(bg, RoundedCornerShape(Radius.pick))
            .border(1.5.dp, border, RoundedCornerShape(Radius.pick))
            .heightIn(min = 44.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mark != null) {
            CategoryMark(mark)
            Spacer(Modifier.size(8.dp))
        } else if (dotColor != null) {
            Dot(dotColor)
            Spacer(Modifier.size(6.dp))
        }
        if (icon != null) {
            Icon(icon, null, tint = ink, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(6.dp))
        }
        Text(text, style = Budget.type.body, color = ink, maxLines = 1)
    }
}

@Composable
fun <T> PickRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (v, label) -> Pick(label, v == selected, { onSelect(v) }) }
    }
}

/**
 * Modal sheet dialog (FLUID_GLASS §7.4): 18dp radius, springs in (translateY 14 → 0, scale
 * .96 → 1). Max width 420dp (wider when [wide]).
 */
@Composable
fun BudgetDialog(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Budget.colors
    val reduce = LocalReduceMotion.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val appear = remember { Animatable(if (reduce) 1f else 0f) }
        LaunchedEffect(Unit) { appear.animateTo(1f, tween(Motion.GLIDE, easing = Motion.Spring)) }
        Box(Modifier.fillMaxWidth().imePadding().padding(20.dp), contentAlignment = Alignment.Center) {
            Column(
                modifier
                    .widthIn(max = if (wide) 560.dp else 420.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = appear.value
                        alpha = (p * 1.6f).coerceIn(0f, 1f)
                        translationY = (1f - p) * 14.dp.toPx()
                        scaleX = .96f + .04f * p
                        scaleY = .96f + .04f * p
                    }
                    .shadow(24.dp, RoundedCornerShape(Radius.sheet), ambientColor = c.shadow.copy(alpha = .22f), spotColor = c.shadow.copy(alpha = .22f))
                    .clip(RoundedCornerShape(Radius.sheet))
                    .background(if (c.isDark) c.card else Color.White.copy(alpha = .96f))
                    .padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 20.dp),
            ) {
                Text(title, style = Budget.type.title, color = c.ink)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { content() }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    actions()
                }
            }
        }
    }
}

/** Promise-style confirm (title, body, yes/no). The destructive confirm is solid `bad`. */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    dismissLabel: String = "Cancel",
) {
    BudgetDialog(
        onDismiss = onDismiss,
        title = title,
        actions = {
            BudgetButton(dismissLabel, onDismiss, kind = ButtonKind.Secondary)
            BudgetButton(confirmLabel, onConfirm, kind = if (destructive) ButtonKind.DangerSolid else ButtonKind.Primary)
        },
    ) {
        Text(body, style = Budget.type.body, color = Budget.colors.ink2)
    }
}
