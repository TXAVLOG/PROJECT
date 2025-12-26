package ms.txams.vv.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import ms.txams.vv.BuildConfig
import ms.txams.vv.core.TXAHttp
import ms.txams.vv.core.TXALogger
import org.json.JSONObject

/**
 * TXA Update Manager
 * 
 * Features:
 * - Check for updates from API
 * - Download APK with progress
 * - Validate and install APK
 * - Retry logic for network errors
 * 
 * API Endpoint:
 * POST /txamusic/api/update/check
 * 
 * @author TXA - fb.com/vlog.txa.2311 - txavlog7@gmail.com
 */
object TXAUpdateManager {
    
    // API endpoint for update check
    private const val UPDATE_ENDPOINT = "https://soft.nrotxa.online/txamusic/api/update/check"
    
    // Retry config
    private const val MAX_DOWNLOAD_RETRIES = 20
    private const val RETRY_DELAY_MS = 5000L
    
    /**
     * Check for updates
     */
    suspend fun checkForUpdate(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        TXALogger.apiI("Checking for updates...")
        
        val currentVersionCode = BuildConfig.VERSION_CODE
        val currentVersionName = BuildConfig.VERSION_NAME
        val locale = "en" // TODO: Get from TXATranslation
        
        TXALogger.apiD("Current version: $currentVersionName ($currentVersionCode)")
        
        try {
            val result = checkEndpoint(UPDATE_ENDPOINT, currentVersionCode, currentVersionName, locale)
            result ?: UpdateCheckResult.Error("Empty response from server")
        } catch (e: Exception) {
            TXALogger.apiE("Update check failed", e)
            UpdateCheckResult.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Check single endpoint
     */
    private fun checkEndpoint(
        endpoint: String,
        versionCode: Int,
        versionName: String,
        locale: String
    ): UpdateCheckResult? {
        TXALogger.apiD("Checking endpoint: $endpoint")
        
        val jsonBody = """
            {
                "packageId": "${BuildConfig.APPLICATION_ID}",
                "versionCode": $versionCode,
                "versionName": "$versionName",
                "locale": "$locale"
            }
        """.trimIndent()
        
        val request = TXAHttp.buildPost(endpoint, jsonBody)
        
        TXAHttp.client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            TXALogger.apiD("Response ${response.code}: ${body?.take(500)}")
            
            if (!response.isSuccessful) {
                return null
            }
            
            if (body == null) {
                return null
            }
            
            return parseUpdateResponse(body, versionCode, versionName)
        }
    }
    
    /**
     * Parse update response JSON
     */
    private fun parseUpdateResponse(
        jsonStr: String,
        currentVersionCode: Int,
        currentVersionName: String
    ): UpdateCheckResult {
        return try {
            val json = JSONObject(jsonStr)
            
            if (!json.optBoolean("ok", false)) {
                val errorCode = json.optString("error_code", "unknown")
                return UpdateCheckResult.Error("Server error: $errorCode")
            }
            
            val updateAvailable = json.optBoolean("update_available", false)
            
            if (!updateAvailable) {
                return UpdateCheckResult.NoUpdate(currentVersionName)
            }
            
            val latest = json.optJSONObject("latest") ?: return UpdateCheckResult.Error("Missing latest info")
            
            val latestVersionCode = latest.optInt("versionCode", 0)
            val latestVersionName = latest.optString("versionName", "")
            val downloadUrl = latest.optString("downloadUrl", "")
            val changelog = latest.optString("changelog", "")
            val releaseDate = latest.optString("releaseDate", "")
            val mandatory = latest.optBoolean("mandatory", false)
            val downloadSize = latest.optLong("downloadSizeBytes", 0)
            
            // Validate
            if (downloadUrl.isEmpty()) {
                return UpdateCheckResult.Error("Download URL missing")
            }
            
            // Double check version comparison
            val shouldUpdate = latestVersionCode > currentVersionCode ||
                    (latestVersionName.isNotEmpty() && latestVersionName != currentVersionName)
            
            if (!shouldUpdate) {
                return UpdateCheckResult.NoUpdate(currentVersionName)
            }
            
            UpdateCheckResult.UpdateAvailable(
                UpdateInfo(
                    versionCode = latestVersionCode,
                    versionName = latestVersionName,
                    downloadUrl = downloadUrl,
                    changelog = changelog,
                    releaseDate = releaseDate,
                    mandatory = mandatory,
                    downloadSizeBytes = downloadSize
                )
            )
        } catch (e: Exception) {
            TXALogger.apiE("Failed to parse update response", e)
            UpdateCheckResult.Error("Parse error: ${e.message}")
        }
    }
    
    /**
     * Download update APK
     * Returns Flow of download progress
     */
    fun downloadUpdate(
        context: Context,
        updateInfo: UpdateInfo
    ): Flow<TXAUpdatePhase> = flow {
        emit(TXAUpdatePhase.Starting)
        
        val destFile = TXADownload.getUpdateApkFile(context)
        
        // Delete existing file
        if (destFile.exists()) {
            destFile.delete()
        }
        
        var retryCount = 0
        var success = false
        
        while (retryCount < MAX_DOWNLOAD_RETRIES && !success) {
            if (retryCount > 0) {
                emit(TXAUpdatePhase.Retrying(retryCount, MAX_DOWNLOAD_RETRIES))
                kotlinx.coroutines.delay(RETRY_DELAY_MS)
            }
            
            try {
                TXADownload.downloadApk(updateInfo.downloadUrl, destFile)
                    .collect { progress ->
                        when (progress.state) {
                            TXADownloadState.RESOLVING -> emit(TXAUpdatePhase.Resolving)
                            TXADownloadState.CONNECTING -> emit(TXAUpdatePhase.Connecting)
                            TXADownloadState.DOWNLOADING -> emit(TXAUpdatePhase.Downloading(
                                progress.downloadedBytes,
                                progress.totalBytes,
                                progress.speed,
                                progress.etaSeconds
                            ))
                            TXADownloadState.COMPLETE -> {
                                success = true
                            }
                            TXADownloadState.ERROR -> {
                                throw Exception(progress.errorMessage ?: "Download error")
                            }
                        }
                    }
            } catch (e: Exception) {
                TXALogger.downloadE("Download attempt ${retryCount + 1} failed", e)
                retryCount++
            }
        }
        
        if (!success) {
            emit(TXAUpdatePhase.Error("Download failed after $MAX_DOWNLOAD_RETRIES retries"))
            return@flow
        }
        
        // Validate APK
        emit(TXAUpdatePhase.Validating)
        
        if (!TXADownload.validateApkFile(context, destFile)) {
            emit(TXAUpdatePhase.Error("Invalid APK file"))
            destFile.delete()
            return@flow
        }
        
        emit(TXAUpdatePhase.ReadyToInstall(destFile))
    }.flowOn(Dispatchers.IO)
    
    /**
     * Install downloaded update
     */
    fun installUpdate(context: Context) {
        val apkFile = TXADownload.getUpdateApkFile(context)
        
        if (!apkFile.exists()) {
            TXALogger.downloadE("Update APK not found")
            return
        }
        
        TXAInstall.installApk(context, apkFile)
    }
}

/**
 * Update check result
 */
sealed class UpdateCheckResult {
    data class UpdateAvailable(val updateInfo: UpdateInfo) : UpdateCheckResult()
    data class NoUpdate(val currentVersion: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

/**
 * Update info from API
 */
@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val changelog: String,
    val releaseDate: String,
    val mandatory: Boolean,
    val downloadSizeBytes: Long
)

/**
 * Update phase for UI
 */
sealed class TXAUpdatePhase {
    object Starting : TXAUpdatePhase()
    object Resolving : TXAUpdatePhase()
    object Connecting : TXAUpdatePhase()
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speed: Double,
        val etaSeconds: Int
    ) : TXAUpdatePhase() {
        val progressPercent: Int
            get() = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
    }
    data class Retrying(val attempt: Int, val maxAttempts: Int) : TXAUpdatePhase()
    object Validating : TXAUpdatePhase()
    data class ReadyToInstall(val apkFile: java.io.File) : TXAUpdatePhase()
    data class Error(val message: String) : TXAUpdatePhase()
}
