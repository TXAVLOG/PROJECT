package gc.txa.demo.core

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

object TXATranslation {

    private const val API_BASE = "https://soft.nrotxa.online/txademo/api"
    private const val CACHE_DIR = "languages"
    
    private var currentTranslations: Map<String, String> = emptyMap()
    private val gson = Gson()

    // Hardcoded fallback translations (English)
    private val fallbackTranslations = mapOf(
        // Core App
        "app_name" to "TXA Demo",
        "app_description" to "TXA Demo Application with OTA Updates",
        
        // Splash
        "splash_checking_permissions" to "Checking permissions...",
        "splash_requesting_permissions" to "Requesting permissions...",
        "splash_checking_language" to "Checking language updates...",
        "splash_downloading_language" to "Downloading translations...",
        "splash_language_updated" to "Language updated successfully",
        "splash_language_failed" to "Failed to update language",
        "splash_initializing" to "Initializing application...",
        
        // Demo Notice
        "demo_notice_title" to "Demo Version Notice",
        "demo_notice_description" to "This is a demo version with test features",
        "demo_notice_feature_1" to "✓ OTA Translation System",
        "demo_notice_feature_2" to "✓ MediaFire Download Resolver",
        "demo_notice_feature_3" to "✓ Background Update Checker",
        "demo_notice_feature_4" to "✓ Legacy Storage Support (Android 9)",
        "demo_notice_feature_5" to "✓ Multi-language Support",
        "demo_notice_confirm" to "Confirm",
        "demo_notice_warning" to "This version is for testing purposes only",
        
        // Settings
        "settings_title" to "Settings",
        "settings_app_info" to "Application Information",
        "settings_version" to "Version",
        "settings_app_set_id" to "App Set ID",
        "settings_language" to "Language",
        "settings_change_language" to "Change Language",
        "settings_update" to "Update",
        "settings_check_update" to "Check for Updates",
        "settings_about" to "About",
        
        // Language Names
        "lang_vi" to "Tiếng Việt",
        "lang_en" to "English",
        "lang_zh" to "中文",
        "lang_ja" to "日本語",
        "lang_ko" to "한국어",
        
        // Update Flow - Status
        "update_checking" to "Checking for updates...",
        "update_available" to "Update available",
        "update_not_available" to "You are using the latest version",
        "update_downloading" to "Downloading update...",
        "update_download_complete" to "Download complete",
        "update_installing" to "Installing update...",
        "update_install_prompt" to "Tap to install update",
        
        // Update Flow - Info
        "update_new_version" to "New version",
        "update_current_version" to "Current version",
        "update_changelog" to "Changelog",
        "update_file_size" to "File size",
        "update_download_progress" to "Progress",
        "update_download_speed" to "Speed",
        "update_download_eta" to "Time remaining",
        
        // Update Flow - Actions
        "update_download_now" to "Download Now",
        "update_install_now" to "Install Now",
        "update_cancel" to "Cancel",
        "update_later" to "Later",
        "update_retry" to "Retry",
        
        // Update Flow - Errors
        "error_update_check_failed" to "Failed to check for updates",
        "error_download_failed" to "Download failed",
        "error_install_failed" to "Installation failed",
        "error_network" to "Network error",
        "error_storage_permission" to "Storage permission required",
        "error_install_permission" to "Install permission required",
        "error_no_space" to "Insufficient storage space",
        "error_invalid_apk" to "Invalid APK file",
        "error_resolver_failed" to "Failed to resolve download URL",
        
        // Common Actions
        "action_ok" to "OK",
        "action_cancel" to "Cancel",
        "action_yes" to "Yes",
        "action_no" to "No",
        "action_close" to "Close",
        "action_retry" to "Retry",
        "action_continue" to "Continue",
        "action_back" to "Back",
        
        // Common Messages
        "msg_loading" to "Loading...",
        "msg_please_wait" to "Please wait...",
        "msg_success" to "Success",
        "msg_failed" to "Failed",
        "msg_error" to "Error",
        "msg_warning" to "Warning",
        "msg_info" to "Information",
        
        // Permissions
        "permission_storage_title" to "Storage Permission",
        "permission_storage_message" to "This app needs storage permission to download updates",
        "permission_install_title" to "Install Permission",
        "permission_install_message" to "This app needs permission to install updates",
        "permission_denied" to "Permission denied",
        "permission_required" to "Permission required to continue",
        
        // Formats
        "format_bytes" to "%s",
        "format_speed" to "%s/s",
        "format_percent" to "%s%%",
        "format_version" to "v%s"
    )

