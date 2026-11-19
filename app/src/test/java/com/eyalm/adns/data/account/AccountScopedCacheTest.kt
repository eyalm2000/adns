package com.eyalm.adns.data.account

import com.eyalm.adns.data.nextdns.auth.NextDnsSessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountScopedCacheTest {
    private val repository = newRepository(FakeSecureStore(), FakeSecretCipher())
    private val session = NextDnsSessionManager(repository)
    private val cache = AccountScopedCache<String, String>(session.accountEpoch::value)

    private fun signIn(label: String, key: String): String =
        (repository.signIn(label, key, label) as SignInResult.Success).accountId

    @Test
    fun `entries of one account are not visible after another account becomes active`() {
        signIn("a@example.test", "key-a")
        assertTrue(cache.put(session.accountEpoch.value, "stats", "data of A"))
        assertEquals("data of A", cache["stats"])

        signIn("b@example.test", "key-b")

        assertNull(cache["stats"])
        assertFalse("stats" in cache)
    }

    @Test
    fun `a late write from the previous account is dropped`() {
        signIn("a@example.test", "key-a")
        val epochOfA = session.accountEpoch.value
        val b = signIn("b@example.test", "key-b")

        val stored = cache.put(epochOfA, "stats", "late data of A")

        assertFalse(stored)
        assertNull(cache["stats"])
        // Account B's own data is stored normally.
        assertTrue(cache.put(session.accountEpoch.value, "stats", "data of B"))
        assertEquals("data of B", cache["stats"])
        assertEquals(b, session.accountEpoch.value.accountId)
    }

    @Test
    fun `returning to an account does not bring back its old entries or accept its old requests`() {
        val a = signIn("a@example.test", "key-a")
        val firstEpochOfA = session.accountEpoch.value
        cache.put(firstEpochOfA, "stats", "old data of A")
        val b = signIn("b@example.test", "key-b")
        repository.setActive(a)

        assertNull(cache["stats"])
        // A request started during the first period of A must still be rejected.
        assertFalse(cache.put(firstEpochOfA, "stats", "late old data of A"))
        assertNull(cache["stats"])
        assertTrue(cache.put(session.accountEpoch.value, "stats", "fresh data of A"))
        assertEquals("fresh data of A", cache["stats"])
        repository.setActive(b)
        assertNull(cache["stats"])
    }

    @Test
    fun `clear empties the cache and keeps it usable`() {
        signIn("a@example.test", "key-a")
        cache.put(session.accountEpoch.value, "one", "1")
        cache.put(session.accountEpoch.value, "two", "2")

        cache.clear()

        assertNull(cache["one"])
        assertTrue(cache.put(session.accountEpoch.value, "one", "again"))
        assertEquals("again", cache["one"])
    }

    @Test
    fun `refreshing the same account keeps its entries`() {
        val a = signIn("a@example.test", "key-a")
        cache.put(session.accountEpoch.value, "stats", "data of A")

        repository.setSelectedHostname(a, "a.dns.nextdns.io")
        repository.signIn("a@example.test", "key-a", "a@example.test")

        assertEquals("data of A", cache["stats"])
    }
}
