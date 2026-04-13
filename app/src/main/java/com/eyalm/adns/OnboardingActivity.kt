package com.eyalm.adns

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eyalm.adns.ui.screens.ActivationMethodScreen
import com.eyalm.adns.ui.screens.AdbActivationScreen
import com.eyalm.adns.ui.screens.ShizukuActivationScreen
import com.eyalm.adns.ui.screens.SuccessScreen
import com.eyalm.adns.ui.screens.WelcomeScreen
import com.eyalm.adns.ui.theme.AdnsTheme
import com.eyalm.adns.viewmodel.OnboardingViewModel

class OnboardingActivity : ComponentActivity() {
    enum class Step { INTRO , ACTIVATION_METHOD, ADB, SHIZUKU, SUCCESS }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: OnboardingViewModel = viewModel()
            AdnsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val step = viewModel.currentStep

                    AnimatedContent(
                        targetState = step,
                        transitionSpec = {
                            if (targetState.ordinal > initialState.ordinal) {
                                (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                                        scaleIn(
                                            initialScale = 0.92f,
                                            animationSpec = tween(300)
                                        ) +
                                        slideIntoContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.Up,
                                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                                            initialOffset = { it / 8 }
                                        )) togetherWith
                                        (fadeOut(animationSpec = tween(90)) +
                                                scaleOut(
                                                    targetScale = 1.08f,
                                                    animationSpec = tween(300)
                                                ))
                            } else {
                                (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                                        scaleIn(
                                            initialScale = 1.08f,
                                            animationSpec = tween(300)
                                        )) togetherWith
                                        (fadeOut(animationSpec = tween(90)) +
                                                scaleOut(
                                                    targetScale = 0.92f,
                                                    animationSpec = tween(300)
                                                ) +
                                                slideOutOfContainer(
                                                    towards = AnimatedContentTransitionScope.SlideDirection.Down,
                                                    animationSpec = tween(
                                                        300,
                                                        easing = FastOutSlowInEasing
                                                    ),
                                                    targetOffset = { it / 8 }
                                                ))
                            }.using(
                                SizeTransform(clip = false)
                            )
                        },
                        label = "onboarding_step_transition"
                    ) { targetStep ->
                        when (targetStep) {
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
                                        startActivity(
                                            Intent(
                                                this@OnboardingActivity,
                                                MainActivity::class.java
                                            )
                                        )
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
}
