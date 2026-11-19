package com.eyalm.adns.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What happens to stored key blobs when the account index cannot be read, for example after a
 * Keystore failure. Their accounts' keys are unreadable then too, but may become readable again,
 * so they must not be treated as leftovers.
 */
class AccountUnreadableIndexTest {
    private val cipher = FakeSecretCipher()
    private val store = FakeSecureStore()

    private val slotA = NextDnsAccountRepository.keyEntry("id-1")
    private val slotB = NextDnsAccountRepository.keyEntry("id-2")
    private lateinit var originalIndex: String
    private lateinit var blobA: String
    private lateinit var blobB: String

    /** Two accounts are stored, then their index is damaged. */
    private fun storeTwoAccountsThenDamageIndex() {
        val repository = newRepository(store, cipher)
        repository.signIn("a@example.test", "key-a", "a@example.test")
        repository.signIn("b@example.test", "key-b", "b@example.test")
        originalIndex = store.entries.getValue(NextDnsAccountRepository.INDEX_KEY)
        blobA = store.entries.getValue(slotA)
        blobB = store.entries.getValue(slotB)
        store.entries[NextDnsAccountRepository.INDEX_KEY] = "garbage"
    }

    @Test
    fun `signing in with an unreadable index keeps its backup and every existing key blob`() {
        storeTwoAccountsThenDamageIndex()
        val damaged = newRepository(store, cipher, idPrefix = "new")
        assertEquals(AccountMigrationOutcome.IndexUnreadable, damaged.migrationOutcome)

        val result = damaged.signIn("c@example.test", "key-c", "c@example.test")

        assertTrue(result is SignInResult.Success)
        assertEquals("garbage", store.entries[NextDnsAccountRepository.INDEX_BACKUP_KEY])
        assertEquals(blobA, store.entries[slotA])
        assertEquals(blobB, store.entries[slotB])
        assertNotNull(store.entries[NextDnsAccountRepository.keyEntry("new-1")])
        assertEquals("key-c", damaged.activeApiKey())
    }

    @Test
    fun `the preserved key blobs survive later restarts and writes`() {
        storeTwoAccountsThenDamageIndex()
        newRepository(store, cipher, idPrefix = "new")
            .signIn("c@example.test", "key-c", "c@example.test")

        // The index is readable again now, but the backup of the old one still exists.
        val restarted = newRepository(store, cipher, idPrefix = "again")
        restarted.setSelectedHostname(restarted.snapshot.value.activeAccountId!!, "c.dns.nextdns.io")
        newRepository(store, cipher, idPrefix = "third")

        assertEquals(blobA, store.entries[slotA])
        assertEquals(blobB, store.entries[slotB])
    }

    @Test
    fun `the old accounts are recoverable once their index can be read again`() {
        storeTwoAccountsThenDamageIndex()
        newRepository(store, cipher, idPrefix = "new")
            .signIn("c@example.test", "key-c", "c@example.test")

        // Simulates the Keystore working again: the original index is readable.
        store.entries[NextDnsAccountRepository.INDEX_KEY] = originalIndex
        val recovered = newRepository(store, cipher, idPrefix = "r").snapshot.value

        assertEquals(setOf("id-1", "id-2"), recovered.accounts.map { it.id }.toSet())
        assertTrue(recovered.accounts.all { it.hasCredential })
    }

    @Test
    fun `a failed write while the index is unreadable changes nothing durable`() {
        storeTwoAccountsThenDamageIndex()
        val damaged = newRepository(store, cipher, idPrefix = "new")
        val before = store.snapshot()
        store.failCommits = true

        val result = damaged.signIn("c@example.test", "key-c", "c@example.test")
        store.restart()

        assertEquals(SignInResult.Failed, result)
        assertEquals(before, store.snapshot())
        assertNull(store.entries[NextDnsAccountRepository.INDEX_BACKUP_KEY])
    }

    @Test
    fun `leftover key blobs are still removed while the index is readable`() {
        val repository = newRepository(store, cipher)
        repository.signIn("a@example.test", "key-a", "a@example.test")
        store.entries["account_key_stray"] = cipher.encrypt("stray-secret", "x")

        repository.signIn("b@example.test", "key-b", "b@example.test")

        assertNull(store.entries["account_key_stray"])
        assertNull(store.entries[NextDnsAccountRepository.INDEX_BACKUP_KEY])
        assertNotNull(store.entries[NextDnsAccountRepository.keyEntry("id-1")])
        assertNotNull(store.entries[NextDnsAccountRepository.keyEntry("id-2")])
    }

    @Test
    fun `leftover key blobs are removed when loading a readable index`() {
        newRepository(store, cipher).signIn("a@example.test", "key-a", "a@example.test")
        store.entries["account_key_stray"] = cipher.encrypt("stray-secret", "x")

        val restarted = newRepository(store, cipher, idPrefix = "r")

        assertNull(store.entries["account_key_stray"])
        assertFalse(restarted.snapshot.value.accounts.isEmpty())
    }
}
