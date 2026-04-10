package com.eyalm.adns

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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


    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        if (checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }


        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AdnsTheme {
                val isEnabled by viewModel.adBlockingState.collectAsState()
                val runningTime by viewModel.runningTimeFlow.collectAsState()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        isEnabled = isEnabled,
                        runningTime = runningTime,
                        onToggle = { viewModel.toggleDns() },
                        modifier = Modifier.padding(innerPadding)
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
    modifier: Modifier = Modifier
) {
    val localContext = LocalContext.current
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
                        Text(text = "Server")
                        Text(text = "dns.adguard-dns.com") // TODO: Custom servers
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
