package com.txapp.musicplayer.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.txapp.musicplayer.model.Song
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.OutputStreamWriter

/**
 * Utility for writing M3U playlist files
 */
object M3UWriter {
    private const val TAG = "M3UWriter"
    
    /**
     * Write a playlist to an M3U file
     * @param context Application context
     * @param songs List of songs to include in playlist
     * @param playlistName Name of the playlist (without extension)
     * @return The created file, or null if failed
     */
    fun write(
        context: Context,
        songs: List<Song>,
        playlistName: String
    ): File? {
        if (songs.isEmpty()) {
            TXALogger.w(TAG, "Cannot write empty playlist")
            return null
        }
        
        val fileName = sanitizeFileName(playlistName) + M3UConstants.EXTENSION

        // Android 10+ (Q) uses MediaStore
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return writeMediaStore(context, songs, fileName)
        }
        
        // Legacy Storage
        return writeLegacy(songs, fileName)
    }

    private fun writeMediaStore(context: Context, songs: List<Song>, fileName: String): File? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/x-mpegurl") // or audio/mpegurl
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Playlists")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val collection = MediaStore.Files.getContentUri("external")
        var uri: Uri? = null
        
        try {
            uri = resolver.insert(collection, contentValues)
            if (uri == null) {
                TXALogger.e(TAG, "Failed to create MediaStore entry")
                return null
            }

            resolver.openOutputStream(uri)?.use { outputStream ->
                BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
                    writePlaylistContent(writer, songs)
                }
            }

            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            TXALogger.i(TAG, "Playlist written to MediaStore: $uri")
            
            // Return best-effort File object
            val path = TXAFilePickerUtil.getPath(context, uri)
            return if (path != null) File(path) else File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Playlists/$fileName")

        } catch (e: Exception) {
            TXALogger.e(TAG, "Error writing playlist to MediaStore", e)
             // Try to cleanup
            uri?.let { try { resolver.delete(it, null, null) } catch (ignored: Exception) {} }
            return null
        }
    }

    private fun writeLegacy(songs: List<Song>, fileName: String): File? {
        // Create playlists directory
        val playlistsDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "Playlists"
        )
        
        if (!playlistsDir.exists() && !playlistsDir.mkdirs()) {
            TXALogger.e(TAG, "Failed to create playlists directory")
            return null
        }
        
        val playlistFile = File(playlistsDir, fileName)
        
        return try {
            BufferedWriter(FileWriter(playlistFile)).use { writer ->
                writePlaylistContent(writer, songs)
            }
            TXALogger.i(TAG, "Playlist written to: ${playlistFile.absolutePath}")
            playlistFile
        } catch (e: IOException) {
            TXALogger.e(TAG, "Error writing playlist (Legacy)", e)
            null
        }
    }
    
    /**
     * Write playlist content using a writer
     */
    private fun writePlaylistContent(writer: BufferedWriter, songs: List<Song>) {
        // Write M3U header
        writer.write(M3UConstants.HEADER)
        writer.newLine()
        
        // Write each song entry
        songs.forEach { song ->
            writeEntry(writer, song)
        }
    }
    
    /**
     * Write a single song entry in M3U format:
     * #EXTINF:duration,artist - title
     * /path/to/song.mp3
     */
    private fun writeEntry(writer: BufferedWriter, song: Song) {
        // Write info line
        val durationSeconds = (song.duration / 1000).toInt()
        val artist = if (song.artist.isBlank() || song.artist == "<unknown>") "Unknown Artist" else song.artist
        writer.write("${M3UConstants.ENTRY}$durationSeconds,$artist - ${song.title}")
        writer.newLine()
        
        // Write file path
        writer.write(song.data)
        writer.newLine()
    }
    
    /**
     * Sanitize filename by removing invalid characters
     */
    private fun sanitizeFileName(name: String): String {
        return name
            .replace("[\\\\/:*?\"<>|]".toRegex(), "_")
            .trim()
            .take(255) // Max filename length
    }
    
    /**
     * Parse an M3U file and return list of file paths
     * @param file M3U file to parse
     * @return List of file paths found in the playlist
     */
    fun read(file: File): List<String> {
        if (!file.exists() || !file.canRead()) {
            TXALogger.w(TAG, "Cannot read file: ${file.absolutePath}")
            return emptyList()
        }
        
        val paths = mutableListOf<String>()
        
        try {
            file.bufferedReader().use { reader ->
                reader.lineSequence()
                    .map { it.trim() }
                    .filter { line ->
                        // Skip comments and empty lines
                        line.isNotEmpty() && 
                        !line.startsWith("#") &&
                        !line.startsWith("http") // Skip URLs  
                    }
                    .forEach { path ->
                        // Check if file exists
                        // Note: On Android 11+, this check might fail even if file exists due to visibility
                        // We add it anyway, and Repo will filter non-existing later
                         paths.add(path)
                    }
            }
        } catch (e: IOException) {
            TXALogger.e(TAG, "Error reading M3U file", e)
        }
        
        return paths
    }
}
