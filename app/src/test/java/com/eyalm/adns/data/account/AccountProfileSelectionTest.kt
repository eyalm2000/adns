package com.eyalm.adns.data.account

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Per-account profile, rename, and removal with a fallback account. */
class AccountProfileSelectionTest {
    private val cipher = FakeSecretCipher()
    private val store = FakeSecureStore()
    private val repository = newRepository(store, cipher)

    private fun signIn(label: String, key: String): String =
        (repository.signIn(label, key, label) as SignInResult.Success).accountId

    private fun NextDnsAccountRepository.account(id: String) =
        snapshot.value.accounts.first { it.id == id }

    // --- selected profile --------------------------------------------------------------------

    @Test
    fun `each account keeps its own profile and it is restored when switching back`() {
        val a = signIn("a@example.test", "key-a")
        repository.setSelectedProfile(a, "profA1")
        val b = signIn("b@example.test", "key-b")
        repository.setSelectedProfile(b, "profB1")
        assertEquals("profB1", repository.snapshot.value.activeProfileId)

        repository.setActive(a)
        assertEquals("profA1", repository.snapshot.value.activeProfileId)
        repository.setActive(b)
        assertEquals("profB1", repository.snapshot.value.activeProfileId)
        repository.setActive(a)
        assertEquals("profA1", repository.snapshot.value.activeProfileId)
    }

    @Test
    fun `an account without a profile has none and does not inherit another account's`() {
        val a = signIn("a@example.test", "key-a")
        repository.setSelectedProfile(a, "profA1")

        signIn("b@example.test", "key-b")

        assertNull(repository.snapshot.value.activeProfileId)
        assertEquals("profA1", repository.account(a).selectedProfileId)
    }

    @Test
    fun `the profile survives a restart and is independent of the device hostname`() {
        val a = signIn("a@example.test", "key-a")
        repository.setSelectedHostname(a, "Phone-device1.dns.nextdns.io")
        repository.setSelectedProfile(a, "profA1")

        val restarted = newRepository(store, cipher, idPrefix = "r").snapshot.value.accounts.single()

        assertEquals("profA1", restarted.selectedProfileId)
        assertEquals("Phone-device1.dns.nextdns.io", restarted.selectedHostname)
    }

    @Test
    fun `clearing and blank values leave no profile`() {
        val a = signIn("a@example.test", "key-a")
        repository.setSelectedProfile(a, "profA1")

        assertEquals(AccountUpdateResult.Applied, repository.setSelectedProfile(a, "  "))
        assertNull(repository.account(a).selectedProfileId)
        repository.setSelectedProfile(a, "profA1")
        assertEquals(AccountUpdateResult.Applied, repository.setSelectedProfile(a, null))
        assertNull(repository.account(a).selectedProfileId)
    }

    @Test
    fun `an unknown account is reported and nothing is stored`() {
        signIn("a@example.test", "key-a")

        assertEquals(AccountUpdateResult.NotFound, repository.setSelectedProfile("missing", "x"))
    }

    @Test
    fun `a profile that cannot be stored is reported and leaves the old one`() {
        val a = signIn("a@example.test", "key-a")
        repository.setSelectedProfile(a, "profA1")
        store.failCommits = true

        val result = repository.setSelectedProfile(a, "profA2")
        store.restart()

        assertEquals(AccountUpdateResult.PersistFailed, result)
        assertEquals("profA1", repository.account(a).selectedProfileId)
        store.failCommits = false
        assertEquals(
            "profA1",
            newRepository(store, cipher, idPrefix = "r").account(a).selectedProfileId,
        )
    }

    @Test
    fun `choosing a profile for one account does not change another account`() {
        val a = signIn("a@example.test", "key-a")
        val b = signIn("b@example.test", "key-b")
        repository.setSelectedProfile(a, "profA1")

        repository.setSelectedProfile(b, "profB1")

        assertEquals("profA1", repository.account(a).selectedProfileId)
        assertEquals("profB1", repository.account(b).selectedProfileId)
    }

    // --- migration and compatibility ---------------------------------------------------------

