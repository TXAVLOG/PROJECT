package ms.txams.vv.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
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
 * - Storage: 
 *   - With permission: {APP_NAME}/logs/
 *   - Without permission: Android/data/{package}/files/logs/
 * 
 * @author TXA - fb.com/vlog.txa.2311 - txavlog7@gmail.com
 */
object TXALogger {
    
    private const val MAX_FILE_SIZE_BYTES = 1 * 1024 * 1024 // 1MB
    private const val APP_FOLDER_NAME = "TXA Music"
    private const val LOGS_FOLDER = "logs"
    private const val CACHE_FOLDER = "cache"
    private const val LANG_CACHE_FOLDER = "lang"
    
    private var context: Context? = null
    private var hasStoragePermission = false
    
    // Log types
    enum class LogType(val tag: String, val prefix: String) {
        CRASH("TXACRASH", "crash"),
        APP("TXAAPP", "app"),
        API("TXAAPI", "api"),
        DOWNLOAD("TXADOWNLOAD", "download")
    }
    
    /**
     * Initialize logger with context
     */
    fun init(ctx: Context) {
        context = ctx.applicationContext
        hasStoragePermission = checkStoragePermission()
        
        // Setup uncaught exception handler for crash logs
        setupCrashHandler()
        
        // Create necessary directories
        createDirectories()
        
        d(LogType.APP, "TXALogger initialized. Storage permission: $hasStoragePermission")
    }
    
    /**
     * Update permission status (call after permission granted)
     */
    fun updatePermissionStatus() {
        hasStoragePermission = checkStoragePermission()
        createDirectories()
        d(LogType.APP, "Permission status updated: $hasStoragePermission")
    }
    
    /**
     * Check if storage permission is granted
     */
    private fun checkStoragePermission(): Boolean {
        val ctx = context ?: return false
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - check MANAGE_EXTERNAL_STORAGE
            Environment.isExternalStorageManager()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-10 - check WRITE_EXTERNAL_STORAGE
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) == 
                PackageManager.PERMISSION_GRANTED
        } else {
            // Android 5 and below - permission granted by default
            true
        }
    }
    
    /**
     * Check if permission is needed
     */
    fun needsPermission(): Boolean {
        return !hasStoragePermission
    }
    
    /**
     * Get logs directory path
     */
    fun getLogsDir(): File {
        val ctx = context ?: throw IllegalStateException("TXALogger not initialized")
        
        return if (hasStoragePermission) {
            // With permission: /storage/emulated/0/TXA Music/logs/
            File(Environment.getExternalStorageDirectory(), "$APP_FOLDER_NAME/$LOGS_FOLDER")
        } else {
            // Without permission: Android/data/{package}/files/logs/
            File(ctx.getExternalFilesDir(null), LOGS_FOLDER)
        }
    }
    
    /**
     * Get language cache directory (always in app-specific storage)
     * Path: Android/data/{package}/files/cache/lang/
     */
    fun getLangCacheDir(): File {
        val ctx = context ?: throw IllegalStateException("TXALogger not initialized")
        return File(ctx.getExternalFilesDir(null), "$CACHE_FOLDER/$LANG_CACHE_FOLDER")
    }
    
    /**
     * Create necessary directories
     */
    private fun createDirectories() {
        try {
            getLogsDir().mkdirs()
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
                // Log crash
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
    private fun getLogFile(type: LogType): File {
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US)
        val dateStr = dateFormat.format(Date())
        val fileName = "TXA_${type.prefix}_$dateStr.log"
        return File(getLogsDir(), fileName)
    }
    
    /**
     * Write log to file
     */
    @Synchronized
    private fun writeToFile(type: LogType, level: String, message: String, throwable: Throwable? = null) {
        try {
            val logFile = getLogFile(type)
            
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
            Log.e(type.tag, "Failed to write log: ${e.message}")
        }
    }
    
    // ==================== Public Log Methods ====================
    
    /**
     * Debug log
     */
    fun d(type: LogType, message: String) {
        Log.d(type.tag, message)
        writeToFile(type, "DEBUG", message)
    }
    
    /**
     * Info log
     */
    fun i(type: LogType, message: String) {
        Log.i(type.tag, message)
        writeToFile(type, "INFO", message)
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
        writeToFile(type, "WARN", message, throwable)
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
        writeToFile(type, "ERROR", message, throwable)
    }
    
    /**
     * Crash log (always writes, even during startup)
     */
    fun crash(message: String, throwable: Throwable) {
        Log.e(LogType.CRASH.tag, message, throwable)
        writeToFile(LogType.CRASH, "CRASH", message, throwable)
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
            getLogsDir().listFiles()?.filter { it.isFile && it.name.endsWith(".log") } ?: emptyList()
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
        getAllLogFiles().forEach { it.delete() }
        appI("All logs cleared")
    }
    
    /**
     * Clear old log files (older than specified days)
     */
    fun clearOldLogs(daysToKeep: Int = 7) {
        val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        getAllLogFiles().forEach { file ->
            if (file.lastModified() < cutoffTime) {
                file.delete()
            }
        }
        appI("Old logs cleared (kept last $daysToKeep days)")
    }
}
