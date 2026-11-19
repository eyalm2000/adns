package com.eyalm.adns

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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eyalm.adns.ui.screens.providerLogin.Login
import com.eyalm.adns.data.Locales
import com.eyalm.adns.ui.security.setProtectedContent
import com.eyalm.adns.data.localization.AppLocaleRepository
import com.eyalm.adns.ui.screens.providerLogin.ProfileOptionPage
import com.eyalm.adns.ui.screens.providerLogin.SuccessLoginScreen
import com.eyalm.adns.ui.theme.AdnsTheme
import com.eyalm.adns.viewmodel.ProviderLoginViewModel
import com.eyalm.adns.viewmodel.ProviderLoginResult
import com.eyalm.adns.viewmodel.ProviderLoginStep

class ProviderLoginActivity : AppCompatActivity() {
    companion object {
        /** Set when another account is added to an app that is already signed in. */
        const val EXTRA_ADD_ACCOUNT = "add_account"
    }


    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Locales.sync(this, AppLocaleRepository(this).selectedTag())
        enableEdgeToEdge()


        setProtectedContent {

            val viewModel: ProviderLoginViewModel = viewModel()
            LaunchedEffect(viewModel) {
                viewModel.configure(addAccount = intent.getBooleanExtra(EXTRA_ADD_ACCOUNT, false))
            }

            AdnsTheme {
                val flowState by viewModel.flowState.collectAsState()
                val step = flowState.step
                val profiles = viewModel.profiles

                LaunchedEffect(viewModel) {
                    viewModel.results.collect { result ->
                        when (result) {
                            is ProviderLoginResult.Completed,
                            ProviderLoginResult.Cancelled,
                            -> finish()
                        }
                    }
                }


                Surface(modifier = Modifier.fillMaxSize()) {
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
                                            animationSpec = tween(
                                                300,
                                                easing = FastOutSlowInEasing
                                            ),
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
                            ProviderLoginStep.Login -> {
                                Login(
                                    onBackClick = {
                                        viewModel.cancel()
                                    }
                                )
                            }

                            ProviderLoginStep.Profile -> {
                                BackHandler { viewModel.back() }
                                ProfileOptionPage(
                                    profiles = profiles,
                                    onNextClick = { profile ->
                                        viewModel.setProfile(profile)
                                    },
                                    createProfile = { name ->
                                        viewModel.createProfile(name)
                                    }
                                )
                            }

                            ProviderLoginStep.Success -> {
                                BackHandler { viewModel.back() }
                                SuccessLoginScreen(
                                    onFinishClicked = {
                                        viewModel.commitSelectedProfile()
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
