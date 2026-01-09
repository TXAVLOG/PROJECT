package com.txapp.musicplayer.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.txapp.musicplayer.MusicApplication
import com.txapp.musicplayer.data.MusicRepository

class DetailViewModelFactory(
    private val application: Application,
    private val id: Long,
    private val name: String? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val database = (application as MusicApplication).database
        val repository = MusicRepository(database, application.contentResolver)
        
        return when {
            modelClass.isAssignableFrom(AlbumDetailsViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                AlbumDetailsViewModel(repository, id) as T
            }
            modelClass.isAssignableFrom(ArtistDetailsViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                ArtistDetailsViewModel(repository, id, name) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
