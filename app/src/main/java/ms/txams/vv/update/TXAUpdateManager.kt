package ms.txams.vv.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import ms.txams.vv.BuildConfig
import ms.txams.vv.TXAApp
import ms.txams.vv.core.TXAHttp
import ms.txams.vv.core.TXALog
import ms.txams.vv.core.TXATranslation
import ms.txams.vv.download.TXADownloadService
import ms.txams.vv.ui.TXASettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.IOException
import java.io.Serializable

object TXAUpdateManager {

    // Temporary force-test block (remove after backend verification)
    private const val FORCE_TEST_MODE = false
    private const val TEST_VERSION_NAME = "3.0.0_txa"
    private const val TEST_CHANGELOG = "Phiên bản test nội bộ 3.0.0_txa – kiểm tra resolver & file manager."
    private const val TEST_DOWNLOAD_URL = "https://www.mediafire.com/file/jdy9nl8o6uqoyvq/TXA_AUTHENTICATOR_3.0.0_txa.apk/file"

    private const val API_BASE = "https://soft.nrotxa.online/txamusic/api"
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
                    val errorMessage = getErrorMessage(e) ?: TXATranslation.txa("txamusic_error_update_check_failed")
                    TXALog.e(TAG, "Final update check error: $errorMessage")
                    return UpdateCheckResult.Error(errorMessage)
                }
                
                TXALog.w(TAG, "Retrying after ${1000L * attempt}ms delay...")
                // Wait before retry (exponential backoff: 1s, 2s, 4s)
                delay(1000L * attempt)
            }
        }
        
        return UpdateCheckResult.Error(TXATranslation.txa("txamusic_error_update_check_failed"))
    }
    
    /**
     * Perform actual API call to check for updates
     */
    private suspend fun performUpdateCheck(
        context: Context,
        versionCode: Int,
        versionName: String
    ): UpdateCheckResult {
        
        val locale = runCatching { TXAApp.getLocale(context) }.getOrNull().orEmpty()
        val url = buildUpdateCheckUrl(versionCode, versionName, locale)
        TXALog.d(TAG, "Making update check request to: $url")
        TXAHttp.logInfo(context, TAG, "Update check URL: $url")
        
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("User-Agent", "TXAMusic-Android/${BuildConfig.VERSION_NAME}")
            .build()
        
        TXALog.d(TAG, "Executing update check request...")
        
        try {
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
                TXAHttp.logError(context, "UpdateCheck_JSON", e)
                throw IOException("Invalid JSON response: ${e.message}")
            }

            updateResponse.apiVersion?.let {
                TXALog.i(TAG, "Update API version: $it (source=${updateResponse.source.orEmpty()})")
            }

            val latestPayload = updateResponse.latest ?: run {
                TXALog.e(TAG, "Missing latest block in update response: $responseBody")
                throw IOException("Invalid JSON response: missing latest payload")
            }

            val latestVersionCode = latestPayload.versionCode ?: run {
                TXALog.e(TAG, "Missing latest.versionCode in update response: $responseBody")
                throw IOException("Invalid JSON response: missing latest.versionCode")
            }

            val latestVersionName = latestPayload.versionName ?: run {
                TXALog.e(TAG, "Missing latest.versionName in update response: $responseBody")
                throw IOException("Invalid JSON response: missing latest.versionName")
            }

            val latestDownloadUrl = latestPayload.downloadUrl ?: run {
                TXALog.e(TAG, "Missing latest.downloadUrl in update response: $responseBody")
                throw IOException("Invalid JSON response: missing latest.downloadUrl")
            }

            val latestChangelog = latestPayload.changelog ?: ""
            val latestFileSize = latestPayload.downloadSizeBytes ?: 0L
            val isForceUpdate = latestPayload.mandatory ?: false

            val updateAvailable = latestVersionCode > versionCode
            TXALog.i(TAG, "Update available: $updateAvailable (current: $versionCode, latest: $latestVersionCode)")

            return if (updateAvailable) {
                UpdateCheckResult.UpdateAvailable(
                    UpdateInfo(
                        versionName = latestVersionName,
                        versionCode = latestVersionCode,
                        downloadUrl = latestDownloadUrl,
                        changelog = latestChangelog,
                        fileSize = latestFileSize,
                        isForced = isForceUpdate
                    )
                )
            } else {
                UpdateCheckResult.NoUpdate
            }
            
        } catch (e: IOException) {
            TXALog.e(TAG, "Network error during update check", e)
            TXAHttp.logError(context, "UpdateCheck_Network", e)
            throw e
        } catch (e: Exception) {
            TXALog.e(TAG, "Unexpected error during update check", e)
            TXAHttp.logError(context, "UpdateCheck_Unexpected", e)
            throw e
        }
    
    private fun buildUpdateCheckUrl(
        versionCode: Int,
        versionName: String,
        locale: String
    ): String {
        val baseUrl = "$API_BASE/update/check"
        val httpUrl = baseUrl.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid API base: $baseUrl")

        val fullUrl = httpUrl.newBuilder()
            .addQueryParameter("versionCode", versionCode.toString())
            .addQueryParameter("versionName", versionName)
            .apply {
                if (locale.isNotBlank()) {
                    addQueryParameter("locale", locale)
                }
            }
            .build()
            .toString()

        TXALog.d(TAG, "Built update check URL: $fullUrl")
        return fullUrl
    }
    
    /**
     * Get user-friendly error message from exception
     */
    private fun getErrorMessage(exception: Exception): String? {
        return when {
            exception is IOException && exception.message?.contains("HTTP 404") == true -> 
                TXATranslation.txa("txamusic_error_metadata_unavailable")
            exception is IOException && exception.message?.contains("HTTP") == true -> 
                TXATranslation.txa("txamusic_error_network")
            exception.message?.contains("JSON") == true -> 
                TXATranslation.txa("txamusic_error_invalid_metadata")
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

    fun showUpdateAvailableNotification(context: Context, updateInfo: UpdateInfo) {
        ensureUpdateNotificationChannel(context)

        val content = String.format(
            TXATranslation.txa("txamusic_update_notification_body"),
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
            .setContentTitle(TXATranslation.txa("txamusic_update_available"))
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
            TXATranslation.txa("txamusic_update_notification_channel_name"),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = TXATranslation.txa("txamusic_update_notification_channel_description")
        }
        manager.createNotificationChannel(channel)
    }

    data class UpdateInfo(
        val versionName: String,
        val versionCode: Int,
        val downloadUrl: String,
        val changelog: String,
        val fileSize: Long,
        val isForced: Boolean,
        val updatedAt: String? = null
    ) : Serializable

    sealed class UpdateCheckResult {
        data class UpdateAvailable(val updateInfo: UpdateInfo) : UpdateCheckResult()
        object NoUpdate : UpdateCheckResult()
        data class Error(val message: String) : UpdateCheckResult()
    }

    data class UpdateCheckResponse(
        val ok: Boolean = false,
        val source: String? = null,
        @SerializedName("update_available")
        val updateAvailable: Boolean = false,
        val client: ClientInfo? = null,
        val latest: LatestPayload? = null,
        @SerializedName("api_version")
        val apiVersion: String? = null
    )

    data class ClientInfo(
        @SerializedName(value = "version_code", alternate = ["versionCode"])
        val versionCode: Int? = null,
        @SerializedName(value = "version_name", alternate = ["versionName"])
        val versionName: String? = null
    )

    data class LatestPayload(
        @SerializedName(value = "version_code", alternate = ["versionCode"])
        val versionCode: Int?,
        @SerializedName(value = "version_name", alternate = ["versionName"])
        val versionName: String?,
        @SerializedName(value = "download_url", alternate = ["downloadUrl"])
        val downloadUrl: String?,
        @SerializedName(value = "release_date", alternate = ["releaseDate"])
        val releaseDate: String? = null,
        val mandatory: Boolean = false,
        val changelog: String? = null
    )
}
