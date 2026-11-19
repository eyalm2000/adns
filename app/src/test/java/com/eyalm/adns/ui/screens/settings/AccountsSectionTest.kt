package com.eyalm.adns.ui.screens.settings

import com.eyalm.adns.data.account.AccountSummary
import com.eyalm.adns.data.nextdns.api.NextDnsProfile
import com.eyalm.adns.data.nextdns.auth.NextDnsManagementSession
import com.eyalm.adns.data.nextdns.auth.NextDnsSessionManager
import com.eyalm.adns.data.nextdns.profile.isProfileOfAccount
import com.eyalm.adns.data.account.FakeSecretCipher
import com.eyalm.adns.data.account.FakeSecureStore
import com.eyalm.adns.data.account.SignInResult
import com.eyalm.adns.data.account.newRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountsSectionTest {
    private fun account(id: String, label: String) = AccountSummary(
        id = id,
        label = label,
        selectedHostname = null,
        hasCredential = true,
    )

    @Test
    fun `accounts with the same name get numbers so they can be told apart`() {
        val names = accountDisplayNames(
            listOf(
                account("1", "API Key Account"),
                account("2", "work@example.test"),
                account("3", "API Key Account"),
                account("4", "  "),
            ),
            unnamed = "Unnamed account",
        )

        assertEquals("API Key Account (1)", names["1"])
        assertEquals("work@example.test", names["2"])
        assertEquals("API Key Account (2)", names["3"])
        assertEquals("Unnamed account", names["4"])
    }

    @Test
    fun `names are never empty and never expose more than the label`() {
        val names = accountDisplayNames(listOf(account("1", "")), unnamed = "Unnamed account")

        assertEquals(mapOf("1" to "Unnamed account"), names)
    }

    @Test
    fun `only a profile from the account's own list can be chosen`() {
        val own = listOf(
            NextDnsProfile(id = "own1", name = "Home"),
            NextDnsProfile(id = "own2", name = "Work"),
        )

        assertTrue(isProfileOfAccount(own, "own1"))
        assertFalse(isProfileOfAccount(own, "otherAccountsProfile"))
        assertFalse(isProfileOfAccount(emptyList(), "own1"))
        assertFalse(isProfileOfAccount(own, ""))
    }

    @Test
    fun `the session manager reports the active account's profile and follows switches`() {
        val repository = newRepository(FakeSecureStore(), FakeSecretCipher())
        val session = NextDnsSessionManager(repository)
        val a = (repository.signIn("a@example.test", "key-a", "a@example.test") as SignInResult.Success).accountId
        repository.setSelectedProfile(a, "profA1")
        val b = (repository.signIn("b@example.test", "key-b", "b@example.test") as SignInResult.Success).accountId
        assertNull(session.activeProfileId.value)

        repository.setSelectedProfile(b, "profB1")
        assertEquals("profB1", session.activeProfileId.value)
        repository.setActive(a)
        assertEquals("profA1", session.activeProfileId.value)
        repository.setActive(b)
        assertEquals("profB1", session.activeProfileId.value)
        repository.remove(b, activateFallback = true)
        assertEquals("profA1", session.activeProfileId.value)
        assertEquals(NextDnsManagementSession.Active, session.state.value)
    }

    @Test
    fun `choosing another profile for the same account does not start a new epoch`() {
        val repository = newRepository(FakeSecureStore(), FakeSecretCipher())
        val session = NextDnsSessionManager(repository)
        val a = (repository.signIn("a@example.test", "key-a", "a@example.test") as SignInResult.Success).accountId
        repository.setSelectedProfile(a, "profA1")
        val epoch = session.accountEpoch.value

        repository.setSelectedProfile(a, "profA2")

        assertEquals("profA2", session.activeProfileId.value)
        assertEquals(epoch, session.accountEpoch.value)
    }
}
