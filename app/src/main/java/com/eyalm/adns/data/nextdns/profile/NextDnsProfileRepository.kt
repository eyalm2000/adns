package com.eyalm.adns.data.nextdns.profile

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.eyalm.adns.data.AppRuntimeRepositories
import com.eyalm.adns.data.DnsRepository
import com.eyalm.adns.data.TokenManager
import com.eyalm.adns.data.account.AccountUpdateResult
import com.eyalm.adns.data.account.NextDnsAccounts
import com.eyalm.adns.data.account.profileIdFromNextDnsHostname
import com.eyalm.adns.data.dns.DnsConfigurationResult
import com.eyalm.adns.data.provider.DnsProviderCatalog
import com.eyalm.adns.data.provider.DnsProviderSelection
import com.eyalm.adns.data.provider.ResolverPresetId
import com.eyalm.adns.data.network.ApiClient
import com.eyalm.adns.data.nextdns.api.NextDnsApi
import com.eyalm.adns.data.nextdns.api.NextDnsCreateProfileRequest
import com.eyalm.adns.data.nextdns.api.NextDnsProfile
import com.eyalm.adns.data.nextdns.api.nextDnsApiCall
import com.eyalm.adns.data.nextdns.api.toEmptyApiResult
import com.eyalm.adns.data.nextdns.api.toJsonApiResult
import com.eyalm.adns.data.nextdns.api.toServerFailure
import com.eyalm.adns.data.nextdns.auth.NextDnsSessionManager
import com.eyalm.adns.data.nextdns.logs.LogExportResult
import com.eyalm.adns.domain.nextdns.ApiProblem
import com.eyalm.adns.domain.nextdns.ApiResult
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NextDnsProfileRepository(
    context: Context,
    private val api: NextDnsApi = ApiClient.nextDnsApi,
) {
    private val appContext = context.applicationContext
    private val sharedPreferences =
        appContext.getSharedPreferences("adns_settings", Context.MODE_PRIVATE)
    private val dnsRepository = DnsRepository(appContext)
    private val tokenManager = TokenManager.getInstance(appContext)
    private val accounts = NextDnsAccounts.getInstance(appContext)
    private val gson = Gson()

    /**
     * The profile the app uses for the active account's statistics, logs and settings. Each
     * account has its own. It is null when the active account has not chosen one; the device DNS
     * profile of another account is never used as a stand-in.
     */
    fun currentProfileId(): String? = accounts.snapshot.value.activeProfileId

    /** The profile in the device-wide DNS hostname (`enhanced_url`). Not account specific. */
    private fun deviceProfileId(): String? =
        profileIdFromNextDnsHostname(sharedPreferences.getString("enhanced_url", null))

    suspend fun email(): String = withContext(Dispatchers.IO) {
        tokenManager.getEmail().orEmpty()
    }

    fun isSignedIn(): Boolean = tokenManager.hasToken()

    suspend fun profiles(): ApiResult<List<NextDnsProfile>> = authenticatedCall {
        when (val result = api.getProfilesRaw().toJsonApiResult()) {
            is ApiResult.Success -> {
                val data = result.value.getAsJsonArray("data")
                    ?: return@authenticatedCall ApiResult.SerializationFailure(
                        IllegalStateException("Missing profiles data")
                    )
                ApiResult.Success(
                    gson.fromJson(data, Array<NextDnsProfile>::class.java).toList(),
                    result.status,
                )
            }

            is ApiResult.ServerFailure -> result
            is ApiResult.NetworkFailure -> result
            is ApiResult.SerializationFailure -> result
        }
    }

    suspend fun createProfile(name: String): ApiResult<NextDnsProfile> = authenticatedCall {
        when (
            val result = api.createProfile(NextDnsCreateProfileRequest.withName(name.trim()))
                .toJsonApiResult()
        ) {
            is ApiResult.Success -> {
                val data = result.value.getAsJsonObject("data")
                    ?: return@authenticatedCall ApiResult.SerializationFailure(
                        IllegalStateException("Missing created profile data")
                    )
                ApiResult.Success(
                    gson.fromJson(data, NextDnsProfile::class.java),
                    result.status,
                )
            }

            is ApiResult.ServerFailure -> result
            is ApiResult.NetworkFailure -> result
            is ApiResult.SerializationFailure -> result
        }
    }

    /**
     * Chooses [profile] for the active account. With [applyToDevice] the device-wide DNS setting
     * is changed to it as well (what choosing a profile always did); without it only the app's
     * own profile for this account changes and the device DNS is left alone.
     */
    suspend fun selectProfile(
        profile: NextDnsProfile,
        deviceName: String? = DEFAULT_NEXTDNS_DEVICE_NAME,
        applyToDevice: Boolean = true,
    ): DnsConfigurationResult {
        if (!applyToDevice) {
            return if (selectAppProfile(profile.id) == AccountUpdateResult.Applied) {
                DnsConfigurationResult.Changed(
                    dnsRepository.currentSelection(),
                    appliedToDevice = false,
                )
            } else {
                DnsConfigurationResult.InvalidSelection
            }
        }
        return selectProfileId(profile.id, deviceName)
    }

    suspend fun selectProfileId(
        profileId: String,
        deviceName: String? = DEFAULT_NEXTDNS_DEVICE_NAME,
    ): DnsConfigurationResult {
        val result = selectProfileHostname(nextDnsProfileHostname(profileId, deviceName))
        // The app's profile follows the device selection here, as it always did. If it cannot be
        // stored the selection is reported as failed, so it is not shown as done.
        if (result is DnsConfigurationResult.Changed &&
            selectAppProfile(profileId) != AccountUpdateResult.Applied
        ) {
            return DnsConfigurationResult.InvalidSelection
        }
        return result
    }

    /** Sets, or with null clears, the app's profile for the active account. */
    suspend fun selectAppProfile(profileId: String?): AccountUpdateResult =
        withContext(Dispatchers.IO) {
            val accountId = accounts.snapshot.value.activeAccountId
                ?: return@withContext AccountUpdateResult.NotFound
            accounts.setSelectedProfile(accountId, profileId)
        }

    suspend fun setDeviceName(deviceName: String): DnsConfigurationResult? {
        // The device name belongs to the device DNS hostname, so it keeps that hostname's profile.
        val profileId = deviceProfileId() ?: return null
        return selectProfileHostname(nextDnsProfileHostname(profileId, deviceName))
    }

    fun deviceName(): String {
        val hostname = sharedPreferences.getString("enhanced_url", "").orEmpty()
        val cleanUrl = hostname.removeSuffix(".dns.nextdns.io")
        return if ('-' in cleanUrl) {
            cleanUrl.substringBeforeLast('-').replace("--", " ")
        } else {
            ""
        }
    }

    suspend fun clearSelectedProfile() {
        selectAppProfile(null)
        if (dnsRepository.currentSelection() is DnsProviderSelection.Enhanced) {
            if (!selectFallbackProvider()) return
        }
        val result = dnsRepository.clearEnhancedHostname()
        if (result is com.eyalm.adns.data.provider.ProviderSelectionUpdateResult.Saved) {
            recordSelectedHostname(null)
        }
    }

    /** Signs out the active account. Nothing changes unless [AccountUpdateResult.Applied] is returned. */
    suspend fun signOut(): AccountUpdateResult = withContext(Dispatchers.IO) {
        // If another account can be used it takes over in the same write instead of leaving the
        // app signed out with accounts that nobody can switch to.
        NextDnsSessionManager.getInstance(appContext).signedOut(activateFallback = true)
    }

    /** Makes [accountId] the active account. Only an account that has a key can be activated. */
    suspend fun switchAccount(accountId: String): AccountUpdateResult =
        withContext(Dispatchers.IO) {
            val target = accounts.snapshot.value.accounts.firstOrNull { it.id == accountId }
                ?: return@withContext AccountUpdateResult.NotFound
            if (!target.hasCredential) return@withContext AccountUpdateResult.Invalid
            accounts.setActive(accountId)
        }

    suspend fun renameAccount(accountId: String, label: String): AccountUpdateResult =
        withContext(Dispatchers.IO) { accounts.rename(accountId, label) }

    /** Removes one account. If it was the active one the next usable account takes over. */
    suspend fun removeAccount(accountId: String): AccountUpdateResult =
        withContext(Dispatchers.IO) { accounts.remove(accountId, activateFallback = true) }

    private suspend fun selectProfileHostname(hostname: String): DnsConfigurationResult {
        val capabilities = AppRuntimeRepositories.capabilities(appContext).current()
        val result = if (capabilities.canControlPrivateDns) {
            dnsRepository.changeEnhancedSelection(hostname)
        } else {
            when (dnsRepository.stageEnhancedSelection(hostname)) {
                is com.eyalm.adns.data.provider.ProviderSelectionUpdateResult.Saved ->
                    DnsConfigurationResult.Changed(
                        DnsProviderSelection.Enhanced(DnsProviderCatalog.NEXTDNS),
                        appliedToDevice = false,
                    )

                is com.eyalm.adns.data.provider.ProviderSelectionUpdateResult.Invalid ->
                    DnsConfigurationResult.InvalidSelection
            }
        }
        if (result is DnsConfigurationResult.Changed) recordSelectedHostname(hostname)
        return result
    }

    // The device-wide hostname (enhanced_url) stays authoritative in this stage. It is also
    // recorded on the active account so each account keeps its own selected profile.
    private suspend fun recordSelectedHostname(hostname: String?) =
        withContext(Dispatchers.IO) {
            accounts.snapshot.value.activeAccountId?.let { accountId ->
                accounts.setSelectedHostname(accountId, hostname)
            }
        }

    private suspend fun selectFallbackProvider(): Boolean {
        val fallback = DnsProviderSelection.Standard(
            DnsProviderCatalog.ADGUARD,
            ResolverPresetId("default"),
        )
        val result = if (
            AppRuntimeRepositories.capabilities(appContext).current().canControlPrivateDns
        ) {
            dnsRepository.changeSelection(fallback)
        } else {
            return dnsRepository.stageSelection(fallback) is
                com.eyalm.adns.data.provider.ProviderSelectionUpdateResult.Saved
        }
        return result is DnsConfigurationResult.Changed
    }

    suspend fun clearLogs(profileId: String): ApiResult<Unit> = authenticatedCall {
        api.clearLogs(profileId).toEmptyApiResult()
    }

    suspend fun exportLogs(profileId: String, destination: Uri): LogExportResult =
        withContext(Dispatchers.IO) {
            if (!isSignedIn()) {
                return@withContext LogExportResult.ApiFailure(authRequired())
            }
            val response = try {
                api.downloadLogs(profileId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                return@withContext LogExportResult.ApiFailure(ApiResult.NetworkFailure(error))
            } catch (error: Exception) {
                return@withContext LogExportResult.ApiFailure(
                    ApiResult.SerializationFailure(error)
                )
            }

            if (!response.isSuccessful) {
                return@withContext LogExportResult.ApiFailure(response.toServerFailure())
            }
            val body = response.body()
                ?: return@withContext LogExportResult.ApiFailure(
                    ApiResult.SerializationFailure(
                        IllegalStateException("Missing logs download body")
                    )
                )

            try {
                body.byteStream().use { input ->
                    appContext.contentResolver.openOutputStream(destination)?.use { output ->
                        input.copyTo(output)
                    } ?: return@withContext LogExportResult.DestinationFailure(
                        IllegalStateException("Unable to open export destination")
                    )
                }
                LogExportResult.Success
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                LogExportResult.DestinationFailure(error)
            }
        }

    suspend fun duplicateProfile(
        profileId: String,
        newName: String,
    ): ApiResult<NextDnsProfile> {
        val detail = profileDetail(profileId)
        if (detail !is ApiResult.Success) return detail.asFailure()

        return authenticatedCall {
            when (
                val result = api.duplicateProfile(
                    detail.value.toDuplicateProfilePayload(newName.trim())
                ).toJsonApiResult()
            ) {
                is ApiResult.Success -> {
                    val data = result.value.getAsJsonObject("data")
                        ?: return@authenticatedCall ApiResult.SerializationFailure(
                            IllegalStateException("Missing duplicated profile data")
                        )
                    ApiResult.Success(
                        gson.fromJson(data, NextDnsProfile::class.java),
                        result.status,
                    )
                }

                is ApiResult.ServerFailure -> result
                is ApiResult.NetworkFailure -> result
                is ApiResult.SerializationFailure -> result
            }
        }
    }

    suspend fun renameProfile(profileId: String, newName: String): ApiResult<Unit> =
        authenticatedCall {
            api.renameProfile(
                profileId,
                JsonObject().apply { addProperty("name", newName.trim()) },
            ).toEmptyApiResult()
        }

    suspend fun deleteOrLeaveProfile(profileId: String): ApiResult<Unit> =
        authenticatedCall {
            api.deleteOrLeaveProfile(profileId).toEmptyApiResult()
        }

    private suspend fun profileDetail(profileId: String): ApiResult<JsonObject> =
        authenticatedCall {
            when (val result = api.getProfileDetail(profileId).toJsonApiResult()) {
                is ApiResult.Success -> {
                    val data = result.value.getAsJsonObject("data")
                        ?: return@authenticatedCall ApiResult.SerializationFailure(
                            IllegalStateException("Missing profile detail data")
                        )
                    ApiResult.Success(data, result.status)
                }

                is ApiResult.ServerFailure -> result
                is ApiResult.NetworkFailure -> result
                is ApiResult.SerializationFailure -> result
            }
        }

    private suspend fun <T> authenticatedCall(
        block: suspend () -> ApiResult<T>,
    ): ApiResult<T> {
        if (!isSignedIn()) return authRequired()
        return nextDnsApiCall(block)
    }

    private fun authRequired() = ApiResult.ServerFailure(
        status = 401,
        problems = listOf(ApiProblem("authRequired")),
    )
}

const val DEFAULT_NEXTDNS_DEVICE_NAME = "ADNS"

fun nextDnsProfileHostname(
    profileId: String,
    deviceName: String? = DEFAULT_NEXTDNS_DEVICE_NAME,
): String {
    val sanitizedName = deviceName.orEmpty().trim().replace(" ", "--")
    return if (sanitizedName.isEmpty()) {
        "$profileId.dns.nextdns.io"
    } else {
        "$sanitizedName-$profileId.dns.nextdns.io"
    }
}

private fun <T> ApiResult<JsonObject>.asFailure(): ApiResult<T> = when (this) {
    is ApiResult.ServerFailure -> this
    is ApiResult.NetworkFailure -> this
    is ApiResult.SerializationFailure -> this
    is ApiResult.Success -> error("Success cannot be converted to a failure")
}

/**
 * Whether [profileId] is one of the profiles the active account was just given by NextDNS. A
 * profile is only ever chosen from that list, never from an id typed or stored elsewhere.
 */
internal fun isProfileOfAccount(available: List<NextDnsProfile>, profileId: String): Boolean =
    available.any { it.id == profileId }