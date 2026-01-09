package com.txapp.musicplayer.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.savedstate.*
import com.txapp.musicplayer.R
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.txapp.musicplayer.ui.component.DeviceNotSupportedModal
import com.txapp.musicplayer.ui.component.PermissionDialog
import com.txapp.musicplayer.ui.component.UpdateDialog
import com.txapp.musicplayer.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


/**
 * TXA Splash Activity
 * Follows the splash flow:
 * 1. Device Compatibility Check
 * 2. Permission Check (using Compose Dialog)
 * 3. Update Check (using Compose Dialog)
 * 4. Scan Music Library
 * 5. Navigate to Main
 */
class SplashActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SplashActivity"
    }

    private lateinit var statusText: TextView
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var progressPercent: TextView
    private lateinit var versionText: TextView
    private lateinit var openStatus: TextView
    private lateinit var composeView: ComposeView

    // Dialog States
    private val showPermissionDialog = mutableStateOf(false)
    private val showDeviceNotSupportedDialog = mutableStateOf(false)
    private val showAndroid9WarningDialog = mutableStateOf(false)
    private val showLocationForcedDialog = mutableStateOf(false)
    private val showRamWarningDialog = mutableStateOf(false)
    private val showRootInfoDialog = mutableStateOf(false)
    
    // Continuation for permission flow
    // Continuation for permission flow
    private var permissionContinuation: kotlin.coroutines.Continuation<Unit>? = null
    private var ramWarningContinuation: kotlin.coroutines.Continuation<Unit>? = null
    private var rootInfoContinuation: kotlin.coroutines.Continuation<Unit>? = null
    
    // Restricted Mode
    private val showNetworkRestrictedDialog = mutableStateOf(false)
    private var networkRestrictedContinuation: kotlin.coroutines.Continuation<Unit>? = null

    // Post Update Dialog (after app update success)
    private val showPostUpdateDialog = mutableStateOf(false)
    private val postUpdateChangelog = mutableStateOf("")
    private var postUpdateContinuation: kotlin.coroutines.Continuation<Unit>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_TXMusicPlayer)
        setContentView(R.layout.activity_splash)

        initViews()
        setupCompose()
        
        // Handle ACTION_VIEW intent
        if (intent?.action == Intent.ACTION_VIEW) {
            openStatus.visibility = View.VISIBLE
            openStatus.text = "txamusic_splash_opening_file".txa()
        }

        // Start splash flow
        startSplashFlow()
    }

    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        progressBar = findViewById(R.id.progressBar)
        progressPercent = findViewById(R.id.progressPercent)
        versionText = findViewById(R.id.versionText)
        openStatus = findViewById(R.id.openStatus)
        composeView = findViewById(R.id.composeView)
        composeView.setViewTreeLifecycleOwner(this)
        composeView.setViewTreeViewModelStoreOwner(this)
        composeView.setViewTreeSavedStateRegistryOwner(this)

        // Set version
        versionText.text = "v${TXADeviceInfo.getVersionName()}"
    }

    private fun setupCompose() {
        composeView.setContent {
            val updateInfo by TXAUpdateManager.updateInfo.collectAsState()
            
            // Theo dõi hiển thị dialog
            val isSomeDialogVisible = showPermissionDialog.value || 
                                     (showScanResultDialog.value && scanResultData.value != null) || 
                                     showDeviceNotSupportedDialog.value ||
                                     showAndroid9WarningDialog.value ||
                                     showLocationForcedDialog.value ||
                                     showLocationForcedDialog.value ||
                                     showRootInfoDialog.value ||
                                     showNetworkRestrictedDialog.value
            
            LaunchedEffect(isSomeDialogVisible) {
                composeView.visibility = if (isSomeDialogVisible) View.VISIBLE else View.GONE
                TXALogger.appD(TAG, "ComposeView visibility set to: ${if (isSomeDialogVisible) "VISIBLE" else "GONE"}")
            }

            if (showPermissionDialog.value) {
                PermissionDialog(
                    onAllGranted = {
                        TXALogger.appI(TAG, "Permissions granted via Compose dialog")
                        showPermissionDialog.value = false
                        permissionContinuation?.resume(Unit)
                        permissionContinuation = null
                    }
                )
            }
            
            // Scan Result Modal
            if (showScanResultDialog.value && scanResultData.value != null) {
                com.txapp.musicplayer.ui.component.ScanResultModal(
                    successCount = scanResultData.value!!.first,
                    failedCount = scanResultData.value!!.second,
                    minDurationSeconds = 2,
                    onConfirm = {
                        TXALogger.appI(TAG, "ScanResultModal confirmed by user")
                        showScanResultDialog.value = false
                        scanResultContinuation?.resume(Unit)
                        scanResultContinuation = null
                    }
                )
            }
            
            // Device Not Supported Modal
            if (showDeviceNotSupportedDialog.value) {
                DeviceNotSupportedModal(
                    minRequired = TXADeviceInfo.getMinRequiredAndroid(),
                    currentVersion = TXADeviceInfo.getAndroidVersion(),
                    onExit = {
                        finishAffinity()
                    }
                )
            }

            // Android 9 Warning Modal
            if (showAndroid9WarningDialog.value) {
                com.txapp.musicplayer.ui.component.Android9WarningModal(
                    onContinue = {
                        TXALogger.appI(TAG, "Android 9 Warning dismissed")
                        showAndroid9WarningDialog.value = false
                        android9WarningContinuation?.resume(Unit)
                        android9WarningContinuation = null
                    }
                )
            }

            // Location Permission Mandatory Modal
            if (showLocationForcedDialog.value) {
                com.txapp.musicplayer.ui.component.LocationPermissionModal(
                    onOpenSettings = {
                        TXAPermissionHelper.openAppSettings(this@SplashActivity)
                    },
                    onExit = {
                        finishAffinity()
                    }
                )
            }
 
            // Root Info Modal
            if (showRootInfoDialog.value) {
                com.txapp.musicplayer.ui.component.RootInfoModal(
                    deviceName = TXADeviceInfo.getDeviceName(),
                    androidVersion = TXADeviceInfo.getAndroidVersion(),
                    onConfirm = {
                        TXALogger.appI(TAG, "Root info modal dismissed")
                        showRootInfoDialog.value = false
                        rootInfoContinuation?.resume(Unit)
                        rootInfoContinuation = null
                    }
                )
            }

             // RAM Warning Modal
             if (showRamWarningDialog.value) {
                com.txapp.musicplayer.ui.component.RamWarningModal(
                    onContinue = {
                        TXALogger.appI(TAG, "RAM Warning dismissed")
                        showRamWarningDialog.value = false
                        ramWarningContinuation?.resume(Unit)
                        ramWarningContinuation = null
                    }
                )
            }

            // Network Restricted Modal
            if (showNetworkRestrictedDialog.value) {
                com.txapp.musicplayer.ui.component.NetworkRestrictedModal(
                    onContinue = {
                        TXALogger.appW(TAG, "Entering Restricted Mode due to Data Exhaustion")
                        showNetworkRestrictedDialog.value = false
                        networkRestrictedContinuation?.resume(Unit)
                        networkRestrictedContinuation = null
                    }
                )
            }

            // Post Update Dialog (after updating app)
            if (showPostUpdateDialog.value) {
                com.txapp.musicplayer.ui.component.PostUpdateDialog(
                    appName = getString(R.string.app_name),
                    versionName = TXADeviceInfo.getVersionName(),
                    changelog = postUpdateChangelog.value,
                    onDismiss = {
                        TXALogger.appI(TAG, "PostUpdateDialog dismissed, saving version flag")
                        // Save current version to prevent showing again
                        TXAPreferences.setLastSeenVersionCode(TXADeviceInfo.getVersionCode())
                        showPostUpdateDialog.value = false
                        postUpdateContinuation?.resume(Unit)
                        postUpdateContinuation = null
                    }
                )
            }
        }
    }
    
    // Additional states for scan result
    private val showScanResultDialog = mutableStateOf(false)
    private val scanResultData = mutableStateOf<Pair<Int, Int>?>(null)
    private var scanResultContinuation: kotlin.coroutines.Continuation<Unit>? = null
    
    private var android9WarningContinuation: kotlin.coroutines.Continuation<Unit>? = null

    private fun startSplashFlow() {
        lifecycleScope.launch {
            try {
                // Initial State
                updateProgress(0)

                // Step 0: Network & Image Quality Check
                TXALogger.appD(TAG, "Step 0: Checking network status...")
                val networkStatus = TXANetworkHelper.getNetworkStatus(this@SplashActivity)
                
                // Reset restricted mode by default
                TXAPreferences.isRestrictedMode = false
                
                when (networkStatus) {
                    TXANetworkHelper.NetworkStatus.WIFI_CONNECTED -> {
                        TXAPreferences.setImageQuality("high")
                        TXALogger.appI(TAG, "Network: WiFi Connected - Quality High")
                    }
                    TXANetworkHelper.NetworkStatus.WIFI_NO_INTERNET -> {
                        TXAPreferences.setImageQuality("low")
                        withContext(Dispatchers.Main) {
                            TXAToast.warning(this@SplashActivity, "txamusic_network_wifi_no_internet".txa())
                        }
                    }
                    TXANetworkHelper.NetworkStatus.CELLULAR_CONNECTED -> {
                        TXAPreferences.setImageQuality("medium")
                        TXALogger.appI(TAG, "Network: Cellular Connected - Quality Medium")
                    }
                    TXANetworkHelper.NetworkStatus.CELLULAR_NO_INTERNET -> {
                         TXAPreferences.setImageQuality("low")
                         TXAPreferences.isRestrictedMode = true
                         
                         // Show Modal for Data Exhausted
                         withContext(Dispatchers.Main) {
                             suspendCancellableCoroutine<Unit> { cont ->
                                 networkRestrictedContinuation = cont
                                 showNetworkRestrictedDialog.value = true
                             }
                        }
                    }
                    else -> {
                        // NONE or other - Default behaviors
                        TXAPreferences.setImageQuality("low")
                    }
                }
                
                // Step 1: Initializing (0-10%)
                TXALogger.appD(TAG, "Step 1: Initializing...")
                updateStatus("txamusic_splash_initializing".txa())
                smoothProgress(0, 10, 500)

                // Step 2: Language Sync (10-30%)
                val prefs = getSharedPreferences("txa_translation_prefs", android.content.Context.MODE_PRIVATE)
                val savedLocale = prefs.getString("current_locale", null)
                val targetLocale = savedLocale ?: if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    resources.configuration.locales[0].language
                } else {
                    @Suppress("DEPRECATION")
                    resources.configuration.locale.language
                }
                
                val displayLang = if (targetLocale == "vi") "Tiếng Việt" else "English"
                TXALogger.appD(TAG, "Step 2: Syncing language ($targetLocale)...")
                updateStatus("txamusic_splash_checking_language".txa(displayLang))
                
                val langSuccess = TXATranslation.downloadAndApply(this@SplashActivity, targetLocale) { subPercent, subStatus ->
                    runOnUiThread {
                        if (subPercent == -1) {
                            // Error state
                            statusText.setTextColor(android.graphics.Color.RED)
                            statusText.text = "Error: $subStatus"
                            // Error is handled below to stop the flow
                        } else {
                            val overall = 10 + (subPercent * 20 / 100)
                            updateProgress(overall)
                            statusText.text = "txamusic_splash_checking_language".txa(displayLang) + " ($subPercent%)"
                        }
                    }
                }

                if (!langSuccess && !TXATranslation.hasCacheFor(this@SplashActivity, targetLocale)) {
                    // Critical failure: No cache and download failed (Network Error)
                    TXALogger.appE(TAG, "Language sync failed and no cache available. Launching Network Error screen.")
                    
                    val androidVersion = TXADeviceInfo.getAndroidVersion()
                    val errorCode = "TXAAPP_${androidVersion}_NWFA"
                    
                    val intent = Intent(this@SplashActivity, TXAErrorActivity::class.java).apply {
                        putExtra(TXACrashHandler.INTENT_DATA_ERROR_LOG, "Fatal Translation Download Error\nFailed to sync language '$targetLocale' and no local cache available.\nPlease check your network connection.")
                        putExtra(TXACrashHandler.INTENT_DATA_ERROR_CODE, errorCode)
                        putExtra(TXACrashHandler.INTENT_DATA_SUGGESTION, "network")
                    }
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    startActivity(intent)
                    finish()
                    return@launch
                }
                updateProgress(30)

                // Step 2.5: Check Post-Update (show "Thank you for updating" dialog)
                val lastSeenVersion = TXAPreferences.getLastSeenVersionCode()
                val currentVersion = TXADeviceInfo.getVersionCode()
                TXALogger.appD(TAG, "Version Check: Current=$currentVersion, LastSeen=$lastSeenVersion")
                
                if (lastSeenVersion > 0 && currentVersion > lastSeenVersion) {
                    // User just updated! Show changelog
                    TXALogger.appI(TAG, "App was updated from $lastSeenVersion to $currentVersion. Showing PostUpdateDialog.")
                    
                    // Get changelog (use same logic as UpdateDialog)
                    val changelogHtml = try {
                        val lang = TXAPreferences.getCurrentLanguage()
                        val changelogUrl = if (lang == "vi") {
                            "https://raw.githubusercontent.com/TXAVLOG/PROJECT/main/CHANGELOG.html"
                        } else {
                            "https://raw.githubusercontent.com/TXAVLOG/PROJECT/main/CHANGELOG.html"
                        }
                        withContext(Dispatchers.IO) {
                            java.net.URL(changelogUrl).readText()
                        }
                    } catch (e: Exception) {
                        TXALogger.appW(TAG, "Failed to fetch changelog: ${e.message}")
                        "<p>Có gì mới? Vui lòng kiểm tra trong phần Cài đặt > Giới thiệu.</p>"
                    }
                    
                    postUpdateChangelog.value = changelogHtml
                    
                    suspendCancellableCoroutine<Unit> { continuation ->
                        postUpdateContinuation = continuation
                        showPostUpdateDialog.value = true
                    }
                } else if (lastSeenVersion == 0L) {
                    // First install - just save current version
                    TXAPreferences.setLastSeenVersionCode(currentVersion)
                    TXALogger.appI(TAG, "First install detected, saving version $currentVersion")
                }

                // Step 3: Permissions (30-60%)
                TXALogger.appD(TAG, "Step 3: Checking permissions...")
                updateStatus("txamusic_splash_loading_resources".txa())
                if (!TXAPermissionHelper.hasAllFilesPermission(this@SplashActivity) || 
                    !TXAPermissionHelper.hasNotificationPermission(this@SplashActivity) ||
                    !TXAPermissionHelper.hasLocationPermission(this@SplashActivity) ||
                    !TXAPermissionHelper.hasWriteSettingsPermission(this@SplashActivity)) {
                    
                    suspendCancellableCoroutine<Unit> { continuation ->
                        permissionContinuation = continuation
                        showPermissionDialog.value = true
                    }
                }

                // Double check location if it was denied in the dialog
                if (!TXAPermissionHelper.hasLocationPermission(this@SplashActivity)) {
                    showLocationForcedDialog.value = true
                    // Block execution here until granted (user will go to settings and return)
                    while (!TXAPermissionHelper.hasLocationPermission(this@SplashActivity)) {
                        delay(1000)
                    }
                    showLocationForcedDialog.value = false
                }
                smoothProgress(30, 60, 500)

                // Step 4: Device & Resource Check (60-70%)
                TXALogger.appD(TAG, "Step 4: Checking device compatibility...")
                if (!TXADeviceInfo.isDeviceSupported()) {
                    showDeviceNotSupportedDialog.value = true
                    suspendCancellableCoroutine<Unit> { }
                }

                // Check RAM
                TXALogger.appD(TAG, "Step 5: Checking RAM status...")
                val totalRam = TXADeviceInfo.getTotalRam()
                val availRam = TXADeviceInfo.getAvailableRam()
                val minRam = 3L * 1024 * 1024 * 1024 // 3GB
                val lowMemThreshold = 300L * 1024 * 1024 // 300MB
                
                if (totalRam < minRam || availRam < lowMemThreshold) {
                     suspendCancellableCoroutine<Unit> { continuation ->
                        ramWarningContinuation = continuation
                        showRamWarningDialog.value = true
                    }
                }
                
                updateProgress(65)
                // Show warning for Android 9 specifically
                if (android.os.Build.VERSION.SDK_INT == 28) {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        android9WarningContinuation = continuation
                        showAndroid9WarningDialog.value = true
                    }
                }
                updateProgress(65)

                // Step 6: Scan Library (65-95%)
                TXALogger.appD(TAG, "Step 6: Scanning library...")
                updateStatus("txamusic_splash_scanning_library".txa())
                val repository = com.txapp.musicplayer.data.MusicRepository(
                    (application as com.txapp.musicplayer.MusicApplication).database,
                    contentResolver
                )
                
                if (TXAPermissionHelper.hasAllFilesPermission(this@SplashActivity)) {
                    // Try to load from MediaStore first (baseline)
                    val msSongs = repository.loadSongsFromMediaStore()
                    if (msSongs.isNotEmpty()) {
                        repository.saveSongs(msSongs)
                        runOnUiThread {
                            openStatus.visibility = View.VISIBLE
                            openStatus.text = "Found: ${msSongs.size}"
                        }
                    }

                    // Force deep scan if MediaStore returned nothing (or if deep scan preferenced) to ensure music is found
                    val forceDeepScan = msSongs.isEmpty()
                    if (forceDeepScan || TXAMusicScanner.shouldPerformDeepScan(this@SplashActivity)) {
                        val result = TXAMusicScanner.scanMusic(this@SplashActivity, repository, object : TXAMusicScanner.ScanCallback {
                            override fun onProgress(currentPath: String, count: Int) {
                                runOnUiThread {
                                    val totalFound = msSongs.size + count
                                    statusText.text = "txamusic_scan_scanning".txa() + ": " + currentPath.takeLast(30)
                                    openStatus.visibility = View.VISIBLE
                                    openStatus.text = "Found: $totalFound"
                                }
                            }
                        })
                        
                        if (result.successCount > 0 || result.failedCount > 0 || msSongs.isNotEmpty()) {
                            // Only show dialog if we actually found something or if explicit scan was requested
                            TXALogger.appI(TAG, "Waiting for ScanResult dialog (Success: ${result.successCount}, Failed: ${result.failedCount}, MS: ${msSongs.size})")
                            showScanResultDialog(result)
                            TXALogger.appI(TAG, "ScanResult dialog confirmed")
                        }
                    }
                }
                smoothProgress(65, 95, 800)

                // Step 7: Finalize (95-100%)
                TXALogger.appD(TAG, "Step 7: Finalizing flow...")
                updateStatus("txamusic_splash_loading_resources".txa())
                smoothProgress(95, 100, 500)
 
                // Final Step: Root Check for advanced features
                TXALogger.appD(TAG, "Final Step: Checking root status...")
                if (TXASuHelper.isRooted()) {
                    TXALogger.appI(TAG, "Device binaries suggest root is available. Verifying...")
                    if (TXASuHelper.verifyRoot()) {
                        TXALogger.appI(TAG, "Root permission granted. Showing info modal.")
                        suspendCancellableCoroutine<Unit> { continuation ->
                            rootInfoContinuation = continuation
                            showRootInfoDialog.value = true
                        }
                    } else {
                        TXALogger.appW(TAG, "Root binary found but permission denied or failed.")
                    }
                } else {
                    TXALogger.appI(TAG, "Device is not rooted. Proceeding with standard mode.")
                }

                navigateToMain()

            } catch (e: Throwable) {
                // Catch EVERYTHING (Exception, Error, OOM, etc.)
                if (e is kotlinx.coroutines.CancellationException) {
                    throw e
                }
                
                // 1. Log to system immediately
                TXALogger.appE(TAG, "FATAL Splash Flow Error: ${e.message}", e)
                
                // 2. Log extra info about RAM if it's OOM
                if (e is OutOfMemoryError) {
                    val total = TXADeviceInfo.getTotalRam() / (1024 * 1024)
                    val avail = TXADeviceInfo.getAvailableRam() / (1024 * 1024)
                    TXALogger.appE(TAG, "OOM Details - Total: ${total}MB, Available: ${avail}MB")
                }

                // 3. Show error screen
                TXACrashHandler.reportFatalError(this@SplashActivity, e, TAG, killProcess = true)
            }
        }
    }

    private suspend fun smoothProgress(from: Int, to: Int, durationMs: Long) {
        val steps = 10
        val interval = durationMs / steps
        val stepSize = (to - from) / steps.toFloat()
        for (i in 1..steps) {
            updateProgress((from + i * stepSize).toInt())
            delay(interval)
        }
        updateProgress(to)
    }

    private suspend fun showScanResultDialog(result: TXAMusicScanner.ScanResult) = suspendCancellableCoroutine<Unit> { continuation ->
        scanResultContinuation = continuation
        scanResultData.value = Pair(result.successCount, result.failedCount)
        showScanResultDialog.value = true
    }

    private fun updateStatus(status: String, progress: Int? = null) {
        runOnUiThread {
            statusText.text = status
            progress?.let { updateProgress(it) }
        }
    }

    private fun updateProgress(progress: Int) {
        runOnUiThread {
            progressBar.setProgressCompat(progress, true)
            progressPercent.text = "$progress%"
        }
    }

    private fun navigateToMain() {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            action = intent.action
            data = intent.data
            // Critical: Pass permission flags from incoming intent
            flags = intent.flags or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(mainIntent)
        finish()
    }
}
