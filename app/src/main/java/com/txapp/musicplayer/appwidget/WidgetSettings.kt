package com.txapp.musicplayer.appwidget

import android.content.Context
import org.json.JSONObject

/**
 * Widget display settings
 * 
 * Allows users to customize which elements are shown on the widget.
 * Settings are persisted to SharedPreferences and can be backed up/restored.
 */
data class WidgetSettings(
    val showAlbumArt: Boolean = true,
    val showTitle: Boolean = true,
    val showArtist: Boolean = true,
    val showProgress: Boolean = true,
    val showShuffle: Boolean = true,
    val showRepeat: Boolean = true,
    val showPrevNext: Boolean = true,
    val backgroundColor: String = "#99000000", // Semi-transparent black
    val textColor: String = "#FFFFFF",
    val accentColor: String = "#00D269" // TXA green
) {
    companion object {
        private const val PREFS_NAME = "widget_settings"
        private const val KEY_SETTINGS = "widget_config"
        
        /**
         * Load settings from SharedPreferences
         */
        fun load(context: Context): WidgetSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_SETTINGS, null) ?: return WidgetSettings()
            
            return try {
                fromJson(json)
            } catch (e: Exception) {
                WidgetSettings()
            }
        }
        
        /**
         * Parse settings from JSON string (for backup restore)
         */
        fun fromJson(json: String): WidgetSettings {
            val obj = JSONObject(json)
            return WidgetSettings(
                showAlbumArt = obj.optBoolean("showAlbumArt", true),
                showTitle = obj.optBoolean("showTitle", true),
                showArtist = obj.optBoolean("showArtist", true),
                showProgress = obj.optBoolean("showProgress", true),
                showShuffle = obj.optBoolean("showShuffle", true),
                showRepeat = obj.optBoolean("showRepeat", true),
                showPrevNext = obj.optBoolean("showPrevNext", true),
                backgroundColor = obj.optString("backgroundColor", "#99000000"),
                textColor = obj.optString("textColor", "#FFFFFF"),
                accentColor = obj.optString("accentColor", "#00D269")
            )
        }
    }
    
    /**
     * Save settings to SharedPreferences
     */
    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SETTINGS, toJson()).apply()
        
        // Trigger widget update
        TXAMusicWidget.updateWidgets(context)
    }
    
    /**
     * Convert to JSON string (for backup)
     */
    fun toJson(): String {
        return JSONObject().apply {
            put("showAlbumArt", showAlbumArt)
            put("showTitle", showTitle)
            put("showArtist", showArtist)
            put("showProgress", showProgress)
            put("showShuffle", showShuffle)
            put("showRepeat", showRepeat)
            put("showPrevNext", showPrevNext)
            put("backgroundColor", backgroundColor)
            put("textColor", textColor)
            put("accentColor", accentColor)
        }.toString()
    }
}
