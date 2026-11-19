package com.eyalm.adns.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountMigrationTest {
    private val cipher = FakeSecretCipher()
    private val store = FakeSecureStore()

    private fun legacyStore(
        key: String? = LEGACY_KEY,
        email: String? = "user@example.test",
    ) = store.apply { putLegacy(cipher, key, email) }

    @Test
    fun `single account credentials migrate with label and selected profile`() {
        legacyStore()

        val repository = newRepository(
            store = store,
            cipher = cipher,
            legacyHostname = "ADNS-abc123.dns.nextdns.io",
        )

        assertEquals(AccountMigrationOutcome.Migrated, repository.migrationOutcome)
        val snapshot = repository.snapshot.value
        val account = snapshot.active
        assertNotNull(account)
        assertEquals(1, snapshot.accounts.size)
        assertEquals("user@example.test", account!!.label)
        assertEquals("ADNS-abc123.dns.nextdns.io", account.selectedHostname)
        assertTrue(account.hasCredential)
        assertEquals(LEGACY_KEY, repository.activeApiKey())
        assertNotNull(repository.currentRef())
    }

    @Test
    fun `legacy entries are removed only after the new copy is stored`() {
        legacyStore()

        newRepository(store, cipher)

        assertNull(store.get(NextDnsAccountRepository.LEGACY_API_KEY))
        assertNull(store.get(NextDnsAccountRepository.LEGACY_EMAIL))
        assertNotNull(store.get(NextDnsAccountRepository.INDEX_KEY))
        assertEquals(1, store.entries.keys.count { it.startsWith("account_key_") })
    }

    @Test
    fun `migrated storage never contains the plaintext key or email`() {
        legacyStore()

        newRepository(store, cipher, legacyHostname = "host.dns.nextdns.io")

        store.entries.values.forEach { value ->
            assertFalse(value.contains(LEGACY_KEY))
            assertFalse(value.contains("user@example.test"))
        }
    }

    @Test
    fun `running migration repeatedly does not create duplicate accounts`() {
        legacyStore()
        val first = newRepository(store, cipher)
        val accountId = first.snapshot.value.activeAccountId

        val second = newRepository(store, cipher, idPrefix = "later")
        val third = newRepository(store, cipher, idPrefix = "even-later")

        assertEquals(AccountMigrationOutcome.AlreadyMigrated, second.migrationOutcome)
        assertEquals(1, third.snapshot.value.accounts.size)
        assertEquals(accountId, third.snapshot.value.activeAccountId)
        assertEquals(LEGACY_KEY, third.activeApiKey())
    }

    @Test
    fun `leftover legacy copy of an already stored key is cleaned up without a new account`() {
        legacyStore()
        newRepository(store, cipher)
        // A crash after writing the account but before removing the legacy entries.
        store.putLegacy(cipher, LEGACY_KEY, "user@example.test")

        val repository = newRepository(store, cipher, idPrefix = "retry")

        assertEquals(1, repository.snapshot.value.accounts.size)
        assertEquals(AccountMigrationOutcome.AlreadyMigrated, repository.migrationOutcome)
        assertNull(store.get(NextDnsAccountRepository.LEGACY_API_KEY))
        assertEquals(LEGACY_KEY, repository.activeApiKey())
    }

    @Test
    fun `existing account list is not overwritten when another legacy key appears`() {
        val first = newRepository(store, cipher)
        first.signIn("current@example.test", "current-key")
        first.setSelectedHostname(first.snapshot.value.activeAccountId!!, "keep.dns.nextdns.io")
        val originalId = first.snapshot.value.activeAccountId
        // For example a downgrade followed by a login with the old build.
        store.putLegacy(cipher, "other-legacy-key", "other@example.test")

        val repository = newRepository(store, cipher, legacyHostname = "x", idPrefix = "imp")

        val snapshot = repository.snapshot.value
        assertEquals(2, snapshot.accounts.size)
        assertEquals(originalId, snapshot.activeAccountId)
        assertEquals("current-key", repository.activeApiKey())
        val original = snapshot.accounts.first { it.id == originalId }
        assertEquals("keep.dns.nextdns.io", original.selectedHostname)
        // The imported account does not take over the device-wide selected profile.
        assertNull(snapshot.accounts.first { it.id != originalId }.selectedHostname)
        assertNull(store.get(NextDnsAccountRepository.LEGACY_API_KEY))
    }

    @Test
    fun `corrupted legacy key is kept and reported instead of silently dropped`() {
        store.entries[NextDnsAccountRepository.LEGACY_API_KEY] = "garbage"
        store.putLegacy(cipher, null, "user@example.test")

        val repository = newRepository(store, cipher)

        assertEquals(AccountMigrationOutcome.LegacyUnreadable, repository.migrationOutcome)
        assertTrue(repository.snapshot.value.accounts.isEmpty())
        assertEquals("garbage", store.get(NextDnsAccountRepository.LEGACY_API_KEY))
        assertNotNull(store.get(NextDnsAccountRepository.LEGACY_EMAIL))
        assertNull(store.get(NextDnsAccountRepository.INDEX_KEY))
    }

    @Test
    fun `blank legacy key is treated as unreadable and kept`() {
        store.entries[NextDnsAccountRepository.LEGACY_API_KEY] = cipher.legacy("   ")

        val repository = newRepository(store, cipher)

        assertEquals(AccountMigrationOutcome.LegacyUnreadable, repository.migrationOutcome)
        assertTrue(repository.snapshot.value.accounts.isEmpty())
        assertNotNull(store.get(NextDnsAccountRepository.LEGACY_API_KEY))
    }

    @Test
    fun `corrupted legacy email does not block migrating the key`() {
        store.putLegacy(cipher, LEGACY_KEY, null)
        store.entries[NextDnsAccountRepository.LEGACY_EMAIL] = "garbage"

        val repository = newRepository(store, cipher)

        assertEquals(AccountMigrationOutcome.Migrated, repository.migrationOutcome)
        assertEquals(LEGACY_KEY, repository.activeApiKey())
        assertEquals("", repository.snapshot.value.active!!.label)
    }

    @Test
    fun `missing selected profile migrates with no hostname`() {
        legacyStore()

        val repository = newRepository(store, cipher, legacyHostname = null)

        assertEquals(AccountMigrationOutcome.Migrated, repository.migrationOutcome)
        assertNull(repository.snapshot.value.active!!.selectedHostname)
        assertEquals(LEGACY_KEY, repository.activeApiKey())
    }

    @Test
    fun `blank selected profile is not stored as a hostname`() {
        legacyStore()

        val repository = newRepository(store, cipher, legacyHostname = "   ")

        assertNull(repository.snapshot.value.active!!.selectedHostname)
    }

    @Test
    fun `no legacy data means nothing is written`() {
        val repository = newRepository(store, cipher)

        assertEquals(AccountMigrationOutcome.NotNeeded, repository.migrationOutcome)
        assertTrue(repository.snapshot.value.accounts.isEmpty())
        assertNull(repository.currentRef())
        assertEquals(0, store.commitCount)
        assertTrue(store.entries.isEmpty())
    }

    @Test
    fun `failed write leaves legacy credentials untouched and the next start retries`() {
        legacyStore()
        store.failCommits = true

        val failed = newRepository(store, cipher)

        assertEquals(AccountMigrationOutcome.WriteFailed, failed.migrationOutcome)
        assertTrue(failed.snapshot.value.accounts.isEmpty())
        // After a process restart only the durable content is left.
        store.restart()
        assertNotNull(store.get(NextDnsAccountRepository.LEGACY_API_KEY))
        assertNull(store.get(NextDnsAccountRepository.INDEX_KEY))

        store.failCommits = false
        val retried = newRepository(store, cipher)

        assertEquals(AccountMigrationOutcome.Migrated, retried.migrationOutcome)
        assertEquals(LEGACY_KEY, retried.activeApiKey())
    }

    @Test
    fun `encryption failure is reported as a failed write and keeps legacy data`() {
        legacyStore()
        val failingCipher = FakeSecretCipher().apply { failEncrypt = true }

        val repository = newRepository(store, failingCipher)

        assertEquals(AccountMigrationOutcome.WriteFailed, repository.migrationOutcome)
        assertNotNull(store.get(NextDnsAccountRepository.LEGACY_API_KEY))
        assertNull(store.get(NextDnsAccountRepository.INDEX_KEY))
    }

    @Test
    fun `read-back mismatch is not reported as success and rolls back`() {
        legacyStore()
        store.corruptOnWrite += NextDnsAccountRepository.INDEX_KEY

        val repository = newRepository(store, cipher)

        assertEquals(AccountMigrationOutcome.VerificationFailed, repository.migrationOutcome)
        assertTrue(repository.snapshot.value.accounts.isEmpty())
        assertNull(repository.activeApiKey())
        assertNotNull(store.get(NextDnsAccountRepository.LEGACY_API_KEY))
        assertNull(store.get(NextDnsAccountRepository.INDEX_KEY))
        assertEquals(0, store.entries.keys.count { it.startsWith("account_key_") })

        store.corruptOnWrite.clear()
        assertEquals(
            AccountMigrationOutcome.Migrated,
            newRepository(store, cipher).migrationOutcome,
        )
    }

    @Test
    fun `unreadable account index is never modified and legacy data is kept`() {
        legacyStore()
        store.entries[NextDnsAccountRepository.INDEX_KEY] = "garbage"
        val before = store.snapshot()

        val repository = newRepository(store, cipher)

        assertEquals(AccountMigrationOutcome.IndexUnreadable, repository.migrationOutcome)
        assertTrue(repository.snapshot.value.accounts.isEmpty())
        assertEquals(before, store.snapshot())
        assertEquals(0, store.commitCount)
    }

    @Test
    fun `replacing an unreadable index keeps a backup of it`() {
        store.entries[NextDnsAccountRepository.INDEX_KEY] = "garbage"
        val repository = newRepository(store, cipher)

        val result = repository.signIn("user@example.test", "fresh-key")

        assertTrue(result is SignInResult.Success)
        assertEquals("garbage", store.get(NextDnsAccountRepository.INDEX_BACKUP_KEY))
        assertEquals("fresh-key", newRepository(store, cipher, idPrefix = "r").activeApiKey())
    }

    @Test
    fun `legacy label that looks like an email becomes the login identity`() {
        legacyStore(email = "user@example.test")
        val repository = newRepository(store, cipher)
        repository.expire(repository.currentRef()!!)

        // The returning account is recognised by its email, not by a display label.
        val result = repository.signIn("user@example.test", "new-key", "user@example.test")

        assertEquals(1, repository.snapshot.value.accounts.size)
        assertEquals(
            repository.snapshot.value.activeAccountId,
            (result as SignInResult.Success).accountId,
        )
    }

    @Test
    fun `legacy placeholder label is not treated as an identity`() {
        legacyStore(email = "API key account")
        val repository = newRepository(store, cipher, legacyHostname = "old.dns.nextdns.io")
        val migratedId = repository.snapshot.value.activeAccountId
        repository.expire(repository.currentRef()!!)

        val result = repository.signIn("API key account", "other-account-key")

        // A different API key must not inherit the old account's selected profile.
        val created = (result as SignInResult.Success).accountId
        assertTrue(created != migratedId)
        assertNull(repository.snapshot.value.accounts.first { it.id == created }.selectedHostname)
        assertEquals(2, repository.snapshot.value.accounts.size)
    }

    @Test
    fun `leftover key blobs of accounts that are not in the index are removed on load`() {
        legacyStore()
        newRepository(store, cipher)
        store.entries["account_key_stray"] = cipher.encrypt("stray-secret", "x")

        newRepository(store, cipher, idPrefix = "r")

        assertNull(store.entries["account_key_stray"])
    }
    private companion object {
        const val LEGACY_KEY = "legacy-secret-key-value"
    }
}
