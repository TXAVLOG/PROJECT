package ms.txams.vv.core

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TXA Logger - Centralized logging system for TXA Music
 * 
 * Features:
 * - Log to file with daily rotation
 * - Max file size 1MB (auto-rotate)
 * - Log types: CRASH, APP, API, DOWNLOAD
 * - ADB tag format: TXA+{type} (e.g., TXACRASH, TXAAPP, TXAAPI, TXADOWNLOAD)
 * - Storage: Always uses app-specific storage (no extra permission needed)
 *   - Android/data/{package}/files/logs/
 *   - Android/data/{package}/files/cache/lang/
 * 
 * Compatibility: Android 6+ (API 23+)
 * 
 * @author TXA - fb.com/vlog.txa.2311 - txavlog7@gmail.com
 */
object TXALogger {
    
    private const val MAX_FILE_SIZE_BYTES = 1 * 1024 * 1024 // 1MB
    private const val LOGS_FOLDER = "logs"
    private const val CACHE_FOLDER = "cache"
    private const val LANG_CACHE_FOLDER = "lang"
    
    private var context: Context? = null
    private var isInitialized = false
    
    // Log types with ADB tags
    enum class LogType(val tag: String, val prefix: String) {
        CRASH("TXACRASH", "crash"),
        APP("TXAAPP", "app"),
        API("TXAAPI", "api"),
        DOWNLOAD("TXADOWNLOAD", "download")
    }

    /**
     * Initialize logger with context
     * Safe to call multiple times
     */
    fun init(ctx: Context) {
        if (isInitialized) return
        
        try {
            context = ctx.applicationContext
            
            // Setup uncaught exception handler for crash logs
            setupCrashHandler()
            
            // Create directories safely
            createDirectoriesSafe()
            
            isInitialized = true
            d(LogType.APP, "TXALogger initialized successfully")
        } catch (e: Exception) {
            // Log to logcat only if init fails
            Log.e("TXAAPP", "TXALogger init failed: ${e.message}", e)
        }
    }
    
    /**
     * Check if logger is initialized
     */
    fun isReady(): Boolean = isInitialized && context != null

    /**
     * Get logs directory path - SAFE, handles null
     * Path: Android/data/{package}/files/logs/
     */
    fun getLogsDir(): File? {
        val ctx = context ?: return null
        
        return try {
            // Use app-specific external storage (no permission needed on Android 4.4+)
            val filesDir = ctx.getExternalFilesDir(null)
            if (filesDir != null) {
                File(filesDir, LOGS_FOLDER)
            } else {
                // Fallback to internal storage if external not available
                File(ctx.filesDir, LOGS_FOLDER)
            }
        } catch (e: Exception) {
            Log.e("TXAAPP", "getLogsDir failed: ${e.message}")
            // Ultimate fallback to internal storage
            try {
                File(ctx.filesDir, LOGS_FOLDER)
            } catch (e2: Exception) {
                null
            }
        }
    }
    
    /**
     * Get language cache directory - SAFE, handles null
     * Path: Android/data/{package}/files/cache/lang/
     * 
     * NOTE: This is app-specific storage, no special permission needed on Android 4.4+
     */
    fun getLangCacheDir(): File {
        val ctx = context ?: throw IllegalStateException("TXALogger not initialized. Call TXALogger.init() first.")
        
        return try {
            val filesDir = ctx.getExternalFilesDir(null)
            if (filesDir != null) {
                File(filesDir, "$CACHE_FOLDER/$LANG_CACHE_FOLDER")
            } else {
                // Fallback to internal storage
                File(ctx.filesDir, "$CACHE_FOLDER/$LANG_CACHE_FOLDER")
            }
        } catch (e: Exception) {
            Log.e("TXAAPP", "getLangCacheDir failed, using internal: ${e.message}")
            // Ultimate fallback to internal storage
            File(ctx.filesDir, "$CACHE_FOLDER/$LANG_CACHE_FOLDER")
        }
    }
    
    /**
     * Get internal cache directory (always available)
     */
    fun getInternalCacheDir(): File? {
        return context?.cacheDir
    }

    /**
     * Create necessary directories safely
     */
    private fun createDirectoriesSafe() {
        try {
            getLogsDir()?.mkdirs()
            getLangCacheDir().mkdirs()
        } catch (e: Exception) {
            Log.e("TXAAPP", "Failed to create directories: ${e.message}")
        }
    }
    
