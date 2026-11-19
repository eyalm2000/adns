package com.eyalm.adns.data.account

/**
 * In-memory [SecureKeyValueStore] that mimics the SharedPreferences behaviour the repository
 * has to cope with (see the AOSP `SharedPreferencesImpl.commit`):
 *
 * - [entries] is what is durably on disk, and is what a restart would see.
 * - A failed commit still changes the in-process view (an overlay over [entries]) but not the
 *   disk. A later successful commit writes the overlay too.
 * - [restart] simulates the process dying: the overlay is lost.
 */
class FakeSecureStore : SecureKeyValueStore {
    /** Durable content. Tests may seed this directly to represent data from an earlier run. */
    val entries = LinkedHashMap<String, String>()

    private val overlay = LinkedHashMap<String, String?>()

    var failCommits = false
    var commitCount = 0
        private set

    /** Keys whose value is replaced by garbage as it is written, to simulate bad read-back. */
    val corruptOnWrite = mutableSetOf<String>()

    override fun get(key: String): String? =
        if (overlay.containsKey(key)) overlay[key] else entries[key]

    override fun keys(): Set<String> {
        val result = LinkedHashSet(entries.keys)
        for ((key, value) in overlay) {
            if (value == null) result.remove(key) else result.add(key)
        }
        return result
    }

    override fun commit(edits: Map<String, String?>): Boolean {
        commitCount++
        val processed = LinkedHashMap<String, String?>()
        for ((key, value) in edits) {
            processed[key] = if (value != null && key in corruptOnWrite) "garbage" else value
        }
        if (failCommits) {
            overlay.putAll(processed)
            return false
        }
        for ((key, value) in overlay + processed) {
            if (value == null) entries.remove(key) else entries[key] = value
        }
        overlay.clear()
        return true
    }

    /** Simulates process death: in-process leftovers of failed writes disappear. */
    fun restart() {
        overlay.clear()
    }

    /** Durable content, copied. */
    fun snapshot(): Map<String, String> = LinkedHashMap(entries)
}
/**
 * Reversible stand-in for Tink. The ciphertext does not contain the plaintext and only
 * decrypts with the same associated data, which is the property the repository relies on.
 */
class FakeSecretCipher : SecretCipher {
    var failEncrypt = false

    /** Simulates a Keystore that cannot be used yet: nothing can be decrypted while this is set. */
    var failDecrypt = false

    override fun encrypt(plaintext: String, associatedData: String?): String {
        check(!failEncrypt) { "encryption unavailable" }
        return "$MAGIC${hex(associatedData.orEmpty())}.${hex(plaintext)}"
    }

    override fun decrypt(ciphertext: String, associatedData: String?): String? {
        if (failDecrypt) return null
        if (!ciphertext.startsWith(MAGIC)) return null
        val parts = ciphertext.removePrefix(MAGIC).split('.')
        if (parts.size != 2 || parts[0] != hex(associatedData.orEmpty())) return null
        return unhex(parts[1])
    }

    /** Builds a value as the previous single-account TokenManager stored it (no associated data). */
    fun legacy(plaintext: String): String = encrypt(plaintext, null)

    private fun hex(value: String): String =
        value.toByteArray(Charsets.UTF_8)
            .joinToString("") { "%02x".format((it.toInt() xor MASK) and 0xFF) }

    private fun unhex(value: String): String? = try {
        val bytes = ByteArray(value.length / 2) { index ->
            (value.substring(index * 2, index * 2 + 2).toInt(16) xor MASK).toByte()
        }
        String(bytes, Charsets.UTF_8)
    } catch (_: NumberFormatException) {
        null
    }

    private companion object {
        const val MAGIC = "fake:"
        const val MASK = 0x5A
    }
}

fun newRepository(
    store: FakeSecureStore,
    cipher: FakeSecretCipher = FakeSecretCipher(),
    legacyHostname: String? = null,
    idPrefix: String = "id",
): NextDnsAccountRepository {
    val ids = generateSequence(1) { it + 1 }.map { "$idPrefix-$it" }.iterator()
    return NextDnsAccountRepository(
        store = store,
        cipher = cipher,
        legacySelectedHostname = { legacyHostname },
        newId = { ids.next() },
    )
}

fun FakeSecureStore.putLegacy(
    cipher: FakeSecretCipher,
    apiKey: String?,
    email: String? = null,
) {
    apiKey?.let { entries[NextDnsAccountRepository.LEGACY_API_KEY] = cipher.legacy(it) }
    email?.let { entries[NextDnsAccountRepository.LEGACY_EMAIL] = cipher.legacy(it) }
}
