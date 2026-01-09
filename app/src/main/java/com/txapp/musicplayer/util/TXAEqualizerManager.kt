package com.txapp.musicplayer.util

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Custom Equalizer Manager
 * Manages Equalizer, BassBoost, and Virtualizer audio effects
 * Works with ExoPlayer's audio session ID
 */
object TXAEqualizerManager {
    
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    
    private var currentAudioSessionId: Int = 0
    
    // State flows
    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()
    
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    private val _bandLevels = MutableStateFlow<List<Int>>(emptyList())
    val bandLevels: StateFlow<List<Int>> = _bandLevels.asStateFlow()
    
    private val _bassBoostStrength = MutableStateFlow(0)
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()
    
    private val _virtualizerStrength = MutableStateFlow(0)
    val virtualizerStrength: StateFlow<Int> = _virtualizerStrength.asStateFlow()
    
    private val _currentPreset = MutableStateFlow(-1)
    val currentPreset: StateFlow<Int> = _currentPreset.asStateFlow()
    
    // Equalizer info
    var numberOfBands: Short = 0
        private set
    var bandLevelRange: Pair<Short, Short> = Pair(0, 0)
        private set
    var centerFrequencies: List<Int> = emptyList()
        private set
    var presetNames: List<String> = emptyList()
        private set
    
    /**
     * Initialize equalizer with audio session ID
     */
    fun init(audioSessionId: Int) {
        if (audioSessionId <= 0) {
            TXALogger.appI("TXAEqualizerManager", "Invalid audio session ID: $audioSessionId")
            return
        }
        
        if (currentAudioSessionId == audioSessionId && equalizer != null) {
            TXALogger.appI("TXAEqualizerManager", "Already initialized with session $audioSessionId")
            return
        }
        
        release() // Release previous instance
        currentAudioSessionId = audioSessionId
        
        try {
            // Initialize Equalizer
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = TXAPreferences.isEqualizerEnabled
            }
            
            equalizer?.let { eq ->
                numberOfBands = eq.numberOfBands
                bandLevelRange = Pair(eq.bandLevelRange[0], eq.bandLevelRange[1])
                
                // Get center frequencies
                val freqs = mutableListOf<Int>()
                for (i in 0 until numberOfBands) {
                    freqs.add(eq.getCenterFreq(i.toShort()) / 1000) // Convert to Hz
                }
                centerFrequencies = freqs
                
                // Get preset names
                val presets = mutableListOf<String>()
                for (i in 0 until eq.numberOfPresets) {
                    presets.add(eq.getPresetName(i.toShort()))
                }
                presetNames = presets
                
                // Load saved band levels
                val savedLevels = TXAPreferences.getEqualizerBandLevels(numberOfBands.toInt())
                if (savedLevels.isNotEmpty()) {
                    savedLevels.forEachIndexed { index, level ->
                        if (index < numberOfBands) {
                            eq.setBandLevel(index.toShort(), level.toShort())
                        }
                    }
                    _bandLevels.value = savedLevels
                } else {
                    _bandLevels.value = List(numberOfBands.toInt()) { 0 }
                }
                
                // Load saved preset
                val savedPreset = TXAPreferences.getEqualizerPreset()
                if (savedPreset >= 0 && savedPreset < eq.numberOfPresets) {
                    eq.usePreset(savedPreset.toShort())
                    _currentPreset.value = savedPreset
                    // Update band levels from preset
                    updateBandLevelsFromEqualizer()
                }
            }
            
            // Initialize Bass Boost
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = TXAPreferences.isBassBoostEnabled
                if (strengthSupported) {
                    val savedStrength = TXAPreferences.getBassBoostStrength()
                    setStrength(savedStrength.toShort())
                    _bassBoostStrength.value = savedStrength
                }
            }
            
