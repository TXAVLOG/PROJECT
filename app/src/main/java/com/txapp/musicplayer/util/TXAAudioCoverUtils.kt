package com.txapp.musicplayer.util

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.images.Artwork
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import android.util.Log

object TXAAudioCoverUtils {
    private const val TAG = "TXAAudioCoverUtils"

    private val FALLBACKS = arrayOf(
        "cover.jpg", "album.jpg", "folder.jpg",
        "cover.png", "album.png", "folder.png",
        "cover.webp", "album.webp", "folder.webp"
    )

    fun getArtwork(context: android.content.Context, uri: android.net.Uri): ByteArray? {
        if (uri.scheme == "file") {
            return getArtwork(uri.path ?: "")
        }
        
        // Resolve Content URI to real path
        var path: String? = null
        try {
            val cursor = context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.Audio.Media.DATA), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(android.provider.MediaStore.Audio.Media.DATA)
                    if (idx != -1) {
                        path = it.getString(idx)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve URI: $uri", e)
        }
        
        return if (path != null) getArtwork(path) else null
    }

    fun getArtwork(path: String): ByteArray? {
        // Method 1: use embedded high resolution album art
        try {
            val file = File(path)
            if (!file.exists()) return null

            // Use generic AudioFileIO to support MP3, FLAC, OGG, etc.
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag
            if (tag != null) {
                val art = tag.firstArtwork
                if (art != null) {
                    return art.binaryData
                }
            }
        } catch (e: Exception) {
            // Ignore read errors, proceed to fallback
            Log.w(TAG, "Failed to read embedded artwork from $path: ${e.message}")
        }

        // Method 2: look for album art in external files
        try {
            val parent = File(path).parentFile ?: return null
            for (fallback in FALLBACKS) {
                val cover = File(parent, fallback)
                if (cover.exists()) {
                    return cover.readBytes()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read fallback artwork: ${e.message}")
        }

        return null
    }
}
