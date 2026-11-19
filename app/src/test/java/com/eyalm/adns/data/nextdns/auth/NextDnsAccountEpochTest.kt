package com.eyalm.adns.data.nextdns.auth

import com.eyalm.adns.data.account.AccountEpoch
import com.eyalm.adns.data.account.AccountUpdateResult
import com.eyalm.adns.data.account.FakeSecretCipher
import com.eyalm.adns.data.account.FakeSecureStore
import com.eyalm.adns.data.account.SignInResult
import com.eyalm.adns.data.account.newRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The signal that tells screens the active account changed. */
class NextDnsAccountEpochTest {
    private val cipher = FakeSecretCipher()
    private val store = FakeSecureStore()
    private val repository = newRepository(store, cipher)
    private val session = NextDnsSessionManager(repository)

    private fun signIn(label: String, key: String): String {
        val result = repository.signIn(label, key, label)
        return (result as SignInResult.Success).accountId
    }

    @Test
    fun `without an account the epoch has no account`() {
        assertEquals(AccountEpoch(accountId = null, sequence = 0), session.accountEpoch.value)
    }

    @Test
    fun `an account that is already active when the manager starts is the first epoch`() {
        val a = signIn("a@example.test", "key-a")

        val epoch = NextDnsSessionManager(repository).accountEpoch.value

        assertEquals(AccountEpoch(a, 1), epoch)
    }

    @Test
    fun `every change of the active account starts a new epoch`() {
        val a = signIn("a@example.test", "key-a")
        val afterA = session.accountEpoch.value
        val b = signIn("b@example.test", "key-b")
        val afterB = session.accountEpoch.value
        repository.setActive(a)
        val backToA = session.accountEpoch.value
        repository.remove(a)
        val signedOut = session.accountEpoch.value

        assertEquals(a, afterA.accountId)
        assertEquals(b, afterB.accountId)
        assertEquals(a, backToA.accountId)
        assertNull(signedOut.accountId)
        // Returning to account A is a new epoch, not the old one.
        assertNotEquals(afterA, backToA)
        assertEquals(
            listOf(1L, 2L, 3L, 4L),
            listOf(afterA, afterB, backToA, signedOut).map { it.sequence },
        )
    }

    @Test
    fun `changes that keep the same account do not start a new epoch`() {
        val a = signIn("a@example.test", "key-a")
        val ref = repository.currentRef()!!
        val epoch = session.accountEpoch.value

        repository.setSelectedHostname(a, "a.dns.nextdns.io")
        repository.setActive(a)
        repository.signIn("a@example.test", "key-a", "a@example.test")
        session.unauthorized(ref)
        // Signing in again after the key expired is still the same account.
        repository.signIn("a@example.test", "new-key-a", "a@example.test")

        assertEquals(epoch, session.accountEpoch.value)
    }

    @Test
    fun `signing out through the session manager ends the epoch`() {
        signIn("a@example.test", "key-a")
        val before = session.accountEpoch.value

        session.signedOut()

        assertNull(session.accountEpoch.value.accountId)
        assertEquals(before.sequence + 1, session.accountEpoch.value.sequence)
        assertEquals(NextDnsManagementSession.SignedOut, session.state.value)
    }

    @Test
    fun `the session state is already up to date when the epoch changes`() {
        val a = signIn("a@example.test", "key-a")
        signIn("b@example.test", "key-b")
        session.unauthorized(repository.currentRef()!!)
        assertEquals(NextDnsManagementSession.Expired, session.state.value)

        repository.setActive(a)

        // Observers of the epoch read the session state next, so it must not lag behind.
        assertEquals(a, session.accountEpoch.value.accountId)
        assertEquals(NextDnsManagementSession.Active, session.state.value)
    }

    @Test
    fun `a failed switch does not change the epoch`() {
        val a = signIn("a@example.test", "key-a")
        signIn("b@example.test", "key-b")
        repository.setActive(a)
        val epoch = session.accountEpoch.value
        store.failCommits = true

        val result = repository.setActive("id-2")

        assertEquals(AccountUpdateResult.PersistFailed, result)
        assertEquals(epoch, session.accountEpoch.value)
    }
}
