package com.personal.budget.ui.screens.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.budget.BuildConfig
import com.personal.budget.data.local.ThemeMode
import com.personal.budget.data.repository.AccountOutcome
import com.personal.budget.data.repository.AuthState
import com.personal.budget.data.repository.BudgetSnapshot
import com.personal.budget.domain.model.BudgetSettings
import com.personal.budget.domain.model.Category
import com.personal.budget.domain.model.CategoryKind
import com.personal.budget.domain.model.RecurringItem
import com.personal.budget.domain.model.Tracking
import com.personal.budget.domain.usecase.Money
import com.personal.budget.domain.usecase.Num
import com.personal.budget.ui.MainViewModel
import com.personal.budget.ui.appContainer
import com.personal.budget.ui.appViewModel
import com.personal.budget.ui.components.isScrolled
import com.personal.budget.ui.components.AnimatedList
import com.personal.budget.ui.components.AppIcon
import com.personal.budget.ui.components.BudgetButton
import com.personal.budget.ui.components.BudgetCard
import com.personal.budget.ui.components.BudgetDialog
import com.personal.budget.ui.components.BudgetSwitch
import com.personal.budget.ui.components.BudgetTextField
import com.personal.budget.ui.components.ButtonKind
import com.personal.budget.ui.components.CardHeader
import com.personal.budget.ui.components.ConfirmDialog
import com.personal.budget.ui.components.Dot
import com.personal.budget.ui.components.GhostIconButton
import com.personal.budget.ui.components.GlassTopBar
import com.personal.budget.ui.components.Hairline
import com.personal.budget.ui.components.LocalShowSettingsGear
import com.personal.budget.ui.components.LocalToast
import com.personal.budget.ui.components.Lucide
import com.personal.budget.ui.components.MicroLabel
import com.personal.budget.ui.components.MoneyField
import com.personal.budget.ui.components.PickRow
import com.personal.budget.ui.components.Pill
import com.personal.budget.ui.components.ScreenFrame
import com.personal.budget.ui.components.SegmentedControl
import com.personal.budget.ui.components.SyncDot
import com.personal.budget.ui.components.describe
import com.personal.budget.ui.components.tappable
import com.personal.budget.ui.screens.LocalWindowLayout
import com.personal.budget.ui.screens.paletteFor
import com.personal.budget.ui.components.CategoryMark
import com.personal.budget.ui.screens.kindLabel
import com.personal.budget.ui.theme.Budget
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Settings: Account, Income & targets, Categories, Recurring, Appearance, Data, About.
 * Medium/expanded: two columns.
 */
@Composable
fun SettingsScreen(main: MainViewModel, onBack: () -> Unit) {
    val vm = appViewModel { c, h -> SettingsViewModel(c, h) }
    val snapshot by main.snapshot.collectAsStateWithLifecycle()
    val editing by vm.editing.collectAsStateWithLifecycle()
    val layout = LocalWindowLayout.current
    val scroll = rememberScrollState()
    val s = snapshot ?: return

    androidx.compose.runtime.CompositionLocalProvider(LocalShowSettingsGear provides false) {
        ScreenFrame(topBar = {
            GlassTopBar(
                micro = "Budget",
                title = "Settings",
                scrolled = scroll.isScrolled(),
                navigation = { GhostIconButton(Lucide.ChevronLeft, "Back", onClick = onBack) },
            )
        }) { padding ->
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(padding).padding(horizontal = layout.gutter, vertical = 14.dp)) {
                if (layout.isCompact) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AccountCard(main, vm)
                        IncomeCard(s.settings, vm)
                        CategoriesCard(s, vm)
                        RecurringCard(s, vm)
                        AppearanceCard(main)
                        DataCard(vm)
                        AboutCard()
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            AccountCard(main, vm)
                            IncomeCard(s.settings, vm)
                            AppearanceCard(main)
                            DataCard(vm)
                            AboutCard()
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            CategoriesCard(s, vm)
                            RecurringCard(s, vm)
                        }
                    }
                }
            }
        }
    }

    val e = editing
    when {
        e == null -> Unit
        e.startsWith("category:") -> CategoryDialog(s, s.book.categoriesById[e.removePrefix("category:")], vm)
        e.startsWith("recurring:") -> RecurringDialog(s, s.recurring.firstOrNull { it.id == e.removePrefix("recurring:") }, vm)
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    val c = Budget.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Budget.type.body, color = c.ink)
            if (subtitle != null) Text(subtitle, style = Budget.type.small, color = c.ink3)
        }
        trailing()
    }
}

