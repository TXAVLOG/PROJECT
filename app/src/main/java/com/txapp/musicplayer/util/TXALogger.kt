package com.txapp.musicplayer.util

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Locale

/**
 * TXA Logger - Centralized logging system
 * - Writes logs to file with auto-rotation
 * - Supports multiple log types (APP, CRASH, API, DOWNLOAD, etc.)
 * - Auto-deletes files over 1MB
 */
object TXALogger {
    
    enum class LogType {
        APP, CRASH, API, LANG, RESOLVE, DOWNLOAD, MISSING_KEY, ALBUMART, HOLIDAY, PLAYBACK, FALLBACK_KEY
    }
    
    private const val MAX_FILE_SIZE = 1024 * 1024 // 1MB
    private const val TAG = "TXALogger"
    
    private lateinit var appContext: Context
    private var logDir: File? = null
    
    fun init(context: Context) {
        appContext = context.applicationContext
        if (logDir == null) {
            logDir = getLogDirectory(appContext)
        }
        
        // Clean old logs
        cleanOldLogs()
        
        // Print diagnostics to ADB
        printDiagnostics(appContext)
    }
    
    private fun getLogDirectory(context: Context? = null): File {
        val actualContext = context ?: try { appContext } catch (e: Exception) { null }
        val appName = "TXA Music"
        
        // 1. Try Public External Storage if permission is granted
        val isAllFilesAccess = if (actualContext != null) {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                Environment.isExternalStorageManager()
            } else {
                actualContext.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        } else false

        if (isAllFilesAccess) {
            try {
                val publicDir = File(Environment.getExternalStorageDirectory(), "$appName/logs")
                if (publicDir.exists() || TXASuHelper.mkdirs(publicDir)) {
                    return publicDir
                }
            } catch (e: Exception) {
                // Fallback if failed to create public dir
            }
        }

        // 2. Try App-specific external files dir if context is available
        if (actualContext != null) {
            val externalDir = actualContext.getExternalFilesDir(null)
            if (externalDir != null) {
                val logsDir = File(externalDir, "logs")
                if (logsDir.exists() || TXASuHelper.mkdirs(logsDir)) {
                    return logsDir
                }
            }
        }

        // 3. Last fallback: Internal cache (always available) or hardcoded path if no context at all
        val fallbackDir = if (actualContext != null) {
            File(actualContext.cacheDir, "logs")
        } else {
            // Extreme fallback if NO context at all (e.g., called before init in a non-Android context)
            File(Environment.getExternalStorageDirectory(), "$appName/logs") // Fallback to public dir if no context
        }
        if (!fallbackDir.exists()) TXASuHelper.mkdirs(fallbackDir)
        return fallbackDir
    }
    
