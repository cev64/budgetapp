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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.personal.budget.ui.theme.Budget
import com.personal.budget.ui.theme.LocalReduceMotion
import com.personal.budget.ui.theme.Motion
import com.personal.budget.ui.theme.Radius
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** What the sync dot shows (docs/UI_ANATOMY.md top bar). */
sealed interface SyncIndicator {
    data object Synced : SyncIndicator
    data object Syncing : SyncIndicator
    data class Pending(val count: Int, val offline: Boolean) : SyncIndicator
    data class Error(val message: String) : SyncIndicator
    data object SignIn : SyncIndicator
    data object LocalOnly : SyncIndicator
}

/** Padding the app shell reserves (floating bottom nav on compact) that screens add inside their scroll. */
val LocalShellPadding = staticCompositionLocalOf { PaddingValues(0.dp) }

/** Width the shell reserves at the start for the floating rail; the top bar's glass reaches under it. */
val LocalShellStartReach = staticCompositionLocalOf { 0.dp }

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
    Box(modifier.size(8.dp).graphicsLayer { alpha = pulse }.clip(CircleShape).background(animated))
}

fun SyncIndicator.describe(): String = when (this) {
    SyncIndicator.Synced -> "Synced"
    SyncIndicator.Syncing -> "Syncing"
    is SyncIndicator.Pending -> if (offline) "Offline · $count pending" else "$count change${if (count == 1) "" else "s"} pending"
    is SyncIndicator.Error -> "Sync error: $message"
    SyncIndicator.SignIn -> "Sign in to sync"
    SyncIndicator.LocalOnly -> "Not syncing"
}

/** Height of the compact top bar (below the status bar). */
val TopBarHeight = 52.dp

/** The condensed bar's glass runs this far below the bar and fades out over it (no hard bottom edge). */
val TopBarFade = 28.dp

/**
 * Collapsing top bar (FLUID_GLASS v2 §5): 52dp, transparent at rest while the page shows its
 * [LargeTitle]; once that scrolls under ([scrolled]), the bar turns blurred `glass-strong` and the
 * compact title (Inter 17/600) cross-fades in (200 ms). The glass runs [TopBarFade] past the bar,
 * fading out over it, and reaches under the side rail, so it has no hard bottom edge or corner. Sync dot and
 * Settings stay on the right. [titleAlways] keeps the compact title visible (screens without a
 * large title, e.g. a detail pane).
 */
