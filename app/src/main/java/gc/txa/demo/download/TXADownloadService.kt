package gc.txa.demo.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import gc.txa.demo.R
import gc.txa.demo.core.TXAFormat
import gc.txa.demo.core.TXALog
import gc.txa.demo.core.TXATranslation
import gc.txa.demo.ui.TXASettingsActivity
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

class TXADownloadService : Service() {

    companion object {
        private const val TAG = "DownloadService"
        private const val LOG_TAG = "TXAAPK"
        private const val NOTIFICATION_CHANNEL_ID = "txa_download_channel"
        private const val NOTIFICATION_ID = 1001
        private const val BACKGROUND_INFO_NOTIFICATION_ID = 1002
        private const val PREFS_NAME = "txa_download_prefs"
        private const val KEY_DOWNLOAD_URL = "download_url"
        private const val KEY_DOWNLOAD_PROGRESS = "download_progress"
        private const val KEY_DOWNLOAD_FILE_PATH = "download_file_path"
        private const val KEY_DOWNLOAD_VERSION_NAME = "download_version_name"
        private const val KEY_IS_DOWNLOADING = "is_downloading"
        private const val KEY_LAST_UPDATE_TIME = "last_update_time"
        
        // Throttle updates to every 100ms minimum & log every 1s
        private const val UPDATE_THROTTLE_MS = 100L
        private const val PROGRESS_LOG_INTERVAL_MS = 1000L
        
        // Actions
        const val ACTION_CANCEL = "gc.txa.demo.download.CANCEL"
        const val ACTION_RETURN = "gc.txa.demo.download.RETURN"
        const val ACTION_APP_FOREGROUND = "gc.txa.demo.download.APP_FOREGROUND"
        const val ACTION_APP_BACKGROUND = "gc.txa.demo.download.APP_BACKGROUND"
        
        // Broadcast intents
        const val BROADCAST_DOWNLOAD_PROGRESS = "gc.txa.demo.DOWNLOAD_PROGRESS"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_DOWNLOADED_BYTES = "downloaded_bytes"
        const val EXTRA_TOTAL_BYTES = "total_bytes"
    }

    private var downloadJob: Job? = null
    private var isAppInForeground = false
    private var backgroundNoticeVisible = false
    private lateinit var prefs: SharedPreferences
    private lateinit var notificationManager: NotificationManager
    private val httpClient = OkHttpClient()
    private var lastProgress = 0
    private var lastDownloadedBytes = 0L
    private var lastTotalBytes = 0L

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        TXALog.i(LOG_TAG, "Service created, ready for background downloads")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> cancelDownload()
            ACTION_RETURN -> returnToApp()
            ACTION_APP_FOREGROUND -> {
                TXALog.i(LOG_TAG, "App returned to foreground during download")
                isAppInForeground = true
                dismissBackgroundNotice()
                refreshCurrentNotification()
            }
            ACTION_APP_BACKGROUND -> {
                TXALog.i(LOG_TAG, "App moved to background during download")
                isAppInForeground = false
                showBackgroundNotice()
            }
            else -> {
                // Check if we should resume an interrupted download
                if (prefs.getBoolean(KEY_IS_DOWNLOADING, false)) {
                    TXALog.i(TAG, "Resuming interrupted download")
                    resumeDownload()
                } else {
                    startDownload(intent)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownload(intent: Intent?) {
        val url = intent?.getStringExtra("download_url") ?: run {
            TXALog.w(LOG_TAG, "startDownload called without URL")
            return
        }
        val versionName = intent.getStringExtra("version_name") ?: run {
            TXALog.w(LOG_TAG, "startDownload called without version name")
            return
        }
        TXALog.i(LOG_TAG, "Starting download for $versionName from $url")
        
        // Save download state
        prefs.edit().apply {
            putString(KEY_DOWNLOAD_URL, url)
            putString(KEY_DOWNLOAD_VERSION_NAME, versionName)
            putBoolean(KEY_IS_DOWNLOADING, true)
            putInt(KEY_DOWNLOAD_PROGRESS, 0)
            apply()
        }

        startDownloadJob(url, versionName)
    }

    private fun resumeDownload() {
        val url = prefs.getString(KEY_DOWNLOAD_URL, null) ?: return
        val versionName = prefs.getString(KEY_DOWNLOAD_VERSION_NAME, null) ?: return
        
        TXALog.i(LOG_TAG, "Resuming download for $versionName from $url")
        startDownloadJob(url, versionName)
    }

    private fun startDownloadJob(url: String, versionName: String) {
        // Start foreground service with initial notification
        val initialNotification = createNotification(
            title = TXATranslation.txa("txademo_download_background_title"),
            content = TXATranslation.txa("txademo_download_background_starting"),
            progress = 0,
            indeterminate = true
        )
        startForeground(NOTIFICATION_ID, initialNotification)
        dismissBackgroundNotice()
        isAppInForeground = true

        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                performDownload(url, versionName)
            } catch (e: Exception) {
                TXALog.e(LOG_TAG, "Download failed", e)
                handleDownloadError(e)
            }
        }
    }

