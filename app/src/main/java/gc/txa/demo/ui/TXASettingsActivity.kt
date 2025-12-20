package gc.txa.demo.ui

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.appset.AppSet
import com.google.android.gms.appset.AppSetIdClient
import gc.txa.demo.BuildConfig
import gc.txa.demo.TXAApp
import gc.txa.demo.core.TXAFormat
import gc.txa.demo.core.TXAHttp
import gc.txa.demo.core.TXATranslation
import gc.txa.demo.databinding.ActivityTxaSettingsBinding
import gc.txa.demo.update.TXADownload
import gc.txa.demo.update.TXADownloadUrlResolver
import gc.txa.demo.update.TXAInstall
import gc.txa.demo.update.TXAUpdateManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

class TXASettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTxaSettingsBinding
    private lateinit var appSetIdClient: AppSetIdClient
    private var progressDialog: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTxaSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appSetIdClient = AppSet.getClient(this)
        
        setupUI()
        loadAppSetId()
    }

    private fun setupUI() {
        binding.apply {
            // Set texts
            tvSettingsTitle.text = TXATranslation.txa("settings_title")
            tvAppInfoLabel.text = TXATranslation.txa("settings_app_info")
            tvVersionLabel.text = TXATranslation.txa("settings_version")
            tvAppSetIdLabel.text = TXATranslation.txa("settings_app_set_id")
            tvLanguageLabel.text = TXATranslation.txa("settings_language")
            btnChangeLanguage.text = TXATranslation.txa("settings_change_language")
            tvUpdateLabel.text = TXATranslation.txa("settings_update")
            btnCheckUpdate.text = TXATranslation.txa("settings_check_update")

            // Set values
            tvVersionValue.text = BuildConfig.VERSION_NAME

            // Set listeners
            btnChangeLanguage.setOnClickListener {
                showLanguageDialog()
            }

            btnCheckUpdate.setOnClickListener {
                checkForUpdate()
            }
        }
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
                val localeNames = locales.map { TXATranslation.txa("lang_${it.tag}") }.toTypedArray()
                val currentLocale = TXAApp.getLocale(this@TXASettingsActivity)
                val currentIndex = locales.indexOfFirst { it.tag == currentLocale }

                AlertDialog.Builder(this@TXASettingsActivity)
                    .setTitle(TXATranslation.txa("settings_change_language"))
                    .setSingleChoiceItems(localeNames, currentIndex) { dialog, which ->
                        val selectedLocale = locales[which]
                        dialog.dismiss()
                        changeLanguage(selectedLocale.tag)
                    }
                    .setNegativeButton(TXATranslation.txa("action_cancel"), null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@TXASettingsActivity,
                    TXATranslation.txa("error_network"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun changeLanguage(locale: String) {
        lifecycleScope.launch {
            val progress = ProgressDialog(this@TXASettingsActivity)
            progress.setMessage(TXATranslation.txa("splash_downloading_language"))
            progress.setCancelable(false)
            progress.show()

            try {
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
                            "${TXATranslation.txa("error_network")}: ${result.error}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } finally {
                progress.dismiss()
            }
        }
    }

    private fun checkForUpdate() {
        lifecycleScope.launch {
            val progress = ProgressDialog(this@TXASettingsActivity)
            progress.setMessage(TXATranslation.txa("update_checking"))
            progress.setCancelable(false)
            progress.show()

            try {
                val result = TXAUpdateManager.checkForUpdate(this@TXASettingsActivity)
                progress.dismiss()

                when (result) {
                    is TXAUpdateManager.UpdateCheckResult.UpdateAvailable -> {
                        showUpdateDialog(result.updateInfo)
                    }
                    is TXAUpdateManager.UpdateCheckResult.NoUpdate -> {
                        Toast.makeText(
                            this@TXASettingsActivity,
                            TXATranslation.txa("update_not_available"),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is TXAUpdateManager.UpdateCheckResult.Error -> {
                        Toast.makeText(
                            this@TXASettingsActivity,
                            "${TXATranslation.txa("error_update_check_failed")}: ${result.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                progress.dismiss()
                Toast.makeText(
                    this@TXASettingsActivity,
                    TXATranslation.txa("error_network"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showUpdateDialog(updateInfo: TXAUpdateManager.UpdateInfo) {
        val message = """
            ${TXATranslation.txa("update_new_version")}: ${updateInfo.versionName}
            ${TXATranslation.txa("update_current_version")}: ${TXAUpdateManager.getCurrentVersion()}
            
            ${TXATranslation.txa("update_changelog")}:
            ${updateInfo.changelog}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle(TXATranslation.txa("update_available"))
            .setMessage(message)
            .setPositiveButton(TXATranslation.txa("update_download_now")) { _, _ ->
                startDownload(updateInfo)
            }
            .setNegativeButton(TXATranslation.txa("update_later"), null)
            .show()
    }

    private fun startDownload(updateInfo: TXAUpdateManager.UpdateInfo) {
        lifecycleScope.launch {
            try {
                // Resolve URL first
                val resolvedUrl = TXADownloadUrlResolver.resolveUrl(updateInfo.downloadUrl)
                
                if (resolvedUrl == null) {
                    Toast.makeText(
                        this@TXASettingsActivity,
                        TXATranslation.txa("error_resolver_failed"),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                TXAHttp.logInfo(this@TXASettingsActivity, "Download", "Resolved URL: $resolvedUrl")

                // Prepare download location
                val downloadDir = File("/storage/emulated/0/Download/TXADEMO")
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }
                val apkFile = File(downloadDir, "TXA_${updateInfo.versionName}.apk")

                // Show progress dialog
                progressDialog = ProgressDialog(this@TXASettingsActivity).apply {
                    setTitle(TXATranslation.txa("update_downloading"))
                    setMessage(TXATranslation.txa("msg_please_wait"))
                    setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
                    setCancelable(false)
                    max = 100
                    show()
                }

                // Start download
                TXADownload.downloadFile(resolvedUrl, apkFile).collect { progress ->
                    when (progress) {
                        is TXADownload.DownloadProgress.Started -> {
                            progressDialog?.setMessage(TXATranslation.txa("update_downloading"))
                        }
                        is TXADownload.DownloadProgress.Downloading -> {
                            val percent = (progress.downloadedBytes * 100 / progress.totalBytes).toInt()
                            progressDialog?.progress = percent
                            
                            val message = """
                                ${TXAFormat.formatBytes(progress.downloadedBytes)} / ${TXAFormat.formatBytes(progress.totalBytes)}
                                ${TXATranslation.txa("update_download_speed")}: ${TXAFormat.formatSpeed(progress.speed)}
                                ${TXATranslation.txa("update_download_eta")}: ${TXAFormat.formatETA(progress.eta)}
                            """.trimIndent()
                            
                            progressDialog?.setMessage(message)
                        }
                        is TXADownload.DownloadProgress.Completed -> {
                            progressDialog?.dismiss()
                            showInstallDialog(progress.file)
                        }
                        is TXADownload.DownloadProgress.Failed -> {
                            progressDialog?.dismiss()
                            Toast.makeText(
                                this@TXASettingsActivity,
                                "${TXATranslation.txa("error_download_failed")}: ${progress.error}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                progressDialog?.dismiss()
                TXAHttp.logError(this@TXASettingsActivity, "Download", e)
                Toast.makeText(
                    this@TXASettingsActivity,
                    "${TXATranslation.txa("error_download_failed")}: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showInstallDialog(apkFile: File) {
        AlertDialog.Builder(this)
            .setTitle(TXATranslation.txa("update_download_complete"))
            .setMessage(TXATranslation.txa("update_install_prompt"))
            .setPositiveButton(TXATranslation.txa("update_install_now")) { _, _ ->
                val success = TXAInstall.installApk(this, apkFile)
                if (!success) {
                    Toast.makeText(
                        this,
                        TXATranslation.txa("error_install_failed"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(TXATranslation.txa("update_later"), null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        progressDialog?.dismiss()
    }
}
