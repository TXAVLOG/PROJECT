package ms.txams.vv.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * TXA Translation Manager
 * 
 * Fallback Logic (3 layers):
 * 1. OTA Cache (downloaded from API)
 * 2. Hardcoded Fallback Map (embedded in app)
 * 3. Key itself (last resort)
 */
object TXATranslation {
    private var otaTranslations: Map<String, String> = emptyMap()
    private var fallbackTranslations: Map<String, String> = emptyMap()
    private var context: Context? = null
    
    private val _currentLocale = MutableStateFlow("en")
    val currentLocale: StateFlow<String> = _currentLocale.asStateFlow()
    
    private val _loadingState = MutableStateFlow(LoadingState.IDLE)
    val loadingState: StateFlow<LoadingState> = _loadingState.asStateFlow()
    
    // API endpoint for translation files (placeholder - replace with actual API)
    private const val TRANSLATION_API_BASE = "https://raw.githubusercontent.com/TXAVLOG/PROJECT/main/translations/"
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    enum class LoadingState {
        IDLE,
        CHECKING_CACHE,
        DOWNLOADING,
        VERIFYING,
        COMPLETE,
        ERROR,
        USING_FALLBACK
    }

    fun init(ctx: Context) {
        context = ctx.applicationContext
        // 1. Load Hardcoded Fallback (Layer 2)
        loadEmbeddedFallback()
    }

