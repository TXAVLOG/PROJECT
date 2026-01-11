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
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.txapp.musicplayer.R
import com.txapp.musicplayer.ui.component.LyricLine
import com.txapp.musicplayer.util.TXAFormat
import com.txapp.musicplayer.util.TXALogger
import com.txapp.musicplayer.util.TXAPreferences
import com.txapp.musicplayer.util.txa
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * Floating Lyrics Bubble Service - Messenger Style
 * 
 * Features:
 * - Draggable bubble that snaps to screen edges
 * - Tap to expand/collapse lyrics panel
 * - Drag to dismiss zone to close
 * - Works on Android 9+ and emulators
 * - Auto-saves position
 * - Localized strings
 */
class FloatingLyricsService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    companion object {
        private const val CHANNEL_ID = "floating_lyrics_channel"
        private const val NOTIFICATION_ID = 2024

        // Static action to trigger music controls from UI
        var onPlayPause: () -> Unit = {}
        var onNext: () -> Unit = {}
        var onPrev: () -> Unit = {}

        private val _currentLyric = MutableStateFlow("")
        val currentLyric = _currentLyric.asStateFlow()

        private val _songTitle = MutableStateFlow("")
        val songTitle = _songTitle.asStateFlow()

        private val _albumArtUri = MutableStateFlow("")
        val albumArtUri = _albumArtUri.asStateFlow()

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        private val _currentPosition = MutableStateFlow(0L)
        val currentPosition = _currentPosition.asStateFlow()
        
        private val _currentLyricsList = MutableStateFlow<List<LyricLine>>(emptyList())
        val currentLyricsList = _currentLyricsList.asStateFlow()

        private val _duration = MutableStateFlow(1L)
        val duration = _duration.asStateFlow()

        private val _isPlaying = MutableStateFlow(false)
        val isPlaying = _isPlaying.asStateFlow()

        fun updateLyric(lyric: String) {
            _currentLyric.value = lyric
        }

        fun updateLyricsList(lyrics: List<LyricLine>) {
            _currentLyricsList.value = lyrics
            // Reset current lyric when song changes/list updates
            _currentLyric.value = "" 
        }

