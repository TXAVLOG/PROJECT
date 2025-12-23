package ms.txams.vv.ui.splash

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ms.txams.vv.R
import ms.txams.vv.core.TXATranslation
import ms.txams.vv.core.TXAFormat
import ms.txams.vv.core.audio.TXAAudioInjectionManager
import ms.txams.vv.core.txa
import ms.txams.vv.ui.main.TXAMainActivity
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TXA Splash Activity - Màn hình khởi động với version guard và loading progress
 * Flow: Version Check → Permission Check → Translation Sync → Audio Integrity Check → Main Activity
 */
class TXASplashActivity : AppCompatActivity() {

    private lateinit var translation: TXATranslation
    private var isInitialized = AtomicBoolean(false)
    
    // UI Components
    private lateinit var progressBar: View
    private lateinit var progressText: View
    private lateinit var logoView: View
    
    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionResults(permissions)
    }
    
    // Required permissions
    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_txa_splash)
        
        translation = TXATranslation.getInstance(this)
        initializeUI()
        
        // Bắt đầu splash flow
        if (isInitialized.compareAndSet(false, true)) {
            startSplashFlow()
        }
    }

    private fun initializeUI() {
        // Initialize UI components
        progressBar = findViewById(R.id.progress_bar)
        progressText = findViewById(R.id.progress_text)
        logoView = findViewById(R.id.logo_view)
        
        // Setup initial state
        updateProgressUI(0, translation.txa("txamusic_loading_language"))
        
        // Logo animation
        animateLogo()
    }

    private fun animateLogo() {
        logoView.apply {
            alpha = 0f
            scaleX = 0.8f
            scaleY = 0.8f
            
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .setDuration(800)
                .start()
        }
    }

    private fun startSplashFlow() {
        lifecycleScope.launch {
            try {
                // Step 1: Version Guard
                if (!checkVersionCompatibility()) {
                    return@launch
                }
                updateProgressUI(10, translation.txa("txamusic_checking_data"))
                
                // Step 2: Permission Check
                if (!checkAndRequestPermissions()) {
                    return@launch
                }
                updateProgressUI(20, translation.txa("txamusic_checking_data"))
                
                // Step 3: Translation Sync & Audio Integrity (Parallel)
                val startTime = System.currentTimeMillis()
                val syncResult = syncTranslationsAndCheckIntegrity()
                val elapsedTime = System.currentTimeMillis() - startTime
                
                // Ensure minimum 5 seconds loading with fake progress
                val remainingTime = maxOf(0L, 5000 - elapsedTime)
                simulateProgressToCompletion(syncResult, remainingTime)
                
            } catch (e: Exception) {
                Timber.e(e, "Splash flow failed")
                handleSplashError(e)
            }
        }
    }

    private suspend fun syncTranslationsAndCheckIntegrity(): Boolean {
        return try {
            // Run translation sync and audio integrity check in parallel
            val translationJob = lifecycleScope.launch {
                val success = translation.syncIfNewer()
                if (!success) {
                    Timber.w("Translation sync failed, using cached data")
                }
            }
            
            val audioJob = lifecycleScope.launch {
                val audioManager = TXAAudioInjectionManager(this@TXASplashActivity)
                audioManager.initialize()
                val integrity = audioManager.checkIntegrity()
                if (!integrity) {
                    Timber.w("Audio integrity check failed")
                }
            }
            
            // Wait for both to complete
            translationJob.join()
            audioJob.join()
            
            updateProgressUI(80, translation.txa("txamusic_entering_app"))
            true
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync translations or check audio integrity")
            updateProgressUI(80, translation.txa("txamusic_error_connection"))
            false
        }
    }

    private suspend fun simulateProgressToCompletion(syncSuccess: Boolean, delayMs: Long) {
        val steps = 20 // Number of progress steps
        val stepDelay = delayMs / steps
        
        for (i in 1..steps) {
            delay(stepDelay)
            val progress = 80 + (20 * i / steps)
            val message = when {
                i < steps / 2 -> translation.txa("txamusic_checking_data")
                i < steps -> translation.txa("txamusic_entering_app")
                else -> translation.txa("txamusic_entering_app")
            }
            updateProgressUI(progress, message)
        }
        
        // Navigate to main activity
        navigateToMainActivity()
    }

    private fun checkVersionCompatibility(): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) { // SDK 33
            showIncompatibleVersionDialog()
            false
        } else {
            true
        }
    }

    private fun showIncompatibleVersionDialog() {
        AlertDialog.Builder(this)
            .setTitle(translation.txa("txamusic_error"))
            .setMessage(translation.txa("txamusic_app_incompatible"))
            .setCancelable(false)
            .setPositiveButton(translation.txa("txamusic_ok")) { _, _ ->
                finishAffinity()
            }
            .show()
    }

    private fun checkAndRequestPermissions(): Boolean {
        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        return if (missingPermissions.isEmpty()) {
            true
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
            false
        }
    }

    private fun handlePermissionResults(permissions: Map<String, Boolean>) {
        val allGranted = permissions.values.all { it }
        
        if (allGranted) {
            // Permissions granted, continue splash flow
            lifecycleScope.launch {
                startSplashFlow()
            }
        } else {
            // Show permission denied dialog
            showPermissionDeniedDialog()
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle(translation.txa("txamusic_permission_storage_title"))
            .setMessage(translation.txa("txamusic_permission_storage_message"))
            .setCancelable(false)
            .setPositiveButton(translation.txa("txamusic_retry")) { _, _ ->
                // Retry permission request
                checkAndRequestPermissions()
            }
            .setNegativeButton(translation.txa("txamusic_exit")) { _, _ ->
                finishAffinity()
            }
            .show()
    }

    private fun updateProgressUI(progress: Int, message: String) {
        runOnUiThread {
            // Update progress bar
            when (progressBar) {
                is android.widget.ProgressBar -> progressBar.progress = progress
                is android.widget.SeekBar -> progressBar.progress = progress
            }
            
            // Update progress text
            when (progressText) {
                is android.widget.TextView -> {
                    progressText.text = "${TXAFormat.formatPercent(progress.toLong(), 100L)} • $message"
                }
            }
            
            Timber.d("Splash progress: $progress% - $message")
        }
    }

    private fun navigateToMainActivity() {
        runOnUiThread {
            try {
                val intent = Intent(this, TXAMainActivity::class.java)
                startActivity(intent)
                finish()
                
                // Override transition
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to navigate to main activity")
                handleSplashError(e)
            }
        }
    }

    private fun handleSplashError(error: Exception) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle(translation.txa("txamusic_error"))
                .setMessage(translation.txa("txamusic_error_load_failed"))
                .setCancelable(false)
                .setPositiveButton(translation.txa("txamusic_retry")) { _, _ ->
                    // Retry splash flow
                    lifecycleScope.launch {
                        delay(1000) // Brief delay before retry
                        startSplashFlow()
                    }
                }
                .setNegativeButton(translation.txa("txamusic_exit")) { _, _ ->
                    finishAffinity()
                }
                .show()
        }
    }

    override fun onBackPressed() {
        // Prevent back press during splash
        // User can only exit via dialog or complete the flow
    }

    override fun onDestroy() {
        super.onDestroy()
        isInitialized.set(false)
    }

    companion object {
        private const val TAG = "TXASplashActivity"
        
        // Splash timing constants
        private const val MIN_SPLASH_DURATION_MS = 5000L
        private const val PROGRESS_UPDATE_INTERVAL_MS = 100L
        
        // Permission request codes
        private const val PERMISSION_REQUEST_CODE = 1001
    }
}