    /**
     * Get translation by key
     */
    fun txa(key: String): String {
        return currentTranslations[key] ?: fallbackTranslations[key] ?: key
    }

    /**
     * Get available locales from API
     */
    suspend fun getAvailableLocales(): List<LocaleInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$API_BASE/locales")
                .get()
                .build()

            val response = TXAHttp.getClient().newCall(request).execute()
            if (response.isSuccessful) {
                val json = response.body?.string() ?: return@withContext emptyList()
                val type = object : TypeToken<LocalesResponse>() {}.type
                val localesResponse: LocalesResponse = gson.fromJson(json, type)
                return@withContext localesResponse.supported_locales
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Return default locales if API fails
        return@withContext listOf(
            LocaleInfo("vi", "Tiếng Việt"),
            LocaleInfo("en", "English"),
            LocaleInfo("zh", "中文"),
            LocaleInfo("ja", "日本語"),
            LocaleInfo("ko", "한국어")
        )
    }

    /**
     * Sync translations if newer version available
     */
    suspend fun syncIfNewer(context: Context, locale: String = "vi"): SyncResult = withContext(Dispatchers.IO) {
        try {
            // Get cached file
            val cacheDir = File(context.filesDir, CACHE_DIR)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val cacheFile = File(cacheDir, "$locale.json")

            // Fetch from API
            val request = Request.Builder()
                .url("$API_BASE/tXALocale/$locale")
                .get()
                .build()

            val response = TXAHttp.getClient().newCall(request).execute()
            if (response.isSuccessful) {
                val json = response.body?.string() ?: return@withContext SyncResult.Failed("Empty response")
                
                // Save to cache
                cacheFile.writeText(json)
                
                // Load translations
                loadFromCache(context, locale)
                
                return@withContext SyncResult.Success
            } else {
                // Load from cache if API fails
                if (cacheFile.exists()) {
                    loadFromCache(context, locale)
                    return@withContext SyncResult.CachedUsed
                }
                return@withContext SyncResult.Failed("HTTP ${response.code}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Try to load from cache
            loadFromCache(context, locale)
            return@withContext SyncResult.Failed(e.message ?: "Unknown error")
        }
    }

    /**
     * Load translations from cache
     */
    private fun loadFromCache(context: Context, locale: String) {
        try {
            val cacheDir = File(context.filesDir, CACHE_DIR)
            val cacheFile = File(cacheDir, "$locale.json")
            
            if (cacheFile.exists()) {
                val json = cacheFile.readText()
                val type = object : TypeToken<Map<String, String>>() {}.type
                currentTranslations = gson.fromJson(json, type) ?: emptyMap()
            } else {
                currentTranslations = emptyMap()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            currentTranslations = emptyMap()
        }
    }

    /**
     * Force load translations (for initial load)
     */
    suspend fun forceLoad(context: Context, locale: String = "vi") {
        loadFromCache(context, locale)
    }

    // Data classes
    data class LocaleInfo(
        val tag: String,
        val name: String
    )

    data class LocalesResponse(
        val supported_locales: List<LocaleInfo>
    )

    sealed class SyncResult {
        object Success : SyncResult()
        object CachedUsed : SyncResult()
        data class Failed(val error: String) : SyncResult()
    }
}
