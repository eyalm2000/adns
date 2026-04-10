package com.eyalm.addns

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eyalm.addns.ui.screens.ActivationMethodScreen
import com.eyalm.addns.ui.screens.AdbActivationScreen
import com.eyalm.addns.ui.screens.ShizukuActivationScreen
import com.eyalm.addns.ui.screens.SuccessScreen
import com.eyalm.addns.ui.screens.WelcomeScreen
import com.eyalm.addns.ui.theme.AddnsTheme
import com.eyalm.addns.viewmodel.OnboardingViewModel

class OnboardingActivity : ComponentActivity() {
    enum class Step { INTRO , ACTIVATION_METHOD, ADB, SHIZUKU, SUCCESS }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: OnboardingViewModel = viewModel()
            AddnsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val step = viewModel.currentStep

                    when (step) {
                        Step.INTRO -> WelcomeScreen(
                            onNextClick = { viewModel.nextStep() }
                        )
                        Step.ACTIVATION_METHOD -> {
                            BackHandler { viewModel.previousStep() }

                            ActivationMethodScreen(
                                onBackClick = { viewModel.previousStep() },
                                onNextClick = { _, isAdb ->
                                    if (isAdb) viewModel.nextStep()
                                    else viewModel.goToShizuku()
                                }
                            )
                        }
                        Step.ADB -> {
                            BackHandler { viewModel.previousStep() }

                            AdbActivationScreen(
                                onBack = { viewModel.previousStep() }
                            )
                        }
                        Step.SHIZUKU -> {
                            BackHandler { viewModel.previousStep() }

                            ShizukuActivationScreen()
                        }
                        Step.SUCCESS -> {
                            BackHandler { viewModel.previousStep() }
                            SuccessScreen(
                                onFinishClicked = {
                                    startActivity(Intent(this@OnboardingActivity, MainActivity::class.java))
                                    finish()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
