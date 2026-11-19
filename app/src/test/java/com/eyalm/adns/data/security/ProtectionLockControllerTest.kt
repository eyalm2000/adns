package com.eyalm.adns.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionLockControllerTest {
    private class FakeStore(var enabled: Boolean = false, var failWrites: Boolean = false) :
        ProtectionLockStore {
        override fun isEnabled() = enabled

        override fun setEnabled(enabled: Boolean): Boolean {
            if (failWrites) return false
            this.enabled = enabled
            return true
        }
    }

    private var now = 1_000L
    private val grace = 30_000L
    private val store = FakeStore(enabled = true)

    private fun controller(s: FakeStore = store) =
        ProtectionLockController(s, clock = { now }, gracePeriodMs = grace)

    /** The app comes to the foreground with one screen. */
    private fun ProtectionLockController.open() = onActivityStarted()

    /** The last visible screen goes away. */
    private fun ProtectionLockController.leave() = onActivityStopped()

    private fun ProtectionLockController.unlock() {
        assertTrue(endAuthentication(beginAuthentication()!!, success = true))
    }

    @Test
    fun `a lock that is off never locks`() {
        val lock = controller(FakeStore(enabled = false))

        lock.open()
        lock.leave()
        now += 10 * grace
        lock.open()

        assertFalse(lock.locked.value)
    }

    @Test
    fun `the app starts locked while the lock is on`() {
        val lock = controller()

        lock.open()

        assertTrue(lock.locked.value)
    }

    @Test
    fun `a restarted process starts locked again`() {
        val first = controller()
        first.open()
        first.unlock()
        assertFalse(first.locked.value)

        // New process: a new controller reading the stored setting.
        val second = controller()
        second.open()

        assertTrue(second.locked.value)
    }

    @Test
    fun `returning within the grace period does not lock`() {
        val lock = controller()
        lock.open()
        lock.unlock()
        lock.leave()
        now += grace - 1

        lock.open()

        assertFalse(lock.locked.value)
    }

    @Test
    fun `returning after the grace period locks`() {
        val lock = controller()
        lock.open()
        lock.unlock()
        lock.leave()
        now += grace

        lock.open()

        assertTrue(lock.locked.value)
    }

    @Test
    fun `moving between ADNS screens is not leaving the app`() {
        val lock = controller()
        lock.open()
        lock.unlock()

        // A second screen becomes visible before the first one stops.
        now += 5 * grace
        lock.open()
        lock.leave()
        now += 5 * grace
        lock.leave()
        now += grace
        lock.open()

        assertTrue(lock.locked.value)
        lock.unlock()
        // The same sequence with only short pauses never locks.
        lock.open()
        now += 1
        lock.leave()
        now += 1
        lock.leave()
        now += 1
        lock.open()
        assertFalse(lock.locked.value)
    }

    @Test
    fun `a screen being recreated does not lock`() {
        val lock = controller()
        lock.open()
        lock.unlock()

        lock.leave()
        now += 50
        lock.open()

        assertFalse(lock.locked.value)
    }

    @Test
    fun `a clock that went backwards locks`() {
        val lock = controller()
        lock.open()
        lock.unlock()
        lock.leave()
        now -= 5_000

        lock.open()

        assertTrue(lock.locked.value)
    }

    @Test
    fun `leaving and coming back never unlocks a locked app`() {
        val lock = controller()
        lock.open()
        assertTrue(lock.locked.value)

        lock.leave()
        now += 1
        lock.open()

        assertTrue(lock.locked.value)
    }

    @Test
    fun `a failed or cancelled unlock leaves the app locked`() {
        val lock = controller()
        lock.open()

        val token = lock.beginAuthentication()!!
        assertFalse(lock.endAuthentication(token, success = false))

        assertTrue(lock.locked.value)
    }

    @Test
    fun `a long unlock prompt does not relock the app that was just unlocked`() {
        val lock = controller()
        lock.open()
        // The prompt is another screen, so ADNS goes to the background while it is shown.
        lock.beginAuthentication()
        lock.leave()
        now += 10 * grace
        lock.open()
        assertTrue(lock.locked.value)

        lock.unlock()

        assertFalse(lock.locked.value)
    }

    @Test
    fun `time spent in the unlock prompt does not count as time in the background`() {
        val lock = controller()
        lock.open()
        lock.unlock()
        // Asked for the screen lock, for example to turn the lock off.
        val token = lock.beginAuthentication()!!
        lock.leave()
        now += ProtectionLockController.AUTHENTICATION_TIMEOUT_MS - 1
        lock.open()
        lock.endAuthentication(token, success = false)

        assertFalse(lock.locked.value)
    }

    @Test
    fun `turning the lock on does not lock the session it was turned on in`() {
        val off = FakeStore(enabled = false)
        val lock = controller(off)
        lock.open()

        assertTrue(lock.setEnabled(true))

        assertFalse(lock.locked.value)
        assertTrue(off.enabled)
        lock.leave()
        now += grace
        lock.open()
        assertTrue(lock.locked.value)
    }

    @Test
    fun `turning the lock off unlocks and stays off`() {
        val lock = controller()
        lock.open()
        assertTrue(lock.locked.value)

        assertTrue(lock.setEnabled(false))

        assertFalse(lock.locked.value)
        assertFalse(store.enabled)
        lock.leave()
        now += 10 * grace
        lock.open()
        assertFalse(lock.locked.value)
    }

    @Test
    fun `a setting that cannot be stored is reported and changes nothing`() {
        val failing = FakeStore(enabled = false, failWrites = true)
        val lock = controller(failing)
        lock.open()

        assertFalse(lock.setEnabled(true))

        assertFalse(lock.enabled.value)
        assertFalse(failing.enabled)
        assertFalse(lock.locked.value)
    }

    @Test
    fun `setting the current value is accepted without writing`() {
        val failing = FakeStore(enabled = true, failWrites = true)
        val lock = controller(failing)

        assertTrue(lock.setEnabled(true))
        assertEquals(true, lock.enabled.value)
    }

    @Test
    fun `a second prompt cannot start while one is active`() {
        val lock = controller()
        lock.open()
        val first = lock.beginAuthentication()!!
        assertEquals(null, lock.beginAuthentication())
        assertFalse(lock.endAuthentication(first, success = false))
        // Cancel frees the slot again; the app is still locked.
        assertTrue(lock.locked.value)
        assertTrue(lock.beginAuthentication() != null)
    }

    @Test
    fun `a result for an abandoned prompt does not unlock`() {
        val lock = controller()
        lock.open()
        val old = lock.beginAuthentication()!!
        lock.abandonAuthentication(old)
        val current = lock.beginAuthentication()!!
        assertFalse(lock.endAuthentication(old, success = true))
        assertTrue(lock.locked.value)
        assertTrue(lock.endAuthentication(current, success = true))
        assertFalse(lock.locked.value)
    }

    @Test
    fun `a result cannot be used twice`() {
        val lock = controller()
        lock.open()
        val token = lock.beginAuthentication()!!
        assertTrue(lock.endAuthentication(token, success = true))
        lock.leave()
        now += grace
        lock.open()
        assertTrue(lock.locked.value)
        assertFalse(lock.endAuthentication(token, success = true))
        assertTrue(lock.locked.value)
    }

    @Test
    fun `a success from an earlier lock period does not unlock a later one`() {
        val lock = controller()
        lock.open()
        val token = lock.beginAuthentication()!!
        // The prompt outlives its period: the lock is turned off and on again meanwhile.
        assertTrue(lock.setEnabled(false))
        assertTrue(lock.setEnabled(true))
        lock.leave()
        now += grace
        lock.open()
        assertTrue(lock.locked.value)
        assertFalse(lock.endAuthentication(token, success = true))
        assertTrue(lock.locked.value)
    }

    @Test
    fun `a prompt that never answers stops holding off the lock`() {
        val lock = controller()
        lock.open()
        lock.unlock()
        val lost = lock.beginAuthentication()!!
        lock.leave()
        now += ProtectionLockController.AUTHENTICATION_TIMEOUT_MS
        lock.open()
        assertTrue(lock.locked.value)
        assertFalse(lock.endAuthentication(lost, success = true))
        assertTrue(lock.locked.value)
        assertTrue(lock.beginAuthentication() != null)
    }

    @Test
    fun `turning the lock off drops a running prompt`() {
        val lock = controller()
        lock.open()
        val token = lock.beginAuthentication()!!
        assertTrue(lock.setEnabled(false))
        assertFalse(lock.endAuthentication(token, success = true))
        assertTrue(lock.beginAuthentication() != null)
    }

    @Test
    fun `the unlock method follows what the phone reports`() {
        assertEquals(UnlockMethod.BIOMETRIC_PROMPT, chooseUnlockMethod(BIOMETRIC_SUCCESS, true))
        // Unsupported combination, no hardware, nothing enrolled, update needed, unknown: all fall back.
        for (code in listOf(-2, -1, 1, 11, 12, 15, 99)) {
            assertEquals(UnlockMethod.SCREEN_LOCK_CONFIRMATION, chooseUnlockMethod(code, true))
            assertEquals(UnlockMethod.NONE, chooseUnlockMethod(code, false))
        }
    }
}