@Composable
private fun AccountCard(main: MainViewModel, vm: SettingsViewModel) {
    val c = Budget.colors
    val auth by main.auth.collectAsStateWithLifecycle()
    val indicator by main.syncIndicator.collectAsStateWithLifecycle()
    val prefs by vm.lastSyncAt.collectAsStateWithLifecycle(null)
    val pending by vm.pending.collectAsStateWithLifecycle(0)
    val signOutPending by vm.signOutPending.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirmSignOut by remember { mutableStateOf(false) }
    var reauth by rememberSaveable { mutableStateOf(false) }
    val a = auth as? AuthState.SignedIn
    BudgetCard {
        CardHeader("Account")
        SettingRow(a?.email?.ifBlank { "Signed in" } ?: "Not signed in", if (a?.sessionValid == false) "Session expired: sign in again to sync" else "Email + password via Supabase")
        Hairline()
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            SyncDot(indicator)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(indicator.describe(), style = Budget.type.body, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val last = prefs?.lastSyncAt ?: 0L
                Text(
                    if (last > 0) "Last sync " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(last)) else "Never synced",
                    style = Budget.type.small,
                    color = c.ink3,
                )
            }
            BudgetButton("Sync now", { vm.syncNow() }, icon = Lucide.RefreshCw, enabled = vm.config.isConfigured && a?.sessionValid == true)
        }
        if (pending > 0) Text("$pending change${if (pending == 1) "" else "s"} waiting to upload", style = Budget.type.small, color = c.ink3)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (a?.sessionValid == false) BudgetButton("Sign in again", { reauth = true }, kind = ButtonKind.Primary)
            BudgetButton("Sign out", { confirmSignOut = true }, kind = ButtonKind.Danger, icon = Lucide.LogOut, enabled = !busy)
        }
    }
    if (confirmSignOut) {
        ConfirmDialog(
            title = "Sign out?",
            body = "Unsynced changes are uploaded first. Then this device's copy of your budget is removed (it stays in your account).",
            confirmLabel = "Sign out",
            onConfirm = {
                confirmSignOut = false
                scope.launch { vm.signOut(force = false) }
            },
            onDismiss = { confirmSignOut = false },
        )
    }
    signOutPending?.let { n ->
        ConfirmDialog(
            title = "$n change${if (n == 1) "" else "s"} not synced",
            body = "They couldn't be uploaded (offline or signed out). Signing out now deletes them from this device for good. Export a backup first if unsure.",
            confirmLabel = "Discard and sign out",
            destructive = true,
            onConfirm = { scope.launch { vm.signOut(force = true) } },
            onDismiss = { vm.dismissSignOutWarning() },
        )
    }
    if (reauth && a != null) ReauthDialog(a.email, onDone = { reauth = false })
}

@Composable
private fun ReauthDialog(email: String, onDone: () -> Unit) {
    val container = appContainer
    val scope = rememberCoroutineScope()
    var password by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    BudgetDialog(
        onDismiss = onDone,
        title = "Sign in again",
        actions = {
            BudgetButton("Cancel", onDone)
            BudgetButton("Sign in", {
                busy = true
                scope.launch {
                    runCatching { container.accounts.signIn(email, password) }
                        .onSuccess { r ->
                            if (r is AccountOutcome.SignedIn) onDone() else error = "Couldn't sign in"
                        }
                        .onFailure { error = it.message }
                    busy = false
                }
            }, kind = ButtonKind.Primary, enabled = password.isNotEmpty() && !busy)
        },
    ) {
        Text(email, style = Budget.type.bodyStrong, color = Budget.colors.ink)
        Spacer(Modifier.height(10.dp))
        BudgetTextField(password, { password = it }, label = "Password", password = true, keyboardType = KeyboardType.Password)
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = Budget.type.secondary, color = Budget.colors.bad)
        }
    }
}

