package com.personal.budget.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personal.budget.BudgetApp
import com.personal.budget.data.repository.SyncResult

/** Periodic background sync (every 6 h, network required). Uses the app's single SyncEngine. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as BudgetApp).container
        return when (container.syncEngine.sync()) {
            SyncResult.Success, SyncResult.Skipped, SyncResult.NeedsSignIn -> Result.success()
            is SyncResult.Offline -> Result.retry()
            is SyncResult.Failed -> Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "budget-periodic-sync"
    }
}
