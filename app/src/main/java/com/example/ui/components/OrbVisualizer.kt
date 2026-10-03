package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LiveVoiceState
import com.example.ui.theme.ArushiError
import com.example.ui.theme.ArushiPrimary
import com.example.ui.theme.ArushiPrimaryLight
import com.example.ui.theme.ArushiSecondary
import com.example.ui.theme.ArushiSuccess
import com.example.ui.theme.ArushiTertiary
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun OrbVisualizer(
    voiceState: LiveVoiceState,
    amplitude: Float,
    isMicActive: Boolean,
    onOrbClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_rotation"
    )

    // Dynamic color matching voice state
    val (primaryColor, glowColor, stateLabel) = when (voiceState) {
        LiveVoiceState.LISTENING -> Triple(ArushiSecondary, Color(0x6606B6D4), "Listening to you...")
        LiveVoiceState.SPEAKING -> Triple(ArushiPrimary, Color(0x668B5CF6), "Arushi speaking...")
        LiveVoiceState.THINKING -> Triple(ArushiTertiary, Color(0x66F59E0B), "Thinking...")
        LiveVoiceState.EXECUTING_ACTION -> Triple(ArushiSuccess, Color(0x6610B981), "Executing action...")
        LiveVoiceState.ERROR -> Triple(ArushiError, Color(0x66EF4444), "Connection issue")
        LiveVoiceState.CONNECTING -> Triple(ArushiTertiary, Color(0x66F59E0B), "Connecting to Live...")
        LiveVoiceState.IDLE -> Triple(ArushiPrimaryLight, Color(0x338B5CF6), if (isMicActive) "Listening" else "Tap orb to speak")
    }

    val dynamicAmp = (amplitude * 0.4f).coerceIn(0f, 0.5f)
    val effectiveScale = pulseScale + dynamicAmp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(240.dp)
                .testTag("arushi_orb_visualizer")
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOrbClick
                )
        ) {
            // Background Canvas: Pulsing ripple rings and audio reactive bars
            Canvas(modifier = Modifier.size(240.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val baseRadius = size.width / 2.6f * effectiveScale

                // Outer ambient glow ring
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent),
                        center = center,
                        radius = baseRadius * 1.35f
                    ),
                    center = center,
                    radius = baseRadius * 1.35f
                )

                // Middle pulse ring
                drawCircle(
                    color = primaryColor.copy(alpha = 0.25f),
                    center = center,
                    radius = baseRadius * 1.15f,
                    style = Stroke(width = 2.5.dp.toPx())
                )

                // Reactive audio wave spikes
                val spikes = 24
                for (i in 0 until spikes) {
                    val angleDeg = (i * 360f / spikes) + rotationAngle
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    val spikeLength = (10.dp.toPx() + amplitude * 35.dp.toPx()) * (if (i % 2 == 0) 1f else 0.6f)

                    val startX = (center.x + baseRadius * cos(angleRad)).toFloat()
                    val startY = (center.y + baseRadius * sin(angleRad)).toFloat()
                    val endX = (center.x + (baseRadius + spikeLength) * cos(angleRad)).toFloat()
                    val endY = (center.y + (baseRadius + spikeLength) * sin(angleRad)).toFloat()

                    drawLine(
                        color = primaryColor.copy(alpha = 0.5f + (amplitude * 0.5f)),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = 3.dp.toPx()
                    )
                }
            }

            // Core Glowing Orb
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(150.dp)
                    .scale(effectiveScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.9f),
                                primaryColor,
                                Color(0xFF0F0B1E)
                            )
                        )
                    )
            ) {
                val icon = when (voiceState) {
                    LiveVoiceState.LISTENING -> Icons.Default.Mic
                    LiveVoiceState.SPEAKING -> Icons.Default.GraphicEq
                    LiveVoiceState.THINKING -> Icons.Default.Psychology
                    LiveVoiceState.EXECUTING_ACTION -> Icons.Default.SmartToy
                    else -> if (isMicActive) Icons.Default.Mic else Icons.Default.MicOff
                }

                Icon(
                    imageVector = icon,
                    contentDescription = stateLabel,
                    tint = Color.White,
                    modifier = Modifier.size(54.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // State indicator label
        Text(
            text = stateLabel,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            ),
            color = primaryColor
        )
    }
}
