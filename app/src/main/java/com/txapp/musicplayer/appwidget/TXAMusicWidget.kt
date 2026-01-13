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
import android.view.View
import android.widget.RemoteViews
import androidx.media3.common.Player
import com.txapp.musicplayer.R
import com.txapp.musicplayer.service.MusicService
import com.txapp.musicplayer.ui.MainActivity
import com.txapp.musicplayer.util.TXALogger
import com.txapp.musicplayer.util.TXATranslation
import com.txapp.musicplayer.util.TXAFormat
import kotlinx.coroutines.*

/**
 * TXA Music Widget - Classic Style
 * 
 * Based on Backup_Ref widget implementation with:
 * - Album art display (left side)
 * - Song title and artist (right top)
 * - Playback controls (right bottom): Shuffle, Prev, Play/Pause, Next, Repeat
 * - Progress bar (optional)
 * - Integrated with TXATranslation for multilingual support
 * - Syncs with MediaSession and Notification
 */
/**
 * Specialized providers for different widget sizes
 */
class TXAMusicWidgetSmall : TXAMusicWidget() {
    override fun getLayoutId() = com.txapp.musicplayer.R.layout.widget_txa_music_small
}

class TXAMusicWidgetBig : TXAMusicWidget() {
    override fun getLayoutId() = com.txapp.musicplayer.R.layout.widget_txa_music_big
}

open class TXAMusicWidget : AppWidgetProvider() {

    open fun getLayoutId() = com.txapp.musicplayer.R.layout.widget_txa_music


