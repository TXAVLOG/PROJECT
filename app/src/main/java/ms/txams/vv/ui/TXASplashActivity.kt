package ms.txams.vv.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ms.txams.vv.R
import ms.txams.vv.core.TXAApp
import ms.txams.vv.core.TXAFormat
import ms.txams.vv.core.TXALogger
import ms.txams.vv.core.TXATranslation
import ms.txams.vv.data.manager.TXAAudioInjectionManager
import ms.txams.vv.databinding.ActivitySplashBinding
import ms.txams.vv.update.TXAInstall
import ms.txams.vv.update.TXAUpdateManager
import ms.txams.vv.update.TXAUpdatePhase
import ms.txams.vv.update.UpdateCheckResult
import ms.txams.vv.update.UpdateInfo
import javax.inject.Inject

/**
 * TXA Splash Activity
 * 
 * Flow:
 * 1. Version Check
 * 2. Integrity Check
 * 3. Update Check (with Progress)
 * 4. Navigate to Main
 * 
 * @author TXA - fb.com/vlog.txa.2311 - txavlog7@gmail.com
 */
@AndroidEntryPoint
class TXASplashActivity : BaseActivity() {

    @Inject lateinit var audioInjectionManager: TXAAudioInjectionManager
    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (!TXAApp.isDeviceSupported()) {
            showUnsupportedDialogAndExit()
            return
        }
        
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        try {
            binding.tvVersion.text = "v${ms.txams.vv.BuildConfig.VERSION_NAME}"
        } catch (e: Exception) { }
        
        TXALogger.appI("TXASplashActivity started")

        if (!performIntegrityCheck()) {
            TXALogger.appE("Integrity check failed")
            showIntegrityErrorDialog()
            return
        }