        fun updatePlaybackState(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        fun updateDuration(duration: Long) {
            _duration.value = if (duration > 0) duration else 1L
        }

        fun updatePosition(position: Long) {
            _currentPosition.value = position
            // Update active lyric based on position
            val lyrics = _currentLyricsList.value
            if (lyrics.isNotEmpty()) {
                val activeIndex = lyrics.indexOfLast { it.timestamp <= position + 200 }.coerceAtLeast(0)
                if (activeIndex >= 0 && activeIndex < lyrics.size) {
                    val newLyric = lyrics[activeIndex].text
                    if (_currentLyric.value != newLyric) {
                        _currentLyric.value = newLyric
                    }
                }
            }
        }

        fun updateSongInfo(title: String, artUri: String = "") {
            if (_songTitle.value != title || _albumArtUri.value != artUri) {
                _songTitle.value = title
                _albumArtUri.value = artUri
                // Reset lyrics on new song
                _currentLyric.value = ""
                _currentLyricsList.value = emptyList() // Clear list to ensure fresh update
                TXALogger.floatingI("FloatingLyricsService", "Song info updated: $title, Art: $artUri")
            }
        }

        fun startService(context: Context) {
            if (!Settings.canDrawOverlays(context)) {
                TXALogger.floatingE("FloatingLyricsService", "Cannot start service: overlay permission not granted.")
                return
            }
            TXALogger.floatingI("FloatingLyricsService", "Starting service via startForegroundService")
            val intent = Intent(context, FloatingLyricsService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, FloatingLyricsService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    
    // Trash View
    private var trashView: ComposeView? = null
    private var trashLayoutParams: WindowManager.LayoutParams? = null
    private var isTrashVisible = false
    private val isTrashHighlighted = MutableStateFlow(false)
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val _viewModelStore = ViewModelStore()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Screen dimensions
    private var screenWidth = 0
    private var screenHeight = 0
    
    // Position tracking
    private var posX = 0
    private var posY = 0
    
    // State to track expanded mode to adjust layout params
    private var isExpandedMode = false

    private fun updateBubblePosition(deltaX: Float, deltaY: Float) {
        posX = (posX + deltaX.roundToInt()).coerceIn(0, screenWidth - 100)
        posY = (posY + deltaY.roundToInt()).coerceIn(0, screenHeight - 150)
        
        // Show trash if not visible
        if (!isTrashVisible) showTrash()
        
        // Check overlap with trash (assuming trash is at bottom center)
        val trashCenterX = screenWidth / 2
        val trashCenterY = screenHeight - 150 
        val bubbleCenterX = posX + 30 // Approx center offset
        val bubbleCenterY = posY + 30
        
        val distance = kotlin.math.sqrt(
            ((trashCenterX - bubbleCenterX) * (trashCenterX - bubbleCenterX) + 
             (trashCenterY - bubbleCenterY) * (trashCenterY - bubbleCenterY)).toFloat()
        )
        
        isTrashHighlighted.value = distance < 200 // Detection radius
        
        layoutParams?.apply {
            x = posX
            y = posY
        }
        try {
            floatingView?.let { windowManager.updateViewLayout(it, layoutParams) }
        } catch (e: Exception) {
            TXALogger.floatingE("FloatingLyricsService", "Failed to update layout", e)
        }
    }
    
    private fun snapBubbleToEdge() {
        if (isExpandedMode) return // Don't snap if expanded

        // Saving last valid position
        TXAPreferences.setFloatingLyricsPosition(posX, posY)

        val snapToRight = posX > screenWidth / 2 - 30
        val targetX = if (snapToRight) screenWidth - 140 else 20
        
        // Smooth animation using ValueAnimator
        val animator = android.animation.ValueAnimator.ofInt(posX, targetX)
        animator.duration = 200 // 200ms animation
        animator.interpolator = android.view.animation.DecelerateInterpolator()
        animator.addUpdateListener { valueAnimator ->
            posX = valueAnimator.animatedValue as Int
            layoutParams?.x = posX
            try {
                floatingView?.let { windowManager.updateViewLayout(it, layoutParams) }
            } catch (e: Exception) {
                // Ignore if view is already removed
            }
        }
        animator.start()
    }
    
    // Dim background view for expanded state
    private var dimView: ComposeView? = null
    private var dimLayoutParams: WindowManager.LayoutParams? = null
    
    private fun showDimBackground() {
        if (dimView != null) return
        
        dimLayoutParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }
        
        dimView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingLyricsService)
            setViewTreeSavedStateRegistryOwner(this@FloatingLyricsService)
            setContent {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                )
            }
        }
        