    companion object {
        private const val TAG = "TXAMusicWidget"
        const val NAME = "widget_txa_music"
        
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
        
        private var mInstance: TXAMusicWidget? = null
        
        val instance: TXAMusicWidget
            @Synchronized get() {
                if (mInstance == null) {
                    mInstance = TXAMusicWidget()
                }
                return mInstance!!
            }
        
        /**
         * Update all widget instances with current playback state
         */
        fun updateWidgets(context: Context) {
            val providers = arrayOf(
                TXAMusicWidget::class.java,
                TXAMusicWidgetSmall::class.java,
                TXAMusicWidgetBig::class.java
            )
            for (provider in providers) {
                val intent = Intent(context, provider).apply {
                    action = ACTION_UPDATE
                }
                context.sendBroadcast(intent)
            }
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
            isPlaying?.let { if (it) cachedIsPlaying = true else cachedIsPlaying = false; cachedIsPlaying = it }
            isShuffleOn?.let { cachedIsShuffleOn = it }
            repeatMode?.let { cachedRepeatMode = it }
            position?.let { cachedPosition = it }
            duration?.let { cachedDuration = it }
            
            if (cachedDuration > 0 && cachedPosition > 0) {
                cachedProgress = ((cachedPosition * 100) / cachedDuration).toInt()
            }
            
            updateWidgets(context)
        }
        
        /**
         * Check if there are any widget instances
         */
        fun hasInstances(context: Context): Boolean {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val providers = arrayOf(
                TXAMusicWidget::class.java,
                TXAMusicWidgetSmall::class.java,
                TXAMusicWidgetBig::class.java
            )
            for (provider in providers) {
                val widgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, provider)
                )
                if (widgetIds.isNotEmpty()) return true
            }
            return false
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        TXALogger.appI(TAG, "onUpdate called for ${appWidgetIds.size} widgets")
        defaultAppWidget(context, appWidgetIds)
    }
    
    /**
     * Initialize widgets to default state
     */
    private fun defaultAppWidget(context: Context, appWidgetIds: IntArray) {
        val views = RemoteViews(context.packageName, getLayoutId())
        val settings = WidgetSettings.load(context)
        
        // Set default text using TXATranslation
        val defaultTitle = TXATranslation.get("txamusic_app_name")
        val defaultArtist = TXATranslation.get("txamusic_widget_preview_artist")
        
        views.setTextViewText(R.id.widget_title, cachedTitle.ifEmpty { defaultTitle })
        views.setTextViewText(R.id.widget_artist, cachedArtist.ifEmpty { defaultArtist })
        
        // Set default album art
        views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_default_album_art)
        
        // Set default play button
        views.setImageViewResource(R.id.widget_btn_play_pause, R.drawable.ic_play_widget)
        
        // Set default progress (0%)
        views.setProgressBar(R.id.widget_progress, 100, cachedProgress, false)
        
        // Set default time display
        val currentTimeStr = TXAFormat.formatDuration(cachedPosition)
        val totalTimeStr = TXAFormat.formatDuration(cachedDuration)
        views.setTextViewText(R.id.widget_time_current, currentTimeStr)
        views.setTextViewText(R.id.widget_time_total, totalTimeStr)
        
        // Apply visibility settings
        applySettings(context, views, settings)
        
        // Link buttons
        linkButtons(context, views)
        
        // Push update
        pushUpdate(context, appWidgetIds, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        TXALogger.appI(TAG, "onReceive: ${intent.action}")
        
        when (intent.action) {
            ACTION_UPDATE -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val widgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, this::class.java)
                )
                performUpdate(context, widgetIds)
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
        performUpdate(context, intArrayOf(appWidgetId))
    }
    
    /**
     * Push update to widgets
     */
    private fun pushUpdate(context: Context, appWidgetIds: IntArray?, views: RemoteViews) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        if (appWidgetIds != null) {
            appWidgetManager.updateAppWidget(appWidgetIds, views)
        } else {
            appWidgetManager.updateAppWidget(
                ComponentName(context, this::class.java),
                views
            )
        }
    }

    /**
     * Update all active widget instances by pushing changes
     */
    private fun performUpdate(context: Context, appWidgetIds: IntArray?) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val targetIds = appWidgetIds ?: appWidgetManager.getAppWidgetIds(ComponentName(context, this::class.java))
        
        if (targetIds.isEmpty()) return

        val views = RemoteViews(context.packageName, getLayoutId())
        val settings = WidgetSettings.load(context)
        
        // Set the titles
        val defaultTitle = TXATranslation.get("txamusic_app_name")
        val defaultArtist = TXATranslation.get("txamusic_widget_preview_artist")
        
        // Set visibility of the content area
        views.setViewVisibility(R.id.widget_media_titles, View.VISIBLE)
        views.setTextViewText(R.id.widget_title, if (cachedTitle.isEmpty()) defaultTitle else cachedTitle)
        views.setTextViewText(R.id.widget_artist, if (cachedArtist.isEmpty()) defaultArtist else cachedArtist)
        
        // Update play/pause button
        val playPauseIcon = if (cachedIsPlaying) {
            R.drawable.ic_pause_widget
        } else {
            R.drawable.ic_play_widget
        }
        views.setImageViewResource(R.id.widget_btn_play_pause, playPauseIcon)
        
        // Shuffle button state
        val shuffleAlpha = if (cachedIsShuffleOn) 255 else 128
        views.setInt(R.id.widget_btn_shuffle, "setImageAlpha", shuffleAlpha)
        
        // Repeat button state
        val repeatIcon = when (cachedRepeatMode) {
            Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one_widget
            Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat_widget
            else -> R.drawable.ic_repeat_off_widget
        }
        val repeatAlpha = if (cachedRepeatMode != Player.REPEAT_MODE_OFF) 255 else 128
        views.setInt(R.id.widget_btn_repeat, "setImageAlpha", repeatAlpha)
        views.setImageViewResource(R.id.widget_btn_repeat, repeatIcon)
        
        // Progress bar (only linear now)
        try {
            views.setProgressBar(R.id.widget_progress, 100, cachedProgress, false)
        } catch (e: Exception) {
            TXALogger.appE(TAG, "Error setting progress bar", e)
        }
        
        // Time display - Current position (left) and Total duration (right)
        val currentTimeStr = TXAFormat.formatDuration(cachedPosition)
        val totalTimeStr = TXAFormat.formatDuration(cachedDuration)
        views.setTextViewText(R.id.widget_time_current, currentTimeStr)
        views.setTextViewText(R.id.widget_time_total, totalTimeStr)
        
        // Apply visibility settings
        applySettings(context, views, settings)
        
        // Link buttons
        linkButtons(context, views)
        
        // Load album art async
        loadAlbumArt(context, views, appWidgetIds)
    }
    
    /**
     * Apply visibility settings from WidgetSettings
     */
    private fun applySettings(context: Context, views: RemoteViews, settings: WidgetSettings) {
        // Title visibility
        if (!settings.showTitle) {
            views.setViewVisibility(R.id.widget_title, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_title, View.VISIBLE)
        }
        
        // Artist visibility
        if (!settings.showArtist) {
            views.setViewVisibility(R.id.widget_artist, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_artist, View.VISIBLE)
        }
        
        // Album art visibility
        if (!settings.showAlbumArt) {
            views.setViewVisibility(R.id.widget_album_art, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_album_art, View.VISIBLE)
        }
        
        // Progress bar visibility
        if (!settings.showProgress) {
            views.setViewVisibility(R.id.widget_progress, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_progress, View.VISIBLE)
        }
        
        // Shuffle button visibility
        if (!settings.showShuffle) {
            views.setViewVisibility(R.id.widget_btn_shuffle, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_btn_shuffle, View.VISIBLE)
        }
        
        // Repeat button visibility
        if (!settings.showRepeat) {
            views.setViewVisibility(R.id.widget_btn_repeat, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_btn_repeat, View.VISIBLE)
        }
    }

    /**
     * Link up various button actions using PendingIntent
     */
    private fun linkButtons(context: Context, views: RemoteViews) {
        val serviceName = ComponentName(context, MusicService::class.java)
        
        // Open app when clicking on album art or titles
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_album_art, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_media_titles, openAppPendingIntent)
        
        // Control buttons
        views.setOnClickPendingIntent(R.id.widget_btn_prev, buildPendingIntent(context, ACTION_PREVIOUS, serviceName))
        views.setOnClickPendingIntent(R.id.widget_btn_play_pause, buildPendingIntent(context, ACTION_TOGGLE_PLAYBACK, serviceName))
        views.setOnClickPendingIntent(R.id.widget_btn_next, buildPendingIntent(context, ACTION_NEXT, serviceName))
        views.setOnClickPendingIntent(R.id.widget_btn_shuffle, buildPendingIntent(context, ACTION_TOGGLE_SHUFFLE, serviceName))
        views.setOnClickPendingIntent(R.id.widget_btn_repeat, buildPendingIntent(context, ACTION_TOGGLE_REPEAT, serviceName))
    }

    /**
     * Build a PendingIntent that sends an action directly to MusicService
     */
    private fun buildPendingIntent(
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

    /**
     * Load album art asynchronously
     */
    private fun loadAlbumArt(
        context: Context,
        views: RemoteViews,
        appWidgetIds: IntArray?
    ) {
        if (cachedAlbumArtUri.isEmpty()) {
            views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_default_album_art)
            pushUpdate(context, appWidgetIds, views)
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
                    pushUpdate(context, appWidgetIds, views)
                }
            } catch (e: Exception) {
                TXALogger.appE(TAG, "Error loading album art", e)
                withContext(Dispatchers.Main) {
                    views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_default_album_art)
                    pushUpdate(context, appWidgetIds, views)
                }
            }
        }
    }

    /**
     * Load bitmap from URI with size optimization
     */
    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                
                // First decode with inJustDecodeBounds=true to check dimensions
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                
                // Calculate inSampleSize to target ~200px (widget is small)
                val targetSize = 200
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
