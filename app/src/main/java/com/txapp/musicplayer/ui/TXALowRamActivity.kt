package com.txapp.musicplayer.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.txapp.musicplayer.util.TXATranslation

/**
 * Super lightweight activity to show when the app runs out of memory (OOM).
 * Uses minimal resources and basic views to ensure it can display even when RAM is low.
 * Runs in a separate process (:error).
 */
class TXALowRamActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Use a very basic layout created in code to save memory/inflation time
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(64, 128, 64, 64)
            setBackgroundColor(Color.WHITE)
        }

        val title = TextView(this).apply {
            text = translate("txamusic_low_mem_title", "Low Memory Detected")
            textSize = 24f
            setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        
        val message = TextView(this).apply {
            val rawMsg = translate("txamusic_error_friendly_memory", "The app ran out of memory. Please try closing other apps.")
            text = "\n$rawMsg\n"
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 64)
        }

        val btnRestart = Button(this).apply {
            text = translate("txamusic_error_action_restart", "Restart App")
            setOnClickListener {
                restartApp()
            }
        }

        root.addView(title)
        root.addView(message)
        root.addView(btnRestart)

        setContentView(root)
    }

    private fun translate(key: String, default: String): String {
        // Since we are in a separate process, TXATranslation might not be fully loaded.
        // We use a safe way to get basic translations.
        val lang = getSystemLanguage()
        // Try to get from TXATranslation if it's somehow available, else use hardcoded/fallback
        return try {
            // Helper logic for minimalist translation access
            val fallbackMap = if (lang == "vi") {
                getFallbackVi()
            } else {
                getFallbackEn()
            }
            fallbackMap[key] ?: default
        } catch (e: Exception) {
            default
        }
    }

    private fun getSystemLanguage(): String {
        return try {
            val locale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                resources.configuration.locales[0]
            } else {
                @Suppress("DEPRECATION")
                resources.configuration.locale
            }
            if (locale.language == "vi") "vi" else "en"
        } catch (e: Exception) {
            "en"
        }
    }

    private fun restartApp() {
        val intent = Intent(this, SplashActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    // Direct access to fallback strings to avoid loading the whole TXATranslation object if heap is tight
    private fun getFallbackEn() = mapOf(
        "txamusic_low_mem_title" to "Low Memory Detected",
        "txamusic_error_friendly_memory" to "The app ran out of memory. This happens when the device has too many apps running at once. Please close other apps and try again.",
        "txamusic_error_action_restart" to "Restart App"
    )

    private fun getFallbackVi() = mapOf(
        "txamusic_low_mem_title" to "Phát hiện thiếu RAM",
        "txamusic_error_friendly_memory" to "Ứng dụng bị thoát do thiếu bộ nhớ RAM. Điều này thường xảy ra khi máy đang chạy quá nhiều ứng dụng cùng lúc. Vui lòng đóng bớt ứng dụng khác và thử lại.",
        "txamusic_error_action_restart" to "Khởi động lại"
    )
}
