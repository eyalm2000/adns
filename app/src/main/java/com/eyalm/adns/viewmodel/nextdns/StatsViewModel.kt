package com.eyalm.adns.viewmodel.nextdns

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eyalm.adns.data.DnsRepository
import com.eyalm.adns.data.account.AccountEpoch
import com.eyalm.adns.data.account.AccountScopedCache
import com.eyalm.adns.data.provider.DnsProviderSelection
import com.eyalm.adns.data.nextdns.api.NextDnsDeviceItem
import com.eyalm.adns.data.nextdns.api.NextDnsStatsGraphResponse
import com.eyalm.adns.data.nextdns.analytics.AnalyticsPeriod
import com.eyalm.adns.data.nextdns.analytics.AnalyticsScope
import com.eyalm.adns.data.nextdns.analytics.ListCard
import com.eyalm.adns.data.nextdns.analytics.NextDnsAnalyticsRepository
import com.eyalm.adns.data.nextdns.analytics.PercentCard
import com.eyalm.adns.data.nextdns.analytics.StatRow
import com.eyalm.adns.data.nextdns.analytics.StatsRegistry
import com.eyalm.adns.data.nextdns.analytics.parseList
import com.eyalm.adns.data.nextdns.analytics.parsePercent
import com.eyalm.adns.data.nextdns.auth.NextDnsManagementSession
import com.eyalm.adns.data.nextdns.auth.NextDnsSessionManager
import com.eyalm.adns.data.nextdns.api.TrackerInfo
import com.eyalm.adns.data.nextdns.trackers.NextDnsTrackerRepository
import com.eyalm.adns.data.nextdns.profile.NextDnsProfileRepository
import com.eyalm.adns.domain.nextdns.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

sealed interface CardState {
    data object Loading : CardState
    data class ListData(val rows: List<StatRow>) : CardState
    data class PercentData(val percent: Float) : CardState
    data object Error : CardState
}

data class StatsUiState(
    val profileId: String? = null,
    val scope: AnalyticsScope = AnalyticsScope(),
    val graph: NextDnsStatsGraphResponse? = null,
    val cards: Map<String, CardState> = emptyMap(),
    val devices: List<NextDnsDeviceItem> = emptyList(),
    val selectedItem: StatRow? = null,
    val selectedTrackerInfo: TrackerInfo? = null,
    val trackerLoading: Boolean = false,
    val initialLoading: Boolean = false,
    val graphLoading: Boolean = false,
    val devicesLoading: Boolean = false,
    val refreshing: Boolean = false,
    val graphError: ApiResult<*>? = null,
)

