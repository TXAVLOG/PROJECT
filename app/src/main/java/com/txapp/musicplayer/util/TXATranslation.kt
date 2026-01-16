package com.txapp.musicplayer.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Multi-language Translation System
 * - Uses cached JSON files instead of strings.xml
 * - Falls back to key if translation not found
 * - Auto-detects system language on first launch
 */
object TXATranslation {

    private const val PREFS_NAME = "txa_translation_prefs"
    private const val KEY_LOCALE = "current_locale"
    private const val KEY_UPDATED_AT = "updated_at"
    private const val BASE_URL = "https://soft.nrotxa.online/txamusic/api/"

    // State for UI to react
    private val _onLanguageChanged = MutableStateFlow(0)
    val onLanguageChanged = _onLanguageChanged.asStateFlow()

    // Loading state for getting available languages
    private val _isLoadingLanguages = MutableStateFlow(false)
    val isLoadingLanguages = _isLoadingLanguages.asStateFlow()

    // Loading state for downloading language
    private val _isDownloadingLanguage = MutableStateFlow(false)
    val isDownloadingLanguage = _isDownloadingLanguage.asStateFlow()

    private var currentLocale: String = "en"
    private var translations: MutableMap<String, String> = mutableMapOf()
    private var updatedAt: String = ""

    // Available languages cache
    private var availableLanguages: List<LanguageInfo> = emptyList()

    data class LanguageInfo(val code: String, val displayName: String)