    private suspend fun performDownload(url: String, versionName: String) {
        TXALog.i(LOG_TAG, "Creating HTTP request for $url")
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            TXALog.e(LOG_TAG, "HTTP error ${response.code}: ${response.message}")
            throw IOException("HTTP ${response.code}: ${response.message}")
        }
        TXALog.i(LOG_TAG, "Server response OK (${response.code}), contentLength=${response.body?.contentLength()}")

        val totalBytes = response.body?.contentLength() ?: 0L
        val downloadedBytes = response.body?.byteStream()?.use { inputStream ->
            val downloadDir = File(getExternalFilesDir(null), "downloads")
            if (!downloadDir.exists()) downloadDir.mkdirs()
            
            val fileName = "TXADemo_${versionName.replace(" ", "_")}.apk"
            val outputFile = File(downloadDir, fileName)
            
            // Save file path for cleanup
            prefs.edit().putString(KEY_DOWNLOAD_FILE_PATH, outputFile.absolutePath).apply()
            
            var downloaded = 0L
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var lastUpdateTime = 0L
            var lastLogTime = 0L
            
            outputFile.outputStream().use { outputStream ->
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    // Check cancellation frequently (every 4KB chunk)
                    if (!prefs.getBoolean(KEY_IS_DOWNLOADING, false)) {
                        throw IOException("Download cancelled")
                    }
                    
                    outputStream.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    
                    val progress = if (totalBytes > 0) {
                        ((downloaded * 100) / totalBytes).toInt()
                    } else 0
                    
                    val currentTime = System.currentTimeMillis()
                    
                    // Throttle updates to every 100ms minimum for performance
                    if (currentTime - lastUpdateTime >= UPDATE_THROTTLE_MS) {
                        lastProgress = progress
                        lastDownloadedBytes = downloaded
                        lastTotalBytes = totalBytes

                        // Update progress in preferences
                        prefs.edit().apply {
                            putInt(KEY_DOWNLOAD_PROGRESS, progress)
                            putLong(KEY_LAST_UPDATE_TIME, currentTime)
                            apply()
                        }
                        
                        // Update notification
                        updateNotification(
                            title = TXATranslation.txa("txademo_download_background_title"),
                            content = TXATranslation.txa("txademo_download_background_progress"),
                            progress = progress,
                            indeterminate = false
                        )
                        
                        // Broadcast progress to UI
                        broadcastProgress(progress, downloaded, totalBytes)

                        if (!isAppInForeground) {
                            showBackgroundNotice()
                        }
                        
                        lastUpdateTime = currentTime
                    }

                    if (currentTime - lastLogTime >= PROGRESS_LOG_INTERVAL_MS) {
                        TXALog.i(
                            LOG_TAG,
                            "Progress $progress% (${TXAFormat.formatBytes(downloaded)}/${if (totalBytes > 0) TXAFormat.formatBytes(totalBytes) else "unknown"})"
                        )
                        lastLogTime = currentTime
                    }
                }
            }
            
