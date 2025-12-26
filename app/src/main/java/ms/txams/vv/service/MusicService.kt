package ms.txams.vv.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import ms.txams.vv.R
import ms.txams.vv.core.TXALogger
import ms.txams.vv.ui.TXAMainActivity
import javax.inject.Inject

/**
 * TXA Music Service
 * 
 * Features:
 * - Media3 ExoPlayer for playback
 * - MediaSession for system integration
 * - Samsung Now Bar support (OneUI 7+)
 * - Custom notification with action buttons
 * - Lock screen controls
 * 
 * @author TXA - fb.com/vlog.txa.2311 - txavlog7@gmail.com
 */
@AndroidEntryPoint
class MusicService : MediaSessionService() {
    
    companion object {
        private const val CHANNEL_ID = "txa_music_playback"
        private const val CHANNEL_NAME = "Music Playback"
        private const val NOTIFICATION_ID = 1
        
        // Action constants
        const val ACTION_PLAY = "ms.txams.vv.ACTION_PLAY"
        const val ACTION_PAUSE = "ms.txams.vv.ACTION_PAUSE"
        const val ACTION_PREV = "ms.txams.vv.ACTION_PREV"
        const val ACTION_NEXT = "ms.txams.vv.ACTION_NEXT"
        const val ACTION_CLOSE = "ms.txams.vv.ACTION_CLOSE"
    }
    
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var currentAlbumArt: Bitmap? = null
    private var notificationReceiver: NotificationActionReceiver? = null
    
    override fun onCreate() {
        super.onCreate()
        TXALogger.appI("MusicService: onCreate")
        
        // Log device info for Samsung detection
        TXASamsungNowBar.logDeviceInfo()
        
        // Create notification channel
        createNotificationChannel()
        
        // Register broadcast receiver for notification actions
        registerNotificationReceiver()
        
        // Create ExoPlayer
        player = ExoPlayer.Builder(this).build().also { exo ->
            exo.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateNotification()
                }
                
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updateNotification()
                }
                
                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                    loadAlbumArt(mediaMetadata)
                    updateNotification()
                }
                
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        updateNotification()
                    }
                }
            })
        }
        
        // Create MediaSession
        mediaSession = MediaSession.Builder(this, player!!)
            .setSessionActivity(createContentIntent())
            .build()
        
        TXALogger.appI("MusicService: MediaSession created")
    }
    
    /**
     * Register broadcast receiver for notification button clicks
     */
    private fun registerNotificationReceiver() {
        notificationReceiver = NotificationActionReceiver()
        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY)
            addAction(ACTION_PAUSE)
            addAction(ACTION_PREV)
            addAction(ACTION_NEXT)
            addAction(ACTION_CLOSE)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(notificationReceiver, filter)
        }
        
        TXALogger.appD("Notification action receiver registered")
    }
    
    /**
     * Broadcast receiver for notification actions
     */
    inner class NotificationActionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val currentPlayer = player ?: return
            
            when (intent?.action) {
                ACTION_PLAY -> {
                    TXALogger.appD("Notification: Play")
                    currentPlayer.play()
                }
                ACTION_PAUSE -> {
                    TXALogger.appD("Notification: Pause")
                    currentPlayer.pause()
                }
                ACTION_PREV -> {
                    TXALogger.appD("Notification: Previous")
                    currentPlayer.seekToPreviousMediaItem()
                }
                ACTION_NEXT -> {
                    TXALogger.appD("Notification: Next")
                    currentPlayer.seekToNextMediaItem()
                }
                ACTION_CLOSE -> {
                    TXALogger.appD("Notification: Close")
                    currentPlayer.stop()
                    stopSelf()
                }
            }
        }
    }
    
    /**
     * Create notification channel
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "TXA Music playback controls"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
    
    /**
     * Create content intent for notification tap
     */
    private fun createContentIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this,
            0,
            Intent(this, TXAMainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    /**
     * Create action PendingIntent
     */
    private fun createActionIntent(action: String): PendingIntent {
        val intent = Intent(action).setPackage(packageName)
        return PendingIntent.getBroadcast(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    /**
     * Load album art from metadata
     */
    private fun loadAlbumArt(metadata: MediaMetadata) {
        val artworkData = metadata.artworkData
        if (artworkData != null) {
            try {
                currentAlbumArt = BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size)
            } catch (e: Exception) {
                TXALogger.appE("Failed to decode album art", e)
            }
        }
    }
    
    /**
     * Update notification with current playback state
     */
    private fun updateNotification() {
        val session = mediaSession ?: return
        val currentPlayer = player ?: return
        
        val metadata = currentPlayer.mediaMetadata
        val title = metadata.title?.toString() ?: "TXA Music"
        val artist = metadata.artist?.toString() ?: "Unknown Artist"
        val album = metadata.albumTitle?.toString()
        val isPlaying = currentPlayer.isPlaying
        
        val notification = buildNotification(title, artist, album, isPlaying)
        
        if (isPlaying) {
            startForeground(NOTIFICATION_ID, notification)
        } else {
            stopForeground(STOP_FOREGROUND_DETACH)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        }
    }
    
    /**
     * Build notification with custom action buttons
     */
    private fun buildNotification(
        title: String,
        artist: String,
        album: String?,
        isPlaying: Boolean
    ): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(createContentIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setDeleteIntent(createActionIntent(ACTION_CLOSE))
        
        // Set album as subtext
        album?.let { builder.setSubText(it) }
        
        // Set album art
        currentAlbumArt?.let { builder.setLargeIcon(it) }
        
        // Add action buttons with custom icons
        // Previous
        builder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_skip_previous,
                "Previous",
                createActionIntent(ACTION_PREV)
            ).build()
        )
        
        // Play/Pause
        if (isPlaying) {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_pause,
                    "Pause",
                    createActionIntent(ACTION_PAUSE)
                ).build()
            )
        } else {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_play,
                    "Play",
                    createActionIntent(ACTION_PLAY)
                ).build()
            )
        }
        
        // Next
        builder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_skip_next,
                "Next",
                createActionIntent(ACTION_NEXT)
            ).build()
        )
        
        // Close
        builder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_close,
                "Close",
                createActionIntent(ACTION_CLOSE)
            ).build()
        )
        // Apply MediaStyle for Now Bar
        // Show first 3 actions (prev, play/pause, next) in compact view
        builder.setStyle(
            androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(mediaSession!!)
                .setShowActionsInCompactView(0, 1, 2)
        )
        
        return builder.build()
    }
    
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = player
        if (currentPlayer != null && !currentPlayer.playWhenReady) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        TXALogger.appI("MusicService: onDestroy")
        
        // Unregister receiver
        notificationReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                TXALogger.appE("Failed to unregister receiver", e)
            }
        }
        notificationReceiver = null
        
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        currentAlbumArt = null
        
        super.onDestroy()
    }
}