@Composable
private fun IncomeCard(settings: BudgetSettings, vm: SettingsViewModel) {
    BudgetCard {
        CardHeader("Income & targets")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MoneyField(settings.netIncome, { vm.saveSettings(settings.copy(netIncome = it ?: settings.netIncome)) }, label = "Annual net", allowBlank = false, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            MoneyField(settings.grossIncome, { vm.saveSettings(settings.copy(grossIncome = it ?: settings.grossIncome)) }, label = "Annual gross", allowBlank = false, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
        }
        Spacer(Modifier.height(6.dp))
        Text("Net and gross are used for the year's “% of net / gross income”.", style = Budget.type.small, color = Budget.colors.ink3)
    }
}

@Composable
private fun CategoriesCard(s: BudgetSnapshot, vm: SettingsViewModel) {
    val c = Budget.colors
    val cats = s.book.categories
    BudgetCard(padding = PaddingValues(top = 14.dp, bottom = 6.dp)) {
        CardHeader("Categories", Modifier.padding(horizontal = 14.dp)) {
            BudgetButton("Add", { vm.edit("category:new") }, kind = ButtonKind.Ghost, icon = Lucide.Plus)
        }
        CategoryKind.entries.forEach { kind ->
            val group = cats.filter { it.kind == kind }
            if (group.isEmpty()) return@forEach
            MicroLabel(kindLabel(kind), Modifier.padding(start = 14.dp, top = 8.dp, bottom = 2.dp))
            AnimatedList(group, key = { it.id }) { cat ->
                val index = cats.indexOf(cat)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .tappable(shape = RectangleShape, onClick = { vm.edit("category:${cat.id}") })
                        .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryMark(paletteFor(cat, s.book), size = 14.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(cat.name, style = Budget.type.body, color = if (cat.archived) c.ink3 else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (cat.archived) {
                                Spacer(Modifier.width(6.dp))
                                Pill("archived")
                            }
                        }
                        Text(
                            (if (cat.tracking == Tracking.LEDGER) "Ledger" else "Manual") +
                                if (cat.kind == CategoryKind.SAVINGS && cat.matchMultiplier != 1.0) " · ×${Money.formatInput(cat.matchMultiplier)} match" else "",
                            style = Budget.type.small,
                            color = c.ink3,
                        )
                    }
                    GhostIconButton(Lucide.ChevronUp, "Move ${cat.name} up", iconSize = 20.dp, enabled = index > 0, onClick = { vm.move(cats, cat.id, -1) })
                    GhostIconButton(Lucide.ChevronDown, "Move ${cat.name} down", iconSize = 20.dp, enabled = index < cats.lastIndex, onClick = { vm.move(cats, cat.id, 1) })
                }
            }
        }
    }
}

@Composable
private fun CategoryDialog(s: BudgetSnapshot, existing: Category?, vm: SettingsViewModel) {
    val c = Budget.colors
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var kind by rememberSaveable { mutableStateOf(existing?.kind ?: CategoryKind.EXPENSE) }
    var tracking by rememberSaveable { mutableStateOf(existing?.tracking ?: Tracking.LEDGER) }
    var multiplier by rememberSaveable { mutableStateOf(Money.formatInput(existing?.matchMultiplier ?: 1.0)) }
    var archived by rememberSaveable { mutableStateOf(existing?.archived ?: false) }
    val hasData by produceState(true, existing?.id) { value = existing?.let { vm.categoryHasData(it.id) } ?: false }
    val duplicate = s.book.categories.any { it.id != existing?.id && it.name.trim().equals(name.trim(), ignoreCase = true) }
    BudgetDialog(
        onDismiss = { vm.edit(null) },
        title = if (existing == null) "New category" else "Edit category",
        actions = {
            if (existing != null && !hasData) {
                BudgetButton("Delete", {
                    scope.launch {
                        if (vm.deleteCategory(existing.id)) toast.show("Deleted ${existing.name}") else toast.show("${existing.name} has data: archive it instead")
                        vm.edit(null)
                    }
                }, kind = ButtonKind.Danger)
            }
            Spacer(Modifier.weight(1f))
            BudgetButton("Cancel", { vm.edit(null) })
            BudgetButton("Save", {
                vm.saveCategory(
                    Category(
                        id = existing?.id.orEmpty(), name = name.trim(), kind = kind, tracking = tracking,
                        matchMultiplier = if (kind == CategoryKind.SAVINGS) (multiplier.toDoubleOrNull() ?: 1.0) else 1.0,
                        sortOrder = existing?.sortOrder ?: 0, icon = existing?.icon, color = existing?.color, archived = archived,
                    ),
                )
                vm.edit(null)
            }, kind = ButtonKind.Primary, enabled = name.isNotBlank() && !duplicate)
        },
    ) {
        BudgetTextField(name, { name = it }, label = "Name", placeholder = "Groceries")
        if (duplicate) Text("A category with that name exists.", style = Budget.type.small, color = c.bad)
        Spacer(Modifier.height(12.dp))
        MicroLabel("Kind")
        Spacer(Modifier.height(6.dp))
        PickRow(CategoryKind.entries.map { it to kindLabel(it) }, kind, { kind = it })
        Spacer(Modifier.height(12.dp))
        MicroLabel("Tracking")
        Spacer(Modifier.height(6.dp))
        PickRow(listOf(Tracking.LEDGER to "Ledger (sum of transactions)", Tracking.MANUAL to "Manual (type the actual)"), tracking, { tracking = it })
        if (kind == CategoryKind.SAVINGS) {
            Spacer(Modifier.height(12.dp))
            BudgetTextField(multiplier, { multiplier = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = "Match multiplier", keyboardType = KeyboardType.Decimal)
            Text("401k with a 100% employer match = 2. Counts toward Saved, not Leftover.", style = Budget.type.small, color = c.ink3)
        }
        if (existing != null) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Archived", style = Budget.type.body, color = c.ink)
                    Text("Hidden from new months; still counts where it has data.", style = Budget.type.small, color = c.ink3)
                }
                BudgetSwitch(archived, { archived = it })
            }
            if (hasData) {
                Spacer(Modifier.height(6.dp))
                Text("This category has data, so it can be archived but not deleted.", style = Budget.type.small, color = c.ink3)
            }
        }
    }
}