            // Initialize Virtualizer
            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = TXAPreferences.isVirtualizerEnabled
                if (strengthSupported) {
                    val savedStrength = TXAPreferences.getVirtualizerStrength()
                    setStrength(savedStrength.toShort())
                    _virtualizerStrength.value = savedStrength
                }
            }
            
            _isEnabled.value = TXAPreferences.isEqualizerEnabled
            _isInitialized.value = true
            
            TXALogger.appI("TXAEqualizerManager", "Initialized: $numberOfBands bands, ${presetNames.size} presets")
            
        } catch (e: Exception) {
            TXALogger.appE("TXAEqualizerManager", "Failed to initialize", e)
            release()
        }
    }
    
    /**
     * Enable/disable all effects
     */
    fun setEnabled(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled && TXAPreferences.isBassBoostEnabled
            virtualizer?.enabled = enabled && TXAPreferences.isVirtualizerEnabled
            _isEnabled.value = enabled
            TXAPreferences.isEqualizerEnabled = enabled
            TXALogger.appI("TXAEqualizerManager", "Enabled: $enabled")
        } catch (e: Exception) {
            TXALogger.appE("TXAEqualizerManager", "Error setting enabled", e)
        }
    }
    
    // Physical hardware limit is usually 1500 or 2000
    private fun clampToHardware(level: Int): Int {
        val min = bandLevelRange.first.toInt()
        val max = bandLevelRange.second.toInt()
        return level.coerceIn(min, max)
    }
    
    /**
     * Set band level with virtual support up to ±30dB
     */
    fun setBandLevel(band: Int, level: Int) {
        try {
            // Apply clamped value to hardware
            equalizer?.setBandLevel(band.toShort(), clampToHardware(level).toShort())
            
            // Save virtual value to state and preferences
            val newLevels = _bandLevels.value.toMutableList()
            if (band < newLevels.size) {
                newLevels[band] = level
                _bandLevels.value = newLevels
                TXAPreferences.setEqualizerBandLevels(newLevels)
            }
            _currentPreset.value = -1 // Custom = no preset
            TXAPreferences.setEqualizerPreset(-1)
        } catch (e: Exception) {
            TXALogger.appE("TXAEqualizerManager", "Error setting band level", e)
        }
    }
    
    /**
     * Use preset
     */
    fun usePreset(presetIndex: Int) {
        try {
            equalizer?.usePreset(presetIndex.toShort())
            _currentPreset.value = presetIndex
            TXAPreferences.setEqualizerPreset(presetIndex)
            updateBandLevelsFromEqualizer()
            TXALogger.appI("TXAEqualizerManager", "Using preset: ${presetNames.getOrNull(presetIndex)}")
        } catch (e: Exception) {
            TXALogger.appE("TXAEqualizerManager", "Error using preset", e)
        }
    }
    
    /**
     * Set bass boost strength (0-1000)
     */
    fun setBassBoostStrength(strength: Int) {
        try {
            bassBoost?.setStrength(strength.toShort())
            _bassBoostStrength.value = strength
            TXAPreferences.setBassBoostStrength(strength)
        } catch (e: Exception) {
            TXALogger.appE("TXAEqualizerManager", "Error setting bass boost", e)
        }
    }
    
    /**
     * Set virtualizer strength (0-1000)
     */
    fun setVirtualizerStrength(strength: Int) {
        try {
            virtualizer?.setStrength(strength.toShort())
            _virtualizerStrength.value = strength
            TXAPreferences.setVirtualizerStrength(strength)
        } catch (e: Exception) {
            TXALogger.appE("TXAEqualizerManager", "Error setting virtualizer", e)
        }
    }
    
    /**
     * Reset all effects to default
     */
    fun reset() {
        try {
            // Reset band levels to 0
            val defaultLevels = List(numberOfBands.toInt()) { 0 }
            defaultLevels.forEachIndexed { index, level ->
                equalizer?.setBandLevel(index.toShort(), level.toShort())
            }
            _bandLevels.value = defaultLevels
            TXAPreferences.setEqualizerBandLevels(defaultLevels)
            
            // Reset preset
            _currentPreset.value = -1
            TXAPreferences.setEqualizerPreset(-1)
            
            // Reset bass and virtualizer
            bassBoost?.setStrength(0)
            virtualizer?.setStrength(0)
            _bassBoostStrength.value = 0
            _virtualizerStrength.value = 0
            TXAPreferences.setBassBoostStrength(0)
            TXAPreferences.setVirtualizerStrength(0)
            
            TXALogger.appI("TXAEqualizerManager", "Reset all effects")
        } catch (e: Exception) {
            TXALogger.appE("TXAEqualizerManager", "Error resetting", e)
        }
    }
    
    /**
     * Check if equalizer is available
     */
    fun isAvailable(): Boolean = _isInitialized.value
    
    /**
     * Check if bass boost is supported
     */
    fun isBassBoostSupported(): Boolean = bassBoost?.strengthSupported ?: false
    
    /**
     * Check if virtualizer is supported
     */
    fun isVirtualizerSupported(): Boolean = virtualizer?.strengthSupported ?: false
    
    private fun updateBandLevelsFromEqualizer() {
        equalizer?.let { eq ->
            val levels = mutableListOf<Int>()
            for (i in 0 until numberOfBands) {
                levels.add(eq.getBandLevel(i.toShort()).toInt())
            }
            _bandLevels.value = levels
            TXAPreferences.setEqualizerBandLevels(levels)
        }
    }
    
    /**
     * Release resources
     */
    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
        } catch (e: Exception) {
            TXALogger.appE("TXAEqualizerManager", "Error releasing", e)
        }
        equalizer = null
        bassBoost = null
        virtualizer = null
        currentAudioSessionId = 0
        _isInitialized.value = false
    }
}
