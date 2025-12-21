package gc.txa.demo.core

import android.content.Context
import gc.txa.demo.BuildConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

object TXATranslation {

    private const val API_BASE = "https://soft.nrotxa.online/txademo/api"
    private const val CACHE_DIR = "languages"
    private const val PREFS_NAME = "txa_translation_prefs"
    private const val KEY_API_MIGRATION_VERSION = "api_migration_version"
    private const val CURRENT_API_VERSION = 2
    private const val TAG = "Translation"
    
    private var currentTranslations: Map<String, String> = emptyMap()
    private val gson = Gson()

    // Hardcoded fallback translations (English)
    private val fallbackTranslations = mapOf(
        // Core App
        "txademo_app_name" to "TXA Demo",
        "txademo_app_description" to "TXA Demo Application with OTA Updates",
        
        // Splash
        "txademo_splash_checking_permissions" to "Checking permissions...",
        "txademo_splash_requesting_permissions" to "Requesting permissions...",
        "txademo_splash_checking_language" to "Checking language updates...",
        "txademo_splash_downloading_language" to "Downloading translations...",
        "txademo_splash_language_updated" to "Language updated successfully",
        "txademo_splash_language_failed" to "Failed to update language",
        "txademo_splash_initializing" to "Initializing application...",
        
        // Demo Notice
        "txademo_demo_notice_title" to "Demo Version Notice",
        "txademo_demo_notice_description" to "This is a demo version with test features",
        "txademo_demo_notice_feature_1" to "✓ OTA Translation System",
        "txademo_demo_notice_feature_2" to "✓ MediaFire Download Resolver",
        "txademo_demo_notice_feature_3" to "✓ Background Update Checker",
        "txademo_demo_notice_feature_4" to "✓ Legacy Storage Support (Android 9)",
        "txademo_demo_notice_feature_5" to "✓ Multi-language Support",
        "txademo_demo_notice_confirm" to "Confirm",
        "txademo_demo_notice_warning" to "This version is for testing purposes only",
        
        // Settings
        "txademo_settings_title" to "Settings",
        "txademo_settings_app_info" to "Application Information",
        "txademo_settings_version" to "Version",
        "txademo_settings_app_set_id" to "App Set ID",
        "txademo_settings_language" to "Language",
        "txademo_settings_change_language" to "Change Language",
        "txademo_settings_update" to "Update",
        "txademo_settings_check_update" to "Check for Updates",
        "txademo_settings_about" to "About",
        
        // Language Names
        "txademo_lang_vi" to "Tiếng Việt",
        "txademo_lang_en" to "English",
        "txademo_lang_zh" to "中文",
        "txademo_lang_ja" to "日本語",
        "txademo_lang_ko" to "한국어",
        
        // Update Flow - Status
        "txademo_update_checking" to "Checking for updates...",
        "txademo_update_available" to "Update available",
        "txademo_update_not_available" to "You are using the latest version",
        "txademo_update_downloading" to "Downloading update...",
        "txademo_update_download_complete" to "Download complete",
        "txademo_update_installing" to "Installing update...",
        "txademo_update_install_prompt" to "Tap to install update",
        
        // Update Flow - Info
        "txademo_update_new_version" to "New version",
        "txademo_update_current_version" to "Current version",
        "txademo_update_changelog" to "Changelog",
        "txademo_update_file_size" to "File size",
        "txademo_update_download_progress" to "Progress",
        "txademo_update_download_speed" to "Speed",
        "txademo_update_download_eta" to "Time remaining",
        
        // Update Flow - Actions
        "txademo_update_download_now" to "Download Now",
        "txademo_update_install_now" to "Install Now",
        "txademo_update_cancel" to "Cancel",
        "txademo_update_later" to "Later",
        "txademo_update_retry" to "Retry",
        
        // Update Flow - Errors
        "txademo_error_update_check_failed" to "Failed to check for updates",
        "txademo_error_download_failed" to "Download failed",
        "txademo_error_install_failed" to "Installation failed",
        "txademo_error_network" to "Network error",
        "txademo_error_storage_permission" to "Storage permission required",
        "txademo_error_install_permission" to "Install permission required",
        "txademo_error_no_space" to "Insufficient storage space",
        "txademo_error_invalid_apk" to "Invalid APK file",
        "txademo_error_resolver_failed" to "Failed to resolve download URL",
        
        // Common Actions
        "txademo_action_ok" to "OK",
        "txademo_action_cancel" to "Cancel",
        "txademo_action_yes" to "Yes",
        "txademo_action_no" to "No",
        "txademo_action_close" to "Close",
        "txademo_action_retry" to "Retry",
        "txademo_action_continue" to "Continue",
        "txademo_action_back" to "Back",
        
        // Common Messages
        "txademo_msg_loading" to "Loading...",
        "txademo_msg_please_wait" to "Please wait...",
        "txademo_msg_success" to "Success",
        "txademo_msg_failed" to "Failed",
        "txademo_msg_error" to "Error",
        "txademo_msg_warning" to "Warning",
        "txademo_msg_info" to "Information",
        
        // Permissions
        "txademo_permission_storage_title" to "Storage Permission",
        "txademo_permission_storage_message" to "This app needs storage permission to download updates",
        "txademo_permission_install_title" to "Install Permission",
        "txademo_permission_install_message" to "This app needs permission to install updates",
        "txademo_permission_denied" to "Permission denied",
        "txademo_permission_required" to "Permission required to continue",
        
        // Formats
        "txademo_format_bytes" to "%s",
        "txademo_format_speed" to "%s/s",
        "txademo_format_percent" to "%s%%",
        "txademo_format_version" to "v%s",
        
        // API Error Messages
        "txademo_error_metadata_unavailable" to "Update metadata not available",
        "txademo_error_download_url_missing" to "Download URL not available",
        "txademo_error_invalid_metadata" to "Invalid metadata format",
        "txademo_error_locale_not_found" to "Language not found",
        "txademo_error_invalid_locale_file" to "Invalid language file format",
        "txademo_error_update_check_failed" to "Failed to check for updates",
        "txademo_error_network" to "Network error",
        "txademo_error_server" to "Server error",
        "txademo_error_cache_invalid" to "Cache data is invalid, please refresh",
        
        // File Manager
        "txademo_file_manager_title" to "File Manager",
        "txademo_file_manager_empty_title" to "No downloaded files",
        "txademo_file_manager_empty_subtitle" to "Downloaded APKs will appear here",
        "txademo_file_manager_refresh" to "Refresh",
        "txademo_file_manager_cleanup" to "Clean Up",
        "txademo_file_manager_install" to "Install",
        "txademo_file_manager_delete" to "Delete",
        "txademo_file_manager_storage_path" to "Storage Path",
        "txademo_file_manager_files_count" to "%s files",
        "txademo_file_manager_total_size" to "Total Size: %s",
        "txademo_file_manager_delete_confirm" to "Delete File",
        "txademo_file_manager_delete_message" to "Are you sure you want to delete %s?",
        "txademo_file_manager_delete_success" to "File deleted successfully",
        "txademo_file_manager_delete_failed" to "Failed to delete file",
        "txademo_file_manager_install_success" to "Installation started",
        "txademo_file_manager_install_failed" to "Failed to start installation",
        "txademo_settings_file_manager" to "File Manager",
        "txademo_settings_open_file_manager" to "Open File Manager",
        
        // Download Notifications
        "txademo_download_background_title" to "TXA Demo Update",
        "txademo_download_background_starting" to "Starting download...",
        "txademo_download_background_progress" to "Downloading update...",
        "txademo_download_cancel" to "Cancel",
        "txademo_download_return_app" to "Return to App",
        "txademo_download_cancelled" to "Download Cancelled",
        "txademo_download_cancelled_message" to "The download has been cancelled",
        "txademo_download_complete" to "Download Complete",
        "txademo_download_complete_message" to "Update downloaded successfully",
        "txademo_download_failed" to "Download Failed",
        "txademo_download_failed_message" to "Failed to download update",
        "txademo_download_channel_name" to "TXA Downloads",
        "txademo_download_channel_description" to "Background download notifications"
    )

