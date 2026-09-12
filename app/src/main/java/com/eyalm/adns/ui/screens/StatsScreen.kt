package com.eyalm.adns.ui.screens


import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.icu.text.NumberFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eyalm.adns.R
import com.eyalm.adns.data.Locales
import com.eyalm.adns.data.nextdns.analytics.AnalyticsPeriod
import com.eyalm.adns.data.nextdns.analytics.ListCard
import com.eyalm.adns.data.nextdns.analytics.PercentCard
import com.eyalm.adns.data.nextdns.analytics.StatsRegistry
import com.eyalm.adns.data.nextdns.api.NextDnsDeviceItem
import com.eyalm.adns.ui.components.GenericStatsListCard
import com.eyalm.adns.ui.components.GenericStatsPercentCard
import com.eyalm.adns.ui.components.StatItemDetailsBottomSheet
import com.eyalm.adns.ui.components.WavyLineChart
import com.eyalm.adns.ui.components.refresh.AdnsPullToRefresh
import com.eyalm.adns.viewmodel.nextdns.CardState
import com.eyalm.adns.viewmodel.nextdns.StatsViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatsScreen(
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier,
    statsViewModel: StatsViewModel = viewModel(),
    scrollState: LazyListState = rememberLazyListState(),
    onScrollVisibilityChange: (Boolean) -> Unit = {},
    onExpandedChange: (Boolean) -> Unit = {},
) {
    val uiState by statsViewModel.state.collectAsState()
    var selectedExpandedCard by remember { mutableStateOf<ListCard?>(null) }

    LaunchedEffect(selectedExpandedCard) {
        onExpandedChange(selectedExpandedCard != null)
    }

    BackHandler(enabled = selectedExpandedCard != null) {
        selectedExpandedCard = null
    }

    AnimatedContent(
        targetState = selectedExpandedCard,
        transitionSpec = {
            if (targetState != null) {
                (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                    scaleIn(initialScale = 0.92f, animationSpec = tween(300)) +
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        initialOffset = { it / 8 }
                    )) togetherWith
                    (fadeOut(animationSpec = tween(90)) +
                        scaleOut(targetScale = 1.08f, animationSpec = tween(300)))
            } else {
                (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                    scaleIn(initialScale = 1.08f, animationSpec = tween(300))) togetherWith
                    (fadeOut(animationSpec = tween(90)) +
                        scaleOut(targetScale = 0.92f, animationSpec = tween(300)) +
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Down,
                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                            targetOffset = { it / 8 }
                        ))
            }.using(SizeTransform(clip = false))
        },
        label = "StatsScreenDetailTransition",
        modifier = modifier
    ) { expandedCard ->
        if (expandedCard != null) {
            StatsCardDetailScreen(
                profileId = uiState.profileId ?: "",
                card = expandedCard,
                scope = uiState.scope,
                onBack = { selectedExpandedCard = null },
                innerPadding = innerPadding,
                onScrollVisibilityChange = onScrollVisibilityChange,
            )
        } else {
            StatsOverviewContent(
                innerPadding = innerPadding,
                uiState = uiState,
                statsViewModel = statsViewModel,
                onCardClick = { card -> selectedExpandedCard = card },
                onScrollVisibilityChange = onScrollVisibilityChange,
                scrollState = scrollState,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatsOverviewContent(
    innerPadding: PaddingValues,
    uiState: com.eyalm.adns.viewmodel.nextdns.StatsUiState,
    statsViewModel: StatsViewModel,
    onCardClick: (ListCard) -> Unit,
    onScrollVisibilityChange: (Boolean) -> Unit,
    scrollState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.copied_to_clipboard)
    val nestedScrollConnection = remember(onScrollVisibilityChange) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < -4f) {
                    onScrollVisibilityChange(false)
                } else if (delta > 4f) {
                    onScrollVisibilityChange(true)
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.firstVisibleItemIndex == 0 && scrollState.firstVisibleItemScrollOffset == 0 }
            .collect { isAtTop ->
                if (isAtTop) onScrollVisibilityChange(true)
            }
    }

    val filterOptions = remember {
        AnalyticsPeriod.entries.map { period ->
            Locales.getString("timeRangeSelector", "ranges", period.localeKey) to period
        }
    }
    val stats = uiState.graph
    val isRefreshing = uiState.refreshing
    val cardStates = uiState.cards
    AdnsPullToRefresh(
        refreshing = isRefreshing,
        onRefresh = statsViewModel::refresh,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(top = innerPadding.calculateTopPadding())
                .statusBarsPadding()
        ) {
            if (uiState.initialLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularWavyProgressIndicator(modifier = Modifier.size(64.dp))
                }
            } else if (stats == null && uiState.graphError != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.cannot_load_stats))
                }
            } else {
                val allSeries = stats?.data ?: emptyList()
                val blockedSeries =
                    allSeries.firstOrNull { it.status == "blocked" }?.queries
                        ?: emptyList()

                val size = allSeries.maxOfOrNull { it.queries.size } ?: 0
                val totalPoints =
                    (0 until size).map { i -> allSeries.sumOf { it.queries.getOrElse(i) { 0 } }.toFloat() }
                val blockedQueriesSum = blockedSeries.sum()
                val blockedPoints = if (blockedQueriesSum > 0) {
                    (0 until size).map { i -> blockedSeries.getOrElse(i) { 0 }.toFloat() }
                } else {
                    emptyList()
                }
                val maxQueries = (totalPoints.maxOrNull() ?: 1f).coerceAtLeast(1f)

                val totalQueriesSum = allSeries.sumOf { it.queries.sum() }
                val blockedPercent =
                    if (totalQueriesSum > 0) (blockedQueriesSum.toFloat() / totalQueriesSum * 100).toInt() else 0

                LazyColumn(
                    state = scrollState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .nestedScroll(nestedScrollConnection),
                    contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    item {
                        TotalQueriesCard(
                            totalCount = if (uiState.graphLoading && stats == null) null else formatInteger(totalQueriesSum),
                            blockedCount = if (uiState.graphLoading && stats == null) null else stringResource(R.string.blocked, formatInteger(blockedQueriesSum), blockedPercent),
                            totalQueriesPoints = totalPoints,
                            blockedQueriesPoints = blockedPoints,
                            maxQueries = maxQueries,
                            filterOptions = filterOptions.map { it.first },
                            selectedFilter = filterOptions
                                .first { it.second == uiState.scope.period }
                                .first,
                            onFilterSelected = { label ->
                                filterOptions.firstOrNull { it.first == label }
                                    ?.second
                                    ?.let(statsViewModel::selectPeriod)
                            },
                            devices = uiState.devices,
                            selectedDeviceId = uiState.scope.deviceId,
                            onDeviceSelected = statsViewModel::selectDevice,
                            isLoading = uiState.graphLoading
                        )
                    }
                    items(StatsRegistry.cards, key = { it.key }) { card ->
                        val state = cardStates[card.key] ?: CardState.Loading
                        when (card) {
                            is ListCard -> GenericStatsListCard(
                                card = card,
                                state = state,
                                onItemClick = { row -> statsViewModel.selectItem(row) },
                                onClick = { onCardClick(card) }
                            )
                            is PercentCard -> GenericStatsPercentCard(card, state)
                        }
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }

    uiState.selectedItem?.let { item ->
        StatItemDetailsBottomSheet(
            item = item,
            trackerInfo = uiState.selectedTrackerInfo,
            trackerLoading = uiState.trackerLoading,
            canToggleGraph = false,
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("stat_value", it))
                Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
            },
            onDismissRequest = { statsViewModel.selectItem(null) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TotalQueriesCard(
    totalCount: String?,
    blockedCount: String?,
    totalQueriesPoints: List<Float>,
    blockedQueriesPoints: List<Float>,
    maxQueries: Float,
    filterOptions: List<String>,
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    devices: List<NextDnsDeviceItem>,
    selectedDeviceId: String?,
    onDeviceSelected: (String?) -> Unit,
    isLoading: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.total_queries),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = totalCount ?: "—",
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 42.sp, fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.primary.copy(alpha = if (totalCount == null) 0.5f else 1f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = if (blockedCount == null) 0.3f else 0.7f),
                shape = CircleShape
            ) {
                Text(
                    text = blockedCount ?: "—",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = if (blockedCount == null) 0.5f else 1f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Box(modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
            ) {
                if (totalQueriesPoints.size >= 2) {
                    WavyLineChart(
                        points = totalQueriesPoints,
                        lineColor = MaterialTheme.colorScheme.primary.copy(alpha = if (isLoading) 0.5f else 1f),
                        strokeWidth = 5.dp,
                        maxY = maxQueries,
                        modifier = Modifier.fillMaxSize()
                    )
                    WavyLineChart(
                        points = blockedQueriesPoints,
                        lineColor = MaterialTheme.colorScheme.error.copy(alpha = if (isLoading) 0.5f else 1f),
                        strokeWidth = 5.dp,
                        maxY = maxQueries,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (isLoading) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(40.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            var expanded by remember { mutableStateOf(false) }
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val padding = 24.dp.roundToPx()
                        val maxWidth = constraints.maxWidth + padding * 2
                        val placeable = measurable.measure(
                            constraints.copy(maxWidth = maxWidth, minWidth = maxWidth)
                        )
                        layout(constraints.maxWidth, placeable.height) {
                            placeable.placeRelative(-padding, 0)
                        }
                    },
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            )  {
                item {
                    Box {
                        Button(onClick = { expanded = true }) {
                            Text(selectedFilter, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            filterOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        expanded = false
                                        onFilterSelected(option)
                                    },
                                )
                            }
                        }
                    }
                }
                item {
                    AnalyticsDeviceSelector(
                        devices = devices,
                        selectedDeviceId = selectedDeviceId,
                        onSelected = onDeviceSelected,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalyticsDeviceSelector(
    devices: List<NextDnsDeviceItem>,
    selectedDeviceId: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = when (selectedDeviceId) {
        null -> Locales.getString("deviceSelector", "all")
        "__UNIDENTIFIED__" -> Locales.getString(
            "analytics",
            "devices",
            "unidentified",
            "name",
        )
        else -> devices.firstOrNull { it.id == selectedDeviceId }?.name ?: selectedDeviceId
    }
    Box(modifier = modifier) {
        Button(
            onClick = { expanded = true },
        ) {
            Text(selectedName, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(Locales.getString("deviceSelector", "all")) },
                onClick = {
                    expanded = false
                    onSelected(null)
                },
            )
            devices.filterNot { it.id == "__UNIDENTIFIED__" }.forEach { device ->
                DropdownMenuItem(
                    text = { Text(device.name ?: device.id) },
                    onClick = {
                        expanded = false
                        onSelected(device.id)
                    },
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        Locales.getString(
                            "analytics",
                            "devices",
                            "unidentified",
                            "name",
                        )
                    )
                },
                onClick = {
                    expanded = false
                    onSelected("__UNIDENTIFIED__")
                },
            )
        }
    }
}

private fun formatInteger(number: Int): String {
    return NumberFormat.getNumberInstance(Locale.US).format(number)
}

