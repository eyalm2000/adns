package com.eyalm.adns.data.account

/**
 * Identifies the credential generation a request was issued with.
 *
 * [version] is process-local and monotonic. It changes whenever the account's API key is
 * replaced or removed, so a response belonging to an old credential can never be attributed
 * to a newer one. It contains no secret material.
 */
data class AccountCredentialRef(
    val accountId: String,
    val version: Long,
)

/** Public, secret-free view of a stored account. */
data class AccountSummary(
    val id: String,
    val label: String,
    val selectedHostname: String?,
    val hasCredential: Boolean,
    /** The server rejected this account's key during this process. Never persisted. */
    val sessionExpired: Boolean = false,
    /**
     * The NextDNS profile the app's statistics and logs use for this account. It is separate from
     * [selectedHostname], which only mirrors the device-wide DNS setting.
     */
    val selectedProfileId: String? = null,
) {
    // The label is usually an email address; keep it out of logs and crash reports.
    override fun toString(): String = "AccountSummary(id=$id, hasCredential=$hasCredential)"
}

/** Session status of the active account. Derived from [AccountsSnapshot], never stored. */
enum class ActiveSessionStatus {
    SignedOut,
    Active,
    Expired,
}

data class AccountsSnapshot(
    val accounts: List<AccountSummary> = emptyList(),
    val activeAccountId: String? = null,
) {
    val active: AccountSummary?
        get() = accounts.firstOrNull { it.id == activeAccountId }

    /** The profile the app uses for statistics and logs right now. Null if none is chosen. */
    val activeProfileId: String?
        get() = active?.selectedProfileId

    /**
     * The one definition of the session state:
     * - no active account: signed out
     * - active account with a key: active
     * - active account whose key the server rejected in this process: expired
     * - active account without a key after a restart: signed out
     */
    val sessionStatus: ActiveSessionStatus
        get() {
            val account = active ?: return ActiveSessionStatus.SignedOut
            return when {
                account.hasCredential -> ActiveSessionStatus.Active
                account.sessionExpired -> ActiveSessionStatus.Expired
                else -> ActiveSessionStatus.SignedOut
            }
        }
}

/**
 * Thrown by TokenManager.signIn when an account could not be stored. Callers that sign users in
 * catch exactly this type, so unrelated programming errors are not hidden. It carries no secrets.
 */
class AccountStorageException : IllegalStateException("Unable to store account")

enum class AccountMigrationOutcome {
    /** No legacy credentials were present. */
    NotNeeded,

    /** Legacy credentials were copied, verified, and then removed. */
    Migrated,

    /** The account list already existed and no legacy credential remained to import. */
    AlreadyMigrated,

    /** A legacy key exists but cannot be decrypted. It was left untouched. */
    LegacyUnreadable,

    /** The account index exists but cannot be read. Nothing was modified. */
    IndexUnreadable,

    /** The new representation could not be persisted. Legacy data was left untouched. */
    WriteFailed,

    /** The new representation did not read back correctly. Legacy data was left untouched. */
    VerificationFailed,
}

sealed interface SignInResult {
    /** [existing] is true when the key already belonged to a stored account. */
    data class Success(val accountId: String, val existing: Boolean) : SignInResult
    data object Failed : SignInResult
}

sealed interface ExpireResult {
    /**
     * The credential was revoked in memory and can no longer be used by this process.
     * [persisted] is false when the durable copy could not be updated yet; it is retried by
     * the next write and by [NextDnsAccountRepository.flushPending].
     */
    data class Applied(
        val accountId: String,
        val wasActive: Boolean,
        val persisted: Boolean,
    ) : ExpireResult

    /** The credential was already replaced or removed, or the account no longer exists. */
    data object Stale : ExpireResult
}

/** Result of an operation that either fully applies or leaves the account state unchanged. */
enum class AccountUpdateResult {
    Applied,
    NotFound,

    /** The value was not acceptable, for example an empty name. Nothing was changed. */
    Invalid,

    /** The change could not be stored, so nothing was changed. */
    PersistFailed,
}

private const val NEXTDNS_HOSTNAME_SUFFIX = ".dns.nextdns.io"

/**
 * The profile id inside a NextDNS DNS hostname such as `Phone-abc123.dns.nextdns.io` or
 * `abc123.dns.nextdns.io`. Null for anything else, so a hostname that is not a NextDNS one is
 * never mistaken for a profile.
 */
fun profileIdFromNextDnsHostname(hostname: String?): String? {
    val value = hostname?.trim().orEmpty()
    if (!value.endsWith(NEXTDNS_HOSTNAME_SUFFIX, ignoreCase = true)) return null
    val prefix = value.dropLast(NEXTDNS_HOSTNAME_SUFFIX.length)
    return prefix.substringAfterLast('-').takeIf { it.isNotEmpty() }
}