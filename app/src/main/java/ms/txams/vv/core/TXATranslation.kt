package ms.txams.vv.core

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * TXA Translation Manager
 * 
 * Architecture:
 * ```
 * App Start
 *   ↓
 * TXAApp.onCreate()
 *   ↓ init()
 * TXATranslation
 *   ↓ readLocalLocale() → null?
 *   ↓ YES: loadFallbackStrings()
 *   ↓ NO: applyPayload(cache)
 *   ↓
 * UI Ready (txa() always returns text)
 *   ↓
 * Background: syncIfNewer()
 *   ↓ API check
 *   ↓ ts > localTs?
 *   ↓ YES: download + cache + apply
 *   ↓ NO: keep current
 * ```
 * 
 * Fallback Logic (3 layers):
 * 1. OTA Cache (downloaded from API)
 * 2. Hardcoded Fallback Map (embedded in app)
 * 3. Key itself (last resort)
 * 
 * @author TXA - fb.com/vlog.txa.2311 - txavlog7@gmail.com
 */
object TXATranslation {
    
    // Current translations (from cache or fallback)
    private var currentTranslations: Map<String, String> = emptyMap()
    private var fallbackTranslations: Map<String, String> = emptyMap()
    private var context: Context? = null
    
    // Current locale and cached updated_at
    private var currentLocale = "en"
    private var localUpdatedAt: String? = null
    
    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    
    // Background scope for sync
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // API endpoint
    private const val TRANSLATION_API_BASE = "https://soft.nrotxa.online/txamusic/api/"

    enum class SyncState {
        IDLE,           // Not syncing
        CHECKING,       // Checking for updates
        DOWNLOADING,    // Downloading new translations
        SYNCED,         // Successfully synced
        USING_CACHE,    // Using cached data
        USING_FALLBACK, // Using fallback data
        ERROR           // Sync failed
    }

    /**
     * Initialize translation system
     * This is SYNCHRONOUS - UI is ready immediately after this returns
     * 
     * Flow:
     * 1. Load embedded fallback strings
     * 2. Try to read local cache
     * 3. If cache exists -> apply it
     * 4. If no cache -> use fallback
     * 5. Start background sync
     */
    fun init(ctx: Context, locale: String = "en") {
        context = ctx.applicationContext
        currentLocale = locale
        
        TXALogger.appI("TXATranslation init (locale: $locale)")
        
        // Step 1: Load embedded fallback (always available)
        loadFallbackStrings()
        
        // Step 2: Try to read local cache
        val cacheData = readLocalLocale(locale)
        
        if (cacheData != null) {
            // Step 3a: Cache exists -> apply it
            applyPayload(cacheData.translations)
            localUpdatedAt = cacheData.updatedAt
            _syncState.value = SyncState.USING_CACHE
            TXALogger.appI("Using cached translations (updated_at: ${cacheData.updatedAt})")
        } else {
            // Step 3b: No cache -> use fallback
            currentTranslations = fallbackTranslations
            _syncState.value = SyncState.USING_FALLBACK
            TXALogger.appI("Using fallback translations (no cache)")
        }
        
        // Step 4: Start background sync
        syncScope.launch {
            syncIfNewer(locale)
        }
    }

    /**
     * Get translated string - ALWAYS returns text immediately
     * 
     * Fallback order:
     * 1. currentTranslations (cache or OTA)
     * 2. fallbackTranslations (embedded)
     * 3. key itself
     */
    fun txa(key: String): String {
        return currentTranslations[key] 
            ?: fallbackTranslations[key] 
            ?: key
    }

    /**
     * Read local cached locale data
     * @return CacheData if exists, null otherwise
     */
    private fun readLocalLocale(locale: String): CacheData? {
        return try {
            val langCacheDir = TXALogger.getLangCacheDir()
            val cacheFile = File(langCacheDir, "lang_$locale.json")
            val updatedAtFile = File(langCacheDir, "lang_${locale}_updated_at.txt")
            
            if (!cacheFile.exists()) {
                TXALogger.appD("No cache file for locale: $locale")
                return null
            }
            
            val content = cacheFile.readText()
            val updatedAt = if (updatedAtFile.exists()) updatedAtFile.readText() else null
            val translations = parseJsonToMap(content)
            
            if (translations.isEmpty()) {
                TXALogger.appW("Cache file empty or invalid for locale: $locale")
                return null
            }
            
            CacheData(translations, updatedAt)
        } catch (e: Exception) {
            TXALogger.appE("Failed to read local cache", e)
            null
        }
    }

