package com.eyalm.adns.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath

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
fun DnsSwitch(
    isEnabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
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

    val morph = remember { Morph(DnsShapes.Cookie4, DnsShapes.Cookie9) }
    val animatedShape = rememberAnimatedShape(morph, shapeProgress)

    val rotation = remember { Animatable(0f) }
    val transitionDuration = 1500
    val continuousTurnDuration = 15000
    val smoothHandoffEasing = remember {

        androidx.compose.animation.core.CubicBezierEasing(0.4f, 0.0f, 0.2f, 0.96f)
    }

    LaunchedEffect(isEnabled) {
        rotation.snapTo(rotation.value % 360f)

        if (isEnabled) {
            val startAngle = rotation.value
            val startupDelta = 720f
            val startupDurationNanos = transitionDuration * 1_000_000L
            val steadyDegreesPerNano = 360f / (continuousTurnDuration * 1_000_000f)
            var startNanos = 0L

            while (true) {
                val frameNanos = androidx.compose.runtime.withFrameNanos { it }
                if (startNanos == 0L) startNanos = frameNanos

                val elapsedNanos = frameNanos - startNanos
                val angle = if (elapsedNanos <= startupDurationNanos) {
                    val t = (elapsedNanos.toFloat() / startupDurationNanos).coerceIn(0f, 1f)
                    startAngle + startupDelta * smoothHandoffEasing.transform(t)
                } else {
                    val afterStartupNanos = elapsedNanos - startupDurationNanos
                    startAngle + startupDelta + (afterStartupNanos * steadyDegreesPerNano)
                }
                rotation.snapTo(angle)
            }
        } else {
            val targetAngle = 360f

            rotation.animateTo(
                targetValue = targetAngle,
                animationSpec = tween(durationMillis = transitionDuration, easing = FastOutSlowInEasing)
            )
        }
    }

    Box(
        modifier = modifier
            .padding(horizontal = 48.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer { rotationZ = rotation.value }
            .background(backgroundColor, animatedShape)
            .clip(animatedShape)
            .clickable { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            modifier = Modifier
                .fillMaxSize(0.6f)
                .graphicsLayer { rotationZ = -rotation.value },
            imageVector = if (isEnabled) Icons.Filled.Shield else Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = contentColor
        )
    }
}