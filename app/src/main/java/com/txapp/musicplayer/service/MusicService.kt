@file:OptIn(com.txapp.musicplayer.media.ExperimentalApi::class)
@file:Suppress("UnstableApi")
package com.txapp.musicplayer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.CommandButton
import com.txapp.musicplayer.media.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.txapp.musicplayer.MusicApplication
import com.txapp.musicplayer.R
import com.txapp.musicplayer.data.MusicRepository
import com.txapp.musicplayer.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.*
import kotlin.OptIn
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult.RESULT_ERROR_BAD_VALUE
import androidx.media3.session.LibraryResult.RESULT_ERROR_UNKNOWN
import com.txapp.musicplayer.auto.AutoMediaIDHelper
import com.txapp.musicplayer.auto.AutoMusicProvider
import com.txapp.musicplayer.util.txa
import com.txapp.musicplayer.util.TXALogger
import com.txapp.musicplayer.util.LyricsUtil
import com.txapp.musicplayer.service.FloatingLyricsService
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Extension function to convert suspend block to ListenableFuture
 */
private fun <T> CoroutineScope.future(block: suspend () -> T): ListenableFuture<T> {
    val deferred = async { block() }
    return object : ListenableFuture<T> {
        private val listeners = mutableListOf<Pair<Runnable, java.util.concurrent.Executor>>()
        
        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            deferred.cancel()
            return true
        }
        
        override fun isCancelled(): Boolean = deferred.isCancelled
        override fun isDone(): Boolean = deferred.isCompleted
        
        override fun get(): T = runBlocking { deferred.await() }
        override fun get(timeout: Long, unit: java.util.concurrent.TimeUnit): T = runBlocking {
            withTimeout(unit.toMillis(timeout)) { deferred.await() }
        }
        
        override fun addListener(listener: Runnable, executor: java.util.concurrent.Executor) {
            listeners.add(listener to executor)
            deferred.invokeOnCompletion { executor.execute(listener) }
        }
    }
}

class MusicService : MediaLibraryService() {

    private lateinit var player: Player // Changed from ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibraryService.MediaLibrarySession
    private lateinit var musicRepository: MusicRepository
    private lateinit var autoMusicProvider: AutoMusicProvider

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Manual Shuffle Implementation
    private var originalMediaItems: MutableList<MediaItem> = mutableListOf()
    private var isShuffleEnabled = false
    
    // Crossfade (Fade In/Out) logic
    private val fadeHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var fadeOutJob: Job? = null
    private val FADE_CHECK_INTERVAL = 500L // 500ms
    
    // Mute/Unmute logic
    private var previousVolume: Float = 1.0f
    
    // Stuck Player Detection
    private var lastPlaybackProgressTime: Long = 0
    private var lastPlaybackPosition: Long = 0
    private var lastStuckCheckTime: Long = 0
    private var isManualSkip: Boolean = false

