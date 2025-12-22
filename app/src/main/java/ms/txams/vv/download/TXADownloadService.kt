package ms.txams.vv.download

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
import ms.txams.vv.R
import ms.txams.vv.core.TXAFormat
import ms.txams.vv.core.TXALog
import ms.txams.vv.core.TXATranslation
import ms.txams.vv.ui.TXASettingsActivity
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
        private const val COMPLETION_NOTIFICATION_ID = 1003
        const val PREFS_NAME = "txa_download_prefs"
        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_DOWNLOAD_PROGRESS = "download_progress"
        const val KEY_DOWNLOAD_FILE_PATH = "download_file_path"
        const val KEY_DOWNLOAD_VERSION_NAME = "download_version_name"
        const val KEY_IS_DOWNLOADING = "is_downloading"
        const val KEY_LAST_UPDATE_TIME = "last_update_time"
        const val KEY_START_IN_FOREGROUND = "start_in_foreground"
        
        // Throttle updates to every 100ms minimum & log every 1s
        private const val UPDATE_THROTTLE_MS = 100L
        private const val PROGRESS_LOG_INTERVAL_MS = 1000L
        
        // Actions
        const val ACTION_CANCEL = "ms.txams.vv.download.CANCEL"
        const val ACTION_RETURN = "ms.txams.vv.download.RETURN"
        const val ACTION_APP_FOREGROUND = "ms.txams.vv.download.APP_FOREGROUND"
        const val ACTION_APP_BACKGROUND = "ms.txams.vv.download.APP_BACKGROUND"
        
        // Broadcast intents
        const val BROADCAST_DOWNLOAD_PROGRESS = "ms.txams.vv.DOWNLOAD_PROGRESS"
        const val BROADCAST_DOWNLOAD_COMPLETE = "ms.txams.vv.DOWNLOAD_COMPLETE"
        const val BROADCAST_DOWNLOAD_ERROR = "ms.txams.vv.DOWNLOAD_ERROR"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_DOWNLOADED_BYTES = "downloaded_bytes"
        const val EXTRA_TOTAL_BYTES = "total_bytes"
        const val EXTRA_SPEED_BPS = "speed_bps"
        const val EXTRA_ETA_SECONDS = "eta_seconds"
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_START_FOREGROUND = "start_foreground"
        const val EXTRA_ERROR_MESSAGE = "error_message"
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
    private var shouldStartInForeground = true
    private var isForegroundActive = false
    private var lastSpeedTime = 0L
    private var lastSpeedBytes = 0L

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
                refreshForegroundState()
            }
            ACTION_APP_BACKGROUND -> {
                TXALog.i(LOG_TAG, "App moved to background during download")
                isAppInForeground = false
                showBackgroundProgress()
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
        // Start with silent foreground notification (Android requirement)
        isAppInForeground = true
        dismissBackgroundNotice()
        startForeground(NOTIFICATION_ID, createSilentNotification())
        isForegroundActive = true

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
            
            val fileName = "TXAMusic_${versionName.replace(" ", "_")}.apk"
            val outputFile = File(downloadDir, fileName)
            
            // Save file path for cleanup
            prefs.edit().putString(KEY_DOWNLOAD_FILE_PATH, outputFile.absolutePath).apply()
            
            var downloaded = 0L
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var lastUpdateTime = 0L
            var lastLogTime = 0L
            
            // Initialize speed calculation
            lastSpeedTime = System.currentTimeMillis()
            lastSpeedBytes = 0L
            
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
                        
                        // Calculate speed and ETA
                        val speedBps = if (currentTime - lastSpeedTime > 0) {
                            ((downloaded - lastSpeedBytes) * 1000) / (currentTime - lastSpeedTime)
                        } else 0L
                        
                        val etaSeconds = if (speedBps > 0 && totalBytes > 0) {
                            (totalBytes - downloaded) / speedBps
                        } else 0L
                        
                        // Update speed calculation for next interval
                        if (currentTime - lastSpeedTime >= 1000) { // Update speed reference every second
                            lastSpeedTime = currentTime
                            lastSpeedBytes = downloaded
                        }
                        
                        // Broadcast progress to UI
                        broadcastProgress(progress, downloaded, totalBytes, speedBps, etaSeconds)

                        // Only update notification when app is backgrounded
                        if (!isAppInForeground) {
                            updateNotification(
                                title = TXATranslation.txa("txamusic_download_background_title"),
                                content = buildProgressContent(downloaded, totalBytes),
                                progress = progress,
                                indeterminate = totalBytes <= 0
                            )
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
                TXATranslation.txa("txamusic_download_cancel"),
                cancelPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_more,
                TXATranslation.txa("txamusic_download_return_app"),
                returnPendingIntent
            )

        return builder.build()
    }

    private fun broadcastProgress(progress: Int, downloadedBytes: Long, totalBytes: Long, speedBps: Long, etaSeconds: Long) {
        val intent = Intent(BROADCAST_DOWNLOAD_PROGRESS).apply {
            putExtra(EXTRA_PROGRESS, progress)
            putExtra(EXTRA_DOWNLOADED_BYTES, downloadedBytes)
            putExtra(EXTRA_TOTAL_BYTES, totalBytes)
            putExtra(EXTRA_SPEED_BPS, speedBps)
            putExtra(EXTRA_ETA_SECONDS, etaSeconds)
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
            .setContentTitle(TXATranslation.txa("txamusic_download_cancelled"))
            .setContentText(TXATranslation.txa("txamusic_download_cancelled_message"))
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
        refreshForegroundState()
    }

    private fun handleDownloadComplete(downloadedBytes: Long, totalBytes: Long) {
        TXALog.i(LOG_TAG, "Download completed: $downloadedBytes/$totalBytes bytes")
        
        // Clear downloading state but keep file info
        prefs.edit().putBoolean(KEY_IS_DOWNLOADING, false).apply()
        dismissBackgroundNotice()
        
        // Get the downloaded file path
        val filePath = prefs.getString(KEY_DOWNLOAD_FILE_PATH, "")
        
        // Broadcast completion event to UI
        val completeIntent = Intent(BROADCAST_DOWNLOAD_COMPLETE).apply {
            putExtra(EXTRA_FILE_PATH, filePath)
            putExtra(EXTRA_DOWNLOADED_BYTES, downloadedBytes)
            putExtra(EXTRA_TOTAL_BYTES, totalBytes)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(completeIntent)
        
        // Show completion notification with install action
        val installIntent = Intent(this, TXASettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("install_file", filePath)
        }
        val installPendingIntent = PendingIntent.getActivity(
            this, 0, installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val completionNotification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(TXATranslation.txa("txamusic_download_complete"))
            .setContentText(TXATranslation.txa("txamusic_download_complete_message"))
            .setContentIntent(installPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_save,
                TXATranslation.txa("txamusic_update_install"),
                installPendingIntent
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        notificationManager.notify(COMPLETION_NOTIFICATION_ID, completionNotification)
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
        
        val message = error.message ?: TXATranslation.txa("txamusic_download_failed_message")

        // Notify UI layer about failure
        val errorIntent = Intent(BROADCAST_DOWNLOAD_ERROR).apply {
            putExtra(EXTRA_ERROR_MESSAGE, message)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(errorIntent)

        // Show error notification with app icon
        val errorNotification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(TXATranslation.txa("txamusic_download_failed"))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
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
                TXATranslation.txa("txamusic_download_channel_name"),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = TXATranslation.txa("txamusic_download_channel_description")
                setShowBadge(true)
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
            .setContentTitle(TXATranslation.txa("txamusic_download_background_title"))
            .setContentText(TXATranslation.txa("txamusic_download_background_progress"))
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

    private fun showBackgroundProgress() {
        updateNotification(
            title = TXATranslation.txa("txamusic_download_background_title"),
            content = buildProgressContent(lastDownloadedBytes, lastTotalBytes),
            progress = lastProgress,
            indeterminate = lastTotalBytes <= 0
        )
        showBackgroundNotice()
    }

    private fun createSilentNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(TXATranslation.txa("txamusic_download_background_title"))
            .setContentText(TXATranslation.txa("txamusic_download_background_starting"))
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    private fun refreshForegroundState() {
        if (isAppInForeground) {
            startForeground(NOTIFICATION_ID, createSilentNotification())
        } else {
            updateNotification(
                title = TXATranslation.txa("txamusic_download_background_title"),
                content = buildProgressContent(lastDownloadedBytes, lastTotalBytes),
                progress = lastProgress,
                indeterminate = lastTotalBytes <= 0
            )
            showBackgroundNotice()
        }
    }

    private fun buildProgressContent(downloaded: Long, total: Long): String {
        val downloadedText = TXAFormat.formatBytes(downloaded)
        val totalText = if (total > 0) TXAFormat.formatBytes(total) else TXATranslation.txa("txamusic_update_downloading")
        return "$downloadedText / $totalText"
    }
}
