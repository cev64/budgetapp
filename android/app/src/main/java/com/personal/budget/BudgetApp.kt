package com.personal.budget

import android.app.Application
import androidx.work.Configuration
import com.personal.budget.data.repository.AuthState
import kotlinx.coroutines.launch

class BudgetApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    /** On-demand WorkManager initialisation (no startup ContentProvider needed). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch {
            container.auth.init()
            container.syncScheduler.start()
            val state = container.auth.state.value
            if (state is AuthState.SignedIn && state.sessionValid) container.syncScheduler.schedulePeriodic()
        }
    }
}
