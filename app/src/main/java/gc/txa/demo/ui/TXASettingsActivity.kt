package gc.txa.demo.ui

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.appset.AppSet
import com.google.android.gms.appset.AppSetIdClient
import gc.txa.demo.BuildConfig
import gc.txa.demo.TXAApp
import gc.txa.demo.core.TXAFormat
import gc.txa.demo.core.TXAHttp
import gc.txa.demo.core.TXATranslation
import gc.txa.demo.core.TXALog
import gc.txa.demo.databinding.ActivityTxaSettingsBinding
import gc.txa.demo.ui.components.TXAProgressDialog
import gc.txa.demo.update.TXADownload
import gc.txa.demo.update.TXADownloadUrlResolver
import gc.txa.demo.update.TXAInstall
import gc.txa.demo.update.TXAUpdateManager
import gc.txa.demo.download.TXADownloadService
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import android.content.BroadcastReceiver
import android.content.IntentFilter

class TXASettingsActivity : AppCompatActivity() {

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

                val result = TXATranslation.syncIfNewer(this@TXASettingsActivity, locale)
                
                when (result) {
                    is TXATranslation.SyncResult.Success,
                    is TXATranslation.SyncResult.CachedUsed -> {
                        TXAApp.setLocale(this@TXASettingsActivity, locale)
                        
                        // Restart activity
                        val intent = intent
                        finish()
                        startActivity(intent)
                    }
                    is TXATranslation.SyncResult.Failed -> {
                        Toast.makeText(
                            this@TXASettingsActivity,
                            "${TXATranslation.txa("txademo_error_network")}: ${result.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                TXALog.e("SettingsActivity", "Language change failed", e)
                Toast.makeText(
                    this@TXASettingsActivity,
                    "Language change failed: ${e.message}",
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

    private fun showUpdateDialog(updateInfo: TXAUpdateManager.UpdateInfo) {
        val message = """
            ${TXATranslation.txa("txademo_update_new_version")}: ${updateInfo.versionName}
            ${TXATranslation.txa("txademo_update_current_version")}: ${TXAUpdateManager.getCurrentVersion()}
            
            ${TXATranslation.txa("txademo_update_changelog")}:
            ${updateInfo.changelog}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle(TXATranslation.txa("txademo_update_available"))
            .setMessage(message)
            .setPositiveButton(TXATranslation.txa("txademo_update_download_now")) { _, _ ->
                startDownload(updateInfo)
            }
            .setNegativeButton(TXATranslation.txa("txademo_update_later"), null)
            .show()
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

        // Start background download
        TXAUpdateManager.startBackgroundDownload(this, updateInfo)
        
        Toast.makeText(
            this,
            TXATranslation.txa("txademo_download_background_starting"),
            Toast.LENGTH_SHORT
        ).show()
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

    private val downloadProgressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            val progress = intent?.getIntExtra(TXADownloadService.EXTRA_PROGRESS, 0) ?: 0
            val downloadedBytes = intent?.getLongExtra(TXADownloadService.EXTRA_DOWNLOADED_BYTES, 0L) ?: 0L
            val totalBytes = intent?.getLongExtra(TXADownloadService.EXTRA_TOTAL_BYTES, 0L) ?: 0L
            
            runOnUiThread {
                downloadProgressDialog?.let { dialog ->
                    dialog.update(
                        progressPercent = progress,
                        message = "${TXATranslation.txa("txademo_update_downloading")} - $progress%"
                    )
                }
            }
            
            TXALog.i("SettingsActivity", "Download progress: $progress% ($downloadedBytes/$totalBytes bytes)")
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

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(downloadProgressReceiver)
        downloadProgressDialog?.dismiss()
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

    override fun onDestroy() {
        super.onDestroy()
        downloadProgressDialog?.dismiss()
    }
}