@Composable
private fun RecurringCard(s: BudgetSnapshot, vm: SettingsViewModel) {
    val c = Budget.colors
    BudgetCard(padding = PaddingValues(top = 14.dp, bottom = 6.dp)) {
        CardHeader("Recurring", Modifier.padding(horizontal = 14.dp)) {
            BudgetButton("Add", { vm.edit("recurring:new") }, kind = ButtonKind.Ghost, icon = Lucide.Plus)
        }
        Text("Added to each new month as transactions (subscriptions).", style = Budget.type.small, color = c.ink3, modifier = Modifier.padding(horizontal = 14.dp))
        if (s.recurring.isEmpty()) Text("No recurring items.", style = Budget.type.secondary, color = c.ink3, modifier = Modifier.padding(14.dp))
        AnimatedList(s.recurring, key = { it.id }) { r ->
            val cat = s.book.categoriesById[r.categoryId]
            Row(
                Modifier
                    .fillMaxWidth()
                    .tappable(shape = RectangleShape, onClick = { vm.edit("recurring:${r.id}") })
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(Lucide.Repeat, null, tint = if (r.active) c.accentInk else c.ink3, size = 16.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.item.ifBlank { "(no item)" }, style = Budget.type.body, color = if (r.active) c.ink else c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(cat?.name, r.dayOfMonth?.let { "day $it" }, if (!r.active) "paused" else null).joinToString(" · "),
                        style = Budget.type.small,
                        color = c.ink3,
                    )
                }
                Text(Money.format(r.amount), style = Budget.type.bodyStrong, color = c.ink)
            }
        }
    }
}