    /**
     * Get translation by key
     */
    fun txa(key: String): String {
        return currentTranslations[key] ?: fallbackTranslations[key] ?: key
    }

    /**
     * Get available locales from API (new format: simple array)
     */
    suspend fun getAvailableLocales(): List<LocaleInfo> = withContext(Dispatchers.IO) {
        try {
            TXALog.i(TAG, "Fetching available locales from API")
            val result = getLocalesWithRetry()
            TXALog.i(TAG, "Loaded ${result.size} locales from API: ${result.joinToString { it.tag }}")
            return@withContext result
        } catch (e: Exception) {
            TXALog.e(TAG, "Failed to load locales from API", e)
        }
        
        // Return default locales (fallback: only vi + en) if API fails
        TXALog.w(TAG, "Using fallback locale list (API unavailable)")
        return@withContext listOf(
            LocaleInfo("vi", "Tiếng Việt"),
            LocaleInfo("en", "English")
        )
    }
    
    /**
     * Get locales with retry logic
     */
    private suspend fun getLocalesWithRetry(maxRetries: Int = 3): List<LocaleInfo> {
        for (attempt in 1..maxRetries) {
            try {
                val request = Request.Builder()
                    .url("$API_BASE/locales")
                    .get()
                    .addHeader("User-Agent", "TXADemo-Android/${BuildConfig.VERSION_NAME}")
                    .build()

                TXALog.d(TAG, "Fetching locale list: $API_BASE/locales (attempt $attempt)")
                val response = TXAHttp.client.newCall(request).execute()
                TXALog.d(TAG, "Locales response code: ${response.code}, message: ${response.message}")
                if (response.isSuccessful) {
                    val json = response.body?.string() ?: throw IOException("Empty response")
                    TXALog.v(TAG, "Locales response JSON: ${json.take(200)}")
                    
                    // Parse new format: simple array ["en", "vi"]
                    val localeArray: List<String> = try {
                        gson.fromJson(json, object : TypeToken<List<String>>() {}.type)
                    } catch (e: Exception) {
                        TXALog.e(TAG, "Locales JSON parsing failed", e)
                        throw IOException("Invalid locales array format")
                    }
                    
                    // Convert to LocaleInfo objects
                    val locales = localeArray.map { locale ->
                        when (locale) {
                            "vi" -> LocaleInfo("vi", "Tiếng Việt")
                            "en" -> LocaleInfo("en", "English")
                            "zh" -> LocaleInfo("zh", "中文")
                            "ja" -> LocaleInfo("ja", "日本語")
                            "ko" -> LocaleInfo("ko", "한국어")
                            else -> LocaleInfo(locale, locale.uppercase())
                        }
                    }
                    TXALog.i(TAG, "Locale API returned: ${locales.joinToString { it.tag }}")
                    return locales
                } else {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
            } catch (e: Exception) {
                TXALog.e(TAG, "Locale fetch failed on attempt $attempt", e)
                if (attempt == maxRetries) {
                    throw e
                }
                TXALog.w(TAG, "Retrying locale fetch after ${1000L * attempt}ms")
                delay(1000L * attempt) // Exponential backoff
            }
        }
        throw IOException("Failed to fetch locales after $maxRetries attempts")
    }

    /**
     * Sync translations if newer version available (with timestamp comparison)
     */
    suspend fun syncIfNewer(context: Context, locale: String = "vi"): SyncResult = withContext(Dispatchers.IO) {
        try {
            // Get cached file and metadata
            val cacheDir = File(context.filesDir, CACHE_DIR)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val cacheFile = File(cacheDir, "$locale.json")
            val metadataFile = File(cacheDir, "$locale.meta.json")
            
            // Get cached timestamp
            val cachedUpdatedAt = if (metadataFile.exists()) {
                try {
                    val metadata = gson.fromJson(metadataFile.readText(), CachedMetadata::class.java)
                    metadata.updatedAt
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
            
            // Fetch from API with retry
            val apiResult = fetchLocaleWithRetry(locale, cachedUpdatedAt)
            
            when (apiResult) {
                is LocaleFetchResult.NotModified -> {
                    // Use cached version
                    if (cacheFile.exists()) {
                        loadFromCache(context, locale)
                        return@withContext SyncResult.CachedUsed
                    }
                    return@withContext SyncResult.Failed("No cached data available")
                }
                
                is LocaleFetchResult.Success -> {
                    // Save new translations and metadata
                    cacheFile.writeText(apiResult.translationsJson)
                    
                    val metadata = CachedMetadata(
                        locale = locale,
                        updatedAt = apiResult.updatedAt,
                        cachedAt = System.currentTimeMillis()
                    )
                    metadataFile.writeText(gson.toJson(metadata))
                    
                    // Load translations
                    loadFromCache(context, locale)
                    
                    return@withContext SyncResult.Success
                }
                
                is LocaleFetchResult.Error -> {
                    // Try to load from cache if API fails
                    if (cacheFile.exists()) {
                        loadFromCache(context, locale)
                        return@withContext SyncResult.CachedUsed
                    }
                    return@withContext SyncResult.Failed(apiResult.message)
                }
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            // Try to load from cache
            loadFromCache(context, locale)
            return@withContext SyncResult.Failed(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Fetch locale with retry logic and timestamp comparison
     */
    @Suppress("UNUSED_PARAMETER", "UNREACHABLE_CODE")
    private suspend fun fetchLocaleWithRetry(
        locale: String,
        cachedUpdatedAt: String?,
        maxRetries: Int = 3
    ): LocaleFetchResult {
        
        for (attempt in 1..maxRetries) {
            try {
                val url = "$API_BASE/tXALocale/$locale"
                TXALog.i(TAG, "Attempt $attempt: Fetching locale from $url")
                
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("User-Agent", "TXADemo-Android/${gc.txa.demo.BuildConfig.VERSION_NAME}")
                    .build()

                TXALog.d(TAG, "Making request to: $url")
                val response = TXAHttp.client.newCall(request).execute()
                TXALog.d(TAG, "Response code: ${response.code}, message: ${response.message}")
                
                if (!response.isSuccessful) {
                    TXALog.e(TAG, "HTTP error: ${response.code} - ${response.message}")
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                
                val json = response.body?.string() 
                    ?: throw IOException("Empty response body")
                
                TXALog.d(TAG, "Response JSON length: ${json.length} chars")
                TXALog.v(TAG, "Response JSON preview: ${json.take(200)}...")
                
                // Parse response as translation map
                val translations: Map<String, String> = try {
                    val type = object : TypeToken<Map<String, String>>() {}.type
                    gson.fromJson<Map<String, String>>(json, type) ?: emptyMap()
                } catch (e: Exception) {
                    TXALog.e(TAG, "JSON parsing failed", e)
                    throw IOException("Invalid JSON response: ${e.message}")
                }
                
                TXALog.i(TAG, "Successfully parsed ${translations.size} translations")
                
                // Always return new translations since API doesn't provide updated_at
                return LocaleFetchResult.Success(
                    translationsJson = json,
                    updatedAt = System.currentTimeMillis().toString()
                )
                
            } catch (e: Exception) {
                TXALog.e(TAG, "Network request failed on attempt $attempt", e)
                TXALog.e(TAG, "Exception type: ${e.javaClass.simpleName}, message: ${e.message}")
                
                if (attempt == maxRetries) {
                    val errorMessage = when {
                        e.message?.contains("HTTP 404") == true -> 
                            txa("txademo_error_locale_not_found")
                        e.message?.contains("JSON") == true -> 
                            txa("txademo_error_invalid_locale_file")
                        e.message?.contains("HTTP") == true -> 
                            txa("txademo_error_network")
                        e.message?.contains("UnknownHost") == true ->
                            "DNS resolution failed for $API_BASE"
                        e.message?.contains("SSL") == true ->
                            "SSL/TLS connection failed"
                        e.message?.contains("timeout") == true ->
                            "Connection timeout"
                        else -> e.message ?: "Unknown error: ${e.javaClass.simpleName}"
                    }
                    TXALog.e(TAG, "Final error message: $errorMessage")
                    return LocaleFetchResult.Error(errorMessage)
                }
                TXALog.w(TAG, "Retrying after ${1000L * attempt}ms delay...")
                delay(1000L * attempt) // Exponential backoff
            }
        }
        
        return LocaleFetchResult.Error("Failed after $maxRetries attempts")
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
        data class Failed(val message: String) : SyncResult()
    }
    
    // Locale fetch result for timestamp comparison
    sealed class LocaleFetchResult {
        data class Success(val translationsJson: String, val updatedAt: String) : LocaleFetchResult()
        object NotModified : LocaleFetchResult()
        data class Error(val message: String) : LocaleFetchResult()
    }
    
    // Cached metadata for timestamp tracking
    data class CachedMetadata(
        val locale: String,
        val updatedAt: String,
        val cachedAt: Long,
        val appVersionCode: Int = BuildConfig.VERSION_CODE
    )
    
    /**
     * Clear invalid cache when app updates or API format changes
     */
    suspend fun clearInvalidCache(context: Context) {
        try {
            withContext(Dispatchers.IO) {
                val cacheDir = File(context.filesDir, CACHE_DIR)
                if (cacheDir.exists()) {
                    val currentVersionCode = BuildConfig.VERSION_CODE
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val lastApiVersion = prefs.getInt(KEY_API_MIGRATION_VERSION, 1)
                    
                    // Force clear cache if API version changed
                    val shouldClearAll = lastApiVersion != CURRENT_API_VERSION
                    
                    cacheDir.listFiles()?.forEach { file ->
                        if (file.name.endsWith(".meta.json")) {
                            try {
                                val metadata = gson.fromJson(file.readText(), CachedMetadata::class.java)
                                // Clear cache if it's from a different app version or API format changed
                                if (metadata.appVersionCode != currentVersionCode || shouldClearAll) {
                                    val localeFile = File(cacheDir, "${metadata.locale}.json")
                                    localeFile.delete()
                                    file.delete()
                                }
                            } catch (e: Exception) {
                                // Delete corrupted metadata files
                                file.delete()
                            }
                        }
                    }
                    
                    // Update API migration version
                    if (shouldClearAll) {
                        prefs.edit()
                            .putInt(KEY_API_MIGRATION_VERSION, CURRENT_API_VERSION)
                            .apply()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