        try {
            windowManager.addView(dimView, dimLayoutParams)
        } catch (e: Exception) {
            TXALogger.floatingE("FloatingLyricsService", "Failed to add dim view", e)
        }
    }
    
    private fun hideDimBackground() {
        dimView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) { }
        }
        dimView = null
    }

    private fun setExpandedState(expanded: Boolean) {
        if (isExpandedMode == expanded) return
        isExpandedMode = expanded

        try {
            if (expanded) {
                // Show dim background first
                showDimBackground()
                
                // When expanded, we center the view on screen or make it match specific size
                // To keep it simple and safe from clipping, we Center it.
                // NOTE: We save the old bubble pos to restore later
                val savedPos = TXAPreferences.getFloatingLyricsPosition()
                if (savedPos.first != -1) {
                    posX = savedPos.first
                    posY = savedPos.second
                }
                
                layoutParams?.apply {
                    width = WindowManager.LayoutParams.WRAP_CONTENT
                    height = WindowManager.LayoutParams.WRAP_CONTENT
                    // Center on screen
                    gravity = Gravity.CENTER
                    x = 0
                    y = 0
                }
            } else {
                // Hide dim background
                hideDimBackground()
                
                // Restore to bubble mode
                val savedPos = TXAPreferences.getFloatingLyricsPosition()
                // If we have saved pos, use it, else default
                var restoreX = if (savedPos.first != -1) savedPos.first else (screenWidth - 140)
                var restoreY = if (savedPos.second != -1) savedPos.second else (screenHeight / 3)
                
                // Ensure valid bounds
                restoreX = restoreX.coerceIn(0, screenWidth - 100)
                restoreY = restoreY.coerceIn(0, screenHeight - 150)

                posX = restoreX
                posY = restoreY

                layoutParams?.apply {
                    width = WindowManager.LayoutParams.WRAP_CONTENT
                    height = WindowManager.LayoutParams.WRAP_CONTENT
                    gravity = Gravity.TOP or Gravity.START
                    x = posX
                    y = posY
                }
            }
            floatingView?.let { windowManager.updateViewLayout(it, layoutParams) }
        } catch (e: Exception) {
            TXALogger.floatingE("FloatingLyricsService", "Failed to update layout for expansion", e)
        }
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = _viewModelStore

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val displayMetrics = resources.displayMetrics
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        
        createNotificationChannel()
        setupTrashView()
        
        _isServiceRunning.value = true
        
        // Listen to preference changes to auto-stop
        serviceScope.launch {
            TXAPreferences.showLyricsInPlayer.collect { show ->
                if (!show) {
                    TXALogger.floatingI("FloatingLyricsService", "Preference disabled, stopping service")
                    stopSelf()
                }
            }
        }
        
        TXALogger.floatingI("FloatingLyricsService", "Service onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification())
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        showFloatingView()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        // Connect controls using startService to ensure reliable delivery
        onPlayPause = {
            val i = Intent(this, MusicService::class.java).apply {
                action = MusicService.ACTION_TOGGLE_PLAYBACK
            }
            startService(i)
        }
        onNext = {
            val i = Intent(this, MusicService::class.java).apply {
                action = MusicService.ACTION_NEXT
            }
            startService(i)
        }
        onPrev = {
            val i = Intent(this, MusicService::class.java).apply {
                action = MusicService.ACTION_PREVIOUS
            }
            startService(i)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // Save final position before verify kill
        if (!isExpandedMode) {
            TXAPreferences.setFloatingLyricsPosition(posX, posY)
        }
        
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        removeFloatingView()
        removeTrashView()
        hideDimBackground() // Clean up dim background
        serviceScope.cancel()
        viewModelStore.clear()
        _isServiceRunning.value = false
        TXALogger.floatingI("FloatingLyricsService", "Service onDestroy")
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "txamusic_floating_lyrics_title".txa(),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "txamusic_show_lyrics_overlay_desc".txa()
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
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
            .setContentTitle("txamusic_floating_lyrics_title".txa())
            .setContentText("txamusic_floating_lyrics_tap_hint".txa())
            .setSmallIcon(R.drawable.ic_audiotrack)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(R.drawable.ic_close, "txamusic_floating_lyrics_stop".txa(), stopPendingIntent)
            .build()
    }

    private fun showFloatingView() {
        if (floatingView != null) return

        layoutParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
        }
        
        // Initialize position from prefs
        val savedPos = TXAPreferences.getFloatingLyricsPosition()
        if (savedPos.first != -1 && savedPos.second != -1) {
            posX = savedPos.first
            posY = savedPos.second
        } else {
            posX = screenWidth - 140
            posY = screenHeight / 4
        }
        
        layoutParams?.x = posX
        layoutParams?.y = posY

        floatingView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingLyricsService)
            setViewTreeSavedStateRegistryOwner(this@FloatingLyricsService)
            setViewTreeViewModelStoreOwner(this@FloatingLyricsService)
            
            setContent {
                FloatingLyricsBubble(
                    onClose = { stopSelf() },
                    onDrag = { deltaX, deltaY ->
                        if (!isExpandedMode) updateBubblePosition(deltaX, deltaY)
                    },
                    onDragEnd = { 
                        if (isTrashHighlighted.value) {
                             stopSelf()
                        } else {
                             hideTrash()
                             snapBubbleToEdge()
                        }
                    },
                    onExpandStateChange = { expanded ->
                        setExpandedState(expanded)
                    },
                    screenWidth = screenWidth
                )
            }
        }

        try {
            if (floatingView?.parent != null) {
                windowManager.removeView(floatingView)
            }
            windowManager.addView(floatingView, layoutParams)
        } catch (e: Exception) {
            TXALogger.floatingE("FloatingLyricsService", "CRITICAL: Failed to add floating view", e)
        }
    }

    private fun removeFloatingView() {
        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                TXALogger.e("FloatingLyricsService", "Failed to remove floating view", e)
            }
        }
        floatingView = null
    }

    private fun setupTrashView() {
        trashLayoutParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else 
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 100 // Margin from bottom
        }

        trashView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingLyricsService)
            setViewTreeSavedStateRegistryOwner(this@FloatingLyricsService)
            
            setContent {
                val highlighted by isTrashHighlighted.collectAsState()
                TrashIcon(highlighted)
            }
        }
    }

    private fun showTrash() {
        if (!isTrashVisible && trashView != null) {
            try {
                windowManager.addView(trashView, trashLayoutParams)
                isTrashVisible = true
            } catch(e: Exception) { }
        }
    }

    private fun hideTrash() {
        if (isTrashVisible && trashView != null) {
            try {
                windowManager.removeView(trashView)
                isTrashVisible = false
                isTrashHighlighted.value = false
            } catch(e: Exception) { }
        }
    }
    
    private fun removeTrashView() {
        hideTrash()
        trashView = null
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
    onExpandStateChange: (Boolean) -> Unit,
    screenWidth: Int
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    
    val currentLyric by FloatingLyricsService.currentLyric.collectAsState()
    val songTitle by FloatingLyricsService.songTitle.collectAsState()
    val albumArtUri by FloatingLyricsService.albumArtUri.collectAsState()
    val lyricsList by FloatingLyricsService.currentLyricsList.collectAsState()
    val position by FloatingLyricsService.currentPosition.collectAsState()
    val isPlaying by FloatingLyricsService.isPlaying.collectAsState()

    LaunchedEffect(isExpanded) {
        onExpandStateChange(isExpanded)
    }

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
        androidx.compose.animation.AnimatedContent(
            targetState = isExpanded,
            transitionSpec = {
                if (targetState) {
                    (fadeIn(animationSpec = tween(200)) + 
                     scaleIn(initialScale = 0.8f, animationSpec = tween(200)))
                        .togetherWith(fadeOut(animationSpec = tween(150)))
                } else {
                    (fadeIn(animationSpec = tween(150)) + 
                     scaleIn(initialScale = 0.8f, animationSpec = tween(150)))
                        .togetherWith(fadeOut(animationSpec = tween(100)))
                }
            },
            label = "expandCollapse"
        ) { expanded ->
            if (expanded) {
                ExpandedLyricsPanel(
                    lyric = currentLyric,
                    songTitle = songTitle,
                    albumArtUri = albumArtUri,
                    lyricsList = lyricsList,
                    currentPosition = position,
                    isPlaying = isPlaying,
                    onCollapse = { isExpanded = false },
                    onClose = onClose
                )
            } else {
                CollapsedBubble(
                    hasLyrics = lyricsList.isNotEmpty(),
                    currentPosition = position,
                    isPlaying = isPlaying, // Pass isPlaying
                    onClick = { if (!isDragging) isExpanded = true },
                    // Pass empty lambdas for drag as they are handled by parent Box
                    onDragStart = {},
                    onDragEnd = {},
                    onDrag = {_,_ ->}
                )
            }
        }
    }
}