    @Test
    fun `migration takes the profile from the device hostname the app used until now`() {
        store.putLegacy(cipher, "legacy-key", "user@example.test")

        val repository = newRepository(store, cipher, legacyHostname = "ADNS-abc123.dns.nextdns.io")

        val account = repository.snapshot.value.active!!
        assertEquals("abc123", account.selectedProfileId)
        assertEquals("ADNS-abc123.dns.nextdns.io", account.selectedHostname)
    }

    @Test
    fun `migration does not invent a profile from a hostname that is not a NextDNS one`() {
        store.putLegacy(cipher, "legacy-key", "user@example.test")

        val repository = newRepository(store, cipher, legacyHostname = "dns.example.test")

        assertNull(repository.snapshot.value.active!!.selectedProfileId)
    }

    @Test
    fun `an index written before profiles were stored still loads`() {
        val index = JsonObject().apply {
            addProperty("version", 1)
            addProperty("active", "id-1")
            add(
                "accounts",
                JsonArray().apply {
                    add(
                        JsonObject().apply {
                            addProperty("id", "id-1")
                            addProperty("label", "a@example.test")
                            addProperty("email", "a@example.test")
                            addProperty("hostname", "Phone-abc123.dns.nextdns.io")
                        }
                    )
                }
            )
        }
        store.entries[NextDnsAccountRepository.INDEX_KEY] =
            cipher.encrypt(index.toString(), "adns.accounts.index.v1")
        store.entries[NextDnsAccountRepository.keyEntry("id-1")] =
            cipher.encrypt("key-a", NextDnsAccountRepository.keyAad("id-1"))

        val loaded = newRepository(store, cipher, idPrefix = "r")

        val account = loaded.snapshot.value.active!!
        assertTrue(account.hasCredential)
        assertEquals("key-a", loaded.activeApiKey())
        assertNull(account.selectedProfileId)
        assertEquals("Phone-abc123.dns.nextdns.io", account.selectedHostname)
        // The user can choose a profile and it is stored in the same index.
        assertEquals(AccountUpdateResult.Applied, loaded.setSelectedProfile("id-1", "profA1"))
    }

    @Test
    fun `profile ids are read from NextDNS hostnames only`() {
        assertEquals("abc123", profileIdFromNextDnsHostname("abc123.dns.nextdns.io"))
        assertEquals("abc123", profileIdFromNextDnsHostname("Phone-abc123.dns.nextdns.io"))
        assertEquals("abc123", profileIdFromNextDnsHostname("My--Phone-abc123.DNS.NextDNS.io"))
        assertEquals("abc123", profileIdFromNextDnsHostname("  abc123.dns.nextdns.io "))
        assertNull(profileIdFromNextDnsHostname("dns.google"))
        assertNull(profileIdFromNextDnsHostname(".dns.nextdns.io"))
        assertNull(profileIdFromNextDnsHostname("Phone-.dns.nextdns.io"))
        assertNull(profileIdFromNextDnsHostname(""))
        assertNull(profileIdFromNextDnsHostname(null))
    }

    // --- removal ------------------------------------------------------------------------------

    @Test
    fun `removing an account keeps the other account's key and profile`() {
        val a = signIn("a@example.test", "key-a")
        repository.setSelectedProfile(a, "profA1")
        val refA = repository.currentRef()!!
        val b = signIn("b@example.test", "key-b")
        repository.setSelectedProfile(b, "profB1")

        assertEquals(AccountUpdateResult.Applied, repository.remove(b))

        assertEquals("key-a", repository.apiKeyFor(refA))
        assertEquals("profA1", repository.account(a).selectedProfileId)
        val restarted = newRepository(store, cipher, idPrefix = "r").snapshot.value
        assertEquals(listOf(a), restarted.accounts.map { it.id })
        assertEquals("profA1", restarted.accounts.single().selectedProfileId)
        assertTrue(restarted.accounts.single().hasCredential)
        assertNull(store.entries[NextDnsAccountRepository.keyEntry(b)])
    }

