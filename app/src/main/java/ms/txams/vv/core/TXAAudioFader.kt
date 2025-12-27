package ms.txams.vv.core

import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import androidx.media3.common.Player

/**
 * TXA Audio Fader
 * Handles fade in/out effects for audio playback
 * 
 * Features:
 * - Fade in on play start (3 seconds)
 * - Fade out on pause/stop (3 seconds)
 * - Volume boost for intro section
 * - Smooth volume transitions using ValueAnimator
 */
object TXAAudioFader {
    
    private const val DEFAULT_FADE_DURATION_MS = 3000L
    private const val INTRO_VOLUME_BOOST = 1.3f // 30% louder for intro
    private var fadeAnimator: ValueAnimator? = null
    private var currentVolume = 1f
    
    /**
     * Start playback with fade in effect
     * @param player The ExoPlayer/MediaController
     * @param durationMs Fade duration in milliseconds
     * @param onComplete Callback when fade completes
     */
    fun fadeIn(
        player: Player,
        durationMs: Long = DEFAULT_FADE_DURATION_MS,
        startVolume: Float = 0f,
        endVolume: Float = 1f,
        onComplete: (() -> Unit)? = null
    ) {
        TXABackgroundLogger.d("Starting fade in: ${startVolume} -> ${endVolume} over ${durationMs}ms")
        
        // Cancel any existing fade
        fadeAnimator?.cancel()
        
        // Set initial volume
        player.volume = startVolume
        currentVolume = startVolume
        
        // Start playback first
        player.play()
        
        fadeAnimator = ValueAnimator.ofFloat(startVolume, endVolume).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            
            addUpdateListener { animation ->
                val volume = animation.animatedValue as Float
                player.volume = volume.coerceIn(0f, 1f)
                currentVolume = volume
            }
            
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    TXABackgroundLogger.d("Fade in complete at volume: $currentVolume")
                    onComplete?.invoke()
                }
            })
            
            start()
        }
    }
    
    /**
     * Pause playback with fade out effect
     * @param player The ExoPlayer/MediaController
     * @param durationMs Fade duration in milliseconds
     * @param onComplete Callback when fade completes (pause should be called here)
     */
    fun fadeOut(
        player: Player,
        durationMs: Long = DEFAULT_FADE_DURATION_MS,
        onComplete: (() -> Unit)? = null
    ) {
        val startVolume = player.volume
        TXABackgroundLogger.d("Starting fade out: ${startVolume} -> 0 over ${durationMs}ms")
        
        // Cancel any existing fade
        fadeAnimator?.cancel()
        
        fadeAnimator = ValueAnimator.ofFloat(startVolume, 0f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            
            addUpdateListener { animation ->
                val volume = animation.animatedValue as Float
                player.volume = volume.coerceIn(0f, 1f)
                currentVolume = volume
            }
            
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    TXABackgroundLogger.d("Fade out complete, pausing")
                    player.pause()
                    // Restore volume for next play
                    player.volume = 1f
                    onComplete?.invoke()
                }
            })
            
            start()
        }
    }
    
    /**
     * Play with intro volume boost then fade to normal
     * Boosts volume at start (for intro), then fades to normal volume
     * @param player The ExoPlayer/MediaController
     * @param introDurationMs Duration of boosted intro (default 5 seconds for intro_txa.mp3)
     */
    fun playWithIntroBoost(
        player: Player,
        introDurationMs: Long = 5000L,
        fadeInDurationMs: Long = DEFAULT_FADE_DURATION_MS
    ) {
        TXABackgroundLogger.i("Playing with intro boost")
        
        // Cancel any existing fade
        fadeAnimator?.cancel()
        
        // Start with normal volume but will boost for intro
        player.volume = 0f
        player.play()
        
        // Fade in to boosted volume
        fadeIn(
            player = player,
            durationMs = fadeInDurationMs,
            startVolume = 0f,
            endVolume = INTRO_VOLUME_BOOST.coerceAtMost(1f), // Cap at 1f since Player.volume max is 1
            onComplete = {
                // After intro duration, fade back to normal volume
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (player.isPlaying) {
                        fadeToNormalVolume(player, fadeInDurationMs)
                    }
                }, introDurationMs)
            }
        )
    }
    
    /**
     * Fade from current volume to normal (1.0)
     */
    private fun fadeToNormalVolume(
        player: Player,
        durationMs: Long = DEFAULT_FADE_DURATION_MS
    ) {
        val currentVol = player.volume
        if (currentVol == 1f) return
        
        TXABackgroundLogger.d("Fading to normal volume from: $currentVol")
        
        fadeAnimator?.cancel()
        
        fadeAnimator = ValueAnimator.ofFloat(currentVol, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            
            addUpdateListener { animation ->
                val volume = animation.animatedValue as Float
                player.volume = volume.coerceIn(0f, 1f)
                currentVolume = volume
            }
            
            start()
        }
    }
    
    /**
     * Cancel any ongoing fade animation
     */
    fun cancelFade() {
        fadeAnimator?.cancel()
        fadeAnimator = null
    }
    
    /**
     * Check if fade is currently in progress
     */
    fun isFading(): Boolean = fadeAnimator?.isRunning == true
    
    /**
     * Get current fade volume
     */
    fun getCurrentVolume(): Float = currentVolume
}