    // Fallback dictionary (embedded) - English
    private val fallbackMapEn = mapOf(
        "txamusic_app_name" to "TXA Music",
        "txamusic_error_prefix" to "Error: ",
        "txamusic_error_unknown" to "Unknown Error",
        "txamusic_update_version_label" to "Version: ",
        "txamusic_settings_section_social" to "Follow TXA",
        "txamusic_home" to "Home",
        "txamusic_library" to "Library",
        "txamusic_settings" to "Settings",
        "txamusic_external_audio" to "External Audio",
        "txamusic_external_source" to "External Source",
        "txamusic_external_file_opened" to "You opened this file from %s",
        "txamusic_play_now" to "Play Now",
        "txamusic_settings_remember_pos" to "Remember Playback Position",
        "txamusic_settings_remember_pos_desc" to "Resume songs from where you left off.",
        "txamusic_settings_clear_history" to "Manage Playback History",
        "txamusic_settings_history_count" to "Saved progress for %d songs",
        "txamusic_history_deleted" to "Playback history cleared",
        "txamusic_clear_history_confirm" to "Are you sure you want to clear saved playback positions for all songs?",
        "txamusic_history_dialog_title" to "Playback History",
        "txamusic_resume_playback_title" to "Resume Playback?",
        "txamusic_resume_playback_msg" to "Do you want to resume '%s' from %s?",
        "txamusic_action_resume" to "Resume",
        "txamusic_action_start_over" to "Start Over",
        "txamusic_pause" to "Pause",
        "txamusic_add_to_queue" to "Add to Queue",
        "txamusic_update_available" to "Update Available: %s",
        "txamusic_update_date" to "Released: %s",
        "txamusic_update_whats_new" to "What's New",
        "txamusic_update_resolving" to "Getting download link...",
        "txamusic_update_downloading" to "Downloading update...",
        "txamusic_update_ready" to "Download Ready!",
        "txamusic_update_failed" to "Update Failed",
        "txamusic_update_copy_hint" to "Tap link to copy",
        // Buttons
        "txamusic_btn_ok" to "OK",
        "txamusic_btn_cancel" to "Cancel",
        "txamusic_btn_later" to "Later",
        "txamusic_btn_cancel_download" to "Cancel Download",
        "txamusic_btn_install" to "Install",
        "txamusic_btn_confirm" to "Confirm",
        "txamusic_btn_update" to "Update",
        "txamusic_btn_browser" to "Open Browser",
        "txamusic_btn_grant" to "Grant Permissions",
        "txamusic_browser_not_found" to "No browser found to open link",
        // Permissions
        "txamusic_permissions_title" to "Permissions Required",
        "txamusic_permissions_desc" to "This app needs access to storage to play music and notifications to control playback.",
        "txamusic_permission_notification_title" to "Notifications",
        "txamusic_permission_notification_desc" to "Show playback controls in the notification bar.",
        "txamusic_permission_storage_title" to "Storage Access",
        "txamusic_permission_storage_message" to "Needed to find and play music files on your device.",
        "txamusic_permission_write_settings_title" to "Modify System Settings",
        "txamusic_permission_write_settings_desc" to "Required for volume control and audio features.",
        "txamusic_permission_location_title" to "Location Access",
        "txamusic_permission_location_desc" to "Used to determine your local timezone for date and time synchronization.",
        "txamusic_permission_location_required_title" to "Location Required",
        "txamusic_permission_location_required_desc" to "Location permission is mandatory to synchronize the music library dates and application timezone correctly. Please grant it in settings (choose 'While using the app').",
        "txamusic_btn_open_settings" to "Open Settings",
        "txamusic_permission_status_granted" to "Granted",
        "txamusic_permission_system_alert_title" to "Display over other apps",
        "txamusic_permission_system_alert_desc" to "Required for the custom toast system to display messages on any screen.",
        // Splash
        "txamusic_splash_initializing" to "Initializing...",
        "txamusic_splash_checking_language" to "Checking language...",
        "txamusic_splash_loading_resources" to "Loading resources...",
        "txamusic_splash_checking_update" to "Checking for updates...",
        "txamusic_splash_scanning_library" to "Scanning music library...",
        "txamusic_splash_opening_file" to "Opening file...",
        // Scan
        "txamusic_scan_title" to "Scan Results",
        "txamusic_scan_result_title" to "Scan Report",
        "txamusic_scan_success" to "Success: %d",
        "txamusic_scan_failed" to "Failed: %d",
        "txamusic_scan_confirm" to "Confirm",
        "txamusic_scan_scanning" to "Scanning",
        "txamusic_scan_complete" to "Scan Complete",
        "txamusic_scan_desc" to "Files shorter than %d seconds are skipped.",
        "txamusic_repeat_one" to "Repeat One",
        "txamusic_repeat_all" to "Repeat All",
        "txamusic_repeat_off" to "Repeat Off",
        // Other
        // Language 
        "txamusic_lang_en" to "English",
        "txamusic_lang_vi" to "Tiếng Việt",
        // Settings
        "txamusic_settings_language" to "Language",
        "txamusic_settings_getting_languages" to "Getting languages...",
        "txamusic_settings_downloading_language" to "Downloading language...",
        "txamusic_settings_language_updated" to "Language updated successfully",
        "txamusic_settings_language_failed" to "Failed to update language",
        "txamusic_settings_version" to "Version",
        "txamusic_settings_check_update" to "Check for Updates",
        "txamusic_settings_about" to "About",
        "txamusic_settings_no_update" to "No update available",
        // Settings Categories 1.2
        "txamusic_settings_section_visual" to "Visual & Theme",
        "txamusic_settings_section_visual_desc" to "Customize colors, dark mode, and app accent.",
        "txamusic_settings_section_audio" to "Audio",
        "txamusic_settings_section_audio_desc" to "Equalizer, smooth transitions, and audio focus.",
        "txamusic_settings_section_now_playing" to "Now Playing",
        "txamusic_settings_section_now_playing_desc" to "Change player interface and controls.",
        "txamusic_settings_section_personalize" to "Personalize",
        "txamusic_settings_section_personalize_desc" to "Artist/Album grid styles and functionality tabs.",
        "txamusic_error_report_sent" to "Error report sent to server",
        "txamusic_settings_section_images" to "Images",
        "txamusic_settings_section_images_desc" to "Cover art quality and display options.",
        "txamusic_settings_section_other" to "Backup & Storage",
        "txamusic_settings_section_other_desc" to "Backup your library and manage cache.",
        "txamusic_settings_section_update_info" to "Update & Info",
        "txamusic_settings_section_update_info_desc" to "Current version, check for updates, and developer info.",
        "txamusic_settings_theme_title" to "Appearance",
        "txamusic_settings_theme_desc" to "Switch between light and dark mode.",
        "txamusic_settings_accent_title" to "Accent Color",
        "txamusic_settings_accent_desc" to "Choose your favorite app highlight color.",
        "txamusic_settings_grid_title" to "Grid Columns",
        "txamusic_settings_grid_desc" to "Set number of columns for lists.",
        "txamusic_settings_eq_title" to "Equalizer",
        "txamusic_settings_eq_desc" to "Manage system equalizer",
        "txamusic_settings_eq_not_found" to "No equalizer found on this device",
        "txamusic_settings_eq_no_session" to "No audio session. Play a song first.",
        "txamusic_settings_fade_title" to "Crossfade",
        "txamusic_settings_fade_desc" to "Fade between songs",
        "txamusic_settings_fade_off" to "Off",
        "txamusic_settings_fade_dialog_desc" to "Smoothly fade out the current song and fade in the next.",
        "txamusic_settings_audio_focus_title" to "Audio Focus",
        "txamusic_settings_audio_focus_desc" to "Pause when other apps play audio",
        "txamusic_settings_bluetooth_title" to "Bluetooth Playback",
        "txamusic_settings_bluetooth_desc" to "Auto resume when Bluetooth device connects",
        "txamusic_settings_headset_title" to "Play on Headset Connect",
        "txamusic_settings_headset_desc" to "Auto play when headphones are plugged in",
        "txamusic_settings_audio_fade_title" to "Audio Fade",
        "txamusic_settings_audio_fade_desc" to "Fade audio when song is paused or played",
        "txamusic_settings_audio_fade_dialog_desc" to "Duration to fade in/out when toggling playback.",
        "txamusic_eq_limit_warning" to "Warning: Exceeding ±15dB may distort audio on some devices.",
        // Custom Equalizer
        "txamusic_eq_enable" to "Enable Equalizer",
        "txamusic_eq_on" to "Enabled",
        "txamusic_eq_off" to "Disabled",
        "txamusic_eq_presets" to "Presets",
        "txamusic_eq_custom" to "Custom",
        "txamusic_eq_bands" to "Frequency Bands",
        "txamusic_eq_bass_boost" to "Bass Boost",
        "txamusic_eq_virtualizer" to "3D Surround",
        "txamusic_settings_eq_play_first" to "Play a song first to use the equalizer",
        "txamusic_settings_backup_title" to "Backup Data",
        "txamusic_settings_backup_desc" to "Export favorites and history",
        "txamusic_settings_restore_title" to "Restore Data",
        "txamusic_settings_restore_desc" to "Import from backup file",
        "txamusic_settings_np_style_title" to "Now Playing Style",
        "txamusic_settings_np_style_desc" to "Aurora, Glass, Vinyl, Neon...",
        "txamusic_np_style_aurora" to "Aurora",
        "txamusic_np_style_glass" to "Glass",
        "txamusic_np_style_vinyl" to "Vinyl",
        "txamusic_np_style_neon" to "Neon",

        "txamusic_np_style_spectrum" to "Spectrum",
        "txamusic_settings_playback_speed_title" to "Playback Speed",
        "txamusic_settings_playback_speed_desc" to "Adjust the speed of the music (0.5x to 2.0x).",
        "txamusic_settings_contact_title" to "Contact Support",
        "txamusic_settings_contact_desc" to "Email txavlog7@gmail.com for help",
        "txamusic_contact_email_subject" to "TXA Music - Support Request",
        "txamusic_contact_email_body" to "Hello TXA,\n\nI need help with:\n• App crashes when playing certain songs\n• Playback speed control not working on my device\n• Settings reset after restarting the app\n• Album art not displaying correctly\n• Other: [Please describe your issue - pick one from above and delete the rest, or describe in detail if other]\n\n---\nDevice Info:\n%s",
        "txamusic_settings_image_quality_title" to "Image Quality",
        "txamusic_settings_image_quality_desc" to "Low, Medium, High",
        "txamusic_settings_auto_download_title" to "Auto-download album art",
        "txamusic_settings_auto_download_desc" to "Automatically download missing album art from the server.",
        "txamusic_settings_show_shuffle" to "Show Shuffle button",
        "txamusic_settings_show_shuffle_desc" to "Show the shuffle button in the media notification",
        "txamusic_settings_show_favorite" to "Show Favorite button",
        "txamusic_settings_show_favorite_desc" to "Show the favorite button in the media notification",
        "txamusic_feature_coming_soon" to "Coming soon!",
        "txamusic_gift_open" to "Open Gift",
        "txamusic_gift_title" to "Happy New Year 2026!",
        "txamusic_gift_artist" to "TXA Music Gift 🎁",
        // Download notification
        "txamusic_noti_downloading_title" to "Downloading Update",
        "txamusic_noti_downloading_desc" to "%d%% - %s",
        "txamusic_noti_success_title" to "Download Complete",
        "txamusic_noti_success_desc" to "Tap to install update",
        "txamusic_noti_error_title" to "Download Failed",
        "txamusic_noti_error_desc" to "Error: %s",
        // Device compatibility
        "txamusic_device_not_supported_desc" to "This app requires Android %s or higher. Your device is running Android %s.",
        "txamusic_android9_warning_title" to "Android 9 Stability Notice",
        "txamusic_android9_warning_body" to "You are using Android 9. Due to system memory constraints on this version, the app may occasionally experience lag or stability issues when loading high-quality album art.",
        "txamusic_android9_warning_how_to_fix" to "💡 To improve performance: Clear the app cache or restart the app if it becomes slow.",
        "txamusic_android9_warning_footer" to "If problems persist, consider upgrading your OS or device for the best experience.",
        "txamusic_btn_exit" to "Exit",
        // Root Info
        "txamusic_root_modal_title" to "Root Access Unleashed! 🚀",
        "txamusic_root_modal_body" to "Hello! We detected that your powerful device <b>%s</b> is running with <b>Root Privileges</b> (Android %s). TXA Music can now leverage peak performance and advanced file system access for an even smoother experience.",
        "txamusic_root_modal_footer" to "Root access granted. Ready for high-performance audio.",
        // RAM
        "txamusic_ram_warning_title" to "Insufficient RAM Warning ⚠️",
        "txamusic_ram_warning_body" to "This app requires at least %s RAM to run smoothly. Your device only has %s total RAM. You may experience freezes or crashes.",
        "txamusic_low_mem_title" to "Low Memory Detected",
        "txamusic_low_mem_body" to "Available RAM is critically low (%s). Please free up memory.",
        "txamusic_action_clean_ram" to "Clean RAM",
        "txamusic_ram_status" to "Available: %s / %s",
        "txamusic_ram_cleaned" to "Cleaned! Available: %s (%s)",
        "txamusic_ram_clean_fail" to "Clean failed: %s",
        "txamusic_top_played_empty" to "You haven't listened to any song many times. Go listen!",
        "txamusic_home_greeting_day" to "Good Morning",
        "txamusic_home_greeting_afternoon" to "Good Afternoon",
        "txamusic_home_greeting_evening" to "Good evening",
        "txamusic_home_greeting" to "Welcome,",
        "txamusic_home_recent_title" to "Recently Played",
        "txamusic_home_top_tracks_title" to "Top Played",
        "txamusic_home_favorite_title" to "Favorites",
        "txamusic_home_suggestion_title" to "Suggestions",
        "txamusic_home_recent_added" to "Recently Added",
        "txamusic_action_search" to "Search songs...",
        "txamusic_playlist_create_success" to "Playlist created successfully!",
        "txamusic_action_add_to_favorites" to "Added to favorites",
        "txamusic_action_remove_from_favorites" to "Removed from favorites",
        "txamusic_shuffle_on" to "Shuffle ON 🔀",
        "txamusic_shuffle_off" to "Shuffle OFF",
        "txamusic_unit_day" to "d",
        "txamusic_unit_hour" to "h",
        "txamusic_unit_minute" to "m",
        "txamusic_unit_second" to "s",
        "txamusic_splash_lang_fallback" to "Using offline fallback, continuing in %d s...",
        "txamusic_splash_no_internet" to "No internet connection detected. Continuing offline mode...",
        "txamusic_noti_channel_name" to "Playback Status",
        "txamusic_noti_channel_desc" to "Shows currently playing song and playback controls",
        "txamusic_home_no_songs" to "No songs found",
        "txamusic_player_queue" to "Queue",
        "txamusic_media_songs" to "Songs",
        "txamusic_play_now" to "Play Now",
        "txamusic_social_facebook" to "Facebook",
        "txamusic_social_youtube" to "YouTube",
        "txamusic_social_github" to "GitHub",
        "txamusic_social_telegram" to "Telegram",
        "txamusic_settings_aod_brightness" to "AOD Brightness",
        
        // Network & Image
        "txamusic_network_wifi_no_internet" to "WiFi connected but no internet access!",
        "txamusic_network_cellular_exhausted" to "Data may be exhausted. Restricted Mode enabled.",
        "txamusic_network_restricted_mode" to "Restricted Mode",
        "txamusic_network_restricted_mode_desc" to "Only Home and Songs tabs are available due to network issues.",
        "txamusic_network_restored_title" to "Connection Restored",
        "txamusic_network_restored_desc" to "Restarting app to restore full functionality...",
        "txamusic_settings_image_quality" to "Image Quality",
        "txamusic_settings_image_quality_desc" to "Adjust image resolution based on network.",
        "txamusic_settings_image_quality_high" to "High",
        "txamusic_settings_image_quality_medium" to "Medium",
        "txamusic_settings_image_quality_low" to "Low",
        "txamusic_settings_image_quality_auto" to "Auto (Network)",
        "txamusic_network_check_failed" to "Unable to check for updates. No internet connection.",

        // Support Author / Donate
        "txamusic_settings_support_author" to "Support Author",
        "txamusic_settings_support_author_desc" to "Buy me a coffee to support development",

        // Search, Queue, Lyrics
        "txamusic_search_hint" to "Search songs, albums, artists...",
        "txamusic_playing_queue" to "Playing Queue",
        "txamusic_queue_empty" to "Queue is empty",
        "txamusic_up_next" to "Up Next",
        "txamusic_clear_queue" to "Clear Queue",
        "txamusic_clear_queue_confirm" to "Are you sure you want to clear the current queue?",
        "txamusic_btn_clear" to "Clear",
        "txamusic_no_lyrics" to "No lyrics found",
        "txamusic_no_lyrics_hint" to "Add a .lrc file next to the audio file or search online",

        // Holiday Greetings
        "txamusic_holiday_newyear_title" to "Happy New Year 2026!",
        "txamusic_holiday_newyear_body" to "Wishing you a year fully loaded with happiness.",
        "txamusic_holiday_tet_title" to "Happy Lunar New Year!",
        "txamusic_holiday_tet_body" to "Wishing you prosperity, good health, and good luck!",
        "txamusic_holiday_tatnien_title" to "Happy Year End!",
        "txamusic_holiday_tatnien_body" to "Wrapping up the year with joy and readiness for a fresh start.",
        "txamusic_holiday_tet_27_title" to "Preparing for Tet (27th)",
        "txamusic_holiday_tet_27_body" to "Cleaning and decorating, the festive spirit is in the air!",
        "txamusic_holiday_tet_28_title" to "Preparing for Tet (28th)",
        "txamusic_holiday_tet_28_body" to "Cooking Banh Chung, the tradition continues.",
        "txamusic_holiday_tet_29_title" to "Preparing for Tet (29th)",
        "txamusic_holiday_tet_29_body" to "Final preparations before the big day!",
        "txamusic_holiday_giaothua_title" to "Happy Lunar New Year's Eve!",
        "txamusic_holiday_giaothua_body" to "Giao Thua is here! Wishing you a magical transition to the new year.",
        "txamusic_holiday_mung1_title" to "Happy New Year! (Mung 1)",
        "txamusic_holiday_mung1_body" to "May the first day of the year bring you endless luck and prosperity.",
        "txamusic_holiday_mung1_extra_title" to "A Special Blessing for You",
        "txamusic_holiday_mung1_extra_body" to "Good health, success, and happiness in every step you take this year.",
        "txamusic_holiday_mung2_title" to "Happy Tet! (Mung 2)",
        "txamusic_holiday_mung2_body" to "Wishing you joyful moments with family and relatives today.",
        "txamusic_holiday_mung3_title" to "Happy Tet! (Mung 3)",
        "txamusic_holiday_mung3_body" to "Today is for friends and teachers. Enjoy the festivities!",
        "txamusic_holiday_mung4_title" to "Happy Tet! (Mung 4)",
        "txamusic_holiday_mung4_body" to "Let the positive energy of spring stay with you all year long.",
        "txamusic_holiday_dont_show_today" to "Don't show again today",
        "txamusic_holiday_noti_title" to "New Year Celebration!",
        "txamusic_holiday_noti_body" to "Happy New Year! Check out what's special today.",
        "txamusic_holiday_noti_giaothua" to "🕛 Happy Giao Thua! The new year has officially begun!",
        "txamusic_holiday_channel_name" to "Holiday Greetings",
        "txamusic_action_continue" to "Continue",
        "txamusic_settings_holiday_effect" to "Holiday Effects",

        // Backup & Restore
        "txamusic_backup_dialog_title" to "Create Backup",
        "txamusic_backup_dialog_desc" to "Save your favorites, play history, and settings to a secure encrypted file.",
        "txamusic_backup_name" to "Backup Name",
        "txamusic_backup_select_content" to "Select content to backup:",
        "txamusic_backup_favorites" to "Favorites",
        "txamusic_backup_history" to "Play History",
        "txamusic_backup_settings" to "Settings",
        "txamusic_backup_create" to "Create Backup",
        "txamusic_backup_preparing" to "Preparing backup...",
        "txamusic_backup_collecting" to "Collecting data...",
        "txamusic_backup_encrypting" to "Encrypting...",
        "txamusic_backup_success" to "Backup created successfully!",
        "txamusic_backup_in_progress" to "Creating Backup...",
        "txamusic_backup_last" to "Last backup: %s",
        "txamusic_backup_existing" to "Existing Backups",
        "txamusic_backup_deleted" to "Backup deleted",
        "txamusic_backup_result_success" to "Backup saved to:\n%s",
        "txamusic_backup_result_failed" to "Backup failed: %s",
        "txamusic_backup_success_title" to "Success!",
        "txamusic_backup_failed_title" to "Failed",
        "txamusic_restore_dialog_title" to "Restore Backup",
        "txamusic_restore_dialog_desc" to "Choose a backup file to restore your data.",
        "txamusic_restore_from_file" to "Select from file",
        "txamusic_restore_from_file_desc" to "Browse for .txa backup file",
        "txamusic_restore_existing" to "Or restore from existing",
        "txamusic_restore_reading" to "Reading backup file...",
        "txamusic_restore_decrypting" to "Decrypting...",
        "txamusic_restore_processing" to "Processing...",
        "txamusic_restore_favorites" to "Restoring favorites...",
        "txamusic_restore_history" to "Restoring play history...",
        "txamusic_restore_settings" to "Restoring settings...",
        "txamusic_restore_playlists" to "Restoring playlists...",
        "txamusic_restore_success" to "Restore completed!",
        "txamusic_restore_in_progress" to "Restoring Backup...",
        "txamusic_restore_result_success" to "Restored successfully!\n• Favorites: %d\n• History: %d\n• Playlists: %d\n• Settings: %s",
        "txamusic_restore_result_failed" to "Restore failed: %s",
        "txamusic_restore_skipped" to "⚠️ Skipped: %d (files not found)",
        "txamusic_backup_playlists" to "Playlists",

        // Delete confirmation
        "txamusic_delete_confirm_title" to "Delete Backup?",
        "txamusic_delete_confirm_desc" to "Are you sure you want to delete \"%s\"? This action cannot be undone.",
        "txamusic_action_delete" to "Delete",
        "txamusic_action_cancel" to "Cancel",
        "txamusic_action_confirm" to "Confirm",

        // Post Update Dialog
        "txamusic_post_update_title" to "Update Successful!",
        "txamusic_post_update_intro" to "Thank you for installing %s v%s",
        "txamusic_btn_close" to "Close",

        // Restore warning
        "txamusic_restore_warning_title" to "Overwrite Data?",
        "txamusic_restore_warning_desc" to "Your current favorites and play history will be overwritten by the backup data. This action cannot be undone.",

        // Player Effects
        "txamusic_settings_player_effects" to "Player Effects",
        "txamusic_settings_player_effect_type" to "Effect Type",

        // Full Player Style
        "txamusic_np_style_full" to "Full",

        "txamusic_settings_other_placeholder" to "Additional settings like cache management will be added here in future updates.",
        "txamusic_settings_section_backup" to "Backup & Restore",


        // Full Player
        "txamusic_next_song" to "Next song",

        // Sleep Timer
        "txamusic_sleep_timer" to "Sleep Timer",
        "txamusic_sleep_timer_desc" to "Music will stop after the set time",
        "txamusic_sleep_timer_active" to "Timer active",
        "txamusic_sleep_timer_set" to "Sleep timer set for %d minutes",
        "txamusic_sleep_timer_canceled" to "Sleep timer canceled",
        "txamusic_sleep_timer_start" to "Start Timer",
        "txamusic_unit_minutes" to "minutes",

        // Lyrics
        "txamusic_lyrics" to "Lyrics",
        "txamusic_lyrics_not_found" to "No lyrics found for this song",
        "txamusic_lyrics_search" to "Search lyrics",
        "txamusic_lyrics_search_online" to "Search online",
        "txamusic_edit_lyrics" to "Edit Lyrics",
        "txamusic_edit_normal_lyrics" to "Edit Normal Lyrics",
        "txamusic_edit_synced_lyrics" to "Edit Synced Lyrics (LRC)",
        "txamusic_paste_lyrics_here" to "Paste lyrics here...",
        "txamusic_lyrics_saved" to "Lyrics saved successfully",
        "txamusic_lyrics_save_failed" to "Failed to save lyrics",
        "txamusic_synced_lyrics" to "Synced",
        "txamusic_normal_lyrics" to "Normal",
        "txamusic_add_lyrics" to "Add Lyrics",
        "txamusic_paste_timeframe_lyrics_here" to "Paste LRC lyrics with timestamps here...",
        "txamusic_paste_synced_lyrics_hint" to "Format: %s Lyric text",
        "txamusic_lyrics_format_short" to "[mm:ss.xx]",
        "txamusic_lyrics_format_short_extended" to "[mm:ss.xx - mm:ss.xx]",
        "txamusic_lyrics_format_long" to "[hh:mm:ss.xx]",
        "txamusic_lyrics_format_long_extended" to "[hh:mm:ss.xx - hh:mm:ss.xx]",
        "txamusic_hide_lyrics" to "Hide Lyrics",
        "txamusic_paste_normal_lyrics_hint" to "Plain text lyrics without timestamps",
        "txamusic_lyrics_searching" to "Searching for lyrics...",
        "txamusic_lyrics_search_success" to "Lyrics found!",
        "txamusic_lyrics_search_failed" to "No lyrics found for this song",
        "txamusic_lyrics_unsaved_title" to "Unsaved Changes",
        "txamusic_lyrics_unsaved_desc" to "You have unsaved changes. Do you want to save them before leaving?",
        "txamusic_btn_discard" to "Discard",

        "txamusic_permission_grant" to "Grant Permission",
        "txamusic_settings_remember_last_tab" to "Remember Last Tab",
        "txamusic_settings_remember_last_tab_desc" to "Open the app on the last visited tab",
        "txamusic_settings_album_grid_size" to "Album Grid Columns",
        "txamusic_settings_artist_grid_size" to "Artist Grid Columns",
        "txamusic_settings_refresh_playlists" to "Refresh Playlists",
        "txamusic_settings_refresh_playlists_desc" to "Re-scan and update all playlists",
        "txamusic_backup_rename" to "Rename Backup",
        "txamusic_backup_rename_hint" to "Enter new backup name",
        "txamusic_share_backup_text" to "Here is my backup file for TXA Music Player.",
        "txamusic_share_backup_title" to "Share Backup",
        "txamusic_backup_rename_success" to "Backup renamed successfully",

        // Device Info
        "txamusic_info_device" to "Device Info",
        "txamusic_info_model" to "Model: %s",
        "txamusic_info_android" to "Android: %s",
        "txamusic_info_emulator" to "Emulator: %s",
        "txamusic_tag_emulator" to "Emulator",
        "txamusic_info_root_status" to "Root Access: %s",
        "txamusic_root_granted" to "Granted",
        "txamusic_root_denied" to "Denied",
        "txamusic_yes" to "Yes",
        "txamusic_no" to "No",

        // Exact Alarm Permission (Alarms & Reminders)
        "txamusic_permission_alarm_title" to "Alarms & Reminders",
        "txamusic_permission_alarm_desc" to "Allow app to schedule holiday notifications and reminders at exact times.",
        "txamusic_permission_optional" to "(Optional)",

        "txamusic_albums" to "Albums",
        "txamusic_artists" to "Artists",
        "txamusic_more_from_artist" to "More from %s",
        "txamusic_new_music_mix" to "New Music Mix",
        "txamusic_clear_history" to "Clear History",
        "txamusic_history_cleared" to "History cleared",
        "txamusic_history_undo" to "Undo",
        "txamusic_songs" to "Songs",
        "txamusic_playlists" to "Playlists",
        "txamusic_shuffle_all" to "Shuffle All",
        "txamusic_top_tracks" to "Top Tracks",
        "txamusic_folders" to "Folders",
        "txamusic_genres" to "Genres",
        "txamusic_error_contact_btn" to "Contact Support",
        "txamusic_contact_option_title" to "Contact Options",
        "txamusic_contact_facebook_msg" to "Hello TXA, I encountered an error:\n\n",
        "txamusic_contact_copied_fb" to "Error info copied! Opening Facebook...",
        "txamusic_btn_create_playlist" to "Create Playlist",
        "txamusic_play_options_title" to "Playback Options",
        "txamusic_play_options_desc" to "Do you want to play this song now or add it to the queue?",
        "txamusic_added_to_queue" to "Added to queue",
        "txamusic_favorites" to "Favorites",
        "txamusic_favorites_empty" to "Your favorite list is empty",
        "txamusic_action_play_all" to "Play All",
        "txamusic_action_shuffle" to "Shuffle",
        "txamusic_loading" to "Loading...",
        "txamusic_media_playlists" to "Playlists",
        "txamusic_unknown_title" to "Unknown Title",
        "txamusic_unknown_artist" to "Unknown Artist",
        "txamusic_removed_from_playlist" to "Removed from playlist",
        "txamusic_remove_from_playlist" to "Remove from playlist",
        "txamusic_playlist_deleted" to "Playlist deleted",
        "txamusic_delete_playlist" to "Delete Playlist",
        "txamusic_delete_playlist_confirm" to "Are you sure you want to delete this playlist?",
        "txamusic_playlist_empty" to "This playlist is empty",
        "txamusic_btn_delete" to "Delete",
        "txamusic_add_to_playlist" to "Add to Playlist",
        "txamusic_playback_speed" to "Playback Speed",
        "txamusic_error_file_not_found" to "File not found: %s. It may have been removed or moved. Removing from library...",
        "txamusic_refreshing_library" to "Refreshing music library...",
        "txamusic_refresh_done" to "Library refresh complete.",
        "txamusic_action_added_to_playlist" to "Added to playlist",
        "txamusic_playlist_added_status" to "Added",
        "txamusic_home_history" to "History",
        "txamusic_home_last_added" to "Recently Added",
        "txamusic_home_top_played" to "Top Played",
        "txamusic_error_friendly_location_api" to "Location Service Error",
        "txamusic_set_as_ringtone" to "Set as Ringtone",
        "txamusic_root_optimizing" to "Optimizing system performance (Root)...",

        // Power & Root Settings
        "txamusic_settings_section_power" to "Power & Performance",
        "txamusic_settings_section_power_desc" to "Leverage Root & System permissions for extreme performance.",
        "txamusic_settings_power_tip" to "Using Turbo Mode and AOD optimization can significantly improve playback stability and battery life.",
        "txamusic_settings_root_power" to "Turbo Power Mode (Root)",
        "txamusic_settings_root_power_desc" to "Ensures the app has maximum system priority. Prevents audio from stuttering or being killed by battery saving.",
        "txamusic_settings_write_permission" to "Write Settings Permission",
        "txamusic_settings_write_permission_desc" to "Allow changing brightness and setting ringtones.",


        // Tag Editor
        "txamusic_tag_editor" to "Edit Song Info",
        "txamusic_edit_tag" to "Edit Info",
        "txamusic_btn_save" to "Save",
        "txamusic_saving" to "Saving...",
        "txamusic_file_path" to "File",
        "txamusic_duration" to "Duration",
        "txamusic_title" to "Title",
        "txamusic_artist" to "Artist",
        "txamusic_album" to "Album",
        "txamusic_album_artist" to "Album Artist",
        "txamusic_composer" to "Composer",
        "txamusic_year" to "Year",
        "txamusic_track_number" to "Track #",
        "txamusic_tag_editor_note" to "Changes will be written directly to the audio file and synchronized with your music library.",
        "txamusic_tag_saved" to "Tags saved successfully",
        "txamusic_tag_save_failed" to "Failed to save tags",
        "txamusic_ringtone_set_success" to "Set as ringtone successfully",
        "txamusic_ringtone_set_failed" to "Failed to set ringtone",
        "txamusic_ringtone_permission_title" to "Allow Write Settings",
        "txamusic_ringtone_permission_desc" to "To set a ringtone, the app needs permission to change system settings.",
        "txamusic_search_placeholder" to "Search songs, artists...",
        "txamusic_no_results" to "No results found for \"%s\"",
        "txamusic_add_to_playlist_desc" to "Open from Main App to access playlists",
        "txamusic_playing" to "Playing",
        "txamusic_more" to "More",
        "txamusic_btn_back" to "Back",
        "txamusic_drive_mode" to "Drive Mode",
        "txamusic_error_song_not_loaded" to "Song info not loaded. Please wait and try again.",
        "txamusic_error_song_not_found" to "Song not found in library",
        
        // Manual Add
        "txamusic_select_music" to "Select Music",
        "txamusic_back" to "Back",
        "txamusic_add_selected" to "Add (%d)",
        "txamusic_empty_folder" to "Empty Folder",
        "txamusic_manual_add_result" to "Added %d songs. Skipped %d existing songs.",
        "txamusic_storage_root" to "Root Storage",
        "txamusic_internal_storage" to "Internal Storage",

        
        // Delete from App
        "txamusic_delete_from_app" to "Remove from Library",
        "txamusic_delete_confirm_title" to "Remove Song?",
        "txamusic_delete_confirm_message" to "Remove \"%s\" from app? The file won't be deleted.",
        "txamusic_song_deleted" to "Song removed from library",
        
        // UI Hints
        "txamusic_tap_to_close" to "Tap anywhere to close",
        
        // Multi-select
        "txamusic_multi_select" to "Select Multiple",
        "txamusic_multi_select_count" to "%d selected",
        "txamusic_batch_actions" to "Batch Actions",
        "txamusic_action_add_all_to_playlist" to "Add All to Playlist",
        "txamusic_action_delete_all" to "Delete All",
        "txamusic_action_play_selected" to "Play Selected",
        "txamusic_confirm_delete_multiple_title" to "Delete %d Songs?",
        "txamusic_confirm_delete_multiple_desc" to "Are you sure you want to remove these %d songs from the library?",
        
        // Lyrics Overlay
        "txamusic_show_lyrics_overlay" to "Floating Lyrics",
        "txamusic_show_lyrics_overlay_desc" to "Show lyrics overlay on top of other apps",
        "txamusic_overlay_permission_title" to "Overlay Permission Required",
        "txamusic_overlay_permission_desc" to "To display floating lyrics on top of other apps, please grant the \"Display over other apps\" permission.",
        "txamusic_lyrics_style" to "Lyrics Style",
        "txamusic_lyrics_style_desc" to "Customize how lyrics are displayed",
        "txamusic_lyrics_font_size" to "Font Size",
        "txamusic_lyrics_text_align" to "Text Alignment",
        "txamusic_lyrics_align_left" to "Left",
        "txamusic_lyrics_align_center" to "Center",
        "txamusic_lyrics_align_right" to "Right",
        
        // Refresh Interval
        "txamusic_refresh_interval" to "Auto Refresh Interval",
        "txamusic_refresh_interval_desc" to "Automatically refresh playlists after this duration",
        "txamusic_refresh_never" to "Never",
        "txamusic_refresh_hourly" to "Every Hour",
        "txamusic_refresh_daily" to "Daily",
        "txamusic_refresh_weekly" to "Weekly",

        // App Shortcuts
        "txamusic_shortcuts_shuffle_all" to "Shuffle All",
        "txamusic_shortcuts_top_tracks" to "Top Tracks",
        "txamusic_shortcuts_last_added" to "Last Added",
        "txamusic_shortcuts_check_update" to "Check Update",
        
        // Shortcut Check Update Service
        "txamusic_shortcut_checking_update" to "Checking for updates...",
        "txamusic_shortcut_update_found" to "New version %s available!",
        "txamusic_shortcut_update_found_title" to "Update Available",
        "txamusic_shortcut_no_update" to "You're up to date!",
        "txamusic_shortcut_update_error" to "Failed to check for updates",
        "txamusic_shortcut_open_app" to "Open App",
        "txamusic_shortcut_update_channel_name" to "Update Check",
        "txamusic_shortcut_update_channel_desc" to "Notifications for update checks from app shortcut",
        "txamusic_tag_emulator" to "Tag Emulator",
        
        // Widget Settings
        "txamusic_widget_settings" to "Widget Settings",
        "txamusic_widget_settings_desc" to "Customize the appearance and controls of your home screen widget.",
        "txamusic_widget_preview_title" to "Sample Song",
        "txamusic_widget_preview_artist" to "Sample Artist",
        "txamusic_widget_display" to "Display Options",
        "txamusic_widget_show_album_art" to "Show Album Art",
        "txamusic_widget_show_title" to "Show Song Title",
        "txamusic_widget_show_artist" to "Show Artist Name",
        "txamusic_widget_show_progress" to "Show Progress Bar",
        "txamusic_widget_controls" to "Control Options",
        "txamusic_widget_show_shuffle" to "Show Shuffle Button",
        "txamusic_widget_show_repeat" to "Show Repeat Button",
        "txamusic_widget_info" to "Changes will be applied immediately to all widget instances on your home screen.",
        "txamusic_audio_route_speaker" to "Audio switched to speaker",
        
        // Visualizer
        "txamusic_settings_visualizer_title" to "Music Visualizer",
        "txamusic_settings_visualizer_desc" to "Show animated audio visualization in Now Playing",
        "txamusic_settings_visualizer_style" to "Visualizer Style",
        "txamusic_visualizer_bars" to "Bars",
        "txamusic_visualizer_wave" to "Wave",
        "txamusic_visualizer_circle" to "Circle",
        "txamusic_visualizer_spectrum" to "Spectrum",
        "txamusic_visualizer_glow" to "Glow Bars",
        "txamusic_visualizer_fluid" to "Fluid (Namida)",
        "txamusic_permission_audio_denied" to "Audio recording permission is required to show the visualizer.",
        "txamusic_blacklist_folders" to "Blacklist Folders",
        "txamusic_blacklist_folder_desc" to "Hide folders from music library",
        "txamusic_folder_blacklisted" to "Folder blacklisted",
        "txamusic_folder_removed_from_blacklist" to "Removed from blacklist",
        "txamusic_import_playlist" to "Import Playlist",
        "txamusic_import_playlist_desc" to "Select an .m3u or .m3u8 file to import songs.",
        "txamusic_select_file" to "Select File",
        "txamusic_select_playlist_file" to "Select M3U Playlist",
        "txamusic_import_success" to "Imported %d songs from playlist",
        "txamusic_import_failed" to "Failed to import playlist",
        "txamusic_rename_playlist" to "Rename Playlist",
        "txamusic_save_playlist" to "Save Playlist",
        "txamusic_save_playlist_desc" to "Export %d songs to M3U file",
        "txamusic_file_name" to "File Name",
        "txamusic_save_location_hint" to "Saved to: Music/Playlists/",
        "txamusic_playlist_saved" to "Playlist saved successfully",
        "txamusic_playlist_save_failed" to "Failed to save playlist",
        "txamusic_playlist_renamed" to "Playlist renamed",
        "txamusic_btn_rename_playlist" to "Rename Playlist",
        "txamusic_export_playlist" to "Export M3U"
    )

