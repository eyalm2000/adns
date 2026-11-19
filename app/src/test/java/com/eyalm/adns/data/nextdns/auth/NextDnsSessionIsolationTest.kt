package com.eyalm.adns.data.nextdns.auth

import com.eyalm.adns.data.account.AccountCredentialRef
import com.eyalm.adns.data.account.AccountUpdateResult
import com.eyalm.adns.data.account.ActiveSessionStatus
import com.eyalm.adns.data.account.ExpireResult
import com.eyalm.adns.data.account.FakeSecretCipher
import com.eyalm.adns.data.account.FakeSecureStore
import com.eyalm.adns.data.account.NextDnsAccountRepository
import com.eyalm.adns.data.account.SignInResult
import com.eyalm.adns.data.account.newRepository
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextDnsSessionIsolationTest {
    private val cipher = FakeSecretCipher()
    private val store = FakeSecureStore()
    private val repository = newRepository(store, cipher)

    /** Signs in and returns the id and the credential reference issued for the account. */
    private fun signIn(
        label: String,
        key: String,
        loginEmail: String? = null,
    ): Pair<String, AccountCredentialRef> {
        val result = repository.signIn(label, key, loginEmail)
        assertTrue(result is SignInResult.Success)
        return (result as SignInResult.Success).accountId to repository.currentRef()!!
    }

    private fun hasCredential(accountId: String): Boolean =
        repository.snapshot.value.accounts.first { it.id == accountId }.hasCredential

    // --- the session follows the repository ----------------------------------------------

    @Test
    fun `activating an account updates the observed session state`() {
        val (a, _) = signIn("a@example.test", "key-a")
        val (b, refB) = signIn("b@example.test", "key-b")
        val session = NextDnsSessionManager(repository)
        repository.expire(refB)
        assertEquals(NextDnsManagementSession.Expired, session.state.value)
        assertTrue(session.reauthenticationRequested.value)

        // Called on the repository directly, not through the session manager.
        assertEquals(AccountUpdateResult.Applied, repository.setActive(a))

        assertEquals(NextDnsManagementSession.Active, session.state.value)
        assertFalse(session.reauthenticationRequested.value)
        assertEquals(AccountUpdateResult.Applied, repository.setActive(b))
        assertEquals(NextDnsManagementSession.Expired, session.state.value)
        assertTrue(session.reauthenticationRequested.value)
    }

    @Test
    fun `removing the active account directly leaves no stale active session`() {
        val (a, refA) = signIn("a@example.test", "key-a")
        val (b, _) = signIn("b@example.test", "key-b")
        val session = NextDnsSessionManager(repository)
        assertEquals(NextDnsManagementSession.Active, session.state.value)

        assertEquals(AccountUpdateResult.Applied, repository.remove(b))

        assertEquals(NextDnsManagementSession.SignedOut, session.state.value)
        assertNull(repository.currentRef())
        assertNull(repository.activeApiKey())
        assertTrue(hasCredential(a))
        assertEquals("key-a", repository.apiKeyFor(refA))
    }

    @Test
    fun `expiring the active account directly leaves no valid looking session`() {
        val (_, refA) = signIn("a@example.test", "key-a")
        val session = NextDnsSessionManager(repository)

        val result = repository.expire(refA)

        assertTrue(result is ExpireResult.Applied)
        assertEquals(NextDnsManagementSession.Expired, session.state.value)
        assertTrue(session.reauthenticationRequested.value)
        assertNull(repository.apiKeyFor(refA))
        assertFalse(session.requestFeatureAccess())
    }

    @Test
    fun `an expired session after a restart is signed out like before accounts existed`() {
        val (_, ref) = signIn("a@example.test", "key-a")
        repository.expire(ref)

        val restarted = NextDnsSessionManager(newRepository(store, cipher, idPrefix = "r"))

        assertEquals(NextDnsManagementSession.SignedOut, restarted.state.value)
        assertFalse(restarted.reauthenticationRequested.value)
    }

    @Test
    fun `session state always agrees with the repository`() {
        val session = NextDnsSessionManager(repository)
        fun check() {
            val expected = when (repository.snapshot.value.sessionStatus) {
                ActiveSessionStatus.SignedOut -> NextDnsManagementSession.SignedOut
                ActiveSessionStatus.Active -> NextDnsManagementSession.Active
                ActiveSessionStatus.Expired -> NextDnsManagementSession.Expired
            }
            assertEquals(expected, session.state.value)
        }
        check()
        val (a, refA) = signIn("a@example.test", "key-a")
        check()
        val (b, refB) = signIn("b@example.test", "key-b")
        check()
        repository.setActive(a); check()
        repository.expire(refB); check()
        repository.setActive(b); check()
        repository.setSelectedHostname(b, "b.dns.nextdns.io"); check()
        repository.remove(b); check()
        repository.setActive(a); check()
        repository.expire(refA); check()
        session.signedOut(); check()
        assertEquals(NextDnsManagementSession.SignedOut, session.state.value)
    }

    @Test
    fun `reauthentication prompt is cleared when the session leaves the expired state`() {
        val session = NextDnsSessionManager(repository)
        assertFalse(session.requestFeatureAccess())
        assertTrue(session.reauthenticationRequested.value)

        signIn("a@example.test", "key-a")

        assertEquals(NextDnsManagementSession.Active, session.state.value)
        assertFalse(session.reauthenticationRequested.value)
        assertTrue(session.requestFeatureAccess())
    }

    // --- one account does not affect another ---------------------------------------------

    @Test
    fun `authentication failure of account A does not expire account B`() {
        val (a, refA) = signIn("a@example.test", "key-a")
        val (b, refB) = signIn("b@example.test", "key-b")
        val session = NextDnsSessionManager(repository)

        session.unauthorized(refA)

        assertFalse(hasCredential(a))
        assertTrue(hasCredential(b))
        assertEquals("key-b", repository.apiKeyFor(refB))
        assertEquals(b, repository.snapshot.value.activeAccountId)
        // B is the account in use, so its session is untouched and no prompt is raised.
        assertEquals(NextDnsManagementSession.Active, session.state.value)
        assertFalse(session.reauthenticationRequested.value)
    }

    @Test
    fun `authentication failure of the active account expires only that account`() {
        val (a, refA) = signIn("a@example.test", "key-a")
        val (b, refB) = signIn("b@example.test", "key-b")
        val session = NextDnsSessionManager(repository)

        session.unauthorized(refB)

        assertEquals(NextDnsManagementSession.Expired, session.state.value)
        assertTrue(session.reauthenticationRequested.value)
        assertFalse(hasCredential(b))
        assertNull(repository.apiKeyFor(refB))
        assertTrue(hasCredential(a))
        assertEquals("key-a", repository.apiKeyFor(refA))
    }

    @Test
    fun `signing out removes only the active account`() {
        val (a, refA) = signIn("a@example.test", "key-a")
        val (b, _) = signIn("b@example.test", "key-b")
        val session = NextDnsSessionManager(repository)

        val result = session.signedOut()

        assertEquals(AccountUpdateResult.Applied, result)
        assertEquals(NextDnsManagementSession.SignedOut, session.state.value)
        assertEquals(listOf(a), repository.snapshot.value.accounts.map { it.id })
        assertNull(repository.snapshot.value.activeAccountId)
        assertNull(repository.activeApiKey())
        assertNull(repository.currentRef())
        // The other account is untouched and survives a restart.
        assertEquals("key-a", repository.apiKeyFor(refA))
        val restarted = newRepository(store, cipher, idPrefix = "r")
        restarted.setActive(a)
        assertEquals("key-a", restarted.activeApiKey())
        assertTrue(store.entries.containsKey(NextDnsAccountRepository.keyEntry(a)))
        assertFalse(store.entries.containsKey(NextDnsAccountRepository.keyEntry(b)))
    }

    @Test
    fun `a sign-out that cannot be stored changes nothing and says so`() {
        signIn("a@example.test", "key-a")
        val (b, refB) = signIn("b@example.test", "key-b")
        val session = NextDnsSessionManager(repository)
        store.failCommits = true

        val result = session.signedOut()

        assertEquals(AccountUpdateResult.PersistFailed, result)
        assertEquals(NextDnsManagementSession.Active, session.state.value)
        assertEquals(b, repository.snapshot.value.activeAccountId)
        assertEquals("key-b", repository.apiKeyFor(refB))
        store.failCommits = false
        assertEquals(AccountUpdateResult.Applied, session.signedOut())
        assertEquals(NextDnsManagementSession.SignedOut, session.state.value)
    }

    // --- stale and repeated failures -------------------------------------------------------

    @Test
    fun `delayed failure from a replaced credential cannot expire the new one`() {
        val (a, oldRef) = signIn("a@example.test", "old-key", "a@example.test")
        val session = NextDnsSessionManager(repository)
        session.unauthorized(oldRef)
        assertEquals(NextDnsManagementSession.Expired, session.state.value)
        val (sameAccount, newRef) = signIn("a@example.test", "new-key", "a@example.test")
        assertEquals(a, sameAccount)
        assertEquals(NextDnsManagementSession.Active, session.state.value)

        // A second response, issued with the old key, arrives late.
        session.unauthorized(oldRef)

        assertEquals(NextDnsManagementSession.Active, session.state.value)
        assertFalse(session.reauthenticationRequested.value)
        assertTrue(hasCredential(a))
        assertEquals("new-key", repository.apiKeyFor(newRef))
        assertNull(repository.apiKeyFor(oldRef))
    }

    @Test
    fun `delayed failure from a removed account does not touch the active one`() {
        val (a, refA) = signIn("a@example.test", "key-a")
        val (b, refB) = signIn("b@example.test", "key-b")
        val session = NextDnsSessionManager(repository)
        repository.remove(a)

        session.unauthorized(refA)

        assertEquals(b, repository.snapshot.value.activeAccountId)
        assertEquals("key-b", repository.apiKeyFor(refB))
        assertEquals(NextDnsManagementSession.Active, session.state.value)
    }

    @Test
    fun `repeated failures from one credential invalidate it once`() {
        val (_, ref) = signIn("a@example.test", "key-a")

        val first = repository.expire(ref)
        val second = repository.expire(ref)

        assertTrue(first is ExpireResult.Applied)
        assertEquals(ExpireResult.Stale, second)
    }

    @Test
    fun `a repeated failure does not raise the reauthentication request again`() {
        val (_, ref) = signIn("a@example.test", "key-a")
        val session = NextDnsSessionManager(repository)
        session.unauthorized(ref)
        session.dismissReauthenticationRequest()

        session.unauthorized(ref)

        assertFalse(session.reauthenticationRequested.value)
        assertEquals(NextDnsManagementSession.Expired, session.state.value)
    }

    @Test
    fun `concurrent failures from one credential invalidate it exactly once`() {
        val (_, ref) = signIn("a@example.test", "key-a")
        val (b, refB) = signIn("b@example.test", "key-b")
        repository.setActive(repository.snapshot.value.accounts.first().id)
        val session = NextDnsSessionManager(repository)
        val threads = 16
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val applied = AtomicInteger()
        repeat(threads) {
            pool.execute {
                start.await()
                if (repository.expire(ref) is ExpireResult.Applied) applied.incrementAndGet()
                session.unauthorized(ref)
                done.countDown()
            }
        }

        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        pool.shutdown()

        assertEquals(1, applied.get())
        assertEquals(NextDnsManagementSession.Expired, session.state.value)
        assertTrue(session.reauthenticationRequested.value)
        assertTrue(hasCredential(b))
        assertEquals("key-b", repository.apiKeyFor(refB))
    }

    @Test
    fun `failures without a usable credential reference are ignored`() {
        val (_, ref) = signIn("a@example.test", "key-a")
        val session = NextDnsSessionManager(repository)

        session.unauthorized(null)
        session.unauthorized(AccountCredentialRef("unknown-account", 1))
        session.unauthorized(AccountCredentialRef(ref.accountId, ref.version + 1))

        assertEquals(NextDnsManagementSession.Active, session.state.value)
        assertEquals("key-a", repository.activeApiKey())
        assertFalse(session.reauthenticationRequested.value)
    }

    @Test
    fun `without an active account the session is signed out and operations are safe`() {
        val session = NextDnsSessionManager(repository)

        session.unauthorized(null)
        val result = session.signedOut()

        assertEquals(AccountUpdateResult.NotFound, result)
        assertEquals(NextDnsManagementSession.SignedOut, session.state.value)
        assertNull(repository.currentRef())
        assertFalse(session.requestFeatureAccess())
        assertTrue(session.reauthenticationRequested.value)
        session.dismissReauthenticationRequest()
        assertFalse(session.reauthenticationRequested.value)
    }

    @Test
    fun `initial state follows the stored credential`() {
        assertEquals(
            NextDnsManagementSession.SignedOut,
            NextDnsSessionManager(repository).state.value,
        )

        signIn("a@example.test", "key-a")

        assertEquals(
            NextDnsManagementSession.Active,
            NextDnsSessionManager(repository).state.value,
        )
        assertTrue(NextDnsSessionManager(repository).requestFeatureAccess())
    }
}
