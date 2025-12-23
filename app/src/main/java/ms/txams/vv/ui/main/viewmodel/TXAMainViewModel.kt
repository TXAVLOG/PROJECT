package ms.txams.vv.ui.main.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ms.txams.vv.core.data.database.dao.TXASongDao
import ms.txams.vv.core.data.database.dao.TXAQueueDao
import ms.txams.vv.core.data.database.entity.TXASongEntity
import ms.txams.vv.core.service.TXAMusicService
import ms.txams.vv.core.service.RepeatMode
import timber.log.Timber
import javax.inject.Inject

/**
 * TXA Main ViewModel - MVVM pattern với proper ServiceConnection
 * Quản lý state cho Media3 integration và UI state management
 */
@HiltViewModel
class TXAMainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: TXASongDao,
    private val queueDao: TXAQueueDao
) : ViewModel() {

    // Service connection
    private var musicService: TXAMusicService? = null
    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TXAMusicService.TXAMusicBinder
            musicService = binder.getService()
            _isServiceConnected.value = true
            
            // Start observing service state
            observeServiceState()
            
            Timber.d("TXAMusicService connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            _isServiceConnected.value = false
            Timber.d("TXAMusicService disconnected")
        }
    }

    // Playback state
    private val _currentSong = MutableStateFlow<TXASongEntity?>(null)
    val currentSong: StateFlow<TXASongEntity?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private val _playbackState = MutableStateFlow(ms.txams.vv.core.service.TXAPlaybackState.IDLE)
    val playbackState: StateFlow<ms.txams.vv.core.service.TXAPlaybackState> = _playbackState.asStateFlow()

    // UI state
    private val _uiState = MutableStateFlow(UIState.LOADING)
    val uiState: StateFlow<UIState> = _uiState.asStateFlow()

    // Queue state
    private val _queueSize = MutableStateFlow(0)
    val queueSize: StateFlow<Int> = _queueSize.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    // Audio effects state
    private val _shuffleMode = MutableStateFlow(false)
    val shuffleMode: StateFlow<Boolean> = _shuffleMode.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    init {
        initializeViewModel()
    }

    private fun initializeViewModel() {
        viewModelScope.launch {
            try {
                loadLibraryData()
                _uiState.value = UIState.CONTENT
                Timber.d("TXAMainViewModel initialized successfully")
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize TXAMainViewModel")
                _uiState.value = UIState.ERROR
            }
        }
    }

    private suspend fun loadLibraryData() {
        try {
            val songCount = songDao.getAvailableSongCount()
            val favoriteCount = songDao.getFavoriteSongCount()
            
            val queueItems = queueDao.getQueueSnapshot()
            _queueSize.value = queueItems.size
            
            val currentQueueItem = queueItems.find { it.isCurrent }
            currentQueueItem?.let { item ->
                val song = songDao.getSongById(item.songId)
                _currentSong.value = song
                _currentPosition.value = item.lastPlayedPosition
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to load library data")
        }
    }

    fun connectToService() {
        try {
            val intent = Intent(context, TXAMusicService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            Timber.d("Attempting to connect to TXAMusicService")
        } catch (e: Exception) {
            Timber.e(e, "Failed to bind to music service")
        }
    }

    fun disconnectFromService() {
        try {
            if (_isServiceConnected.value) {
                context.unbindService(serviceConnection)
                musicService = null
                _isServiceConnected.value = false
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to disconnect from music service")
        }
    }

    fun refreshServiceConnection() {
        if (!_isServiceConnected.value) {
            connectToService()
        }
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            musicService?.let { service ->
                // Collect service state flows
                service.currentSong.collect { song ->
                    _currentSong.value = song
                }
                
                service.isPlaying.collect { isPlaying ->
                    _isPlaying.value = isPlaying
                }
                
                service.playbackState.collect { state ->
                    _playbackState.value = state
                }
            }
        }
    }

    // Playback controls
    fun togglePlayback() {
        musicService?.let { service ->
            if (_isPlaying.value) {
                service.pause()
            } else {
                service.play()
            }
        }
    }

    fun playNext() {
        musicService?.playNext()
    }

    fun playPrevious() {
        musicService?.playPrevious()
    }

    fun seekTo(position: Long) {
        musicService?.seekTo(position)
        _currentPosition.value = position
    }

    // Queue management
    fun addToQueue(song: TXASongEntity) {
        musicService?.addToQueue(song.id)
        _queueSize.value = (_queueSize.value + 1)
    }

    fun addToQueueNext(song: TXASongEntity) {
        musicService?.addToQueueNext(song.id)
        _queueSize.value = (_queueSize.value + 1)
    }

    fun removeFromQueue(songId: Long) {
        musicService?.removeFromQueue(songId)
        _queueSize.value = (_queueSize.value - 1).coerceAtLeast(0)
    }

    fun clearQueue() {
        musicService?.clearQueue()
        _queueSize.value = 0
    }

    fun shuffleQueue() {
        musicService?.shuffleQueue()
        _shuffleMode.value = true
    }

    // Playback modes
    fun toggleShuffle() {
        val newShuffleMode = !_shuffleMode.value
        _shuffleMode.value = newShuffleMode
        
        if (newShuffleMode) {
            shuffleQueue()
        }
        
        musicService?.setShuffleMode(newShuffleMode)
    }

    fun toggleRepeatMode() {
        val currentMode = _repeatMode.value
        val newMode = when (currentMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        
        _repeatMode.value = newMode
        musicService?.setRepeatMode(newMode)
    }

    // Song management
    fun playSong(song: TXASongEntity) {
        musicService?.playSong(song)
        _currentSong.value = song
    }

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch {
            try {
                val song = songDao.getSongById(songId)
                song?.let {
                    val newFavorite = !it.favorite
                    songDao.updateFavorite(songId, newFavorite)
                    
                    if (_currentSong.value?.id == songId) {
                        _currentSong.value = it.copy(favorite = newFavorite)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle favorite for song: $songId")
            }
        }
    }

    // Progress tracking
    fun updateProgress(position: Long, duration: Long) {
        _currentPosition.value = position
        _duration.value = duration
        
        if (duration > 0) {
            _playbackProgress.value = position.toFloat() / duration.toFloat()
        }
    }

    // State management
    fun saveCurrentState() {
        viewModelScope.launch {
            try {
                _currentSong.value?.let { song ->
                    queueDao.updateLastPlayedPosition(song.id, _currentPosition.value)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save current state")
            }
        }
    }

    // Library data
    fun getLibraryStats(): LibraryStats {
        return LibraryStats(
            totalSongs = _queueSize.value,
            currentSong = _currentSong.value,
            isPlaying = _isPlaying.value,
            shuffleMode = _shuffleMode.value,
            repeatMode = _repeatMode.value
        )
    }

    // Error handling
    fun handleError(error: Throwable) {
        Timber.e(error, "Error in TXAMainViewModel")
        _uiState.value = UIState.ERROR
    }

    override fun onCleared() {
        super.onCleared()
        disconnectFromService()
    }

    companion object {
        private const val TAG = "TXAMainViewModel"
    }
}

enum class UIState {
    LOADING,
    CONTENT,
    ERROR
}

data class LibraryStats(
    val totalSongs: Int,
    val currentSong: TXASongEntity?,
    val isPlaying: Boolean,
    val shuffleMode: Boolean,
    val repeatMode: RepeatMode
)
