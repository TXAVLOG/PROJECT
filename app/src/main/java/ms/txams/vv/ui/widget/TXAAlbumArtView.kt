package ms.txams.vv.ui.widget

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import ms.txams.vv.R
import ms.txams.vv.core.TXABackgroundLogger
import ms.txams.vv.data.manager.TXANowBarSettingsManager

/**
 * TXA Album Art View
 * Custom ImageView for album artwork with special effects
 * 
 * Inspired by Namida's ArtworkWidget:
 * - Different shape styles (square, rounded, circle)
 * - Drop shadow / glow effect
 * - Fade in animation when loading
 * - Tilt/floating effect (optional)
 * - Rotation animation for vinyl disc effect
 * 
 * @author TXA - fb.com/vlog.txa.2311
 */
class TXAAlbumArtView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {
    
    // Shape styles matching TXANowBarSettingsManager
    enum class ShapeStyle {
        SQUARE,
        ROUNDED,
        CIRCLE
    }
    
    // Current settings
    var shapeStyle: ShapeStyle = ShapeStyle.ROUNDED
        set(value) {
            field = value
            invalidate()
        }
    
    var cornerRadius: Float = 16f.dpToPx()
        set(value) {
            field = value
            invalidate()
        }
    
    var enableDropShadow: Boolean = true
        set(value) {
            field = value
            invalidate()
        }
    
    var shadowBlur: Float = 8f.dpToPx()
        set(value) {
            field = value
            invalidate()
        }
    
    var shadowColor: Int = Color.argb(80, 0, 0, 0)
    
    var enableFadeAnimation: Boolean = true
    var fadeAnimationDuration: Long = 300L
    
    var enableRotation: Boolean = false
        set(value) {
            field = value
            if (value) startRotation() else stopRotation()
        }
    
    var rotationSpeed: Long = 20000L // Time for one full rotation
    
    // Animation
    private var rotationAnimator: ObjectAnimator? = null
    private var fadeAnimator: ValueAnimator? = null
    private var currentAlpha = 1f
    
