package ms.txams.vv.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import ms.txams.vv.R
import ms.txams.vv.core.TXABackgroundLogger

/**
 * TXA Liquid Tab Bar
 * Custom Bottom Navigation with Glassmorphism and Liquid animation style.
 * 
 * Inspired by: AndroidLiquidGlass
 * 
 * @author TXA - fb.com/vlog.txa.2311
 */
class TXALiquidTabBar : View {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    // Configuration
    private val tabCount = 2
    private var activeIndex = 0
    private var targetIndex = 0
    
    // Dimensions
    private var indicatorX = 0f
    private val indicatorHeight = 48f.dpToPx()
    private val indicatorWidth = 80f.dpToPx()
    private val cornerRadius = 24f.dpToPx()
    
    // Paints
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#20FFFFFF") // Semi-transparent white
        style = Paint.Style.FILL
    }
    
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40FFFFFF") // White border
        style = Paint.Style.STROKE
        strokeWidth = 1.5f.dpToPx()
    }
    
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.txa_primary)
        style = Paint.Style.FILL
        // Add glow effect
        setShadowLayer(16f, 0f, 4f, ContextCompat.getColor(context, R.color.txa_primary))
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14f.dpToPx()
        textAlign = Paint.Align.CENTER
        // typeFace = Typeface.DEFAULT_BOLD // Can set custom font here
    }

    private val activeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14f.dpToPx()
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    
    // Animation
    private var animator: ValueAnimator? = null
    
    // Callbacks
    var onTabSelected: ((Int) -> Unit)? = null
    
    private val tabTitles = listOf("Library", "Settings")
    private val tabIcons = listOf(R.drawable.ic_music_note, R.drawable.ic_settings) // Make sure these exist or use defaults
    
    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // For shadow layer (glow)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Initialize indicator position for the first time
        val tabWidth = width / tabCount.toFloat()
        indicatorX = (tabWidth * activeIndex) + (tabWidth - indicatorWidth) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        val tabWidth = width / tabCount
        
        // 1. Draw Glass Background
        val bgRect = RectF(0f, 0f, width, height)
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, glassPaint)
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, borderPaint)
        
        // 2. Draw Liquid Indicator
        // Calculate center Y
        val centerY = height / 2f
        val indicatorRect = RectF(
            indicatorX,
            centerY - indicatorHeight / 2f,
            indicatorX + indicatorWidth,
            centerY + indicatorHeight / 2f
        )
        canvas.drawRoundRect(indicatorRect, indicatorHeight / 2f, indicatorHeight / 2f, indicatorPaint)
        
        // 3. Draw Icons/Text
        val iconSize = 24f.dpToPx()
        
        for (i in 0 until tabCount) {
            val cx = (tabWidth * i) + (tabWidth / 2f)
            val cy = height / 2f
            
            // Draw text for now as it's simpler
            val isSelected = i == activeIndex // Use targetIndex if we want instant color switch, or heuristic based on position
            
            // Actually, let's just draw text.
            val paint = if (Math.abs(cx - (indicatorX + indicatorWidth/2)) < tabWidth/3) activeTextPaint else textPaint
            
            // Draw Text
            val textY = cy + (textPaint.textSize / 3f)
            canvas.drawText(tabTitles[i], cx, textY, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val tabWidth = width / tabCount
            val clickedIndex = (event.x / tabWidth).toInt().coerceIn(0, tabCount - 1)
            
            if (clickedIndex != activeIndex) {
                animateToTab(clickedIndex)
            }
        }
        return true
    }
    
    fun setActiveTab(index: Int) {
        if (index != activeIndex) {
            animateToTab(index)
        }
    }
    
    private fun animateToTab(index: Int) {
        targetIndex = index
        
        val tabWidth = width / tabCount.toFloat()
        val targetX = (tabWidth * index) + (tabWidth - indicatorWidth) / 2f
        
        animator?.cancel()
        animator = ValueAnimator.ofFloat(indicatorX, targetX).apply {
            duration = 400
            interpolator = OvershootInterpolator(1.2f) // Liquid bounce effect
            addUpdateListener {
                indicatorX = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        
        activeIndex = index
        onTabSelected?.invoke(index)
    }

    private fun Float.dpToPx(): Float {
        return this * context.resources.displayMetrics.density
    }
}
