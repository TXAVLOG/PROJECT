package ms.txams.vv.core

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object TXAHttp {

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Create a GET request
     */
    fun createGetRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .get()
            .build()
    }

    /**
     * Log message to public storage
     */
    fun logToPublic(context: Context, type: String, message: String) {
        try {
            val baseDir = context.getExternalFilesDir(null) ?: File("/storage/emulated/0")
            val logDir = File(baseDir, "TXAMusic/logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
            val now = Date()
            
            val logFile = File(logDir, "txa_${dateFormat.format(now)}.txt")
            val timestamp = timeFormat.format(now)
            val logEntry = "[$timestamp] [$type] $message\n"

            FileWriter(logFile, true).use { writer ->
                writer.append(logEntry)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Log error to public storage
     */
    fun logError(context: Context, tag: String, error: Throwable) {
        val message = "$tag: ${error.message}\n${error.stackTraceToString()}"
        logToPublic(context, "ERROR", message)
    }

    /**
     * Log info to public storage
     */
    fun logInfo(context: Context, tag: String, message: String) {
        logToPublic(context, "INFO", "$tag: $message")
    }
}
