package ms.txams.vv.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import ms.txams.vv.R
import ms.txams.vv.core.TXABackgroundLogger

/**
 * TXA Waveform View
 * Audio waveform visualization like Namida's WaveformController
 * 
 * Features:
 * - Animated waveform display
 * - Progress indicator
 * - Tap to seek
 * - Gradient coloring
 * - Responsive scaling animation
 * 
 * @author TXA - fb.com/vlog.txa.2311
 */
class TXAWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    // Waveform data (normalized 0.0 to 1.0)
    private var waveformData: FloatArray = FloatArray(0)
    
    // Playback progress (0.0 to 1.0)
    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }
    
    // Number of bars to display
    var barCount: Int = 60
        set(value) {
            field = value
            calculateDisplayData()
            invalidate()
        }
    
    // Visual settings
    var barWidth: Float = 3f.dpToPx()
    var barSpacing: Float = 2f.dpToPx()
    var barCornerRadius: Float = 2f.dpToPx()
    var minBarHeight: Float = 4f.dpToPx()
    
    // Colors
    var playedColor: Int = ContextCompat.getColor(context, R.color.txa_primary)
    var unplayedColor: Int = ContextCompat.getColor(context, R.color.txa_surface_variant)
    var useGradient: Boolean = true
    
    // Animation intensity (like Namida's animatingThumbnailIntensity)
    var animationIntensity: Float = 1.0f
    
    // Cached display data
    private var displayData: FloatArray = FloatArray(0)
    
    // Paints
    private val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val unplayedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = unplayedColor
    }
    
    // Callbacks
    var onSeek: ((Float) -> Unit)? = null
    
    init {
        // Generate dummy waveform for preview
        generateDummyWaveform()
    }
    
    /**
     * Set waveform data from audio analysis
     * Data should be normalized (0.0 to 1.0)
     */
    fun setWaveformData(data: FloatArray) {
        waveformData = data
        calculateDisplayData()
        invalidate()
        TXABackgroundLogger.d("Waveform data set: ${data.size} samples")
    }
    
    /**
     * Generate waveform from audio file path
     */
    fun generateFromPath(path: String) {
        // TODO: Implement waveform extraction
        // For now, generate dummy data
        generateDummyWaveform()
    }
    
    /**
     * Generate dummy waveform for testing/preview
     */
    fun generateDummyWaveform() {
        waveformData = FloatArray(200) { i ->
            val x = i / 200f
            // Create a nice wave pattern
            (Math.sin(x * Math.PI * 4).toFloat() * 0.3f + 0.5f +
             Math.random().toFloat() * 0.2f).coerceIn(0.2f, 1f)
        }
        calculateDisplayData()
        invalidate()
    }
    
    /**
     * Downscale waveform data to display size
     * Like Namida's _downscaledWaveformLists
     */
    private fun calculateDisplayData() {
        if (waveformData.isEmpty()) {
            displayData = FloatArray(barCount) { 0.5f }
            return
        }
        
        displayData = FloatArray(barCount)
        val samplesPerBar = waveformData.size / barCount.toFloat()
        
        for (i in 0 until barCount) {
            val startIndex = (i * samplesPerBar).toInt()
            val endIndex = ((i + 1) * samplesPerBar).toInt().coerceAtMost(waveformData.size)
            
            // Average the samples in this range
            var sum = 0f
            for (j in startIndex until endIndex) {
                sum += waveformData[j]
            }
            displayData[i] = if (endIndex > startIndex) sum / (endIndex - startIndex) else 0.5f
        }
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateGradient()
    }
    
    private fun updateGradient() {
        if (useGradient && width > 0) {
            val gradient = LinearGradient(
                0f, 0f, width.toFloat(), 0f,
                playedColor,
                ContextCompat.getColor(context, R.color.txa_primary_variant),
                Shader.TileMode.CLAMP
            )
            playedPaint.shader = gradient
        } else {
            playedPaint.shader = null
            playedPaint.color = playedColor
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (displayData.isEmpty()) return
        
        val totalBarWidth = barWidth + barSpacing
        val startX = (width - barCount * totalBarWidth + barSpacing) / 2f
        val centerY = height / 2f
        val maxBarHeight = (height - paddingTop - paddingBottom) * 0.9f
        
        val progressX = width * progress
        
        for (i in 0 until barCount) {
            val x = startX + i * totalBarWidth
            val barProgress = x / width.toFloat()
            
            // Calculate bar height with animation
            val baseHeight = displayData[i] * maxBarHeight
            val animatedHeight = if (barProgress <= progress) {
                // Add pulsing effect for played portion
                baseHeight * (1f + animationIntensity * 0.1f * Math.sin(System.currentTimeMillis() / 200.0 + i * 0.3).toFloat())
            } else {
                baseHeight
            }
            
            val finalHeight = animatedHeight.coerceAtLeast(minBarHeight)
            
            // Draw bar
            val left = x
            val top = centerY - finalHeight / 2
            val right = x + barWidth
            val bottom = centerY + finalHeight / 2
            
            val rect = RectF(left, top, right, bottom)
            val paint = if (barProgress <= progress) playedPaint else unplayedPaint
            
            canvas.drawRoundRect(rect, barCornerRadius, barCornerRadius, paint)
        }
        
        // Request redraw for animation
        if (animationIntensity > 0) {
            postInvalidateDelayed(50)
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val seekProgress = (event.x / width).coerceIn(0f, 1f)
                onSeek?.invoke(seekProgress)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
    
    private fun Float.dpToPx(): Float {
        return this * context.resources.displayMetrics.density
    }
    
    /**
     * Get current animating scale (like Namida's getCurrentAnimatingScale)
     */
    fun getCurrentAnimatingScale(): Float {
        val barIndex = (progress * barCount).toInt().coerceIn(0, displayData.size - 1)
        return if (displayData.isNotEmpty()) displayData[barIndex] else 1f
    }
}
