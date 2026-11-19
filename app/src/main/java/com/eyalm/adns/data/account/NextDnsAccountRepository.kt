package com.eyalm.adns.data.account

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stores zero or more NextDNS accounts (API keys plus per-account metadata) and is the single
 * authority for which account is active and what its session status is.
 *
 * ## Storage layout
 * All values are encrypted with [cipher]:
 * - `accounts_v1`: JSON index of account ids, labels, login emails, selected hostnames, and the
 *   active id.
 * - `account_key_<id>`: the API key of one account, bound to its id as associated data.
 *
 * An account without a stored key keeps its label and selected hostname.
 *
 * ## Legacy data and downgrades
 * Earlier versions kept one key and one email in pi_key and email of the same file. They are
 * imported once and deleted after the copy was read back successfully. An older app version
 * started afterwards finds no pi_key, so it shows the user as signed out and needs a fresh
 * sign-in. Keeping a second copy of the key for that case was rejected on purpose.
 *
 * ## Writes
 * Every write persists the complete intended state (index, every key, removal of every key that
 * no longer belongs to an account) in one [SecureKeyValueStore.commit]. A single commit is
 * all-or-nothing on disk. Because a failed commit can leave its edits in the store's in-process
 * view, no write relies on an earlier one having been rolled back: the next successful write
 * always makes the durable state equal to the in-memory state, including leftovers of a failed
 * attempt.
 *
 * ## Unreadable index
 * If the index cannot be read at start-up the repository stays signed out and changes nothing. The
 * first operation that could write reads the stored index again and adopts it if it is readable by
 * then. If it is still unreadable, a write replaces it only after the old one was copied to a
 * backup slot (`accounts_v1_unreadable_backup`, then `_2` ... `_5`). Backups are never
 * overwritten or deleted, and if no slot is free the write is refused. While any backup exists,
 * key blobs that no account refers to are not deleted, because they may belong to a backed-up index.
 * Nothing restores a backup automatically.
 *
 * ## Which copy wins when a write fails
 * - User-initiated changes (sign-in, removal, activation, selected hostname) persist first and
 *   change memory only after the commit succeeded. On failure nothing changes and the caller
 *   gets an explicit result, so memory and disk still agree.
 * - A credential rejected by the server ([expire]) is revoked in memory immediately, because it
 *   must not be sent again. The durable copy is then brought up to date by retrying: the next
 *   write, [flushPending], or the next rejected request. If the process dies first, the old key
 *   is loaded again and the server rejects it again; it was already invalid.
 *
 * Secrets are never persisted outside the cipher and never appear in logs or exceptions.
 */
