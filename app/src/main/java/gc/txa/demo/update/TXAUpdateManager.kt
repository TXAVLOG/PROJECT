package gc.txa.demo.update

import android.content.Context
import android.content.SharedPreferences
import gc.txa.demo.BuildConfig
import gc.txa.demo.core.TXATranslation
import gc.txa.demo.core.TXAHttp
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

object TXAUpdateManager {

    // Temporary force-test block (remove after backend verification)
    private const val FORCE_TEST_MODE = true
    private const val TEST_VERSION_NAME = "3.0.0_txa"
    private const val TEST_CHANGELOG = "Phiên bản test nội bộ 3.0.0_txa – kiểm tra resolver & file manager."
    private const val TEST_DOWNLOAD_URL = "https://www.mediafire.com/file/jdy9nl8o6uqoyvq/TXA_AUTHENTICATOR_3.0.0_txa.apk/file"

    private const val API_BASE = "https://soft.nrotxa.online/txademo/api"
    private const val PREFS_NAME = "txa_update_prefs"
    private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
    private const val KEY_CACHED_VERSION_CODE = "cached_version_code"
    
    private val gson = Gson()
    

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
                val result = performUpdateCheck(context, versionCode, versionName)
                
                // Cache successful result
                if (result is UpdateCheckResult.UpdateAvailable || result is UpdateCheckResult.NoUpdate) {
                    cacheUpdateCheck(context, versionCode)
                }
                
                return result
                
            } catch (e: Exception) {
                if (attempt == maxRetries) {
                    // Final attempt failed, return error
                    return UpdateCheckResult.Error(
                        getErrorMessage(e) ?: TXATranslation.txa("txademo_error_update_check_failed")
                    )
                }
                
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
        
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("User-Agent", "TXADemo-Android/${BuildConfig.VERSION_NAME}")
            .build()
        
        val response = TXAHttp.getClient().newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code}: ${response.message}")
        }
        
        val responseBody = response.body?.string()
            ?: throw IOException("Empty response body")
        
        // Parse response
        val updateResponse = try {
            gson.fromJson(responseBody, UpdateCheckResponse::class.java)
        } catch (e: Exception) {
            throw IOException("Invalid JSON response: ${e.message}")
        }
        
        // Check if update is available
        return if (updateResponse.latestVersion.code > versionCode) {
            UpdateCheckResult.UpdateAvailable(
                UpdateInfo(
                    versionName = updateResponse.latestVersion.name,
                    versionCode = updateResponse.latestVersion.code,
                    downloadUrl = updateResponse.downloadUrl,
                    changelog = "", // Changelog can be fetched separately if needed
                    fileSize = 0L, // Unknown until resolved
                    isForced = updateResponse.forceUpdate
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
     * Update check response from server
     */
    data class UpdateCheckResponse(
        @SerializedName("latestVersion")
        val latestVersion: LatestVersion,
        @SerializedName("downloadUrl")
        val downloadUrl: String,
        @SerializedName("forceUpdate")
        val forceUpdate: Boolean
    )
    
    data class LatestVersion(
        @SerializedName("name")
        val name: String,
        @SerializedName("code")
        val code: Int
    )
    
    /**
     * Update check result
     */
    sealed class UpdateCheckResult {
        data class UpdateAvailable(val updateInfo: UpdateInfo) : UpdateCheckResult()
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
        val isForced: Boolean
    )
}
