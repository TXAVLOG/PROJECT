package ms.txams.vv.update

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import ms.txams.vv.core.TXAHttp
import ms.txams.vv.core.TXALogger
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * TXA Download Manager
 * 
 * Features:
 * - Resolve direct download URL (via TXADownloadUrlResolver)
 * - Stream file to disk with progress
 * - Validate content-type/size to avoid downloading HTML
 * - Emit Flow<TXADownloadProgress> for UI updates
 * - Validate APK file after download
 * 
 * @author TXA - fb.com/vlog.txa.2311 - txavlog7@gmail.com
 */
object TXADownload {
    
    private const val BUFFER_SIZE = 8 * 1024 // 8KB buffer
    
    /**
     * Download APK from URL to destination file
     * Resolves redirects and special links (MediaFire, Drive, GitHub) first
     * 
     * @param url Original download URL
     * @param destFile Destination file
     * @return Flow of download progress
     */
    fun downloadApk(
        url: String,
        destFile: File
    ): Flow<TXADownloadProgress> = flow {
        TXALogger.downloadI("Starting download: $url")
        TXALogger.downloadI("Destination: ${destFile.absolutePath}")
        
        emit(TXADownloadProgress(0, 0, 0.0, 0, TXADownloadState.RESOLVING))
        
        // Step 1: Resolve direct download URL
        val resolveResult = TXADownloadUrlResolver.resolve(url)
        
        when (resolveResult) {
            is TXADownloadUrlResolver.ResolveResult.Error -> {
                TXALogger.downloadE("URL resolve failed: ${resolveResult.message}")
                emit(TXADownloadProgress(0, 0, 0.0, 0, TXADownloadState.ERROR, resolveResult.message))
                return@flow
            }
            is TXADownloadUrlResolver.ResolveResult.Success -> {
                TXALogger.downloadI("Resolved URL: ${resolveResult.directUrl}")
                TXALogger.downloadI("Filename: ${resolveResult.fileName}")
            }
        }
        
        val directUrl = (resolveResult as TXADownloadUrlResolver.ResolveResult.Success).directUrl
        
        emit(TXADownloadProgress(0, 0, 0.0, 0, TXADownloadState.CONNECTING))
        
        // Step 2: Create request and download
        val request = Request.Builder()
            .url(directUrl)
            .header("User-Agent", "TXAMusic/1.0 (Android)")
            .build()
        
        try {
            TXAHttp.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorMsg = "HTTP ${response.code}: ${response.message}"
                    TXALogger.downloadE("Download failed: $errorMsg")
                    emit(TXADownloadProgress(0, 0, 0.0, 0, TXADownloadState.ERROR, errorMsg))
                    return@flow
                }
                
                // Validate content type
                val contentType = response.header("Content-Type", "")
                if (contentType?.contains("text/html") == true) {
                    val errorMsg = "Invalid content type: $contentType (expected APK)"
                    TXALogger.downloadE(errorMsg)
                    emit(TXADownloadProgress(0, 0, 0.0, 0, TXADownloadState.ERROR, errorMsg))
                    return@flow
                }
                
                val body = response.body ?: run {
                    emit(TXADownloadProgress(0, 0, 0.0, 0, TXADownloadState.ERROR, "Empty response body"))
                    return@flow
                }
                
                val totalBytes = body.contentLength().takeIf { it > 0 } ?: -1L
                TXALogger.downloadI("Total size: $totalBytes bytes")
                
                emit(TXADownloadProgress(0, totalBytes, 0.0, 0, TXADownloadState.DOWNLOADING))
                
                // Create parent directory
                destFile.parentFile?.mkdirs()
                
                // Stream to file
                var downloadedBytes = 0L
                var lastEmitTime = System.currentTimeMillis()
                var lastEmitBytes = 0L
                val startTime = System.currentTimeMillis()
                
                body.byteStream().use { inputStream ->
                    FileOutputStream(destFile).use { outputStream ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            
                            // Emit progress every 100ms
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastEmitTime >= 100) {
                                val elapsedSec = (currentTime - startTime) / 1000.0
                                val speed = if (elapsedSec > 0) (downloadedBytes / elapsedSec).toLong() else 0L
                                val eta = if (speed > 0 && totalBytes > 0) {
                                    ((totalBytes - downloadedBytes) / speed).toInt()
                                } else 0
                                
                                emit(TXADownloadProgress(
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                    speed = speed.toDouble(),
                                    etaSeconds = eta,
                                    state = TXADownloadState.DOWNLOADING
                                ))
                                
                                lastEmitTime = currentTime
                                lastEmitBytes = downloadedBytes
                            }
                        }
                    }
                }
                
                TXALogger.downloadI("Download complete: ${destFile.length()} bytes")
                emit(TXADownloadProgress(downloadedBytes, totalBytes, 0.0, 0, TXADownloadState.COMPLETE))
            }
        } catch (e: Exception) {
            TXALogger.downloadE("Download exception", e)
            emit(TXADownloadProgress(0, 0, 0.0, 0, TXADownloadState.ERROR, e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Validate APK file using PackageManager
     */
    fun validateApkFile(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists()) {
            TXALogger.downloadE("APK file not found: ${apkFile.absolutePath}")
            return false
        }
        
        if (apkFile.length() < 1024) {
            TXALogger.downloadE("APK file too small: ${apkFile.length()} bytes")
            return false
        }
        
        return try {
            val packageInfo = context.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.GET_ACTIVITIES
            )
            
            if (packageInfo != null) {
                TXALogger.downloadI("APK validated: ${packageInfo.packageName} v${packageInfo.versionName}")
                true
            } else {
                TXALogger.downloadE("APK validation failed: PackageInfo is null")
                false
            }
        } catch (e: Exception) {
            TXALogger.downloadE("APK validation exception", e)
            false
        }
    }
    
    /**
     * Get updates directory
     */
    fun getUpdatesDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "updates")
        dir.mkdirs()
        return dir
    }
    
    /**
     * Get APK file path for update
     */
    fun getUpdateApkFile(context: Context): File {
        return File(getUpdatesDir(context), "TXAMusic-UPDATE.apk")
    }
}

/**
 * Download progress data
 */
data class TXADownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speed: Double, // bytes per second
    val etaSeconds: Int,
    val state: TXADownloadState,
    val errorMessage: String? = null
) {
    val progressPercent: Int
        get() = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
    
    val isComplete: Boolean
        get() = state == TXADownloadState.COMPLETE
    
    val isError: Boolean
        get() = state == TXADownloadState.ERROR
}

/**
 * Download state enum
 */
enum class TXADownloadState {
    RESOLVING,      // Resolving redirect URLs
    CONNECTING,     // Connecting to server
    DOWNLOADING,    // Downloading file
    COMPLETE,       // Download complete
    ERROR           // Error occurred
}
