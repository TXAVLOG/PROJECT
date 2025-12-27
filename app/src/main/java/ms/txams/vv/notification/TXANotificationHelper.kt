package ms.txams.vv.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import ms.txams.vv.R
import ms.txams.vv.core.TXABackgroundLogger
import ms.txams.vv.data.manager.TXANowBarSettingsManager
import ms.txams.vv.ui.TXAMainActivity

/**
 * TXA Notification Helper
 * Creates customizable media notifications based on TXANowBarSettingsManager
 * 
 * Inspired by Namida's notification_controller.dart
 * Uses MediaStyleInformation with customizable action buttons
 * 
 * Features:
 * - Album art in notification (square, rounded, or circle based on settings)
 * - Customizable buttons (Previous, Play/Pause, Next, Stop, Shuffle, Repeat, Like)
 * - Compact view with configurable 3 buttons
 * - Dynamic coloring from album art
 * - Works on all Android versions (fallback for non-pill devices)
 * 
 * @author TXA - fb.com/vlog.txa.2311
 */
class TXANotificationHelper(
    private val context: Context,
    private val mediaSession: MediaSession
) {
    
    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "txa_music_playback"
        const val CHANNEL_NAME = "TXA Music Playback"
        
        // Action constants
        const val ACTION_PLAY = "ms.txams.vv.ACTION_PLAY"
        const val ACTION_PAUSE = "ms.txams.vv.ACTION_PAUSE"
        const val ACTION_PREVIOUS = "ms.txams.vv.ACTION_PREVIOUS"
        const val ACTION_NEXT = "ms.txams.vv.ACTION_NEXT"
        const val ACTION_STOP = "ms.txams.vv.ACTION_STOP"
        const val ACTION_SHUFFLE = "ms.txams.vv.ACTION_SHUFFLE"
        const val ACTION_REPEAT = "ms.txams.vv.ACTION_REPEAT"
        const val ACTION_LIKE = "ms.txams.vv.ACTION_LIKE"
    }
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val settingsManager = TXANowBarSettingsManager(context)
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "TXA Music playback controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
            TXABackgroundLogger.d("Created notification channel: $CHANNEL_ID")
        }
    }
    
    /**
     * Build and show the notification
     */
    fun showNotification(
        title: String,
        artist: String,
        album: String,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        position: Long,
        duration: Long,
        isShuffleOn: Boolean = false,
        repeatMode: Int = Player.REPEAT_MODE_OFF,
        isLiked: Boolean = false
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText(album)
            .setSmallIcon(R.drawable.ic_music_note)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .setContentIntent(createContentIntent())
        
        // Set album art with style from settings
        val processedArt = processAlbumArt(albumArt)
        if (processedArt != null) {
            builder.setLargeIcon(processedArt)
        }
        
        // Build action list based on settings
        val actions = buildActionList(isPlaying, isShuffleOn, repeatMode, isLiked)
        actions.forEach { action ->
            builder.addAction(action)
        }
        
        // Configure compact view
        val compactViewIndices = getCompactViewIndices(actions, isPlaying)
        
        // MediaStyle with session token
        val mediaStyle = MediaStyleNotificationHelper.MediaStyle(mediaSession)
            .setShowActionsInCompactView(*compactViewIndices.toIntArray())
        
        builder.setStyle(mediaStyle)
        
        // Progress if supported
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setProgress(duration.toInt(), position.toInt(), false)
        }
        
        val notification = builder.build()
        notificationManager.notify(NOTIFICATION_ID, notification)
        
        TXABackgroundLogger.d("Showing notification: $title - $artist")
        return notification
    }
    
    /**
     * Process album art based on settings (square, rounded, circle)
     */
    private fun processAlbumArt(bitmap: Bitmap?): Bitmap? {
        if (bitmap == null) return null
        
        return when (settingsManager.getAlbumArtStyle()) {
            TXANowBarSettingsManager.ALBUM_ART_CIRCLE -> {
                // Create circular bitmap
                createCircularBitmap(bitmap)
            }
            TXANowBarSettingsManager.ALBUM_ART_ROUNDED -> {
                // Create rounded corners bitmap
                createRoundedBitmap(bitmap, 24f)
            }
            else -> {
                // Square - just resize
                Bitmap.createScaledBitmap(bitmap, 512, 512, true)
            }
        }
    }
    
    /**
     * Create circular bitmap from source
     */
    private fun createCircularBitmap(source: Bitmap): Bitmap {
        val size = minOf(source.width, source.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            shader = android.graphics.BitmapShader(
                source,
                android.graphics.Shader.TileMode.CLAMP,
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        return output
    }
    
    /**
     * Create rounded corners bitmap
     */
    private fun createRoundedBitmap(source: Bitmap, radius: Float): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            shader = android.graphics.BitmapShader(
                source,
                android.graphics.Shader.TileMode.CLAMP,
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        
        val rect = android.graphics.RectF(0f, 0f, source.width.toFloat(), source.height.toFloat())
        canvas.drawRoundRect(rect, radius, radius, paint)
        return output
    }
    
    /**
     * Build list of notification actions based on settings
     */
    private fun buildActionList(
        isPlaying: Boolean,
        isShuffleOn: Boolean,
        repeatMode: Int,
        isLiked: Boolean
    ): List<NotificationCompat.Action> {
        val actions = mutableListOf<NotificationCompat.Action>()
        
        // Shuffle (optional)
        if (settingsManager.isButtonEnabled(TXANowBarSettingsManager.KEY_SHOW_SHUFFLE)) {
            val shuffleIcon = if (isShuffleOn) R.drawable.ic_shuffle else R.drawable.ic_shuffle
            actions.add(createAction(shuffleIcon, "Shuffle", ACTION_SHUFFLE))
        }
        
        // Previous (optional but usually shown)
        if (settingsManager.isButtonEnabled(TXANowBarSettingsManager.KEY_SHOW_PREVIOUS)) {
            actions.add(createAction(R.drawable.ic_previous, "Previous", ACTION_PREVIOUS))
        }
        
        // Play/Pause (always shown)
        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val playPauseAction = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        actions.add(createAction(playPauseIcon, if (isPlaying) "Pause" else "Play", playPauseAction))
        
        // Next (optional but usually shown)
        if (settingsManager.isButtonEnabled(TXANowBarSettingsManager.KEY_SHOW_NEXT)) {
            actions.add(createAction(R.drawable.ic_next, "Next", ACTION_NEXT))
        }
        
        // Repeat (optional)
        if (settingsManager.isButtonEnabled(TXANowBarSettingsManager.KEY_SHOW_REPEAT)) {
            val repeatIcon = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat // Could use ic_repeat_one
                Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat
                else -> R.drawable.ic_repeat
            }
            actions.add(createAction(repeatIcon, "Repeat", ACTION_REPEAT))
        }
        
        // Like (optional)
        if (settingsManager.isButtonEnabled(TXANowBarSettingsManager.KEY_SHOW_LIKE)) {
            val likeIcon = if (isLiked) R.drawable.ic_heart else R.drawable.ic_heart
            actions.add(createAction(likeIcon, "Like", ACTION_LIKE))
        }
        
        // Stop (optional)
        if (settingsManager.isButtonEnabled(TXANowBarSettingsManager.KEY_SHOW_STOP)) {
            actions.add(createAction(R.drawable.ic_stop, "Stop", ACTION_STOP))
        }
        
        return actions
    }
    
    /**
     * Create a notification action
     */
    private fun createAction(icon: Int, title: String, action: String): NotificationCompat.Action {
        val intent = Intent(action).apply {
            setPackage(context.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action.Builder(icon, title, pendingIntent).build()
    }
    
    /**
     * Get indices for compact view (max 3 actions)
     * Priority: Previous, Play/Pause, Next
     */
    private fun getCompactViewIndices(actions: List<NotificationCompat.Action>, isPlaying: Boolean): List<Int> {
        val indices = mutableListOf<Int>()
        
        // Find Play/Pause (always first priority)
        val playPauseIndex = actions.indexOfFirst { 
            it.title == "Play" || it.title == "Pause" 
        }
        
        // Find Previous and Next
        val prevIndex = actions.indexOfFirst { it.title == "Previous" }
        val nextIndex = actions.indexOfFirst { it.title == "Next" }
        
        // Add in order: Previous, Play/Pause, Next
        if (prevIndex >= 0) indices.add(prevIndex)
        if (playPauseIndex >= 0) indices.add(playPauseIndex)
        if (nextIndex >= 0) indices.add(nextIndex)
        
        // Limit to 3
        return indices.take(3)
    }
    
    /**
     * Create content intent to open app
     */
    private fun createContentIntent(): PendingIntent {
        val intent = Intent(context, TXAMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
    
    /**
     * Cancel notification
     */
    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
        TXABackgroundLogger.d("Notification cancelled")
    }
    
    /**
     * Update notification with new info
     */
    fun updateNotification(
        title: String,
        artist: String,
        album: String,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        position: Long = 0,
        duration: Long = 0
    ) {
        showNotification(title, artist, album, albumArt, isPlaying, position, duration)
    }
    
    /**
     * Get default album art if none provided
     */
    fun getDefaultAlbumArt(): Bitmap {
        return BitmapFactory.decodeResource(context.resources, R.drawable.ic_music_note)
    }
}