@Composable
fun GlassTopBar(
    title: String,
    scrolled: Boolean,
    modifier: Modifier = Modifier,
    navigation: (@Composable () -> Unit)? = null,
    titleAlways: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Budget.colors
    val acts = LocalTopBarActions.current
    val reduce = LocalReduceMotion.current
    val a by animateFloatAsState(if (scrolled) 1f else 0f, if (reduce) tween(0) else tween(Motion.BAR_CONDENSE, easing = Motion.Ease), label = "barGlass")
    val titleA by animateFloatAsState(if (scrolled || titleAlways) 1f else 0f, if (reduce) tween(0) else tween(Motion.BAR_CONDENSE, easing = Motion.Ease), label = "barTitle")
    Box(modifier.fillMaxWidth()) {
        val reach = LocalShellStartReach.current
        Box(
            Modifier
                .matchParentSize()
                .layout { measurable, constraints ->
                    // Grow under the rail (start) and down by the fade; the bar's own size is unchanged.
                    val start = reach.roundToPx()
                    val p = measurable.measure(Constraints.fixed(constraints.maxWidth + start, constraints.maxHeight + TopBarFade.roundToPx()))
                    layout(constraints.maxWidth, constraints.maxHeight) { p.placeRelative(-start, 0) }
                }
                // Offscreen so the alpha fade and the bottom mask apply to the blur and fill as one.
                .graphicsLayer { alpha = a; compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val solid = 1f - TopBarFade.toPx() / size.height
                    drawRect(Brush.verticalGradient(0f to Color.Black, solid to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                }
                .blurredGlass(LocalFrameHaze.current, RectangleShape),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .height(TopBarHeight)
                .padding(start = if (navigation != null) 4.dp else 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigation?.invoke()
            Text(
                title,
                style = Budget.type.barTitle,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = if (navigation != null) 4.dp else 0.dp).graphicsLayer { alpha = titleA },
            )
            actions()
            Box(
                Modifier
                    .size(44.dp)
                    .tappable(shape = CircleShape, label = "Sync now", scale = Motion.ICON_PRESS_SCALE, onClick = acts.onSync)
                    .semantics { contentDescription = acts.sync.describe() },
                contentAlignment = Alignment.Center,
            ) { SyncDot(acts.sync) }
            if (LocalShowSettingsGear.current) {
                GhostIconButton(Lucide.Settings, "Settings", size = 44.dp, iconSize = 21.dp, onClick = acts.onSettings)
            }
        }
    }
}

/**
 * The large title block at the top of a screen's scroll (v2 §5): micro label 12/500 ink-3 above the
 * Barlow display title 40/44. [onTitleClick] makes the title a button (e.g. the month picker).
 */
@Composable
fun LargeTitle(
    micro: String?,
    title: String,
    modifier: Modifier = Modifier,
    onTitleClick: (() -> Unit)? = null,
    titleClickLabel: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = Budget.colors
    Row(modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            if (micro != null) MicroLabel(micro)
            Text(
                title.uppercase(),
                style = Budget.type.screenTitle,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .then(if (onTitleClick != null) Modifier.tappable(shape = RoundedCornerShape(Radius.control), label = titleClickLabel, onClick = onTitleClick) else Modifier)
                    .semantics { heading() },
            )
        }
        trailing()
    }
}

/**
 * Screen frame: content fills the whole area and scrolls under the top bar; the scrolling content
 * is the blur source for the bar. The padding passed to [content] reserves the bar plus the shell's
 * floating nav.
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
    val haze = rememberHazeState()
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            content(PaddingValues(top = topHeight, bottom = shell.calculateBottomPadding()))
        }
        Box(Modifier.fillMaxWidth().onSizeChanged { topHeight = with(density) { it.height.toDp() } }) {
            CompositionLocalProvider(LocalFrameHaze provides haze) { topBar() }
        }
    }
}

data class NavItem(val route: String, val label: String, val icon: ImageVector)

/** Visual height of the floating nav pill (cover screen). */
val FloatingNavHeight = 60.dp

/** Width of one floating-nav item for a window [width]: 5 items + Add must fit with 12dp margins. */
fun floatingNavItemWidth(width: Dp, count: Int): Dp =
    ((width - 24.dp - 12.dp - 66.dp) / count).coerceIn(48.dp, 78.dp)

/**
 * Cover-screen navigation (§5): a detached floating glass pill (blurred), one item per destination
 * with a gliding `fill-2` indicator (spring-soft), and the round accent Add button floating beside
 * it. Items size to the window so all five fit the cover screen; very narrow windows drop labels.
 */
@Composable
fun FloatingNavBar(
    items: List<NavItem>,
    selectedRoute: String?,
    onSelect: (NavItem) -> Unit,
    onAdd: () -> Unit,
    showAdd: Boolean,
    itemWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val c = Budget.colors
    val density = LocalDensity.current
    val reduce = LocalReduceMotion.current
    val positions = remember { mutableStateMapOf<Int, Dp>() }
    var ready by remember { mutableStateOf(false) }
    val sel = items.indexOfFirst { it.route == selectedRoute }
    val target = positions[sel]
    val x by animateDpAsState(target ?: 0.dp, if (ready && !reduce) tween(Motion.THUMB, easing = Motion.SpringSoft) else tween(0), label = "navX")
    LaunchedEffect(target != null) {
        if (target != null) {
            kotlinx.coroutines.delay(32)
            ready = true
        }
    }
    val indicatorA by animateFloatAsState(if (sel >= 0) 1f else 0f, tween(Motion.HOVER, easing = Motion.Ease), label = "navInd")
    val pill = RoundedCornerShape(Radius.pill)
    val labels = itemWidth >= 58.dp
    Row(modifier.navigationBarsPadding().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .height(FloatingNavHeight)
                .glassShadow(pill, large = true)
                .blurredGlass(LocalShellHaze.current, pill)
                .glassEdge(pill)
                .padding(6.dp),
        ) {
            if (target != null) {
                Box(Modifier.offset { IntOffset(x.roundToPx(), 0) }.size(itemWidth, FloatingNavHeight - 12.dp).graphicsLayer { alpha = indicatorA }.clip(pill).background(c.fill2))
            }
            Row {
                items.forEachIndexed { i, item ->
                    val selected = i == sel
                    val ink by animateColorAsState(if (selected) c.ink else c.ink3, tween(Motion.HOVER, easing = Motion.Ease), label = "navInk")
                    val interaction = remember { MutableInteractionSource() }
                    Column(
                        Modifier
                            .size(itemWidth, FloatingNavHeight - 12.dp)
                            .onPlaced { with(density) { positions[i] = it.positionInParent().x.toDp() } }
                            .pressScale(interaction)
                            .clip(pill)
                            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClickLabel = item.label) { onSelect(item) }
                            .semantics {
                                this.selected = selected
                                contentDescription = item.label
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(item.icon, null, tint = ink, modifier = Modifier.size(20.dp))
                        if (labels) {
                            Spacer(Modifier.height(3.dp))
                            Text(item.label, style = Budget.type.nav, color = ink, maxLines = 1, overflow = TextOverflow.Clip)
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = showAdd,
            enter = scaleIn(tween(Motion.ARRIVE, easing = Motion.Spring), initialScale = .6f) + fadeIn(tween(200)),
            exit = scaleOut(tween(200, easing = Motion.Ease), targetScale = .6f) + fadeOut(tween(160)),
        ) {
            Row {
                Spacer(Modifier.width(10.dp))
                AddCircle(onAdd, size = 56.dp)
            }
        }
    }
}

/** The one accent per view: round Add with its tinted shadow (`0 10px 24px -6px accent .45`). */
@Composable
fun AddCircle(onAdd: () -> Unit, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val c = Budget.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(size)
            .pressScale(interaction, Motion.ICON_PRESS_SCALE)
            // Tinted lift in light; a plain dark shadow in dark so it never reads as a glow.
            .softShadow(CircleShape, if (c.isDark) Color.Black.copy(alpha = .55f) else c.accent.copy(alpha = .40f), 24.dp, 10.dp, (-6).dp)
            .clip(CircleShape)
            .background(c.accent)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = "Add transaction", onClick = onAdd)
            .semantics { contentDescription = "Add transaction" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Lucide.Plus, null, tint = c.onAccent, modifier = Modifier.size(size * .46f))
    }
}

/** Rail widths: 76dp (600–1023dp windows, Add on top) or 220dp with the lockup (≥ 1024dp). */
fun railWidth(wide: Boolean): Dp = if (wide) 220.dp else 76.dp

/**
 * Inner-screen navigation (§5): a floating glass panel inset 12dp (radius 24); the active item sits
 * on a gliding `fill-2` pill with ink text (spring-soft), inactive items are ink-2.
 */
@Composable
fun GlassRail(
    items: List<NavItem>,
    selectedRoute: String?,
    onSelect: (NavItem) -> Unit,
    onAdd: () -> Unit,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = Budget.colors
    val density = LocalDensity.current
    val reduce = LocalReduceMotion.current
    val positions = remember { mutableStateMapOf<Int, Dp>() }
    var ready by remember { mutableStateOf(false) }
    val sel = items.indexOfFirst { it.route == selectedRoute }
    val target = positions[sel]
    val y by animateDpAsState(target ?: 0.dp, if (ready && !reduce) tween(Motion.THUMB, easing = Motion.SpringSoft) else tween(0), label = "railY")
    LaunchedEffect(target != null) {
        if (target != null) {
            kotlinx.coroutines.delay(32)
            ready = true
        }
    }
    val indicatorA by animateFloatAsState(if (sel >= 0) 1f else 0f, tween(Motion.HOVER, easing = Motion.Ease), label = "railInd")
    val panel = RoundedCornerShape(Radius.rail)
    val itemShape = if (wide) RoundedCornerShape(Radius.control) else RoundedCornerShape(18.dp)
    val itemH = if (wide) 44.dp else 58.dp
    val itemW = 64.dp
    Column(
        modifier
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars).union(WindowInsets.displayCutout).only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
            .padding(12.dp)
            .fillMaxHeight()
            .width(railWidth(wide))
            .glassShadow(panel)
            .blurredGlass(LocalShellHaze.current, panel)
            .glassEdge(panel)
            .padding(vertical = 16.dp, horizontal = if (wide) 12.dp else 6.dp),
        horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        if (wide) {
            BrandLockup(width = 112.dp, modifier = Modifier.padding(start = 8.dp))
            Spacer(Modifier.height(20.dp))
            BudgetButton("Add", onAdd, Modifier.fillMaxWidth(), kind = ButtonKind.Primary, icon = Lucide.Plus)
        } else {
            BrandMark(size = 28.dp, contentDescription = "Budget")
            Spacer(Modifier.height(18.dp))
            AddCircle(onAdd, size = 48.dp)
        }
        Spacer(Modifier.height(20.dp))
        Box {
            if (target != null) {
                Box(
                    Modifier
                        .offset { IntOffset(0, y.roundToPx()) }
                        .then(if (wide) Modifier.fillMaxWidth() else Modifier.width(itemW))
                        .height(itemH)
                        .graphicsLayer { alpha = indicatorA }
                        .clip(itemShape)
                        .background(c.fill2),
                )
            }
            Column {
                items.forEachIndexed { i, item ->
                    val selected = i == sel
                    val ink by animateColorAsState(if (selected) c.ink else c.ink2, tween(Motion.HOVER, easing = Motion.Ease), label = "railInk")
                    val interaction = remember { MutableInteractionSource() }
                    val base = Modifier
                        .then(if (wide) Modifier.fillMaxWidth() else Modifier.width(itemW))
                        .height(itemH)
                        .onPlaced { with(density) { positions[i] = it.positionInParent().y.toDp() } }
                        .pressScale(interaction)
                        .clip(itemShape)
                        .clickable(interactionSource = interaction, indication = null, role = Role.Tab) { onSelect(item) }
                        .semantics { this.selected = selected }
                    if (wide) {
                        Row(base.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(item.icon, null, tint = ink, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(item.label, style = Budget.type.segment, color = ink, maxLines = 1)
                        }
                    } else {
                        Column(base, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(item.icon, null, tint = ink, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.height(3.dp))
                            Text(item.label, style = Budget.type.nav, color = ink, maxLines = 1, overflow = TextOverflow.Clip)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
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
        enter = slideInVertically(tween(Motion.SHEET_IN, easing = Motion.Spring)) { it / 2 } + fadeIn(tween(250, easing = Motion.Ease)) +
            scaleIn(tween(Motion.SHEET_IN, easing = Motion.Spring), initialScale = .96f),
        exit = fadeOut(tween(200, easing = Motion.Ease)) + slideOutVertically(tween(200, easing = Motion.Ease)) { it / 3 },
    ) {
        val m = last ?: return@AnimatedVisibility
        Row(
            Modifier
                .softShadow(RoundedCornerShape(16.dp), Color(8, 32, 79).copy(alpha = .28f), 32.dp, 12.dp, (-10).dp)
                .clip(RoundedCornerShape(16.dp))
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

/** Past the large title: the top bar turns to glass and shows the compact title. */
@Composable
fun androidx.compose.foundation.ScrollState.pastTitle(): Boolean {
    val state = this
    val px = with(LocalDensity.current) { 56.dp.toPx() }
    return remember(state, px) { androidx.compose.runtime.derivedStateOf { state.value > px } }.value
}

@Composable
fun androidx.compose.foundation.lazy.LazyListState.pastTitle(): Boolean {
    val state = this
    val px = with(LocalDensity.current) { 56.dp.toPx() }
    return remember(state, px) { androidx.compose.runtime.derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > px } }.value
}
