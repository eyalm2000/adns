package com.eyalm.adns.ui.screens.settings


import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BroadcastOnPersonal
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eyalm.adns.BuildConfig
import com.eyalm.adns.R
import com.eyalm.adns.data.AppRuntimeRepositories
import com.eyalm.adns.data.dns.DnsDisableBehavior
import com.eyalm.adns.data.nextdns.resources.NextDnsResourceRegistry
import com.eyalm.adns.data.provider.DnsProviderCatalog
import com.eyalm.adns.data.provider.DnsProviderSelection
import com.eyalm.adns.ui.components.ExpressiveIcon
import com.eyalm.adns.ui.components.NavigationSettingRow
import com.eyalm.adns.ui.components.RadioSettingRow
import com.eyalm.adns.ui.components.SegmentPosition
import com.eyalm.adns.ui.components.dialogs.FormDialog
import com.eyalm.adns.ui.components.segmentPosition
import com.eyalm.adns.ui.theme.pageTitle
import com.eyalm.adns.ui.theme.settingsLabel
import com.eyalm.adns.viewmodel.SettingsViewModel
import com.eyalm.adns.viewmodel.SettingsViewModel.Page


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onAddQuickTile: () -> Unit = {},
    currentPage: Page = Page.MAIN,
    onPageChange: (Page) -> Unit = {},
    innerPadding: PaddingValues,
    state: LazyListState = rememberLazyListState(),
    onScrollVisibilityChange: (Boolean) -> Unit = {},
) {
    val viewModel: SettingsViewModel = viewModel()
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

    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0 }
            .collect { isAtTop ->
                if (isAtTop) onScrollVisibilityChange(true)
            }
    }
    val provider by viewModel.selectedProvider.collectAsState()
    val disableBehavior by viewModel.disableBehavior.collectAsState()
    val context = LocalContext.current
    val capabilities by remember(context) {
        AppRuntimeRepositories.capabilities(context.applicationContext)
    }.state.collectAsState()
    var disableBehaviorDialogVisible by remember { mutableStateOf(false) }
    var pendingDisableBehavior by remember(disableBehavior) {
        mutableStateOf(disableBehavior)
    }

    if (disableBehaviorDialogVisible) {
        FormDialog(
            title = stringResource(R.string.dns_toggling_mode),
            confirmLabel = stringResource(R.string.confirm),
            onConfirm = {
                viewModel.setDisableBehavior(pendingDisableBehavior)
                disableBehaviorDialogVisible = false
            },
            onDismiss = { disableBehaviorDialogVisible = false },
        ) {
            RadioSettingRow(
                title = stringResource(R.string.dns_disable_off),
                description = stringResource(R.string.dns_disable_off_description),
                selected = pendingDisableBehavior == DnsDisableBehavior.Off,
                onClick = { pendingDisableBehavior = DnsDisableBehavior.Off },
                position = SegmentPosition.First,
                radio = { selected, onClick -> RadioButton(selected, onClick) },
            )
            Spacer(Modifier.height(4.dp))
            RadioSettingRow(
                title = stringResource(R.string.dns_disable_automatic),
                description = stringResource(R.string.dns_disable_automatic_description),
                selected = pendingDisableBehavior == DnsDisableBehavior.Automatic,
                onClick = { pendingDisableBehavior = DnsDisableBehavior.Automatic },
                position = SegmentPosition.Last,
                radio = { selected, onClick -> RadioButton(selected, onClick) },
            )
        }
    }



    val onAccountSettingsClick = remember(onPageChange) { { onPageChange(Page.ACCOUNT_SETTINGS) } }
    val onSetupClick = remember(onPageChange) { { onPageChange(Page.SETUP) } }
    val onProvidersClick = remember(onPageChange) { { onPageChange(Page.PROVIDERS) } }
    val onActivationClick = remember(onPageChange) { { onPageChange(Page.ACTIVATION) } }
    val onNotificationSettingsClick = remember(onPageChange) { { onPageChange(Page.NOTIFICATIONS) } }
    val onWifiRulesClick = remember(onPageChange) { { onPageChange(Page.WIFI_RULES) } }
    val onSecurityClick = remember(onPageChange) { { onPageChange(Page.SECURITY) } }
    val onPrivacyClick = remember(onPageChange) { { onPageChange(Page.PRIVACY) } }
    val onDenylistClick = remember(onPageChange) {  { viewModel.openListScreen(NextDnsResourceRegistry.denylist) } }
    val onAllowlistClick = remember(onPageChange) {  { viewModel.openListScreen(NextDnsResourceRegistry.allowlist) } }
    val onParentalControlClick = remember(onPageChange) { { onPageChange(Page.PARENTAL_CONTROL) } }
    val onSettingsPageClick = remember(onPageChange) { { onPageChange(Page.SETTINGS_PAGE) } }
    val onLanguagePageClick = remember(onPageChange) { { onPageChange(Page.LANGUAGE) } }
    val onAppearancePageClick = remember(onPageChange) { { onPageChange(Page.APPEARANCE) } }


        LazyColumn(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = 16.dp)
                .nestedScroll(nestedScrollConnection),
            contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            val isNextDns = (provider as? DnsProviderSelection.Enhanced)
                ?.providerId == DnsProviderCatalog.NEXTDNS
            val providerNameRes = DnsProviderCatalog.default.provider(provider)?.titleRes
                ?: R.string.custom_dns_hostname
            item {
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.pageTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 144.dp, bottom = 8.dp),
                )
            }

            item {
                SettingsNavigationGroup(
                    title = stringResource(R.string.provider_settings),
                    entries = buildList {
                        add(
                            SettingsNavigationEntry(
                                title = stringResource(R.string.change_provider),
                                description = stringResource(R.string.change_the_provider_to_use),
                                icon = Icons.Filled.BroadcastOnPersonal,
                                onClick = onProvidersClick,
                            ),
                        )
                        if (isNextDns) {
                            add(
                                SettingsNavigationEntry(
                                    title = stringResource(
                                        R.string.settings_1,
                                        stringResource(providerNameRes),
                                    ),
                                    description = stringResource(
                                        R.string.change_account_settings_for,
                                    ),
                                    icon = Icons.Filled.AccountCircle,
                                    onClick = onAccountSettingsClick,
                                ),
                            )
                        }
                    },
                )
            }



            if (isNextDns) {
                item {
                    SettingsNavigationGroup(
                        title = stringResource(R.string.nextdns_profile_settings),
                        entries = listOf(
                            SettingsNavigationEntry(stringResource(R.string.setup_another_device), stringResource(R.string.setup_nextdns_on_another_device), Icons.Filled.Devices, onSetupClick),
                            SettingsNavigationEntry(stringResource(R.string.security), stringResource(R.string.threat_protection_dns_rebinding_tlds), Icons.Filled.Security, onSecurityClick),
                            SettingsNavigationEntry(stringResource(R.string.privacy), stringResource(R.string.blocklists_trackers_affiliate_links), Icons.Filled.PrivacyTip, onPrivacyClick),
                            SettingsNavigationEntry(stringResource(R.string.parental_control), stringResource(R.string.safesearch_blocked_apps_categories), Icons.Filled.FamilyRestroom, onParentalControlClick),
                            SettingsNavigationEntry(stringResource(R.string.allowlist), stringResource(R.string.add_specific_domains_to_the_allowlist), Icons.Filled.Check, onAllowlistClick),
                            SettingsNavigationEntry(stringResource(R.string.denylist), stringResource(R.string.add_specific_domains_to_the_denylist), Icons.Filled.Block, onDenylistClick),
                            SettingsNavigationEntry(stringResource(R.string.general_profile_settings), stringResource(R.string.logs_performance_block_page), Icons.Filled.Tune, onSettingsPageClick),
                        ),
                    )
                }
            }
            if (capabilities.canUseDnsToggleSurfaces) {
                item {
                    SettingsNavigationGroup(
                        title = stringResource(R.string.toggling_settings),
                        entries = buildList {
                            add(
                                SettingsNavigationEntry(
                                    stringResource(R.string.dns_toggling_mode),
                                    stringResource(if (disableBehavior == DnsDisableBehavior.Off) R.string.dns_disable_off else R.string.dns_disable_automatic),
                                    Icons.Filled.Tune,
                                ) {
                                    pendingDisableBehavior = disableBehavior
                                    disableBehaviorDialogVisible = true
                                },
                            )
                            add(SettingsNavigationEntry(stringResource(R.string.state_notifications), stringResource(R.string.enable_or_disable_blocker_state_notifications), Icons.Filled.Notifications, onNotificationSettingsClick))
                            add(SettingsNavigationEntry(stringResource(R.string.wifi_rules), stringResource(R.string.wifi_rules_enable_description), Icons.Filled.Wifi, onWifiRulesClick))
                            add(SettingsNavigationEntry(stringResource(R.string.add_the_quick_settings_tile), stringResource(R.string.add_the_quick_settings_tile_to_your_device), Icons.Filled.SettingsSuggest, onAddQuickTile))
                        }
                    )
                }
            }


            item {
                SettingsNavigationGroup(
                    title = stringResource(R.string.app_settings),
                    entries = buildList {
                        add(SettingsNavigationEntry(stringResource(R.string.activation), stringResource(R.string.manage_activation), Icons.Filled.SettingsSuggest, onActivationClick))
                        add(SettingsNavigationEntry(stringResource(R.string.language), stringResource(R.string.language_description), Icons.Filled.Language, onLanguagePageClick))
                        add(SettingsNavigationEntry(stringResource(R.string.appearance), stringResource(R.string.appearance_description), Icons.Filled.Palette, onAppearancePageClick))
                    },
                )
            }


            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    onClick = {
                        val url = "https://adns.eyalm.dev"
                        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            Log.e("Settings", "No browser found to open GitHub URL", e)
                        }
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_adns_filled),
                            contentDescription = stringResource(R.string.app_icon),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(64.dp)
                        )

                        Text(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 12.dp, bottom = 4.dp),
                            text = stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.version_ncreated_by_eyal_meirom, BuildConfig.VERSION_NAME),
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

}

private data class SettingsNavigationEntry(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun SettingsNavigationGroup(
    title: String,
    entries: List<SettingsNavigationEntry>,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.settingsLabel,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        entries.forEachIndexed { index, entry ->
            NavigationSettingRow(
                title = entry.title,
                description = entry.description,
                position = segmentPosition(index, entries.size),
                leading = { ExpressiveIcon(entry.icon, Modifier.size(36.dp)) },
                trailing = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                    )
                },
                onClick = entry.onClick,
            )
        }
    }
}
