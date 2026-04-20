package com.eyalm.adns

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BroadcastOnPersonal
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight.Companion.Bold
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eyalm.adns.data.DnsConstants
import com.eyalm.adns.ui.components.ClickableCardSettings
import com.eyalm.adns.ui.theme.AdnsTheme
import com.eyalm.adns.viewmodel.SettingsViewModel

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: SettingsViewModel = viewModel()
            val dnsUrl by viewModel.dnsUrl.collectAsState()

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                if (isGranted) {
                    viewModel.refreshNotification()
                    Log.d("Permission", "Permission Granted")
                    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, this@SettingsActivity.packageName)
                        putExtra(Settings.EXTRA_CHANNEL_ID, "dns_status_channel")
                    }
                    this@SettingsActivity.startActivity(intent)
                } else {
                    Log.d("Permission", "Permission Denied")
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, this@SettingsActivity.packageName)
                    }
                    this@SettingsActivity.startActivity(intent)
                }
            }

            AdnsTheme {
                Greeting2(
                    dnsUrl = dnsUrl,
                    onDnsUrlChange = { viewModel.setDnsUrl(it) },
                    modifier = Modifier.fillMaxSize(),
                    onBack = { finish() },
                    onAddQuickTile = { viewModel.addQuickTile() },
                    permissionLauncher = permissionLauncher
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Greeting2(
    dnsUrl: String,
    onDnsUrlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onAddQuickTile: () -> Unit = {},
    permissionLauncher: ActivityResultLauncher<String>? = null
) {
    val context = LocalContext.current
    val openDnsDialog = remember { mutableStateOf(false) }

    when {
        openDnsDialog.value -> {
            DnsDialog(
                onDismissRequest = { openDnsDialog.value = false },
                onConfirmation = {
                    onDnsUrlChange(it)
                    openDnsDialog.value = false
                },
                currentUrl = dnsUrl)
        }
    }


    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = {
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 48.dp, bottom = 16.dp),
                fontSize = 32.sp,
            ) }
            item {
                ClickableCardSettings(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    title = "State Notifications",
                    description = "Enable or disable blocker state notifications",
                    icon = Icons.Filled.Notifications
                )
            }
            item {
                ClickableCardSettings(
                    onClick = onAddQuickTile,
                    title = "Add the quick settings tile",
                    description = "Add the quick settings tile to your device",
                    icon = Icons.Filled.SettingsSuggest
                )
            }
            item {
                ClickableCardSettings(
                    onClick = { openDnsDialog.value = true },
                    title = "Change the DNS server (advanced)",
                    description = "Change the DNS server to use",
                    icon = Icons.Filled.BroadcastOnPersonal
                )
            }
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val url = "https://github.com/eyalm2000/adns"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Icon(
                            painter = painterResource(id = R.drawable.ic_adns_filled),
                            contentDescription = "App icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(64.dp)
                        )

                        Text(
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                .padding(top = 8.dp, bottom = 8.dp),
                            text = "ADNS",
                            fontWeight = Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Version ${BuildConfig.VERSION_NAME}\nCreated by Eyal Meirom",
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                .padding(bottom = 8.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }

            }
        }
    }
}

@Composable
fun DnsDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: (String) -> Unit,
    currentUrl: String
) {
    val isAdGuard = remember { mutableStateOf(currentUrl == DnsConstants.ADGUARD_DNS) }
    val customUrlText = remember { mutableStateOf(if (currentUrl == DnsConstants.ADGUARD_DNS) "" else currentUrl) }

    val isCustomValid = customUrlText.value.isNotEmpty() && android.util.Patterns.DOMAIN_NAME.matcher(customUrlText.value).matches()
    val isConfirmEnabled = isAdGuard.value || isCustomValid

    AlertDialog(
        icon = {
            Icon(Icons.Filled.BroadcastOnPersonal, contentDescription = "DNS Server")
        },
        title = {
            Text(text = "Set DNS Server")
        },
        text = {
            Column {
                Text(text = "Choose a DNS server to use")
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .selectable(
                            selected = isAdGuard.value,
                            onClick = { isAdGuard.value = true },
                            role = Role.RadioButton
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isAdGuard.value,
                        onClick = null
                    )
                    Text(
                        text = DnsConstants.ADGUARD_DNS,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 16.sp
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = !isAdGuard.value,
                            onClick = { isAdGuard.value = false },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        modifier = Modifier
                            .align(Alignment.Top),
                        selected = !isAdGuard.value,
                        onClick = null
                    )
                    Column(
                        modifier = Modifier.padding(start = 16.dp)
                    ) {
                        Text(
                            text = "Custom hostname:",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 16.sp
                        )
                        TextField(
                            modifier = Modifier.fillMaxWidth().
                                padding(top = 8.dp),
                            value = customUrlText.value,
                            onValueChange = { 
                                customUrlText.value = it
                                isAdGuard.value = false
                            },
                            isError = !isAdGuard.value && !isCustomValid,
                            supportingText = {
                                if (!isAdGuard.value && !isCustomValid && customUrlText.value.isNotEmpty()) {
                                    Text("Invalid hostname")
                                }
                            }
                        )
                    }
                }
            }
        },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalUrl = if (isAdGuard.value) DnsConstants.ADGUARD_DNS else customUrlText.value
                    onConfirmation(finalUrl)
                },
                enabled = isConfirmEnabled
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                }
            ) {
                Text("Dismiss")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun DnsDialogPreview() {
    AdnsTheme {
        DnsDialog(
            onDismissRequest = {},
            onConfirmation = {},
            currentUrl = DnsConstants.ADGUARD_DNS
        )
    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview2() {
    AdnsTheme {
        Greeting2(
            dnsUrl = DnsConstants.ADGUARD_DNS,
            onDnsUrlChange = {},
            permissionLauncher = null
        )
    }
}