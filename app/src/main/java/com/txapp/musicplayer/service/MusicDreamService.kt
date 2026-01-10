package com.txapp.musicplayer.service

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.dreams.DreamService
import android.view.View
import android.view.WindowManager
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.txapp.musicplayer.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.txapp.musicplayer.util.TXALogger
import com.txapp.musicplayer.util.TXAPreferences
import com.txapp.musicplayer.util.TXAFormat
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.txapp.musicplayer.ui.component.DefaultAlbumArt
import com.txapp.musicplayer.ui.component.TXASleepTimerManager
import android.provider.Settings.Secure


/**
 * MusicDreamService - System Screen Saver with Media Controls
 *
 * Features:
 * - Pure black OLED background for screen protection
 * - Pixel shifting every 60 seconds to prevent burn-in
 * - Breathing alpha animation (0.4 to 0.7) for clock
 * - Media3 integration for playback control
 * - Single tap on background to exit, media controls work without exiting
 * - Support for both Portrait and Landscape orientation
 *
 * Compatible with Android 9 (API 28) and above.
 */
@Suppress("DEPRECATION")
class MusicDreamService : DreamService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    companion object {
        private const val TAG = "MusicDreamService"
        
        // Pixel shifting constants
        private const val SHIFT_INTERVAL_MS = 60_000L // 60 seconds
        private const val MAX_SHIFT_PIXELS = 50f // Maximum shift in any direction
        
        // Breathing animation constants
        private const val BREATHING_MIN_ALPHA = 0.4f
        private const val BREATHING_MAX_ALPHA = 0.7f
        private const val BREATHING_DURATION_MS = 4000

