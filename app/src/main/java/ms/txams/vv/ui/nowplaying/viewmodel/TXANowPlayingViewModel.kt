package ms.txams.vv.ui.nowplaying.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ms.txams.vv.core.data.database.dao.TXASongDao
import ms.txams.vv.core.data.database.entity.TXASongEntity
import ms.txams.vv.core.service.TXAPlaybackState
import ms.txams.vv.core.service.RepeatMode
import timber.log.Timber
import javax.inject.Inject

/**
 * TXA Now Playing ViewModel - Quản lý state cho now playing screen
 * Integration với TXAMusicService và UI state management
 */
@HiltViewModel
class TXANowPlayingViewModel @Inject constructor(
    private val songDao: TXASongDao
) : ViewModel() {

    // Playback state
    private val _currentSong = MutableStateFlow<TXASongEntity?>(null)
    val currentSong: StateFlow<TXASongEntity?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackState = MutableStateFlow(TXAPlaybackState.IDLE)
    val playbackState: StateFlow<TXAPlaybackState> = _playbackState.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    // Playback modes
    private val _shuffleMode = MutableStateFlow(false)
    val shuffleMode: StateFlow<Boolean> = _shuffleMode.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    // UI state
    private val _uiState = MutableStateFlow(NowPlayingUIState.LOADING)
    val uiState: StateFlow<NowPlayingUIState> = _uiState.asStateFlow()

    // Lyrics state
    private val _lyricsVisible = MutableStateFlow(false)
    val lyricsVisible: StateFlow<Boolean> = _lyricsVisible.asStateFlow()

    private val _currentLyrics = MutableStateFlow<String?>(null)
    val currentLyrics: StateFlow<String?> = _currentLyrics.asStateFlow()

    init {
        initializeViewModel()
    }

    private fun initializeViewModel() {
        viewModelScope.launch {
            try {
                _uiState.value = NowPlayingUIState.CONTENT
                Timber.d("TXANowPlayingViewModel initialized successfully")
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize TXANowPlayingViewModel")
                _uiState.value = NowPlayingUIState.ERROR
            }
        }
    }

    // Service state updates - simplified to avoid memory leaks
    fun updateCurrentSong(song: TXASongEntity?) {
        _currentSong.value = song
        loadLyricsForSong(song)
    }

    fun updatePlayingState(isPlaying: Boolean) {
        _isPlaying.value = isPlaying
    }

    fun updatePlaybackState(state: TXAPlaybackState) {
        _playbackState.value = state
    }

    fun updateProgress(position: Long, duration: Long) {
        _currentPosition.value = position
        _duration.value = duration
        
        if (duration > 0) {
            _playbackProgress.value = position.toFloat() / duration.toFloat()
        }
    }

    fun updateShuffleMode(enabled: Boolean) {
        _shuffleMode.value = enabled
    }

    fun updateRepeatMode(mode: RepeatMode) {
        _repeatMode.value = mode
    }

    // Song management
    fun toggleFavorite(songId: Long) {
        viewModelScope.launch {
            try {
                val song = songDao.getSongById(songId)
                song?.let {
                    val newFavorite = !it.favorite
                    songDao.updateFavorite(songId, newFavorite)
                    
                    // Update current song if it's the one being favorited
                    if (_currentSong.value?.id == songId) {
                        _currentSong.value = it.copy(favorite = newFavorite)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle favorite for song: $songId")
            }
        }
    }

    // Lyrics management
    private fun loadLyricsForSong(song: TXASongEntity?) {
        viewModelScope.launch {
            try {
                song?.let {
                    // Load lyrics from embedded metadata or online source
                    val lyrics = loadLyricsFromMetadata(it)
                    _currentLyrics.value = lyrics
                } ?: run {
                    _currentLyrics.value = null
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load lyrics for song: ${song?.title}")
                _currentLyrics.value = null
            }
        }
    }

    private suspend fun loadLyricsFromMetadata(song: TXASongEntity): String? {
        // Implementation for extracting lyrics from file metadata
        // or fetching from online lyrics service
        return null // Placeholder
    }

    fun toggleLyricsVisibility() {
        _lyricsVisible.value = !_lyricsVisible.value
    }

    // Audio effects state
    private val _equalizerEnabled = MutableStateFlow(false)
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    private val _crossfadeEnabled = MutableStateFlow(false)
    val crossfadeEnabled: StateFlow<Boolean> = _crossfadeEnabled.asStateFlow()

    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _pitch = MutableStateFlow(1.0f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    fun toggleEqualizer() {
        _equalizerEnabled.value = !_equalizerEnabled.value
    }

    fun toggleCrossfade() {
        _crossfadeEnabled.value = !_crossfadeEnabled.value
    }

    fun setSpeed(speed: Float) {
        _speed.value = speed.coerceIn(0.25f, 4.0f)
    }

    fun setPitch(pitch: Float) {
        _pitch.value = pitch.coerceIn(0.5f, 2.0f)
    }

    // Queue management
    private val _queueSize = MutableStateFlow(0)
    val queueSize: StateFlow<Int> = _queueSize.asStateFlow()

    private val _currentQueueIndex = MutableStateFlow(0)
    val currentQueueIndex: StateFlow<Int> = _currentQueueIndex.asStateFlow()

    fun updateQueueInfo(size: Int, currentIndex: Int) {
        _queueSize.value = size
        _currentQueueIndex.value = currentIndex
    }

    // Statistics and tracking
    private val _playCount = MutableStateFlow(0)
    val playCount: StateFlow<Int> = _playCount.asStateFlow()

    private val _skipCount = MutableStateFlow(0)
    val skipCount: StateFlow<Int> = _skipCount.asStateFlow()

    fun incrementPlayCount() {
        _playCount.value = _playCount.value + 1
    }

    fun incrementSkipCount() {
        _skipCount.value = _skipCount.value + 1
    }

    // UI state management
    fun showLyrics() {
        _lyricsVisible.value = true
    }

    fun hideLyrics() {
        _lyricsVisible.value = false
    }

    fun setErrorState(error: String) {
        _uiState.value = NowPlayingUIState.ERROR
        Timber.e("NowPlaying error: $error")
    }

    fun resetToContentState() {
        _uiState.value = NowPlayingUIState.CONTENT
    }

    // Utility methods
    fun getCurrentTimeString(): String {
        return formatTime(_currentPosition.value)
    }

    fun getTotalTimeString(): String {
        return formatTime(_duration.value)
    }

    fun getProgressPercentage(): Int {
        return if (_duration.value > 0) {
            ((_currentPosition.value.toFloat() / _duration.value.toFloat()) * 100).toInt()
        } else {
            0
        }
    }

    private fun formatTime(milliseconds: Long): String {
        val seconds = (milliseconds / 1000) % 60
        val minutes = (milliseconds / (1000 * 60)) % 60
        val hours = milliseconds / (1000 * 60 * 60)
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    // State validation
    fun isPlaying(): Boolean {
        return _isPlaying.value && _playbackState.value == TXAPlaybackState.PLAYING
    }

    fun canPlayNext(): Boolean {
        return _queueSize.value > 0 && _currentQueueIndex.value < _queueSize.value - 1
    }

    fun canPlayPrevious(): Boolean {
        return _queueSize.value > 0 && _currentQueueIndex.value > 0
    }

    fun hasCurrentSong(): Boolean {
        return _currentSong.value != null
    }

    // Cleanup
    override fun onCleared() {
        super.onCleared()
        // Cleanup resources if needed
        Timber.d("TXANowPlayingViewModel cleared")
    }

    companion object {
        private const val TAG = "TXANowPlayingViewModel"
    }
}

/**
 * UI state enumeration for now playing screen
 */
enum class NowPlayingUIState {
    LOADING,
    CONTENT,
    ERROR,
    LYRICS_VISIBLE
}