@Composable
private fun RecurringDialog(s: BudgetSnapshot, existing: RecurringItem?, vm: SettingsViewModel) {
    val c = Budget.colors
    var item by rememberSaveable { mutableStateOf(existing?.item.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(existing?.amount) }
    var categoryId by rememberSaveable { mutableStateOf(existing?.categoryId ?: s.book.categories.firstOrNull { it.name.equals("Subscriptions", true) }?.id) }
    var day by rememberSaveable { mutableStateOf(existing?.dayOfMonth?.toString().orEmpty()) }
    var active by rememberSaveable { mutableStateOf(existing?.active ?: true) }
    BudgetDialog(
        onDismiss = { vm.edit(null) },
        title = if (existing == null) "New recurring item" else "Edit recurring item",
        actions = {
            if (existing != null) BudgetButton("Delete", { vm.deleteRecurring(existing.id); vm.edit(null) }, kind = ButtonKind.Danger)
            Spacer(Modifier.weight(1f))
            BudgetButton("Cancel", { vm.edit(null) })
            BudgetButton("Save", {
                val cat = categoryId ?: return@BudgetButton
                vm.saveRecurring(
                    RecurringItem(
                        id = existing?.id.orEmpty(), categoryId = cat, item = item.trim(), amount = amount ?: 0.0,
                        dayOfMonth = day.toIntOrNull()?.coerceIn(1, 31), active = active,
                        sortOrder = existing?.sortOrder ?: ((s.recurring.maxOfOrNull { it.sortOrder } ?: -1) + 1),
                    ),
                )
                vm.edit(null)
            }, kind = ButtonKind.Primary, enabled = item.isNotBlank() && categoryId != null)
        },
    ) {
        BudgetTextField(item, { item = it }, label = "Item", placeholder = "Streaming")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MoneyField(amount, { amount = it }, label = "Amount", modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            BudgetTextField(day, { day = it.filter(Char::isDigit).take(2) }, label = "Day of month", placeholder = "—", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        MicroLabel("Category")
        Spacer(Modifier.height(6.dp))
        PickRow(s.book.categories.filter { !it.archived && it.kind == CategoryKind.EXPENSE }.map { it.id to it.name }, categoryId ?: "", { categoryId = it })
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Active", style = Budget.type.body, color = c.ink, modifier = Modifier.weight(1f))
            BudgetSwitch(active, { active = it })
        }
    }
}

@Composable
private fun AppearanceCard(main: MainViewModel) {
    val prefs by main.prefs.collectAsStateWithLifecycle()
    val p = prefs ?: return
    BudgetCard {
        CardHeader("Appearance")
        SegmentedControl(ThemeMode.entries.map { it to it.label }, p.theme, { main.setTheme(it) }, fill = true, modifier = Modifier.fillMaxWidth())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Spacer(Modifier.height(6.dp))
            SettingRow("Dynamic colour", "Use wallpaper colours. Off keeps the same look as the web app.") {
                BudgetSwitch(p.dynamicColor, { main.setDynamicColor(it) })
            }
        }
    }
}

@Composable
private fun DataCard(vm: SettingsViewModel) {
    val c = Budget.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    val preview by vm.importPreview.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            vm.export(context.contentResolver, uri)
                .onSuccess { n -> toast.show("Backup saved ($n transactions)") }
                .onFailure { toast.show("Export failed: ${it.message}") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            vm.preview(context.contentResolver, uri).onFailure { toast.show(it.message ?: "Couldn't read that file") }
        }
    }
    BudgetCard {
        CardHeader("Data")
        Text("Backups use the same JSON as the web app. Import merges: nothing is deleted.", style = Budget.type.small, color = c.ink3)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BudgetButton("Export backup", { exportLauncher.launch("budget-backup-${java.time.LocalDate.now()}.json") }, icon = Lucide.Download, modifier = Modifier.weight(1f))
            BudgetButton("Import backup", { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, icon = Lucide.Upload, modifier = Modifier.weight(1f))
        }
    }
    preview?.let { plan ->
        BudgetDialog(
            onDismiss = { vm.cancelImport() },
            title = "Import backup?",
            actions = {
                BudgetButton("Cancel", { vm.cancelImport() })
                BudgetButton("Import", {
                    scope.launch {
                        vm.applyImport()
                        toast.show("Backup imported")
                    }
                }, kind = ButtonKind.Primary, enabled = !busy && !plan.isEmpty)
            },
        ) {
            Text(plan.summary(), style = Budget.type.body, color = c.ink)
            Spacer(Modifier.height(10.dp))
            plan.changes.filter { it.total > 0 }.forEach { ch ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(ch.table.replace('_', ' '), style = Budget.type.secondary, color = c.ink2, modifier = Modifier.weight(1f))
                    Text("+${ch.added}" + if (ch.updated > 0) " · ${ch.updated} updated" else "", style = Budget.type.secondary, color = c.ink)
                }
            }
        }
    }
}

@Composable
private fun AboutCard() {
    val c = Budget.colors
    BudgetCard {
        CardHeader("About")
        com.personal.budget.ui.components.BrandLockup(width = 136.dp, modifier = Modifier.padding(vertical = 6.dp))
        SettingRow("Budget ${BuildConfig.VERSION_NAME}", "Build ${BuildConfig.VERSION_CODE} · ${if (BuildConfig.RELEASE_SIGNED) "release-signed" else if (BuildConfig.DEBUG) "debug build" else "UNSIGNED (debug key)"}")
        Text(
            "Fonts: Inter and Barlow Condensed (SIL OFL 1.1). Icons: Lucide (ISC).",
            style = Budget.type.small,
            color = c.ink3,
        )
    }
}
