package com.personal.budget.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.personal.budget.AppContainer
import com.personal.budget.BudgetApp

val appContainer: AppContainer
    @Composable get() = (LocalContext.current.applicationContext as BudgetApp).container

private tailrec fun android.content.Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Activity-scoped ViewModels: one instance per screen for the whole activity, so selections,
 * tabs and drafts survive switching destinations, folding/unfolding and rotation; their
 * SavedStateHandle survives process death.
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(noinline create: (AppContainer, SavedStateHandle) -> VM): VM {
    val container = appContainer
    val owner = activityOwner()
    return viewModel(
        viewModelStoreOwner = owner,
        factory = viewModelFactory { initializer { create(container, createSavedStateHandle()) } },
    )
}

@Composable
fun activityOwner(): ComponentActivity = LocalContext.current.findActivity() ?: error("No ComponentActivity in context")
