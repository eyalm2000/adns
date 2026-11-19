package com.eyalm.adns.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eyalm.adns.R
import com.eyalm.adns.ui.components.rememberAccountEpoch
import com.eyalm.adns.data.Locales
import com.eyalm.adns.data.nextdns.setup.LinkedIpContent
import com.eyalm.adns.data.nextdns.setup.DdnsValidationError
import com.eyalm.adns.data.nextdns.setup.GuideRichText
import com.eyalm.adns.data.nextdns.setup.SetupGuideBlock
import com.eyalm.adns.data.nextdns.setup.SetupGuideCatalogFactory
import com.eyalm.adns.data.nextdns.setup.SetupGuideMethod
import com.eyalm.adns.data.nextdns.setup.SetupGuideTag
import com.eyalm.adns.data.nextdns.setup.SetupContent
import com.eyalm.adns.data.nextdns.setup.segments
import com.eyalm.adns.domain.nextdns.ApiResult
import com.eyalm.adns.ui.components.dialogs.FormDialog
import com.eyalm.adns.ui.components.settings.LoadingError
import com.eyalm.adns.viewmodel.SettingsViewModel
import com.eyalm.adns.viewmodel.nextdns.DdnsDialogState
import com.eyalm.adns.viewmodel.nextdns.NextDnsConnectionStatusViewModel
import com.eyalm.adns.viewmodel.nextdns.SetupEffect
import com.eyalm.adns.viewmodel.nextdns.SetupUiState
import com.eyalm.adns.viewmodel.nextdns.SetupViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SetupScreen(
    profileId: String?,
    canManageLinkedIp: Boolean,
    onBack: () -> Unit,
    onSelectProfile: () -> Unit,
) {
    if (profileId == null) {
        SetupProfileRequiredScreen(
            onBack = onBack,
            onSelectProfile = onSelectProfile,
        )
        return
    }

    val epoch = rememberAccountEpoch()
    val viewModel: SetupViewModel = viewModel(key = "setup-${epoch.sequence}-$profileId")
    val connectionViewModel: NextDnsConnectionStatusViewModel =
        viewModel(key = "connection-${epoch.sequence}-$profileId")
    val settingsViewModel: SettingsViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val connectionState by connectionViewModel.state.collectAsState()
    val profileSession by settingsViewModel.profileSessionState.collectAsState()
    val selectedProfile = profileSession.selected
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val copiedToClipboardMessage = stringResource(R.string.copied_to_clipboard)

    LaunchedEffect(profileId) {
        viewModel.load(profileId)
    }
    LaunchedEffect(selectedProfile) {
        connectionViewModel.refresh(selectedProfile, force = true)
    }
    LaunchedEffect(canManageLinkedIp) {
        viewModel.setCanManageLinkedIp(canManageLinkedIp)
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SetupEffect.CopyToClipboard -> {
                    clipboard.setText(AnnotatedString(effect.text))
                    Toast.makeText(
                        context,
                        copiedToClipboardMessage,
                        Toast.LENGTH_SHORT,
                    ).show()
                }

                SetupEffect.LinkedIpUpdated -> {
                    Toast.makeText(context, R.string.done, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    SettingsScreenScaffold(
        onBack = onBack,
        title = Locales.getString("pages", "setup"),
        refreshing = state.loading && state.content != null,
        onRefresh = {
            viewModel.load(profileId, force = true)
        },
    ) {
        state.error?.let { error ->
            item {
                LoadingError(
                    error = error,
                    onRetry = viewModel::retry,
                )
                Spacer(Modifier.height(16.dp))
            }
        }

        if (state.loading && state.content == null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularWavyProgressIndicator()
                }
            }
        }

        state.content?.let { content ->
            item {
                NextDnsConnectionStatusSection(
                    status = connectionState.connection,
                    profiles = profileSession.profiles,
                    refreshing = connectionState.refreshing,
                    onRefresh = {
                        connectionViewModel.refresh(selectedProfile, force = true)
                    },
                )
                Spacer(Modifier.height(20.dp))
            }
            item {
                EndpointsCard(
                    content = content,
                    onCopy = { value ->
                        clipboard.setText(AnnotatedString(value))
                        Toast.makeText(
                            context,
                            copiedToClipboardMessage,
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
                Spacer(Modifier.height(20.dp))
            }
            item {
                LinkedIpCard(
                    linkedIp = content.linkedIp,
                    state = state,
                    onCopy = { value ->
                        clipboard.setText(AnnotatedString(value))
                        Toast.makeText(
                            context,
                            copiedToClipboardMessage,
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onLinkIp = viewModel::linkCurrentIp,
                    onConfigureDdns = viewModel::openDdnsDialog,
                    onRemoveDdns = viewModel::removeDdns,
                    onShowAdvanced = viewModel::showAdvancedOptions,
                    onCopyProgrammaticUrl = viewModel::copyProgrammaticUpdateUrl,
                )
                Spacer(Modifier.height(20.dp))
            }
            item {
                SetupGuideSection(
                    content = content,
                    onOpenUrl = { url ->
                        runCatching { uriHandler.openUri(url) }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    R.string.unknown_error,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                    },
                    onCopy = { value ->
                        clipboard.setText(AnnotatedString(value))
                        Toast.makeText(
                            context,
                            copiedToClipboardMessage,
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
                Spacer(Modifier.height(20.dp))
            }
            item {
                IdentifyDevicesCard(
                    content = content,
                    onCopy = { value ->
                        clipboard.setText(AnnotatedString(value))
                        Toast.makeText(
                            context,
                            copiedToClipboardMessage,
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    state.ddnsDialog?.let { dialog ->
        DdnsDialog(
            state = dialog,
            onHostnameChange = viewModel::updateDdnsHostname,
            onConfirm = viewModel::submitDdns,
            onDismiss = viewModel::dismissDdnsDialog,
        )
    }
}

@Composable
private fun SetupProfileRequiredScreen(
    onBack: () -> Unit,
    onSelectProfile: () -> Unit,
) {
    SettingsScreenScaffold(
        onBack = onBack,
        title = Locales.getString("pages", "setup"),
    ) {
        item {
            SetupCard(
                title = Locales.getString("pages", "setup"),
                description = stringResource(R.string.setup_profile_required),
            ) {
                Button(onClick = onSelectProfile) {
                    Text(stringResource(R.string.select_profile))
                }
            }
        }
    }
}


@Composable
private fun EndpointsCard(
    content: SetupContent,
    onCopy: (String) -> Unit,
) {
    SetupCard(
        title = Locales.getString("setup", "endpoints", "name"),
        description = Locales.getString("setup", "endpoints", "description"),
    ) {
        SetupValueRow(
            label = stringResource(R.string.profile_id),
            value = content.profileId,
            onCopy = onCopy,
        )
        SetupValueRow(
            label = stringResource(R.string.dns_over_tls_quic),
            value = content.dnsOverTls,
            onCopy = onCopy,
        )
        SetupValueRow(
            label = stringResource(R.string.dns_over_https),
            value = content.dnsOverHttps,
            onCopy = onCopy,
        )
        content.ipv4.takeIf { it.isNotEmpty() }?.let { addresses ->
            SetupValueRow(
                label = stringResource(R.string.ipv4),
                value = addresses.joinToString("\n"),
                onCopy = onCopy,
            )
        }
        content.ipv6.takeIf { it.isNotEmpty() }?.let { addresses ->
            SetupValueRow(
                label = stringResource(R.string.ipv6),
                value = addresses.joinToString("\n"),
                onCopy = onCopy,
            )
        }
        content.dnscryptStamp?.takeIf(String::isNotBlank)?.let { stamp ->
            SetupValueRow(
                label = stringResource(R.string.dnscrypt).replace("\n", ""),
                value = stamp,
                onCopy = onCopy,
                isLast = true,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LinkedIpCard(
    linkedIp: LinkedIpContent,
    state: SetupUiState,
    onCopy: (String) -> Unit,
    onLinkIp: () -> Unit,
    onConfigureDdns: () -> Unit,
    onRemoveDdns: () -> Unit,
    onShowAdvanced: () -> Unit,
    onCopyProgrammaticUrl: () -> Unit,
) {
    SetupCard(
        title = Locales.getString("setup", "linkedIp", "name"),
        description = Locales.getString("setup", "linkedIp", "description"),
    ) {
        linkedIp.servers.takeIf { it.isNotEmpty() }?.let { servers ->
            SetupValueRow(
                label = Locales.getString("setup", "linkedIp", "servers", "name"),
                value = servers.joinToString("\n"),
                onCopy = onCopy,
            )
        }
        SetupValueRow(
            label = Locales.getString("setup", "linkedIp", "ip", "name"),
            value = linkedIp.address ?: stringResource(R.string.not_linked),
            onCopy = onCopy,
            copyEnabled = linkedIp.address != null,
            altContent = {
                if (state.canManageLinkedIp) {
                    Button(
                        onClick = onLinkIp,
                        enabled = !state.linkingIp && !state.loading && state.linkIpAvailable,
                    ) {
                        Text(
                            if (state.linkingIp) stringResource(R.string.loading)
                            else if (!state.linkIpAvailable) stringResource(R.string.already_linked)
                            else Locales.getString("setup", "linkedIp", "ip", "link"),
                        )
                    }
                }
            }
        )
        linkedIp.ddnsHostname?.let { hostname ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = Locales.getPlainString(
                        arrayOf("setup", "linkedIp", "ddns", "status"),
                        values = mapOf("hostname" to hostname),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = onRemoveDdns,
                    enabled = !state.removingDdns,
                ) {
                    Text(
                        if (state.removingDdns) stringResource(R.string.loading)
                        else Locales.getString("setup", "linkedIp", "ddns", "unset"),
                    )
                }
            }

        }

        if (state.canManageLinkedIp) {
            if (linkedIp.ddnsHostname == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = Locales.getString("setup", "linkedIp", "ddns", "explanation"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = onConfigureDdns) {
                        Text(Locales.getString("setup", "linkedIp", "ddns", "set"))
                    }
                }
            } else {

            }
        }

        if (state.canManageLinkedIp) {
            Spacer(Modifier.height(20.dp))
            if (state.advancedOptionsVisible) {
                Text(
                    text = Locales.getString("setup", "linkedIp", "programmatically"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onCopyProgrammaticUrl) {
                    Text(stringResource(R.string.copy_linked_ip_update_url))
                }
            } else {
                OutlinedButton(onClick = onShowAdvanced) {
                    Text(Locales.getString("setup", "linkedIp", "showAdvanced"))
                }
            }
        }
    }
}

@Composable
private fun DdnsDialog(
    state: DdnsDialogState,
    onHostnameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    FormDialog(
        title = Locales.getString("setup", "linkedIp", "ddns", "set"),
        confirmLabel = Locales.getString("global", "save"),
        submitting = state.submitting,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = state.hostname,
            onValueChange = onHostnameChange,
            label = {
                Text(
                    Locales.getString(
                        "setup", "linkedIp", "ddns", "form", "hostname", "label",
                    ),
                )
            },
            placeholder = {
                Text(
                    Locales.getString(
                        "setup", "linkedIp", "ddns", "form", "hostname", "placeholder",
                    ),
                )
            },
            isError = state.error != null,
            supportingText = {
                state.error?.let { error ->
                    Text(ddnsErrorText(error))
                }
            },
            enabled = !state.submitting,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )
    }
}

@Composable
private fun ddnsErrorText(error: DdnsValidationError): String = when (error) {
    DdnsValidationError.Required -> Locales.getString(
        "setup", "linkedIp", "ddns", "form", "hostname", "errors", "required",
    )

    DdnsValidationError.Invalid -> Locales.getString(
        "setup", "linkedIp", "ddns", "form", "hostname", "errors", "invalid",
    )

    DdnsValidationError.IpAddress -> Locales.getString(
        "setup", "linkedIp", "ddns", "form", "hostname", "errors", "ip",
    )

    is DdnsValidationError.Server -> when (error.code) {
        "different", "error", "empty", "multiple" -> Locales.getString(
            "setup", "linkedIp", "ddns", "form", "hostname", "errors", error.code,
        )

        else -> Locales.getString(
            "setup", "linkedIp", "ddns", "form", "hostname", "errors", "error",
        )
    }

    DdnsValidationError.Request -> stringResource(R.string.network_error_please_try_again)
}

@Composable
private fun SetupGuideSection(
    content: SetupContent,
    onOpenUrl: (String) -> Unit,
    onCopy: (String) -> Unit,
) {
    val context = LocalContext.current
    val catalog = remember(content) {
        SetupGuideCatalogFactory.create(context, content)
    }
    if (catalog.platforms.isEmpty()) return

    var selectedPlatformId by remember(content.profileId) {
        mutableStateOf(catalog.platforms.first().id)
    }
    val selectedPlatform = catalog.platforms
        .firstOrNull { it.id == selectedPlatformId }
        ?: catalog.platforms.first()

    SetupCard(
        title = Locales.getString("setup", "guide", "name"),
        description = Locales.getString("setup", "guide", "description"),
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val padding = 16.dp.roundToPx()
                    val maxWidth = constraints.maxWidth + padding * 2
                    val placeable = measurable.measure(
                        constraints.copy(maxWidth = maxWidth, minWidth = maxWidth)
                    )
                    layout(constraints.maxWidth, placeable.height) {
                        placeable.placeRelative(-padding, 0)
                    }
                },
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(catalog.platforms, key = { it.id }) { platform ->
                FilterChip(
                    selected = platform.id == selectedPlatform.id,
                    onClick = { selectedPlatformId = platform.id },
                    label = { Text(platform.title) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        selectedPlatform.methods.forEachIndexed { index, method ->
            SetupGuideMethodCard(
                method = method,
                onOpenUrl = onOpenUrl,
                onCopy = onCopy,
                isFirst = index == 0,
                isLast = index == selectedPlatform.methods.lastIndex
            )
            if (index != selectedPlatform.methods.lastIndex) {
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun SetupGuideMethodCard(
    method: SetupGuideMethod,
    onOpenUrl: (String) -> Unit,
    onCopy: (String) -> Unit,
    isFirst: Boolean = false,
    isLast: Boolean = false
) {
    Card(
        shape = RoundedCornerShape(
            topStart = if (isFirst) 12.dp else 2.dp,
            topEnd = if (isFirst) 12.dp else 2.dp,
            bottomStart = if (isLast) 12.dp else 2.dp,
            bottomEnd = if (isLast) 12.dp else 2.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = method.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                method.tags.forEach { tag ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = when (tag) {
                                SetupGuideTag.Recommended -> Locales.getString(
                                    "setup", "guide", "tags", "recommended",
                                )

                                SetupGuideTag.Advanced -> Locales.getString(
                                    "setup", "guide", "tags", "advanced",
                                )
                            },
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            method.requirements?.let { requirement ->
                Spacer(Modifier.height(12.dp))
                GuideText(
                    text = requirement,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            method.blocks.forEach { block ->
                Spacer(Modifier.height(12.dp))
                when (block) {
                    is SetupGuideBlock.Steps -> Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        block.values.forEach { step ->
                            GuideText(text = step)
                        }
                    }

                    is SetupGuideBlock.Paragraph -> GuideText(
                        text = block.value,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    is SetupGuideBlock.Warning -> Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        GuideText(
                            text = block.value,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }

                    is SetupGuideBlock.Code -> Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = block.value,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onCopy(block.value) }) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = stringResource(R.string.copy),
                            )
                        }
                    }

                    is SetupGuideBlock.ExternalLink -> OutlinedButton(
                        onClick = { onOpenUrl(block.url) },
                    ) {
                        GuideText(text = block.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideText(
    text: GuideRichText,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val annotated = remember(text) {
        buildAnnotatedString {
            text.segments().forEach { segment ->
                if (segment.emphasized) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(segment.value)
                    }
                } else {
                    append(segment.value)
                }
            }
        }
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
    )
}

@Composable
private fun IdentifyDevicesCard(
    content: SetupContent,
    onCopy: (String) -> Unit,
) {
    SetupCard(
        title = Locales.getString("setup", "identify", "name"),
        description = Locales.getString("setup", "identify", "description"),
    ) {
        IdentifyDeviceMethod(
            title = stringResource(R.string.dns_over_tls_quic),
            instructions = Locales.getPlainString(
                arrayOf("setup", "identify", "dot", "instructions"),
            ) + "\n" + Locales.getPlainString(
                path = arrayOf("setup", "identify", "dot", "example"),
                values = mapOf("profile" to content.profileId)
            ),
        )
        Spacer(Modifier.height(16.dp))
        IdentifyDeviceMethod(
            title = stringResource(R.string.dns_over_https),
            instructions = Locales.getPlainString(
                arrayOf("setup", "identify", "doh", "instructions"),
            ) + "\n" + Locales.getPlainString(
                path = arrayOf("setup", "identify", "doh", "example"),
                values = mapOf("profile" to content.profileId)
            ),
        )
        Spacer(Modifier.height(16.dp))
        IdentifyDeviceMethod(
            title = stringResource(R.string.adns_app),
            instructions = stringResource(R.string.set_device_name_in_the_nextdns_settings_section)
        )
        Spacer(Modifier.height(16.dp))
        IdentifyDeviceMethod(
            title = Locales.getString("setup", "identify", "apps", "name"),
            instructions = Locales.getString("setup", "identify", "apps", "instructions")
        )
    }
}

@Composable
private fun IdentifyDeviceMethod(
    title: String,
    instructions: String,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = instructions,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SetupCard(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun SetupValueRow(
    label: String,
    value: String,
    onCopy: (String) -> Unit,
    copyEnabled: Boolean = true,
    isLast: Boolean = false,
    maxLines: Int? = null,
    altContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (isLast) 0.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = maxLines ?: Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        if (altContent != null) {
            altContent()
        } else {
            IconButton(
                onClick = { onCopy(value) },
                enabled = copyEnabled,
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.copy),
                )
            }
        }

    }
}
