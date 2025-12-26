package ms.txams.vv.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ms.txams.vv.core.TXAFormat
import ms.txams.vv.core.TXATranslation
import ms.txams.vv.data.manager.TXAAudioInjectionManager
import ms.txams.vv.databinding.ActivitySplashBinding
import javax.inject.Inject

@AndroidEntryPoint
class TXASplashActivity : AppCompatActivity() {

    @Inject lateinit var audioInjectionManager: TXAAudioInjectionManager
    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Step 1: Version Filtering - Check Android version
        if (Build.VERSION.SDK_INT < 33) {
            showIncompatibleDialog()
            return
        }

        // Step 2: Integrity Check - Verify intro_txa.mp3
        if (!performIntegrityCheck()) {
            showIntegrityErrorDialog()
            return
        }

        // Step 3: Start Loading Sequence
        startLoadingSequence()
    }

    /**
     * Version Filtering: Show Material 3 Dialog for incompatible Android versions
     * Key: txamusic_app_incompatible
     */
    private fun showIncompatibleDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(TXATranslation.txa("txamusic_msg_warning"))
            .setMessage(TXATranslation.txa("txamusic_app_incompatible"))
            .setPositiveButton(TXATranslation.txa("txamusic_action_ok")) { _, _ ->
                finishAffinity()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Integrity Check: Verify assets/intro_txa.mp3 exists and is valid
     */
    private fun performIntegrityCheck(): Boolean {
        updateStatus(TXATranslation.txa("txamusic_splash_checking_permissions"))
        
        val integrityResult = audioInjectionManager.getIntegrityDetail()
        
        if (!integrityResult.isValid) {
            // Log details for debugging
            android.util.Log.e("TXASplash", "Integrity check failed: ${integrityResult.errorMessage}")
            android.util.Log.e("TXASplash", "File exists: ${integrityResult.exists}, Size: ${integrityResult.size}, MD5: ${integrityResult.md5Hash}")
            return false
        }
        
        return true
    }

    /**
     * Show dialog when integrity check fails
     */
    private fun showIntegrityErrorDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(TXATranslation.txa("txamusic_msg_error"))
            .setMessage(TXATranslation.txa("txamusic_integrity_check_failed"))
            .setPositiveButton(TXATranslation.txa("txamusic_action_ok")) { _, _ ->
                finishAffinity()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Main Loading Sequence with Language Loading and Cache Check
     */
    private fun startLoadingSequence() {
        lifecycleScope.launch {
            // Show progress bar
            binding.progressBar?.visibility = View.VISIBLE
            
            val result = TXATranslation.loadLanguageWithProgress("en") { current, total, message ->
                runOnUiThread {
                    updateStatus(message)
                    updateProgress(current, total)
                }
            }
            
            when (result) {
                is TXATranslation.LoadResult.Success -> {
                    if (result.usedFallback) {
                        // Show fallback notification briefly
                        updateStatus(TXATranslation.txa("txamusic_splash_connection_error"))
                        delay(1500)
                    }
                    
                    updateStatus(TXATranslation.txa("txamusic_splash_entering_app"))
                    delay(500)
                    
                    // Navigate to main activity
                    navigateToMain()
                }
                is TXATranslation.LoadResult.Error -> {
                    updateStatus("${TXATranslation.txa("txamusic_msg_error")}: ${result.message}")
                    
                    // Show error dialog with retry option
                    showErrorDialog(result.message)
                }
            }
        }
    }

    /**
     * Update status text
     */
    private fun updateStatus(message: String) {
        binding.tvStatus.text = message
    }

    /**
     * Update progress bar and percentage text
     */
    private fun updateProgress(current: Long, total: Long) {
        binding.progressBar?.let { progressBar ->
            val progress = if (total > 0) ((current * 100) / total).toInt() else 0
            progressBar.progress = progress
        }
        
        // Update progress text if available
        binding.tvProgress?.text = TXAFormat.formatPercent(current, total)
    }

    /**
     * Show error dialog with retry option
     */
    private fun showErrorDialog(errorMessage: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(TXATranslation.txa("txamusic_msg_error"))
            .setMessage(errorMessage)
            .setPositiveButton(TXATranslation.txa("txamusic_action_retry")) { _, _ ->
                startLoadingSequence()
            }
            .setNegativeButton(TXATranslation.txa("txamusic_action_close")) { _, _ ->
                finishAffinity()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Navigate to main activity
     */
    private fun navigateToMain() {
        startActivity(Intent(this@TXASplashActivity, TXAMainActivity::class.java))
        finish()
    }
}
