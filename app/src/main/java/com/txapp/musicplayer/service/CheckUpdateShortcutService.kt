package com.txapp.musicplayer.service

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.txapp.musicplayer.R
import com.txapp.musicplayer.ui.SplashActivity
import com.txapp.musicplayer.util.TXALogger
import com.txapp.musicplayer.util.TXAToast
import com.txapp.musicplayer.util.TXATranslation
import com.txapp.musicplayer.util.TXAUpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Service to check for updates from app shortcut (long-press icon)
 * - Shows TXAToast + notification when checking
 * - If update available: sends notification with action to open app
 * - If no update: shows toast + notification
 * 
 * Notification Group: All app notifications are grouped under TXAMUSIC group
 * so they appear together and at the top of notification shade
 */
class CheckUpdateShortcutService : Service() {

    companion object {
        // Notification Group for all app notifications
        private const val NOTIFICATION_GROUP_ID = "com.txapp.musicplayer.NOTIFICATION_GROUP"
        private const val NOTIFICATION_GROUP_NAME = "TXAMUSIC"
        
        // Update check channel
        private const val CHANNEL_ID = "txa_update_check_channel"
        private const val NOTIFICATION_ID = 9876
        
        const val ACTION_CHECK_UPDATE = "com.txapp.musicplayer.action.SHORTCUT_CHECK_UPDATE"
        const val EXTRA_FROM_NOTIFICATION = "extra_from_update_notification"
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        createNotificationGroup()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start foreground immediately to satisfy Android FGS requirements
        startForegroundWithCheckingNotification()
        
        when (intent?.action) {
            ACTION_CHECK_UPDATE -> {
                checkForUpdate()
            }
            else -> {
                // Unknown action, stop service
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }
    
    private fun startForegroundWithCheckingNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_noti)
            .setContentTitle(TXATranslation.txa("txamusic_app_name"))
            .setContentText(TXATranslation.txa("txamusic_shortcut_checking_update"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setGroup(NOTIFICATION_GROUP_ID)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .setOngoing(true)
            .setAutoCancel(false)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
    
    private fun checkForUpdate() {
        TXALogger.appI("CheckUpdateShortcut", "Starting update check from shortcut")
        
        // Show checking toast using TXAToast.info()
        val checkingMsg = TXATranslation.txa("txamusic_shortcut_checking_update")
        showInfoToast(checkingMsg)
        
        // Notification already shown by startForegroundWithCheckingNotification()
        
        serviceScope.launch {
            try {
                val updateInfo = TXAUpdateManager.checkForUpdate()
                
                withContext(Dispatchers.Main) {
                    if (updateInfo != null && updateInfo.updateAvailable) {
                        // Update available - show notification with action
                        val updateMsg = TXATranslation.txa(
                            "txamusic_shortcut_update_found",
                            updateInfo.latestVersionName
                        )
                        showSuccessToast(updateMsg)
                        showUpdateAvailableNotification(updateInfo.latestVersionName, updateInfo.changelog)
                    } else {
                        // No update - show notification
                        val noUpdateMsg = TXATranslation.txa("txamusic_shortcut_no_update")
                        showSuccessToast(noUpdateMsg)
                        showNoUpdateNotification()
                    }
                }
            } catch (e: Exception) {
                TXALogger.appE("CheckUpdateShortcut", "Update check failed", e)
                withContext(Dispatchers.Main) {
                    val errorMsg = TXATranslation.txa("txamusic_shortcut_update_error")
                    showErrorToast(errorMsg)
                    showErrorNotification(e.message ?: "Unknown error")
                }
            } finally {
                // Stop service after work is done
                stopSelf()
            }
        }
    }
    
    // Using TXAToast helper functions directly
    private fun showInfoToast(message: String) {
        try {
            android.os.Handler(mainLooper).post {
                TXAToast.info(applicationContext, message)
            }
        } catch (e: Exception) {
            TXALogger.appE("CheckUpdateShortcut", "Failed to show info toast", e)
        }
    }
    
    private fun showSuccessToast(message: String) {
        try {
            android.os.Handler(mainLooper).post {
                TXAToast.success(applicationContext, message)
            }
        } catch (e: Exception) {
            TXALogger.appE("CheckUpdateShortcut", "Failed to show success toast", e)
        }
    }
    
    private fun showErrorToast(message: String) {
        try {
            android.os.Handler(mainLooper).post {
                TXAToast.error(applicationContext, message)
            }
        } catch (e: Exception) {
            TXALogger.appE("CheckUpdateShortcut", "Failed to show error toast", e)
        }
    }
    
    private fun showUpdateAvailableNotification(versionName: String, changelog: String?) {
        // Intent to open app -> splash -> will navigate to settings/update
        val openIntent = Intent(this, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_FROM_NOTIFICATION, true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_noti)
            .setContentTitle(TXATranslation.txa("txamusic_shortcut_update_found_title"))
            .setContentText(TXATranslation.txa("txamusic_shortcut_update_found", versionName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setGroup(NOTIFICATION_GROUP_ID)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .addAction(
                R.drawable.ic_play,
                TXATranslation.txa("txamusic_shortcut_open_app"),
                pendingIntent
            )
        
        // Add BigText style if changelog is available
        if (!changelog.isNullOrEmpty()) {
            notificationBuilder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${TXATranslation.txa("txamusic_shortcut_update_found", versionName)}\n\n$changelog")
                    .setBigContentTitle(TXATranslation.txa("txamusic_shortcut_update_found_title"))
            )
        }
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }
    
    private fun showNoUpdateNotification() {
        val openIntent = Intent(this, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_noti)
            .setContentTitle(TXATranslation.txa("txamusic_app_name"))
            .setContentText(TXATranslation.txa("txamusic_shortcut_no_update"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setGroup(NOTIFICATION_GROUP_ID)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun showErrorNotification(errorDetail: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_noti)
            .setContentTitle(TXATranslation.txa("txamusic_app_name"))
            .setContentText(TXATranslation.txa("txamusic_shortcut_update_error"))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${TXATranslation.txa("txamusic_shortcut_update_error")}\n\n$errorDetail")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setGroup(NOTIFICATION_GROUP_ID)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Create notification group so all app notifications are grouped together
     * and appear at the top of notification shade
     */
    private fun createNotificationGroup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val group = NotificationChannelGroup(
                NOTIFICATION_GROUP_ID,
                NOTIFICATION_GROUP_NAME
            )
            
            notificationManager.createNotificationChannelGroup(group)
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = TXATranslation.txa("txamusic_shortcut_update_channel_name")
            val description = TXATranslation.txa("txamusic_shortcut_update_channel_desc")
            
            // Use IMPORTANCE_HIGH so notifications appear at top
            val importance = NotificationManager.IMPORTANCE_HIGH
            
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                this.description = description
                this.group = NOTIFICATION_GROUP_ID
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        TXALogger.appI("CheckUpdateShortcut", "Service destroyed")
    }
}
