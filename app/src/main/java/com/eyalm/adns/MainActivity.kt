package com.eyalm.adns

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eyalm.adns.data.DnsRepository
import com.eyalm.adns.data.Locales
import com.eyalm.adns.ui.security.setProtectedContent
import com.eyalm.adns.data.activation.ActivationMode
import com.eyalm.adns.data.activation.ActivationRepositories
import com.eyalm.adns.data.localization.AppLocaleRepository
import com.eyalm.adns.data.nextdns.auth.NextDnsSessionManager
import com.eyalm.adns.data.provider.DnsProviderCatalog
import com.eyalm.adns.data.provider.DnsProviderSelection
import com.eyalm.adns.data.runtime.RuntimeServiceController
import com.eyalm.adns.domain.AppCapabilities
import com.eyalm.adns.domain.AppDestination
import com.eyalm.adns.domain.MainTab
import com.eyalm.adns.domain.resolveAvailableMainTab
import com.eyalm.adns.ui.components.ExpressiveFloatingMenu
import com.eyalm.adns.ui.components.FloatingMenuItem
import com.eyalm.adns.ui.components.dialogs.ConfirmationDialog
import com.eyalm.adns.ui.screens.ActivationScreen
import com.eyalm.adns.ui.screens.HomeScreen
import com.eyalm.adns.ui.screens.StatsScreen
import com.eyalm.adns.ui.screens.UpdateDialog
import com.eyalm.adns.ui.screens.forcedActivationExitPolicy
import com.eyalm.adns.ui.screens.settings.LogsScreen
import com.eyalm.adns.ui.screens.settings.SettingsTabRouter
import com.eyalm.adns.ui.screens.updatedForcedActivationVisibility
import com.eyalm.adns.ui.theme.AdnsTheme
import com.eyalm.adns.viewmodel.MainViewModel
import com.eyalm.adns.viewmodel.OnboardingViewModel
import com.eyalm.adns.viewmodel.RuntimeMonitoringViewModel
import com.eyalm.adns.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val runtimeMonitoringViewModel: RuntimeMonitoringViewModel by viewModels()
    private fun handleShortcutIntent(intent: Intent?) {
        if (intent?.action == "com.eyalm.adns.TOGGLE_ACTION") {
            viewModel.toggleDns()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcutIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            ActivationRepositories.getInstance(applicationContext).refreshPermission()
            runtimeMonitoringViewModel.refreshSystemState()
            settingsViewModel.refreshProvider()
            if (settingsViewModel.selectedProvider.value is DnsProviderSelection.Enhanced) {
                settingsViewModel.refreshProfileSession()
                settingsViewModel.email = settingsViewModel.getEmail()
            }
        }


    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        Locales.sync(this, AppLocaleRepository(this).selectedTag())

        val activationRepository = ActivationRepositories.getInstance(applicationContext)
        activationRepository.refreshPermission()
        if (
            com.eyalm.adns.data.AppRuntimeRepositories
                .capabilities(applicationContext)
                .current()
                .startupDestination == AppDestination.Onboarding
        ) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        enableEdgeToEdge()
        handleShortcutIntent(intent)

        setProtectedContent {
            AdnsTheme {
                val isEnabled by viewModel.adBlockingState.collectAsState()
                val runningTime by viewModel.runningTimeFlow.collectAsState()
                val server by viewModel.dnsUrlFlow.collectAsState()
                val settingsPage by settingsViewModel.page.collectAsState()
                val capabilities by viewModel.capabilities.collectAsState()
                val activationState by activationRepository.state.collectAsState()
                val permissionViewModel: OnboardingViewModel = viewModel()
                val permissionAcquisitionState by
                    permissionViewModel.permissionState.collectAsState()
                var activationVisible by rememberSaveable {
                    mutableStateOf(
                        capabilities.startupDestination == AppDestination.Activation
                    )
                }
                var previousActivationWarning by remember {
                    mutableStateOf(capabilities.showActivationWarning)
                }

                LaunchedEffect(capabilities.showActivationWarning) {
                    activationVisible = updatedForcedActivationVisibility(
                        currentlyVisible = activationVisible,
                        previouslyRequired = previousActivationWarning,
                        currentlyRequired = capabilities.showActivationWarning,
                    )
                    previousActivationWarning = capabilities.showActivationWarning
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (activationVisible) {
                        ActivationScreen(
                            state = activationState,
                            permissionAcquisitionState = permissionAcquisitionState,
                            controlOnlyEligible = capabilities.canManageNextDns,
                            exitPolicy = forcedActivationExitPolicy(
                                controlOnlyEligible = capabilities.canManageNextDns,
                            ),
                            onStartPermissionMonitoring =
                                permissionViewModel::startPermissionCheck,
                            onRequestShizuku = permissionViewModel::requestShizukuActivation,
                            onExit = { activationVisible = false },
                            onUseControlOnly = {
                                activationRepository.changeMode(
                                    ActivationMode.NextDnsControlOnly
                                )
                                activationVisible = false
                            },
                            onUsePrivileged = {
                                activationRepository.changeMode(
                                    ActivationMode.PrivilegedDnsControl
                                )
                            },
                            onStopPermissionAcquisition =
                                permissionViewModel::stopPermissionAcquisition,
                        )
                    } else {
                        Greeting(
                            isEnabled = isEnabled,
                            runningTime = runningTime,
                            onToggle = { viewModel.toggleDns() },
                            modifier = Modifier.padding(innerPadding),
                            server = server,
                            onEditClick = {
                                settingsViewModel.setPage(SettingsViewModel.Page.PROVIDERS)
                            },
                            checkForUpdate = viewModel::checkForUpdate,
                            settingsPage = settingsPage,
                            capabilities = capabilities,
                        )
                    }
                }

            }
        }

    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Greeting(
    isEnabled: Boolean,
    runningTime: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    server: String,
    onEditClick: () -> Unit = {},
    checkForUpdate: ((String?) -> Unit) -> Unit = {},
    settingsPage: SettingsViewModel.Page = SettingsViewModel.Page.MAIN,
    capabilities: AppCapabilities,
) {
    val settingsViewModel: SettingsViewModel = viewModel()
    val profileSession by settingsViewModel.profileSessionState.collectAsState()

    var selectedItem by rememberSaveable(inputs = arrayOf(capabilities.defaultTab)) {
        mutableStateOf(capabilities.defaultTab)
    }
    val homeTab = stringResource(R.string.home)
    val statsTab = stringResource(R.string.stats)
    val logsTab = stringResource(R.string.logs)
    val settingsTab = stringResource(R.string.settings)
    val labels = remember(homeTab, statsTab, logsTab, settingsTab) {
        mapOf(
            MainTab.Home to homeTab,
            MainTab.Stats to statsTab,
            MainTab.Logs to logsTab,
            MainTab.Settings to settingsTab,
        )
    }
    val selectedIcons = remember {
        mapOf(
            MainTab.Home to Icons.Filled.Home,
            MainTab.Stats to Icons.Filled.Insights,
            MainTab.Logs to Icons.AutoMirrored.Filled.List,
            MainTab.Settings to Icons.Filled.Settings,
        )
    }
    val unselectedIcons = remember {
        mapOf(
            MainTab.Home to Icons.Outlined.Home,
            MainTab.Stats to Icons.Outlined.Insights,
            MainTab.Logs to Icons.AutoMirrored.Outlined.List,
            MainTab.Settings to Icons.Outlined.Settings,
        )
    }
    val context = LocalContext.current
    val dnsRepository = remember(context) { DnsRepository(context.applicationContext) }
    LaunchedEffect(capabilities.canUseDnsToggleSurfaces) {
        dnsRepository.updateShortcuts()
        RuntimeServiceController.sync(context.applicationContext)
    }
    val onNavigateToProviders = remember(context) {
        { providerId: String ->
            val intent = Intent(context, ProviderLoginActivity::class.java).apply {
                putExtra("provider", providerId)
            }
            context.startActivity(intent)
        }
    }
    val nextDnsSessionManager = remember(context) {
        NextDnsSessionManager.getInstance(context.applicationContext)
    }
    val reauthenticationRequested by nextDnsSessionManager.reauthenticationRequested.collectAsState()

    if (reauthenticationRequested) {
        ConfirmationDialog(
            title = stringResource(R.string.nextdns_session_expired_title),
            body = stringResource(R.string.nextdns_session_expired_message),
            confirmLabel = stringResource(R.string.sign_in),
            onConfirm = {
                nextDnsSessionManager.dismissReauthenticationRequest()
                onNavigateToProviders(DnsProviderCatalog.NEXTDNS.value)
            },
            onDismiss = nextDnsSessionManager::dismissReauthenticationRequest,
        )
    }
    val latestVersion = remember { mutableStateOf<String?>(null) }

    if (!BuildConfig.IS_FOSS) {
        LaunchedEffect(Unit) {
            checkForUpdate { version ->
                latestVersion.value = version
            }
        }

        latestVersion.value?.let { version ->
            UpdateDialog(
                version = version,
                onClose = { latestVersion.value = null }
            )
        }
    }

    LaunchedEffect(capabilities.visibleTabs, capabilities.defaultTab) {
        selectedItem = resolveAvailableMainTab(selectedItem, capabilities)
    }

    BackHandler(enabled = selectedItem != capabilities.defaultTab) {
        selectedItem = capabilities.defaultTab
    }

    var isFloatingMenuVisible by remember { mutableStateOf(true) }
    var isStatsCardExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(selectedItem, settingsPage) {
        isFloatingMenuVisible = true
        if (selectedItem != MainTab.Stats) {
            isStatsCardExpanded = false
        }
    }

    val statsScrollState = rememberLazyListState()

    val menuItems = remember(capabilities.visibleTabs, labels, selectedIcons, unselectedIcons) {
        capabilities.visibleTabs.map { tab ->
            FloatingMenuItem(
                id = tab,
                label = requireNotNull(labels[tab]),
                selectedIcon = requireNotNull(selectedIcons[tab]),
                unselectedIcon = requireNotNull(unselectedIcons[tab]),
            )
        }
    }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = isFloatingMenuVisible && !(
                    settingsPage != SettingsViewModel.Page.MAIN &&
                        selectedItem == MainTab.Settings
                    ) && !(
                    isStatsCardExpanded &&
                        selectedItem == MainTab.Stats
                    ),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                ExpressiveFloatingMenu(
                    items = menuItems,
                    selectedItem = selectedItem,
                    onItemSelected = { item ->
                        if (
                            (item != MainTab.Stats && item != MainTab.Logs) ||
                            nextDnsSessionManager.requestFeatureAccess()
                        ) {
                            selectedItem = item
                        }
                    }
                )
            }
        },
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()) // Ignore bottom padding
        ) {
            AnimatedContent(
                targetState = selectedItem,
                transitionSpec = {
                    ((fadeIn(animationSpec = tween(220)) +
                            scaleIn(
                                initialScale = 0.93f,
                                animationSpec = tween(300, easing = LinearOutSlowInEasing)
                            )) togetherWith
                            (fadeOut(animationSpec = tween(120)) +
                                    scaleOut(
                                        targetScale = 1.07f,
                                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                                    )))
                        .using(SizeTransform(clip = false))
                },
                label = "MainContentTransition"
            ) { targetIndex ->
                when (targetIndex) {
                    MainTab.Home -> {
                        HomeScreen(
                            isEnabled = isEnabled,
                            runningTime = runningTime,
                            onToggle = onToggle,
                            controlsEnabled = capabilities.canUseDnsToggleSurfaces,
                            server = server,
                            onEditClick = {
                                selectedItem = MainTab.Settings
                                onEditClick()
                            },
                            innerPadding = innerPadding,
                            onSettingsClick = {
                                selectedItem = MainTab.Settings
                            }
                        )

                    }

                    MainTab.Stats -> StatsScreen(
                        innerPadding = innerPadding,
                        scrollState = statsScrollState,
                        onScrollVisibilityChange = { isVisible -> isFloatingMenuVisible = isVisible },
                        onExpandedChange = { isExpanded -> isStatsCardExpanded = isExpanded }
                    )

                    MainTab.Logs -> LogsScreen(
                        onBack = null,
                        profileState = profileSession,
                        innerPadding = innerPadding,
                        onScrollVisibilityChange = { isVisible -> isFloatingMenuVisible = isVisible }
                    )

                    MainTab.Settings -> {
                        SettingsTabRouter(
                            onNavigateToProvidersActivity = onNavigateToProviders,
                            innerPadding = innerPadding,
                            onScrollVisibilityChange = { isVisible -> isFloatingMenuVisible = isVisible }
                        )
                    }
                }
            }
        }

    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AdnsTheme {
        Greeting(
            isEnabled = true,
            runningTime = "00:05:23",
            onToggle = {},
            server = "dns.example",
            capabilities = AppCapabilities(
                canControlPrivateDns = true,
                canUseDnsToggleSurfaces = true,
                canManageNextDns = false,
                showHome = true,
                showStats = false,
                showLogs = false,
                showActivationWarning = false,
                canRunRuntimeMonitor = true,
                canUseWifiRules = true,
                defaultTab = MainTab.Home,
                visibleTabs = listOf(MainTab.Home, MainTab.Settings),
                startupDestination = AppDestination.Main(MainTab.Home),
            ),
        )
    }
}
