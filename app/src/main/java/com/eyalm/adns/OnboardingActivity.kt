package com.eyalm.adns

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eyalm.adns.data.activation.ActivationMode
import com.eyalm.adns.data.Locales
import com.eyalm.adns.ui.security.setProtectedContent
import com.eyalm.adns.data.localization.AppLocaleRepository
import com.eyalm.adns.data.provider.DnsProviderCatalog
import com.eyalm.adns.data.provider.DnsProviderSelection
import com.eyalm.adns.ui.screens.onboarding.ActivationMethodScreen
import com.eyalm.adns.ui.screens.onboarding.AdbActivationScreen
import com.eyalm.adns.ui.screens.onboarding.OnboardingActivationModeScreen
import com.eyalm.adns.ui.screens.onboarding.OnboardingPresetScreen
import com.eyalm.adns.ui.screens.onboarding.OnboardingProviderScreen
import com.eyalm.adns.ui.screens.onboarding.ShizukuActivationScreen
import com.eyalm.adns.ui.screens.onboarding.SuccessScreen
import com.eyalm.adns.ui.screens.onboarding.WelcomeScreen
import com.eyalm.adns.ui.screens.providerLogin.Login
import com.eyalm.adns.ui.screens.providerLogin.ProfileOptionPage
import com.eyalm.adns.ui.theme.AdnsTheme
import com.eyalm.adns.viewmodel.OnboardingIntent
import com.eyalm.adns.viewmodel.OnboardingStep
import com.eyalm.adns.viewmodel.OnboardingViewModel
import com.eyalm.adns.viewmodel.PrivilegedMethod
import com.eyalm.adns.viewmodel.ProviderLoginStep
import com.eyalm.adns.viewmodel.ProviderLoginViewModel

class OnboardingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Locales.sync(this, AppLocaleRepository(this).selectedTag())
        enableEdgeToEdge()
        setProtectedContent {
            val onboardingViewModel: OnboardingViewModel = viewModel()
            val providerLoginViewModel: ProviderLoginViewModel = viewModel()
            val onboardingState by onboardingViewModel.state.collectAsState()
            val permissionState by onboardingViewModel.permissionState.collectAsState()
            val providerLoginState by providerLoginViewModel.flowState.collectAsState()

            LaunchedEffect(onboardingViewModel) {
                onboardingViewModel.completion.collect {
                    startActivity(Intent(this@OnboardingActivity, MainActivity::class.java))
                    finish()
                }
            }

            AdnsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = onboardingState.step,
                        transitionSpec = {
                            if (targetState.ordinal > initialState.ordinal) {
                                (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                                    scaleIn(
                                        initialScale = 0.92f,
                                        animationSpec = tween(300),
                                    ) +
                                    slideIntoContainer(
                                        towards = AnimatedContentTransitionScope.SlideDirection.Up,
                                        animationSpec = tween(
                                            300,
                                            easing = FastOutSlowInEasing,
                                        ),
                                        initialOffset = { it / 8 },
                                    )) togetherWith
                                    (fadeOut(animationSpec = tween(90)) +
                                        scaleOut(
                                            targetScale = 1.08f,
                                            animationSpec = tween(300),
                                        ))
                            } else {
                                (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                                    scaleIn(
                                        initialScale = 1.08f,
                                        animationSpec = tween(300),
                                    )) togetherWith
                                    (fadeOut(animationSpec = tween(90)) +
                                        scaleOut(
                                            targetScale = 0.92f,
                                            animationSpec = tween(300),
                                        ) +
                                        slideOutOfContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.Down,
                                            animationSpec = tween(
                                                300,
                                                easing = FastOutSlowInEasing,
                                            ),
                                            targetOffset = { it / 8 },
                                        ))
                            }.using(SizeTransform(clip = false))
                        },
                        label = "onboarding_step_transition",
                    ) { step ->
                        val back = {
                            onboardingViewModel.dispatch(OnboardingIntent.Back)
                        }
                        when (step) {
                            OnboardingStep.Welcome -> WelcomeScreen(
                                onNextClick = {
                                    onboardingViewModel.dispatch(OnboardingIntent.Continue)
                                }
                            )

                            OnboardingStep.Provider -> {
                                BackHandler(onBack = back)
                                OnboardingProviderScreen(
                                    currentSelection = onboardingState.draft.providerSelection,
                                    onBack = back,
                                    onSelected = { selection ->
                                        onboardingViewModel.dispatch(
                                            OnboardingIntent.ProviderSelected(selection)
                                        )
                                    },
                                )
                            }

                            OnboardingStep.Preset -> {
                                BackHandler(onBack = back)
                                val selection = onboardingState.draft.providerSelection
                                    as? DnsProviderSelection.Standard
                                val provider = selection?.let {
                                    DnsProviderCatalog.default.standardProvider(it.providerId)
                                }
                                if (selection != null && provider != null) {
                                    OnboardingPresetScreen(
                                        provider = provider,
                                        current = selection,
                                        onBack = back,
                                        onSelected = {
                                            onboardingViewModel.dispatch(
                                                OnboardingIntent.PresetSelected(it)
                                            )
                                        },
                                    )
                                }
                            }

                            OnboardingStep.ProviderLogin -> {
                                BackHandler(onBack = back)
                                LaunchedEffect(Unit) {
                                    providerLoginViewModel.resumeSession()
                                }
                                when (providerLoginState.step) {
                                    ProviderLoginStep.Login -> Login(
                                        onBackClick = back,
                                        viewModel = providerLoginViewModel,
                                    )

                                    ProviderLoginStep.Profile -> ProfileOptionPage(
                                        profiles = providerLoginViewModel.profiles,
                                        onNextClick = { profile ->
                                            providerLoginViewModel.setProfile(profile)
                                            onboardingViewModel.selectNextDnsProfile(profile)
                                        },
                                        createProfile = providerLoginViewModel::createProfile,
                                    )

                                    ProviderLoginStep.Success -> Unit
                                }
                            }

                            OnboardingStep.ActivationMode -> {
                                val activationModeBack = {
                                    providerLoginViewModel.back()
                                    back()
                                }
                                BackHandler(onBack = activationModeBack)
                                OnboardingActivationModeScreen(
                                    selectedMode = onboardingState.draft.mode,
                                    onBack = activationModeBack,
                                    onSelected = { mode ->
                                        onboardingViewModel.dispatch(
                                            OnboardingIntent.ModeSelected(mode)
                                        )
                                    },
                                )
                            }

                            OnboardingStep.ActivationMethod -> {
                                BackHandler(onBack = back)
                                ActivationMethodScreen(
                                    onBackClick = back,
                                    onNextClick = { shizuku, adb ->
                                        val method = when {
                                            adb -> PrivilegedMethod.Adb
                                            shizuku -> PrivilegedMethod.Shizuku
                                            else -> return@ActivationMethodScreen
                                        }
                                        onboardingViewModel.dispatch(
                                            OnboardingIntent.ActivationMethodSelected(method)
                                        )
                                    },
                                )
                            }

                            OnboardingStep.Adb -> {
                                BackHandler(onBack = back)
                                AdbActivationScreen(
                                    onBack = back,
                                    onStartMonitoring = onboardingViewModel::startPermissionCheck,
                                    onStopMonitoring = onboardingViewModel::stopPermissionCheck,
                                )
                            }

                            OnboardingStep.Shizuku -> {
                                BackHandler(onBack = back)
                                ShizukuActivationScreen(
                                    state = permissionState,
                                    onBack = back,
                                    onStart = onboardingViewModel::requestShizukuActivation,
                                    onStop = onboardingViewModel::stopPermissionAcquisition,
                                )
                            }

                            OnboardingStep.Success -> {
                                BackHandler(onBack = back)
                                SuccessScreen(onFinishClicked = onboardingViewModel::finish)
                            }
                        }
                    }
                }
            }
        }
    }
}