    /**
     * Apply downloaded/cached translations
     */
    private fun applyPayload(translations: Map<String, String>) {
        currentTranslations = translations
        TXALogger.appD("Applied ${translations.size} translations")
    }

    /**
     * Load embedded fallback strings (hardcoded)
     */
    private fun loadFallbackStrings() {
        val jsonString = """
{
  "txamusic_app_name": "TXA Music",
  "txamusic_app_description": "TXA Music – Dynamic music player with OTA updates and now bar UI",
  "txamusic_splash_checking_permissions": "Checking permissions...",
  "txamusic_splash_requesting_permissions": "Requesting permissions...",
  "txamusic_splash_checking_language": "Checking language updates...",
  "txamusic_splash_downloading_language": "Downloading translations...",
  "txamusic_splash_language_updated": "Language updated successfully",
  "txamusic_splash_language_failed": "Failed to update language",
  "txamusic_splash_initializing": "Initializing application...",
  "txamusic_splash_loading_data": "Loading language data...",
  "txamusic_splash_checking_data": "Checking data...",
  "txamusic_splash_entering_app": "Entering app...",
  "txamusic_splash_connection_error": "Connection error, using fallback data",
  "txamusic_settings_title": "Settings",
  "txamusic_settings_app_info": "Application Information",
  "txamusic_settings_version": "Version",
  "txamusic_settings_app_set_id": "App Set ID",
  "txamusic_settings_language": "Language",
  "txamusic_settings_change_language": "Change Language",
  "txamusic_settings_update": "Update",
  "txamusic_settings_check_update": "Check for Updates",
  "txamusic_settings_about": "About",
  "txamusic_lang_vi": "Tiếng Việt",
  "txamusic_lang_en": "English",
  "txamusic_lang_zh": "中文",
  "txamusic_lang_ja": "日本語",
  "txamusic_lang_ko": "한국어",
  "txamusic_update_checking": "Checking for updates...",
  "txamusic_update_available": "Update available",
  "txamusic_update_not_available": "You are using the latest version",
  "txamusic_update_downloading": "Downloading update...",
  "txamusic_update_download_complete": "Download complete",
  "txamusic_update_installing": "Installing update...",
  "txamusic_update_install_prompt": "Tap to install update",
  "txamusic_update_new_version": "New version",
  "txamusic_update_current_version": "Current version",
  "txamusic_update_changelog": "Changelog",
  "txamusic_update_changelog_empty": "No changelog was provided for this version.",
  "txamusic_update_file_size": "File size",
  "txamusic_update_download_progress": "Progress",
  "txamusic_update_download_speed": "Speed",
  "txamusic_update_download_eta": "Time remaining",
  "txamusic_update_on_default": "Updated on",
  "txamusic_update_time_unavailable": "Release time not provided",
  "txamusic_update_download_now": "Download Now",
  "txamusic_update_install_now": "Install Now",
  "txamusic_update_cancel": "Cancel",
  "txamusic_update_later": "Later",
  "txamusic_update_retry": "Retry",
  "txamusic_error_update_check_failed": "Failed to check for updates",
  "txamusic_error_download_failed": "Download failed",
  "txamusic_error_install_failed": "Installation failed",
  "txamusic_error_network": "Network error",
  "txamusic_error_storage_permission": "Storage permission required",
  "txamusic_error_install_permission": "Install permission required",
  "txamusic_error_no_space": "Insufficient storage space",
  "txamusic_error_invalid_apk": "Invalid APK file",
  "txamusic_error_resolver_failed": "Failed to resolve download URL",
  "txamusic_action_ok": "OK",
  "txamusic_action_cancel": "Cancel",
  "txamusic_action_yes": "Yes",
  "txamusic_action_no": "No",
  "txamusic_action_close": "Close",
  "txamusic_action_retry": "Retry",
  "txamusic_action_continue": "Continue",
  "txamusic_action_back": "Back",
  "txamusic_msg_loading": "Loading...",
  "txamusic_msg_please_wait": "Please wait...",
  "txamusic_msg_success": "Success",
  "txamusic_msg_failed": "Failed",
  "txamusic_msg_error": "Error",
  "txamusic_msg_warning": "Warning",
  "txamusic_msg_info": "Information",
  "txamusic_permission_storage_title": "Storage Permission",
  "txamusic_permission_storage_message": "This app needs storage permission to download updates",
  "txamusic_permission_install_title": "Install Permission",
  "txamusic_permission_install_message": "This app needs permission to install updates",
  "txamusic_permission_denied": "Permission denied",
  "txamusic_permission_required": "Permission required to continue",
  "txamusic_format_bytes": "%s",
  "txamusic_format_speed": "%s/s",
  "txamusic_format_percent": "%s%%",
  "txamusic_format_version": "v%s",
  "txamusic_error_metadata_unavailable": "Update metadata not available",
  "txamusic_error_download_url_missing": "Download URL not available",
  "txamusic_error_invalid_metadata": "Invalid metadata format",
  "txamusic_error_locale_not_found": "Language not found",
  "txamusic_error_invalid_locale_file": "Invalid language file format",
  "txamusic_error_server": "Server error",
  "txamusic_error_cache_invalid": "Cache data is invalid, please refresh",
  "txamusic_file_manager_title": "File Manager",
  "txamusic_file_manager_empty_title": "No downloaded files",
  "txamusic_file_manager_empty_subtitle": "Downloaded APKs will appear here",
  "txamusic_file_manager_refresh": "Refresh",
  "txamusic_file_manager_cleanup": "Clean Up",
  "txamusic_file_manager_install": "Install",
  "txamusic_file_manager_delete": "Delete",
  "txamusic_file_manager_storage_path": "Storage Path",
  "txamusic_file_manager_files_count": "%s files",
  "txamusic_file_manager_total_size": "Total Size: %s",
  "txamusic_file_manager_delete_confirm": "Delete File",
  "txamusic_file_manager_delete_message": "Are you sure you want to delete %s?",
  "txamusic_file_manager_delete_success": "File deleted successfully",
  "txamusic_file_manager_delete_failed": "Failed to delete file",
  "txamusic_file_manager_install_success": "Installation started",
  "txamusic_file_manager_install_failed": "Failed to start installation",
  "txamusic_settings_file_manager": "File Manager",
  "txamusic_settings_open_file_manager": "Open File Manager",
  "txamusic_download_background_title": "TXA Music Update",
  "txamusic_download_background_starting": "Starting download...",
  "txamusic_download_background_progress": "Downloading update...",
  "txamusic_download_cancel": "Cancel",
  "txamusic_download_return_app": "Return to App",
  "txamusic_download_cancelled": "Download Cancelled",
  "txamusic_download_cancelled_message": "The download has been cancelled",
  "txamusic_download_complete": "Download Complete",
  "txamusic_download_complete_message": "Update downloaded successfully",
  "txamusic_download_completed": "Download completed",
  "txamusic_download_failed": "Download Failed",
  "txamusic_download_failed_message": "Failed to download update",
  "txamusic_download_channel_name": "TXA Downloads",
  "txamusic_download_channel_description": "Background download notifications",
  "txamusic_update_install": "Install",
  "txamusic_file_not_found": "Downloaded file not found",
  "txamusic_time_now": "now",
  "txamusic_time_seconds": "%ds",
  "txamusic_time_minutes": "%dm %ds",
  "txamusic_time_hours": "%dh %dm %ds",
  "txamusic_time_days": "%dd %dh %dm",
  "txamusic_time_months": "%dM %dd %dh",
  "txamusic_time_years": "%dy %dM %dd",
  "txamusic_update_on": "UPDATE ON %s",
  "txamusic_powered_by": "POWER BY TXA!",
  "txamusic_update_notification_body": "Version %s is available to download",
  "txamusic_update_notification_channel_name": "TXA Update Alerts",
  "txamusic_update_notification_channel_description": "Receive notifications when a new version is ready",
  "txamusic_language_change_success": "Language changed successfully",
  "txamusic_language_change_failed": "Language change failed: %s",
  "txamusic_music_library_title": "Music Library",
  "txamusic_all_songs": "All Songs",
  "txamusic_refresh_library": "Refresh Library",
  "txamusic_scan_library": "Scan Library",
  "txamusic_songs_count": "%s songs",
  "txamusic_library_scanned": "Library scanned: %s songs found",
  "txamusic_scan_failed": "Failed to scan library",
  "txamusic_permission_audio_rationale": "This app needs access to your audio files to display your music library.",
  "txamusic_action_grant": "Grant",
  "txamusic_settings_music_library": "Music Library",
  "txamusic_settings_open_music_library": "Open Music Library",
  "txamusic_nav_home": "Home",
  "txamusic_nav_explore": "Explore",
  "txamusic_nav_library": "Library",
  "txamusic_nav_settings": "Settings",
  "txamusic_now_playing": "Now Playing",
  "txamusic_queue": "Queue",
  "txamusic_songs": "Songs",
  "txamusic_albums": "Albums",
  "txamusic_artists": "Artists",
  "txamusic_playlists": "Playlists",
  "txamusic_favorites": "Favorites",
  "txamusic_recently_played": "Recently Played",
  "txamusic_most_played": "Most Played",
  "txamusic_shuffle": "Shuffle",
  "txamusic_repeat": "Repeat",
  "txamusic_repeat_all": "Repeat All",
  "txamusic_repeat_one": "Repeat One",
  "txamusic_repeat_off": "Repeat Off",
  "txamusic_play": "Play",
  "txamusic_pause": "Pause",
  "txamusic_next": "Next",
  "txamusic_previous": "Previous",
  "txamusic_add_to_queue": "Add to Queue",
  "txamusic_add_to_playlist": "Add to Playlist",
  "txamusic_remove_from_queue": "Remove from Queue",
  "txamusic_clear_queue": "Clear Queue",
  "txamusic_search": "Search",
  "txamusic_search_hint": "Search songs, artists, albums...",
  "txamusic_no_results": "No results found",
  "txamusic_lyrics": "Lyrics",
  "txamusic_no_lyrics": "No lyrics available",
  "txamusic_equalizer": "Equalizer",
  "txamusic_sleep_timer": "Sleep Timer",
  "txamusic_speed": "Speed",
  "txamusic_pitch": "Pitch",
  "txamusic_crossfade": "Crossfade",
  "txamusic_audio_effects": "Audio Effects",
  "txamusic_branding_enabled": "Branding Enabled",
  "txamusic_gapless_playback": "Gapless Playback",
  "txamusic_silence_skip": "Skip Silence",
  "txamusic_stats_for_nerds": "Stats for Nerds",
  "txamusic_tag_editor": "Tag Editor",
  "txamusic_sponsorblock": "SponsorBlock for Audio",
  "txamusic_floating_lyrics": "Floating Lyrics",
  "txamusic_now_bar": "Now Bar",
  "txamusic_shared_elements": "Shared Elements",
  "txamusic_glassmorphism": "Glassmorphism",
  "txamusic_app_incompatible": "Your Android version is not yet supported. We will support it soon.",
  "txamusic_integrity_check_failed": "App integrity check failed. Please reinstall the app.",
  "txamusic_update_resolving_url": "Resolving download URL...",
  "txamusic_update_connecting": "Connecting to server...",
  "txamusic_update_validating": "Validating APK file...",
  "txamusic_update_ready_install": "Ready to install",
  "txamusic_update_retrying": "Retrying (%d/%d)...",
  "txamusic_update_download_failed_retry": "Download failed. Retrying in %ds...",
  "txamusic_update_download_failed_max_retry": "Download failed after %d attempts",
  "txamusic_update_calculating": "Calculating...",
  "txamusic_update_release_date": "Release date: %s",
  "txamusic_update_mandatory": "This update is required",
  "txamusic_permission_all_files_title": "All Files Access",
  "txamusic_permission_all_files_message": "This app needs access to all files to save logs and backup data to a public folder.",
  "txamusic_permission_battery_title": "Battery Optimization",
  "txamusic_permission_battery_message": "Please allow this app to ignore battery optimization for reliable update checks",
  "txamusic_settings_font": "Font Style",
  "txamusic_settings_change_font": "Change Font Style",
  "txamusic_current_font": "Current Font",
  "txamusic_now_bar_waiting": "Choose a song to play"
}
"""
        try {
            fallbackTranslations = parseJsonToMap(jsonString)
            TXALogger.appD("Loaded ${fallbackTranslations.size} fallback strings")
        } catch (e: Exception) {
            TXALogger.appE("Failed to load fallback strings", e)
        }
    }

