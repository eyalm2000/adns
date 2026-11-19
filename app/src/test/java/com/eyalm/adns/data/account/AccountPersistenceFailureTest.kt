package com.eyalm.adns.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour when the durable write fails. "Restart" means a new repository on the same store
 * after [FakeSecureStore.restart], which drops everything that was never written to disk.
 */
class AccountPersistenceFailureTest {
    private val cipher = FakeSecretCipher()
    private val store = FakeSecureStore()
    private val repository = newRepository(store, cipher)

    private val a: String
    private val b: String
    private val refA: AccountCredentialRef
    private val refB: AccountCredentialRef

    init {
        a = (repository.signIn("a@example.test", "key-a", "a@example.test")
            as SignInResult.Success).accountId
        refA = repository.currentRef()!!
        b = (repository.signIn("b@example.test", "key-b", "b@example.test")
            as SignInResult.Success).accountId
        refB = repository.currentRef()!!
    }

    private fun restart(): NextDnsAccountRepository {
        store.restart()
        return newRepository(store, cipher, idPrefix = "restart")
    }

    private fun NextDnsAccountRepository.account(id: String) =
        snapshot.value.accounts.first { it.id == id }

    // --- expiration -------------------------------------------------------------------------

    @Test
    fun `expiration revokes the key in memory even when it cannot be stored`() {
        store.failCommits = true

        val result = repository.expire(refA)

        assertEquals(ExpireResult.Applied(a, wasActive = false, persisted = false), result)
        assertNull(repository.apiKeyFor(refA))
        assertFalse(repository.account(a).hasCredential)
        assertTrue(repository.account(a).sessionExpired)
        assertTrue(repository.hasPendingWrite)
        // The other account is not affected.
        assertEquals("key-b", repository.apiKeyFor(refB))
        assertTrue(repository.account(b).hasCredential)
    }

    @Test
    fun `an expiration that was not stored is reported and the old key returns only after a restart`() {
        store.failCommits = true
        val result = repository.expire(refA) as ExpireResult.Applied
        assertFalse(result.persisted)

        // Documented limitation: if the process dies before the write succeeds, the durable
        // copy still holds the key the server already rejected. It is rejected again.
        val restarted = restart()

        assertTrue(restarted.account(a).hasCredential)
        assertTrue(restarted.account(b).hasCredential)
    }

    @Test
    fun `a pending expiration is written by flushPending after the storage recovers`() {
        store.failCommits = true
        repository.expire(refA)
        assertFalse(repository.flushPending())
        assertTrue(repository.hasPendingWrite)

        store.failCommits = false
        assertTrue(repository.flushPending())

        assertFalse(repository.hasPendingWrite)
        val restarted = restart()
        assertFalse(restarted.account(a).hasCredential)
        assertEquals("key-b", restarted.apiKeyFor(restarted.currentRef()))
        assertNull(store.entries[NextDnsAccountRepository.keyEntry(a)])
    }

    @Test
    fun `the next successful write also stores a pending expiration`() {
        store.failCommits = true
        repository.expire(refA)
        store.failCommits = false

        assertEquals(
            AccountUpdateResult.Applied,
            repository.setSelectedHostname(b, "b-host.dns.nextdns.io"),
        )

        assertFalse(repository.hasPendingWrite)
        val restarted = restart()
        assertFalse(restarted.account(a).hasCredential)
        assertEquals("b-host.dns.nextdns.io", restarted.account(b).selectedHostname)
    }

    @Test
    fun `a repeated rejection after a failed write does not expire anything twice`() {
        store.failCommits = true
        repository.expire(refA)

        assertEquals(ExpireResult.Stale, repository.expire(refA))
    }

    // --- removal ----------------------------------------------------------------------------

    @Test
    fun `a removal that cannot be stored changes nothing and says so`() {
        store.failCommits = true

        val result = repository.remove(b)

        assertEquals(AccountUpdateResult.PersistFailed, result)
        assertEquals(b, repository.snapshot.value.activeAccountId)
        assertEquals("key-b", repository.apiKeyFor(refB))
        assertEquals(setOf(a, b), repository.snapshot.value.accounts.map { it.id }.toSet())
        // Memory and disk still agree: after a restart the account is still there.
        val restarted = restart()
        assertEquals(setOf(a, b), restarted.snapshot.value.accounts.map { it.id }.toSet())
        assertEquals("key-b", restarted.activeApiKey())
    }

    @Test
    fun `a removal can be retried after a transient failure`() {
        store.failCommits = true
        assertEquals(AccountUpdateResult.PersistFailed, repository.remove(b))
        store.failCommits = false

        assertEquals(AccountUpdateResult.Applied, repository.remove(b))

        assertNull(repository.activeApiKey())
        val restarted = restart()
        assertEquals(listOf(a), restarted.snapshot.value.accounts.map { it.id })
        assertNull(store.entries[NextDnsAccountRepository.keyEntry(b)])
        // Account A kept its key through the failed and the successful attempt.
        assertTrue(restarted.account(a).hasCredential)
        assertEquals("key-a", restarted.apiKeyFor(AccountCredentialRef(a, 1)))
    }

