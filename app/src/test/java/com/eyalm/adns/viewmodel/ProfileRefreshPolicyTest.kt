package com.eyalm.adns.viewmodel

import com.eyalm.adns.data.account.AccountEpoch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRefreshPolicyTest {
    private val epoch = AccountEpoch("account-a", 3)
    private val interval = PROFILE_REFRESH_MIN_INTERVAL_MS

    private fun decide(
        forced: Boolean = false,
        hasProfiles: Boolean = true,
        selectionChanged: Boolean = false,
        lastSuccessEpoch: AccountEpoch? = epoch,
        currentEpoch: AccountEpoch = epoch,
        lastSuccessAtMs: Long = 100_000L,
        nowMs: Long = 100_000L + 1_000L,
    ) = shouldRefreshProfiles(
        forced, hasProfiles, selectionChanged, lastSuccessEpoch, currentEpoch, lastSuccessAtMs, nowMs,
    )

    @Test
    fun `a recent successful load for the same account is reused`() {
        assertFalse(decide())
    }

    @Test
    fun `a refresh the user asked for always goes out`() {
        assertTrue(decide(forced = true))
    }

    @Test
    fun `an old load is refreshed`() {
        assertTrue(decide(nowMs = 100_000L + interval))
        assertFalse(decide(nowMs = 100_000L + interval - 1))
    }

    @Test
    fun `another account is never served from the previous account's load`() {
        assertTrue(decide(currentEpoch = AccountEpoch("account-b", 4)))
        // Returning to the same account id is a new epoch too.
        assertTrue(decide(currentEpoch = AccountEpoch("account-a", 5)))
    }

    @Test
    fun `nothing loaded yet means a request`() {
        assertTrue(decide(lastSuccessEpoch = null))
        assertTrue(decide(hasProfiles = false))
    }

    @Test
    fun `a changed selection is refreshed`() {
        assertTrue(decide(selectionChanged = true))
    }

    @Test
    fun `a clock that went backwards is not trusted`() {
        assertTrue(decide(lastSuccessAtMs = 200_000L, nowMs = 100_000L))
    }
}
