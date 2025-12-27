package ms.txams.vv.data.manager

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import ms.txams.vv.core.TXABackgroundLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TXA Now Bar Settings Manager
 * Manages customizable buttons/actions for Samsung Now Bar and Media Notification
 * 
 * Features:
 * - Enable/disable specific notification buttons
 * - Customize which actions appear in compact/expanded view
 * - Support for custom actions like Sleep Timer, Repeat, Shuffle
 * - Compatible with Samsung One UI Now Bar
 * 
 * @author TXA - fb.com/vlog.txa.2311 - txavlog7@gmail.com
 */
@Singleton
class TXANowBarSettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "txa_nowbar_settings"
        
        // Button keys
        const val KEY_SHOW_PREVIOUS = "show_previous"
        const val KEY_SHOW_PLAY_PAUSE = "show_play_pause"
        const val KEY_SHOW_NEXT = "show_next"
        const val KEY_SHOW_STOP = "show_stop"
        const val KEY_SHOW_SHUFFLE = "show_shuffle"
        const val KEY_SHOW_REPEAT = "show_repeat"
        const val KEY_SHOW_LIKE = "show_like"
        const val KEY_SHOW_SLEEP_TIMER = "show_sleep_timer"
        const val KEY_SHOW_LYRICS = "show_lyrics"
        
        // Compact view (max 3 buttons)
        const val KEY_COMPACT_BUTTONS = "compact_buttons"
        
        // Album art style
        const val KEY_ALBUM_ART_STYLE = "album_art_style"
        const val STYLE_SQUARE = 0
        const val STYLE_ROUNDED = 1
        const val STYLE_CIRCLE = 2
    }
    
    /**
     * Default settings - All basic controls enabled
     */
    private val defaults = mapOf(
        KEY_SHOW_PREVIOUS to true,
        KEY_SHOW_PLAY_PAUSE to true,
        KEY_SHOW_NEXT to true,
        KEY_SHOW_STOP to false,      // Hidden by default
        KEY_SHOW_SHUFFLE to false,   // Hidden by default
        KEY_SHOW_REPEAT to false,    // Hidden by default
        KEY_SHOW_LIKE to false,      // Hidden by default
        KEY_SHOW_SLEEP_TIMER to false,
        KEY_SHOW_LYRICS to false
    )
    
    /**
     * Check if a button should be shown
     */
    fun isButtonEnabled(buttonKey: String): Boolean {
        return prefs.getBoolean(buttonKey, defaults[buttonKey] ?: false)
    }
    
    /**
     * Enable or disable a button
     */
    fun setButtonEnabled(buttonKey: String, enabled: Boolean) {
        prefs.edit().putBoolean(buttonKey, enabled).apply()
        TXABackgroundLogger.d("Now Bar setting changed: $buttonKey = $enabled")
    }
    
    /**
     * Toggle a button
     */
    fun toggleButton(buttonKey: String): Boolean {
        val newValue = !isButtonEnabled(buttonKey)
        setButtonEnabled(buttonKey, newValue)
        return newValue
    }
    
    /**
     * Get all enabled buttons for expanded notification
     * Returns list of button keys that should be shown
     */
    fun getEnabledButtons(): List<String> {
        return defaults.keys.filter { isButtonEnabled(it) }
    }
    
    /**
     * Get buttons for compact notification (max 3)
     * Default: Previous, Play/Pause, Next
     */
    fun getCompactButtons(): List<String> {
        val saved = prefs.getString(KEY_COMPACT_BUTTONS, null)
        return if (saved != null) {
            saved.split(",").take(3)
        } else {
            listOf(KEY_SHOW_PREVIOUS, KEY_SHOW_PLAY_PAUSE, KEY_SHOW_NEXT)
        }
    }
    
    /**
     * Set compact buttons (indices for MediaStyle.setShowActionsInCompactView)
     */
    fun setCompactButtons(buttons: List<String>) {
        prefs.edit().putString(KEY_COMPACT_BUTTONS, buttons.take(3).joinToString(",")).apply()
    }
    
    /**
     * Get album art style for notification
     */
    fun getAlbumArtStyle(): Int {
        return prefs.getInt(KEY_ALBUM_ART_STYLE, STYLE_ROUNDED)
    }
    
    /**
     * Set album art style
     */
    fun setAlbumArtStyle(style: Int) {
        prefs.edit().putInt(KEY_ALBUM_ART_STYLE, style).apply()
    }
    
    /**
     * Get all settings as a map for UI binding
     */
    fun getAllSettings(): Map<String, Boolean> {
        return defaults.keys.associateWith { isButtonEnabled(it) }
    }
    
    /**
     * Reset to default settings
     */
    fun resetToDefaults() {
        prefs.edit().clear().apply()
        TXABackgroundLogger.i("Now Bar settings reset to defaults")
    }
    
    /**
     * Data class for Now Bar button info
     */
    data class NowBarButton(
        val key: String,
        val titleResKey: String,
        val iconRes: Int,
        val isEnabled: Boolean
    )
    
    /**
     * Get all available buttons with their current state
     */
    fun getAllButtonsInfo(): List<NowBarButton> {
        return listOf(
            NowBarButton(KEY_SHOW_PREVIOUS, "txamusic_previous", android.R.drawable.ic_media_previous, isButtonEnabled(KEY_SHOW_PREVIOUS)),
            NowBarButton(KEY_SHOW_PLAY_PAUSE, "txamusic_play", android.R.drawable.ic_media_play, isButtonEnabled(KEY_SHOW_PLAY_PAUSE)),
            NowBarButton(KEY_SHOW_NEXT, "txamusic_next", android.R.drawable.ic_media_next, isButtonEnabled(KEY_SHOW_NEXT)),
            NowBarButton(KEY_SHOW_STOP, "txamusic_stop", android.R.drawable.ic_media_pause, isButtonEnabled(KEY_SHOW_STOP)),
            NowBarButton(KEY_SHOW_SHUFFLE, "txamusic_shuffle", android.R.drawable.ic_menu_rotate, isButtonEnabled(KEY_SHOW_SHUFFLE)),
            NowBarButton(KEY_SHOW_REPEAT, "txamusic_repeat", android.R.drawable.ic_menu_revert, isButtonEnabled(KEY_SHOW_REPEAT)),
            NowBarButton(KEY_SHOW_LIKE, "txamusic_favorites", android.R.drawable.btn_star, isButtonEnabled(KEY_SHOW_LIKE)),
            NowBarButton(KEY_SHOW_SLEEP_TIMER, "txamusic_sleep_timer", android.R.drawable.ic_menu_day, isButtonEnabled(KEY_SHOW_SLEEP_TIMER)),
            NowBarButton(KEY_SHOW_LYRICS, "txamusic_lyrics", android.R.drawable.ic_menu_gallery, isButtonEnabled(KEY_SHOW_LYRICS))
        )
    }
}
