package ms.txams.vv.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.media3.common.Player
import ms.txams.vv.R
import ms.txams.vv.core.TXABackgroundLogger
import ms.txams.vv.core.TXATranslation
import ms.txams.vv.data.manager.TXANowBarSettingsManager

/**
 * TXA Pill MiniPlayer
 * A pill-shaped (viên thuốc) miniplayer inspired by Namida and Dynamic Island
 * 
 * Features:
 * - Smooth expand/collapse animation
 * - Swipe gestures to control
 * - Dynamic album art coloring
 * - Customizable buttons via TXANowBarSettingsManager
 * - Party mode with breathing edge effect
 * 
 * @author TXA - fb.com/vlog.txa.2311
 * @copyright 2024 TXA Music
 */
class TXAPillMiniPlayer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr) {

    // UI Components
    private lateinit var albumArt: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var btnPrevious: ImageButton
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnStop: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnRepeat: ImageButton
    private lateinit var btnLike: ImageButton
    private lateinit var progressBar: View
    private lateinit var expandedContent: View
    
    // State
    private var isExpanded = false
    private var animationProgress = 0f // 0 = collapsed, 1 = expanded
    private var currentAnimator: ValueAnimator? = null
    
    // Dimensions
    private val collapsedHeight = 64.dpToPx()
    private val expandedHeight = 280.dpToPx()
    private val collapsedCornerRadius = 32.dpToPx().toFloat()
    private val expandedCornerRadius = 24.dpToPx().toFloat()
    
    // Callbacks
    var onPlayPauseClick: (() -> Unit)? = null
    var onPreviousClick: (() -> Unit)? = null
    var onNextClick: (() -> Unit)? = null
    var onStopClick: (() -> Unit)? = null
    var onShuffleClick: (() -> Unit)? = null
    var onRepeatClick: (() -> Unit)? = null
    var onLikeClick: (() -> Unit)? = null
    var onExpandClick: (() -> Unit)? = null
    
    // Settings manager (injected)
    var nowBarSettings: TXANowBarSettingsManager? = null
        set(value) {
            field = value
            updateButtonVisibility()
        }
    
