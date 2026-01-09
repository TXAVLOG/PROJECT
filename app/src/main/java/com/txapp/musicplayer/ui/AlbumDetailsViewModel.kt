package com.txapp.musicplayer.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.txapp.musicplayer.data.MusicRepository
import com.txapp.musicplayer.model.Album
import com.txapp.musicplayer.model.Artist
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AlbumDetailsViewModel(
    private val repository: MusicRepository,
    private val albumId: Long
) : ViewModel() {

    private val _album = MutableLiveData<Album>()
    val album: LiveData<Album> = _album

    private val _moreAlbums = MutableLiveData<List<Album>>()
    val moreAlbums: LiveData<List<Album>> = _moreAlbums

    init {
        loadAlbum()
    }

    private fun loadAlbum() {
        viewModelScope.launch {
            repository.albums.collect { albums ->
                val album = albums.find { it.id == albumId }
                if (album != null) {
                    _album.postValue(album)
                    loadMoreFromArtist(album.artistId)
                }
            }
        }
    }

    private fun loadMoreFromArtist(artistId: Long) {
        viewModelScope.launch {
            repository.albums.collect { allAlbums ->
                val fromArtist = allAlbums.filter { it.artistId == artistId && it.id != albumId }
                _moreAlbums.postValue(fromArtist)
            }
        }
    }
}
