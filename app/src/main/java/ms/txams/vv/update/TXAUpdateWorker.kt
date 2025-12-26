package ms.txams.vv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ms.txams.vv.core.TXALogger
import java.util.concurrent.TimeUnit

/**
 * TXA Update Worker
 * 
 * Background worker for checking updates periodically.
 * 
 * Features:
 * - Periodic check every 15 minutes (minimum allowed by WorkManager)
 * - Immediate check available via scheduleImmediate()
 * - Network constraint (only runs when network available)
 * - Battery-friendly (works with system optimization)
 * - Silent check (no notification, saves result to SharedPreferences)
 * 
 * Battery Optimization Note:
 * - Worker works WITHOUT requesting battery optimization ignore
 * - Request battery ignore only if user explicitly enables "background updates"
 * - Google Play policy restricts apps from requesting battery exemption unnecessarily
 * 
 * @author TXA - fb.com/vlog.txa.2311 - txavlog7@gmail.com
 */
class TXAUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val WORK_NAME_PERIODIC = "txa_update_periodic"
        private const val WORK_NAME_IMMEDIATE = "txa_update_immediate"
        private const val PREF_NAME = "txa_update_worker"
        private const val KEY_LAST_CHECK = "last_check_time"
        private const val KEY_UPDATE_AVAILABLE = "update_available"
        private const val KEY_UPDATE_VERSION = "update_version"
        private const val KEY_BACKGROUND_ENABLED = "background_enabled"
        
        // Minimum interval is 15 minutes for PeriodicWorkRequest
        private const val CHECK_INTERVAL_MINUTES = 15L
        
        /**
         * Start periodic update checks
         * Called on app startup
         */
        fun startPeriodic(context: Context) {
            // Only start if background updates are enabled
            if (!isBackgroundEnabled(context)) {
                TXALogger.appD("TXAUpdateWorker: Background updates disabled")
                return
            }
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)  // Don't run on low battery
                .build()
            
            val workRequest = PeriodicWorkRequestBuilder<TXAUpdateWorker>(
                CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,  // Keep existing if already scheduled
                workRequest
            )
            
            TXALogger.appI("TXAUpdateWorker: Periodic check started (every $CHECK_INTERVAL_MINUTES min)")
        }
        
        /**
         * Stop periodic update checks
         */
        fun stopPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
            TXALogger.appI("TXAUpdateWorker: Periodic check stopped")
        }
        
        /**
         * Schedule an immediate update check
         * Use when user opens Settings or explicitly requests check
         */
        fun scheduleImmediate(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val workRequest = OneTimeWorkRequestBuilder<TXAUpdateWorker>()
                .setConstraints(constraints)
                .build()
            
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_IMMEDIATE,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            
            TXALogger.appI("TXAUpdateWorker: Immediate check scheduled")
        }
        
        /**
         * Enable/disable background updates
         */
        fun setBackgroundEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_BACKGROUND_ENABLED, enabled)
                .apply()
            
            if (enabled) {
                startPeriodic(context)
                TXALogger.appI("Background updates enabled")
            } else {
                stopPeriodic(context)
                TXALogger.appI("Background updates disabled")
            }
        }
        
        /**
         * Check if background updates are enabled
         */
        fun isBackgroundEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_BACKGROUND_ENABLED, true)  // Default: enabled
        }
        
        /**
         * Check if battery optimization is ignored (optional, for better reliability)
         */
        fun isBatteryOptimizationIgnored(context: Context): Boolean {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        }
        
        /**
         * Request to ignore battery optimization
         * Note: Only use when user explicitly enables "reliable background updates"
         * Google Play restricts unnecessary use of this permission
         */
        fun requestIgnoreBatteryOptimization(context: Context): Boolean {
            return try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:${context.packageName}")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                TXALogger.appE("Failed to request battery optimization ignore", e)
                false
            }
        }
        
        /**
         * Open battery optimization settings (fallback)
         */
        fun openBatterySettings(context: Context): Boolean {
            return try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                TXALogger.appE("Failed to open battery settings", e)
                false
            }
        }
        
        /**
         * Get last check time
         */
        fun getLastCheckTime(context: Context): Long {
            return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_CHECK, 0)
        }
        
        /**
         * Check if there's a cached update available
         */
        fun hasCachedUpdate(context: Context): Boolean {
            return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_UPDATE_AVAILABLE, false)
        }
        
        /**
         * Get cached update version
         */
        fun getCachedUpdateVersion(context: Context): String? {
            return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_UPDATE_VERSION, null)
        }
        
        /**
         * Clear cached update
         */
        fun clearCachedUpdate(context: Context) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_UPDATE_AVAILABLE, false)
                .remove(KEY_UPDATE_VERSION)
                .apply()
        }
        
        /**
         * Save update result to cache
         */
        private fun saveUpdateResult(context: Context, available: Boolean, version: String?) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_UPDATE_AVAILABLE, available)
                .putString(KEY_UPDATE_VERSION, version)
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                .apply()
        }
    }
    
    override suspend fun doWork(): Result {
        TXALogger.appI("TXAUpdateWorker: Starting update check")
        
        try {
            // Check for updates
            when (val result = TXAUpdateManager.checkForUpdate(applicationContext)) {
                is UpdateCheckResult.UpdateAvailable -> {
                    TXALogger.appI("TXAUpdateWorker: Update available - ${result.updateInfo.versionName}")
                    saveUpdateResult(applicationContext, true, result.updateInfo.versionName)
                }
                is UpdateCheckResult.NoUpdate -> {
                    TXALogger.appD("TXAUpdateWorker: No update available")
                    saveUpdateResult(applicationContext, false, null)
                }
                is UpdateCheckResult.Error -> {
                    TXALogger.appW("TXAUpdateWorker: Check failed - ${result.message}")
                    // Don't clear cache on error, keep previous result
                }
            }
        } catch (e: Exception) {
            TXALogger.appE("TXAUpdateWorker: Exception during check", e)
            return Result.retry()
        }
        
        return Result.success()
    }
}
