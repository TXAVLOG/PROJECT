package ms.txams.vv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ms.txams.vv.data.database.SongEntity
import ms.txams.vv.data.repository.MusicRepository
import ms.txams.vv.service.MusicService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val repository: MusicRepository
) : ViewModel() {

    private val _currentSong = MutableStateFlow<SongEntity?>(null)
    val currentSong: StateFlow<SongEntity?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val allSongs = repository.getAllSongs()
    val favoriteSongs = repository.getFavoriteSongs()
    val recentlyAddedSongs = repository.getRecentlyAddedSongs(20)
    val mostPlayedSongs = repository.getMostPlayedSongs(20)

    init {
        viewModelScope.launch {
            combine(
                repository.getAllSongs(),
                currentSong
            ) { songs, current ->
                // Auto-play first song if no current song
                if (current == null && songs.isNotEmpty()) {
                    _currentSong.value = songs.first()
                }
            }
        }
    }

    fun scanMusicLibrary() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val count = repository.scanMusicLibrary()
                // Refresh current song if needed
                allSongs.collect { songs ->
                    if (_currentSong.value == null && songs.isNotEmpty()) {
                        _currentSong.value = songs.first()
                    }
                }
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun playSong(song: SongEntity) {
        _currentSong.value = song
        _isPlaying.value = true
        // Service will handle actual playback
    }

    fun pausePlayback() {
        _isPlaying.value = false
        // Service will handle actual pause
    }

    fun resumePlayback() {
        _isPlaying.value = true
        // Service will handle actual resume
    }

    fun skipToNext() {
        viewModelScope.launch {
            allSongs.collect { songs ->
                val currentIndex = songs.indexOf(_currentSong.value)
                if (currentIndex != -1 && currentIndex < songs.size - 1) {
                    playSong(songs[currentIndex + 1])
                }
            }
        }
    }

    fun skipToPrevious() {
        viewModelScope.launch {
            allSongs.collect { songs ->
                val currentIndex = songs.indexOf(_currentSong.value)
                if (currentIndex > 0) {
                    playSong(songs[currentIndex - 1])
                }
            }
        }
    }

    fun seekTo(position: Long) {
        _playbackPosition.value = position
        // Service will handle actual seek
    }

    fun toggleFavorite() {
        _currentSong.value?.let { song ->
            viewModelScope.launch {
                repository.updateFavoriteStatus(song.id, !song.isFavorite)
                // Update local state
                _currentSong.value = song.copy(isFavorite = !song.isFavorite)
            }
        }
    }

    fun searchSongs(query: String) = repository.searchSongs(query)
    fun getSongsByArtist(artist: String) = repository.getSongsByArtist(artist)
    fun getSongsByAlbum(album: String) = repository.getSongsByAlbum(album)
    fun getAllAlbums() = repository.getAllAlbums()
    fun getAllArtists() = repository.getAllArtists()
}
