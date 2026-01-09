package com.txapp.musicplayer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.txapp.musicplayer.data.MusicRepository
import com.txapp.musicplayer.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val repository: MusicRepository) : ViewModel() {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    init {
        loadSongs()
    }

    private fun loadSongs() {
        viewModelScope.launch {
            val loadedSongs = repository.loadSongsFromMediaStore()
            repository.saveSongs(loadedSongs)
            _songs.value = loadedSongs
        }
    }
}
