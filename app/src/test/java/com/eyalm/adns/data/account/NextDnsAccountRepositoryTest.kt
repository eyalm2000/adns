package com.eyalm.adns.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextDnsAccountRepositoryTest {
    private val cipher = FakeSecretCipher()
    private val store = FakeSecureStore()
    private val repository = newRepository(store, cipher)

    private fun NextDnsAccountRepository.signInOk(
        label: String,
        key: String,
        loginEmail: String? = null,
    ): String {
        val result = signIn(label, key, loginEmail)
        assertTrue(result is SignInResult.Success)
        return (result as SignInResult.Success).accountId
    }

    @Test
    fun `accounts are persisted and restored`() {
        val a = repository.signInOk("a@example.test", "key-a")
        val b = repository.signInOk("b@example.test", "key-b")
        repository.setSelectedHostname(a, "dev-aaa111.dns.nextdns.io")
        repository.setActive(a)

        val restored = newRepository(store, cipher, idPrefix = "r").snapshot.value

        assertEquals(listOf(a, b), restored.accounts.map { it.id })
        assertEquals(a, restored.activeAccountId)
        assertEquals(
            listOf("a@example.test", "b@example.test"),
            restored.accounts.map { it.label },
        )
        assertEquals("dev-aaa111.dns.nextdns.io", restored.accounts[0].selectedHostname)
        assertNull(restored.accounts[1].selectedHostname)
        assertTrue(restored.accounts.all { it.hasCredential })
    }

    @Test
    fun `each account keeps its own key and selected hostname`() {
        val a = repository.signInOk("a@example.test", "key-a")
        val b = repository.signInOk("b@example.test", "key-b")
        repository.setSelectedHostname(a, "a-host.dns.nextdns.io")
        repository.setSelectedHostname(b, "b-host.dns.nextdns.io")

        val snapshot = repository.snapshot.value

        assertEquals(b, snapshot.activeAccountId)
        assertEquals("key-b", repository.activeApiKey())
        assertEquals("a-host.dns.nextdns.io", snapshot.accounts.first { it.id == a }.selectedHostname)
        assertEquals("b-host.dns.nextdns.io", snapshot.accounts.first { it.id == b }.selectedHostname)
        assertEquals(AccountUpdateResult.Applied, repository.setActive(a))
        assertEquals("key-a", repository.activeApiKey())
    }

    @Test
    fun `stored values are encrypted and bound to their slot`() {
        repository.signInOk("a@example.test", "key-a-secret")
        repository.signInOk("b@example.test", "key-b-secret")

        store.entries.values.forEach {
            assertFalse(it.contains("key-a-secret"))
            assertFalse(it.contains("key-b-secret"))
            assertFalse(it.contains("a@example.test"))
        }

        // Moving a key blob into another account's slot must not authenticate.
        val slotA = NextDnsAccountRepository.keyEntry("id-1")
        val slotB = NextDnsAccountRepository.keyEntry("id-2")
        val blobA = store.entries.getValue(slotA)
        store.entries[slotA] = store.entries.getValue(slotB)
        store.entries[slotB] = blobA

        val swapped = newRepository(store, cipher, idPrefix = "r")

        assertTrue(swapped.snapshot.value.accounts.none { it.hasCredential })
        assertNull(swapped.activeApiKey())
    }

    @Test
    fun `signing in with a key that is already stored reuses that account`() {
        val first = repository.signInOk("a@example.test", "key-a")
        val ref = repository.currentRef()

        val result = repository.signIn("A@Example.test ", "key-a")

        assertEquals(SignInResult.Success(first, existing = true), result)
        assertEquals(1, repository.snapshot.value.accounts.size)
        // The credential is unchanged, so requests already in flight stay valid.
        assertEquals(ref, repository.currentRef())
        // The existing name is kept; signing in again does not rename the account.
        assertEquals("a@example.test", repository.snapshot.value.active!!.label)
    }

    @Test
    fun `a different key creates a separate account even with the same label`() {
        val first = repository.signInOk("a@example.test", "key-a")

        val second = repository.signInOk("a@example.test", "key-other")

        assertNotEquals(first, second)
        assertEquals(2, repository.snapshot.value.accounts.size)
        assertEquals(second, repository.snapshot.value.activeAccountId)
        assertEquals("key-a", repository.apiKeyFor(AccountCredentialRef(first, 1)))
    }

    @Test
    fun `signing in again after expiry keeps the account and its selected hostname`() {
        val id = repository.signInOk("a@example.test", "old-key", loginEmail = "a@example.test")
        repository.setSelectedHostname(id, "dev-aaa111.dns.nextdns.io")
        repository.expire(repository.currentRef()!!)

        val again = repository.signInOk("a@example.test", "new-key", loginEmail = "a@example.test")

        assertEquals(id, again)
        assertEquals(1, repository.snapshot.value.accounts.size)
        assertEquals("new-key", repository.activeApiKey())
        assertEquals(
            "dev-aaa111.dns.nextdns.io",
            repository.snapshot.value.active!!.selectedHostname,
        )
    }

    @Test
    fun `failed persistence leaves the previous state untouched`() {
        val a = repository.signInOk("a@example.test", "key-a")
        store.failCommits = true

        assertEquals(SignInResult.Failed, repository.signIn("b@example.test", "key-b"))
        assertEquals(
            AccountUpdateResult.PersistFailed,
            repository.setSelectedHostname(a, "x.dns.nextdns.io"),
        )

        assertEquals(1, repository.snapshot.value.accounts.size)
        assertNull(repository.snapshot.value.active!!.selectedHostname)
        assertEquals("key-a", repository.activeApiKey())

        store.failCommits = false
        cipher.failEncrypt = true
        assertEquals(SignInResult.Failed, repository.signIn("b@example.test", "key-b"))
        assertEquals(1, repository.snapshot.value.accounts.size)
    }

    @Test
    fun `blank keys are rejected`() {
        assertEquals(SignInResult.Failed, repository.signIn("a@example.test", "  "))
        assertTrue(repository.snapshot.value.accounts.isEmpty())
    }

    @Test
    fun `with no accounts there is no credential`() {
        assertNull(repository.currentRef())
        assertNull(repository.activeApiKey())
        assertNull(repository.apiKeyFor(null))
        assertNull(repository.snapshot.value.active)
        assertEquals(AccountUpdateResult.NotFound, repository.setActive("missing"))
        assertEquals(
            AccountUpdateResult.NotFound,
            repository.setSelectedHostname("missing", "x"),
        )
        assertEquals(AccountUpdateResult.NotFound, repository.remove("missing"))
    }

    @Test
    fun `a removed account stays removed after restart`() {
        repository.signInOk("a@example.test", "key-a")
        val other = newRepository(store, cipher, idPrefix = "o")
        other.remove("id-1")

        val restored = newRepository(store, cipher, idPrefix = "r").snapshot.value

        assertTrue(restored.accounts.isEmpty())
        assertNull(restored.activeAccountId)
        assertTrue(store.entries.keys.none { it.startsWith("account_key_") })
    }

    @Test
    fun `accounts that share a placeholder label are never merged`() {
        val first = repository.signInOk("API key account", "key-1")
        repository.setSelectedHostname(first, "one.dns.nextdns.io")
        repository.expire(repository.currentRef()!!)

        val second = repository.signInOk("API key account", "key-2")

        assertNotEquals(first, second)
        val accounts = repository.snapshot.value.accounts
        assertEquals(2, accounts.size)
        assertEquals("one.dns.nextdns.io", accounts.first { it.id == first }.selectedHostname)
        assertNull(accounts.first { it.id == second }.selectedHostname)
        assertFalse(accounts.first { it.id == first }.hasCredential)
    }

    @Test
    fun `an ambiguous returning account creates a new one instead of guessing`() {
        val one = repository.signInOk("a@example.test", "key-1", loginEmail = "a@example.test")
        val two = repository.signInOk("a@example.test", "key-2", loginEmail = "a@example.test")
        assertNotEquals(one, two)
        repository.expire(repository.currentRef()!!)
        repository.setActive(one)
        repository.expire(repository.currentRef()!!)

        val three = repository.signInOk("a@example.test", "key-3", loginEmail = "A@Example.test")

        assertTrue(three != one && three != two)
        assertEquals(3, repository.snapshot.value.accounts.size)
    }

    @Test
    fun `login email survives a restart so a returning account is recognised`() {
        val id = repository.signInOk("a@example.test", "key-1", loginEmail = "a@example.test")
        repository.expire(repository.currentRef()!!)

        val restarted = newRepository(store, cipher, idPrefix = "r")
        val again = (restarted.signIn("a@example.test", "key-2", "a@example.test")
            as SignInResult.Success).accountId

        assertEquals(id, again)
        assertEquals(1, restarted.snapshot.value.accounts.size)
    }
    @Test
    fun `account summaries do not print label or hostname`() {
        val id = repository.signInOk("secret@example.test", "key-a")
        repository.setSelectedHostname(id, "private-host.dns.nextdns.io")

        val text = repository.snapshot.value.active.toString()

        assertFalse(text.contains("secret@example.test"))
        assertFalse(text.contains("private-host"))
    }
}
