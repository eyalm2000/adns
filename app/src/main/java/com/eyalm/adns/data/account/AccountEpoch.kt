package com.eyalm.adns.data.account

/**
 * Names one period during which a given account was the active one.
 *
 * [sequence] grows every time the active account changes, including a change back to an account
 * that was active before. Work started under one epoch must not be shown or stored under
 * another, even when the account id is the same again.
 */
data class AccountEpoch(
    val accountId: String?,
    val sequence: Long,
)

/**
 * A cache whose entries belong to one [AccountEpoch]. It empties itself the first time it is used
 * after the epoch changed, so nothing can be read across accounts even if the owner has not yet
 * reacted to the switch. A value is stored only if the epoch it was requested under is still
 * current, so a response that arrives late is dropped.
 */
class AccountScopedCache<K, V>(private val currentEpoch: () -> AccountEpoch) {
    private val entries = HashMap<K, V>()
    private var owner: AccountEpoch = currentEpoch()

    @Synchronized
    operator fun get(key: K): V? {
        discardIfOwnerChanged()
        return entries[key]
    }

    @Synchronized
    operator fun contains(key: K): Boolean {
        discardIfOwnerChanged()
        return key in entries
    }

    /** Stores [value] and returns true only if [epoch] is still the current epoch. */
    @Synchronized
    fun put(epoch: AccountEpoch, key: K, value: V): Boolean {
        if (epoch != currentEpoch()) return false
        discardIfOwnerChanged()
        entries[key] = value
        return true
    }

    @Synchronized
    fun clear() {
        entries.clear()
        owner = currentEpoch()
    }

    private fun discardIfOwnerChanged() {
        val now = currentEpoch()
        if (now != owner) {
            entries.clear()
            owner = now
        }
    }
}
