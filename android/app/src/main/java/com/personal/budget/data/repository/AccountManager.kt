package com.personal.budget.data.repository

import com.personal.budget.data.local.PrefsStore
import com.personal.budget.data.remote.SignUpResult

sealed interface AccountOutcome {
    data object SignedIn : AccountOutcome
    data class ConfirmEmail(val email: String) : AccountOutcome
    /** Another account's unsynced local changes would be discarded by signing in. */
    data class WouldDiscard(val pending: Int) : AccountOutcome
}

sealed interface SignOutOutcome {
    data object Done : SignOutOutcome
    /** The final push did not get everything to the server. Ask before discarding. */
    data class Unsynced(val pending: Int) : SignOutOutcome
}

/** Sign-in / sign-up / sign-out flows that touch both the session and local data. */
class AccountManager(
    private val auth: AuthRepository,
    private val repo: BudgetRepository,
    private val prefs: PrefsStore,
    private val engine: SyncEngine,
    private val scheduler: SyncScheduler,
    private val pendingCount: suspend () -> Int,
) {
    suspend fun signIn(email: String, password: String, discardOtherAccount: Boolean = false): AccountOutcome {
        val session = auth.signIn(email, password)
        val owner = prefs.current().ownerUserId
        if (owner != null && owner != session.user.id) {
            val pending = pendingCount()
            if (pending > 0 && !discardOtherAccount) return AccountOutcome.WouldDiscard(pending)
            repo.clearAll()
        }
        prefs.setOwner(session.user.id, session.user.email ?: email.trim())
        auth.adopt(session)
        scheduler.schedulePeriodic()
        scheduler.requestSync()
        return AccountOutcome.SignedIn
    }

    suspend fun signUp(email: String, password: String): AccountOutcome = when (val r = auth.signUp(email, password)) {
        is SignUpResult.ConfirmationRequired -> AccountOutcome.ConfirmEmail(r.email)
        is SignUpResult.SignedIn -> {
            val owner = prefs.current().ownerUserId
            if (owner != null && owner != r.session.user.id) repo.clearAll()
            prefs.setOwner(r.session.user.id, r.session.user.email ?: email.trim())
            auth.adopt(r.session)
            scheduler.schedulePeriodic()
            scheduler.requestSync()
            AccountOutcome.SignedIn
        }
    }

    suspend fun sendPasswordReset(email: String) = auth.sendPasswordReset(email)

    /** Pushes first. Local data is cleared only after a clean push, or when [force] is set. */
    suspend fun signOut(force: Boolean): SignOutOutcome {
        if (!force) {
            val pending = if (auth.hasSession()) engine.pushOnly() else pendingCount()
            if (pending > 0) return SignOutOutcome.Unsynced(pending)
        }
        scheduler.cancelPeriodic()
        engine.exclusive {
            auth.signOut()
            repo.clearAll()
            prefs.setOwner(null, null)
        }
        return SignOutOutcome.Done
    }
}