    // Gesture detector for swipe
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (e1 == null) return false
            val deltaY = e2.y - e1.y
            if (Math.abs(deltaY) > 50) {
                if (deltaY < 0) {
                    // Swipe up - expand
                    expand()
                } else {
                    // Swipe down - collapse
                    collapse()
                }
                return true
            }
            return false
        }
        
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (!isExpanded) {
                onExpandClick?.invoke()
            }
            return true
        }
    })
    
    init {
        setupView()
        setupPillShape()
    }
    
    private fun setupView() {
        // Inflate layout
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.txa_pill_miniplayer, this, true)
        
        // Find views
        albumArt = view.findViewById(R.id.ivAlbumArt)
        tvTitle = view.findViewById(R.id.tvTitle)
        tvArtist = view.findViewById(R.id.tvArtist)
        btnPrevious = view.findViewById(R.id.btnPrevious)
        btnPlayPause = view.findViewById(R.id.btnPlayPause)
        btnNext = view.findViewById(R.id.btnNext)
        btnStop = view.findViewById(R.id.btnStop)
        btnShuffle = view.findViewById(R.id.btnShuffle)
        btnRepeat = view.findViewById(R.id.btnRepeat)
        btnLike = view.findViewById(R.id.btnLike)
        progressBar = view.findViewById(R.id.progressBar)
        expandedContent = view.findViewById(R.id.expandedContent)
        
        // Setup click listeners
        btnPlayPause.setOnClickListener { onPlayPauseClick?.invoke() }
        btnPrevious.setOnClickListener { onPreviousClick?.invoke() }
        btnNext.setOnClickListener { onNextClick?.invoke() }
        btnStop.setOnClickListener { onStopClick?.invoke() }
        btnShuffle.setOnClickListener { onShuffleClick?.invoke() }
        btnRepeat.setOnClickListener { onRepeatClick?.invoke() }
        btnLike.setOnClickListener { onLikeClick?.invoke() }
        
        // Initial state - collapsed
        expandedContent.alpha = 0f
        expandedContent.isVisible = false
    }
    
    private fun setupPillShape() {
        // Pill shape with rounded corners
        radius = collapsedCornerRadius
        cardElevation = 12.dpToPx().toFloat()
        useCompatPadding = true
        
        // Gradient background
        val gradientDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = collapsedCornerRadius
            setColor(ContextCompat.getColor(context, R.color.txa_surface))
        }
        background = gradientDrawable
        
        // Set initial height
        layoutParams?.height = collapsedHeight
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }
    
    /**
     * Expand the pill to full miniplayer
     */
    fun expand(animate: Boolean = true) {
        if (isExpanded) return
        isExpanded = true
        
        if (animate) {
            animateToState(1f)
        } else {
            setAnimationProgress(1f)
        }
        
        TXABackgroundLogger.d("Pill MiniPlayer expanded")
    }
    
    /**
     * Collapse to pill shape
     */
    fun collapse(animate: Boolean = true) {
        if (!isExpanded) return
        isExpanded = false
        
        if (animate) {
            animateToState(0f)
        } else {
            setAnimationProgress(0f)
        }
        
        TXABackgroundLogger.d("Pill MiniPlayer collapsed")
    }
    
    /**
     * Toggle expand/collapse
     */
    fun toggle() {
        if (isExpanded) collapse() else expand()
    }
    
    private fun animateToState(targetProgress: Float) {
        currentAnimator?.cancel()
        
        currentAnimator = ValueAnimator.ofFloat(animationProgress, targetProgress).apply {
            duration = 350
            interpolator = DecelerateInterpolator(2f)
            addUpdateListener { animator ->
                setAnimationProgress(animator.animatedValue as Float)
            }
            start()
        }
    }
    
    private fun setAnimationProgress(progress: Float) {
        animationProgress = progress
        
        // Interpolate height
        val height = lerp(collapsedHeight.toFloat(), expandedHeight.toFloat(), progress).toInt()
        layoutParams?.height = height
        requestLayout()
        
        // Interpolate corner radius - like Namida
        // Top always rounded, bottom only when collapsed
        val topRadius = lerp(collapsedCornerRadius, expandedCornerRadius, progress)
        val bottomRadius = collapsedCornerRadius * (1 - progress * 10 + 9).coerceIn(0f, 1f)
        
        // Update card radius (CardView only supports uniform radius, so we use max)
        radius = maxOf(topRadius, bottomRadius)
        
        // Update expanded content visibility
        expandedContent.alpha = progress
        expandedContent.isVisible = progress > 0.1f
        
        // Scale album art based on progress
        val scale = lerp(0.8f, 1f, progress)
        albumArt.scaleX = scale
        albumArt.scaleY = scale
    }
    
    private fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction
    }
    
    private fun Int.dpToPx(): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
    
    /**
     * Update button visibility based on TXANowBarSettingsManager
     */
    fun updateButtonVisibility() {
        val settings = nowBarSettings ?: return
        
        btnPrevious.isVisible = settings.isButtonEnabled(TXANowBarSettingsManager.KEY_SHOW_PREVIOUS)
        btnNext.isVisible = settings.isButtonEnabled(TXANowBarSettingsManager.KEY_SHOW_NEXT)
        btnStop.isVisible = settings.isButtonEnabled(TXANowBarSettingsManager.KEY_SHOW_STOP)
        btnShuffle.isVisible = settings.isButtonEnabled(TXANowBarSettingsManager.KEY_SHOW_SHUFFLE)
        btnRepeat.isVisible = settings.isButtonEnabled(TXANowBarSettingsManager.KEY_SHOW_REPEAT)
        btnLike.isVisible = settings.isButtonEnabled(TXANowBarSettingsManager.KEY_SHOW_LIKE)
    }
    
    /**
     * Update track info
     */
    fun updateTrackInfo(title: String, artist: String) {
        tvTitle.text = title
        tvArtist.text = artist
    }
    
    /**
     * Update play/pause button state
     */
    fun updatePlayState(isPlaying: Boolean) {
        btnPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }
    
    /**
     * Update progress (0-100)
     */
    fun updateProgress(progress: Int) {
        val progressWidth = (width * progress / 100f).toInt()
        progressBar.layoutParams?.width = progressWidth
        progressBar.requestLayout()
    }
    
    /**
     * Set album art
     */
    fun setAlbumArt(bitmap: android.graphics.Bitmap?) {
        if (bitmap != null) {
            albumArt.setImageBitmap(bitmap)
        } else {
            albumArt.setImageResource(R.drawable.ic_music_note)
        }
    }
    
    /**
     * Apply dynamic coloring from album art
     */
    fun applyDynamicColor(dominantColor: Int) {
        val gradientDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(dominantColor)
        }
        background = gradientDrawable
    }
    
    /**
     * Start party mode - breathing edge effect
     */
    fun startPartyMode() {
        // Breathing animation on elevation
        ValueAnimator.ofFloat(12f, 24f, 12f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                cardElevation = (animator.animatedValue as Float).dpToPx().toFloat()
            }
            start()
        }
    }
    
    /**
     * Stop party mode
     */
    fun stopPartyMode() {
        cardElevation = 12.dpToPx().toFloat()
    }
    
    private fun Float.dpToPx(): Float {
        return this * context.resources.displayMetrics.density
    }
}
