package ms.txams.vv.core

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView

/**
 * TXA Disc Animation
 * Animates album art like a spinning vinyl disc
 * 
 * Features:
 * - Continuous 360-degree rotation when playing
 * - Smooth pause/resume of rotation
 * - Preserves rotation angle on pause
 * - Works on all Android versions (API 21+)
 */
class TXADiscAnimation(private val imageView: ImageView) {
    
    companion object {
        private const val ROTATION_DURATION_MS = 10000L // 10 seconds per full rotation
    }
    
    private var rotateAnimator: ObjectAnimator? = null
    private var isPaused = false
    private var pausedRotation = 0f
    
    /**
     * Start continuous rotation animation
     */
    fun startRotation() {
        TXABackgroundLogger.d("Starting disc rotation")
        
        // If was paused, resume from last rotation
        val startRotation = if (isPaused) pausedRotation else imageView.rotation
        
        // Cancel existing animator
        rotateAnimator?.cancel()
        
        rotateAnimator = ObjectAnimator.ofFloat(
            imageView,
            View.ROTATION,
            startRotation,
            startRotation + 360f
        ).apply {
            duration = ROTATION_DURATION_MS
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            
            addUpdateListener {
                pausedRotation = imageView.rotation % 360f
            }
            
            start()
        }
        
        isPaused = false
    }
    
    /**
     * Pause rotation, preserving current angle
     */
    fun pauseRotation() {
        TXABackgroundLogger.d("Pausing disc rotation at angle: $pausedRotation")
        
        rotateAnimator?.let { animator ->
            if (animator.isRunning) {
                pausedRotation = imageView.rotation % 360f
                animator.pause()
                isPaused = true
            }
        }
    }
    
    /**
     * Resume rotation from paused state
     */
    fun resumeRotation() {
        TXABackgroundLogger.d("Resuming disc rotation from angle: $pausedRotation")
        
        rotateAnimator?.let { animator ->
            if (isPaused) {
                animator.resume()
                isPaused = false
            }
        } ?: startRotation()
    }
    
    /**
     * Stop rotation and reset to initial state
     */
    fun stopRotation() {
        TXABackgroundLogger.d("Stopping disc rotation")
        
        rotateAnimator?.cancel()
        rotateAnimator = null
        isPaused = false
        pausedRotation = 0f
        
        // Animate back to original position
        imageView.animate()
            .rotation(0f)
            .setDuration(300)
            .setInterpolator(LinearInterpolator())
            .start()
    }
    
    /**
     * Check if disc is currently rotating
     */
    fun isRotating(): Boolean = rotateAnimator?.isRunning == true && !isPaused
    
    /**
     * Clean up resources
     */
    fun release() {
        rotateAnimator?.cancel()
        rotateAnimator = null
    }
}
