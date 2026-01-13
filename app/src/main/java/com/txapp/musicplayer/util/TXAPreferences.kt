package com.txapp.musicplayer.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App Preferences Manager
 * Handles settings like Theme, Accent, Grid size, etc.
 */
object TXAPreferences {
    private const val PREFS_NAME = "txa_music_prefs"

    // Default values
    // Keys
    private const val KEY_THEME = "setting_theme"
    private const val KEY_ACCENT = "setting_accent"
    private const val KEY_GRID_SIZE = "setting_grid_size"
    private const val KEY_CROSSFADE = "setting_crossfade"
    private const val KEY_NOW_PLAYING_UI = "setting_now_playing_ui"
    private const val KEY_PLAYBACK_SPEED = "setting_playback_speed"
    private const val KEY_PLAYBACK_PITCH = "setting_playback_pitch"
    private const val KEY_IMAGE_QUALITY = "setting_image_quality"
    private const val KEY_AUTO_DOWNLOAD_IMAGES = "setting_auto_download_images"
    private const val KEY_HOLIDAY_EFFECT_ENABLED = "setting_holiday_effect_enabled"
    private const val KEY_SHOW_SHUFFLE_BTN = "setting_show_shuffle_btn"
    private const val KEY_SHOW_FAVORITE_BTN = "setting_show_favorite_btn"
    private const val KEY_AUDIO_FOCUS = "setting_audio_focus"
    private const val KEY_BLUETOOTH_PLAYBACK = "setting_bluetooth_playback"
    private const val KEY_HEADSET_PLAY = "setting_headset_play"
    private const val KEY_AUDIO_FADE = "setting_audio_fade"
    private const val KEY_PLAYER_EFFECTS_ENABLED = "setting_player_effects_enabled"
    private const val KEY_PLAYER_EFFECT_TYPE = "setting_player_effect_type"
    private const val KEY_EXTRA_CONTROLS = "setting_extra_controls"
    private const val KEY_ALBUM_COVER_TRANSFORM = "setting_album_cover_transform"
    private const val KEY_CAROUSEL_EFFECT = "setting_carousel_effect"
    private const val KEY_POWER_MODE = "setting_power_mode"
    private const val KEY_AOD_AUTO_BRIGHTNESS = "setting_aod_auto_brightness"
    private const val KEY_REMEMBER_LAST_TAB = "setting_remember_last_tab"
    private const val KEY_LAST_TAB = "setting_last_tab"
    private const val KEY_LYRICS_SCREEN_ON = "setting_lyrics_screen_on"
    private const val KEY_SHOW_LYRICS_IN_PLAYER = "setting_show_lyrics_in_player"
    private const val KEY_ALBUM_GRID_SIZE = "setting_album_grid_size"
    private const val KEY_ARTIST_GRID_SIZE = "setting_artist_grid_size"


    // Default values
    private const val DEF_THEME = "system"
    private const val DEF_ACCENT = "#FF1744" 
    private const val DEF_GRID_SIZE = 2
    private const val DEF_CROSSFADE = 0
    private const val DEF_PLAYBACK_SPEED = 1.0f
    private const val DEF_PLAYBACK_PITCH = 1.0f
    private const val DEF_IMAGE_QUALITY = "medium"
    private const val DEF_AUTO_DOWNLOAD_IMAGES = false
    private const val DEF_HOLIDAY_EFFECT_ENABLED = true
    private const val DEF_SHOW_SHUFFLE_BTN = true
    private const val DEF_SHOW_FAVORITE_BTN = true
    private const val DEF_AUDIO_FOCUS = true
    private const val DEF_BLUETOOTH_PLAYBACK = false
    private const val DEF_HEADSET_PLAY = false
    private const val DEF_AUDIO_FADE = 500
    private const val DEF_PLAYER_EFFECTS_ENABLED = true
    private const val DEF_PLAYER_EFFECT_TYPE = "snow"
    private const val DEF_EXTRA_CONTROLS = true
    private const val DEF_ALBUM_COVER_TRANSFORM = "normal"
    private const val DEF_CAROUSEL_EFFECT = false
    private const val DEF_REMEMBER_LAST_TAB = true
    private const val DEF_LAST_TAB = 0
    private const val DEF_ALBUM_GRID_SIZE = 2
    private const val DEF_LYRICS_SCREEN_ON = false
    private const val DEF_SHOW_LYRICS_IN_PLAYER = false
    private const val DEF_ARTIST_GRID_SIZE = 3