class NextDnsAccountRepository(
    private val store: SecureKeyValueStore,
    private val cipher: SecretCipher,
    private val legacySelectedHostname: () -> String? = { null },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private data class Record(
        val id: String,
        val label: String,
        /** Email the account signed in with. Null for API-key sign-ins and unknown legacy labels. */
        val loginEmail: String?,
        val selectedHostname: String?,
        val selectedProfileId: String? = null,
    )

    private class State(
        val records: List<Record>,
        val activeId: String?,
        val keys: Map<String, String>,
    )

    private class LoadedIndex(
        val state: State,
        /** Accounts whose key blob exists but could not be decrypted. */
        val unreadableKeyIds: Set<String>,
    )

    private val lock = Any()
    private var records: List<Record> = emptyList()
    private var activeId: String? = null
    private val keys = HashMap<String, String>()
    private val versions = HashMap<String, Long>()
    private var versionCounter = 0L
    private val expiredInSession = HashSet<String>()
    private val unreadableKeyIds = HashSet<String>()
    private var unreadableIndex: String? = null
    private val listeners = ArrayList<(AccountsSnapshot) -> Unit>()

    private val _snapshot = MutableStateFlow(AccountsSnapshot())
    val snapshot: StateFlow<AccountsSnapshot> = _snapshot.asStateFlow()

    @Volatile
    var migrationOutcome: AccountMigrationOutcome = AccountMigrationOutcome.NotNeeded
        private set

    /** True while a revocation is applied in memory but not yet written. */
    @Volatile
    var hasPendingWrite: Boolean = false
        private set

    init {
        synchronized(lock) { load() }
    }

    /**
     * Registers [listener] and calls it with the current snapshot. It is called again, on the
     * thread that made the change and while the repository lock is held, after every change.
     * It must be quick and must not call back into the repository.
     */
    fun addSnapshotListener(listener: (AccountsSnapshot) -> Unit) {
        synchronized(lock) {
            listeners += listener
            listener(_snapshot.value)
        }
    }

    fun activeApiKey(): String? = synchronized(lock) {
        activeId?.let { keys[it] }
    }

    /** Reference to the active account's current credential, or null if it has none. */
    fun currentRef(): AccountCredentialRef? = synchronized(lock) {
        val id = activeId ?: return@synchronized null
        val version = versions[id] ?: return@synchronized null
        AccountCredentialRef(id, version)
    }

    /** Returns the key only while [ref] still names the account's current credential. */
    fun apiKeyFor(ref: AccountCredentialRef?): String? {
        if (ref == null) return null
        return synchronized(lock) {
            if (versions[ref.accountId] == ref.version) keys[ref.accountId] else null
        }
    }

    /**
     * Stores [apiKey] and makes its account active.
     *
     * - A key identical to one already stored reuses that account.
     * - Otherwise, if [loginEmail] is given and exactly one stored account without a key was
     *   signed in with that same email, that account is re-keyed. A display label never
     *   decides this: API-key sign-ins all share one placeholder label.
     * - Otherwise, including when more than one account matches, a new account is created.
     *   Distinct accounts are never merged on a guess.
     *
     * Duplicate detection compares keys in memory; no fingerprint of a key is stored. Accounts
     * without a key cannot be compared, which is why they are matched only by [loginEmail].
     */
    fun signIn(label: String, apiKey: String, loginEmail: String? = null): SignInResult {
        if (apiKey.isBlank()) return SignInResult.Failed
        val email = loginEmail?.trim()?.takeIf { it.isNotEmpty() }
        return synchronized(lock) {
            retryUnreadableIndex()
            signInLocked(label.trim(), apiKey, email)
        }
    }

    /**
     * Revokes the credential [ref] names. Applies only if [ref] is still current, so a late
     * authentication failure from a replaced credential cannot expire a newer one, and the
     * check and the revocation are one atomic step, so concurrent failures revoke it once.
     */
    fun expire(ref: AccountCredentialRef): ExpireResult = synchronized(lock) {
        if (versions[ref.accountId] != ref.version || !keys.containsKey(ref.accountId)) {
            return@synchronized ExpireResult.Stale
        }
        keys.remove(ref.accountId)
        versions.remove(ref.accountId)
        expiredInSession += ref.accountId
        hasPendingWrite = true
        val persisted = persist(currentState())
        publish()
        ExpireResult.Applied(
            accountId = ref.accountId,
            wasActive = ref.accountId == activeId,
            persisted = persisted,
        )
    }

    /**
     * Removes one account and its key. If the removed account was active, no account is
     * active afterwards. On [AccountUpdateResult.PersistFailed] nothing was changed.
     */
    fun remove(accountId: String, activateFallback: Boolean = false): AccountUpdateResult =
        synchronized(lock) {
            retryUnreadableIndex()
            val record = records.firstOrNull { it.id == accountId }
                ?: return@synchronized AccountUpdateResult.NotFound
            val newKeys = HashMap<String, String>(keys)
            newKeys.remove(accountId)
            val remaining = records - record
            val newActive = when {
                activeId != accountId -> activeId
                // The next account that can still be used, chosen in the same write, so there is
                // no moment in which nothing is active although another account could be.
                activateFallback -> remaining.firstOrNull { newKeys.containsKey(it.id) }?.id
                else -> null
            }
            commit(State(remaining, newActive, newKeys))
        }

    /** Changes the display name of [accountId]. It never changes how the account is recognised. */
    fun rename(accountId: String, label: String): AccountUpdateResult = synchronized(lock) {
        retryUnreadableIndex()
        val clean = label.trim()
        if (clean.isEmpty()) return@synchronized AccountUpdateResult.Invalid
        val record = records.firstOrNull { it.id == accountId }
            ?: return@synchronized AccountUpdateResult.NotFound
        if (record.label == clean) return@synchronized AccountUpdateResult.Applied
        commit(
            State(
                records = records.map { if (it.id == accountId) it.copy(label = clean) else it },
                activeId = activeId,
                keys = HashMap(keys),
            )
        )
    }

    /**
     * Records which NextDNS profile the app uses for [accountId]'s statistics and logs. A null
     * profile clears it. This does not touch the device DNS setting.
     */
    fun setSelectedProfile(accountId: String, profileId: String?): AccountUpdateResult =
        synchronized(lock) {
            retryUnreadableIndex()
            val record = records.firstOrNull { it.id == accountId }
                ?: return@synchronized AccountUpdateResult.NotFound
            val normalized = profileId?.trim()?.takeIf { it.isNotEmpty() }
            if (record.selectedProfileId == normalized) {
                return@synchronized AccountUpdateResult.Applied
            }
            commit(
                State(
                    records = records.map {
                        if (it.id == accountId) it.copy(selectedProfileId = normalized) else it
                    },
                    activeId = activeId,
                    keys = HashMap(keys),
                )
            )
        }

    /** Records the NextDNS hostname chosen for [accountId]. A null hostname clears it. */
    fun setSelectedHostname(accountId: String, hostname: String?): AccountUpdateResult =
        synchronized(lock) {
            retryUnreadableIndex()
            val record = records.firstOrNull { it.id == accountId }
                ?: return@synchronized AccountUpdateResult.NotFound
            val normalized = hostname?.trim()?.takeIf { it.isNotEmpty() }
            if (record.selectedHostname == normalized) {
                return@synchronized AccountUpdateResult.Applied
            }
            commit(
                State(
                    records = records.map {
                        if (it.id == accountId) it.copy(selectedHostname = normalized) else it
                    },
                    activeId = activeId,
                    keys = HashMap(keys),
                )
            )
        }

    fun setActive(accountId: String): AccountUpdateResult = synchronized(lock) {
        retryUnreadableIndex()
        if (records.none { it.id == accountId }) {
            return@synchronized AccountUpdateResult.NotFound
        }
        if (activeId == accountId) return@synchronized AccountUpdateResult.Applied
        commit(State(records, accountId, HashMap(keys)))
    }

    /** Retries writing a revocation that could not be stored. Returns true when nothing is pending. */
    fun flushPending(): Boolean = synchronized(lock) {
        if (!hasPendingWrite) return@synchronized true
        persist(currentState())
    }

    private fun signInLocked(label: String, apiKey: String, loginEmail: String?): SignInResult {
        val duplicate = records.firstOrNull { record ->
            keys[record.id]?.let { secretEquals(it, apiKey) } == true
        }
        val target: Record
        val newRecords: List<Record>
        val existing: Boolean
        if (duplicate != null) {
            target = duplicate.copy(
                // A name the user chose, or one set earlier, is kept.
                label = duplicate.label.ifEmpty { label },
                loginEmail = loginEmail ?: duplicate.loginEmail,
            )
            newRecords = records.map { if (it.id == duplicate.id) target else it }
            existing = true
        } else {
            val rekey = if (loginEmail == null) {
                null
            } else {
                records
                    .filter {
                        !keys.containsKey(it.id) &&
                            it.loginEmail.equals(loginEmail, ignoreCase = true)
                    }
                    .singleOrNull()
            }
            if (rekey != null) {
                target = rekey.copy(label = rekey.label.ifEmpty { label })
                newRecords = records.map { if (it.id == rekey.id) target else it }
                existing = true
            } else {
                target = Record(newId(), label, loginEmail, null, null)
                newRecords = records + target
                existing = false
            }
        }
        val newKeys = HashMap<String, String>(keys)
        newKeys[target.id] = apiKey
        val result = commit(State(newRecords, target.id, newKeys))
        return if (result == AccountUpdateResult.Applied) {
            SignInResult.Success(target.id, existing)
        } else {
            SignInResult.Failed
        }
    }

    /** Persists [state] and, only if that succeeded, makes it the in-memory state. */
    private fun commit(state: State): AccountUpdateResult {
        if (!persist(state)) return AccountUpdateResult.PersistFailed
        applyState(state)
        return AccountUpdateResult.Applied
    }

    private fun currentState() = State(records, activeId, HashMap(keys))

    private fun applyState(state: State) {
        for ((id, key) in state.keys) {
            if (keys[id] != key) versions[id] = ++versionCounter
        }
        for (id in keys.keys.toList()) {
            if (id !in state.keys) versions.remove(id)
        }
        keys.clear()
        keys.putAll(state.keys)
        records = state.records
        activeId = state.activeId
        val ids = records.mapTo(HashSet()) { it.id }
        expiredInSession.retainAll(ids)
        expiredInSession.removeAll(keys.keys)
        unreadableKeyIds.retainAll(ids)
        unreadableKeyIds.removeAll(keys.keys)
        publish()
    }

    private fun load() {
        val rawIndex = store.get(INDEX_KEY)
        if (rawIndex != null) {
            val loaded = readIndex(rawIndex)
            if (loaded == null) {
                // Never overwrite or delete anything while the index cannot be read.
                unreadableIndex = rawIndex
                migrationOutcome = AccountMigrationOutcome.IndexUnreadable
                return
            }
            adoptIndex(loaded)
        }
        importLegacy()
        publish()
    }

    /** Makes a readable index the in-memory state. Only used while no account is loaded. */
    private fun adoptIndex(loaded: LoadedIndex) {
        unreadableIndex = null
        records = loaded.state.records
        activeId = loaded.state.activeId
        for ((id, key) in loaded.state.keys) {
            keys[id] = key
            versions[id] = ++versionCounter
        }
        unreadableKeyIds += loaded.unreadableKeyIds
        migrationOutcome = AccountMigrationOutcome.AlreadyMigrated
        removeOrphanKeys()
    }

    /**
     * Called first by every operation that could write. If the stored index was unreadable when
     * the repository started, it is read again, because the cause may have been temporary
     * (for example a Keystore that was not ready yet). If it can be read now it is adopted, and
     * the operation runs on the recovered accounts instead of replacing them.
     *
     * The index that is read is the one in storage now, never an older copy held in memory, so
     * a newer valid index cannot be overwritten. If it is still unreadable nothing changes: the
     * accounts stay hidden and a later write keeps the old index as a backup (see [persist]).
     */
    private fun retryUnreadableIndex() {
        if (unreadableIndex == null) return
        val stored = store.get(INDEX_KEY) ?: return
        unreadableIndex = stored
        val loaded = readIndex(stored) ?: return
        adoptIndex(loaded)
        importLegacy()
        publish()
    }

    /**
     * Key blobs that no account in the current index refers to are only leftovers of a failed
     * write while the index is trustworthy. After an index could not be read, they may belong to
     * the accounts of that index. Those accounts' keys cannot be read now either, but might be
     * once the Keystore works again, so nothing is deleted while the unreadable index is
     * pending or its backup exists. The cost is that genuine leftovers stay (encrypted) until
     * that backup is dealt with.
     */
    private fun orphanCleanupAllowed(): Boolean =
        unreadableIndex == null && store.keys().none { it.startsWith(INDEX_BACKUP_KEY) }

    private sealed interface BackupPlan {
        data object AlreadyPreserved : BackupPlan
        data class WriteTo(val key: String) : BackupPlan
        data object NoFreeSlot : BackupPlan
    }

    /**
     * Decides where the unreadable index [raw] is kept before it is replaced. Existing backups
     * are never overwritten: the first slot is the original single backup key, further slots are
     * numbered. An identical copy that is already stored is not stored again.
     */
    private fun planBackup(raw: String): BackupPlan {
        var free: String? = null
        for (slot in 1..MAX_INDEX_BACKUPS) {
            val key = backupKey(slot)
            val existing = store.get(key)
            if (existing == raw) return BackupPlan.AlreadyPreserved
            if (existing == null && free == null) free = key
        }
        return free?.let { BackupPlan.WriteTo(it) } ?: BackupPlan.NoFreeSlot
    }

    /** Deletes key blobs of accounts that are not in the index, such as leftovers of a failed write. */
    private fun removeOrphanKeys() {
        if (!orphanCleanupAllowed()) return
        val known = records.mapTo(HashSet()) { it.id }
        val orphans = store.keys().filter {
            it.startsWith(KEY_PREFIX) && it.removePrefix(KEY_PREFIX) !in known
        }
        if (orphans.isNotEmpty()) {
            store.commit(orphans.associateWith<String, String?> { null })
        }
    }

    private fun importLegacy() {
        val legacyKeyRaw = store.get(LEGACY_API_KEY) ?: return
        val legacyKey = cipher.decrypt(legacyKeyRaw, null)?.takeIf { it.isNotBlank() }
        if (legacyKey == null) {
            // Keep the entry: it might become readable again, and deleting it would lose it.
            migrationOutcome = AccountMigrationOutcome.LegacyUnreadable
            return
        }

        val holder = records.firstOrNull { record ->
            keys[record.id]?.let { secretEquals(it, legacyKey) } == true
        }
        if (holder != null) {
            // An earlier run already stored this key; only the cleanup was missing.
            store.commit(mapOf(LEGACY_API_KEY to null, LEGACY_EMAIL to null))
            migrationOutcome = AccountMigrationOutcome.AlreadyMigrated
            return
        }

        val legacyLabel = store.get(LEGACY_EMAIL)
            ?.let { cipher.decrypt(it, null) }
            ?.trim()
            .orEmpty()
        val id = newId()
        val hostname = if (records.isEmpty()) {
            legacySelectedHostname()?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            null
        }
        val newKeys = HashMap<String, String>(keys)
        newKeys[id] = legacyKey
        val newState = State(
            records = records + Record(
                id = id,
                label = legacyLabel,
                loginEmail = emailIdentity(legacyLabel),
                selectedHostname = hostname,
                // Until now the app's profile was the one in the device hostname.
                selectedProfileId = profileIdFromNextDnsHostname(hostname),
            ),
            activeId = activeId ?: id,
            keys = newKeys,
        )
        val previousIndex = store.get(INDEX_KEY)

        if (!persist(newState)) {
            migrationOutcome = AccountMigrationOutcome.WriteFailed
            return
        }
        val verified = store.get(INDEX_KEY)?.let { readIndex(it) }
        val intact = verified != null &&
            verified.state.records.map { it.id } == newState.records.map { it.id } &&
            newState.keys.all { (accountId, key) -> verified.state.keys[accountId] == key }
        if (!intact) {
            // Put the previous index back and drop the new key; legacy data was never touched.
            store.commit(mapOf(INDEX_KEY to previousIndex, keyEntry(id) to null))
            migrationOutcome = AccountMigrationOutcome.VerificationFailed
            return
        }

        applyState(newState)
        migrationOutcome = AccountMigrationOutcome.Migrated
        // Legacy copies are removed only after the new ones were read back successfully. If
        // this write fails the next start finds the key already stored and retries the cleanup.
        store.commit(mapOf(LEGACY_API_KEY to null, LEGACY_EMAIL to null))
    }

    private fun readIndex(raw: String): LoadedIndex? {
        val json = cipher.decrypt(raw, INDEX_AAD) ?: return null
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            if (root.get("version")?.asInt != INDEX_VERSION) return null
            val list = ArrayList<Record>()
            for (element in root.getAsJsonArray("accounts")) {
                val entry = element.asJsonObject
                val id = entry.get("id").asString
                if (id.isBlank() || list.any { it.id == id }) return null
                list += Record(
                    id = id,
                    label = entry.optString("label").orEmpty(),
                    loginEmail = entry.optString("email"),
                    selectedHostname = entry.optString("hostname"),
                    selectedProfileId = entry.optString("profile"),
                )
            }
            val active = root.optString("active")
                ?.takeIf { candidate -> list.any { it.id == candidate } }
            val keyMap = HashMap<String, String>()
            val unreadable = HashSet<String>()
            for (record in list) {
                val blob = store.get(keyEntry(record.id)) ?: continue
                val key = cipher.decrypt(blob, keyAad(record.id))?.takeIf { it.isNotBlank() }
                if (key != null) keyMap[record.id] = key else unreadable += record.id
            }
            LoadedIndex(State(list, active, keyMap), unreadable)
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.optString(name: String): String? =
        get(name)?.takeIf { !it.isJsonNull }?.asString

    private fun encryptIndex(state: State): String {
        val root = JsonObject()
        root.addProperty("version", INDEX_VERSION)
        if (state.activeId != null) root.addProperty("active", state.activeId)
        val array = JsonArray()
        for (record in state.records) {
            val entry = JsonObject()
            entry.addProperty("id", record.id)
            entry.addProperty("label", record.label)
            if (record.loginEmail != null) entry.addProperty("email", record.loginEmail)
            if (record.selectedHostname != null) {
                entry.addProperty("hostname", record.selectedHostname)
            }
            if (record.selectedProfileId != null) {
                entry.addProperty("profile", record.selectedProfileId)
            }
            array.add(entry)
        }
        root.add("accounts", array)
        return cipher.encrypt(root.toString(), INDEX_AAD)
    }

    /**
     * Writes [state] completely in one commit: the index, every key, and the removal of every
     * key blob that does not belong to a key of [state]. Returns false if it was not stored.
     */
    private fun persist(state: State): Boolean {
        val edits = HashMap<String, String?>()
        try {
            edits[INDEX_KEY] = encryptIndex(state)
            for (record in state.records) {
                val key = state.keys[record.id]
                when {
                    key != null ->
                        edits[keyEntry(record.id)] = cipher.encrypt(key, keyAad(record.id))
                    // An undecryptable blob might become readable again; do not destroy it.
                    record.id in unreadableKeyIds -> Unit
                    else -> edits[keyEntry(record.id)] = null
                }
            }
        } catch (_: Exception) {
            return false
        }
        if (orphanCleanupAllowed()) {
            val known = state.records.mapTo(HashSet()) { it.id }
            for (entry in store.keys()) {
                if (entry.startsWith(KEY_PREFIX) && entry.removePrefix(KEY_PREFIX) !in known) {
                    edits[entry] = null
                }
            }
        }
        // Keep a copy of an unreadable index before replacing it, so nothing is lost silently.
        // If there is no free slot to keep it in, fail closed: the unreadable index stays as it is.
        val unreadable = unreadableIndex
        if (unreadable != null) {
            when (val plan = planBackup(unreadable)) {
                BackupPlan.AlreadyPreserved -> Unit
                is BackupPlan.WriteTo -> edits[plan.key] = unreadable
                BackupPlan.NoFreeSlot -> return false
            }
        }
        val persisted = store.commit(edits)
        if (persisted) {
            unreadableIndex = null
            hasPendingWrite = false
        }
        return persisted
    }

    private fun publish() {
        val snapshot = AccountsSnapshot(
            accounts = records.map {
                AccountSummary(
                    id = it.id,
                    label = it.label,
                    selectedHostname = it.selectedHostname,
                    hasCredential = keys.containsKey(it.id),
                    sessionExpired = it.id in expiredInSession,
                    selectedProfileId = it.selectedProfileId,
                )
            },
            activeAccountId = activeId,
        )
        _snapshot.value = snapshot
        for (listener in listeners) listener(snapshot)
    }

    private fun secretEquals(a: String, b: String): Boolean = MessageDigest.isEqual(
        a.toByteArray(Charsets.UTF_8),
        b.toByteArray(Charsets.UTF_8),
    )

    /** A stored label is only treated as a login email if it looks like one. */
    private fun emailIdentity(label: String): String? =
        label.takeIf { it.isNotEmpty() && '@' in it && ' ' !in it }

    companion object {
        internal const val INDEX_KEY = "accounts_v1"
        internal const val INDEX_BACKUP_KEY = "accounts_v1_unreadable_backup"
        internal const val MAX_INDEX_BACKUPS = 5

        /** Slot 1 is [INDEX_BACKUP_KEY]; later slots are numbered, starting at 2. */
        internal fun backupKey(slot: Int): String =
            if (slot == 1) INDEX_BACKUP_KEY else "${INDEX_BACKUP_KEY}_$slot"
        internal const val LEGACY_API_KEY = "api_key"
        internal const val LEGACY_EMAIL = "email"
        internal const val KEY_PREFIX = "account_key_"
        private const val INDEX_AAD = "adns.accounts.index.v1"
        private const val INDEX_VERSION = 1

        internal fun keyEntry(accountId: String): String = KEY_PREFIX + accountId
        internal fun keyAad(accountId: String): String = "adns.accounts.key.v1:$accountId"
    }
}
