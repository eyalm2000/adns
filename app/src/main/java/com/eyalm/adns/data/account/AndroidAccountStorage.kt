package com.eyalm.adns.data.account

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AesGcmKeyManager
import com.google.crypto.tink.integration.android.AndroidKeysetManager

class SharedPreferencesSecureStore(
    private val preferences: SharedPreferences,
) : SecureKeyValueStore {
    override fun get(key: String): String? = preferences.getString(key, null)

    override fun keys(): Set<String> = preferences.all.keys.toSet()

    override fun commit(edits: Map<String, String?>): Boolean {
        val editor = preferences.edit()
        for ((key, value) in edits) {
            if (value == null) {
                editor.remove(key)
            } else {
                editor.putString(key, value)
            }
        }
        return editor.commit()
    }
}

/**
 * Tink AES-256-GCM under an Android Keystore master key. The keyset location and master key
 * URI are identical to the ones the single-account `TokenManager` used, so entries written by
 * earlier versions remain readable.
 */
class TinkSecretCipher(context: Context) : SecretCipher {
    private val appContext = context.applicationContext

    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(appContext, "tink_keyset", "tink_prefs_internal")
            .withKeyTemplate(AesGcmKeyManager.aes256GcmTemplate())
            .withMasterKeyUri("android-keystore://secure_string_master_key")
            .build()
            .keysetHandle
            .getPrimitive(Aead::class.java)
    }

    override fun encrypt(plaintext: String, associatedData: String?): String {
        val encrypted = aead.encrypt(
            plaintext.toByteArray(Charsets.UTF_8),
            associatedData?.toByteArray(Charsets.UTF_8),
        )
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    override fun decrypt(ciphertext: String, associatedData: String?): String? = try {
        val decrypted = aead.decrypt(
            Base64.decode(ciphertext, Base64.DEFAULT),
            associatedData?.toByteArray(Charsets.UTF_8),
        )
        String(decrypted, Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }
}

object NextDnsAccounts {
    @Volatile
    private var instance: NextDnsAccountRepository? = null

    fun getInstance(context: Context): NextDnsAccountRepository =
        instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

    private fun create(appContext: Context): NextDnsAccountRepository =
        NextDnsAccountRepository(
            store = SharedPreferencesSecureStore(
                appContext.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)
            ),
            cipher = TinkSecretCipher(appContext),
            legacySelectedHostname = {
                appContext
                    .getSharedPreferences("adns_settings", Context.MODE_PRIVATE)
                    .getString("enhanced_url", null)
            },
        )
}