    private lateinit var prefs: SharedPreferences

    // StateFlows
    private val _theme = MutableStateFlow(DEF_THEME)
    val theme: StateFlow<String> = _theme.asStateFlow()

    private val _accentColor = MutableStateFlow(DEF_ACCENT)
    val accentColor: StateFlow<String> = _accentColor.asStateFlow()

    private val _gridSize = MutableStateFlow(DEF_GRID_SIZE)
    val gridSize: StateFlow<Int> = _gridSize.asStateFlow()
    
    private val _nowPlayingUI = MutableStateFlow("aurora")
    val nowPlayingUI: StateFlow<String> = _nowPlayingUI.asStateFlow()
    
    private val _autoDownloadImages = MutableStateFlow(DEF_AUTO_DOWNLOAD_IMAGES)
    val autoDownloadImages: StateFlow<Boolean> = _autoDownloadImages.asStateFlow()
    
    private val _playbackSpeed = MutableStateFlow(DEF_PLAYBACK_SPEED)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _holidayEffectEnabled = MutableStateFlow(DEF_HOLIDAY_EFFECT_ENABLED)
    val holidayEffectEnabled: StateFlow<Boolean> = _holidayEffectEnabled.asStateFlow()

    private val _showShuffleBtn = MutableStateFlow(DEF_SHOW_SHUFFLE_BTN)
    val showShuffleBtn: StateFlow<Boolean> = _showShuffleBtn.asStateFlow()

    private val _showFavoriteBtn = MutableStateFlow(DEF_SHOW_FAVORITE_BTN)
    val showFavoriteBtn: StateFlow<Boolean> = _showFavoriteBtn.asStateFlow()

    private val _crossfadeDuration = MutableStateFlow(DEF_CROSSFADE)
    val crossfadeDuration: StateFlow<Int> = _crossfadeDuration.asStateFlow()

    private val _audioFocus = MutableStateFlow(DEF_AUDIO_FOCUS)
    val audioFocus: StateFlow<Boolean> = _audioFocus.asStateFlow()

    private val _bluetoothPlayback = MutableStateFlow(DEF_BLUETOOTH_PLAYBACK)
    val bluetoothPlayback: StateFlow<Boolean> = _bluetoothPlayback.asStateFlow()

    private val _headsetPlay = MutableStateFlow(DEF_HEADSET_PLAY)
    val headsetPlay: StateFlow<Boolean> = _headsetPlay.asStateFlow()

    private val _audioFadeDuration = MutableStateFlow(DEF_AUDIO_FADE)
    val audioFadeDuration: StateFlow<Int> = _audioFadeDuration.asStateFlow()

    private val _playerEffectsEnabled = MutableStateFlow(DEF_PLAYER_EFFECTS_ENABLED)
    val playerEffectsEnabled: StateFlow<Boolean> = _playerEffectsEnabled.asStateFlow()

    private val _playerEffectType = MutableStateFlow(DEF_PLAYER_EFFECT_TYPE)
    val playerEffectType: StateFlow<String> = _playerEffectType.asStateFlow()

    private val _extraControls = MutableStateFlow(DEF_EXTRA_CONTROLS)
    val extraControls: StateFlow<Boolean> = _extraControls.asStateFlow()

    private val _albumCoverTransform = MutableStateFlow(DEF_ALBUM_COVER_TRANSFORM)
    val albumCoverTransform: StateFlow<String> = _albumCoverTransform.asStateFlow()

