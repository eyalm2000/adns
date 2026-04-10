package com.eyalm.addns

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eyalm.addns.ui.theme.AddnsTheme
import com.eyalm.addns.viewmodel.MainViewModel
import rikka.shizuku.Shizuku


class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()


    override fun onCreate(savedInstanceState: Bundle?) {
        if (checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AddnsTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        onTestClick = { testWriteSecureSettings() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberAnimatedShape(
    morph: Morph,
    progress: Float
): Shape {
    return remember(morph, progress) {
        object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline {
                val path = morph.toPath(progress).asComposePath()
                val matrix = Matrix()
                matrix.scale(size.width, size.height)
                path.transform(matrix)
                path.translate(size.center - path.getBounds().center)
                return Outline.Generic(path)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Greeting(name: String, onTestClick: () -> Unit, modifier: Modifier = Modifier, viewModel: MainViewModel = viewModel()) {
    val isEnabled by viewModel.adBlockingState.collectAsState()

    val backgroundColor by animateColorAsState(
        targetValue = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        label = "backgroundColor",
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )
    val contentColor by animateColorAsState(
        targetValue = if (isEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onError,
        label = "contentColor",
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    val shapeProgress by animateFloatAsState(
        targetValue = if (isEnabled) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "shapeProgress"
    )

    val morph = remember {
        Morph(MaterialShapes.Cookie6Sided, MaterialShapes.Pill)
    }
    
    val animatedShape = rememberAnimatedShape(morph, shapeProgress)

    Column(modifier = modifier.padding(16.dp)) {
        Box(
            modifier = Modifier
                .padding(horizontal = 48.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(backgroundColor, animatedShape)
                .clip(animatedShape)
                .clickable { viewModel.toggleDns() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                modifier = Modifier.fillMaxSize(0.6f),
                imageVector = if (isEnabled) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                tint = contentColor
            )
        }

        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // הצגת ה-State
            Text(
                text = if (isEnabled) "Status: ACTIVE" else "Status: OFF",
                style = MaterialTheme.typography.headlineMedium,
                color = if (isEnabled) Color.Green else Color.Red
            )

            Spacer(modifier = Modifier.height(16.dp))

            // כפתור ה-Toggle
            Button(onClick = { viewModel.toggleDns() }) {
                Text(if (isEnabled) "Turn OFF" else "Turn ON")
            }
        }
    }
}