package com.txapp.musicplayer.util

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import com.txapp.musicplayer.ui.TXAErrorActivity
import com.txapp.musicplayer.ui.TXALowRamActivity
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date
import kotlin.system.exitProcess

class TXACrashHandler private constructor(private val application: Application) : Thread.UncaughtExceptionHandler {

    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

    companion object {
        const val INTENT_DATA_ERROR_LOG = "intent_data_error_log"
        const val INTENT_DATA_ERROR_CODE = "intent_data_error_code"
        const val INTENT_DATA_SUGGESTION = "intent_data_suggestion"
        private const val MIN_CRASH_INTERVAL = 3000L // 3s
        private const val CRASH_LOOP_THRESHOLD = 3 // Number of crashes to consider it a loop
        private const val CRASH_LOOP_WINDOW = 60000L // 60s window to check for crash loops
        private var lastCrashTime = 0L
        private var crashCount = 0
        private var lastCrashStackHash = 0
        
        private var appContext: Context? = null

        /**
         * Global Coroutine Exception Handler for the entire app.
         */
        val GlobalCoroutineExceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
            appContext?.let {
                reportFatalError(it, throwable, "GlobalCoroutine", killProcess = true)
            }
        }

        fun init(application: Application) {
            appContext = application.applicationContext
            val handler = TXACrashHandler(application)
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }
        

        
        /**
         * Call this from any catch block to redirect to error screen.
         * Use for FATAL errors that should show the error screen.
         * 
         * @param context The context (Activity or Application context)
         * @param e The exception that was caught
         * @param tag Optional tag for logging
         * @param killProcess If true, kills the app after showing error screen (default: true)
         */
        fun reportFatalError(context: Context, e: Throwable, tag: String = "TXACrashHandler", killProcess: Boolean = true) {
            try {
                // Log the error (with safety)
                try {
                    TXALogger.appE(tag, "Fatal error caught: ${e.message}", e)
                } catch (ignored: Exception) {}
                
                val stackTrace = generateStackTrace(context, e)
                val errorType = detectErrorType(e, stackTrace)
                
                // Save log with extra safety
                try {
                    saveLogToFile(context, stackTrace)
                } catch (ignored: Exception) {}
                
                // Launch Error Activity
                val intent = if (e is OutOfMemoryError || stackTrace.contains("OutOfMemoryError")) {
                    Intent(context, TXALowRamActivity::class.java)
                } else {
                    Intent(context, TXAErrorActivity::class.java).apply {
                        putExtra(INTENT_DATA_ERROR_LOG, stackTrace)
                        putExtra(INTENT_DATA_ERROR_CODE, generateErrorCode(e))
                        putExtra(INTENT_DATA_SUGGESTION, errorType)
                    }
                }
                
                intent.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                }
                context.startActivity(intent)
                
                if (killProcess) {
                    // Critical: Give system enough time to start the new Task
                    // Android 9/older needs more time when process is dying
                    Thread.sleep(1200)
                    android.os.Process.killProcess(android.os.Process.myPid())
                    exitProcess(10)
                }
            } catch (ex: Exception) {
                // Last resort logging
                android.util.Log.e("TXACrashHandler", "Panic during error report", ex)
                if (killProcess) {
                    exitProcess(1)
                }
            }
        }
        
        /**
         * Call this from any catch block to log error and optionally show error screen.
         * Use for NON-FATAL errors that should be logged but not necessarily crash the app.
         * 
         * @param context The context
         * @param e The exception
         * @param tag Tag for logging
         * @param showErrorScreen If true, shows the error screen (default: false for non-fatal)
         */
        fun reportError(context: Context, e: Throwable, tag: String = "TXACrashHandler", showErrorScreen: Boolean = false) {
            TXALogger.appE(tag, "Error caught: ${e.message}", e)
            
            if (showErrorScreen) {
                reportFatalError(context, e, tag, killProcess = false)
            }
        }
        
        /**
         * Smart error handler - automatically detects if error is fatal and shows error screen accordingly.
         * Fatal errors: GMS missing, OutOfMemory, SecurityException, etc.
         * Non-fatal errors: Network timeouts, parsing errors, etc.
         * 
         * @param context The context
         * @param e The exception
         * @param tag Tag for logging
         */
        fun handleError(context: Context, e: Throwable, tag: String = "TXACrashHandler") {
            val isFatal = isFatalError(e)
            TXALogger.appE(tag, "Error caught (fatal=$isFatal): ${e.message}", e)
            
            if (isFatal) {
                reportFatalError(context, e, tag, killProcess = false)
            }
        }
        
        /**
         * Check if an exception is considered fatal and should show error screen
         */
        fun isFatalError(e: Throwable): Boolean {
            val message = e.message ?: ""
            val stackTrace = e.stackTraceToString()
            
            return when {
                // Memory errors - always fatal
                e is OutOfMemoryError -> true
                
                // Security/Permission errors - fatal
                e is SecurityException -> true
                stackTrace.contains("PermissionException") -> true
                
                // Google Play Services errors - fatal
                stackTrace.contains("com.google.android.gms") -> true
                stackTrace.contains("GooglePlayServicesNotAvailableException") -> true
                message.contains("Google Play Services") -> true
                
                // Resource errors - fatal
                e is android.content.res.Resources.NotFoundException -> true
                
                // Fatal state errors
                e is IllegalStateException && message.contains("Activity has been destroyed") -> true
                e is IllegalStateException && message.contains("Fragment already added") -> true
                
                // Database corruption
                stackTrace.contains("SQLiteException") && message.contains("corrupt") -> true
                
                // IO/File errors - only fatal if not network related
                e is java.io.IOException -> {
                    val msg = e.message?.lowercase() ?: ""
                    msg.contains("no space") || msg.contains("permission denied") || 
                    msg.contains("read-only") || msg.contains("disk full")
                }

                // Non-fatal errors (return false)
                e is java.net.SocketTimeoutException -> false
                e is java.net.UnknownHostException -> false
                e is org.json.JSONException -> false // Parsing errors
                e is NumberFormatException -> false
                e is NullPointerException -> false // Usually recoverable in catch (but check stacktrace if needed)
                
                // Default: not fatal
                else -> false
            }
        }
        
        /**
         * Convenience function to wrap a block of code with error handling.
         * If an exception occurs, it redirects to the error screen.
         */
        inline fun <T> runCatching(context: Context, tag: String = "TXACrashHandler", fatal: Boolean = true, block: () -> T): T? {
            return try {
                block()
            } catch (e: Exception) {
                if (fatal) {
                    reportFatalError(context, e, tag, killProcess = true)
                } else {
                    reportError(context, e, tag, showErrorScreen = false)
                }
                null
            }
        }
        
        private fun generateStackTrace(context: Context, e: Throwable): String {
            return try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                e.printStackTrace(pw)
                
                var versionName = "Unknown"
                try {
                    val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    versionName = pInfo.versionName
                } catch (ignored: Exception) {}

                val errorCode = generateErrorCode(e)
                
                """
                Error Code: $errorCode
                Timestamp: ${Date()}
                App Version: $versionName
                Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (${android.os.Build.DEVICE})
                OS API: ${android.os.Build.VERSION.SDK_INT} (Android ${getAndroidVersion(android.os.Build.VERSION.SDK_INT)})
                ------------------------------------
                $sw
                """.trimIndent()
            } catch (ex: Exception) {
                "Failed to generate stack trace: ${ex.message}\nOriginal Error: ${e.message}"
            }
        }
        
        private fun detectErrorType(e: Throwable, stackTrace: String): String {
            val message = e.message ?: ""
            return when {
                e is NullPointerException || stackTrace.contains("NullPointerException") -> "null"
                
                // Network
                e is java.io.IOException || stackTrace.contains("IOException") || 
                stackTrace.contains("SocketException") || stackTrace.contains("UnknownHostException") -> "network"
                
                // Memory
                e is OutOfMemoryError || stackTrace.contains("OutOfMemoryError") -> "memory"
                
                // Permission
                e is SecurityException || stackTrace.contains("SecurityException") || 
                stackTrace.contains("PermissionException") -> "permission"
                
                // GMS
                stackTrace.contains("com.google.android.gms") || 
                stackTrace.contains("GooglePlayServicesNotAvailableException") ||
                stackTrace.contains("Missing Google Play Services") -> "gms"
                
                // Resource
                e is android.content.res.Resources.NotFoundException -> "resource"
                
                // Activity/Fragment
                message.contains("Activity has been destroyed") -> "activity"
                message.contains("Fragment already added") -> "fragment"
                
                // Database
                stackTrace.contains("SQLiteException") && message.contains("corrupt") -> "database"
                
                else -> "unknown"
            }
        }
        
        private fun getSafeLogDir(context: Context): File {
            val external = context.getExternalFilesDir(null)
            val baseDir = if (external != null && android.os.Environment.getExternalStorageState() == android.os.Environment.MEDIA_MOUNTED) {
                File(external, "bug/reports")
            } else {
                // Fallback to internal storage if external is not available (e.g. emulator issues)
                File(context.filesDir, "bug/reports")
            }
            
            if (!baseDir.exists()) {
                TXASuHelper.mkdirs(baseDir)
            }
            return baseDir
        }

        private fun saveLogToFile(context: Context, content: String) {
            try {
                val logDir = getSafeLogDir(context)
                
                // Delete old logs
                logDir.listFiles()?.forEach { 
                    try { it.delete() } catch (ignored: Exception) {} 
                }

                val now = System.currentTimeMillis()
                // Format: TXA Music - bug_report - HH-mm-ss dd-MM-yy.txt
                // Replace invalid filename characters (:, /) from TXAFormat output
                val timeStr = TXAFormat.formatTime(now)
                    .replace(":", "-")
                    .replace("/", "-")
                
                val appName = "TXA Music"
                val fileName = "$appName - bug_report - $timeStr.txt"
                
                val file = File(logDir, fileName)
                file.writeText(content)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        private fun generateErrorCode(e: Throwable): String {
            val typePart = when (e) {
                is NullPointerException -> "NPE"
                is OutOfMemoryError -> "OOM"
                is IllegalStateException -> "ISE"
                is SecurityException -> "SEC"
                is java.io.IOException -> "IOE"
                else -> "UNK"
            }
            val hashPart = Math.abs(e.stackTraceToString().hashCode() % 10000).toString().padStart(4, '0')
            return "TXAAPP-$typePart-$hashPart"
        }

        private fun getAndroidVersion(sdk: Int): String {
            return when (sdk) {
                36 -> "16"
                35 -> "15"
                34 -> "14"
                33 -> "13"
                32 -> "12L"
                31 -> "12"
                30 -> "11"
                29 -> "10"
                28 -> "9"
                27 -> "8.1"
                26 -> "8.0"
                else -> sdk.toString()
            }
        }

        fun sendErrorToServer(context: Context, stackTrace: String, errorCode: String, onSuccess: (() -> Unit)? = null) {
            // Run in background to not block the main error flow
            Thread {
                try {
                    // Logic to extract only the exception and the first 'at' line, skipping metadata header
                    // Original stackTrace contains custom header (Timestamp, device info etc).
                    // We want to skip those and find the actual exception start.
                    val lines = stackTrace.lines()
                    // Detect typical exception patterns
                    val exceptionLineIndex = lines.indexOfFirst { 
                        it.trimStart().startsWith("java.") || 
                        it.trimStart().startsWith("kotlin.") || 
                        it.trimStart().startsWith("android.") ||
                        it.trimStart().startsWith("com.txapp")
                    }
                    
                    val summarizedStack = if (exceptionLineIndex != -1) {
                         // Take the exception line and the next few lines (e.g. 5 lines of trace)
                         lines.drop(exceptionLineIndex).take(6).joinToString("\n")
                    } else {
                        // Fallback: just take first 6 lines if pattern match fails
                        lines.take(6).joinToString("\n")
                    }
                    
                    val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    val versionName = pInfo.versionName

                    // Extract message from stack trace if possible
                    val message = if (exceptionLineIndex != -1 && exceptionLineIndex < lines.size) {
                        lines[exceptionLineIndex]
                    } else {
                        "Error Details in Log"
                    }

                    val json = org.json.JSONObject().apply {
                        put("error_code", errorCode)
                        put("app_version", versionName)
                        put("device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                        put("os_api", android.os.Build.VERSION.SDK_INT)
                        put("android_version", getAndroidVersion(android.os.Build.VERSION.SDK_INT))
                        put("message", message)
                        put("stack_trace", summarizedStack)
                        put("full_log_id", errorCode) // Use code as reference
                    }

                    // Log the API Request content to separate file
                    TXALogger.errorApiLog("TXACrashHandler", "Sending Error Report:\nURL: https://soft.nrotxa.online/txamusic/api/debug/crash-report\nBody: $json")

                    val url = java.net.URL("https://soft.nrotxa.online/txamusic/api/debug/crash-report")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json; utf-8")
                    conn.setRequestProperty("Accept", "application/json")
                    conn.connectTimeout = 5000 // 5s timeout
                    conn.readTimeout = 5000
                    conn.doOutput = true
                    
                    conn.outputStream.use { os ->
                        val input = json.toString().toByteArray(Charsets.UTF_8)
                        os.write(input, 0, input.size)
                    }

                    val responseCode = conn.responseCode
                    TXALogger.appD("TXACrashHandler", "Server report sent, response: $responseCode")
                    
                    if (responseCode in 200..299) {
                        onSuccess?.invoke()
                    }
                } catch (ex: Exception) {
                    TXALogger.appE("TXACrashHandler", "Failed to send error to server", ex)
                }
            }.start()
        }
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            // Check for recursive crash or fast-sequence crash
            val now = System.currentTimeMillis()
            if (now - lastCrashTime < MIN_CRASH_INTERVAL) {
                // Too many crashes, just let system handle it to avoid infinite loop
                defaultHandler?.uncaughtException(t, e)
                return
            }
            lastCrashTime = now

            // 1. Prepare Data securely
            val stackTrace = generateStackTrace(application, e)
            val errorType = detectErrorType(e, stackTrace)
            
            // Log to central system as well
            TXALogger.crashE("UncaughtException", "Fatal crash detected: ${e.message}", e)
            

            
            // 2. Save log (securely)
            try {
                saveLogToFile(application, stackTrace)
            } catch (ignored: Exception) {}

            // 3. Launch Error Activity
            val intent = if (e is OutOfMemoryError || stackTrace.contains("OutOfMemoryError")) {
                // VERY important: Use the lightweight OOM activity when out of memory
                Intent(application, TXALowRamActivity::class.java)
            } else {
                Intent(application, TXAErrorActivity::class.java).apply {
                    putExtra(INTENT_DATA_ERROR_LOG, stackTrace)
                    putExtra(INTENT_DATA_ERROR_CODE, generateErrorCode(e))
                    putExtra(INTENT_DATA_SUGGESTION, errorType)
                }
            }
            
            intent.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            }
            application.startActivity(intent)

            // 4. Wait for Intent to be sent before killing
            Thread.sleep(1200)

            // 5. Kill process
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(10)

        } catch (exception: Exception) {
            android.util.Log.e("TXACrashHandler", "Secondary crash in handler", exception)
            defaultHandler?.uncaughtException(t, e)
        }
    }
}