        private val digitalFont = FontFamily(Font(R.font.digital_7))
    }

    // Lifecycle management for Compose
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val _viewModelStore = ViewModelStore()
    
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = _viewModelStore

    // Media3 MediaController
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    // Coroutine scope for async operations
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Pixel shifting handler
    private val shiftHandler = Handler(Looper.getMainLooper())
    private var shiftX by mutableFloatStateOf(0f)
    private var shiftY by mutableFloatStateOf(0f)

    // State for UI
    private val _currentMediaItem = mutableStateOf<MediaItem?>(null)
    private val _isPlaying = mutableStateOf(false)
    private val _currentPosition = mutableLongStateOf(0L)
    private val _duration = mutableLongStateOf(0L)

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentMediaItem.value = mediaItem
            _duration.longValue = mediaController?.duration ?: 0L
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            // Update when metadata changes (e.g., artwork loaded)
            _currentMediaItem.value = mediaController?.currentMediaItem
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                _duration.longValue = mediaController?.duration ?: 0L
            }
        }
    }

    // Pixel shift runnable
    private val shiftRunnable = object : Runnable {
        override fun run() {
            // Random shift within bounds
            shiftX = (Math.random() * 2 - 1).toFloat() * 50f
            shiftY = (Math.random() * 2 - 1).toFloat() * 50f
            TXALogger.appI(TAG, "Pixel shift: x=$shiftX, y=$shiftY")
            shiftHandler.postDelayed(this, com.txapp.musicplayer.util.TXAAODSettings.pixelShiftInterval.value)
        }
    }

    // Position update job
    private var positionUpdateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        TXALogger.appI(TAG, "MusicDreamService onCreate")
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        
        // Initialize AOD settings from SharedPreferences
        com.txapp.musicplayer.util.TXAAODSettings.init(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        TXALogger.appI(TAG, "MusicDreamService onAttachedToWindow")

        // Dream configuration
        isInteractive = true
        isFullscreen = true
        isScreenBright = false // Keep screen dim for OLED protection

        // Setup window flags for fullscreen immersive
        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }

        // Setup owners on decorView to ensure Compose can find them during attachment
        window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
        }

        // Create Compose content
        val composeView = ComposeView(this).apply {
            setContent {
                DreamContent()
            }
        }

        setContentView(composeView)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        TXALogger.appI(TAG, "MusicDreamService onDreamingStarted")
        
        // Move lifecycle to STARTED then RESUMED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        // Start pixel shifting
        shiftHandler.postDelayed(shiftRunnable, com.txapp.musicplayer.util.TXAAODSettings.pixelShiftInterval.value)

        // Connect to MediaSession
        connectToMediaSession()
        
        // Start position update loop
        startPositionUpdate()
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        TXALogger.appI(TAG, "MusicDreamService onDreamingStopped")
        
        // Stop pixel shifting
        shiftHandler.removeCallbacks(shiftRunnable)
        
        // Stop position updates
        positionUpdateJob?.cancel()

        // Move lifecycle to STARTED (paused)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        TXALogger.appI(TAG, "MusicDreamService onDetachedFromWindow")
        
        // Cleanup MediaController
        mediaController?.removeListener(playerListener)
        mediaControllerFuture?.let { future ->
            MediaController.releaseFuture(future)
        }
        mediaController = null
        mediaControllerFuture = null

        // Cancel all coroutines
        serviceScope.cancel()

        // Move lifecycle to DESTROYED
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        _viewModelStore.clear()
    }

    private fun connectToMediaSession() {
        TXALogger.appI(TAG, "Connecting to MediaSession...")
        
        val sessionToken = SessionToken(
            this,
            ComponentName(this, MusicService::class.java)
        )

        mediaControllerFuture = MediaController.Builder(this, sessionToken)
            .buildAsync()

        mediaControllerFuture?.addListener({
            try {
                mediaController = mediaControllerFuture?.get()
                mediaController?.addListener(playerListener)
                
                // Update initial state
                _currentMediaItem.value = mediaController?.currentMediaItem
                _isPlaying.value = mediaController?.isPlaying == true
                _duration.longValue = mediaController?.duration ?: 0L
                _currentPosition.longValue = mediaController?.currentPosition ?: 0L
                
                TXALogger.appI(TAG, "MediaController connected successfully")
            } catch (e: Exception) {
                TXALogger.appE(TAG, "Failed to connect MediaController", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun startPositionUpdate() {
        positionUpdateJob = serviceScope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    _currentPosition.longValue = controller.currentPosition
                }
                delay(1000)
            }
        }
    }

    @Composable
    private fun DreamContent() {
        val currentMediaItem by _currentMediaItem
        val isPlaying by _isPlaying
        val currentPosition by _currentPosition
        val duration by _duration
        val context = androidx.compose.ui.platform.LocalContext.current
        
        // Sleep Timer State
        var sleepTimerRemaining by remember { mutableLongStateOf(0L) }
        
        // Read AOD settings - Using TXAAODSettings for all visual consistency
        val showControls by com.txapp.musicplayer.util.TXAAODSettings.showControls.collectAsState()
        val nightMode by com.txapp.musicplayer.util.TXAAODSettings.nightMode.collectAsState()
        val showDate by com.txapp.musicplayer.util.TXAAODSettings.showDate.collectAsState()
        val dateFormat by com.txapp.musicplayer.util.TXAAODSettings.dateFormat.collectAsState()
        val showBattery by com.txapp.musicplayer.util.TXAAODSettings.showBattery.collectAsState()
        val clockColor = com.txapp.musicplayer.util.TXAAODSettings.getClockColorCompose()
        val breathingAnimation by com.txapp.musicplayer.util.TXAAODSettings.breathingAnimation.collectAsState()
        val pixelShiftInterval by com.txapp.musicplayer.util.TXAAODSettings.pixelShiftInterval.collectAsState()
        val aodOpacity by com.txapp.musicplayer.util.TXAAODSettings.opacity.collectAsState()
        
        // Night mode reduces overall brightness significantly
        val nightModeAlpha = if (nightMode) 0.3f else 1f
        val finalAlpha = nightModeAlpha * aodOpacity

        // Breathing alpha animation
        val infiniteTransition = rememberInfiniteTransition(label = "breathing")
        val breathingAlpha by infiniteTransition.animateFloat(
            initialValue = BREATHING_MIN_ALPHA,
            targetValue = BREATHING_MAX_ALPHA,
            animationSpec = infiniteRepeatable(
                animation = tween(BREATHING_DURATION_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breathingAlpha"
        )
        
        val currentBreathingAlpha = if (breathingAnimation) breathingAlpha else 1f

        // Animated shift values
        val animatedShiftX by animateFloatAsState(
            targetValue = shiftX,
            animationSpec = tween(2000, easing = LinearOutSlowInEasing),
            label = "shiftX"
        )
        val animatedShiftY by animateFloatAsState(
            targetValue = shiftY,
            animationSpec = tween(2000, easing = LinearOutSlowInEasing),
            label = "shiftY"
        )
        
        // Update Sleep Timer
        LaunchedEffect(Unit) {
            while (true) {
                sleepTimerRemaining = TXASleepTimerManager.getRemainingTime(context)
                delay(1000)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black)
                .alpha(finalAlpha)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    // Single tap on background exits the dream
                    TXALogger.appI(TAG, "Background tapped, finishing dream")
                    finish()
                }
        ) {
            // Main content with pixel shifting
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = animatedShiftX.dp, y = animatedShiftY.dp)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Clock with breathing alpha
                    ClockDisplay(
                        modifier = Modifier.alpha(currentBreathingAlpha),
                        showDate = showDate,
                        dateFormat = dateFormat,
                        showBattery = showBattery,
                        clockColor = clockColor
                    )
                    
                    // Sleep Timer Indicator
                    if (sleepTimerRemaining > 0) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Bedtime, 
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.txamusic_sleep_timer) + " • " + formatSleepTimer(sleepTimerRemaining),
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f),
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    // Music info and controls
                    val mediaItem = currentMediaItem
                    if (mediaItem != null) {
                        MusicInfoCard(
                            mediaItem = mediaItem,
                            isPlaying = isPlaying,
                            currentPosition = currentPosition,
                            duration = duration,
                            showControls = showControls,
                            clockColor = clockColor,
                            onPlayPause = {
                                mediaController?.let { controller ->
                                    if (controller.isPlaying) {
                                        controller.pause()
                                    } else {
                                        controller.play()
                                    }
                                }
                            },
                            onPrevious = {
                                mediaController?.seekToPrevious()
                            },
                            onNext = {
                                mediaController?.seekToNext()
                            }
                        )
                    } else { // Corrected: added 'else'
                        // No music playing state
                        NoMusicPlaying()
                    }
                }
            }
        }
    }

    @Composable
    private fun ClockDisplay(
        modifier: Modifier = Modifier,
        showDate: Boolean,
        dateFormat: Int,
        showBattery: Boolean,
        clockColor: androidx.compose.ui.graphics.Color
    ) {
        var currentTime by remember { mutableStateOf(getCurrentTime()) }
        var currentSeconds by remember { mutableStateOf(getCurrentSeconds()) }
        var currentDate by remember { mutableStateOf("") }
        var batteryLevel by remember { mutableIntStateOf(0) }
        var isCharging by remember { mutableStateOf(false) }
        val context = LocalContext.current
        
        // Use a persistent breathing animation for OLED protection and aesthetics
        val infiniteTransition = rememberInfiniteTransition(label = "breathing")
        val breathingAlpha by infiniteTransition.animateFloat(
            initialValue = BREATHING_MIN_ALPHA,
            targetValue = BREATHING_MAX_ALPHA,
            animationSpec = infiniteRepeatable(
                animation = tween(BREATHING_DURATION_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )
        
        // Update clock and battery every second
        LaunchedEffect(dateFormat) {
            while (true) {
                currentTime = getCurrentTime()
                currentSeconds = getCurrentSeconds()
                currentDate = getCurrentDateFormatted(dateFormat)
                
                // Fetch battery status
                val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                batteryLevel = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                
                delay(1000)
            }
        }
        
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Battery indicator at the very top for detail
            if (showBattery) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.alpha(0.9f * breathingAlpha)
                ) {
                    Icon(
                        imageVector = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                        contentDescription = null,
                        tint = clockColor,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$batteryLevel%",
                        color = clockColor,
                        fontSize = 72.dp.value.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Clock HH:mm:SS all same size and huge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$currentTime:$currentSeconds",
                    color = clockColor.copy(alpha = breathingAlpha),
                    fontSize = 150.dp.value.sp, // EVEN HUGER as requested
                    fontFamily = digitalFont,
                    letterSpacing = 2.sp,
                    lineHeight = 150.dp.value.sp
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            // More detailed Date display
            if (showDate) {
                Surface(
                    color = clockColor.copy(alpha = 0.05f * breathingAlpha),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = currentDate.uppercase(),
                        color = clockColor.copy(alpha = 0.6f * breathingAlpha),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    @Composable
    private fun MusicInfoCard(
        mediaItem: MediaItem,
        isPlaying: Boolean,
        currentPosition: Long,
        duration: Long,
        showControls: Boolean,
        clockColor: androidx.compose.ui.graphics.Color,
        onPlayPause: () -> Unit,
        onPrevious: () -> Unit,
        onNext: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Artwork with fallback
            Box(
                modifier = Modifier
                    .size(200.dp) // Larger artwork
                    .shadow(
                        elevation = 24.dp, 
                        shape = RoundedCornerShape(24.dp), 
                        spotColor = androidx.compose.ui.graphics.Color.White.copy(0.2f)
                    )
                    .clip(RoundedCornerShape(24.dp))
                    .background(androidx.compose.ui.graphics.Color.DarkGray)
            ) {
                 val artUri = mediaItem.mediaMetadata.artworkUri
                 
                 SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(artUri)
                        .crossfade(true)
                        .size(600) // Load high quality
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                ) {
                    val state = painter.state
                    if (state is coil.compose.AsyncImagePainter.State.Loading || state is coil.compose.AsyncImagePainter.State.Error) {
                        DefaultAlbumArt(vibrantColor = androidx.compose.ui.graphics.Color.Gray)
                    } else {
                        SubcomposeAsyncImageContent()
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Title & Artist
            Text(
                text = mediaItem.mediaMetadata.title?.toString() ?: "Unknown Title",
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = mediaItem.mediaMetadata.artist?.toString() ?: "Unknown Artist",
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Progress
            if (duration > 0) {
                val progress = currentPosition.toFloat() / duration.toFloat()
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(CircleShape),
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
                    trackColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f),
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = TXAFormat.formatDuration(currentPosition),
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = TXAFormat.formatDuration(duration),
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp
                    )
                }
            }

            // Controls (Conditional)
            if (showControls) {
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPrevious) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Previous",
                            tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f))
                            .clickable { onPlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = clockColor,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    IconButton(onClick = onNext) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Next",
                            tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun NoMusicPlaying() {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.MusicNote,
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f),
                modifier = Modifier.size(100.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.txamusic_aod_no_music),
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.3f),
                fontSize = 14.sp
            )
        }
    }

    private fun getCurrentTime(): String {
        val now = java.util.Calendar.getInstance()
        val hour = TXAFormat.format2Digits(now.get(java.util.Calendar.HOUR_OF_DAY))
        val minute = TXAFormat.format2Digits(now.get(java.util.Calendar.MINUTE))
        return "$hour:$minute"
    }

    private fun getCurrentSeconds(): String {
        val now = java.util.Calendar.getInstance()
        return TXAFormat.format2Digits(now.get(java.util.Calendar.SECOND))
    }

    private fun getCurrentDateFormatted(format: Int): String {
        val locale = Locale.getDefault()
        val pattern = when (format) {
            0 -> if (locale.language == "vi") "EEEE, d MMMM" else "EEEE, MMMM d" // Full
            1 -> if (locale.language == "vi") "EEE, d MMMM" else "EEE, MMMM d"  // Short
            else -> if (locale.language == "vi") "d MMMM" else "MMMM d"        // No Day
        }
        var result = SimpleDateFormat(pattern, locale).format(Date())
        if (locale.language == "vi") {
            result = result.replace("Thứ Hai", "T2")
                           .replace("Thứ Ba", "T3")
                           .replace("Thứ Tư", "T4")
                           .replace("Thứ Năm", "T5")
                           .replace("Thứ Sáu", "T6")
                           .replace("Thứ Bảy", "T7")
                           .replace("Chủ Nhật", "CN")
                           .replace("Th 2", "T2")
                           .replace("Th 3", "T3")
                           .replace("Th 4", "T4")
                           .replace("Th 5", "T5")
                           .replace("Th 6", "T6")
                           .replace("Th 7", "T7")
                           .replace("CN", "CN")
        }
        return result
    }

    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    private fun formatSleepTimer(ms: Long): String {
        if (ms <= 0) return "00:00"
        
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}

/**
 * Helper object to manage Screen Saver Settings navigation
 */
object TXADreamSettingsHelper {
    
    private const val TAG = "TXADreamSettingsHelper"

    /**
     * Opens the system Screen Saver (Daydream) settings.
     * Falls back to Display settings if the direct intent is not available.
     */
    fun openScreenSaverSettings(context: Context) {
        try {
            // Try the direct Daydream settings intent
            val dreamIntent = Intent(Settings.ACTION_DREAM_SETTINGS)
            dreamIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            if (dreamIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(dreamIntent)
                TXALogger.appI(TAG, "Opened Dream Settings directly")
                return
            }
        } catch (e: Exception) {
            TXALogger.appW(TAG, "Direct Dream Settings not available: ${e.message}")
        }

        // Fallback to Display Settings
        try {
            val displayIntent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
            displayIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(displayIntent)
            TXALogger.appI(TAG, "Opened Display Settings as fallback")
        } catch (e: Exception) {
            TXALogger.appE(TAG, "Failed to open any settings", e)
        }
    }

    /**
     * Check if this app's DreamService is currently set as the active dream.
     */
    fun isDreamSettingsAvailable(context: Context): Boolean {
        // First check if screensaver is enabled
        val isEnabled = Secure.getInt(context.contentResolver, "screensaver_enabled", 0) == 1
        if (!isEnabled) return false
        
        // Check component
        val component = Secure.getString(context.contentResolver, "screensaver_components")
        val myService = ComponentName(context, MusicDreamService::class.java).flattenToString()
        
        return component == myService
    }
}
