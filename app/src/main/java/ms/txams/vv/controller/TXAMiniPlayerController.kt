package ms.txams.vv.controller

import android.animation.Animator
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.lifecycle.MutableLiveData
import ms.txams.vv.core.TXABackgroundLogger

/**
 * TXA MiniPlayer Controller
 * Manages the state and animations of the Pill MiniPlayer
 * 
 * Inspired by Namida's MiniplayerController
 * 
 * Features:
 * - Global state management for miniplayer
 * - Animation control (expand/collapse/bounce)
 * - Queue scrolling to current track
 * - Party mode toggle
 * 
 * @author TXA - fb.com/vlog.txa.2311
 */
object TXAMiniPlayerController {
    
    // Animation states
    enum class State {
        COLLAPSED,
        EXPANDING,
        EXPANDED,
        COLLAPSING
    }
    
    // Observable state
    val state = MutableLiveData(State.COLLAPSED)
    val animationProgress = MutableLiveData(0f)
    val isPartyMode = MutableLiveData(false)
    
    // Current animator
    private var currentAnimator: ValueAnimator? = null
    
    // Animation duration
    private const val ANIMATION_DURATION = 350L
    private const val BOUNCE_DURATION = 200L
    
    // Callbacks
    var onStateChanged: ((State) -> Unit)? = null
    var onProgressChanged: ((Float) -> Unit)? = null
    var onScrollToCurrentTrack: (() -> Unit)? = null
    
    /**
     * Expand the miniplayer
     */
    fun expand() {
        if (state.value == State.EXPANDED || state.value == State.EXPANDING) return
        
        state.postValue(State.EXPANDING)
        animateTo(1f) {
            state.postValue(State.EXPANDED)
            onStateChanged?.invoke(State.EXPANDED)
        }
        
        TXABackgroundLogger.d("MiniPlayerController: Expanding")
    }
    
    /**
     * Collapse the miniplayer
     */
    fun collapse() {
        if (state.value == State.COLLAPSED || state.value == State.COLLAPSING) return
        
        state.postValue(State.COLLAPSING)
        animateTo(0f) {
            state.postValue(State.COLLAPSED)
            onStateChanged?.invoke(State.COLLAPSED)
        }
        
        TXABackgroundLogger.d("MiniPlayerController: Collapsing")
    }
    
    /**
     * Toggle expand/collapse
     */
    fun toggle() {
        when (state.value) {
            State.COLLAPSED, State.COLLAPSING -> expand()
            State.EXPANDED, State.EXPANDING -> collapse()
            else -> {}
        }
    }
    
    /**
     * Animate to target progress
     */
    private fun animateTo(target: Float, onComplete: () -> Unit) {
        currentAnimator?.cancel()
        
        val currentProgress = animationProgress.value ?: 0f
        
        currentAnimator = ValueAnimator.ofFloat(currentProgress, target).apply {
            duration = ANIMATION_DURATION
            interpolator = DecelerateInterpolator(2f)
            
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                animationProgress.postValue(progress)
                onProgressChanged?.invoke(progress)
            }
            
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationEnd(animation: Animator) { onComplete() }
                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            })
            
            start()
        }
    }
    
    /**
     * Bounce up animation (like notification tap)
     */
    fun bounceUp() {
        val currentProgress = animationProgress.value ?: 0f
        if (currentProgress < 0.1f) {
            // Only bounce when collapsed
            ValueAnimator.ofFloat(0f, 0.1f, 0f).apply {
                duration = BOUNCE_DURATION
                interpolator = OvershootInterpolator()
                addUpdateListener { animator ->
                    animationProgress.postValue(animator.animatedValue as Float)
                    onProgressChanged?.invoke(animator.animatedValue as Float)
                }
                start()
            }
        }
    }
    
    /**
     * Bounce down animation
     */
    fun bounceDown() {
        val currentProgress = animationProgress.value ?: 1f
        if (currentProgress > 0.9f) {
            // Only bounce when expanded
            ValueAnimator.ofFloat(1f, 0.9f, 1f).apply {
                duration = BOUNCE_DURATION
                interpolator = OvershootInterpolator()
                addUpdateListener { animator ->
                    animationProgress.postValue(animator.animatedValue as Float)
                    onProgressChanged?.invoke(animator.animatedValue as Float)
                }
                start()
            }
        }
    }
    
    /**
     * Scroll queue to current track
     */
    fun scrollToCurrentTrack() {
        onScrollToCurrentTrack?.invoke()
        TXABackgroundLogger.d("MiniPlayerController: Scrolling to current track")
    }
    
    /**
     * Toggle party mode
     */
    fun togglePartyMode() {
        val newValue = !(isPartyMode.value ?: false)
        isPartyMode.postValue(newValue)
        TXABackgroundLogger.d("MiniPlayerController: Party mode = $newValue")
    }
    
    /**
     * Set progress directly (for gesture dragging)
     */
    fun setProgress(progress: Float) {
        currentAnimator?.cancel()
        val clampedProgress = progress.coerceIn(0f, 1f)
        animationProgress.postValue(clampedProgress)
        onProgressChanged?.invoke(clampedProgress)
        
        // Update state based on progress
        when {
            clampedProgress == 0f -> state.postValue(State.COLLAPSED)
            clampedProgress == 1f -> state.postValue(State.EXPANDED)
            clampedProgress > (animationProgress.value ?: 0f) -> state.postValue(State.EXPANDING)
            else -> state.postValue(State.COLLAPSING)
        }
    }
    
    /**
     * Snap to nearest state based on current progress
     */
    fun snapToNearestState() {
        val progress = animationProgress.value ?: 0f
        if (progress > 0.5f) {
            expand()
        } else {
            collapse()
        }
    }
    
    /**
     * Reset to initial state
     */
    fun reset() {
        currentAnimator?.cancel()
        state.postValue(State.COLLAPSED)
        animationProgress.postValue(0f)
        isPartyMode.postValue(false)
    }
    
    /**
     * Check if miniplayer is visible (not fully collapsed)
     */
    fun isVisible(): Boolean {
        return (animationProgress.value ?: 0f) > 0f
    }
    
    /**
     * Check if miniplayer is fully expanded
     */
    fun isFullyExpanded(): Boolean {
        return state.value == State.EXPANDED
    }
}
