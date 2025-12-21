package gc.txa.demo.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import gc.txa.demo.BuildConfig
import gc.txa.demo.core.TXATranslation
import gc.txa.demo.core.TXAHttp
import gc.txa.demo.core.TXALog
import gc.txa.demo.download.TXADownloadService
import gc.txa.demo.ui.TXASettingsActivity
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException
import java.io.Serializable
import java.util.regex.Pattern

object TXAUpdateManager {

    // Temporary force-test block (remove after backend verification)
    private const val FORCE_TEST_MODE = false
    private const val TEST_VERSION_NAME = "3.0.0_txa"
    private const val TEST_CHANGELOG = "Phiên bản test nội bộ 3.0.0_txa – kiểm tra resolver & file manager."
    private const val TEST_DOWNLOAD_URL = "https://www.mediafire.com/file/jdy9nl8o6uqoyvq/TXA_AUTHENTICATOR_3.0.0_txa.apk/file"

    private const val API_BASE = "https://soft.nrotxa.online/txademo/api"
    private const val PREFS_NAME = "txa_update_prefs"
    private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
    private const val KEY_CACHED_VERSION_CODE = "cached_version_code"
    private const val UPDATE_NOTIFICATION_CHANNEL_ID = "txa_update_alerts"
    private const val UPDATE_NOTIFICATION_ID = 2001
    
    private val gson = Gson()
    private const val TAG = "UpdateManager"
    

    /**
     * Check for updates with retry logic
     */
    suspend fun checkForUpdate(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        // Force test mode: always return fake update
        if (FORCE_TEST_MODE) {
            return@withContext UpdateCheckResult.UpdateAvailable(
                UpdateInfo(
                    versionName = TEST_VERSION_NAME,
                    versionCode = 300,
                    downloadUrl = TEST_DOWNLOAD_URL,
                    changelog = TEST_CHANGELOG,
                    fileSize = 0L,
                    isForced = false
                )
            )
        }

        val currentVersionCode = BuildConfig.VERSION_CODE
        val currentVersionName = BuildConfig.VERSION_NAME
        
        // Check with retry logic
        return@withContext checkForUpdateWithRetry(context, currentVersionCode, currentVersionName)
    }
    
    /**
     * Check for updates with exponential backoff retry
     */
    private suspend fun checkForUpdateWithRetry(
        context: Context, 
        versionCode: Int, 
        versionName: String,
        maxRetries: Int = 3
    ): UpdateCheckResult {
        
        for (attempt in 1..maxRetries) {
            try {
                TXALog.i(TAG, "Attempt $attempt: Checking for updates (v$versionName, code $versionCode)")
                val result = performUpdateCheck(context, versionCode, versionName)
                
                TXALog.i(TAG, "Update check result: $result")
                
                // Cache successful result
                if (result is UpdateCheckResult.UpdateAvailable || result is UpdateCheckResult.NoUpdate) {
                    cacheUpdateCheck(context, versionCode)
                }
                
                return result
                
            } catch (e: Exception) {
                TXALog.e(TAG, "Update check failed on attempt $attempt", e)
                
                if (attempt == maxRetries) {
                    // Final attempt failed, return error
                    val errorMessage = getErrorMessage(e) ?: TXATranslation.txa("txademo_error_update_check_failed")
                    TXALog.e(TAG, "Final update check error: $errorMessage")
                    return UpdateCheckResult.Error(errorMessage)
                }
                
                TXALog.w(TAG, "Retrying after ${1000L * attempt}ms delay...")
                // Wait before retry (exponential backoff: 1s, 2s, 4s)
                delay(1000L * attempt)
            }
        }
        
        return UpdateCheckResult.Error(TXATranslation.txa("txademo_error_update_check_failed"))
    }
    
