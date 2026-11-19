package com.eyalm.adns.data.nextdns.auth

import android.content.Context
import com.eyalm.adns.data.account.AccountCredentialRef
import com.eyalm.adns.data.account.AccountEpoch
import com.eyalm.adns.data.account.AccountUpdateResult
import com.eyalm.adns.data.account.AccountsSnapshot
import com.eyalm.adns.data.account.ActiveSessionStatus
import com.eyalm.adns.data.account.NextDnsAccountRepository
import com.eyalm.adns.data.account.NextDnsAccounts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface NextDnsManagementSession {
    data object SignedOut : NextDnsManagementSession
    data object Active : NextDnsManagementSession
    data object Expired : NextDnsManagementSession
}

/**
 * Observable session state of the active NextDNS account.
 *
 * **State ownership:** [NextDnsAccountRepository] is the only owner of account and session
 * state. [state] is not stored here; it is recomputed from the repository's snapshot inside
 * the repository's own critical section after every change, whoever made the change. Calling
 * the repository directly (activating, removing or expiring an account) therefore cannot leave
 * this object disagreeing with it.
 *
 * What this class owns is only the "ask the user to sign in again" prompt, which is raised when
 * the session moves into [NextDnsManagementSession.Expired] and cleared when it moves anywhere
 * else or is dismissed.
 *
 * Authentication failures are attributed to the credential that issued the request
 * ([AccountCredentialRef]), never to whichever account is active now.
 */
class NextDnsSessionManager internal constructor(
    private val accounts: NextDnsAccountRepository,
) {
    private val _state =
        MutableStateFlow<NextDnsManagementSession>(NextDnsManagementSession.SignedOut)
    val state = _state.asStateFlow()

    private val _reauthenticationRequested = MutableStateFlow(false)
    val reauthenticationRequested = _reauthenticationRequested.asStateFlow()

    private val _accountEpoch = MutableStateFlow(AccountEpoch(accountId = null, sequence = 0))

    /**
     * Which account is active, and a counter that changes whenever that changes. Screens that
     * hold account data observe this to drop it, and tag the requests they start with it so a
     * late response from another account is ignored. It is derived from the repository's
     * snapshot together with [state], so the two cannot disagree.
     */
    val accountEpoch = _accountEpoch.asStateFlow()

    private val _activeProfileId = MutableStateFlow<String?>(null)

    /**
     * The profile the app uses for the active account's statistics and logs. It changes when the
     * account changes and when the user picks another profile for it.
     */
    val activeProfileId = _activeProfileId.asStateFlow()

    init {
        // Called immediately with the current snapshot, then after every repository change.
        accounts.addSnapshotListener(::onSnapshot)
    }

    private fun onSnapshot(snapshot: AccountsSnapshot) {
        val previous = _state.value
        val next = when (snapshot.sessionStatus) {
            ActiveSessionStatus.SignedOut -> NextDnsManagementSession.SignedOut
            ActiveSessionStatus.Active -> NextDnsManagementSession.Active
            ActiveSessionStatus.Expired -> NextDnsManagementSession.Expired
        }
        _state.value = next
        _activeProfileId.value = snapshot.activeProfileId
        val epoch = _accountEpoch.value
        if (snapshot.activeAccountId != epoch.accountId) {
            _accountEpoch.value = AccountEpoch(snapshot.activeAccountId, epoch.sequence + 1)
        }
        if (next != previous) {
            _reauthenticationRequested.value = next == NextDnsManagementSession.Expired
        }
    }

    /**
     * Handles a request that was rejected as unauthenticated. Ignored when [ref] is null or no
     * longer current. Only the account that issued the request is affected.
     */
    fun unauthorized(ref: AccountCredentialRef?) {
        if (ref == null) return
        accounts.expire(ref)
    }

    /**
     * Signs out only the active account; other accounts are left intact. With
     * [activateFallback] the next account that has a key becomes active in the same write.
     * Nothing changes unless [AccountUpdateResult.Applied] is returned.
     */
    fun signedOut(activateFallback: Boolean = false): AccountUpdateResult {
        val activeId = accounts.snapshot.value.activeAccountId
            ?: return AccountUpdateResult.NotFound
        return accounts.remove(activeId, activateFallback)
    }

    fun requestFeatureAccess(): Boolean {
        if (_state.value == NextDnsManagementSession.Active) {
            return true
        }
        _reauthenticationRequested.value = true
        return false
    }

    fun dismissReauthenticationRequest() {
        _reauthenticationRequested.value = false
    }

    companion object {
        @Volatile
        private var instance: NextDnsSessionManager? = null

        fun getInstance(context: Context): NextDnsSessionManager =
            instance ?: synchronized(this) {
                instance ?: NextDnsSessionManager(
                    NextDnsAccounts.getInstance(context.applicationContext)
                ).also { instance = it }
            }
    }
}