        if (!ms.txams.vv.util.TXAPermissionManager.hasAllFilesAccess(this)) {
            showPermissionExplanationDialog()
        } else {
            startInitSequence()
        }
    }

    // ... (Keep existing minor dialog methods or permission methods) ...
    // Note: I'm writing the full file content to ensure clean structure

    private fun showPermissionExplanationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(TXATranslation.txa("txamusic_permission_all_files_title"))
            .setMessage(TXATranslation.txa("txamusic_permission_all_files_message"))
            .setPositiveButton(TXATranslation.txa("txamusic_action_ok")) { _, _ ->
                ms.txams.vv.util.TXAPermissionManager.requestAllFilesAccess(this, REQ_ALL_FILES)
            }
            .setNegativeButton(TXATranslation.txa("txamusic_action_cancel")) { _, _ ->
                startInitSequence() 
            }
            .setCancelable(false)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ALL_FILES) {
            TXALogger.init(this)
            startInitSequence()
        }
    }

    companion object {
        private const val REQ_ALL_FILES = 1001
    }

    private fun showUnsupportedDialogAndExit() {
        try {
            AlertDialog.Builder(this)
                .setTitle("Not Supported")
                .setMessage(TXAApp.getUnsupportedMessage())
                .setPositiveButton("OK") { _, _ -> finishAffinity() }
                .setCancelable(false)
                .show()
        } catch (e: Exception) {
            finishAffinity()
        }
    }

    private fun performIntegrityCheck(): Boolean {
        updateStatus(TXATranslation.txa("txamusic_splash_checking_permissions"))
        return try {
            val integrityResult = audioInjectionManager.getIntegrityDetail()
            integrityResult.isValid
        } catch (e: Exception) {
            false
        }
    }

    private fun showIntegrityErrorDialog() {
        try {
            MaterialAlertDialogBuilder(this)
                .setTitle(TXATranslation.txa("txamusic_msg_error"))
                .setMessage(TXATranslation.txa("txamusic_integrity_check_failed"))
                .setPositiveButton(TXATranslation.txa("txamusic_action_ok")) { _, _ -> finishAffinity() }
                .setCancelable(false)
                .show()
        } catch (e: Exception) {
            finishAffinity()
        }
    }

    private fun startInitSequence() {
        lifecycleScope.launch {
            try {
                // Initial State
                updateStatus(TXATranslation.txa("txamusic_splash_initializing"))
                binding.progressBar?.visibility = View.VISIBLE
                binding.progressBar?.progress = 0
                binding.tvProgress?.text = "0%"

                // 1. Simlulate Integrity/Init (0-30%)
                for (i in 1..30 step 5) {
                    delay(50)
                    updateProgress(i)
                }
                
                // 2. Check Updates (30-80%)
                updateStatus(TXATranslation.txa("txamusic_update_check_title"))
                
                val updateResult = withContext(Dispatchers.IO) {
                    try {
                        TXAUpdateManager.checkForUpdate(applicationContext)
                    } catch (e: Exception) {
                        null
                    }
                }
                
                updateProgress(80)

                // 3. Handle Update Result
                if (updateResult is UpdateCheckResult.UpdateAvailable) {
                    updateStatus(TXATranslation.txa("txamusic_update_available"))
                    updateProgress(100)
                    
                    showUpdateDialog(updateResult.updateInfo)
                    return@launch // Stop here, dialog handles navigation
                }

                // 4. Finish (80-100%)
                for (i in 81..100 step 5) {
                    delay(30)
                    updateProgress(i)
                }

                updateStatus(TXATranslation.txa("txamusic_splash_entering_app"))
                delay(300)
                navigateToMain()

            } catch (e: Exception) {
                TXALogger.appE("Init sequence failed", e)
                navigateToMain()
            }
        }
    }

    private fun updateProgress(progress: Int) {
        binding.progressBar?.progress = progress
        binding.tvProgress?.text = "$progress%"
    }

    private fun updateStatus(message: String) {
        try {
            binding.tvStatus.text = message
        } catch (e: Exception) { }
    }

    private fun navigateToMain() {
        try {
            startActivity(Intent(this@TXASplashActivity, TXAMainActivity::class.java))
            finish()
        } catch (e: Exception) {
            finishAffinity()
        }
    }

    // --- Update Dialog Logic (Duplicate from Settings for now) ---

    private fun showUpdateDialog(updateInfo: UpdateInfo) {
        // Build Custom View
        val dialogView = layoutInflater.inflate(R.layout.txa_dialog_update_changelog, null)
        
        val tvVersion = dialogView.findViewById<TextView>(R.id.tvUpdateVersion)
        val tvSize = dialogView.findViewById<TextView>(R.id.tvUpdateSize)
        val tvDate = dialogView.findViewById<TextView>(R.id.tvUpdateDate)
        val webView = dialogView.findViewById<WebView>(R.id.webViewChangelog)
        val btnUpdate = dialogView.findViewById<View>(R.id.btnUpdate)
        val btnCancel = dialogView.findViewById<View>(R.id.btnCancel)
        val progressChangelog = dialogView.findViewById<View>(R.id.progressChangelog)

        // Set Texts
        tvVersion.text = TXATranslation.txa("txamusic_update_version").format(updateInfo.versionName)
        tvSize.text = TXATranslation.txa("txamusic_update_size").format(TXAFormat.formatBytes(updateInfo.downloadSizeBytes))
        tvDate.text = TXATranslation.txa("txamusic_update_release_date").format(updateInfo.releaseDate)

        // Load Changelog
        progressChangelog.visibility = View.VISIBLE
        webView.settings.defaultTextEncodingName = "utf-8"
        val htmlContent = """
            <html>
            <body style="font-family: sans-serif; color: #e0e0e0; background-color: #1a1a2e; padding: 8px;">
            ${updateInfo.changelog}
            </body></html>
        """.trimIndent()
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
        progressChangelog.visibility = View.GONE // Simple hide, can use WebViewClient for better sync

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(!updateInfo.mandatory)
            .create()

        btnUpdate.setOnClickListener {
            dialog.dismiss()
            startUpdateDownload(updateInfo)
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
            if (updateInfo.mandatory) {
                finishAffinity() // Exit if mandatory
            } else {
                navigateToMain()
            }
        }
        
        dialog.setOnCancelListener {
             if (!updateInfo.mandatory) navigateToMain() else finishAffinity()
        }

        dialog.show()
    }

    private fun startUpdateDownload(updateInfo: UpdateInfo) {
        // Show Progress Dialog
        val dialogView = layoutInflater.inflate(R.layout.txa_dialog_download_progress, null)
        val cpProgress = dialogView.findViewById<CircularProgressIndicator>(R.id.cpDownloadProgress)
        val tvPercent = dialogView.findViewById<TextView>(R.id.tvProgressPercent)
        val progressBar = dialogView.findViewById<LinearProgressIndicator>(R.id.progressBar)
        val tvSizeInfo = dialogView.findViewById<TextView>(R.id.tvDownloadSizeInfo)
        val tvSpeed = dialogView.findViewById<TextView>(R.id.tvDownloadSpeed)
        val tvEta = dialogView.findViewById<TextView>(R.id.tvDownloadEta)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tvDownloadStatus)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.show()

        lifecycleScope.launch {
            TXAUpdateManager.downloadUpdate(this@TXASplashActivity, updateInfo)
                .collect { phase ->
                    when (phase) {
                        is TXAUpdatePhase.Starting -> {
                            tvStatus.text = TXATranslation.txa("txamusic_update_status_idle")
                        }
                        is TXAUpdatePhase.Resolving -> {
                            tvStatus.text = TXATranslation.txa("txamusic_update_status_resolving")
                        }
                        is TXAUpdatePhase.Connecting -> {
                            tvStatus.text = "Connecting..."
                        }
                        is TXAUpdatePhase.Downloading -> {
                            cpProgress.progress = phase.progressPercent
                            progressBar.progress = phase.progressPercent
                            tvPercent.text = "${phase.progressPercent}%"
                            
                            tvSizeInfo.text = TXAFormat.formatProgressDetail(phase.downloadedBytes, phase.totalBytes)
                            tvSpeed.text = TXAFormat.formatSpeed(phase.speed.toLong())
                            tvEta.text = TXAFormat.formatTimeRemaining(phase.etaSeconds.toLong() * 1000)
                            
                            tvStatus.text = "Downloading..."
                        }
                        is TXAUpdatePhase.Validating -> {
                            tvStatus.text = "Validating APK..."
                            cpProgress.isIndeterminate = true
                        }
                        is TXAUpdatePhase.ReadyToInstall -> {
                            dialog.dismiss()
                            TXAInstall.installApk(this@TXASplashActivity, phase.apkFile)
                            finish() // Close Splash
                        }
                        is TXAUpdatePhase.Error -> {
                             dialog.dismiss()
                             showErrorDialog(phase.message, updateInfo.mandatory)
                        }
                        is TXAUpdatePhase.Retrying -> {
                            tvStatus.text = "${TXATranslation.txa("txamusic_download_retrying")} (${phase.attempt}/${phase.maxAttempts})"
                        }
                        is TXAUpdatePhase.ChecksumMismatch -> {
                            dialog.dismiss()
                            // Show integrity error dialog - app needs reinstall
                            MaterialAlertDialogBuilder(this@TXASplashActivity)
                                .setTitle(TXATranslation.txa("txamusic_msg_error"))
                                .setMessage(TXATranslation.txa("txamusic_integrity_check_failed"))
                                .setPositiveButton(TXATranslation.txa("txamusic_action_ok")) { _, _ ->
                                    finishAffinity()
                                }
                                .setCancelable(false)
                                .show()
                        }
                    }
                }
        }
    }
    
    private fun showErrorDialog(message: String, mandatory: Boolean) {
         MaterialAlertDialogBuilder(this)
            .setTitle(TXATranslation.txa("txamusic_msg_error"))
            .setMessage(message)
            .setPositiveButton(TXATranslation.txa("txamusic_action_retry")) { _, _ ->
                 startInitSequence() // Retry whole sequence
            }
            .setNegativeButton(TXATranslation.txa("txamusic_action_cancel")) { _, _ ->
                if (mandatory) finishAffinity() else navigateToMain()
            }
            .show()
    }
}