    /**
     * Perform actual API call to check for updates
     */
    private suspend fun performUpdateCheck(
        context: Context,
        versionCode: Int,
        versionName: String
    ): UpdateCheckResult {
        
        val url = "$API_BASE/update/check?versionCode=$versionCode&versionName=$versionName"
        TXALog.d(TAG, "Making update check request to: $url")
        TXAHttp.logInfo(context, TAG, "Update check URL: $url")
        
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("User-Agent", "TXADemo-Android/${BuildConfig.VERSION_NAME}")
            .build()
        
        TXALog.d(TAG, "Executing update check request...")
        val response = TXAHttp.client.newCall(request).execute()
        TXALog.d(TAG, "Update check response: ${response.code} - ${response.message}")
        
        if (!response.isSuccessful) {
            TXALog.e(TAG, "Update check HTTP error: ${response.code} - ${response.message}")
            throw IOException("HTTP ${response.code}: ${response.message}")
        }
        
        val responseBody = response.body?.string()
            ?: throw IOException("Empty response body")
        
        TXALog.d(TAG, "Update check response length: ${responseBody.length} chars")
        TXALog.v(TAG, "Update check response preview: ${responseBody.take(200)}...")
        
        // Parse response
        val updateResponse = try {
            gson.fromJson(responseBody, UpdateCheckResponse::class.java)
        } catch (e: Exception) {
            TXALog.e(TAG, "Update check JSON parsing failed", e)
            throw IOException("Invalid JSON response: ${e.message}")
        }

        updateResponse.apiVersion?.let {
            TXALog.i(TAG, "Update API version: $it (source=${updateResponse.source.orEmpty()})")
        }

        val latestPayload = updateResponse.latest
            ?: updateResponse.legacyLatestVersion?.let { legacy ->
                LatestPayload(
                    versionCode = legacy.code,
                    versionName = legacy.name,
                    downloadUrl = updateResponse.legacyDownloadUrl,
                    releaseDate = updateResponse.legacyUpdatedAt,
                    mandatory = updateResponse.legacyForceUpdate,
                    changelog = updateResponse.legacyChangelog
                )
            }

        val latestVersionCode = latestPayload?.versionCode ?: run {
            TXALog.e(TAG, "Missing latest.versionCode in update response: $responseBody")
            throw IOException("Invalid JSON response: missing latest.versionCode")
        }

        val latestVersionName = latestPayload.versionName ?: run {
            TXALog.e(TAG, "Missing latest.versionName in update response: $responseBody")
            throw IOException("Invalid JSON response: missing latest.versionName")
        }

        val downloadUrl = latestPayload.downloadUrl ?: run {
            TXALog.e(TAG, "Missing latest.downloadUrl in update response: $responseBody")
            throw IOException("Invalid JSON response: missing latest.downloadUrl")
        }

        TXALog.i(
            TAG,
            "Parsed update response: latest=$latestVersionName ($latestVersionCode), current=$versionName ($versionCode)"
        )
        
        val apiSignalsUpdate = updateResponse.updateAvailable
        val shouldUpdate = apiSignalsUpdate || latestVersionCode > versionCode

        if (!shouldUpdate) {
            return UpdateCheckResult.NoUpdate
        }

        val changelogContent = latestPayload.changelog?.takeIf { it.isNotBlank() }
            ?: TXATranslation.txa("txademo_update_changelog")

        val forced = latestPayload.mandatory || (
            updateResponse.legacyMinVersionCode?.let { versionCode < it } ?: false
        )

        return if (latestVersionCode > versionCode) {
            UpdateCheckResult.UpdateAvailable(
                UpdateInfo(
                    versionName = latestVersionName,
                    versionCode = latestVersionCode,
                    downloadUrl = downloadUrl,
                    changelog = changelogContent,
                    fileSize = 0L, // Unknown until resolved
                    isForced = forced,
                    updatedAt = latestPayload.releaseDate ?: updateResponse.legacyUpdatedAt
                )
            )
        } else {
            UpdateCheckResult.NoUpdate
        }
    }
    
