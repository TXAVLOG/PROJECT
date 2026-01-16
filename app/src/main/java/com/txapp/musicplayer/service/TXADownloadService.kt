package com.txapp.musicplayer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.txapp.musicplayer.ui.MainActivity
import com.txapp.musicplayer.util.*
import kotlinx.coroutines.*
import java.io.File

class TXADownloadService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var downloadJob: Job? = null
    private val notificationId = 2001
    private val channelId = "txa_download_channel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            TXAUpdateManager.ACTION_START -> {
                val url = intent.getStringExtra(TXAUpdateManager.EXTRA_UPDATE_INFO)
                if (url != null) {
                    startDownload(url)
                }
            }
            TXAUpdateManager.ACTION_STOP, TXAUpdateManager.ACTION_CANCEL -> {
                stopDownload()
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownload(url: String) {
        val initialNoti = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("txamusic_splash_loading_resources".txa())
            .setProgress(0, 0, true)
            .build()
        startForeground(notificationId, initialNoti)

        downloadJob?.cancel()
        // Use global exception handler to catch any hidden crashes
        val exHandler = TXACrashHandler.GlobalCoroutineExceptionHandler
        downloadJob = serviceScope.launch(Dispatchers.Main + exHandler) {
            try {
                TXAUpdateManager.downloadApk(this@TXADownloadService, url).collect { state ->
                    updateNotification(state)
                    if (state is DownloadState.Success) {
                        onDownloadComplete(state.file)
                    }
                }
            } catch (e: CancellationException) {
                // Ignore cancellation
            } catch (e: Throwable) {
                TXACrashHandler.reportFatalError(this@TXADownloadService, e, "DownloadService")
            }
        }
    }

    private fun stopDownload() {
        TXALogger.downloadI("DownloadService", "Service received stop/cancel request")
        downloadJob?.cancel()
        androidx.core.app.ServiceCompat.stopForeground(this, androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification(state: DownloadState) {
        val notification = when (state) {
            is DownloadState.Progress -> {
                val cancelIntent = Intent(this, TXADownloadService::class.java).apply {
                    action = TXAUpdateManager.ACTION_CANCEL
                }
                val pendingCancel = PendingIntent.getService(this, 3002, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

                NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("txamusic_noti_downloading_title".txa())
                    .setContentText(run {
                        val downloaded = TXAFormat.formatSize(state.downloaded)
                        val total = TXAFormat.formatSize(state.total)
                        val speed = TXAFormat.formatSpeed(state.bps)
                        val remaining = state.total - state.downloaded
                        val eta = TXAFormat.formatETA(remaining, state.bps)
                        "$downloaded / $total • $speed • $eta"
                    })
                    .setStyle(NotificationCompat.BigTextStyle().bigText(run {
                        val downloaded = TXAFormat.formatSize(state.downloaded)
                        val total = TXAFormat.formatSize(state.total)
                        val speed = TXAFormat.formatSpeed(state.bps)
                        val remaining = state.total - state.downloaded
                        val eta = TXAFormat.formatETA(remaining, state.bps)
                        "$downloaded / $total • $speed • $eta"
                    }))
                    .setProgress(100, state.percentage, false)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .addAction(android.R.drawable.ic_menu_close_clear_cancel, "txamusic_btn_cancel_download".txa(), pendingCancel)
                    .build()
            }
            is DownloadState.Success -> {
                NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("txamusic_noti_success_title".txa())
                    .setContentText("txamusic_noti_success_desc".txa())
                    .setAutoCancel(true)
                    .setOngoing(false)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentIntent(getInstallIntent(state.file))
                    .build()
            }
            is DownloadState.Error -> {
                NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setContentTitle("txamusic_noti_error_title".txa())
                    .setContentText("txamusic_noti_error_desc".txa(state.message))
                    .setAutoCancel(true)
                    .setOngoing(false)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .build()
            }
        }
        
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId, notification)
        
        if (state !is DownloadState.Progress) {
            TXALogger.downloadI("DownloadService", "Đã gửi thông báo kết quả tải xuống: ${state::class.simpleName}")
            androidx.core.app.ServiceCompat.stopForeground(this, androidx.core.app.ServiceCompat.STOP_FOREGROUND_DETACH)
            if (state is DownloadState.Success) {
                stopSelf()
            }
        }
    }

    private fun onDownloadComplete(file: File) {
        // Nếu app đang ở foreground, TXAUpdateManager.downloadState (StateFlow) 
        // sẽ được UI quan sát và tự động mở hộp thoại cài đặt.
        // Chỉ cần log lại ở đây.
        TXALogger.apiI("DownloadService", "Download Service: Success")
    }

    private fun getInstallIntent(file: File): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("install_apk_path", file.absolutePath)
        }
        return PendingIntent.getActivity(this, 3001, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "TXA Updates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
