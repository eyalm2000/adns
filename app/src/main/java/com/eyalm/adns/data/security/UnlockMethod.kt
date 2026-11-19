package com.eyalm.adns.data.security

/** How ADNS asks the user to prove they are the phone's owner. */
enum class UnlockMethod {
    /** Android's biometric prompt: fingerprint or face (class 2 or better), or the screen lock. */
    BIOMETRIC_PROMPT,

    /** The system screen lock confirmation screen, for phones where the combined prompt is not offered. */
    SCREEN_LOCK_CONFIRMATION,

    /** The phone offers nothing to check with. */
    NONE,
}

/**
 * Chooses the unlock method from what the phone reports.
 *
 * [canAuthenticateResult] is `BiometricManager.canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)`;
 * [BIOMETRIC_SUCCESS] means the combined prompt can be shown. Anything else (not supported on this
 * Android version, no hardware, nothing enrolled, security update needed, unknown) falls back to the
 * screen lock confirmation when the phone has a screen lock, and otherwise to [UnlockMethod.NONE].
 * Nothing here ever means "unlocked".
 */
fun chooseUnlockMethod(canAuthenticateResult: Int, deviceHasScreenLock: Boolean): UnlockMethod = when {
    canAuthenticateResult == BIOMETRIC_SUCCESS -> UnlockMethod.BIOMETRIC_PROMPT
    deviceHasScreenLock -> UnlockMethod.SCREEN_LOCK_CONFIRMATION
    else -> UnlockMethod.NONE
}

/** Same value as `BiometricManager.BIOMETRIC_SUCCESS`. */
const val BIOMETRIC_SUCCESS = 0