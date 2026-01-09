package com.txapp.musicplayer.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import com.txapp.musicplayer.data.MusicRepository
import com.txapp.musicplayer.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Stack

/**
 * TXA Music Scanner
 * Scans device for audio files, filters them, and saves to database.
 * Supports real-time progress reporting.
 */
object TXAMusicScanner {

    data class ScanResult(
        val successCount: Int,
        val failedCount: Int,
        val scannedFolders: FolderNode,
        val isFullScan: Boolean = true
    )

    data class FolderNode(
        val name: String,
        val path: String,
        val files: MutableList<FileNode> = mutableListOf(),
        val children: MutableList<FolderNode> = mutableListOf(),
        var isExcluded: Boolean = false,
        var reason: String? = null
    )

    data class FileNode(
        val name: String,
        var isSuccess: Boolean,
        var reason: String? = null
    )

    interface ScanCallback {
        fun onProgress(currentPath: String, count: Int)
    }

    /**
     * Optimizes scan by checking if we need a full scan.
     * Returns true if we should perform a deep scan.
     */
    fun shouldPerformDeepScan(context: Context): Boolean {
        val prefs = context.getSharedPreferences("TXA_SCAN_PREFS", Context.MODE_PRIVATE)
        val lastScanTime = prefs.getLong("last_scan_time", 0L)
        
        // If never scanned or more than 24 hours ago, scan again
        if (lastScanTime == 0L || System.currentTimeMillis() - lastScanTime > 24 * 60 * 60 * 1000) {
            return true
        }
        
        return false
    }

    fun markScanComplete(context: Context) {
        context.getSharedPreferences("TXA_SCAN_PREFS", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_scan_time", System.currentTimeMillis())
            .apply()
    }

    suspend fun scanMusic(
        context: Context,
        repository: MusicRepository,
        callback: ScanCallback
    ): ScanResult = withContext(Dispatchers.IO) {
        try {
            // Cleanup: Ensure .txa backup files are not in the library
            repository.removeSongsByExtension("txa")

            val roots = getAllStorageRoots(context)
            val rootNode = FolderNode("Root", "/")
            
            val stack = Stack<Pair<File, FolderNode>>()
            for (root in roots) {
                if (root.exists()) {
                    stack.push(root to rootNode)
                }
            }

            var successCount = 0
            var failedCount = 0
            val scsongs = mutableListOf<Song>()
            
            var lastUpdateTime = 0L
            
            while (stack.isNotEmpty()) {
                if (!isActive) break

                val (currentDir, currentNode) = stack.pop()
                
                // Report progress - Throttled
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpdateTime > 200) {
                    lastUpdateTime = currentTime
                    withContext(Dispatchers.Main) {
                        callback.onProgress(currentDir.absolutePath, successCount)
                    }
                }

                // Check exclusion
                val exclusionReason = TXAFileFilter.getExclusionReason(currentDir.absolutePath)
                if (exclusionReason != null) {
                    currentNode.isExcluded = true
                    currentNode.reason = exclusionReason
                    continue
                }

                val files: Array<File>? = try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        val fileList = mutableListOf<File>()
                        try {
                            java.nio.file.Files.newDirectoryStream(currentDir.toPath()).use { stream ->
                                for (entry in stream) {
                                    fileList.add(entry.toFile())
                                }
                            }
                            fileList.toTypedArray()
                        } catch (e: Throwable) {
                            // If NIO fails, fallback
                            currentDir.listFiles()
                        }
                    } else {
                        currentDir.listFiles()
                    }
                } catch (e: Exception) {
                    currentNode.reason = "Access Denied: ${e.message}"
                    null
                }
                
                if (files == null) continue

                for (file in files) {
                    if (!isActive) break

                    if (file.isDirectory) {
                        val childNode = FolderNode(file.name, file.absolutePath)
                        currentNode.children.add(childNode)
                        stack.push(file to childNode)
                    } else {
                        if (TXAFileFilter.isValidAudioFile(file)) {
                            try {
                                val duration = getDuration(context, file)
                                if (TXAFileFilter.isValidDuration(duration)) {
                                    val song = createSong(context, file, duration)
                                    scsongs.add(song)
                                    successCount++
                                    currentNode.files.add(FileNode(file.name, true))
                                    
                                    if (scsongs.size >= 50) {
                                        repository.saveSongs(ArrayList(scsongs))
                                        scsongs.clear()
                                    }
                                } else {
                                    failedCount++
                                    currentNode.files.add(FileNode(file.name, false, "Too short (<30s)"))
                                }
                            } catch (e: Exception) {
                                failedCount++
                                currentNode.files.add(FileNode(file.name, false, e.message))
                            }
                        }
                    }
                }
            }
            
            if (scsongs.isNotEmpty()) {
                repository.saveSongs(scsongs)
            }
            
            markScanComplete(context)
            ScanResult(successCount, failedCount, rootNode)
        } catch (e: Throwable) {
            // Report fatal scan error
            TXALogger.appE("MusicScanner", "Fatal scan error", e)
            TXACrashHandler.handleError(context, e, "MusicScanner")
            // Throw so the caller knows it failed
            throw e
        }
    }

    private fun getAllStorageRoots(context: Context): List<File> {
        val roots = mutableListOf<File>()
        // Primary external storage
        roots.add(Environment.getExternalStorageDirectory())
        
        // Other volumes (SD cards)
        val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
        for (dir in externalDirs) {
            dir?.let {
                val path = it.absolutePath
                if (path.contains("/Android/data/")) {
                    val rootPath = path.substringBefore("/Android/data/")
                    val rootFile = File(rootPath)
                    if (rootFile.exists() && rootFile.isDirectory && !roots.contains(rootFile)) {
                        roots.add(rootFile)
                    }
                }
            }
        }
        
        // Ensure common music locations are covered if roots missed them
        // (Like if user specifically wants Pictures)
        val pictures = File(Environment.getExternalStorageDirectory(), "Pictures")
        if (pictures.exists() && !roots.contains(pictures)) {
            // Already covered by Environment.getExternalStorageDirectory() recursion, 
            // but distinct() makes it safe.
        }

        return roots.distinct()
    }

    private fun getDuration(context: Context, file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }

    private fun createSong(context: Context, file: File, duration: Long): Song {
        val retriever = MediaMetadataRetriever()
        var title = file.nameWithoutExtension
        var artist = "<unknown>"
        var album = "<unknown>"
        var albumId = 0L
        
        try {
            retriever.setDataSource(file.absolutePath)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: title
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: artist
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: album
            
            // Try to get albumId from title/album hash if missing
            albumId = (album + artist).hashCode().toLong()
        } catch (e: Exception) {
            // Ignore
        } finally {
            retriever.release()
        }

        return Song(
            id = file.absolutePath.hashCode().toLong(), // More stable than file.hashCode()
            title = title,
            artist = artist,
            album = album,
            data = file.absolutePath,
            duration = duration,
            albumId = albumId,
            dateAdded = file.lastModified() / 1000
        )
    }
}