    private fun cleanOldLogs() {
        try {
            // Ensure logDir is initialized before trying to list files
            if (logDir == null) {
                logDir = getLogDirectory()
            }
            logDir?.listFiles()?.forEach { file ->
                if (file.length() > MAX_FILE_SIZE) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to clean old logs", e)
        }
    }
    
    private fun writeLog(type: LogType, level: String, tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = TXAFormat.formatTime(System.currentTimeMillis())
        val logMessage = buildString {
            append("[$timestamp] [$level/${type.name}] $tag: $message")
            if (throwable != null) {
                append("\n")
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                append(sw.toString())
            }
        }
        
        // Print to Logcat
        when (level) {
            "D" -> android.util.Log.d(tag, message, throwable)
            "I" -> android.util.Log.i(tag, message, throwable)
            "W" -> android.util.Log.w(tag, message, throwable)
            "E" -> android.util.Log.e(tag, message, throwable)
        }
        
        // Write to file
        try {
            if (logDir == null) {
                logDir = getLogDirectory()
            }
            val dateStr = TXAFormat.formatDate(System.currentTimeMillis()).replace("/", "-")
            val logFile = File(logDir, "txa_log_$dateStr.txt")
            logFile.appendText("$logMessage\n")
            
            // If it's an error, also write to a dedicated error log
            if (level == "E" || type == LogType.CRASH) {
                val errorFile = File(logDir, "txa_error_$dateStr.txt") // Use dated error file
                
                // Limit error file size to 5MB
                if (errorFile.exists() && errorFile.length() > 5 * 1024 * 1024) {
                    errorFile.delete() // Delete completely as requested (no backup)
                    // Re-create header
                    errorFile.appendText("[$timestamp] Log rotation: Previous log deleted due to size limit > 5MB\n")
                }
                
                errorFile.appendText("$logMessage\n")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to write log to file", e)
        }
    }
    
    /**
     * Print diagnostics to Logcat for ADB retrieval
     */
    fun printDiagnostics(context: Context) {
        val path = logDir?.absolutePath ?: "Not initialized"
        val canWrite = logDir?.canWrite() == true
        val totalSpace = logDir?.totalSpace ?: 0
        val freeSpace = logDir?.freeSpace ?: 0
        
        val border = "=".repeat(50)
        val msg = """
            
            $border
            TXA MUSIC LOGGER DIAGNOSTICS
            $border
            Path       : $path
            Writable   : $canWrite
            Disk Usage : ${TXAFormat.formatSize(Math.max(0, totalSpace - freeSpace).toLong())} / ${TXAFormat.formatSize(totalSpace)}
            ADB Pull   : adb pull "$path" .
            $border
        """.trimIndent()
        
        android.util.Log.i(TAG, msg)
    }
    
    // APP logs
    fun appD(tag: String, message: String) = writeLog(LogType.APP, "D", tag, message)
    fun appI(tag: String, message: String) = writeLog(LogType.APP, "I", tag, message)
    fun appW(tag: String, message: String) = writeLog(LogType.APP, "W", tag, message)
    fun appE(tag: String, message: String, throwable: Throwable? = null) = writeLog(LogType.APP, "E", tag, message, throwable)
    
    // Generic shortcuts (aliases for app logs)
    fun d(tag: String, message: String) = appD(tag, message)
    fun i(tag: String, message: String) = appI(tag, message)
    fun w(tag: String, message: String) = appW(tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) = appE(tag, message, throwable)
    
    // CRASH logs
    fun crashE(tag: String, message: String, throwable: Throwable? = null) = writeLog(LogType.CRASH, "E", tag, message, throwable)
    
    // API logs
    fun apiD(tag: String, message: String) = writeLog(LogType.API, "D", tag, message)
    fun apiI(tag: String, message: String) = writeLog(LogType.API, "I", tag, message)
    fun apiE(tag: String, message: String, throwable: Throwable? = null) = writeLog(LogType.API, "E", tag, message, throwable)
    
    // LANG logs
    fun langI(tag: String, message: String) = writeLog(LogType.LANG, "I", tag, message)
    fun langE(tag: String, message: String, throwable: Throwable? = null) = writeLog(LogType.LANG, "E", tag, message, throwable)
    
    // DOWNLOAD logs
    fun downloadI(tag: String, message: String) = writeLog(LogType.DOWNLOAD, "I", tag, message)
    fun downloadE(tag: String, message: String, throwable: Throwable? = null) = writeLog(LogType.DOWNLOAD, "E", tag, message, throwable)
    
    // RESOLVE logs
    fun resolveI(tag: String, message: String) = writeLog(LogType.RESOLVE, "I", tag, message)
    fun resolveE(tag: String, message: String, throwable: Throwable? = null) = writeLog(LogType.RESOLVE, "E", tag, message, throwable)
    
    // MISSING_KEY logs - writes to separate file for easy tracking
    private val missingKeys = mutableSetOf<String>()
    
    fun missingKey(key: String) {
        if (missingKeys.contains(key)) return // Avoid duplicate logs
        missingKeys.add(key)
        
        writeLog(LogType.MISSING_KEY, "W", "MissingKey", "Missing translation key: $key")
        
        // Also write to separate file
        try {
            val dateStr = TXAFormat.formatDate(System.currentTimeMillis()).replace("/", "-")
            val missingKeyFile = File(logDir, "txa_missing_keys_$dateStr.txt")
            missingKeyFile.appendText("$key\n")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to write missing key to file", e)
        }
    }
    
    fun getMissingKeys(): Set<String> = missingKeys.toSet()

    // FALLBACK_KEY logs - writes to separate file for easy tracking
    private val fallbackKeys = mutableSetOf<String>()

    fun fallbackKey(key: String) {
        if (fallbackKeys.contains(key)) return // Avoid duplicate logs
        fallbackKeys.add(key)
        
        writeLog(LogType.FALLBACK_KEY, "W", "FallbackKey", "Used fallback for key: $key (Not found in API/Local cache)")
        
        // Also write to separate file
        try {
            val dateStr = TXAFormat.formatDate(System.currentTimeMillis()).replace("/", "-")
            val fallbackKeyFile = File(logDir, "txa_fallback_keys_$dateStr.txt")
            fallbackKeyFile.appendText("$key\n")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to write fallback key to file", e)
        }
    }
    
    // ALBUMART logs - writes to separate file for album art fetching tracking
    fun albumArtD(tag: String, message: String) {
        writeLog(LogType.ALBUMART, "D", tag, message)
        writeToSeparateFile("txa_albumart", "[D] $tag: $message")
    }
    fun albumArtI(tag: String, message: String) {
        writeLog(LogType.ALBUMART, "I", tag, message)
        writeToSeparateFile("txa_albumart", "[I] $tag: $message")
    }
    fun albumArtE(tag: String, message: String, throwable: Throwable? = null) {
        writeLog(LogType.ALBUMART, "E", tag, message, throwable)
        writeToSeparateFile("txa_albumart", "[E] $tag: $message")
    }
    
    // HOLIDAY logs - writes to separate file for holiday feature tracking
    fun holidayD(tag: String, message: String) {
        writeLog(LogType.HOLIDAY, "D", tag, message)
        writeToSeparateFile("txa_holiday", "[D] $tag: $message")
    }
    fun holidayI(tag: String, message: String) {
        writeLog(LogType.HOLIDAY, "I", tag, message)
        writeToSeparateFile("txa_holiday", "[I] $tag: $message")
    }
    fun holidayW(tag: String, message: String) {
        writeLog(LogType.HOLIDAY, "W", tag, message)
        writeToSeparateFile("txa_holiday", "[W] $tag: $message")
    }
    fun holidayE(tag: String, message: String, throwable: Throwable? = null) {
        writeLog(LogType.HOLIDAY, "E", tag, message, throwable)
        writeToSeparateFile("txa_holiday", "[E] $tag: $message")
    }

    // PLAYBACK logs - writes to separate file for playback persistence tracking
    fun playbackD(tag: String, message: String) {
        writeLog(LogType.PLAYBACK, "D", tag, message)
        writeToSeparateFile("txa_playback", "[D] $tag: $message")
    }
    fun playbackI(tag: String, message: String) {
        writeLog(LogType.PLAYBACK, "I", tag, message)
        writeToSeparateFile("txa_playback", "[I] $tag: $message")
    }
    fun playbackE(tag: String, message: String, throwable: Throwable? = null) {
        writeLog(LogType.PLAYBACK, "E", tag, message, throwable)
        writeToSeparateFile("txa_playback", "[E] $tag: $message")
    }

    // ERROR API logs - writes to separate file for error report api tracking
    fun errorApiLog(tag: String, message: String) {
        writeLog(LogType.API, "I", tag, message)
        writeToSeparateFile("txa_error_api", "[API] $tag: $message")
    }
    
    private fun writeToSeparateFile(prefix: String, message: String) {
        try {
            val timestamp = TXAFormat.formatTime(System.currentTimeMillis())
            val dateStr = TXAFormat.formatDate(System.currentTimeMillis()).replace("/", "-")
            val logFile = File(logDir, "${prefix}_$dateStr.txt")
            logFile.appendText("[$timestamp] $message\n")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to write $prefix log to file", e)
        }
    }
    
    fun getLogFiles(): List<File> {
        return logDir?.listFiles()?.toList() ?: emptyList()
    }
}
