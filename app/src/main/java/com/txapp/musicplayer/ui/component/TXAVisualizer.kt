package com.txapp.musicplayer.ui.component

import android.media.audiofx.Visualizer
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import com.txapp.musicplayer.ui.MainActivity
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.txapp.musicplayer.util.txa
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.txapp.musicplayer.service.MusicService
import com.txapp.musicplayer.util.TXALogger
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * TXA Music Visualizer - Premium audio visualization for Now Playing screen
 * Supports multiple visualization styles: Bars, Wave, Circle, Spectrum
 */

enum class VisualizerStyle {
    BARS,           // Classic bar equalizer
    WAVE,           // Smooth waveform
    CIRCLE,         // Circular spectrum
    SPECTRUM,       // Mirrored spectrum bars
    GLOW_BARS       // Bars with glow effect
}

@Composable
fun TXAVisualizer(
    modifier: Modifier = Modifier,
    style: VisualizerStyle = VisualizerStyle.BARS,
    accentColor: Color = Color(0xFFFF1744),
    isPlaying: Boolean = false,
    barCount: Int = 32,
    audioSessionId: Int = MusicService.audioSessionId
) {
    var fftData by remember { mutableStateOf(ByteArray(0)) }
    var waveformData by remember { mutableStateOf(ByteArray(0)) }
    var visualizer by remember { mutableStateOf<Visualizer?>(null) }
    
    // Animated values for smooth transitions
    val animatedBars = remember { mutableStateListOf<Float>().apply { repeat(barCount) { add(0f) } } }
    
    val context = LocalContext.current
    
    // Initialize Visualizer
    DisposableEffect(audioSessionId, isPlaying) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (audioSessionId > 0 && isPlaying) {
            if (!hasPermission) {
                // Request permission if not granted
                (context as? MainActivity)?.requestAudioPermissionLauncher?.launch(Manifest.permission.RECORD_AUDIO)
                return@DisposableEffect onDispose {}
            }

            try {
                visualizer?.release()
                visualizer = Visualizer(audioSessionId).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[1]
                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(
                                vis: Visualizer?,
                                waveform: ByteArray?,
                                samplingRate: Int
                            ) {
                                waveform?.let { waveformData = it.copyOf() }
                            }

                            override fun onFftDataCapture(
                                vis: Visualizer?,
                                fft: ByteArray?,
                                samplingRate: Int
                            ) {
                                fft?.let { fftData = it.copyOf() }
                            }
                        },
                        Visualizer.getMaxCaptureRate() / 2,
                        true,
                        true
                    )
                    enabled = true
                }
                TXALogger.appI("TXAVisualizer", "Visualizer started with session $audioSessionId")
            } catch (e: Exception) {
                TXALogger.appE("TXAVisualizer", "Failed to init visualizer", e)
            }
        }
        
        onDispose {
            try {
                visualizer?.enabled = false
                visualizer?.release()
                visualizer = null
            } catch (e: Exception) {
                TXALogger.appE("TXAVisualizer", "Failed to release visualizer", e)
            }
        }
    }
    
    // Animate bar heights smoothly
    LaunchedEffect(fftData) {
        if (fftData.size >= barCount * 2) {
            for (i in 0 until barCount) {
                val real = fftData.getOrElse(i * 2) { 0 }.toInt()
                val imag = fftData.getOrElse(i * 2 + 1) { 0 }.toInt()
                val magnitude = kotlin.math.sqrt((real * real + imag * imag).toDouble()).toFloat()
                val normalized = (magnitude / 128f).coerceIn(0f, 1f)
                
                // Smooth transition
                val current = animatedBars.getOrElse(i) { 0f }
                animatedBars[i] = current + (normalized - current) * 0.3f
            }
        }
    }
    
    // Fallback animation when not playing or no data
    val infiniteTransition = rememberInfiniteTransition(label = "idle")
    val idlePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "idlePhase"
    )
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (style) {
                VisualizerStyle.BARS -> drawBars(animatedBars, accentColor, isPlaying, idlePhase)
                VisualizerStyle.WAVE -> drawWave(waveformData, accentColor, isPlaying, idlePhase)
                VisualizerStyle.CIRCLE -> drawCircle(animatedBars, accentColor, isPlaying, idlePhase)
                VisualizerStyle.SPECTRUM -> drawSpectrum(animatedBars, accentColor, isPlaying, idlePhase)
                VisualizerStyle.GLOW_BARS -> drawGlowBars(animatedBars, accentColor, isPlaying, idlePhase)
            }
        }
    }
}

private fun DrawScope.drawBars(
    bars: List<Float>,
    accentColor: Color,
    isPlaying: Boolean,
    idlePhase: Float
) {
    val barWidth = size.width / (bars.size * 1.5f)
    val spacing = barWidth * 0.5f
    val maxHeight = size.height * 0.8f
    
    bars.forEachIndexed { index, value ->
        val height = if (isPlaying && value > 0.01f) {
            value * maxHeight
        } else {
            // Idle animation - subtle wave
            val phase = (idlePhase + index * 15) % 360
            (0.1f + 0.05f * sin(Math.toRadians(phase.toDouble())).toFloat()) * maxHeight
        }
        
        val x = index * (barWidth + spacing) + spacing
        val y = (size.height - height) / 2
        
        // Gradient from accent to lighter shade
        val brush = Brush.verticalGradient(
            colors = listOf(
                accentColor,
                accentColor.copy(alpha = 0.6f)
            ),
            startY = y,
            endY = y + height
        )
        
        drawRoundRect(
            brush = brush,
            topLeft = Offset(x, y),
            size = Size(barWidth, height),
            cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
        )
    }
}

