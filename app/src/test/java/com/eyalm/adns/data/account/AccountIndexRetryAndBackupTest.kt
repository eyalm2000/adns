package com.eyalm.adns.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An account index that cannot be read at start-up: it is read again before anything replaces
 * it, and when it does get replaced the old one is kept in a numbered backup that is never
 * overwritten.
 */
class AccountIndexRetryAndBackupTest {
    private val cipher = FakeSecretCipher()
    private val store = FakeSecureStore()

    private val slotA = NextDnsAccountRepository.keyEntry("id-1")
    private val slotB = NextDnsAccountRepository.keyEntry("id-2")
    private lateinit var originalIndex: String
    private lateinit var blobA: String
    private lateinit var blobB: String

    private fun backups(): Map<String, String> = store.entries
        .filterKeys { it.startsWith(NextDnsAccountRepository.INDEX_BACKUP_KEY) }

    private fun backup(slot: Int): String? =
        store.entries[NextDnsAccountRepository.backupKey(slot)]

    /** Two healthy accounts (id-1, id-2). Their key blobs and index are remembered. */
    private fun storeTwoAccounts() {
        val repository = newRepository(store, cipher)
        repository.signIn("a@example.test", "key-a", "a@example.test")
        repository.signIn("b@example.test", "key-b", "b@example.test")
        originalIndex = store.entries.getValue(NextDnsAccountRepository.INDEX_KEY)
        blobA = store.entries.getValue(slotA)
        blobB = store.entries.getValue(slotB)
    }

    private fun corruptIndex(value: String) {
        store.entries[NextDnsAccountRepository.INDEX_KEY] = value
    }

    private fun NextDnsAccountRepository.signInC() =
        signIn("c@example.test", "key-c", "c@example.test")

    // --- retry before replacing -------------------------------------------------------------

    @Test
    fun `a temporary failure is recovered before sign-in replaces the index`() {
        storeTwoAccounts()
        cipher.failDecrypt = true
        val repository = newRepository(store, cipher, idPrefix = "new")
        assertEquals(AccountMigrationOutcome.IndexUnreadable, repository.migrationOutcome)
        assertTrue(repository.snapshot.value.accounts.isEmpty())
        cipher.failDecrypt = false

        val result = repository.signInC()

        assertTrue(result is SignInResult.Success)
        val snapshot = repository.snapshot.value
        assertEquals(setOf("id-1", "id-2", "new-1"), snapshot.accounts.map { it.id }.toSet())
        assertEquals("new-1", snapshot.activeAccountId)
        assertTrue(snapshot.accounts.all { it.hasCredential })
        // The index was never unreadable as far as storage is concerned, so no backup exists.
        assertTrue(backups().isEmpty())
        // Everything survives a restart.
        val restarted = newRepository(store, cipher, idPrefix = "r").snapshot.value
        assertEquals(3, restarted.accounts.size)
        assertTrue(restarted.accounts.all { it.hasCredential })
    }

    @Test
    fun `other writing operations also retry the unreadable index first`() {
        storeTwoAccounts()
        cipher.failDecrypt = true
        val repository = newRepository(store, cipher, idPrefix = "new")
        cipher.failDecrypt = false

        assertEquals(AccountUpdateResult.Applied, repository.setActive("id-1"))

        assertEquals("id-1", repository.snapshot.value.activeAccountId)
        assertEquals(2, repository.snapshot.value.accounts.size)
        assertEquals("key-a", repository.activeApiKey())
        assertTrue(backups().isEmpty())
    }

    @Test
    fun `the retry reads the index that is stored now, so a newer valid index is not overwritten`() {
        storeTwoAccounts()
        corruptIndex("garbage")
        val repository = newRepository(store, cipher, idPrefix = "new")
        // A valid index appears in storage after the repository started.
        corruptIndex(originalIndex)

        val result = repository.signInC()

        assertTrue(result is SignInResult.Success)
        assertEquals(3, repository.snapshot.value.accounts.size)
        assertTrue(backups().isEmpty())
        assertEquals("key-c", repository.activeApiKey())
    }

    @Test
    fun `an index that stays unreadable is kept in a backup and no key blob is touched`() {
        storeTwoAccounts()
        cipher.failDecrypt = true
        val repository = newRepository(store, cipher, idPrefix = "new")

        // Encrypting still works, decrypting does not: the retry fails, so the old index is
        // preserved before it is replaced.
        val result = repository.signInC()

        assertTrue(result is SignInResult.Success)
        assertEquals(originalIndex, backup(1))
        assertEquals(blobA, store.entries[slotA])
        assertEquals(blobB, store.entries[slotB])
        assertNotNull(store.entries[NextDnsAccountRepository.keyEntry("new-1")])
        assertEquals(1, backups().size)
    }

    @Test
    fun `nothing durable changes when the write fails while the index stays unreadable`() {
        storeTwoAccounts()
        cipher.failDecrypt = true
        val repository = newRepository(store, cipher, idPrefix = "new")
        val before = store.snapshot()
        store.failCommits = true

        val result = repository.signInC()
        store.restart()

        assertEquals(SignInResult.Failed, result)
        assertEquals(before, store.snapshot())
        assertEquals(originalIndex, store.entries[NextDnsAccountRepository.INDEX_KEY])
        assertTrue(backups().isEmpty())
    }

