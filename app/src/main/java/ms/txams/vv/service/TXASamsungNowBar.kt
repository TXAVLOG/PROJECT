package ms.txams.vv.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import ms.txams.vv.R
import ms.txams.vv.core.TXALogger
import ms.txams.vv.ui.TXAMainActivity

/**
 * TXA Samsung Now Bar Helper
 * 
 * Optimizes media notification for Samsung OneUI Now Bar compatibility.
 * 
 * Samsung Now Bar (OneUI 7+):
 * - Pill-shaped widget on lock screen and AOD
 * - Auto-displays music controls from MediaStyle notifications
 * - Expandable for full controls
 * 
 * Implementation Strategy:
 * 1. Use standard MediaStyle notification (works on ALL devices)
 * 2. Samsung automatically converts to Now Bar on OneUI 7+
 * 3. Optimize metadata for best display
 * 
 * Future: Android 16 "Live Updates" API will provide standardized
 * method for Now Bar integration across manufacturers.
 * 
 * @author TXA - fb.com/vlog.txa.2311 - txavlog7@gmail.com
 */
object TXASamsungNowBar {
    
    private const val CHANNEL_ID = "txa_music_playback"
    private const val CHANNEL_NAME = "Music Playback"
    private const val NOTIFICATION_ID = 1
    
    /**
     * Check if device is Samsung
     */
    fun isSamsungDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer.contains("samsung")
    }
    
    /**
     * Check if device supports OneUI 7+ (Now Bar)
     * OneUI 7 = Android 15 on Samsung
     * OneUI 6 = Android 14 on Samsung
     */
    fun supportsNowBar(): Boolean {
        if (!isSamsungDevice()) return false
        
        // OneUI 7 requires Android 15 (API 35)
        // OneUI 6 has basic Now Bar on Android 14 (API 34)
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE // API 34
    }
    
    /**
     * Get OneUI version estimate
     * Note: Actual OneUI version detection requires Samsung SDK
     */
    fun getOneUIVersionEstimate(): String {
        if (!isSamsungDevice()) return "N/A"
        
        return when {
            Build.VERSION.SDK_INT >= 35 -> "OneUI 7+" // Android 15
            Build.VERSION.SDK_INT >= 34 -> "OneUI 6"  // Android 14
            Build.VERSION.SDK_INT >= 33 -> "OneUI 5"  // Android 13
            Build.VERSION.SDK_INT >= 31 -> "OneUI 4"  // Android 12
            else -> "OneUI 3 or older"
        }
    }
    
    /**
     * Create notification channel optimized for Samsung
     */
    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW // Low = no sound, but visible
        ).apply {
            description = "TXA Music playback controls"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            
            // Samsung-specific: Enable on lock screen
            if (isSamsungDevice()) {
                TXALogger.appD("Samsung device detected, optimizing notification channel")
            }
        }
        
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
    
    /**
     * Build media notification optimized for Now Bar
     * 
     * Key points for Samsung Now Bar:
     * - Use MediaStyle with MediaSession token
     * - Provide album art (will be shown in Now Bar)
     * - Set proper actions (play/pause, prev, next)
     * - Set metadata (title, artist, album)
     */
    fun buildMediaNotification(
        context: Context,
        mediaSession: MediaSession,
        title: String,
        artist: String,
        album: String? = null,
        albumArt: Bitmap? = null,
        isPlaying: Boolean = false
    ): Notification {
        // Content intent - opens app when notification clicked
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, TXAMainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build notification with MediaStyle
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText(album)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
        
        // Set album art (important for Now Bar display)
        albumArt?.let { 
            builder.setLargeIcon(it)
        }
        
        // Apply MediaStyle (required for Now Bar)
        // MediaStyle connects notification to MediaSession
        builder.setStyle(
            MediaStyleNotificationHelper.MediaStyle(mediaSession)
                .setShowActionsInCompactView(0, 1, 2) // prev, play/pause, next in compact view
        )
        
        // Samsung optimization: Ensure metadata is set on MediaSession
        // The Now Bar pulls info from MediaSession metadata
        if (isSamsungDevice()) {
            TXALogger.appD("Building notification for Samsung Now Bar")
        }
        
        return builder.build()
    }
    
    /**
     * Get notification ID
     */
    fun getNotificationId(): Int = NOTIFICATION_ID
    
    /**
     * Get channel ID
     */
    fun getChannelId(): String = CHANNEL_ID
    
    /**
     * Log device info for debugging
     */
    fun logDeviceInfo() {
        TXALogger.appI(buildString {
            appendLine("=== Device Info ===")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Samsung: ${isSamsungDevice()}")
            appendLine("Supports Now Bar: ${supportsNowBar()}")
            if (isSamsungDevice()) {
                appendLine("OneUI Version: ${getOneUIVersionEstimate()}")
            }
        })
    }
    
    /**
     * Samsung Face Widget hint (for future implementation)
     * 
     * To implement Face Widget (lock screen widget):
     * 1. Add to AndroidManifest.xml:
     *    <uses-permission android:name="com.samsung.systemui.permission.FACE_WIDGET"/>
     *    <meta-data android:name="com.samsung.systemui.facewidget.executable" android:value="true"/>
     * 
     * 2. Create facewidgets.json in res/raw/
     * 
     * 3. Use RemoteViews for widget layout
     * 
     * Note: Face Widget is different from Now Bar and requires Samsung certification.
     */
    
    /**
     * Android 16 Live Updates API hint (for future)
     * 
     * Android 16 will introduce "Live Updates" API that works with:
     * - Status bar
     * - Lock screen
     * - Samsung Now Bar
     * 
     * This will be the standardized way to integrate with Now Bar.
     */
}