@Composable
fun CollapsedBubble(
    hasLyrics: Boolean,
    currentPosition: Long,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    val duration by FloatingLyricsService.duration.collectAsState()
    
    // Status Logic:
    // Playing -> Green Ring
    // Paused -> Amber Ring
    // Not Started/Invalid Duration -> Gray Ring
    val isPaused = !isPlaying && currentPosition > 0
    
    // Fix: If duration is invalid or very small, show 0 progress to avoid "Full" circle bug
    val validDuration = duration > 1000
    val progress = remember(currentPosition, duration) {
        if (validDuration) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, // Deeper pulse for better visibility
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .size(64.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Main Bubble Background
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(Color(0xFF1F1F1F))
                .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher),
                contentDescription = "App Logo",
                modifier = Modifier.size(52.dp).clip(CircleShape),
                alpha = if (isPlaying) pulseAlpha else 1f
            )
        }
        
        // Circular Progress Indicator
        // Color logic for the ring
        val ringColor = when {
            isPlaying -> Color(0xFF1DB954) // Green
            isPaused -> Color(0xFFFFB300) // Amber
            else -> Color.Gray.copy(alpha = 0.5f) // Dim
        }

        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(60.dp),
            color = ringColor,
            strokeWidth = 3.dp,
            trackColor = Color.Gray.copy(alpha = 0.3f) // Visible track so user sees it's a progress bar
        )

        // Timer overlay with formatted duration
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-4).dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(
                text = TXAFormat.formatDuration(currentPosition),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        // "New Message" Style Badge for Lyrics (Top Right - Outside bubble ring)
        if (hasLyrics) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.Red)
                    .border(1.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "1",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ExpandedLyricsPanel(
    lyric: String,
    songTitle: String,
    albumArtUri: String,
    lyricsList: List<LyricLine>,
    currentPosition: Long,
    isPlaying: Boolean,
    onCollapse: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(320.dp)
            .wrapContentHeight(),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF161616).copy(alpha = 0.95f),
        shadowElevation = 16.dp,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with Song Info, Quick Controls, Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                 // Album Art & Title
                 Row(
                     verticalAlignment = Alignment.CenterVertically,
                     modifier = Modifier.weight(1f)
                 ) {
                     AsyncImage(
                         model = ImageRequest.Builder(LocalContext.current)
                             .data(albumArtUri)
                             .crossfade(true)
                             .build(),
                         contentDescription = null,
                         contentScale = ContentScale.Crop,
                         modifier = Modifier
                             .size(44.dp)
                             .clip(CircleShape)
                             .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                         error = painterResource(id = R.drawable.ic_launcher)
                     )
                     
                     Spacer(modifier = Modifier.width(10.dp))
                     
                     Column {
                         Text(
                             text = songTitle.ifBlank { "txamusic_floating_lyrics_waiting".txa() },
                             color = Color.White,
                             fontSize = 14.sp,
                             fontWeight = FontWeight.Bold,
                             maxLines = 1,
                             overflow = TextOverflow.Ellipsis
                         )
                         Text(
                            text = TXAFormat.formatDuration(currentPosition),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp
                         )
                     }
                 }
                 
                 // Controls
                 Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { FloatingLyricsService.onPrev() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { FloatingLyricsService.onPlayPause() }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, 
                            null, 
                            tint = Color.White, 
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = { FloatingLyricsService.onNext() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                 }
            }
            
            // Second Row: Close/Collapse (top right corner logic is usually better but here we stack)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onCollapse, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Remove, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(18.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Lyrics content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp, max = 200.dp),
                contentAlignment = Alignment.Center
            ) {
                if (lyricsList.isEmpty()) {
                    Text(
                        text = "♪",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center
                    )
                } else {
                    val activeIndex = lyricsList.indexOfLast { it.timestamp <= currentPosition + 200 }.coerceAtLeast(0)
                    val activeLine = lyricsList.getOrNull(activeIndex)
                    val nextLine = lyricsList.getOrNull(activeIndex + 1)
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                         if (activeLine != null) {
                             val lineDuration = (activeLine.endTimestamp - activeLine.timestamp).coerceAtLeast(1)
                             val elapsed = (currentPosition - activeLine.timestamp).coerceAtLeast(0)
                             val progress = (elapsed.toFloat() / lineDuration.toFloat()).coerceIn(0f, 1f)
                             
                             Text(
                                 text = buildKaraokeText(activeLine.text, progress),
                                 fontSize = 18.sp,
                                 fontWeight = FontWeight.Bold,
                                 textAlign = TextAlign.Center,
                                 lineHeight = 26.sp,
                                 modifier = Modifier.fillMaxWidth()
                             )
                         }
                         
                         if (nextLine != null) {
                             Spacer(modifier = Modifier.height(8.dp))
                             Text(
                                 text = nextLine.text,
                                 fontSize = 14.sp,
                                 color = Color.White.copy(alpha = 0.4f),
                                 textAlign = TextAlign.Center,
                                 maxLines = 1,
                                 overflow = TextOverflow.Ellipsis
                             )
                         }
                    }
                }
            }
        }
    }
}

fun buildKaraokeText(text: String, progress: Float): AnnotatedString {
    return buildAnnotatedString {
        val pivot = (text.length * progress).toInt()
        withStyle(SpanStyle(color = Color.Green, fontWeight = FontWeight.Bold)) {
            append(text.substring(0, pivot.coerceAtMost(text.length)))
        }
        withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Normal)) {
            append(text.substring(pivot.coerceAtMost(text.length)))
        }
    }
}

@Composable
fun TrashIcon(highlighted: Boolean) {
    val scale by animateFloatAsState(if (highlighted) 1.5f else 1.0f, label = "trashScale")
    val color by animateColorAsState(if (highlighted) Color.Red else Color.White.copy(alpha = 0.6f), label = "trashColor")
    
    Box(
        modifier = Modifier
            .size(60.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = if (highlighted) -20f else 0f
            }
            .clip(CircleShape) // Ensure drag area is circular
            .background(Color.Black.copy(alpha = 0.5f), CircleShape), // Ensure background is circular
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = color,
            modifier = Modifier.size(32.dp)
        )
    }
}
