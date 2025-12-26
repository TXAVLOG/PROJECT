package ms.txams.vv.ui

import android.app.AlertDialog
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
import ms.txams.vv.core.TXAApp
import ms.txams.vv.core.TXALogger
import ms.txams.vv.core.TXATranslation
import ms.txams.vv.data.manager.TXAAudioInjectionManager
import ms.txams.vv.databinding.ActivitySplashBinding
import javax.inject.Inject

/**
 * TXA Splash Activity
 * 
 * Flow:
 * 1. Version Check (Android 13+) - FIRST, before anything else
 * 2. Integrity Check (intro_txa.mp3)
 * 3. Brief initialization delay
 * 4. Navigate to Main
 * 
 * If device is not supported, show dialog and exit.
 * Translation is already loaded in TXAApp.onCreate()
 * 
 * @author TXA - fb.com/vlog.txa.2311 - txavlog7@gmail.com
 */
@AndroidEntryPoint
class TXASplashActivity : BaseActivity() {

    @Inject lateinit var audioInjectionManager: TXAAudioInjectionManager
    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Step 0: Check device support IMMEDIATELY (before any other code)
        if (!TXAApp.isDeviceSupported()) {
            // Show unsupported dialog using basic AlertDialog (not Material)
            // because Material components might not work on old Android
            showUnsupportedDialogAndExit()
            return
        }
        
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        TXALogger.appI("TXASplashActivity started")

        // Step 1: Integrity Check
        if (!performIntegrityCheck()) {
            TXALogger.appE("Integrity check failed")
            showIntegrityErrorDialog()
            return
        }

        // Step 2: Check Permissions (All Files Access)
        if (!ms.txams.vv.util.TXAPermissionManager.hasAllFilesAccess(this)) {
            showPermissionExplanationDialog()
        } else {
            // Step 3: Start initialization sequence
            startInitSequence()
        }
    }

    private fun showPermissionExplanationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(TXATranslation.txa("txamusic_permission_all_files_title"))
            .setMessage(TXATranslation.txa("txamusic_permission_all_files_message"))
            .setPositiveButton(TXATranslation.txa("txamusic_action_ok")) { _, _ ->
                ms.txams.vv.util.TXAPermissionManager.requestAllFilesAccess(this, REQ_ALL_FILES)
            }
            .setNegativeButton(TXATranslation.txa("txamusic_action_cancel")) { _, _ ->
                startInitSequence() // Continue anyway, will use app-specific storage
            }
            .setCancelable(false)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ALL_FILES) {
            // Re-initialize logger to potentially use new public path
            TXALogger.init(this)
            startInitSequence()
        }
    }

    companion object {
        private const val REQ_ALL_FILES = 1001
    }

    /**
     * Show unsupported device dialog and exit
     * Uses basic AlertDialog for maximum compatibility with old Android versions
     */
    private fun showUnsupportedDialogAndExit() {
        try {
            // Use basic AlertDialog for compatibility
            AlertDialog.Builder(this)
                .setTitle("Not Supported")
                .setMessage(TXAApp.getUnsupportedMessage())
                .setPositiveButton("OK") { _, _ ->
                    finishAffinity()
                }
                .setCancelable(false)
                .setOnDismissListener {
                    finishAffinity()
                }
                .show()
        } catch (e: Exception) {
            // If dialog fails, just log and exit
            TXALogger.appE("Failed to show unsupported dialog", e)
            android.widget.Toast.makeText(
                this,
                "Android ${Build.VERSION.RELEASE} is not supported. TXA Music requires Android 13+",
                android.widget.Toast.LENGTH_LONG
            ).show()
            finishAffinity()
        }
    }

    /**
     * Perform integrity check on assets
     */
    private fun performIntegrityCheck(): Boolean {
        updateStatus(TXATranslation.txa("txamusic_splash_checking_permissions"))
        
        return try {
            val integrityResult = audioInjectionManager.getIntegrityDetail()
            
            if (!integrityResult.isValid) {
                TXALogger.appE("Integrity check failed: ${integrityResult.errorMessage}")
                TXALogger.appE("File exists: ${integrityResult.exists}, Size: ${integrityResult.size}, MD5: ${integrityResult.md5Hash}")
                false
            } else {
                TXALogger.appD("Integrity check passed: MD5=${integrityResult.md5Hash}")
                true
            }
        } catch (e: Exception) {
            TXALogger.appE("Integrity check exception", e)
            false
        }
    }

    /**
     * Show dialog when integrity check fails
     */
    private fun showIntegrityErrorDialog() {
        try {
            MaterialAlertDialogBuilder(this)
                .setTitle(TXATranslation.txa("txamusic_msg_error"))
                .setMessage(TXATranslation.txa("txamusic_integrity_check_failed"))
                .setPositiveButton(TXATranslation.txa("txamusic_action_ok")) { _, _ ->
                    finishAffinity()
                }
                .setCancelable(false)
                .show()
        } catch (e: Exception) {
            TXALogger.appE("Failed to show integrity error dialog", e)
            finishAffinity()
        }
    }

    /**
     * Initialization sequence
     * - Brief delay for Hilt/ExoPlayer initialization
     * - Navigate to main activity
     */
    private fun startInitSequence() {
        lifecycleScope.launch {
            try {
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
            } catch (e: Exception) {
                TXALogger.appE("Init sequence failed", e)
                // Try to navigate anyway
                navigateToMain()
            }
        }
    }

    /**
     * Update status text
     */
    private fun updateStatus(message: String) {
        try {
            binding.tvStatus.text = message
        } catch (e: Exception) {
            // Ignore if binding not ready
        }
    }

    /**
     * Navigate to main activity
     */
    private fun navigateToMain() {
        try {
            startActivity(Intent(this@TXASplashActivity, TXAMainActivity::class.java))
            finish()
        } catch (e: Exception) {
            TXALogger.appE("Failed to navigate to main", e)
            finishAffinity()
        }
    }
}
