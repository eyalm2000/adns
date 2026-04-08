package com.eyalm.addns

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.eyalm.addns.ui.theme.AddnsTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.finishAffinity
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.ContextCompat.getSystemService
class OnboardingActivity : ComponentActivity() {
    private var privilegedService: IPrivilegedService? = null
    enum class Step { INTRO , ACTIVATION_METHOD, ADB /** GRANT_PERM, SUCCESS **/ }
    var currentStep by mutableStateOf(Step.INTRO)

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AddnsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (currentStep) {
                        Step.INTRO -> WelcomeScreen(onBackClick = { finishAffinity(this) }, onNextClick = { currentStep = Step.ACTIVATION_METHOD }) //
                        Step.ACTIVATION_METHOD -> ActivationMethod(onBackClick = { currentStep = Step.INTRO }, onNextClick = { Shizuku, Adb -> if (Adb) currentStep = Step.ADB})
                        Step.ADB -> AdbActivation(onBackClick = { currentStep = Step.ACTIVATION_METHOD })
                        /** Step.GRANT_PERM -> GrantSystemScreen()
                        Step.SUCCESS -> SuccessScreen() **/

                    }
                }
            }
        }
    }
}
@Preview
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    onBackClick: () -> Unit = {},
    onNextClick: () -> Unit = {}
) {

    // 4. Scaffold is the skeleton of our screen
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                        /** Text(
                            text = "Enhanced Mode",
                            fontWeight = FontWeight.SemiBold
                        ) **/
                },
                navigationIcon = {
                    /** IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate Back"
                        )
                    } **/
                },
                // Make the top bar transparent to blend with the background
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            // 5. The Bottom Control Bar (from your background_setup_controls.xml)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                // Match the rounded top corners from your XML
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                // Using dynamic color for the bottom sheet area
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        // Add some navigation bar padding so it doesn't overlap gestures
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "We'll help you set up the blocking on your browser and apps. Stay with us!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(end = 16.dp)
                    )

                    // The Extended FAB styled button
                    Button(
                        onClick = onNextClick,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Next")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Skip",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp) // Automatically adds space between items!
        ) {

            // Item 1: The large header title
            item {
                Text(
                    text = "Welcome to ADDNS",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }

            // Item 2: The Switch Card
            /** item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    // This creates the lighter card background using Dynamic Colors
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Enhanced Mode",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Requires Shizuku or Sui",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = isEnhancedModeEnabled,
                            onCheckedChange = { isEnhancedModeEnabled = it }
                        )
                    }
                }
            }

            // Item 3: The Info Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    // This creates a slightly darker card background
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Enhanced Mode allows Smartspacer to:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            // Bulleted list
                            val infoItems = listOf(
                                "Integrate into the Smartspace on the Lock Screen",
                                "Integrate into the OEM Smartspace (if available)",
                                "Use system App Predictions as a Requirement",
                                "Use Recent Apps as a Requirement"
                            )

                            infoItems.forEach { item ->
                                Text(
                                    text = "• $item",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                } **/

            item {
                Text(
                    text = "The best Ad blocker in the universe. No root needed!",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
@Preview
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivationMethod(
    onBackClick: () -> Unit = {},
    onNextClick: (shizuku: Boolean, adb: Boolean) -> Unit = { _, _ -> }
) {

    var ShizukuPressed by remember { mutableStateOf(false) }
    var AdbPressed by remember { mutableStateOf(false) }

        // 4. Scaffold is the skeleton of our screen
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    /** Text(
                    text = "Enhanced Mode",
                    fontWeight = FontWeight.SemiBold
                    ) **/
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate Back"
                        )
                    }
                },
                // Make the top bar transparent to blend with the background
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            // 5. The Bottom Control Bar (from your background_setup_controls.xml)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                // Match the rounded top corners from your XML
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                // Using dynamic color for the bottom sheet area
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        // Add some navigation bar padding so it doesn't overlap gestures
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Please choose one option.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(end = 16.dp)
                    )

                    // The Extended FAB styled button
                    Button(
                        onClick = { onNextClick(ShizukuPressed, AdbPressed) },
                        shape = RoundedCornerShape(12.dp),
                        enabled = if (ShizukuPressed || AdbPressed) true else false
                    ) {
                        Text("Next")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp) // Automatically adds space between items!
        ) {

            // Item 1: The large header title
            item {
                Text(
                    text = "Activation",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            item {
                Text(
                    text = "Please choose an activation method.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Item 2: The Switch Card
            item {
                val color by animateColorAsState(
                    targetValue = if (!ShizukuPressed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    label = ""
                )
                Card(
                    shape = RoundedCornerShape(16.dp),
                    // This creates the lighter card background using Dynamic Colors
                    colors = CardDefaults.cardColors(
                        containerColor = color // if (!ShizukuPressed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    onClick = {
                        ShizukuPressed = !ShizukuPressed
                        AdbPressed = false
                    },
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Shizuku",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (!ShizukuPressed) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.secondaryContainer
                            )
                            Text(
                                text = "Easiest method. Requires Shizuku or Sui installed and set up.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (!ShizukuPressed) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.secondaryContainer
                            )
                        }
                    }
                }
                val color2 by animateColorAsState(
                    targetValue = if (!AdbPressed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    label = ""
                )
                Card(
                    shape = RoundedCornerShape(16.dp),
                    // This creates the lighter card background using Dynamic Colors
                    colors = CardDefaults.cardColors(
                        containerColor = color2
                    ),
                    onClick = {
                        AdbPressed = !AdbPressed
                        ShizukuPressed = false
                    },
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "ADB Shell",
                                style = MaterialTheme.typography.titleMedium,

                            )
                            Text(
                                text = "Requires ADB shell access, usually with a computer, and a little bit of technical knowledge.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }




            }
    }
}

@Preview
@ExperimentalMaterial3ExpressiveApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbActivation(onBackClick: () -> Unit = {}) {
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    /** Text(
                    text = "Enhanced Mode",
                    fontWeight = FontWeight.SemiBold
                    ) **/
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate Back"
                        )
                    }
                },
                // Make the top bar transparent to blend with the background
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            // 5. The Bottom Control Bar (from your background_setup_controls.xml)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                // Match the rounded top corners from your XML
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                // Using dynamic color for the bottom sheet area
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        // Add some navigation bar padding so it doesn't overlap gestures
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Waiting for permission...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(end = 16.dp)
                    )

                    // The Extended FAB styled button
                    /** Button(
                        onClick = { onNextClick(ShizukuPressed, AdbPressed) },
                        shape = RoundedCornerShape(12.dp),
                        enabled = if (ShizukuPressed || AdbPressed) true else false
                    ) {
                        Text("Next")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next",
                            modifier = Modifier.size(18.dp)
                        )
                    } **/

                    LoadingIndicator()
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp) // Automatically adds space between items!
        ) {
            item {
                Text(
                    text = "Activation",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            item {
                Text(
                    text = "Paste the following command into your terminal:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item{
                Card(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "adb shell pm grant com.eyalm.addns android.permission.WRITE_SECURE_SETTINGS",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString("adb shell pm grant com.eyalm.addns android.permission.WRITE_SECURE_SETTINGS"))

                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "copy",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer

                            )
                        }
                    }
                }
            }
            item {
                Text(
                    text = "Don't worry! It's completely safe. After you ran the command, come back here.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


/**
@Composable
fun IntroScreen(onNext: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            IconButton(onClick = {  }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Welcome to Addns",
                fontSize = 32.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
**/