package com.eyalm.adns.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eyalm.adns.ui.components.OnboardingTemplate
import com.eyalm.adns.ui.components.SelectableCard
import com.eyalm.adns.ui.components.StandardBottomBar

@Preview
@Composable
fun ActivationMethodScreen(
    onBackClick: () -> Unit = {},
    onNextClick: (shizuku: Boolean, adb: Boolean) -> Unit = { _, _ -> }
) {
    var shizukuPressed by remember { mutableStateOf(false) }
    var adbPressed by remember { mutableStateOf(false) }

    OnboardingTemplate(
        onBackClick = onBackClick,
        bottomBarContent = {
            /** Text(
                text = "Please choose one option.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            )

            Button(
                onClick = { onNextClick(shizukuPressed, adbPressed) },
                shape = RoundedCornerShape(12.dp),
                enabled = shizukuPressed || adbPressed
            ) {
                Text("Next")
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next",
                    modifier = Modifier.size(18.dp)
                )
            } **/
            StandardBottomBar(
                message = "Please choose one option.",
                enabled = shizukuPressed || adbPressed,
                onNextClick = { onNextClick(shizukuPressed, adbPressed) }
            )

        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Activation",
                    style = MaterialTheme.typography.headlineMedium,
                    fontSize = 36.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 16.dp)
                )

            }
            item {
                Text(
                    text = "Please choose an activation method.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                SelectableCard(
                    title = "Shizuku",
                    description = "Easiest method. Requires Shizuku or Sui installed and set up.",
                    selected = shizukuPressed,
                    onClick = {
                        shizukuPressed = !shizukuPressed
                        adbPressed = false
                    },
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                SelectableCard(
                    title = "ADB Shell",
                    description = "Requires ADB shell access, usually with a computer, and a little bit of technical knowledge.",
                    selected = adbPressed,
                    onClick = {
                        adbPressed = !adbPressed
                        shizukuPressed = false
                    }
                )
            }
        }
    }
}