    private val _carouselEffect = MutableStateFlow(DEF_CAROUSEL_EFFECT)
    val carouselEffect: StateFlow<Boolean> = _carouselEffect.asStateFlow()

    private val _powerMode = MutableStateFlow(false)
    val powerMode: StateFlow<Boolean> = _powerMode.asStateFlow()

    private val _aodAutoBrightness = MutableStateFlow(true)
    val aodAutoBrightness: StateFlow<Boolean> = _aodAutoBrightness.asStateFlow()

    private val _rememberLastTab = MutableStateFlow(DEF_REMEMBER_LAST_TAB)
    val rememberLastTab: StateFlow<Boolean> = _rememberLastTab.asStateFlow()

    private val _lastTab = MutableStateFlow(DEF_LAST_TAB)
    val lastTab: StateFlow<Int> = _lastTab.asStateFlow()

    private val _albumGridSize = MutableStateFlow(DEF_ALBUM_GRID_SIZE)
    val albumGridSize: StateFlow<Int> = _albumGridSize.asStateFlow()

    private val _artistGridSize = MutableStateFlow(DEF_ARTIST_GRID_SIZE)
    val artistGridSize: StateFlow<Int> = _artistGridSize.asStateFlow()

    private val _lyricsScreenOn = MutableStateFlow(DEF_LYRICS_SCREEN_ON)
    val lyricsScreenOn: StateFlow<Boolean> = _lyricsScreenOn.asStateFlow()

    private val _showLyricsInPlayer = MutableStateFlow(DEF_SHOW_LYRICS_IN_PLAYER)
    val showLyricsInPlayer: StateFlow<Boolean> = _showLyricsInPlayer.asStateFlow()



    // Network Restricted Mode (Memory only, resets on app launch)
    var isRestrictedMode: Boolean = false
    
