package com.eyalm.addns.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eyalm.addns.OnboardingActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OnboardingViewModel : ViewModel() {

    var currentStep by mutableStateOf(OnboardingActivity.Step.INTRO)
        private set

    var isPermissionGranted by mutableStateOf(false)
        private set

    fun nextStep() {
        currentStep = when (currentStep) {
            OnboardingActivity.Step.INTRO -> OnboardingActivity.Step.ACTIVATION_METHOD
            OnboardingActivity.Step.ACTIVATION_METHOD -> OnboardingActivity.Step.ADB
            OnboardingActivity.Step.ADB -> OnboardingActivity.Step.SUCCESS
            OnboardingActivity.Step.SUCCESS -> OnboardingActivity.Step.INTRO
        }
    }

    fun previousStep() {
        currentStep = when (currentStep) {
            OnboardingActivity.Step.ADB -> OnboardingActivity.Step.ACTIVATION_METHOD
            OnboardingActivity.Step.ACTIVATION_METHOD -> OnboardingActivity.Step.INTRO
            OnboardingActivity.Step.INTRO -> OnboardingActivity.Step.INTRO
            OnboardingActivity.Step.SUCCESS -> OnboardingActivity.Step.INTRO
        }
    }

    fun startPermissionCheck(context: Context) {
        viewModelScope.launch {
            while (!isPermissionGranted) {
                val granted = context.checkSelfPermission(
                    android.Manifest.permission.WRITE_SECURE_SETTINGS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (granted) {
                    isPermissionGranted = true
                    nextStep()
                }
                delay(1000)
            }
        }
    }
}