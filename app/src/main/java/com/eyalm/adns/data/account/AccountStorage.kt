package com.eyalm.adns.data.account

/** Minimal persistent string store with all-or-nothing multi-key writes. */
interface SecureKeyValueStore {
    fun get(key: String): String?

    /** Every key currently present. */
    fun keys(): Set<String>

    /**
     * Applies every edit as one write. A null value removes the key.
     * Returns false when the edits were not durably persisted.
     *
     * A failed write may still be visible to later [get] and [keys] calls in this process
     * (Android's SharedPreferences applies edits to its in-memory map before writing the file),
     * and a later successful write then also persists it. Callers must therefore not rely on a
     * failed write being rolled back and must always write the complete intended state.
     */
    fun commit(edits: Map<String, String?>): Boolean
}

/**
 * Authenticated encryption of short strings.
 *
 * [associatedData] binds a ciphertext to its storage slot so a blob copied to another slot
 * fails authentication.
 */
interface SecretCipher {
    fun encrypt(plaintext: String, associatedData: String?): String

    /** Returns null when the value cannot be decrypted or authenticated. */
    fun decrypt(ciphertext: String, associatedData: String?): String?
}