    // App context for language helper
    private var appContext: Context? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        appContext = context.applicationContext  // Store for language helper
        _theme.value = prefs.getString(KEY_THEME, DEF_THEME) ?: DEF_THEME
        _accentColor.value = prefs.getString(KEY_ACCENT, DEF_ACCENT) ?: DEF_ACCENT
        _gridSize.value = prefs.getInt(KEY_GRID_SIZE, DEF_GRID_SIZE)
        _nowPlayingUI.value = prefs.getString(KEY_NOW_PLAYING_UI, "full") ?: "full"
        _autoDownloadImages.value = prefs.getBoolean(KEY_AUTO_DOWNLOAD_IMAGES, DEF_AUTO_DOWNLOAD_IMAGES)
        _playbackSpeed.value = prefs.getFloat(KEY_PLAYBACK_SPEED, DEF_PLAYBACK_SPEED)
        _holidayEffectEnabled.value = prefs.getBoolean(KEY_HOLIDAY_EFFECT_ENABLED, DEF_HOLIDAY_EFFECT_ENABLED)
        _showShuffleBtn.value = prefs.getBoolean(KEY_SHOW_SHUFFLE_BTN, DEF_SHOW_SHUFFLE_BTN)
        _showFavoriteBtn.value = prefs.getBoolean(KEY_SHOW_FAVORITE_BTN, DEF_SHOW_FAVORITE_BTN)
        _crossfadeDuration.value = prefs.getInt(KEY_CROSSFADE, DEF_CROSSFADE)
        _audioFocus.value = prefs.getBoolean(KEY_AUDIO_FOCUS, DEF_AUDIO_FOCUS)
        _bluetoothPlayback.value = prefs.getBoolean(KEY_BLUETOOTH_PLAYBACK, DEF_BLUETOOTH_PLAYBACK)
        _headsetPlay.value = prefs.getBoolean(KEY_HEADSET_PLAY, DEF_HEADSET_PLAY)
        _audioFadeDuration.value = prefs.getInt(KEY_AUDIO_FADE, DEF_AUDIO_FADE)
        _playerEffectsEnabled.value = prefs.getBoolean(KEY_PLAYER_EFFECTS_ENABLED, DEF_PLAYER_EFFECTS_ENABLED)
        _playerEffectType.value = prefs.getString(KEY_PLAYER_EFFECT_TYPE, DEF_PLAYER_EFFECT_TYPE) ?: DEF_PLAYER_EFFECT_TYPE
        _extraControls.value = prefs.getBoolean(KEY_EXTRA_CONTROLS, DEF_EXTRA_CONTROLS)
        _albumCoverTransform.value = prefs.getString(KEY_ALBUM_COVER_TRANSFORM, DEF_ALBUM_COVER_TRANSFORM) ?: DEF_ALBUM_COVER_TRANSFORM
        _carouselEffect.value = prefs.getBoolean(KEY_CAROUSEL_EFFECT, DEF_CAROUSEL_EFFECT)
        _powerMode.value = prefs.getBoolean(KEY_POWER_MODE, false)
        _aodAutoBrightness.value = prefs.getBoolean(KEY_AOD_AUTO_BRIGHTNESS, true)
        _rememberLastTab.value = prefs.getBoolean(KEY_REMEMBER_LAST_TAB, DEF_REMEMBER_LAST_TAB)
        _lastTab.value = prefs.getInt(KEY_LAST_TAB, DEF_LAST_TAB)
        _albumGridSize.value = prefs.getInt(KEY_ALBUM_GRID_SIZE, DEF_ALBUM_GRID_SIZE)
        _artistGridSize.value = prefs.getInt(KEY_ARTIST_GRID_SIZE, DEF_ARTIST_GRID_SIZE)
        _lyricsScreenOn.value = prefs.getBoolean(KEY_LYRICS_SCREEN_ON, DEF_LYRICS_SCREEN_ON)
        _showLyricsInPlayer.value = prefs.getBoolean(KEY_SHOW_LYRICS_IN_PLAYER, DEF_SHOW_LYRICS_IN_PLAYER)
        _rememberPlaybackPosition.value = prefs.getBoolean(KEY_REMEMBER_PLAYBACK_POSITION, DEF_REMEMBER_PLAYBACK_POSITION)

    }

    var currentTheme: String
        get() = _theme.value
        set(value) {
            _theme.value = value
            prefs.edit().putString(KEY_THEME, value).apply()
        }

    var currentAccent: String
        get() = _accentColor.value
        set(value) {
            _accentColor.value = value
            prefs.edit().putString(KEY_ACCENT, value).apply()
        }

    var currentGridSize: Int
        get() = _gridSize.value
        set(value) {
            _gridSize.value = value
            prefs.edit().putInt(KEY_GRID_SIZE, value).apply()
        }
    
    var currentCrossfadeDuration: Int
        get() = _crossfadeDuration.value
        set(value) {
            _crossfadeDuration.value = value
            prefs.edit().putInt(KEY_CROSSFADE, value).apply()
        }

    var isAudioFocusEnabled: Boolean
        get() = _audioFocus.value
        set(value) {
            _audioFocus.value = value
            prefs.edit().putBoolean(KEY_AUDIO_FOCUS, value).apply()
        }

    var isBluetoothPlaybackEnabled: Boolean
        get() = _bluetoothPlayback.value
        set(value) {
            _bluetoothPlayback.value = value
            prefs.edit().putBoolean(KEY_BLUETOOTH_PLAYBACK, value).apply()
        }

    var isHeadsetPlayEnabled: Boolean
        get() = _headsetPlay.value
        set(value) {
            _headsetPlay.value = value
            prefs.edit().putBoolean(KEY_HEADSET_PLAY, value).apply()
        }

    var currentAudioFadeDuration: Int
        get() = _audioFadeDuration.value
        set(value) {
            _audioFadeDuration.value = value
            prefs.edit().putInt(KEY_AUDIO_FADE, value).apply()
        }
    
    fun getNowPlayingUI(): String = _nowPlayingUI.value
    fun setNowPlayingUI(value: String) {
        _nowPlayingUI.value = value
        prefs.edit().putString(KEY_NOW_PLAYING_UI, value).apply()
    }
    
    // Playback Speed
    var currentPlaybackSpeed: Float
        get() = _playbackSpeed.value
        set(value) {
            _playbackSpeed.value = value
            prefs.edit().putFloat(KEY_PLAYBACK_SPEED, value).apply()
        }
    
    // Playback Pitch
    fun getPlaybackPitch(): Float = prefs.getFloat(KEY_PLAYBACK_PITCH, DEF_PLAYBACK_PITCH)
    fun setPlaybackPitch(value: Float) = prefs.edit().putFloat(KEY_PLAYBACK_PITCH, value).apply()
    
    // Image Quality
    fun getImageQuality(): String = prefs.getString(KEY_IMAGE_QUALITY, DEF_IMAGE_QUALITY) ?: DEF_IMAGE_QUALITY
    fun setImageQuality(value: String) = prefs.edit().putString(KEY_IMAGE_QUALITY, value).apply()
    
    // Auto Download Images
    var isAutoDownloadImagesEnabled: Boolean
        get() = _autoDownloadImages.value
        set(value) {
            _autoDownloadImages.value = value
            prefs.edit().putBoolean(KEY_AUTO_DOWNLOAD_IMAGES, value).apply()
        }

    // Holiday Effects
    var isHolidayEffectEnabled: Boolean
        get() = _holidayEffectEnabled.value
        set(value) {
            _holidayEffectEnabled.value = value
            prefs.edit().putBoolean(KEY_HOLIDAY_EFFECT_ENABLED, value).apply()
        }

    // Player Effects
    var isPlayerEffectsEnabled: Boolean
        get() = _playerEffectsEnabled.value
        set(value) {
            _playerEffectsEnabled.value = value
            prefs.edit().putBoolean(KEY_PLAYER_EFFECTS_ENABLED, value).apply()
        }

    var currentPlayerEffectType: String
        get() = _playerEffectType.value
        set(value) {
            _playerEffectType.value = value
            prefs.edit().putString(KEY_PLAYER_EFFECT_TYPE, value).apply()
        }

    var isShowShuffleBtn: Boolean
        get() = _showShuffleBtn.value
        set(value) {
            _showShuffleBtn.value = value
            prefs.edit().putBoolean(KEY_SHOW_SHUFFLE_BTN, value).apply()
        }

    var isShowFavoriteBtn: Boolean
        get() = _showFavoriteBtn.value
        set(value) {
            _showFavoriteBtn.value = value
            prefs.edit().putBoolean(KEY_SHOW_FAVORITE_BTN, value).apply()
        }
    
    // Extra Controls (prev/next buttons on MiniPlayer)
    var isExtraControls: Boolean
        get() = _extraControls.value
        set(value) {
            _extraControls.value = value
            prefs.edit().putBoolean(KEY_EXTRA_CONTROLS, value).apply()
        }
    
    // Album Cover Transform Style
    var currentAlbumCoverTransform: String
        get() = _albumCoverTransform.value
        set(value) {
            _albumCoverTransform.value = value
            prefs.edit().putString(KEY_ALBUM_COVER_TRANSFORM, value).apply()
        }
    
    // Carousel Effect
    var isCarouselEffect: Boolean
        get() = _carouselEffect.value
        set(value) {
            _carouselEffect.value = value
            prefs.edit().putBoolean(KEY_CAROUSEL_EFFECT, value).apply()
        }

    var isPowerMode: Boolean
        get() = _powerMode.value
        set(value) {
            _powerMode.value = value
            prefs.edit().putBoolean(KEY_POWER_MODE, value).apply()
        }

    var isAodAutoBrightness: Boolean
        get() = _aodAutoBrightness.value
        set(value) {
            _aodAutoBrightness.value = value
            prefs.edit().putBoolean(KEY_AOD_AUTO_BRIGHTNESS, value).apply()
        }

    var isRememberLastTab: Boolean
        get() = _rememberLastTab.value
        set(value) {
            _rememberLastTab.value = value
            prefs.edit().putBoolean(KEY_REMEMBER_LAST_TAB, value).apply()
        }

    var currentLastTab: Int
        get() = _lastTab.value
        set(value) {
            _lastTab.value = value
            prefs.edit().putInt(KEY_LAST_TAB, value).apply()
        }

    var currentAlbumGridSize: Int
        get() = _albumGridSize.value
        set(value) {
            _albumGridSize.value = value
            prefs.edit().putInt(KEY_ALBUM_GRID_SIZE, value).apply()
        }

    var currentArtistGridSize: Int
        get() = _artistGridSize.value
        set(value) {
            _artistGridSize.value = value
            prefs.edit().putInt(KEY_ARTIST_GRID_SIZE, value).apply()
        }
    var isLyricsScreenOn: Boolean
        get() = _lyricsScreenOn.value
        set(value) {
            _lyricsScreenOn.value = value
            prefs.edit().putBoolean(KEY_LYRICS_SCREEN_ON, value).apply()
        }

    fun setShowLyricsInPlayer(enabled: Boolean) {
        _showLyricsInPlayer.value = enabled
        prefs.edit().putBoolean(KEY_SHOW_LYRICS_IN_PLAYER, enabled).apply()
    }

    
    /**
     * Check if allowed to download metadata/images based on network policy
     */
    fun isAllowedToDownloadMetadata(): Boolean {
        return isAutoDownloadImagesEnabled
    }
    
    // AOD settings have been moved to TXAAODSettings for better synchronization and dedicated management
    
    // ============== EQUALIZER SETTINGS ==============
    private const val KEY_EQUALIZER_ENABLED = "setting_eq_enabled"
    private const val KEY_EQUALIZER_BAND_LEVELS = "setting_eq_band_levels"
    private const val KEY_EQUALIZER_PRESET = "setting_eq_preset"
    private const val KEY_BASS_BOOST_ENABLED = "setting_bass_boost_enabled"
    private const val KEY_BASS_BOOST_STRENGTH = "setting_bass_boost_strength"
    private const val KEY_VIRTUALIZER_ENABLED = "setting_virtualizer_enabled"
    private const val KEY_VIRTUALIZER_STRENGTH = "setting_virtualizer_strength"
    
    var nowPlayingStyle: String
        get() = _nowPlayingUI.value
        set(value) = setNowPlayingUI(value)
    
    var isEqualizerEnabled: Boolean
        get() = prefs.getBoolean(KEY_EQUALIZER_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_EQUALIZER_ENABLED, value).apply()
    
    fun getEqualizerBandLevels(numBands: Int): List<Int> {
        val saved = prefs.getString(KEY_EQUALIZER_BAND_LEVELS, null)
        return try {
            saved?.split(",")?.map { it.toInt() } ?: List(numBands) { 0 }
        } catch (e: Exception) {
            List(numBands) { 0 }
        }
    }
    
    fun setEqualizerBandLevels(levels: List<Int>) {
        prefs.edit().putString(KEY_EQUALIZER_BAND_LEVELS, levels.joinToString(",")).apply()
    }
    
    fun getEqualizerPreset(): Int = prefs.getInt(KEY_EQUALIZER_PRESET, -1)
    fun setEqualizerPreset(preset: Int) = prefs.edit().putInt(KEY_EQUALIZER_PRESET, preset).apply()
    
    var isBassBoostEnabled: Boolean
        get() = prefs.getBoolean(KEY_BASS_BOOST_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BASS_BOOST_ENABLED, value).apply()
    
    fun getBassBoostStrength(): Int = prefs.getInt(KEY_BASS_BOOST_STRENGTH, 0)
    fun setBassBoostStrength(strength: Int) = prefs.edit().putInt(KEY_BASS_BOOST_STRENGTH, strength).apply()
    
    var isVirtualizerEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIRTUALIZER_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_VIRTUALIZER_ENABLED, value).apply()
    
    fun getVirtualizerStrength(): Int = prefs.getInt(KEY_VIRTUALIZER_STRENGTH, 0)
    fun setVirtualizerStrength(strength: Int) = prefs.edit().putInt(KEY_VIRTUALIZER_STRENGTH, strength).apply()

    // ============== POST UPDATE TRACKING ==============
    private const val KEY_LAST_SEEN_VERSION_CODE = "setting_last_seen_version_code"

    fun getLastSeenVersionCode(): Long = prefs.getLong(KEY_LAST_SEEN_VERSION_CODE, 0L)
    fun setLastSeenVersionCode(code: Long) = prefs.edit().putLong(KEY_LAST_SEEN_VERSION_CODE, code).apply()

    // Key for pending changelog (to show after update)
    private const val KEY_PENDING_CHANGELOG = "setting_pending_changelog"
    private const val KEY_PENDING_CHANGELOG_VERSION = "setting_pending_changelog_version"

    fun getPendingChangelog(): String? = prefs.getString(KEY_PENDING_CHANGELOG, null)
    fun getPendingChangelogVersion(): Long = prefs.getLong(KEY_PENDING_CHANGELOG_VERSION, 0L)

    fun setPendingChangelog(version: Long, changelog: String?) {
        prefs.edit()
            .putLong(KEY_PENDING_CHANGELOG_VERSION, version)
            .putString(KEY_PENDING_CHANGELOG, changelog)
            .apply()
    }

    fun clearPendingChangelog() {
        prefs.edit()
            .remove(KEY_PENDING_CHANGELOG)
            .remove(KEY_PENDING_CHANGELOG_VERSION)
            .apply()
    }

    // ============== LANGUAGE HELPER ==============
    fun getCurrentLanguage(): String {
        return try {
            appContext?.let { ctx ->
                val langPrefs = ctx.getSharedPreferences("txa_translation_prefs", Context.MODE_PRIVATE)
                langPrefs.getString("current_locale", "en") ?: "en"
            } ?: "en"
        } catch (e: Exception) {
            "en"
        }
    }

    // ============== PLAYBACK HISTORY ==============
    private const val KEY_REMEMBER_PLAYBACK_POSITION = "setting_remember_playback_pos"
    private const val DEF_REMEMBER_PLAYBACK_POSITION = true

    private val _rememberPlaybackPosition = MutableStateFlow(DEF_REMEMBER_PLAYBACK_POSITION)
    val rememberPlaybackPosition: StateFlow<Boolean> = _rememberPlaybackPosition.asStateFlow()

    var isRememberPlaybackPositionEnabled: Boolean
        get() = _rememberPlaybackPosition.value
        set(value) {
            _rememberPlaybackPosition.value = value
            prefs.edit().putBoolean(KEY_REMEMBER_PLAYBACK_POSITION, value).apply()
        }

    // ============== FLOATING LYRICS POSITION ==============
    private const val KEY_FLOATING_LYRICS_POS_X = "floating_lyrics_pos_x"
    private const val KEY_FLOATING_LYRICS_POS_Y = "floating_lyrics_pos_y"

    fun getFloatingLyricsPosition(): Pair<Int, Int> {
        val x = prefs.getInt(KEY_FLOATING_LYRICS_POS_X, -1)
        val y = prefs.getInt(KEY_FLOATING_LYRICS_POS_Y, -1)
        return Pair(x, y)
    }

    fun setFloatingLyricsPosition(x: Int, y: Int) {
        prefs.edit()
            .putInt(KEY_FLOATING_LYRICS_POS_X, x)
            .putInt(KEY_FLOATING_LYRICS_POS_Y, y)
            .apply()
    }
}