    private fun loadEmbeddedFallback() {
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
  "txamusic_integrity_check_failed": "App integrity check failed. Please reinstall the app."
}
"""
        try {
            fallbackTranslations = parseJsonToMap(jsonString)
            // Initialize OTA with fallback if no cache exists
            if (otaTranslations.isEmpty()) {
                otaTranslations = fallbackTranslations
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Get translated string with 3-layer fallback
     * Layer 1: OTA Cache (downloaded translations)
     * Layer 2: Hardcoded Fallback (embedded in app)
     * Layer 3: Key itself (last resort)
     */
    fun txa(key: String): String {
        return otaTranslations[key] 
            ?: fallbackTranslations[key] 
            ?: key
    }

    private fun parseJsonToMap(json: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val jsonObject = JSONObject(json)
            jsonObject.keys().forEach { key ->
                map[key] = jsonObject.getString(key)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    /**
     * Load language with progress reporting
     * 
     * First Time Scenario:
     * - Display "Loading language data..."
     * - Download JSON from API
     * - If success -> Save Cache -> Enter App
     * - If error -> Display "Connection error, using fallback data" -> Use Fallback -> Enter App
     * 
     * Subsequent Scenario:
     * - Display "Checking data..."
     * - Compare MD5 of cache with API
     * - If match -> Display "Entering app" -> Delay 5s (init Hilt/ExoPlayer) -> Enter App
     * - If update available -> Run download progress like first time
     */
    suspend fun loadLanguageWithProgress(
        locale: String, 
        onProgress: (current: Long, total: Long, message: String) -> Unit
    ): LoadResult = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext LoadResult.Error("Context not initialized")
        
        val cacheFile = File(ctx.cacheDir, "lang_$locale.json")
        val cacheHashFile = File(ctx.cacheDir, "lang_${locale}_hash.txt")
        val isFirstTime = !cacheFile.exists()

        try {
            if (isFirstTime) {
                // First Time Scenario
                _loadingState.value = LoadingState.DOWNLOADING
                onProgress(0, 100, txa("txamusic_splash_loading_data"))
                
                // Try to download from API
                val downloadResult = downloadTranslationFile(locale) { current, total ->
                    onProgress(current, total, "${txa("txamusic_splash_downloading_language")} ${TXAFormat.formatPercent(current, total)}")
                }
                
                if (downloadResult != null) {
                    // Save to cache
                    cacheFile.writeText(downloadResult)
                    cacheHashFile.writeText(calculateMD5(downloadResult))
                    
                    // Parse and use downloaded translations
                    otaTranslations = parseJsonToMap(downloadResult)
                    _loadingState.value = LoadingState.COMPLETE
                    onProgress(100, 100, txa("txamusic_splash_language_updated"))
                    LoadResult.Success(isUpdated = true)
                } else {
                    // Download failed, use fallback
                    _loadingState.value = LoadingState.USING_FALLBACK
                    onProgress(100, 100, txa("txamusic_splash_connection_error"))
                    // Save fallback to cache for next time
                    cacheFile.writeText(JSONObject(fallbackTranslations).toString())
                    cacheHashFile.writeText(calculateMD5(JSONObject(fallbackTranslations).toString()))
                    LoadResult.Success(isUpdated = false, usedFallback = true)
                }
            } else {
                // Subsequent Scenario
                _loadingState.value = LoadingState.CHECKING_CACHE
                onProgress(0, 100, txa("txamusic_splash_checking_data"))
                
                // Load cached translations first
                val cachedContent = cacheFile.readText()
                otaTranslations = parseJsonToMap(cachedContent)
                val cachedHash = cacheHashFile.takeIf { it.exists() }?.readText() ?: ""
                
                // Try to get API hash (optional - for update check)
                val apiHash = getRemoteHash(locale)
                
                if (apiHash != null && cachedHash != apiHash) {
                    // Update available - download new version
                    _loadingState.value = LoadingState.DOWNLOADING
                    val downloadResult = downloadTranslationFile(locale) { current, total ->
                        onProgress(current, total, "${txa("txamusic_splash_downloading_language")} ${TXAFormat.formatPercent(current, total)}")
                    }
                    
                    if (downloadResult != null) {
                        cacheFile.writeText(downloadResult)
                        cacheHashFile.writeText(calculateMD5(downloadResult))
                        otaTranslations = parseJsonToMap(downloadResult)
                        _loadingState.value = LoadingState.COMPLETE
                        onProgress(100, 100, txa("txamusic_splash_language_updated"))
                        LoadResult.Success(isUpdated = true)
                    } else {
                        // Update download failed, continue with cache
                        _loadingState.value = LoadingState.COMPLETE
                        onProgress(100, 100, txa("txamusic_splash_entering_app"))
                        LoadResult.Success(isUpdated = false)
                    }
                } else {
                    // No update needed - proceed to app with initialization delay
                    _loadingState.value = LoadingState.VERIFYING
                    onProgress(50, 100, txa("txamusic_splash_entering_app"))
                    
                    // Simulate initialization delay (5 seconds for Hilt/ExoPlayer)
                    val delaySteps = 50
                    val delayPerStep = 100L // 5000ms / 50 = 100ms per step
                    for (i in 1..delaySteps) {
                        kotlinx.coroutines.delay(delayPerStep)
                        onProgress(50 + i, 100, txa("txamusic_splash_initializing"))
                    }
                    
                    _loadingState.value = LoadingState.COMPLETE
                    onProgress(100, 100, txa("txamusic_splash_entering_app"))
                    LoadResult.Success(isUpdated = false)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _loadingState.value = LoadingState.ERROR
            onProgress(0, 100, txa("txamusic_msg_error"))
            LoadResult.Error(e.message ?: "Unknown error")
        }
    }

    private suspend fun downloadTranslationFile(
        locale: String,
        onProgress: (current: Long, total: Long) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        try {
            val url = "${TRANSLATION_API_BASE}translation_keys_$locale.json"
            val request = Request.Builder().url(url).build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                
                val body = response.body ?: return@withContext null
                val contentLength = body.contentLength().takeIf { it > 0 } ?: 10000L
                
                val source = body.source()
                val buffer = StringBuilder()
                var bytesRead = 0L
                val charBuffer = ByteArray(1024)
                
                while (true) {
                    val read = source.read(charBuffer)
                    if (read == -1L) break
                    
                    buffer.append(String(charBuffer, 0, read.toInt()))
                    bytesRead += read
                    onProgress(bytesRead, contentLength)
                }
                
                buffer.toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun getRemoteHash(locale: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "${TRANSLATION_API_BASE}translation_keys_${locale}_hash.txt"
            val request = Request.Builder().url(url).build()
            
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()?.trim()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateMD5(content: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(content.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    sealed class LoadResult {
        data class Success(val isUpdated: Boolean, val usedFallback: Boolean = false) : LoadResult()
        data class Error(val message: String) : LoadResult()
    }
}

// Extension function for easy access
fun String.txa(): String = TXATranslation.txa(this)
