package com.txapp.musicplayer.util

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicLong

sealed class DownloadState {
    data class Progress(val percentage: Int, val downloaded: Long, val total: Long, val bps: Long) : DownloadState()
    data class Merging(val percentage: Int) : DownloadState()
    data class Success(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

/**
 * TXADownloader - High Performance Multi-threaded Downloader
 * 
 * Logic Rules:
 * - Dynamic Chunking: Max 10MB per chunk, min 7 chunks if possible.
 * - Concurrency: Max 7 parallel downloads using Semaphore.
 * - Merge Step: Efficiently combines temporary chunks into final file.
 * - Automatic Cleanup: Deletes temporary chunks after merging.
 */
object TXADownloader {

    suspend fun download(
        context: Context,
        url: String,
        destination: File,
        userAgent: String = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36"
    ): Flow<DownloadState> = channelFlow {
        try {
            // Prepare destination
            if (destination.exists()) destination.delete()
            val parent = destination.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()

            // 1. Initial HEAD request to check range support and size
            val headRequest = Request.Builder().url(url).head().header("User-Agent", userAgent).build()
            val headResponse = TXAHttp.client.newCall(headRequest).execute()
            val acceptRanges = headResponse.header("Accept-Ranges")
            val contentLength = headResponse.header("Content-Length")?.toLongOrNull() ?: -1L
            headResponse.close()

            val isTurboSupported = acceptRanges == "bytes" && contentLength > 10 * 1024 * 1024 // > 10MB

            if (isTurboSupported) {
                TXALogger.downloadI("TXADownloader", "Turbo Download! Size: ${TXAFormat.formatSize(contentLength)}")
                
                val tempDir = File(context.getExternalFilesDir(null), "temp_dl_${System.currentTimeMillis()}").apply { mkdirs() }
                
                // Config: Max 7 parallel, max 10MB per chunk
                val maxParallel = 7
                val maxChunkSizeLimit = 10 * 1024 * 1024L 
                
                val totalChunks = if (contentLength / maxParallel > maxChunkSizeLimit) {
                    ((contentLength + maxChunkSizeLimit - 1) / maxChunkSizeLimit).toInt()
                } else {
                    maxParallel
                }
                
                val actualChunkSize = contentLength / totalChunks
                val semaphore = Semaphore(maxParallel)
                val downloadedBytes = AtomicLong(0)
                val startTime = System.currentTimeMillis()
                
                // Store expected sizes for each chunk for verification
                val expectedChunkSizes = (0 until totalChunks).map { i ->
                    val start = i * actualChunkSize
                    val end = if (i == totalChunks - 1) contentLength - 1 else (start + actualChunkSize - 1)
                    end - start + 1
                }
                
                coroutineScope {
                    val jobs = (0 until totalChunks).map { i ->
                        val start = i * actualChunkSize
                        val end = if (i == totalChunks - 1) contentLength - 1 else (start + actualChunkSize - 1)
                        val chunkFile = File(tempDir, "chunk_$i.txa.bin")
                        
                        launch(Dispatchers.IO) {
                            semaphore.withPermit {
                                val request = Request.Builder()
                                    .url(url)
                                    .header("Range", "bytes=$start-$end")
                                    .header("User-Agent", userAgent)
                                    .build()
                                
                                val response = TXAHttp.client.newCall(request).execute()
                                if (!response.isSuccessful) throw Exception("Chunk $i failed: ${response.code}")
                                
                                val body = response.body ?: throw Exception("Chunk $i body null")
                                body.byteStream().use { input ->
                                    java.io.BufferedOutputStream(FileOutputStream(chunkFile)).use { output ->
                                        val buffer = ByteArray(8192)
                                        var read: Int
                                        while (input.read(buffer).also { read = it } != -1) {
                                            if (!isActive) throw CancellationException()
                                            output.write(buffer, 0, read)
                                            downloadedBytes.addAndGet(read.toLong())
                                        }
                                        output.flush() // Đảm bảo tất cả bytes được ghi trước khi close
                                    }
                                }
                                response.close()
                            }
                        }
                    }
                    
                    val reporter = launch(Dispatchers.IO) {
                        while (isActive) {
                            val current = downloadedBytes.get()
                            if (current >= contentLength) break
                            
                            val now = System.currentTimeMillis()
                            val timePassed = (now - startTime + 1) / 1000f
                            val bps = (current / timePassed).toLong()
                            val progress = ((current * 100) / contentLength).toInt()
                            
                            send(DownloadState.Progress(progress, current, contentLength, bps))
                            delay(500)
                        }
                    }
                    
                    jobs.joinAll()
                    reporter.cancel()
                }
                
                // Verify all chunks before merging
                for (i in 0 until totalChunks) {
                    val chunkFile = File(tempDir, "chunk_$i.txa.bin")
                    val expectedSize = expectedChunkSizes[i]
                    if (!chunkFile.exists() || chunkFile.length() != expectedSize) {
                        tempDir.listFiles()?.forEach { it.delete() }
                        tempDir.delete()
                        throw Exception("Chunk $i corrupted: expected=$expectedSize, actual=${chunkFile.length()}")
                    }
                }
                
                // 2. Merge Step
                TXALogger.downloadI("TXADownloader", "Merging $totalChunks chunks...")
                send(DownloadState.Merging(0))

                java.io.BufferedOutputStream(FileOutputStream(destination)).use { output ->
                    for (i in 0 until totalChunks) {
                        val chunkFile = File(tempDir, "chunk_$i.txa.bin")
                        chunkFile.inputStream().use { input ->
                            val buffer = ByteArray(128 * 1024)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                            }
                        }
                        output.flush() // Flush sau mỗi chunk
                        send(DownloadState.Merging(((i + 1) * 100 / totalChunks)))
                        chunkFile.delete()
                    }
                    output.flush() // Final flush
                }
                tempDir.delete()
                
                // Final file verification
                val finalSize = destination.length()
                if (finalSize != contentLength) {
                    destination.delete()
                    throw Exception("Merged APK size mismatch: expected=$contentLength, got=$finalSize")
                }
                
                TXALogger.downloadI("TXADownloader", "Download verified: ${TXAFormat.formatSize(finalSize)}")
                send(DownloadState.Success(destination))

            } else {
                // Standard Single Threaded Download
                TXALogger.downloadI("TXADownloader", "Standard Download")
                val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
                val response = TXAHttp.client.newCall(request).execute()
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                
                val body = response.body ?: throw Exception("Body null")
                val totalBytes = body.contentLength()
                val startTime = System.currentTimeMillis()
                var downloaded = 0L
                var lastUpdate = 0L

                body.byteStream().use { input ->
                    FileOutputStream(destination).use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            if (!currentCoroutineContext().isActive) throw CancellationException()
                            output.write(buffer, 0, read)
                            downloaded += read
                            
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 500) {
                                val bps = (downloaded / ((now - startTime + 1) / 1000f)).toLong()
                                val progress = if (totalBytes > 0) (downloaded * 100 / totalBytes).toInt() else 0
                                send(DownloadState.Progress(progress, downloaded, totalBytes, bps))
                                lastUpdate = now
                            }
                        }
                    }
                }
                send(DownloadState.Success(destination))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            TXALogger.downloadE("TXADownloader", "Download failed", e)
            send(DownloadState.Error(e.message ?: "Unknown download error"))
        }
    }
}
