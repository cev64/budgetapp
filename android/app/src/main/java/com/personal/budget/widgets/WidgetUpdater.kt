package com.personal.budget.widgets

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/** Refreshes every Budget widget shortly after local edits and after each sync that changed data. */
@OptIn(FlowPreview::class)
class WidgetUpdater(private val context: Context, scope: CoroutineScope) {
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        scope.launch {
            requests.debounce(800).collect {
                runCatching { BudgetWidget().updateAll(context) }
            }
        }
    }

    fun request() {
        requests.tryEmit(Unit)
    }
}
