package com.eyalm.adns.viewmodel

import android.app.Application
import android.app.StatusBarManager
import android.content.ComponentName
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eyalm.adns.R
import com.eyalm.adns.data.DnsRepository
import com.eyalm.adns.data.account.AccountEpoch
import com.eyalm.adns.data.account.AccountUpdateResult
import com.eyalm.adns.data.account.NextDnsAccounts
import com.eyalm.adns.data.dns.DnsConfigurationResult
import com.eyalm.adns.data.dns.DnsDisableBehavior
import com.eyalm.adns.data.nextdns.api.NextDnsProfile
import com.eyalm.adns.data.nextdns.auth.NextDnsManagementSession
import com.eyalm.adns.data.nextdns.auth.NextDnsSessionManager
import com.eyalm.adns.data.nextdns.profile.DEFAULT_NEXTDNS_DEVICE_NAME
import com.eyalm.adns.data.nextdns.profile.NextDnsProfileRepository
import com.eyalm.adns.data.nextdns.profile.isProfileOfAccount
import com.eyalm.adns.data.nextdns.resources.NextDnsResourceSpec
import com.eyalm.adns.data.provider.DnsProviderCatalog
import com.eyalm.adns.data.provider.DnsProviderSelection
import com.eyalm.adns.data.provider.ProviderId
import com.eyalm.adns.domain.nextdns.ApiResult
import com.eyalm.adns.domain.nextdns.ProfileCapabilities
import com.eyalm.adns.domain.nextdns.ProfileRole
import com.eyalm.adns.domain.nextdns.capabilities
import com.eyalm.adns.domain.nextdns.profileRoleFromWire
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

