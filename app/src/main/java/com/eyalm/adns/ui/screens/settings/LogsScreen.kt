package com.eyalm.adns.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RawOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eyalm.adns.R
import com.eyalm.adns.data.Locales
import com.eyalm.adns.data.nextdns.api.NextDnsLogEntry
import com.eyalm.adns.data.nextdns.api.TrackerInfo
import com.eyalm.adns.data.nextdns.logs.DomainRuleList
import com.eyalm.adns.data.nextdns.model.ListIcon
import com.eyalm.adns.data.nextdns.model.nextDnsFaviconUrl
import com.eyalm.adns.data.nextdns.trackers.NextDnsTrackerRepository
import com.eyalm.adns.domain.nextdns.ApiResult
import com.eyalm.adns.ui.components.BottomSheetDetailRow
import com.eyalm.adns.ui.components.DomainTitleText
import com.eyalm.adns.ui.components.ExpressiveCard
import com.eyalm.adns.ui.components.ExpressiveIcon
import com.eyalm.adns.ui.components.ListIconView
import com.eyalm.adns.ui.components.NavigationSettingRow
import com.eyalm.adns.ui.components.ResourceSettingRow
import com.eyalm.adns.ui.components.SegmentPosition
import com.eyalm.adns.ui.components.ToggleSettingRow
import com.eyalm.adns.ui.components.TrackerInsightsCard
import com.eyalm.adns.ui.components.segmentPosition
import com.eyalm.adns.viewmodel.ProfileSessionState
import com.eyalm.adns.viewmodel.nextdns.LogsEffect
import com.eyalm.adns.viewmodel.nextdns.LogsViewModel
import com.eyalm.adns.viewmodel.nextdns.PendingLogAction
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onBack: (() -> Unit)? = null,
    profileState: ProfileSessionState,
    innerPadding: PaddingValues = PaddingValues(0.dp),
    onScrollVisibilityChange: (Boolean) -> Unit = {},
) {
    val profileId = profileState.selectedProfileId ?: return
    val viewModel: LogsViewModel = viewModel(key = "logs-$profileId")
    val context = LocalContext.current
    val copiedToClipboardMessage = stringResource(R.string.copied_to_clipboard)
    val state by viewModel.state.collectAsState()
    val items = state.items
    val devices = state.devices

    var showConfig by remember(profileId) { mutableStateOf(true) }
    var selectedLog by remember(profileId) { mutableStateOf<NextDnsLogEntry?>(null) }

    LaunchedEffect(profileId, profileState.logsRevision) {
        viewModel.load(profileId, force = profileState.logsRevision > 0)
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LogsEffect.CopyDomain -> {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("domain", effect.domain))
                    Toast.makeText(
                        context,
                        copiedToClipboardMessage,
                        Toast.LENGTH_SHORT,
                    ).show()
                }

                is LogsEffect.Message -> {
                    Toast.makeText(context, effect.value, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    SettingsScreenScaffold(
        onBack = onBack,
        title = stringResource(R.string.logs),
        refreshing = state.refreshing,
        onRefresh = viewModel::refresh,
        innerPadding = innerPadding,
        onScrollVisibilityChange = onScrollVisibilityChange,
        showTopBar = false
    ) {
        item {
            Row(
                verticalAlignment = CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                TextField(
                    value = state.query.search,
                    onValueChange = {
                        viewModel.updateSearchQuery(it)
                    },
                    placeholder = { Text(stringResource(R.string.search)) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))
                ExpressiveIcon(
                    icon = Icons.Default.Settings,
                    selected = showConfig,
                    bgcolor = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .clickable(
                            onClick = { showConfig = !showConfig },
                            role = Role.Button
                        )
                )
            }
        }

        if (showConfig) {
            item {
                Spacer(Modifier.height(8.dp))
                ToggleSettingRow(
                    title = stringResource(R.string.blocked_only),
                    description = stringResource(R.string.show_only_blocked_items),
                    checked = state.query.blockedOnly,
                    leading = { ExpressiveIcon(Icons.Filled.Block) },
                    toggle = { checked, onCheckedChange ->
                        Switch(
                            checked = checked,
                            onCheckedChange = onCheckedChange,
                        )
                    },
                    onCheckedChange = viewModel::setBlocked,
                    position = SegmentPosition.First,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            item {
                ToggleSettingRow(
                    title = stringResource(R.string.raw_mode),
                    description = stringResource(R.string.show_raw_dns_logs),
                    checked = state.query.raw,
                    leading = { ExpressiveIcon(Icons.Filled.RawOn) },
                    toggle = { checked, onCheckedChange ->
                        Switch(
                            checked = checked,
                            onCheckedChange = onCheckedChange,
                        )
                    },
                    onCheckedChange = viewModel::setRaw,
                    position = SegmentPosition.Middle,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            item {
                var expanded by remember { mutableStateOf(false) }
                val selectedDeviceName = when (state.query.deviceId) {
                    null -> stringResource(R.string.all_devices)
                    "__UNIDENTIFIED__" -> stringResource(R.string.unknown_devices)
                    else -> devices.find { it.id == state.query.deviceId }?.name ?: state.query.deviceId!!
                }

                NavigationSettingRow(
                    onClick = {
                        expanded = true
                    },
                    title = selectedDeviceName,
                    description = stringResource(R.string.filter_the_logs_to_a_certain_device),
                    leading = { ExpressiveIcon(Icons.Filled.Devices) },
                    trailing = {
                        Box(
                            modifier = Modifier
                                .wrapContentSize(Alignment.TopEnd)
                        ) {
                            IconButton(onClick = { expanded = true }) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            }

                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.all_devices)) },
                                    onClick = {
                                        expanded = false
                                        viewModel.setDevice(null)
                                    }
                                )
                                devices.filterNot { it.id == "__UNIDENTIFIED__" }.forEach { deviceItem ->
                                    DropdownMenuItem(
                                        text = { Text(deviceItem.name ?: deviceItem.id) },
                                        onClick = {
                                            expanded = false
                                            viewModel.setDevice(deviceItem.id)
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.unknown_devices)) },
                                    onClick = {
                                        expanded = false
                                        viewModel.setDevice("__UNIDENTIFIED__")
                                    }
                                )
                            }
                        }
                    },
                    position = SegmentPosition.Last,
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }

        if (state.initialLoading && items.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (items.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_logs_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(items.size) { index ->
                val log = items[index]
                val domain = log.domain?.trim()?.takeIf(String::isNotEmpty)
                LaunchedEffect(state.query, index, items.size) {
                    if (index >= items.size - 5) {
                        viewModel.fetchNextPage()
                    }
                }
                ResourceSettingRow(
                    title = domain ?: stringResource(R.string.domain_hidden),
                    titleContent = domain?.let { availableDomain ->
                        {
                            DomainTitleText(
                                domain = availableDomain,
                                root = log.root,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    },
                    description = formatRelativeTime(context, log.timestamp),
                    position = segmentPosition(index, items.size),
                    indicatorColor = if (log.status == "blocked") MaterialTheme.colorScheme.error else null,
                    leading = {
                        ListIconView(
                            icon = domain
                                ?.let(::nextDnsFaviconUrl)
                                ?.let(ListIcon::Url)
                                ?: ListIcon.None,
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    onClick = {
                        selectedLog = log
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (state.loadingNextPage) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    }

    selectedLog?.let { log ->
        LogDetailsBottomSheet(
            log = log,
            canEdit = profileState.capabilities.canEditSettings,
            pendingActions = state.pendingActions,
            isRawMode = state.query.raw,
            onDismissRequest = { selectedLog = null },
            onApplyRule = { rule, domain ->
                viewModel.applyRule(rule, domain, canEdit = true)
            },
            onCopyDomain = { domain ->
                viewModel.copyDomain(domain)
            }
        )
    }
}

fun parseUtcInstant(timestamp: String): Instant? {
    return try {
        Instant.parse(timestamp)
    } catch (_: Exception) {
        try {
            ZonedDateTime.parse(timestamp).toInstant()
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(timestamp).toInstant()
            } catch (_: Exception) {
                try {
                    LocalDateTime.parse(timestamp).toInstant(ZoneOffset.UTC)
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}


fun formatRelativeTime(context: Context, timestamp: String, nowMilli: Long = System.currentTimeMillis()): String {
    val instant = parseUtcInstant(timestamp) ?: return timestamp
    val epochMilli = instant.toEpochMilli()
    val diff = nowMilli - epochMilli

    return when {
        diff < 5_000L -> context.getString(R.string.just_now)
        diff < 60_000L -> context.getString(R.string.a_few_seconds_ago)
        else -> DateUtils.getRelativeTimeSpanString(
            epochMilli,
            nowMilli,
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDetailsBottomSheet(
    log: NextDnsLogEntry,
    canEdit: Boolean,
    pendingActions: Set<PendingLogAction>,
    isRawMode: Boolean,
    onDismissRequest: () -> Unit,
    onApplyRule: (DomainRuleList, String) -> Unit,
    onCopyDomain: (String) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    val scrollState = rememberScrollState()
    val domain = log.domain?.trim()?.takeIf(String::isNotEmpty)
    val relativeTime = formatRelativeTime(context, log.timestamp)

    var trackerInfo by remember(log.tracker) { mutableStateOf<TrackerInfo?>(null) }
    var trackerLoading by remember(log.tracker) { mutableStateOf(!log.tracker.isNullOrBlank()) }
    val trackerRepository = remember { NextDnsTrackerRepository() }

    LaunchedEffect(log.tracker) {
        val tId = log.tracker
        if (!tId.isNullOrBlank()) {
            trackerLoading = true
            when (val res = trackerRepository.getTrackerInfo(tId)) {
                is ApiResult.Success -> {
                    trackerInfo = res.value
                    trackerLoading = false
                }
                else -> {
                    trackerLoading = false
                }
            }
        } else {
            trackerInfo = null
            trackerLoading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                ListIconView(
                    icon = domain
                        ?.let(::nextDnsFaviconUrl)
                        ?.let(ListIcon::Url)
                        ?: ListIcon.None,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                if (domain != null) {
                    DomainTitleText(
                        domain = domain,
                        root = log.root,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.domain_hidden),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            ExpressiveCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier.padding(0.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val resolvedLabel = stringResource(R.string.resolved)
                    val unencryptedOverLabel = stringResource(R.string.unencrypted_over)
                    val overLabel = stringResource(R.string.over)
                    val fromLabel = stringResource(R.string.from)

                    val protocolAnnotated = remember(log, resolvedLabel, unencryptedOverLabel, overLabel, fromLabel) {
                        buildAnnotatedString {
                            append(resolvedLabel)
                            append(" ")
                            if (!log.encrypted) {
                                append(unencryptedOverLabel)
                            } else {
                                append(overLabel)
                            }
                            append(" ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(log.protocol)
                            }
                            log.clientIp?.takeIf(String::isNotBlank)?.let { ip ->
                                append(" ")
                                append(fromLabel)
                                append(" ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append(ip)
                                }
                            }
                        }
                    }
                    BottomSheetDetailRow(
                        icon = if (log.encrypted) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                        text = protocolAnnotated
                    )

                    BottomSheetDetailRow(
                        icon = Icons.Outlined.Schedule,
                        label = resolvedLabel,
                        value = relativeTime
                    )

                    log.root?.takeIf(String::isNotBlank)?.let { rootDom ->
                        BottomSheetDetailRow(
                            icon = Icons.Outlined.Language,
                            label = stringResource(R.string.root_domain_label),
                            value = rootDom
                        )
                    }

                    log.device?.let { dev ->
                        val devName = dev.name.orEmpty()
                        val devModel = dev.model?.let { " ($it)" }.orEmpty()
                        if (devName.isNotEmpty() || devModel.isNotEmpty()) {
                            BottomSheetDetailRow(
                                icon = Icons.Filled.Devices,
                                label = stringResource(R.string.device),
                                value = "$devName$devModel"
                            )
                        }
                    }

                    log.client?.takeIf(String::isNotBlank)?.let { clientId ->
                        BottomSheetDetailRow(
                            icon = Icons.Outlined.Dns,
                            label = stringResource(R.string.client),
                            value = Locales.getString("logs", "log", "clients", clientId),
                        )
                    }

                    if (log.status == "blocked" && log.reasons.isNotEmpty()) {
                        BottomSheetDetailRow(
                            icon = Icons.Outlined.Shield,
                            label = stringResource(R.string.blocked_by),
                            value = log.reasons.joinToString(", ") { it.name },
                            valueColor = MaterialTheme.colorScheme.error
                        )
                    }

                    if (isRawMode && !log.type.isNullOrBlank()) {
                        BottomSheetDetailRow(
                            icon = Icons.Outlined.Code,
                            label = stringResource(R.string.query_type_label),
                            value = log.type
                        )
                    }
                }
            }

            if (trackerLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            } else {
                trackerInfo?.let { TrackerInsightsCard(tracker = it) }
            }

            domain?.let { availableDomain ->
                val isBlocked = log.status == "blocked"
                val allowPending = PendingLogAction(availableDomain, DomainRuleList.Allow) in pendingActions
                val denyPending = PendingLogAction(availableDomain, DomainRuleList.Deny) in pendingActions

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (canEdit) {
                        if (isBlocked) {
                            Button(
                                onClick = { onApplyRule(DomainRuleList.Allow, availableDomain) },
                                enabled = !allowPending,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                if (allowPending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.allow_domain),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = { onApplyRule(DomainRuleList.Deny, availableDomain) },
                                enabled = !denyPending,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                if (denyPending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onError
                                    )
                                } else {
                                    Icon(Icons.Filled.Block, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.block_domain),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { onCopyDomain(availableDomain) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.copy),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}
