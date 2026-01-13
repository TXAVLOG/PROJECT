package com.txapp.musicplayer.appwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import androidx.media3.common.Player
import com.txapp.musicplayer.R
import com.txapp.musicplayer.service.MusicService
import com.txapp.musicplayer.ui.MainActivity
import com.txapp.musicplayer.util.TXALogger
import kotlinx.coroutines.*
import java.io.FileNotFoundException

/**
 * TXA Music Widget 4x2
 * 
 * Features:
 * - Album art display
 * - Song title and artist
 * - Playback controls (prev, play/pause, next)
 * - Progress bar (optional)
 * - Shuffle & Repeat indicators
 * - Fully customizable via WidgetSettings
 * - Syncs with MediaSession, Notification, Samsung Now Bar
 * 
 * Uses Intent-based service communication (like Backup_Ref) instead of MediaController binding
 * to avoid BroadcastReceiver service binding issues.
 */
class TXAMusicWidget : AppWidgetProvider() {

    companion object {
        private const val TAG = "TXAMusicWidget"
        
        // Actions for widget buttons - must match MusicService companion object
        const val ACTION_TOGGLE_PLAYBACK = "com.txapp.musicplayer.action.TOGGLE_PLAYBACK"
        const val ACTION_NEXT = "com.txapp.musicplayer.action.NEXT"
        const val ACTION_PREVIOUS = "com.txapp.musicplayer.action.PREVIOUS"
        const val ACTION_TOGGLE_SHUFFLE = "com.txapp.musicplayer.action.TOGGLE_SHUFFLE"
        const val ACTION_TOGGLE_REPEAT = "com.txapp.musicplayer.action.TOGGLE_REPEAT"
        const val ACTION_UPDATE = "com.txapp.musicplayer.widget.UPDATE"
        
        // Cached state for quick updates
        private var cachedTitle: String = ""
        private var cachedArtist: String = ""
        private var cachedAlbumArtUri: String = ""
        private var cachedIsPlaying: Boolean = false
        private var cachedIsShuffleOn: Boolean = false
        private var cachedRepeatMode: Int = Player.REPEAT_MODE_OFF
        private var cachedProgress: Int = 0
        private var cachedDuration: Long = 0
        private var cachedPosition: Long = 0
        
        /**
         * Update all widget instances with current playback state
         */
        fun updateWidgets(context: Context) {
            val intent = Intent(context, TXAMusicWidget::class.java).apply {
                action = ACTION_UPDATE
            }
            context.sendBroadcast(intent)
        }
        
        /**
         * Update cached state and refresh widgets
         */
        fun updateState(
            context: Context,
            title: String? = null,
            artist: String? = null,
            albumArtUri: String? = null,
            isPlaying: Boolean? = null,
            isShuffleOn: Boolean? = null,
            repeatMode: Int? = null,
            position: Long? = null,
            duration: Long? = null
        ) {
            title?.let { cachedTitle = it }
            artist?.let { cachedArtist = it }
            albumArtUri?.let { cachedAlbumArtUri = it }
            isPlaying?.let { cachedIsPlaying = it }
            isShuffleOn?.let { cachedIsShuffleOn = it }
            repeatMode?.let { cachedRepeatMode = it }
            position?.let { cachedPosition = it }
            duration?.let { cachedDuration = it }
            
            if (cachedDuration > 0 && position != null) {
                cachedProgress = ((position * 100) / cachedDuration).toInt()
            }
            
            updateWidgets(context)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        TXALogger.appI(TAG, "onUpdate called for ${appWidgetIds.size} widgets")
        
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        TXALogger.appI(TAG, "onReceive: ${intent.action}")
        
        when (intent.action) {
            ACTION_UPDATE -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val widgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, TXAMusicWidget::class.java)
                )
                for (id in widgetIds) {
                    updateAppWidget(context, appWidgetManager, id)
                }
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        TXALogger.appI(TAG, "Widget enabled - first instance added")
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        TXALogger.appI(TAG, "Widget disabled - last instance removed")
        scope.cancel()
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val settings = WidgetSettings.load(context)
        val views = RemoteViews(context.packageName, R.layout.widget_txa_music)
        
        // Set up click intents
        setupClickIntents(context, views)
        
        // Update text content
        if (settings.showTitle) {
            views.setTextViewText(R.id.widget_title, cachedTitle.ifEmpty { "TXA Music" })
            views.setViewVisibility(R.id.widget_title, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_title, android.view.View.GONE)
        }
        
        if (settings.showArtist) {
            views.setTextViewText(R.id.widget_artist, cachedArtist.ifEmpty { "Tap to play" })
            views.setViewVisibility(R.id.widget_artist, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_artist, android.view.View.GONE)
        }
        
        // Update play/pause button
        val playPauseIcon = if (cachedIsPlaying) {
            R.drawable.ic_pause_widget
        } else {
            R.drawable.ic_play_widget
        }
        views.setImageViewResource(R.id.widget_btn_play_pause, playPauseIcon)
        
        // Shuffle button
        if (settings.showShuffle) {
            val shuffleAlpha = if (cachedIsShuffleOn) 255 else 128
            views.setInt(R.id.widget_btn_shuffle, "setImageAlpha", shuffleAlpha)
            views.setViewVisibility(R.id.widget_btn_shuffle, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_btn_shuffle, android.view.View.GONE)
        }
        
        // Repeat button
        if (settings.showRepeat) {
            val repeatIcon = when (cachedRepeatMode) {
                Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one_widget
                Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat_widget
                else -> R.drawable.ic_repeat_off_widget
            }
            val repeatAlpha = if (cachedRepeatMode != Player.REPEAT_MODE_OFF) 255 else 128
            views.setInt(R.id.widget_btn_repeat, "setImageAlpha", repeatAlpha)
            views.setImageViewResource(R.id.widget_btn_repeat, repeatIcon)
            views.setViewVisibility(R.id.widget_btn_repeat, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_btn_repeat, android.view.View.GONE)
        }
        
        // Progress bar
        if (settings.showProgress) {
            views.setProgressBar(R.id.widget_progress, 100, cachedProgress, false)
            views.setViewVisibility(R.id.widget_progress, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_progress, android.view.View.GONE)
        }
        
        // Album art
        if (settings.showAlbumArt) {
            views.setViewVisibility(R.id.widget_album_art, android.view.View.VISIBLE)
            loadAlbumArt(context, views, appWidgetManager, appWidgetId)
        } else {
            views.setViewVisibility(R.id.widget_album_art, android.view.View.GONE)
        }
        
        // Update widget
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun setupClickIntents(context: Context, views: RemoteViews) {
        // Open app when clicking on album art or text
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_album_art, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_title, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_artist, openAppPendingIntent)
        
        // Control buttons - send Intent directly to MusicService
        val serviceName = ComponentName(context, MusicService::class.java)
        views.setOnClickPendingIntent(R.id.widget_btn_prev, buildServicePendingIntent(context, ACTION_PREVIOUS, serviceName))
        views.setOnClickPendingIntent(R.id.widget_btn_play_pause, buildServicePendingIntent(context, ACTION_TOGGLE_PLAYBACK, serviceName))
        views.setOnClickPendingIntent(R.id.widget_btn_next, buildServicePendingIntent(context, ACTION_NEXT, serviceName))
        views.setOnClickPendingIntent(R.id.widget_btn_shuffle, buildServicePendingIntent(context, ACTION_TOGGLE_SHUFFLE, serviceName))
        views.setOnClickPendingIntent(R.id.widget_btn_repeat, buildServicePendingIntent(context, ACTION_TOGGLE_REPEAT, serviceName))
    }

