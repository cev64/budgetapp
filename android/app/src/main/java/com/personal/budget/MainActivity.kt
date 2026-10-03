package com.personal.budget

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.budget.data.local.ThemeMode
import com.personal.budget.data.repository.AuthState
import com.personal.budget.ui.MainViewModel
import com.personal.budget.ui.appViewModel
import com.personal.budget.ui.navigation.AppShell
import com.personal.budget.ui.screens.auth.AuthScreen
import com.personal.budget.ui.theme.Budget
import com.personal.budget.ui.theme.BudgetTheme

class MainActivity : ComponentActivity() {
    private var pendingAdd = false
    private var main: MainViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Only a fresh launch from the widget/shortcut/deep link opens the sheet; after a
        // recreation the sheet's own saved state already restores it.
        pendingAdd = savedInstanceState == null && intent.isAddIntent()
        val container = (application as BudgetApp).container
        splash.setKeepOnScreenCondition {
            val auth = container.auth.state.value
            auth is AuthState.Loading || (auth is AuthState.SignedIn && container.snapshot.value == null)
        }
        setContent {
            val vm = appViewModel { c, h -> MainViewModel(c, h) }
            main = vm
            val prefs by vm.prefs.collectAsStateWithLifecycle()
            val p = prefs
            BudgetTheme(themeMode = p?.theme ?: ThemeMode.SYSTEM, dynamicColor = p?.dynamicColor ?: false) {
                SystemBars(p?.theme ?: ThemeMode.SYSTEM)
                Root(vm)
            }
            if (pendingAdd) {
                pendingAdd = false
                vm.openAdd()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.isAddIntent()) main?.openAdd()
    }

    private fun Intent?.isAddIntent(): Boolean =
        this?.action == Intent.ACTION_VIEW && data?.scheme == "budget" && data?.host == "add"

    @Composable
    private fun SystemBars(mode: ThemeMode) {
        val dark = when (mode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        DisposableEffect(dark) {
            val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            onDispose {}
        }
    }
}

@Composable
private fun Root(vm: MainViewModel) {
    val auth by vm.auth.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(Budget.colors.bg)) {
        when (auth) {
            AuthState.Loading -> Unit
            AuthState.SignedOut -> AuthScreen()
            is AuthState.SignedIn -> AppShell(vm)
        }
    }
}
