package com.txapp.musicplayer.util

import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay

/**
 * Custom Toast Types for different message styles
 */
enum class ToastType(
    val icon: ImageVector,
    val gradientColors: List<Color>,
    val iconTint: Color
) {
    SUCCESS(
        icon = Icons.Filled.CheckCircle,
        gradientColors = listOf(Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFF388E3C)),
        iconTint = Color(0xFF81C784)
    ),
    ERROR(
        icon = Icons.Filled.Error,
        gradientColors = listOf(Color(0xFFB71C1C), Color(0xFFC62828), Color(0xFFD32F2F)),
        iconTint = Color(0xFFEF9A9A)
    ),
    WARNING(
        icon = Icons.Filled.Warning,
        gradientColors = listOf(Color(0xFFE65100), Color(0xFFEF6C00), Color(0xFFF57C00)),
        iconTint = Color(0xFFFFCC80)
    ),
    INFO(
        icon = Icons.Outlined.Info,
        gradientColors = listOf(Color(0xFF0D47A1), Color(0xFF1565C0), Color(0xFF1976D2)),
        iconTint = Color(0xFF90CAF9)
    ),
    DEFAULT(
        icon = Icons.Filled.Notifications,
        gradientColors = listOf(Color(0xFF37474F), Color(0xFF455A64), Color(0xFF546E7A)),
        iconTint = Color(0xFFB0BEC5)
    )
}

/**
 * Toast Duration types
 */
enum class ToastDuration(val durationMs: Long) {
    SHORT(2500L),
    MEDIUM(3500L),
    LONG(5000L)
}

/**
 * Beautiful custom Toast implementation with modern Material3 design
 */
object TXAToast {
    private val handler = Handler(Looper.getMainLooper())
    private var currentToastView: ComposeView? = null
    private var currentLifecycleOwner: TXALifecycleOwner? = null
    private var windowManager: WindowManager? = null

    /**
     * Show a default toast message
     */
    fun show(context: Context, message: String) {
        show(context, message, ToastType.DEFAULT, ToastDuration.SHORT)
    }

    /**
     * Show toast with specific type
     */
    fun show(context: Context, message: String, type: ToastType) {
        show(context, message, type, ToastDuration.SHORT)
    }

    /**
     * Show success toast
     */
    fun success(context: Context, message: String, duration: ToastDuration = ToastDuration.SHORT) {
        show(context, message, ToastType.SUCCESS, duration)
    }

    /**
     * Show error toast
     */
    fun error(context: Context, message: String, duration: ToastDuration = ToastDuration.SHORT) {
        show(context, message, ToastType.ERROR, duration)
    }

    /**
     * Show warning toast
     */
    fun warning(context: Context, message: String, duration: ToastDuration = ToastDuration.SHORT) {
        show(context, message, ToastType.WARNING, duration)
    }

    /**
     * Show info toast
     */
    fun info(context: Context, message: String, duration: ToastDuration = ToastDuration.SHORT) {
        show(context, message, ToastType.INFO, duration)
    }

    /**
     * Main show function with full customization
     */
    fun show(
        context: Context,
        message: String,
        type: ToastType,
        duration: ToastDuration
    ) {
        // Ensure we're on main thread
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { show(context, message, type, duration) }
            return
        }

        // Dismiss any existing toast
        dismiss()

        // Check if we can draw overlays
        if (!android.provider.Settings.canDrawOverlays(context)) {
            // Fallback to system toast
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val ctx = context.applicationContext
            windowManager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val lifecycleOwner = TXALifecycleOwner()
            lifecycleOwner.onCreate()
            lifecycleOwner.onStart()
            lifecycleOwner.onResume()

            val composeView = ComposeView(ctx).apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)
                setContent {
                    TXAToastContent(
                        message = message,
                        type = type,
                        duration = duration
                    )
                }
            }
            currentLifecycleOwner = lifecycleOwner

            val layoutFlag = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = 100 // Distance from bottom
            }

            windowManager?.addView(composeView, params)
            currentToastView = composeView

            // Auto dismiss after duration
            handler.postDelayed({
                dismiss()
            }, duration.durationMs + 500) // Add animation time

        } catch (e: Exception) {
            TXALogger.appE("TXAToast", "Failed to show toast: ${e.message}")
            // Fallback to system toast
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Dismiss current toast
     */
    fun dismiss() {
        try {
            currentToastView?.let { view ->
                windowManager?.removeView(view)
            }
            currentLifecycleOwner?.onDestroy()
        } catch (e: Exception) {
            // View might already be removed
        }
        currentToastView = null
        currentLifecycleOwner = null
    }
}

/**
 * Composable content for the custom toast
 */
@Composable
private fun TXAToastContent(
    message: String,
    type: ToastType,
    duration: ToastDuration
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        visible = true
        delay(duration.durationMs)
        visible = false
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically(
            targetOffsetY = { it / 2 },
            animationSpec = tween(200)
        ) + fadeOut(animationSpec = tween(200))
    ) {
        ToastCard(message = message, type = type)
    }
}

/**
 * The visual card component of the toast
 */
@Composable
private fun ToastCard(
    message: String,
    type: ToastType
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    Card(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .widthIn(min = 200.dp, max = 340.dp)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = type.gradientColors[0].copy(alpha = 0.3f),
                spotColor = type.gradientColors[1].copy(alpha = 0.5f)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = type.gradientColors
                    )
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Animated Icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = type.icon,
                        contentDescription = null,
                        tint = type.iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Message Text
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    lineHeight = 20.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
