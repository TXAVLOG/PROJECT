package com.txapp.musicplayer.util

import android.content.Context
import android.os.Environment
import com.txapp.musicplayer.model.Song
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException

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
        
        // Create playlists directory
        val playlistsDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "Playlists"
        )
        
        if (!playlistsDir.exists() && !playlistsDir.mkdirs()) {
            TXALogger.e(TAG, "Failed to create playlists directory")
            return null
        }
        
        // Create M3U file
        val fileName = sanitizeFileName(playlistName) + M3UConstants.EXTENSION
        val playlistFile = File(playlistsDir, fileName)
        
        return try {
            writePlaylistFile(playlistFile, songs)
            TXALogger.i(TAG, "Playlist written to: ${playlistFile.absolutePath}")
            playlistFile
        } catch (e: IOException) {
            TXALogger.e(TAG, "Error writing playlist", e)
            null
        }
    }
    
    /**
     * Write songs to M3U file
     */
    private fun writePlaylistFile(file: File, songs: List<Song>) {
        BufferedWriter(FileWriter(file)).use { writer ->
            // Write M3U header
            writer.write(M3UConstants.HEADER)
            writer.newLine()
            
            // Write each song entry
            songs.forEach { song ->
                writeEntry(writer, song)
            }
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
        writer.write("${M3UConstants.ENTRY}$durationSeconds,${song.artist} - ${song.title}")
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
                        if (File(path).exists()) {
                            paths.add(path)
                        } else {
                            TXALogger.d(TAG, "File not found: $path")
                        }
                    }
            }
        } catch (e: IOException) {
            TXALogger.e(TAG, "Error reading M3U file", e)
        }
        
        return paths
    }
}
