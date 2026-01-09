package com.txapp.musicplayer.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.txapp.musicplayer.data.MusicRepository
import com.txapp.musicplayer.model.Home
import com.txapp.musicplayer.model.Song
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: MusicRepository) : ViewModel() {

    private val _recentSongs = MutableLiveData<List<Song>>()
    val recentSongs: LiveData<List<Song>> = _recentSongs

    private val _topTracks = MutableLiveData<List<Song>>()
    val topTracks: LiveData<List<Song>> = _topTracks

    private val _smartMixSongs = MutableLiveData<List<Song>>()
    val smartMixSongs: LiveData<List<Song>> = _smartMixSongs

    private val _favoriteSongs = MutableLiveData<List<Song>>()
    val favoriteSongs: LiveData<List<Song>> = _favoriteSongs

    private val _lastAdded = MutableLiveData<List<Song>>()
    val lastAdded: LiveData<List<Song>> = _lastAdded

    private val _homeSections = MutableLiveData<List<Home>>()
    val homeSections: LiveData<List<Home>> = _homeSections
    
    private val _suggestions = MutableLiveData<List<Song>>()
    val suggestions: LiveData<List<Song>> = _suggestions

    private var allAlbums = listOf<com.txapp.musicplayer.model.Album>()
    private var allArtists = listOf<com.txapp.musicplayer.model.Artist>()

    init {
        loadRecentSongs()
        loadTopTracks()
        loadFavoriteSongs()
        loadLastAdded()
        loadSmartMix()
        loadHomeSections()
        loadSuggestions()
    }

    private var allTopTracks = listOf<Song>()

    private fun loadHomeSections() {
        viewModelScope.launch {
            repository.albums.collect { 
                allAlbums = it ?: emptyList()
                updateHomeSections()
            }
        }
        viewModelScope.launch {
            repository.artists.collect {
                allArtists = it ?: emptyList()
                updateHomeSections()
            }
        }
        viewModelScope.launch {
            repository.recentlyPlayed.collect {
                updateHomeSections()
            }
        }
        viewModelScope.launch {
            repository.topTracks.collect {
                allTopTracks = it ?: emptyList()
                updateHomeSections()
            }
        }
    }

    private fun updateHomeSections() {
        val sections = mutableListOf<Home>()
        
        // Top Tracks is now handled by standalone TopTracksSection (Compose)
        // No longer adding to homeSections (RecyclerView) to avoid duplication

        if (allAlbums.isNotEmpty()) {
            sections.add(Home(allAlbums.take(10), com.txapp.musicplayer.RECENT_ALBUMS, com.txapp.musicplayer.R.string.albums))
        }
        
        if (allArtists.isNotEmpty()) {
            sections.add(Home(allArtists.take(10), com.txapp.musicplayer.RECENT_ARTISTS, com.txapp.musicplayer.R.string.artists))
        }

        _homeSections.postValue(sections)
    }

    private fun loadFavoriteSongs() {
        viewModelScope.launch {
            repository.favoriteSongs.collect { songs ->
                _favoriteSongs.postValue(songs ?: emptyList())
            }
        }
    }

    private fun loadRecentSongs() {
        viewModelScope.launch {
            repository.recentlyPlayed.collect { songs ->
                _recentSongs.postValue(songs ?: emptyList())
            }
        }
    }

    fun refreshFavorites() {
        viewModelScope.launch {
            // Force a re-fetch from repository
            val songs = repository.favoriteSongs.first()
            _favoriteSongs.postValue(songs)
        }
    }

    fun refreshLastAdded() {
        viewModelScope.launch {
            val songs = repository.allSongs.first()
            _lastAdded.postValue(songs.take(20))
        }
    }

    fun refreshTopTracks() {
        viewModelScope.launch {
            val songs = repository.topTracks.first()
            _topTracks.postValue(songs)
        }
    }

    private fun loadLastAdded() {
        viewModelScope.launch {
            repository.allSongs.collect { songs ->
                // allSongs is already sorted by dateAdded DESC in SongDao
                _lastAdded.postValue(songs.take(20))
            }
        }
    }

    private fun loadTopTracks() {
        // Handled in loadHomeSections for Section display, 
        // but keep this for standalone LiveData if needed by other fragments
        viewModelScope.launch {
            repository.topTracks.collect { songs ->
                _topTracks.postValue(songs ?: emptyList())
            }
        }
    }

    private fun loadSmartMix() {
        // Smart Mix is always random suggestions
        viewModelScope.launch {
            repository.getSuggestions(20).collect { songs ->
                _smartMixSongs.postValue(songs ?: emptyList())
            }
        }
    }

    // Suggestions for the grid


    private fun loadSuggestions() {
        viewModelScope.launch {
            repository.getSuggestions(8).collect { songs ->
                _suggestions.postValue(songs ?: emptyList())
            }
        }
    }

    fun refreshSuggestions() {
        viewModelScope.launch {
            // Get new random suggestions
            val allSongs = repository.allSongs.first()
            if (allSongs.isNotEmpty()) {
                _suggestions.postValue(allSongs.shuffled().take(8))
            }
        }
    }

    fun generateSmartMix() {
        loadSmartMix()
    }
}
