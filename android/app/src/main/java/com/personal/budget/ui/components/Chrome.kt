package com.personal.budget.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.budget.ui.theme.Budget
import com.personal.budget.ui.theme.LocalReduceMotion
import com.personal.budget.ui.theme.Motion

/** What the sync dot shows (docs/UI_ANATOMY.md top bar). */
sealed interface SyncIndicator {
    data object Synced : SyncIndicator
    data object Syncing : SyncIndicator
    data class Pending(val count: Int, val offline: Boolean) : SyncIndicator
    data class Error(val message: String) : SyncIndicator
    data object SignIn : SyncIndicator
    data object LocalOnly : SyncIndicator
}

/** Padding the app shell reserves (bottom bar on compact) that screens add inside their scroll. */
val LocalShellPadding = staticCompositionLocalOf { PaddingValues(0.dp) }

/** The rail shows the brand mark on medium/expanded, so the top bar only shows it on compact. */
val LocalShowMarkInTopBar = staticCompositionLocalOf { true }

/** Whether the app shell is showing the top-bar Settings gear etc. */
val LocalShowSettingsGear = staticCompositionLocalOf { true }

class TopBarActions(val onSync: () -> Unit, val onSettings: () -> Unit, val sync: SyncIndicator)

val LocalTopBarActions = staticCompositionLocalOf { TopBarActions({}, {}, SyncIndicator.Synced) }