data class ProfileSessionState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val profiles: List<NextDnsProfile> = emptyList(),
    /** The locally configured profile, available even before the profile list can be refreshed. */
    val selectedProfileId: String? = null,
    val selected: NextDnsProfile? = null,
    val capabilities: ProfileCapabilities = ProfileRole.Unknown.capabilities(),
    val logsRevision: Long = 0,
    val error: ApiResult<*>? = null,
    /** The profile this account had chosen no longer exists for it, so the choice was cleared. */
    val selectedProfileMissing: Boolean = false,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    enum class Page {
        MAIN,
        ACTIVATION,
        NOTIFICATIONS,
        WIFI_RULES,
        PROVIDERS,
        ACCOUNT_SETTINGS,
        SETUP,
        SECURITY,
        PRIVACY,
        PARENTAL_CONTROL,
        SETTINGS_PAGE,
        GENERIC_LIST,
        LANGUAGE,
        APPEARANCE,
    }

    private val repository = DnsRepository(application)
    private val profileRepository = NextDnsProfileRepository(application)
    private val nextDnsSessionManager = NextDnsSessionManager.getInstance(application)
    private val accounts = NextDnsAccounts.getInstance(application)

    /** The configured accounts (labels and status only, never keys). */
    val accountsState = accounts.snapshot

    /** True while an account switch, rename or removal is being stored. */
    var accountActionInProgress by mutableStateOf(false)
        private set

    private val _profileSessionState = MutableStateFlow(
        ProfileSessionState(
            selectedProfileId = profileRepository.currentProfileId(),
        )
    )
    val profileSessionState = _profileSessionState.asStateFlow()
    private var profileLoadGeneration = 0L
    private var knownEpoch: AccountEpoch = nextDnsSessionManager.accountEpoch.value

    // When the profile list was last loaded successfully, and for which account.
    private var lastProfileRefreshEpoch: AccountEpoch? = null
    private var lastProfileRefreshAtMs = 0L

    init {
        viewModelScope.launch {
            yield()
            nextDnsSessionManager.accountEpoch.collect { epoch ->
                if (epoch == knownEpoch) return@collect
                knownEpoch = epoch
                // Profiles and their settings belong to the previous account.
                profileLoadGeneration++
                invalidateProfileScopedState()
                email = null
                setPage(Page.MAIN)
                _profileSessionState.value = ProfileSessionState(
                    selectedProfileId = profileRepository.currentProfileId(),
                )
                if (nextDnsSessionManager.state.value == NextDnsManagementSession.Active) {
                    refreshProfileSession(force = true)
                }
            }
        }
        viewModelScope.launch {
            // Defer the first StateFlow emission until all ViewModel properties are initialized.
            yield()
            nextDnsSessionManager.state.collect { session ->
                if (session == NextDnsManagementSession.Expired) {
                    profileLoadGeneration++
                    invalidateProfileScopedState()
                    email = null
                    setPage(Page.MAIN)
                    val previous = _profileSessionState.value
                    _profileSessionState.value = previous.copy(
                        loading = false,
                        profiles = emptyList(),
                        selected = null,
                        capabilities = ProfileRole.Unknown.capabilities(),
                        error = ApiResult.ServerFailure(
                            status = 401,
                            problems = emptyList(),
                        ),
                    )
                }
            }
        }
    }

    fun refreshProfileSession(force: Boolean = false) {
        if (nextDnsSessionManager.state.value != NextDnsManagementSession.Active) return
        val requestEpoch = nextDnsSessionManager.accountEpoch.value
        val shouldRefresh = shouldRefreshProfiles(
            forced = force,
            hasProfiles = _profileSessionState.value.profiles.isNotEmpty(),
            selectionChanged =
                _profileSessionState.value.selectedProfileId != profileRepository.currentProfileId(),
            lastSuccessEpoch = lastProfileRefreshEpoch,
            currentEpoch = requestEpoch,
            lastSuccessAtMs = lastProfileRefreshAtMs,
            nowMs = SystemClock.elapsedRealtime(),
        )
        if (!shouldRefresh) return
        val generation = ++profileLoadGeneration
        val previous = _profileSessionState.value
        val selectedProfileId = profileRepository.currentProfileId()
        val selected = previous.selected?.takeIf { it.id == selectedProfileId }
        val profileChanged = previous.selectedProfileId != selectedProfileId
        if (profileChanged) {
            invalidateProfileScopedState()
            if (_page.value == Page.GENERIC_LIST) {
                setPage(Page.MAIN)
            }
        }
        _profileSessionState.value = previous.copy(
            loading = previous.profiles.isEmpty(),
            refreshing = force && previous.profiles.isNotEmpty(),
            selectedProfileId = selectedProfileId,
            selected = selected,
            capabilities = profileRoleFromWire(selected?.role).capabilities(),
            logsRevision = previous.logsRevision + if (profileChanged) 1 else 0,
            error = null,
        )
        viewModelScope.launch {
            val result = profileRepository.profiles()
            if (
                generation != profileLoadGeneration ||
                requestEpoch != nextDnsSessionManager.accountEpoch.value
            ) return@launch
            when (result) {
                is ApiResult.Success -> {
                    publishProfileSession(result.value)
                    lastProfileRefreshEpoch = requestEpoch
                    lastProfileRefreshAtMs = SystemClock.elapsedRealtime()
                    dropMissingProfileSelection(result.value, requestEpoch)
                }
                else -> {
                    _profileSessionState.value = _profileSessionState.value.copy(
                        loading = false,
                        refreshing = false,
                        error = result,
                    )
                }
            }
        }
    }

    fun onProfileRemoved(
        removedProfileId: String,
        onComplete: () -> Unit = {},
    ) {
        viewModelScope.launch {
            val result = profileRepository.profiles()
            val remaining = when (result) {
                is ApiResult.Success -> result.value.filterNot { it.id == removedProfileId }
                else -> _profileSessionState.value.profiles.filterNot { it.id == removedProfileId }
            }
            val wasSelected = profileRepository.currentProfileId() == removedProfileId
            if (wasSelected) {
                val applyToDevice = accounts.snapshot.value.accounts.size <= 1
                remaining.firstOrNull()?.let { fallback ->
                    profileRepository.selectProfile(
                        fallback,
                        profileRepository.deviceName(),
                        applyToDevice,
                    )
                } ?: if (applyToDevice) {
                    profileRepository.clearSelectedProfile()
                } else {
                    profileRepository.selectAppProfile(null)
                }
                refreshProvider()
            }
            invalidateProfileScopedState()
            publishProfileSession(remaining)
            if (result !is ApiResult.Success) {
                _profileSessionState.value = _profileSessionState.value.copy(error = result)
            }
            setPage(Page.MAIN)
            onComplete()
        }
    }

    fun onProfileRenamed() = refreshProfileSession(force = true)

    private fun publishProfileSession(availableProfiles: List<NextDnsProfile>) {
        val previous = _profileSessionState.value
        val locallySelectedId = profileRepository.currentProfileId()
        val selected = availableProfiles.firstOrNull { it.id == locallySelectedId }
        val selectedProfileId = selected?.id
        val profileChanged = previous.selectedProfileId != selectedProfileId
        if (profileChanged) {
            invalidateProfileScopedState()
            if (_page.value == Page.GENERIC_LIST) {
                setPage(Page.MAIN)
            }
        }
        _profileSessionState.value = ProfileSessionState(
            loading = false,
            refreshing = false,
            profiles = availableProfiles,
            selectedProfileId = selectedProfileId,
            selected = selected,
            capabilities = profileRoleFromWire(selected?.role).capabilities(),
            logsRevision = previous.logsRevision + if (profileChanged) 1 else 0,
        )
    }

    private fun invalidateProfileScopedState() {
        currentListSetting = null
    }

    fun invalidateLogs() {
        _profileSessionState.value = _profileSessionState.value.copy(
            logsRevision = _profileSessionState.value.logsRevision + 1,
        )
    }

    private val _page = MutableStateFlow(Page.MAIN)
    val page = _page.asStateFlow()

    fun addQuickTile() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val statusBarManager =
                getApplication<Application>().getSystemService(StatusBarManager::class.java)
            statusBarManager?.requestAddTileService(
                ComponentName(
                    getApplication(),
                    com.eyalm.adns.services.AdnsTileService::class.java
                ),
                getApplication<Application>().getString(R.string.app_name),
                android.graphics.drawable.Icon.createWithResource(
                    getApplication(),
                    R.drawable.ic_launcher_foreground
                ),
                getApplication<Application>().mainExecutor
            ) { result ->
                val message = when (result) {
                    1 -> getApplication<Application>().getString(R.string.tile_already_added)
                    2 -> getApplication<Application>().getString(R.string.tile_added)
                    else -> ""
                }
                if (message.isNotEmpty())
                    Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(
                getApplication(),
                getApplication<Application>().getString(R.string.feature_not_supported_on_this_version),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val selectedProvider = repository.getSelectionFlow()
    val disableBehavior = repository.getDisableBehaviorFlow()
    private val _providerChangeResult = MutableStateFlow<DnsConfigurationResult?>(null)
    val providerChangeResult = _providerChangeResult.asStateFlow()

    fun setProvider(selection: DnsProviderSelection) {
        viewModelScope.launch {
            val result = repository.changeSelection(selection)
            _providerChangeResult.value = result
            if (result == DnsConfigurationResult.PermissionMissing) {
                setPage(Page.ACTIVATION)
            }
        }
    }

    fun setDisableBehavior(value: DnsDisableBehavior) {
        repository.setDisableBehavior(value)
    }

    fun refreshProvider() = Unit

    fun isLoggedIn(providerId: ProviderId): Boolean =
        providerId == DnsProviderCatalog.NEXTDNS && profileRepository.isSignedIn()

    suspend fun getEmail(): String {
        return profileRepository.email()
    }

    fun setPage(page: Page): Boolean {
        if (!requestPageAccess(page)) return false
        _page.value = page
        return true
    }

    private fun requestPageAccess(page: Page): Boolean = when (page) {
        Page.ACCOUNT_SETTINGS,
        Page.SETUP,
        Page.SECURITY,
        Page.PRIVACY,
        Page.PARENTAL_CONTROL,
        Page.SETTINGS_PAGE,
        Page.GENERIC_LIST,
        -> nextDnsSessionManager.requestFeatureAccess()

        Page.MAIN,
        Page.ACTIVATION,
        Page.NOTIFICATIONS,
        Page.WIFI_RULES,
        Page.PROVIDERS,
        Page.LANGUAGE,
        Page.APPEARANCE,
        -> true
    }



    var email by mutableStateOf<String?>(null)
    var nextDnsDeviceName by mutableStateOf(profileRepository.deviceName())
        private set

    fun setProfile(profile: NextDnsProfile) {
        val requestEpoch = nextDnsSessionManager.accountEpoch.value
        viewModelScope.launch {
            // Only a profile the active account itself was given by NextDNS can be chosen.
            if (!isProfileOfAccount(_profileSessionState.value.profiles, profile.id)) {
                showMessage(R.string.account_action_failed)
                return@launch
            }
            val currentName = profileRepository.deviceName()
            val nameToSet = currentName.ifEmpty { DEFAULT_NEXTDNS_DEVICE_NAME }
            // With one account, choosing a profile also sets the device DNS, as it always did.
            // With several accounts it only sets this account's profile, so working with another
            // account can never change the device DNS by accident.
            val applyToDevice = accounts.snapshot.value.accounts.size <= 1
            profileRepository.selectProfile(profile, nameToSet, applyToDevice)
            if (requestEpoch != nextDnsSessionManager.accountEpoch.value) return@launch
            nextDnsDeviceName = profileRepository.deviceName()
            if (profileRepository.currentProfileId() != profile.id) {
                showMessage(R.string.account_action_failed)
                return@launch
            }
            val knownProfiles = _profileSessionState.value.profiles
            val updatedProfiles = if (knownProfiles.any { it.id == profile.id }) {
                knownProfiles.map { if (it.id == profile.id) profile else it }
            } else {
                knownProfiles + profile
            }
            publishProfileSession(updatedProfiles)
            refreshProvider()
            refreshProfileSession()
        }
    }

    fun switchAccount(accountId: String) =
        runAccountAction(R.string.account_switched) { profileRepository.switchAccount(accountId) }

    fun renameAccount(accountId: String, label: String) =
        runAccountAction(R.string.done) { profileRepository.renameAccount(accountId, label) }

    fun removeAccount(accountId: String) =
        runAccountAction(R.string.account_removed) { profileRepository.removeAccount(accountId) }

    private fun runAccountAction(
        successMessage: Int,
        action: suspend () -> AccountUpdateResult,
    ) {
        if (accountActionInProgress) return
        accountActionInProgress = true
        viewModelScope.launch {
            try {
                val result = action()
                // Only a stored change is reported as done; anything else is shown as a failure.
                showMessage(
                    if (result == AccountUpdateResult.Applied) {
                        successMessage
                    } else {
                        R.string.account_action_failed
                    }
                )
            } finally {
                accountActionInProgress = false
            }
        }
    }

    /**
     * If the profile this account had chosen is not among the profiles NextDNS just returned for
     * it (deleted, or no longer shared), the choice is cleared. Another profile is never picked
     * for the user, and a profile of another account is never used instead.
     */
    private fun dropMissingProfileSelection(
        available: List<NextDnsProfile>,
        requestEpoch: AccountEpoch,
    ) {
        val stored = profileRepository.currentProfileId() ?: return
        if (isProfileOfAccount(available, stored)) return
        viewModelScope.launch {
            if (requestEpoch != nextDnsSessionManager.accountEpoch.value) return@launch
            if (profileRepository.currentProfileId() != stored) return@launch
            if (profileRepository.selectAppProfile(null) != AccountUpdateResult.Applied) return@launch
            if (requestEpoch != nextDnsSessionManager.accountEpoch.value) return@launch
            _profileSessionState.value = _profileSessionState.value.copy(
                selectedProfileId = null,
                selected = null,
                selectedProfileMissing = true,
            )
        }
    }

    private fun showMessage(message: Int) {
        Toast.makeText(
            getApplication(),
            getApplication<Application>().getString(message),
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun updateDeviceName(name: String) {
        viewModelScope.launch {
            profileRepository.setDeviceName(name)
            nextDnsDeviceName = profileRepository.deviceName()
            Toast.makeText(
                getApplication(),
                getApplication<Application>().getString(R.string.done),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun createProfile(name: String) {
        viewModelScope.launch {
            when (val createResult = profileRepository.createProfile(name)) {
                is ApiResult.Success -> when (val profilesResult = profileRepository.profiles()) {
                    is ApiResult.Success -> publishProfileSession(profilesResult.value)
                    else -> _profileSessionState.value =
                        _profileSessionState.value.copy(error = profilesResult)
                }

                else -> _profileSessionState.value =
                    _profileSessionState.value.copy(error = createResult)
            }
        }
    }

    fun logout() {
        profileLoadGeneration++
        viewModelScope.launch {
            // PersistFailed means the sign-out could not be stored and nothing changed, so the
            // session is still valid: keep showing it instead of pretending to be signed out.
            if (profileRepository.signOut() == AccountUpdateResult.PersistFailed) {
                refreshProfileSession(force = true)
                return@launch
            }
            invalidateProfileScopedState()
            _profileSessionState.value = ProfileSessionState(
                loading = false,
                selectedProfileId = profileRepository.currentProfileId(),
            )
            setPage(Page.MAIN)
            refreshProvider()
        }
    }

    var currentListSetting: NextDnsResourceSpec? = null
        private set

    private var listParentPage: Page = Page.MAIN

    fun openListScreen(listSetting: NextDnsResourceSpec) {
        if (!requestPageAccess(Page.GENERIC_LIST)) return
        currentListSetting = listSetting
        listParentPage = when (listSetting.parentPage) {
            NextDnsResourceSpec.ParentPage.SECURITY -> Page.SECURITY
            NextDnsResourceSpec.ParentPage.PRIVACY -> Page.PRIVACY
            NextDnsResourceSpec.ParentPage.PARENTAL_CONTROL -> Page.PARENTAL_CONTROL
            null -> Page.MAIN
        }
        _page.value = Page.GENERIC_LIST
    }

    fun getListParentPage(): Page = listParentPage
}

/** A refresh nobody asked for is skipped if the same account was refreshed this recently. */
internal const val PROFILE_REFRESH_MIN_INTERVAL_MS = 30_000L

/**
 * Whether the profile list should be requested again. A refresh the user asked for, an empty
 * list, a changed selection, another account, or a clock that went backwards always refreshes;
 * otherwise a recent successful load for the same account is reused. This keeps coming back to
 * the app from sending a request every time, without ever hiding a change the user made.
 */
internal fun shouldRefreshProfiles(
    forced: Boolean,
    hasProfiles: Boolean,
    selectionChanged: Boolean,
    lastSuccessEpoch: AccountEpoch?,
    currentEpoch: AccountEpoch,
    lastSuccessAtMs: Long,
    nowMs: Long,
): Boolean = forced ||
    !hasProfiles ||
    selectionChanged ||
    lastSuccessEpoch != currentEpoch ||
    nowMs < lastSuccessAtMs ||
    nowMs - lastSuccessAtMs >= PROFILE_REFRESH_MIN_INTERVAL_MS