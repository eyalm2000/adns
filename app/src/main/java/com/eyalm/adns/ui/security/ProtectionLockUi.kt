package com.eyalm.adns.ui.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.DisposableEffect
import com.eyalm.adns.data.security.UnlockMethod
import com.eyalm.adns.data.security.chooseUnlockMethod
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eyalm.adns.R
import com.eyalm.adns.data.security.ProtectionLockController
import com.eyalm.adns.data.security.ProtectionLocks
import com.eyalm.adns.ui.components.SwitchSettingCard
import com.eyalm.adns.ui.theme.AdnsTheme

/**
 * Like `setContent`, but the content is only composed while Protection Lock allows it. While the
 * app is locked nothing of the real screen is composed, so there is nothing to navigate around.
 */
fun ComponentActivity.setProtectedContent(content: @Composable () -> Unit) {
    val controller = ProtectionLocks.getInstance(this)
    // Set before the first frame, so the window is never visible without the flag while the lock is on.
    if (controller.enabled.value) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
    }
    setContent { ProtectionLockGate(controller, content) }
}

@Composable
fun ProtectionLockGate(
    controller: ProtectionLockController,
    content: @Composable () -> Unit,
) {
    val enabled by controller.enabled.collectAsState()
    val locked by controller.locked.collectAsState()
    val activity = LocalContext.current.findActivity()

    // While the lock is on, the screen is kept out of screenshots and the recent apps preview.
    LaunchedEffect(enabled, activity) {
        val window = activity?.window ?: return@LaunchedEffect
        if (enabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    if (enabled && locked) {
        AdnsTheme { LockScreen(controller) }
    } else {
        content()
    }
}

@Composable
private fun LockScreen(controller: ProtectionLockController) {
    val context = LocalContext.current
    var attempted by rememberSaveable { mutableStateOf(false) }
    var notUnlocked by remember { mutableStateOf(false) }
    val title = stringResource(R.string.protection_lock_prompt_title)
    val noScreenLock = stringResource(R.string.protection_lock_turned_off_no_screen_lock)
    val prompt = rememberUnlockPrompt(controller) { success -> notUnlocked = !success }

    fun request() {
        notUnlocked = false
        if (!prompt(title, null)) {
            // Without a screen lock the phone gives no way to check who is there. Staying locked
            // would lock the owner out of their own data, so the lock turns itself off.
            controller.setEnabled(false)
            Toast.makeText(context, noScreenLock, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!attempted) {
            attempted = true
            request()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    if (notUnlocked) {
                        R.string.protection_lock_unlock_failed
                    } else {
                        R.string.protection_lock_locked_message
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = if (notUnlocked) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = ::request, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.protection_lock_unlock))
            }
        }
    }
}

/** The on/off switch with an honest description of what the lock does and does not do. */
@Composable
fun ProtectionLockCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val controller = remember(context) { ProtectionLocks.getInstance(context) }
    val enabled by controller.enabled.collectAsState()
    var pendingTarget by remember { mutableStateOf<Boolean?>(null) }
    var message by remember { mutableStateOf<Int?>(null) }
    val title = stringResource(R.string.protection_lock_title)
    val prompt = rememberUnlockPrompt(controller) { success ->
        val target = pendingTarget
        pendingTarget = null
        if (success && target != null && !controller.setEnabled(target)) {
            message = R.string.account_action_failed
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SwitchSettingCard(
            title = title,
            description = stringResource(R.string.protection_lock_description),
            checked = enabled,
            onCheckedChange = { target ->
                message = null
                // Changing the lock needs the screen lock too, so it cannot be turned off by
                // whoever happens to hold the unlocked phone.
                if (prompt(title, null)) {
                    pendingTarget = target
                } else {
                    message = R.string.protection_lock_needs_screen_lock
                }
            },
        )
        Text(
            text = stringResource(R.string.protection_lock_details),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        message?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Returns a function that asks the user to prove who they are. It shows Android's biometric prompt
 * (fingerprint or face the phone accepts as class 2 or better, with the screen lock as the way
 * out), or the system screen lock screen on phones where that combined prompt is not offered. It
 * returns false, without showing anything, when the phone has no screen lock at all.
 *
 * [onResult] gets true only when the controller accepted the result of the current prompt.
 * Failed attempts do not end the prompt; cancel, errors, lockout and results that arrive after
 * the prompt was replaced or abandoned are reported as false and never unlock.
 */
@Composable
private fun rememberUnlockPrompt(
    controller: ProtectionLockController,
    onResult: (success: Boolean) -> Unit,
): (title: String, description: String?) -> Boolean {
    val context = LocalContext.current
    val latestOnResult = rememberUpdatedState(onResult)
    val holder = remember { PromptHolder() }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val token = holder.token
        holder.token = null
        val accepted = token != null &&
            controller.endAuthentication(token, result.resultCode == Activity.RESULT_OK)
        latestOnResult.value(accepted)
    }

    DisposableEffect(controller) {
        onDispose {
            // The screen is going away (rotation, process end). The result of a prompt that
            // outlives it must not unlock anything, and the next screen has to be able to ask.
            holder.token?.let(controller::abandonAuthentication)
            holder.token = null
            holder.prompt?.cancelAuthentication()
            holder.prompt = null
        }
    }

    return remember(launcher, controller) {
        { title, description ->
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            val activity = context.findActivity() as? FragmentActivity
            val canAuthenticate = BiometricManager.from(context).canAuthenticate(UNLOCK_AUTHENTICATORS)
            val method = chooseUnlockMethod(canAuthenticate, keyguard?.isDeviceSecure == true)
            if (method == UnlockMethod.NONE) return@remember false
            if (holder.token != null) return@remember true // A prompt is already showing.
            val token = controller.beginAuthentication() ?: return@remember true
            holder.token = token
            if (method == UnlockMethod.BIOMETRIC_PROMPT && activity != null) {
                val callback = object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        holder.finish(controller, token, success = true, latestOnResult.value)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        holder.finish(controller, token, success = false, latestOnResult.value)
                    }

                    // A wrong finger or face is not final: the prompt stays up for another try.
                    override fun onAuthenticationFailed() = Unit
                }
                val info = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .apply { description?.let(::setDescription) }
                    .setAllowedAuthenticators(UNLOCK_AUTHENTICATORS)
                    .setConfirmationRequired(false)
                    .build()
                val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
                holder.prompt = prompt
                prompt.authenticate(info)
            } else {
                val intent = keyguard?.createConfirmDeviceCredentialIntent(title, description)
                if (intent == null) {
                    holder.token = null
                    controller.abandonAuthentication(token)
                    return@remember false
                }
                launcher.launch(intent)
            }
            true
        }
    }
}

private class PromptHolder {
    var token: Long? = null
    var prompt: BiometricPrompt? = null

    fun finish(
        controller: ProtectionLockController,
        token: Long,
        success: Boolean,
        onResult: (Boolean) -> Unit,
    ) {
        if (this.token == token) {
            this.token = null
            prompt = null
        }
        onResult(controller.endAuthentication(token, success))
    }
}

/**
 * Class 2 ("weak") biometrics or the screen lock. Class 3 ("strong") is not required: many phones
 * rate their face unlock or some fingerprint sensors as class 2, and requiring class 3 would shut
 * them out. The cost is that a class 2 biometric is easier to fool. That is acceptable here because
 * the lock guards ADNS's own screens and unlocks nothing cryptographic, and it never protects
 * against someone who can already use the phone's settings.
 */
private const val UNLOCK_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
