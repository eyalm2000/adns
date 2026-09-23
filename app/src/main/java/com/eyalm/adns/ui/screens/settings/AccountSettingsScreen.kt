package com.eyalm.adns.ui.screens.settings


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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eyalm.adns.R
import com.eyalm.adns.ui.components.ProfilesList
import com.eyalm.adns.ui.components.dialogs.DestructiveConfirmationDialog
import com.eyalm.adns.ui.screens.providerLogin.CreateProfileDialog
import com.eyalm.adns.ui.theme.pageTitle
import com.eyalm.adns.viewmodel.SettingsViewModel
import com.eyalm.adns.viewmodel.nextdns.NextDnsConnectionStatusViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccountSettingsScreen(
    onBack: () -> Unit = {},
    canControlPrivateDns: Boolean = true,
) {
    val viewModel: SettingsViewModel = viewModel()
    val connectionViewModel: NextDnsConnectionStatusViewModel = viewModel()
    val email = viewModel.email
    val profileSession by viewModel.profileSessionState.collectAsState()
    val connectionState by connectionViewModel.state.collectAsState()
    val selectedProfile = profileSession.selected
    val lifecycleOwner = LocalLifecycleOwner.current
    var openCreateProfileDialog by remember { mutableStateOf(false) }
    var openLogoutConfirmationDialog by remember { mutableStateOf(false) }
    var deviceName by remember { mutableStateOf(viewModel.nextDnsDeviceName) }
    val isDeviceNameValid = remember(deviceName) {
        deviceName.all { it.isDigit() || it in 'a'..'z' || it in 'A'..'Z' || it == ' ' || it == '-' }
    }

    val scrollState = rememberLazyListState()
    val showAppBarTitle by remember {
        derivedStateOf {
            scrollState.firstVisibleItemIndex > 0
        }
    }

    LaunchedEffect(viewModel.nextDnsDeviceName) {
        deviceName = viewModel.nextDnsDeviceName
    }

    LaunchedEffect(selectedProfile) {
        connectionViewModel.refresh(selectedProfile, force = true)
    }

    DisposableEffect(lifecycleOwner, selectedProfile) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                connectionViewModel.refresh(selectedProfile)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when {
        openCreateProfileDialog -> {
            CreateProfileDialog(
                onDismissRequest = { openCreateProfileDialog = false },
                onConfirmation = { name ->
                    openCreateProfileDialog = false
                    viewModel.createProfile(name)
                }
            )

        }
        openLogoutConfirmationDialog -> {
            DestructiveConfirmationDialog(
                title = stringResource(R.string.logout_confirmation_title),
                body = stringResource(R.string.logout_confirmation_message),
                confirmLabel = stringResource(R.string.logout),
                onConfirm = {
                    openLogoutConfirmationDialog = false
                    viewModel.logout()
                },
                onDismiss = {
                    openLogoutConfirmationDialog = false
                }
            )
        }
    }


    SettingsScreenLayout(
        title = stringResource(
            R.string.settings_1,
            stringResource(R.string.nextdns_name),
        ),
        onBack = onBack,
        showAppBarTitle = showAppBarTitle,
        modifier = Modifier,
        refreshing = profileSession.refreshing,
        onRefresh = {
            refreshAccountSettings(
                refreshProfiles = { viewModel.refreshProfileSession(force = true) },
                refreshConnection = {
                    connectionViewModel.refresh(selectedProfile, force = true)
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            state = scrollState,
        ) {
            item {
                Text(
                    text = stringResource(
                        R.string.settings_1,
                        stringResource(R.string.nextdns_name),
                    ),
                    style = MaterialTheme.typography.pageTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 40.dp),
                )
            }

            if (profileSession.loading && selectedProfile == null) {
                item {
                    Loading()
                }
            } else {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = stringResource(R.string.logged_in_as),
                                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = email ?: stringResource(R.string.loading),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            if (shouldShowDeviceNameEditor(canControlPrivateDns)) {
                                OutlinedTextField(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp),
                                    value = deviceName,
                                    onValueChange = { deviceName = it },
                                    label = { Text(stringResource(R.string.device_name_for_logs)) },
                                    isError = !isDeviceNameValid,
                                    trailingIcon = {
                                        Button(
                                            onClick = {
                                                if (isDeviceNameValid) {
                                                    viewModel.updateDeviceName(deviceName)
                                                }
                                            },
                                            enabled = isDeviceNameValid && deviceName != viewModel.nextDnsDeviceName,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.padding(end = 8.dp)
                                        ) {
                                            Text(stringResource(R.string.save))
                                        }
                                    },
                                    placeholder = { Text(stringResource(R.string.device_name_is_not_set)) },
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Text,
                                        imeAction = ImeAction.Done
                                    ),
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            Button(
                                onClick = {
                                    openLogoutConfirmationDialog = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(stringResource(R.string.logout), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                item {
                    NextDnsConnectionStatusSection(
                        status = connectionState.connection,
                        profiles = profileSession.profiles,
                        refreshing = connectionState.refreshing,
                        onRefresh = {
                            connectionViewModel.refresh(selectedProfile, force = true)
                        },
                    )
                }
                item {
                    ProfilesList(
                        profiles = profileSession.profiles,
                        selectedProfile = selectedProfile,
                        onProfileSelected = viewModel::setProfile,
                        onCreateProfileClick = {
                            openCreateProfileDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

fun shouldShowDeviceNameEditor(canControlPrivateDns: Boolean): Boolean =
    canControlPrivateDns

internal fun refreshAccountSettings(
    refreshProfiles: () -> Unit,
    refreshConnection: () -> Unit,
) {
    refreshProfiles()
    refreshConnection()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Loading() {
    Column(
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ContainedLoadingIndicator(modifier = Modifier.size(100.dp))
    }
}
