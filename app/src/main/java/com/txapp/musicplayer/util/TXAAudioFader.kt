package com.txapp.musicplayer.util

import android.animation.ValueAnimator
import androidx.media3.exoplayer.ExoPlayer

/**
 * Helper to handle audio fading for ExoPlayer
 */
object TXAAudioFader {

    private var currentAnimator: ValueAnimator? = null

    /**
     * Start a fade in/out animation for an ExoPlayer instance
     * @param player The ExoPlayer to fade
     * @param fadeIn True to fade in (0 -> 1), False to fade out (1 -> 0)
     * @param duration Duration in milliseconds
     * @param onEnd Optional callback when fade ends
     */
    fun startFade(
        player: ExoPlayer,
        fadeIn: Boolean,
        duration: Long,
        onEnd: (() -> Unit)? = null
    ) {
        currentAnimator?.cancel()
        
        if (duration <= 0) {
            player.volume = if (fadeIn) 1.0f else 0.0f
            onEnd?.invoke()
            return
        }

        val startValue = if (fadeIn) {
            // If already fading or playing, start from current volume instead of jumping to 0
            if (player.volume > 0.05f) player.volume else 0.0f
        } else {
            player.volume
        }
        val endValue = if (fadeIn) 1.0f else 0.0f

        currentAnimator = ValueAnimator.ofFloat(startValue, endValue).apply {
            this.duration = duration
            addUpdateListener { animation ->
                player.volume = animation.animatedValue as Float
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onEnd?.invoke()
                    currentAnimator = null
                }
            })
            start()
        }
    }

    /**
     * Cancel any running fade animation
     */
    fun cancel() {
        currentAnimator?.cancel()
        currentAnimator = null
    }
}
