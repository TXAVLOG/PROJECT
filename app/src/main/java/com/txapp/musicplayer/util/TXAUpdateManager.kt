package com.txapp.musicplayer.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.txapp.musicplayer.service.TXADownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CancellationException

data class UpdateInfo(
    val updateAvailable: Boolean,
    val forceUpdate: Boolean = false,
    val latestVersionCode: Int = 0,
    val latestVersionName: String = "",
    val downloadUrl: String = "",
    val downloadSizeBytes: Long = 0,
    val changelog: String = "",
    val releaseDate: String = "",
    val checksumType: String = "",
    val checksumValue: String = ""
)

// DownloadState is now in TXADownloader.kt

object TXAUpdateManager {
    private const val API_URL = "https://soft.nrotxa.online/txamusic/api/update/check"
    
    // Global Download State
    private val _downloadState = MutableStateFlow<DownloadState?>(null)
    val downloadState = _downloadState.asStateFlow()

    var isResolving = MutableStateFlow(false)
    val updateInfo = MutableStateFlow<UpdateInfo?>(null)

    const val ACTION_START = "com.txapp.musicplayer.action.START_DOWNLOAD"
    const val ACTION_STOP = "com.txapp.musicplayer.action.STOP_DOWNLOAD"
    const val ACTION_CANCEL = "com.txapp.musicplayer.action.CANCEL_DOWNLOAD"
    const val ACTION_PAUSE = "com.txapp.musicplayer.action.PAUSE_DOWNLOAD"
    const val EXTRA_UPDATE_INFO = "extra_update_info"

    // TEST MODE
    private const val IS_TEST_MODE = true
    private const val TEST_URL = "https://www.mediafire.com/file/5zzvr5xcs6edjym/TXAMUSIC_v2.9.2_txa.apk/file"

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        if (IS_TEST_MODE) {
            val info = UpdateInfo(
                updateAvailable = true,
                forceUpdate = false,
                latestVersionCode = 999999,
                latestVersionName = "Test Ver",
                downloadUrl = TEST_URL,
                changelog = "Testing Download System...",
                releaseDate = "2026-01-16"
            )
            updateInfo.value = info
            return@withContext info
        }
        try {
            val jsonBody = JSONObject().apply {
                put("packageId", "com.txapp.musicplayer")
                put("versionCode", TXADeviceInfo.getVersionCode())
                put("versionName", TXADeviceInfo.getVersionName())
                put("platform", "android")
                put("locale", TXATranslation.getSystemLanguage())
                // put("debug", BuildConfig.DEBUG) // Simplified
            }

            val request = Request.Builder()
                .url(API_URL)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val fastClient = TXAHttp.client.newBuilder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val response = fastClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                
                if (json.optBoolean("ok") && json.optBoolean("update_available")) {
                    val latest = json.getJSONObject("latest")
                    val latestVersionCode = latest.optInt("versionCode")
                    val currentVersionCode = TXADeviceInfo.getVersionCode()
                    
                    
                    val checksum = latest.optJSONObject("checksum")
                    
                    val info = UpdateInfo(
                        updateAvailable = true,
                        forceUpdate = json.optBoolean("force_update") || latest.optBoolean("mandatory"),
                        latestVersionCode = latestVersionCode,
                        latestVersionName = latest.optString("versionName"),
                        downloadUrl = latest.optString("downloadUrl"),
                        downloadSizeBytes = latest.optLong("downloadSizeBytes"),
                        changelog = latest.optString("changelog"),
                        releaseDate = latest.optString("releaseDate"),
                        checksumType = checksum?.optString("type") ?: "",
                        checksumValue = checksum?.optString("value") ?: ""
                    )

                     if (latestVersionCode <= currentVersionCode) {
                        TXALogger.apiI("UpdateManager", "Server returned older or same version ($latestVersionCode <= $currentVersionCode)")
                        // Logic changed: return null for checkForUpdate usage, but we need a way to get changelog.
                        // We will keep existing behavior for checkForUpdate() but add fetchChangelog() separately.
                        return@withContext null
                    }
                    
                    updateInfo.value = info
                    
                    // Save pending changelog for post-update display
                    TXAPreferences.setPendingChangelog(latestVersionCode.toLong(), info.changelog)
                    
                    return@withContext info
                }
            }
            null
        } catch (e: Exception) {
            TXALogger.apiE("UpdateManager", "Update check failed", e)
            null
        }
    }

    /**
     * Fetch changelog for a specific version from server
     */
    suspend fun fetchChangelog(versionCode: Long? = null, locale: String? = null): String? = withContext(Dispatchers.IO) {
        try {
            val targetVersion = versionCode ?: TXADeviceInfo.getVersionCode()
            val targetLocale = locale ?: TXATranslation.getSystemLanguage()
            
            // New API endpoint structure: /api/changelog/{version}?locale={locale}
            // Base URL: https://soft.nrotxa.online/txamusic/api
            val baseUrl = "https://soft.nrotxa.online/txamusic/api/changelog/$targetVersion"
            val urlWithParams = "$baseUrl?locale=$targetLocale"

            val request = Request.Builder()
                .url(urlWithParams)
                .get()
                .build()

            val response = TXAHttp.client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                
                if (json.optBoolean("ok")) {
                    return@withContext json.optString("changelog")
                }
            }
            null
        } catch (e: Exception) {
            TXALogger.apiE("UpdateManager", "Fetch changelog for $versionCode failed", e)
            null
        }
    }

    fun startDownload(context: Context, info: UpdateInfo) {
        val intent = Intent(context, TXADownloadService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_UPDATE_INFO, info.downloadUrl)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopDownload(context: Context) {
        TXALogger.downloadI("UpdateManager", "Stopping download service/job")
        val intent = Intent(context, TXADownloadService::class.java).apply {
            action = ACTION_STOP
        }
        context.startService(intent)
        _downloadState.value = null
    }

    fun resetDownloadState() {
        TXALogger.downloadI("UpdateManager", "Global state reset")
        _downloadState.value = null
    }

    suspend fun downloadApk(context: Context, url: String): Flow<DownloadState> = channelFlow {
        _downloadState.value = null
        try {
            isResolving.value = true
            TXALogger.resolveI("UpdateManager", "Starting resolve for: $url")
            val resolveResult = TXADownloadUrlResolver.resolve(url)
            val directUrl = resolveResult.getOrElse {
                TXALogger.resolveE("UpdateManager", "Resolve failed: ${it.message}")
                throw it
            }
            isResolving.value = false
            TXALogger.resolveI("UpdateManager", "Resolved to Direct: $directUrl")

            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val destination = File(baseDir, "update.apk")

            TXADownloader.download(context, directUrl, destination).collect { state ->
                _downloadState.value = state
                send(state)
            }

        } catch (e: Throwable) {
            isResolving.value = false
            if (e is CancellationException) {
                _downloadState.value = null
            } else {
                TXALogger.downloadE("UpdateManager", "Download failed", e)
                if (TXACrashHandler.isFatalError(e)) {
                    TXACrashHandler.reportFatalError(context, e, "UpdateManagerDownload")
                } else {
                    val error = DownloadState.Error(e.message ?: "Download failed")
                    _downloadState.value = error
                    send(error)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    fun canInstallApp(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
    }

    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    }

    fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