    /**
     * Setup uncaught exception handler for crash logs
     */
    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // Log crash to file and logcat
                crash("Uncaught exception in thread ${thread.name}", throwable)
            } catch (e: Exception) {
                Log.e("TXACRASH", "Failed to log crash: ${e.message}")
            } finally {
                // Call default handler
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
    
    /**
     * Get log file for specific type and date
     */
    private fun getLogFile(type: LogType): File? {
        val logsDir = getLogsDir() ?: return null
        
        return try {
            val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US)
            val dateStr = dateFormat.format(Date())
            val fileName = "TXA_${type.prefix}_$dateStr.log"
            File(logsDir, fileName)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Write log to file - SAFE, handles all errors
     */
    @Synchronized
    private fun writeToFile(type: LogType, level: String, message: String, throwable: Throwable? = null) {
        try {
            val logFile = getLogFile(type) ?: return
            
            // Check file size, rotate if needed
            if (logFile.exists() && logFile.length() > MAX_FILE_SIZE_BYTES) {
                logFile.delete()
            }
            
            // Ensure parent directory exists
            logFile.parentFile?.mkdirs()
            
            // Build log entry
            val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
            val timestamp = timeFormat.format(Date())
            val logEntry = StringBuilder()
            logEntry.append("[$timestamp] [$level] $message\n")
            
            // Add stack trace if throwable provided
            if (throwable != null) {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                logEntry.append(sw.toString())
                logEntry.append("\n")
            }
            
            // Append to file
            FileOutputStream(logFile, true).use { fos ->
                fos.write(logEntry.toString().toByteArray())
            }
        } catch (e: Exception) {
            // Silent fail - don't crash app because of logging failure
            Log.e(type.tag, "Failed to write log to file: ${e.message}")
        }
    }
    
    // ==================== Public Log Methods ====================
    
    /**
     * Debug log
     */
    fun d(type: LogType, message: String) {
        Log.d(type.tag, message)
        if (isInitialized) {
            writeToFile(type, "DEBUG", message)
        }
    }
    
    /**
     * Info log
     */
    fun i(type: LogType, message: String) {
        Log.i(type.tag, message)
        if (isInitialized) {
            writeToFile(type, "INFO", message)
        }
    }
    
    /**
     * Warning log
     */
    fun w(type: LogType, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(type.tag, message, throwable)
        } else {
            Log.w(type.tag, message)
        }
        if (isInitialized) {
            writeToFile(type, "WARN", message, throwable)
        }
    }
    
    /**
     * Error log
     */
    fun e(type: LogType, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(type.tag, message, throwable)
        } else {
            Log.e(type.tag, message)
        }
        if (isInitialized) {
            writeToFile(type, "ERROR", message, throwable)
        }
    }
    
    /**
     * Crash log (always writes, even during startup)
     * This logs to both file and logcat
     */
    fun crash(message: String, throwable: Throwable) {
        Log.e(LogType.CRASH.tag, message, throwable)
        // Try to write even if not fully initialized
        try {
            writeToFile(LogType.CRASH, "CRASH", message, throwable)
        } catch (e: Exception) {
            Log.e(LogType.CRASH.tag, "Could not write crash to file: ${e.message}")
        }
    }
    
    // ==================== Shortcut Methods ====================
    
    // APP logs
    fun appD(message: String) = d(LogType.APP, message)
    fun appI(message: String) = i(LogType.APP, message)
    fun appW(message: String, t: Throwable? = null) = w(LogType.APP, message, t)
    fun appE(message: String, t: Throwable? = null) = e(LogType.APP, message, t)
    
    // API logs
    fun apiD(message: String) = d(LogType.API, message)
    fun apiI(message: String) = i(LogType.API, message)
    fun apiW(message: String, t: Throwable? = null) = w(LogType.API, message, t)
    fun apiE(message: String, t: Throwable? = null) = e(LogType.API, message, t)
    
    // DOWNLOAD logs
    fun downloadD(message: String) = d(LogType.DOWNLOAD, message)
    fun downloadI(message: String) = i(LogType.DOWNLOAD, message)
    fun downloadW(message: String, t: Throwable? = null) = w(LogType.DOWNLOAD, message, t)
    fun downloadE(message: String, t: Throwable? = null) = e(LogType.DOWNLOAD, message, t)
    
    // ==================== Utility Methods ====================
    
    /**
     * Get all log files
     */
    fun getAllLogFiles(): List<File> {
        return try {
            getLogsDir()?.listFiles()?.filter { it.isFile && it.name.endsWith(".log") } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get total logs size in bytes
     */
    fun getTotalLogsSize(): Long {
        return getAllLogFiles().sumOf { it.length() }
    }
    
    /**
     * Clear all log files
     */
    fun clearAllLogs() {
        getAllLogFiles().forEach { 
            try { it.delete() } catch (e: Exception) { /* ignore */ }
        }
        appI("All logs cleared")
    }
    
    /**
     * Clear old log files (older than specified days)
     */
    fun clearOldLogs(daysToKeep: Int = 7) {
        val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        getAllLogFiles().forEach { file ->
            try {
                if (file.lastModified() < cutoffTime) {
                    file.delete()
                }
            } catch (e: Exception) { /* ignore */ }
        }
        appI("Old logs cleared (kept last $daysToKeep days)")
    }
    
    /**
     * Get device and app info for debugging
     */
    fun getDeviceInfo(): String {
        return buildString {
            appendLine("=== Device Info ===")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Storage State: ${Environment.getExternalStorageState()}")
            appendLine("Logs Dir: ${getLogsDir()?.absolutePath ?: "null"}")
            appendLine("Cache Dir: ${getLangCacheDir().absolutePath}")
            appendLine("==================")
        }
    }
}