    /**
     * Background sync - check if newer version available and download
     * 
     * Flow:
     * 1. Get remote updated_at from API
     * 2. Compare with local updated_at
     * 3. If remote > local: download new translations
     * 4. Apply new translations
     */
    private suspend fun syncIfNewer(locale: String) = withContext(Dispatchers.IO) {
        try {
            _syncState.value = SyncState.CHECKING
            TXALogger.apiD("Starting background sync for locale: $locale")
            
            // Step 1: Get remote updated_at
            val remoteUpdatedAt = getRemoteUpdatedAt(locale)
            
            if (remoteUpdatedAt == null) {
                TXALogger.apiW("Could not get remote updated_at, keeping current")
                _syncState.value = if (localUpdatedAt != null) SyncState.USING_CACHE else SyncState.USING_FALLBACK
                return@withContext
            }
            
            TXALogger.apiD("Remote updated_at: $remoteUpdatedAt, Local: $localUpdatedAt")
            
            // Step 2: Compare timestamps
            if (localUpdatedAt != null && localUpdatedAt == remoteUpdatedAt) {
                TXALogger.apiD("Translations up to date, no sync needed")
                _syncState.value = SyncState.SYNCED
                return@withContext
            }
            
            // Step 3: Download new translations
            _syncState.value = SyncState.DOWNLOADING
            TXALogger.apiI("Downloading new translations...")
            
            val newTranslations = downloadLocale(locale)
            
            if (newTranslations != null) {
                // Step 4: Save to cache
                saveToCache(locale, newTranslations.rawJson, newTranslations.updatedAt)
                
                // Step 5: Apply new translations
                applyPayload(newTranslations.translations)
                localUpdatedAt = newTranslations.updatedAt
                
                _syncState.value = SyncState.SYNCED
                TXALogger.apiI("Translations synced successfully (updated_at: ${newTranslations.updatedAt})")
            } else {
                TXALogger.apiW("Download failed, keeping current translations")
                _syncState.value = SyncState.ERROR
            }
        } catch (e: Exception) {
            TXALogger.apiE("Sync failed", e)
            _syncState.value = SyncState.ERROR
        }
    }

