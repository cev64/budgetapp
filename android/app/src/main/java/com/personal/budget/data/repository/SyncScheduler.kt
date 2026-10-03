package com.personal.budget.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.personal.budget.workers.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * When to sync (docs/SYNC.md rule 7, Android):
 *  - app start and every return to the foreground (ProcessLifecycleOwner ON_START)
 *  - 1.5 s after the last local edit (debounced)
 *  - when connectivity comes back
 *  - every 6 h in the background via WorkManager (network required)
 */
@OptIn(FlowPreview::class)
class SyncScheduler(
    private val context: Context,
    private val scope: CoroutineScope,
    private val engine: SyncEngine,
) {
    private val localEdits = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var started = false
    private var inFlight: Job? = null

    fun start() {
        if (started) return
        started = true
        scope.launch { localEdits.debounce(LOCAL_EDIT_DEBOUNCE_MS).collect { requestSync() } }

        scope.launch(Dispatchers.Main.immediate) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    requestSync()
                }
            })
        }

        val cm = context.getSystemService(ConnectivityManager::class.java)
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        runCatching {
            cm?.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    requestSync()
                }
            })
        }
    }

    fun requestSync() {
        inFlight = scope.launch { engine.sync() }
    }

    fun onLocalChange() {
        localEdits.tryEmit(Unit)
    }

    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(SyncWorker.UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelPeriodic() {
        WorkManager.getInstance(context).cancelUniqueWork(SyncWorker.UNIQUE_NAME)
    }

    companion object {
        const val LOCAL_EDIT_DEBOUNCE_MS = 1500L
    }
}
