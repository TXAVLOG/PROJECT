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
import ms.txams.vv.core.TXALogger
import ms.txams.vv.core.TXATranslation
import ms.txams.vv.data.manager.TXAAudioInjectionManager
import ms.txams.vv.databinding.ActivitySplashBinding
import javax.inject.Inject

/**
 * TXA Splash Activity
 * 
 * Flow:
 * 1. Version Check (Android 13+)
 * 2. Integrity Check (intro_txa.mp3)
 * 3. Brief initialization delay
 * 4. Navigate to Main
 * 
 * Note: Translation is already loaded synchronously in TXAApp.onCreate()
 * txa() is always available and returns text immediately
 */
@AndroidEntryPoint
class TXASplashActivity : AppCompatActivity() {

    @Inject lateinit var audioInjectionManager: TXAAudioInjectionManager
    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        TXALogger.appI("TXASplashActivity started")

        // Step 1: Version Check
        if (Build.VERSION.SDK_INT < 33) {
            TXALogger.appW("Android version not supported: ${Build.VERSION.SDK_INT}")
            showIncompatibleDialog()
            return
        }

        // Step 2: Integrity Check
        if (!performIntegrityCheck()) {
            TXALogger.appE("Integrity check failed")
            showIntegrityErrorDialog()
            return
        }

        // Step 3: Start initialization sequence
        startInitSequence()
    }

    /**
     * Show dialog for incompatible Android version
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
     * Perform integrity check on assets
     */
    private fun performIntegrityCheck(): Boolean {
        updateStatus(TXATranslation.txa("txamusic_splash_checking_permissions"))
        
        val integrityResult = audioInjectionManager.getIntegrityDetail()
        
        if (!integrityResult.isValid) {
            TXALogger.appE("Integrity check failed: ${integrityResult.errorMessage}")
            TXALogger.appE("File exists: ${integrityResult.exists}, Size: ${integrityResult.size}, MD5: ${integrityResult.md5Hash}")
            return false
        }
        
        TXALogger.appD("Integrity check passed: MD5=${integrityResult.md5Hash}")
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
     * Initialization sequence
     * - Brief delay for Hilt/ExoPlayer initialization
     * - Navigate to main activity
     */
    private fun startInitSequence() {
        lifecycleScope.launch {
            // Show initializing status
            updateStatus(TXATranslation.txa("txamusic_splash_initializing"))
            binding.progressBar?.visibility = View.VISIBLE
            
            // Brief initialization delay (2 seconds for Hilt/ExoPlayer)
            val totalDelay = 2000L
            val steps = 20
            val stepDelay = totalDelay / steps
            
            for (i in 1..steps) {
                delay(stepDelay)
                val progress = (i * 100) / steps
                binding.progressBar?.progress = progress
                binding.tvProgress?.text = "$progress%"
            }
            
            // Ready to navigate
            updateStatus(TXATranslation.txa("txamusic_splash_entering_app"))
            TXALogger.appI("Splash complete, navigating to main")
            
            delay(300) // Brief pause before navigation
            navigateToMain()
        }
    }

    /**
     * Update status text
     */
    private fun updateStatus(message: String) {
        binding.tvStatus.text = message
    }

    /**
     * Navigate to main activity
     */
    private fun navigateToMain() {
        startActivity(Intent(this@TXASplashActivity, TXAMainActivity::class.java))
        finish()
    }
}
