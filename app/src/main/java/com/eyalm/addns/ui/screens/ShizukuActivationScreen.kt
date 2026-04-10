package com.eyalm.addns.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eyalm.addns.ui.components.OnboardingTemplate
import com.eyalm.addns.viewmodel.OnboardingViewModel

@Preview
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShizukuActivationScreen() {

    val viewModel: OnboardingViewModel = viewModel()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.writePermissionShizuku(context)
    }

    OnboardingTemplate(
        content = { },
        bottomBarContent = {
            LinearWavyProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    )
}