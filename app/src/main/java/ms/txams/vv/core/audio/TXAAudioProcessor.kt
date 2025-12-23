package ms.txams.vv.core.audio

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ConcatenatingMediaSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ms.txams.vv.core.data.database.entity.TXASongEntity
import timber.log.Timber
import kotlin.math.*

/**
 * TXA Audio Processor - Xử lý crossfade, volume scaling, và audio effects
 * Sử dụng logarithmic volume scaling và sine curve cho crossfade tự nhiên
 */
@UnstableApi
class TXAAudioProcessor(private val context: Context) {

    private val processorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isInitialized = false

    // Crossfade configuration
    private var crossfadeDurationMs: Long = 3000
    private var isCrossfadeEnabled: Boolean = true
    private var crossfadeCurveType: CrossfadeCurve = CrossfadeCurve.SINE

    // Audio effects configuration
    private var pitchMultiplier: Float = 1.0f
    private var speedMultiplier: Float = 1.0f
    private var volumeMultiplier: Float = 1.0f
    
    // Runtime adjustment variables
    private var volumeAdjustment: Float = 1.0f
    private var speedAdjustment: Float = 1.0f
    private var pitchAdjustment: Float = 0f

    // Audio processing state
    private var activeProcessor: androidx.media3.common.audio.AudioProcessor? = null

    fun initialize() {
        try {
            if (isInitialized) {
                Timber.w("TXAAudioProcessor already initialized")
                return
            }

            // Initialize audio processing components
            setupAudioProcessors()
            
            isInitialized = true
            Timber.d("TXAAudioProcessor initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize TXAAudioProcessor")
            throw e
        }
    }

    fun release() {
        try {
            if (!isInitialized) {
                return
            }

            // Release audio processing resources
            activeProcessor?.flush()
            activeProcessor?.release()
            activeProcessor = null

            isInitialized = false
            Timber.d("TXAAudioProcessor released")
        } catch (e: Exception) {
            Timber.e(e, "Failed to release TXAAudioProcessor")
        }
    }

    private fun setupAudioProcessors() {
        // Setup Media3 audio processors for effects
        // This would integrate with ExoPlayer's audio processing pipeline
    }

    // Crossfade methods
    fun setCrossfadeDuration(durationMs: Long) {
        crossfadeDurationMs = durationMs.coerceIn(500, 10000)
        Timber.d("Crossfade duration set to ${crossfadeDurationMs}ms")
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        isCrossfadeEnabled = enabled
        Timber.d("Crossfade enabled: $enabled")
    }

    fun setCrossfadeCurve(curveType: CrossfadeCurve) {
        crossfadeCurveType = curveType
        Timber.d("Crossfade curve set to $curveType")
    }

    fun isCrossfadeEnabled(): Boolean = isCrossfadeEnabled

    fun startCrossfade(
        currentPlayer: androidx.media3.exoplayer.ExoPlayer,
        nextSong: ms.txams.vv.core.data.database.entity.TXASongEntity,
        dataSourceFactory: androidx.media3.datasource.DataSource.Factory
    ) {
        if (!isCrossfadeEnabled || !isInitialized) {
            return
        }

        processorScope.launch {
            try {
                // Implementation for seamless crossfade
                applyCrossfadeTransition(currentPlayer, nextSong, dataSourceFactory)
            } catch (e: Exception) {
                Timber.e(e, "Failed to start crossfade")
            }
        }
    }

    private fun applyCrossfadeTransition(
        currentPlayer: androidx.media3.exoplayer.ExoPlayer,
        nextSong: ms.txams.vv.core.data.database.entity.TXASongEntity,
        dataSourceFactory: androidx.media3.datasource.DataSource.Factory
    ) {
        // Calculate crossfade volumes using logarithmic scaling
        val currentPosition = currentPlayer.currentPosition
        val duration = currentPlayer.duration
        val remainingTime = duration - currentPosition

        if (remainingTime <= crossfadeDurationMs) {
            val crossfadeProgress = 1f - (remainingTime.toFloat() / crossfadeDurationMs.toFloat())
            
            // Apply crossfade curve
            val adjustedProgress = applyCrossfadeCurve(crossfadeProgress)
            
            // Set volumes using logarithmic scaling
            val currentVolume = calculateLogarithmicVolume(1f - adjustedProgress)
            val nextVolume = calculateLogarithmicVolume(adjustedProgress)
            
            currentPlayer.volume = currentVolume
            
            // Prepare next track with volume
            // This would involve preparing the next media source
        }
    }

    private fun applyCrossfadeCurve(progress: Float): Float {
        return when (crossfadeCurveType) {
            CrossfadeCurve.LINEAR -> progress
            CrossfadeCurve.SINE -> (sin((progress - 0.5f) * PI) + 1f) / 2f
            CrossfadeCurve.LOGARITHMIC -> log10((progress * 9f) + 1f) / log10(10f)
            CrossfadeCurve.EXPONENTIAL -> (progress * progress)
        }
    }

    private fun calculateLogarithmicVolume(linearVolume: Float): Float {
        // Convert linear volume to logarithmic scale for better perception
        return if (linearVolume <= 0f) {
            0f
        } else {
            // Use logarithmic scaling: 20 * log10(volume) for dB conversion
            val db = 20f * log10(linearVolume.coerceAtLeast(0.001f))
            // Convert back to linear scale
            (10f.pow(db / 20f)).coerceIn(0f, 1f)
        }
    }

    // Audio effects methods
    fun setPitch(multiplier: Float) {
        pitchMultiplier = multiplier.coerceIn(0.5f, 2.0f)
        Timber.d("Pitch multiplier set to $pitchMultiplier")
    }

    fun setSpeed(multiplier: Float) {
        speedMultiplier = multiplier.coerceIn(0.25f, 4.0f)
        speedAdjustment = speedMultiplier
        Timber.d("Speed multiplier set to $speedMultiplier")
    }

    fun setVolume(multiplier: Float) {
        volumeMultiplier = multiplier.coerceIn(0f, 2.0f)
        volumeAdjustment = volumeMultiplier
        Timber.d("Volume multiplier set to $volumeMultiplier")
    }

    // Utility methods
    fun getAudioInfo(): AudioInfo {
        return AudioInfo(
            isInitialized = isInitialized,
            crossfadeEnabled = isCrossfadeEnabled,
            crossfadeDuration = crossfadeDurationMs,
            pitchMultiplier = pitchMultiplier,
            speedMultiplier = speedMultiplier,
            volumeMultiplier = volumeMultiplier
        )
    }

    data class AudioInfo(
        val isInitialized: Boolean,
        val crossfadeEnabled: Boolean,
        val crossfadeDuration: Long,
        val pitchMultiplier: Float,
        val speedMultiplier: Float,
        val volumeMultiplier: Float
    )

    enum class CrossfadeCurve {
        LINEAR,
        SINE,
        LOGARITHMIC,
        EXPONENTIAL
    }
}