    @Test
    fun `removing the active account can hand over to the next usable account in one write`() {
        val a = signIn("a@example.test", "key-a")
        repository.setSelectedProfile(a, "profA1")
        val refA = repository.currentRef()!!
        val b = signIn("b@example.test", "key-b")
        val c = signIn("c@example.test", "key-c")
        repository.setActive(b)
        // A can no longer be used, so the hand-over must skip it.
        repository.expire(refA)
        val writesBefore = store.commitCount

        val result = repository.remove(b, activateFallback = true)

        assertEquals(AccountUpdateResult.Applied, result)
        assertEquals(c, repository.snapshot.value.activeAccountId)
        assertEquals(1, store.commitCount - writesBefore)
        assertEquals(c, newRepository(store, cipher, idPrefix = "r").snapshot.value.activeAccountId)
        assertFalse(repository.account(a).hasCredential)
    }

    @Test
    fun `removing the active account without a usable fallback leaves nothing active`() {
        val a = signIn("a@example.test", "key-a")
        val refA = repository.currentRef()!!
        val b = signIn("b@example.test", "key-b")
        repository.expire(refA)

        assertEquals(AccountUpdateResult.Applied, repository.remove(b, activateFallback = true))

        assertNull(repository.snapshot.value.activeAccountId)
        assertEquals(listOf(a), repository.snapshot.value.accounts.map { it.id })
    }

    @Test
    fun `removing the last account leaves a clean signed out state`() {
        val a = signIn("a@example.test", "key-a")

        assertEquals(AccountUpdateResult.Applied, repository.remove(a, activateFallback = true))

        assertTrue(repository.snapshot.value.accounts.isEmpty())
        assertNull(repository.snapshot.value.activeAccountId)
        assertNull(repository.currentRef())
        assertTrue(newRepository(store, cipher, idPrefix = "r").snapshot.value.accounts.isEmpty())
    }

    @Test
    fun `removing a non-active account does not change which account is active`() {
        val a = signIn("a@example.test", "key-a")
        val b = signIn("b@example.test", "key-b")

        repository.remove(a, activateFallback = true)

        assertEquals(b, repository.snapshot.value.activeAccountId)
    }

    @Test
    fun `a removal that cannot be stored changes nothing, including the fallback`() {
        val a = signIn("a@example.test", "key-a")
        val b = signIn("b@example.test", "key-b")
        store.failCommits = true

        val result = repository.remove(b, activateFallback = true)

        assertEquals(AccountUpdateResult.PersistFailed, result)
        assertEquals(b, repository.snapshot.value.activeAccountId)
        assertEquals(setOf(a, b), repository.snapshot.value.accounts.map { it.id }.toSet())
    }

    // --- rename -------------------------------------------------------------------------------

    @Test
    fun `renaming changes only the display name and survives a restart`() {
        val a = signIn("a@example.test", "key-a")
        repository.setSelectedProfile(a, "profA1")

        assertEquals(AccountUpdateResult.Applied, repository.rename(a, "  Work  "))

        assertEquals("Work", repository.account(a).label)
        val restarted = newRepository(store, cipher, idPrefix = "r").account(a)
        assertEquals("Work", restarted.label)
        assertEquals("profA1", restarted.selectedProfileId)
        assertTrue(restarted.hasCredential)
    }

    @Test
    fun `a renamed account is still recognised by its login email after its key expired`() {
        val a = signIn("a@example.test", "key-a")
        repository.rename(a, "Work")
        repository.expire(repository.currentRef()!!)

        val again = repository.signIn("a@example.test", "new-key", "a@example.test")

        assertEquals(SignInResult.Success(a, existing = true), again)
        assertEquals(1, repository.snapshot.value.accounts.size)
        assertEquals("Work", repository.account(a).label)
    }

    @Test
    fun `an empty name is rejected and an unknown account is reported`() {
        val a = signIn("a@example.test", "key-a")

        assertEquals(AccountUpdateResult.Invalid, repository.rename(a, "   "))
        assertEquals(AccountUpdateResult.NotFound, repository.rename("missing", "Work"))
        assertEquals("a@example.test", repository.account(a).label)
    }

    @Test
    fun `a rename that cannot be stored changes nothing`() {
        val a = signIn("a@example.test", "key-a")
        store.failCommits = true

        val result = repository.rename(a, "Work")
        store.restart()

        assertEquals(AccountUpdateResult.PersistFailed, result)
        assertEquals("a@example.test", repository.account(a).label)
        store.failCommits = false
        assertEquals(
            "a@example.test",
            newRepository(store, cipher, idPrefix = "r").account(a).label,
        )
    }
}
