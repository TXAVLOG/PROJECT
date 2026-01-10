package com.txapp.musicplayer.util

import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import com.txapp.musicplayer.model.SongTagInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.AndroidArtwork
import java.io.File
import java.io.IOException

/**
 * Utility to write metadata directly to audio files using JAudioTagger
 */
object TXATagWriter {
    private const val TAG = "TXATagWriter"

    /**
     * Get MediaStore Uri for a file path
     */
    fun getSongUri(context: Context, filePath: String): Uri? {
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID),
            MediaStore.Audio.Media.DATA + "=?",
            arrayOf(filePath),
            null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                return Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
            }
        }
        return null
    }

    /**
     * Check if we need write permission for the given file paths (Android 11+)
     */
    fun createWriteRequest(context: Context, filePaths: List<String>): PendingIntent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val uris = filePaths.mapNotNull { getSongUri(context, it) }
            if (uris.isNotEmpty()) {
                return MediaStore.createWriteRequest(context.contentResolver, uris)
            }
        }
        return null
    }

    sealed class WriteResult {
        object Success : WriteResult()
        object Failure : WriteResult()
        data class PermissionRequired(val intent: PendingIntent) : WriteResult()
    }

    /**
     * Write tags to the file. 
     * Handles Android 11+ requirements by using a temporary cache file if needed.
     */
    suspend fun writeTags(context: Context, tagInfo: SongTagInfo): WriteResult = withContext(Dispatchers.IO) {
        var anyPermissionRequired: PendingIntent? = null
        val resolver = context.contentResolver

        for (filePath in tagInfo.filePaths) {
            try {
                val originalFile = File(filePath)
                if (!originalFile.exists()) {
                    TXALogger.appE(TAG, "File does not exist: $filePath")
                    return@withContext WriteResult.Failure
                }

                // 1. Create a temporary cache file
                val cacheFile = File(context.cacheDir, originalFile.name)
                originalFile.inputStream().use { input ->
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // 2. Write tags to the CACHE file
                val audioFile = AudioFileIO.read(cacheFile)
                val tag = audioFile.tagOrCreateAndSetDefault

                tag.setField(FieldKey.TITLE, tagInfo.title)
                tag.setField(FieldKey.ARTIST, tagInfo.artist)
                tag.setField(FieldKey.ALBUM, tagInfo.album)
                
                tagInfo.albumArtist?.let { tag.setField(FieldKey.ALBUM_ARTIST, it) }
                tagInfo.composer?.let { tag.setField(FieldKey.COMPOSER, it) }
                
                tagInfo.trackNumber?.let { 
                    try { tag.setField(FieldKey.TRACK, it) } catch (e: Exception) {}
                }
                
                tagInfo.year?.let { 
                    try { tag.setField(FieldKey.YEAR, it) } catch (e: Exception) {}
                }

                audioFile.commit()

                // 3. Copy back to original file
                val contentUri = getSongUri(context, filePath)
                
                try {
                    if (contentUri != null) {
                        resolver.openOutputStream(contentUri, "rwt")?.use { output ->
                            cacheFile.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                    } else {
                        originalFile.outputStream().use { output ->
                            cacheFile.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Check if it's a security exception on Android 11+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && 
                        (e is SecurityException || e.javaClass.simpleName == "RecoverableSecurityException")) {
                        val intent = createWriteRequest(context, listOf(filePath))
                        if (intent != null) {
                            anyPermissionRequired = intent
                            return@withContext WriteResult.PermissionRequired(intent)
                        }
                    }

                    TXALogger.appW(TAG, "Standard write failed for $filePath, trying root...")
                    if (!copyFileWithRoot(cacheFile, originalFile)) {
                        TXALogger.appE(TAG, "Failed to write back even with Root: $filePath")
                        return@withContext WriteResult.Failure
                    }
                } finally {
                    cacheFile.delete()
                }

            } catch (e: Exception) {
                TXALogger.appE(TAG, "Error processing $filePath", e)
                return@withContext WriteResult.Failure
            }
        }

        // If artwork was provided, also update the MediaStore album art cache
        if (tagInfo.artwork != null && tagInfo.albumId > 0) {
            updateAlbumArtCache(context, tagInfo.albumId, tagInfo.artwork)
        } else if (tagInfo.deleteArtwork && tagInfo.albumId > 0) {
            deleteAlbumArt(context, tagInfo.albumId)
        }
        
        scanFiles(context, tagInfo.filePaths)
        return@withContext WriteResult.Success
    }


    /**
     * Write lyrics specifically to embedded tags
     */
    suspend fun writeLyrics(context: Context, filePath: String, lyrics: String): WriteResult = withContext(Dispatchers.IO) {
        try {
            val originalFile = File(filePath)
            if (!originalFile.exists()) return@withContext WriteResult.Failure

            val cacheFile = File(context.cacheDir, originalFile.name)
            originalFile.inputStream().use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val audioFile = AudioFileIO.read(cacheFile)
            val tag = audioFile.tagOrCreateAndSetDefault
            tag.setField(FieldKey.LYRICS, lyrics)
            audioFile.commit()

            // Copy back
            val contentUri = getSongUri(context, filePath)
            
            try {
                if (contentUri != null) {
                    context.contentResolver.openOutputStream(contentUri, "rwt")?.use { output ->
                        cacheFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                } else {
                    originalFile.outputStream().use { output ->
                        cacheFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                }
            } catch (e: Exception) {
                 // Check if it's a security exception on Android 11+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && 
                    (e is SecurityException || e.javaClass.simpleName == "RecoverableSecurityException")) {
                    val intent = createWriteRequest(context, listOf(filePath))
                    if (intent != null) {
                        return@withContext WriteResult.PermissionRequired(intent)
                    }
                }

                TXALogger.appW(TAG, "Standard write failed for lyrics on $filePath, trying root...")
                if (!copyFileWithRoot(cacheFile, originalFile)) {
                    TXALogger.appE(TAG, "Failed to copy lyrics back even with Root: $filePath")
                    return@withContext WriteResult.Failure
                }
            } finally {
                cacheFile.delete()
            }

            scanFiles(context, listOf(filePath))
            return@withContext WriteResult.Success
        } catch (e: Exception) {
            TXALogger.appE(TAG, "Error writing lyrics to $filePath", e)
            return@withContext WriteResult.Failure
        }
    }



    /**
     * Copy file using root as a fallback
     */
    private fun copyFileWithRoot(source: File, destination: File): Boolean {
        return TXASuHelper.runAsRoot("cp -f '${source.absolutePath}' '${destination.absolutePath}' && chmod 664 '${destination.absolutePath}'")
    }

    /**
     * Trigger MediaScanner for updated files
     */
    private fun scanFiles(context: Context, paths: List<String>) {
        MediaScannerConnection.scanFile(
            context,
            paths.toTypedArray(),
            null
        ) { path, uri ->
            TXALogger.appI(TAG, "Scanned $path: $uri")
        }
    }
    
    /**
     * Update the MediaStore album art cache for immediate UI refresh.
     * This is CRITICAL for lists/playlists to show the new artwork.
     */
    private fun updateAlbumArtCache(context: Context, albumId: Long, artwork: Bitmap) {
        try {
            // 1. Create temp file for artwork
            val artDir = File(context.cacheDir, "albumthumbs")
            if (!artDir.exists()) artDir.mkdirs()
            val artFile = File(artDir, "${System.currentTimeMillis()}.jpg")
            artFile.outputStream().use { out ->
                artwork.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            
            // 2. Delete old art from MediaStore
            val artworkUri = "content://media/external/audio/albumart".toUri()
            context.contentResolver.delete(ContentUris.withAppendedId(artworkUri, albumId), null, null)
            
            // 3. Insert new art into MediaStore
            val values = ContentValues().apply {
                put("album_id", albumId)
                put("_data", artFile.absolutePath)
            }
            try {
                context.contentResolver.insert(artworkUri, values)
                context.contentResolver.notifyChange(artworkUri, null)
                TXALogger.appI(TAG, "Updated album art cache for albumId: $albumId")
            } catch (e: Exception) {
                TXALogger.appE(TAG, "Failed to insert album art into MediaStore", e)
            }
        } catch (e: Exception) {
            TXALogger.appE(TAG, "Failed to update album art cache", e)
        }
    }
    
    /**
     * Delete album art from MediaStore cache.
     */
    private fun deleteAlbumArt(context: Context, albumId: Long) {
        try {
            val artworkUri = "content://media/external/audio/albumart".toUri()
            context.contentResolver.delete(ContentUris.withAppendedId(artworkUri, albumId), null, null)
            context.contentResolver.notifyChange(artworkUri, null)
            TXALogger.appI(TAG, "Deleted album art cache for albumId: $albumId")
        } catch (e: Exception) {
            TXALogger.appE(TAG, "Failed to delete album art from cache", e)
        }
    }
}
