package com.txapp.musicplayer.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.txapp.musicplayer.data.MusicRepository
import com.txapp.musicplayer.model.Album
import com.txapp.musicplayer.model.Artist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArtistDetailsViewModel(
    private val repository: MusicRepository,
    private val artistId: Long,
    private val artistName: String? = null
) : ViewModel() {

    private val _artist = MutableLiveData<Artist>()
    val artist: LiveData<Artist> = _artist

    init {
        loadArtist()
    }

    private fun loadArtist() {
        viewModelScope.launch {
            repository.artists.collect { artists ->
                if (artists.isEmpty()) {
                    com.txapp.musicplayer.util.TXALogger.appD("ArtistDetailsVM", "Artists list is empty, waiting...")
                    return@collect
                }

                var foundArtist = if (artistId != -1L) {
                    artists.find { it.id == artistId }
                } else null
                
                // Fallback to name if ID not found or not provided
                if (foundArtist == null && !artistName.isNullOrEmpty()) {
                    foundArtist = artists.find { it.name.equals(artistName, ignoreCase = true) }
                }
                
                if (foundArtist != null) {
                    _artist.postValue(foundArtist)
                } else {
                    com.txapp.musicplayer.util.TXALogger.appW("ArtistDetailsVM", "Artist not found for ID: $artistId, Name: $artistName")
                }
            }
        }
    }
}