private fun DrawScope.drawWave(
    waveform: ByteArray,
    accentColor: Color,
    isPlaying: Boolean,
    idlePhase: Float
) {
    if (waveform.isEmpty() && !isPlaying) {
        // Idle wave animation
        val path = Path()
        val amplitude = size.height * 0.1f
        
        path.moveTo(0f, size.height / 2)
        for (x in 0..size.width.toInt() step 4) {
            val phase = (idlePhase + x * 0.5f) % 360
            val y = size.height / 2 + amplitude * sin(Math.toRadians(phase.toDouble())).toFloat()
            path.lineTo(x.toFloat(), y)
        }
        
        drawPath(
            path = path,
            color = accentColor.copy(alpha = 0.5f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
        )
        return
    }
    
    val path = Path()
    val step = waveform.size.toFloat() / size.width
    
    path.moveTo(0f, size.height / 2)
    
    for (x in 0..size.width.toInt()) {
        val index = (x * step).toInt().coerceIn(0, waveform.size - 1)
        val value = (waveform[index].toInt() and 0xFF) - 128
        val y = size.height / 2 + (value / 128f) * (size.height * 0.4f)
        path.lineTo(x.toFloat(), y)
    }
    
    // Draw filled area under the wave
    val fillPath = Path().apply {
        addPath(path)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    
    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.4f),
                accentColor.copy(alpha = 0.1f)
            )
        )
    )
    
    drawPath(
        path = path,
        color = accentColor,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
    )
}

private fun DrawScope.drawCircle(
    bars: List<Float>,
    accentColor: Color,
    isPlaying: Boolean,
    idlePhase: Float
) {
    val centerX = size.width / 2
    val centerY = size.height / 2
    val baseRadius = minOf(size.width, size.height) * 0.3f
    val maxBarLength = minOf(size.width, size.height) * 0.15f
    
    bars.forEachIndexed { index, value ->
        val angle = (360f / bars.size) * index - 90
        val radians = Math.toRadians(angle.toDouble())
        
        val barLength = if (isPlaying && value > 0.01f) {
            value * maxBarLength
        } else {
            val phase = (idlePhase + index * 10) % 360
            (0.2f + 0.1f * sin(Math.toRadians(phase.toDouble())).toFloat()) * maxBarLength
        }
        
        val startX = centerX + baseRadius * cos(radians).toFloat()
        val startY = centerY + baseRadius * sin(radians).toFloat()
        val endX = centerX + (baseRadius + barLength) * cos(radians).toFloat()
        val endY = centerY + (baseRadius + barLength) * sin(radians).toFloat()
        
        drawLine(
            color = accentColor.copy(alpha = 0.6f + value * 0.4f),
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = 4f
        )
    }
    
    // Inner circle
    drawCircle(
        color = accentColor.copy(alpha = 0.2f),
        radius = baseRadius,
        center = Offset(centerX, centerY)
    )
}

private fun DrawScope.drawSpectrum(
    bars: List<Float>,
    accentColor: Color,
    isPlaying: Boolean,
    idlePhase: Float
) {
    val barWidth = size.width / (bars.size * 2f)
    val spacing = barWidth * 0.3f
    val maxHeight = size.height * 0.4f
    val centerY = size.height / 2
    
    bars.forEachIndexed { index, value ->
        val height = if (isPlaying && value > 0.01f) {
            value * maxHeight
        } else {
            val phase = (idlePhase + index * 12) % 360
            (0.15f + 0.08f * sin(Math.toRadians(phase.toDouble())).toFloat()) * maxHeight
        }
        
        val x = index * (barWidth + spacing) + spacing
        
        // Top bar
        drawRoundRect(
            color = accentColor,
            topLeft = Offset(x, centerY - height),
            size = Size(barWidth, height),
            cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
        )
        
        // Bottom bar (mirrored)
        drawRoundRect(
            color = accentColor.copy(alpha = 0.5f),
            topLeft = Offset(x, centerY),
            size = Size(barWidth, height),
            cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
        )
    }
}

private fun DrawScope.drawGlowBars(
    bars: List<Float>,
    accentColor: Color,
    isPlaying: Boolean,
    idlePhase: Float
) {
    val barWidth = size.width / (bars.size * 1.5f)
    val spacing = barWidth * 0.5f
    val maxHeight = size.height * 0.8f
    
    bars.forEachIndexed { index, value ->
        val height = if (isPlaying && value > 0.01f) {
            value * maxHeight
        } else {
            val phase = (idlePhase + index * 15) % 360
            (0.1f + 0.05f * sin(Math.toRadians(phase.toDouble())).toFloat()) * maxHeight
        }
        
        val x = index * (barWidth + spacing) + spacing
        val y = (size.height - height) / 2
        
        // Glow effect (larger, blurred bar behind)
        drawRoundRect(
            color = accentColor.copy(alpha = 0.3f),
            topLeft = Offset(x - 2, y - 2),
            size = Size(barWidth + 4, height + 4),
            cornerRadius = CornerRadius(barWidth / 2 + 2, barWidth / 2 + 2)
        )
        
        // Main bar with gradient
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    accentColor,
                    accentColor.copy(alpha = 0.8f),
                    accentColor.copy(alpha = 0.4f)
                ),
                startY = y,
                endY = y + height
            ),
            topLeft = Offset(x, y),
            size = Size(barWidth, height),
            cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
        )
    }
}

/**
 * Mini visualizer for MiniPlayer - simplified bars
 */
@Composable
fun TXAMiniVisualizer(
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFFFF1744),
    isPlaying: Boolean = false,
    audioSessionId: Int = MusicService.audioSessionId
) {
    TXAVisualizer(
        modifier = modifier,
        style = VisualizerStyle.BARS,
        accentColor = accentColor,
        isPlaying = isPlaying,
        barCount = 8,
        audioSessionId = audioSessionId
    )
}