    private val connectionReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", -1)
                    if (state == 1) { // Plugged
                        if (com.txapp.musicplayer.util.TXAPreferences.isHeadsetPlayEnabled) {
                            TXALogger.playbackI("MusicService", "Tai nghe đã kết nối, tự động phát")
                            player.play()
                        } else {
                           // If disabled, ensure we don't play or route? 
                           // User wants to FORCE speaker.
                           forceAudioToSpeaker(context, "Headset connected but disabled")
                        }
                    } else {
                        // Unplugged - reset speaker mode
                        resetSpeakerMode(context)
                    }
                }
                "android.bluetooth.device.action.ACL_CONNECTED" -> {
                    if (com.txapp.musicplayer.util.TXAPreferences.isBluetoothPlaybackEnabled) {
                        TXALogger.playbackI("MusicService", "Bluetooth đã kết nối, chuẩn bị tự động phát")
                        serviceScope.launch {
                            delay(1500) // Đợi thiết bị Bluetooth ổn định
                            player.play()
                        }
                    } else {
                        // Force speaker if BT playback disabled
                        serviceScope.launch {
                            delay(500) // Wait for BT to stabilize
                            forceAudioToSpeaker(context, "Bluetooth connected but disabled")
                        }
                    }
                }
                "android.bluetooth.device.action.ACL_DISCONNECTED" -> {
                     resetSpeakerMode(context)
                }
                "com.txapp.musicplayer.action.AUDIO_ROUTE_SETTING_CHANGED" -> {
                    val type = intent.getStringExtra("type")
                    val enabled = intent.getBooleanExtra("enabled", true)
                    TXALogger.playbackI("MusicService", "Audio route setting changed: $type = $enabled")
                    
                    if (!enabled) {
                        // User disabled the setting - force speaker immediately
                        when (type) {
                            "bluetooth" -> {
                                if (isBluetoothAudioConnected(context)) {
                                    forceAudioToSpeaker(context, "Bluetooth disabled by user")
                                }
                            }
                            "headset" -> {
                                if (isHeadsetConnected(context)) {
                                    forceAudioToSpeaker(context, "Headset disabled by user")
                                }
                            }
                        }
                    } else {
                        // User enabled the setting - reset to default routing
                        resetSpeakerMode(context)
                    }
                }
            }
        }
    }
    
    private fun isBluetoothAudioConnected(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val devices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any { 
            it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || 
            it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO 
        }
    }
    
    private fun isHeadsetConnected(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
            devices.any {
                it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isWiredHeadsetOn
        }
    }
    
    @Suppress("DEPRECATION")
    private fun forceAudioToSpeaker(context: Context, reason: String) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        
        try {
            // Stop Bluetooth SCO if active
            if (audioManager.isBluetoothScoOn) {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            }
            
            // Disable A2DP routing (deprecated but still works)
            audioManager.isBluetoothA2dpOn = false
            
            // Force speaker mode
            audioManager.mode = android.media.AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = true
            
            TXALogger.playbackI("MusicService", "Force Speaker: $reason")
            
            // Show toast to user
            serviceScope.launch(Dispatchers.Main) {
                com.txapp.musicplayer.util.TXAToast.info(
                    context, 
                    "txamusic_audio_route_speaker".txa()
                )
            }
        } catch (e: Exception) {
            TXALogger.playbackE("MusicService", "Error forcing speaker", e)
        }
    }
    
    @Suppress("DEPRECATION")
    private fun resetSpeakerMode(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            if (audioManager.isSpeakerphoneOn) {
                audioManager.isSpeakerphoneOn = false
                audioManager.mode = android.media.AudioManager.MODE_NORMAL
                TXALogger.playbackI("MusicService", "Reset speaker mode to default")
            }
        } catch (e: Exception) {
            TXALogger.playbackE("MusicService", "Error resetting speaker mode", e)
        }
    }
    
    @Suppress("DEPRECATION")
    private fun checkAudioRouting(context: Context) {
        // Legacy method - redirect to new methods
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        
        if (isBluetoothAudioConnected(context) && !com.txapp.musicplayer.util.TXAPreferences.isBluetoothPlaybackEnabled) {
            forceAudioToSpeaker(context, "BT check: disabled in settings")
            return
        }
        
        if (isHeadsetConnected(context) && !com.txapp.musicplayer.util.TXAPreferences.isHeadsetPlayEnabled) {
            forceAudioToSpeaker(context, "Headset check: disabled in settings")
            return
        }
        
        resetSpeakerMode(context)
    }


    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        // Initialize repository
        val database = (application as MusicApplication).database
        val contentResolver = contentResolver
        musicRepository = MusicRepository(database, contentResolver)
        
        // Initialize Android Auto provider
        autoMusicProvider = AutoMusicProvider(this, musicRepository)

        // Custom LoadControl to limit buffering
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                50000, // Min buffer
                50000, // Max buffer
                1500,  // Buffer for playback
                5000   // Buffer for rebuffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
            
        // Create CompositionPlayer (wrapping ExoPlayer)
        player = com.txapp.musicplayer.media.CompositionPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL) // Requested Wake Mode
            .setLoadControl(loadControl)
            .build()
        
        // Update audio session ID
        var sessionId = 0
        if (player is com.txapp.musicplayer.media.CompositionPlayer) {
             val internal = (player as com.txapp.musicplayer.media.CompositionPlayer).wrappedPlayer
             if (internal is androidx.media3.exoplayer.ExoPlayer) {
                 sessionId = internal.audioSessionId
             }
        }
        audioSessionId = sessionId
        
        // Restore previous state
        restorePlaybackState()
        
        // Sync widgets
        com.txapp.musicplayer.appwidget.TXAMusicWidget.updateWidgets(this)

        // Load per-song playback history
        serviceScope.launch {
            com.txapp.musicplayer.util.TXAPlaybackHistory.load(this@MusicService)
            
            // Auto-start Floating Lyrics if enabled
            // Auto-start Floating Lyrics if enabled
            // MOVED to MainActivity to comply with: "Show only when app opened"
            // if (com.txapp.musicplayer.util.TXAPreferences.showLyricsInPlayer.value) {
            //    com.txapp.musicplayer.service.FloatingLyricsService.startService(this@MusicService)
            // }
        }

        // SessionActivity for Samsung Now Bar / Dynamic Island
        val intent = Intent(this, com.txapp.musicplayer.ui.MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Create MediaLibrarySession
        mediaLibrarySession = MediaLibraryService.MediaLibrarySession.Builder(
            this,
            player,
            MediaLibrarySessionCallback()
        )
        .setSessionActivity(pendingIntent)
        .build()

        // Custom notification provider with Favorite and Shuffle buttons
        setMediaNotificationProvider(TXAMediaNotificationProvider(this))
        
        // Register Metadata Updated receiver
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).registerReceiver(
            metadataUpdateReceiver,
            android.content.IntentFilter("com.txapp.musicplayer.action.METADATA_UPDATED")
        )

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateMediaSessionLayout()
                savePlaybackState() // Save immediately on change
                
                // Update Floating Lyrics
                updateLyricsForFloatingPlayer()
                
                // Sync widget with new song info
                if (mediaItem != null) {
                    val metadata = mediaItem.mediaMetadata
                    val albumId = metadata.extras?.getLong("album_id") ?: -1L
                    val albumArtUri = if (albumId >= 0) "content://media/external/audio/albumart/$albumId" else ""
                    
                    val currentDuration = player.duration
                    com.txapp.musicplayer.appwidget.TXAMusicWidget.updateState(
                        context = this@MusicService,
                        title = (metadata.title ?: "Unknown").toString(),
                        artist = (metadata.artist ?: "Unknown").toString(),
                        albumArtUri = albumArtUri,
                        isPlaying = player.isPlaying,
                        duration = if (currentDuration > 0) currentDuration else null
                    )
                }
                
                // Prompt Playback Position for the new item
                if (mediaItem != null && com.txapp.musicplayer.util.TXAPreferences.isRememberPlaybackPositionEnabled) {
                    val path = getNormalizedPath(mediaItem.localConfiguration?.uri)
                    if (!path.isNullOrBlank()) {
                        val savedPos = com.txapp.musicplayer.util.TXAPlaybackHistory.getPosition(path)
                        TXALogger.playbackI("MusicService", "Transition to: $path (History position: $savedPos ms, Reason: $reason)")
                        
                        // We check savedPos. If it's substantial, we prompt.
                        if (savedPos > 3000) { 
                             serviceScope.launch {
                                 // Immediately pause to prevent audio pop
                                 player.pause()
                                 
                                 // Update Manager
                                 com.txapp.musicplayer.util.TXAPlaybackManager.requestResumePrompt(
                                     songId = mediaItem.mediaId,
                                     title = mediaItem.mediaMetadata.title?.toString() ?: "Unknown",
                                     position = savedPos,
                                     path = path
                                 )
                                 TXALogger.playbackI("MusicService", "Signaled Resume Prompt for: $path")
                             }
                        }
                    }
                }

                mediaItem?.mediaId?.toLongOrNull()?.let { songId ->
                    serviceScope.launch(Dispatchers.Default) {
                        musicRepository.updateHistory(songId)
                        
                        // Check artwork validity and fallback if needed
                        val artworkUri = mediaItem.mediaMetadata.artworkUri
                        if (artworkUri != null && artworkUri.scheme == "content") {
                            // Verify if content exists
                            var isValid = false
                            try {
                                contentResolver.openInputStream(artworkUri)?.use { isValid = true }
                            } catch (e: Exception) {
                                isValid = false
                            }
                            
                            if (!isValid) {
                                // Fallback
                                val fallbackUri = com.txapp.musicplayer.util.TXAImageUtils.getFallbackUri(this@MusicService, songId)
                                val updatedMetadata = mediaItem.mediaMetadata.buildUpon()
                                    .setArtworkUri(fallbackUri)
                                    .build()
                                val updatedItem = mediaItem.buildUpon()
                                    .setMediaMetadata(updatedMetadata)
                                    .build()
                                
                                withContext(Dispatchers.Main) {
                                    // Only update if current item hasn't changed
                                    if (player.currentMediaItem?.mediaId == songId.toString()) {
                                        player.replaceMediaItem(player.currentMediaItemIndex, updatedItem)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                isShuffleEnabled = shuffleModeEnabled
                updateMediaSessionLayout()
                savePlaybackState()
                
                // Broadcast for UI sync
                val shuffleBroadcast = Intent("com.txapp.musicplayer.action.SHUFFLE_MODE_CHANGED")
                shuffleBroadcast.putExtra("is_enabled", shuffleModeEnabled)
                sendBroadcast(shuffleBroadcast)
                
                // Sync widget
                com.txapp.musicplayer.appwidget.TXAMusicWidget.updateState(
                    context = this@MusicService,
                    isShuffleOn = shuffleModeEnabled
                )
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateMediaSessionLayout()
                FloatingLyricsService.updatePlaybackState(isPlaying)
                
                // Sync widget
                com.txapp.musicplayer.appwidget.TXAMusicWidget.updateState(
                    context = this@MusicService,
                    isPlaying = isPlaying
                )
                
                if (isPlaying) {
                    // Critical: If resuming from pause, ensure volume is restored
                    if (player.volume < 1.0f) {
                        startFadeIn(isManualPlay = true)
                    }
                } else {
                    savePlaybackState()
                    saveCurrentSongProgress()
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady && player.playbackState == Player.STATE_READY) {
                    // Ensure volume is restored when user hits play
                    if (player.volume < 1.0f) {
                        startFadeIn(isManualPlay = true)
                    }
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                updateMediaSessionLayout()
                savePlaybackState()
                
                // Broadcast for UI sync
                val repeatBroadcast = Intent("com.txapp.musicplayer.action.REPEAT_MODE_CHANGED")
                repeatBroadcast.putExtra("mode", repeatMode)
                sendBroadcast(repeatBroadcast)
                
                // Sync widget
                com.txapp.musicplayer.appwidget.TXAMusicWidget.updateState(
                    context = this@MusicService,
                    repeatMode = repeatMode
                )
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId > 0) {
                    com.txapp.musicplayer.service.MusicService.audioSessionId = audioSessionId
                    com.txapp.musicplayer.util.TXAEqualizerManager.init(audioSessionId)
                    TXALogger.playbackI("MusicService", "Audio Session ID updated for Eq: $audioSessionId")
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    // Sync widget when ready - ensures duration is updated correctly
                    com.txapp.musicplayer.appwidget.TXAMusicWidget.updateState(
                        context = this@MusicService,
                        position = player.currentPosition,
                        duration = player.duration,
                        isPlaying = player.isPlaying
                    )
                    
                    if (player.playWhenReady) {
                        // This could be manual play or auto transition
                        // If position is near start, it's likely a start of song
                        if (player.currentPosition < 1000) {
                             startFadeIn(isManualPlay = isManualSkip) 
                             isManualSkip = false // Reset after applying
                        } else {
                             // Resumed from pause
                             startFadeIn(isManualPlay = true)
                        }
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    isManualSkip = false
                    startFadeIn(isManualPlay = false)
                } else if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    // Could be manual skip or manual seek
                    // If it was triggered by ACTION_NEXT/PREV, isManualSkip is true
                    if (isManualSkip && player.playbackState == Player.STATE_READY) {
                        startFadeIn(isManualPlay = true)
                        isManualSkip = false
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val errorCode = error.errorCode
                TXALogger.playbackE("MusicService", "Player Error ($errorCode): ${error.message}")
                
                // Check if it's a "File Not Found" or "Source Error"
                val isSourceError = error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                                   error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                                   error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED ||
                                   error.message?.contains("ENOENT", ignoreCase = true) == true
                
                if (isSourceError) {
                    val currentId = player.currentMediaItem?.mediaId?.toLongOrNull()
                    if (currentId != null) {
                        serviceScope.launch {
                            // 1. Show Toast
                            val msg = "txamusic_error_file_not_found".txa(player.currentMediaItem?.mediaMetadata?.title ?: "Unknown")
                            withContext(Dispatchers.Main) {
                                com.txapp.musicplayer.util.TXAToast.show(this@MusicService, msg)
                            }
                            
                            // 2. Remove from database
                            musicRepository.deleteSong(currentId)
                            
                            // 3. Trigger Scan on Activity if possible (via Broadcast)
                            val scanIntent = Intent("com.txapp.musicplayer.action.TRIGGER_RESCAN")
                            sendBroadcast(scanIntent)
                            
                            // 4. Skip to next song automatically
                            withContext(Dispatchers.Main) {
                                if (player.mediaItemCount > 1) {
                                    player.seekToNextMediaItem()
                                    player.prepare()
                                    player.play()
                                } else {
                                    player.stop()
                                }
                            }
                        }
                    }
                }
            }
        })
        
        // Init Equalizer if ID is already available
        if (com.txapp.musicplayer.service.MusicService.audioSessionId > 0) {
            com.txapp.musicplayer.util.TXAEqualizerManager.init(com.txapp.musicplayer.service.MusicService.audioSessionId)
        }

        // Periodic save for crash protection and widget/floating lyrics sync
        serviceScope.launch {
            var lastSaveTime = System.currentTimeMillis()
            var lastWidgetUpdateTime = 0L
            while (isActive) {
                if (player.isPlaying) {
                     FloatingLyricsService.updatePosition(player.currentPosition)
                     
                     // Update widget progress every 1 second (only if widget exists)
                     val now = System.currentTimeMillis()
                     if (now - lastWidgetUpdateTime > 1000) {
                         lastWidgetUpdateTime = now
                         if (com.txapp.musicplayer.appwidget.TXAMusicWidget.hasInstances(this@MusicService)) {
                             val currentDuration = player.duration
                             com.txapp.musicplayer.appwidget.TXAMusicWidget.updateState(
                                 context = this@MusicService,
                                 position = player.currentPosition,
                                 duration = if (currentDuration > 0) currentDuration else null
                             )
                         }
                     }
                     delay(50)
                } else {
                     // Even when paused, update widget occasionally (every 2 seconds)
                     val now = System.currentTimeMillis()
                     if (now - lastWidgetUpdateTime > 2000) {
                         lastWidgetUpdateTime = now
                         if (com.txapp.musicplayer.appwidget.TXAMusicWidget.hasInstances(this@MusicService)) {
                             val currentDuration = player.duration
                             com.txapp.musicplayer.appwidget.TXAMusicWidget.updateState(
                                 context = this@MusicService,
                                 position = player.currentPosition,
                                 duration = if (currentDuration > 0) currentDuration else null
                             )
                         }
                     }
                     delay(500)
                }

                if (System.currentTimeMillis() - lastSaveTime > 5000) {
                     saveCurrentSongProgress()
                     lastSaveTime = System.currentTimeMillis()
                }
            }
        }

        // Observe playback speed changes
        serviceScope.launch {
            com.txapp.musicplayer.util.TXAPreferences.playbackSpeed.collect { speed ->
                player.setPlaybackParameters(androidx.media3.common.PlaybackParameters(speed))
            }
        }

        // Observe button visibility changes to update layout
        serviceScope.launch {
            kotlinx.coroutines.flow.combine(
                com.txapp.musicplayer.util.TXAPreferences.showShuffleBtn,
                com.txapp.musicplayer.util.TXAPreferences.showFavoriteBtn
            ) { _, _ -> }.collect {
                updateMediaSessionLayout()
            }
        }
        
        // Observe Audio Focus Preference
        serviceScope.launch {
            com.txapp.musicplayer.util.TXAPreferences.audioFocus.collect { enabled ->
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    enabled
                )
            }
        }

        // Register connection receiver
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction("android.bluetooth.device.action.ACL_CONNECTED")
            addAction("android.bluetooth.device.action.ACL_DISCONNECTED")
            addAction("com.txapp.musicplayer.action.AUDIO_ROUTE_SETTING_CHANGED")
        }
        registerReceiver(connectionReceiver, filter)

        // Register screen lock receiver for Lockscreen Player
        registerLockScreenReceiver()
        
        // Register playback command receiver for LockScreen controls
        registerPlaybackCommandReceiver()

        // Start fade check loop
        startFadeCheckLoop()
    }
    
    private val lockScreenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    if (player.isPlaying && com.txapp.musicplayer.util.TXAPreferences.isLockScreenPlayerEnabled) {
                        showLockScreenPlayer()
                    }
                }
            }
        }
    }
    
    private val playbackCommandReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.txapp.musicplayer.action.TOGGLE_PLAYBACK" -> {
                    if (player.isPlaying) {
                        startFadeOut(isManualPause = true) { player.pause() }
                    } else {
                        if (player.volume > 0.9f && com.txapp.musicplayer.util.TXAPreferences.currentAudioFadeDuration > 0) {
                            player.volume = 0f
                        }
                        player.play()
                    }
                    sendPlaybackBroadcast()
                }
                "com.txapp.musicplayer.action.SKIP_PREVIOUS" -> {
                    startFadeOut(isManualPause = true) {
                        isManualSkip = true
                        player.seekToPreviousMediaItem()
                    }
                }
                "com.txapp.musicplayer.action.SKIP_NEXT" -> {
                    startFadeOut(isManualPause = true) {
                        isManualSkip = true
                        player.seekToNextMediaItem()
                    }
                }
                "com.txapp.musicplayer.action.TOGGLE_SHUFFLE" -> {
                    player.shuffleModeEnabled = !player.shuffleModeEnabled
                }
                "com.txapp.musicplayer.action.TOGGLE_REPEAT" -> {
                    val newMode = when (player.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                    player.repeatMode = newMode
                }
                "com.txapp.musicplayer.action.SEEK_TO" -> {
                    val position = intent.getLongExtra("position", 0)
                    player.seekTo(position)
                }
            }
        }
    }
    
    private fun registerLockScreenReceiver() {
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(lockScreenReceiver, filter)
    }
    
    private fun registerPlaybackCommandReceiver() {
        val filter = android.content.IntentFilter().apply {
            addAction("com.txapp.musicplayer.action.TOGGLE_PLAYBACK")
            addAction("com.txapp.musicplayer.action.SKIP_PREVIOUS")
            addAction("com.txapp.musicplayer.action.SKIP_NEXT")
            addAction("com.txapp.musicplayer.action.TOGGLE_SHUFFLE")
            addAction("com.txapp.musicplayer.action.TOGGLE_REPEAT")
            addAction("com.txapp.musicplayer.action.SEEK_TO")
        }
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            playbackCommandReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }
    
    private fun showLockScreenPlayer() {
        try {
            val intent = Intent(this, com.txapp.musicplayer.ui.LockScreenActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        } catch (e: Exception) {
            TXALogger.e("MusicService", "Error starting LockScreenActivity", e)
        }
    }
    
    private fun sendPlaybackBroadcast() {
        val intent = Intent("com.txapp.musicplayer.action.PLAYBACK_STATE_CHANGED")
        intent.putExtra("is_playing", player.isPlaying)
        sendBroadcast(intent)
    }

    private fun startFadeCheckLoop() {
        serviceScope.launch {
            while (isActive) {
                delay(FADE_CHECK_INTERVAL)
                checkFadeOutNeed()
                checkForStuckPlayer()
            }
        }
    }
    
    private fun checkForStuckPlayer() {
        if (!player.isPlaying) {
            lastPlaybackProgressTime = System.currentTimeMillis()
            return
        }
        
        val currentPos = player.currentPosition
        val now = System.currentTimeMillis()
        
        // Update progress time if position moved
        if (kotlin.math.abs(currentPos - lastPlaybackPosition) > 100) {
            lastPlaybackProgressTime = now
            lastPlaybackPosition = currentPos
        }
        
        // 1. STATE_BUFFERING > 10 mins
        if (player.playbackState == Player.STATE_BUFFERING) {
             // If buffering for too long without shift? 
             // Logic: if currentPos hasn't changed in 10 mins BUT we are buffering.
             // Usually buffering means position stuck.
             if (now - lastPlaybackProgressTime > 600000) { // 10 mins
                 handleStuckPlayer("Stuck in BUFFERING for 10 mins")
             }
        }
        
        // 2. STATE_READY stuck > 10s
        if (player.playbackState == Player.STATE_READY && player.playWhenReady) {
            if (now - lastPlaybackProgressTime > 10000) { // 10s
                handleStuckPlayer("Stuck in READY for 10s without progress")
            }
        }
        
        // 3. READY > 1 min past duration (sanity check)
        val duration = player.duration
        if (duration > 0 && currentPos > duration + 60000) {
             handleStuckPlayer("Position exceeded duration by 1 min")
        }
    }

    private fun handleStuckPlayer(reason: String) {
        // Prevent spamming
        if (System.currentTimeMillis() - lastStuckCheckTime < 60000) return
        lastStuckCheckTime = System.currentTimeMillis()
        
        TXALogger.playbackE("MusicService", "StuckPlayerException: $reason. Restarting playback logic.")
        try {
             // Simple recovery: pause and play, or seek to current
             val pos = player.currentPosition
             player.stop()
             player.prepare()
             player.seekTo(pos)
             player.play()
        } catch (e: Exception) {
            TXALogger.playbackE("MusicService", "Error recovering stuck player", e)
        }
    }

    private fun checkFadeOutNeed() {
        if (!player.isPlaying) return
        
        val duration = player.duration
        val position = player.currentPosition
        if (duration <= 0) return

        val fadeDur = com.txapp.musicplayer.util.TXAPreferences.currentCrossfadeDuration * 1000L
        if (fadeDur <= 0) return

        val remaining = duration - position
        if (remaining <= fadeDur && remaining > 0) {
            // Already fading? 
            // We need a way to check if fader is already fading out.
            // TXAAudioFader current animator can be checked, but it's simpler to use a flag or just call it (it cancels previous).
            // BUT we only want to start fade out ONCE per song end.
            if (player.volume > 0.9f) {
                TXALogger.playbackI("MusicService", "Starting auto fade out ($fadeDur ms)")
                com.txapp.musicplayer.util.TXAAudioFader.startFade(player, false, fadeDur)
            }
        }
    }

    private fun startFadeIn(isManualPlay: Boolean = false) {
        val fadeDur = if (isManualPlay) {
            com.txapp.musicplayer.util.TXAPreferences.currentAudioFadeDuration.toLong()
        } else {
            com.txapp.musicplayer.util.TXAPreferences.currentCrossfadeDuration * 1000L
        }

        if (fadeDur > 0) {
            com.txapp.musicplayer.util.TXAAudioFader.startFade(player, true, fadeDur)
        } else {
            // Fading disabled - ensure volume is at max but don't force repeatedly if not needed
            if (player.volume < 1.0f) {
                player.volume = 1.0f
            }
        }
    }

    private fun startFadeOut(isManualPause: Boolean = false, onEnd: () -> Unit) {
        val fadeDur = if (isManualPause) {
            com.txapp.musicplayer.util.TXAPreferences.currentAudioFadeDuration.toLong()
        } else {
            com.txapp.musicplayer.util.TXAPreferences.currentCrossfadeDuration * 1000L
        }

        if (fadeDur > 0) {
            com.txapp.musicplayer.util.TXAAudioFader.startFade(player, false, fadeDur, onEnd)
        } else {
            // Fading disabled - skip volume reduction and execute action immediately
            onEnd()
        }
    }

    private fun savePlaybackState() {
        val currentItem = player.currentMediaItem ?: return
        val currentId = currentItem.mediaId.toLongOrNull() ?: -1L
        val currentPos = player.currentPosition
        val queueIds = mutableListOf<Long>()
        for (i in 0 until player.mediaItemCount) {
            player.getMediaItemAt(i).mediaId.toLongOrNull()?.let { queueIds.add(it) }
        }
        
        com.txapp.musicplayer.util.TXAPlaybackPersistence.savePlaybackState(
            this, currentId, currentPos, queueIds, player.shuffleModeEnabled, player.repeatMode
        )
    }

    private fun saveCurrentSongProgress(blocking: Boolean = false) {
        if (!com.txapp.musicplayer.util.TXAPreferences.isRememberPlaybackPositionEnabled) return
        
        val currentItem = player.currentMediaItem ?: return
        val path = getNormalizedPath(currentItem.localConfiguration?.uri) ?: return
        val pos = player.currentPosition
        val duration = player.duration
        
        TXALogger.playbackI("MusicService", "Saving progress for $path: $pos ms")

        // Only save if:
        // 1. Position > 5s (don't save if just started)
        // 2. Position < Duration - 5s (don't save if finished or almost finished)
        if (pos > 5000 && (duration <= 0 || pos < duration - 5000)) {
            com.txapp.musicplayer.util.TXAPlaybackHistory.saveProgress(this, path, pos)
            if (blocking) {
                runBlocking { com.txapp.musicplayer.util.TXAPlaybackHistory.persist(this@MusicService) }
            } else {
                serviceScope.launch { com.txapp.musicplayer.util.TXAPlaybackHistory.persist(this@MusicService) }
            }
        } else if (duration > 0 && pos >= duration - 5000) {
            // Remove if finished
             com.txapp.musicplayer.util.TXAPlaybackHistory.clearPosition(path)
             if (blocking) {
                 runBlocking { com.txapp.musicplayer.util.TXAPlaybackHistory.persist(this@MusicService) }
             } else {
                 serviceScope.launch { com.txapp.musicplayer.util.TXAPlaybackHistory.persist(this@MusicService) }
             }
        }
    }

    private fun getNormalizedPath(uri: Uri?): String? {
        if (uri == null) return null
        return if (uri.scheme == "file") {
            uri.path // Decoded path for files
        } else {
            uri.toString() // Full URI for content:// or others
        }
    }

    private fun restorePlaybackState() {
        serviceScope.launch {
            val lastId = com.txapp.musicplayer.util.TXAPlaybackPersistence.getLastSongId(this@MusicService)
            if (lastId == -1L) return@launch
            
            val queueIds = com.txapp.musicplayer.util.TXAPlaybackPersistence.getQueueIds(this@MusicService)
            val pos = com.txapp.musicplayer.util.TXAPlaybackPersistence.getLastPosition(this@MusicService)
            val shuffle = com.txapp.musicplayer.util.TXAPlaybackPersistence.getShuffleMode(this@MusicService)
            val repeat = com.txapp.musicplayer.util.TXAPlaybackPersistence.getRepeatMode(this@MusicService)
            
            val songs = musicRepository.getSongsByIds(queueIds.toLongArray())
            val songMap = songs.associateBy { it.id }
            val orderedSongs = queueIds.mapNotNull { songMap[it] }
            
            if (orderedSongs.isNotEmpty()) {
                val lastSongIndex = orderedSongs.indexOfFirst { it.id == lastId }
                withContext(Dispatchers.Main) {
                    val mediaItems = orderedSongs.map { createMediaItem(it) }
                    player.setMediaItems(mediaItems, if (lastSongIndex >= 0) lastSongIndex else 0, pos)
                    player.shuffleModeEnabled = shuffle
                    player.repeatMode = repeat
                    player.prepare()
                    TXALogger.playbackI("MusicService", "Khôi phục trạng thái: Bài ID=$lastId, Vị trí=${pos}ms")
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibraryService.MediaLibrarySession {
        return mediaLibrarySession
    }

    private fun updateLyricsForFloatingPlayer() {
        val currentItem = player.currentMediaItem ?: return
        val metadata = currentItem.mediaMetadata
        val title = (metadata.title ?: "Unknown").toString()
        val artist = (metadata.artist ?: "Unknown").toString()
        val mediaUri = currentItem.localConfiguration?.uri?.toString() ?: ""
        val path = try { android.net.Uri.parse(mediaUri).path ?: "" } catch (e: Exception) { "" }

        val albumId = metadata.extras?.getLong("album_id") ?: -1L
        val albumArtUri = if (albumId >= 0) "content://media/external/audio/albumart/$albumId" else ""

        TXALogger.floatingI("MusicService", "Updating Song Info: Title='$title', URI='$mediaUri'")
        
        FloatingLyricsService.updateSongInfo(title, albumArtUri)
        FloatingLyricsService.updatePlaybackState(player.isPlaying)
        FloatingLyricsService.updateDuration(player.duration)
        
        serviceScope.launch(Dispatchers.IO) {
            val raw = LyricsUtil.getRawLyrics(path, title, artist)
            val lyricsList = if (raw != null) LyricsUtil.parseLrc(raw) else emptyList()
            withContext(Dispatchers.Main) {
                FloatingLyricsService.updateLyricsList(lyricsList)
                TXALogger.floatingI("MusicService", "Sent lyrics list to FloatingLyricsService (${lyricsList.size} lines)")
            }
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(connectionReceiver)
        } catch (e: Exception) {
            TXALogger.appE("MusicService", "Error unregistering receiver", e)
        }
        try {
            unregisterReceiver(lockScreenReceiver)
        } catch (e: Exception) {}
        try {
            unregisterReceiver(playbackCommandReceiver)
        } catch (e: Exception) {}
        try {
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).unregisterReceiver(metadataUpdateReceiver)
        } catch (e: Exception) {}
        savePlaybackState()
        saveCurrentSongProgress(blocking = true)
        mediaLibrarySession.release()
        player.release()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        savePlaybackState()
        saveCurrentSongProgress(blocking = true)
        super.onTaskRemoved(rootIntent)
        player.stop()
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_SONG -> {
                val songId = intent.getLongExtra(EXTRA_SONG_ID, -1)
                if (songId > 0) {
                    serviceScope.launch {
                        val song = musicRepository.getSongById(songId)
                        if (song != null) {
                            isManualSkip = true
                            if (com.txapp.musicplayer.util.TXAPreferences.currentAudioFadeDuration > 0) {
                                player.volume = 0f
                            }
                            playSong(song)
                        }
                    }
                }
            }

            ACTION_TOGGLE_PLAYBACK -> {
                if (player.isPlaying) {
                    startFadeOut(isManualPause = true) { player.pause() }
                } else {
                    if (player.volume > 0.9f && com.txapp.musicplayer.util.TXAPreferences.currentAudioFadeDuration > 0) {
                        player.volume = 0f
                    }
                    player.play() 
                    // onPlaybackStateChanged will handle startFadeIn(true)
                }
            }

            ACTION_NEXT -> {
                startFadeOut(isManualPause = true) { 
                    isManualSkip = true
                    player.seekToNextMediaItem()
                }
            }

            ACTION_PREVIOUS -> {
                 startFadeOut(isManualPause = true) { 
                    isManualSkip = true
                    player.seekToPreviousMediaItem()
                }
            }

            ACTION_PLAY_EXTERNAL_URI -> {
                val uriString = intent.getStringExtra(EXTRA_URI)
                if (!uriString.isNullOrEmpty()) {
                    val uri = Uri.parse(uriString)
                    playExternalUri(uri)
                }
            }

            ACTION_ENQUEUE_EXTERNAL_URI -> {
                val uriString = intent.getStringExtra(EXTRA_URI)
                if (!uriString.isNullOrEmpty()) {
                    val uri = Uri.parse(uriString)
                    enqueueExternalUri(uri)
                }
            }
            
            ACTION_TOGGLE_FAVORITE -> {
                val songId = intent.getLongExtra(EXTRA_SONG_ID, -1)
                if (songId > 0) {
                    serviceScope.launch {
                        val song = musicRepository.getSongById(songId)
                        val newStatus = if (song != null) !song.isFavorite else false
                        
                        if (song != null) {
                            musicRepository.setFavorite(songId, newStatus)
                        }
                        
                        // Update current media item if it's the one toggled
                        val currentItem = player.currentMediaItem
                        if (currentItem?.mediaId == songId.toString()) {
                            val currentExtras = currentItem.mediaMetadata.extras ?: Bundle()
                            currentExtras.putBoolean("is_favorite", newStatus)
                            val newMetadata = currentItem.mediaMetadata.buildUpon()
                                .setExtras(currentExtras)
                                .build()
                            val newItem = currentItem.buildUpon().setMediaMetadata(newMetadata).build()
                            
                            withContext(Dispatchers.Main) {
                                if (player.currentMediaItem?.mediaId == songId.toString()) {
                                    player.replaceMediaItem(player.currentMediaItemIndex, newItem)
                                }
                            }
                        }
                        
                        // Notify widgets/others like backup
                        val broadcastIntent = Intent("com.txapp.musicplayer.action.FAVORITE_STATE_CHANGED")
                        broadcastIntent.putExtra("song_id", songId)
                        broadcastIntent.putExtra("is_favorite", newStatus)
                        sendBroadcast(broadcastIntent)
                        
                        updateMediaSessionLayout(overrideFavorite = newStatus)
                    }
                }
            }

            ACTION_PLAY_QUEUE -> {
                val songIds = intent.getLongArrayExtra(EXTRA_SONG_IDS)
                if (songIds != null && songIds.isNotEmpty()) {
                    serviceScope.launch {
                        val songs = mutableListOf<com.txapp.musicplayer.model.Song>()
                        for (id in songIds) {
                            musicRepository.getSongById(id)?.let { songs.add(it) }
                        }
                        if (songs.isNotEmpty()) {
                            isManualSkip = true
                            if (com.txapp.musicplayer.util.TXAPreferences.currentAudioFadeDuration > 0) {
                                player.volume = 0f
                            }
                            playQueue(songs)
                        }
                    }
                }
            }
            
            ACTION_TOGGLE_SHUFFLE -> {
                toggleShuffle()
                // Update widget immediately
                com.txapp.musicplayer.appwidget.TXAMusicWidget.updateState(
                    this,
                    isShuffleOn = player.shuffleModeEnabled
                )
            }
            
            ACTION_TOGGLE_REPEAT -> {
                 // Toggle repeat logic
                 val newMode = when (player.repeatMode) {
                     Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                     Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                     else -> Player.REPEAT_MODE_OFF
                 }
                 player.repeatMode = newMode
                 
                 // Show feedback
                 val message = when(newMode) {
                     Player.REPEAT_MODE_ALL -> "txamusic_repeat_all".txa()
                     Player.REPEAT_MODE_ONE -> "txamusic_repeat_one".txa()
                     else -> "txamusic_repeat_off".txa()
                 }
                 com.txapp.musicplayer.util.TXAToast.show(this, message)
                 
                 // Broadcast update
                 val repeatBroadcast = Intent("com.txapp.musicplayer.action.REPEAT_MODE_CHANGED")
                 repeatBroadcast.putExtra("mode", newMode)
                 sendBroadcast(repeatBroadcast)
                 
                 // Update widget immediately
                 com.txapp.musicplayer.appwidget.TXAMusicWidget.updateState(
                    this,
                    repeatMode = newMode
                 )
                 
                 updateMediaSessionLayout()
            }
            
            ACTION_PLAY_SONG_IN_CONTEXT -> {
                val songId = intent.getLongExtra(EXTRA_SONG_ID, -1)
                val contextIds = intent.getLongArrayExtra(EXTRA_CONTEXT_SONG_IDS) ?: longArrayOf()
                if (songId > 0 && contextIds.isNotEmpty()) {
                    serviceScope.launch {
                        val fetchedSongs = musicRepository.getSongsByIds(contextIds)
                        // Preserve the order of songs as they appear in contextIds
                        val songMap = fetchedSongs.associateBy { it.id }
                        val songs = contextIds.toList().mapNotNull { songMap[it] } 
                        
                        if (songs.isNotEmpty()) {
                            // Find index of target song in the ordered list
                            val targetIndex = songs.indexOfFirst { it.id == songId }
                            if (targetIndex >= 0) {
                                isManualSkip = true
                                if (com.txapp.musicplayer.util.TXAPreferences.currentAudioFadeDuration > 0) {
                                    player.volume = 0f
                                }
                                playSongInContext(songs, targetIndex)
                            } else {
                                // Fallback if not found in context (shouldn't happen)
                                val fallbackSong = musicRepository.getSongById(songId)
                                if (fallbackSong != null) {
                                    isManualSkip = true
                                    if (com.txapp.musicplayer.util.TXAPreferences.currentAudioFadeDuration > 0) {
                                        player.volume = 0f
                                    }
                                    playSong(fallbackSong)
                                }
                            }
                        }
                    }
                }
            }
            
            ACTION_ENQUEUE_SONG -> {
                val songId = intent.getLongExtra(EXTRA_SONG_ID, -1)
                if (songId > 0) {
                    serviceScope.launch {
                        val song = musicRepository.getSongById(songId)
                        if (song != null) {
                            enqueueSong(song)
                        }
                    }
                }
            }
            
            ACTION_STOP -> {
                // Sleep Timer or manual stop request
                TXALogger.playbackI("MusicService", "Nhận ACTION_STOP - Dừng nhạc (Sleep Timer)")
                startFadeOut(isManualPause = true) {
                    player.stop()
                    stopSelf()
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    fun setVirtualDeviceId(deviceId: Int) {
        // Attempt to set device ID if supported by Player or Routing
        // Since ExoPlayer doesn't have a direct 'setVirtualDeviceId', we might need to use platform APIs
        // or this is a placeholder for a specific implementation requirement.
        // For now, we log it.
        TXALogger.playbackI("MusicService", "Setting Virtual Device ID: $deviceId")
        // If there was a specific API like player.setDeviceInfo, we would use it here.
    }

    fun mute() {
        if (player.volume > 0) {
            previousVolume = player.volume
            player.volume = 0f
        }
    }

    fun unmute() {
        player.volume = if (previousVolume > 0) previousVolume else 1.0f
    }

    fun playSong(song: Song) {
        val mediaItem = createMediaItem(song)
        player.setMediaItem(mediaItem)
        player.seekTo(0) // Ensure it starts from beginning
        player.prepare()
        player.play()
    }

    fun playQueue(songs: List<Song>) {
        if (songs.isEmpty()) {
            player.stop()
            player.clearMediaItems()
            return
        }
        val mediaItems = songs.map { createMediaItem(it) }
        originalMediaItems = mediaItems.toMutableList()
        player.setMediaItems(mediaItems)
        player.shuffleModeEnabled = isShuffleEnabled
        player.prepare()
        player.play()
    }
    
    /**
     * Play a song within a queue context (e.g., play song X from library, queue = all library songs)
     */
    fun playSongInContext(songs: List<Song>, startIndex: Int) {
        val mediaItems = songs.map { createMediaItem(it) }
        originalMediaItems = mediaItems.toMutableList()
        player.setMediaItems(mediaItems, startIndex, 0)
        player.shuffleModeEnabled = isShuffleEnabled
        player.prepare()
        player.play()
    }
    
    /**
     * Add a song to the end of the current queue
     */
    fun enqueueSong(song: Song) {
        val mediaItem = createMediaItem(song)
        player.addMediaItem(mediaItem)
        originalMediaItems.add(mediaItem)
        if (!player.isPlaying && player.mediaItemCount == 1) {
            player.prepare()
            player.play()
        }
    }

    private fun createMediaItem(song: Song): MediaItem {
        val artworkUri = Uri.parse("content://media/external/audio/albumart/" + song.albumId)
        val defaultMetadataBuilder = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri(artworkUri) // Set URI first, player will try to load it
            .setExtras(Bundle().apply {
                 putLong("album_id", song.albumId)
                 putBoolean("is_favorite", song.isFavorite)
                 putLong("duration", song.duration)
            })

        // Optimization: Check if artwork exists? 
        // We can't do IO here easily as it might block. 
        // BUT we can use a lazy check or check at playback start.
        // For now, let's just use the URI. 
        // If we want fallback, we can use a custom logic in onMediaItemTransition
        
        val uri = if (song.data.startsWith("content://")) {
            Uri.parse(song.data)
        } else {
            Uri.fromFile(java.io.File(song.data))
        }

        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(song.id.toString())
            .setMediaMetadata(defaultMetadataBuilder.build())
            .build()
    }

    private fun playExternalUri(uri: Uri) {
        val fileName = getFileNameFromUri(uri)
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(fileName)
                    .build()
            )
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    private fun enqueueExternalUri(uri: Uri) {
        val fileName = getFileNameFromUri(uri)
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(fileName)
                    .build()
            )
            .build()
        player.addMediaItem(mediaItem)
        if (!player.isPlaying) {
            player.prepare()
            player.play()
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            result = cursor.getString(index)
                        }
                    }
                }
            } catch (e: Exception) {
                TXALogger.appE("MusicService", "Error resolving filename for $uri", e)
            }
        }
        if (result == null) {
            result = uri.lastPathSegment
        }
        // Fallback or cleanup
        return result ?: uri.toString()
    }
    
    private var lastUpdateLayoutTime = 0L

    private fun updateMediaSessionLayout(overrideFavorite: Boolean? = null) {
        // Throttling removed to ensure state sync
        // val now = System.currentTimeMillis()
        // if (now - lastUpdateLayoutTime < 300) return
        // lastUpdateLayoutTime = now

        // Ensure UI updates happen on main thread to avoid race conditions
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.post {
            if (!this::mediaLibrarySession.isInitialized || !this::player.isInitialized) return@post
            
            val buttons = mutableListOf<CommandButton>()
            val isShuffleOn = player.shuffleModeEnabled
            val currentItem = player.currentMediaItem
            
            val isFavorite = overrideFavorite ?: (currentItem?.mediaMetadata?.extras?.getBoolean("is_favorite", false) ?: false)
            
            // 1. Shuffle
            if (com.txapp.musicplayer.util.TXAPreferences.isShowShuffleBtn) {
                val shuffleIcon = if (isShuffleOn) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle
                buttons.add(
                    CommandButton.Builder()
                        .setDisplayName("Shuffle")
                        .setIconResId(shuffleIcon)
                        .setSessionCommand(TXAMediaNotificationProvider.COMMAND_TOGGLE_SHUFFLE)
                        .build()
                )
            }
            
            // 2. Previous
            buttons.add(
                 CommandButton.Builder()
                    .setDisplayName("Previous")
                    .setIconResId(R.drawable.ic_skip_previous)
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            )
            
            // 3. Play/Pause
            val isPlaying = player.isPlaying
            buttons.add(
                CommandButton.Builder()
                    .setDisplayName(if (isPlaying) "Pause" else "Play")
                    .setIconResId(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
                    .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                    .build()
            )
            
            // 4. Next
            buttons.add(
                CommandButton.Builder()
                    .setDisplayName("Next")
                    .setIconResId(R.drawable.ic_skip_next)
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .build()
            )
            
            // 5. Favorite
            if (com.txapp.musicplayer.util.TXAPreferences.isShowFavoriteBtn) {
                val favoriteIcon = if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
                buttons.add(
                    CommandButton.Builder()
                        .setDisplayName("Favorite")
                        .setIconResId(favoriteIcon)
                        .setSessionCommand(TXAMediaNotificationProvider.COMMAND_TOGGLE_FAVORITE)
                        .build()
                )
            }
            
            mediaLibrarySession.setCustomLayout(buttons)
        }
    }

    private fun toggleShuffle() {
        isShuffleEnabled = !isShuffleEnabled
        player.shuffleModeEnabled = isShuffleEnabled
        
        com.txapp.musicplayer.util.TXAToast.show(this, 
            if (isShuffleEnabled) "txamusic_shuffle_on".txa() else "txamusic_shuffle_off".txa())

        val shuffleBroadcast = Intent("com.txapp.musicplayer.action.SHUFFLE_MODE_CHANGED")
        shuffleBroadcast.putExtra("is_enabled", isShuffleEnabled)
        sendBroadcast(shuffleBroadcast)
        updateMediaSessionLayout()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "txamusic_noti_channel_name".txa(),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "txamusic_noti_channel_desc".txa()
            setShowBadge(false)
            setBypassDnd(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "txmusic_playback"

        const val ACTION_PLAY_SONG = "com.txapp.musicplayer.action.PLAY_SONG"
        const val EXTRA_SONG_ID = "extra_song_id"

        const val ACTION_TOGGLE_PLAYBACK = "com.txapp.musicplayer.action.TOGGLE_PLAYBACK"
        const val ACTION_NEXT = "com.txapp.musicplayer.action.NEXT"
        const val ACTION_PREVIOUS = "com.txapp.musicplayer.action.PREVIOUS"
        const val ACTION_PLAY_EXTERNAL_URI = "com.txapp.musicplayer.action.PLAY_EXTERNAL_URI"
        const val ACTION_ENQUEUE_EXTERNAL_URI = "com.txapp.musicplayer.action.ENQUEUE_EXTERNAL_URI"
        const val ACTION_TOGGLE_FAVORITE = "com.txapp.musicplayer.action.TOGGLE_FAVORITE"
        const val ACTION_PLAY_QUEUE = "com.txapp.musicplayer.action.PLAY_QUEUE"
        const val EXTRA_SONG_IDS = "extra_song_ids"
        const val EXTRA_URI = "extra_uri"
        
        // Custom Shuffle/Repeat
        const val ACTION_TOGGLE_SHUFFLE = "com.txapp.musicplayer.action.TOGGLE_SHUFFLE"
        const val ACTION_TOGGLE_REPEAT = "com.txapp.musicplayer.action.TOGGLE_REPEAT"
        const val ACTION_PLAY_SONG_IN_CONTEXT = "com.txapp.musicplayer.action.PLAY_SONG_IN_CONTEXT"
        const val ACTION_ENQUEUE_SONG = "com.txapp.musicplayer.action.ENQUEUE_SONG"
        const val EXTRA_CONTEXT_SONG_IDS = "extra_context_song_ids"
        
        // Sleep Timer
        const val ACTION_STOP = "com.txapp.musicplayer.action.STOP"
        
        // Audio session ID for Equalizer
        @Volatile
        var audioSessionId: Int = android.media.audiofx.AudioEffect.ERROR_BAD_VALUE
            private set
    }
    private inner class MediaLibrarySessionCallback : MediaLibraryService.MediaLibrarySession.Callback {
        
        @Suppress("UnstableApi")
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val availableSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand("PLAY_NEXT", Bundle.EMPTY))
                .add(SessionCommand("ADD_TO_QUEUE", Bundle.EMPTY))
                .add(SessionCommand("TOGGLE_FAVORITE", Bundle.EMPTY))
                // Add notification custom commands
                .add(TXAMediaNotificationProvider.COMMAND_TOGGLE_FAVORITE)
                .add(TXAMediaNotificationProvider.COMMAND_TOGGLE_SHUFFLE)
                .build()
            return MediaSession.ConnectionResult.accept(
                availableSessionCommands, 
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
            )
        }
        
        // ========== ANDROID AUTO BROWSING ==========
        
        /**
         * Trả về root cho Android Auto browsing
         */
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            // Check if this is a recent request (for "Continue playing" on Auto home)
            val isRecent = params?.isRecent == true
            
            val rootMediaId = if (isRecent) {
                AutoMediaIDHelper.MEDIA_ID_RECENT
            } else {
                AutoMediaIDHelper.MEDIA_ID_ROOT
            }
            
            val rootItem = MediaItem.Builder()
                .setMediaId(rootMediaId)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle("TXA Music")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build()
                )
                .build()
            
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }
        
        /**
         * Trả về children cho một parent ID (dùng cho Android Auto browsing)
         */
        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return serviceScope.future {
                try {
                    val children = autoMusicProvider.getChildren(parentId)
                    LibraryResult.ofItemList(ImmutableList.copyOf(children), params)
                } catch (e: Exception) {
                    TXALogger.appE("MusicService", "Error getting children for $parentId", e)
                    LibraryResult.ofItemList(ImmutableList.of(), params)
                }
            }
        }
        
        /**
         * Xử lý khi Android Auto muốn phát một item
         */
        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return serviceScope.future {
                try {
                    // Extract song ID from mediaId
                    val songIdStr = AutoMediaIDHelper.extractMusicID(mediaId)
                    val songId = songIdStr?.toLongOrNull()
                    
                    if (songId != null) {
                        val song = musicRepository.getSongById(songId)
                        if (song != null) {
                            val mediaItem = createMediaItem(song)
                            LibraryResult.ofItem(mediaItem, null)
                        } else {
                            LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                        }
                    } else {
                        // It's a browsable item, not a song
                        val children = autoMusicProvider.getChildren(mediaId)
                        if (children.isNotEmpty()) {
                            LibraryResult.ofItem(children.first(), null)
                        } else {
                            LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                        }
                    }
                } catch (e: Exception) {
                    TXALogger.appE("MusicService", "Error getting item $mediaId", e)
                    LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN)
                }
            }
        }
        
        /**
         * Handle search từ Android Auto voice command
         */
        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            // Trigger search and notify when ready
            serviceScope.launch {
                val results = musicRepository.searchSongs(query)
                val mediaItems = results.take(20).map { song -> createMediaItem(song) }
                session.notifySearchResultChanged(browser, query, mediaItems.size, params)
            }
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }
        
        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return serviceScope.future {
                try {
                    val results = musicRepository.searchSongs(query)
                    val mediaItems = results.take(pageSize).map { song -> createMediaItem(song) }
                    LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), params)
                } catch (e: Exception) {
                    LibraryResult.ofItemList(ImmutableList.of(), params)
                }
            }
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            val handler = android.os.Handler(android.os.Looper.getMainLooper())

            // Handle Favorite toggle from notification or app
            if (customCommand.customAction == "TOGGLE_FAVORITE" || 
                customCommand.customAction == TXAMediaNotificationProvider.ACTION_TOGGLE_FAVORITE) {
                 serviceScope.launch {
                     val currentMediaItem = player.currentMediaItem
                     val songId = currentMediaItem?.mediaId?.toLongOrNull()
                     if (songId != null) {
                         val song = musicRepository.getSongById(songId)
                         if (song != null) {
                             val newStatus = !song.isFavorite
                             musicRepository.setFavorite(songId, newStatus)
                             
                             // Show Feedback
                             handler.post {
                                 com.txapp.musicplayer.util.TXAToast.show(this@MusicService, 
                                     if (newStatus) "txamusic_action_add_to_favorites".txa() 
                                     else "txamusic_action_remove_from_favorites".txa())
                             }
                             
                             // Update metadata instantly
                             val currentExtras = currentMediaItem.mediaMetadata.extras ?: Bundle()
                             currentExtras.putBoolean("is_favorite", newStatus)
                             val newMetadata = currentMediaItem.mediaMetadata.buildUpon()
                                 .setExtras(currentExtras)
                                 .build()
                             val newItem = currentMediaItem.buildUpon().setMediaMetadata(newMetadata).build()
                             
                             // Update player - this will trigger notification refresh
                             withContext(Dispatchers.Main) {
                                 if (player.currentMediaItem?.mediaId == songId.toString()) {
                                      player.replaceMediaItem(player.currentMediaItemIndex, newItem)
                                 }
                             }
                             
                             // Broadcast for legacy UI sync
                             val intent = Intent("com.txapp.musicplayer.action.FAVORITE_STATE_CHANGED")
                             intent.putExtra("song_id", songId)
                             intent.putExtra("is_favorite", newStatus)
                             sendBroadcast(intent)
                             
                             updateMediaSessionLayout()
                         }
                     }
                 }
                 return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            
            // Handle Shuffle toggle from notification
            if (customCommand.customAction == TXAMediaNotificationProvider.ACTION_TOGGLE_SHUFFLE) {
                // Must toggle on Main Thread or safely
                handler.post {
                    isShuffleEnabled = !isShuffleEnabled
                    player.shuffleModeEnabled = isShuffleEnabled
                    
                    com.txapp.musicplayer.util.TXAToast.show(this@MusicService, 
                         if (isShuffleEnabled) "txamusic_shuffle_on".txa() 
                         else "txamusic_shuffle_off".txa())
                    
                    updateMediaSessionLayout()
                    
                    // Broadcast for UI sync
                    val shuffleBroadcast2 = Intent("com.txapp.musicplayer.action.SHUFFLE_MODE_CHANGED")
                    shuffleBroadcast2.putExtra("is_enabled", isShuffleEnabled)
                    sendBroadcast(shuffleBroadcast2)
                }
                    
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    private val metadataUpdateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            val songId = intent.getLongExtra("song_id", -1L)
            if (songId == -1L) return
            
            serviceScope.launch {
                val song = musicRepository.getSongById(songId) ?: return@launch
                
                // If this song is currently playing, update the player's metadata
                withContext(Dispatchers.Main) {
                    val currentItem = player.currentMediaItem
                    if (currentItem?.mediaId == songId.toString()) {
                        val newMediaItem = createMediaItem(song)
                        player.replaceMediaItem(player.currentMediaItemIndex, newMediaItem)
                        TXALogger.appI("MusicService", "Updated metadata for currently playing song: ${song.title}")
                }
            }
        }
    }
    }
}