    /**
     * Force sync - manually trigger sync
     */
    suspend fun forceSync(locale: String = currentLocale) {
        syncIfNewer(locale)
    }

    /**
     * Get remote updated_at from API
     */
    private suspend fun getRemoteUpdatedAt(locale: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "${TRANSLATION_API_BASE}locales"
            val request = Request.Builder().url(url).build()
            
            TXAHttp.client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val rawBody = response.body?.string() ?: ""
                    if (rawBody.trim().startsWith("[")) {
                        TXALogger.apiD("Locales API returned array list, skipping timestamp check")
                        return@withContext null
                    }
                    val json = JSONObject(rawBody)
                    if (json.optBoolean("ok")) {
                        val locales = json.getJSONArray("locales")
                        for (i in 0 until locales.length()) {
                            val item = locales.getJSONObject(i)
                            if (item.getString("code") == locale) {
                                return@withContext item.getString("updated_at")
                            }
                        }
                    }
                }
                null
            }
        } catch (e: Exception) {
            TXALogger.apiE("Failed to get remote updated_at", e)
            null
        }
    }

    /**
     * Download locale from API
     */
    private suspend fun downloadLocale(locale: String): DownloadResult? = withContext(Dispatchers.IO) {
        try {
            val url = "${TRANSLATION_API_BASE}locale/$locale"
            TXALogger.apiD("Downloading: $url")
            val request = Request.Builder().url(url).build()
            
            TXAHttp.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    TXALogger.apiE("Download failed: HTTP ${response.code}")
                    return@withContext null
                }
                
                val rawJson = response.body?.string() ?: return@withContext null
                val json = JSONObject(rawJson)
                val updatedAt = json.optString("updated_at", "")
                val translations = parseJsonToMap(rawJson)
                
                DownloadResult(translations, updatedAt, rawJson)
            }
        } catch (e: Exception) {
            TXALogger.apiE("Download failed", e)
            null
        }
    }

    /**
     * Save translations to cache
     */
    private fun saveToCache(locale: String, rawJson: String, updatedAt: String) {
        try {
            val langCacheDir = TXALogger.getLangCacheDir()
            langCacheDir.mkdirs()
            
            val cacheFile = File(langCacheDir, "lang_$locale.json")
            val updatedAtFile = File(langCacheDir, "lang_${locale}_updated_at.txt")
            
            cacheFile.writeText(rawJson)
            updatedAtFile.writeText(updatedAt)
            
            TXALogger.apiD("Saved to cache: ${cacheFile.absolutePath}")
        } catch (e: Exception) {
            TXALogger.apiE("Failed to save cache", e)
        }
    }

    /**
     * Parse JSON string to Map
     */
    private fun parseJsonToMap(json: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val jsonObject = JSONObject(json)
            jsonObject.keys().forEach { key ->
                if (key != "updated_at") { // Skip metadata fields
                    map[key] = jsonObject.getString(key)
                }
            }
        } catch (e: Exception) {
            TXALogger.appE("Failed to parse JSON", e)
        }
        return map
    }

    /**
     * Get current locale
     */
    fun getCurrentLocale(): String = currentLocale

    /**
     * Check if using fallback
     */
    fun isUsingFallback(): Boolean = _syncState.value == SyncState.USING_FALLBACK

    /**
     * Get cache info
     */
    fun getCacheInfo(): CacheInfo {
        val langCacheDir = TXALogger.getLangCacheDir()
        val cacheFile = File(langCacheDir, "lang_$currentLocale.json")
        return CacheInfo(
            locale = currentLocale,
            updatedAt = localUpdatedAt,
            cacheExists = cacheFile.exists(),
            cacheSize = if (cacheFile.exists()) cacheFile.length() else 0,
            translationCount = currentTranslations.size
        )
    }

    /**
     * Get available locales from API
     * @return List of locale codes or null if failed
     */
    suspend fun getAvailableLocales(): List<String>? = withContext(Dispatchers.IO) {
        try {
            val url = "${TRANSLATION_API_BASE}locales"
            TXALogger.apiD("Getting available locales: $url")
            val request = Request.Builder().url(url).build()
            
            TXAHttp.client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.trim().startsWith("[")) {
                        val locales = org.json.JSONArray(body)
                        val result = mutableListOf<String>()
                        for (i in 0 until locales.length()) {
                            result.add(locales.getString(i))
                        }
                        TXALogger.apiD("Available locales (array): $result")
                        return@withContext result
                    } else {
                        val json = JSONObject(body)
                        if (json.optBoolean("ok")) {
                            val locales = json.getJSONArray("locales")
                            val result = mutableListOf<String>()
                            for (i in 0 until locales.length()) {
                                try {
                                    val item = locales.get(i)
                                    if (item is JSONObject) {
                                        result.add(item.getString("code"))
                                    } else if (item is String) {
                                        result.add(item)
                                    }
                                } catch (e: Exception) {}
                            }
                            TXALogger.apiD("Available locales (object): $result")
                            return@withContext result
                        }
                    }
                }
                null
            }
        } catch (e: Exception) {
            TXALogger.apiE("Failed to get available locales", e)
            null
        }
    }

    // Data classes
    data class CacheData(
        val translations: Map<String, String>,
        val updatedAt: String?
    )

    data class DownloadResult(
        val translations: Map<String, String>,
        val updatedAt: String,
        val rawJson: String
    )

    data class CacheInfo(
        val locale: String,
        val updatedAt: String?,
        val cacheExists: Boolean,
        val cacheSize: Long,
        val translationCount: Int
    )
}

// Extension function for easy access
fun String.txa(): String = TXATranslation.txa(this)