    @Test
    fun `a failed write followed by a retry stores the unreadable index once`() {
        storeTwoAccounts()
        corruptIndex("garbage")
        val repository = newRepository(store, cipher, idPrefix = "new")
        store.failCommits = true
        assertEquals(SignInResult.Failed, repository.signInC())
        store.failCommits = false

        val result = repository.signInC()

        assertTrue(result is SignInResult.Success)
        assertEquals(mapOf(NextDnsAccountRepository.INDEX_BACKUP_KEY to "garbage"), backups())
    }

    // --- numbered backups -------------------------------------------------------------------

    @Test
    fun `an existing backup is never overwritten by a later one`() {
        storeTwoAccounts()
        store.entries[NextDnsAccountRepository.INDEX_BACKUP_KEY] = "older-unreadable-index"
        corruptIndex("garbage")
        val repository = newRepository(store, cipher, idPrefix = "new")

        val result = repository.signInC()

        assertTrue(result is SignInResult.Success)
        assertEquals("older-unreadable-index", backup(1))
        assertEquals("garbage", backup(2))
        assertEquals(2, backups().size)
        assertEquals(blobA, store.entries[slotA])
        assertEquals(blobB, store.entries[slotB])
    }

    @Test
    fun `several failures keep every earlier index and survive restarts`() {
        storeTwoAccounts()
        corruptIndex("bad-1")
        newRepository(store, cipher, idPrefix = "p1").signInC()
        corruptIndex("bad-2")
        newRepository(store, cipher, idPrefix = "p2").signInC()
        corruptIndex("bad-3")
        newRepository(store, cipher, idPrefix = "p3").signInC()

        assertEquals("bad-1", backup(1))
        assertEquals("bad-2", backup(2))
        assertEquals("bad-3", backup(3))

        // A restart and a write do not remove anything. Key blobs of earlier accounts stay even
        // though the current index no longer refers to them.
        store.entries["account_key_stray"] = cipher.encrypt("stray-secret", "x")
        val restarted = newRepository(store, cipher, idPrefix = "p4")
        restarted.setSelectedHostname(
            restarted.snapshot.value.activeAccountId!!,
            "current.dns.nextdns.io",
        )
        newRepository(store, cipher, idPrefix = "p5")

        assertEquals(setOf("bad-1", "bad-2", "bad-3"), backups().values.toSet())
        assertEquals(blobA, store.entries[slotA])
        assertEquals(blobB, store.entries[slotB])
        assertNotNull(store.entries[NextDnsAccountRepository.keyEntry("p1-1")])
        assertNotNull(store.entries[NextDnsAccountRepository.keyEntry("p2-1")])
        // Cleanup stays off while backups exist.
        assertNotNull(store.entries["account_key_stray"])
    }

    @Test
    fun `the same unreadable index is not backed up twice`() {
        storeTwoAccounts()
        store.entries[NextDnsAccountRepository.backupKey(3)] = "garbage"
        corruptIndex("garbage")
        val repository = newRepository(store, cipher, idPrefix = "new")

        val result = repository.signInC()

        assertTrue(result is SignInResult.Success)
        assertEquals(1, backups().size)
        assertEquals("garbage", backup(3))
    }

    @Test
    fun `with every backup slot used a new unreadable index is not replaced`() {
        storeTwoAccounts()
        for (slot in 1..NextDnsAccountRepository.MAX_INDEX_BACKUPS) {
            store.entries[NextDnsAccountRepository.backupKey(slot)] = "old-$slot"
        }
        corruptIndex("garbage")
        val repository = newRepository(store, cipher, idPrefix = "new")
        val before = store.snapshot()

        val result = repository.signInC()

        assertEquals(SignInResult.Failed, result)
        assertEquals(before, store.snapshot())
        assertEquals("garbage", store.entries[NextDnsAccountRepository.INDEX_KEY])
        assertEquals(blobA, store.entries[slotA])
        assertEquals(blobB, store.entries[slotB])
        assertTrue(repository.snapshot.value.accounts.isEmpty())
        for (slot in 1..NextDnsAccountRepository.MAX_INDEX_BACKUPS) {
            assertEquals("old-$slot", backup(slot))
        }
    }

    @Test
    fun `orphan cleanup is off when only a numbered backup exists`() {
        val repository = newRepository(store, cipher)
        repository.signIn("a@example.test", "key-a", "a@example.test")
        store.entries[NextDnsAccountRepository.backupKey(2)] = "some-backup"
        store.entries["account_key_stray"] = cipher.encrypt("stray-secret", "x")

        newRepository(store, cipher, idPrefix = "r")
        repository.signIn("b@example.test", "key-b", "b@example.test")

        assertNotNull(store.entries["account_key_stray"])
        assertEquals("some-backup", backup(2))
    }

    @Test
    fun `orphan cleanup still works when there is no backup`() {
        val repository = newRepository(store, cipher)
        repository.signIn("a@example.test", "key-a", "a@example.test")
        store.entries["account_key_stray"] = cipher.encrypt("stray-secret", "x")

        repository.signIn("b@example.test", "key-b", "b@example.test")

        assertNull(store.entries["account_key_stray"])
        assertTrue(backups().isEmpty())
    }
}
