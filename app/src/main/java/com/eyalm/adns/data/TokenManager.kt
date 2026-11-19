package com.eyalm.adns.data

import android.content.Context
import com.eyalm.adns.data.account.AccountCredentialRef
import com.eyalm.adns.data.account.AccountStorageException
import com.eyalm.adns.data.account.NextDnsAccountRepository
import com.eyalm.adns.data.account.NextDnsAccounts
import com.eyalm.adns.data.account.SignInResult

/**
 * Credential access for the active NextDNS account.
 *
 * Storage, encryption and migration live in [NextDnsAccountRepository]; this class keeps the
 * single-account surface the rest of the app already uses.
 */
class TokenManager private constructor(private val accounts: NextDnsAccountRepository) {
    fun getApiKey(): String? = accounts.activeApiKey()

    fun hasToken(): Boolean = getApiKey() != null

    /** Label (email) of the active account, or null while it has no valid credential. */
    fun getEmail(): String? =
        accounts.snapshot.value.active?.takeIf { it.hasCredential }?.label

    /** Names the active account's current credential, for attributing a request to it. */
    fun credentialRef(): AccountCredentialRef? = accounts.currentRef()

    /** Returns the key only if [ref] is still the account's current credential. */
    fun apiKeyFor(ref: AccountCredentialRef?): String? = accounts.apiKeyFor(ref)

    /**
     * Stores the credential and makes its account active. [loginEmail] is the email typed at a
     * password sign-in (null for API-key sign-ins); it lets a returning account be recognised
     * after its key expired. Blocking: call off the main thread.
     * Throws [AccountStorageException] (it contains no secrets) if the account could not be stored.
     */
    fun signIn(label: String, apiKey: String, loginEmail: String? = null): String =
        when (val result = accounts.signIn(label, apiKey, loginEmail)) {
            is SignInResult.Success -> result.accountId
            SignInResult.Failed -> throw AccountStorageException()
        }

    companion object {
        @Volatile
        private var instance: TokenManager? = null

        fun getInstance(context: Context): TokenManager =
            instance ?: synchronized(this) {
                instance ?: TokenManager(
                    NextDnsAccounts.getInstance(context.applicationContext)
                ).also { instance = it }
            }
    }
}