    // Fallback dictionary (embedded) - Vietnamese
    private val fallbackMapVi = mapOf(
        "txamusic_app_name" to "TXA Music",
        "txamusic_error_prefix" to "Lỗi: ",
        "txamusic_error_unknown" to "Lỗi không xác định",
        "txamusic_update_version_label" to "Phiên bản: ",
        "txamusic_settings_section_social" to "Theo dõi TXA",
        "txamusic_home" to "Trang chủ",
        "txamusic_library" to "Thư viện",
        "txamusic_settings" to "Cài đặt",
        "txamusic_external_audio" to "Âm thanh bên ngoài",
        "txamusic_external_source" to "Nguồn bên ngoài",
        "txamusic_external_file_opened" to "Bạn đã mở tập tin này từ %s",
        "txamusic_play_now" to "Phát ngay",
        "txamusic_settings_remember_pos" to "Ghi nhớ vị trí phát",
        "txamusic_settings_remember_pos_desc" to "Tiếp tục phát nhạc tại vị trí đã dừng.",
        "txamusic_settings_clear_history" to "Quản lý lịch sử phát",
        "txamusic_settings_history_count" to "Đã lưu vị trí %d bài hát",
        "txamusic_history_deleted" to "Đã xóa lịch sử phát",
        "txamusic_clear_history_confirm" to "Bạn có chắc chắn muốn xóa vị trí phát đã lưu của tất cả bài hát không?",
        "txamusic_history_dialog_title" to "Lịch sử phát nhạc",
        "txamusic_resume_playback_title" to "Tiếp tục phát?",
        "txamusic_resume_playback_msg" to "Bạn có muốn tiếp tục phát '%s' từ %s?",
        "txamusic_action_resume" to "Tiếp tục",
        "txamusic_action_start_over" to "Phát lại từ đầu",
        "txamusic_pause" to "Tạm dừng",
        "txamusic_add_to_queue" to "Thêm vào hàng đợi",
        "txamusic_update_available" to "Có bản cập nhật: %s",
        "txamusic_update_date" to "Phát hành: %s",
        "txamusic_update_whats_new" to "Có gì mới",
        "txamusic_update_resolving" to "Đang lấy link tải...",
        "txamusic_update_downloading" to "Đang tải cập nhật...",
        "txamusic_update_ready" to "Tải xong!",
        "txamusic_update_failed" to "Cập nhật thất bại",
        "txamusic_update_copy_hint" to "Chạm để sao chép link",
        // Buttons
        "txamusic_btn_ok" to "Đồng ý",
        "txamusic_btn_cancel" to "Hủy",
        "txamusic_btn_later" to "Để sau",
        "txamusic_btn_cancel_download" to "Hủy tải",
        "txamusic_btn_install" to "Cài đặt",
        "txamusic_btn_confirm" to "Xác nhận",
        "txamusic_btn_update" to "Cập nhật",
        "txamusic_btn_browser" to "Mở trình duyệt",
        "txamusic_btn_grant" to "Cấp quyền",
        "txamusic_browser_not_found" to "Không tìm thấy trình duyệt để mở link",
        "txamusic_eq_limit_warning" to "Mức âm lượng trên 15dB có thể gây méo tiếng!",
        // Permissions
        "txamusic_permissions_title" to "Cần cấp quyền",
        "txamusic_permissions_desc" to "Ứng dụng cần quyền truy cập bộ nhớ để phát nhạc và quyền thông báo để hiển thị trình phát nhạc.",
        "txamusic_permission_notification_title" to "Thông báo",
        "txamusic_permission_notification_desc" to "Bắt buộc để hiển thị trình điều khiển trên thanh thông báo.",
        "txamusic_permission_storage_title" to "Quyền truy cập bộ nhớ",
        "txamusic_permission_storage_message" to "Bắt buộc để tìm và phát các tệp nhạc trên thiết bị.",
        "txamusic_permission_write_settings_title" to "Thay đổi cài đặt hệ thống",
        "txamusic_permission_write_settings_desc" to "Bắt buộc để điều chỉnh âm lượng và cài đặt nhạc chuông.",
        "txamusic_permission_location_title" to "Quyền truy cập vị trí",
        "txamusic_permission_location_desc" to "Được sử dụng để xác định múi giờ địa phương của bạn để đồng bộ hóa ngày giờ.",
        "txamusic_permission_location_required_title" to "Yêu cầu quyền vị trí",
        "txamusic_permission_location_required_desc" to "Quyền vị trí là bắt buộc để đồng bộ hóa ngày tháng của thư viện nhạc và múi giờ ứng dụng một cách chính xác. Vui lòng cấp quyền trong cài đặt (chọn 'Khi dùng ứng dụng').",
        "txamusic_btn_open_settings" to "Mở cài đặt",
        "txamusic_permission_status_granted" to "Đã cấp",
        "txamusic_permission_system_alert_title" to "Hiển thị trên các ứng dụng khác",
        "txamusic_permission_system_alert_desc" to "Cần thiết để hệ thống thông báo tùy chỉnh hiển thị tin nhắn trên mọi màn hình.",
        // Splash
        "txamusic_splash_initializing" to "Đang khởi tạo...",
        "txamusic_splash_checking_language" to "Đang kiểm tra ngôn ngữ...",
        "txamusic_splash_loading_resources" to "Đang tải tài nguyên...",
        "txamusic_splash_checking_update" to "Đang kiểm tra cập nhật...",
        "txamusic_splash_scanning_library" to "Đang quét thư viện nhạc...",
        "txamusic_splash_opening_file" to "Đang mở tập tin...",
        // Scan
        "txamusic_scan_title" to "Kết quả quét",
        "txamusic_scan_result_title" to "Báo cáo quét nhạc",
        "txamusic_scan_success" to "Thành công: %d",
        "txamusic_scan_failed" to "Thất bại: %d",
        "txamusic_scan_confirm" to "Xác nhận",
        "txamusic_scan_scanning" to "Đang quét",
        "txamusic_scan_complete" to "Quét hoàn tất",
        "txamusic_scan_desc" to "Các file ngắn hơn %d giây đã bị bỏ qua.",
        "txamusic_repeat_one" to "Lặp lại 1 bài",
        "txamusic_repeat_all" to "Lặp lại tất cả",
        "txamusic_repeat_off" to "Tắt lặp lại",
        // Other
        // Language 
        "txamusic_lang_en" to "Tiếng Anh",
        "txamusic_lang_vi" to "Tiếng Việt",
        // Settings
        "txamusic_settings_language" to "Ngôn ngữ",
        "txamusic_settings_getting_languages" to "Đang tải danh sách ngôn ngữ...",
        "txamusic_settings_downloading_language" to "Đang tải ngôn ngữ...",
        "txamusic_settings_language_updated" to "Đã cập nhật ngôn ngữ thành công",
        "txamusic_settings_language_failed" to "Cập nhật ngôn ngữ thất bại",
        "txamusic_settings_version" to "Phiên bản",
        "txamusic_settings_check_update" to "Kiểm tra cập nhật",
        "txamusic_settings_about" to "Về ứng dụng",
        "txamusic_settings_no_update" to "Không có bản cập nhật mới",
        // Cài đặt 1.2
        "txamusic_settings_section_visual" to "Giao diện & Chủ đề",
        "txamusic_settings_section_visual_desc" to "Tùy chỉnh màu sắc, chế độ tối và phong cách ứng dụng.",
        "txamusic_settings_section_audio" to "Âm thanh",
        "txamusic_settings_section_audio_desc" to "Bộ chỉnh âm, chuyển bài mượt mà và tiêu điểm âm thanh.",
        "txamusic_settings_section_now_playing" to "Màn hình đang phát",
        "txamusic_settings_section_now_playing_desc" to "Thay đổi giao diện trình phát nhạc và các nút điều khiển.",
        "txamusic_settings_section_personalize" to "Cá nhân hóa",
        "txamusic_settings_section_personalize_desc" to "Sắp xếp lưới nghệ sĩ, album và các tab chức năng.",
        "txamusic_settings_section_images" to "Hình ảnh",
        "txamusic_settings_section_images_desc" to "Chất lượng tải ảnh bìa và các tùy chọn hiển thị.",
        "txamusic_settings_section_other" to "Sao lưu & Bộ nhớ",
        "txamusic_settings_section_other_desc" to "Sao lưu thư viện và quản lý bộ nhớ đệm.",
        "txamusic_settings_section_update_info" to "Cập nhật & Thông tin",
        "txamusic_settings_section_update_info_desc" to "Phiên bản hiện tại, kiểm tra cập nhật và thông tin nhà phát triển.",
        "txamusic_settings_theme_title" to "Giao diện",
        "txamusic_settings_theme_desc" to "Chuyển đổi giữa chế độ sáng và tối.",
        "txamusic_settings_accent_title" to "Màu chủ đạo",
        "txamusic_settings_accent_desc" to "Chọn màu sắc nổi bật cho ứng dụng.",
        "txamusic_settings_eq_title" to "Trình cân bằng EQ",
        "txamusic_settings_eq_desc" to "Quản lý âm sắc hệ thống",
        "txamusic_settings_eq_not_found" to "Không tìm thấy trình cân bằng trên thiết bị này",
        "txamusic_error_report_sent" to "Báo cáo lỗi đã được gửi về máy chủ",
        "txamusic_settings_eq_no_session" to "Không có phiên âm thanh. Hãy phát một bài hát trước.",
        "txamusic_settings_fade_title" to "Chuyển mượt",
        "txamusic_settings_fade_desc" to "Làm mờ dần giữa các bài hát",
        "txamusic_settings_fade_off" to "Tắt",
        "txamusic_settings_fade_dialog_desc" to "Làm mờ dần bài hiện tại và nhẹ nhàng chuyển sang bài tiếp theo.",
        "txamusic_settings_audio_focus_title" to "Tiêu điểm âm thanh",
        "txamusic_settings_audio_focus_desc" to "Tạm dừng khi ứng dụng khác phát âm thanh",
        "txamusic_settings_bluetooth_title" to "Phát qua Bluetooth",
        "txamusic_settings_bluetooth_desc" to "Tự động phát khi kết nối thiết bị Bluetooth",
        "txamusic_settings_headset_title" to "Phát khi cắm tai nghe",
        "txamusic_settings_headset_desc" to "Tự động phát khi cắm tai nghe vào",
        "txamusic_settings_audio_fade_title" to "Âm thanh nhỏ dần",
        "txamusic_settings_audio_fade_desc" to "Làm mờ âm thanh khi dừng hoặc phát nhạc",
        "txamusic_settings_audio_fade_dialog_desc" to "Thời gian nhỏ dần/lớn dần khi tạm dừng hoặc tiếp tục phát.",
        "txamusic_eq_limit_warning" to "Cảnh báo: Vượt quá ±15dB có thể làm biến dạng âm thanh trên một số thiết bị.",
        // Custom Equalizer
        "txamusic_eq_enable" to "Bật trình cân bằng",
        "txamusic_eq_on" to "Đã bật",
        "txamusic_eq_off" to "Đã tắt",
        "txamusic_eq_presets" to "Chế độ âm thanh",
        "txamusic_eq_custom" to "Tùy chỉnh",
        "txamusic_eq_bands" to "Dải tần số",
        "txamusic_eq_bass_boost" to "Tăng Bass",
        "txamusic_eq_virtualizer" to "Âm thanh vòm 3D",
        "txamusic_settings_eq_play_first" to "Phát một bài hát trước để sử dụng trình cân bằng",
        "txamusic_settings_grid_title" to "Kiểu lưới",
        "txamusic_settings_grid_desc" to "Số lượng mục trên mỗi hàng",
        "txamusic_settings_backup_title" to "Sao lưu dữ liệu",
        "txamusic_settings_backup_desc" to "Xuất yêu thích và lịch sử",
        "txamusic_settings_restore_title" to "Khôi phục dữ liệu",
        "txamusic_settings_restore_desc" to "Nhập từ file sao lưu",
        "txamusic_settings_np_style_title" to "Giao diện phát nhạc",
        "txamusic_settings_np_style_desc" to "Aurora, Glass, Vinyl, Neon...",
        "txamusic_np_style_aurora" to "Cực quang",
        "txamusic_np_style_glass" to "Kính mờ",
        "txamusic_np_style_vinyl" to "Đĩa than",
        "txamusic_np_style_neon" to "Neon",
        "txamusic_np_style_spectrum" to "Dải quang phổ",

        "txamusic_gift_open" to "Mở Quà Tặng",
        "txamusic_gift_title" to "Chúc Mừng Năm Mới 2026!",
        "txamusic_gift_artist" to "Quà Tặng Âm Nhạc TXA 🎁",

        "txamusic_drive_mode_sleep_timer_msg" to "Nhạc sẽ tắt sau",
        "txamusic_settings_contact_title" to "Liên hệ hỗ trợ",
        "txamusic_settings_contact_desc" to "Gửi email cho txavlog7@gmail.com",
        "txamusic_contact_email_subject" to "TXA Music - Yêu cầu hỗ trợ",
        "txamusic_contact_email_body" to "Chào TXA,\n\nTôi cần giúp đỡ về:\n• Ứng dụng bị crash khi phát một số bài hát\n• Điều khiển tốc độ phát không hoạt động trên thiết bị của tôi\n• Cài đặt bị reset lại sau khi khởi động ứng dụng\n• Ảnh bìa album không hiển thị đúng\n• Khác: [Vui lòng mô tả vấn đề của bạn - chọn 1 cái ở trên rồi xóa các lí do còn lại đi còn nếu khác vui lòng mô tả kĩ]\n\n---\nThông tin thiết bị:\n%s",
        "txamusic_settings_image_quality_title" to "Chất lượng ảnh",
        "txamusic_settings_image_quality_desc" to "Thấp, Trung bình, Cao",
        "txamusic_settings_auto_download_title" to "Tự động tải ảnh bìa",
        "txamusic_settings_auto_download_desc" to "Tự động tải ảnh bìa còn thiếu từ máy chủ.",
        "txamusic_settings_show_shuffle" to "Hiển thị nút Trộn bài",
        "txamusic_settings_show_shuffle_desc" to "Hiển thị nút trộn bài trong thông báo đang phát",
        "txamusic_settings_show_favorite" to "Hiển thị nút Yêu thích",
        "txamusic_settings_show_favorite_desc" to "Hiển thị nút yêu thích trong thông báo đang phát",
        "txamusic_feature_coming_soon" to "Sắp ra mắt!",
        // Download notification
        "txamusic_noti_downloading_title" to "Đang tải cập nhật",
        "txamusic_noti_downloading_desc" to "%d%% - %s",
        "txamusic_noti_success_title" to "Tải xong",
        "txamusic_noti_success_desc" to "Chạm để cài đặt cập nhật",
        "txamusic_noti_error_title" to "Tải thất bại",
        "txamusic_noti_error_desc" to "Lỗi: %s",
        // Device compatibility
        "txamusic_device_not_supported_desc" to "Ứng dụng yêu cầu Android %s mà cái máy rác của bạn mới chạy Android %s. Vứt máy đi mua cái mới đi cho rảnh nợ!",
        "txamusic_tag_emulator" to "Giả lập",
        "txamusic_android9_warning_title" to "Thông báo Độ ổn định Android 9",
        "txamusic_android9_warning_body" to "Bạn đang sử dụng Android 9. Do giới hạn bộ nhớ của hệ thống trên phiên bản này, ứng dụng đôi khi có thể bị giật lag hoặc kém ổn định khi tải ảnh bìa chất lượng cao.",
        "txamusic_android9_warning_how_to_fix" to "💡 Cách khắc phục: Hãy thường xuyên xóa bộ nhớ đệm hoặc khởi động lại ứng dụng nếu thấy chậm.",
        "txamusic_android9_warning_footer" to "Nếu vẫn gặp sự cố, bạn nên cập nhật hệ điều hành hoặc nâng cấp máy mới để có trải nghiệm tốt nhất.",
        "txamusic_btn_exit" to "Thoát",
        // Root Info
        "txamusic_root_modal_title" to "Quyền Root Đã Sẵn Sàng! 🚀",
        "txamusic_root_modal_body" to "Xin chào! Chúng tôi nhận thấy thiết bị mạnh mẽ của bạn <b>%s</b> đang chạy với <b>Quyền Root</b> (Android %s). TXA Music giờ đây có thể tận dụng tối đa hiệu năng và quyền truy cập tệp chuyên sâu để mang lại trải nghiệm mượt mà nhất.",
        "txamusic_root_modal_footer" to "Đã cấp quyền Root. Sẵn sàng cho âm thanh hiệu suất cao.",
        // RAM
        "txamusic_ram_warning_title" to "Cảnh báo thiếu RAM ⚠️",
        "txamusic_ram_warning_body" to "Ứng dụng này cần tối thiểu %s RAM để hoạt động ổn định. Máy của bạn chỉ có tổng %s RAM. Có thể xảy ra hiện tượng đơ hoặc thoát ứng dụng.",
        "txamusic_low_mem_title" to "Bộ nhớ khả dụng thấp",
        "txamusic_low_mem_body" to "RAM khả dụng hiện còn rất ít (%s). Vui lòng giải phóng bộ nhớ.",
        "txamusic_action_clean_ram" to "Dọn dẹp RAM",
        "txamusic_ram_status" to "Khả dụng: %s / %s",
        "txamusic_ram_cleaned" to "Đã dọn dẹp! Khả dụng: %s (%s)",
        "txamusic_ram_clean_fail" to "Dọn dẹp thất bại: %s",
        "txamusic_top_played_empty" to "Bạn chưa nghe bài nào nhiều lần cả. Đi nghe nhạc đi!",
        "txamusic_home_greeting_day" to "Chào buổi sáng",
        "txamusic_home_greeting_afternoon" to "Chào buổi chiều",
        "txamusic_home_greeting_evening" to "Chào buổi tối",
        "txamusic_home_greeting" to "Chào mừng,",
        "txamusic_home_recent_added" to "Linh lực mới",
        "txamusic_home_favorite_title" to "Yêu thích",
        "txamusic_home_suggestion_title" to "Gợi ý",
        "txamusic_noti_channel_name" to "Trạng thái phát nhạc",
        "txamusic_noti_channel_desc" to "Hiển thị bài hát đang phát và thuộc tính điều khiển",
        "txamusic_playlist_name" to "Tên danh sách phát",
        "txamusic_playlist_create_success" to "Đã tạo danh sách phát thành công!",
        "txamusic_action_add_to_favorites" to "Đã thêm vào yêu thích",
        "txamusic_action_remove_from_favorites" to "Đã xóa khỏi yêu thích",
        "txamusic_unit_day" to "n",
        "txamusic_unit_hour" to "g",
        "txamusic_unit_minute" to "p",
        "txamusic_unit_second" to "s",
        "txamusic_splash_lang_fallback" to "Đang dùng bản dịch ngoại tuyến, tiếp tục sau %d s...",
        "txamusic_home_no_songs" to "Không tìm thấy bài hát",
        "txamusic_player_queue" to "Hàng đợi",
        "txamusic_media_songs" to "Bài hát",
        
        // Network & Image
        "txamusic_network_wifi_no_internet" to "Đã kết nối WiFi nhưng không có mạng!",
        "txamusic_network_cellular_exhausted" to "Dữ liệu có thể đã hết. Đã bật Chế độ Hạn chế.",
        "txamusic_network_restricted_mode" to "Chế độ Hạn chế",
        "txamusic_network_restricted_mode_desc" to "Chỉ các tab Trang chủ và Bài hát khả dụng do vấn đề mạng.",
        "txamusic_network_restored_title" to "Đã khôi phục kết nối",
        "txamusic_network_restored_desc" to "Đang khởi động lại ứng dụng để khôi phục chức năng...",
        "txamusic_settings_image_quality" to "Chất lượng hình ảnh",
        "txamusic_settings_image_quality_desc" to "Điều chỉnh độ phân giải hình ảnh dựa trên mạng.",
        "txamusic_settings_image_quality_high" to "Cao",
        "txamusic_settings_image_quality_medium" to "Trung bình",
        "txamusic_settings_image_quality_low" to "Thấp",
        "txamusic_settings_image_quality_auto" to "Tự động (Theo mạng)",
        "txamusic_network_check_failed" to "Không thể kiểm tra cập nhật. Không có kết nối internet.",
        "txamusic_settings_aod_brightness" to "Độ sáng AOD",
        "txamusic_shuffle_all" to "Xáo trộn tất cả",
        "txamusic_top_tracks" to "Nghe nhiều nhất",
        "txamusic_social_facebook" to "Facebook",
        "txamusic_social_youtube" to "YouTube",
        "txamusic_social_github" to "GitHub",
        "txamusic_social_telegram" to "Telegram",

        // Support Author / Donate
        "txamusic_settings_support_author" to "Hỗ trợ tác giả",
        "txamusic_settings_support_author_desc" to "Tiếp thêm chút linh lực để ứng dụng ngày càng hoàn thiện hơn",

        "txamusic_shuffle_on" to "Bật xáo trộn 🔀",
        "txamusic_shuffle_off" to "Tắt xáo trộn",

        // Playback Speed & Sleep Timer
        "txamusic_playback_speed" to "Tốc độ phát",
        "txamusic_speed_slower" to "Chậm hơn",
        "txamusic_speed_faster" to "Nhanh hơn",
        "txamusic_speed_normal" to "Bình thường",
        "txamusic_btn_reset" to "Đặt lại",
        "txamusic_btn_apply" to "Áp dụng",

        // Holiday Greetings
        "txamusic_holiday_newyear_title" to "Chúc Mừng Năm Mới 2026!",
        "txamusic_holiday_newyear_body" to "Chúc bạn một năm mới tràn đầy hạnh phúc và thành công.",
        "txamusic_holiday_tet_title" to "Chúc Mừng Năm Mới!",
        "txamusic_holiday_tet_body" to "An Khang Thịnh Vượng - Vạn Sự Như Ý!",
        "txamusic_holiday_tatnien_title" to "Chúc Mừng Tất Niên!",
        "txamusic_holiday_tatnien_body" to "Khép lại năm cũ, đón chào năm mới với nhiều niềm vui.",
        "txamusic_holiday_tet_27_title" to "Sắm Tết (27 tháng Chạp)",
        "txamusic_holiday_tet_27_body" to "Không khí Tết đã tràn ngập phố phường, cùng dọn dẹp đón xuân thôi!",
        "txamusic_holiday_tet_28_title" to "Sắm Tết (28 tháng Chạp)",
        "txamusic_holiday_tet_28_body" to "Nồi bánh chưng đã lên bếp, mùi hương Tết thật nồng nàn.",
        "txamusic_holiday_tet_29_title" to "Sắm Tết (29 tháng Chạp)",
        "txamusic_holiday_tet_29_body" to "Những khâu chuẩn bị cuối cùng cho ngày khởi đầu mới.",
        "txamusic_holiday_giaothua_title" to "Chúc Mừng Giao Thừa!",
        "txamusic_holiday_giaothua_body" to "Khoảnh khắc thiêng liêng đã đến. Chúc bạn một năm mới rực rỡ!",
        "txamusic_holiday_mung1_title" to "Mừng Mùng 1 Tết!",
        "txamusic_holiday_mung1_body" to "Khai xuân rạng rỡ, vạn sự như ý, tỷ sự như mơ!",
        "txamusic_holiday_mung1_extra_title" to "Lời Chúc Đặc Biệt",
        "txamusic_holiday_mung1_extra_body" to "Chúc bạn một năm mới sức khỏe dồi dào, thành công rực rỡ và luôn hạnh phúc.",
        "txamusic_holiday_mung2_title" to "Mừng Mùng 2 Tết!",
        "txamusic_holiday_mung2_body" to "Tết của ông bà nội ngoại, chúc bạn có những phút giây ấm áp bên người thân.",
        "txamusic_holiday_mung3_title" to "Mừng Mùng 3 Tết!",
        "txamusic_holiday_mung3_body" to "Mùng 3 Tết thầy, chúc bạn gặp nhiều may mắn và suôn sẻ trong công việc.",
        "txamusic_holiday_mung4_title" to "Mừng Mùng 4 Tết!",
        "txamusic_holiday_mung4_body" to "Dư vị xuân vẫn còn đó, hãy giữ vững năng lượng tích cực này suốt cả năm nhé!",
        "txamusic_holiday_dont_show_today" to "Không hiển thị lại trong hôm nay",
        "txamusic_holiday_noti_title" to "Chúc Mừng Năm Mới!",
        "txamusic_holiday_noti_body" to "Chúc bạn một năm mới an khang thịnh vượng! Xem ngay quà tặng âm nhạc hôm nay.",
        "txamusic_holiday_noti_giaothua" to "🕛 Chúc mừng Giao Thừa! Năm mới đã chính thức bắt đầu!",
        "txamusic_holiday_channel_name" to "Lời chúc lễ hội",
        "txamusic_action_continue" to "Tiếp tục",
        "txamusic_settings_holiday_effect" to "Hiệu ứng lễ hội",

        // Backup & Restore
        "txamusic_backup_dialog_title" to "Tạo bản sao lưu",
        "txamusic_backup_dialog_desc" to "Lưu yêu thích, lịch sử nghe nhạc và cài đặt vào file được mã hóa an toàn.",
        "txamusic_backup_name" to "Tên bản sao lưu",
        "txamusic_backup_select_content" to "Chọn nội dung cần sao lưu:",
        "txamusic_backup_favorites" to "Yêu thích",
        "txamusic_backup_history" to "Lịch sử nghe",
        "txamusic_backup_settings" to "Cài đặt",
        "txamusic_backup_create" to "Tạo bản sao lưu",
        "txamusic_backup_preparing" to "Đang chuẩn bị sao lưu...",
        "txamusic_backup_collecting" to "Đang thu thập dữ liệu...",
        "txamusic_backup_encrypting" to "Đang mã hóa...",
        "txamusic_backup_success" to "Tạo bản sao lưu thành công!",
        "txamusic_backup_in_progress" to "Đang sao lưu...",
        "txamusic_backup_last" to "Sao lưu gần nhất: %s",
        "txamusic_backup_existing" to "Các bản sao lưu",
        "txamusic_backup_deleted" to "Đã xóa bản sao lưu",
        "txamusic_backup_result_success" to "Đã lưu tại:\n%s",
        "txamusic_backup_result_failed" to "Sao lưu thất bại: %s",
        "txamusic_backup_success_title" to "Thành công!",
        "txamusic_backup_failed_title" to "Thất bại",
        "txamusic_restore_dialog_title" to "Khôi phục dữ liệu",
        "txamusic_restore_dialog_desc" to "Chọn file sao lưu để khôi phục dữ liệu của bạn.",
        "txamusic_restore_from_file" to "Chọn từ file",
        "txamusic_restore_from_file_desc" to "Duyệt tìm file .txa sao lưu",
        "txamusic_restore_existing" to "Hoặc khôi phục từ bản có sẵn",
        "txamusic_restore_reading" to "Đang đọc file sao lưu...",
        "txamusic_restore_decrypting" to "Đang giải mã...",
        "txamusic_restore_processing" to "Đang xử lý...",
        "txamusic_restore_favorites" to "Đang khôi phục yêu thích...",
        "txamusic_restore_history" to "Đang khôi phục lịch sử...",
        "txamusic_restore_settings" to "Đang khôi phục cài đặt...",
        "txamusic_restore_playlists" to "Đang khôi phục danh sách phát...",
        "txamusic_restore_success" to "Khôi phục hoàn tất!",
        "txamusic_restore_in_progress" to "Đang khôi phục...",
        "txamusic_restore_result_success" to "Khôi phục thành công!\n• Yêu thích: %d\n• Lịch sử: %d\n• Danh sách phát: %d\n• Cài đặt: %s",
        "txamusic_restore_result_failed" to "Khôi phục thất bại: %s",
        "txamusic_restore_skipped" to "⚠️ Bỏ qua: %d (file không còn tồn tại)",
        "txamusic_backup_playlists" to "Danh sách phát",

        // Delete confirmation
        "txamusic_delete_confirm_title" to "Xóa bản sao lưu?",
        "txamusic_delete_confirm_desc" to "Bạn có chắc muốn xóa \"%s\"? Hành động này không thể hoàn tác.",
        "txamusic_action_delete" to "Xóa",
        "txamusic_action_cancel" to "Hủy",
        "txamusic_action_confirm" to "Xác nhận",

        // Restore warning
        "txamusic_restore_warning_title" to "Ghi đè dữ liệu?",
        "txamusic_restore_warning_desc" to "Danh sách yêu thích và lịch sử phát nhạc hiện tại sẽ bị ghi đè bởi dữ liệu sao lưu. Hành động này không thể hoàn tác.",

        // Player Effects
        "txamusic_settings_player_effects" to "Hiệu ứng trình phát",
        "txamusic_settings_player_effect_type" to "Loại hiệu ứng",

        // Full Player Style
        "txamusic_np_style_full" to "Đầy đủ",

        "txamusic_settings_other_placeholder" to "Các cài đặt bổ sung như quản lý bộ nhớ đệm sẽ được thêm vào đây trong các bản cập nhật tới.",
        "txamusic_search_placeholder" to "Tìm kiếm bài hát, nghệ sĩ...",
        "txamusic_no_results" to "Không tìm thấy kết quả cho \"%s\"",
        "txamusic_add_to_playlist_desc" to "Mở từ ứng dụng chính để truy cập danh sách phát",
        "txamusic_playing" to "Đang phát",
        "txamusic_more" to "Thêm",
        "txamusic_btn_back" to "Quay lại",
        "txamusic_drive_mode" to "Chế độ Lái xe",
        "txamusic_settings_section_backup" to "Sao lưu & Khôi phục",


        // Full Player
        "txamusic_next_song" to "Bài tiếp theo",

        // Sleep Timer
        "txamusic_sleep_timer" to "Hẹn giờ ngủ",
        "txamusic_sleep_timer_desc" to "Nhạc sẽ dừng sau thời gian đã đặt",
        "txamusic_sleep_timer_active" to "Đang hẹn giờ",
        "txamusic_sleep_timer_set" to "Đã đặt hẹn giờ ngủ %d phút",
        "txamusic_sleep_timer_canceled" to "Đã hủy hẹn giờ ngủ",
        "txamusic_sleep_timer_start" to "Bắt đầu",
        "txamusic_unit_minutes" to "phút",
        "txamusic_settings_playback_speed_title" to "Tốc độ phát",
        "txamusic_settings_playback_speed_desc" to "Điều chỉnh tốc độ phát nhạc",

        // Lyrics
        "txamusic_lyrics" to "Lời bài hát",
        "txamusic_lyrics_not_found" to "Không tìm thấy lời bài hát",
        "txamusic_lyrics_search" to "Tìm lời bài hát",
        "txamusic_lyrics_search_online" to "Tìm trên mạng",
        "txamusic_edit_lyrics" to "Chỉnh sửa lời bài hát",
        "txamusic_edit_normal_lyrics" to "Sửa lời bài hát (Văn bản)",
        "txamusic_edit_synced_lyrics" to "Sửa lời bài hát (Đồng bộ LRC)",
        "txamusic_paste_lyrics_here" to "Dán lời bài hát vào đây...",
        "txamusic_lyrics_saved" to "Đã lưu lời bài hát thành công",
        "txamusic_lyrics_save_failed" to "Không thể lưu lời bài hát",
        "txamusic_synced_lyrics" to "Đồng bộ",
        "txamusic_normal_lyrics" to "Thường",
        "txamusic_add_lyrics" to "Thêm lời bài hát",
        "txamusic_paste_timeframe_lyrics_here" to "Dán lời LRC có nhãn thời gian vào đây...",
        "txamusic_paste_synced_lyrics_hint" to "Định dạng: %s Lời bài hát",
        "txamusic_lyrics_format_short" to "[mm:ss.xx]",
        "txamusic_lyrics_format_short_extended" to "[mm:ss.xx - mm:ss.xx]",
        "txamusic_lyrics_format_long" to "[hh:mm:ss.xx]",
        "txamusic_lyrics_format_long_extended" to "[hh:mm:ss.xx - hh:mm:ss.xx]",
        "txamusic_hide_lyrics" to "Ẩn lời bài hát",
        "txamusic_paste_normal_lyrics_hint" to "Lời bài hát thuần văn bản",
        "txamusic_lyrics_unsaved_title" to "Chưa lưu thay đổi",
        "txamusic_lyrics_unsaved_desc" to "Bạn có thay đổi chưa được lưu. Bạn có muốn lưu trước khi thoát không?",
        "txamusic_btn_discard" to "Bỏ qua",

        "txamusic_permission_grant" to "Cấp quyền",
        "txamusic_settings_remember_last_tab" to "Nhớ tab cuối cùng",
        "txamusic_settings_remember_last_tab_desc" to "Mở ứng dụng tại tab bạn đã truy cập cuối cùng",
        "txamusic_settings_album_grid_size" to "Số cột lưới Album",
        "txamusic_settings_artist_grid_size" to "Số cột lưới Nghệ sĩ",
        "txamusic_settings_refresh_playlists" to "Làm mới danh sách phát",
        "txamusic_settings_refresh_playlists_desc" to "Quét lại và cập nhật tất cả danh sách phát",
        "txamusic_backup_rename" to "Đổi tên bản sao lưu",
        "txamusic_backup_rename_hint" to "Nhập tên mới cho bản sao lưu",
        "txamusic_backup_rename_success" to "Đã đổi tên bản sao lưu thành công",

        // Device Info
        "txamusic_info_device" to "Thông tin thiết bị",
        "txamusic_info_model" to "Model: %s",
        "txamusic_info_android" to "Android: %s",
        "txamusic_info_emulator" to "Giả lập: %s",
        "txamusic_info_root_status" to "Quyền Root: %s",
        "txamusic_root_granted" to "Đã cấp",
        "txamusic_root_denied" to "Từ chối",
        "txamusic_yes" to "Có",
        "txamusic_no" to "Không",

        "txamusic_set_as_ringtone" to "Đặt làm nhạc chuông",
        "txamusic_root_optimizing" to "Đang tối ưu hiệu suất hệ thống (Root)...",

        // Exact Alarm Permission (Alarms & Reminders)
        "txamusic_permission_alarm_title" to "Chuông báo & Nhắc nhở",
        "txamusic_permission_alarm_desc" to "Cho phép ứng dụng đặt lịch thông báo lễ hội và nhắc nhở vào thời điểm chính xác.",
        "txamusic_permission_optional" to "(Tùy chọn)",
        "txamusic_albums" to "Album",
        "txamusic_artists" to "Nghệ sĩ",
        "txamusic_more_from_artist" to "Thêm từ %s",
        "txamusic_new_music_mix" to "Mix Nhạc Mới",
        "txamusic_clear_history" to "Xóa lịch sử",
        "txamusic_history_cleared" to "Đã xóa lịch sử",
        "txamusic_history_undo" to "Hoàn tác",
        "txamusic_songs" to "Bài hát",
        "txamusic_media_songs" to "Bài hát",
        "txamusic_play_now" to "Phát ngay",
        "txamusic_shuffle_on" to "Bật trộn bài 🔀",
        "txamusic_shuffle_off" to "Tắt trộn bài",
        "txamusic_playlists" to "Danh sách phát",
        "txamusic_folders" to "Thư mục",
        "txamusic_genres" to "Thể loại",
        "txamusic_error_contact_btn" to "Liên hệ hỗ trợ",
        "txamusic_contact_option_title" to "Tùy chọn liên hệ",
        "txamusic_contact_facebook_msg" to "Chào TXA, mình gặp lỗi này:\n\n",
        "txamusic_contact_copied_fb" to "Đã copy lỗi! Đang mở Facebook...",
        "txamusic_btn_create_playlist" to "Tạo danh sách phát",
        "txamusic_play_options_title" to "Tuỳ chọn phát",
        "txamusic_play_options_desc" to "Bạn muốn phát bài hát này ngay lập tức hay thêm vào hàng đợi?",
        "txamusic_added_to_queue" to "Đã thêm vào hàng đợi",
        "txamusic_favorites" to "Yêu thích",
        "txamusic_favorites_empty" to "Danh sách yêu thích trống",
        "txamusic_action_play_all" to "Phát tất cả",
        "txamusic_action_shuffle" to "Trộn bài",
        "txamusic_loading" to "Đang tải...",
        "txamusic_media_playlists" to "Danh sách phát",
        "txamusic_unknown_title" to "Không rõ tên bài hát",
        "txamusic_unknown_artist" to "Không rõ nghệ sĩ",
        "txamusic_removed_from_playlist" to "Đã xóa khỏi danh sách phát",
        "txamusic_remove_from_playlist" to "Xóa khỏi danh sách phát",
        "txamusic_playlist_deleted" to "Đã xóa danh sách phát",
        "txamusic_delete_playlist" to "Xóa danh sách phát",
        "txamusic_delete_playlist_confirm" to "Bạn có chắc muốn xóa danh sách phát này?",
        "txamusic_playlist_empty" to "Danh sách phát trống",
        "txamusic_btn_delete" to "Xóa",
        "txamusic_add_to_playlist" to "Thêm vào danh sách phát",
        "txamusic_playlist_empty_state" to "Chưa có danh sách nào", // Corrected key name for clarity
        "txamusic_error_file_not_found" to "Không tìm thấy file: %s. Có thể file đã bị xóa hoặc di chuyển. Đang xóa khỏi thư viện...",
        "txamusic_refreshing_library" to "Đang quét lại thư viện nhạc...",
        "txamusic_refresh_done" to "Đã quét xong thư viện.",
        "txamusic_action_added_to_playlist" to "Đã thêm vào danh sách",
        "txamusic_playlist_added_status" to "Đã thêm",
        "txamusic_home_history" to "Lịch sử",
        "txamusic_home_last_added" to "Mới thêm gần đây",
        "txamusic_home_top_played" to "Nghe nhiều nhất",
        "txamusic_error_friendly_location_api" to "Lỗi dịch vụ vị trí",
        "txamusic_splash_no_internet" to "Không có kết nối mạng! Tiếp tục với chế độ ngoại tuyến.",

        // Settings & AOD
        "txamusic_status_active" to "Đang phát",
        "txamusic_status_paused" to "Đã tạm dừng",
        "txamusic_status_stopped" to "Đã dừng",

        // Power & Root Settings
        "txamusic_settings_section_power" to "Sức mạnh & Hiệu năng",
        "txamusic_settings_section_power_desc" to "Sử dụng quyền Root & Hệ thống để tối ưu hiệu suất cực hạn.",
        "txamusic_settings_power_tip" to "Sử dụng Turbo Mode và tối ưu AOD giúp tăng tính ổn định khi phát nhạc và tiết kiệm pin đáng kể.",
        "txamusic_settings_root_power" to "Chế độ Turbo (Root)",
        "txamusic_settings_root_power_desc" to "Đảm bảo ứng dụng có mức ưu tiên hệ thống cao nhất. Tránh bị giật lag hoặc bị kill bởi chế độ tiết kiệm pin.",
        "txamusic_settings_write_permission" to "Quyền thay đổi hệ thống",
        "txamusic_settings_write_permission_desc" to "Cho phép thay đổi độ sáng và cài đặt nhạc chuông.",

        // Tag Editor
        "txamusic_tag_editor" to "Chỉnh sửa thông tin",
        "txamusic_edit_tag" to "Chỉnh sửa",
        "txamusic_btn_save" to "Lưu",
        "txamusic_saving" to "Đang lưu...",
        "txamusic_file_path" to "Tệp",
        "txamusic_duration" to "Thời lượng",
        "txamusic_title" to "Tên bài hát",
        "txamusic_artist" to "Nghệ sĩ",
        "txamusic_album" to "Album",
        "txamusic_album_artist" to "Nghệ sĩ Album",
        "txamusic_composer" to "Nhạc sĩ",
        "txamusic_year" to "Năm",
        "txamusic_track_number" to "Track #",
        "txamusic_tag_editor_note" to "Các thay đổi sẽ được ghi trực tiếp vào tệp âm thanh và đồng bộ hóa với thư viện nhạc của bạn.",
        "txamusic_tag_saved" to "Đã lưu thông tin",
        "txamusic_tag_save_failed" to "Không thể lưu thông tin",
        "txamusic_ringtone_set_success" to "Đã đặt làm nhạc chuông",
        "txamusic_ringtone_set_failed" to "Lỗi khi đặt nhạc chuông",
        "txamusic_ringtone_permission_title" to "Quyền cài đặt hệ thống",
        "txamusic_ringtone_permission_desc" to "Để đặt nhạc chuông, ứng dụng cần quyền thay đổi cài đặt hệ thống.",
        "txamusic_error_song_not_loaded" to "Chưa tải thông tin bài hát. Vui lòng đợi và thử lại.",
        "txamusic_error_song_not_found" to "Không tìm thấy bài hát trong thư viện",
        
        // Manual Add
        "txamusic_select_music" to "Chọn nhạc",
        "txamusic_back" to "Quay lại",
        "txamusic_add_selected" to "Thêm (%d)",
        "txamusic_empty_folder" to "Thư mục trống",
        "txamusic_manual_add_result" to "Đã thêm %d bài. Bỏ qua %d bài đã có.",
        "txamusic_storage_root" to "Bộ nhớ gốc (Root)",
        "txamusic_internal_storage" to "Bộ nhớ trong",

        
        // Delete from App
        "txamusic_delete_from_app" to "Xóa khỏi thư viện",
        "txamusic_delete_confirm_title" to "Xóa bài hát?",
        "txamusic_delete_confirm_message" to "Xóa \"%s\" khỏi ứng dụng? File nhạc sẽ không bị xóa.",
        "txamusic_song_deleted" to "Đã xóa bài hát khỏi thư viện",
        
        // UI Hints
        "txamusic_tap_to_close" to "Chạm vào đâu đó để đóng",

        // Post Update Dialog
        "txamusic_post_update_title" to "Cập nhật thành công!",
        "txamusic_post_update_intro" to "Cảm ơn bạn đã cài đặt %s v%s",
        "txamusic_btn_close" to "Đóng",

        // Lyrics Search
        "txamusic_lyrics_searching" to "Đang tìm kiếm lời bài hát...",
        "txamusic_lyrics_search_success" to "Đã tìm thấy lời bài hát!",
        "txamusic_lyrics_search_failed" to "Không tìm thấy lời cho bài hát này",
        "txamusic_share_backup_text" to "Đây là file sao lưu của tôi cho trình phát nhạc TXA Music Player.",
        "txamusic_share_backup_title" to "Chia sẻ bản sao lưu",
        
        // Multi-select
        "txamusic_multi_select" to "Chọn nhiều",
        "txamusic_multi_select_count" to "Đã chọn %d",
        "txamusic_batch_actions" to "Hành động hàng loạt",
        "txamusic_action_add_all_to_playlist" to "Thêm tất cả vào Playlist",
        "txamusic_action_delete_all" to "Xóa tất cả",
        "txamusic_action_play_selected" to "Phát các bài đã chọn",
        "txamusic_confirm_delete_multiple_title" to "Xóa %d bài hát?",
        "txamusic_confirm_delete_multiple_desc" to "Bạn có chắc muốn xóa %d bài hát này khỏi thư viện không?",
        
        // Lyrics Overlay
        "txamusic_show_lyrics_overlay" to "Lời bài hát nổi",
        "txamusic_show_lyrics_overlay_desc" to "Hiển thị lời bài hát lên trên các ứng dụng khác",
        "txamusic_overlay_permission_title" to "Cần cấp quyền Overlay",
        "txamusic_overlay_permission_desc" to "Để hiển thị lời bài hát nổi trên các ứng dụng khác, vui lòng cấp quyền \"Hiển thị trên các ứng dụng khác\".",
        "txamusic_lyrics_style" to "Kiểu lời bài hát",
        "txamusic_lyrics_style_desc" to "Tùy chỉnh cách hiển thị lời bài hát",
        "txamusic_lyrics_font_size" to "Cỡ chữ",
        "txamusic_lyrics_text_align" to "Căn lề chữ",
        "txamusic_lyrics_align_left" to "Trái",
        "txamusic_lyrics_align_center" to "Giữa",
        "txamusic_lyrics_align_right" to "Phải",
        
        // Refresh Interval
        "txamusic_refresh_interval" to "Tần suất làm mới",
        "txamusic_refresh_interval_desc" to "Tự động làm mới danh sách phát sau khoảng thời gian này",
        "txamusic_refresh_never" to "Không tự động",
        "txamusic_refresh_hourly" to "Mỗi giờ",
        "txamusic_refresh_daily" to "Hàng ngày",
        "txamusic_refresh_weekly" to "Hàng tuần",
        
        // App Shortcuts
        "txamusic_shortcuts_shuffle_all" to "Phát ngẫu nhiên tất cả",
        "txamusic_shortcuts_top_tracks" to "Bài hát nghe nhiều nhất",
        "txamusic_shortcuts_last_added" to "Vừa mới thêm",
        "txamusic_shortcuts_check_update" to "Kiểm tra cập nhật",
        
        // Shortcut Check Update Service
        "txamusic_shortcut_checking_update" to "Đang kiểm tra cập nhật...",
        "txamusic_shortcut_update_found" to "Có phiên bản mới %s!",
        "txamusic_shortcut_update_found_title" to "Có bản cập nhật",
        "txamusic_shortcut_no_update" to "Bạn đang dùng phiên bản mới nhất!",
        "txamusic_shortcut_update_error" to "Không thể kiểm tra cập nhật",
        "txamusic_shortcut_open_app" to "Mở ứng dụng",
        "txamusic_shortcut_update_channel_name" to "Kiểm tra cập nhật",
        "txamusic_shortcut_update_channel_desc" to "Thông báo kiểm tra cập nhật từ lối tắt ứng dụng",
        "txamusic_tag_emulator" to "Giả lập thẻ",

        // Widget Settings
        "txamusic_widget_settings" to "Cài đặt Widget",
        "txamusic_widget_settings_desc" to "Tùy chỉnh giao diện và điều khiển của widget ngoài màn hình chính.",
        "txamusic_widget_preview_title" to "Tên bài hát",
        "txamusic_widget_preview_artist" to "Tên nghệ sĩ",
        "txamusic_widget_display" to "Tùy chọn hiển thị",
        "txamusic_widget_show_album_art" to "Hiện ảnh bìa",
        "txamusic_widget_show_title" to "Hiện tiêu đề bài hát",
        "txamusic_widget_show_artist" to "Hiện tên nghệ sĩ",
        "txamusic_widget_show_progress" to "Hiện thanh tiến trình",
        "txamusic_widget_controls" to "Tùy chọn điều khiển",
        "txamusic_widget_show_shuffle" to "Hiện nút trộn bài",
        "txamusic_widget_show_repeat" to "Hiện nút lặp lại",
        "txamusic_widget_info" to "Các thay đổi sẽ được áp dụng ngay lập tức cho tất cả các widget trên màn hình chính của bạn.",

        // Search, Queue, Lyrics
        "txamusic_search_hint" to "Tìm bài hát, album, nghệ sĩ...",
        "txamusic_no_results" to "Không tìm thấy kết quả",
        "txamusic_playing_queue" to "Danh sách chờ",
        "txamusic_queue_empty" to "Danh sách chờ trống",
        "txamusic_playing" to "Đang phát",
        "txamusic_up_next" to "Tiếp theo",
        "txamusic_clear_queue" to "Xóa danh sách chờ",
        "txamusic_clear_queue_confirm" to "Bạn có chắc muốn xóa toàn bộ danh sách chờ?",
        "txamusic_btn_clear" to "Xóa",
        "txamusic_no_lyrics" to "Không tìm thấy lời bài hát",
        "txamusic_no_lyrics_hint" to "Thêm file .lrc cạnh file nhạc hoặc tìm kiếm trực tuyến",
        "txamusic_library" to "Thư viện",
        "txamusic_folders" to "Thư mục",
        "txamusic_genres" to "Thể loại",
        "txamusic_albums" to "Album",
        "txamusic_artists" to "Nghệ sĩ",
        "txamusic_playlists" to "Danh sách phát",
        "txamusic_audio_route_speaker" to "Âm thanh đã chuyển sang loa ngoài",
        
        // Visualizer
        "txamusic_settings_visualizer_title" to "Hiệu ứng sóng nhạc",
        "txamusic_settings_visualizer_desc" to "Hiển thị sóng nhạc động trong màn hình đang phát",
        "txamusic_settings_visualizer_style" to "Kiểu hiệu ứng",
        "txamusic_visualizer_bars" to "Thanh",
        "txamusic_visualizer_wave" to "Sóng",
        "txamusic_visualizer_circle" to "Vòng tròn",
        "txamusic_visualizer_spectrum" to "Phổ tần",
        "txamusic_visualizer_glow" to "Thanh phát sáng",
        "txamusic_visualizer_fluid" to "Chuyển động (Namida)",
        "txamusic_permission_audio_denied" to "Cần quyền ghi âm để hiển thị sóng nhạc.",
        "txamusic_blacklist_folders" to "Chặn thư mục",
        "txamusic_blacklist_folder_desc" to "Ẩn các thư mục khỏi thư viện nhạc",
        "txamusic_folder_blacklisted" to "Đã chặn thư mục",
        "txamusic_folder_removed_from_blacklist" to "Đã bỏ chặn thư mục",
        "txamusic_import_playlist" to "Nhập Danh sách phát",
        "txamusic_import_playlist_desc" to "Chọn file .m3u hoặc .m3u8 để nhập danh sách bài hát.",
        "txamusic_select_file" to "Chọn File",
        "txamusic_select_playlist_file" to "Chọn Playlist M3U",
        "txamusic_import_success" to "Đã nhập %d bài hát từ playlist",
        "txamusic_import_failed" to "Nhập playlist thất bại",
        "txamusic_rename_playlist" to "Đổi tên Playlist",
        "txamusic_save_playlist" to "Lưu Playlist",
        "txamusic_save_playlist_desc" to "Xuất %d bài hát ra file M3U",
        "txamusic_file_name" to "Tên File",
        "txamusic_save_location_hint" to "Đã lưu tại: Music/Playlists/",
        "txamusic_playlist_saved" to "Đã lưu playlist thành công",
        "txamusic_playlist_save_failed" to "Lưu playlist thất bại",
        "txamusic_playlist_renamed" to "Đã đổi tên playlist",
        "txamusic_btn_rename_playlist" to "Đổi tên Playlist",
        "txamusic_export_playlist" to "Xuất file M3U"
    )

