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
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.txapp.musicplayer.util.LyricsUtil
import com.txapp.musicplayer.ui.component.LyricLine
import com.txapp.musicplayer.util.TXAFormat
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
class FloatingLyricsService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    companion object {
        private const val CHANNEL_ID = "floating_lyrics_channel"
        private const val NOTIFICATION_ID = 2024

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

        fun updateLyric(lyric: String) {
            _currentLyric.value = lyric
        }

        fun updateLyricsList(lyrics: List<LyricLine>) {
            _currentLyricsList.value = lyrics
        }

        fun updatePosition(position: Long) {
            _currentPosition.value = position
            // Update active lyric based on position
            val lyrics = _currentLyricsList.value
            if (lyrics.isNotEmpty()) {
                val activeIndex = lyrics.indexOfLast { it.timestamp <= position + 500 }.coerceAtLeast(0)
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
                TXALogger.floatingI("FloatingLyricsService", "Song info updated: $title, Art: $artUri")
            }
        }

        fun startService(context: Context) {
            if (!Settings.canDrawOverlays(context)) {
                TXALogger.floatingE("FloatingLyricsService", "Cannot start service: overlay permission not granted. Requesting via intent.")
                // Should ideally show the system permission screen
                return
            }
            TXALogger.floatingI("FloatingLyricsService", "Starting service via startForegroundService")
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
    
    private fun updateBubblePosition(deltaX: Float, deltaY: Float) {
        posX = (posX + deltaX.roundToInt()).coerceIn(0, screenWidth - 150)
        posY = (posY + deltaY.roundToInt()).coerceIn(0, screenHeight - 200)
        
        // Show trash if not visible
        if (!isTrashVisible) showTrash()
        
        // Check overlap with trash (assuming trash is at bottom center)
        val trashCenterX = screenWidth / 2
        val trashCenterY = screenHeight - 150 // Approx y position including margin
        val bubbleCenterX = posX + 75 // Assuming bubble size ~56dp + margin ~ 150/2
        val bubbleCenterY = posY + 75
        
        val distance = kotlin.math.sqrt(
            ((trashCenterX - bubbleCenterX) * (trashCenterX - bubbleCenterX) + 
             (trashCenterY - bubbleCenterY) * (trashCenterY - bubbleCenterY)).toFloat()
        )
        
        isTrashHighlighted.value = distance < 300 // Detection radius
        
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
        val snapToRight = posX > screenWidth / 2
        posX = if (snapToRight) screenWidth - 170 else 20
        layoutParams?.x = posX
        try {
            floatingView?.let { windowManager.updateViewLayout(it, layoutParams) }
            TXALogger.floatingI("FloatingLyricsService", "Snapped to edge: x=$posX")
        } catch (e: Exception) {
            TXALogger.floatingE("FloatingLyricsService", "Failed to snap layout at x=$posX", e)
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
        
        // Get screen dimensions
        val displayMetrics = resources.displayMetrics
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        
        
        createNotificationChannel()
        setupTrashView()
        _isServiceRunning.value = true
        TXALogger.floatingI("FloatingLyricsService", "Service onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle stop action from notification
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        
        if (!Settings.canDrawOverlays(this)) {
            TXALogger.floatingE("FloatingLyricsService", "Overlay permission not granted in onStartCommand, stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        TXALogger.floatingI("FloatingLyricsService", "onStartCommand: Starting foreground and showing view")

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
        removeTrashView()
        serviceScope.cancel()
        viewModelStore.clear()
        _isServiceRunning.value = false
        TXALogger.floatingI("FloatingLyricsService", "Service onDestroy - Floating view and ViewModelStore cleaned up")
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
            setViewTreeViewModelStoreOwner(this@FloatingLyricsService)
            
            setContent {
                FloatingLyricsBubble(
                    onClose = { stopSelf() },
                    onDrag = { deltaX, deltaY ->
                        updateBubblePosition(deltaX, deltaY)
                    },
                    onDragEnd = { 
                        if (isTrashHighlighted.value) {
                             stopSelf()
                        } else {
                             hideTrash()
                             snapBubbleToEdge()
                        }
                    },
                    screenWidth = screenWidth
                )
            }
        }

        try {
            TXALogger.floatingI("FloatingLyricsService", "Attempting to add view to WindowManager...")
            if (floatingView?.parent != null) {
                windowManager.removeView(floatingView)
            }
            windowManager.addView(floatingView, layoutParams)
            TXALogger.floatingI("FloatingLyricsService", "SUCCESS: Floating bubble added. Visible at $posX, $posY")
        } catch (e: Exception) {
            TXALogger.floatingE("FloatingLyricsService", "CRITICAL: Failed to add floating view to WindowManager", e)
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

    private fun setupTrashView() {
        trashLayoutParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else 
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
            // No ViewModel needed for static trash
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
            } catch(e: Exception) { 
                TXALogger.floatingE("FloatingLyricsService", "Error showing trash", e) 
            }
        }
    }

    private fun hideTrash() {
        if (isTrashVisible && trashView != null) {
            try {
                windowManager.removeView(trashView)
                isTrashVisible = false
                isTrashHighlighted.value = false
            } catch(e: Exception) { 
                TXALogger.floatingE("FloatingLyricsService", "Error hiding trash", e) 
            }
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
    screenWidth: Int
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    
    val currentLyric by FloatingLyricsService.currentLyric.collectAsState()
    val songTitle by FloatingLyricsService.songTitle.collectAsState()
    val albumArtUri by FloatingLyricsService.albumArtUri.collectAsState()
    val lyricsList by FloatingLyricsService.currentLyricsList.collectAsState()
    val position by FloatingLyricsService.currentPosition.collectAsState()
    
    // Animate bubble size
    val bubbleSize by animateDpAsState(
        targetValue = if (isExpanded) 0.dp else 60.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "bubbleSize"
    )

    // Logging state changes
    LaunchedEffect(isExpanded) {
        TXALogger.floatingI("FloatingLyricsBubble", "State changed: isExpanded=$isExpanded")
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
                    onCollapse = { isExpanded = false },
                    onClose = onClose
                )
            } else {
                CollapsedBubble(
                    hasLyrics = currentLyric.isNotBlank(),
                    currentPosition = position,
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
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .size(60.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(Color(0xFF1F1F1F))
            .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher),
            contentDescription = "App Logo",
            modifier = Modifier.size(56.dp).clip(CircleShape),
            alpha = if (hasLyrics) pulseAlpha else 1f
        )
        
        // Timer overlay with formatted duration
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-8).dp)
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
        
        // Indicator dot when has lyrics
        if (hasLyrics) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-8).dp, y = (8).dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E))
                    .border(1.dp, Color.White, CircleShape)
            )
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
    onCollapse: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(300.dp)
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
            // Header with Album Art, Title, Close/Collapse
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                 // Song info
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
                             .size(40.dp)
                             .clip(CircleShape)
                             .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                         error = painterResource(id = R.drawable.ic_launcher)
                     )
                     
                     Spacer(modifier = Modifier.width(10.dp))
                     
                     Column {
                         Text(
                             text = songTitle,
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
                 
                 Row {
                    IconButton(onClick = onCollapse, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Remove, 
                            null, 
                            tint = Color.White.copy(0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close, 
                            null, 
                            tint = Color.White.copy(0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                 }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Lyrics content - Karaoke Style
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp, max = 200.dp),
                contentAlignment = Alignment.Center
            ) {
                if (lyricsList.isEmpty()) {
                    Text(
                        text = lyric.ifBlank { "♪ ..." },
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                } else {
                    // Find active line with a slightly larger lookahead for smoother transition
                    val activeIndex = lyricsList.indexOfLast { it.timestamp <= currentPosition + 200 }.coerceAtLeast(0)
                    val activeLine = lyricsList.getOrNull(activeIndex)
                    val nextLine = lyricsList.getOrNull(activeIndex + 1)
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                         // Active Line with Karaoke effect
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
                         
                         // Next Line (faded)
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

// Helper for karaoke text
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
            }
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f)),
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
