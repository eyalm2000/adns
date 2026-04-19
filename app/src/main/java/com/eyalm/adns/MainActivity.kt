package com.eyalm.adns

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.eyalm.adns.ui.components.DnsSwitch
import com.eyalm.adns.ui.theme.AdnsTheme
import com.eyalm.adns.viewmodel.MainViewModel


class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private fun handleShortcutIntent(intent: Intent?) {
        if (intent?.action == "com.eyalm.adns.TOGGLE_ACTION") {
            viewModel.toggleDns()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcutIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        if (checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        enableEdgeToEdge()
        handleShortcutIntent(intent)

        setContent {
            AdnsTheme {
                val isEnabled by viewModel.adBlockingState.collectAsState()
                val runningTime by viewModel.runningTimeFlow.collectAsState()


                val showDialog = remember { mutableStateOf(false) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        isEnabled = isEnabled,
                        runningTime = runningTime,
                        onToggle = { viewModel.toggleDns() },
                        modifier = Modifier.padding(innerPadding),
                        server = viewModel.getHostname(),
                        onEditClick = { showDialog.value = true },
                        checkForUpdate = viewModel::checkForUpdate
                    )
                }

                if (showDialog.value) {
                    DnsDialog(
                        onDismissRequest = { showDialog.value = false },
                        onConfirmation = {
                            viewModel.setDnsUrl(it)
                            showDialog.value = false
                        },
                        currentUrl = viewModel.getHostname()
                    )
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
    server: String = "dns.adguard-dns.com",
    onEditClick: () -> Unit = {},
    checkForUpdate: ((String?) -> Unit) -> Unit = {}
) {
    val localContext = LocalContext.current
    val latestVersion = remember { mutableStateOf<String?>(null) }

    if (!BuildConfig.IS_FOSS) {
        LaunchedEffect(Unit) {
            checkForUpdate { version ->
                Log.d("update", "Latest version: $version")
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






    Scaffold(
        /**topBar  = {
            IconButton(
                onClick = {  },
                modifier = Modifier
                    .padding(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings"
                )
            }
        } **/
    ) {
        innerPadding ->
        Column(modifier = modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(16.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isEnabled) "Goooodbye,\nAds!" else "Blocker\nDisabled",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 48.sp,
                    lineHeight = 48.sp,


                    )
                Spacer(modifier = Modifier.height(32.dp))
                LazyColumn() {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text(text = "DNS Ad Blocker")
                                Text(
                                    text = if (isEnabled) "Running" else "Not running",
                                    color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                            IconButton(
                                modifier = Modifier
                                    .align(Alignment.Top),
                                onClick = {
                                    localContext.startActivity(Intent(localContext,
                                        SettingsActivity::class.java))

                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = "Settings"
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text(text = "Server")
                                Text(text = server)
                            }
                            IconButton(
                                modifier = Modifier
                                    .align(Alignment.Top),
                                onClick = onEditClick,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "Change DNS Server"
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = if (isEnabled) "Uptime" else "")
                        Text(text = if (isEnabled) "$runningTime" else "")
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            DnsSwitch(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally),
                isEnabled = isEnabled,
                onToggle = onToggle
            )

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun UpdateDialog(
    version: String,
    onClose: () -> Unit = {},
) {
    val context = LocalContext.current
    AlertDialog(
        icon = {
            Icon(imageVector = Icons.Filled.Update, contentDescription = "Update Icon")
        },
        title = {
            Text(text = "New Update")
        },
        text = {
            Text(text = "Version v$version is available.\nWould you like to download it?")
        },
        onDismissRequest = {
            onClose()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val url = "https://github.com/eyalm2000/adns/releases"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    try { context.startActivity(intent) } catch (e: Exception) {}
                    onClose()
                }
            ) {
                Text("Download")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onClose()
                }
            ) {
                Text("Dismiss")
            }
        }
    )
}


@Preview(showBackground = true)
@Composable
fun UpdateDialogPreview() {
    AdnsTheme {
        UpdateDialog(
            version = "1.0.0",
            onClose = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AdnsTheme {
        Greeting(
            isEnabled = true,
            runningTime = "00:05:23",
            onToggle = {}
        )
    }
}