    suspend fun init(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedLocale = prefs.getString(KEY_LOCALE, null)

        if (savedLocale != null) {
            currentLocale = savedLocale
        } else {
            // First time - detect system language
            val sysLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.resources.configuration.locales[0].language
            } else {
                @Suppress("DEPRECATION")
                context.resources.configuration.locale.language
            }
            currentLocale = if (sysLocale == "vi") "vi" else "en"
            prefs.edit().putString(KEY_LOCALE, currentLocale).apply()
        }

        loadFromCache(context)
        syncWithServer(context)
    }

    private fun loadFromCache(context: Context) {
        try {
            val cacheFile = getCacheFile(context, currentLocale)
            if (cacheFile.exists()) {
                val jsonStr = cacheFile.readText()
                val json = JSONObject(jsonStr)
                updateMapFromJson(json)
                updatedAt = json.optString("updated_at", "")
                TXALogger.appI("TXATranslation", "Loaded $currentLocale from cache, updated_at: $updatedAt")

                // Hotfix: Remove outdated Root Modal text if it contains markdown '**'
                // This forces the use of the updated fallback map with HTML '<b>' tags
                if (translations["txamusic_root_modal_body"]?.contains("**") == true) {
                    translations.remove("txamusic_root_modal_body")
                }
            }
        } catch (e: Exception) {
            TXALogger.appE("TXATranslation", "Cache load failed", e)
        }
    }

    private suspend fun syncWithServer(context: Context) = withContext(Dispatchers.IO) {
        try {
            val url = "${BASE_URL}tXALocale/$currentLocale"
            val request = Request.Builder().url(url).build()
            val response = TXAHttp.client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext
                val json = JSONObject(body)
                val serverUpdatedAt = json.optString("updated_at", "")

                val serverMillis = TXAFormat.parseUtcToMillis(serverUpdatedAt)
                val currentMillis = TXAFormat.parseUtcToMillis(updatedAt)

                if (serverMillis != currentMillis || updatedAt.isEmpty()) {
                    saveToCache(context, currentLocale, body)
                    updateMapFromJson(json)
                    updatedAt = serverUpdatedAt
                    availableLanguages = emptyList() // Clear cache
                    _onLanguageChanged.value += 1  // Trigger UI refresh
                    TXALogger.appI(
                        "TXATranslation",
                        "Language synced with server: $serverUpdatedAt (Server: $serverMillis, Local cached: $currentMillis)"
                    )
                } else {
                    TXALogger.appI(
                        "TXATranslation",
                        "Language is up-to-date (Server: $serverMillis, Local cached: $currentMillis)"
                    )
                }
            } else {
                TXALogger.appE("TXATranslation", "Sync failed: code ${response.code}")
            }
        } catch (e: Exception) {
            TXALogger.appE("TXATranslation", "Language server error", e)
        }
    }

    private fun saveToCache(context: Context, locale: String, jsonStr: String) {
        try {
            val cacheFile = getCacheFile(context, locale)
            cacheFile.writeText(jsonStr)
        } catch (e: Exception) {
            TXALogger.appE("TXATranslation", "Failed to save cache", e)
        }
    }

    private fun getCacheFile(context: Context, locale: String): File {
        val cacheDir = File(context.filesDir, "cache/lang")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        return File(cacheDir, "lang_$locale.json")
    }

    private fun updateMapFromJson(json: JSONObject) {
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            var value = json.getString(key)
            // Automatically patch markdown bold **text** to HTML <b>text</b>
            // This fixes issues where server returns outdated markdown syntax for Root Modal etc.
            if (value.contains("**")) {
                value = value.replace(Regex("\\*\\*(.*?)\\*\\*"), "<b>$1</b>")
            }
            translations[key] = value
        }
    }

    /**
     * Get translated string by key
     */
    fun txa(key: String): String = get(key)

    /**
     * Get translated string with format arguments
     */
    fun txa(key: String, vararg args: Any): String = get(key, *args)

    fun get(key: String, vararg args: Any): String {
        // Fallback priority: Dynamic Map -> Fallback Map (Vi/En) -> Key itself
        val fallbackMap = if (currentLocale == "vi") fallbackMapVi else fallbackMapEn
        var value = translations[key]

        // Anti-stale-cache hack for contact email body
        // Anti-stale-cache hack for contact email body
        if (key == "txamusic_contact_email_body" && value != null) {
            if (value.contains("[Enter your message here]") || value.contains("[Nhập nội dung tại đây]")) {
                value = null // Force fallback
            }
        }

        // Check dynamic map first
        if (value != null) {
            return try {
                if (args.isNotEmpty()) String.format(Locale.getDefault(), value, *args) else value
            } catch (e: Exception) {
                value
            }
        }

        // Not in dynamic map, check fallbacks
        val fbValue = fallbackMap[key] ?: fallbackMapEn[key]

        if (fbValue != null) {
            // Found in fallback but not in dynamic map -> Log it!
            TXALogger.fallbackKey(key)
        } else {
            // Not found anywhere -> Missing key
            TXALogger.missingKey(key)
        }

        val finalValue = fbValue

        val raw = finalValue ?: key

        return try {
            if (args.isNotEmpty()) String.format(Locale.getDefault(), raw, *args) else raw
        } catch (e: Exception) {
            raw
        }
    }

    fun getSystemLanguage(): String {
        return currentLocale
    }

    fun getCurrentLocale(): String = currentLocale

    /**
     * Get available languages from server
     * Falls back to hardcoded list if API fails
     */
    suspend fun getAvailableLanguages(context: Context): List<LanguageInfo> = withContext(Dispatchers.IO) {
        if (availableLanguages.isNotEmpty()) {
            return@withContext availableLanguages
        }

        _isLoadingLanguages.value = true
        TXALogger.langI("TXATranslation", "Fetching available languages from server...")

        try {
            val request = Request.Builder().url("${BASE_URL}locales").build()
            val response = TXAHttp.client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: "[]"
                val jsonArray = JSONArray(body)
                val languages = mutableListOf<LanguageInfo>()

                for (i in 0 until jsonArray.length()) {
                    val code = jsonArray.getString(i)
                    // Get display name from fallback or dynamic key
                    val displayName = get("txamusic_lang_$code")
                    languages.add(LanguageInfo(code, displayName))
                }

                availableLanguages = languages
                TXALogger.langI(
                    "TXATranslation",
                    "Got ${languages.size} languages from server: ${languages.map { it.code }}"
                )
                return@withContext languages
            }
        } catch (e: Exception) {
            TXALogger.langE("TXATranslation", "Failed to get languages from server", e)
        } finally {
            _isLoadingLanguages.value = false
        }

        // Fallback to hardcoded list
        val fallbacks = listOf(
            LanguageInfo("en", get("txamusic_lang_en")),
            LanguageInfo("vi", get("txamusic_lang_vi"))
        )
        availableLanguages = fallbacks
        fallbacks
    }

    /**
     * Check if we have cache for a locale
     */
    fun hasCacheFor(context: Context, locale: String): Boolean {
        return getCacheFile(context, locale).exists()
    }

    /**
     * Get cached updated_at timestamp for a locale
     */
    fun getCachedUpdatedAt(context: Context, locale: String): String? {
        try {
            val cacheFile = getCacheFile(context, locale)
            if (cacheFile.exists()) {
                val json = JSONObject(cacheFile.readText())
                val value = json.optString("updated_at", "")
                return if (value.isEmpty()) null else value
            }
        } catch (e: Exception) {
            TXALogger.langE("TXATranslation", "Error reading cache timestamp", e)
        }
        return null
    }

    private fun buildFallbackMapEn(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        // Existing fallback keys would go here

        // Info keys
        map["txamusic_info_device"] = "Device Info"
        map["txamusic_info_model"] = "Model: %s"
        map["txamusic_info_android"] = "Android: %s"
        map["txamusic_info_emulator"] = "Emulator: %s"
        map["txamusic_info_root_status"] = "Root Access: %s"
        map["txamusic_root_granted"] = "Granted"
        map["txamusic_root_denied"] = "Denied"
        map["txamusic_yes"] = "Yes"
        map["txamusic_no"] = "No"

        return map
    }

    /**
     * Download and apply a language
     * Flow:
     * 1. Check if cache exists -> apply immediately
     * 2. Fetch from server to compare updated_at
     * 3. If server has newer -> download and cache
     * 4. Apply and notify UI
     * 5. Fallback to embedded if all fails
     */
    suspend fun downloadAndApply(
        context: Context,
        locale: String,
        onProgress: ((Int, String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        TXALogger.langI("TXATranslation", "downloadAndApply called for locale: $locale")

        try {
            onProgress?.invoke(5, "Connecting...")
            val cacheFile = getCacheFile(context, locale)
            var cachedUpdatedAt: String? = null

            // Step 1: Apply cache immediately if exists
            if (cacheFile.exists()) {
                val cachedJson = JSONObject(cacheFile.readText())
                cachedUpdatedAt = cachedJson.optString("updated_at", "")

                currentLocale = locale
                updateMapFromJson(cachedJson)
                updatedAt = cachedUpdatedAt

                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putString(KEY_LOCALE, locale).apply()

                availableLanguages = emptyList() // Clear cache
                _onLanguageChanged.value += 1
                onProgress?.invoke(20, "Cache found, checking server...")
            } else {
                onProgress?.invoke(10, "No cache, connecting to server...")
                _isDownloadingLanguage.value = true
            }

            // Step 2: Check server for updates with a shorter timeout for splash stability
            val url = "${BASE_URL}tXALocale/$locale"
            val request = Request.Builder().url(url).build()
            onProgress?.invoke(30, "Fetching metadata...")

            val fastClient = TXAHttp.client.newBuilder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val response = fastClient.newCall(request).execute()

            if (response.isSuccessful) {
                onProgress?.invoke(50, "Server responded, parsing...")
                val body = response.body?.string() ?: throw Exception("Empty response body")

                val serverJson = JSONObject(body)
                val serverUpdatedAt = serverJson.optString("updated_at", "")

                val serverMillis = TXAFormat.parseUtcToMillis(serverUpdatedAt)
                val currentMillis = TXAFormat.parseUtcToMillis(cachedUpdatedAt)

                // Step 3: Update if needed
                if (serverMillis != currentMillis || !cacheFile.exists()) {
                    onProgress?.invoke(70, "Downloading newer version...")
                    saveToCache(context, locale, body)

                    currentLocale = locale
                    updateMapFromJson(serverJson)
                    updatedAt = serverUpdatedAt

                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putString(KEY_LOCALE, locale).apply()

                    availableLanguages = emptyList() // Clear cache
                    _onLanguageChanged.value += 1
                    onProgress?.invoke(90, "Applying translation...")
                } else {
                    onProgress?.invoke(100, "Up to date")
                }

                onProgress?.invoke(100, "Done")
                return@withContext true
            } else {
                throw Exception("Server error: ${response.code}")
            }
        } catch (e: Exception) {
            TXALogger.langE("TXATranslation", "downloadAndApply failed", e)
            onProgress?.invoke(-1, e.message ?: "Unknown error")
            return@withContext getCacheFile(context, locale).exists()
        } finally {
            _isDownloadingLanguage.value = false
        }
    }

    /**
     * Set locale without downloading (uses cache or fallback)
     */
    suspend fun setLocale(context: Context, locale: String) = withContext(Dispatchers.IO) {
        currentLocale = locale
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LOCALE, locale).apply()

        loadFromCache(context)
        availableLanguages = emptyList() // Clear cache
        _onLanguageChanged.value += 1
        TXALogger.langI("TXATranslation", "Locale set to: $locale")
    }
}

/**
 * Extension function for easy access
 */
fun String.txa(): String = TXATranslation.txa(this)
fun String.txa(vararg args: Any): String = TXATranslation.txa(this, *args)