@Composable
fun SyncDot(indicator: SyncIndicator, modifier: Modifier = Modifier) {
    val c = Budget.colors
    val reduce = LocalReduceMotion.current
    val color = when (indicator) {
        SyncIndicator.Synced -> c.good
        SyncIndicator.Syncing -> c.accent
        is SyncIndicator.Pending -> c.warn
        is SyncIndicator.Error -> c.bad
        SyncIndicator.SignIn -> c.warn
        SyncIndicator.LocalOnly -> c.ink3
    }
    val animated by animateColorAsState(color, tween(Motion.HOVER, easing = Motion.Ease), label = "dot")
    val pulse = if (indicator == SyncIndicator.Syncing && !reduce) {
        val t = rememberInfiniteTransition(label = "pulse")
        t.animateFloat(1f, .35f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulseA").value
    } else {
        1f
    }
    Box(modifier.size(9.dp).graphicsLayer { alpha = pulse }.clip(CircleShape).background(animated))
}

fun SyncIndicator.describe(): String = when (this) {
    SyncIndicator.Synced -> "Synced"
    SyncIndicator.Syncing -> "Syncing"
    is SyncIndicator.Pending -> if (offline) "Offline · $count pending" else "$count change${if (count == 1) "" else "s"} pending"
    is SyncIndicator.Error -> "Sync error: $message"
    SyncIndicator.SignIn -> "Sign in to sync"
    SyncIndicator.LocalOnly -> "Not syncing"
}

/**
 * Sticky glass top bar: micro-label above the Barlow screen title; sync dot and Settings gear on
 * the right. The shadow appears (and the hairline hides) only once content scrolls under it.
 */
@Composable
fun GlassTopBar(
    micro: String,
    title: String,
    scrolled: Boolean,
    modifier: Modifier = Modifier,
    navigation: (@Composable () -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    showMark: Boolean = LocalShowMarkInTopBar.current,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Budget.colors
    val acts = LocalTopBarActions.current
    val shadowAlpha by animateFloatAsState(if (scrolled) 1f else 0f, tween(Motion.HOVER, easing = Motion.Ease), label = "barShadow")
    Column(
        modifier
            .fillMaxWidth()
            .shadow((8 * shadowAlpha).dp, RectangleShape, clip = false, ambientColor = c.shadow.copy(alpha = .07f), spotColor = c.shadow.copy(alpha = .1f))
            .background(c.glassBar)
            .drawBehind {
                if (shadowAlpha < 1f) drawLine(c.line.copy(alpha = 1f - shadowAlpha), Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1.dp.toPx())
            }
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = if (navigation != null) 4.dp else 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (navigation != null) {
                navigation()
            } else if (showMark) {
                BrandMark(size = 28.dp, contentDescription = "Budget")
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                MicroLabel(micro)
                if (titleContent != null) {
                    titleContent()
                } else {
                    Text(title.uppercase(), style = Budget.type.screenTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            actions()
            Box(
                Modifier
                    .size(40.dp)
                    .tappable(shape = CircleShape, label = "Sync now", onClick = acts.onSync)
                    .semantics { contentDescription = acts.sync.describe() },
                contentAlignment = Alignment.Center,
            ) { SyncDot(acts.sync) }
            if (LocalShowSettingsGear.current) {
                GhostIconButton(Lucide.Settings, "Settings", onClick = acts.onSettings)
            }
        }
    }
}

/**
 * Screen frame: the content fills the whole area and scrolls *under* the glass top bar; the
 * padding passed to [content] reserves the bar's measured height plus the shell's bottom bar.
 */
@Composable
fun ScreenFrame(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(PaddingValues) -> Unit,
) {
    val density = LocalDensity.current
    var topHeight by remember { mutableStateOf(0.dp) }
    val shell = LocalShellPadding.current
    Box(modifier.fillMaxSize().background(Budget.colors.bg)) {
        content(PaddingValues(top = topHeight, bottom = shell.calculateBottomPadding()))
        Box(Modifier.fillMaxWidth().onSizeChanged { topHeight = with(density) { it.height.toDp() } }) { topBar() }
    }
}

data class NavItem(val route: String, val label: String, val icon: ImageVector)

/** Glass bottom bar (compact): 5 items, an accent-soft pill glides to the selected one. */
@Composable
fun GlassBottomBar(items: List<NavItem>, selectedRoute: String?, onSelect: (NavItem) -> Unit, modifier: Modifier = Modifier) {
    val c = Budget.colors
    val density = LocalDensity.current
    val reduce = LocalReduceMotion.current
    val positions = remember { mutableStateMapOf<Int, Pair<Dp, Dp>>() }
    var ready by remember { mutableStateOf(false) }
    val sel = items.indexOfFirst { it.route == selectedRoute }
    val target = positions[sel]
    val x by animateDpAsState(target?.first ?: 0.dp, if (ready && !reduce) tween(Motion.GLIDE, easing = Motion.Ease) else tween(0), label = "navX")
    LaunchedEffect(target != null) {
        if (target != null) {
            kotlinx.coroutines.delay(32)
            ready = true
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(c.glassBar)
            .drawBehind { drawLine(c.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .navigationBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().height(68.dp)) {
            if (target != null && sel >= 0) {
                Box(
                    Modifier
                        .offset(x = x + (target.second - 56.dp) / 2, y = 8.dp)
                        .size(56.dp, 30.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(c.accentSoft),
                )
            }
            Row(Modifier.fillMaxSize()) {
                items.forEachIndexed { i, item ->
                    val selected = i == sel
                    val ink by animateColorAsState(if (selected) c.accentInk else c.ink3, tween(Motion.HOVER, easing = Motion.Ease), label = "navInk")
                    val interaction = remember { MutableInteractionSource() }
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .onPlaced { with(density) { positions[i] = it.positionInParent().x.toDp() to it.size.width.toDp() } }
                            .pressScale(interaction)
                            .clickable(interactionSource = interaction, indication = null, role = Role.Tab) { onSelect(item) }
                            .semantics { this.selected = selected },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(item.icon, null, tint = ink, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(5.dp))
                        Text(item.label, style = Budget.type.nav, color = ink, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Navigation rail (medium / expanded): 80dp, Add at the top. */
@Composable
fun GlassRail(
    items: List<NavItem>,
    selectedRoute: String?,
    onSelect: (NavItem) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Budget.colors
    Column(
        modifier
            .fillMaxHeight()
            .width(80.dp)
            .background(c.glassBar)
            .drawBehind { drawLine(c.line, Offset(size.width - 1f, 0f), Offset(size.width - 1f, size.height), 1.dp.toPx()) }
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
            .statusBarsPadding()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandMark(size = 30.dp, contentDescription = "Budget")
        Spacer(Modifier.height(18.dp))
        AddButtonSquare(onAdd)
        Spacer(Modifier.height(20.dp))
        items.forEach { item ->
            val selected = item.route == selectedRoute
            val ink by animateColorAsState(if (selected) c.accentInk else c.ink3, tween(Motion.HOVER, easing = Motion.Ease), label = "railInk")
            val pill by animateColorAsState(if (selected) c.accentSoft else Color.Transparent, tween(Motion.GLIDE, easing = Motion.Ease), label = "railPill")
            val interaction = remember { MutableInteractionSource() }
            Column(
                Modifier
                    .padding(vertical = 6.dp)
                    .width(72.dp)
                    .pressScale(interaction)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Tab) { onSelect(item) }
                    .semantics { this.selected = selected },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(56.dp, 32.dp).clip(RoundedCornerShape(99.dp)).background(pill), contentAlignment = Alignment.Center) {
                    Icon(item.icon, null, tint = ink, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(item.label, style = Budget.type.nav, color = ink, maxLines = 1)
            }
        }
    }
}

@Composable
private fun AddButtonSquare(onAdd: () -> Unit) {
    val c = Budget.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(52.dp)
            .pressScale(interaction)
            .shadow(6.dp, RoundedCornerShape(16.dp), ambientColor = c.shadow.copy(alpha = .2f), spotColor = c.shadow.copy(alpha = .25f))
            .clip(RoundedCornerShape(16.dp))
            .background(c.accent)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = "Add transaction", onClick = onAdd)
            .semantics { contentDescription = "Add transaction" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Lucide.Plus, null, tint = c.onAccent, modifier = Modifier.size(24.dp))
    }
}

/** Compact FAB: round accent button; springs in. */
@Composable
fun AddFab(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Budget.colors
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(tween(Motion.ARRIVE, easing = Motion.Spring), initialScale = .6f) + fadeIn(tween(250)),
        exit = scaleOut(tween((Motion.ARRIVE * Motion.EXIT_FACTOR).toInt(), easing = Motion.Ease), targetScale = .6f) + fadeOut(tween(200)),
    ) {
        val interaction = remember { MutableInteractionSource() }
        Box(
            Modifier
                .size(56.dp)
                .pressScale(interaction)
                .shadow(10.dp, CircleShape, ambientColor = c.shadow.copy(alpha = .22f), spotColor = c.shadow.copy(alpha = .3f))
                .clip(CircleShape)
                .background(c.accent)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = "Add transaction", onClick = onClick)
                .semantics { contentDescription = "Add transaction" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Lucide.Plus, null, tint = c.onAccent, modifier = Modifier.size(26.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Toast (dark glass): auto-hides after 2.6 s; a new toast replaces the current one.
// ---------------------------------------------------------------------------------------------

data class ToastMessage(val text: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null, val id: Long = System.nanoTime())

class ToastState {
    var current by mutableStateOf<ToastMessage?>(null)
        private set

    fun show(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        current = ToastMessage(text, actionLabel, onAction)
    }

    fun dismiss(id: Long) {
        if (current?.id == id) current = null
    }
}

val LocalToast = staticCompositionLocalOf { ToastState() }

@Composable
fun ToastHost(state: ToastState, modifier: Modifier = Modifier) {
    val c = Budget.colors
    val msg = state.current
    LaunchedEffect(msg?.id) {
        val id = msg?.id ?: return@LaunchedEffect
        kotlinx.coroutines.delay(if (msg.actionLabel != null) 4000 else 2600)
        state.dismiss(id)
    }
    var last by remember { mutableStateOf<ToastMessage?>(null) }
    if (msg != null) last = msg
    AnimatedVisibility(
        visible = msg != null,
        modifier = modifier,
        enter = slideInVertically(tween(Motion.ARRIVE, easing = Motion.Spring)) { it / 2 } + fadeIn(tween(300, easing = Motion.Ease)) +
            scaleIn(tween(Motion.ARRIVE, easing = Motion.Spring), initialScale = .96f),
        exit = fadeOut(tween(200, easing = Motion.Ease)) + slideOutVertically(tween(270, easing = Motion.Ease)) { it / 3 },
    ) {
        val m = last ?: return@AnimatedVisibility
        Row(
            Modifier
                .shadow(12.dp, RoundedCornerShape(10.dp), ambientColor = Color(16, 24, 40).copy(alpha = .14f), spotColor = Color(16, 24, 40).copy(alpha = .2f))
                .clip(RoundedCornerShape(10.dp))
                .background(c.toast)
                .padding(start = 18.dp, end = if (m.actionLabel != null) 8.dp else 18.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(m.text, style = Budget.type.secondary, color = c.onToast, maxLines = 3)
            if (m.actionLabel != null) {
                Spacer(Modifier.width(12.dp))
                Text(
                    m.actionLabel,
                    style = Budget.type.button,
                    color = if (c.isDark) Color(0xFF0A45CC) else Color(0xFF8CB0FF),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            m.onAction?.invoke()
                            state.dismiss(m.id)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
fun ProvideToast(state: ToastState, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalToast provides state, content = content)
}

/** True once content has scrolled under the top bar. Derived, so scrolling doesn't recompose screens. */
@Composable
fun androidx.compose.foundation.ScrollState.isScrolled(): Boolean {
    val state = this
    return remember(state) { androidx.compose.runtime.derivedStateOf { state.value > 0 } }.value
}