class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val profileRepository = NextDnsProfileRepository(application)
    private val dnsRepository = DnsRepository(application)
    private val analyticsRepository = NextDnsAnalyticsRepository()
    private val trackerRepository = NextDnsTrackerRepository()
    private val sessionManager = NextDnsSessionManager.getInstance(application)

    private val _state = MutableStateFlow(StatsUiState())
    val state = _state.asStateFlow()

    // Both caches empty themselves when the active account changes and refuse late writes.
    private val graphCache = AccountScopedCache<AnalyticsScope, NextDnsStatsGraphResponse>(
        sessionManager.accountEpoch::value
    )
    private val cardCache = AccountScopedCache<AnalyticsScope, Map<String, CardState>>(
        sessionManager.accountEpoch::value
    )
    private var knownEpoch = sessionManager.accountEpoch.value
    private var scopeEpoch = knownEpoch
    private var scopeGeneration = 0L
    private var deviceGeneration = 0L
    private var scopeJob: Job? = null
    private var cardJob: Job? = null
    private var prefetchJob: Job? = null

    init {
        viewModelScope.launch {
            sessionManager.accountEpoch.collect { epoch ->
                if (epoch == knownEpoch) return@collect
                knownEpoch = epoch
                cancelRequests()
                graphCache.clear()
                cardCache.clear()
                _state.value = StatsUiState()
                syncWithSelection()
            }
        }
        viewModelScope.launch {
            // The profile is chosen per account. It changes when the user picks another profile
            // for the active account, not only when the device DNS setting changes.
            sessionManager.activeProfileId.collect { syncWithSelection() }
        }
        viewModelScope.launch {
            dnsRepository.getDnsUrlFlow().collectLatest { syncWithSelection() }
        }
        viewModelScope.launch {
            sessionManager.state.collect { session ->
                if (session == NextDnsManagementSession.Expired) {
                    cancelRequests()
                    graphCache.clear()
                    cardCache.clear()
                    _state.value = _state.value.copy(
                        graph = null,
                        cards = emptyMap(),
                        initialLoading = false,
                        graphLoading = false,
                        refreshing = false,
                        graphError = ApiResult.ServerFailure(
                            status = 401,
                            problems = emptyList(),
                        ),
                    )
                } else if (
                    session == NextDnsManagementSession.Active &&
                    (_state.value.graphError as? ApiResult.ServerFailure)?.status == 401
                ) {
                    val profileId = profileRepository.currentProfileId()
                    if (dnsRepository.currentSelection() is DnsProviderSelection.Enhanced && profileId != null) {
                        activateProfile(profileId)
                    }
                }
            }
        }
    }

    fun selectPeriod(period: AnalyticsPeriod) {
        if (!sessionManager.requestFeatureAccess()) return
        val current = _state.value
        if (current.scope.period == period || current.profileId == null) return
        loadScope(
            profileId = current.profileId,
            scope = current.scope.copy(period = period),
        )
    }

    fun selectDevice(deviceId: String?) {
        if (!sessionManager.requestFeatureAccess()) return
        val current = _state.value
        if (current.scope.deviceId == deviceId || current.profileId == null) return
        loadScope(
            profileId = current.profileId,
            scope = current.scope.copy(deviceId = deviceId),
        )
    }

    fun refresh() {
        if (!sessionManager.requestFeatureAccess()) return
        val current = _state.value
        val profileId = current.profileId ?: return
        loadDevices(profileId)
        loadScope(
            profileId = profileId,
            scope = current.scope,
            force = true,
            refreshing = true,
        )
    }

    fun selectItem(item: StatRow?) {
        _state.update { it.copy(selectedItem = item, selectedTrackerInfo = null, trackerLoading = item?.tracker != null) }
        val trackerId = item?.tracker ?: return
        viewModelScope.launch {
            when (val result = trackerRepository.getTrackerInfo(trackerId)) {
                is ApiResult.Success -> {
                    _state.update {
                        if (it.selectedItem?.id == item.id) {
                            it.copy(selectedTrackerInfo = result.value, trackerLoading = false)
                        } else it
                    }
                }
                else -> {
                    _state.update {
                        if (it.selectedItem?.id == item.id) {
                            it.copy(trackerLoading = false)
                        } else it
                    }
                }
            }
        }
    }

    /**
     * Shows the statistics of the active account's profile while NextDNS is the selected provider
     * and the session is active, and nothing otherwise.
     */
    private fun syncWithSelection() {
        val profileId = profileRepository.currentProfileId()
        if (
            dnsRepository.currentSelection() is DnsProviderSelection.Enhanced &&
            profileId != null &&
            sessionManager.state.value == NextDnsManagementSession.Active
        ) {
            activateProfile(profileId)
        } else {
            clearForUnavailableProvider()
        }
    }

    private fun activateProfile(profileId: String) {
        val current = _state.value
        if (current.profileId == profileId && (current.initialLoading || current.graph != null)) return

        cancelRequests()
        graphCache.clear()
        cardCache.clear()
        _state.value = StatsUiState(
            profileId = profileId,
            scope = AnalyticsScope(),
            initialLoading = true,
            graphLoading = true,
            devicesLoading = true,
        )
        loadDevices(profileId)
        loadScope(profileId, AnalyticsScope())
    }

    private fun loadDevices(profileId: String) {
        val generation = ++deviceGeneration
        val requestEpoch = sessionManager.accountEpoch.value
        _state.update { it.copy(devicesLoading = true) }
        viewModelScope.launch {
            when (val result = analyticsRepository.getDevices(profileId)) {
                is ApiResult.Success -> {
                    if (
                        requestEpoch != sessionManager.accountEpoch.value ||
                        !isCurrentProfile(profileId) ||
                        generation != deviceGeneration
                    ) return@launch
                    _state.update { current ->
                        current.copy(
                            devices = result.value,
                            devicesLoading = false,
                        )
                    }
                    val selectedDevice = _state.value.scope.deviceId
                    if (
                        selectedDevice != null &&
                        selectedDevice != "__UNIDENTIFIED__" &&
                        result.value.none { it.id == selectedDevice }
                    ) {
                        selectDevice(null)
                    }
                }

                else -> {
                    if (
                        requestEpoch == sessionManager.accountEpoch.value &&
                        isCurrentProfile(profileId) &&
                        generation == deviceGeneration
                    ) {
                        _state.update { it.copy(devicesLoading = false) }
                    }
                }
            }
        }
    }

    private fun loadScope(
        profileId: String,
        scope: AnalyticsScope,
        force: Boolean = false,
        refreshing: Boolean = false,
    ) {
        val generation = ++scopeGeneration
        scopeEpoch = sessionManager.accountEpoch.value
        scopeJob?.cancel()
        cardJob?.cancel()
        prefetchJob?.cancel()

        val cachedGraph = graphCache[scope]
        val cachedCards = cardCache[scope]
        _state.update { current ->
            current.copy(
                profileId = profileId,
                scope = scope,
                graph = cachedGraph ?: current.graph,
                cards = cachedCards ?: loadingCards(),
                initialLoading = cachedGraph == null && current.graph == null,
                graphLoading = cachedGraph == null || force,
                refreshing = refreshing,
                graphError = null,
            )
        }

        if (cachedGraph != null && !force) {
            if (cachedCards == null) loadCards(profileId, scope, generation)
            prefetchGraphs(profileId, scope, generation)
            return
        }

        scopeJob = viewModelScope.launch {
            when (val result = analyticsRepository.getGraph(profileId, scope)) {
                is ApiResult.Success -> {
                    if (!isCurrent(profileId, scope, generation)) return@launch
                    graphCache.put(scopeEpoch, scope, result.value)
                    _state.update {
                        it.copy(
                            graph = result.value,
                            initialLoading = false,
                            graphLoading = false,
                            refreshing = false,
                            graphError = null,
                        )
                    }
                    loadCards(profileId, scope, generation, force)
                    prefetchGraphs(profileId, scope, generation)
                }

                else -> {
                    if (!isCurrent(profileId, scope, generation)) return@launch
                    _state.update {
                        it.copy(
                            initialLoading = false,
                            graphLoading = false,
                            refreshing = false,
                            graphError = result,
                        )
                    }
                }
            }
        }
    }

    private fun loadCards(
        profileId: String,
        scope: AnalyticsScope,
        generation: Long,
        force: Boolean = false,
    ) {
        if (!force) {
            cardCache[scope]?.let { cached ->
                if (isCurrent(profileId, scope, generation)) {
                    _state.update { it.copy(cards = cached) }
                }
                return
            }
        }

        val initial = loadingCards()
        val epoch = scopeEpoch
        _state.update { it.copy(cards = initial) }
        cardJob = viewModelScope.launch {
            supervisorScope {
                StatsRegistry.cards.forEach { card ->
                    launch {
                        val result = analyticsRepository.getCardData(
                            profileId = profileId,
                            feature = card.feature,
                            baseParams = card.params,
                            scope = scope,
                        )
                        val cardState = if (result !is ApiResult.Success) {
                            CardState.Error
                        } else {
                            runCatching {
                                when (card) {
                                    is ListCard -> CardState.ListData(parseList(card, result.value))
                                    is PercentCard -> CardState.PercentData(
                                        parsePercent(card, result.value)
                                    )
                                }
                            }.getOrDefault(CardState.Error)
                        }

                        val currentCache = cardCache[scope] ?: initial
                        cardCache.put(epoch, scope, currentCache + (card.key to cardState))
                        if (isCurrent(profileId, scope, generation)) {
                            _state.update { current ->
                                current.copy(cards = current.cards + (card.key to cardState))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun prefetchGraphs(
        profileId: String,
        selectedScope: AnalyticsScope,
        generation: Long,
    ) {
        val epoch = scopeEpoch
        prefetchJob = viewModelScope.launch {
            AnalyticsPeriod.entries
                .filterNot { it == selectedScope.period }
                .forEach { period ->
                    if (!isCurrent(profileId, selectedScope, generation)) return@launch
                    val scope = selectedScope.copy(period = period)
                    if (scope in graphCache) return@forEach
                    when (val result = analyticsRepository.getGraph(profileId, scope)) {
                        is ApiResult.Success -> graphCache.put(epoch, scope, result.value)
                        else -> Unit
                    }
                }
        }
    }

    private fun loadingCards(): Map<String, CardState> =
        StatsRegistry.cards.associate { it.key to CardState.Loading }

    private fun isCurrent(
        profileId: String,
        scope: AnalyticsScope,
        generation: Long,
    ): Boolean =
        generation == scopeGeneration &&
            scopeEpoch == sessionManager.accountEpoch.value &&
            _state.value.profileId == profileId &&
            _state.value.scope == scope

    private fun isCurrentProfile(profileId: String): Boolean =
        _state.value.profileId == profileId

    private fun clearForUnavailableProvider() {
        cancelRequests()
        graphCache.clear()
        cardCache.clear()
        _state.value = StatsUiState()
    }

    private fun cancelRequests() {
        scopeGeneration++
        deviceGeneration++
        scopeJob?.cancel()
        cardJob?.cancel()
        prefetchJob?.cancel()
    }
}
