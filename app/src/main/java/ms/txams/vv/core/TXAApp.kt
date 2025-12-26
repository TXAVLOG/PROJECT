package ms.txams.vv.core

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import dagger.hilt.android.HiltAndroidApp

/**
 * TXA Music Application
 * 
 * Initialization Order:
 * 1. TXALogger.init() - Crash logging available from here
 * 2. Version check - If unsupported, app will show dialog and exit
 * 3. TXATranslation.init() - Load translations (sync, instant)
 * 4. UI Ready
 * 5. Background sync (if supported)
 * 
 * Minimum Supported: Android 13 (API 33)
 * 
 * @author TXA - fb.com/vlog.txa.2311 - txavlog7@gmail.com
 */
@HiltAndroidApp
class TXAApp : Application() {
    
    companion object {
        // Minimum supported Android version
        const val MIN_SDK_VERSION = 33 // Android 13
        
        // Check if device is supported
        fun isDeviceSupported(): Boolean {
            return Build.VERSION.SDK_INT >= MIN_SDK_VERSION
        }
        
        // Get unsupported message
        fun getUnsupportedMessage(): String {
            return "Your Android version (${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT}) is not yet supported.\n\n" +
                   "TXA Music requires Android 13 (API 33) or higher.\n\n" +
                   "We will support older versions soon."
        }

        // --- NEW: Font Helper ---
        fun getCurrentFontResId(context: android.content.Context): Int {
            val prefs = context.getSharedPreferences("txa_prefs", android.content.Context.MODE_PRIVATE)
            val locale = prefs.getString("locale", "en") ?: "en"
            val fontName = prefs.getString("font_selection_$locale", "Soyuz Grotesk") ?: "Soyuz Grotesk"
            
            return when (fontName) {
                "Outfit" -> ms.txams.vv.R.font.outfit
                "Montserrat" -> ms.txams.vv.R.font.montserrat
                "Inter" -> ms.txams.vv.R.font.inter
                else -> ms.txams.vv.R.font.soyuz_grotesk
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Apply theme from preferences
        val prefs = getSharedPreferences("txa_prefs", android.content.Context.MODE_PRIVATE)
        val themeMode = prefs.getInt("theme_mode", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(themeMode)
        
        // Step 1: Initialize logger FIRST (for crash logging)
        try {
            TXALogger.init(this)
            TXALogger.appI("TXA Music App starting...")
            TXALogger.appI("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            TXALogger.appI("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        } catch (e: Exception) {
            // If logger fails, continue anyway - app should still work
            android.util.Log.e("TXAAPP", "Logger init failed: ${e.message}", e)
        }
        
        // Step 2: Check if device is supported
        if (!isDeviceSupported()) {
            TXALogger.appW("Device not supported: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            // Don't crash here - let SplashActivity show the dialog
            // Just log and continue
            return
        }
        
        // Step 3: Initialize translation system (sync - instant)
        try {
            val locale = prefs.getString("locale", "en") ?: "en"
            TXATranslation.init(this, locale)
            TXALogger.appI("Translation system initialized for: $locale")
        } catch (e: Exception) {
            TXALogger.appE("Translation init failed", e)
            // App can still work with fallback strings
        }
        
        TXALogger.appI("TXA Music App initialized successfully")
    }
}
