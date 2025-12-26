package ms.txams.vv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
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
 * - Check update every 3 minutes (self-reschedule)
 * - Only runs when:
 *   - Network is available
 *   - All files access permission granted (Android 11+)
 *   - Battery optimization ignored
 * - If missing permissions: cancel worker, app will show modal when user returns
 * - Silent check (no UI/notification)
 * 
 * @author TXA - fb.com/vlog.txa.2311 - txavlog7@gmail.com
 */
class TXAUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val WORK_NAME = "txa_update_check"
        private const val CHECK_INTERVAL_MINUTES = 3L
        
        /**
         * Schedule the next update check
         */
        fun schedule(context: Context) {
            // Check permissions first
            if (!hasRequiredPermissions(context)) {
                TXALogger.appW("TXAUpdateWorker: Missing permissions, not scheduling")
                cancel(context)
                return
            }
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val workRequest = OneTimeWorkRequestBuilder<TXAUpdateWorker>()
                .setConstraints(constraints)
                .setInitialDelay(CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    1, TimeUnit.MINUTES
                )
                .build()
            
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            
            TXALogger.appI("TXAUpdateWorker scheduled (next check in $CHECK_INTERVAL_MINUTES min)")
        }
        
        /**
         * Cancel the update worker
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            TXALogger.appI("TXAUpdateWorker cancelled")
        }
        
        /**
         * Check if worker has required permissions
         */
        fun hasRequiredPermissions(context: Context): Boolean {
            // Check all files access (Android 11+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    return false
                }
            }
            
            // Check battery optimization ignored
            if (!isBatteryOptimizationIgnored(context)) {
                return false
            }
            
            return true
        }
        
        /**
         * Check if battery optimization is ignored
         */
        private fun isBatteryOptimizationIgnored(context: Context): Boolean {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        }
        
        /**
         * Request all files access permission
         */
        fun requestAllFilesAccess(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:${context.packageName}")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    TXALogger.appE("Failed to open all files access settings", e)
                    // Fallback to general settings
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
        }
        
        /**
         * Request battery optimization ignore
         */
        fun requestIgnoreBatteryOptimization(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:${context.packageName}")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                TXALogger.appE("Failed to request battery optimization ignore", e)
            }
        }
        
        /**
         * Check what permissions are missing
         */
        fun getMissingPermissions(context: Context): List<String> {
            val missing = mutableListOf<String>()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    missing.add("All Files Access")
                }
            }
            
            if (!isBatteryOptimizationIgnored(context)) {
                missing.add("Ignore Battery Optimization")
            }
            
            return missing
        }
    }
    
    override suspend fun doWork(): Result {
        TXALogger.appI("TXAUpdateWorker: Starting update check")
        
        // Re-check permissions
        if (!hasRequiredPermissions(applicationContext)) {
            TXALogger.appW("TXAUpdateWorker: Permissions revoked, stopping worker")
            return Result.failure()
        }
        
        try {
            // Check for updates
            when (val result = TXAUpdateManager.checkForUpdate(applicationContext)) {
                is UpdateCheckResult.UpdateAvailable -> {
                    TXALogger.appI("TXAUpdateWorker: Update available - ${result.updateInfo.versionName}")
                    // Note: Worker runs silently, no notification
                    // Update info will be checked when user opens app/settings
                }
                is UpdateCheckResult.NoUpdate -> {
                    TXALogger.appD("TXAUpdateWorker: No update available")
                }
                is UpdateCheckResult.Error -> {
                    TXALogger.appW("TXAUpdateWorker: Check failed - ${result.message}")
                }
            }
        } catch (e: Exception) {
            TXALogger.appE("TXAUpdateWorker: Exception during check", e)
        }
        
        // Schedule next check
        schedule(applicationContext)
        
        return Result.success()
    }
}
