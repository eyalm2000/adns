package com.eyalm.addns.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eyalm.addns.ui.components.OnboardingTemplate
import com.eyalm.addns.viewmodel.OnboardingViewModel

@Preview
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AdbActivationScreen(onBack: () -> Unit = { }) {
    val viewModel: OnboardingViewModel = viewModel()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.startPermissionCheck(context)
    }

    OnboardingTemplate(
        onBackClick = onBack,
        bottomBarContent = {
            Text(
                text = "Waiting for permission...",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f).padding(end = 16.dp)
            )
            LoadingIndicator(modifier = Modifier.size(100.dp))
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Activation",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )

            Text("Paste the following command into your terminal:")

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Text(
                        text = "adb shell pm grant com.eyalm.addns android.permission.WRITE_SECURE_SETTINGS",
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString("adb shell pm grant com.eyalm.addns android.permission.WRITE_SECURE_SETTINGS"))
                    }) {
                        Icon(Icons.Filled.ContentCopy, "copy")
                    }
                }
            }
            Text("Don't worry! It's completely safe.")
        }
    }
}