    @Test
    fun `a failed removal never costs another account its key`() {
        // A failed commit leaves its edits (here: the deleted key of B) in the store's
        // in-process view. A later, unrelated successful write must not persist that damage.
        store.failCommits = true
        assertEquals(AccountUpdateResult.PersistFailed, repository.remove(b))
        store.failCommits = false

        assertEquals(
            AccountUpdateResult.Applied,
            repository.setSelectedHostname(a, "a-host.dns.nextdns.io"),
        )

        val restarted = restart()
        assertTrue(restarted.account(a).hasCredential)
        // B was not removed, so it must still have its key.
        assertTrue(restarted.account(b).hasCredential)
        assertNotNull(store.entries[NextDnsAccountRepository.keyEntry(b)])
        assertEquals("key-b", restarted.activeApiKey())
    }

    // --- credential replacement and sign-in -------------------------------------------------

    @Test
    fun `a failed re-key leaves the account expired and reports failure`() {
        repository.setActive(a)
        repository.expire(repository.currentRef()!!)
        store.failCommits = true

        val result = repository.signIn("a@example.test", "new-key-a", "a@example.test")

        assertEquals(SignInResult.Failed, result)
        assertNull(repository.activeApiKey())
        assertFalse(repository.account(a).hasCredential)
        val restarted = restart()
        assertFalse(restarted.account(a).hasCredential)
        assertTrue(restarted.account(b).hasCredential)
        // Storage works again after the restart, so account B can be activated.
        store.failCommits = false
        assertEquals(AccountUpdateResult.Applied, restarted.setActive(b))
        assertEquals("key-b", restarted.activeApiKey())
    }

    @Test
    fun `a re-key can be retried and then survives a restart`() {
        repository.setActive(a)
        repository.expire(repository.currentRef()!!)
        store.failCommits = true
        assertEquals(SignInResult.Failed, repository.signIn("a@example.test", "new-key-a", "a@example.test"))
        store.failCommits = false

        val result = repository.signIn("a@example.test", "new-key-a", "a@example.test")

        assertEquals(SignInResult.Success(a, existing = true), result)
        assertEquals("new-key-a", repository.activeApiKey())
        val restarted = restart()
        assertEquals("new-key-a", restarted.activeApiKey())
        assertEquals(a, restarted.snapshot.value.activeAccountId)
    }

    @Test
    fun `a failed sign-in of a new account leaves no trace after the next successful write`() {
        store.failCommits = true
        assertEquals(
            SignInResult.Failed,
            repository.signIn("c@example.test", "key-c", "c@example.test"),
        )
        store.failCommits = false

        repository.setSelectedHostname(a, "a-host.dns.nextdns.io")

        val restarted = restart()
        assertEquals(setOf(a, b), restarted.snapshot.value.accounts.map { it.id }.toSet())
        val expectedBlobs = setOf(
            NextDnsAccountRepository.keyEntry(a),
            NextDnsAccountRepository.keyEntry(b),
        )
        assertEquals(
            expectedBlobs,
            store.entries.keys.filter { it.startsWith(NextDnsAccountRepository.KEY_PREFIX) }.toSet(),
        )
        assertEquals("key-b", restarted.activeApiKey())
    }

    @Test
    fun `an encryption failure is reported like a storage failure and changes nothing`() {
        cipher.failEncrypt = true

        assertEquals(AccountUpdateResult.PersistFailed, repository.setActive(a))
        assertEquals(AccountUpdateResult.PersistFailed, repository.remove(a))

        assertEquals(b, repository.snapshot.value.activeAccountId)
        assertTrue(repository.account(a).hasCredential)
    }

    // --- isolation and recovery -------------------------------------------------------------

    @Test
    fun `failures that affect account A never modify account B`() {
        val keyBefore = store.entries.getValue(NextDnsAccountRepository.keyEntry(b))
        store.failCommits = true
        repository.expire(refA)
        repository.remove(a)
        repository.signIn("a@example.test", "other", "a@example.test")
        store.failCommits = false

        assertEquals("key-b", repository.apiKeyFor(refB))
        assertEquals(b, repository.snapshot.value.activeAccountId)
        assertTrue(repository.account(b).hasCredential)
        assertEquals(keyBefore, store.entries.getValue(NextDnsAccountRepository.keyEntry(b)))
    }

    @Test
    fun `an undecryptable key is not destroyed by an unrelated write`() {
        store.entries[NextDnsAccountRepository.keyEntry(a)] = "garbled-by-keystore-glitch"
        val glitched = newRepository(store, cipher, idPrefix = "g")
        assertFalse(glitched.account(a).hasCredential)

        assertEquals(
            AccountUpdateResult.Applied,
            glitched.setSelectedHostname(b, "b-host.dns.nextdns.io"),
        )

        assertEquals(
            "garbled-by-keystore-glitch",
            store.entries[NextDnsAccountRepository.keyEntry(a)],
        )
    }

    @Test
    fun `removing an account deletes even an undecryptable key blob`() {
        store.entries[NextDnsAccountRepository.keyEntry(a)] = "garbled-by-keystore-glitch"
        val glitched = newRepository(store, cipher, idPrefix = "g")

        assertEquals(AccountUpdateResult.Applied, glitched.remove(a))

        assertNull(store.entries[NextDnsAccountRepository.keyEntry(a)])
    }
}
