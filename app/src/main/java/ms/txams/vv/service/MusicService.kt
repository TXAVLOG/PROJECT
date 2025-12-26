package ms.txams.vv.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
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
import androidx.media3.session.MediaStyleNotificationHelper
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
 * - Lock screen controls
 * - Notification with album art
 * 
 * @author TXA - fb.com/vlog.txa.2311 - txavlog7@gmail.com
 */
@AndroidEntryPoint
class MusicService : MediaSessionService() {
    
    companion object {
        private const val CHANNEL_ID = "txa_music_playback"
        private const val CHANNEL_NAME = "Music Playback"
        private const val NOTIFICATION_ID = 1
    }
    
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var currentAlbumArt: Bitmap? = null
    
    override fun onCreate() {
        super.onCreate()
        TXALogger.appI("MusicService: onCreate")
        
        // Log device info for Samsung detection
        TXASamsungNowBar.logDeviceInfo()
        
        // Create notification channel
        createNotificationChannel()
        
        // Create ExoPlayer
        player = ExoPlayer.Builder(this).build().also { exo ->
            // Add player listener for state changes
            exo.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateNotification()
                }
                
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updateNotification()
                }
                
                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                    // Load album art if available
                    loadAlbumArt(mediaMetadata)
                    updateNotification()
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
     * Create notification channel for music playback
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
        
        TXALogger.appD("Notification channel created: $CHANNEL_ID")
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
     * Load album art from metadata
     */
    private fun loadAlbumArt(metadata: MediaMetadata) {
        // Try to get artwork from metadata
        val artworkData = metadata.artworkData
        if (artworkData != null) {
            try {
                currentAlbumArt = BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size)
                TXALogger.appD("Album art loaded from metadata")
            } catch (e: Exception) {
                TXALogger.appE("Failed to decode album art", e)
            }
        }
    }
    
    /**
     * Update notification with current playback state
     * This is auto-converted to Now Bar on Samsung OneUI 7+
     */
    private fun updateNotification() {
        val session = mediaSession ?: return
        val currentPlayer = player ?: return
        
        val metadata = currentPlayer.mediaMetadata
        val title = metadata.title?.toString() ?: "Unknown"
        val artist = metadata.artist?.toString() ?: "Unknown Artist"
        val album = metadata.albumTitle?.toString()
        val isPlaying = currentPlayer.isPlaying
        
        val notification = buildNotification(session, title, artist, album, isPlaying)
        
        // Start foreground if playing
        if (isPlaying) {
            startForeground(NOTIFICATION_ID, notification)
        } else {
            // Stop foreground but keep notification
            stopForeground(STOP_FOREGROUND_DETACH)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        }
    }
    
    /**
     * Build media notification optimized for Samsung Now Bar
     */
    private fun buildNotification(
        session: MediaSession,
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
        
        // Set album as subtext if available
        album?.let { builder.setSubText(it) }
        
        // Set album art (important for Now Bar display)
        currentAlbumArt?.let { builder.setLargeIcon(it) }
        
        // Apply MediaStyle - this is what makes Now Bar work on Samsung
        // The MediaStyle connects notification controls to MediaSession
        builder.setStyle(
            MediaStyleNotificationHelper.MediaStyle(session)
                .setShowActionsInCompactView(0, 1, 2)
        )
        
        // Log Samsung optimization
        if (TXASamsungNowBar.isSamsungDevice()) {
            TXALogger.appD("Building Now Bar optimized notification")
        }
        
        return builder.build()
    }
    
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        // Stop playback if user swipes away app
        val currentPlayer = player
        if (currentPlayer != null && !currentPlayer.playWhenReady) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        TXALogger.appI("MusicService: onDestroy")
        
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
