package com.txapp.musicplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.txapp.musicplayer.R
import com.txapp.musicplayer.util.TXALogger
import com.txapp.musicplayer.util.TXAPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Floating Lyrics Bubble Service - Messenger Style
 * 
 * Features:
 * - Draggable bubble that snaps to screen edges
 * - Tap to expand/collapse lyrics panel
 * - Drag to dismiss zone to close
 * - Works on Android 9+ and emulators
 */
class FloatingLyricsService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        private const val CHANNEL_ID = "floating_lyrics_channel"
        private const val NOTIFICATION_ID = 2024

        private val _currentLyric = MutableStateFlow("")
        val currentLyric = _currentLyric.asStateFlow()

        private val _songTitle = MutableStateFlow("")
        val songTitle = _songTitle.asStateFlow()

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        fun updateLyric(lyric: String) {
            _currentLyric.value = lyric
        }

        fun updateSongInfo(title: String) {
            _songTitle.value = title
        }

        fun startService(context: Context) {
            if (!Settings.canDrawOverlays(context)) {
                TXALogger.w("FloatingLyricsService", "Cannot start service: overlay permission not granted")
                return
            }
            val intent = Intent(context, FloatingLyricsService::class.java)
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, FloatingLyricsService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Screen dimensions
    private var screenWidth = 0
    private var screenHeight = 0
    
    // Position tracking
    private var posX = 0
    private var posY = 0
    
    private fun updateBubblePosition(deltaX: Float, deltaY: Float) {
        posX = (posX + deltaX.roundToInt()).coerceIn(0, screenWidth - 150)
        posY = (posY + deltaY.roundToInt()).coerceIn(0, screenHeight - 200)
        layoutParams?.apply {
            x = posX
            y = posY
        }
        try {
            floatingView?.let { windowManager.updateViewLayout(it, layoutParams) }
        } catch (e: Exception) {
            TXALogger.e("FloatingLyricsService", "Failed to update layout", e)
        }
    }
    
    private fun snapBubbleToEdge() {
        val snapToRight = posX > screenWidth / 2
        posX = if (snapToRight) screenWidth - 170 else 20
        layoutParams?.x = posX
        try {
            floatingView?.let { windowManager.updateViewLayout(it, layoutParams) }
        } catch (e: Exception) {
            TXALogger.e("FloatingLyricsService", "Failed to snap layout", e)
        }
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // Get screen dimensions
        val displayMetrics = resources.displayMetrics
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        
        createNotificationChannel()
        _isServiceRunning.value = true
        TXALogger.i("FloatingLyricsService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle stop action from notification
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        
        if (!Settings.canDrawOverlays(this)) {
            TXALogger.w("FloatingLyricsService", "Overlay permission not granted, stopping service")
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification())
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        showFloatingView()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        removeFloatingView()
        serviceScope.cancel()
        _isServiceRunning.value = false
        TXALogger.i("FloatingLyricsService", "Service destroyed")
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Floating Lyrics",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows floating lyrics overlay"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, FloatingLyricsService::class.java).apply {
            action = "STOP"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Floating Lyrics")
            .setContentText("Tap bubble to show lyrics")
            .setSmallIcon(R.drawable.ic_audiotrack)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(R.drawable.ic_close, "Stop", stopPendingIntent)
            .build()
    }

    private fun showFloatingView() {
        if (floatingView != null) return

        layoutParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
        }
        
        // Initialize position
        posX = screenWidth - 200
        posY = screenHeight / 3
        layoutParams?.x = posX
        layoutParams?.y = posY

        floatingView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingLyricsService)
            setViewTreeSavedStateRegistryOwner(this@FloatingLyricsService)
            
            setContent {
                FloatingLyricsBubble(
                    onClose = { stopSelf() },
                    onDrag = { deltaX, deltaY ->
                        updateBubblePosition(deltaX, deltaY)
                    },
                    onDragEnd = { 
                        snapBubbleToEdge()
                    },
                    screenWidth = screenWidth
                )
            }
        }

        try {
            windowManager.addView(floatingView, layoutParams)
            TXALogger.i("FloatingLyricsService", "Floating bubble added")
        } catch (e: Exception) {
            TXALogger.e("FloatingLyricsService", "Failed to add floating view", e)
        }
    }

    private fun removeFloatingView() {
        floatingView?.let {
            try {
                windowManager.removeView(it)
                TXALogger.i("FloatingLyricsService", "Floating view removed")
            } catch (e: Exception) {
                TXALogger.e("FloatingLyricsService", "Failed to remove floating view", e)
            }
        }
        floatingView = null
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// COMPOSABLE UI
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FloatingLyricsBubble(
    onClose: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    screenWidth: Int
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    val currentLyric by FloatingLyricsService.currentLyric.collectAsState()
    val songTitle by FloatingLyricsService.songTitle.collectAsState()
    
    // Animate bubble size
    val bubbleSize by animateDpAsState(
        targetValue = if (isExpanded) 0.dp else 56.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "bubbleSize"
    )

    Box(
        modifier = Modifier
            .wrapContentSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { 
                        isDragging = false
                        onDragEnd()
                    },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                )
            }
    ) {
        AnimatedContent(
            targetState = isExpanded,
            transitionSpec = {
                if (targetState) {
                    // Expanding
                    (fadeIn(animationSpec = tween(200)) + 
                     scaleIn(initialScale = 0.8f, animationSpec = tween(200)))
                        .togetherWith(fadeOut(animationSpec = tween(150)))
                } else {
                    // Collapsing
                    (fadeIn(animationSpec = tween(150)) + 
                     scaleIn(initialScale = 0.8f, animationSpec = tween(150)))
                        .togetherWith(fadeOut(animationSpec = tween(100)))
                }
            },
            label = "expandCollapse"
        ) { expanded ->
            if (expanded) {
                // Expanded lyrics panel
                ExpandedLyricsPanel(
                    lyric = currentLyric,
                    songTitle = songTitle,
                    onCollapse = { isExpanded = false },
                    onClose = onClose
                )
            } else {
                // Collapsed bubble
                CollapsedBubble(
                    hasLyrics = currentLyric.isNotBlank(),
                    onClick = { if (!isDragging) isExpanded = true }
                )
            }
        }
    }
}

@Composable
private fun CollapsedBubble(
    hasLyrics: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    Box(
        modifier = Modifier
            .size(56.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF6366F1), // Indigo
                        Color(0xFF8B5CF6)  // Purple
                    )
                )
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.MusicNote,
            contentDescription = "Lyrics",
            tint = Color.White.copy(alpha = if (hasLyrics) pulseAlpha else 0.7f),
            modifier = Modifier.size(28.dp)
        )
        
        // Indicator dot when has lyrics
        if (hasLyrics) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-4).dp, y = 4.dp)
                    .size(12.dp)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E)) // Green
            )
        }
    }
}

@Composable
private fun ExpandedLyricsPanel(
    lyric: String,
    songTitle: String,
    onCollapse: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(280.dp)
            .wrapContentHeight(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1F1F1F),
        shadowElevation = 16.dp,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Lyrics",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (songTitle.isNotBlank()) {
                            Text(
                                text = songTitle,
                                color = Color.Gray,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                
                Row {
                    // Collapse button
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Collapse",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    // Close button
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.1f))
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Lyrics content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp, max = 150.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = lyric.ifBlank { "♪ Waiting for lyrics... ♪" },
                    color = if (lyric.isBlank()) Color.Gray else Color.White,
                    fontSize = 15.sp,
                    fontWeight = if (lyric.isBlank()) FontWeight.Normal else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
