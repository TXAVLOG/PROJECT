package com.txapp.musicplayer.util

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * TXAAODSettings - Shared settings manager for synchronizing AOD configurations
 * between System AOD (MusicDreamService) and In-App AOD (AODScreen)
 * 
 * This singleton ensures both AODs use the same visual settings for consistency.
 */
object TXAAODSettings {
    
    // Clock color - hex string for serialization
    private val _clockColor = MutableStateFlow("#FFFFFF")
    val clockColor: StateFlow<String> = _clockColor.asStateFlow()
    
    // Show date toggle
    private val _showDate = MutableStateFlow(true)
    val showDate: StateFlow<Boolean> = _showDate.asStateFlow()
    
    // Date format (0 = MMM dd, 1 = dd MMM, 2 = dd/MM, etc.)
    private val _dateFormat = MutableStateFlow(0)
    val dateFormat: StateFlow<Int> = _dateFormat.asStateFlow()
    
    // Show battery indicator
    private val _showBattery = MutableStateFlow(true)
    val showBattery: StateFlow<Boolean> = _showBattery.asStateFlow()
    
    // Inactivity timeout in milliseconds (for in-app AOD)
    private val _inactivityTimeout = MutableStateFlow(15_000L) // 15 seconds
    val inactivityTimeout: StateFlow<Long> = _inactivityTimeout.asStateFlow()
    
    // Pixel shift interval in milliseconds
    private val _pixelShiftInterval = MutableStateFlow(60_000L) // 60 seconds
    val pixelShiftInterval: StateFlow<Long> = _pixelShiftInterval.asStateFlow()
    
    // Holiday greeting - don't show again flag
    private val _holidayGreetingDismissed = MutableStateFlow(false)
    val holidayGreetingDismissed: StateFlow<Boolean> = _holidayGreetingDismissed.asStateFlow()
    
    // Clock breathing animation enabled
    private val _breathingAnimation = MutableStateFlow(true)
    val breathingAnimation: StateFlow<Boolean> = _breathingAnimation.asStateFlow()
    
    // Night mode (extra dim)
    private val _nightMode = MutableStateFlow(false)
    val nightMode: StateFlow<Boolean> = _nightMode.asStateFlow()
    
    // Show media controls in AOD
    private val _showControls = MutableStateFlow(true)
    val showControls: StateFlow<Boolean> = _showControls.asStateFlow()
    
    /**
     * Initialize settings from SharedPreferences
     */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences("txa_aod_settings", Context.MODE_PRIVATE)
        _clockColor.value = prefs.getString("clock_color", "#FFFFFF") ?: "#FFFFFF"
        _showDate.value = prefs.getBoolean("show_date", true)
        _dateFormat.value = prefs.getInt("date_format", 0)
        _showBattery.value = prefs.getBoolean("show_battery", true)
        _inactivityTimeout.value = prefs.getLong("inactivity_timeout", 15_000L)
        _pixelShiftInterval.value = prefs.getLong("pixel_shift_interval", 60_000L)
        _holidayGreetingDismissed.value = prefs.getBoolean("holiday_dismissed", false)
        _breathingAnimation.value = prefs.getBoolean("breathing_animation", true)
        _nightMode.value = prefs.getBoolean("night_mode", false)
        _showControls.value = prefs.getBoolean("show_controls", true)
        
        TXALogger.appI("TXAAODSettings", "AOD Settings initialized")
    }
    
    /**
     * Save all settings to SharedPreferences
     */
    private fun save(context: Context) {
        val prefs = context.getSharedPreferences("txa_aod_settings", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("clock_color", _clockColor.value)
            putBoolean("show_date", _showDate.value)
            putInt("date_format", _dateFormat.value)
            putBoolean("show_battery", _showBattery.value)
            putLong("inactivity_timeout", _inactivityTimeout.value)
            putLong("pixel_shift_interval", _pixelShiftInterval.value)
            putBoolean("holiday_dismissed", _holidayGreetingDismissed.value)
            putBoolean("breathing_animation", _breathingAnimation.value)
            putBoolean("night_mode", _nightMode.value)
            putBoolean("show_controls", _showControls.value)
            apply()
        }
    }
    
    // Setters with auto-save
    fun setClockColor(context: Context, hexColor: String) {
        _clockColor.value = hexColor
        save(context)
    }
    
    fun setClockColorFromCompose(context: Context, color: Color) {
        val hexColor = String.format("#%06X", 0xFFFFFF and color.toArgb())
        setClockColor(context, hexColor)
    }
    
    fun setShowDate(context: Context, show: Boolean) {
        _showDate.value = show
        save(context)
    }
    
    fun setDateFormat(context: Context, format: Int) {
        _dateFormat.value = format
        save(context)
    }
    
    fun setShowBattery(context: Context, show: Boolean) {
        _showBattery.value = show
        save(context)
    }
    
    fun setInactivityTimeout(context: Context, timeoutMs: Long) {
        _inactivityTimeout.value = timeoutMs
        save(context)
    }
    
    fun setPixelShiftInterval(context: Context, intervalMs: Long) {
        _pixelShiftInterval.value = intervalMs
        save(context)
    }
    
    fun dismissHolidayGreeting(context: Context) {
        _holidayGreetingDismissed.value = true
        save(context)
    }
    
    fun resetHolidayGreeting(context: Context) {
        _holidayGreetingDismissed.value = false
        save(context)
    }
    
    fun setBreathingAnimation(context: Context, enabled: Boolean) {
        _breathingAnimation.value = enabled
        save(context)
    }
    
    fun setNightMode(context: Context, enabled: Boolean) {
        _nightMode.value = enabled
        save(context)
    }

    fun setShowControls(context: Context, enabled: Boolean) {
        _showControls.value = enabled
        save(context)
    }
    
    /**
     * Get Color from hex string for Compose
     */
    fun getClockColorCompose(): Color {
        return try {
            Color(android.graphics.Color.parseColor(_clockColor.value))
        } catch (e: Exception) {
            Color.White
        }
    }
}
