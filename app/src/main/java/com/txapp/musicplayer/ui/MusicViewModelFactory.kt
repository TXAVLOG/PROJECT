package com.txapp.musicplayer.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.txapp.musicplayer.MusicApplication
import com.txapp.musicplayer.data.MusicRepository

class MusicViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val database = (application as MusicApplication).database
        val repository = MusicRepository(database, application.contentResolver)
        
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                HomeViewModel(repository) as T
            }
            // For DetailViewModels, we might need to use a different factory or pass IDs via savedStateHandle
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
