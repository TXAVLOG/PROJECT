package gc.txa.demo.ui

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.appset.AppSet
import com.google.android.gms.appset.AppSetIdClient
import gc.txa.demo.BuildConfig
import gc.txa.demo.R
import gc.txa.demo.TXAApp
import gc.txa.demo.core.TXAFormat
import gc.txa.demo.core.TXAHttp
import gc.txa.demo.core.TXALog
import gc.txa.demo.core.TXATranslation
import gc.txa.demo.databinding.ActivityTxaSettingsBinding
import gc.txa.demo.download.TXADownloadService
import gc.txa.demo.ui.components.TXAChangelogDialog
import gc.txa.demo.ui.components.TXAProgressDialog
import gc.txa.demo.update.TXADownload
import gc.txa.demo.update.TXADownloadUrlResolver
import gc.txa.demo.update.TXAInstall
import gc.txa.demo.update.TXAUpdateManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

class TXASettingsActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_LAUNCH_FROM_UPDATE_NOTIFICATION = "extra_launch_from_update_notification"
        const val EXTRA_AUTO_START_DOWNLOAD = "extra_auto_start_download"
        const val EXTRA_UPDATE_INFO = "extra_update_info"

        private const val DOWNLOAD_ERROR_CHANNEL_ID = "txa_download_error_channel"
        private const val DOWNLOAD_ERROR_NOTIFICATION_ID = 3101
    }

    private val downloadErrorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message = intent?.getStringExtra(TXADownloadService.EXTRA_ERROR_MESSAGE)
                ?: TXATranslation.txa("txademo_error_download_failed")
            runOnUiThread {
                downloadProgressDialog?.dismiss()
                downloadProgressDialog = null
                showDownloadErrorNotification(message)
                Toast.makeText(this@TXASettingsActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private lateinit var binding: ActivityTxaSettingsBinding
    private lateinit var appSetIdClient: AppSetIdClient
    private var downloadProgressDialog: TXAProgressDialog? = null
    private val NOTIFICATION_PERMISSION_CODE = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTxaSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appSetIdClient = AppSet.getClient(this)
        
        setupUI()
        handleNotificationLaunch(intent)
        loadAppSetId()
        
        // Check if we should show download dialog (from notification return)
        if (intent.getBooleanExtra("show_download_dialog", false)) {
            checkAndShowDownloadDialog()
        }
        
        // Register for download progress broadcasts
        LocalBroadcastManager.getInstance(this).registerReceiver(
            downloadProgressReceiver,
            android.content.IntentFilter(TXADownloadService.BROADCAST_DOWNLOAD_PROGRESS)
        )
        LocalBroadcastManager.getInstance(this).registerReceiver(
            downloadErrorReceiver,
            android.content.IntentFilter(TXADownloadService.BROADCAST_DOWNLOAD_ERROR)
        )
        
        // Register for language change broadcasts
        LocalBroadcastManager.getInstance(this).registerReceiver(
            languageChangeReceiver,
            android.content.IntentFilter(TXATranslation.ACTION_LANGUAGE_CHANGED)
        )
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationLaunch(intent)
    }

    override fun onStart() {
        super.onStart()
        notifyDownloadServiceVisibility(isForeground = true)
    }

    override fun onStop() {
        notifyDownloadServiceVisibility(isForeground = false)
        super.onStop()
    }

    private fun setupUI() {
        binding.apply {
            // Set texts
            tvSettingsTitle.text = TXATranslation.txa("txademo_settings_title")
            tvAppInfoLabel.text = TXATranslation.txa("txademo_settings_app_info")
            tvVersionLabel.text = TXATranslation.txa("txademo_settings_version")
            tvAppSetIdLabel.text = TXATranslation.txa("txademo_settings_app_set_id")
            tvLanguageLabel.text = TXATranslation.txa("txademo_settings_language")
            btnChangeLanguage.text = TXATranslation.txa("txademo_settings_change_language")
            tvFileManagerLabel.text = TXATranslation.txa("txademo_settings_file_manager")
            btnOpenFileManager.text = TXATranslation.txa("txademo_settings_open_file_manager")
            tvUpdateLabel.text = TXATranslation.txa("txademo_settings_update")
            btnCheckUpdate.text = TXATranslation.txa("txademo_settings_check_update")

            // Set values
            tvVersionValue.text = BuildConfig.VERSION_NAME

            // Set listeners
            btnChangeLanguage.setOnClickListener {
                showLanguageDialog()
            }

            btnOpenFileManager.setOnClickListener {
                openFileManager()
            }

            btnCheckUpdate.setOnClickListener {
                checkForUpdate()
            }
        }
    }

    private fun openFileManager() {
        val intent = Intent(this, TXAFileManagerActivity::class.java)
        startActivity(intent)
    }

    private fun loadAppSetId() {
        lifecycleScope.launch {
            try {
                val info = appSetIdClient.appSetIdInfo.await()
                binding.tvAppSetIdValue.text = info.id
            } catch (e: Exception) {
                binding.tvAppSetIdValue.text = "N/A"
            }
        }
    }

    private fun showLanguageDialog() {
        lifecycleScope.launch {
            try {
                val locales = TXATranslation.getAvailableLocales()
                val localeNames = locales.map { TXATranslation.txa("txademo_lang_${it.tag}") }.toTypedArray()
                val currentLocale = TXAApp.getLocale(this@TXASettingsActivity)
                val currentIndex = locales.indexOfFirst { it.tag == currentLocale }

                AlertDialog.Builder(this@TXASettingsActivity)
                    .setTitle(TXATranslation.txa("txademo_settings_change_language"))
                    .setSingleChoiceItems(localeNames, currentIndex) { dialog, which ->
                        val selectedLocale = locales[which]
                        dialog.dismiss()
                        changeLanguage(selectedLocale.tag)
                    }
                    .setNegativeButton(TXATranslation.txa("txademo_action_cancel"), null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@TXASettingsActivity,
                    TXATranslation.txa("txademo_error_network"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun changeLanguage(locale: String) {
        lifecycleScope.launch {
            val progress = TXAProgressDialog(this@TXASettingsActivity)
            try {
                progress.show(
                    message = TXATranslation.txa("txademo_splash_downloading_language"),
                    cancellable = false
                )

                // Use new runtime language change without restart
                TXATranslation.setLanguage(this@TXASettingsActivity, locale)
                
                // Update app locale for future activities
                TXAApp.setLocale(this@TXASettingsActivity, locale)
                
                Toast.makeText(
                    this@TXASettingsActivity,
                    TXATranslation.txa("txademo_language_change_success"),
                    Toast.LENGTH_SHORT
                ).show()
                
            } catch (e: Exception) {
                TXALog.e("SettingsActivity", "Language change failed", e)
                Toast.makeText(
                    this@TXASettingsActivity,
                    String.format(
                        TXATranslation.txa("txademo_language_change_failed"),
                        e.message ?: "unknown"
                    ),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                progress.dismiss()
            }
        }
    }

    private fun checkForUpdate() {
        lifecycleScope.launch {
            val progress = TXAProgressDialog(this@TXASettingsActivity)
            try {
                progress.show(
                    message = TXATranslation.txa("txademo_update_checking"),
                    cancellable = false
                )

                val result = TXAUpdateManager.checkForUpdate(this@TXASettingsActivity)
                when (result) {
                    is TXAUpdateManager.UpdateCheckResult.UpdateAvailable -> {
                        showUpdateDialog(result.updateInfo)
                    }
                    is TXAUpdateManager.UpdateCheckResult.NoUpdate -> {
                        Toast.makeText(
                            this@TXASettingsActivity,
                            TXATranslation.txa("txademo_update_not_available"),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is TXAUpdateManager.UpdateCheckResult.Error -> {
                        Toast.makeText(
                            this@TXASettingsActivity,
                            "${TXATranslation.txa("txademo_error_update_check_failed")}: ${result.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                TXALog.e("SettingsActivity", "Update check failed", e)
                Toast.makeText(
                    this@TXASettingsActivity,
                    "Update check failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                progress.dismiss()
            }
        }
    }

    private fun handleNotificationLaunch(intent: Intent?) {
        val sourceIntent = intent
        val launchedFromNotification = sourceIntent?.getBooleanExtra(EXTRA_LAUNCH_FROM_UPDATE_NOTIFICATION, false) ?: false
        val autoStartDownload = sourceIntent?.getBooleanExtra(EXTRA_AUTO_START_DOWNLOAD, false) ?: false
        val updateInfo = sourceIntent?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getSerializableExtra(
                    EXTRA_UPDATE_INFO,
                    TXAUpdateManager.UpdateInfo::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                it.getSerializableExtra(EXTRA_UPDATE_INFO) as? TXAUpdateManager.UpdateInfo
            }
        }

        if (sourceIntent != null && launchedFromNotification && updateInfo != null) {
            showUpdateDialog(updateInfo, forceAutoDownload = autoStartDownload)
            sourceIntent.apply {
                removeExtra(EXTRA_LAUNCH_FROM_UPDATE_NOTIFICATION)
                removeExtra(EXTRA_AUTO_START_DOWNLOAD)
                removeExtra(EXTRA_UPDATE_INFO)
            }
        }
    }

    private fun showUpdateDialog(updateInfo: TXAUpdateManager.UpdateInfo, forceAutoDownload: Boolean = false) {
        val changelogDialog = TXAChangelogDialog(this)
        
        // Show changelog with WebView and download button
        changelogDialog.show(
            title = TXATranslation.txa("txademo_update_available"),
            changelog = updateInfo.changelog,
            versionName = updateInfo.versionName,
            updatedAt = updateInfo.updatedAt,
            showDownloadButton = true,
            onDownloadClick = { startDownload(updateInfo) }
        )

        if (forceAutoDownload) {
            startDownload(updateInfo)
        }
    }

    private fun startDownload(updateInfo: TXAUpdateManager.UpdateInfo) {
        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
                return
            }
        }

        // Hiển modal ngay lập tức để user thấy tiến trình trong app
        if (downloadProgressDialog == null) {
            showDownloadProgressDialog(initialProgress = 0)
        } else {
            downloadProgressDialog?.update(
                message = TXATranslation.txa("txademo_update_downloading"),
                indeterminate = true,
                progressPercent = 0
            )
        }

        lifecycleScope.launch {
            try {
                val resolvedUrl = TXADownloadUrlResolver.resolveUrl(updateInfo.downloadUrl)
                    ?: throw IllegalArgumentException(
                        TXATranslation.txa("txademo_error_download_url_unsupported")
                    )
                val resolvedInfo = updateInfo.copy(downloadUrl = resolvedUrl)

                TXAUpdateManager.startBackgroundDownload(this@TXASettingsActivity, resolvedInfo)

                Toast.makeText(
                    this@TXASettingsActivity,
                    TXATranslation.txa("txademo_download_background_starting"),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                handleDownloadUrlFailure(e)
            }
        }
    }

    private fun checkAndShowDownloadDialog() {
        if (TXAUpdateManager.isDownloadActive(this)) {
            val progress = TXAUpdateManager.getDownloadProgress(this)
            showDownloadProgressDialog(progress)
        }
    }

    private fun showDownloadProgressDialog(initialProgress: Int) {
        downloadProgressDialog = TXAProgressDialog(this).also { dialog ->
            dialog.show(
                message = TXATranslation.txa("txademo_update_downloading"),
                cancellable = false,
                indeterminate = initialProgress == 0
            )
            // Set initial progress if not indeterminate
            if (initialProgress > 0) {
                dialog.update(progressPercent = initialProgress)
            }
        }
    }

    private fun notifyDownloadServiceVisibility(isForeground: Boolean) {
        if (!TXAUpdateManager.isDownloadActive(this)) return
        val action = if (isForeground) {
            TXADownloadService.ACTION_APP_FOREGROUND
        } else {
            TXADownloadService.ACTION_APP_BACKGROUND
        }
        val intent = Intent(this, TXADownloadService::class.java).apply {
            this.action = action
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun handleDownloadUrlFailure(error: Exception) {
        TXALog.e("SettingsActivity", "Failed to resolve download URL", error)
        downloadProgressDialog?.dismiss()
        downloadProgressDialog = null

        val rawMessage = error.message
        val localizedMessage = when {
            error is IllegalArgumentException -> rawMessage
            rawMessage.isNullOrBlank() -> TXATranslation.txa("txademo_error_resolver_failed")
            else -> rawMessage
        } ?: TXATranslation.txa("txademo_error_resolver_failed")

        showDownloadErrorNotification(localizedMessage)

        Toast.makeText(
            this,
            localizedMessage,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showDownloadErrorNotification(message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureDownloadErrorChannel(notificationManager)

        val notification = NotificationCompat.Builder(this, DOWNLOAD_ERROR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(TXATranslation.txa("txademo_download_failed"))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(DOWNLOAD_ERROR_NOTIFICATION_ID, notification)
    }

    private fun ensureDownloadErrorChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        if (manager.getNotificationChannel(DOWNLOAD_ERROR_CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            DOWNLOAD_ERROR_CHANNEL_ID,
            TXATranslation.txa("txademo_download_failed"),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = TXATranslation.txa("txademo_error_resolver_failed")
        }

        manager.createNotificationChannel(channel)
    }

    private val languageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            val locale = intent?.getStringExtra("locale") ?: return
            TXALog.i("SettingsActivity", "Language changed to: $locale")
            
            // Refresh all UI texts
            runOnUiThread {
                setupUI()
                // Update dialog if it's showing
                downloadProgressDialog?.let { dialog ->
                    dialog.update(
                        message = TXATranslation.txa("txademo_update_downloading"),
                        etaText = dialog.getBinding()?.tvEta?.text?.toString() ?: "--:--"
                    )
                }
            }
        }
    }

    private val downloadProgressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            val progress = intent?.getIntExtra(TXADownloadService.EXTRA_PROGRESS, 0) ?: 0
            val downloadedBytes = intent?.getLongExtra(TXADownloadService.EXTRA_DOWNLOADED_BYTES, 0L) ?: 0L
            val totalBytes = intent?.getLongExtra(TXADownloadService.EXTRA_TOTAL_BYTES, 0L) ?: 0L
            val speedBps = intent?.getLongExtra(TXADownloadService.EXTRA_SPEED_BPS, 0L) ?: 0L
            val etaSeconds = intent?.getLongExtra(TXADownloadService.EXTRA_ETA_SECONDS, 0L) ?: 0L
            
            runOnUiThread {
                downloadProgressDialog?.let { dialog ->
                    dialog.update(
                        progressPercent = progress,
                        message = TXATranslation.txa("txademo_update_downloading"),
                        sizeText = "${TXAFormat.formatBytes(downloadedBytes)} / ${if (totalBytes > 0) TXAFormat.formatBytes(totalBytes) else "Unknown"}",
                        speedText = TXAFormat.formatSpeed(speedBps),
                        etaText = if (etaSeconds > 0) TXAFormat.formatETA(etaSeconds) else "--:--",
                        percentText = "$progress%"
                    )
                }
            }
            
            TXALog.i("SettingsActivity", "Download progress: $progress% ($downloadedBytes/$totalBytes bytes)")
        }
    }
    
    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            val filePath = intent?.getStringExtra(TXADownloadService.EXTRA_FILE_PATH) ?: return
            
            runOnUiThread {
                downloadProgressDialog?.let { dialog ->
                    dialog.showCompleted {
                        // Install the downloaded APK
                        installApk(filePath)
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                    this,
                    "Notification permission granted",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "Notification permission denied. Download progress won't be shown.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showInstallDialog(apkFile: File) {
        AlertDialog.Builder(this)
            .setTitle(TXATranslation.txa("txademo_update_download_complete"))
            .setMessage(TXATranslation.txa("txademo_update_install_prompt"))
            .setPositiveButton(TXATranslation.txa("txademo_update_install_now")) { _, _ ->
                val success = TXAInstall.installApk(this, apkFile)
                if (!success) {
                    Toast.makeText(
                        this,
                        TXATranslation.txa("txademo_error_install_failed"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(TXATranslation.txa("txademo_update_later"), null)
            .show()
    }
    
    private fun installApk(filePath: String) {
        val apkFile = File(filePath)
        if (apkFile.exists()) {
            val success = TXAInstall.installApk(this, apkFile)
            if (!success) {
                Toast.makeText(
                    this,
                    TXATranslation.txa("txademo_error_install_failed"),
                    Toast.LENGTH_SHORT
                ).show()
            }
            // Dismiss dialog after attempting install
            downloadProgressDialog?.dismiss()
            downloadProgressDialog = null
        } else {
            Toast.makeText(
                this,
                TXATranslation.txa("txademo_file_not_found"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun checkDownloadCompletionState() {
        val prefs = getSharedPreferences(TXADownloadService.PREFS_NAME, Context.MODE_PRIVATE)
        val isDownloading = prefs.getBoolean(TXADownloadService.KEY_IS_DOWNLOADING, false)
        val filePath = prefs.getString(TXADownloadService.KEY_DOWNLOAD_FILE_PATH, null)
        
        // If download is not active but we have a file path, it means download completed
        if (!isDownloading && filePath != null) {
            val file = File(filePath)
            if (file.exists()) {
                // Show modal with install button if not already showing
                if (downloadProgressDialog == null) {
                    showDownloadProgressDialog(100) // Show at 100% completion
                    downloadProgressDialog?.showCompleted {
                        installApk(filePath)
                    }
                } else {
                    downloadProgressDialog?.showCompleted {
                        installApk(filePath)
                    }
                }
            }
        } else if (isDownloading) {
            // If download is still active, ensure modal is showing
            val progress = prefs.getInt(TXADownloadService.KEY_DOWNLOAD_PROGRESS, 0)
            if (downloadProgressDialog == null) {
                showDownloadProgressDialog(progress)
            }
        }
    }
    override fun onResume() {
        super.onResume()
        
        // Check if download completed while app was backgrounded
        checkDownloadCompletionState()
        
        // Register download complete receiver
        LocalBroadcastManager.getInstance(this).registerReceiver(
            downloadCompleteReceiver,
            android.content.IntentFilter(TXADownloadService.BROADCAST_DOWNLOAD_COMPLETE)
        )
    }
    
    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(downloadProgressReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(downloadCompleteReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(languageChangeReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(downloadErrorReceiver)
        downloadProgressDialog?.dismiss()
    }
}
