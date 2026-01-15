package com.txapp.musicplayer.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.txapp.musicplayer.model.Song
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TXAFilePickerUtil {

    data class FileInfo(
        val file: File,
        val name: String,
        val isDirectory: Boolean,
        val sizeStr: String,
        val dateModifiedStr: String,
        val durationStr: String? = null,
        val isAudio: Boolean = false
    )

    suspend fun getFiles(path: String, allowedExtensions: Set<String>? = null): List<FileInfo> = withContext(Dispatchers.IO) {
        val directory = File(path)
        val allFiles = directory.listFiles() ?: return@withContext emptyList()

        allFiles.filter { file ->
            if (file.isDirectory) {
                // Keep only non-excluded directories
                !TXAFileFilter.shouldExcludeDirectory(file.absolutePath) && !file.name.startsWith(".")
            } else {
                if (allowedExtensions != null) {
                    file.extension.lowercase(Locale.ROOT) in allowedExtensions
                } else {
                    // Keep only valid audio files
                    TXAFileFilter.isValidAudioFile(file)
                }
            }
        }.map { file ->
            val isDir = file.isDirectory
            val isAudio = !isDir && (allowedExtensions == null || TXAFileFilter.isValidAudioFile(file))
            
            val sizeStr = if (isDir) {
                try {
                    val count = file.list()?.size ?: 0
                    "$count items"
                } catch (e: Exception) {
                    "Unknown"
                }
            } else {
                TXAFormat.formatSize(file.length())
            }
            
            val dateStr = TXAFormat.formatDate(file.lastModified())
            
            var durationStr: String? = null
            if (isAudio) {
                 durationStr = getDuration(file.absolutePath)
            }

            FileInfo(
                file = file,
                name = file.name,
                isDirectory = isDir,
                sizeStr = sizeStr,
                dateModifiedStr = dateStr,
                durationStr = durationStr,
                isAudio = isAudio
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    private fun getDuration(path: String): String? {
        return try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(path)
            val durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            mmr.release()
            if (durationMs > 0) TXAFormat.formatDuration(durationMs) else null
        } catch (e: Exception) {
            null
        }
    }
    
    // Simplified getPath without external dependency
    fun getPath(context: Context, uri: Uri): String? {
        if ("file" == uri.scheme) {
            return uri.path
        }
        // Basic fallback for content URIs: return null to let caller use URI string or try to copy
        // Implementing full ContentResolver logic here is complex and error-prone without proper Utilities.
        // For now, we return null so MusicRepository falls back to uri.toString(), which ExoPlayer can handle.
        return null
    }

    data class AudioMetadata(
        val title: String?,
        val artist: String?,
        val album: String?,
        val duration: Long
    )

    fun extractMetadata(context: Context, uri: Uri): AudioMetadata {
        val ret = MediaMetadataRetriever()
        try {
            ret.setDataSource(context, uri)
            val title = ret.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: getFileName(context, uri)
            val artist = ret.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = ret.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val duration = ret.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            
            return AudioMetadata(title, artist, album, duration)
        } catch (e: Exception) {
            return AudioMetadata(getFileName(context, uri), null, null, 0L)
        } finally {
            try { ret.release() } catch (e: Exception) {}
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) result = cursor.getString(index)
                    }
                }
            } catch (e: Exception) {}
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "Unknown"
    }
}