            downloaded
        } ?: 0L

        // Download completed
        handleDownloadComplete(downloadedBytes, totalBytes)
    }

    private fun updateNotification(title: String, content: String, progress: Int, indeterminate: Boolean) {
        val notification = createNotification(title, content, progress, indeterminate)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(
        title: String, 
        content: String, 
        progress: Int = 0, 
        indeterminate: Boolean = false
    ): Notification {
        // Cancel action
        val cancelIntent = Intent(this, TXADownloadService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Return to app action
        val returnIntent = Intent(this, TXADownloadService::class.java).apply {
            action = ACTION_RETURN
        }
        val returnPendingIntent = PendingIntent.getService(
            this, 1, returnIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(content)
            .setProgress(100, progress, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                TXATranslation.txa("txademo_download_cancel"),
                cancelPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_more,
                TXATranslation.txa("txademo_download_return_app"),
                returnPendingIntent
            )

        return builder.build()
    }

    private fun broadcastProgress(progress: Int, downloadedBytes: Long, totalBytes: Long) {
        val intent = Intent(BROADCAST_DOWNLOAD_PROGRESS).apply {
            putExtra(EXTRA_PROGRESS, progress)
            putExtra(EXTRA_DOWNLOADED_BYTES, downloadedBytes)
            putExtra(EXTRA_TOTAL_BYTES, totalBytes)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun cancelDownload() {
        TXALog.i(LOG_TAG, "Download cancelled by user")
        prefs.edit().putBoolean(KEY_IS_DOWNLOADING, false).apply()
        downloadJob?.cancel()
        dismissBackgroundNotice()
        
        // Delete partial file
        val filePath = prefs.getString(KEY_DOWNLOAD_FILE_PATH, null)
        filePath?.let {
            File(it).delete()
        }
        
        // Clear download state
        prefs.edit().clear().apply()
        
        // Show cancelled notification
        val cancelledNotification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(TXATranslation.txa("txademo_download_cancelled"))
            .setContentText(TXATranslation.txa("txademo_download_cancelled_message"))
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, cancelledNotification)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun returnToApp() {
        val intent = Intent(this, TXASettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("show_download_dialog", true)
        }
        startActivity(intent)
        
        // Don't stop service - let it continue in background
        TXALog.i(LOG_TAG, "Return-to-app action tapped, keeping download in background")
        isAppInForeground = true
        dismissBackgroundNotice()
        refreshCurrentNotification()
    }

    private fun handleDownloadComplete(downloadedBytes: Long, totalBytes: Long) {
        TXALog.i(LOG_TAG, "Download completed: $downloadedBytes/$totalBytes bytes")
        
        // Clear downloading state but keep file info
        prefs.edit().putBoolean(KEY_IS_DOWNLOADING, false).apply()
        dismissBackgroundNotice()
        
        // Show completion notification
        val completionNotification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(TXATranslation.txa("txademo_download_complete"))
            .setContentText(TXATranslation.txa("txademo_download_complete_message"))
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, completionNotification)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleDownloadError(error: Exception) {
        TXALog.e(LOG_TAG, "Download error", error)
        
        // Delete partial file
        val filePath = prefs.getString(KEY_DOWNLOAD_FILE_PATH, null)
        filePath?.let {
            File(it).delete()
        }
        
        // Clear state
        prefs.edit().clear().apply()
        dismissBackgroundNotice()
        
        // Show error notification
        val errorNotification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(TXATranslation.txa("txademo_download_failed"))
            .setContentText(error.message ?: TXATranslation.txa("txademo_download_failed_message"))
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, errorNotification)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                TXATranslation.txa("txademo_download_channel_name"),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = TXATranslation.txa("txademo_download_channel_description")
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        downloadJob?.cancel()
        dismissBackgroundNotice()
        TXALog.i(LOG_TAG, "Service destroyed, job cancelled=${downloadJob?.isCancelled}")
    }

    private fun showBackgroundNotice() {
        if (backgroundNoticeVisible) return
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(TXATranslation.txa("txademo_download_background_title"))
            .setContentText(TXATranslation.txa("txademo_download_background_progress"))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        notificationManager.notify(BACKGROUND_INFO_NOTIFICATION_ID, notification)
        backgroundNoticeVisible = true
    }

    private fun dismissBackgroundNotice() {
        if (!backgroundNoticeVisible) return
        notificationManager.cancel(BACKGROUND_INFO_NOTIFICATION_ID)
        backgroundNoticeVisible = false
    }

    private fun refreshCurrentNotification() {
        updateNotification(
            title = TXATranslation.txa("txademo_download_background_title"),
            content = TXATranslation.txa("txademo_download_background_progress"),
            progress = lastProgress,
            indeterminate = lastTotalBytes <= 0
        )
    }
}
