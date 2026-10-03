package com.personal.budget

import android.content.Context
import com.personal.budget.data.local.AuthStore
import com.personal.budget.data.local.BudgetDatabase
import com.personal.budget.data.local.PrefsStore
import com.personal.budget.data.remote.AuthApi
import com.personal.budget.data.remote.RestApi
import com.personal.budget.data.remote.SupabaseConfig
import com.personal.budget.data.remote.defaultHttpClient
import com.personal.budget.data.repository.AccountManager
import com.personal.budget.data.repository.AuthRepository
import com.personal.budget.data.repository.BudgetRepository
import com.personal.budget.data.repository.SyncEngine
import com.personal.budget.data.repository.SyncScheduler
import com.personal.budget.widgets.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import com.personal.budget.data.repository.BudgetSnapshot

/** Manual dependency injection: one instance of each service for the whole process. */
class AppContainer(context: Context) {
    private val app = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val config = SupabaseConfig.fromBuildConfig()
    private val http = defaultHttpClient()

    val database: BudgetDatabase = BudgetDatabase.build(app)
    val prefsStore = PrefsStore(app)
    private val authStore = AuthStore(app)

    val auth = AuthRepository(config, AuthApi(config, http), authStore, prefsStore)
    private val rest = RestApi(config, http, auth)

    val widgetUpdater = WidgetUpdater(app, appScope)

    val syncEngine = SyncEngine(database, rest, auth, prefsStore, onDataChanged = { widgetUpdater.request() })
    val syncScheduler = SyncScheduler(app, appScope, syncEngine)

    val repository = BudgetRepository(
        db = database,
        onLocalChange = {
            syncScheduler.onLocalChange()
            widgetUpdater.request()
        },
        onImported = { syncEngine.requestNetWorthSnapshot() },
    )

    /** One shared, always-current view of the database for every screen (null until loaded). */
    val snapshot: StateFlow<BudgetSnapshot?> = repository.snapshot
        .stateIn(appScope, SharingStarted.WhileSubscribed(5_000), null)

    val accounts = AccountManager(
        auth = auth,
        repo = repository,
        prefs = prefsStore,
        engine = syncEngine,
        scheduler = syncScheduler,
        pendingCount = { database.syncStateDao().pendingCount() },
    )
}
