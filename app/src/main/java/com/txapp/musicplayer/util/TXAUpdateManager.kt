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
import java.io.FileOutputStream
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

sealed class DownloadState {
    data class Progress(val percentage: Int, val downloaded: Long, val total: Long, val bps: Long) : DownloadState()
    data class Success(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

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

                    // Safety check: local version must be lower than latest version to show update
                    // BUT if we just want to fetch info (e.g. for changelog display), we might want it anyway.
                    // The standard checkForUpdate() returns null if no update needed.
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
            if (!baseDir.exists()) TXASuHelper.mkdirs(baseDir)
            val destination = File(baseDir, "update.apk")
            if (destination.exists()) destination.delete()

            val headRequest = Request.Builder().url(directUrl).head().build()
            val headResponse = TXAHttp.client.newCall(headRequest).execute()
            val acceptRanges = headResponse.header("Accept-Ranges")
            val contentLength = headResponse.header("Content-Length")?.toLongOrNull() ?: -1L
            headResponse.close()

            val isTurboSupported = acceptRanges == "bytes" && contentLength > 0
            val shouldUseTurbo = isTurboSupported && contentLength > 10 * 1024 * 1024

            if (shouldUseTurbo) {
                TXALogger.downloadI("UpdateManager", "Turbo Download Activated! Size: ${TXAFormat.formatSize(contentLength)}")
                
                java.io.RandomAccessFile(destination, "rw").use { it.setLength(contentLength) }
                
                val threadCount = 4
                val chunkSize = contentLength / threadCount
                val chunks = ArrayList<Job>()
                val downloadedBytes = java.util.concurrent.atomic.AtomicLong(0)
                val startTime = System.currentTimeMillis()
                
                coroutineScope {
                    for (i in 0 until threadCount) {
                        val start = i * chunkSize
                        val end = if (i == threadCount - 1) contentLength - 1 else (start + chunkSize - 1)
                        
                        chunks.add(launch(Dispatchers.IO) {
                            val rangeHeader = "bytes=$start-$end"
                            val request = Request.Builder()
                                .url(directUrl)
                                .header("Range", rangeHeader)
                                .build()
                            
                            val response = TXAHttp.client.newCall(request).execute()
                            if (!response.isSuccessful) throw Exception("Chunk $i failed: ${response.code}")
                            
                            val body = response.body ?: throw Exception("Chunk $i body null")
                            val inputStream = body.byteStream()
                            val raf = java.io.RandomAccessFile(destination, "rw")
                            raf.seek(start)
                            
                            val buffer = ByteArray(8192)
                            var read: Int
                            
                            try {
                                while (inputStream.read(buffer).also { read = it } != -1) {
                                    if (!isActive) throw CancellationException()
                                    raf.write(buffer, 0, read)
                                    downloadedBytes.addAndGet(read.toLong())
                                }
                            } finally {
                                raf.close()
                                response.close()
                            }
                        })
                    }
                    
                    val reporter = launch(Dispatchers.IO) {
                        while (isActive) {
                            val current = downloadedBytes.get()
                            if (current >= contentLength) break
                            
                            val now = System.currentTimeMillis()
                            val timePassed = (now - startTime + 1) / 1000f
                            val bps = (current / timePassed).toLong()
                            val progress = ((current * 100) / contentLength).toInt()
                            
                            val state = DownloadState.Progress(progress, current, contentLength, bps)
                            _downloadState.value = state
                            send(state)
                            
                            delay(500)
                        }
                    }
                    
                    chunks.joinAll()
                    reporter.cancel()
                }
                
                TXALogger.downloadI("UpdateManager", "Turbo Download Complete")
                val success = DownloadState.Success(destination)
                _downloadState.value = success
                send(success)

            } else {
                TXALogger.downloadI("UpdateManager", "Standard Download (No Turbo or Small File)")
                
                val request = Request.Builder()
                    .url(directUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                    .build()
                
                val response = TXAHttp.client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    val errorMsg = "HTTP Error: ${response.code} ${response.message}"
                    TXALogger.apiE("UpdateManager", errorMsg)
                    throw Exception(errorMsg)
                }
                
                val body = response.body ?: throw Exception("Response body null")
                val totalBytes = body.contentLength()
                
                body.byteStream().use { inputStream ->
                    FileOutputStream(destination).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var downloadedBytes = 0L
                        var bytesRead: Int
                        var lastUpdate = 0L
                        val startTime = System.currentTimeMillis()
                        
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            if (!currentCoroutineContext().isActive) {
                                throw CancellationException("User cancelled")
                            }

                            outputStream.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 500) {
                                val timePassed = (now - startTime + 1) / 1000f
                                val bps = (downloadedBytes / timePassed).toLong()
                                val progress = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
                                val state = DownloadState.Progress(progress, downloadedBytes, totalBytes, bps)
                                _downloadState.value = state
                                send(state)
                                lastUpdate = now
                            }
                        }
                    }
                }
                TXALogger.downloadI("UpdateManager", "Download success")
                val success = DownloadState.Success(destination)
                _downloadState.value = success
                send(success)
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