    /**
     * Get user-friendly error message from exception
     */
    private fun getErrorMessage(exception: Exception): String? {
        return when {
            exception is IOException && exception.message?.contains("HTTP 404") == true -> 
                TXATranslation.txa("txademo_error_metadata_unavailable")
            exception is IOException && exception.message?.contains("HTTP") == true -> 
                TXATranslation.txa("txademo_error_network")
            exception.message?.contains("JSON") == true -> 
                TXATranslation.txa("txademo_error_invalid_metadata")
            else -> exception.message
        }
    }
    
    /**
     * Cache update check result to avoid frequent API calls
     */
    private fun cacheUpdateCheck(context: Context, versionCode: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_LAST_UPDATE_CHECK, System.currentTimeMillis())
            .putInt(KEY_CACHED_VERSION_CODE, versionCode)
            .apply()
    }
    
    /**
     * Check if we should perform update check (throttling)
     */
    fun shouldCheckForUpdates(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0)
        val currentTime = System.currentTimeMillis()
        
        // Don't check more than once per hour
        return currentTime - lastCheck > 60 * 60 * 1000
    }

    /**
     * Get current app version
     */
    fun getCurrentVersion(): String {
        return BuildConfig.VERSION_NAME
    }

    /**
     * Get current version code
     */
    fun getCurrentVersionCode(): Int {
        return BuildConfig.VERSION_CODE
    }

    /**
     * Start background download for update
     */
    fun startBackgroundDownload(context: Context, updateInfo: UpdateInfo) {
        TXALog.i(TAG, "Starting background download for ${updateInfo.versionName}")
        
        val intent = Intent(context, TXADownloadService::class.java).apply {
            putExtra("download_url", updateInfo.downloadUrl)
            putExtra("version_name", updateInfo.versionName)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    /**
     * Check if download is currently active
     */
    fun isDownloadActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences("txa_download_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("is_downloading", false)
    }

    /**
     * Get current download progress
     */
    fun getDownloadProgress(context: Context): Int {
        val prefs = context.getSharedPreferences("txa_download_prefs", Context.MODE_PRIVATE)
        return prefs.getInt("download_progress", 0)
    }
    
    if (!response.isSuccessful) {
        TXALog.e(TAG, "Update check HTTP error: ${response.code} - ${response.message}")
        throw IOException("HTTP ${response.code}: ${response.message}")
        object NoUpdate : UpdateCheckResult()
        data class Error(val message: String) : UpdateCheckResult()
    }

    /**
     * Update information
     */
    data class UpdateInfo(
        val versionName: String,
        val versionCode: Int,
        val downloadUrl: String,
        val changelog: String,
        val fileSize: Long,
        val isForced: Boolean,
        val updatedAt: String? = null
    ) : Serializable

    fun showUpdateAvailableNotification(context: Context, updateInfo: UpdateInfo) {
        ensureUpdateNotificationChannel(context)

        val content = String.format(
            TXATranslation.txa("txademo_update_notification_body"),
            updateInfo.versionName
        )

        val intent = Intent(context, TXASettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(TXASettingsActivity.EXTRA_LAUNCH_FROM_UPDATE_NOTIFICATION, true)
            putExtra(TXASettingsActivity.EXTRA_AUTO_START_DOWNLOAD, true)
            putExtra(TXASettingsActivity.EXTRA_UPDATE_INFO, updateInfo)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            UPDATE_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, UPDATE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(TXATranslation.txa("txademo_update_available"))
            .setContentText(content)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(UPDATE_NOTIFICATION_ID, builder.build())
    }

    private fun ensureUpdateNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(UPDATE_NOTIFICATION_CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            UPDATE_NOTIFICATION_CHANNEL_ID,
            TXATranslation.txa("txademo_update_notification_channel_name"),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = TXATranslation.txa("txademo_update_notification_channel_description")
        }
        manager.createNotificationChannel(channel)
    }

}
