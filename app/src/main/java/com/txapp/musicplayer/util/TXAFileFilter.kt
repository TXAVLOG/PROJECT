package com.txapp.musicplayer.util

import java.io.File

/**
 * TXA File Filter - Filters music files and directories
 * - Excludes system directories
 * - Filters short audio files (< 30s)
 */
object TXAFileFilter {
    
    // Directories to exclude
    private val EXCLUDED_DIRS = setOf(
        "Android/media",
        "Android/data",
        ".thumbnails",
        "Ringtones",
        "Alarms",
        "Notifications",
        "Podcasts",
        ".cache",
        ".temp",
        "WhatsApp/Media/.Statuses",
        "DCIM/.thumbnails",
        "assets/mp3/tet",
        "mp3/tet"
    )
    
    // Minimum duration in milliseconds (30 seconds)
    const val MIN_DURATION_MS = 30_000L
    
    // Supported audio extensions
    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "aac", "flac", "wav", "ogg", "wma", "opus"
    )
    
    /**
     * Check if a directory should be excluded from scanning
     */
    fun shouldExcludeDirectory(path: String): Boolean {
        val normalizedPath = path.replace("\\", "/")
        return EXCLUDED_DIRS.any { excluded ->
            normalizedPath.contains(excluded, ignoreCase = true)
        }
    }
    
    /**
     * Check if a file is a valid audio file
     */
    fun isValidAudioFile(file: File): Boolean {
        if (!file.isFile) return false
        val extension = file.extension.lowercase()
        return extension in AUDIO_EXTENSIONS
    }
    
    /**
     * Check if audio duration meets minimum requirement
     */
    fun isValidDuration(durationMs: Long): Boolean {
        return durationMs >= MIN_DURATION_MS
    }
    
    /**
     * Get file extension
     */
    fun getExtension(fileName: String): String {
        return fileName.substringAfterLast(".", "").lowercase()
    }
    
    /**
     * Check if extension is supported audio format
     */
    fun isSupportedExtension(extension: String): Boolean {
        return extension.lowercase() in AUDIO_EXTENSIONS
    }
    
    /**
     * Get reason why a file/directory was excluded
     */
    fun getExclusionReason(path: String, durationMs: Long = 0L): String? {
        val normalizedPath = path.replace("\\", "/")
        
        // Check excluded directories
        for (excluded in EXCLUDED_DIRS) {
            if (normalizedPath.contains(excluded, ignoreCase = true)) {
                return "System folder: $excluded"
            }
        }
        
        // Check duration
        if (durationMs > 0 && durationMs < MIN_DURATION_MS) {
            return "Too short (< 30s)"
        }
        
        return null
    }
}