    // Paint for drawing
    private val clipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }
    
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = shadowColor
        style = Paint.Style.FILL
    }
    
    private val clipPath = Path()
    private val rectF = RectF()
    
    init {
        // Enable software layer for shadow
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        
        // Default scale type
        scaleType = ScaleType.CENTER_CROP
        
        // Load settings
        loadShapeFromSettings()
    }
    
    /**
     * Load shape style from TXANowBarSettingsManager
     */
    fun loadShapeFromSettings() {
        try {
            val settingsManager = TXANowBarSettingsManager(context)
            shapeStyle = when (settingsManager.getAlbumArtStyle()) {
                TXANowBarSettingsManager.ALBUM_ART_SQUARE -> ShapeStyle.SQUARE
                TXANowBarSettingsManager.ALBUM_ART_CIRCLE -> ShapeStyle.CIRCLE
                else -> ShapeStyle.ROUNDED
            }
        } catch (e: Exception) {
            TXABackgroundLogger.e("Failed to load album art style from settings", e)
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        // Calculate bounds
        val saveCount = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        
        // Draw shadow if enabled
        if (enableDropShadow && shapeStyle != ShapeStyle.SQUARE) {
            drawShadow(canvas)
        }
        
        // Create clip path based on style
        clipPath.reset()
        rectF.set(0f, 0f, width.toFloat(), height.toFloat())
        
        when (shapeStyle) {
            ShapeStyle.CIRCLE -> {
                val radius = minOf(width, height) / 2f
                val cx = width / 2f
                val cy = height / 2f
                clipPath.addCircle(cx, cy, radius, Path.Direction.CW)
            }
            ShapeStyle.ROUNDED -> {
                clipPath.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW)
            }
            ShapeStyle.SQUARE -> {
                clipPath.addRect(rectF, Path.Direction.CW)
            }
        }
        
        canvas.clipPath(clipPath)
        
        // Apply alpha for fade effect
        if (currentAlpha < 1f) {
            alpha = currentAlpha
        }
        
        super.onDraw(canvas)
        
        canvas.restoreToCount(saveCount)
    }
    
    private fun drawShadow(canvas: Canvas) {
        shadowPaint.setShadowLayer(shadowBlur, 0f, 4f, shadowColor)
        
        when (shapeStyle) {
            ShapeStyle.CIRCLE -> {
                val radius = minOf(width, height) / 2f - shadowBlur
                canvas.drawCircle(width / 2f, height / 2f, radius, shadowPaint)
            }
            ShapeStyle.ROUNDED -> {
                val inset = shadowBlur
                rectF.set(inset, inset, width - inset, height - inset)
                canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, shadowPaint)
            }
            else -> {}
        }
    }
    
    /**
     * Set bitmap with fade animation
     */
    fun setImageBitmapWithFade(bitmap: Bitmap?) {
        if (!enableFadeAnimation || bitmap == null) {
            setImageBitmap(bitmap)
            return
        }
        
        // Fade out
        fadeAnimator?.cancel()
        fadeAnimator = ValueAnimator.ofFloat(1f, 0f, 1f).apply {
            duration = fadeAnimationDuration
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                if (value <= 0.5f) {
                    // First half - fade out
                    currentAlpha = value * 2
                } else {
                    // Second half - set new image and fade in
                    if (value > 0.5f && value < 0.6f) {
                        super.setImageBitmap(bitmap)
                    }
                    currentAlpha = (value - 0.5f) * 2
                }
                invalidate()
            }
            start()
        }
    }
    
    /**
     * Set drawable with fade animation  
     */
    fun setImageDrawableWithFade(drawable: Drawable?) {
        if (drawable is BitmapDrawable) {
            setImageBitmapWithFade(drawable.bitmap)
        } else {
            setImageDrawable(drawable)
        }
    }
    
    /**
     * Start vinyl disc rotation animation
     */
    fun startRotation() {
        if (rotationAnimator?.isRunning == true) return
        
        rotationAnimator = ObjectAnimator.ofFloat(this, "rotation", 0f, 360f).apply {
            duration = rotationSpeed
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        
        TXABackgroundLogger.d("Album art rotation started")
    }
    
    /**
     * Stop rotation animation
     */
    fun stopRotation() {
        rotationAnimator?.cancel()
        rotationAnimator = null
        rotation = 0f
        
        TXABackgroundLogger.d("Album art rotation stopped")
    }
    
    /**
     * Pause rotation (keep current angle)
     */
    fun pauseRotation() {
        rotationAnimator?.pause()
    }
    
    /**
     * Resume rotation from current angle
     */
    fun resumeRotation() {
        rotationAnimator?.resume()
    }
    
    /**
     * Apply tilt effect (3D floating)
     * Simulates Namida's TiltParallax effect
     */
    fun applyTiltEffect(rotationX: Float, rotationY: Float) {
        // Subtle tilt effect
        this.rotationX = rotationX.coerceIn(-10f, 10f)
        this.rotationY = rotationY.coerceIn(-10f, 10f)
    }
    
    /**
     * Reset tilt to default
     */
    fun resetTilt() {
        rotationX = 0f
        rotationY = 0f
    }
    
    /**
     * Set glow color (for party mode)
     */
    fun setGlowColor(color: Int) {
        shadowColor = color
        shadowPaint.color = color
        if (enableDropShadow) {
            invalidate()
        }
    }
    
    /**
     * Animate glow pulsing (party mode)
     */
    fun startGlowPulse() {
        ValueAnimator.ofFloat(0.3f, 1f, 0.3f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                shadowPaint.alpha = (value * 255).toInt()
                invalidate()
            }
            start()
        }
    }
    
    private fun Float.dpToPx(): Float {
        return this * context.resources.displayMetrics.density
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        rotationAnimator?.cancel()
        fadeAnimator?.cancel()
    }
}
