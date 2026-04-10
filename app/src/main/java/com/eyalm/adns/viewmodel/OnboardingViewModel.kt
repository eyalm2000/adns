package com.eyalm.adns.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eyalm.adns.BuildConfig
import com.eyalm.adns.IPrivilegedService
import com.eyalm.adns.OnboardingActivity
import com.eyalm.adns.PrivilegedService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private var privilegedService: IPrivilegedService? = null
    
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            privilegedService = IPrivilegedService.Stub.asInterface(binder)
            Log.d("shizuku", "Service connected")
            try {
                privilegedService?.grantWriteSecureSettings(getApplication<Application>().packageName)
                Log.d("shizuku", "Permission granted")
            } catch (e: Exception) {
                Log.e("shizuku", "Failed to grant permission: ${e.message}")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            privilegedService = null
        }
    }

    var currentStep by mutableStateOf(OnboardingActivity.Step.INTRO)
        private set

    var isPermissionGranted by mutableStateOf(false)
        private set

    private val REQUEST_PERMISSION_RESULT_LISTENER =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            onRequestPermissionsResult(requestCode, grantResult)
        }

    fun nextStep() {
        currentStep = when (currentStep) {
            OnboardingActivity.Step.INTRO -> OnboardingActivity.Step.ACTIVATION_METHOD
            OnboardingActivity.Step.ACTIVATION_METHOD -> OnboardingActivity.Step.ADB
            OnboardingActivity.Step.ADB -> OnboardingActivity.Step.SUCCESS
            OnboardingActivity.Step.SHIZUKU -> OnboardingActivity.Step.SUCCESS
            OnboardingActivity.Step.SUCCESS -> OnboardingActivity.Step.INTRO
        }
    }

    fun goToShizuku() {
        currentStep = OnboardingActivity.Step.SHIZUKU
    }
    fun previousStep() {
        currentStep = when (currentStep) {
            OnboardingActivity.Step.ADB -> OnboardingActivity.Step.ACTIVATION_METHOD
            OnboardingActivity.Step.ACTIVATION_METHOD -> OnboardingActivity.Step.INTRO
            OnboardingActivity.Step.SHIZUKU -> OnboardingActivity.Step.ACTIVATION_METHOD
            OnboardingActivity.Step.INTRO -> OnboardingActivity.Step.INTRO
            OnboardingActivity.Step.SUCCESS -> OnboardingActivity.Step.INTRO
        }
    }

    fun startPermissionCheck(context: Context) {
        viewModelScope.launch {
            while (!isPermissionGranted) {
                val granted = context.checkSelfPermission(
                    android.Manifest.permission.WRITE_SECURE_SETTINGS
                ) == PackageManager.PERMISSION_GRANTED

                if (granted) {
                    isPermissionGranted = true
                    Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER)
                    nextStep()
                }
                delay(1000)
            }
        }
    }

    private fun bindPrivilegedService() {
        if (privilegedService != null) return

        Log.d("shizuku", "Binding to privileged service...")
        val context = getApplication<Application>()
        val componentName = ComponentName(
            context.packageName,
            PrivilegedService::class.java.name
        )
        val args = Shizuku.UserServiceArgs(componentName)
            .processNameSuffix("service")
            .debuggable(BuildConfig.DEBUG)
            .daemon(false)

        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            args.version(packageInfo.versionCode)
        } catch (e: Exception) {
            Log.e("shizuku", "Failed to get version code: ${e.message}")
        }

        try {
            Shizuku.bindUserService(args, connection)
            Log.d("shizuku", "bindUserService called")
        } catch (e: Exception) {
            Log.e("shizuku", "bindUserService failed: ${e.message}")
        }
    }

    private fun checkPermission(code: Int): Boolean {
        if (Shizuku.isPreV11()) return false

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            return true
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            return false
        } else {
            Shizuku.requestPermission(code)
            return false
        }
    }

    private fun onRequestPermissionsResult(requestCode: Int, grantResult: Int): Boolean {
        if (requestCode != 1) return false

        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Log.w("shizuku", "Permission denied for request code: $requestCode")
            previousStep()
            return false
        }

        bindPrivilegedService()
        return true
    }

    fun writePermissionShizuku(context: Context) {
        viewModelScope.launch {
            Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER)
            val permission = checkPermission(1)
            if (permission == true) {
                onRequestPermissionsResult(1, PackageManager.PERMISSION_GRANTED)
            }
            startPermissionCheck(context)
        }
    }

    override fun onCleared() {
        super.onCleared()
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER)
    }
}
