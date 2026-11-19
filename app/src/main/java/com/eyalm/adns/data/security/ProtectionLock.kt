package com.eyalm.adns.data.security

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the on/off choice is kept. It is a plain flag; the lock stores no secret. */
interface ProtectionLockStore {
    fun isEnabled(): Boolean

    /** Returns false if the choice could not be stored. */
    fun setEnabled(enabled: Boolean): Boolean
}

/**
 * Decides when ADNS's own screens are locked.
 *
 * This is an app-level lock: it keeps ADNS from showing accounts, statistics and logs to someone
 * who picks up an unlocked phone. It does not protect against anyone who can change Android
 * settings, revoke permissions, force-stop or uninstall the app, or use the quick settings tile
 * and notification actions, which work without opening ADNS.
 *
 * ADNS stores no PIN, key or biometric data for it. Unlocking is done by Android's own prompt\n * (fingerprint, face or the phone's screen lock), which the UI shows; this class only records\n * the result.
 *
 * Rules:
 * - While the lock is on, the app starts locked, including after the process was killed.
 * - It locks again when no ADNS screen was visible for at least [gracePeriodMs].
 * - While the screen lock prompt is shown the app is in the background, so that time does not
 *   lock the app again, and a successful prompt always unlocks.
 * - Turning the lock on does not lock the session in which it was turned on.
 */
class ProtectionLockController(
    private val store: ProtectionLockStore,
    private val clock: () -> Long,
    private val gracePeriodMs: Long = DEFAULT_GRACE_PERIOD_MS,
) {
    private val _enabled = MutableStateFlow(store.isEnabled())
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var visibleActivities = 0
    private var firstForeground = true
    private var backgroundedAt: Long? = null
    private var nextToken = 0L
    private var activeToken: Long? = null
    private var activeLockSession = 0L
    private var lockSession = 0L
    private var authenticationStartedAt = 0L

    @Synchronized
    fun onActivityStarted() {
        if (visibleActivities == 0) enterForeground()
        visibleActivities++
    }

    @Synchronized
    fun onActivityStopped() {
        if (visibleActivities > 0) visibleActivities--
        if (visibleActivities == 0) backgroundedAt = clock()
    }

    private fun enterForeground() {
        val now = clock()
        val away = backgroundedAt
        val promptActive = activeToken != null &&
            now >= authenticationStartedAt &&
            now - authenticationStartedAt < AUTHENTICATION_TIMEOUT_MS
        if (!promptActive) activeToken = null
        val mustLock = _enabled.value &&
            !promptActive &&
            (
                firstForeground ||
                    away == null ||
                    // A clock that went backwards cannot prove that little time has passed.
                    now < away ||
                    now - away >= gracePeriodMs
                )
        firstForeground = false
        backgroundedAt = null
        if (mustLock) lock()
    }

    /**
     * Call just before an unlock prompt is shown. Returns a token that has to be given back to
     * [endAuthentication], or null when a prompt is already active, so two prompts never run at
     * once. A token that is not answered within [AUTHENTICATION_TIMEOUT_MS] is treated as
     * abandoned, so a prompt that vanished without a callback cannot disable the lock for good.
     */
    @Synchronized
    fun beginAuthentication(): Long? {
        if (activeToken != null) return null
        nextToken++
        activeToken = nextToken
        activeLockSession = lockSession
        authenticationStartedAt = clock()
        return nextToken
    }

    /**
     * Call with the result of the prompt that [beginAuthentication] started. Only a success of
     * the current prompt, for the lock period it was started in, unlocks. A result with an old,
     * unknown or already used token is ignored and returns false. Failure and cancel leave the
     * lock as it was.
     */
    @Synchronized
    fun endAuthentication(token: Long, success: Boolean): Boolean {
        if (token != activeToken) return false
        val sameLockPeriod = activeLockSession == lockSession
        activeToken = null
        if (success && sameLockPeriod) _locked.value = false
        return success && sameLockPeriod
    }

    /** Drops a prompt that is going away (for example its screen is destroyed). Never unlocks. */
    @Synchronized
    fun abandonAuthentication(token: Long) {
        if (token == activeToken) activeToken = null
    }
    private fun lock() {
        if (!_locked.value) lockSession++
        _locked.value = true
    }

    /**
     * Turns the lock on or off. The caller has to have the user confirm with the screen lock
     * first. Returns false, and changes nothing, if the choice could not be stored.
     */
    @Synchronized
    fun setEnabled(enabled: Boolean): Boolean {
        if (_enabled.value == enabled) return true
        if (!store.setEnabled(enabled)) return false
        _enabled.value = enabled
        if (!enabled) {
            _locked.value = false
            activeToken = null
        }
        return true
    }

    companion object {
        /** Keep in step with the 30 seconds named in `protection_lock_details`. */
        const val DEFAULT_GRACE_PERIOD_MS = 30_000L

        /** How long an unanswered prompt keeps the app from relocking. */
        const val AUTHENTICATION_TIMEOUT_MS = 120_000L
    }
}

private class SharedPreferencesProtectionLockStore(context: Context) : ProtectionLockStore {
    private val preferences =
        context.applicationContext.getSharedPreferences("adns_settings", Context.MODE_PRIVATE)

    override fun isEnabled(): Boolean = preferences.getBoolean(KEY, false)

    override fun setEnabled(enabled: Boolean): Boolean =
        preferences.edit().putBoolean(KEY, enabled).commit()

    private companion object {
        const val KEY = "protection_lock_enabled"
    }
}

object ProtectionLocks {
    @Volatile
    private var instance: ProtectionLockController? = null

    fun getInstance(context: Context): ProtectionLockController =
        instance ?: synchronized(this) {
            instance ?: ProtectionLockController(
                store = SharedPreferencesProtectionLockStore(context),
                clock = SystemClock::elapsedRealtime,
            ).also { instance = it }
        }
}