    /**
     * Build a PendingIntent that sends an action directly to MusicService.
     * Uses getForegroundService for Android O+ to comply with background execution limits.
     */
    private fun buildServicePendingIntent(
        context: Context,
        action: String,
        serviceName: ComponentName
    ): PendingIntent {
        val intent = Intent(action).apply {
            component = serviceName
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                context, action.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                context, action.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    private fun loadAlbumArt(
        context: Context,
        views: RemoteViews,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        if (cachedAlbumArtUri.isEmpty()) {
            views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_default_album_art)
            return
        }
        
        scope.launch(Dispatchers.IO) {
            try {
                val uri = Uri.parse(cachedAlbumArtUri)
                val bitmap = loadBitmapFromUri(context, uri)
                
                withContext(Dispatchers.Main) {
                    if (bitmap != null) {
                        views.setImageViewBitmap(R.id.widget_album_art, bitmap)
                    } else {
                        views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_default_album_art)
                    }
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                TXALogger.appE(TAG, "Error loading album art", e)
                withContext(Dispatchers.Main) {
                    views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_default_album_art)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                
                // First decode with inJustDecodeBounds=true to check dimensions
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                
                // Calculate inSampleSize to target ~300px
                val targetSize = 300
                var inSampleSize = 1
                if (options.outHeight > targetSize || options.outWidth > targetSize) {
                    val halfHeight: Int = options.outHeight / 2
                    val halfWidth: Int = options.outWidth / 2
                    while (halfHeight / inSampleSize >= targetSize && halfWidth / inSampleSize >= targetSize) {
                        inSampleSize *= 2
                    }
                }
                
                // Decode with calculated inSampleSize
                val decodeOptions = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            }
        } catch (e: Exception) {
            TXALogger.appE(TAG, "Error loading bitmap: ${e.message}")
            null
        }
    }
}
