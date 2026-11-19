package com.eyalm.adns.viewmodel.nextdns

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eyalm.adns.R
import com.eyalm.adns.data.account.AccountEpoch
import com.eyalm.adns.data.nextdns.api.NextDnsDeviceItem
import com.eyalm.adns.data.nextdns.api.NextDnsLogEntry
import com.eyalm.adns.data.nextdns.analytics.NextDnsAnalyticsRepository
import com.eyalm.adns.data.nextdns.auth.NextDnsManagementSession
import com.eyalm.adns.data.nextdns.auth.NextDnsSessionManager
import com.eyalm.adns.data.nextdns.logs.DomainRuleList
import com.eyalm.adns.data.nextdns.logs.DomainRuleResult
import com.eyalm.adns.data.nextdns.logs.LogsQuery
import com.eyalm.adns.data.nextdns.logs.NextDnsLogsRepository
import com.eyalm.adns.domain.nextdns.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

data class PendingLogAction(
    val domain: String,
    val list: DomainRuleList,
)

data class LogsUiState(
    val profileId: String? = null,
    val query: LogsQuery = LogsQuery(),
    val items: List<NextDnsLogEntry> = emptyList(),
    val devices: List<NextDnsDeviceItem> = emptyList(),
    val initialLoading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingNextPage: Boolean = false,
    val hasMorePages: Boolean = true,
    val pendingActions: Set<PendingLogAction> = emptySet(),
    val error: ApiResult<*>? = null,
)

sealed interface LogsEffect {
    data class CopyDomain(val domain: String) : LogsEffect
    data class Message(val value: String) : LogsEffect
}

class LogsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = NextDnsLogsRepository()
    private val analyticsRepository = NextDnsAnalyticsRepository()
    private val sessionManager = NextDnsSessionManager.getInstance(application)

    private val _state = MutableStateFlow(LogsUiState())
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<LogsEffect>(extraBufferCapacity = 4)
    val effects = _effects.asSharedFlow()

    private var nextCursor: String? = null
    private var loadGeneration = 0L
    private var firstPageJob: Job? = null
    private var searchJob: Job? = null
    private var knownEpoch = sessionManager.accountEpoch.value

    // The account the current list was requested for.
    private var epoch: AccountEpoch = knownEpoch

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
                    loadGeneration++
                    firstPageJob?.cancel()
                    searchJob?.cancel()
                    nextCursor = null
                    _state.value = LogsUiState(
                        profileId = profileId,
                        error = ApiResult.ServerFailure(
                            status = 401,
                            problems = emptyList(),
                        ),
                    )
                } else if (
                    session == NextDnsManagementSession.Active &&
                    (_state.value.error as? ApiResult.ServerFailure)?.status == 401
                ) {
                    _state.value.profileId?.let { load(it, force = true) }
                }
            }
        }
    }

    fun load(profileId: String, force: Boolean = false) {
        val current = _state.value
        val profileChanged = current.profileId != profileId
        if (!force && !profileChanged && (current.initialLoading || current.items.isNotEmpty())) return

        if (profileChanged) {
            loadGeneration++
            nextCursor = null
            _state.value = LogsUiState(profileId = profileId)
        }
        loadDevices(profileId)
        loadFirstPage(
            refreshing = force && !profileChanged && _state.value.items.isNotEmpty()
        )
    }

    fun refresh() {
        val profileId = _state.value.profileId ?: return
        loadDevices(profileId)
        loadFirstPage(refreshing = _state.value.items.isNotEmpty())
    }

    fun updateSearchQuery(query: String) {
        updateQuery(_state.value.query.copy(search = query), loadImmediately = false)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(500.milliseconds)
            loadFirstPage()
        }
    }

    fun setRaw(enabled: Boolean) {
        updateQuery(_state.value.query.copy(raw = enabled))
    }

    fun setDevice(deviceId: String?) {
        updateQuery(_state.value.query.copy(deviceId = deviceId))
    }

    fun setBlocked(blocked: Boolean) {
        updateQuery(_state.value.query.copy(blockedOnly = blocked))
    }

    fun fetchNextPage() {
        val current = _state.value
        val profileId = current.profileId ?: return
        val cursor = nextCursor ?: return
        if (current.loadingNextPage || current.initialLoading || !current.hasMorePages) return
        val generation = loadGeneration
        val query = current.query
        _state.update { it.copy(loadingNextPage = true) }

        viewModelScope.launch {
            when (val result = repository.getLogs(profileId, query, cursor)) {
                is ApiResult.Success -> {
                    if (!isCurrent(profileId, query, generation)) return@launch
                    nextCursor = result.value.meta?.pagination?.cursor
                    _state.update {
                        it.copy(
                            items = it.items + result.value.data,
                            loadingNextPage = false,
                            hasMorePages = nextCursor != null,
                        )
                    }
                }

                else -> {
                    if (!isCurrent(profileId, query, generation)) return@launch
                    _state.update { it.copy(loadingNextPage = false, error = result) }
                    emitLoadFailure()
                }
            }
        }
    }

    fun applyRule(
        list: DomainRuleList,
        domain: String,
        canEdit: Boolean,
    ) {
        val profileId = _state.value.profileId ?: return
        if (!canEdit) return
        val action = PendingLogAction(domain, list)
        if (action in _state.value.pendingActions) return
        _state.update { it.copy(pendingActions = it.pendingActions + action) }

        val requestEpoch = sessionManager.accountEpoch.value
        viewModelScope.launch {
            val result = repository.applyRule(profileId, list, domain)
            if (
                requestEpoch != sessionManager.accountEpoch.value ||
                _state.value.profileId != profileId
            ) return@launch
            _state.update { it.copy(pendingActions = it.pendingActions - action) }
            val listName = getApplication<Application>().getString(
                if (list == DomainRuleList.Allow) R.string.allowlist else R.string.denylist
            )
            val message = when (result) {
                DomainRuleResult.Added -> getApplication<Application>().getString(
                    R.string.added_to,
                    domain,
                    listName,
                )

                DomainRuleResult.Activated -> getApplication<Application>().getString(
                    R.string.activated_in,
                    domain,
                    listName,
                )

                is DomainRuleResult.Failure -> getApplication<Application>().getString(
                    R.string.failed_to_add_domain_safe,
                    domain,
                )
            }
            _effects.emit(LogsEffect.Message(message))
        }
    }

    fun copyDomain(domain: String) {
        _effects.tryEmit(LogsEffect.CopyDomain(domain))
    }

    private fun updateQuery(query: LogsQuery, loadImmediately: Boolean = true) {
        if (_state.value.query == query) return
        _state.update { it.copy(query = query) }
        if (loadImmediately) loadFirstPage()
    }

    private fun loadFirstPage(refreshing: Boolean = false) {
        val current = _state.value
        val profileId = current.profileId ?: return
        val query = current.query
        val generation = ++loadGeneration
        epoch = sessionManager.accountEpoch.value
        nextCursor = null
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
            when (val result = repository.getLogs(profileId, query)) {
                is ApiResult.Success -> {
                    if (!isCurrent(profileId, query, generation)) return@launch
                    nextCursor = result.value.meta?.pagination?.cursor
                    _state.update {
                        it.copy(
                            items = result.value.data,
                            initialLoading = false,
                            refreshing = false,
                            hasMorePages = nextCursor != null,
                        )
                    }
                }

                else -> {
                    if (!isCurrent(profileId, query, generation)) return@launch
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

    private fun loadDevices(profileId: String) {
        val requestEpoch = sessionManager.accountEpoch.value
        viewModelScope.launch {
            when (val result = analyticsRepository.getDevices(profileId)) {
                is ApiResult.Success -> {
                    if (
                        requestEpoch == sessionManager.accountEpoch.value &&
                        _state.value.profileId == profileId
                    ) {
                        _state.update { it.copy(devices = result.value) }
                    }
                }

                else -> Unit
            }
        }
    }

    /**
     * Drops the log list, the device list and the filters of the previous account, and loads the
     * same profile again for the new one. Filters are cleared because a device id from one
     * account means nothing in another.
     */
    private fun resetForAccountChange() {
        val profileId = _state.value.profileId
        loadGeneration++
        firstPageJob?.cancel()
        searchJob?.cancel()
        nextCursor = null
        _state.value = LogsUiState(profileId = profileId)
        if (profileId != null && sessionManager.state.value == NextDnsManagementSession.Active) {
            load(profileId, force = true)
        }
    }

    private fun isCurrent(
        profileId: String,
        query: LogsQuery,
        generation: Long,
    ): Boolean =
        generation == loadGeneration &&
            epoch == sessionManager.accountEpoch.value &&
            _state.value.profileId == profileId &&
            _state.value.query == query

    private suspend fun emitLoadFailure() {
        _effects.emit(
            LogsEffect.Message(
                getApplication<Application>().getString(
                    R.string.failed_to_load_logs_check_your_connection
                )
            )
        )
    }
}
