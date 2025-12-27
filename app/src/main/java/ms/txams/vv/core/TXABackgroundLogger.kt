package ms.txams.vv.core

import android.util.Log

/**
 * TXA Background Logger
 * Specialized logger for background tasks and update checks.
 * Optimized for ADB monitoring using tag: TXA_BG_CHECK
 */
object TXABackgroundLogger {
    private const val TAG = "TXA_BG_CHECK"

    fun i(message: String) {
        Log.i(TAG, " [INFO] $message")
        TXALogger.appI("[BG] $message")
    }

    fun d(message: String) {
        Log.d(TAG, " [DEBUG] $message")
        TXALogger.appD("[BG] $message")
    }

    fun w(message: String) {
        Log.w(TAG, " [WARN] $message")
        TXALogger.appW("[BG] $message")
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, " [ERROR] $message", throwable)
        TXALogger.appE("[BG] $message", throwable)
    }

    fun logStatus(batteryOptimized: Boolean, updateEnabled: Boolean) {
        val status = StringBuilder("--- BACKGROUND STATUS ---\n")
        status.append(" > Battery Optimization Ignored: $batteryOptimized\n")
        status.append(" > Background Update Enabled: $updateEnabled\n")
        status.append(" -------------------------")
        i(status.toString())
    }
}
