package com.eyalm.adns.viewmodel.nextdns

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eyalm.adns.R
import com.eyalm.adns.data.account.AccountEpoch
import com.eyalm.adns.data.nextdns.analytics.AnalyticsScope
import com.eyalm.adns.data.nextdns.analytics.AnalyticsSeriesResult
import com.eyalm.adns.data.nextdns.analytics.ListCard
import com.eyalm.adns.data.nextdns.analytics.NextDnsAnalyticsRepository
import com.eyalm.adns.data.nextdns.analytics.StatRow
import com.eyalm.adns.data.nextdns.analytics.mergeAnalyticsPage
import com.eyalm.adns.data.nextdns.api.TrackerInfo
import com.eyalm.adns.data.nextdns.auth.NextDnsManagementSession
import com.eyalm.adns.data.nextdns.auth.NextDnsSessionManager
import com.eyalm.adns.data.nextdns.trackers.NextDnsTrackerRepository
import com.eyalm.adns.domain.nextdns.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatsCardDetailUiState(
    val profileId: String? = null,
    val card: ListCard? = null,
    val scope: AnalyticsScope = AnalyticsScope(),
    val items: List<StatRow> = emptyList(),
    val series: AnalyticsSeriesResult = AnalyticsSeriesResult(),
    val enabledSeriesNames: Set<String>? = null,
    val selectedItem: StatRow? = null,
    val selectedTrackerInfo: TrackerInfo? = null,
    val trackerLoading: Boolean = false,
    val initialLoading: Boolean = false,
    val seriesLoading: Boolean = false,
    val loadingNextPage: Boolean = false,
    val refreshing: Boolean = false,
    val hasMorePages: Boolean = true,
    val error: ApiResult<*>? = null,
)

sealed interface StatsCardDetailEffect {
    data class Copy(val text: String) : StatsCardDetailEffect
    data class Message(val message: String) : StatsCardDetailEffect
}

fun isGraphSupported(card: ListCard?): Boolean {
    if (card == null) return false
    val f = card.feature.lowercase()
    return f == "devices" ||
           f == "ipversions" ||
           (f == "destinations" && card.params["type"] == "gafam") ||
           f == "querytypes" ||
           f == "protocols" ||
           f == "reasons"
}

class StatsCardDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val analyticsRepository = NextDnsAnalyticsRepository()
    private val trackerRepository = NextDnsTrackerRepository()
    private val sessionManager = NextDnsSessionManager.getInstance(application)

    private val _state = MutableStateFlow(StatsCardDetailUiState())
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<StatsCardDetailEffect>(extraBufferCapacity = 4)
    val effects = _effects.asSharedFlow()

    private var knownEpoch = sessionManager.accountEpoch.value

    // The account the current list and series were requested for.
    private var epoch: AccountEpoch = knownEpoch
    private var nextCursor: String? = null
    private var loadGeneration = 0L
    private var firstPageJob: Job? = null
    private var seriesJob: Job? = null

    init {
        viewModelScope.launch {
            sessionManager.accountEpoch.collect { current ->
                if (current == knownEpoch) return@collect
                knownEpoch = current
                resetForAccountChange()
            }
        }
        viewModelScope.launch {
            sessionManager.state.collect { session ->
                if (session == NextDnsManagementSession.Expired) {
                    val profileId = _state.value.profileId
                    val card = _state.value.card
                    val scope = _state.value.scope
                    cancelJobs()
                    nextCursor = null
                    _state.value = StatsCardDetailUiState(
                        profileId = profileId,
                        card = card,
                        scope = scope,
                        error = ApiResult.ServerFailure(status = 401, problems = emptyList())
                    )
                } else if (
                    session == NextDnsManagementSession.Active &&
                    (_state.value.error as? ApiResult.ServerFailure)?.status == 401
                ) {
                    val profileId = _state.value.profileId
                    val card = _state.value.card
                    val scope = _state.value.scope
                    if (profileId != null && card != null) {
                        load(profileId, card, scope, force = true)
                    }
                }
            }
        }
    }

    fun load(
        profileId: String,
        card: ListCard,
        scope: AnalyticsScope,
        force: Boolean = false,
    ) {
        val current = _state.value
        val configChanged = current.profileId != profileId || current.card?.key != card.key || current.scope != scope
        if (!force && !configChanged && (current.initialLoading || current.items.isNotEmpty())) return

        val generation = nextGeneration()
        nextCursor = null
        val graphAllowed = isGraphSupported(card)
        if (configChanged) {
            _state.value = StatsCardDetailUiState(
                profileId = profileId,
                card = card,
                scope = scope,
                initialLoading = true,
                seriesLoading = graphAllowed,
            )
        }

        if (graphAllowed) {
            loadSeries(profileId, card, scope, generation)
        }
        loadFirstPage(
            profileId = profileId,
            card = card,
            scope = scope,
            refreshing = force && !configChanged && _state.value.items.isNotEmpty(),
            generation = generation,
        )
    }

    fun refresh() {
        val profileId = _state.value.profileId ?: return
        val card = _state.value.card ?: return
        val scope = _state.value.scope
        val generation = nextGeneration()
        nextCursor = null
        if (isGraphSupported(card)) {
            loadSeries(profileId, card, scope, generation)
        }
        loadFirstPage(
            profileId = profileId,
            card = card,
            scope = scope,
            refreshing = _state.value.items.isNotEmpty(),
            generation = generation,
        )
    }

    fun selectItem(item: StatRow?) {
        if (item == null) {
            _state.update { it.copy(selectedItem = null, selectedTrackerInfo = null, trackerLoading = false) }
            return
        }
        _state.update { it.copy(selectedItem = item, selectedTrackerInfo = null, trackerLoading = !item.tracker.isNullOrBlank()) }
        val trackerId = item.tracker
        if (!trackerId.isNullOrBlank()) {
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
    }

    fun toggleItemGraph(itemName: String) {
        val currentEnabled = _state.value.enabledSeriesNames
            ?: _state.value.series.seriesList.take(3).map { it.name }.toSet()
        if (currentEnabled.contains(itemName)) {
            _state.update { it.copy(enabledSeriesNames = currentEnabled - itemName) }
        } else {
            if (currentEnabled.size >= 6) {
                _effects.tryEmit(
                    StatsCardDetailEffect.Message(
                        getApplication<Application>().getString(R.string.max_graph_items_reached)
                    )
                )
                return
            }
            _state.update { it.copy(enabledSeriesNames = currentEnabled + itemName) }
        }
    }

    fun fetchNextPage() {
        val current = _state.value
        val profileId = current.profileId ?: return
        val card = current.card ?: return
        val cursor = nextCursor ?: return
        if (current.loadingNextPage || current.initialLoading || !current.hasMorePages) return

        val generation = loadGeneration
        val scope = current.scope
        _state.update { it.copy(loadingNextPage = true) }

        viewModelScope.launch {
            when (
                val result = analyticsRepository.getExpandedCardData(
                    profileId = profileId,
                    card = card,
                    scope = scope,
                    cursor = cursor,
                    limit = 100,
                )
            ) {
                is ApiResult.Success -> {
                    if (!isCurrent(profileId, card.key, scope, generation)) return@launch
                    val merged = mergeAnalyticsPage(
                        shown = _state.value.items,
                        page = result.value.rows,
                        requestedCursor = cursor,
                        returnedCursor = result.value.cursor,
                    )
                    nextCursor = merged.nextCursor
                    _state.update {
                        it.copy(
                            items = merged.rows,
                            loadingNextPage = false,
                            hasMorePages = nextCursor != null,
                        )
                    }
                }

                else -> {
                    if (!isCurrent(profileId, card.key, scope, generation)) return@launch
                    _state.update { it.copy(loadingNextPage = false, error = result) }
                    emitLoadFailure()
                }
            }
        }
    }

    fun copyText(text: String) {
        _effects.tryEmit(StatsCardDetailEffect.Copy(text))
    }

    private fun loadSeries(
        profileId: String,
        card: ListCard,
        scope: AnalyticsScope,
        generation: Long,
    ) {
        seriesJob?.cancel()
        _state.update { it.copy(seriesLoading = true) }

        seriesJob = viewModelScope.launch {
            when (
                val result = analyticsRepository.getCardSeries(
                    profileId = profileId,
                    card = card,
                    scope = scope,
                )
            ) {
                is ApiResult.Success -> {
                    if (!isCurrent(profileId, card.key, scope, generation)) return@launch
                    val defaultEnabled = result.value.seriesList.take(3).map { it.name }.toSet()
                    _state.update {
                        it.copy(
                            series = result.value,
                            seriesLoading = false,
                            enabledSeriesNames = it.enabledSeriesNames ?: defaultEnabled,
                        )
                    }
                }

                else -> {
                    if (!isCurrent(profileId, card.key, scope, generation)) return@launch
                    _state.update { it.copy(seriesLoading = false) }
                }
            }
        }
    }

    private fun loadFirstPage(
        profileId: String,
        card: ListCard,
        scope: AnalyticsScope,
        refreshing: Boolean,
        generation: Long,
    ) {
        firstPageJob?.cancel()
        _state.update {
            it.copy(
                initialLoading = !refreshing,
                refreshing = refreshing,
                loadingNextPage = false,
                error = null,
            )
        }

        firstPageJob = viewModelScope.launch {
            when (
                val result = analyticsRepository.getExpandedCardData(
                    profileId = profileId,
                    card = card,
                    scope = scope,
                    cursor = null,
                    limit = 100,
                )
            ) {
                is ApiResult.Success -> {
                    if (!isCurrent(profileId, card.key, scope, generation)) return@launch
                    val merged = mergeAnalyticsPage(
                        shown = emptyList(),
                        page = result.value.rows,
                        requestedCursor = null,
                        returnedCursor = result.value.cursor,
                    )
                    nextCursor = merged.nextCursor
                    _state.update {
                        it.copy(
                            items = merged.rows,
                            initialLoading = false,
                            refreshing = false,
                            hasMorePages = nextCursor != null,
                        )
                    }
                }

                else -> {
                    if (!isCurrent(profileId, card.key, scope, generation)) return@launch
                    _state.update {
                        it.copy(
                            initialLoading = false,
                            refreshing = false,
                            error = result,
                        )
                    }
                    emitLoadFailure()
                }
            }
        }
    }

    private fun nextGeneration(): Long {
        epoch = sessionManager.accountEpoch.value
        return ++loadGeneration
    }

    /** Drops everything loaded for the previous account and, if possible, loads it again. */
    private fun resetForAccountChange() {
        val profileId = _state.value.profileId
        val card = _state.value.card
        val scope = _state.value.scope
        cancelJobs()
        nextCursor = null
        _state.value = StatsCardDetailUiState(profileId = profileId, card = card, scope = scope)
        if (
            profileId != null &&
            card != null &&
            sessionManager.state.value == NextDnsManagementSession.Active
        ) {
            load(profileId, card, scope, force = true)
        }
    }

    private fun isCurrent(
        profileId: String,
        cardKey: String,
        scope: AnalyticsScope,
        generation: Long,
    ): Boolean =
        generation == loadGeneration &&
            epoch == sessionManager.accountEpoch.value &&
            _state.value.profileId == profileId &&
            _state.value.card?.key == cardKey &&
            _state.value.scope == scope

    private fun cancelJobs() {
        loadGeneration++
        firstPageJob?.cancel()
        seriesJob?.cancel()
    }

    private suspend fun emitLoadFailure() {
        _effects.emit(
            StatsCardDetailEffect.Message(
                getApplication<Application>().getString(
                    R.string.cannot_load_stats
                )
            )
        )
    }
}
