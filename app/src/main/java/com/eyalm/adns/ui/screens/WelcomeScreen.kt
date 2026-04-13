package com.eyalm.adns.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eyalm.adns.ui.components.OnboardingTemplate
import com.eyalm.adns.ui.components.StandardBottomBar

@Preview
@Composable
fun WelcomeScreen(
    onNextClick: () -> Unit = { }
) {
    OnboardingTemplate(
        bottomBarContent = {
            StandardBottomBar(
                message = "Let’s set up blocking for your browser and apps.",
                onNextClick = onNextClick
            )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Welcome to ADNS",
                    style = MaterialTheme.typography.headlineMedium,
                    fontSize = 36.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )

                Text(
                    text = "A DNS-based ad blocker for Android",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
