package ms.txams.vv.core

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ms.txams.vv.R
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.*

/**
 * TXA Translation System - Hệ thống dịch đa ngôn ngữ với fallback 3 lớp
 * 1. OTA Cache > 2. Hardcoded Map > 3. Context.getString()
 * 
 * Sử dụng hàm extension String.txa() hoặc toàn cục txa(key) để dịch
 */
class TXATranslation private constructor(private val context: Context) {

    private val gson = Gson()
    private var currentTranslations: Map<String, String> = emptyMap()
    private var currentLocale = "en"
    private val cacheDir = File(context.cacheDir, "translations")
    
    companion object {
        @Volatile
        private var INSTANCE: TXATranslation? = null
        
        fun getInstance(context: Context): TXATranslation {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TXATranslation(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        // API endpoints
        private const val API_BASE = "https://soft.nrotxa.online/txamusic/api"
        private const val LOCALES_ENDPOINT = "$API_BASE/locales"
        private const val TRANSLATION_ENDPOINT = "$API_BASE/tXALocale"
        
        // Fallback translations (English) - Sync with translation_keys_en.json
        private val fallbackTranslations = mapOf(
            // App Info
            "txamusic_app_name" to "TXA Music",
            "txamusic_app_description" to "TXA Music – Dynamic music player with OTA updates and now bar UI",
            "txamusic_app_incompatible" to "Your Android version is not yet supported, we will support it soon",
            
            // Splash Screen
            "txamusic_splash_checking_permissions" to "Checking permissions...",
            "txamusic_splash_requesting_permissions" to "Requesting permissions...",
            "txamusic_splash_checking_language" to "Checking language updates...",
            "txamusic_splash_downloading_language" to "Downloading translations...",
            "txamusic_splash_language_updated" to "Language updated successfully",
            "txamusic_splash_language_failed" to "Failed to update language",
            "txamusic_splash_initializing" to "Initializing application...",
            "txamusic_loading_language" to "Loading language data...",
            "txamusic_checking_data" to "Checking data...",
            "txamusic_error_connection" to "Connection error, using backup data",
            "txamusic_entering_app" to "Entering app...",
            
            // Settings
            "txamusic_settings_title" to "Settings",
            "txamusic_settings_app_info" to "Application Information",
            "txamusic_settings_version" to "Version",
            "txamusic_settings_app_set_id" to "App Set ID",
            "txamusic_settings_language" to "Language",
            "txamusic_settings_change_language" to "Change Language",
            "txamusic_settings_update" to "Update",
            "txamusic_settings_check_update" to "Check for Updates",
            "txamusic_settings_about" to "About",
            "txamusic_settings_music_library" to "Music Library",
            "txamusic_settings_open_music_library" to "Open Music Library",
            "txamusic_settings_file_manager" to "File Manager",
            "txamusic_settings_open_file_manager" to "Open File Manager",
            
            // Languages
            "txamusic_lang_vi" to "Tiếng Việt",
            "txamusic_lang_en" to "English",
            "txamusic_lang_zh" to "中文",
            "txamusic_lang_ja" to "日本語",
            "txamusic_lang_ko" to "한국어",
            
            // Update System
            "txamusic_update_checking" to "Checking for updates...",
            "txamusic_update_available" to "Update available",
            "txamusic_update_not_available" to "You are using the latest version",
            "txamusic_update_downloading" to "Downloading update...",
            "txamusic_update_download_complete" to "Download complete",
            "txamusic_update_installing" to "Installing update...",
            "txamusic_update_install_prompt" to "Tap to install update",
            "txamusic_update_new_version" to "New version",
            "txamusic_update_current_version" to "Current version",
            "txamusic_update_changelog" to "Changelog",
            "txamusic_update_changelog_empty" to "No changelog was provided for this version.",
            "txamusic_update_file_size" to "File size",
            "txamusic_update_download_progress" to "Progress",
            "txamusic_update_download_speed" to "Speed",
            "txamusic_update_download_eta" to "Time remaining",
            "txamusic_update_on_default" to "Updated on",
            "txamusic_update_time_unavailable" to "Release time not provided",
            "txamusic_update_download_now" to "Download Now",
            "txamusic_update_install_now" to "Install Now",
            "txamusic_update_cancel" to "Cancel",
            "txamusic_update_later" to "Later",
            "txamusic_update_retry" to "Retry",
            "txamusic_update_install" to "Install",
            "txamusic_update_on" to "UPDATE ON %s",
            "txamusic_update_notification_body" to "Version %s is available to download",
            "txamusic_update_notification_channel_name" to "TXA Update Alerts",
            "txamusic_update_notification_channel_description" to "Receive notifications when a new version is ready",
            
            // Errors
            "txamusic_error" to "Error",
            "txamusic_error_update_check_failed" to "Failed to check for updates",
            "txamusic_error_download_failed" to "Download failed",
            "txamusic_error_install_failed" to "Installation failed",
            "txamusic_error_network" to "Network error",
            "txamusic_error_storage_permission" to "Storage permission required",
            "txamusic_error_install_permission" to "Install permission required",
            "txamusic_error_no_space" to "Insufficient storage space",
            "txamusic_error_invalid_apk" to "Invalid APK file",
            "txamusic_error_resolver_failed" to "Failed to resolve download URL",
            "txamusic_error_download" to "Download Error",
            "txamusic_error_install" to "Installation Error",
            "txamusic_error_load_failed" to "Failed to load music",
            "txamusic_error_metadata_unavailable" to "Update metadata not available",
            "txamusic_error_download_url_missing" to "Download URL not available",
            "txamusic_error_invalid_metadata" to "Invalid metadata format",
            "txamusic_error_locale_not_found" to "Language not found",
            "txamusic_error_invalid_locale_file" to "Invalid language file format",
            "txamusic_error_server" to "Server error",
            "txamusic_error_cache_invalid" to "Cache data is invalid, please refresh",
            "txamusic_error_no_music" to "No music files found",
            
            // Actions
            "txamusic_action_ok" to "OK",
            "txamusic_action_cancel" to "Cancel",
            "txamusic_action_yes" to "Yes",
            "txamusic_action_no" to "No",
            "txamusic_action_close" to "Close",
            "txamusic_action_retry" to "Retry",
            "txamusic_action_continue" to "Continue",
            "txamusic_action_back" to "Back",
            "txamusic_retry" to "Retry",
            "txamusic_settings" to "Settings",
            "txamusic_music_library" to "Music Library",
            "txamusic_check_update" to "Check for Update",
            "txamusic_download_update" to "Download Update",
            "txamusic_no_update" to "No Update Available",
            "txamusic_exit" to "Exit",
            "txamusic_ok" to "OK",
            "txamusic_grant" to "Grant",
            
            // Messages
            "txamusic_msg_loading" to "Loading...",
            "txamusic_msg_please_wait" to "Please wait...",
            "txamusic_msg_success" to "Success",
            "txamusic_msg_failed" to "Failed",
            "txamusic_msg_error" to "Error",
            "txamusic_msg_warning" to "Warning",
            "txamusic_msg_info" to "Information",
            "txamusic_loading" to "Loading...",
            
            // Permissions
            "txamusic_permission_storage_title" to "Storage Permission",
            "txamusic_permission_storage_message" to "This app needs storage permission to download updates",
            "txamusic_permission_install_title" to "Install Permission",
            "txamusic_permission_install_message" to "This app needs permission to install updates",
            "txamusic_permission_denied" to "Permission denied",
            "txamusic_permission_required" to "Permission required to continue",
            "txamusic_permission_audio_rationale" to "This app needs access to your audio files to display your music library.",
            
            // File Manager
            "txamusic_file_manager_title" to "File Manager",
            "txamusic_file_manager_empty_title" to "No downloaded files",
            "txamusic_file_manager_empty_subtitle" to "Downloaded APKs will appear here",
            "txamusic_file_manager_refresh" to "Refresh",
            "txamusic_file_manager_cleanup" to "Clean Up",
            "txamusic_file_manager_install" to "Install",
            "txamusic_file_manager_delete" to "Delete",
            "txamusic_file_manager_storage_path" to "Storage Path",
            "txamusic_file_manager_files_count" to "%s files",
            "txamusic_file_manager_total_size" to "Total Size: %s",
            "txamusic_file_manager_delete_confirm" to "Delete File",
            "txamusic_file_manager_delete_message" to "Are you sure you want to delete %s?",
            "txamusic_file_manager_delete_success" to "File deleted successfully",
            "txamusic_file_manager_delete_failed" to "Failed to delete file",
            "txamusic_file_manager_install_success" to "Installation started",
            "txamusic_file_manager_install_failed" to "Failed to start installation",
            "txamusic_file_not_found" to "Downloaded file not found",
            
            // Download
            "txamusic_download_background_title" to "TXA Music Update",
            "txamusic_download_background_starting" to "Starting download...",
            "txamusic_download_background_progress" to "Downloading update...",
            "txamusic_download_cancel" to "Cancel",
            "txamusic_download_return_app" to "Return to App",
            "txamusic_download_cancelled" to "Download Cancelled",
            "txamusic_download_cancelled_message" to "The download has been cancelled",
            "txamusic_download_complete" to "Download Complete",
            "txamusic_download_complete_message" to "Update downloaded successfully",
            "txamusic_download_completed" to "Download completed",
            "txamusic_download_failed" to "Download Failed",
            "txamusic_download_failed_message" to "Failed to download update",
            "txamusic_download_channel_name" to "TXA Downloads",
            "txamusic_download_channel_description" to "Background download notifications",
            
            // Time Formats
            "txamusic_time_now" to "now",
            "txamusic_time_seconds" to "%ds",
            "txamusic_time_minutes" to "%dm %ds",
            "txamusic_time_hours" to "%dh %dm %ds",
            "txamusic_time_days" to "%dd %dh %dm",
            "txamusic_time_months" to "%dM %dd %dh",
            "txamusic_time_years" to "%dy %dM %dd",
            
            // Other
            "txamusic_powered_by" to "POWER BY TXA!",
            "txamusic_language_change_success" to "Language changed successfully",
            "txamusic_language_change_failed" to "Language change failed: %s",
            
            // Music Library
            "txamusic_music_library_title" to "Music Library",
            "txamusic_all_songs" to "All Songs",
            "txamusic_refresh_library" to "Refresh Library",
            "txamusic_scan_library" to "Scan Library",
            "txamusic_songs_count" to "%s songs",
            "txamusic_library_scanned" to "Library scanned: %s songs found",
            "txamusic_scan_failed" to "Failed to scan library",
            
            // Navigation
            "txamusic_nav_home" to "Home",
            "txamusic_nav_explore" to "Explore",
            "txamusic_nav_library" to "Library",
            "txamusic_nav_settings" to "Settings",
            
            // Music Player - New keys for TXA Music Player functionality
            "txamusic_now_playing" to "Now Playing",
            "txamusic_queue" to "Queue",
            "txamusic_songs" to "Songs",
            "txamusic_albums" to "Albums",
            "txamusic_artists" to "Artists",
            "txamusic_playlists" to "Playlists",
            "txamusic_favorites" to "Favorites",
            "txamusic_recently_played" to "Recently Played",
            "txamusic_most_played" to "Most Played",
            "txamusic_shuffle" to "Shuffle",
            "txamusic_repeat" to "Repeat",
            "txamusic_repeat_all" to "Repeat All",
            "txamusic_repeat_one" to "Repeat One",
            "txamusic_repeat_off" to "Repeat Off",
            "txamusic_play" to "Play",
            "txamusic_pause" to "Pause",
            "txamusic_next" to "Next",
            "txamusic_previous" to "Previous",
            "txamusic_add_to_queue" to "Add to Queue",
            "txamusic_add_to_playlist" to "Add to Playlist",
            "txamusic_remove_from_queue" to "Remove from Queue",
            "txamusic_clear_queue" to "Clear Queue",
            "txamusic_search" to "Search",
            "txamusic_search_hint" to "Search songs, artists, albums...",
            "txamusic_no_results" to "No results found",
            "txamusic_lyrics" to "Lyrics",
            "txamusic_no_lyrics" to "No lyrics available",
            "txamusic_equalizer" to "Equalizer",
            "txamusic_sleep_timer" to "Sleep Timer",
            "txamusic_speed" to "Speed",
            "txamusic_pitch" to "Pitch",
            "txamusic_crossfade" to "Crossfade",
            "txamusic_audio_effects" to "Audio Effects",
            "txamusic_branding_enabled" to "Branding Enabled",
            "txamusic_gapless_playback" to "Gapless Playback",
            "txamusic_silence_skip" to "Skip Silence",
            "txamusic_stats_for_nerds" to "Stats for Nerds",
            "txamusic_tag_editor" to "Tag Editor",
            "txamusic_sponsorblock" to "SponsorBlock for Audio",
            "txamusic_floating_lyrics" to "Floating Lyrics",
            "txamusic_now_bar" to "Now Bar",
            "txamusic_shared_elements" to "Shared Elements",
            "txamusic_glassmorphism" to "Glassmorphism"
        )
    }

    init {
        cacheDir.mkdirs()
        loadSavedLocale()
    }

    /**
     * Hàm dịch chính với fallback 3 lớp
     */
    fun txa(key: String): String {
        // Layer 1: OTA Cache
        currentTranslations[key]?.let { return it }
        
        // Layer 2: Hardcoded Map
        fallbackTranslations[key]?.let { return it }
        
        // Layer 3: Context.getString() (nếu có trong strings.xml)
        return try {
            val resId = context.resources.getIdentifier(key, "string", context.packageName)
            if (resId != 0) context.getString(resId) else key
        } catch (e: Exception) {
            Log.w("TXATranslation", "Translation not found for key: $key", e)
            key
        }
    }

    /**
     * Extension function cho String để dịch
     */
    fun String.txa(): String = txa(this)

    /**
     * Đồng bộ translation từ API nếu có phiên bản mới
     */
    suspend fun syncIfNewer(locale: String = getCurrentLocale()): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Lấy metadata từ API
                val metadataResponse = TXAHttp.get("$TRANSLATION_ENDPOINT/$locale/meta.json")
                val metadata = gson.fromJson(metadataResponse, TranslationMetadata::class.java)
                
                // Kiểm tra cache local
                val localMetaFile = File(cacheDir, "$locale.meta.json")
                if (localMetaFile.exists()) {
                    val localMeta = gson.fromJson(localMetaFile.readText(), TranslationMetadata::class.java)
                    if (localMeta.updatedAt >= metadata.updatedAt) {
                        Log.d("TXATranslation", "Translation cache is up to date")
                        return@withContext true
                    }
                }
                
                // Download translation mới
                val translationResponse = TXAHttp.get("$TRANSLATION_ENDPOINT/$locale")
                val translations = gson.fromJson<Map<String, String>>(
                    translationResponse,
                    object : TypeToken<Map<String, String>>() {}.type
                )
                
                // Lưu cache
                saveTranslation(locale, translations, metadata)
                loadTranslation(locale)
                
                Log.d("TXATranslation", "Translation synced successfully for locale: $locale")
                true
                
            } catch (e: Exception) {
                Log.e("TXATranslation", "Failed to sync translation", e)
                false
            }
        }
    }

    /**
     * Lưu translation vào cache
     */
    private fun saveTranslation(locale: String, translations: Map<String, String>, metadata: TranslationMetadata) {
        try {
            // Lưu translation file
            File(cacheDir, "$locale.json").writeText(gson.toJson(translations))
            
            // Lưu metadata file
            File(cacheDir, "$locale.meta.json").writeText(gson.toJson(metadata))
            
            // Lưu current locale
            File(cacheDir, "current_locale.txt").writeText(locale)
            
        } catch (e: IOException) {
            Log.e("TXATranslation", "Failed to save translation", e)
        }
    }

    /**
     * Load translation từ cache
     */
    private fun loadTranslation(locale: String) {
        try {
            val translationFile = File(cacheDir, "$locale.json")
            if (translationFile.exists()) {
                val translations = gson.fromJson<Map<String, String>>(
                    translationFile.readText(),
                    object : TypeToken<Map<String, String>>() {}.type
                )
                currentTranslations = translations
                currentLocale = locale
                Log.d("TXATranslation", "Loaded translation for locale: $locale")
            }
        } catch (e: Exception) {
            Log.e("TXATranslation", "Failed to load translation", e)
        }
    }

    /**
     * Load saved locale từ cache
     */
    private fun loadSavedLocale() {
        try {
            val localeFile = File(cacheDir, "current_locale.txt")
            if (localeFile.exists()) {
                val savedLocale = localeFile.readText()
                loadTranslation(savedLocale)
            }
        } catch (e: Exception) {
            Log.e("TXATranslation", "Failed to load saved locale", e)
        }
    }

    /**
     * Get available locales from API
     */
    suspend fun getAvailableLocales(): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val response = TXAHttp.get(LOCALES_ENDPOINT)
                val locales = gson.fromJson<List<String>>(response, object : TypeToken<List<String>>() {}.type)
                locales
            } catch (e: Exception) {
                Log.e("TXATranslation", "Failed to get available locales", e)
                listOf("en") // Fallback
            }
        }
    }

    /**
     * Set locale and load corresponding translation
     */
    suspend fun setLocale(locale: String): Boolean {
        return try {
            loadTranslation(locale)
            File(cacheDir, "current_locale.txt").writeText(locale)
            true
        } catch (e: Exception) {
            Log.e("TXATranslation", "Failed to set locale: $locale", e)
            false
        }
    }

    /**
     * Get current locale
     */
    fun getCurrentLocale(): String = currentLocale

    /**
     * Calculate MD5 hash for cache validation
     */
    private fun calculateMD5(text: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val hashBytes = digest.digest(text.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Validate cache integrity
     */
    suspend fun validateCache(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val translationFile = File(cacheDir, "$currentLocale.json")
                if (!translationFile.exists()) return@withContext false
                
                val content = translationFile.readText()
                val localHash = calculateMD5(content)
                
                // Compare with server hash if available
                val metadataFile = File(cacheDir, "$currentLocale.meta.json")
                if (metadataFile.exists()) {
                    val metadata = gson.fromJson(metadataFile.readText(), TranslationMetadata::class.java)
                    // Hash validation logic here
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e("TXATranslation", "Cache validation failed", e)
                false
            }
        }
    }

    /**
     * Clear cache
     */
    fun clearCache() {
        try {
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
            currentTranslations = emptyMap()
            currentLocale = "en"
        } catch (e: Exception) {
            Log.e("TXATranslation", "Failed to clear cache", e)
        }
    }

    /**
     * Get cache size
     */
    fun getCacheSize(): Long {
        return try {
            cacheDir.walkTopDown().sumOf { it.length() }
        } catch (e: Exception) {
            0L
        }
    }
}

/**
 * Data class cho translation metadata
 */
data class TranslationMetadata(
    val locale: String,
    val updatedAt: Long,
    val version: String,
    val checksum: String,
    val totalKeys: Int
)

/**
 * Extension function cho Context để dễ dàng truy cập TXATranslation
 */
fun Context.txa(): TXATranslation = TXATranslation.getInstance(this)

/**
 * Global function để dịch text
 */
fun txa(key: String, context: Context? = null): String {
    return if (context != null) {
        context.txa().txa(key)
    } else {
        // Fallback khi không có context
        TXATranslation.fallbackTranslations[key] ?: key
    }
}

/**
 * Extension function cho String để dịch
 */
fun String.txa(context: Context? = null): String = txa(this, context)
