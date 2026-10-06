package com.personal.budget.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.personal.budget.AppContainer
import com.personal.budget.R
import com.personal.budget.data.remote.HttpException
import com.personal.budget.data.remote.OfflineException
import com.personal.budget.data.repository.AccountOutcome
import com.personal.budget.ui.appViewModel
import com.personal.budget.ui.components.BudgetButton
import com.personal.budget.ui.components.BudgetCard
import com.personal.budget.ui.components.BudgetTextField
import com.personal.budget.ui.components.ButtonKind
import com.personal.budget.ui.components.ConfirmDialog
import com.personal.budget.ui.components.MicroLabel
import com.personal.budget.ui.components.SegmentedControl
import com.personal.budget.ui.components.tappable
import com.personal.budget.ui.theme.Budget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AuthMode { SignIn, SignUp, Reset }

data class AuthMessage(val text: String, val isError: Boolean)

class AuthViewModel(private val c: AppContainer, private val handle: SavedStateHandle) : ViewModel() {
    val mode: StateFlow<String> = handle.getStateFlow("auth_mode", AuthMode.SignIn.name)
    val email: StateFlow<String> = handle.getStateFlow("auth_email", "")
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _message = MutableStateFlow<AuthMessage?>(null)
    val message: StateFlow<AuthMessage?> = _message.asStateFlow()
    private val _discard = MutableStateFlow<Int?>(null)
    val discardPrompt: StateFlow<Int?> = _discard.asStateFlow()

    val configured get() = c.config.isConfigured

    fun setMode(m: AuthMode) {
        handle["auth_mode"] = m.name
        _message.value = null
    }

    fun setEmail(e: String) {
        handle["auth_email"] = e
    }

    fun submit(password: String, discardOtherAccount: Boolean = false) {
        val email = email.value.trim()
        if (email.isEmpty() || !email.contains('@')) {
            _message.value = AuthMessage("Enter your email address", true)
            return
        }
        val mode = AuthMode.valueOf(mode.value)
        if (mode != AuthMode.Reset && password.length < 6) {
            _message.value = AuthMessage("Password must be at least 6 characters", true)
            return
        }
        _busy.value = true
        _message.value = null
        viewModelScope.launch {
            try {
                when (mode) {
                    AuthMode.SignIn -> when (val r = c.accounts.signIn(email, password, discardOtherAccount)) {
                        is AccountOutcome.WouldDiscard -> _discard.value = r.pending
                        else -> _discard.value = null
                    }
                    AuthMode.SignUp -> when (val r = c.accounts.signUp(email, password)) {
                        is AccountOutcome.ConfirmEmail -> {
                            _message.value = AuthMessage("Check ${r.email} for a confirmation link, then sign in.", false)
                            handle["auth_mode"] = AuthMode.SignIn.name
                        }
                        else -> Unit
                    }
                    AuthMode.Reset -> {
                        c.accounts.sendPasswordReset(email)
                        _message.value = AuthMessage("If an account exists for $email, a reset link is on its way.", false)
                    }
                }
            } catch (e: OfflineException) {
                _message.value = AuthMessage("No connection. The first sign-in needs the internet; after that the app works offline.", true)
            } catch (e: HttpException) {
                _message.value = AuthMessage(e.message, true)
            } catch (e: Exception) {
                _message.value = AuthMessage(e.message ?: "Something went wrong", true)
            } finally {
                _busy.value = false
            }
        }
    }

    fun dismissDiscard() {
        _discard.value = null
    }
}

@Composable
fun AuthScreen() {
    val vm = appViewModel { c, h -> AuthViewModel(c, h) }
    val modeName by vm.mode.collectAsStateWithLifecycle()
    val email by vm.email.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val discard by vm.discardPrompt.collectAsStateWithLifecycle()
    // Passwords are deliberately not saved to instance state.
    var password by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val mode = AuthMode.valueOf(modeName)
    val c = Budget.colors

    Box(Modifier.fillMaxSize()) {
    com.personal.budget.ui.components.AmbientBackdrop()
    Box(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(40.dp))
            com.personal.budget.ui.components.BrandLockup(width = 160.dp)
            Spacer(Modifier.height(18.dp))
            Text("A clear view of your money.", style = Budget.type.title, color = c.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            BudgetCard(Modifier.fillMaxWidth()) {
                if (!vm.configured) {
                    Text("Backend not configured", style = Budget.type.cardTitle, color = c.bad)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "This build has no Supabase URL / publishable key. Fill in config/supabase.json (or set SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY) and rebuild.",
                        style = Budget.type.secondary,
                        color = c.ink2,
                    )
                    Spacer(Modifier.height(14.dp))
                }
                if (mode != AuthMode.Reset) {
                    SegmentedControl(
                        listOf(AuthMode.SignIn to "Sign in", AuthMode.SignUp to "Create account"),
                        mode,
                        vm::setMode,
                        fill = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text("Reset password", style = Budget.type.cardTitle, color = c.ink)
                    Text("We'll email you a link to choose a new password.", style = Budget.type.secondary, color = c.ink2)
                }
                Spacer(Modifier.height(14.dp))
                BudgetTextField(
                    email,
                    vm::setEmail,
                    label = "Email",
                    placeholder = "you@example.com",
                    keyboardType = KeyboardType.Email,
                    capitalization = KeyboardCapitalization.None,
                    enabled = vm.configured,
                )
                if (mode != AuthMode.Reset) {
                    Spacer(Modifier.height(10.dp))
                    BudgetTextField(
                        password,
                        { password = it },
                        label = "Password",
                        password = true,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        onIme = { vm.submit(password) },
                        capitalization = KeyboardCapitalization.None,
                        enabled = vm.configured,
                    )
                }
                message?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it.text, style = Budget.type.secondary, color = if (it.isError) c.bad else c.good)
                }
                Spacer(Modifier.height(16.dp))
                BudgetButton(
                    when (mode) {
                        AuthMode.SignIn -> if (busy) "Signing in…" else "Sign in"
                        AuthMode.SignUp -> if (busy) "Creating…" else "Create account"
                        AuthMode.Reset -> if (busy) "Sending…" else "Send reset link"
                    },
                    { vm.submit(password) },
                    kind = ButtonKind.Primary,
                    large = true,
                    enabled = vm.configured && !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (mode == AuthMode.Reset) "Back to sign in" else "Forgot password?",
                    style = Budget.type.secondary,
                    color = c.accentInk,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .tappable(onClick = { vm.setMode(if (mode == AuthMode.Reset) AuthMode.SignIn else AuthMode.Reset) })
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text("The first sign-in needs the internet. After that, everything works offline.", style = Budget.type.small, color = c.ink3, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
    }
    discard?.let { n ->
        ConfirmDialog(
            title = "Replace this device's data?",
            body = "This device holds $n unsynced change${if (n == 1) "" else "s"} from a different account. Signing in as $email removes them.",
            confirmLabel = "Remove and sign in",
            destructive = true,
            onConfirm = { vm.submit(password, discardOtherAccount = true) },
            onDismiss = { vm.dismissDiscard() },
        )
    }
}
