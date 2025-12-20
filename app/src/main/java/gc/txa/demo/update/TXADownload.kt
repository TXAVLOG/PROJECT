package gc.txa.demo.update

import gc.txa.demo.core.TXAHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

object TXADownload {

    /**
     * Download file with progress tracking
     */
    fun downloadFile(url: String, outputFile: File): Flow<DownloadProgress> = flow {
        try {
            emit(DownloadProgress.Started)

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = TXAHttp.getClient().newCall(request).execute()
            
            if (!response.isSuccessful) {
                emit(DownloadProgress.Failed("HTTP ${response.code}: ${response.message}"))
                return@flow
            }

            val body = response.body
            if (body == null) {
                emit(DownloadProgress.Failed("Empty response body"))
                return@flow
            }

            val totalBytes = body.contentLength()
            if (totalBytes <= 0) {
                emit(DownloadProgress.Failed("Unknown file size"))
                return@flow
            }

            // Create parent directories if needed
            outputFile.parentFile?.mkdirs()

            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(outputFile)

            var downloadedBytes = 0L
            var lastEmitTime = System.currentTimeMillis()
            var lastDownloadedBytes = 0L
            val buffer = ByteArray(8192)
            var bytesRead: Int

            try {
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val currentTime = System.currentTimeMillis()
                    val timeDiff = currentTime - lastEmitTime

                    // Emit progress every 500ms
                    if (timeDiff >= 500 || downloadedBytes == totalBytes) {
                        val bytesDiff = downloadedBytes - lastDownloadedBytes
                        val speed = if (timeDiff > 0) {
                            (bytesDiff * 1000 / timeDiff)
                        } else {
                            0L
                        }

                        val eta = if (speed > 0) {
                            (totalBytes - downloadedBytes) / speed
                        } else {
                            0L
                        }

                        emit(
                            DownloadProgress.Downloading(
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                                speed = speed,
                                eta = eta
                            )
                        )

                        lastEmitTime = currentTime
                        lastDownloadedBytes = downloadedBytes
                    }
                }

                outputStream.flush()
                emit(DownloadProgress.Completed(outputFile))
            } finally {
                inputStream.close()
                outputStream.close()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            emit(DownloadProgress.Failed(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Download progress states
     */
    sealed class DownloadProgress {
        object Started : DownloadProgress()
        
        data class Downloading(
            val downloadedBytes: Long,
            val totalBytes: Long,
            val speed: Long, // bytes per second
            val eta: Long // seconds
        ) : DownloadProgress()
        
        data class Completed(val file: File) : DownloadProgress()
        data class Failed(val error: String) : DownloadProgress()
    }
}
