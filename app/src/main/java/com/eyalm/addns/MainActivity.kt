package com.eyalm.addns

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import android.provider.Settings
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.eyalm.addns.ui.theme.AddnsTheme
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.OnRequestPermissionResultListener


class MainActivity : ComponentActivity() {

    private var privilegedService: IPrivilegedService? = null
    private val connection = object : android.content.ServiceConnection {
        override fun onServiceConnected(
            name: android.content.ComponentName?,
            binder: android.os.IBinder?
        ) {
            privilegedService = IPrivilegedService.Stub.asInterface(binder)

            Log.d("shizuku", "Service connected")

            try {
                privilegedService?.grantWriteSecureSettings(packageName)
                Log.d("shizuku", "Permission granted")
            } catch (e: Exception) {
                Log.e("shizuku", "Failed: ${e.message}")
            }
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            privilegedService = null
        }
    }
    companion object {
        internal const val INIT: Int = 0
        internal const val ENABLE_DNS: Int = 1
        internal const val REQUEST_WRITE_SECURE_SETTINGS = 2
    }
    private fun bindPrivilegedService() {
        if (privilegedService != null) return
        
        Log.d("shizuku", "Binding to privileged service...")
        val componentName = android.content.ComponentName(
            packageName,
            PrivilegedService::class.java.name
        )
        val args = rikka.shizuku.Shizuku.UserServiceArgs(componentName)
            .processNameSuffix("service")
            .debuggable(true)
            .daemon(false)

        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            @Suppress("DEPRECATION")
            args.version(packageInfo.versionCode)
        } catch (e: Exception) {
            Log.e("shizuku", "Failed to get version code: ${e.message}")
        }
        
        try {
            rikka.shizuku.Shizuku.bindUserService(args, connection)
            Log.d("shizuku", "bindUserService called")
        } catch (e: Exception) {
            Log.e("shizuku", "bindUserService failed: ${e.message}")
        }
    }

    private fun onRequestPermissionsResult(requestCode: Int, grantResult: Int) {
        val granted = grantResult == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            Log.w("shizuku", "Permission denied for request code: $requestCode")
            return
        }

        when (requestCode) {
            INIT -> {
                Log.d("shizuku", "Shizuku permission granted")
                bindPrivilegedService()
            }
            REQUEST_WRITE_SECURE_SETTINGS -> {
                Log.d("shizuku", "Write Secure Settings permission requested via Shizuku")
                bindPrivilegedService()
            }
            ENABLE_DNS -> Log.d("shizuku", "enabling dns")
            else -> {
                Log.w("shizuku", "Unknown request code: $requestCode, binding service anyway")
                bindPrivilegedService()
            }
        }
    }
    
    private fun checkWriteSecureSettingsPermission(): Boolean {
        return checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    }

    private fun grantWriteSecureSettings() {
        privilegedService?.grantWriteSecureSettings(packageName)
    }


    private val REQUEST_PERMISSION_RESULT_LISTENER = OnRequestPermissionResultListener { requestCode, grantResult ->
        onRequestPermissionsResult(requestCode, grantResult)
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        if (checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER)
        val hasperm = checkWriteSecureSettingsPermission()
        Log.d("shizuku", "hasperm: $hasperm")
        setContent {
            AddnsTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        onShizukuClick = { code ->
                            if (checkPermission(code)) {
                                onRequestPermissionsResult(code, PackageManager.PERMISSION_GRANTED)
                            }
                        },
                        onTestClick = { testWriteSecureSettings() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER)
        super.onDestroy()

    }

    private fun checkPermission(code: Int): Boolean {
        if (Shizuku.isPreV11()) {
            return false
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            return true
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            // Users choose "Deny and don't ask again"
            return false
        } else {
            // Request the permission
            Shizuku.requestPermission(code)
            return false
        }
    }

    fun testWriteSecureSettings(): Boolean {
        return try {
            Settings.Secure.putString(
                contentResolver,
                "test_key",
                "hello"
            )
            Log.d("perm", "WRITE_SECURE_SETTINGS WORKS")
            true
        } catch (e: Exception) {
            Log.e("perm", "FAILED: ${e.message}")
            false
        }
    }
}

@Composable
fun Greeting(name: String, onShizukuClick: (code: Int) -> Unit, onTestClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Hello $name!",
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Button(
            onClick = { onShizukuClick(MainActivity.INIT) },
            contentPadding = ButtonDefaults.ContentPadding
        ) { Text("init") }
        Button(
            onClick = { onShizukuClick(MainActivity.REQUEST_WRITE_SECURE_SETTINGS) },
            contentPadding = ButtonDefaults.ContentPadding
        ) { Text("request permission")}
        Button(
            onClick = onTestClick,
            contentPadding = ButtonDefaults.ContentPadding,
        ) { Text("Test perm")}
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AddnsTheme {
        Greeting("Android", onShizukuClick = {}, onTestClick = {})
    }
}
